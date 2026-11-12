package com.tcordeiro.vilas.mixin.compat;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tcordeiro.cubcoin.trade.Offer;
import com.tcordeiro.cubcoin.trade.TradeData;
import com.tcordeiro.cubcoin.trade.Trades;
import com.tcordeiro.vilas.village.VillageEconomy;

import net.minecraft.server.level.ServerPlayer;

/**
 * Vendas entre jogadores pelo CubCoin ({@code Trades.accept}): se a compra for aceite dentro do território de uma vila, o vendedor
 * paga a taxa da vila. Só é aplicado com o CubCoin instalado ({@link com.tcordeiro.vilas.mixin.VilasMixinPlugin}).
 */
@Mixin(value = Trades.class, remap = false)
public abstract class CubCoinTradesMixin {
	private static final ThreadLocal<Offer> TVILAS_OFFER = new ThreadLocal<>();

	@Inject(method = "accept", at = @At("HEAD"))
	private static void tvilas$remember(ServerPlayer p, UUID offerId, CallbackInfoReturnable<Trades.Result> cir) {
		TVILAS_OFFER.set(TradeData.get(p.level().getServer()).find(offerId));
	}

	@Inject(method = "accept", at = @At("RETURN"))
	private static void tvilas$tax(ServerPlayer p, UUID offerId, CallbackInfoReturnable<Trades.Result> cir) {
		Offer o = TVILAS_OFFER.get();
		TVILAS_OFFER.remove();
		if (o != null && cir.getReturnValue() == Trades.Result.OK && o.priceCents() > 0)
			VillageEconomy.onPlayerSale(p.level(), p.blockPosition(), o.from(), o.priceCents());
	}
}
