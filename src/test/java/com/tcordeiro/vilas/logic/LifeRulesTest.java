package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 0.4.0 — Regras da vida dos aldeões (puras). */
class LifeRulesTest {
	@Test
	void lodByDistance() {
		assertEquals(LifeRules.LOD_FULL, LifeRules.lod(48 * 48));
		assertEquals(LifeRules.LOD_REDUCED, LifeRules.lod(49 * 49));
		assertEquals(LifeRules.LOD_REDUCED, LifeRules.lod(96 * 96));
		assertEquals(LifeRules.LOD_NONE, LifeRules.lod(97 * 97));
	}

	@Test
	void bedtimesAreSpreadBetween18hAnd19h30() {
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long s = 0; s < 500; s++) {
			long b = LifeRules.bedtime(s * 7919);
			assertTrue(b >= LifeRules.BED_BASE && b < LifeRules.BED_BASE + LifeRules.BED_SPREAD, "entre as 18 h e as 19 h 30");
			min = Math.min(min, b);
			max = Math.max(max, b);
		}
		assertTrue(max - min > 1000, "deitam-se aos poucos, não todos ao mesmo tempo");
		assertTrue(LifeRules.socialTime(LifeRules.SOCIAL_START, 1));
		assertFalse(LifeRules.socialTime(LifeRules.SOCIAL_START - 1, 1));
		assertFalse(LifeRules.socialTime(LifeRules.bedtime(1), 1));
	}

	@Test
	void bellChimesOncePerHour() {
		assertEquals(2000, LifeRules.chime(2010, -1));
		assertEquals(-1, LifeRules.chime(2010, 2000), "não repete");
		assertEquals(6000, LifeRules.chime(6050, 2000));
		assertEquals(-1, LifeRules.chime(7000, 2000), "fora da hora");
		assertEquals(8, LifeRules.strokes(2000));
		assertEquals(12, LifeRules.strokes(6000));
		assertEquals(6, LifeRules.strokes(12000));
	}

	@Test
	void giftCaps() {
		assertTrue(LifeRules.mayGift(0));
		assertTrue(LifeRules.mayGift(1));
		assertFalse(LifeRules.mayGift(2), "2 por aldeão por semana");
		assertEquals(3, LifeRules.giftReputation(LifeRules.Taste.LOVED, 0));
		assertEquals(1, LifeRules.giftReputation(LifeRules.Taste.LIKED, 0));
		assertEquals(0, LifeRules.giftReputation(LifeRules.Taste.DISLIKED, 0));
		assertEquals(1, LifeRules.giftReputation(LifeRules.Taste.LOVED, LifeRules.GIFT_REP_PER_PLAYER_DAY - 1), "teto do dia");
		assertEquals(0, LifeRules.giftReputation(LifeRules.Taste.LOVED, LifeRules.GIFT_REP_PER_PLAYER_DAY));
		assertFalse(LifeRules.keepsGift(LifeRules.Taste.DISLIKED), "o detestado volta");
		assertTrue(LifeRules.mayThank(LifeRules.TIER_FRIEND, -1, 5, -1, 40));
		assertFalse(LifeRules.mayThank(LifeRules.TIER_FRIEND, 5, 5, -1, 40), "1 por semana");
		assertFalse(LifeRules.mayThank(LifeRules.TIER_FRIEND, -1, 5, 40, 40), "1 por dia por jogador");
		assertFalse(LifeRules.mayThank(LifeRules.TIER_FRIENDLY, -1, 5, -1, 40), "só Amigo");
	}

	@Test
	void guardsNeverOverdrawTheTreasury() {
		assertEquals(0, LifeRules.guardsPaid(299, 300, 2));
		assertEquals(1, LifeRules.guardsPaid(300, 300, 2));
		assertEquals(2, LifeRules.guardsPaid(10_000, 300, 2), "no máximo os postos");
		assertEquals(0, LifeRules.guardsPaid(-5, 300, 2));
		assertEquals(1, LifeRules.guardPosts(0));
		assertEquals(2, LifeRules.guardPosts(1));
		assertEquals(4, LifeRules.guardPosts(2));
		for (long t = 0; t < 5000; t += 37) assertTrue(t - LifeRules.guardsPaid(t, 300, 4) * 300L >= 0, "nunca negativa");
	}

	@Test
	void festaRebateIsCappedAndNeverCreated() {
		assertEquals(400, LifeRules.festaRebate(4000, 0, 10_000, 100_000), "10 %");
		assertEquals(0, LifeRules.festaRebate(4000, 0, 10_000, 0), "tesouraria vazia: nada");
		assertEquals(100, LifeRules.festaRebate(4000, 0, 100, 100_000), "orçamento da festa");
		assertEquals(150, LifeRules.festaRebate(4000, LifeRules.FESTA_PLAYER_CAP - 150, 10_000, 100_000), "teto por jogador");
		assertEquals(0, LifeRules.festaRebate(-10, 0, 10_000, 100_000));
		assertEquals(0, LifeRules.festaRebate(4000, LifeRules.FESTA_PLAYER_CAP + 5, 10_000, 100_000), "nunca negativo");
		assertEquals(4000, LifeRules.payable(4150, 4000), "só esmeraldas inteiras");
		assertEquals(37, LifeRules.payable(37, 1));
	}

	@Test
	void compass() {
		assertEquals("north", LifeRules.compass(0, -10));
		assertEquals("south", LifeRules.compass(0, 10));
		assertEquals("east", LifeRules.compass(10, 0));
		assertEquals("west", LifeRules.compass(-10, 0));
		assertEquals("northeast", LifeRules.compass(10, -10));
		assertEquals("southwest", LifeRules.compass(-10, 10));
	}

	@Test
	void stuckAndYield() {
		assertTrue(LifeRules.stuck(0.01, 60));
		assertFalse(LifeRules.stuck(0.01, 40));
		assertFalse(LifeRules.stuck(1, 60));
		assertTrue(LifeRules.yields(3, 9));
		assertFalse(LifeRules.yields(9, 3));
		assertEquals(LifeRules.OPINION_MAX, LifeRules.opinionAfterTalk(LifeRules.OPINION_MAX, true));
		assertTrue(LifeRules.welcome(10, 13));
		assertFalse(LifeRules.welcome(10, 12));
	}
}
