package com.tcordeiro.vilas.life;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.4.0 — Festa na Praça: quando a vila sobe de nível ou na véspera do Mercador, o Prefeito avisa («🎉 Amanhã há festa na
 * Praça!»); no dia, das 12 h às 17 h, os aldeões juntam-se na Praça (saltos, ♪, foguetes) e as trocas com aldeões têm um
 * REEMBOLSO de 10 % pago pela TESOURARIA (até 20 CB por jogador e 100 CB por festa; nunca dinheiro novo).
 */
public final class Festa {
	private Festa() {}

	public static final String ICON = "🎉";

	/** Marca a festa para {@code day} e avisa (uma festa de cada vez; nunca duas no mesmo dia). */
	public static boolean schedule(ServerLevel level, Village v, long day, String reason) {
		LifeData.Festa f = v.civic.life.festa;
		long today = VillageEconomy.day(level);
		if (day < today || f.day >= today || f.last == day) return false;
		f.day = day;
		f.reason = reason;
		f.reset();
		decor(level, v, day); // 0.5.0: missão temporária «decorar a festa»
		VillageNews.announce(level, v, ICON, day == today ? "tvilas.news.festa_today" : "tvilas.news.festa", Component.translatable("tvilas.festa." + reason));
		Talk.fact(level, v, null, "festa");
		VillageData.get(level).changed();
		return true;
	}

	/** Para os testes: força a festa a decorrer (null = pelo relógio). */
	public static Boolean forceActive;

	public static boolean today(ServerLevel level, Village v) {
		return v.civic.life.festa.day == VillageEconomy.day(level);
	}

	/** A festa está a decorrer agora? */
	public static boolean active(ServerLevel level, Village v) {
		if (forceActive != null) return forceActive;
		return today(level, v) && LifeRules.festaTime(LifeRules.timeOfDay(level.getOverworldClockTime()));
	}

	/** Acabou o dia da festa: fica registada (para não haver outra no mesmo dia). */
	static void tidy(ServerLevel level, Village v) {
		LifeData.Festa f = v.civic.life.festa;
		long today = VillageEconomy.day(level);
		if (f.day >= 0 && (f.day < today || (f.day == today && LifeRules.timeOfDay(level.getOverworldClockTime()) >= LifeRules.FESTA_END))) finish(level, v);
	}

	/** A festa acabou (ou nunca chegou a ser): fica registada e a missão da festa sai do quadro. */
	public static void finish(ServerLevel level, Village v) {
		LifeData.Festa f = v.civic.life.festa;
		f.last = f.day;
		f.day = -1;
		f.reset();
		clearDecor(v);
		VillageData.get(level).changed();
	}

	/** Um foguete na Praça (só cosmético: não larga nada). */
	static void firework(ServerLevel level, BlockPos at) {
		var r = level.getRandom();
		int[] colors = {0xE02020, 0xF0C020, 0x30A0F0, 0x40D040, 0xD040D0, 0xFFFFFF};
		FireworkExplosion ex = new FireworkExplosion(FireworkExplosion.Shape.values()[r.nextInt(3)],
				it.unimi.dsi.fastutil.ints.IntList.of(colors[r.nextInt(colors.length)], colors[r.nextInt(colors.length)]),
				it.unimi.dsi.fastutil.ints.IntList.of(), r.nextBoolean(), r.nextBoolean());
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1, java.util.List.of(ex)));
		level.addFreshEntity(new FireworkRocketEntity(level, at.getX() + 0.5 + r.nextInt(5) - 2, at.getY() + 1, at.getZ() + 0.5 + r.nextInt(5) - 2, rocket));
	}

	/**
	 * Uma troca durante a festa: a tesouraria devolve 10 % do que o jogador pagou (REGRA {@link LifeRules#festaRebate}). Com o
	 * CubCoin em CubCoins; sem ele, em esmeraldas inteiras (o resto fica para a próxima troca desta festa). Devolve o reembolso pago.
	 */
	public static long onTrade(ServerPlayer p, Village v, MerchantOffer offer) {
		ServerLevel level = p.level();
		if (!active(level, v)) return 0;
		var m = CoinTrades.of(offer);
		long unit = VilasConfig.get().emerald_value_cents;
		long paid = (long) (m.tvilas$emeraldsA() + m.tvilas$emeraldsB()) * unit;
		LifeData.Festa f = v.civic.life.festa;
		long owed = f.owed.getOrDefault(p.getUUID(), 0L);
		long so = f.rebates.getOrDefault(p.getUUID(), 0L) + owed;
		long rebate = LifeRules.festaRebate(paid, so, LifeRules.FESTA_BUDGET - f.spent - owed, v.economy.treasury - owed,
				LifeRules.festaPlayerCap(TVilas.CUBCOIN ? 1 : unit));
		if (rebate <= 0) return 0;
		boolean coins = TVilas.CUBCOIN;
		long due = owed + rebate;
		long now = LifeRules.payable(due, coins ? 1 : unit);
		f.owed.put(p.getUUID(), due - now);
		if (now > 0) {
			v.economy.treasury -= now;
			f.spent += now;
			f.rebates.merge(p.getUUID(), now, Long::sum);
			if (coins) com.tcordeiro.vilas.compat.CubCoinPay.give(p, now);
			else p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, (int) (now / unit)), net.minecraft.util.Prediction.SERVER_ONLY);
			VillageNews.tell(p, ICON, "tvilas.festa.rebate", VillageManager.money(now));
		}
		VillageData.get(level).changed();
		return now;
	}

	// ---------------------------------------------------------------- 0.5.0: missão «decorar a festa»

	/** Põe no quadro (4.ª casa) a missão temporária da festa; o lote de 4 dias não muda. */
	static void decor(ServerLevel level, Village v, long festaDay) {
		if (!v.civic.meta.real) return;
		clearDecor(v);
		var list = com.tcordeiro.vilas.logic.MissionRules.FESTA_DECOR;
		var t = list.get(level.getRandom().nextInt(list.size()));
		v.civic.life.town.decor = com.tcordeiro.vilas.village.Mission.of(++v.missionCounter, t);
		v.civic.life.town.decorDay = festaDay;
	}

	/** A festa acabou: a missão sai do quadro (e o progresso dela). */
	static void clearDecor(Village v) {
		TownData t = v.civic.life.town;
		if (t.decor != null) VillageManager.dropMission(v, t.decor);
		t.decor = null;
		t.decorDay = -1;
	}
}
