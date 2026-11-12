package com.tcordeiro.vilas.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * A muralha encomendada quando a vila chega a Cidade (lógica pura, testada com JUnit): um quadrado à volta da Mesa do Prefeito,
 * com torres nos cantos e a meio dos lados (se o lado for comprido), erguido coluna a coluna pelos construtores.
 * <p>
 * REGRAS DE SEGURANÇA: só substitui ar, erva, flores e neve ({@link #mayReplace}); nunca mexe em terrenos (lotes); salta as
 * colunas assentes em chão que não é natural (blocos de jogador, ruas — que ficam como portões) e contorna-as tentando 1 e 2
 * blocos para fora ({@link #OUTWARD_TRIES}).
 */
public final class WallRules {
	private WallRules() {}

	public static final int HEIGHT = 3;
	public static final int TOWER_HEIGHT = 5;
	/** Metade do lado do quadrado (blocos), numa vila normal. */
	public static final int MAX_HALF = 48;
	/** Lado mínimo para ter torres a meio dos lados. */
	public static final int MID_TOWERS_FROM = 24;
	/** Tentativas para fora (0 = a coluna planeada, 1 e 2 = desvios). */
	public static final int OUTWARD_TRIES = 3;
	/** Custo por bloco posto (cêntimos), pago pela tesouraria à medida. */
	public static final long BLOCK_CENTS = 20;

	/** Uma coluna da muralha: posição, direção para fora (nx, nz) e se é torre. */
	public record Column(int x, int z, int nx, int nz, boolean tower) {}

	/** Metade do lado para uma vila de raio {@code radius} (cabe sempre dentro do território). */
	public static int half(int radius) {
		return Math.max(2, Math.min(MAX_HALF, radius / 2));
	}

	/** As colunas do perímetro, por ordem (lado norte, este, sul, oeste), sem repetir os cantos. */
	public static List<Column> plan(int cx, int cz, int half) {
		List<Column> out = new ArrayList<>();
		int x0 = cx - half, x1 = cx + half, z0 = cz - half, z1 = cz + half;
		for (int x = x0; x < x1; x++) out.add(col(x, z0, 0, -1, x, x0, x1, half));
		for (int z = z0; z < z1; z++) out.add(col(x1, z, 1, 0, z, z0, z1, half));
		for (int x = x1; x > x0; x--) out.add(col(x, z1, 0, 1, x, x0, x1, half));
		for (int z = z1; z > z0; z--) out.add(col(x0, z, -1, 0, z, z0, z1, half));
		return out;
	}

	private static Column col(int x, int z, int nx, int nz, int along, int a0, int a1, int half) {
		boolean corner = along == a0 || along == a1;
		boolean mid = half >= MID_TOWERS_FROM && Math.abs(along - (a0 + a1) / 2) <= 1;
		boolean nearCorner = half >= MID_TOWERS_FROM && (Math.abs(along - a0) <= 1 || Math.abs(along - a1) <= 1);
		return new Column(x, z, nx, nz, corner || mid || nearCorner);
	}

	/** REGRA DE SEGURANÇA: a muralha pode pôr um bloco aqui? Só por cima de ar, erva, flores ou neve, e nunca num terreno. */
	public static boolean mayReplace(boolean air, boolean grassOrFlower, boolean snowLayer, boolean inLot) {
		if (inLot) return false;
		return air || grassOrFlower || snowLayer;
	}

	/** Altura desta coluna. */
	public static int height(Column c) {
		return c.tower() ? TOWER_HEIGHT : HEIGHT;
	}
}
