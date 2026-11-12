package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BuildRulesTest {
	@Test
	void oneBlockAtATimeWhenWatched() {
		assertEquals(1, BuildRules.blocksNow(true, -1, 0));
		assertEquals(0, BuildRules.blocksNow(true, 100, 110));
		assertEquals(1, BuildRules.blocksNow(true, 100, 120));
		assertEquals(0, BuildRules.blocksNow(false, -1, 1000), "sem ninguém a ver espera pela noite");
	}

	@Test
	void stuckAfterTenSeconds() {
		assertFalse(BuildRules.stuck(-1, 1000));
		assertFalse(BuildRules.stuck(0, 199));
		assertTrue(BuildRules.stuck(0, 200));
	}

	@Test
	void polesKeepTheirDistance() {
		var c = List.of(new LightingRules.Spot(0, 0), new LightingRules.Spot(3, 0), new LightingRules.Spot(10, 0), new LightingRules.Spot(0, 12));
		var out = LightingRules.choose(c, 10, 16);
		assertEquals(List.of(new LightingRules.Spot(0, 0), new LightingRules.Spot(10, 0), new LightingRules.Spot(0, 12)), out);
		assertEquals(1, LightingRules.choose(c, 10, 1).size());
	}

	@Test
	void poleWoodFollowsTheBiome() {
		assertEquals("oak", LightingRules.woodFor("plains"));
		assertEquals("spruce", LightingRules.woodFor("taiga"));
		assertEquals("spruce", LightingRules.woodFor("snowy_plains"));
		assertEquals("acacia", LightingRules.woodFor("savanna"));
		assertEquals(null, LightingRules.woodFor("desert"), "deserto: poste de ferro");
		assertEquals("cherry", LightingRules.woodFor("cherry_grove"));
	}

	@Test
	void polesStayWithinTheWireSpan() {
		var c = List.of(new LightingRules.Spot(0, 0), new LightingRules.Spot(30, 0), new LightingRules.Spot(12, 0), new LightingRules.Spot(24, 0));
		// (30,0) só entra depois de (24,0), que está a 12 de (12,0)
		assertEquals(List.of(new LightingRules.Spot(0, 0), new LightingRules.Spot(12, 0), new LightingRules.Spot(24, 0), new LightingRules.Spot(30, 0)).size(),
				LightingRules.choose(c, 5, 16, 16).size());
		assertEquals(1, LightingRules.choose(List.of(new LightingRules.Spot(0, 0), new LightingRules.Spot(40, 0)), 5, 16, 16).size(), "longe demais para o fio");
	}

	@Test
	void lightingComesAfterTheOtherUpgradesInAVillage() {
		assertEquals(TreasuryRules.Upgrade.LIGHTING, TreasuryRules.choose(10_000, 10, 10, 1, 1, VillageRules.VILA, false));
		assertEquals(null, TreasuryRules.choose(10_000, 10, 10, 1, 1, VillageRules.VILA, true));
		assertEquals(null, TreasuryRules.choose(10_000, 5, 5, 1, 0, VillageRules.ALDEIA, false), "aldeia não compra iluminação");
	}
}
