package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 0.6.0 — Memória (6 vagas, a mais fraca sai), resgate e cartas (uma vez), doações (só o que falta, reputação com teto). */
class LoreRulesTest {
	@Test
	void memorySlotsAndDecay() {
		assertEquals(-1, LoreRules.slotFor(new int[] {5, 5}, 3), "há espaço: acrescenta");
		assertEquals(2, LoreRules.slotFor(new int[] {5, 5, 1, 5, 5, 5}, 3), "cheio: sai a mais fraca");
		assertEquals(-2, LoreRules.slotFor(new int[] {5, 5, 5, 5, 5, 5}, 2), "mais fraca que todas: não entra");
		assertEquals(10, LoreRules.weightNow(10, 0, 7));
		assertEquals(9, LoreRules.weightNow(10, 0, 8), "decai 1 a cada 8 dias");
		assertEquals(1, LoreRules.weightNow(2, 0, 400), "nunca abaixo de 1");
	}

	@Test
	void rescueOncePerDayAndLetterPerWeek() {
		assertTrue(LoreRules.rescue(true, -1, 1000));
		assertTrue(LoreRules.rescue(false, 900, 1000), "bateu-lhe há pouco");
		assertFalse(LoreRules.rescue(false, 100, 1000), "bateu-lhe há muito");
		assertFalse(LoreRules.rescue(false, -1, 1000));
		assertTrue(LoreRules.mayRewardSave(Long.MIN_VALUE, 3));
		assertFalse(LoreRules.mayRewardSave(3, 3), "uma vez por dia");
		assertTrue(LoreRules.mayWriteLetter(Long.MIN_VALUE, 1));
		assertFalse(LoreRules.mayWriteLetter(1, 1), "uma carta por semana");
	}

	@Test
	void donationsTakeOnlyWhatIsMissingAndRepIsCapped() {
		assertEquals(10, LoreRules.take(16, 6, 64), "só o que falta");
		assertEquals(3, LoreRules.take(16, 6, 3));
		assertEquals(0, LoreRules.take(16, 16, 64), "já está tudo");
		assertEquals(0, LoreRules.take(16, 20, 64), "nunca negativo");
		assertEquals(2, LoreRules.donationRep(16, 0));
		assertEquals(LoreRules.DONATION_REP_DAY, LoreRules.donationRep(1000, 0), "teto por dia");
		assertEquals(0, LoreRules.donationRep(64, LoreRules.DONATION_REP_DAY), "teto já gasto");
		assertTrue(LoreRules.projectDue(-1, 0));
		assertFalse(LoreRules.projectDue(10, 12));
		assertTrue(LoreRules.projectDue(10, 10 + LoreRules.PROJECT_EVERY_DAYS));
	}
}
