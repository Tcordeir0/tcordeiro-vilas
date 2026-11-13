package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 0.5.0 — As regras que pagam ou cobram (correio, mercado, eleições, ajudante, peixe) e os gestos do EMF. */
class TownRulesTest {
	@Test
	void mailCapsAndReturn() {
		assertTrue(TownRules.mayPost(0, 0));
		assertFalse(TownRules.mayPost(TownRules.MAIL_PER_PLAYER, 0), "teto por jogador");
		assertFalse(TownRules.mayPost(0, TownRules.MAIL_PER_VILLAGE), "teto por vila");
		assertFalse(TownRules.returnToSender(TownRules.MAIL_TRIES - 1));
		assertTrue(TownRules.returnToSender(TownRules.MAIL_TRIES), "à 3.ª volta para quem mandou");
	}

	@Test
	void marketDaysOncePerWeekPerVillage() {
		int n = 0;
		for (long d = 1; d <= 70; d++) if (TownRules.marketDay(d, 7, 3)) n++;
		assertEquals(10, n, "um dia por semana");
		assertFalse(TownRules.marketDay(5, 0, 0), "0 = sem mercado");
		assertTrue(TownRules.marketTime(TownRules.MARKET_START));
		assertFalse(TownRules.marketTime(TownRules.MARKET_END));
	}

	@Test
	void marketRebateNeverCreatesMoney() {
		assertEquals(1500, TownRules.rebate(100_000, 15, 0, 1500, 8000, 1_000_000), "teto por jogador");
		assertEquals(600, TownRules.rebate(4000, 15, 0, 1500, 8000, 1_000_000), "15 %");
		assertEquals(0, TownRules.rebate(4000, 15, 1500, 1500, 8000, 1_000_000), "teto atingido");
		assertEquals(100, TownRules.rebate(4000, 15, 0, 1500, 100, 1_000_000), "orçamento do mercado");
		assertEquals(50, TownRules.rebate(4000, 15, 0, 1500, 8000, 50), "nunca mais do que a tesouraria tem");
		assertEquals(0, TownRules.rebate(4000, 15, 0, 1500, 8000, -5), "tesouraria negativa: nada");
		assertEquals(2250, TownRules.marketPlayerCap(true, 1), "mercado maior: +50 %");
		assertEquals(4000, TownRules.marketPlayerCap(false, 4000), "sem CubCoin: pelo menos uma esmeralda");
	}

	@Test
	void electionsVoteAndPolicies() {
		assertTrue(TownRules.electionDay(8, 8));
		assertFalse(TownRules.electionDay(0, 8), "nunca no dia 0");
		assertFalse(TownRules.electionDay(8, 0), "0 = sem eleições");
		assertTrue(TownRules.mayVote(true, false, true));
		assertFalse(TownRules.mayVote(false, false, true), "sem terreno não vota");
		assertFalse(TownRules.mayVote(true, true, true), "uma vez só");
		assertFalse(TownRules.mayVote(true, false, false), "fora do dia de votar");
		assertEquals(1, TownRules.winner(new int[] {1, 3, 2}, -1));
		assertEquals(2, TownRules.winner(new int[] {2, 0, 2}, 2), "empate: a política em vigor");
		assertEquals(0, TownRules.winner(new int[] {2, 0, 2}, -1), "empate sem política: o primeiro");
		assertEquals(-1, TownRules.winner(new int[] {0, 0}, 0), "sem votos nada muda");
		assertEquals(1.0, TownRules.taxWithPolicy(2.0, TownRules.TAX), 1e-9);
		assertEquals(0.0, TownRules.taxWithPolicy(0.5, TownRules.TAX), 1e-9, "nunca negativo");
		assertEquals(2.0, TownRules.taxWithPolicy(2.0, TownRules.GUARDS), 1e-9);
		assertEquals(3, TownRules.guardPostsWithPolicy(2, TownRules.GUARDS));
		assertEquals(5, TownRules.guardPostsWithPolicy(5, TownRules.GUARDS), "no máximo 5");
		assertEquals(2, TownRules.guardPostsWithPolicy(2, TownRules.TAX));
	}

	@Test
	void helperRules() {
		assertTrue(TownRules.mayHire(false, false, 0, true));
		assertFalse(TownRules.mayHire(true, false, 0, true), "um por jogador");
		assertFalse(TownRules.mayHire(false, true, 0, true), "um contrato por dia");
		assertFalse(TownRules.mayHire(false, false, TownRules.HELPERS_PER_VILLAGE, true), "teto da vila");
		assertFalse(TownRules.mayHire(false, false, 0, false), "o aldeão tem de estar livre");
		assertEquals(1, TownRules.priceInEmeralds(2000, 4000));
		assertEquals(2, TownRules.priceInEmeralds(4001, 4000));
		UUID me = UUID.randomUUID(), other = UUID.randomUUID(), friend = UUID.randomUUID();
		assertTrue(TownRules.helperMayEnter(null, Set.of(), me), "a rua é de todos");
		assertTrue(TownRules.helperMayEnter(me, Set.of(), me));
		assertTrue(TownRules.helperMayEnter(other, Set.of(me), me), "amigo do terreno");
		assertFalse(TownRules.helperMayEnter(other, Set.of(friend), me), "terreno alheio: não entra");
		assertFalse(TownRules.helperMayHarvest(null, Set.of(), me), "fora de terrenos não colhe (campos da vila)");
		assertTrue(TownRules.helperMayHarvest(me, Set.of(), me));
		assertFalse(TownRules.helperMayHarvest(other, Set.of(), me), "nunca no terreno dos outros");
	}

	@Test
	void fishAndPlay() {
		assertTrue(TownRules.mayRestock(TownRules.CATCHES_PER_DAY - 1));
		assertFalse(TownRules.mayRestock(TownRules.CATCHES_PER_DAY));
		assertTrue(TownRules.playTime(500));
		assertFalse(TownRules.playTime(5000), "nas horas de aprendiz não");
		assertTrue(TownRules.playTime(10000));
		assertFalse(TownRules.playTime(13000), "de noite não");
	}

	@Test
	void gestureVariables() {
		assertEquals(2f, GestureRules.code(2, 100, 130, 110));
		assertEquals(0f, GestureRules.code(2, 100, 130, 130), "acabou");
		assertEquals(0f, GestureRules.code(2, 100, 130, 99), "ainda não começou");
		assertEquals(0.5f, GestureRules.progress(100, 120, 110), 1e-6);
		assertEquals(0f, GestureRules.progress(100, 100, 100));
		for (int c = 0; c <= 6; c++) for (float v : GestureRules.deltas(c, 0f)) assertEquals(0f, v, "nada antes do gesto");
		assertTrue(GestureRules.deltas(1, 0.5f)[GestureRules.ARMS_X] < -1f, "acenar levanta os braços");
		assertTrue(Math.abs(GestureRules.deltas(5, 0.125f)[GestureRules.HEAD_Y]) > 0.3f, "«não» vira a cabeça");
		for (float v : GestureRules.deltas(0, 0.5f)) assertEquals(0f, v, "código 0: nada");
		for (float v : GestureRules.deltas(3, 0.5f)) assertTrue(Math.abs(v) <= 1.6f, "pequeno: não desmonta o modelo");
	}
}
