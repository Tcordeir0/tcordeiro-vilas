package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** O Prefeito: um aldeão desempregado apanha a Mesa do Prefeito; só há uma Mesa (e uma vaga) por vila; o Prefeito não troca. */
public class MayorGameTests {
	@GameTest(maxTicks = 3000)
	public void joblessVillagerBecomesMayor(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		Village v = VillageManager.found(h.getLevel(), h.absolutePos(desk), TestUtil.RADIUS);
		h.assertTrue(v != null, "a Mesa funda a vila");
		Villager villager = h.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
		h.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.NONE), "começa desempregado");
		h.succeedWhen(() -> {
			h.assertTrue(VillageManager.isMayor(villager), "o aldeão passou a Prefeito");
			h.setBlock(desk, net.minecraft.world.level.block.Blocks.AIR); // arrumar: sem Mesa não chegam moradores a outros testes
			villager.discard();
		});
	}

	@GameTest
	public void onlyOneDeskPerVillage(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos first = new BlockPos(4, 1, 4), second = new BlockPos(6, 1, 6);
		h.setBlock(first, VilasBlocks.MAYOR_DESK);
		h.assertTrue(VillageManager.found(h.getLevel(), h.absolutePos(first), TestUtil.RADIUS) != null, "primeira Mesa");
		h.assertTrue(!VillageManager.canPlaceDesk(h.getLevel(), h.absolutePos(second)), "não se pode pôr outra na mesma vila");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(6, 1, 3));
		TestUtil.placeWithItem(h, p, new ItemStack(VilasBlocks.MAYOR_DESK), second);
		h.assertBlockNotPresent(VilasBlocks.MAYOR_DESK, second);
		h.assertTrue(VillageManager.found(h.getLevel(), h.absolutePos(second), TestUtil.RADIUS) == null, "uma segunda Mesa não funda vila");
		// a Mesa só tem 1 vaga: só um aldeão pode ser o Prefeito desta Mesa
		var poi = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getValue(VilasProfessions.POIS.get(VilasProfessions.MAYOR));
		h.assertValueEqual(poi.maxTickets(), 1, "vagas na Mesa do Prefeito");
		h.assertTrue(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(VilasProfessions.POIS.get(VilasProfessions.MAYOR)).is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
				"aldeões desempregados procuram a Mesa");
		// partida a Mesa, uma nova Mesa na mesma vila adota-a (reputação e terrenos não se perdem)
		Village v = VillageManager.byDesk(h.getLevel(), h.absolutePos(first));
		h.setBlock(first, net.minecraft.world.level.block.Blocks.AIR);
		h.assertTrue(VillageManager.canPlaceDesk(h.getLevel(), h.absolutePos(second)), "sem Mesa já se pode pôr outra");
		h.setBlock(second, VilasBlocks.MAYOR_DESK);
		h.assertTrue(VillageManager.found(h.getLevel(), h.absolutePos(second), TestUtil.RADIUS) == v, "a nova Mesa adota a vila antiga");
		h.setBlock(second, net.minecraft.world.level.block.Blocks.AIR);
		h.succeed();
	}

	@GameTest
	public void mayorDoesNotTrade(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		Villager villager = h.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().withProfession(h.getLevel().registryAccess(), VilasProfessions.MAYOR));
		h.assertTrue(VillageManager.isMayor(villager), "é o Prefeito");
		h.assertTrue(villager.getOffers().isEmpty(), "o Prefeito não tem trocas");
		h.succeed();
	}
}
