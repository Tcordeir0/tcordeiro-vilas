package com.tcordeiro.vilas.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.economy.MoneyOffer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — Trocas de aldeão em CubCoin ({@link CoinTrades}). A troca guardada não muda (esmeraldas); só com a conversão ligada
 * é que o que se mostra (custo e resultado) passa a notas/moedas e o pagamento passa a valer pelo VALOR do dinheiro posto nas
 * casas, com troco ({@code compat/MoneyTrades}, só carregada com o CubCoin).
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements MoneyOffer {
	@Shadow @Final private ItemCost baseCostA;
	@Shadow @Final private Optional<ItemCost> costB;
	@Shadow @Final private ItemStack result;

	@Shadow
	private int getModifiedCostCount(ItemCost cost) {
		throw new AssertionError();
	}

	@Override
	public int tvilas$emeraldsA() {
		return CoinTrades.isEmerald(baseCostA.itemStack()) ? getModifiedCostCount(baseCostA) : 0;
	}

	@Override
	public int tvilas$emeraldsB() {
		return costB.isPresent() && CoinTrades.isEmerald(costB.get().itemStack()) ? costB.get().count() : 0;
	}

	@Override
	public int tvilas$emeraldsResult() {
		return CoinTrades.isEmerald(result) ? result.getCount() : 0;
	}

	@Override
	public boolean tvilas$money() {
		if (!CoinTrades.active()) return false;
		boolean emerald = CoinTrades.isEmerald(baseCostA.itemStack()) || CoinTrades.isEmerald(result)
				|| costB.isPresent() && CoinTrades.isEmerald(costB.get().itemStack());
		if (!emerald) return false;
		// trocas que já mexem em dinheiro (o Cambista: esmeraldas ↔ notas) ficam como estão
		return !CoinTrades.isMoney(baseCostA.itemStack()) && !CoinTrades.isMoney(result)
				&& !(costB.isPresent() && CoinTrades.isMoney(costB.get().itemStack()));
	}

	private MerchantOffer tvilas$self() {
		return (MerchantOffer) (Object) this;
	}

	/**
	 * O preço base (sem procura nem descontos) em CubCoin, para o ecrã de trocas mostrar o riscado na mesma nota. As contas do jogo
	 * que precisam do número de esmeraldas (o desconto do Herói da Aldeia) leem a troca original: {@code VillagerSpecialPricesMixin}.
	 */
	@Inject(method = "getBaseCostA", at = @At("HEAD"), cancellable = true)
	private void tvilas$baseCostA(CallbackInfoReturnable<ItemStack> cir) {
		if (tvilas$money() && tvilas$emeraldsA() > 0) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.displayBaseA(tvilas$self(), baseCostA.count()));
	}

	@Override
	public ItemStack tvilas$rawBaseCostA() {
		return baseCostA.itemStack();
	}

	@Inject(method = "getCostA", at = @At("HEAD"), cancellable = true)
	private void tvilas$costA(CallbackInfoReturnable<ItemStack> cir) {
		if (tvilas$money() && tvilas$emeraldsA() > 0) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.displayA(tvilas$self()));
	}

	@Inject(method = "getCostB", at = @At("HEAD"), cancellable = true)
	private void tvilas$costB(CallbackInfoReturnable<ItemStack> cir) {
		if (tvilas$money() && (tvilas$emeraldsA() > 0 || tvilas$emeraldsB() > 0))
			cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.displayB(tvilas$self()));
	}

	@Inject(method = "getResult", at = @At("HEAD"), cancellable = true)
	private void tvilas$result(CallbackInfoReturnable<ItemStack> cir) {
		if (tvilas$money() && tvilas$emeraldsResult() > 0) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.resultStack(tvilas$self()));
	}

	@Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
	private void tvilas$assemble(CallbackInfoReturnable<ItemStack> cir) {
		if (tvilas$money() && tvilas$emeraldsResult() > 0) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.resultStack(tvilas$self()));
	}

	@Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
	private void tvilas$satisfied(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (tvilas$money()) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.satisfied(tvilas$self(), baseCostA, getModifiedCostCount(baseCostA), costB, buyA, buyB));
	}

	@Inject(method = "take", at = @At("HEAD"), cancellable = true)
	private void tvilas$take(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (tvilas$money()) cir.setReturnValue(com.tcordeiro.vilas.compat.MoneyTrades.take(tvilas$self(), baseCostA, getModifiedCostCount(baseCostA), costB, buyA, buyB));
	}
}
