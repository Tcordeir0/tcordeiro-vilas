package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.block.MayorDeskBlockEntity;
import com.tcordeiro.vilas.logic.AlarmRules;
import com.tcordeiro.vilas.village.Village;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.raid.Raid;

/**
 * Alarme: numa invasão (raid de verdade, criada como o vanilla cria) o Prefeito chama golens — no máximo 2, com intervalo — e
 * fortalece os golens; acabado o alarme, os golens chamados vão-se embora. O tempo é avançado à mão (alarmTick com «now»).
 */
public class AlarmGameTests {
	@GameTest(maxTicks = 100)
	public void raidCallsGolemsUpToTheCap(GameTestHelper h) {
		Village v = TestUtil.village(h);
		TestUtil.floor(h, 6);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		Raid raid = h.getLevel().getRaids().createOrExtendRaid(p, v.center);
		h.assertTrue(raid != null, "a invasão começou");
		h.assertTrue(MayorDeskBlockEntity.raidActive(h.getLevel(), v), "o Prefeito vê a invasão");
		long t = h.getLevel().getGameTime() + 1_000_000;
		try {
			MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t);
			h.assertTrue(v.alarm.on, "alarme ligado");
			h.assertValueEqual(v.alarm.golems, 1, "o primeiro golem vem logo");
			MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t + 20);
			h.assertValueEqual(v.alarm.golems, 1, "espera o intervalo");
			MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t + AlarmRules.INTERVAL);
			h.assertValueEqual(v.alarm.golems, 2, "o segundo");
			for (int k = 2; k <= 10; k++) MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t + k * AlarmRules.INTERVAL);
			h.assertValueEqual(v.alarm.golems, AlarmRules.cap(v.economy.alarmBonus), "nunca mais de 2 por invasão (sem reforço)");
			h.assertValueEqual(MayorDeskBlockEntity.calledAlive(h.getLevel(), v), 2, "2 golens chamados vivos");
			for (var id : v.alarm.called)
				if (h.getLevel().getEntity(id) instanceof IronGolem g)
					h.assertTrue(g.hasEffect(MobEffects.STRENGTH) && g.hasEffect(MobEffects.HEALTH_BOOST), "golens fortalecidos");
		} finally {
			raid.stop();
		}
		// sem invasão: o alarme acalma, os golens vão-se embora, e só um meio dia depois o contador recomeça
		long end = t + 10 * AlarmRules.INTERVAL + AlarmRules.CALM_DELAY + 1;
		MayorDeskBlockEntity.alarmTick(h.getLevel(), v, end);
		h.assertTrue(!v.alarm.on, "alarme desligado");
		MayorDeskBlockEntity.alarmTick(h.getLevel(), v, end + AlarmRules.GOLEM_STAY);
		h.assertValueEqual(MayorDeskBlockEntity.calledAlive(h.getLevel(), v), 0, "os golens chamados foram-se embora");
		h.assertValueEqual(v.alarm.golems, 2, "ainda não recomeça a contar");
		MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t + 10 * AlarmRules.INTERVAL + AlarmRules.RESET_COOLDOWN);
		h.assertValueEqual(v.alarm.golems, 0, "meio dia depois, recomeça");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void monstersNearRingTheAlarm(GameTestHelper h) {
		Village v = TestUtil.village(h);
		long t = h.getLevel().getGameTime() + 2_000_000;
		MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t);
		h.assertTrue(!v.alarm.on, "sem monstros não há alarme");
		for (int i = 0; i < AlarmRules.MONSTERS_FOR_ALARM; i++) h.spawn(EntityTypes.HUSK, new BlockPos(2 + i * 2, 1, 2)).setNoAi(true);
		MayorDeskBlockEntity.alarmTick(h.getLevel(), v, t + 20);
		h.assertTrue(v.alarm.on, "3 monstros perto: alarme");
		h.killAllEntitiesOfClass(Husk.class);
		h.killAllEntitiesOfClass(IronGolem.class);
		h.succeed();
	}
}
