package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaidSpotsTest {
	@Test
	void paidSpotStaysPaidUntilItExpires() {
		PaidSpots p = new PaidSpots();
		assertFalse(p.isPaid(42, 0));
		p.markPaid(42, 100);
		assertTrue(p.isPaid(42, 100));
		assertTrue(p.isPaid(42, 100 + PaidSpots.TTL - 1), "ainda dentro do prazo");
		assertFalse(p.isPaid(42, 100 + PaidSpots.TTL), "expirou");
		assertFalse(p.isPaid(43, 100), "outra posição");
	}

	@Test
	void boundedAndPrunedOldestFirst() {
		PaidSpots p = new PaidSpots();
		for (int i = 0; i < PaidSpots.MAX + 10; i++) p.markPaid(i, i);
		assertEquals(PaidSpots.MAX, p.size(), "nunca passa do máximo");
		assertFalse(p.isPaid(0, PaidSpots.MAX + 10), "a mais antiga saiu");
		assertTrue(p.isPaid(PaidSpots.MAX + 9, PaidSpots.MAX + 10));
		p.markPaid(-1, PaidSpots.MAX + 10 + PaidSpots.TTL);
		assertEquals(1, p.size(), "as expiradas saem na limpeza");
	}

	@Test
	void savesAndLoads() {
		PaidSpots p = new PaidSpots();
		p.markPaid(7, 1);
		p.markPaid(-9, 2);
		PaidSpots q = PaidSpots.fromArray(p.toArray());
		assertTrue(q.isPaid(7, 3) && q.isPaid(-9, 3));
		assertEquals(2, q.size());
	}
}
