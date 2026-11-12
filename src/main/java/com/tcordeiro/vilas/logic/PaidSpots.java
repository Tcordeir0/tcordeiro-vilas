package com.tcordeiro.vilas.logic;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posições (BlockPos.asLong) já pagas por missões de decorar/construir numa vila, com o instante em que foram pagas (lógica
 * pura, testada com JUnit).
 * <p>
 * REGRA DE SEGURANÇA (economia): uma posição paga não volta a contar — nem para o mesmo jogador (partir e repor o bloco), nem
 * para outro, nem noutra missão. Para não crescer sem fim, cada posição expira ao fim de {@link #TTL} ticks (30 dias de jogo) e
 * guardam-se no máximo {@link #MAX} por vila (sai a mais antiga).
 */
public final class PaidSpots {
	public static final int MAX = 4096;
	/** 30 dias de jogo. */
	public static final long TTL = 30L * 24000L;

	/** posição → instante (game time) em que foi paga; pela ordem em que foram pagas (a mais antiga primeiro). */
	private final LinkedHashMap<Long, Long> spots = new LinkedHashMap<>();

	/** Esta posição já foi paga (e ainda não expirou)? */
	public boolean isPaid(long pos, long now) {
		Long at = spots.get(pos);
		return at != null && now - at < TTL;
	}

	/** Regista a posição como paga agora (e faz a limpeza). */
	public void markPaid(long pos, long now) {
		spots.remove(pos);
		spots.put(pos, now);
		prune(now);
	}

	/** Tira as expiradas e, se passar de {@link #MAX}, as mais antigas. */
	public void prune(long now) {
		Iterator<Map.Entry<Long, Long>> it = spots.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Long> e = it.next();
			if (spots.size() > MAX || now - e.getValue() >= TTL) it.remove();
			else break; // por ordem: as seguintes são mais recentes
		}
	}

	public int size() {
		return spots.size();
	}

	/** Para gravar: pares [posição, instante, posição, instante, ...] pela ordem em que foram pagas. */
	public long[] toArray() {
		long[] out = new long[spots.size() * 2];
		int i = 0;
		for (Map.Entry<Long, Long> e : spots.entrySet()) {
			out[i++] = e.getKey();
			out[i++] = e.getValue();
		}
		return out;
	}

	public static PaidSpots fromArray(long[] pairs) {
		PaidSpots p = new PaidSpots();
		for (int i = 0; i + 1 < pairs.length; i += 2) p.spots.put(pairs[i], pairs[i + 1]);
		return p;
	}
}
