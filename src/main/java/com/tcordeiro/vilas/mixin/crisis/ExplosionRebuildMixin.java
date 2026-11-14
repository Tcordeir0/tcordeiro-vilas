package com.tcordeiro.vilas.mixin.crisis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Rebuild;
import com.tcordeiro.vilas.village.VillageData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;

/**
 * 0.6 crises — Explosões de monstros (creeper, ghast, wither) perto de uma vila: antes de partir os blocos (a lista já vem sem os
 * terrenos, filtrada pelo {@code ServerExplosionMixin}), regista-os para o Construtor os repor ({@link Rebuild#record}). Os blocos
 * registados saem antes da explosão os processar (sem largar item): o material fica para a obra (senão repor duplicava blocos).
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionRebuildMixin {
	@Unique
	private Set<BlockPos> tvilas$kept;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void tvilas$recordBroken(List<BlockPos> targets, CallbackInfo ci) {
		tvilas$kept = null;
		ServerExplosion self = (ServerExplosion) (Object) this;
		Explosion.BlockInteraction bi = self.getBlockInteraction();
		if (bi != Explosion.BlockInteraction.DESTROY && bi != Explosion.BlockInteraction.DESTROY_WITH_DECAY) return;
		ServerLevel level = self.level();
		if (!Rebuild.enabled() || VillageData.get(level).villages.isEmpty()) return;
		Entity cause = self.getIndirectSourceEntity() != null ? self.getIndirectSourceEntity() : self.getDirectSourceEntity();
		if (!Rebuild.causeCounts(cause)) return;
		Set<BlockPos> kept = new HashSet<>();
		for (BlockPos pos : targets) if (Rebuild.record(level, pos, level.getBlockState(pos))) kept.add(pos.immutable());
		if (kept.isEmpty()) return;
		tvilas$kept = kept;
		// revisão de segurança: o vanilla junta os itens de várias posições antes de os largar, por isso filtrar no popResource
		// deixava passar duplicados. Os blocos registados saem JÁ, sem largar nada; a explosão depois só vê ar.
		for (BlockPos pos : kept) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
	}
}
