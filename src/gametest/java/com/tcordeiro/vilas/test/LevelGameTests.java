package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/** Nível da vila: sobe com população E reputação somada; nunca desce. */
public class LevelGameTests {
	static void villagers(GameTestHelper h, int n) {
		for (int i = 0; i < n; i++) {
			Villager v = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + i % 7, 1, 1 + (i / 7) % 7));
			v.setNoAi(true);
		}
	}

	@GameTest
	public void levelRisesWithPopulationAndReputation(GameTestHelper h) {
		Village v = TestUtil.village(h);
		villagers(h, VillageRules.POP_VILA);
		VillageManager.updateLevel(h.getLevel(), v);
		h.assertValueEqual(v.population, VillageRules.POP_VILA, "população contada");
		h.assertValueEqual(v.level, VillageRules.ALDEIA, "sem reputação continua aldeia");
		var p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		VillageManager.addReputation(h.getLevel(), v, p, VillageRules.REP_VILA);
		h.assertTrue(VillageManager.updateLevel(h.getLevel(), v), "subiu");
		h.assertValueEqual(v.level, VillageRules.VILA, "vila");
		VillageManager.addReputation(h.getLevel(), v, p, VillageRules.REP_CIDADE);
		VillageManager.updateLevel(h.getLevel(), v);
		h.assertValueEqual(v.level, VillageRules.VILA, "reputação sem população não chega a cidade");
		villagers(h, VillageRules.POP_CIDADE - VillageRules.POP_VILA);
		VillageManager.updateLevel(h.getLevel(), v);
		h.assertValueEqual(v.level, VillageRules.CIDADE, "cidade");
		h.killAllEntitiesOfClass(Villager.class);
		VillageManager.updateLevel(h.getLevel(), v);
		h.assertValueEqual(v.level, VillageRules.CIDADE, "o nível nunca desce");
		h.succeed();
	}
}
