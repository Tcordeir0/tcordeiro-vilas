package com.tcordeiro.vilas.compat;

import com.tcordeiro.cubcoin.Wallet;

import net.minecraft.server.level.ServerPlayer;

/**
 * Pagamentos em CubCoins pela API pública do CubCoin ({@code Wallet.give}, {@code Wallet.pay}, {@code Wallet.total}). Esta classe
 * só é carregada quando o CubCoin existe ({@code TVilas.CUBCOIN}).
 */
public final class CubCoinPay {
	private CubCoinPay() {}

	public static void give(ServerPlayer player, long cents) {
		if (cents > 0) Wallet.give(player, cents);
	}

	/** Dinheiro que o jogador traz (moedas e notas no inventário). */
	public static long balance(ServerPlayer player) {
		return Wallet.total(player.getInventory());
	}

	/** Cobra; devolve false (sem tirar nada) se não chegar. */
	public static boolean pay(ServerPlayer player, long cents) {
		return Wallet.pay(player, cents);
	}
}
