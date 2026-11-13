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

	/** Índices de {@link #deltas}: rotações da cabeça (x, y, z), dos braços (x, z) e a subida dos braços (y, em píxeis). */
	public static final int HEAD_X = 0, HEAD_Y = 1, HEAD_Z = 2, ARMS_X = 3, ARMS_Z = 4, ARMS_Y = 5;

	/**
	 * 0.5.0 — O gesto como SOMA por cima da animação que já lá está (do Fresh Animations ou do jogo): o Vilas mexe só um pouco na
	 * cabeça e no bloco dos braços, no fim da animação de cada fotograma. {@code t} vai de 0 a 1. Fora dos gestos: tudo 0.
	 * 1 acenar, 2 festejar, 3 encolher os ombros, 4 sim, 5 não, 6 apontar.
	 */
	public static float[] deltas(int code, float t) {
		float[] d = new float[6];
		if (t <= 0 || t >= 1) return d;
		double pi = Math.PI, env = Math.sin(t * pi); // sobe e desce suave
		switch (code) {
			case 1 -> {
				d[ARMS_X] = (float) (-env * 1.3);
				d[ARMS_Z] = (float) (Math.sin(t * pi * 6) * 0.3 * env);
			}
			case 2 -> d[ARMS_X] = (float) (-Math.abs(Math.sin(t * pi * 2)) * 1.5);
			case 3 -> {
				d[ARMS_Y] = (float) (-env * 1.5);
				d[HEAD_Z] = (float) (env * 0.25);
			}
			case 4 -> d[HEAD_X] = (float) (Math.sin(t * pi * 4) * 0.35);
			case 5 -> d[HEAD_Y] = (float) (Math.sin(t * pi * 4) * 0.45);
			case 6 -> d[ARMS_X] = (float) (-Math.sin(Math.min(t * 4, 1) * pi / 2) * 1.1 * Math.min(1, (1 - t) * 6));
			default -> {}
		}
		return d;
	}

	/** De 0 a 1 ao longo do gesto (0 fora dele). */
	public static float progress(long start, long until, long now) {
		if (now < start || now >= until || until <= start) return 0f;
		return (float) (now - start) / (float) (until - start);
	}
}
