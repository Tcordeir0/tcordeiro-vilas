package com.tcordeiro.vilas.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.0 — As trocas dos aldeões em CubCoin (lógica pura, testada com JUnit; sem o CubCoin isto não é usado). Tudo em cêntimos
 * (1 CB = 100¢); uma esmeralda vale {@code emerald_value_cents} (40 CB, o preço do Balcão de Câmbio).
 * <ul>
 * <li>{@link #display}: como mostrar um preço no ecrã de trocas, em 1 ou 2 montes de notas/moedas (no máximo 64 cada);</li>
 * <li>{@link #pay}: que notas/moedas sair das casas de pagamento e quanto volta de troco. REGRA DE SEGURANÇA (sem duplicar
 * dinheiro): o que sai das casas menos o troco é exatamente o preço.</li>
 * </ul>
 */
public final class CoinRules {
	private CoinRules() {}

	/** Os valores do CubCoin (cêntimos), do menor para o maior — iguais a {@code com.tcordeiro.cubcoin.Money}. */
	public static final long[] DENOMS = {1, 5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
	public static final int MAX_STACK = 64;

	/** Um monte para mostrar: o valor de uma unidade e quantas. */
	public record Pile(long denom, int count) {
		public long value() {
			return denom * count;
		}
	}

	/** O preço de {@code emeralds} esmeraldas. */
	public static long price(int emeralds, long emeraldCents) {
		return Math.max(0, emeralds) * Math.max(0, emeraldCents);
	}

	/** O preço cabe EXATAMENTE num só monte? (o maior valor que divide o preço e não passa de 64 unidades) */
	public static Pile single(long cents) {
		if (cents <= 0) return null;
		for (int i = DENOMS.length - 1; i >= 0; i--)
			if (cents % DENOMS[i] == 0 && cents / DENOMS[i] <= MAX_STACK) return new Pile(DENOMS[i], (int) (cents / DENOMS[i]));
		return null;
	}

	/**
	 * Como mostrar {@code cents} em no máximo {@code maxPiles} montes (1 ou 2). Devolve os montes; se não houver forma exata, o
	 * maior monte possível que NÃO passa do preço ({@link #exact} diz se é exato — no ecrã leva o preço escrito no nome).
	 */
	public static List<Pile> display(long cents, int maxPiles) {
		List<Pile> out = new ArrayList<>();
		if (cents <= 0) return out;
		Pile one = single(cents);
		if (one != null) {
			out.add(one);
			return out;
		}
		if (maxPiles >= 2)
			for (int i = DENOMS.length - 1; i >= 0; i--) {
				long n = Math.min(MAX_STACK, cents / DENOMS[i]);
				if (n <= 0) continue;
				Pile rest = single(cents - n * DENOMS[i]);
				if (rest != null && rest.denom() < DENOMS[i]) {
					out.add(new Pile(DENOMS[i], (int) n));
					out.add(rest);
					return out;
				}
			}
		out.add(floor(cents));
		return out;
	}

	/** O maior monte que não passa de {@code cents} (o maior valor com pelo menos 1 unidade, até 64). */
	public static Pile floor(long cents) {
		for (int i = DENOMS.length - 1; i >= 0; i--)
			if (cents >= DENOMS[i]) return new Pile(DENOMS[i], (int) Math.min(MAX_STACK, cents / DENOMS[i]));
		return new Pile(DENOMS[0], 0);
	}

	public static long total(List<Pile> piles) {
		long t = 0;
		for (Pile p : piles) t += p.value();
		return t;
	}

	public static boolean exact(List<Pile> piles, long cents) {
		return total(piles) == cents;
	}

	/** Resultado de um pagamento: quantas unidades sair de cada casa (pela ordem dada) e o troco a devolver. */
	public record Payment(int[] units, long change) {
		public long taken(List<Pile> slots) {
			long t = 0;
			for (int i = 0; i < units.length; i++) t += units[i] * slots.get(i).denom();
			return t;
		}
	}

	/**
	 * Paga {@code price} com o dinheiro das casas ({@code slots}: valor e quantidade de cada casa; casas vazias com 0). Começa pelas
	 * moedas de menor valor (como a carteira do CubCoin) e devolve o troco. Null se não chegar (não se tira nada).
	 */
	public static Payment pay(List<Pile> slots, long price) {
		if (price <= 0) return new Payment(new int[slots.size()], 0);
		if (total(slots) < price) return null;
		// primeiro: uma só casa que chegue, com o menor troco (não mexe nas outras)
		int best = -1;
		long bestChange = Long.MAX_VALUE;
		for (int i = 0; i < slots.size(); i++) {
			Pile s = slots.get(i);
			if (s.denom() <= 0 || s.value() < price) continue;
			long u = (price + s.denom() - 1) / s.denom();
			long change = u * s.denom() - price;
			if (change < bestChange) {
				best = i;
				bestChange = change;
			}
		}
		if (best >= 0) {
			int[] units = new int[slots.size()];
			units[best] = (int) ((price + slots.get(best).denom() - 1) / slots.get(best).denom());
			return new Payment(units, bestChange);
		}
		Integer[] order = new Integer[slots.size()];
		for (int i = 0; i < order.length; i++) order[i] = i;
		java.util.Arrays.sort(order, (a, b) -> Long.compare(slots.get(a).denom(), slots.get(b).denom()));
		int[] units = new int[slots.size()];
		long need = price;
		for (int idx : order) {
			if (need <= 0) break;
			Pile s = slots.get(idx);
			if (s.count() <= 0 || s.denom() <= 0) continue;
			int u = (int) Math.min(s.count(), (need + s.denom() - 1) / s.denom());
			units[idx] = u;
			need -= u * s.denom();
		}
		return new Payment(units, -need);
	}
}
