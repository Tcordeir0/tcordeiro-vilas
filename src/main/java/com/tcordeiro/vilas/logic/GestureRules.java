package com.tcordeiro.vilas.logic;

/**
 * 0.5.0 — As variáveis de animação para o Entity Model Features (lógica pura): {@code tvilas_gesture} = o código do gesto em curso
 * (0 = nenhum) e {@code tvilas_gesture_time} = de 0 a 1, quanto do gesto já passou. Um pacote de recursos (por exemplo, por cima
 * do Fresh Animations) pode usá-las nas expressões do aldeão; sem pacote que as use, não mudam nada.
 */
public final class GestureRules {
	private GestureRules() {}

	/** O código do gesto agora (0 se já acabou ou ainda não começou). */
	public static float code(int code, long start, long until, long now) {
		return now >= start && now < until ? code : 0f;
	}

	/** De 0 a 1 ao longo do gesto (0 fora dele). */
	public static float progress(long start, long until, long now) {
		if (now < start || now >= until || until <= start) return 0f;
		return (float) (now - start) / (float) (until - start);
	}
}
