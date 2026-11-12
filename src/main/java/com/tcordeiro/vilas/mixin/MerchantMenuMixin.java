package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.economy.CoinTrades;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — Escolher uma troca em CubCoin no ecrã põe nas casas as notas/moedas do inventário que a pagam (em vez de procurar
 * esmeraldas). Só move itens do inventário para as casas.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {
	@Shadow @Final private MerchantContainer tradeContainer;
	private MerchantOffer tvilas$offer;

	@Inject(method = "tryMoveItems", at = @At("HEAD"))
	private void tvilas$remember(int index, CallbackInfo ci) {
		var offers = ((MerchantMenu) (Object) this).getOffers();
		tvilas$offer = index >= 0 && index < offers.size() ? offers.get(index) : null;
	}

	@Inject(method = "tryMoveItems", at = @At("RETURN"))
	private void tvilas$forget(int index, CallbackInfo ci) {
		tvilas$offer = null;
	}

	@Inject(method = "moveFromInventoryToPaymentSlot", at = @At("HEAD"), cancellable = true)
	private void tvilas$money(int paymentSlot, ItemCost cost, CallbackInfo ci) {
		MerchantOffer o = tvilas$offer;
		if (o == null || !CoinTrades.of(o).tvilas$money() || !cost.itemStack().is(net.minecraft.world.item.Items.EMERALD)) return;
		ci.cancel();
		var m = CoinTrades.of(o);
		// a 1.ª casa trata das duas quando ambas são de dinheiro; a 2.ª só quando é a única de dinheiro
		if (paymentSlot == 0 || m.tvilas$emeraldsA() == 0)
			com.tcordeiro.vilas.compat.MoneyTrades.fill(o, tradeContainer, ((AbstractContainerMenu) (Object) this).slots, paymentSlot == 1);
	}
}
