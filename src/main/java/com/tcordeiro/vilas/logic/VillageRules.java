package com.tcordeiro.vilas.logic;

/**
 * Regras do nível da vila (lógica pura, sem Minecraft — testada com JUnit).
 * <p>
 * 0 = Aldeia, 1 = Vila, 2 = Cidade. Sobe quando a população (aldeões dentro do raio) E a reputação somada de todos os jogadores
 * passam os limiares. O nível nunca desce (os terrenos já vendidos e as missões não podem «desaparecer» se morrerem aldeões).
 */
public final class VillageRules {
	private VillageRules() {}

	public static final int ALDEIA = 0, VILA = 1, CIDADE = 2;
	/** População mínima para Vila e Cidade. */
	public static final int POP_VILA = 8, POP_CIDADE = 16;
	/** Reputação somada mínima para Vila e Cidade. */
	public static final int REP_VILA = 100, REP_CIDADE = 400;
	/** Raio do território da vila (blocos, na horizontal) à volta da Mesa do Prefeito; configurável em config/tvilas.json. */
	public static final int DEFAULT_RADIUS = 400;
	/** Raio mínimo aceite na configuração. */
	public static final int MIN_RADIUS = 64;

	/** Nível que a população e a reputação permitem (sem contar o nível que a vila já tinha). */
	public static int levelFor(int population, int reputationSum) {
		if (population >= POP_CIDADE && reputationSum >= REP_CIDADE) return CIDADE;
		if (population >= POP_VILA && reputationSum >= REP_VILA) return VILA;
		return ALDEIA;
	}

	/** O novo nível: nunca desce. */
	public static int nextLevel(int current, int population, int reputationSum) {
		return Math.max(current, levelFor(population, reputationSum));
	}

	/** Quantos terrenos a Prefeitura pode vender no total. */
	public static int maxLots(int level) {
		return switch (clamp(level)) {
			case ALDEIA -> 4;
			case VILA -> 8;
			default -> 16;
		};
	}

	/** Missões disponíveis: o «escalão» é o nível + 1 (Aldeia → missões 1; Cidade → missões até 3). */
	public static int missionTier(int level) {
		return clamp(level) + 1;
	}

	public static int clamp(int level) {
		return Math.max(ALDEIA, Math.min(CIDADE, level));
	}

	/** Chave de tradução do nome do nível. */
	public static String levelKey(int level) {
		return switch (clamp(level)) {
			case ALDEIA -> "tvilas.level.aldeia";
			case VILA -> "tvilas.level.vila";
			default -> "tvilas.level.cidade";
		};
	}
}
