package com.tcordeiro.vilas.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tcordeiro.cubcoin.CubCoin;
import com.tcordeiro.cubcoin.Money;
import com.tcordeiro.cubcoin.MoneyItem;
import com.tcordeiro.cubcoin.Wallet;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.economy.MoneyOffer;
import com.tcordeiro.vilas.logic.CoinRules;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — As trocas dos aldeões em CubCoin (ver {@link CoinTrades}). Só é carregada com o CubCoin instalado.
 * <ul>
 * <li>Mostrar: o preço em 1 ou 2 montes de notas/moedas ({@link CoinRules#display}); se não houver forma exata, o monte leva o
 * preço escrito no nome.</li>
 * <li>Pagar: vale o VALOR das notas/moedas postas nas casas (qualquer mistura). Sai o preço e o troco volta ao inventário no fim
 * da troca ({@link CoinTrades#finishTake}). REGRA DE SEGURANÇA: o que sai das casas menos o troco é exatamente o preço; nada é
 * criado (o que o aldeão paga é o valor da troca original em esmeraldas, ao preço do Balcão).</li>
 * </ul>
 */
public final class MoneyTrades {
	private MoneyTrades() {}

	public static boolean isMoney(ItemStack s) {
		return s.getItem() instanceof MoneyItem;
	}

	static long value(ItemStack s) {
		return s.getItem() instanceof MoneyItem ? Wallet.valueOf(s) : 0;
	}

	static ItemStack stack(CoinRules.Pile p) {
		for (Money m : Money.values()) if (m.cents == p.denom()) return new ItemStack(CubCoin.ITEMS.get(m), Math.max(1, p.count()));
		throw new IllegalStateException("valor do CubCoin desconhecido: " + p.denom());
	}

	static long cents(int emeralds) {
		return CoinRules.price(emeralds, CoinTrades.emeraldCents());
	}

	static MoneyOffer m(MerchantOffer o) {
		return CoinTrades.of(o);
	}

	/** A 2.ª casa também é de dinheiro? (pede esmeraldas, ou não pede nada e a 1.ª pede esmeraldas) */
	static boolean moneyB(MerchantOffer o) {
		return m(o).tvilas$emeraldsB() > 0 || m(o).tvilas$emeraldsA() > 0 && o.getItemCostB().isEmpty();
	}

	/** O que se paga em dinheiro nesta troca (cêntimos). */
	public static long price(MerchantOffer o) {
		return cents(m(o).tvilas$emeraldsA() + m(o).tvilas$emeraldsB());
	}

	static ItemStack named(ItemStack s, long cents, boolean exact) {
		if (!exact) s.set(DataComponents.CUSTOM_NAME, Component.translatable("tvilas.coin.price", Money.format(cents)));
		return s;
	}

	public static ItemStack displayA(MerchantOffer o) {
		long p = price(o);
		List<CoinRules.Pile> piles = CoinRules.display(p, moneyB(o) ? 2 : 1);
		return named(stack(piles.get(0)), p, CoinRules.exact(piles, p));
	}

	/** O preço base (sem procura nem descontos) mostrado como a 1.ª casa: para o riscado do ecrã de trocas. */
	public static ItemStack displayBaseA(MerchantOffer o, int baseEmeralds) {
		long p = cents(baseEmeralds + m(o).tvilas$emeraldsB());
		List<CoinRules.Pile> piles = CoinRules.display(p, moneyB(o) ? 2 : 1);
		return named(stack(piles.get(0)), p, CoinRules.exact(piles, p));
	}

	public static ItemStack displayB(MerchantOffer o) {
		MoneyOffer x = m(o);
		if (x.tvilas$emeraldsA() > 0 && !moneyB(o)) return o.getItemCostB().map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
		long p = price(o);
		if (x.tvilas$emeraldsA() == 0) { // só a 2.ª casa pede dinheiro
			List<CoinRules.Pile> piles = CoinRules.display(p, 1);
			return named(stack(piles.get(0)), p, CoinRules.exact(piles, p));
		}
		List<CoinRules.Pile> piles = CoinRules.display(p, 2);
		return piles.size() > 1 ? stack(piles.get(1)) : ItemStack.EMPTY;
	}

	/** O que o aldeão dá: o valor das esmeraldas da troca num monte (o que não couber exato vai de troco no fim). */
	public static ItemStack resultStack(MerchantOffer o) {
		long r = cents(m(o).tvilas$emeraldsResult());
		CoinRules.Pile one = CoinRules.single(r);
		return stack(one != null ? one : CoinRules.floor(r));
	}

	static boolean matches(ItemCost cost, int count, ItemStack s) {
		return cost.test(s) && s.getCount() >= count;
	}

	public static boolean satisfied(MerchantOffer o, ItemCost costA, int modA, Optional<ItemCost> costB, ItemStack a, ItemStack b) {
		MoneyOffer x = m(o);
		boolean moneyA = x.tvilas$emeraldsA() > 0, moneyB = moneyB(o);
		long p = price(o);
		if (moneyA && moneyB) {
			if (!a.isEmpty() && !isMoney(a) || !b.isEmpty() && !isMoney(b)) return false;
			return p > 0 && value(a) + value(b) >= p;
		}
		if (moneyA) return isMoney(a) && value(a) >= p && costB.isPresent() && matches(costB.get(), costB.get().count(), b);
		if (moneyB) return matches(costA, modA, a) && isMoney(b) && value(b) >= p;
		if (!matches(costA, modA, a)) return false;
		return costB.isEmpty() ? b.isEmpty() : matches(costB.get(), costB.get().count(), b);
	}

	public static boolean take(MerchantOffer o, ItemCost costA, int modA, Optional<ItemCost> costB, ItemStack a, ItemStack b) {
		if (!satisfied(o, costA, modA, costB, a, b)) return false;
		MoneyOffer x = m(o);
		boolean moneyA = x.tvilas$emeraldsA() > 0, moneyB = moneyB(o);
		if (!moneyA) a.shrink(modA);
		if (!moneyB && costB.isPresent()) b.shrink(costB.get().count());
		List<ItemStack> money = new ArrayList<>();
		if (moneyA) money.add(a);
		if (moneyB) money.add(b);
		long p = price(o);
		if (p > 0) {
			List<CoinRules.Pile> piles = new ArrayList<>();
			for (ItemStack s : money)
				piles.add(isMoney(s) ? new CoinRules.Pile(((MoneyItem) s.getItem()).money.cents, s.getCount()) : new CoinRules.Pile(0, 0));
			CoinRules.Payment pay = CoinRules.pay(piles, p);
			if (pay == null) return false; // não chega (satisfied já o viu; por segurança não tira nada)
			for (int i = 0; i < money.size(); i++) money.get(i).shrink(pay.units()[i]);
			CoinTrades.owe(pay.change());
		}
		// o resultado que não coube exato num monte: o resto vai de troco
		long r = cents(x.tvilas$emeraldsResult());
		if (r > 0) CoinTrades.owe(r - value(resultStack(o)));
		return true;
	}

	/** Dá o troco (ou o resto) ao jogador: no inventário, nas maiores notas; o que não couber cai aos pés. */
	public static void give(net.minecraft.world.entity.player.Player p, long cents) {
		if (cents > 0) Wallet.give(p, cents);
	}

	// ------------------------------------------------------------------ escolher a troca (pôr o dinheiro nas casas)

	/**
	 * Escolher uma troca no ecrã: põe nas casas notas/moedas do inventário que paguem o preço — de preferência um só valor (o
	 * menor que chegue, para guardar as notas grandes); senão os dois valores com mais dinheiro (se houver 2 casas de dinheiro).
	 * Só MOVE itens do inventário para as casas (nada é criado nem apagado).
	 */
	public static void fill(MerchantOffer o, MerchantContainer tc, List<Slot> slots, boolean slotB) {
		long p = price(o);
		if (p <= 0) return;
		boolean two = m(o).tvilas$emeraldsA() > 0 && moneyB(o);
		int first = slotB ? 1 : 0;
		long[] units = new long[CoinRules.DENOMS.length];
		for (int i = 3; i < 39 && i < slots.size(); i++) {
			ItemStack s = slots.get(i).getItem();
			if (isMoney(s)) units[index(((MoneyItem) s.getItem()).money.cents)] += s.getCount();
		}
		for (int d = 0; d < units.length; d++) {
			long can = Math.min(CoinRules.MAX_STACK, units[d]);
			if (can * CoinRules.DENOMS[d] >= p) {
				move(tc, slots, first, d, (int) can);
				return;
			}
		}
		// nenhum valor sozinho chega: os dois com mais dinheiro (ou só o maior)
		int best = -1, second = -1;
		for (int d = 0; d < units.length; d++) {
			long v = Math.min(CoinRules.MAX_STACK, units[d]) * CoinRules.DENOMS[d];
			if (v <= 0) continue;
			if (best < 0 || v > Math.min(CoinRules.MAX_STACK, units[best]) * CoinRules.DENOMS[best]) {
				second = best;
				best = d;
			} else if (second < 0 || v > Math.min(CoinRules.MAX_STACK, units[second]) * CoinRules.DENOMS[second]) second = d;
		}
		if (best >= 0) move(tc, slots, first, best, (int) Math.min(CoinRules.MAX_STACK, units[best]));
		if (two && second >= 0) move(tc, slots, 1, second, (int) Math.min(CoinRules.MAX_STACK, units[second]));
	}

	static int index(long denom) {
		for (int i = 0; i < CoinRules.DENOMS.length; i++) if (CoinRules.DENOMS[i] == denom) return i;
		return 0;
	}

	/** Tira {@code n} unidades do valor {@code d} do inventário (casas 3–38 do ecrã) e põe-nas na casa de pagamento. */
	static void move(MerchantContainer tc, List<Slot> slots, int pay, int d, int n) {
		ItemStack cur = tc.getItem(pay);
		if (!cur.isEmpty()) return; // a casa já tem alguma coisa: não mistura
		ItemStack out = ItemStack.EMPTY;
		for (int i = 3; i < 39 && i < slots.size() && n > 0; i++) {
			ItemStack s = slots.get(i).getItem();
			if (!isMoney(s) || ((MoneyItem) s.getItem()).money.cents != CoinRules.DENOMS[d]) continue;
			int t = Math.min(n, s.getCount());
			if (out.isEmpty()) out = s.copyWithCount(t);
			else out.grow(t);
			s.shrink(t);
			n -= t;
		}
		if (!out.isEmpty()) tc.setItem(pay, out);
	}
}
