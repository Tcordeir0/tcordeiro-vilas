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

	/** Página: chave de tradução, imagem opcional (textures/gui/guide/&lt;img&gt;.png) e receitas. */
	public record Page(String key, String image, List<Recipe> recipes) {}

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

	public static final List<Page> PAGES = List.of(
			new Page("cover", null, List.of()),
			new Page("mayor", null, List.of(MAYOR_DESK)),
			new Page("town_hall", null, List.of()),
			new Page("territory", null, List.of()),
			new Page("reborn", null, List.of()),
			new Page("talk", null, List.of()),
			new Page("missions", null, List.of()),
			new Page("rewards", null, List.of()),
			new Page("defend", null, List.of()),
			new Page("lots", null, List.of()),
			new Page("lot_rules", null, List.of()),
			new Page("friends", null, List.of()),
			new Page("levels", null, List.of()),
			new Page("unlocks", null, List.of()),
			new Page("alarm", null, List.of()),
			new Page("alarm_warning", null, List.of()),
			new Page("taxes", null, List.of()),
			new Page("treasury", null, List.of()),
			new Page("debts", null, List.of()),
			new Page("market", null, List.of()),
			new Page("baker", null, List.of(BAKER_OVEN)),
			new Page("florist", null, List.of(FLOWER_STALL)),
			new Page("innkeeper", null, List.of(TAVERN_COUNTER)),
			new Page("beekeeper", null, List.of(APIARY_BENCH)),
			new Page("commands", null, List.of()));
}
