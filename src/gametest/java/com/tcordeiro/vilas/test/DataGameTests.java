package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.registry.VilasBlocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;

/** Dados: cada bloco tem item, receita e loot; o livro-guia cabe no limite de 100 páginas. */
public class DataGameTests {
	@GameTest
	public void everyBlockHasItemRecipeAndLoot(GameTestHelper h) {
		var server = h.getLevel().getServer();
		for (Block b : VilasBlocks.all()) {
			var id = BuiltInRegistries.BLOCK.getKey(b);
			h.assertTrue(b.asItem() != Items.AIR, "item de " + id);
			h.assertTrue(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent(), "receita de " + id);
			h.assertTrue(b.getLootTable().isPresent()
					&& server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE).flatMap(l -> l.get(b.getLootTable().get())).isPresent(),
					"loot de " + id);
		}
		h.succeed();
	}

	@GameTest
	public void guideBookFits(GameTestHelper h) {
		var holder = h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, TVilas.id("guide")));
		h.assertTrue(holder.isPresent(), "receita do livro-guia");
		ItemStack book = holder.get().value().display().get(0).result().resolveForFirstStack(SlotDisplayContext.fromLevel(h.getLevel()));
		var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		h.assertTrue(content != null && !content.pages().isEmpty() && content.pages().size() <= 100, "livro com 1–100 páginas");
		// 0.2.0: o livro leva TODAS as páginas (as dos mods dizem «só com o mod»); o separador do Receitário só as dos mods instalados
		h.assertValueEqual(com.tcordeiro.vilas.guide.VilasGuide.ALL.size(), content.pages().size(), "livro = páginas do guia");
		for (var page : com.tcordeiro.vilas.guide.VilasGuide.ALL)
			h.assertValueEqual(com.tcordeiro.vilas.guide.VilasGuide.PAGES.contains(page), page.available(), "página " + page.key() + " só com o mod");
		h.succeed();
	}
}
