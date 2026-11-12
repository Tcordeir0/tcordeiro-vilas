package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class MissionRulesTest {
	@Test
	void everySlotHasMissionsForEveryTier() {
		for (int slot = 0; slot < 3; slot++)
			for (int tier = 1; tier <= 3; tier++) {
				var list = MissionRules.eligible(slot, tier);
				assertFalse(list.isEmpty(), "casa " + slot + " escalão " + tier);
				for (var t : list) {
					assertEquals(MissionRules.SLOTS[slot], t.kind());
					assertTrue(t.tier() <= tier && t.tier() >= tier - 1);
					assertTrue(t.count() > 0 && t.emeralds() > 0 && t.reputation() > 0);
				}
			}
	}

	@Test
	void higherTierPaysMore() {
		int max1 = MissionRules.eligible(0, 1).stream().mapToInt(MissionRules.Template::emeralds).max().orElse(0);
		int min3 = MissionRules.eligible(0, 3).stream().mapToInt(MissionRules.Template::emeralds).min().orElse(0);
		assertTrue(min3 > max1);
	}

	@Test
	void pickAvoidsRepeating() {
		var first = MissionRules.pick(0, 1, 0, Set.of());
		for (int roll = 0; roll < 20; roll++) assertNotEquals(first, MissionRules.pick(0, 1, roll, Set.of(first)));
		var only = MissionRules.eligible(2, 1);
		assertEquals(only.get(0), MissionRules.pick(2, 1, 7, Set.copyOf(only)), "sem alternativa repete");
	}

	@Test
	void pickAvoidsTheSameTarget() {
		var lantern6 = MissionRules.TEMPLATES.stream().filter(t -> t.target().equals("minecraft:lantern") && t.tier() == 1).findFirst().orElseThrow();
		for (int tier = 2; tier <= 3; tier++)
			for (int roll = 0; roll < 30; roll++)
				assertNotEquals("minecraft:lantern", MissionRules.pick(1, tier, roll, Set.of(lantern6)).target(), "lanternas seguidas de lanternas");
	}

	@Test
	void noMissionPaysMoreThanBuyingItsItemsFromVillagers() {
		for (var t : MissionRules.TEMPLATES) assertTrue(MissionRules.noVillagerProfit(t), "comprar e entregar dá lucro: " + t);
		var bread10 = new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:bread", 10, 3, 10, 1);
		assertEquals(2, MissionRules.villagerCost(bread10));
		assertFalse(MissionRules.noVillagerProfit(bread10), "10 pães por 3 esmeraldas era lucro");
		assertEquals(-1, MissionRules.villagerCost(new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:diamond", 2, 12, 40, 3)));
	}

	@Test
	void completionAndBonus() {
		var t = new MissionRules.Template(MissionRules.Kind.DEFEND, MissionRules.INVADERS, 5, 5, 20, 1);
		assertFalse(MissionRules.complete(t, 4));
		assertTrue(MissionRules.complete(t, 5));
		assertEquals(20000, MissionRules.rewardCents(5, 4000));
	}

	@Test
	void noCobblestoneAndEveryRewardIsFair() {
		for (var t : MissionRules.TEMPLATES) {
			assertNotEquals("minecraft:cobblestone", t.target(), "pedregulho saiu das missões");
			assertTrue(MissionRules.fairReward(t, 4000), "recompensa acima do mercado ×1,25: " + t);
		}
		// 24 trigos valem 48 CB no mercado: até 60 CB; 3 esmeraldas (120 CB) eram lucro
		var wheat3 = new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 24, 3, 10, 1);
		assertFalse(MissionRules.fairReward(wheat3, 4000));
		assertTrue(MissionRules.fairReward(new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 24, 1, 10, 1), 4000));
		assertFalse(MissionRules.fairReward(new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:dirt", 64, 1, 10, 1), 4000),
				"sem preço conhecido não passa");
		// no limite: 32 tijolos de pedra (64 CB ×1,25 = 80 CB) pagam 2 esmeraldas, 3 já não
		assertTrue(MissionRules.fairReward(new MissionRules.Template(MissionRules.Kind.DECORATE, "minecraft:stone_bricks", 32, 2, 1, 2), 4000));
		assertFalse(MissionRules.fairReward(new MissionRules.Template(MissionRules.Kind.DECORATE, "minecraft:stone_bricks", 32, 3, 1, 2), 4000));
	}

	@Test
	void treasuryPaysWithCapAndDailyLimit() {
		assertTrue(MissionRules.mayPay(10000, 0, 40000, 0, 3, 4000));
		assertFalse(MissionRules.mayPay(3999, 0, 40000, 0, 3, 4000), "tesouraria sem dinheiro");
		assertFalse(MissionRules.mayPay(100000, 38000, 40000, 0, 3, 4000), "passa o teto do dia");
		assertTrue(MissionRules.mayPay(100000, 36000, 40000, 0, 3, 4000), "chega mesmo ao teto");
		assertFalse(MissionRules.mayPay(100000, 0, 40000, 3, 3, 4000), "4.ª missão do dia");
		assertTrue(MissionRules.mayPay(100000, 900000, 0, 0, 3, 4000), "teto 0 = sem teto");
	}

	@Test
	void defenseOnlyInARealRaid() {
		assertTrue(MissionRules.defenseCounts(true, true, false, false));
		assertFalse(MissionRules.defenseCounts(false, false, false, false), "monstro sem invasão");
		assertFalse(MissionRules.defenseCounts(true, false, false, false), "invasão noutra vila");
		assertFalse(MissionRules.defenseCounts(true, true, true, false), "invasão acabada");
		assertFalse(MissionRules.defenseCounts(true, true, false, true), "de gerador");
	}
}
