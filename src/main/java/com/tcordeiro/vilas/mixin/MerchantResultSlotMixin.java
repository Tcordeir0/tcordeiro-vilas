package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.economy.CoinTrades;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;

/** 0.3.0 — Cada troca com um aldeão: no fim, o troco em CubCoins vai para o inventário de quem trocou ({@link CoinTrades}). */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void tvilas$begin(Player player, ItemStack carried, CallbackInfo ci) {
		CoinTrades.beginTake();
	}

	@Inject(method = "onTake", at = @At("RETURN"))
	private void tvilas$change(Player player, ItemStack carried, CallbackInfo ci) {
		CoinTrades.finishTake(player);
	}
}
