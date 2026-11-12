package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MerchantRulesTest {
	@Test
	void arrivesOnScheduleStaysAndLeaves() {
		assertFalse(MerchantRules.arrives(false, 4, 5));
		assertTrue(MerchantRules.arrives(false, 5, 5));
		assertFalse(MerchantRules.arrives(true, 5, 5), "já está");
		assertFalse(MerchantRules.leaves(true, 6, 5, 2));
		assertTrue(MerchantRules.leaves(true, 7, 5, 2));
		assertEquals(12, MerchantRules.nextVisit(7, 5));
	}
}
