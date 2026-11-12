package com.tcordeiro.vilas.economy;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — Com o CubCoin instalado, TODOS os aldeões (os do jogo, os deste mod, o vendedor ambulante e o Mercador da Vila)
 * compram e vendem em CubCoin em vez de esmeraldas, ao preço do Balcão de Câmbio (1 esmeralda = {@code emerald_value_cents}).
 * <p>
 * Como: as trocas continuam guardadas em esmeraldas (sem o CubCoin, ou com {@code cubcoin_trades: false}, voltam logo a
 * esmeraldas, sem converter nada no mundo). O mixin {@code MerchantOfferMixin} mostra-as em notas/moedas e cobra o valor com
 * troco ({@code compat/MoneyTrades}); o preço acompanha a procura e os descontos do aldeão (contados em esmeraldas, como no jogo).
 * Esta classe é a porta de entrada sem o CubCoin no classpath.
 */
public final class CoinTrades {
	private CoinTrades() {}

	/** A conversão está ligada? */
	public static boolean active() {
		return TVilas.CUBCOIN && VilasConfig.get().cubcoin_trades;
	}

	public static long emeraldCents() {
		return VilasConfig.get().emerald_value_cents;
	}

	public static boolean isEmerald(ItemStack s) {
		return s.is(Items.EMERALD);
	}

	/** É uma nota ou moeda do CubCoin? (falso sem o CubCoin) */
	public static boolean isMoney(ItemStack s) {
		return TVilas.CUBCOIN && com.tcordeiro.vilas.compat.MoneyTrades.isMoney(s);
	}

	public static MoneyOffer of(MerchantOffer offer) {
		return (MoneyOffer) (Object) offer;
	}

	/** Esmeraldas que esta troca movimenta (para a taxa da vila), contadas na troca original. */
	public static int emeraldsTraded(MerchantOffer offer) {
		MoneyOffer m = of(offer);
		return m.tvilas$emeraldsA() + m.tvilas$emeraldsB() + m.tvilas$emeraldsResult();
	}

	// ------------------------------------------------------------------ troco (dado no fim de cada troca, a quem trocou)

	private static final ThreadLocal<long[]> OWED = ThreadLocal.withInitial(() -> new long[1]);

	/** Começa uma troca (o ecrã tira o resultado). */
	public static void beginTake() {
		OWED.get()[0] = 0;
	}

	/** Troco (ou o resto do que o aldeão paga) a dar no fim desta troca. */
	public static void owe(long cents) {
		if (cents > 0) OWED.get()[0] += cents;
	}

	/** Fim da troca: o troco vai para o inventário do jogador (só no servidor; no cliente é só previsão). */
	public static void finishTake(net.minecraft.world.entity.player.Player p) {
		long c = OWED.get()[0];
		OWED.get()[0] = 0;
		if (c > 0 && TVilas.CUBCOIN && p instanceof net.minecraft.server.level.ServerPlayer) com.tcordeiro.vilas.compat.MoneyTrades.give(p, c);
	}
}
