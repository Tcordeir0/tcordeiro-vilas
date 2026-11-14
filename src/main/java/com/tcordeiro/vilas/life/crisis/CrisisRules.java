package com.tcordeiro.vilas.life.crisis;

/**
 * 0.6 crises — As regras puras (testadas com JUnit) da reconstrução, das crises, do funeral e da velhice. Nada aqui toca no mundo.
 */
public final class CrisisRules {
	private CrisisRules() {}

	// ---------------------------------------------------------------- reconstrução

	/** Teto de blocos registados por vila (o resto do estrago fica como está: nunca uma «fotografia» da vila). */
	public static final int REBUILD_MAX = 512;
	/** Raio (à volta da Mesa) onde se registam blocos partidos; nunca maior do que o território. */
	public static final int REBUILD_RADIUS = 96;
	/** Quanto tempo sem estragos (e sem alarme nem invasão) até o Construtor começar a repor (1 minuto). */
	public static final long CALM_TICKS = 1200;

	/**
	 * REGRA DE SEGURANÇA: este bloco partido pode ficar registado para a reconstrução? Só dentro da área da vila, NUNCA num terreno
	 * de jogador, nunca blocos com conteúdo (baús, caixas: o que lá estava cai e não se repõe às cegas), nunca ar/fogo/líquidos/TNT,
	 * nunca a mesma posição duas vezes e nunca acima do teto.
	 */
	public static boolean mayRecord(boolean insideArea, boolean inLot, boolean emptyOrFire, boolean hasBlockEntity, boolean liquidOrTnt,
			boolean alreadyRecorded, int recorded, int cap) {
		if (!insideArea || inLot) return false;
		if (emptyOrFire || hasBlockEntity || liquidOrTnt) return false;
		if (alreadyRecorded) return false;
		return recorded < cap;
	}

	/** Quem partiu conta? Monstros e outros bichos (creeper, ghast, wither, zombie à porta) sim; jogadores nunca; explosão sem causador não. */
	public static boolean causeCounts(boolean causeIsMob, boolean causeIsPlayer) {
		return causeIsMob && !causeIsPlayer;
	}

	/** O Construtor pode começar? Há blocos, não há perigo e passou o tempo de calma desde o último estrago. */
	public static boolean mayStartRebuild(int recorded, boolean danger, long lastBroken, long now) {
		return recorded > 0 && !danger && now - lastBroken >= CALM_TICKS;
	}

	// ---------------------------------------------------------------- crises

	public static final String FIRE = "fire", HUNGER = "hunger", NIGHT = "night";
	/** Revisão de segurança: um fogo que nasce com um jogador a menos disto (blocos) não paga a quem o apaga. */
	public static final int ORIGIN_RADIUS = 16;

	/** Recompensas (em esmeraldas, pagas em CubCoin ou esmeraldas), meta e quantos jogadores no máximo recebem por crise. */
	public static final int FIRE_EMERALDS = 2, FIRE_GOAL = 3, FIRE_PAYEES = 4;
	public static final int NIGHT_EMERALDS = 2, NIGHT_GOAL = 4, NIGHT_PAYEES = 4, NIGHT_MONSTERS = 6;
	public static final int HUNGER_PAYEES = 3;
	public static final int REPUTATION = 5;
	/** Dias entre duas crises do mesmo tipo (com missão) na mesma vila. */
	public static final int FIRE_COOLDOWN = 1, NIGHT_COOLDOWN = 2, HUNGER_COOLDOWN = 4;
	/** Quem acendeu fogo na vila nos últimos 5 minutos não recebe por apagar (nada de pôr fogo para ganhar). */
	public static final long LIGHTER_TICKS = 6000;

	/** Pode abrir-se uma crise deste tipo hoje? (uma por tipo de cada vez e com intervalo de dias) */
	public static boolean mayOpen(boolean alreadyOpen, long lastDay, long today, int cooldownDays) {
		if (alreadyOpen) return false;
		return lastDay < 0 || today - lastDay >= cooldownDays;
	}

	/** A comida da vila está baixa? Menos de 1 ponto de comida (pão = 4, batata/cenoura/beterraba = 1) por aldeão; só com 3+ aldeões. */
	public static boolean hungerLow(int foodPoints, int villagers) {
		return villagers >= 3 && foodPoints < villagers;
	}

	/** Dias seguidos com pouca comida até haver crise. */
	public static final int HUNGER_DAYS = 2;

	/** Quantos pães se pedem (um por aldeão, entre 6 e 24). */
	public static int hungerGoal(int villagers) {
		return Math.max(6, Math.min(24, villagers));
	}

	/**
	 * Quanto paga a crise da fome (esmeraldas): 1,25 × o valor do pão no Agricultor (6 pães por esmeralda), arredondado PARA BAIXO e
	 * pelo menos 1 — nunca dá lucro comprar o pão ao Agricultor e trazê-lo de volta (só a partir de muitos pães, e uma vez por crise).
	 */
	public static int hungerEmeralds(int goal) {
		return Math.max(1, goal * 5 / 24);
	}

	/** Noite perigosa: N monstros dentro da vila, de noite. */
	public static boolean nightDanger(int monsters, int threshold, boolean night) {
		return night && monsters >= Math.max(1, threshold);
	}

	/**
	 * REGRA A1 das crises: a vila pode pagar a este jogador? Uma vez por jogador em cada crise, no máximo {@code maxPayees} jogadores por
	 * crise, a tesouraria tem o dinheiro e não se passa do teto do dia (o mesmo das missões).
	 */
	public static boolean mayPay(boolean alreadyPaid, int paidPlayers, int maxPayees, long treasury, long paidToday, long dailyCap, long reward) {
		if (alreadyPaid || paidPlayers >= maxPayees) return false;
		if (reward <= 0 || treasury < reward) return false;
		return dailyCap <= 0 || paidToday + reward <= dailyCap;
	}

	// ---------------------------------------------------------------- funeral e velhice

	/** O funeral é no dia seguinte, a esta hora (15 h) e dura uma hora de jogo. */
	public static final long FUNERAL_TIME = 9000, FUNERAL_LENGTH = 1000;
	/** Campas por vila (as mais antigas ficam; depois disto já não se põem campas novas, só há funeral). */
	public static final int MAX_GRAVES = 16;
	/** Opinião a partir da qual um aldeão é amigo (fica de luto). */
	public static final int FRIEND_OPINION = 10;
	/** Luto: um dia de jogo. */
	public static final long MOURNING_TICKS = 24000;

	/** O funeral deste morto já devia começar? (no dia a seguir à morte, a partir das 15 h; ou logo, se já passou esse dia) */
	public static boolean funeralDue(long deathDay, long today, long timeOfDay) {
		return today > deathDay + 1 || (today == deathDay + 1 && timeOfDay >= FUNERAL_TIME);
	}

	/** Dias que viveu na vila (desde a primeira vez que foi visto; {@link #UNKNOWN} = nunca visto), pelo menos 1. */
	public static long daysLived(long firstSeenDay, long deathDay) {
		if (firstSeenDay == UNKNOWN || deathDay < firstSeenDay) return 1;
		return Math.max(1, deathDay - firstSeenDay + 1);
	}

	public static final long UNKNOWN = Long.MIN_VALUE;

	/** Velhice: dias de vida por omissão (só conta se a config estiver ligada). */
	public static final int LIFESPAN_DAYS = 120;

	/**
	 * REGRA: a velhice pode levar este aldeão? Só com a config LIGADA (desligada por omissão), só adultos, nunca o Prefeito, nunca
	 * aldeões com nome dado e NUNCA aldeões com trocas trancadas (com experiência de trocas: alguém já negociou com ele).
	 */
	public static boolean mayDieOfAge(boolean enabled, long days, int lifespan, boolean lockedTrades, boolean named, boolean mayor, boolean baby) {
		if (!enabled || lockedTrades || named || mayor || baby) return false;
		return days >= Math.max(1, lifespan);
	}
}
