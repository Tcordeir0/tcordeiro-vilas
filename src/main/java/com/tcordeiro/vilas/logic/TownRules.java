package com.tcordeiro.vilas.logic;

import java.util.List;

/**
 * 0.5.0 — As regras (puras, testadas com JUnit) da vida da vila que pagam ou cobram: correio, mercado semanal, eleições,
 * ajudante, peixe do pescador, brincadeiras das crianças.
 * <p>
 * REGRAS DE ECONOMIA E SEGURANÇA (com mutações em {@code tools/mutation_test.py}):
 * <ul>
 * <li>correio: o selo vai TODO para a tesouraria; no máximo {@link #MAIL_PER_PLAYER} cartas por entregar de cada jogador e
 * {@link #MAIL_PER_VILLAGE} na vila; uma carta que não se entrega ao fim de {@link #MAIL_TRIES} tentativas volta para quem a mandou
 * (os itens nunca se perdem);</li>
 * <li>mercado: o desconto é um REEMBOLSO da tesouraria (percentagem do que o jogador pagou), com teto por jogador e por mercado, e
 * nunca maior que a tesouraria; só nas bancas (perto da Praça) e no dia e na hora do mercado;</li>
 * <li>eleições: só vota quem tem terreno na vila, uma vez por eleição; as propostas mudam taxas e postos, nunca põem dinheiro na
 * tesouraria nem nos jogadores; o imposto nunca fica negativo;</li>
 * <li>ajudante: o preço vai TODO para a tesouraria; um ajudante por jogador, um contrato por jogador por dia e no máximo
 * {@link #HELPERS_PER_VILLAGE} por vila; só colhe nos terrenos de quem o contratou e nunca entra em terrenos alheios;</li>
 * <li>peixe: cada peixe apanhado só repõe uma troca de venda de peixe do pescador (o jogador continua a pagar), no máximo
 * {@link #CATCHES_PER_DAY} por dia.</li>
 * </ul>
 */
public final class TownRules {
	private TownRules() {}

	// ---------------------------------------------------------------- correio (carteiro)

	/** Selo de uma carta (cêntimos): 1 CB, para a tesouraria. Sem o CubCoin o correio é grátis (não há meia esmeralda). */
	public static final long STAMP_CENTS = 100;
	public static final int MAIL_PER_PLAYER = 3, MAIL_PER_VILLAGE = 32, MAIL_TRIES = 3, MAIL_SLOTS = 9;

	/** Pode mandar mais uma carta? (cartas dele por entregar, cartas na vila) */
	public static boolean mayPost(int pendingOfPlayer, int pendingInVillage) {
		return pendingOfPlayer < MAIL_PER_PLAYER && pendingInVillage < MAIL_PER_VILLAGE;
	}

	/** Depois de mais uma tentativa falhada: devolve-se a quem mandou? */
	public static boolean returnToSender(int failedTries) {
		return failedTries >= MAIL_TRIES;
	}

	// ---------------------------------------------------------------- mercado semanal

	public static final int MARKET_EVERY_DAYS = 7;
	/** Das 7 h às 15 h (tempo do dia: 0 = 6 h). */
	public static final long MARKET_START = 1000, MARKET_END = 9000;
	public static final long MARKET_PERCENT = 15, MARKET_PLAYER_CAP = 1500, MARKET_BUDGET = 8000;
	/** Bancas no máximo (vendedores da vila) e distância à Praça a que uma troca conta como «na banca». */
	public static final int MARKET_STALLS = 4, MARKET_RADIUS = 14;

	/** Este dia é dia de mercado nesta vila? ({@code offset} vem da vila, para as vilas não terem todas o mesmo dia). */
	public static boolean marketDay(long day, int every, long offset) {
		if (every <= 0 || day < 1) return false;
		return Math.floorMod(day - offset, every) == every - 1;
	}

	public static boolean marketTime(long timeOfDay) {
		return timeOfDay >= MARKET_START && timeOfDay < MARKET_END;
	}

	/**
	 * Reembolso (cêntimos) de uma troca: {@code percent} % do que pagou, sem passar do teto do jogador, do que resta do orçamento,
	 * nem da tesouraria. Nunca negativo.
	 */
	public static long rebate(long paidCents, long percent, long playerSoFar, long playerCap, long budgetLeft, long treasury) {
		if (paidCents <= 0 || percent <= 0) return 0;
		long want = paidCents * percent / 100;
		want = Math.min(want, playerCap - playerSoFar);
		want = Math.min(want, budgetLeft);
		want = Math.min(want, treasury);
		return Math.max(0, want);
	}

	/** O teto por jogador, com a política «mercado maior» (+50 %) e sem o CubCoin pelo menos uma esmeralda. */
	public static long marketPlayerCap(boolean biggerMarket, long unitCents) {
		long cap = biggerMarket ? MARKET_PLAYER_CAP * 3 / 2 : MARKET_PLAYER_CAP;
		return Math.max(cap, unitCents);
	}

	// ---------------------------------------------------------------- eleições

	public static final int ELECTION_EVERY_DAYS = 8;
	/** As propostas dos candidatos. */
	public static final String TAX = "tax", GUARDS = "guards", FESTAS = "festas", MARKET = "market";
	public static final List<String> POLICIES = List.of(TAX, GUARDS, FESTAS, MARKET);
	/** «Imposto menor»: menos 1 ponto percentual na taxa das trocas (nunca abaixo de 0). */
	public static final double TAX_CUT = 1.0;

	/** O dia de votar: a cada {@code every} dias (0 = sem eleições), nunca no dia 0. */
	public static boolean electionDay(long day, int every) {
		return every > 0 && day > 0 && day % every == 0;
	}

	/** REGRA: só vota quem tem terreno na vila, uma vez por eleição, e só no dia de votar. */
	public static boolean mayVote(boolean hasLot, boolean alreadyVoted, boolean open) {
		return hasLot && !alreadyVoted && open;
	}

	/**
	 * Quem ganhou: o mais votado; empate → o que propõe a política em vigor, senão o primeiro. Sem votos → −1 (nada muda).
	 */
	public static int winner(int[] votes, int incumbent) {
		int best = -1, top = 0;
		for (int i = 0; i < votes.length; i++) {
			if (votes[i] > top || (votes[i] == top && top > 0 && i == incumbent)) {
				top = votes[i];
				best = i;
			}
		}
		return top == 0 ? -1 : best;
	}

	/** A taxa das trocas com a política em vigor (REGRA: nunca negativa). */
	public static double taxWithPolicy(double percent, String policy) {
		if (TAX.equals(policy)) return Math.max(0, percent - TAX_CUT);
		return percent;
	}

	/** Postos de guarda com a política em vigor (+1 com «mais guardas», no máximo 5). */
	public static int guardPostsWithPolicy(int posts, String policy) {
		return GUARDS.equals(policy) ? Math.min(5, posts + 1) : posts;
	}

	// ---------------------------------------------------------------- ajudante

	public static final long HELPER_PRICE_CENTS = 2000;
	public static final int HELPERS_PER_VILLAGE = 2, HELPER_SLOTS = 9;
	/** Um contrato dura um dia de jogo (ticks). */
	public static final long HELPER_TICKS = 24000;

	/** REGRA: pode contratar? (já tem um, já contratou hoje, quantos há na vila, aldeão livre) */
	public static boolean mayHire(boolean hasHelper, boolean hiredToday, int helpersInVillage, boolean villagerFree) {
		return !hasHelper && !hiredToday && helpersInVillage < HELPERS_PER_VILLAGE && villagerFree;
	}

	/** O preço em esmeraldas inteiras (sem o CubCoin): arredonda para cima, no mínimo 1. */
	public static long priceInEmeralds(long cents, long unitCents) {
		if (unitCents <= 0) return Math.max(1, cents);
		return Math.max(1, (cents + unitCents - 1) / unitCents);
	}

	/**
	 * REGRA DE SEGURANÇA: o ajudante pode pôr os pés (ou colher) aqui? Fora de terrenos sim (a vila é de todos), num terreno só se
	 * quem o contratou for dono ou amigo. {@code lotOwner} null = fora de terrenos.
	 */
	public static boolean helperMayEnter(java.util.UUID lotOwner, java.util.Set<java.util.UUID> lotFriends, java.util.UUID employer) {
		if (lotOwner == null) return true;
		return LotRules.mayModify(lotOwner, lotFriends, employer);
	}

	/** REGRA DE SEGURANÇA: só colhe dentro de um terreno de quem o contratou (nunca nos campos da vila nem nos alheios). */
	public static boolean helperMayHarvest(java.util.UUID lotOwner, java.util.Set<java.util.UUID> lotFriends, java.util.UUID employer) {
		return lotOwner != null && LotRules.mayModify(lotOwner, lotFriends, employer);
	}

	// ---------------------------------------------------------------- pescador

	public static final int CATCHES_PER_DAY = 6;
	/** Uma pescaria dura 1 a 2 minutos; entre pescarias, 2 minutos. */
	public static final int FISH_MIN_TICKS = 1200, FISH_MAX_TICKS = 2400, FISH_PAUSE = 2400;

	public static boolean mayRestock(int catchesToday) {
		return catchesToday < CATCHES_PER_DAY;
	}

	// ---------------------------------------------------------------- crianças

	/** Brincam de dia, fora das horas de aprendiz: das 6 h às 8 h e das 15 h às 18 h. */
	public static boolean playTime(long timeOfDay) {
		return (timeOfDay >= 0 && timeOfDay < LifeRules.WORK_START) || (timeOfDay >= LifeRules.WORK_END && timeOfDay < LifeRules.BED_BASE);
	}

	public static final int PLAY_RADIUS = 20, PLAY_COOLDOWN = 600, HIDE_COUNT_TICKS = 100, HIDE_TICKS = 900, TAG_TICKS = 600;
}
