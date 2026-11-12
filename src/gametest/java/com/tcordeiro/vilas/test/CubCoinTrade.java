package com.tcordeiro.vilas.test;

import java.util.UUID;

import com.tcordeiro.cubcoin.Wallet;
import com.tcordeiro.cubcoin.trade.TradeData;
import com.tcordeiro.cubcoin.trade.Trades;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Venda entre jogadores pelo CubCoin, como no jogo (enviar com preço → aceitar). Só é usada com o CubCoin instalado. */
final class CubCoinTrade {
	private CubCoinTrade() {}

	static void give(ServerPlayer p, long cents) {
		Wallet.give(p, cents);
	}

	static long balance(ServerPlayer p) {
		return Wallet.total(p.getInventory());
	}

	/** O preço de uma esmeralda no Balcão de Câmbio (cêntimos). */
	static long emeraldPrice() {
		return com.tcordeiro.cubcoin.exchange.ExchangePrices.base(net.minecraft.world.item.Items.EMERALD);
	}

	/** Valor em cêntimos de um item de troca em JSON ({"id", "count"}): notas/moedas pelo CubCoin, esmeraldas ao preço do Balcão. */
	static long value(com.google.gson.JsonObject o, long emerald) {
		String id = o.get("id").getAsString();
		int n = o.has("count") ? o.get("count").getAsInt() : 1;
		if (id.equals("minecraft:emerald")) return n * emerald;
		if (id.equals("minecraft:emerald_block")) return n * 9 * emerald;
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		return Wallet.valueOf(new ItemStack(item, n));
	}

	/** O vendedor envia {@code stack} ao comprador por {@code cents}; o comprador aceita. Devolve true se correu bem. */
	static boolean sell(ServerPlayer seller, ServerPlayer buyer, ItemStack stack, long cents) {
		if (!Trades.send(seller, stack, buyer, cents).ok()) return false;
		var offers = TradeData.get(seller.level().getServer()).incoming(buyer.getUUID());
		if (offers.isEmpty()) return false;
		UUID id = offers.get(offers.size() - 1).id();
		return Trades.accept(buyer, id) == Trades.Result.OK;
	}
}
