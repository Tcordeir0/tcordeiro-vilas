package com.tcordeiro.vilas.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlarmRulesTest {
	@Test
	void dangerFromRaidOrMonsters() {
		assertTrue(AlarmRules.danger(true, 0));
		assertTrue(AlarmRules.danger(false, AlarmRules.MONSTERS_FOR_ALARM));
		assertFalse(AlarmRules.danger(false, AlarmRules.MONSTERS_FOR_ALARM - 1));
	}

	@Test
	void golemsAreCappedPerAlarm() {
		long t = 1000;
		int cap = AlarmRules.cap(0);
		assertTrue(AlarmRules.canCallGolem(true, 0, -1, t, cap), "o primeiro vem logo");
		assertFalse(AlarmRules.canCallGolem(true, 1, t, t + 1, cap), "intervalo entre golens");
		assertTrue(AlarmRules.canCallGolem(true, 1, t, t + AlarmRules.INTERVAL, cap));
		assertFalse(AlarmRules.canCallGolem(true, AlarmRules.MAX_GOLEMS, t, t + 100 * AlarmRules.INTERVAL, cap), "limite por alarme");
		assertFalse(AlarmRules.canCallGolem(false, 0, -1, t, cap), "sem alarme não chama");
	}

	@Test
	void alarmUpgradeRaisesCapButNotForever() {
		org.junit.jupiter.api.Assertions.assertEquals(2, AlarmRules.cap(0));
		org.junit.jupiter.api.Assertions.assertEquals(3, AlarmRules.cap(1));
		org.junit.jupiter.api.Assertions.assertEquals(4, AlarmRules.cap(2));
		org.junit.jupiter.api.Assertions.assertEquals(4, AlarmRules.cap(99), "reforço no máximo +2");
		org.junit.jupiter.api.Assertions.assertEquals(2, AlarmRules.cap(-5));
		assertFalse(AlarmRules.canCallGolem(true, 4, 0, 1_000_000, AlarmRules.cap(99)));
	}

	@Test
	void counterResetsOnlyAfterCooldown() {
		long t = 5000;
		assertFalse(AlarmRules.mayResetCount(true, t, t + AlarmRules.RESET_COOLDOWN * 2), "durante o alarme não");
		assertFalse(AlarmRules.mayResetCount(false, t, t + AlarmRules.RESET_COOLDOWN - 1), "ligar/desligar não dá golens novos");
		assertTrue(AlarmRules.mayResetCount(false, t, t + AlarmRules.RESET_COOLDOWN));
	}

	@Test
	void alarmCalmsDownAfterDelay() {
		assertTrue(AlarmRules.stillAlarmed(false, 100, 100 + AlarmRules.CALM_DELAY - 1));
		assertFalse(AlarmRules.stillAlarmed(false, 100, 100 + AlarmRules.CALM_DELAY));
		assertFalse(AlarmRules.stillAlarmed(false, -1, 100));
	}
}
