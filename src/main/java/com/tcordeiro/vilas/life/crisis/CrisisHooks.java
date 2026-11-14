package com.tcordeiro.vilas.life.crisis;

import com.tcordeiro.vilas.life.memory.Chronicle;
import com.tcordeiro.vilas.life.memory.Lore;
import com.tcordeiro.vilas.life.memory.Memories;
import com.tcordeiro.vilas.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * 0.6 crises — O ÚNICO ponto onde a morte, o funeral, a reconstrução e as crises avisam o resto do mod: tudo vai para a crónica da
 * vila ({@link Chronicle}) e o epitáfio usa a recordação mais forte do morto ({@link Memories}).
 */
public final class CrisisHooks {
	private CrisisHooks() {}

	/** A profissão/nome do morto como argumento da crónica («#chave» = tradução). */
	static String who(CrisisData.Death d) {
		return d.name.isEmpty() ? d.profession : d.name;
	}

	/** Um aldeão da vila morreu (já registado em {@code CrisisData.deaths}). */
	public static void onDeath(ServerLevel level, Village v, CrisisData.Death death) {
		Chronicle.add(level, v, "death", who(death), "#tvilas.funeral.cause." + (death.cause.isEmpty() ? "rest" : death.cause));
	}

	/** Começou o funeral (grave = a campa, ou null se não coube). */
	public static void onFuneral(ServerLevel level, Village v, CrisisData.Death death, BlockPos grave) {
		Chronicle.add(level, v, "funeral", who(death));
	}

	/** A última linha do epitáfio: a recordação mais forte do morto («Salvo por Talys», «Amigo de Talys»); null = a de reserva. */
	public static Component epitaphLine(Village v, CrisisData.Death death) {
		Lore.Memory best = null;
		for (Lore.Memory m : Memories.of(v, death.villager))
			if (!m.kind().equals(Memories.HIT) && !m.name().isEmpty() && (best == null || m.weight() > best.weight())) best = m;
		if (best == null) return null;
		return Component.translatable(best.kind().equals(Memories.SAVED) ? "tvilas.epitaph.saved" : "tvilas.epitaph.friend", best.name());
	}

	/** Uma crise abriu (type = fire/hunger/night) ou a reconstrução acabou (type = rebuilt). */
	public static void onCrisis(ServerLevel level, Village v, String type) {
		Chronicle.add(level, v, "crisis." + type);
	}
}
