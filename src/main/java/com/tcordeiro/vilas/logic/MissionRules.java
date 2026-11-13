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

	/** Modelo de missão: tipo, alvo (id de item/bloco), quantidade, esmeraldas, reputação, escalão (1–3). */
	public record Template(Kind kind, String target, int count, int emeralds, int reputation, int tier) {}

	/*
	 * 0.3.0 (A1): sem pedregulho; recompensas revistas para nunca passarem do valor de mercado ×1,25 (ver MARKET_CENTS e
	 * fairReward) nem do que custa comprar os itens aos aldeões (noVillagerProfit). A reputação é o prémio principal.
	 */
	public static final List<Template> TEMPLATES = List.of(
			// trazer
			new Template(Kind.DELIVER, "minecraft:wheat", 24, 1, 10, 1),
			new Template(Kind.DELIVER, "minecraft:bread", 18, 1, 10, 1),
			new Template(Kind.DELIVER, "minecraft:white_wool", 24, 1, 10, 1),
			new Template(Kind.DELIVER, "minecraft:coal", 16, 1, 10, 1),
			new Template(Kind.DELIVER, "minecraft:carrot", 24, 1, 8, 1),
			new Template(Kind.DELIVER, "minecraft:potato", 24, 1, 8, 1),
			new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 2, 20, 2),
			new Template(Kind.DELIVER, "minecraft:pumpkin", 10, 2, 18, 2),
			new Template(Kind.DELIVER, "minecraft:honey_bottle", 10, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:glass", 32, 5, 18, 2),
			new Template(Kind.DELIVER, "minecraft:leather", 10, 2, 18, 2),
			new Template(Kind.DELIVER, "minecraft:gold_ingot", 10, 4, 30, 3),
			new Template(Kind.DELIVER, "minecraft:diamond", 2, 3, 40, 3),
			new Template(Kind.DELIVER, "minecraft:book", 12, 3, 28, 3),
			// construir/decorar (os blocos pagos ficam da vila: partidos, não devolvem nada)
			new Template(Kind.DECORATE, "minecraft:lantern", 6, 3, 12, 1),
			new Template(Kind.DECORATE, "minecraft:white_wool", 16, 1, 10, 1),
			new Template(Kind.DECORATE, "minecraft:hay_block", 8, 3, 12, 1),
			new Template(Kind.DECORATE, "minecraft:stone_bricks", 32, 2, 20, 2),
			new Template(Kind.DECORATE, "minecraft:glass_pane", 16, 1, 18, 2),
			new Template(Kind.DECORATE, "minecraft:lantern", 12, 5, 20, 2),
			new Template(Kind.DECORATE, "minecraft:bricks", 64, 6, 30, 3),
			new Template(Kind.DECORATE, "minecraft:lantern", 24, 8, 30, 3),
			// defender (só conta numa invasão a sério)
			new Template(Kind.DEFEND, INVADERS, 5, 5, 20, 1),
			new Template(Kind.DEFEND, INVADERS, 10, 9, 35, 2),
			new Template(Kind.DEFEND, INVADERS, 15, 14, 55, 3));

	/** O tipo de cada uma das 3 casas do quadro. */
	public static final Kind[] SLOTS = {Kind.DELIVER, Kind.DECORATE, Kind.DEFEND};

	/**
	 * 0.5.0 — A missão temporária «decorar a festa» (4.ª casa do quadro, só enquanto há festa marcada): blocos festivos a 24 blocos
	 * da Praça. Paga como as outras (fundo das missões e tesouraria, teto do dia, uma vez por jogador) e não mexe no lote de 4 dias.
	 */
	public static final List<Template> FESTA_DECOR = List.of(
			new Template(Kind.DECORATE, "minecraft:lantern", 6, 2, 10, 1),
			new Template(Kind.DECORATE, "minecraft:candle", 8, 2, 10, 1),
			new Template(Kind.DECORATE, "minecraft:red_wool", 12, 1, 8, 1),
			new Template(Kind.DECORATE, "minecraft:yellow_wool", 12, 1, 8, 1));

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
			"minecraft:oak_log", 8, // Vendedor ambulante
			"minecraft:bricks", 10); // Pedreiro

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

	/**
	 * 0.3.0 (A1) — valor de mercado de cada item das missões, em cêntimos (1 esmeralda = 4000): o MELHOR que um jogador consegue
	 * por ele — o que um aldeão paga (vanilla) ou o Balcão de Câmbio do CubCoin; para o que ninguém compra, o que custa comprá-lo.
	 */
	public static final java.util.Map<String, Integer> MARKET_CENTS = java.util.Map.ofEntries(
			java.util.Map.entry("minecraft:wheat", 200), // Agricultor: 20 por 1 esmeralda
			java.util.Map.entry("minecraft:bread", 667), // ninguém compra; o Padeiro vende 6 por 1 esmeralda
			java.util.Map.entry("minecraft:white_wool", 222), // Pastor: 18 por 1 esmeralda
			java.util.Map.entry("minecraft:coal", 267), // Armeiro/Ferreiro: 15 por 1 esmeralda
			java.util.Map.entry("minecraft:carrot", 182), // Agricultor: 22 por 1 esmeralda
			java.util.Map.entry("minecraft:potato", 154), // Agricultor: 26 por 1 esmeralda
			java.util.Map.entry("minecraft:iron_ingot", 1000), // Armeiro: 4 por 1 esmeralda
			java.util.Map.entry("minecraft:pumpkin", 667), // Agricultor: 6 por 1 esmeralda
			java.util.Map.entry("minecraft:honey_bottle", 2000), // ninguém compra; o Apicultor vende 2 por 1 esmeralda
			java.util.Map.entry("minecraft:glass", 1000), // ninguém compra; o Bibliotecário vende 4 por 1 esmeralda
			java.util.Map.entry("minecraft:leather", 667), // Curtidor: 6 por 1 esmeralda
			java.util.Map.entry("minecraft:gold_ingot", 1333), // Clérigo: 3 por 1 esmeralda (Balcão: 8 CB)
			java.util.Map.entry("minecraft:diamond", 6000), // Balcão de Câmbio: 60 CB
			java.util.Map.entry("minecraft:book", 1000), // Bibliotecário: 4 por 1 esmeralda
			java.util.Map.entry("minecraft:lantern", 4000), // ninguém compra; o Bibliotecário vende 1 por 1 esmeralda
			java.util.Map.entry("minecraft:hay_block", 1800), // 9 trigos
			java.util.Map.entry("minecraft:stone_bricks", 200), // Pedreiro: 20 pedras por 1 esmeralda
			java.util.Map.entry("minecraft:glass_pane", 375), // 6 vidros dão 16 vidraças
			java.util.Map.entry("minecraft:bricks", 400), // ninguém compra; o Pedreiro vende 10 por 1 esmeralda
			// 0.3.0 — pedidos dos aldeões
			java.util.Map.entry("minecraft:paper", 167), // Bibliotecário/Cartógrafo: 24 por 1 esmeralda
			java.util.Map.entry("minecraft:string", 200), // Pescador: 20 por 1 esmeralda
			java.util.Map.entry("minecraft:chicken", 286), // Talhante: 14 por 1 esmeralda
			java.util.Map.entry("minecraft:rotten_flesh", 125), // Clérigo: 32 por 1 esmeralda
			java.util.Map.entry("minecraft:clay_ball", 400), // Pedreiro: 10 por 1 esmeralda
			java.util.Map.entry("minecraft:stick", 125), // Flecheiro: 32 por 1 esmeralda
			java.util.Map.entry("minecraft:glass_bottle", 1000)); // ninguém compra: uma garrafa custa um vidro (Bibliotecário: 4 vidros por esmeralda)

	/** Teto da recompensa: valor de mercado ×1,25 (em centésimos para as contas serem inteiras). */
	public static final int FAIR_PERCENT = 125;

	/** Valor de mercado do que a missão pede (cêntimos), ou −1 sem preço (defesa). */
	public static long marketValue(Template t) {
		Integer per = MARKET_CENTS.get(t.target);
		if (per == null || t.kind == Kind.DEFEND) return -1;
		return (long) per * t.count;
	}

	/**
	 * REGRA DE SEGURANÇA (economia, 0.3.0): a recompensa ({@code emeralds} ao preço {@code emeraldCents}) nunca passa do valor de
	 * mercado ×1,25 do que se entrega — senão trazer itens ao Prefeito era a melhor forma de vender. Itens sem preço conhecido não
	 * passam (todos os itens das missões têm preço; a defesa não entrega itens).
	 */
	public static boolean fairReward(Template t, long emeraldCents) {
		if (t.kind == Kind.DEFEND) return true;
		long market = marketValue(t);
		if (market < 0) return false;
		return (long) t.emeralds * emeraldCents * 100 <= market * FAIR_PERCENT;
	}

	/** 0.3.0: o quadro (e os pedidos dos aldeões) renova-se todo a cada tantos dias de jogo — um «lote». */
	public static final int BATCH_DAYS = 4;

	/** O lote de um dia de jogo. */
	public static long batchOf(long day, int batchDays) {
		return Math.floorDiv(day, Math.max(1, batchDays));
	}

	/** Ticks até ao próximo lote (para o quadro mostrar quanto falta). */
	public static long ticksToNextBatch(long gameTime, long dayTicks, int batchDays) {
		long len = dayTicks * Math.max(1, batchDays);
		return len - Math.floorMod(gameTime, len);
	}

	/**
	 * REGRA DE SEGURANÇA (economia, 0.3.0): a tesouraria paga esta missão (ou pedido)? Só se o jogador ainda não a fez neste lote,
	 * se houver dinheiro (fundo das missões + tesouraria) e se a vila não passar o teto do dia ({@code dailyCap}; 0 = sem teto).
	 * Com cada missão uma vez por jogador por lote e cada recompensa ≤ mercado ×1,25, um jogador nunca tira de uma vila, num lote,
	 * mais do que 1,25× o valor do que lhe entregou.
	 */
	public static boolean mayPay(long treasury, long paidToday, long dailyCap, boolean alreadyDone, long reward) {
		if (reward < 0 || alreadyDone) return false;
		if (treasury < reward) return false;
		return dailyCap <= 0 || paidToday + reward <= dailyCap;
	}

	/** REGRA (0.3.0): a defesa só conta numa invasão a sério — um invasor da invasão que está nesta vila. */
	public static boolean defenseCounts(boolean victimInRaid, boolean raidHere, boolean raidOver, boolean fromSpawner) {
		return victimInRaid && raidHere && !raidOver && !fromSpawner;
	}

	/**
	 * 0.3.0 — Pedidos dos aldeões comuns, pela profissão (vanilla e deste mod): o que pedem e quanto pagam. Todos cumprem
	 * {@link #fairReward} (≤ mercado ×1,25) e nenhum se compra aos aldeões mais barato do que paga ({@link #noVillagerProfit}).
	 */
	public static final java.util.Map<String, List<Template>> REQUESTS = java.util.Map.ofEntries(
			java.util.Map.entry("armorer", List.of(new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 2, 5, 1),
					new Template(Kind.DELIVER, "minecraft:coal", 16, 1, 5, 1))),
			java.util.Map.entry("weaponsmith", List.of(new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 2, 5, 1),
					new Template(Kind.DELIVER, "minecraft:coal", 16, 1, 5, 1))),
			java.util.Map.entry("toolsmith", List.of(new Template(Kind.DELIVER, "minecraft:iron_ingot", 8, 2, 5, 1),
					new Template(Kind.DELIVER, "minecraft:coal", 16, 1, 5, 1))),
			java.util.Map.entry("farmer", List.of(new Template(Kind.DELIVER, "minecraft:wheat", 24, 1, 5, 1),
					new Template(Kind.DELIVER, "minecraft:potato", 24, 1, 5, 1), new Template(Kind.DELIVER, "minecraft:carrot", 24, 1, 5, 1))),
			java.util.Map.entry("librarian", List.of(new Template(Kind.DELIVER, "minecraft:paper", 24, 1, 5, 1),
					new Template(Kind.DELIVER, "minecraft:book", 12, 3, 5, 1))),
			java.util.Map.entry("cartographer", List.of(new Template(Kind.DELIVER, "minecraft:paper", 24, 1, 5, 1))),
			java.util.Map.entry("fisherman", List.of(new Template(Kind.DELIVER, "minecraft:string", 20, 1, 5, 1))),
			java.util.Map.entry("shepherd", List.of(new Template(Kind.DELIVER, "minecraft:white_wool", 24, 1, 5, 1))),
			java.util.Map.entry("butcher", List.of(new Template(Kind.DELIVER, "minecraft:chicken", 14, 1, 5, 1))),
			java.util.Map.entry("leatherworker", List.of(new Template(Kind.DELIVER, "minecraft:leather", 10, 2, 5, 1))),
			java.util.Map.entry("cleric", List.of(new Template(Kind.DELIVER, "minecraft:rotten_flesh", 32, 1, 5, 1))),
			java.util.Map.entry("mason", List.of(new Template(Kind.DELIVER, "minecraft:clay_ball", 10, 1, 5, 1))),
			java.util.Map.entry("fletcher", List.of(new Template(Kind.DELIVER, "minecraft:stick", 32, 1, 5, 1))),
			java.util.Map.entry("baker", List.of(new Template(Kind.DELIVER, "minecraft:wheat", 24, 1, 5, 1))),
			java.util.Map.entry("beekeeper", List.of(new Template(Kind.DELIVER, "minecraft:glass_bottle", 12, 1, 5, 1))));

	/** Um em cada tantos aldeões com profissão faz um pedido em cada lote. */
	public static final int REQUEST_ONE_IN = 4;

	/** Quantos aldeões (de {@code withJob} com profissão que pede) fazem pedidos neste lote: 1 em cada 4, arredondado para cima. */
	public static int requesters(int withJob) {
		return withJob <= 0 ? 0 : (withJob + REQUEST_ONE_IN - 1) / REQUEST_ONE_IN;
	}

	/** O pedido de um aldeão desta profissão ({@code roll} ≥ 0 escolhe), ou null se a profissão não pede nada. */
	public static Template request(String profession, int roll) {
		List<Template> list = REQUESTS.get(profession);
		return list == null || list.isEmpty() ? null : list.get(Math.floorMod(roll, list.size()));
	}

	/** A missão está cumprida com {@code progress} feito? */
	public static boolean complete(Template t, int progress) {
		return progress >= t.count;
	}

	/**
	 * 0.3.0: a recompensa em cêntimos (sai da tesouraria). Com o CubCoin paga-se em CubCoins; sem ele, em esmeraldas inteiras —
	 * é o mesmo valor (até à 0.2.0 o CubCoin dava 10 CB por esmeralda POR CIMA das esmeraldas: acabou).
	 */
	public static long rewardCents(int emeralds, long emeraldCents) {
		return Math.max(0, emeralds) * Math.max(0, emeraldCents);
	}
}
