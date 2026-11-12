package com.tcordeiro.vilas.logic;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Regras dos terrenos (lógica pura, testada com JUnit). Um terreno é um quadrado de {@link #SIZE}×{@link #SIZE} blocos (toda a
 * altura do mundo), com o jogador no meio: x de px−8 a px+7 (o mesmo em z).
 */
public final class LotRules {
	private LotRules() {}

	public static final int SIZE = 16;
	/** Distância mínima (blocos) entre o terreno e a Mesa do Prefeito: a praça da Prefeitura não se vende. */
	public static final int CIVIC_MARGIN = 8;

	/** Retângulo (inclusivo) no plano x/z. */
	public record Area(int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		public boolean overlaps(Area o) {
			return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
		}
	}

	/** O terreno de 16×16 com o jogador em (px, pz). */
	public static Area around(int px, int pz) {
		int half = SIZE / 2;
		return new Area(px - half, pz - half, px - half + SIZE - 1, pz - half + SIZE - 1);
	}

	/** Os 4 cantos do terreno estão dentro do raio da vila (distância horizontal ao centro)? */
	public static boolean insideVillage(Area a, int cx, int cz, int radius) {
		long r2 = (long) radius * radius;
		int[][] corners = {{a.minX, a.minZ}, {a.minX, a.maxZ}, {a.maxX, a.minZ}, {a.maxX, a.maxZ}};
		for (int[] c : corners) {
			long dx = c[0] - cx, dz = c[1] - cz;
			if (dx * dx + dz * dz > r2) return false;
		}
		return true;
	}

	/** O terreno fica longe o bastante da Mesa do Prefeito? */
	public static boolean awayFromDesk(Area a, int deskX, int deskZ) {
		Area civic = new Area(deskX - CIVIC_MARGIN, deskZ - CIVIC_MARGIN, deskX + CIVIC_MARGIN, deskZ + CIVIC_MARGIN);
		return !a.overlaps(civic);
	}

	public static boolean overlapsAny(Area a, Collection<Area> others) {
		for (Area o : others) if (a.overlaps(o)) return true;
		return false;
	}

	/**
	 * REGRA DE SEGURANÇA: pode este jogador partir/pôr blocos neste terreno? Só o dono e os amigos que ele adicionou.
	 * (O {@code actor} pode ser o jogador falso de uma máquina do Industrial, com o UUID do dono da máquina.)
	 */
	public static boolean mayModify(UUID owner, Set<UUID> friends, UUID actor) {
		if (actor == null) return false;
		return actor.equals(owner) || friends.contains(actor);
	}

	/** Preço base em esmeraldas por nível (Aldeia, Vila, Cidade). */
	public static final int[] BASE_PRICE = {12, 20, 32};

	/**
	 * Preço em esmeraldas: o preço base do nível, com desconto pela reputação do jogador nesta vila (1 esmeralda a cada 25 pontos,
	 * no máximo metade do preço). Nunca menos de 1.
	 */
	public static int price(int level, int reputation) {
		int base = BASE_PRICE[VillageRules.clamp(level)];
		int discount = Math.min(Math.max(reputation, 0) / 25, base / 2);
		return Math.max(1, base - discount);
	}
}
