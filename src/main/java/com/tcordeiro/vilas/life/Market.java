package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.registry.VilasItems;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.5.0 — Mercado semanal na Praça: um dia por semana (dia próprio de cada vila, {@code market_every_days}), das 7 h às 15 h, os
 * vendedores da vila montam BANCAS na Praça (modelo do Blender, cenário que se apaga no fim) e ficam lá a apregoar; chega também um
 * vendedor de fora (o vendedor ambulante do jogo, que se vai embora no fim). Quem troca nas bancas recebe um REEMBOLSO de 15 % pago
 * pela TESOURARIA, com teto por jogador (15 CB) e por mercado (80 CB); nunca dinheiro novo. O Prefeito avisa na véspera; às 7 h o
 * sino toca e os vendedores apregoam.
 */
public final class Market {
	private Market() {}

	public static final String TRADER_TAG = "tvilas.market_trader";

	static long offset(Village v) {
		return Math.floorMod(v.id.getLeastSignificantBits(), Math.max(1, every()));
	}

	static int every() {
		return VilasConfig.get().market_every_days;
	}

	/** Para os testes: força o mercado a decorrer (null = pelo relógio). */
	public static Boolean forceActive;

	public static boolean today(ServerLevel level, Village v) {
		return v.civic.life.town.market.day == VillageEconomy.day(level);
	}

	public static boolean active(ServerLevel level, Village v) {
		if (forceActive != null) return forceActive;
		return today(level, v) && TownRules.marketTime(LifeRules.timeOfDay(level.getOverworldClockTime()));
	}

	/** O dia do próximo mercado (para o quadro). */
	public static long next(Village v, long today) {
		for (long d = today; d < today + 64; d++) if (TownRules.marketDay(d, every(), offset(v))) return d;
		return -1;
	}

	/** Uma vez por dia: marca o mercado de hoje e avisa na véspera. */
	public static void daily(ServerLevel level, Village v, long day) {
		if (!v.civic.meta.real || every() <= 0) return;
		TownData.Market m = v.civic.life.town.market;
		if (TownRules.marketDay(day, every(), offset(v)) && m.day != day && m.last != day) {
			m.day = day;
			m.reset();
			VillageNews.announce(level, v, "🧺", "tvilas.news.market_today");
			Talk.fact(level, v, null, "market");
		} else if (TownRules.marketDay(day + 1, every(), offset(v))) VillageNews.announce(level, v, "🧺", "tvilas.news.market_eve");
		VillageData.get(level).changed();
	}

	/** A cada segundo (só com um jogador perto da Praça: LOD): bancas, vendedores, vendedor de fora; no fim arruma tudo. */
	static void tick(ServerLevel level, Village v, Life.Runtime r, Places places, List<Villager> all, List<ServerPlayer> players, long now, long tod) {
		TownData.Market m = v.civic.life.town.market;
		boolean on = active(level, v) && places.plaza() != null;
		if (!on) {
			if (!r.stalls.isEmpty() || m.trader != null) close(level, v, r);
			if (m.day >= 0 && m.day < VillageEconomy.day(level) || (today(level, v) && tod >= TownRules.MARKET_END && forceActive == null)) {
				m.last = m.day;
				m.day = -1;
				VillageData.get(level).changed();
			}
			return;
		}
		Places.Spot plaza = places.plaza();
		if (!Life.near(players, plaza.center, LifeRules.REDUCED)) { // ninguém perto: as bancas desmontam-se (voltam quando alguém chegar)
			if (!r.stalls.isEmpty()) removeStalls(r);
			return;
		}
		boolean fresh = r.stalls.isEmpty();
		if (fresh) open(level, v, r, plaza);
		if (fresh || r.tick % 5 == 0) staff(level, v, r, places, all, now);
		if (m.trader == null && r.traderDay != m.day) {
			r.traderDay = m.day;
			spawnTrader(level, v, plaza, tod);
		}
	}

	/** Monta as bancas (até 4, afastadas umas das outras) à volta da Praça, viradas para o centro, e toca o sino. */
	static void open(ServerLevel level, Village v, Life.Runtime r, Places.Spot plaza) {
		List<BlockPos> spots = new ArrayList<>();
		List<BlockPos> stand = new ArrayList<>(plaza.stand);
		stand.sort((a, b) -> Double.compare(b.distSqr(plaza.center), a.distSqr(plaza.center))); // das bordas para dentro
		for (BlockPos p : stand) {
			if (spots.size() >= TownRules.MARKET_STALLS) break;
			if (p.distSqr(plaza.center) < 9) continue;
			boolean apart = true;
			for (BlockPos q : spots) apart &= q.distSqr(p) >= 16;
			if (apart && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) spots.add(p);
		}
		// Praça pequena ou cheia: em roda do centro, a 5 blocos (no chão, com ar por cima)
		for (int k = 0; k < 8 && spots.size() < 3; k++) {
			double a = k * Math.PI / 4;
			int x = plaza.center.getX() + (int) Math.round(Math.cos(a) * 5), z = plaza.center.getZ() + (int) Math.round(Math.sin(a) * 5);
			BlockPos p = new BlockPos(x, Places.ground(level, x, z, plaza.center.getY()), z);
			boolean apart = true;
			for (BlockPos q : spots) apart &= q.distSqr(p) >= 16;
			if (apart && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) spots.add(p);
		}
		if (spots.isEmpty()) spots.add(plaza.center.offset(2, 0, 0));
		com.tcordeiro.vilas.life.election.VoteBonus.marketStalls(level, v, r.stalls, plaza); // 0.6 eleições: bancas próprias dos apoiadores de «mercado maior»
		for (BlockPos p : spots) {
			double dx = plaza.center.getX() - p.getX(), dz = plaza.center.getZ() - p.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			Entity e = Stage.spawn(level, VilasItems.MARKET_STALL, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 2.0f, yaw);
			if (e != null) {
				r.stalls.add(e);
				r.stallAt.add(p);
			}
		}
		if (!r.cried) {
			r.cried = true;
			r.strokes = Math.max(r.strokes, 3); // o sino abre o mercado
			opened++;
		}
	}

	public static int opened;

	/** Cada banca tem um vendedor (aldeão da vila com trocas); fica lá até ao fim do mercado. */
	static void staff(ServerLevel level, Village v, Life.Runtime r, Places places, List<Villager> all, long now) {
		for (int i = 0; i < r.stallAt.size(); i++) {
			boolean manned = false;
			for (Villager o : all) {
				Task t = Tasks.get(o);
				if (t != null && t.kind == Task.Kind.MARKET && t.ref == i) manned = true;
			}
			if (manned) continue;
			BlockPos stall = r.stallAt.get(i);
			Villager best = null;
			double bd = 64 * 64;
			for (Villager o : all) {
				if (!vendor(o) || Tasks.get(o) != null || o.isSleeping() || o.isTrading() || o.isPassenger()) continue;
				double d = o.distanceToSqr(Vec3.atCenterOf(stall));
				if (d < bd) {
					bd = d;
					best = o;
				}
			}
			if (best == null) continue;
			Places.Spot plaza = places.plaza();
			// o vendedor fica do lado de dentro da banca, de frente para a Praça
			Vec3 toC = Vec3.atCenterOf(plaza.center).subtract(Vec3.atCenterOf(stall)).multiply(1, 0, 1);
			BlockPos front = toC.lengthSqr() < 0.01 ? stall : BlockPos.containing(Vec3.atBottomCenterOf(stall).subtract(toC.normalize().scale(1.2)));
			Task t = new Task(Task.Kind.MARKET, now, now + 9000).to(front, 1.2).speed(0.55);
			t.ref = i;
			t.target = front;
			t.fallback = stall;
			t.places = places;
			t.icon = Balloons.COIN;
			Tasks.start(level, best, t);
		}
	}

	/** Um vendedor: aldeão adulto com trocas (não o Prefeito, o Construtor, os desempregados nem um ajudante contratado). */
	static boolean vendor(Villager o) {
		var p = o.getVillagerData().profession();
		return !o.isBaby() && !p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT) && !VillageManager.isMayor(o) && !Builders.isBuilder(o)
				&& !Helper.isHelper(o);
	}

	/** Na banca: olha para quem passa e de vez em quando apregoa. */
	static void stay(ServerLevel level, Villager vi, Task t, long now) {
		if (Tasks.every(t, now, 40)) Tasks.lookAtNearest(level, vi);
		LifeVillager.Memo m = ((LifeVillager) vi).tvilas$memo();
		if (now >= m.nextGesture) {
			m.nextGesture = now + 300 + level.getRandom().nextInt(300);
			String prof = VillagerRequests.profession(vi);
			Balloons.say(vi, Balloons.COIN, cry(prof), 60);
			Gestures.cheer(vi);
			cries++;
		}
		if (!Props.holding(vi)) {
			var item = Life.workItem(VillagerRequests.profession(vi));
			if (item != null) Props.hold(vi, new ItemStack(item), 200);
		}
	}

	public static int cries;

	static Component cry(String prof) {
		return switch (prof) {
			case "farmer", "baker", "butcher", "fisherman", "florist", "beekeeper", "librarian", "armorer", "weaponsmith", "toolsmith", "cleric", "fletcher",
					"shepherd", "leatherworker", "mason", "cartographer", "innkeeper", "postman" -> Component.translatable("tvilas.market.cry." + prof);
			default -> Component.translatable("tvilas.market.cry.any");
		};
	}

	/** O vendedor de fora (o vendedor ambulante do jogo): chega à Praça e vai-se embora no fim do mercado. */
	static void spawnTrader(ServerLevel level, Village v, Places.Spot plaza, long tod) {
		WanderingTrader t = SpawnUtil.trySpawnMob(EntityTypes.WANDERING_TRADER, EntitySpawnReason.EVENT, level, plaza.center, 10, 4, 4,
				SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).orElse(null);
		if (t == null) return;
		t.addTag(TRADER_TAG);
		t.setCustomName(Component.translatable("entity.tvilas.market_trader"));
		t.setDespawnDelay((int) Math.max(200, TownRules.MARKET_END - tod));
		t.setWanderTarget(plaza.center);
		v.civic.life.town.market.trader = t.getUUID();
		VillageData.get(level).changed();
	}

	/** Fim do mercado: desmonta as bancas, o vendedor de fora vai-se embora, os vendedores voltam ao trabalho. */
	static void close(ServerLevel level, Village v, Life.Runtime r) {
		removeStalls(r);
		r.cried = false;
		TownData.Market m = v.civic.life.town.market;
		if (m.trader != null) {
			Entity e = level.getEntity(m.trader);
			if (e != null) e.discard();
			m.trader = null;
		}
		for (Villager o : Life.villagers(level, v)) {
			Task t = Tasks.get(o);
			if (t != null && t.kind == Task.Kind.MARKET) Tasks.end(level, o, t, "market_over");
		}
		VillageData.get(level).changed();
	}

	static void removeStalls(Life.Runtime r) {
		for (Entity e : r.stalls) Stage.remove(e);
		r.stalls.clear();
		r.stallAt.clear();
	}

	/** Um vendedor de fora que volta a carregar depois do mercado: vai-se embora. */
	public static void onLoad(ServerLevel level, Entity e) {
		if (!(e instanceof WanderingTrader) || !e.entityTags().contains(TRADER_TAG)) return;
		Village v = VillageManager.villageAt(level, e.blockPosition());
		if (v == null || !active(level, v) || !e.getUUID().equals(v.civic.life.town.market.trader)) e.discard();
	}

	/** Para os testes e o cliente: as bancas montadas nesta vila. */
	public static int stalls(Village v) {
		return Life.rt(v).stalls.size();
	}

	/**
	 * Uma troca no território: se for numa banca (perto da Praça) durante o mercado, a tesouraria devolve 15 % do que o jogador
	 * pagou (REGRA {@link TownRules#rebate}; com o CubCoin em CubCoins, sem ele em esmeraldas inteiras). Devolve o reembolso pago.
	 */
	public static long onTrade(ServerPlayer p, Village v, BlockPos where, MerchantOffer offer) {
		ServerLevel level = p.level();
		if (!active(level, v)) return 0;
		Places.Spot plaza = Life.places(level, v).plaza();
		if (plaza == null || where.distSqr(plaza.center) > (double) TownRules.MARKET_RADIUS * TownRules.MARKET_RADIUS) return 0;
		var mo = CoinTrades.of(offer);
		long unit = VilasConfig.get().emerald_value_cents;
		long paid = (long) (mo.tvilas$emeraldsA() + mo.tvilas$emeraldsB()) * unit;
		TownData.Market m = v.civic.life.town.market;
		boolean coins = TVilas.CUBCOIN;
		long owed = m.owed.getOrDefault(p.getUUID(), 0L);
		long so = m.rebates.getOrDefault(p.getUUID(), 0L) + owed;
		long rebate = TownRules.rebate(paid, TownRules.MARKET_PERCENT, so,
				TownRules.marketPlayerCap(TownRules.MARKET.equals(v.civic.life.town.election.policy), coins ? 1 : unit),
				TownRules.MARKET_BUDGET - m.spent - owed, v.economy.treasury - owed);
		if (rebate <= 0) return 0;
		long due = owed + rebate;
		long now = LifeRules.payable(due, coins ? 1 : unit);
		m.owed.put(p.getUUID(), due - now);
		if (now > 0) {
			v.economy.treasury -= now;
			m.spent += now;
			m.rebates.merge(p.getUUID(), now, Long::sum);
			if (coins) com.tcordeiro.vilas.compat.CubCoinPay.give(p, now);
			else p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, (int) (now / unit)), net.minecraft.util.Prediction.SERVER_ONLY);
			VillageNews.tell(p, "🧺", "tvilas.market.rebate", VillageManager.money(now));
		}
		VillageData.get(level).changed();
		return now;
	}
}
