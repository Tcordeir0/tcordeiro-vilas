package com.tcordeiro.vilas.registry;

import com.tcordeiro.vilas.TVilas;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * 0.5.0 — Itens de CENA (modelos feitos no Blender, {@code tools/blender_050.py}): a bolsa do Carteiro, a boia do pescador e a
 * banca do mercado. Só aparecem na mão dos aldeões (itens de cena: nunca caem) ou em mostradores do Vilas que se apagam sozinhos; não
 * estão no separador criativo nem têm receita (não são itens para os jogadores, nem repetem itens do jogo).
 */
public final class VilasItems {
	private VilasItems() {}

	public static final Item MAIL_BAG = item("mail_bag");
	public static final Item BOBBER = item("bobber");
	public static final Item MARKET_STALL = item("market_stall");

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TVilas.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void init() {}
}
