package com.tcordeiro.vilas.guide;

import java.util.List;

/**
 * Páginas do guia das Vilas, lidas pelo separador «Guias» do Receitário por reflexão ({@code PAGES}; cada página com
 * {@code key()}, {@code image()} e {@code recipes()}; cada receita com {@code result()}, {@code count()} e {@code cell(linha, coluna)}).
 * Os textos estão nas traduções: guide.tvilas.&lt;página&gt;.title / .text.
 * <p>
 * GERADO por tools/gen_data.py: não editar à mão.
 */
public final class VilasGuide {
	private VilasGuide() {}

	/** Receita numa página: id, resultado, quantidade e 3 linhas de 3 casas (id de item, «#tag» ou null). */
	public record Recipe(String id, String result, int count, String[][] grid) {
		public String cell(int row, int col) {
			return row >= 0 && row < grid.length && col >= 0 && col < grid[row].length ? grid[row][col] : null;
		}
	}

	/**
	 * Página: chave de tradução, imagem opcional (textures/gui/guide/&lt;img&gt;.png), receitas e o mod de que depende (null =
	 * sempre). As páginas de um mod que não está instalado não entram em {@link #PAGES}.
	 */
	public record Page(String key, String image, List<Recipe> recipes, String requires) {
		public boolean available() {
			return requires == null || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(requires);
		}
	}

	static final Recipe MAYOR_DESK = new Recipe("tvilas:mayor_desk", "tvilas:mayor_desk", 1,
			new String[][] {{"#minecraft:planks", "#minecraft:planks", "#minecraft:planks"}, {"minecraft:book", "minecraft:emerald", "minecraft:book"}, {"#minecraft:planks", null, "#minecraft:planks"}});
	static final Recipe BAKER_OVEN = new Recipe("tvilas:baker_oven", "tvilas:baker_oven", 1,
			new String[][] {{"minecraft:bricks", "minecraft:bricks", "minecraft:bricks"}, {"minecraft:bricks", "minecraft:furnace", "minecraft:bricks"}, {"minecraft:bricks", "minecraft:bricks", "minecraft:bricks"}});
	static final Recipe FLOWER_STALL = new Recipe("tvilas:flower_stall", "tvilas:flower_stall", 1,
			new String[][] {{"#minecraft:wool", "#minecraft:wool", "#minecraft:wool"}, {"#minecraft:wooden_slabs", "minecraft:flower_pot", "#minecraft:wooden_slabs"}, {null, null, null}});
	static final Recipe TAVERN_COUNTER = new Recipe("tvilas:tavern_counter", "tvilas:tavern_counter", 1,
			new String[][] {{"#minecraft:wooden_slabs", "#minecraft:wooden_slabs", "#minecraft:wooden_slabs"}, {"#minecraft:planks", "minecraft:barrel", "#minecraft:planks"}, {null, null, null}});
	static final Recipe APIARY_BENCH = new Recipe("tvilas:apiary_bench", "tvilas:apiary_bench", 1,
			new String[][] {{"minecraft:honeycomb", "minecraft:honeycomb", "minecraft:honeycomb"}, {"#minecraft:planks", "minecraft:glass_bottle", "#minecraft:planks"}, {null, null, null}});
	static final Recipe GUIDE = new Recipe("tvilas:guide", "minecraft:written_book", 1,
			new String[][] {{"minecraft:book", "minecraft:wheat", null}, {null, null, null}, {null, null, null}});
	static final Recipe MECHANIC_BENCH = new Recipe("tvilas:mechanic_bench", "tvilas:mechanic_bench", 1,
			new String[][] {{"tindustrial:copper_wire", "tindustrial:copper_wire", "tindustrial:copper_wire"}, {"minecraft:iron_ingot", "#minecraft:planks", "minecraft:iron_ingot"}, {null, null, null}});
	static final Recipe ALLOY_ANVIL = new Recipe("tvilas:alloy_anvil", "tvilas:alloy_anvil", 1,
			new String[][] {{"tforja:bronze_ingot", "tforja:bronze_ingot", "tforja:bronze_ingot"}, {null, "minecraft:iron_ingot", null}, {"minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot"}});
	static final Recipe CARPENTER_BENCH = new Recipe("tvilas:carpenter_bench", "tvilas:carpenter_bench", 1,
			new String[][] {{"#minecraft:wooden_slabs", "#minecraft:wooden_slabs", "#minecraft:wooden_slabs"}, {"#minecraft:planks", "tmoveis:oak_chair", "#minecraft:planks"}, {null, null, null}});
	static final Recipe CHANGER_COUNTER = new Recipe("tvilas:changer_counter", "tvilas:changer_counter", 1,
			new String[][] {{"cubcoin:note_1", "cubcoin:note_1", "cubcoin:note_1"}, {"#minecraft:planks", "minecraft:emerald", "#minecraft:planks"}, {null, null, null}});

	/** Todas as páginas (o livro escrito leva-as todas; as dos mods dizem «só com o mod»). */
	public static final List<Page> ALL = List.of(
			new Page("cover", null, List.of(), null),
			new Page("mayor", null, List.of(MAYOR_DESK), null),
			new Page("town_hall", null, List.of(), null),
			new Page("territory", null, List.of(), null),
			new Page("reborn", null, List.of(), null),
			new Page("talk", null, List.of(), null),
			new Page("missions", null, List.of(), null),
			new Page("rewards", null, List.of(), null),
			new Page("defend", null, List.of(), null),
			new Page("lots", null, List.of(), null),
			new Page("lot_rules", null, List.of(), null),
			new Page("friends", null, List.of(), null),
			new Page("levels", null, List.of(), null),
			new Page("unlocks", null, List.of(), null),
			new Page("alarm", null, List.of(), null),
			new Page("alarm_warning", null, List.of(), null),
			new Page("taxes", null, List.of(), null),
			new Page("treasury", null, List.of(), null),
			new Page("debts", null, List.of(), null),
			new Page("market", null, List.of(), null),
			new Page("baker", null, List.of(BAKER_OVEN), null),
			new Page("florist", null, List.of(FLOWER_STALL), null),
			new Page("innkeeper", null, List.of(TAVERN_COUNTER), null),
			new Page("beekeeper", null, List.of(APIARY_BENCH), null),
			new Page("guide_book", null, List.of(GUIDE), null),
			new Page("news", null, List.of(), null),
			new Page("builders", null, List.of(), null),
			new Page("builders_night", null, List.of(), null),
			new Page("golem_build", null, List.of(), null),
			new Page("wall", null, List.of(), null),
			new Page("wall_safety", null, List.of(), null),
			new Page("wall_cancel", null, List.of(), null),
			new Page("lighting", null, List.of(), null),
			new Page("lighting_industrial", null, List.of(), null),
			new Page("tax_changes", null, List.of(), null),
			new Page("freeze", null, List.of(), null),
			new Page("pay_debt", null, List.of(), null),
			new Page("merchant", null, List.of(), null),
			new Page("merchant_diff", null, List.of(), null),
			new Page("tavern", null, List.of(), null),
			new Page("plaza", null, List.of(), null),
			new Page("furniture", null, List.of(), null),
			new Page("mod_vendors", null, List.of(), null),
			new Page("mechanic", null, List.of(MECHANIC_BENCH), "tindustrial"),
			new Page("alloy_smith", null, List.of(ALLOY_ANVIL), "tforja"),
			new Page("carpenter", null, List.of(CARPENTER_BENCH), "tmoveis"),
			new Page("money_changer", null, List.of(CHANGER_COUNTER), "cubcoin"),
			new Page("coin_trades", null, List.of(), "cubcoin"),
			new Page("coin_change", null, List.of(), "cubcoin"),
			new Page("coin_more", null, List.of(), "cubcoin"),
			new Page("mission_limits", null, List.of(), null),
			new Page("mission_fund", null, List.of(), null),
			new Page("villager_requests", null, List.of(), null),
			new Page("request_screen", null, List.of(), null),
			new Page("request_money", null, List.of(), null),
			new Page("growth", null, List.of(), null),
			new Page("growth_house", null, List.of(), null),
			new Page("mission_fair", null, List.of(), null),
			new Page("decor_village", null, List.of(), null),
			new Page("old_villages", null, List.of(), null),
			new Page("civic_site", null, List.of(), null),
			new Page("civic_area", null, List.of(), null),
			new Page("lot_animals", null, List.of(), null),
			new Page("mayor_taken", null, List.of(), null),
			new Page("life", null, List.of(), null),
			new Page("balloons", null, List.of(), null),
			new Page("evening", null, List.of(), null),
			new Page("sitting", null, List.of(), null),
			new Page("double_bed", null, List.of(), null),
			new Page("weather", null, List.of(), null),
			new Page("doors", null, List.of(), null),
			new Page("chat", null, List.of(), null),
			new Page("gossip", null, List.of(), null),
			new Page("resident_menu", null, List.of(), null),
			new Page("reputation_tiers", null, List.of(), null),
			new Page("gifts", null, List.of(), null),
			new Page("gifts_more", null, List.of(), null),
			new Page("thanks", null, List.of(), null),
			new Page("directions", null, List.of(), null),
			new Page("work_shown", null, List.of(), null),
			new Page("night_lantern", null, List.of(), null),
			new Page("reactions", null, List.of(), null),
			new Page("panic", null, List.of(), null),
			new Page("guards", null, List.of(), null),
			new Page("guards_pay", null, List.of(), null),
			new Page("healers", null, List.of(), null),
			new Page("festa", null, List.of(), null),
			new Page("festa_rebate", null, List.of(), null),
			new Page("apprentices", null, List.of(), null),
			new Page("bell", null, List.of(), null),
			new Page("welcome", null, List.of(), null),
			new Page("life_far", null, List.of(), null),
			new Page("commands", null, List.of(), null));

	/** As páginas do separador «Guias» do Receitário: sem as dos mods que não estão instalados. */
	public static final List<Page> PAGES = ALL.stream().filter(Page::available).toList();
}
