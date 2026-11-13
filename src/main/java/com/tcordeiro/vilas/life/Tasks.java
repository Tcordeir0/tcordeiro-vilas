package com.tcordeiro.vilas.life;

import com.tcordeiro.vilas.logic.LifeRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — Corre as tarefas dos aldeões (chamado pelo mixin a cada tick enquanto há tarefa; o trabalho pesado só a cada 2 ticks).
 * Cada tarefa tem prazo, anti-preso (60 ticks sem andar → refaz o caminho; à 3.ª desiste: plano B) e trata das portas (abre as de
 * madeira do caminho e fecha-as quando já ninguém passa). Acaba logo se o aldeão for ferido, começar a trocar ou ficar sem jogador
 * perto (o vanilla volta a mandar).
 */
public final class Tasks {
	private Tasks() {}

	public static Task get(Villager v) {
		return ((LifeVillager) v).tvilas$task();
	}

	public static boolean busy(Villager v) {
		return get(v) != null;
	}

	/** Dá uma tarefa (acaba a que houver). O cérebro vanilla fica em pausa a partir do próximo tick. */
	public static void start(ServerLevel level, Villager v, Task t) {
		Task old = get(v);
		if (old != null) end(level, v, old, "replaced");
		if (v.isSleeping()) v.stopSleeping();
		var b = v.getBrain();
		b.eraseMemory(MemoryModuleType.WALK_TARGET);
		b.eraseMemory(MemoryModuleType.PATH);
		b.eraseMemory(MemoryModuleType.LOOK_TARGET);
		v.getNavigation().stop();
		t.lx = v.getX();
		t.ly = v.getY();
		t.lz = v.getZ();
		((LifeVillager) v).tvilas$setTask(t);
		if (t.icon != null) Balloons.show(v, t.icon, 50);
	}

	/** Acaba a tarefa: levanta-se, acorda (da cama de casal), fecha as portas, solta o assento; o vanilla volta. */
	public static void end(ServerLevel level, Villager v, Task t, String why) {
		if (((LifeVillager) v).tvilas$task() != t) return;
		t.endReason = why;
		((LifeVillager) v).tvilas$setTask(null);
		if (t.seated && v.isPassenger()) v.stopRiding();
		if (t.sleeping && v.isSleeping()) v.stopSleeping();
		if (t.places != null) t.places.release(v.getUUID());
		for (BlockPos d : t.doors) closeIfClear(level, v, d, true);
		v.getNavigation().stop();
		var b = v.getBrain();
		b.eraseMemory(MemoryModuleType.WALK_TARGET);
		b.eraseMemory(MemoryModuleType.PATH);
		if (t.kind == Task.Kind.TALK || t.kind == Task.Kind.POINT || t.kind == Task.Kind.FESTA || t.kind == Task.Kind.DELIVER || t.kind == Task.Kind.FISH
				|| t.kind == Task.Kind.MARKET) Props.clear(v);
		if (t.prop != null) { // 0.5.0: a boia nunca fica no mundo
			Stage.remove(t.prop);
			t.prop = null;
		}
		if (t.kind == Task.Kind.PLAY) Play.ended(v, t);
	}

	/** O mixin chama isto a cada tick: true = o cérebro vanilla pode correr (a tarefa acabou). */
	public static boolean tick(ServerLevel level, Villager v, Task t) {
		long now = level.getGameTime();
		if (!v.isAlive() || v.isTrading()) return endNow(level, v, t, "trade");
		if (t.kind != Task.Kind.FLEE && v.hurtTime > 0) return endNow(level, v, t, "hurt");
		if (now >= t.deadline) return endNow(level, v, t, "deadline");
		if ((v.tickCount & 1) != 0) return false;
		try {
			step(level, v, t, now);
		} catch (RuntimeException e) {
			com.tcordeiro.vilas.TVilas.LOGGER.warn("Vilas: tarefa {} de {} falhou: {}", t.kind, v.getUUID(), e.toString());
			return endNow(level, v, t, "error");
		}
		return get(v) != t;
	}

	static boolean endNow(ServerLevel level, Villager v, Task t, String why) {
		end(level, v, t, why);
		return true;
	}

	// ---------------------------------------------------------------- passo

	static void step(ServerLevel level, Villager v, Task t, long now) {
		if (t.follow != null) {
			if (!t.follow.isAlive() || t.follow.level() != level) {
				end(level, v, t, "lost");
				return;
			}
			t.target = t.follow.blockPosition();
		}
		if (t.seated && !v.isPassenger()) { // tiraram-no do assento
			end(level, v, t, "unseated");
			return;
		}
		if (t.kind == Task.Kind.HELP) {
			Helper.step(level, v, t, now); // 0.5.0: decide para onde vai (nunca para dentro de um terreno alheio)
			if (get(v) != t) return;
		}
		switch (t.kind) {
			case TALK -> talk(level, v, t, now);
			case POINT -> point(level, v, t, now);
			default -> {
				if (now < t.holdUntil) {
					v.getNavigation().stop(); // 0.5.0: parado de propósito (a contar, à espera)
					if (t.arrived) stay(level, v, t, now);
				} else if (t.target != null && !t.arrived && !t.seated && !t.sleeping) walk(level, v, t, now);
				else if (t.target != null && t.arrived && (t.kind == Task.Kind.FOLLOW || t.kind == Task.Kind.WELCOME || t.kind == Task.Kind.HELP
						|| (t.kind == Task.Kind.PLAY && t.follow != null))
						&& v.distanceToSqr(Vec3.atBottomCenterOf(t.target)) > (t.reach + 2) * (t.reach + 2))
					t.arrived = false; // o mestre/jogador andou: volta a segui-lo
				if (t.arrived) stay(level, v, t, now);
			}
		}
		doors(level, v, t);
	}

	/** Uma vez a cada {@code n} ticks (o passo corre de 2 em 2 ticks: conta-se a partir do início da tarefa). */
	static boolean every(Task t, long now, int n) {
		return Math.floorMod(now - t.started, n) < 2;
	}

	/** Anda até ao alvo; anti-preso; ceder passagem. */
	static void walk(ServerLevel level, Villager v, Task t, long now) {
		Vec3 c = Vec3.atBottomCenterOf(t.target);
		if (v.distanceToSqr(c) <= t.reach * t.reach) {
			t.arrived = true;
			v.getNavigation().stop();
			arrive(level, v, t, now);
			return;
		}
		if (now < t.waitUntil) return;
		var nav = v.getNavigation();
		if (nav.isDone() || (t.follow != null && every(t, now, 20))) {
			if (!nav.moveTo(c.x, c.y, c.z, t.speed) && nav.isDone()) {
				t.still += 20; // sem caminho possível: conta como preso
			}
		}
		// anti-preso: a cada 20 ticks vê se andou
		if (every(t, now, 20)) {
			double dx = v.getX() - t.lx, dy = v.getY() - t.ly, dz = v.getZ() - t.lz;
			double moved = dx * dx + dy * dy + dz * dz;
			if (moved < LifeRules.STUCK_MOVE * LifeRules.STUCK_MOVE) t.still += 20;
			else t.still = 0;
			t.lx = v.getX();
			t.ly = v.getY();
			t.lz = v.getZ();
			if (LifeRules.stuck(moved, t.still)) {
				// dois aldeões frente a frente: cede passagem o de ID mais baixo
				for (Villager o : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(0.9), o -> o != v && o.isAlive()))
					if (LifeRules.yields(v.getId(), o.getId())) {
						t.waitUntil = now + 30;
						nav.stop();
						t.still = 0;
						return;
					}
				t.still = 0;
				t.replans++;
				if (t.replans > LifeRules.MAX_REPLANS) {
					planB(level, v, t, now);
					return;
				}
				nav.stop();
				nav.moveTo(c.x, c.y, c.z, t.speed); // refaz o caminho
				if (v.onGround()) v.getJumpControl().jump();
			}
		}
	}

	/** O plano B: o assento/cama falhou → fica de pé no sítio; senão desiste (o vanilla volta). */
	static void planB(ServerLevel level, Villager v, Task t, long now) {
		if ((t.seat != null || t.bed != null) && t.fallback != null && !t.fallback.equals(t.target)) {
			if (t.places != null) t.places.release(v.getUUID());
			t.seat = null;
			t.bed = null;
			t.target = t.fallback;
			t.reach = 3.5;
			t.replans = 0;
			t.still = 0;
			v.getNavigation().stop();
			return;
		}
		end(level, v, t, "stuck");
	}

	/** Chegou. */
	static void arrive(ServerLevel level, Villager v, Task t, long now) {
		switch (t.kind) {
			case SOCIAL, SHELTER, MEETING -> {
				if (t.seat != null) {
					if (Seats.sit(level, v, t.seat)) t.seated = true;
					else if (t.places != null) t.places.release(v.getUUID());
				}
			}
			case SLEEP -> {
				if (t.bed != null && v.startSleeping(t.bed)) {
					t.sleeping = true;
					Balloons.show(v, Balloons.SLEEP, 60);
				} else planB(level, v, t, now);
			}
			case HEAL -> Care.heal(level, v, t);
			case REPAIR -> Care.repair(level, v, t);
			case WELCOME -> Life.welcomeArrived(level, v, t);
			case GO -> end(level, v, t, "done");
			case DELIVER -> Mail.arrive(level, v, t);
			case FISH -> Fishing.arrive(level, v, t, now);
			case PLAY -> Play.arrive(level, v, t, now);
			default -> {}
		}
	}

	/** Já lá está: olha à volta, festeja, acompanha. */
	static void stay(ServerLevel level, Villager v, Task t, long now) {
		switch (t.kind) {
			case FESTA -> {
				LifeVillager.Memo m = ((LifeVillager) v).tvilas$memo();
				if (now >= m.nextFestaJump && v.onGround()) {
					v.getJumpControl().jump();
					m.nextFestaJump = now + 30 + level.getRandom().nextInt(60);
					if (level.getRandom().nextInt(3) == 0) {
						Balloons.show(v, level.getRandom().nextBoolean() ? Balloons.NOTE : Balloons.PARTY, 40);
						Balloons.gesture(v, level.getRandom().nextBoolean() ? Gestures.CHEER : Gestures.CLAP, 30);
					}
				}
				if (now >= t.nextStage && t.fallback != null) { // um sítio novo na Praça a cada 5 s
					t.nextStage = now + 100;
					t.target = t.fallback.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
					t.arrived = false;
				}
				lookAtNearest(level, v);
			}
			case SOCIAL, SHELTER, MEETING -> {
				if (every(t, now, 40)) lookAtNearest(level, v);
				// 0.5.1: na Taverna, de vez em quando, come qualquer coisa (pão na mão e o gesto de comer)
				if (t.kind == Task.Kind.SOCIAL && t.places != null && t.places.tavern() != null && every(t, now, 400)
						&& t.places.tavern().center.closerThan(v.blockPosition(), 16) && level.getRandom().nextInt(3) == 0
						&& Props.hold(v, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 50)) {
					Balloons.gesture(v, Gestures.EAT, 50);
					v.playSound(SoundEvents.GENERIC_EAT.value(), 0.5f, 1.0f);
				}
			}
			case FLEE -> {
				if (t.follow != null) look(v, t.follow);
			}
			case FOLLOW, WELCOME, HELP -> {
				if (t.follow != null) look(v, t.follow);
			}
			case FISH -> Fishing.stay(level, v, t, now);
			case MARKET -> Market.stay(level, v, t, now);
			case PLAY -> Play.stay(level, v, t, now);
			default -> {}
		}
	}

	// ---------------------------------------------------------------- conversa e pedir caminho (parados)

	static void talk(ServerLevel level, Villager v, Task t, long now) {
		v.getNavigation().stop();
		if (t.follow != null) look(v, t.follow);
		if (t.line == null) return; // o que ouve só olha
		if (t.stage == 0) {
			Balloons.say(v, Balloons.TALK, t.line, 60);
			Gestures.nod(level, v);
			t.stage = 1;
			t.nextStage = now + 55;
		} else if (t.stage == 1 && now >= t.nextStage) {
			if (t.follow instanceof Villager o) {
				Balloons.say(o, null, t.reply, 50);
				if (t.icon != null && t.icon.equals(Balloons.ANGRY)) Gestures.no(o);
				else Gestures.nod(level, o);
			}
			t.stage = 2;
			t.nextStage = now + 50;
		} else if (t.stage == 2 && now >= t.nextStage) {
			end(level, v, t, "done");
			if (t.follow instanceof Villager o && get(o) != null && get(o).kind == Task.Kind.TALK) end(level, o, get(o), "done");
		}
	}

	static void point(ServerLevel level, Villager v, Task t, long now) {
		v.getNavigation().stop();
		if (t.target == null) return;
		Vec3 to = Vec3.atCenterOf(t.target);
		double dx = to.x - v.getX(), dz = to.z - v.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		v.setYBodyRot(yaw);
		v.setYRot(yaw);
		v.getLookControl().setLookAt(v.getX() + dx, v.getEyeY() + 0.4, v.getZ() + dz);
		// o rasto (só para quem perguntou), a cada 10 ticks durante 5 s
		if (t.asker != null && every(t, now, 10) && level.getEntity(t.asker) instanceof ServerPlayer p) {
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 0.1) {
				int k = (int) ((now - t.started) / 10) % 12;
				double d = 1.5 + k;
				if (d < len) level.sendParticles(p, ParticleTypes.END_ROD, false, true, v.getX() + dx / len * d, v.getY() + 1.1, v.getZ() + dz / len * d, 2,
						0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------- portas

	/** Abre a porta de madeira do caminho (o próximo nó e o atual); fecha as que ficaram para trás quando já ninguém passa. */
	static void doors(ServerLevel level, Villager v, Task t) {
		Path path = v.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			int i = path.getNextNodeIndex();
			for (int k = Math.max(0, i - 1); k <= Math.min(path.getNodeCount() - 1, i + 1); k++) {
				BlockPos p = path.getNodePos(k);
				if (v.distanceToSqr(Vec3.atCenterOf(p)) > 4.0) continue;
				BlockState s = level.getBlockState(p);
				if (DoorBlock.isWoodenDoor(s) && s.getBlock() instanceof DoorBlock door && !door.isOpen(s)) {
					door.setOpen(v, level, s, p, true);
					if (!t.doors.contains(p.immutable()) && t.doors.size() < 4) t.doors.add(p.immutable());
				}
			}
		}
		t.doors.removeIf(d -> closeIfClear(level, v, d, false));
	}

	/** Fecha a porta se este aldeão já passou (mais de 2 blocos) e ninguém está à porta (segura-a ao último). Devolve true se a largou. */
	static boolean closeIfClear(ServerLevel level, Villager v, BlockPos d, boolean force) {
		if (!force && v.distanceToSqr(Vec3.atCenterOf(d)) < 2.5 * 2.5) return false;
		BlockState s = level.getBlockState(d);
		if (!(s.getBlock() instanceof DoorBlock door) || !DoorBlock.isWoodenDoor(s)) return true;
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(d).inflate(1.5, 0.5, 1.5), e -> e != v && e.isAlive()).isEmpty())
			return force; // ainda alguém a passar: fica aberta (no fim da tarefa larga-se e o vanilla trata)
		if (door.isOpen(s)) door.setOpen(v, level, s, d, false);
		return true;
	}

	// ---------------------------------------------------------------- olhar

	public static void look(Villager v, Entity e) {
		// «sim» com a cabeça: durante o aceno o olhar desce e sobe (o Fresh Animations segue o head_pitch)
		double dy = 0;
		long now = v.level().getGameTime();
		if (now < ((LifeVillager) v).tvilas$memo().nodUntil) dy = ((now / 3) % 2 == 0) ? -0.9 : 0.4;
		v.getLookControl().setLookAt(e.getX(), e.getEyeY() + dy, e.getZ());
	}

	static void lookAtNearest(ServerLevel level, Villager v) {
		Entity best = null;
		double bd = 36;
		for (Entity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, v.getBoundingBox().inflate(6),
				e -> e != v && e.isAlive() && (e instanceof Villager || e instanceof ServerPlayer))) {
			double d = e.distanceToSqr(v);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		if (best != null) look(v, best);
	}

	static void sound(ServerLevel level, Villager v, net.minecraft.sounds.SoundEvent s) {
		level.playSound(null, v.blockPosition(), s, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1.0f);
	}

	static void yes(ServerLevel level, Villager v) {
		sound(level, v, SoundEvents.VILLAGER_YES);
	}
}
