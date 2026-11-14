package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Crises;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerExplosion;

/** 0.6 crises (revisão de segurança) — O fogo de uma explosão só é natural se ela vier de um monstro (bola de fogo do ghast); TNT, camas e jogadores não. */
@Mixin(ServerExplosion.class)
public abstract class ExplosionFireMixin {
	@Inject(method = "createFire", at = @At("HEAD"))
	private void tvilas$causeBegin(List<BlockPos> targetBlocks, CallbackInfo ci) {
		Crises.begin(((ServerExplosion) (Object) this).getIndirectSourceEntity() instanceof Mob ? Crises.Cause.NATURAL : Crises.Cause.PLAYER, null);
	}

	@Inject(method = "createFire", at = @At("RETURN"))
	private void tvilas$causeEnd(List<BlockPos> targetBlocks, CallbackInfo ci) {
		Crises.end();
	}
}
