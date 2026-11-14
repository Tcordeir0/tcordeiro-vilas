package com.tcordeiro.vilas.life.crisis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.block.MayorDeskBlockEntity;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.Talk;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.village.Civic;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * 0.6 crises — Crises com pedido de ajuda e o que as liga ao mundo.
 * <ul>
 * <li><b>Incêndio</b>: um bloco da vila ardeu (fora dos terrenos). Missão relâmpago «apagar 3 fogos» (à mão ou com água); quem
 * acendeu fogo na vila nos últimos 5 minutos não recebe. Acaba quando já não há fogo.</li>
 * <li><b>Fome</b>: a comida nos inventários dos aldeões ficou baixa 2 dias seguidos. Missão «trazer N pães» (o pão vai para os
 * aldeões). Dura um dia.</li>
 * <li><b>Noite perigosa</b>: N monstros dentro da vila, de noite. Missão «derrotar 4 monstros na vila» (os de gerador e os dos
 * terrenos não contam). Acaba de manhã.</li>
 * </ul>
 * Cada crise aparece no quadro, dá balões aos aldeões e paga da TESOURARIA com a regra A1 ({@link CrisisRules#mayPay}: uma vez por
 * jogador em cada crise, poucos jogadores por crise, teto do dia das missões). Tudo por eventos ou sensores por vila (1 vez por
 * segundo no máximo), nada por aldeão a cada tick.
 */
public final class Crises {
	private Crises() {}

	public static final String ICON_FIRE = "🔥", ICON_HUNGER = "🍞", ICON_NIGHT = "🌙", ICON_FUNERAL = "🕯";

	/** Estado em memória por vila (não guardado): fogos seguidos, quem acendeu fogo, próximos sensores. */
	static final class Rt {
		final Set<Long> fires = new LinkedHashSet<>();
		final Map<UUID, Long> lighters = new HashMap<>();
		long lastBurn = -1, nextNight, nextBalloon, nextSeen;
	}

	static final Map<UUID, Rt> RT = new HashMap<>();
	static final int MAX_FIRES = 256;

	static Rt rt(Village v) {
		return RT.computeIfAbsent(v.id, k -> new Rt());
	}

	public static CrisisData data(Village v) {
		return v.civic.life.town.crisis;
	}

	public static boolean enabled() {
		return VilasConfig.get().crises;
	}

	/** Contadores e o último pagamento (para os testes). */
	public static int opened, paidCount;
	public static long lastPaid;

	// ---------------------------------------------------------------- eventos

	public static void init() {
		// quem acende fogo na vila (isqueiro, carga de fogo, balde de lava) não recebe por o apagar
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				ItemStack s = player.getItemInHand(hand);
				if (s.getItem() instanceof FlintAndSteelItem || s.getItem() instanceof FireChargeItem || s.is(Items.LAVA_BUCKET)) lit(level, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		// fogo apagado à mão
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && state.is(BlockTags.FIRE)) extinguished(level, sp, pos);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) return;
			if (entity instanceof Villager vi) Funeral.onDeath(level, vi, source);
			else if (source.getEntity() instanceof ServerPlayer killer) killed(level, killer, entity);
		});
		// /vila crise entregar (o botão do quadro). O «vila» junta-se ao comando que já existe (o Brigadier junta os ramos).
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("vila")
				.then(Commands.literal("crise").then(Commands.literal("entregar").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					VillageManager.Result r = deliverFood(p);
					if (r.ok()) c.getSource().sendSuccess(() -> r.message(), false);
					else c.getSource().sendFailure(r.message());
					return r.ok() ? 1 : 0;
				})))));
	}

	/** A vila (com área de reconstrução) onde fica esta posição, fora dos terrenos — ou null. */
	static Village villageFor(ServerLevel level, BlockPos pos) {
		if (VillageData.get(level).villages.isEmpty()) return null;
		Village v = Rebuild.areaOf(level, pos);
		if (v == null || VillageManager.lotAt(level, pos.getX(), pos.getZ()) != null) return null;
		return v;
	}

	static void lit(ServerLevel level, ServerPlayer p, BlockPos pos) {
		Village v = Rebuild.areaOf(level, pos); // revisão: a mesma procura de vila que o credit (villageFor)
		if (v != null) rt(v).lighters.put(p.getUUID(), level.getGameTime());
	}

	/** Um bloco ardeu (FireBlock.checkBurnOut): reconstrução e crise do incêndio. */
	public static void onBurn(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState before) {
		Rebuild.record(level, pos, before);
		if (!enabled()) return;
		Village v = villageFor(level, pos);
		if (v == null) return;
		Rt r = rt(v);
		r.lastBurn = level.getGameTime();
		openFire(level, v, pos);
	}

	/** Um fogo novo: se for perto de uma vila (fora dos terrenos), fica seguido até se apagar. */
	public static void onFirePlaced(ServerLevel level, BlockPos pos) {
		if (!enabled() || VillageData.get(level).villages.isEmpty()) return;
		Village area = Rebuild.areaOf(level, pos);
		if (area == null) return;
		CrisisData ad = data(area);
		if (!ad.tainted.contains(pos.asLong()) && playerOrigin(level, ad, pos)) {
			if (ad.tainted.size() >= CrisisData.MAX_TAINTED) ad.tainted.remove(ad.tainted.iterator().next());
			ad.tainted.add(pos.asLong());
			VillageData.get(level).changed();
		}
		Village v = villageFor(level, pos);
		if (v == null) return;
		Rt r = rt(v);
		if (r.fires.size() < MAX_FIRES) r.fires.add(pos.asLong());
	}

	/**
	 * REGRA DE SEGURANÇA (revisão): este fogo tem origem de jogador? Um jogador a {@link CrisisRules#ORIGIN_RADIUS} blocos, num terreno,
	 * um dispensador ou lava perto, ou um fogo vizinho já marcado (o fogo espalhou-se dele).
	 */
	public static boolean playerOrigin(ServerLevel level, CrisisData d, BlockPos pos) {
		if (VillageManager.lotAt(level, pos.getX(), pos.getZ()) != null) return true;
		for (ServerPlayer p : level.players())
			if (!p.isSpectator() && p.blockPosition().closerThan(pos, CrisisRules.ORIGIN_RADIUS)) return true;
		for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			if (d.tainted.contains(q.asLong())) return true;
			var s = level.getBlockState(q);
			if (s.is(net.minecraft.world.level.block.Blocks.DISPENSER) || s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
		}
		return false;
	}

	/** Este fogo pode render a quem o apaga? (nasceu sem jogador por perto e sem origem de jogador) */
	public static boolean payable(Village v, BlockPos pos) {
		return !data(v).tainted.contains(pos.asLong());
	}

	static void extinguished(ServerLevel level, ServerPlayer p, BlockPos pos) {
		Village v = villageFor(level, pos);
		if (v == null) return;
		rt(v).fires.remove(pos.asLong());
		boolean pay = payable(v, pos);
		data(v).tainted.remove(pos.asLong());
		if (pay) credit(level, v, p, data(v).active(CrisisRules.FIRE));
	}

	/** Um monstro morto por um jogador dentro da vila durante a noite perigosa. */
	static void killed(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
		if (!(victim instanceof Enemy) || victim.entityTags().contains(VillageManager.FROM_SPAWNER_TAG)) return;
		Village v = villageFor(level, victim.blockPosition());
		if (v == null) return;
		credit(level, v, killer, data(v).active(CrisisRules.NIGHT));
	}

	/** Mais um passo para a missão deste jogador; ao chegar à meta, paga. */
	static void credit(ServerLevel level, Village v, ServerPlayer p, CrisisData.Crisis c) {
		if (c == null || c.paid.contains(p.getUUID())) return;
		if (CrisisRules.FIRE.equals(c.type) && lighter(level, v, p)) return; // quem acendeu fogo não ganha por o apagar
		int n = c.progress.merge(p.getUUID(), 1, Integer::sum);
		VillageData.get(level).changed();
		if (n >= c.goal) pay(level, v, p, c);
		else VillageNews.tell(p, icon(c.type), "tvilas.crisis.progress", n, c.goal);
	}

	static boolean lighter(ServerLevel level, Village v, ServerPlayer p) {
		Long t = rt(v).lighters.get(p.getUUID());
		return t != null && level.getGameTime() - t < CrisisRules.LIGHTER_TICKS;
	}

	// ---------------------------------------------------------------- abrir e fechar crises

	static String icon(String type) {
		return switch (type) {
			case CrisisRules.FIRE -> ICON_FIRE;
			case CrisisRules.HUNGER -> ICON_HUNGER;
			default -> ICON_NIGHT;
		};
	}

	static int cooldown(String type) {
		return switch (type) {
			case CrisisRules.FIRE -> CrisisRules.FIRE_COOLDOWN;
			case CrisisRules.HUNGER -> CrisisRules.HUNGER_COOLDOWN;
			default -> CrisisRules.NIGHT_COOLDOWN;
		};
	}

	static int payees(String type) {
		return switch (type) {
			case CrisisRules.FIRE -> CrisisRules.FIRE_PAYEES;
			case CrisisRules.HUNGER -> CrisisRules.HUNGER_PAYEES;
			default -> CrisisRules.NIGHT_PAYEES;
		};
	}

	/** Abre uma crise (se a regra deixar). Devolve a crise aberta ou null. */
	public static CrisisData.Crisis open(ServerLevel level, Village v, String type, long until, int goal, int emeralds, BlockPos where) {
		CrisisData d = data(v);
		long day = VillageEconomy.day(level);
		if (!CrisisRules.mayOpen(d.active(type) != null, d.lastDay.getOrDefault(type, -1L), day, cooldown(type)) || d.crises.size() >= CrisisData.MAX_CRISES)
			return null;
		CrisisData.Crisis c = new CrisisData.Crisis(++d.counter, type, level.getGameTime(), until, goal, emeralds);
		c.where = where == null ? v.center.asLong() : where.asLong();
		d.crises.add(c);
		d.lastDay.put(type, day);
		opened++;
		VillageNews.announce(level, v, icon(type), "tvilas.crisis.open." + type, goal, VillageManager.reward(emeralds));
		fact(level, v, "crisis_" + type);
		CrisisHooks.onCrisis(level, v, type);
		VillageData.get(level).changed();
		return c;
	}

	static void openFire(ServerLevel level, Village v, BlockPos pos) {
		if (data(v).active(CrisisRules.FIRE) != null) return;
		int fires = Math.max(1, rt(v).fires.size());
		open(level, v, CrisisRules.FIRE, level.getGameTime() + 6000, Math.min(CrisisRules.FIRE_GOAL, fires), CrisisRules.FIRE_EMERALDS, pos);
	}

	static void close(ServerLevel level, Village v, CrisisData.Crisis c, String why) {
		data(v).crises.remove(c);
		if (why != null) VillageNews.announce(level, v, icon(c.type), "tvilas.crisis." + why + "." + c.type);
		VillageData.get(level).changed();
	}

	// ---------------------------------------------------------------- o segundo da vila (pela Mesa)

	public static void tick(ServerLevel level, Village v) {
		if (VillageData.get(level).villages.isEmpty()) return;
		long now = level.getGameTime();
		CrisisData d = data(v);
		Rt r = rt(v);
		boolean danger = v.alarm.on || MayorDeskBlockEntity.raidActive(level, v);
		fires(level, v, r, d, now);
		if (Rebuild.enabled()) Rebuild.tick(level, v, d, danger || !r.fires.isEmpty(), now);
		if (enabled()) {
			if (now >= r.nextNight) {
				r.nextNight = now + 200; // sensor da noite: a cada 10 s
				night(level, v, d, now);
			}
			for (Iterator<CrisisData.Crisis> it = new ArrayList<>(d.crises).iterator(); it.hasNext();) {
				CrisisData.Crisis c = it.next();
				if (now >= c.until) close(level, v, c, "over");
			}
			if (!d.crises.isEmpty() && now >= r.nextBalloon) {
				r.nextBalloon = now + 100;
				balloons(level, v, d);
			}
		}
		if (now >= r.nextSeen) {
			r.nextSeen = now + 600; // a cada 30 s: os aldeões novos ficam com o dia em que chegaram
			Funeral.seen(level, v, d);
		}
		Funeral.tick(level, v, d, now);
		r.lighters.values().removeIf(t -> now - t > CrisisRules.LIGHTER_TICKS);
	}

	/** Revê os fogos seguidos: os que já não estão, e quem os apagou com água (o jogador mais perto, a 6 blocos). */
	static void fires(ServerLevel level, Village v, Rt r, CrisisData d, long now) {
		if (r.fires.isEmpty()) {
			CrisisData.Crisis c = d.active(CrisisRules.FIRE);
			if (c != null && now - r.lastBurn > 100) close(level, v, c, "solved");
			return;
		}
		CrisisData.Crisis c = d.active(CrisisRules.FIRE);
		for (Iterator<Long> it = r.fires.iterator(); it.hasNext();) {
			BlockPos pos = BlockPos.of(it.next());
			if (!level.isLoaded(pos)) {
				it.remove();
				continue;
			}
			var s = level.getBlockState(pos);
			if (s.is(BlockTags.FIRE)) continue;
			it.remove();
			boolean pay = payable(v, pos);
			d.tainted.remove(pos.asLong());
			if (pay && c != null && !s.getFluidState().isEmpty()) { // apagado com água: o jogador mais perto
				ServerPlayer p = nearest(level, pos, 6);
				if (p != null) credit(level, v, p, c);
			}
		}
	}

	static ServerPlayer nearest(ServerLevel level, BlockPos pos, double max) {
		ServerPlayer best = null;
		double bd = max * max;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) continue;
			double d = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** Para os testes: força o número de monstros e a hora do dia (null = conta-os / o relógio). */
	public static Integer forceMonsters;
	public static Long forceTod;

	/** Sensor da noite perigosa (a cada 10 s): monstros dentro da vila (fora dos terrenos), de noite. */
	public static void night(ServerLevel level, Village v, CrisisData d, long now) {
		long tod = forceTod != null ? forceTod : LifeRules.timeOfDay(level.getOverworldClockTime());
		boolean night = tod >= 13000 && tod < 23000;
		CrisisData.Crisis c = d.active(CrisisRules.NIGHT);
		if (c != null || !night) return;
		int monsters = forceMonsters != null ? forceMonsters : monsters(level, v);
		if (!CrisisRules.nightDanger(monsters, VilasConfig.get().crisis_night_monsters, true)) return;
		open(level, v, CrisisRules.NIGHT, now + (23000 - tod), CrisisRules.NIGHT_GOAL, CrisisRules.NIGHT_EMERALDS, null);
	}

	public static int monsters(ServerLevel level, Village v) {
		int r = Rebuild.radius(v);
		AABB box = new AABB(v.center).inflate(r, 48, r);
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof Enemy && v.distSqr(e.getBlockX(), e.getBlockZ()) <= (double) r * r
				&& !e.entityTags().contains(VillageManager.FROM_SPAWNER_TAG) && VillageManager.lotAt(level, e.getBlockX(), e.getBlockZ()) == null).size();
	}

	/** Balões das crises: até 3 aldeões com um jogador perto gritam pelo problema. */
	static void balloons(ServerLevel level, Village v, CrisisData d) {
		List<Villager> all = Life.villagers(level, v);
		if (all.isEmpty()) return;
		for (CrisisData.Crisis c : d.crises) {
			BlockPos at = BlockPos.of(c.where);
			int shown = 0;
			for (Villager vi : all) {
				if (shown >= 3) break;
				if (CrisisRules.FIRE.equals(c.type) && !vi.blockPosition().closerThan(at, 32)) continue;
				if (nearest(level, vi.blockPosition(), LifeRules.BALLOON_RANGE) == null) continue;
				String icon = switch (c.type) {
					case CrisisRules.FIRE -> Balloons.FIRE;
					case CrisisRules.HUNGER -> Balloons.HUNGER;
					default -> Balloons.ALERT;
				};
				Balloons.say(vi, icon, Component.translatable("tvilas.crisis.shout." + c.type), 60);
				shown++;
			}
		}
	}

	// ---------------------------------------------------------------- uma vez por dia

	public static void daily(ServerLevel level, Village v, long day) {
		if (VillageData.get(level).villages.isEmpty()) return;
		CrisisData d = data(v);
		if (enabled()) hunger(level, v, d, day);
		Funeral.daily(level, v, d, day);
	}

	/** Pontos de comida nos inventários dos aldeões da vila (pão 4, batata/cenoura/beterraba 1; o componente de comida de aldeão). */
	public static int foodPoints(List<Villager> all) {
		int n = 0;
		for (Villager vi : all) {
			SimpleContainer inv = vi.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				var food = s.get(DataComponents.VILLAGER_FOOD);
				if (food != null) n += food.nutrition() * s.getCount();
			}
		}
		return n;
	}

	public static void hunger(ServerLevel level, Village v, CrisisData d, long day) {
		if (d.hungerChecked == day) return;
		d.hungerChecked = day;
		List<Villager> all = Life.villagers(level, v);
		boolean low = CrisisRules.hungerLow(foodPoints(all), all.size());
		d.hungerDays = low ? d.hungerDays + 1 : 0;
		if (d.hungerDays >= CrisisRules.HUNGER_DAYS && d.active(CrisisRules.HUNGER) == null) {
			int goal = CrisisRules.hungerGoal(all.size());
			open(level, v, CrisisRules.HUNGER, level.getGameTime() + VillageEconomy.DAY, goal, CrisisRules.hungerEmeralds(goal), null);
		}
	}

	/** O botão do quadro na crise da fome: entrega os pães (vão para os inventários dos aldeões) e recebe. */
	public static VillageManager.Result deliverFood(ServerPlayer p) {
		ServerLevel level = p.level();
		Village v = VillageManager.villageAt(level, p.blockPosition());
		if (v == null) return VillageManager.Result.fail("tvilas.cmd.no_village");
		CrisisData d = data(v);
		CrisisData.Crisis c = d.active(CrisisRules.HUNGER);
		if (c == null) return VillageManager.Result.fail("tvilas.crisis.none");
		if (c.paid.contains(p.getUUID())) return VillageManager.Result.fail("tvilas.crisis.already");
		int have = VillageManager.count(p.getInventory(), Items.BREAD);
		if (have < c.goal) return VillageManager.Result.fail("tvilas.crisis.need_bread", have, c.goal);
		List<Villager> all = new ArrayList<>(Life.villagers(level, v));
		all.removeIf(vi -> vi.isBaby());
		if (room(all) < c.goal) return VillageManager.Result.fail("tvilas.crisis.no_room");
		VillageManager.Result no = mayPay(level, v, p, c);
		if (no != null) return no;
		VillageManager.take(p.getInventory(), Items.BREAD, c.goal);
		share(all, c.goal);
		pay(level, v, p, c);
		if (!CrisisRules.hungerLow(foodPoints(Life.villagers(level, v)), Life.villagers(level, v).size())) {
			d.hungerDays = 0;
			close(level, v, c, "solved");
		}
		return VillageManager.Result.ok("tvilas.crisis.delivered", c.goal);
	}

	static int room(List<Villager> all) {
		int n = 0;
		for (Villager vi : all) {
			SimpleContainer inv = vi.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty()) n += 64;
				else if (s.is(Items.BREAD)) n += s.getMaxStackSize() - s.getCount();
			}
		}
		return n;
	}

	/** Reparte os pães pelos aldeões, um a um (cada um fica com um pouco). */
	static void share(List<Villager> all, int bread) {
		int i = 0, guard = bread * Math.max(1, all.size()) + 8;
		while (bread > 0 && !all.isEmpty() && guard-- > 0) {
			Villager vi = all.get(i++ % all.size());
			ItemStack rest = vi.getInventory().addItem(new ItemStack(Items.BREAD));
			if (rest.isEmpty()) bread--;
		}
	}

	// ---------------------------------------------------------------- pagar (regra A1)

	/** A vila pode pagar a crise a este jogador? null = sim; senão a razão. */
	static VillageManager.Result mayPay(ServerLevel level, Village v, ServerPlayer p, CrisisData.Crisis c) {
		if (!v.civic.meta.real) return VillageManager.Result.fail("tvilas.mission.not_real");
		Civic civ = today(level, v);
		VilasConfig cfg = VilasConfig.get();
		long reward = MissionRules.rewardCents(c.emeralds, cfg.emerald_value_cents);
		long available = v.economy.treasury + civ.missionFund; // 0.6.0: como as missões — o fundo das missões e depois a tesouraria
		if (CrisisRules.mayPay(c.paid.contains(p.getUUID()), c.paid.size(), payees(c.type), available, civ.paidToday, cfg.mission_daily_cap_cents, reward))
			return null;
		if (c.paid.contains(p.getUUID())) return VillageManager.Result.fail("tvilas.crisis.already");
		if (c.paid.size() >= payees(c.type)) return VillageManager.Result.fail("tvilas.crisis.full");
		if (available < reward) return VillageManager.Result.fail("tvilas.mission.no_funds", VillageManager.money(reward), VillageManager.money(available));
		return VillageManager.Result.fail("tvilas.mission.daily_cap", VillageManager.money(cfg.mission_daily_cap_cents));
	}

	static Civic today(ServerLevel level, Village v) {
		Civic c = v.civic;
		long day = VillageEconomy.day(level);
		if (c.missionDay != day) {
			c.missionDay = day;
			c.paidToday = 0;
		}
		return c;
	}

	/** Paga a crise (primeiro do fundo das missões, depois da tesouraria; se a regra deixar) e dá reputação. Devolve true se pagou. */
	static boolean pay(ServerLevel level, Village v, ServerPlayer p, CrisisData.Crisis c) {
		VillageManager.Result no = mayPay(level, v, p, c);
		if (no != null) {
			p.sendSystemMessage(VillageNews.line(icon(c.type), "tvilas.crisis.unpaid").append(" ").append(no.message()));
			return false;
		}
		long reward = MissionRules.rewardCents(c.emeralds, VilasConfig.get().emerald_value_cents);
		Civic civ = today(level, v);
		long fund = com.tcordeiro.vilas.logic.FundRules.fromFund(civ.missionFund, reward); // primeiro o fundo das missões
		civ.missionFund -= fund;
		v.economy.treasury -= reward - fund; // o resto da tesouraria
		civ.paidToday += reward;
		c.paid.add(p.getUUID());
		if (TVilas.CUBCOIN) com.tcordeiro.vilas.compat.CubCoinPay.give(p, reward);
		else p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, c.emeralds), net.minecraft.util.Prediction.SERVER_ONLY);
		VillageManager.addReputation(level, v, p, CrisisRules.REPUTATION);
		paidCount++;
		lastPaid = reward;
		VillageNews.tell(p, icon(c.type), "tvilas.crisis.paid", VillageManager.reward(c.emeralds), CrisisRules.REPUTATION);
		VillageData.get(level).changed();
		return true;
	}

	// ---------------------------------------------------------------- quadro

	/** As crises no quadro da Prefeitura (uma linha cada; o botão de entregar na fome). */
	public static void board(ServerPlayer p, ServerLevel level, Village v, List<DialogBody> body, List<ActionButton> buttons,
			BiFunction<Component, String, ActionButton> button) {
		for (CrisisData.Crisis c : data(v).crises) {
			MutableComponent line = Component.literal(icon(c.type) + " ").append(Component.translatable("tvilas.dialog.crisis." + c.type, c.goal,
					VillageManager.reward(c.emeralds), CrisisRules.REPUTATION)).withStyle(ChatFormatting.RED);
			boolean done = c.paid.contains(p.getUUID());
			int prog = CrisisRules.HUNGER.equals(c.type) ? VillageManager.count(p.getInventory(), Items.BREAD) : c.progress.getOrDefault(p.getUUID(), 0);
			if (!done) line.append(Component.translatable("tvilas.dialog.progress", Math.min(prog, c.goal), c.goal).withStyle(ChatFormatting.GRAY));
			else line.append(Component.translatable("tvilas.dialog.mission_done").withStyle(ChatFormatting.GREEN));
			body.add(new PlainMessage(line, 300));
			if (!done && CrisisRules.HUNGER.equals(c.type)) buttons.add(button.apply(Component.translatable("tvilas.dialog.crisis_deliver"), "/vila crise entregar"));
		}
	}

	/** Um facto para a fofoca (só com a vida dos aldeões ligada). */
	static void fact(ServerLevel level, Village v, String key, String... args) {
		if (VilasConfig.get().villager_life) Talk.fact(level, v, null, key, args);
	}
}
