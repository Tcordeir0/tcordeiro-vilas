package com.tcordeiro.vilas.logic;

/**
 * 0.3.0 — A vila cresce com as missões (lógica pura, testada com JUnit): cada missão concluída (do quadro ou pedido de um aldeão)
 * dá 1 de prestígio; a cada {@link #DEFAULT_PER_RESIDENT} chega um morador novo, a pé, pela borda da vila — se houver cama livre;
 * sem cama, o Prefeito encomenda uma casa simples (paga pela tesouraria). Nunca acima do teto de população (servidores leves).
 */
public final class GrowthRules {
	private GrowthRules() {}

	public static final int DEFAULT_PER_RESIDENT = 3, DEFAULT_CAP = 40;
	/** A casa simples (uma casa pequena da vila do jogo, com cama): 50 CB. */
	public static final long HOUSE_CENTS = 5000;

	/** O que fazer agora com o prestígio juntado. */
	public enum Step {
		WAIT, ARRIVE, ORDER_HOUSE, FULL
	}

	/**
	 * REGRA: chega um morador? Só com prestígio bastante e abaixo do teto; sem cama livre, encomenda-se uma casa (uma de cada vez e
	 * se a tesouraria a pagar).
	 */
	public static Step step(int prestige, int perResident, int population, int cap, int freeBeds, boolean houseInProgress, long treasury) {
		if (prestige < Math.max(1, perResident)) return Step.WAIT;
		if (population >= cap) return Step.FULL;
		if (freeBeds > 0) return Step.ARRIVE;
		return !houseInProgress && treasury >= HOUSE_CENTS ? Step.ORDER_HOUSE : Step.WAIT;
	}
}
