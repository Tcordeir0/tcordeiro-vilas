package com.tcordeiro.vilas.test;

import java.util.ArrayList;
import java.util.List;

import com.tcordeiro.vilas.registry.VilasBlocks;
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
	public void villagePoolsIncludeNewPieces(GameTestHelper h) {
		var pools = h.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (String biome : VillagePoolInjector.BIOMES) {
			var centers = pools.getValue(VillagePoolInjector.pool(biome, "town_centers"));
			var houses = pools.getValue(VillagePoolInjector.pool(biome, "houses"));
			h.assertTrue(VillagePoolInjector.contains(centers, VillagePoolInjector.townHall(biome)), "Prefeitura nos centros de " + biome);
			h.assertTrue(VillagePoolInjector.contains(houses, VillagePoolInjector.market(biome)), "Mercado nas casas de " + biome);
			h.assertTrue(!VillagePoolInjector.contains(houses, VillagePoolInjector.townHall(biome)), "a Prefeitura não vai para as casas");
		}
		h.succeed();
	}
}
