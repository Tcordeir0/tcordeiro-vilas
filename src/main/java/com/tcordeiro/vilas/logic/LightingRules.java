package com.tcordeiro.vilas.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Iluminação pública (lógica pura, testada com JUnit): escolhe sítios para postes ao lado das ruas, afastados uns dos outros
 * ({@link #SPACING}), no máximo {@link #MAX_POLES}, os mais perto do centro primeiro.
 */
public final class LightingRules {
	private LightingRules() {}

	public static final int SPACING = 10;
	public static final int MAX_POLES = 16;
	/** Raio (blocos) à volta da Mesa onde se procuram ruas. */
	public static final int SEARCH_RADIUS = 48;

	public record Spot(int x, int z) {}

	/** Os postes, a partir dos candidatos (já ordenados do centro para fora). */
	public static List<Spot> choose(List<Spot> candidates, int spacing, int max) {
		return choose(candidates, spacing, Integer.MAX_VALUE, max);
	}

	/**
	 * Como {@link #choose(List, int, int)}, e cada poste novo a no máximo {@code span} blocos de um já escolhido (o fio aéreo do
	 * Industrial não passa de 16).
	 */
	public static List<Spot> choose(List<Spot> candidates, int spacing, int span, int max) {
		List<Spot> out = new ArrayList<>();
		long s2 = (long) spacing * spacing, sp2 = span == Integer.MAX_VALUE ? Long.MAX_VALUE : (long) span * span;
		boolean grew = true;
		while (grew && out.size() < max) {
			grew = false;
			for (Spot c : candidates) {
				if (out.size() >= max) break;
				if (out.contains(c)) continue;
				boolean far = true, reach = out.isEmpty();
				for (Spot o : out) {
					long dx = c.x() - o.x(), dz = c.z() - o.z(), d2 = dx * dx + dz * dz;
					if (d2 < s2) {
						far = false;
						break;
					}
					if (d2 <= sp2) reach = true;
				}
				if (far && reach) {
					out.add(c);
					grew = true;
				}
			}
		}
		return out;
	}

	/** A madeira dos postes do Industrial pelo bioma (caminho do id do bioma); null = poste de ferro (deserto, badlands). */
	public static String woodFor(String biome) {
		if (biome.contains("desert") || biome.contains("badlands")) return null;
		for (String w : new String[] {"dark_oak", "pale_oak", "cherry", "mangrove", "bamboo", "birch", "jungle", "acacia", "spruce", "crimson", "warped"})
			if (biome.contains(w.replace("_oak", "")) || biome.contains(w)) return w;
		if (biome.contains("savanna")) return "acacia";
		if (biome.contains("taiga") || biome.contains("snow") || biome.contains("frozen") || biome.contains("grove")) return "spruce";
		return "oak";
	}

}
