package com.tcordeiro.vilas.life.election;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 0.6 eleições — As regras puras da votação de 3 dias, do desempate e dos bónus do voto. */
class VoteRulesTest {
	@Test
	void votingLastsThreeDaysAndCountsAtTheEnd() {
		assertFalse(VoteRules.open(-1, 5), "nenhuma aberta");
		assertTrue(VoteRules.open(8, 8));
		assertTrue(VoteRules.open(8, 10), "3.º dia ainda aberta");
		assertFalse(VoteRules.open(8, 11), "fecha no fim do 3.º dia");
		assertFalse(VoteRules.open(8, 7));
		assertFalse(VoteRules.countNow(8, 10), "não conta antes do fim");
		assertTrue(VoteRules.countNow(8, 11));
		assertFalse(VoteRules.countNow(-1, 99));
		assertEquals(3 * 24000L, VoteRules.ticksLeft(8, 8 * 24000L, 24000));
		assertEquals(1000L, VoteRules.ticksLeft(8, 11 * 24000L - 1000, 24000));
		assertEquals(0L, VoteRules.ticksLeft(8, 12 * 24000L, 24000), "nunca negativo");
	}

	@Test
	void tieGoesToTheFavoriteThenTheIncumbentThenASeededDraw() {
		assertEquals(-1, VoteRules.winner(new int[] {0, 0}, new long[] {9, 0}, 0, 1), "sem votos nada muda");
		assertEquals(1, VoteRules.winner(new int[] {1, 2}, new long[] {99, 0}, 0, 1), "o mais votado ganha, mesmo menos querido");
		assertEquals(1, VoteRules.winner(new int[] {2, 2}, new long[] {3, 7}, 0, 1), "empate: o mais querido");
		assertEquals(0, VoteRules.winner(new int[] {2, 2, 1}, new long[] {5, 5, 99}, 0, 1), "empate na estima: a proposta em vigor");
		for (long s = 0; s < 32; s++) assertEquals(1, VoteRules.winner(new int[] {2, 2}, new long[] {5, 5}, 1, s), "a proposta em vigor ganha qualquer sorteio");
		int a = VoteRules.winner(new int[] {2, 2}, new long[] {5, 5}, -1, 42), b = VoteRules.winner(new int[] {2, 2}, new long[] {5, 5}, -1, 42);
		assertEquals(a, b, "o sorteio com a mesma semente dá sempre o mesmo");
		assertTrue(a == 0 || a == 1);
		boolean[] seen = new boolean[2];
		for (long s = 0; s < 64; s++) seen[VoteRules.winner(new int[] {1, 1}, new long[] {0, 0}, -1, s)] = true;
		assertTrue(seen[0] && seen[1], "o sorteio escolhe os dois, conforme a semente");
		assertEquals(10L, VoteRules.likes(10, -5), "reputação negativa não tira");
		assertEquals(15L, VoteRules.likes(10, 5));
	}

	@Test
	void reputationOncePerElection() {
		assertEquals(VoteRules.VOTE_REP, VoteRules.voteReputation(false));
		assertEquals(0, VoteRules.voteReputation(true));
	}

	@Test
	void bonusesOnlyForSupportersDuringTheirTerm() {
		assertTrue(VoteRules.supporter(true, 3, 3));
		assertFalse(VoteRules.supporter(false, 3, 3), "não votou no vencedor");
		assertFalse(VoteRules.supporter(true, 3, 4), "o mandato acabou");
		assertFalse(VoteRules.supporter(true, 0, 0), "nenhum mandato");
		assertTrue(VoteRules.promised(true, "tax", "tax"));
		assertFalse(VoteRules.promised(true, "guards", "tax"), "outra proposta");
		assertFalse(VoteRules.promised(false, "tax", "tax"));
	}

	@Test
	void supporterRebateHasCapsAndNeverCreatesMoney() {
		assertEquals(50L, VoteRules.rebate(true, 1000, 5, 0, 2000, 10000, 100000));
		assertEquals(0L, VoteRules.rebate(false, 1000, 5, 0, 2000, 10000, 100000), "só apoiadores");
		assertEquals(10L, VoteRules.rebate(true, 1000, 5, 1990, 2000, 10000, 100000), "teto por jogador");
		assertEquals(7L, VoteRules.rebate(true, 1000, 5, 0, 2000, 7, 100000), "teto do mandato");
		assertEquals(3L, VoteRules.rebate(true, 1000, 5, 0, 2000, 10000, 3), "nunca mais que a tesouraria");
		assertEquals(0L, VoteRules.rebate(true, 1000, 5, 0, 2000, 10000, -50), "nunca negativo");
		assertEquals(0L, VoteRules.rebate(true, 1000, 5, 2500, 2000, 10000, 100000));
		assertEquals(4000L, VoteRules.supporterPlayerCap(4000), "sem CubCoin pelo menos uma esmeralda");
	}

	@Test
	void promisesOnlyLowerTaxesAndAreCapped() {
		assertEquals(80L, VoteRules.lotTax(100, true), "−20 %");
		assertEquals(100L, VoteRules.lotTax(100, false));
		assertEquals(0L, VoteRules.lotTax(-5, true), "nunca negativo");
		assertEquals(0L, VoteRules.ownStallSaleTax(30, true));
		assertEquals(30L, VoteRules.ownStallSaleTax(30, false));
		assertTrue(VoteRules.mayReserve(true, false, 0));
		assertFalse(VoteRules.mayReserve(false, false, 0), "só apoiadores");
		assertFalse(VoteRules.mayReserve(true, true, 0), "um por lote");
		assertFalse(VoteRules.mayReserve(true, false, VoteRules.RESERVED_PER_VILLAGE), "teto por vila");
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		assertTrue(VoteRules.mayTakeRequest(null, a), "pedido de todos");
		assertTrue(VoteRules.mayTakeRequest(a, a));
		assertFalse(VoteRules.mayTakeRequest(a, b), "exclusivo de outro");
	}
}
