package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

class WallRulesTest {
	@Test
	void onlyAirGrassFlowersSnowAndNeverInLots() {
		assertTrue(WallRules.mayReplace(true, false, false, false));
		assertTrue(WallRules.mayReplace(false, true, false, false));
		assertTrue(WallRules.mayReplace(false, false, true, false));
		assertFalse(WallRules.mayReplace(false, false, false, false), "um bloco qualquer (de jogador) nunca");
		assertFalse(WallRules.mayReplace(true, false, false, true), "nem ar dentro de um terreno");
	}

	@Test
	void squarePerimeterWithoutRepeatsAndTowersAtCorners() {
		var plan = WallRules.plan(0, 0, 30);
		assertEquals(8 * 30, plan.size());
		var seen = new HashSet<Long>();
		for (var c : plan) {
			assertTrue(Math.max(Math.abs(c.x()), Math.abs(c.z())) == 30, "no perímetro: " + c);
			assertTrue(seen.add(((long) c.x() << 32) ^ (c.z() & 0xffffffffL)), "sem repetir: " + c);
			assertEquals(1, Math.abs(c.nx()) + Math.abs(c.nz()), "normal para fora");
		}
		for (var c : plan) if (Math.abs(c.x()) == 30 && Math.abs(c.z()) == 30) assertTrue(c.tower(), "torre no canto " + c);
		assertTrue(plan.stream().anyMatch(c -> c.tower() && c.x() == 0), "torre a meio do lado");
	}

	@Test
	void halfFitsTheTerritory() {
		assertEquals(WallRules.MAX_HALF, WallRules.half(400));
		assertEquals(3, WallRules.half(6));
	}
}
