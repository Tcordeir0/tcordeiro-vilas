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

	/** Tirar sem largar itens, sem atualizar a forma dos vizinhos nem os avisar: UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS. */
	@Unique
	private static final int CLEAR_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
			| net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

	/** A outra metade de uma porta/planta alta ou de uma cama (ou null). */
	@Unique
	private static BlockPos tvilas$partner(net.minecraft.world.level.block.state.BlockState s, BlockPos pos) {
		if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF))
			return s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
					== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		if (s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
			return pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(s));
		return null;
	}

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
		// deixava passar duplicados. Os blocos registados (e a outra metade das portas e camas, que o registo também guarda) saem JÁ,
		// de cima para baixo, SEM largar nada e sem avisar os vizinhos (tochas, portas e camas apoiadas não caem como item); a
		// explosão depois só vê ar.
		for (BlockPos pos : new java.util.ArrayList<>(kept)) {
			BlockPos other = tvilas$partner(level.getBlockState(pos), pos);
			if (other != null && Rebuild.recorded(level, other)) kept.add(other.immutable());
		}
		java.util.List<BlockPos> order = new java.util.ArrayList<>(kept);
		order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos pos : order) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
	}
}
