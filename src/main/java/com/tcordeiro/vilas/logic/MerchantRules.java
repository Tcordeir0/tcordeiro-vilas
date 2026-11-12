package com.tcordeiro.vilas.logic;

/**
 * O mercador viajante da vila (lógica pura, testada com JUnit): chega à praça de {@code every} em {@code every} dias, fica
 * {@code stay} dias e vai embora. O primeiro chega {@code every} dias depois de a vila ser fundada.
 */
public final class MerchantRules {
	private MerchantRules() {}

	public static final int DEFAULT_EVERY_DAYS = 5;
	public static final int DEFAULT_STAY_DAYS = 2;

	/** Chega hoje? {@code next} = o dia marcado para a próxima visita. */
	public static boolean arrives(boolean present, long today, long next) {
		return !present && next >= 0 && today >= next;
	}

	/** Vai-se embora hoje? */
	public static boolean leaves(boolean present, long today, long arrived, int stay) {
		return present && arrived >= 0 && today - arrived >= Math.max(1, stay);
	}

	/** O dia da próxima visita, a contar do dia em que partiu. */
	public static long nextVisit(long leftDay, int every) {
		return leftDay + Math.max(1, every);
	}
}
