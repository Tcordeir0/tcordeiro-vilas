package com.tcordeiro.vilas.life.crisis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import com.tcordeiro.vilas.village.Village;

/**
 * 0.6 crises — O ÚNICO ponto onde a morte, o funeral, a reconstrução e as crises avisam o resto do mod. No merge, o agente principal
 * liga isto à crónica ({@code life.memory.Chronicle.add}) e às recordações ({@code Memories.of}). Aqui só há o comportamento de
 * reserva (nada; o epitáfio usa a profissão e os dias de vida).
 */
public final class CrisisHooks {
	private CrisisHooks() {}

	/** Um aldeão da vila morreu (já registado em {@code CrisisData.deaths}). */
	public static void onDeath(ServerLevel level, Village v, CrisisData.Death death) {}

	/** Começou o funeral (grave = a campa, ou null se não coube). */
	public static void onFuneral(ServerLevel level, Village v, CrisisData.Death death, BlockPos grave) {}

	/** A última linha do epitáfio a partir da crónica (null = usa a linha de reserva pela causa da morte). */
	public static Component epitaphLine(CrisisData.Death death) {
		return null;
	}

	/** Uma crise abriu (type = fire/hunger/night) ou a reconstrução acabou (type = rebuilt). */
	public static void onCrisis(ServerLevel level, Village v, String type) {}
}
