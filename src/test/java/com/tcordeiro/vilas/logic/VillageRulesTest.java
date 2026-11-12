package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageRulesTest {
	@Test
	void levelNeedsBothPopulationAndReputation() {
		assertEquals(VillageRules.ALDEIA, VillageRules.levelFor(0, 0));
		assertEquals(VillageRules.ALDEIA, VillageRules.levelFor(VillageRules.POP_VILA, VillageRules.REP_VILA - 1));
		assertEquals(VillageRules.ALDEIA, VillageRules.levelFor(VillageRules.POP_VILA - 1, VillageRules.REP_VILA));
		assertEquals(VillageRules.VILA, VillageRules.levelFor(VillageRules.POP_VILA, VillageRules.REP_VILA));
		assertEquals(VillageRules.VILA, VillageRules.levelFor(VillageRules.POP_CIDADE, VillageRules.REP_CIDADE - 1));
		assertEquals(VillageRules.VILA, VillageRules.levelFor(VillageRules.POP_CIDADE - 1, VillageRules.REP_CIDADE));
		assertEquals(VillageRules.CIDADE, VillageRules.levelFor(VillageRules.POP_CIDADE, VillageRules.REP_CIDADE));
		assertEquals(VillageRules.CIDADE, VillageRules.levelFor(500, 99999));
	}

	@Test
	void territoryIsLarge() {
		assertTrue(VillageRules.DEFAULT_RADIUS >= 400, "território de pelo menos 400 blocos");
		assertTrue(LotRules.insideVillage(LotRules.around(380, 0), 0, 0, VillageRules.DEFAULT_RADIUS), "um terreno a 380 blocos ainda é da vila");
	}

	@Test
	void levelNeverGoesDown() {
		assertEquals(VillageRules.CIDADE, VillageRules.nextLevel(VillageRules.CIDADE, 0, 0));
		assertEquals(VillageRules.VILA, VillageRules.nextLevel(VillageRules.ALDEIA, VillageRules.POP_VILA, VillageRules.REP_VILA));
	}

	@Test
	void moreLotsAndHarderMissionsWithLevel() {
		assertTrue(VillageRules.maxLots(0) < VillageRules.maxLots(1));
		assertTrue(VillageRules.maxLots(1) < VillageRules.maxLots(2));
		assertEquals(1, VillageRules.missionTier(0));
		assertEquals(3, VillageRules.missionTier(2));
		assertEquals(3, VillageRules.missionTier(99));
	}
}
