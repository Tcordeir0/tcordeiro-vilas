package com.tcordeiro.vilas.logic;

/**
 * O Prefeito ajusta a taxa das trocas (lógica pura, testada com JUnit):
 * <ul>
 * <li>sobe {@code perStructure} pontos percentuais por cada melhoria paga pela tesouraria (morador, golem, reforço, muralha,
 * iluminação) — a vila cresce e custa mais a manter;</li>
 * <li>nunca passa do teto ({@code cap});</li>
 * <li>quando a tesouraria está cheia ({@code fullAt}), volta à taxa base: o Prefeito não precisa de mais dinheiro.</li>
 * </ul>
 */
public final class TaxRules {
	private TaxRules() {}

	public static final double DEFAULT_PER_STRUCTURE = 0.5;
	public static final double DEFAULT_CAP = 5.0;
	/** Tesouraria cheia: 1000 CB. */
	public static final long DEFAULT_FULL_CENTS = 100_000;

	/** Porquê a taxa é a que é (o quadro mostra). */
	public enum Reason {
		BASE, RAISED, CAPPED, FULL;

		public String key() {
			return "tvilas.tax.reason." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Rate(double percent, Reason reason) {}

	/** A taxa em vigor. REGRA: nunca passa do teto. */
	public static Rate rate(double base, int structures, double perStructure, double cap, long treasury, long fullAt) {
		double b = Math.max(0, base);
		double top = Math.max(0, cap);
		if (fullAt > 0 && treasury >= fullAt) return new Rate(Math.min(b, top), Reason.FULL);
		double raised = b + Math.max(0, structures) * Math.max(0, perStructure);
		if (raised >= top) return new Rate(top, Reason.CAPPED);
		return new Rate(raised, raised > b ? Reason.RAISED : Reason.BASE);
	}

	/** «2,5» (vírgula, sem zeros a mais) para mostrar a percentagem. */
	public static String format(double percent) {
		long tenths = Math.round(percent * 10);
		return tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "," + Math.abs(tenths % 10);
	}
}
