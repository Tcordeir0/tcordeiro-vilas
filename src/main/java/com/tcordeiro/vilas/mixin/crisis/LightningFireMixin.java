package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Crises;

import net.minecraft.world.entity.LightningBolt;

/** 0.6 crises (revisão de segurança) — O fogo de um raio é natural, a não ser que o raio tenha um jogador como causa (tridente com Canalização). */
@Mixin(LightningBolt.class)
public abstract class LightningFireMixin {
	@Inject(method = "spawnFire", at = @At("HEAD"))
	private void tvilas$causeBegin(int additionalSources, CallbackInfo ci) {
		Crises.begin(((LightningBolt) (Object) this).getCause() == null ? Crises.Cause.NATURAL : Crises.Cause.PLAYER, null);
	}

	@Inject(method = "spawnFire", at = @At("RETURN"))
	private void tvilas$causeEnd(int additionalSources, CallbackInfo ci) {
		Crises.end();
	}
}
