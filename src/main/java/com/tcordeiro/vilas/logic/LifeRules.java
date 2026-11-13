package com.tcordeiro.vilas.logic;

/**
 * 0.4.0 — As regras (puras, testadas com JUnit) da vida dos aldeões: distâncias do LOD, horários (convívio, deitar aos poucos,
 * festa, sino), tetos dos presentes, salários dos guardas, reembolso da festa, curas e reparações por dia, reputação visível.
 * <p>
 * REGRAS DE ECONOMIA E SEGURANÇA (com mutações em {@code tools/mutation_test.py}):
 * <ul>
 * <li>presentes: no máximo {@link #GIFTS_PER_VILLAGER_WEEK} por aldeão e por semana e {@link #GIFT_REP_PER_PLAYER_DAY} de reputação
 * por jogador e por dia; um presente detestado não dá nada (e é devolvido);</li>
 * <li>guardas: o salário sai SEMPRE da tesouraria; só se pagam os guardas que a tesouraria consegue pagar, nunca fica negativa;</li>
 * <li>festa: o reembolso sai SEMPRE da tesouraria e do orçamento da festa, é uma percentagem do que o jogador pagou, com teto por
 * jogador; nunca é dinheiro novo;</li>
 * <li>presente de volta: um por par jogador–aldeão por semana e um por jogador por dia (itens da profissão, nunca dinheiro).</li>
 * </ul>
 */
public final class LifeRules {
	private LifeRules() {}

	// ---------------------------------------------------------------- LOD por distância ao jogador mais perto

	/** Detalhe total até 48 blocos, reduzido até 96, quase nada além. */
	public static final int FULL = 48, REDUCED = 96;
	public static final int LOD_FULL = 2, LOD_REDUCED = 1, LOD_NONE = 0;

	public static int lod(double distSqToNearestPlayer) {
		if (distSqToNearestPlayer <= FULL * FULL) return LOD_FULL;
		if (distSqToNearestPlayer <= REDUCED * REDUCED) return LOD_REDUCED;
		return LOD_NONE;
	}

	/** Balões e falas só chegam a quem está a 32 blocos ou menos. */
	public static final int BALLOON_RANGE = 32;

	// ---------------------------------------------------------------- horas (tempo do dia: 0 = 6 h, 1000 por hora)

	public static final long DAY = 24000;
	/** O convívio na Praça/Taverna começa às 16 h (10000) e cada aldeão deita-se à sua hora, entre as 18 h e as 19 h 30. */
	public static final long SOCIAL_START = 10000, BED_BASE = 12000, BED_SPREAD = 1500;
	/** Acorda-se (quem dormiu na cama de casal) às 6 h 30. */
	public static final long WAKE = 500;
	/** Festa: das 12 h às 17 h. */
	public static final long FESTA_START = 6000, FESTA_END = 11000;
	/** Trabalho (em que as crianças seguem o mestre): das 8 h às 15 h. */
	public static final long WORK_START = 2000, WORK_END = 9000;
	/** O sino da Prefeitura toca às 8 h, às 12 h e às 18 h. */
	public static final long[] CHIMES = {2000, 6000, 12000};

	public static long timeOfDay(long dayTime) {
		return Math.floorMod(dayTime, DAY);
	}

	/** A hora a que este aldeão se deita (cada um à sua: deitam-se aos poucos). */
	public static long bedtime(long seed) {
		return BED_BASE + Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 17, BED_SPREAD);
	}

	/** Hora do convívio para este aldeão? (das 16 h até à hora de se deitar) */
	public static boolean socialTime(long timeOfDay, long seed) {
		return timeOfDay >= SOCIAL_START && timeOfDay < bedtime(seed);
	}

	/** Noite (hora de dormir na cama de casal)? */
	public static boolean night(long timeOfDay) {
		return timeOfDay >= BED_BASE || timeOfDay < WAKE;
	}

	public static boolean festaTime(long timeOfDay) {
		return timeOfDay >= FESTA_START && timeOfDay < FESTA_END;
	}

	public static boolean workTime(long timeOfDay) {
		return timeOfDay >= WORK_START && timeOfDay < WORK_END;
	}

	/** O sino deve tocar agora? ({@code last} = a última hora a que tocou, para não repetir.) Devolve a hora, ou −1. */
	public static long chime(long timeOfDay, long lastChime) {
		for (long c : CHIMES)
			if (timeOfDay >= c && timeOfDay < c + 200 && lastChime != c) return c;
		return -1;
	}

	/** Badaladas: 8 h → 8, 12 h → 12, 18 h → 6 (como os relógios das torres), no máximo 12. */
	public static int strokes(long chimeTime) {
		int hour = (int) ((chimeTime / 1000 + 6) % 24);
		int h12 = hour % 12 == 0 ? 12 : hour % 12;
		return Math.min(12, h12);
	}

	// ---------------------------------------------------------------- clima

	/** Frio para ir para a lareira (°C, com o Clima). */
	public static final double COLD_C = 5.0;

	public static boolean cold(double celsius) {
		return celsius < COLD_C;
	}

	// ---------------------------------------------------------------- tarefas: prazos, anti-preso

	/** Sem andar 0,3 blocos em 60 ticks = preso: refaz o caminho; à 3.ª vez desiste (plano B). */
	public static final int STUCK_TICKS = 60, MAX_REPLANS = 2;
	public static final double STUCK_MOVE = 0.3;

	public static boolean stuck(double movedSq, int ticksWithoutMoving) {
		return movedSq < STUCK_MOVE * STUCK_MOVE && ticksWithoutMoving >= STUCK_TICKS;
	}

	/** Dois aldeões presos frente a frente: cede passagem o de ID mais baixo (o outro continua). */
	public static boolean yields(int myId, int otherId) {
		return myId < otherId;
	}

	// ---------------------------------------------------------------- conversas

	/** Cada aldeão conversa no máximo de 60 em 60 s; na vila, uma conversa nova no máximo a cada 8 s. */
	public static final int TALK_COOLDOWN = 1200, VILLAGE_TALK_COOLDOWN = 160;
	public static final int OPINION_MIN = -100, OPINION_MAX = 100;

	public static int opinionAfterTalk(int opinion, boolean friendly) {
		return Math.max(OPINION_MIN, Math.min(OPINION_MAX, opinion + (friendly ? 2 : -1)));
	}

	/** Conversa amigável se a opinião não for má (abaixo de −20 resmungam). */
	public static boolean friendly(int opinion) {
		return opinion > -20;
	}

	// ---------------------------------------------------------------- reputação visível

	public static final int TIER_DISTRUST = 0, TIER_NEUTRAL = 1, TIER_FRIENDLY = 2, TIER_FRIEND = 3;
	public static final String[] TIER_KEYS = {"tvilas.rep.distrust", "tvilas.rep.neutral", "tvilas.rep.friendly", "tvilas.rep.friend"};

	/** A reputação de UM aldeão (mexericos vanilla, {@code Villager.getPlayerReputation}). */
	public static int villagerTier(int gossip) {
		if (gossip <= -30) return TIER_DISTRUST;
		if (gossip < 20) return TIER_NEUTRAL;
		if (gossip < 60) return TIER_FRIENDLY;
		return TIER_FRIEND;
	}

	/** A reputação na vila (a do quadro). */
	public static int villageTier(int reputation) {
		if (reputation < 0) return TIER_DISTRUST;
		if (reputation < 30) return TIER_NEUTRAL;
		if (reputation < 100) return TIER_FRIENDLY;
		return TIER_FRIEND;
	}

	// ---------------------------------------------------------------- presentes

	public enum Taste {LOVED, LIKED, NEUTRAL, DISLIKED}

	public static final int GIFTS_PER_VILLAGER_WEEK = 2;
	/** Reputação na vila que os presentes podem dar a um jogador num dia. */
	public static final int GIFT_REP_PER_PLAYER_DAY = 6;
	/** Mexericos (vanilla, MINOR_POSITIVE) por presente: amado 10, gostado 5. */
	public static final int GOSSIP_LOVED = 10, GOSSIP_LIKED = 5;

	public static long week(long day) {
		return Math.floorDiv(day, 7);
	}

	/** Pode dar mais um presente a este aldeão esta semana? */
	public static boolean mayGift(int givenThisWeek) {
		return givenThisWeek < GIFTS_PER_VILLAGER_WEEK;
	}

	/** Reputação na vila que este presente dá (0 se detestado ou se o jogador já ganhou o teto do dia com presentes). */
	public static int giftReputation(Taste taste, int repFromGiftsToday) {
		int want = switch (taste) {
			case LOVED -> 3;
			case LIKED -> 1;
			case NEUTRAL, DISLIKED -> 0;
		};
		return Math.max(0, Math.min(want, GIFT_REP_PER_PLAYER_DAY - repFromGiftsToday));
	}

	public static int giftGossip(Taste taste) {
		return switch (taste) {
			case LOVED -> GOSSIP_LOVED;
			case LIKED -> GOSSIP_LIKED;
			case NEUTRAL, DISLIKED -> 0;
		};
	}

	/** O presente detestado é devolvido (não se perde, e não há castigo). */
	public static boolean keepsGift(Taste taste) {
		return taste != Taste.DISLIKED;
	}

	/** Presente de volta (gratidão): só «Amigo», uma vez por semana por aldeão e uma por dia por jogador. */
	public static boolean mayThank(int tier, long lastWeekThanked, long week, long lastDayThankedPlayer, long day) {
		return tier == TIER_FRIEND && lastWeekThanked != week && lastDayThankedPlayer != day;
	}

	// ---------------------------------------------------------------- guardas

	/** Postos de guarda pelo nível da vila: Aldeia 1, Vila 2, Cidade 4. */
	public static int guardPosts(int villageLevel) {
		return switch (villageLevel) {
			case 0 -> 1;
			case 1 -> 2;
			default -> 4;
		};
	}

	public static final long GUARD_WAGE_CENTS = 300;

	/** Quantos guardas a tesouraria paga hoje (no máximo os {@code wanted}); a tesouraria nunca fica negativa. */
	public static int guardsPaid(long treasury, long wage, int wanted) {
		if (wage <= 0) return Math.max(0, wanted);
		if (treasury <= 0 || wanted <= 0) return 0;
		return (int) Math.min(wanted, treasury / wage);
	}

	/** Um guarda volta ao posto se se afastou mais de 6 blocos e não está a lutar. */
	public static final int GUARD_LEASH = 6;

	// ---------------------------------------------------------------- curas e reparações

	/** O clérigo cura no máximo 3 vezes por dia; cada ferreiro repara golens 3 vezes por dia. */
	public static final int HEALS_PER_DAY = 3, REPAIRS_PER_DAY = 3;
	/** Um golem com menos de 70 % da vida precisa do ferreiro; a reparação dá 25 de vida (como um lingote). */
	public static final float REPAIR_BELOW = 0.7f, REPAIR_AMOUNT = 25f;

	public static boolean mayHeal(int doneToday) {
		return doneToday < HEALS_PER_DAY;
	}

	public static boolean mayRepair(int doneToday) {
		return doneToday < REPAIRS_PER_DAY;
	}

	// ---------------------------------------------------------------- pânico

	public static final int PANIC_RADIUS = 15;

	// ---------------------------------------------------------------- festa

	/** Reembolso da festa: 10 % do que se pagou ao aldeão, até 20 CB por jogador e por festa, do orçamento (100 CB) e da tesouraria. */
	public static final int FESTA_REBATE_PERCENT = 10;
	public static final long FESTA_PLAYER_CAP = 2000, FESTA_BUDGET = 10000;

	/**
	 * Quanto a tesouraria devolve a este jogador por esta troca (cêntimos): 10 % do que pagou, sem passar do teto por jogador, do
	 * que resta do orçamento da festa, nem da tesouraria. Nunca negativo.
	 */
	public static long festaRebate(long paidCents, long playerSoFar, long budgetLeft, long treasury) {
		return festaRebate(paidCents, playerSoFar, budgetLeft, treasury, FESTA_PLAYER_CAP);
	}

	/** Sem o CubCoin o teto por jogador é de pelo menos 1 esmeralda (senão nunca chegaria a uma esmeralda inteira). */
	public static long festaPlayerCap(long unitCents) {
		return Math.max(FESTA_PLAYER_CAP, unitCents);
	}

	public static long festaRebate(long paidCents, long playerSoFar, long budgetLeft, long treasury, long playerCap) {
		if (paidCents <= 0) return 0;
		long want = paidCents * FESTA_REBATE_PERCENT / 100;
		want = Math.min(want, playerCap - playerSoFar);
		want = Math.min(want, budgetLeft);
		want = Math.min(want, treasury);
		return Math.max(0, want);
	}

	/** Sem o CubCoin paga-se em esmeraldas inteiras: o que se pode pagar já, e o resto fica guardado para a próxima troca. */
	public static long payable(long owedCents, long unitCents) {
		if (unitCents <= 1) return Math.max(0, owedCents);
		return Math.max(0, owedCents / unitCents * unitCents);
	}

	// ---------------------------------------------------------------- pedir caminho

	public static final String[] COMPASS = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

	/** Rumo de (dx, dz) em 8 direções (norte = −z), como chave de tradução {@code tvilas.dir.<rumo>}. */
	public static String compass(double dx, double dz) {
		double deg = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = sul, 90 = oeste, 180 = norte, −90 = leste (yaw do jogo)
		int i = (int) Math.floorMod(Math.round(deg / 45.0), 8);
		return COMPASS[i];
	}

	// ---------------------------------------------------------------- boas-vindas

	/** Quem esteve fora 3 dias ou mais é recebido por um aldeão amigo. */
	public static boolean welcome(long lastSeenDay, long day) {
		return lastSeenDay >= 0 && day - lastSeenDay >= 3;
	}
}
