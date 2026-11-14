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

	/** Índices de {@link #deltas}: rotações da cabeça (x, y, z), dos braços (x, z), a subida dos braços (y, em píxeis; negativo = para cima, como no modelo do jogo), a largura dos braços (soma à escala x: abrir/fechar) e o salto do corpo todo (y, píxeis). */
	public static final int HEAD_X = 0, HEAD_Y = 1, HEAD_Z = 2, ARMS_X = 3, ARMS_Z = 4, ARMS_Y = 5, ARMS_SX = 6, JUMP = 7;

	/**
	 * 0.5.0 — O gesto como SOMA por cima da animação que já lá está (do Fresh Animations ou do jogo): o Vilas mexe só um pouco na
	 * cabeça e no bloco dos braços, no fim da animação de cada fotograma. {@code t} vai de 0 a 1. Fora dos gestos: tudo 0.
	 * 1 acenar, 2 festejar, 3 encolher os ombros, 4 sim, 5 não, 6 apontar, 7 bater palmas, 8 martelar, 9 comer.
	 */
	public static float[] deltas(int code, float t) {
		float[] d = new float[8];
		if (t <= 0 || t >= 1) return d;
		double pi = Math.PI, env = Math.sin(t * pi); // sobe e desce suave
		// os braços do aldeão são UMA peça (arms), presa à altura dos ombros: rx negativo levanta-os (−0,75 é a pose normal);
		// ty negativo sobe-os. Os valores foram escolhidos para se verem de frente e de três quartos.
		switch (code) {
			case 1 -> { // acenar: braços acima da cabeça, a balançar de um lado para o outro 3 vezes
				d[ARMS_X] = (float) (-env * 2.4);
				d[ARMS_Y] = (float) (-env * 3);
				d[ARMS_Z] = (float) (Math.sin(t * pi * 6) * 0.6 * env);
			}
			case 2 -> { // festejar: dois saltinhos com os braços no ar
				d[ARMS_X] = (float) (-env * 2.6);
				d[ARMS_Y] = (float) (-env * 3);
				d[JUMP] = (float) (-Math.abs(Math.sin(t * pi * 2)) * 4);
			}
			case 3 -> { // encolher os ombros: os braços sobem bem e a cabeça inclina
				d[ARMS_Y] = (float) (-env * 4);
				d[ARMS_X] = (float) (-env * 0.4);
				d[HEAD_Z] = (float) (env * 0.35);
			}
			case 4 -> d[HEAD_X] = (float) (Math.sin(t * pi * 6) * 0.6 * Math.min(1, env * 3)); // sim
			case 5 -> d[HEAD_Y] = (float) (Math.sin(t * pi * 6) * 0.8 * Math.min(1, env * 3)); // não
			case 6 -> { // apontar: braços esticados para a frente e ficam
				double k = Math.sin(Math.min(t * 4, 1) * pi / 2) * Math.min(1, (1 - t) * 6);
				d[ARMS_X] = (float) (-k * 1.6);
				d[ARMS_Y] = (float) (-k * 2);
				d[HEAD_Y] = (float) (k * 0.25);
			}
			case 7 -> { // bater palmas: braços à frente do peito, a abrir e a fechar 5 vezes
				d[ARMS_X] = (float) (-env * 1.4);
				d[ARMS_SX] = (float) (Math.sin(t * pi * 10) * 0.35 * Math.min(1, env * 3));
			}
			case 8 -> { // martelar: os braços sobem e descem com força, 3 golpes
				d[ARMS_X] = (float) (-env * (1.0 + Math.max(0, Math.sin(t * pi * 6)) * 1.4));
				d[HEAD_X] = (float) (env * 0.3);
			}
			case 9 -> { // comer: braços à boca, a cabeça baixa e mastiga
				d[ARMS_X] = (float) (-env * 1.6);
				d[ARMS_Y] = (float) (-env * 2);
				d[HEAD_X] = (float) (env * (0.35 + Math.sin(t * pi * 12) * 0.12));
			}
			case 10 -> { // 0.6 crises — luto: cabeça baixa e os braços juntos à frente, quietos
				d[HEAD_X] = (float) (env * 0.55);
				d[ARMS_X] = (float) (-env * 0.35);
				d[ARMS_SX] = (float) (-env * 0.1);
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
