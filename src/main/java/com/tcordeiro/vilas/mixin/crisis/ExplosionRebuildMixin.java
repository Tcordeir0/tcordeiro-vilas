package com.tcordeiro.vilas.mixin.crisis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.tcordeiro.vilas.life.crisis.Rebuild;
import com.tcordeiro.vilas.village.VillageData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;

/**
 * 0.6 crises — Explosões de monstros (creeper, ghast, wither) perto de uma vila: antes de partir os blocos (a lista já vem sem os
 * terrenos, filtrada pelo {@code ServerExplosionMixin}), regista-os para o Construtor os repor ({@link Rebuild#record}). Os blocos
 * registados não largam item: o material fica para a obra (senão repor duplicava blocos).
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
		if (!kept.isEmpty()) tvilas$kept = kept;
	}

	@WrapWithCondition(method = "interactWithBlocks", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
	private boolean tvilas$keepForRebuild(Level level, BlockPos pos, ItemStack stack) {
		return tvilas$kept == null || !tvilas$kept.contains(pos);
	}
}
