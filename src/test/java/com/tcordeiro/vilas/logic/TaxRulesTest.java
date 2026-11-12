package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaxRulesTest {
	@Test
	void risesHalfAPointPerPaidWork() {
		assertEquals(2.0, TaxRules.rate(2, 0, 0.5, 5, 0, 100_000).percent(), 1e-9);
		assertEquals(TaxRules.Reason.BASE, TaxRules.rate(2, 0, 0.5, 5, 0, 100_000).reason());
		assertEquals(3.5, TaxRules.rate(2, 3, 0.5, 5, 0, 100_000).percent(), 1e-9);
		assertEquals(TaxRules.Reason.RAISED, TaxRules.rate(2, 3, 0.5, 5, 0, 100_000).reason());
	}

	@Test
	void neverPassesTheCeiling() {
		for (int works = 0; works < 100; works++)
			assertEquals(Math.min(5.0, 2 + works * 0.5), TaxRules.rate(2, works, 0.5, 5, 0, 100_000).percent(), 1e-9);
		assertEquals(TaxRules.Reason.CAPPED, TaxRules.rate(2, 20, 0.5, 5, 0, 100_000).reason());
		// base acima do teto: fica no teto
		assertEquals(5.0, TaxRules.rate(8, 0, 0.5, 5, 0, 100_000).percent(), 1e-9);
	}

	@Test
	void fullTreasuryLowersToBase() {
		var r = TaxRules.rate(2, 6, 0.5, 5, 100_000, 100_000);
		assertEquals(2.0, r.percent(), 1e-9);
		assertEquals(TaxRules.Reason.FULL, r.reason());
		assertEquals(5.0, TaxRules.rate(2, 6, 0.5, 5, 99_999, 100_000).percent(), 1e-9);
	}

	@Test
	void formatsWithComma() {
		assertEquals("2", TaxRules.format(2.0));
		assertEquals("2,5", TaxRules.format(2.5));
	}
}
