package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CivicRulesTest {
	@Test
	void oneAtATimeInOrderAndPaid() {
		assertEquals("town_hall", CivicRules.next(Set.of(), false, 1_000_000, true));
		assertEquals("market", CivicRules.next(Set.of("town_hall"), false, 1_000_000, true));
		assertEquals("plaza", CivicRules.next(Set.of("town_hall", "market", "tavern"), false, 1_000_000, true));
		assertNull(CivicRules.next(Set.of("town_hall", "market", "tavern", "plaza"), false, 1_000_000, true), "nada falta");
		assertNull(CivicRules.next(Set.of(), true, 1_000_000, true), "já há um em obras");
		assertNull(CivicRules.next(Set.of(), false, CivicRules.TOWN_HALL_CENTS - 1, true), "tesouraria curta: espera (não salta)");
		assertNull(CivicRules.next(Set.of(), false, 1_000_000, false), "não é uma vila de verdade");
	}

	@Test
	void realVillageAndSlope() {
		assertTrue(CivicRules.realVillage(true, 0));
		assertTrue(CivicRules.realVillage(false, CivicRules.MIN_POPULATION));
		assertFalse(CivicRules.realVillage(false, CivicRules.MIN_POPULATION - 1));
		assertTrue(CivicRules.flatEnough(64, 66));
		assertFalse(CivicRules.flatEnough(64, 67));
	}
}
