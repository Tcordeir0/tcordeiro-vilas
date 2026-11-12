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
			new Template(Kind.DELIVER, "minecraft:bread", 18, 3, 10, 1), // 0.1.1: eram 10 (comprados ao Padeiro por 2 esmeraldas, pagavam 3)
			new Template(Kind.DELIVER, "minecraft:white_wool", 12, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:oak_log", 24, 3, 10, 1),
			new Template(Kind.DELIVER, "minecraft:carrot", 24, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:potato", 24, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:cobblestone", 64, 2, 8, 1),
			new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 6, 20, 2),
			new Template(Kind.DELIVER, "minecraft:pumpkin", 10, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:honey_bottle", 10, 5, 18, 2), // 0.1.1: eram 4 (2 esmeraldas no Apicultor, pagavam 5)
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
	 * Escolhe a missão para a casa {@code slot}: {@code roll} é um número aleatório ≥ 0. Evita repetir o ALVO (item/bloco) do
	 * modelo que estava lá ({@code avoid}) — não só o mesmo modelo: «6 lanternas» não é seguida de «12 lanternas» — se houver
	 * alternativa.
	 */
	public static Template pick(int slot, int tier, int roll, Set<Template> avoid) {
		List<Template> list = eligible(slot, tier);
		List<Template> fresh = new ArrayList<>(list);
		for (Template a : avoid) fresh.removeIf(t -> t.target.equals(a.target));
		List<Template> from = fresh.isEmpty() ? list : fresh;
		return from.get(Math.floorMod(roll, from.size()));
	}

	/**
	 * Itens das missões que os aldeões VENDEM (vanilla e os vendedores deste mod): item → quantos dão por 1 esmeralda, no melhor
	 * caso. Uma troca nunca custa menos de 1 esmeralda, mesmo com descontos de reputação.
	 */
	public static final java.util.Map<String, Integer> VILLAGER_SALES = java.util.Map.of(
			"minecraft:bread", 6, // Padeiro (tvilas) e Agricultor
			"minecraft:honey_bottle", 2, // Apicultor (tvilas)
			"minecraft:glass", 4, // Bibliotecário
			"minecraft:lantern", 1, // Bibliotecário (o Taberneiro vende 2 por 4)
			"minecraft:white_wool", 1, // Pastor
			"minecraft:pumpkin", 1, // Vendedor ambulante
			"minecraft:oak_log", 8); // Vendedor ambulante

	/** Quanto custa, em esmeraldas, comprar aos aldeões tudo o que a missão pede (−1 = ninguém vende). */
	public static int villagerCost(Template t) {
		Integer per = VILLAGER_SALES.get(t.target);
		if (per == null || t.kind == Kind.DEFEND) return -1;
		return (t.count + per - 1) / per;
	}

	/**
	 * REGRA DE SEGURANÇA (economia): nenhuma missão paga mais esmeraldas do que custa comprar aos aldeões o que ela pede — senão
	 * comprar e entregar era dinheiro infinito (pão do Padeiro, mel do Apicultor).
	 */
	public static boolean noVillagerProfit(Template t) {
		int cost = villagerCost(t);
		return cost < 0 || t.emeralds <= cost;
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
