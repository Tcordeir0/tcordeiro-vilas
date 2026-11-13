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
	 * 1 acenar, 2 festejar, 3 encolher os ombros, 4 sim, 5 não, 6 apontar, 7 bater palmas, 8 martelar, 9 comer.
	 */
	public static float[] deltas(int code, float t) {
		float[] d = new float[6];
		if (t <= 0 || t >= 1) return d;
		double pi = Math.PI, env = Math.sin(t * pi); // sobe e desce suave
		switch (code) {
			case 1 -> { // acenar: braços sobem e abanam de lado
				d[ARMS_X] = (float) (-env * 1.3);
				d[ARMS_Y] = (float) (-env * 2.5);
				d[ARMS_Z] = (float) (Math.sin(t * pi * 6) * 0.5 * env);
			}
			case 2 -> { // festejar: braços ao alto duas vezes
				d[ARMS_X] = (float) (-Math.abs(Math.sin(t * pi * 2)) * 1.5);
				d[ARMS_Y] = (float) (-Math.abs(Math.sin(t * pi * 2)) * 3.0);
			}
			case 3 -> { // encolher os ombros
				d[ARMS_Y] = (float) (-env * 2.5);
				d[HEAD_Z] = (float) (env * 0.3);
			}
			case 4 -> d[HEAD_X] = (float) (Math.sin(t * pi * 4) * 0.35); // sim
			case 5 -> d[HEAD_Y] = (float) (Math.sin(t * pi * 4) * 0.45); // não
			case 6 -> { // apontar: braços para a frente e ficam
				double k = Math.sin(Math.min(t * 4, 1) * pi / 2) * Math.min(1, (1 - t) * 6);
				d[ARMS_X] = (float) (-k * 1.1);
				d[ARMS_Y] = (float) (-k * 1.5);
				d[HEAD_Y] = (float) (k * 0.2);
			}
			case 7 -> { // bater palmas: braços meio levantados, a bater depressa
				double clap = Math.abs(Math.sin(t * pi * 8));
				d[ARMS_X] = (float) (-env * (0.7 + clap * 0.3));
				d[ARMS_Y] = (float) (-env * (1.5 + clap * 1.0));
			}
			case 8 -> { // martelar: o bloco dos braços sobe e desce com força, a cabeça acompanha o golpe
				double hit = Math.max(0, Math.sin(t * pi * 6));
				d[ARMS_X] = (float) (-hit * 0.9 * env);
				d[ARMS_Y] = (float) (-hit * 2.0 * env);
				d[HEAD_X] = (float) (0.25 * env);
			}
			case 9 -> { // comer: braços à boca, a cabeça baixa e mastiga
				d[ARMS_X] = (float) (-env * 0.8);
				d[ARMS_Y] = (float) (-env * 2.0);
				d[HEAD_X] = (float) (env * (0.25 + Math.sin(t * pi * 10) * 0.08));
			}
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
