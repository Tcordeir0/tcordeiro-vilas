package com.tcordeiro.vilas.block;

import java.util.UUID;

import com.tcordeiro.vilas.logic.AlarmRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A Mesa do Prefeito trabalha: regista a vila (1.º tick), conta a população e sobe o nível (a cada 5 s) e cuida do ALARME (a cada
 * segundo). As regras (quando há perigo, quantos golens, intervalos) estão em {@link AlarmRules} — aqui só se age no mundo.
 * <p>
 * Defesa: sem «guardas» novos (seria outra entidade, IA e modelos — inflacionava o mod). Em vez disso o Prefeito toca o sino,
 * chama golens de ferro vanilla (no máximo {@link AlarmRules#MAX_GOLEMS} por alarme, +1/+2 com o reforço comprado pela
 * tesouraria, com intervalo) e dá a todos os golens da
 * vila Força, Resistência e Vida Extra enquanto o alarme durar. Os golens chamados vão-se embora depois e não largam ferro.
 */
public class MayorDeskBlockEntity extends BlockEntity {
	/** Etiqueta (entity tag) dos golens chamados pelo alarme. */
	public static final String CALLED_TAG = "tvilas.called";
	private int ticks;

	public MayorDeskBlockEntity(BlockPos pos, BlockState state) {
		super(VilasBlocks.MAYOR_DESK_BE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MayorDeskBlockEntity be) {
		if (!(level instanceof ServerLevel sl)) return;
		be.ticks++;
		if (be.ticks % 20 != 1) return;
		Village v = VillageManager.byDesk(sl, pos);
		if (v == null) {
			v = VillageManager.found(sl, pos, com.tcordeiro.vilas.VilasConfig.get().radius());
			if (v == null) return; // outra Mesa já manda nesta vila: esta não faz nada
		}
		if (be.ticks % 100 == 1) {
			VillageManager.updateLevel(sl, v);
			com.tcordeiro.vilas.village.VillageEconomy.ensureMayor(sl, v, sl.getGameTime());
			com.tcordeiro.vilas.village.VillageEconomy.daily(sl, v, com.tcordeiro.vilas.village.VillageEconomy.day(sl));
		}
		alarmTick(sl, v, sl.getGameTime());
		com.tcordeiro.vilas.build.Builders.tick(sl, v, sl.getGameTime()); // 0.2.0: o construtor põe 1 bloco por segundo à vista
	}

	/** Invasão ativa perto da Mesa? */
	public static boolean raidActive(ServerLevel level, Village v) {
		Raid raid = level.getRaidAt(v.center);
		return raid != null && !raid.isOver();
	}

	public static int monstersNear(ServerLevel level, Village v) {
		AABB box = new AABB(v.center).inflate(AlarmRules.MONSTER_RADIUS);
		return level.getEntitiesOfClass(Monster.class, box, Entity::isAlive).size();
	}

	/** Um passo do alarme no instante {@code now} (público para os testes poderem avançar o tempo). */
	public static void alarmTick(ServerLevel level, Village v, long now) {
		Village.Alarm a = v.alarm;
		boolean dangerNow = AlarmRules.danger(raidActive(level, v), monstersNear(level, v));
		if (dangerNow) a.lastDanger = now;
		boolean was = a.on;
		a.on = AlarmRules.stillAlarmed(dangerNow, a.lastDanger, now);
		if (a.on && !was) {
			ringBells(level, v);
			VillageManager.broadcast(level, v, Component.translatable("tvilas.alarm.on"));
		} else if (!a.on && was) {
			a.ended = now;
			VillageManager.broadcast(level, v, Component.translatable("tvilas.alarm.off"));
		}
		if (a.on) {
			if (AlarmRules.canCallGolem(true, a.golems, a.lastGolem, now, AlarmRules.cap(v.economy.alarmBonus))) {
				a.lastGolem = now;
				IronGolem golem = callGolem(level, v);
				if (golem != null) {
					a.golems++;
					a.called.add(golem.getUUID());
					ringBells(level, v);
				}
			}
			strengthenGolems(level, v);
		} else {
			if (a.golems > 0 && AlarmRules.mayResetCount(false, a.lastGolem, now)) {
				a.golems = 0;
				a.lastGolem = -1;
			}
			if (!a.called.isEmpty() && a.ended >= 0 && now - a.ended >= AlarmRules.GOLEM_STAY) dismissGolems(level, v);
		}
		VillageData.get(level).changed();
	}

	/** Toca os sinos perto da Mesa (raio 12); se não houver, o som do sino na própria Mesa. */
	static void ringBells(ServerLevel level, Village v) {
		boolean rang = false;
		for (BlockPos p : BlockPos.betweenClosed(v.center.offset(-12, -4, -12), v.center.offset(12, 6, 12))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof BellBlock bell) rang |= bell.attemptToRing(level, p.immutable(), null);
		}
		if (!rang) level.playSound(null, v.center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
	}

	static IronGolem callGolem(ServerLevel level, Village v) {
		return SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, v.center, 10, 8, 6,
				SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false).map(g -> {
					g.addTag(CALLED_TAG);
					return g;
				}).orElse(null);
	}

	/** Força I, Resistência I e Vida Extra (+20) a todos os golens da vila, renovados enquanto o alarme dura. */
	static void strengthenGolems(ServerLevel level, Village v) {
		java.util.Set<IronGolem> golems = new java.util.HashSet<>(level.getEntitiesOfClass(IronGolem.class, VillageManager.box(v, level),
				e -> e.isAlive() && v.covers(e.getBlockX(), e.getBlockZ())));
		for (UUID id : v.alarm.called) if (level.getEntity(id) instanceof IronGolem g && g.isAlive()) golems.add(g);
		for (IronGolem g : golems) {
			MobEffectInstance cur = g.getEffect(MobEffects.STRENGTH);
			if (cur != null && cur.getDuration() > 200) continue;
			g.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 0));
			g.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 0));
			g.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 400, 4));
		}
	}

	/** Os golens chamados vão-se embora (os que ainda estiverem carregados). */
	static void dismissGolems(ServerLevel level, Village v) {
		for (UUID id : v.alarm.called) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) e.discard();
		}
		v.alarm.called.clear();
	}

	/** Quantos golens chamados por esta vila estão vivos (para os testes). */
	public static int calledAlive(ServerLevel level, Village v) {
		int n = 0;
		for (UUID id : v.alarm.called)
			if (level.getEntity(id) instanceof IronGolem g && g.isAlive() && g.entityTags().contains(CALLED_TAG)) n++;
		return n;
	}
}
