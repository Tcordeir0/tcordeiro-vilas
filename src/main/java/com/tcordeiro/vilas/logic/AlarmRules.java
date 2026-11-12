package com.tcordeiro.vilas.logic;

/**
 * Regras do alarme (lógica pura, testada com JUnit). Durante um alarme (invasão ou monstros perto), o Prefeito chama golens de
 * ferro: no máximo {@link #MAX_GOLEMS} por alarme e com pelo menos {@link #INTERVAL} ticks entre chamadas. O contador só volta a
 * zero quando o alarme acabou E já passou {@link #RESET_COOLDOWN} desde a última chamada (para não se fazer «quinta de ferro»
 * ligando e desligando o alarme).
 */
public final class AlarmRules {
	private AlarmRules() {}

	public static final int MAX_GOLEMS = 2;
	/** 15 segundos entre golens. */
	public static final long INTERVAL = 300;
	/** Meio dia de jogo até o contador de golens recomeçar. */
	public static final long RESET_COOLDOWN = 12000;
	/** Monstros perto (raio {@link #MONSTER_RADIUS}) para soar o alarme sem invasão. */
	public static final int MONSTERS_FOR_ALARM = 3;
	public static final int MONSTER_RADIUS = 32;
	/** O alarme acaba 30 s depois de deixar de haver perigo. */
	public static final long CALM_DELAY = 600;
	/** Os golens chamados vão-se embora 60 s depois de o alarme acabar. */
	public static final long GOLEM_STAY = 1200;

	/** Há perigo? Uma invasão ativa ou monstros suficientes perto. */
	public static boolean danger(boolean raidActive, int monstersNear) {
		return raidActive || monstersNear >= MONSTERS_FOR_ALARM;
	}

	/**
	 * REGRA DE SEGURANÇA: pode chamar mais um golem agora? {@code lastCall} &lt; 0 = ainda nenhum neste alarme; {@code cap} = {@link #cap}.
	 */
	public static boolean canCallGolem(boolean alarm, int calledThisAlarm, long lastCall, long now, int cap) {
		if (!alarm) return false;
		if (calledThisAlarm >= cap) return false;
		return lastCall < 0 || now - lastCall >= INTERVAL;
	}

	/** Golens por alarme: {@link #MAX_GOLEMS} + o reforço comprado pela tesouraria (no máximo +2). */
	public static int cap(int alarmBonus) {
		return MAX_GOLEMS + Math.max(0, Math.min(2, alarmBonus));
	}

	/** O contador de golens pode recomeçar (novo alarme pode chamar outros 2)? */
	public static boolean mayResetCount(boolean alarm, long lastCall, long now) {
		return !alarm && (lastCall < 0 || now - lastCall >= RESET_COOLDOWN);
	}

	/** O alarme continua? (perigo agora, ou houve há menos de {@link #CALM_DELAY} ticks) */
	public static boolean stillAlarmed(boolean dangerNow, long lastDanger, long now) {
		return dangerNow || (lastDanger >= 0 && now - lastDanger < CALM_DELAY);
	}
}
