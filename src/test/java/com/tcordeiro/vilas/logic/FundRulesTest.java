package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FundRulesTest {
	@Test
	void fundGrowsFiftyADayUpToTheCap() {
		assertEquals(20000, FundRules.START_CENTS, "200 CB ao nascer");
		assertEquals(25000, FundRules.topUp(20000, 1, 5000, 40000));
		assertEquals(40000, FundRules.topUp(20000, 4, 5000, 40000));
		assertEquals(40000, FundRules.topUp(20000, 1_000_000, 5000, 40000), "nunca passa o teto (nem com muitos dias)");
		assertEquals(40000, FundRules.topUp(39000, 1, 5000, 40000));
		assertEquals(50000, FundRules.topUp(50000, 3, 5000, 40000), "acima do teto não sobe nem desce");
		assertEquals(20000, FundRules.topUp(20000, 0, 5000, 40000));
	}

	@Test
	void missionPaysFromTheFundFirst() {
		assertEquals(4000, FundRules.fromFund(20000, 4000));
		assertEquals(1000, FundRules.fromFund(1000, 4000), "o resto sai da tesouraria");
		assertEquals(0, FundRules.fromFund(0, 4000));
	}

	@Test
	void mayorGoneAfterThreeDays() {
		long day = 24000;
		assertFalse(FundRules.mayorGone(0, 3 * day - 1, day));
		assertTrue(FundRules.mayorGone(0, 3 * day, day));
		assertFalse(FundRules.mayorGone(-1, 100 * day, day), "nunca visto: começa a contar agora");
	}
}
