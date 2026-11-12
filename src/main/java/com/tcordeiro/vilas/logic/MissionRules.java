package com.tcordeiro.vilas.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Missões da vila (lógica pura, testada com JUnit). Cada vila tem 3 missões ativas, uma de cada tipo:
 * <ul>
 * <li>{@link Kind#DELIVER} — trazer N itens ao Prefeito (tira-os do inventário ao entregar);</li>
 * <li>{@link Kind#DECORATE} — pôr N blocos dentro da vila (só contam os que ainda lá estão ao entregar);</li>
 * <li>{@link Kind#DEFEND} — matar N monstros/invasores dentro da vila DURANTE um alarme.</li>
 * </ul>
 * O escalão sobe com o nível da vila: missões maiores, mais esmeraldas e mais reputação.
 */
public final class MissionRules {
	private MissionRules() {}

	public enum Kind {
		DELIVER, DECORATE, DEFEND;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Alvo das missões de defesa (qualquer monstro ou invasor). */
	public static final String INVADERS = "invaders";
	/** Bónus em CubCoins por esmeralda paga (em cêntimos: 10 CB), só com o CubCoin instalado. */
	public static final long CUBCOIN_CENTS_PER_EMERALD = 1000;

	/** Modelo de missão: tipo, alvo (id de item/bloco), quantidade, esmeraldas, reputação, escalão (1–3). */
	public record Template(Kind kind, String target, int count, int emeralds, int reputation, int tier) {}

	public static final List<Template> TEMPLATES = List.of(
			// trazer
			new Template(Kind.DELIVER, "minecraft:wheat", 24, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:bread", 10, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:white_wool", 12, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:oak_log", 24, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:carrot", 24, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:potato", 24, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:cobblestone", 64, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 6, 20, 2),
			new Template(Kind.DELIVER, "minecraft:pumpkin", 10, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:honey_bottle", 4, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:glass", 32, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:leather", 10, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:gold_ingot", 10, 9, 30, 3),
			new Template(Kind.DELIVER, "minecraft:diamond", 2, 12, 40, 3),
			new Template(Kind.DELIVER, "minecraft:book", 12, 8, 28, 3),
			// construir/decorar
			new Template(Kind.DECORATE, "minecraft:lantern", 6, 3, 12, 1),
			new Template(Kind.DECORATE, "minecraft:oak_fence", 16, 2, 10, 1),
			new Template(Kind.DECORATE, "minecraft:hay_block", 8, 3, 12, 1),
			new Template(Kind.DECORATE, "minecraft:stone_bricks", 32, 5, 20, 2),
			new Template(Kind.DECORATE, "minecraft:glass_pane", 16, 4, 18, 2),
			new Template(Kind.DECORATE, "minecraft:lantern", 12, 5, 20, 2),
			new Template(Kind.DECORATE, "minecraft:bricks", 64, 8, 30, 3),
			new Template(Kind.DECORATE, "minecraft:lantern", 24, 8, 30, 3),
			// defender
			new Template(Kind.DEFEND, INVADERS, 5, 5, 20, 1),
			new Template(Kind.DEFEND, INVADERS, 10, 9, 35, 2),
			new Template(Kind.DEFEND, INVADERS, 15, 14, 55, 3));

	/** O tipo de cada uma das 3 casas do quadro. */
	public static final Kind[] SLOTS = {Kind.DELIVER, Kind.DECORATE, Kind.DEFEND};

	/** Modelos possíveis numa casa: do tipo da casa, do escalão da vila ou do anterior (uma cidade não pede trigo). */
	public static List<Template> eligible(int slot, int tier) {
		Kind kind = SLOTS[slot];
		List<Template> out = new ArrayList<>();
		for (Template t : TEMPLATES)
			if (t.kind == kind && t.tier <= tier && t.tier >= tier - 1) out.add(t);
		return out;
	}

	/**
	 * Escolhe a missão para a casa {@code slot}: {@code roll} é um número aleatório ≥ 0. Evita repetir o modelo que estava lá
	 * ({@code avoid}), se houver alternativa.
	 */
	public static Template pick(int slot, int tier, int roll, Set<Template> avoid) {
		List<Template> list = eligible(slot, tier);
		List<Template> fresh = new ArrayList<>(list);
		fresh.removeAll(avoid);
		List<Template> from = fresh.isEmpty() ? list : fresh;
		return from.get(Math.floorMod(roll, from.size()));
	}

	/** A missão está cumprida com {@code progress} feito? */
	public static boolean complete(Template t, int progress) {
		return progress >= t.count;
	}

	/** Bónus em CubCoins (cêntimos) para uma missão que paga {@code emeralds}. */
	public static long cubcoinBonus(int emeralds) {
		return emeralds * CUBCOIN_CENTS_PER_EMERALD;
	}
}
