package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.DebtRules;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Consequência das dívidas: depois de {@code debt_freeze_days} dias a dever, os terrenos do devedor nesta vila ficam
 * CONGELADOS — ele (e os amigos) não constroem nem partem lá até pagar. NADA é apagado, tirado ou despejado: o terreno fica
 * exatamente como estava. Um dia antes o Prefeito avisa (uma linha). Pagar no quadro (ou pagar tudo de outra forma) descongela.
 */
public final class Debts {
	private Debts() {}

	/** Dívida mínima que conta: sem CubCoin, uma esmeralda inteira (é o que se pode cobrar). */
	public static long minCents() {
		VilasConfig c = VilasConfig.get();
		return TVilas.CUBCOIN ? c.debt_min_cents : Math.max(c.debt_min_cents, c.emerald_value_cents);
	}

	public static boolean frozen(Village v, UUID owner) {
		return owner != null && v.extra.frozen.contains(owner);
	}

	/** Uma vez por dia: conta os dias em dívida, avisa na véspera e congela. */
	public static void daily(ServerLevel level, Village v, long day) {
		Village.Extra x = v.extra;
		int days = VilasConfig.get().debt_freeze_days;
		long min = minCents();
		for (UUID who : new ArrayList<>(v.economy.debts.keySet())) {
			if (DebtRules.counts(VillageEconomy.debt(v, who), min)) x.debtSince.putIfAbsent(who, day);
			else x.debtSince.remove(who);
		}
		for (UUID who : new ArrayList<>(x.debtSince.keySet())) {
			if (!DebtRules.counts(VillageEconomy.debt(v, who), min)) {
				cleared(level, v, who);
				continue;
			}
			long since = x.debtSince.get(who);
			if (days > 0 && DebtRules.frozen(since, day, days)) {
				if (!x.frozen.contains(who)) freeze(level, v, who);
			} else if (days > 0 && DebtRules.warnToday(since, day, days) && x.warned.add(who)) {
				VillageNews.tell(player(level, who), VillageNews.WARN, "tvilas.news.debt_warning", VillageManager.money(VillageEconomy.debt(v, who)));
			}
		}
		for (UUID who : new ArrayList<>(x.frozen)) if (VillageEconomy.debt(v, who) <= 0) cleared(level, v, who);
	}

	/** Congela os terrenos do devedor nesta vila. NÃO mexe em nenhum bloco: só marca. */
	public static void freeze(ServerLevel level, Village v, UUID who) {
		v.extra.frozen.add(who);
		VillageNews.tell(player(level, who), VillageNews.FROZEN, "tvilas.news.debt_frozen", VillageManager.money(VillageEconomy.debt(v, who)));
		VillageData.get(level).changed();
	}

	/** A dívida foi paga: tudo volta ao normal. */
	public static void cleared(ServerLevel level, Village v, UUID who) {
		Village.Extra x = v.extra;
		boolean was = x.frozen.remove(who);
		x.debtSince.remove(who);
		x.warned.remove(who);
		if (was) VillageNews.tell(player(level, who), VillageNews.PAID, "tvilas.news.debt_unfrozen");
		VillageData.get(level).changed();
	}

	static ServerPlayer player(ServerLevel level, UUID who) {
		return level.getServer().getPlayerList().getPlayer(who);
	}

	/** Pagar a dívida TODA no quadro. Com o CubCoin, em moedas; sem ele, em esmeraldas (arredondado para cima). */
	public static VillageManager.Result pay(ServerPlayer p, Village v) {
		ServerLevel level = p.level();
		UUID who = p.getUUID();
		long debt = VillageEconomy.debt(v, who);
		if (debt <= 0) return VillageManager.Result.fail("tvilas.debt.none");
		long paid;
		if (TVilas.CUBCOIN) {
			if (com.tcordeiro.vilas.compat.CubCoinPay.balance(p) < debt || !com.tcordeiro.vilas.compat.CubCoinPay.pay(p, debt))
				return VillageManager.Result.fail("tvilas.debt.no_money", VillageManager.money(debt));
			paid = debt;
		} else {
			long value = VilasConfig.get().emerald_value_cents;
			long n = DebtRules.emeraldsToClear(debt, value);
			if (VillageManager.count(p.getInventory(), Items.EMERALD) < n) return VillageManager.Result.fail("tvilas.debt.no_emeralds", n);
			VillageManager.take(p.getInventory(), Items.EMERALD, (int) n);
			paid = n * value;
		}
		v.economy.debts.remove(who);
		v.economy.treasury += paid;
		v.economy.collected += paid;
		cleared(level, v, who);
		return VillageManager.Result.ok("tvilas.debt.paid", VillageManager.money(debt));
	}
}
