package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Crises;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.6 crises — O fogo: cada bloco que ardeu ({@code checkBurnOut} trocou-o por ar ou por fogo) vai para a reconstrução e acende a
 * crise do incêndio ({@link Crises#onBurn}); cada fogo novo perto de uma vila fica seguido ({@link Crises#onFirePlaced}) para saber
 * quando o incêndio acabou e quem o apagou.
 */
@Mixin(FireBlock.class)
public abstract class FireRebuildMixin {
	@Unique
	private static BlockState tvilas$before;

	@Inject(method = "checkBurnOut", at = @At("HEAD"))
	private void tvilas$beforeBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		tvilas$before = level instanceof ServerLevel ? level.getBlockState(pos) : null;
	}

	@Inject(method = "checkBurnOut", at = @At("RETURN"))
	private void tvilas$afterBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		BlockState before = tvilas$before;
		tvilas$before = null;
		if (before == null || before.isAir() || before.is(BlockTags.FIRE) || !(level instanceof ServerLevel sl)) return;
		if (level.getBlockState(pos) == before) return; // não ardeu
		Crises.onBurn(sl, pos, before);
	}
}
