package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreasuryRulesTest {
	@Test
	void tradeTaxIsTwoPercentWithMinimum() {
		assertEquals(200, TreasuryRules.tradeTax(10_000, 2.0), "2 % de 100 CB = 2 CB");
		assertEquals(80, TreasuryRules.tradeTax(4000, 2.0), "1 esmeralda (40 CB) paga 80¢");
		assertEquals(1, TreasuryRules.tradeTax(10, 2.0), "taxa mínima de 1¢");
		assertEquals(0, TreasuryRules.tradeTax(0, 2.0));
		assertEquals(0, TreasuryRules.tradeTax(10_000, 0), "taxa 0 desliga");
		assertEquals(500, TreasuryRules.tradeTax(10_000, 5.0), "a percentagem é configurável");
	}

	@Test
	void bedTaxPerBedPerDay() {
		assertEquals(6, TreasuryRules.bedTax(3, 2, 1));
		assertEquals(0, TreasuryRules.bedTax(0, 5, 1));
		assertEquals(3 * TreasuryRules.MAX_DAYS_AT_ONCE, TreasuryRules.bedTax(3, 1000, 1), "no máximo 7 dias de uma vez");
	}

	@Test
	void emeraldsRoundDown() {
		assertEquals(0, TreasuryRules.emeraldsFor(3999, 4000));
		assertEquals(1, TreasuryRules.emeraldsFor(4000, 4000));
		assertEquals(2, TreasuryRules.emeraldsFor(8100, 4000));
	}

	@Test
	void emptyVillageGetsFreeResidentsOnlyWithBeds() {
		assertTrue(TreasuryRules.freeResident(0, 4));
		assertTrue(TreasuryRules.freeResident(2, 4));
		assertFalse(TreasuryRules.freeResident(3, 10), "só os primeiros três são grátis");
		assertFalse(TreasuryRules.freeResident(0, 0), "sem camas não chega ninguém");
		assertFalse(TreasuryRules.freeResident(1, 1));
	}

	@Test
	void mayorSpendsInPriorityOrder() {
		var R = TreasuryRules.Upgrade.RESIDENT;
		assertEquals(R, TreasuryRules.choose(R.cost, 3, 5, 0, 0, 0), "camas livres: moradores primeiro");
		assertNull(TreasuryRules.choose(R.cost - 1, 3, 5, 0, 0, 0), "sem dinheiro não compra");
		assertEquals(TreasuryRules.Upgrade.GOLEM, TreasuryRules.choose(1_000_000, 5, 5, 0, 0, 0));
		assertNull(TreasuryRules.choose(1_000_000, 5, 5, 1, 0, 0), "aldeia: sem reforço do alarme");
		assertEquals(TreasuryRules.Upgrade.ALARM, TreasuryRules.choose(1_000_000, 5, 5, 1, 0, 1), "vila: 1 reforço");
		assertNull(TreasuryRules.choose(1_000_000, 5, 5, 1, 1, 1));
		assertEquals(TreasuryRules.Upgrade.ALARM, TreasuryRules.choose(1_000_000, 20, 20, 2, 1, 2), "cidade: 2 reforços");
	}
}
