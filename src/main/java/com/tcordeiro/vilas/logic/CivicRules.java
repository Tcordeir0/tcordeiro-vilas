package com.tcordeiro.vilas.logic;

import java.util.Collection;
import java.util.List;

/**
 * 0.3.0 — Vilas antigas (lógica pura, testada com JUnit): quando a Mesa do Prefeito chega a uma vila sem Prefeitura, Mercado,
 * Taverna ou Praça, o Prefeito manda construir o que falta, UM de cada vez, pago pela tesouraria, num sítio livre perto do
 * centro (nunca sobre construções nem terrenos — ver {@code build/CivicWork}).
 */
public final class CivicRules {
	private CivicRules() {}

	/** Pela ordem das obras. */
	public static final List<String> ORDER = List.of("town_hall", "market", "tavern", "plaza");
	/** Preços (cêntimos): Prefeitura 120 CB, Mercado 80 CB, Taverna 100 CB, Praça 60 CB. */
	public static final long TOWN_HALL_CENTS = 12000, MARKET_CENTS = 8000, TAVERN_CENTS = 10000, PLAZA_CENTS = 6000;
	/** Uma vila sem estrutura de vila do jogo conta como «antiga» com pelo menos estes aldeões (não se ergue nada no meio do mato). */
	public static final int MIN_POPULATION = 5;
	/** Desnível máximo do chão debaixo de um edifício (blocos). */
	public static final int MAX_SLOPE = 2;

	public static long cost(String kind) {
		return switch (kind) {
			case "town_hall" -> TOWN_HALL_CENTS;
			case "market" -> MARKET_CENTS;
			case "tavern" -> TAVERN_CENTS;
			case "plaza" -> PLAZA_CENTS;
			default -> Long.MAX_VALUE;
		};
	}

	/** É uma vila de verdade (com estrutura de vila do jogo, ou com moradores bastantes)? */
	public static boolean realVillage(boolean villageStructure, int population) {
		return villageStructure || population >= MIN_POPULATION;
	}

	/**
	 * O que o Prefeito encomenda hoje (ou null): o primeiro edifício que falta, se não houver já um em obras e a tesouraria o
	 * pagar. Um de cada vez.
	 */
	public static String next(Collection<String> built, boolean oneBuilding, long treasury, boolean realVillage) {
		if (oneBuilding || !realVillage) return null;
		for (String k : ORDER) {
			if (built.contains(k)) continue;
			return treasury >= cost(k) ? k : null;
		}
		return null;
	}

	/** O chão serve (desnível pequeno)? */
	public static boolean flatEnough(int minY, int maxY) {
		return maxY - minY <= MAX_SLOPE;
	}
}
