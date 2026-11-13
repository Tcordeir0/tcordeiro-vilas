package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 0.5.0 — Crianças a brincar perto da Praça, de dia e fora das horas de aprendiz: ESCONDE-ESCONDE (uma conta de costas, as outras
 * escondem-se atrás de alguma coisa; depois procura-as uma a uma: «Achei!») e APANHADA (quem é apanhado passa a apanhar). Só com um
 * jogador a 48 blocos da Praça (LOD); cada jogo tem prazo, e o vanilla volta logo a seguir. Nada de dinheiro.
 */
public final class Play {
	private Play() {}

	/** Um jogo a decorrer numa vila (em memória). */
	static final class Game {
		final boolean hide;
		final List<Villager> kids = new ArrayList<>();
		Villager it;
		final Set<UUID> found = new HashSet<>();
		long until, countEnd;

		Game(boolean hide) {
			this.hide = hide;
		}
	}

	public static int games, founds, tags;

	/** A cada segundo, pela vila. */
	static void tick(ServerLevel level, Village v, Life.Runtime r, Places places, List<Villager> all, List<ServerPlayer> players, long now, long tod) {
		Places.Spot plaza = places.plaza();
		Game g = r.game;
		if (g != null) {
			g.kids.removeIf(k -> !k.isAlive() || !k.isBaby() || Tasks.get(k) == null || Tasks.get(k).kind != Task.Kind.PLAY);
			if (now >= g.until || g.kids.size() < 2 || plaza == null || !Life.near(players, plaza.center, LifeRules.FULL) || !g.kids.contains(g.it)) {
				stop(level, r);
				return;
			}
			if (g.hide) hideStep(level, g, now);
			else tagStep(level, g, now, plaza);
			return;
		}
		if (plaza == null || now < r.nextGame || !TownRules.playTime(tod) || !Life.near(players, plaza.center, LifeRules.FULL)) return;
		r.nextGame = now + 200;
		List<Villager> kids = new ArrayList<>();
		for (Villager k : all)
			if (k.isBaby() && Tasks.get(k) == null && !k.isSleeping() && !k.isPassenger()
					&& k.distanceToSqr(Vec3.atCenterOf(plaza.center)) <= TownRules.PLAY_RADIUS * TownRules.PLAY_RADIUS && kids.size() < 5)
				kids.add(k);
		if (kids.size() < 2) return;
		start(level, r, places, kids, now, (r.played++ & 1) == 0);
	}

	/** Começa um jogo (para os testes também). */
	public static boolean start(ServerLevel level, Life.Runtime r, Places places, List<Villager> kids, long now, boolean hide) {
		Places.Spot plaza = places.plaza();
		if (plaza == null || kids.size() < 2) return false;
		Game g = new Game(hide);
		g.kids.addAll(kids);
		g.it = kids.get(level.getRandom().nextInt(kids.size()));
		g.until = now + (hide ? TownRules.HIDE_TICKS : TownRules.TAG_TICKS);
		g.countEnd = now + TownRules.HIDE_COUNT_TICKS;
		r.game = g;
		RandomSource rnd = level.getRandom();
		for (Villager k : kids) {
			Task t = new Task(Task.Kind.PLAY, now, g.until).speed(hide ? 0.6 : 0.75);
			t.places = places;
			if (k == g.it) {
				t.role = hide ? "count" : "chase";
				if (hide) {
					t.target = k.blockPosition();
					t.arrived = true;
					t.holdUntil = g.countEnd;
					t.icon = null;
				}
			} else if (hide) {
				t.role = "hide";
				t.target = hideSpot(level, plaza.center, g.it, rnd);
				t.reach = 0.8;
			} else {
				t.role = "run";
				t.target = away(level, k, g.it, plaza.center, rnd);
				t.reach = 1.2;
			}
			Tasks.start(level, k, t);
		}
		if (hide) Balloons.say(g.it, null, Component.translatable("tvilas.play.counting"), TownRules.HIDE_COUNT_TICKS);
		else Balloons.say(g.it, null, Component.translatable("tvilas.play.tag_start"), 60);
		games++;
		return true;
	}

	static void stop(ServerLevel level, Life.Runtime r) {
		Game g = r.game;
		r.game = null;
		r.nextGame = level.getGameTime() + TownRules.PLAY_COOLDOWN;
		if (g == null) return;
		for (Villager k : g.kids) {
			Task t = Tasks.get(k);
			if (t != null && t.kind == Task.Kind.PLAY) Tasks.end(level, k, t, "game_over");
		}
	}

	// ---------------------------------------------------------------- esconde-esconde

	static void hideStep(ServerLevel level, Game g, long now) {
		Task it = Tasks.get(g.it);
		if (it == null) return;
		if (now < g.countEnd) return; // ainda a contar
		if ("count".equals(it.role)) {
			it.role = "seek";
			Balloons.say(g.it, Balloons.QUESTION, Component.translatable("tvilas.play.ready"), 50);
		}
		Villager next = null;
		double bd = Double.MAX_VALUE;
		for (Villager k : g.kids) {
			if (k == g.it || g.found.contains(k.getUUID())) continue;
			double d = k.distanceToSqr(g.it);
			if (d < bd) {
				bd = d;
				next = k;
			}
		}
		if (next == null) { // achou todos
			Balloons.show(g.it, Balloons.PARTY, 60);
			g.until = now + 40;
			return;
		}
		it.follow = next;
		it.reach = 1.3;
		if (bd <= 1.8 * 1.8) {
			g.found.add(next.getUUID());
			founds++;
			Balloons.say(g.it, Balloons.EXCLAIM, Component.translatable("tvilas.play.found"), 40);
			Balloons.show(next, Balloons.HEART, 40);
			next.getJumpControl().jump();
			it.follow = null;
			it.arrived = false;
		}
	}

	/** Um sítio para se esconder: 5–12 blocos da Praça, com alguma coisa entre ele e quem conta (senão, um sítio qualquer). */
	static BlockPos hideSpot(ServerLevel level, BlockPos center, Villager counter, RandomSource r) {
		BlockPos any = null;
		Vec3 eye = counter.getEyePosition();
		for (int i = 0; i < 16; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = 5 + r.nextInt(8);
			int x = (int) Math.floor(center.getX() + Math.cos(a) * d), z = (int) Math.floor(center.getZ() + Math.sin(a) * d);
			BlockPos p = new BlockPos(x, Places.ground(level, x, z, center.getY()), z);
			if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
			if (any == null) any = p;
			var hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(p), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, counter));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(Vec3.atCenterOf(p)) > 1.0) return p;
		}
		return any != null ? any : center;
	}

	// ---------------------------------------------------------------- apanhada

	static void tagStep(ServerLevel level, Game g, long now, Places.Spot plaza) {
		Task it = Tasks.get(g.it);
		if (it == null) return;
		Villager prey = null;
		double bd = Double.MAX_VALUE;
		for (Villager k : g.kids) {
			if (k == g.it) continue;
			double d = k.distanceToSqr(g.it);
			if (d < bd) {
				bd = d;
				prey = k;
			}
		}
		if (prey == null) return;
		it.follow = prey;
		it.reach = 1.0;
		if (bd <= 1.3 * 1.3 && now >= it.holdUntil) { // apanhado! troca de papéis
			Task pt = Tasks.get(prey);
			if (pt == null) return;
			tags++;
			Balloons.say(g.it, null, Component.translatable("tvilas.play.tagged"), 40);
			Balloons.show(prey, Balloons.EXCLAIM, 30);
			it.role = "run";
			it.follow = null;
			it.target = away(level, g.it, prey, plaza.center, level.getRandom());
			it.arrived = false;
			pt.role = "chase";
			pt.holdUntil = now + 30; // conta até três antes de correr
			pt.follow = g.it;
			g.it = prey;
			return;
		}
		// quem foge escolhe outro sítio a cada 3 s
		for (Villager k : g.kids) {
			if (k == g.it) continue;
			Task t = Tasks.get(k);
			if (t != null && (t.arrived || Tasks.every(t, now, 60))) {
				t.target = away(level, k, g.it, plaza.center, level.getRandom());
				t.arrived = false;
			}
		}
	}

	/** Um sítio para fugir: para longe de quem apanha, sem sair dos 12 blocos da Praça. */
	static BlockPos away(ServerLevel level, Villager kid, Villager from, BlockPos center, RandomSource r) {
		Vec3 dir = kid.position().subtract(from.position()).multiply(1, 0, 1);
		if (dir.lengthSqr() < 0.01) dir = new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
		Vec3 to = kid.position().add(dir.normalize().scale(5)).add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
		Vec3 c = Vec3.atCenterOf(center);
		Vec3 off = to.subtract(c).multiply(1, 0, 1);
		if (off.length() > 12) to = c.add(off.normalize().scale(10 - r.nextInt(4)));
		int x = (int) Math.floor(to.x), z = (int) Math.floor(to.z);
		return new BlockPos(x, Places.ground(level, x, z, center.getY()), z);
	}

	// ---------------------------------------------------------------- pela tarefa

	static void arrive(ServerLevel level, Villager v, Task t, long now) {
		if ("hide".equals(t.role)) {
			t.holdUntil = t.deadline; // escondido: fica quieto até o acharem
			Balloons.show(v, Balloons.HIDE, 30);
		}
	}

	static void stay(ServerLevel level, Villager v, Task t, long now) {
		if ("count".equals(t.role) && t.places != null && t.places.plaza() != null) {
			// de costas para a Praça, a contar
			BlockPos c = t.places.plaza().center;
			Vec3 away = v.position().subtract(Vec3.atCenterOf(c)).multiply(1, 0, 1);
			if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
			v.getLookControl().setLookAt(v.getX() + away.x, v.getEyeY() - 0.3, v.getZ() + away.z);
		}
	}

	static void ended(Villager v, Task t) {}

	/** Para os testes: o jogo desta vila (null = nenhum). */
	public static String game(Village v) {
		Game g = Life.rt(v).game;
		return g == null ? null : (g.hide ? "hide" : "tag") + ":" + g.kids.size();
	}

	public static void startForTest(ServerLevel level, Village v, List<Villager> kids, boolean hide) {
		start(level, Life.rt(v), Life.places(level, v), kids, level.getGameTime(), hide);
	}

	public static void stopForTest(ServerLevel level, Village v) {
		stop(level, Life.rt(v));
	}
}
