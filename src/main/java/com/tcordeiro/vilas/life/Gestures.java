package com.tcordeiro.vilas.life;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.4.0 — Gestos SÓ com estados vanilla (o modelo do aldeão não muda; o Fresh Animations lê estes estados): «não» = o abanar de
 * cabeça vanilla ({@code setUnhappyCounter}), «sim» = a cabeça desce e sobe a olhar para alguém, com o som vanilla. Os sons têm
 * tempo de espera (2 s por aldeão), para o pacote «Vocal Villagers» não virar um coro.
 */
public final class Gestures {
	private Gestures() {}

	public static final int SOUND_COOLDOWN = 40;
	/** 0.5.0: códigos da variável EMF {@code tvilas_gesture} (0 = nenhum). */
	public static final int WAVE = 1, CHEER = 2, SHRUG = 3, NOD = 4, NO = 5, POINT = 6;

	/** 0.5.0: acena (balão + gesto EMF). */
	public static void wave(Villager v) {
		Balloons.gesture(v, WAVE, 30);
	}

	/** 0.5.0: festeja (um pulinho, como o «celebrar» vanilla, + gesto EMF). */
	public static void cheer(Villager v) {
		if (v.onGround()) v.getJumpControl().jump();
		Balloons.gesture(v, CHEER, 30);
	}

	/** 0.5.0: encolhe os ombros (gesto EMF). */
	public static void shrug(Villager v) {
		Balloons.gesture(v, SHRUG, 25);
	}

	static boolean soundReady(Villager v) {
		LifeVillager.Memo m = ((LifeVillager) v).tvilas$memo();
		long now = v.level().getGameTime();
		if (now < m.nextSound) return false;
		m.nextSound = now + SOUND_COOLDOWN;
		return true;
	}

	/** Abana a cabeça («não»). */
	public static void no(Villager v) {
		v.setUnhappyCounter(40);
		Balloons.gesture(v, NO, 30);
		if (soundReady(v)) v.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1.0f);
	}

	/** Acena com a cabeça («sim»): só se vê enquanto olha para alguém (tarefa de conversa, presente, caminho). */
	public static void nod(ServerLevel level, Villager v) {
		((LifeVillager) v).tvilas$memo().nodUntil = level.getGameTime() + 12;
		Balloons.gesture(v, NOD, 12);
		if (soundReady(v)) level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1.0f);
	}

	/** Olha já para a entidade (sem tarefa: o cérebro volta a mandar no olhar daqui a pouco). */
	public static void glance(Villager v, net.minecraft.world.entity.Entity e) {
		v.getLookControl().setLookAt(e.getX(), e.getEyeY(), e.getZ());
		v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
				new net.minecraft.world.entity.ai.behavior.EntityTracker(e, true));
	}
}
