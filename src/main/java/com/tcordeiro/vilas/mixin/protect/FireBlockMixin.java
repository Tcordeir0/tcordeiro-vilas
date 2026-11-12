package com.tcordeiro.vilas.mixin.protect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrenos e fogo: um fogo fora de um terreno (ou num terreno de outro dono) não queima os blocos vizinhos do terreno
 * ({@code checkBurnOut}) nem se espalha para dentro dele (o {@code setBlockAndUpdate} do ciclo de espalhar, em {@code tick}).
 * Dentro do próprio terreno o fogo comporta-se como no vanilla.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/FireBlock;checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V"))
	private boolean tvilas$noBurnInLots(FireBlock self, Level level, BlockPos target, int chance, RandomSource random, int age,
			@Local(argsOnly = true) BlockPos firePos) {
		return !(level instanceof ServerLevel sl) || VillageManager.mayCross(sl, firePos, target);
	}

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean tvilas$noSpreadIntoLots(ServerLevel level, BlockPos target, BlockState fire, @Local(argsOnly = true) BlockPos firePos) {
		return VillageManager.mayCross(level, firePos, target);
	}
}
