package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Crises;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.6 crises (revisão de segurança) — Cada fogo novo, de qualquer origem (isqueiro, dispensador, lava, espalhar-se, raio), passa por
 * {@link Crises#onFirePlaced}: fica seguido e, se tiver origem de jogador, marcado (apagá-lo não paga). O {@code FireBlock.onPlace}
 * chama este, por isso cobre o fogo normal e o fogo das almas.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireRebuildMixin {
	@Inject(method = "onPlace", at = @At("TAIL"))
	private void tvilas$firePlaced(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		if (level instanceof ServerLevel sl) Crises.onFirePlaced(sl, pos);
	}
}
