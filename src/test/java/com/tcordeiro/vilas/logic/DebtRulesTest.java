package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DebtRulesTest {
	@Test
	void freezesAfterNDaysAndWarnsTheDayBefore() {
		assertFalse(DebtRules.frozen(10, 12, 3));
		assertTrue(DebtRules.warnToday(10, 12, 3));
		assertTrue(DebtRules.frozen(10, 13, 3));
		assertFalse(DebtRules.warnToday(10, 13, 3));
		assertFalse(DebtRules.frozen(-1, 100, 3));
	}

	@Test
	void smallDebtsDoNotCount() {
		assertFalse(DebtRules.counts(50, 100));
		assertTrue(DebtRules.counts(100, 100));
		assertFalse(DebtRules.counts(0, 0));
	}

	@Test
	void payingAtTheBoardRoundsUp() {
		assertEquals(1, DebtRules.emeraldsToClear(1, 4000));
		assertEquals(1, DebtRules.emeraldsToClear(4000, 4000));
		assertEquals(2, DebtRules.emeraldsToClear(4001, 4000));
		assertEquals(0, DebtRules.emeraldsToClear(0, 4000));
	}
}
