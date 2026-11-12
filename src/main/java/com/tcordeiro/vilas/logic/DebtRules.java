package com.tcordeiro.vilas.logic;

/**
 * Consequência das dívidas (lógica pura, testada com JUnit). Quem deve pelo menos {@code minCents} há {@code freezeDays} dias
 * fica com os terrenos «congelados»: não pode construir nem partir lá até pagar. NADA é apagado nem tirado — só fica parado.
 * Um dia antes, o Prefeito avisa (uma linha).
 */
public final class DebtRules {
	private DebtRules() {}

	public static final int DEFAULT_FREEZE_DAYS = 3;
	/** Dívida mínima que conta (1 CB): uns cêntimos de quem está desligado não congelam nada. */
	public static final long DEFAULT_MIN_CENTS = 100;

	/** A dívida conta para o congelamento? (sem CubCoin só conta a partir de uma esmeralda inteira, que é o que se pode pagar) */
	public static boolean counts(long debtCents, long minCents) {
		return debtCents > 0 && debtCents >= Math.max(1, minCents);
	}

	/** Dias seguidos em dívida (0 se {@code since} &lt; 0). */
	public static long daysInDebt(long since, long today) {
		return since < 0 ? 0 : Math.max(0, today - since);
	}

	/** O terreno fica congelado? */
	public static boolean frozen(long since, long today, int freezeDays) {
		return since >= 0 && daysInDebt(since, today) >= Math.max(1, freezeDays);
	}

	/** É o dia do aviso (falta um dia para congelar)? */
	public static boolean warnToday(long since, long today, int freezeDays) {
		return since >= 0 && daysInDebt(since, today) == Math.max(1, freezeDays) - 1;
	}

	/** Sem CubCoin: esmeraldas para pagar TUDO no quadro (arredondado para cima; o troco fica para a tesouraria). */
	public static long emeraldsToClear(long debtCents, long emeraldCents) {
		if (debtCents <= 0 || emeraldCents <= 0) return 0;
		return (debtCents + emeraldCents - 1) / emeraldCents;
	}
}
