package com.tcordeiro.vilas.mixin.protect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;

/**
 * Terrenos e lava: a lava acende fogo nos blocos à volta ({@code randomTick}) — não dentro de um terreno se a lava estiver fora
 * dele (ou num terreno de outro dono). O escorrer da lava para os lados passa pelo {@code spreadTo} da classe-mãe
 * ({@link FlowingFluidMixin}); para baixo fica sempre na mesma coluna, logo no mesmo terreno (a lava que cai em água e vira
 * pedra não atravessa fronteiras).
 */
@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {
	@WrapWithCondition(method = "randomTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean tvilas$noFireInLots(ServerLevel level, BlockPos firePos, BlockState fire, @Local(argsOnly = true) BlockPos lavaPos) {
		return VillageManager.mayCross(level, lavaPos, firePos);
	}
}
