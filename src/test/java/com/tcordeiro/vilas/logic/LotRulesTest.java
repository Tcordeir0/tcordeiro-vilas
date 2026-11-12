package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LotRulesTest {
	static final UUID OWNER = UUID.randomUUID(), FRIEND = UUID.randomUUID(), STRANGER = UUID.randomUUID();

	@Test
	void lotIs16x16AroundThePlayer() {
		LotRules.Area a = LotRules.around(100, -20);
		assertEquals(16, a.maxX() - a.minX() + 1);
		assertEquals(16, a.maxZ() - a.minZ() + 1);
		assertTrue(a.contains(100, -20));
		assertTrue(a.contains(92, -28));
		assertTrue(a.contains(107, -13));
		assertFalse(a.contains(108, -20));
		assertFalse(a.contains(91, -20));
	}

	@Test
	void onlyOwnerAndFriendsMayModify() {
		Set<UUID> friends = Set.of(FRIEND);
		assertTrue(LotRules.mayModify(OWNER, friends, OWNER));
		assertTrue(LotRules.mayModify(OWNER, friends, FRIEND));
		assertFalse(LotRules.mayModify(OWNER, friends, STRANGER));
		assertFalse(LotRules.mayModify(OWNER, friends, null));
		assertFalse(LotRules.mayModify(OWNER, Set.of(), FRIEND), "amigo removido deixa de poder");
	}

	@Test
	void overlapAndVillageBounds() {
		LotRules.Area a = LotRules.around(0, 0);
		assertTrue(a.overlaps(LotRules.around(15, 15)));
		assertFalse(a.overlaps(LotRules.around(16, 0)));
		assertTrue(LotRules.overlapsAny(a, List.of(LotRules.around(100, 100), LotRules.around(-10, 5))));
		assertTrue(LotRules.insideVillage(LotRules.around(20, 0), 0, 0, 64));
		assertFalse(LotRules.insideVillage(LotRules.around(60, 0), 0, 0, 64), "canto fora do raio");
		assertFalse(LotRules.awayFromDesk(LotRules.around(10, 0), 0, 0), "a praça da Prefeitura não se vende");
		assertTrue(LotRules.awayFromDesk(LotRules.around(20, 0), 0, 0));
	}

	@Test
	void priceByLevelWithReputationDiscount() {
		assertEquals(12, LotRules.price(0, 0));
		assertEquals(20, LotRules.price(1, 0));
		assertEquals(32, LotRules.price(2, 0));
		assertEquals(10, LotRules.price(0, 50));
		assertEquals(6, LotRules.price(0, 100000), "desconto no máximo metade");
		assertEquals(12, LotRules.price(0, -500), "reputação negativa não encarece");
	}
}
