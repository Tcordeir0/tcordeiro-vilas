package com.tcordeiro.vilas.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.block.JobSiteBlock;
import com.tcordeiro.vilas.block.MayorDeskBlock;
import com.tcordeiro.vilas.block.MayorDeskBlockEntity;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocos: a Mesa do Prefeito (posto de trabalho do Prefeito e quadro de missões) e os 4 postos dos vendedores novos. */
public final class VilasBlocks {
	private VilasBlocks() {}

	private static final List<Block> ALL = new ArrayList<>();

	public static final Block MAYOR_DESK = block("mayor_desk", MayorDeskBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).ignitedByLava());
	public static final Block BAKER_OVEN = block("baker_oven", p -> new JobSiteBlock(JobSiteBlock.OVEN, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.5f).sound(SoundType.STONE).requiresCorrectToolForDrops()
					.lightLevel(s -> 7));
	public static final Block FLOWER_STALL = block("flower_stall", p -> new JobSiteBlock(JobSiteBlock.STALL, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava().noOcclusion());
	public static final Block TAVERN_COUNTER = block("tavern_counter", p -> new JobSiteBlock(JobSiteBlock.FULL, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).ignitedByLava());
	public static final Block APIARY_BENCH = block("apiary_bench", p -> new JobSiteBlock(JobSiteBlock.BENCH, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f).sound(SoundType.WOOD).ignitedByLava().noOcclusion());

	public static final BlockEntityType<MayorDeskBlockEntity> MAYOR_DESK_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			TVilas.id("mayor_desk"), new BlockEntityType<>(MayorDeskBlockEntity::new, Set.of(MAYOR_DESK)));

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Identifier id = TVilas.id(name);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALL.add(block);
		return block;
	}

	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void init() {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TVilas.id("villages"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.tvilas.villages"))
				.icon(() -> new ItemStack(MAYOR_DESK))
				.displayItems((params, out) -> ALL.forEach(out::accept))
				.build());
	}
}
