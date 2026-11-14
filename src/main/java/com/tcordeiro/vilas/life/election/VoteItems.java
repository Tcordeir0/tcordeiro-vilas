package com.tcordeiro.vilas.life.election;

import com.tcordeiro.vilas.TVilas;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 0.6 eleições — O broche «Eu votei» ({@code tvilas:i_voted}): um item cosmético (textura de 16×16 em estilo vanilla, feita com PIL em
 * {@code tools/textures_060.py}) que a vila dá a quem votou numa eleição com vencedor. O nome da vila e do eleito vão na descrição
 * (componente {@code lore}). Não tem receita, não vale dinheiro nem se troca por nada; empilha até 16 (um por eleição).
 */
public final class VoteItems {
	private VoteItems() {}

	public static final Item I_VOTED = item("i_voted");

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TVilas.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.UNCOMMON)));
	}

	public static void init() {}
}
