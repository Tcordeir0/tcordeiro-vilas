package com.tcordeiro.vilas.life.crisis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.Ground;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.life.Gestures;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.Places;
import com.tcordeiro.vilas.life.Task;
import com.tcordeiro.vilas.life.Tasks;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.6 crises — Morte, luto e funeral. Quando um aldeão da vila morre, no dia seguinte, às 15 h, há funeral na Praça: uma campa
 * (blocos vanilla: pedra lavrada, uma vela acesa e uma placa com o epitáfio, posta só onde há ar e nunca num terreno) e os amigos
 * juntam-se à volta dela. Os amigos ficam de luto um dia (balão da vela e o gesto do luto). A velhice está DESLIGADA por omissão
 * ({@code villager_aging}); ligada, só leva aldeões sem trocas trancadas (e nunca o Prefeito, aldeões com nome ou crianças).
 */
public final class Funeral {
	private Funeral() {}

	public static final String CAUSE_RAID = "raid", CAUSE_MONSTER = "monster", CAUSE_AGE = "age";

	/** Contadores para os testes. */
	public static int deaths, funerals, graves, aged;
	/** O aldeão que a velhice está a levar agora (a causa da morte fica «velhice»). */
	static UUID dyingOfAge;

	public static boolean enabled() {
		return VilasConfig.get().funerals;
	}

	// ---------------------------------------------------------------- a morte

	public static void onDeath(ServerLevel level, Villager vi, DamageSource source) {
		if (!enabled() || Builders.isBuilder(vi)) return;
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		if (v == null || vi.getUUID().equals(v.extra.merchant)) return;
		CrisisData d = Crises.data(v);
		long day = VillageEconomy.day(level), now = level.getGameTime();
		String cause = vi.getUUID().equals(dyingOfAge) ? CAUSE_AGE
				: source.getEntity() instanceof Raider || com.tcordeiro.vilas.block.MayorDeskBlockEntity.raidActive(level, v) ? CAUSE_RAID
						: source.getEntity() instanceof net.minecraft.world.entity.monster.Enemy ? CAUSE_MONSTER : "";
		Long first = d.firstSeen.remove(vi.getUUID());
		String name = vi.hasCustomName() ? cut(vi.getCustomName().getString(), 48) : "";
		CrisisData.Death x = new CrisisData.Death(vi.getUUID(), name, profession(vi), CrisisRules.daysLived(first == null ? CrisisRules.UNKNOWN : first, day), day, cause);
		x.friends.addAll(friends(level, v, vi));
		while (d.deaths.size() >= CrisisData.MAX_DEATHS) d.deaths.remove(0);
		d.deaths.add(x);
		for (UUID f : x.friends) CrisisData.putCapped(d.mourning, f, now + CrisisRules.MOURNING_TICKS);
		deaths++;
		VillageNews.announce(level, v, Crises.ICON_FUNERAL, "tvilas.funeral.died", who(x));
		Crises.fact(level, v, "died", name.isEmpty() ? x.profession : name);
		CrisisHooks.onDeath(level, v, x);
		VillageData.get(level).changed();
	}

	/** A profissão como texto guardável: «#chave» (traduz-se na língua de quem lê). */
	static String profession(Villager vi) {
		Component c = vi.getVillagerData().profession().value().name();
		if (c.getContents() instanceof TranslatableContents t) return "#" + t.getKey();
		return cut(c.getString(), 96);
	}

	static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	static Component text(String s) {
		return s.startsWith("#") ? Component.translatable(s.substring(1)) : Component.literal(s);
	}

	/** «o Talys» (nome dado) ou a profissão. */
	static Component who(CrisisData.Death x) {
		return x.name.isEmpty() ? text(x.profession) : Component.literal(x.name);
	}

	/** Os amigos do morto (opinião ≥ {@link CrisisRules#FRIEND_OPINION}); sem amigos, os 3 vizinhos mais perto (até 8). */
	static List<UUID> friends(ServerLevel level, Village v, Villager dead) {
		List<UUID> out = new ArrayList<>();
		String me = dead.getUUID().toString();
		for (Map.Entry<String, Integer> e : v.civic.life.opinions.entrySet()) {
			if (out.size() >= 8) break;
			if (e.getValue() < CrisisRules.FRIEND_OPINION || !e.getKey().contains(me)) continue;
			String[] two = e.getKey().split("\\|");
			if (two.length != 2) continue;
			try {
				out.add(UUID.fromString(two[0].equals(me) ? two[1] : two[0]));
			} catch (IllegalArgumentException ignored) {
				// par estragado: salta
			}
		}
		if (out.isEmpty()) {
			List<Villager> near = new ArrayList<>(Life.villagers(level, v));
			near.removeIf(o -> o == dead || !o.isAlive() || o.distanceToSqr(dead) > 32 * 32);
			near.sort(java.util.Comparator.comparingDouble(o -> o.distanceToSqr(dead)));
			for (int i = 0; i < near.size() && i < 3; i++) out.add(near.get(i).getUUID());
		}
		return out;
	}

	// ---------------------------------------------------------------- o funeral (no dia seguinte)

	/** Para os testes: a hora do dia (null = o relógio). */
	public static Long forceTime;

	public static void tick(ServerLevel level, Village v, CrisisData d, long now) {
		if (d.deaths.isEmpty() && d.mourning.isEmpty()) return;
		long day = VillageEconomy.day(level), tod = forceTime != null ? forceTime : LifeRules.timeOfDay(level.getOverworldClockTime());
		for (Iterator<CrisisData.Death> it = d.deaths.iterator(); it.hasNext();) {
			CrisisData.Death x = it.next();
			if (x.held < 0) {
				if (!enabled() || !CrisisRules.funeralDue(x.day, day, tod)) continue;
				hold(level, v, d, x, now);
			} else if (now - x.held >= CrisisRules.FUNERAL_LENGTH || now < x.held) {
				it.remove();
				VillageData.get(level).changed();
				continue;
			}
			if (x.held >= 0 && Math.floorMod(now - x.held, 100) < 20) mourn(level, v, x.friends, 4); // gestos à volta da campa
		}
		d.mourning.values().removeIf(t -> t <= now);
		if (!d.mourning.isEmpty() && Math.floorMod(now, 300) < 20) mourn(level, v, new ArrayList<>(d.mourning.keySet()), 2);
	}

	/** Começa o funeral: a campa (se ainda couber) e os amigos vão para lá. */
	static void hold(ServerLevel level, Village v, CrisisData d, CrisisData.Death x, long now) {
		x.held = now;
		funerals++;
		Places.Spot plaza = Life.places(level, v).plaza();
		BlockPos center = plaza != null ? plaza.center : v.center;
		BlockPos grave = d.graves.size() < CrisisRules.MAX_GRAVES ? placeGrave(level, v, d, center, x) : null;
		if (grave != null) x.grave = grave.asLong();
		BlockPos at = grave != null ? grave : center;
		VillageNews.announce(level, v, Crises.ICON_FUNERAL, "tvilas.funeral.today", who(x));
		CrisisHooks.onFuneral(level, v, x, grave);
		if (VilasConfig.get().villager_life) {
			for (Villager vi : Life.villagers(level, v)) {
				if (!x.friends.contains(vi.getUUID()) || vi.isSleeping() || vi.isBaby()) continue;
				Task t = new Task(Task.Kind.MEETING, now, now + CrisisRules.FUNERAL_LENGTH).to(at.offset(level.getRandom().nextInt(5) - 2, 0, 2), 2.5).speed(0.5);
				t.icon = Balloons.CANDLE;
				Tasks.start(level, vi, t);
			}
		}
		VillageData.get(level).changed();
	}

	/** Gesto e balão do luto em até {@code max} aldeões com alguém a ver. */
	static void mourn(ServerLevel level, Village v, List<UUID> who, int max) {
		int n = 0;
		for (Villager vi : Life.villagers(level, v)) {
			if (n >= max) return;
			if (!who.contains(vi.getUUID()) || Crises.nearest(level, vi.blockPosition(), LifeRules.BALLOON_RANGE) == null) continue;
			Balloons.say(vi, Balloons.CANDLE, Component.translatable("tvilas.funeral.mourn"), 60);
			Gestures.mourn(vi);
			n++;
		}
	}

	/** A campa: procura um sítio livre à volta da Praça (anel de 4 a 9 blocos) e põe pedra lavrada, a vela e a placa. */
	static BlockPos placeGrave(ServerLevel level, Village v, CrisisData d, BlockPos center, CrisisData.Death x) {
		for (int r = 4; r <= 9; r++)
			for (int a = 0; a < 16; a++) {
				double ang = (a + (r % 2) * 0.5) * Math.PI / 8;
				int gx = center.getX() + (int) Math.round(Math.cos(ang) * r), gz = center.getZ() + (int) Math.round(Math.sin(ang) * r);
				BlockPos pos = Ground.surface(level, gx, gz, center.getY());
				Direction face = Direction.getApproximateNearest(center.getX() - gx, 0, center.getZ() - gz);
				if (face.getAxis().isVertical()) face = Direction.NORTH;
				if (!graveFits(level, d, pos, face)) continue;
				level.setBlock(pos, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
				level.setBlock(pos.above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), 3);
				BlockPos signAt = pos.relative(face);
				level.setBlock(signAt, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face), 3);
				if (level.getBlockEntity(signAt) instanceof SignBlockEntity sign) {
					sign.setText(epitaph(x), SignTextSlot.FRONT);
					sign.setWaxed(true);
				}
				d.graves.add(pos.asLong());
				graves++;
				return pos;
			}
		return null;
	}

	/** REGRA DE SEGURANÇA: a campa só vai para onde há ar/erva/flores/neve, nunca num terreno ({@link Ground#mayBuild}), em chão firme, fora das ruas. */
	static boolean graveFits(ServerLevel level, CrisisData d, BlockPos pos, Direction face) {
		if (!Ground.mayBuild(level, pos) || !Ground.mayBuild(level, pos.above()) || !Ground.mayBuild(level, pos.relative(face))) return false;
		BlockState below = level.getBlockState(pos.below());
		if (!below.isSolidRender() || Ground.street(below) || !level.getBlockState(pos.relative(face).below()).isSolidRender()) return false;
		for (long g : d.graves) if (BlockPos.of(g).distManhattan(pos) < 3) return false;
		return true;
	}

	/** O epitáfio (4 linhas da placa): nome, profissão, dias na vila e a causa (ou a linha da crónica, se houver). */
	public static SignText epitaph(CrisisData.Death x) {
		Component l1 = who(x), l2 = x.name.isEmpty() ? Component.empty() : text(x.profession);
		Component l3 = Component.translatable("tvilas.funeral.days", x.days);
		Component l4 = CrisisHooks.epitaphLine(x); // crónica do principal (no merge)
		if (l4 == null) l4 = Component.translatable("tvilas.funeral.cause." + (x.cause.isEmpty() ? "rest" : x.cause));
		return SignText.EMPTY.asMutable().setLine(0, l1).setLine(1, l2).setLine(2, l3).setLine(3, l4).asImmutable();
	}

	// ---------------------------------------------------------------- dias de vida e velhice

	/** Os aldeões da vila ficam com o dia em que foram vistos pela primeira vez (para os dias de vida). */
	static void seen(ServerLevel level, Village v, CrisisData d) {
		long day = VillageEconomy.day(level);
		boolean changed = false;
		for (Villager vi : Life.villagers(level, v))
			if (!d.firstSeen.containsKey(vi.getUUID())) {
				CrisisData.putCapped(d.firstSeen, vi.getUUID(), day);
				changed = true;
			}
		if (changed) VillageData.get(level).changed();
	}

	/** Uma vez por dia: a velhice (só se estiver ligada), no máximo um aldeão por dia. */
	public static void daily(ServerLevel level, Village v, CrisisData d, long day) {
		VilasConfig cfg = VilasConfig.get();
		if (!cfg.villager_aging || d.agingDay == day) return;
		d.agingDay = day;
		for (Villager vi : new ArrayList<>(Life.villagers(level, v))) {
			Long first = d.firstSeen.get(vi.getUUID());
			if (first == null) continue;
			if (!CrisisRules.mayDieOfAge(true, CrisisRules.daysLived(first, day), cfg.villager_lifespan_days, vi.getVillagerXp() > 0, vi.hasCustomName(),
					VillageManager.isMayor(vi), vi.isBaby()))
				continue;
			dyingOfAge = vi.getUUID();
			try {
				vi.kill(level);
			} finally {
				dyingOfAge = null;
			}
			aged++;
			return;
		}
	}

	/** Para os testes e o quadro: este aldeão está de luto? */
	public static boolean mourning(Village v, UUID who) {
		return Crises.data(v).mourning.containsKey(who);
	}
}
