package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tcordeiro.vilas.logic.GrowthRules.Step;

class GrowthRulesTest {
	@Test
	void prestigeBringsResidentsWithABedUnderTheCap() {
		assertEquals(Step.WAIT, GrowthRules.step(2, 3, 10, 40, 5, false, 100000), "ainda não há prestígio bastante");
		assertEquals(Step.ARRIVE, GrowthRules.step(3, 3, 10, 40, 1, false, 0), "3 missões e uma cama livre: chega um morador");
		assertEquals(Step.FULL, GrowthRules.step(9, 3, 40, 40, 5, false, 100000), "no teto de população ninguém chega");
		assertEquals(Step.ORDER_HOUSE, GrowthRules.step(3, 3, 10, 40, 0, false, GrowthRules.HOUSE_CENTS), "sem cama: encomenda uma casa");
		assertEquals(Step.WAIT, GrowthRules.step(3, 3, 10, 40, 0, true, 100000), "uma casa de cada vez");
		assertEquals(Step.WAIT, GrowthRules.step(3, 3, 10, 40, 0, false, GrowthRules.HOUSE_CENTS - 1), "sem dinheiro para a casa: espera");
	}
}
