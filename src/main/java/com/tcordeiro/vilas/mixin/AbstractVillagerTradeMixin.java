package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.village.VillageEconomy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;

/** Cada troca feita com um aldeão (ou mercador) dentro do território paga a taxa da vila. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void tvilas$tax(MerchantOffer offer, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (self.getTradingPlayer() instanceof ServerPlayer p) VillageEconomy.onVillagerTrade(p, self.blockPosition(), offer);
	}
}
