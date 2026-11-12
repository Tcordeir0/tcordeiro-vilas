package com.tcordeiro.vilas.economy;

/**
 * Uma troca de aldeão ({@code MerchantOffer}) vista pelo Vilas (interface acrescentada pelo mixin {@code MerchantOfferMixin}).
 * Os números são os ORIGINAIS da troca, em esmeraldas — mesmo quando, com o CubCoin, o ecrã a mostra em CubCoins.
 */
public interface MoneyOffer {
	/** Esmeraldas pedidas na 1.ª casa, já com a procura e os descontos (0 se a 1.ª casa não pede esmeraldas). */
	int tvilas$emeraldsA();

	/** Esmeraldas pedidas na 2.ª casa (0 se não pede). */
	int tvilas$emeraldsB();

	/** Esmeraldas que o aldeão dá (0 se dá outra coisa). */
	int tvilas$emeraldsResult();

	/** Esta troca é feita em CubCoin? (CubCoin instalado, a troca usa esmeraldas e não mexe já em dinheiro — o Cambista não muda) */
	boolean tvilas$money();
}
