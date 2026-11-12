package com.tcordeiro.vilas.test;

import java.util.ArrayList;
import java.util.List;

import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.worldgen.FurnitureProcessor;
import com.tcordeiro.vilas.worldgen.VillagePoolInjector;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** As peças NBT carregam, cabem num lote de vila e têm o que devem; as pools das 5 vilas vanilla incluem-nas. */
public class StructureGameTests {
	static StructureTemplate load(GameTestHelper h, net.minecraft.resources.Identifier id) {
		var t = h.getLevel().getStructureTemplateManager().get(id);
		h.assertTrue(t.isPresent(), "estrutura carrega: " + id);
		return t.get();
	}

	static List<StructureTemplate.StructureBlockInfo> blocks(StructureTemplate t, Block b) {
		return t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), b);
	}

	static List<String> jigsaws(StructureTemplate t, String name) {
		List<String> out = new ArrayList<>();
		for (var info : blocks(t, Blocks.JIGSAW))
			if (info.nbt() != null && info.nbt().getStringOr("name", "").equals(name)) out.add(info.nbt().getStringOr("pool", ""));
		return out;
	}

	@GameTest
	public void structuresLoadAndFit(GameTestHelper h) {
		for (String biome : VillagePoolInjector.BIOMES) {
			StructureTemplate hall = load(h, VillagePoolInjector.townHall(biome));
			h.assertTrue(hall.getSize().getX() <= 16 && hall.getSize().getZ() <= 16 && hall.getSize().getY() <= 12, "a Prefeitura cabe: " + hall.getSize());
			h.assertValueEqual(blocks(hall, VilasBlocks.MAYOR_DESK).size(), 1, "uma Mesa do Prefeito em " + biome);
			h.assertValueEqual(blocks(hall, Blocks.BELL).size(), 1, "um sino em " + biome);
			List<String> streets = jigsaws(hall, "minecraft:street");
			h.assertValueEqual(streets.size(), 3, "3 ligações de rua em " + biome);
			for (String pool : streets) h.assertValueEqual(pool, "minecraft:village/" + biome + "/streets", "ruas do próprio bioma");
			h.assertTrue(jigsaws(hall, "minecraft:bottom").contains("minecraft:village/" + biome + "/villagers"), "aldeões do bioma");

			StructureTemplate market = load(h, VillagePoolInjector.market(biome));
			h.assertTrue(market.getSize().getX() <= 16 && market.getSize().getZ() <= 16, "o Mercado cabe: " + market.getSize());
			for (Block job : new Block[] {VilasBlocks.BAKER_OVEN, VilasBlocks.FLOWER_STALL, VilasBlocks.TAVERN_COUNTER, VilasBlocks.APIARY_BENCH})
				h.assertValueEqual(blocks(market, job).size(), 1, "uma banca de " + job + " em " + biome);
			h.assertValueEqual(jigsaws(market, "minecraft:building_entrance").size(), 1, "entrada do Mercado em " + biome);
		}
		h.succeed();
	}

	@GameTest
	public void tavernAndPlazaLoadAndFit(GameTestHelper h) {
		for (String biome : VillagePoolInjector.BIOMES) {
			StructureTemplate tavern = load(h, VillagePoolInjector.tavern(biome));
			h.assertTrue(tavern.getSize().getX() <= 16 && tavern.getSize().getZ() <= 16 && tavern.getSize().getY() <= 12, "a Taverna cabe: " + tavern.getSize());
			h.assertValueEqual(blocks(tavern, VilasBlocks.TAVERN_COUNTER).size(), 1, "balcão do Taberneiro em " + biome);
			h.assertValueEqual(blocks(tavern, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED)).size(), 4, "2 camas de solteiro em " + biome);
			h.assertValueEqual(blocks(tavern, FurnitureProcessor.LEFT_BED).size() + blocks(tavern, FurnitureProcessor.RIGHT_BED).size(), 4,
					"cama de casal (marcador) em " + biome);
			h.assertValueEqual(jigsaws(tavern, "minecraft:building_entrance").size(), 1, "entrada da Taverna em " + biome);
			StructureTemplate plaza = load(h, VillagePoolInjector.plaza(biome));
			h.assertTrue(plaza.getSize().getX() <= 16 && plaza.getSize().getZ() <= 16, "a Praça cabe: " + plaza.getSize());
			h.assertTrue(blocks(plaza, Blocks.WATER).size() >= 9, "fonte e poço com água em " + biome);
			h.assertValueEqual(jigsaws(plaza, "minecraft:building_entrance").size(), 1, "entrada da Praça em " + biome);
		}
		h.succeed();
	}

	/** Os blocos da peça depois do processador dos móveis. */
	static List<StructureTemplate.StructureBlockInfo> processed(GameTestHelper h, StructureTemplate t) {
		List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>();
		var all = new ArrayList<StructureTemplate.StructureBlockInfo>();
		for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			if (b == Blocks.AIR) continue;
			all.addAll(blocks(t, b));
		}
		for (var info : all) {
			var r = FurnitureProcessor.INSTANCE.processBlock(h.getLevel(), BlockPos.ZERO, BlockPos.ZERO, info.pos(), info, new StructurePlaceSettings());
			out.add(r);
		}
		return out;
	}

	static long count(List<StructureTemplate.StructureBlockInfo> l, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> p) {
		return l.stream().filter(i -> p.test(i.state())).count();
	}

	static boolean moveis(net.minecraft.world.level.block.state.BlockState s, String suffix) {
		var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
		return id.getNamespace().equals("tmoveis") && id.getPath().endsWith(suffix);
	}

	@GameTest
	public void furnitureProcessorSwapsMarkersOnlyWithMoveis(GameTestHelper h) {
		boolean mod = com.tcordeiro.vilas.compat.Mods.loaded(com.tcordeiro.vilas.compat.Mods.MOVEIS);
		for (String biome : VillagePoolInjector.BIOMES) {
			StructureTemplate tavern = load(h, VillagePoolInjector.tavern(biome));
			var out = processed(h, tavern);
			h.assertTrue(out.stream().allMatch(java.util.Objects::nonNull), "o processador não tira blocos");
			long chairs = count(out, s -> moveis(s, "_chair")), tables = count(out, s -> moveis(s, "_table")),
					lamps = count(out, s -> moveis(s, "chandelier")), beds = count(out, s -> moveis(s, "_double_bed"));
			if (mod) {
				h.assertValueEqual(chairs, 4L, "4 cadeiras do Móveis em " + biome);
				h.assertValueEqual(tables, 2L, "2 mesas do Móveis em " + biome);
				h.assertValueEqual(lamps, 2L, "2 lustres do Móveis em " + biome);
				h.assertValueEqual(beds, 4L, "as 4 partes da cama de casal em " + biome);
				h.assertValueEqual(count(out, s -> s.is(FurnitureProcessor.LEFT_BED) || s.is(FurnitureProcessor.RIGHT_BED)), 0L, "sem marcadores de cama");
				h.assertValueEqual(count(out, s -> s.getBlock() instanceof net.minecraft.world.level.block.PressurePlateBlock), 0L, "as placas saíram");
				h.assertValueEqual(count(out, s -> s.is(Blocks.REDSTONE_BLOCK)), 2L, "redstone por cima dos lustres");
				h.assertValueEqual(count(out, s -> s.is(Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED))), 4L, "as camas de solteiro ficam (aldeões)");
			} else {
				h.assertValueEqual(chairs + tables + lamps + beds, 0L, "sem o Móveis não há móveis");
				h.assertValueEqual(count(out, s -> s.is(FurnitureProcessor.LEFT_BED)), 2L, "sem o Móveis a cama de casal são duas camas vanilla");
				h.assertValueEqual(count(out, s -> s.getBlock() instanceof net.minecraft.world.level.block.PressurePlateBlock), 2L, "as mesas ficam com a placa");
				h.assertValueEqual(count(out, s -> s.is(Blocks.REDSTONE_BLOCK)), 0L, "sem redstone no teto");
			}
		}
		h.succeed();
	}

	/** Põe a Taverna de verdade (com o processador), 40 blocos acima do teste, e vê se a cama de casal fica inteira. */
	@GameTest(maxTicks = 100)
	public void tavernPlacedInTheWorldKeepsItsFurniture(GameTestHelper h) {
		StructureTemplate tavern = load(h, VillagePoolInjector.tavern("plains"));
		BlockPos at = h.absolutePos(new BlockPos(0, 40, 0));
		var settings = new StructurePlaceSettings().addProcessor(FurnitureProcessor.INSTANCE);
		tavern.placeInWorld(h.getLevel(), at, at, settings, h.getLevel().getRandom(), 2);
		boolean mod = com.tcordeiro.vilas.compat.Mods.loaded(com.tcordeiro.vilas.compat.Mods.MOVEIS);
		h.runAfterDelay(20, () -> {
			int beds = 0, counters = 0;
			for (BlockPos p : BlockPos.betweenClosed(at, at.offset(tavern.getSize()).offset(-1, -1, -1))) {
				var s = h.getLevel().getBlockState(p);
				if (moveis(s, "_double_bed")) beds++;
				if (s.is(VilasBlocks.TAVERN_COUNTER)) counters++;
			}
			h.assertValueEqual(counters, 1, "balcão posto");
			h.assertValueEqual(beds, mod ? 4 : 0, "cama de casal inteira (4 partes) com o Móveis");
			for (BlockPos p : BlockPos.betweenClosed(at, at.offset(tavern.getSize()).offset(-1, -1, -1))) h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			h.succeed();
		});
	}

	@GameTest
	public void villagePoolsIncludeNewPieces(GameTestHelper h) {
		var pools = h.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (String biome : VillagePoolInjector.BIOMES) {
			var centers = pools.getValue(VillagePoolInjector.pool(biome, "town_centers"));
			var houses = pools.getValue(VillagePoolInjector.pool(biome, "houses"));
			h.assertTrue(VillagePoolInjector.contains(centers, VillagePoolInjector.townHall(biome)), "Prefeitura nos centros de " + biome);
			h.assertTrue(VillagePoolInjector.contains(houses, VillagePoolInjector.market(biome)), "Mercado nas casas de " + biome);
			h.assertTrue(!VillagePoolInjector.contains(houses, VillagePoolInjector.townHall(biome)), "a Prefeitura não vai para as casas");
			h.assertTrue(VillagePoolInjector.contains(houses, VillagePoolInjector.tavern(biome)), "Taverna nas casas de " + biome);
			h.assertTrue(VillagePoolInjector.contains(houses, VillagePoolInjector.plaza(biome)), "Praça nas casas de " + biome);
		}
		h.succeed();
	}
}
