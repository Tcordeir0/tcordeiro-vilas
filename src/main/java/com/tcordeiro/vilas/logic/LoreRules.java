package com.tcordeiro.vilas.logic;

/**
 * 0.6.0 — As regras (puras, testadas com JUnit) da memória dos aldeões, da crónica, do «você me salvou», das cartas dos aldeões,
 * dos projetos comunitários com doações.
 * <p>
 * REGRAS DE ECONOMIA E SEGURANÇA (com mutações em {@code tools/mutation_test.py}):
 * <ul>
 * <li>memória: no máximo {@link #MEMORY_SLOTS} recordações por aldeão; a mais fraca sai quando entra uma mais forte;</li>
 * <li>«você me salvou»: reputação uma vez por aldeão, por jogador e por dia;</li>
 * <li>cartas e presentes dos aldeões: no máximo uma por jogador por semana; o presente é um ITEM, nunca dinheiro;</li>
 * <li>doações: só se tira ao jogador o que ainda falta à obra; a reputação das doações tem teto por dia; nunca se dá dinheiro;</li>
 * </ul>
 */
public final class LoreRules {
	private LoreRules() {}

	// ---------------------------------------------------------------- memória

	public static final int MEMORY_SLOTS = 6;
	/** A cada tantos dias uma recordação perde 1 de peso (nunca abaixo de 1). */
	public static final int DECAY_DAYS = 8;

	/** O peso de uma recordação hoje (decai devagar, nunca abaixo de 1). */
	public static int weightNow(int weight, long day, long today) {
		long lost = Math.max(0, today - day) / DECAY_DAYS;
		return (int) Math.max(1, weight - lost);
	}

	/**
	 * Onde entra uma recordação nova de peso {@code weight}: -1 = há espaço (acrescenta), {@code -2} = não entra (é mais fraca que
	 * todas), senão o índice da mais fraca, que sai. {@code now} são os pesos de hoje das que já lá estão.
	 */
	public static int slotFor(int[] now, int weight) {
		if (now.length < MEMORY_SLOTS) return -1;
		int weakest = 0;
		for (int i = 1; i < now.length; i++) if (now[i] < now[weakest]) weakest = i;
		return now[weakest] <= weight ? weakest : -2;
	}

	// ---------------------------------------------------------------- crónica

	public static final int CHRONICLE_MAX = 200, CHRONICLE_SHOWN = 12, BOOK_PAGES = 50;

	// ---------------------------------------------------------------- «você me salvou»

	/** Um monstro que atacou um aldeão há menos disto (ticks) e morre às mãos de um jogador conta como resgate. */
	public static final int SAVE_WINDOW = 200;
	public static final int SAVE_GOSSIP = 10, SAVE_REP = 3;

	/** O monstro morto estava a atacar o aldeão (alvo dele, ou bateu-lhe há pouco)? */
	public static boolean rescue(boolean targetWasVillager, long lastHitTick, long now) {
		return targetWasVillager || (lastHitTick >= 0 && now - lastHitTick <= SAVE_WINDOW);
	}

	/** REGRA: reputação pelo resgate só uma vez por dia por aldeão e jogador. */
	public static boolean mayRewardSave(long lastRewardDay, long today) {
		return lastRewardDay != today;
	}

	/** REGRA: uma carta (ou presente) de um aldeão por jogador por semana. */
	public static boolean mayWriteLetter(long lastLetterWeek, long week) {
		return lastLetterWeek != week;
	}

	// ---------------------------------------------------------------- projetos comunitários

	/** A vila propõe uma obra nova a cada tantos dias (se não houver nenhuma a decorrer). */
	public static final int PROJECT_EVERY_DAYS = 6;
	/** Reputação: 1 por cada {@link #ITEMS_PER_REP} itens doados, até {@link #DONATION_REP_DAY} por jogador por dia. */
	public static final int ITEMS_PER_REP = 8, DONATION_REP_DAY = 12, MAX_DONORS = 32;

	/** REGRA: quantos itens se tiram ao jogador (só o que falta; nunca negativo). */
	public static int take(int needed, int have, int offered) {
		return Math.max(0, Math.min(offered, needed - have));
	}

	/** REGRA: a reputação de uma doação (com teto por dia; nunca dinheiro). */
	public static int donationRep(int items, int repToday) {
		int rep = items / ITEMS_PER_REP;
		return Math.max(0, Math.min(rep, DONATION_REP_DAY - repToday));
	}

	public static boolean projectDue(long lastProposed, long today) {
		return lastProposed < 0 || today - lastProposed >= PROJECT_EVERY_DAYS;
	}

}
