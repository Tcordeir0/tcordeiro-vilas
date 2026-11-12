package com.tcordeiro.vilas.logic;

/**
 * Os construtores (lógica pura, testada com JUnit): com alguém a ver, põem os blocos UM a UM ({@link #STEP_TICKS} entre
 * cada); sem ninguém perto, o que falta aparece de uma vez depois da noite (no máximo {@link #OVERNIGHT_MAX} blocos por noite).
 */
public final class BuildRules {
	private BuildRules() {}

	/** Ticks entre dois blocos postos à vista (1 s). */
	public static final long STEP_TICKS = 20;
	/** Distância (blocos) a que o construtor põe um bloco. */
	public static final double REACH = 4.0;
	/** Ticks sem chegar perto até o construtor «saltar» para lá (preso atrás de uma casa). */
	public static final long STUCK_TICKS = 200;
	/** Blocos por noite, no máximo (uma muralha grande demora várias noites). */
	public static final int OVERNIGHT_MAX = 4096;
	/** Distância (blocos) a que um jogador «está a ver» a obra. */
	public static final int WATCH_RADIUS = 64;

	/** Quantos blocos põe agora: com alguém a ver, 1 se já passou o intervalo; sem ninguém, 0 (esperam pela noite). */
	public static int blocksNow(boolean watched, long lastStep, long now) {
		if (!watched) return 0;
		return lastStep < 0 || now - lastStep >= STEP_TICKS ? 1 : 0;
	}

	/** O construtor está preso? */
	public static boolean stuck(long walkingSince, long now) {
		return walkingSince >= 0 && now - walkingSince >= STUCK_TICKS;
	}
}
