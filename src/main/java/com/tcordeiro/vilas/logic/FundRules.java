package com.tcordeiro.vilas.logic;

/**
 * 0.3.0 — O fundo das missões (lógica pura, testada com JUnit). Uma vila nova nasce com {@link #START_CENTS} e recebe
 * {@link #DAILY_CENTS} por dia de jogo, sem passar de {@link #CAP_CENTS}. As missões pagam primeiro do fundo e depois da
 * tesouraria. Assim uma vila nova tem missões logo, e o dinheiro criado do nada tem um teto.
 */
public final class FundRules {
	private FundRules() {}

	/** 200 CB ao nascer, +50 CB por dia, até 400 CB. */
	public static final long START_CENTS = 20000, DAILY_CENTS = 5000, CAP_CENTS = 40000;

	/** REGRA DE SEGURANÇA (economia): o fundo depois de {@code days} dias, nunca acima do teto (nem abaixo do que já tinha). */
	public static long topUp(long fund, long days, long daily, long cap) {
		if (days <= 0 || daily <= 0 || fund >= cap) return fund;
		return Math.min(cap, fund + Math.min(days, cap / Math.max(1, daily) + 1) * daily);
	}

	/** Quanto sai do fundo para pagar {@code reward} (o resto sai da tesouraria). */
	public static long fromFund(long fund, long reward) {
		return Math.max(0, Math.min(fund, reward));
	}

	/** Dias sem ver o Prefeito até chegar outro. */
	public static final int MAYOR_GONE_DAYS = 3;

	/** REGRA (C6/0.3.0): o Prefeito sumiu há tempo bastante para chegar outro? (nunca visto conta como agora) */
	public static boolean mayorGone(long lastSeen, long now, long dayTicks) {
		return lastSeen >= 0 && now - lastSeen >= MAYOR_GONE_DAYS * dayTicks;
	}
}
