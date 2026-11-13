package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tcordeiro.vilas.economy.CoinTrades;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — O desconto do Herói da Aldeia conta-se em ESMERALDAS (o custo base guardado), mesmo quando o ecrã mostra o preço em
 * CubCoin ({@code MerchantOfferMixin#getBaseCostA}). Sem isto o desconto seria contado em notas de 20 CB (o dobro).
 */
@Mixin(Villager.class)
public abstract class VillagerSpecialPricesMixin {
	@Redirect(method = "updateSpecialPrices", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;getBaseCostA()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack tvilas$rawBase(MerchantOffer offer) {
		return CoinTrades.of(offer).tvilas$rawBaseCostA();
	}
}
