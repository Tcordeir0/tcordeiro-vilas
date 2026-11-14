package com.tcordeiro.vilas.life.crisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 0.6 crises — regras puras da reconstrução, das crises, do funeral e da velhice. */
class CrisisRulesTest {
	@Test
	void recordsOnlyVillageBlocksOutsideLotsUnderTheCap() {
		assertTrue(CrisisRules.mayRecord(true, false, false, false, false, false, 0, 10));
		assertFalse(CrisisRules.mayRecord(true, true, false, false, false, false, 0, 10), "nunca num terreno de jogador");
		assertFalse(CrisisRules.mayRecord(false, false, false, false, false, false, 0, 10), "fora da vila");
		assertFalse(CrisisRules.mayRecord(true, false, true, false, false, false, 0, 10), "ar/fogo");
		assertFalse(CrisisRules.mayRecord(true, false, false, true, false, false, 0, 10), "baús e caixas: o conteúdo não se repõe");
		assertFalse(CrisisRules.mayRecord(true, false, false, false, true, false, 0, 10), "líquidos e TNT");
		assertFalse(CrisisRules.mayRecord(true, false, false, false, false, true, 0, 10), "a mesma posição duas vezes");
		assertFalse(CrisisRules.mayRecord(true, false, false, false, false, false, 10, 10), "teto");
	}

	@Test
	void onlyMobsCountAsCause() {
		assertTrue(CrisisRules.causeCounts(true, false));
		assertFalse(CrisisRules.causeCounts(false, true), "TNT de jogador");
		assertFalse(CrisisRules.causeCounts(false, false), "sem causador");
	}

	@Test
	void rebuildWaitsForCalm() {
		assertFalse(CrisisRules.mayStartRebuild(5, true, 0, 99999), "com perigo, não");
		assertFalse(CrisisRules.mayStartRebuild(5, false, 1000, 1000 + CrisisRules.CALM_TICKS - 1));
		assertTrue(CrisisRules.mayStartRebuild(5, false, 1000, 1000 + CrisisRules.CALM_TICKS));
		assertFalse(CrisisRules.mayStartRebuild(0, false, 0, 99999), "nada para repor");
	}

	@Test
	void crisisCooldownAndOnePerType() {
		assertTrue(CrisisRules.mayOpen(false, -1, 5, 2));
		assertFalse(CrisisRules.mayOpen(true, -1, 5, 2));
		assertFalse(CrisisRules.mayOpen(false, 4, 5, 2));
		assertTrue(CrisisRules.mayOpen(false, 3, 5, 2));
	}

	@Test
	void hungerAndNight() {
		assertTrue(CrisisRules.hungerLow(2, 5));
		assertFalse(CrisisRules.hungerLow(5, 5));
		assertFalse(CrisisRules.hungerLow(0, 2), "vila pequena demais");
		assertEquals(6, CrisisRules.hungerGoal(2));
		assertEquals(24, CrisisRules.hungerGoal(40));
		assertEquals(1, CrisisRules.hungerEmeralds(6), "6 pães custam 1 esmeralda no Agricultor: paga 1, sem lucro");
		assertEquals(2, CrisisRules.hungerEmeralds(12));
		assertTrue(CrisisRules.nightDanger(6, 6, true));
		assertFalse(CrisisRules.nightDanger(6, 6, false));
		assertFalse(CrisisRules.nightDanger(5, 6, true));
	}

	@Test
	void payRuleA1() {
		assertTrue(CrisisRules.mayPay(false, 0, 4, 1000, 0, 40000, 80));
		assertFalse(CrisisRules.mayPay(true, 0, 4, 1000, 0, 40000, 80), "uma vez por jogador");
		assertFalse(CrisisRules.mayPay(false, 4, 4, 1000, 0, 40000, 80), "poucos jogadores por crise");
		assertFalse(CrisisRules.mayPay(false, 0, 4, 79, 0, 40000, 80), "tesouraria sem dinheiro");
		assertFalse(CrisisRules.mayPay(false, 0, 4, 1000, 39950, 40000, 80), "teto do dia");
		assertTrue(CrisisRules.mayPay(false, 0, 4, 1000, 39950, 0, 80), "teto 0 = sem teto");
	}

	@Test
	void funeralAndAging() {
		assertFalse(CrisisRules.funeralDue(10, 10, 20000), "no próprio dia, não");
		assertFalse(CrisisRules.funeralDue(10, 11, CrisisRules.FUNERAL_TIME - 1));
		assertTrue(CrisisRules.funeralDue(10, 11, CrisisRules.FUNERAL_TIME));
		assertTrue(CrisisRules.funeralDue(10, 13, 0), "atrasado: logo");
		assertEquals(5, CrisisRules.daysLived(6, 10));
		assertEquals(1, CrisisRules.daysLived(CrisisRules.UNKNOWN, 10));
		assertFalse(CrisisRules.mayDieOfAge(false, 999, 120, false, false, false, false), "desligada por omissão");
		assertTrue(CrisisRules.mayDieOfAge(true, 120, 120, false, false, false, false));
		assertFalse(CrisisRules.mayDieOfAge(true, 999, 120, true, false, false, false), "trocas trancadas: nunca");
		assertFalse(CrisisRules.mayDieOfAge(true, 999, 120, false, true, false, false), "com nome: nunca");
		assertFalse(CrisisRules.mayDieOfAge(true, 999, 120, false, false, true, false), "Prefeito: nunca");
		assertFalse(CrisisRules.mayDieOfAge(true, 999, 120, false, false, false, true), "criança: nunca");
		assertFalse(CrisisRules.mayDieOfAge(true, 119, 120, false, false, false, false));
	}
}
