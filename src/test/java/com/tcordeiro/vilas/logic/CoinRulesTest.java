package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CoinRulesTest {
	@Test
	void priceIsFortyCbPerEmerald() {
		assertEquals(4000, CoinRules.price(1, 4000));
		assertEquals(256000, CoinRules.price(64, 4000));
		assertEquals(0, CoinRules.price(-3, 4000));
	}

	@Test
	void displayIsExactWhenItCan() {
		assertEquals(List.of(new CoinRules.Pile(2000, 2)), CoinRules.display(4000, 2), "1 esmeralda: 2 notas de 20");
		assertEquals(List.of(new CoinRules.Pile(10000, 4)), CoinRules.display(40000, 1), "10 esmeraldas: 4 notas de 100");
		// 33 esmeraldas (1320 CB) não cabem num monte: 13 notas de 100 + 1 de 20
		var two = CoinRules.display(132000, 2);
		assertEquals(2, two.size());
		assertTrue(CoinRules.exact(two, 132000));
		var one = CoinRules.display(132000, 1);
		assertEquals(1, one.size());
		assertTrue(CoinRules.total(one) < 132000, "um só monte inexato fica abaixo do preço (e leva o preço escrito)");
		for (int e = 1; e <= 64; e++) {
			var piles = CoinRules.display(CoinRules.price(e, 4000), 2);
			assertTrue(CoinRules.exact(piles, CoinRules.price(e, 4000)), "exato com 2 montes: " + e);
			for (var p : piles) assertTrue(p.count() >= 1 && p.count() <= 64);
		}
	}

	@Test
	void paymentNeverCreatesMoney() {
		long[] prices = {1, 99, 3000, 3599, 4000, 8000, 12345, 256000};
		List<List<CoinRules.Pile>> wallets = List.of(
				List.of(new CoinRules.Pile(10000, 3)),
				List.of(new CoinRules.Pile(1, 64), new CoinRules.Pile(10000, 1)),
				List.of(new CoinRules.Pile(2000, 2), new CoinRules.Pile(25, 7)),
				List.of(new CoinRules.Pile(10000, 64), new CoinRules.Pile(0, 0)),
				List.of(new CoinRules.Pile(2000, 1), new CoinRules.Pile(25, 64))); // 30 CB: só as duas casas juntas pagam
		for (var w : wallets)
			for (long price : prices) {
				var pay = CoinRules.pay(w, price);
				if (CoinRules.total(w) < price) {
					assertNull(pay, "não chega: não tira nada");
					continue;
				}
				for (int i = 0; i < w.size(); i++) assertTrue(pay.units()[i] >= 0 && pay.units()[i] <= w.get(i).count());
				assertEquals(price, pay.taken(w) - pay.change(), "sai o preço, nem mais nem menos: " + w + " " + price);
				assertTrue(pay.change() >= 0);
			}
	}

	@Test
	void payPrefersOneSlotWithLittleChange() {
		var w = List.of(new CoinRules.Pile(1, 64), new CoinRules.Pile(10000, 1));
		var pay = CoinRules.pay(w, 4000);
		assertEquals(0, pay.units()[0], "não gasta as moedas pequenas");
		assertEquals(1, pay.units()[1]);
		assertEquals(6000, pay.change());
	}
}
