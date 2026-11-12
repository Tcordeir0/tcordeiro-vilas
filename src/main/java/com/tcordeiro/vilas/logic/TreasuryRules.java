package com.tcordeiro.vilas.logic;

/**
 * Impostos e tesouraria (lógica pura, testada com JUnit). Tudo em cêntimos de CubCoin (1 CB = 100¢).
 * <ul>
 * <li>Moradia: {@code bed_tax_cents} por cama e por dia de jogo, nas camas dentro dos terrenos dos jogadores.</li>
 * <li>Trocas: {@code trade_tax_percent} (2 %) do valor de cada troca no território — com aldeões (o valor das esmeraldas da
 * troca) e entre jogadores pelo CubCoin (o preço). Paga quem recebe o dinheiro (o vendedor) ou, com aldeões, o jogador.</li>
 * <li>O Prefeito gasta a tesouraria uma vez por dia, por esta ordem: mais moradores (se há camas livres), um golem da vila (até 1
 * por 10 aldeões), reforço do alarme (+1 golem por alarme; até +1 numa Vila, +2 numa Cidade).</li>
 * </ul>
 */
public final class TreasuryRules {
	private TreasuryRules() {}

	public static final double DEFAULT_TRADE_TAX_PERCENT = 2.0;
	public static final long DEFAULT_BED_TAX_CENTS = 1;
	/** Uma esmeralda = 40 CB (o preço base do Balcão de Câmbio do CubCoin). */
	public static final long DEFAULT_EMERALD_CENTS = 4000;
	/** Moradores que chegam de graça (1 por dia) a uma vila com camas: é assim que uma vila vazia renasce. */
	public static final int FREE_RESIDENTS = 3;
	/** Dias de impostos cobrados de uma vez, no máximo (vila que ficou muito tempo sem ninguém por perto). */
	public static final int MAX_DAYS_AT_ONCE = 7;

	public enum Upgrade {
		RESIDENT(2000), GOLEM(6000), ALARM(12000);

		public final long cost;

		Upgrade(long cost) {
			this.cost = cost;
		}

		public String key() {
			return "tvilas.upgrade." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Taxa sobre uma troca de {@code valueCents}: a percentagem, arredondada, pelo menos 1¢ (taxa mínima). */
	public static long tradeTax(long valueCents, double percent) {
		if (valueCents <= 0 || percent <= 0) return 0;
		return Math.max(1, Math.round(valueCents * percent / 100.0));
	}

	/** Imposto de moradia. */
	public static long bedTax(int beds, long days, long centsPerBed) {
		if (beds <= 0 || days <= 0 || centsPerBed <= 0) return 0;
		return beds * Math.min(days, MAX_DAYS_AT_ONCE) * centsPerBed;
	}

	/** Sem CubCoin: quantas esmeraldas inteiras se cobram de uma dívida (o resto fica para a próxima). */
	public static long emeraldsFor(long debtCents, long emeraldCents) {
		return emeraldCents <= 0 ? 0 : Math.max(0, debtCents) / emeraldCents;
	}

	/** Chega um morador de graça? (vila com camas e menos de {@link #FREE_RESIDENTS} aldeões) */
	public static boolean freeResident(int population, int beds) {
		return population < Math.min(beds, FREE_RESIDENTS);
	}

	/** Golens que a vila deve ter (os de casa, não os do alarme). */
	public static int wantedGolems(int population) {
		return Math.max(1, population / 10);
	}

	/** Reforço máximo do alarme pelo nível (Aldeia 0, Vila 1, Cidade 2). */
	public static int maxAlarmBonus(int level) {
		return VillageRules.clamp(level);
	}

	/** O que o Prefeito compra hoje (ou null): a primeira melhoria que faz falta e que a tesouraria paga. */
	public static Upgrade choose(long treasury, int population, int beds, int golems, int alarmBonus, int level) {
		Upgrade want;
		if (population < beds) want = Upgrade.RESIDENT;
		else if (golems < wantedGolems(population)) want = Upgrade.GOLEM;
		else if (alarmBonus < maxAlarmBonus(level)) want = Upgrade.ALARM;
		else return null;
		return treasury >= want.cost ? want : null;
	}
}
