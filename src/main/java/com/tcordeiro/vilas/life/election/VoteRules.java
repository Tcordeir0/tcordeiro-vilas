package com.tcordeiro.vilas.life.election;

import java.util.UUID;

/**
 * 0.6 eleições — As regras PURAS (testadas com JUnit) das eleições revistas e dos bónus do voto. Nada aqui toca no mundo.
 * <p>
 * REGRAS DE ECONOMIA E SEGURANÇA (com mutações em {@code tools/mutation_test.py}, filtro «0.6 eleições»):
 * <ul>
 * <li>a votação dura {@link #VOTE_DAYS} dias de jogo; conta-se no fim do último;</li>
 * <li>empate: o mais querido pela vila; depois a proposta em vigor; depois um sorteio com semente (sempre o mesmo resultado);</li>
 * <li>votar dá {@link #VOTE_REP} de reputação UMA vez por eleição;</li>
 * <li>NENHUM bónus cria dinheiro: o desconto do apoiador e o extra VIP são reembolsos da TESOURARIA com tetos (por jogador e por
 * mandato) e nunca maiores que a tesouraria; o presente da posse e o broche são ITENS; o imposto do terreno só desce;</li>
 * <li>só quem votou no vencedor, e só durante o mandato dele.</li>
 * </ul>
 */
public final class VoteRules {
	private VoteRules() {}

	// ---------------------------------------------------------------- a votação

	/** A votação dura 3 dias de jogo (o dia em que abre e os 2 seguintes). */
	public static final int VOTE_DAYS = 3;

	/** A votação que abriu no dia {@code openDay} ainda está aberta hoje? ({@code openDay} −1 = nenhuma) */
	public static boolean open(long openDay, long today) {
		return openDay >= 0 && today >= openDay && today < openDay + VOTE_DAYS;
	}

	/** Já é altura de contar? (no fim do 3.º dia, isto é, no começo do dia a seguir) */
	public static boolean countNow(long openDay, long today) {
		return openDay >= 0 && today >= openDay + VOTE_DAYS;
	}

	/** Ticks que faltam para fechar a votação (0 se já fechou). */
	public static long ticksLeft(long openDay, long gameTime, long dayTicks) {
		if (openDay < 0) return 0;
		return Math.max(0, (openDay + VOTE_DAYS) * dayTicks - gameTime);
	}

	/**
	 * Quem ganhou: o mais votado. Empate: o mais querido pela vila ({@code likes}); se persistir, o que propõe a política em vigor
	 * ({@code incumbent}, −1 = nenhuma); se não houver, sorteio com a semente {@code seed}. Sem votos → −1 (nada muda).
	 */
	public static int winner(int[] votes, long[] likes, int incumbent, long seed) {
		int top = 0;
		for (int v : votes) top = Math.max(top, v);
		if (top == 0) return -1;
		long best = Long.MIN_VALUE;
		for (int i = 0; i < votes.length; i++) if (votes[i] == top) best = Math.max(best, likes[i]);
		int n = 0;
		int[] tied = new int[votes.length];
		for (int i = 0; i < votes.length; i++) if (votes[i] == top && likes[i] == best) tied[n++] = i;
		if (n == 1) return tied[0];
		for (int k = 0; k < n; k++) if (tied[k] == incumbent) return incumbent;
		return tied[(int) Math.floorMod(new java.util.Random(seed).nextLong(), (long) n)];
	}

	/** Quanto a vila gosta de um candidato: as opiniões dos aldeões sobre ele + a reputação (positiva) de quem votou nele. */
	public static long likes(int opinionsSum, long votersReputation) {
		return (long) opinionsSum + Math.max(0, votersReputation);
	}

	// ---------------------------------------------------------------- reputação

	/** Reputação por votar (uma vez por eleição). */
	public static final int VOTE_REP = 5;

	/** REGRA: a reputação do voto; 0 se já a recebeu nesta eleição. */
	public static int voteReputation(boolean alreadyGiven) {
		return alreadyGiven ? 0 : VOTE_REP;
	}

	// ---------------------------------------------------------------- mandato

	/** REGRA: este jogador tem os bónus de apoiador? (votou no vencedor e o mandato dele ainda está em vigor) */
	public static boolean supporter(boolean votedForWinner, int mandateTerm, int currentTerm) {
		return votedForWinner && mandateTerm > 0 && mandateTerm == currentTerm;
	}

	/** REGRA: a promessa {@code promise} vale para este jogador? (apoiador e a proposta vencedora é essa) */
	public static boolean promised(boolean supporter, String winningPolicy, String promise) {
		return supporter && promise.equals(winningPolicy);
	}

	// ---------------------------------------------------------------- desconto do apoiador (reembolso da tesouraria)

	/** −5 % nas trocas com os aldeões da vila, com teto por jogador e por mandato (os mesmos da festa). */
	public static final long SUPPORTER_PERCENT = 5, SUPPORTER_PLAYER_CAP = 2000, SUPPORTER_BUDGET = 10000;

	/** O teto por jogador; sem o CubCoin pelo menos uma esmeralda (senão nunca chegaria a uma inteira). */
	public static long supporterPlayerCap(long unitCents) {
		return Math.max(SUPPORTER_PLAYER_CAP, unitCents);
	}

	/**
	 * Reembolso (cêntimos) de uma troca: {@code percent} % do que pagou, sem passar do teto do jogador, do orçamento que resta do
	 * mandato, nem da tesouraria. Nunca negativo; 0 para quem não é apoiador.
	 */
	public static long rebate(boolean eligible, long paidCents, long percent, long playerSoFar, long playerCap, long budgetLeft, long treasury) {
		if (!eligible || paidCents <= 0 || percent <= 0) return 0;
		long want = paidCents * percent / 100;
		want = Math.min(want, playerCap - playerSoFar);
		want = Math.min(want, budgetLeft);
		want = Math.min(want, treasury);
		return Math.max(0, want);
	}

	// ---------------------------------------------------------------- promessas

	/** «Imposto menor»: −20 % no imposto do terreno de quem votou na proposta. */
	public static final long LOT_TAX_CUT_PERCENT = 20;

	/** REGRA: o imposto do terreno (cêntimos) com a promessa; só desce, nunca fica negativo. */
	public static long lotTax(long taxCents, boolean promised) {
		if (taxCents <= 0) return 0;
		if (!promised) return taxCents;
		return Math.max(0, taxCents - taxCents * LOT_TAX_CUT_PERCENT / 100);
	}

	/** «Mais festas»: o convite VIP dá +5 % de reembolso na festa, dentro dos tetos da festa. */
	public static final long VIP_EXTRA_PERCENT = 5;

	/** «Mercado maior»: no máximo 2 bancas próprias por mercado. */
	public static final int OWN_STALLS = 2;

	/** REGRA: a venda (entre jogadores) na banca própria não paga a taxa das trocas; as outras pagam a de sempre. */
	public static long ownStallSaleTax(long taxCents, boolean ownStall) {
		return ownStall ? 0 : Math.max(0, taxCents);
	}

	// ---------------------------------------------------------------- pedidos «!» exclusivos

	/** No máximo 3 pedidos exclusivos por vila e por lote; um por apoiador. */
	public static final int RESERVED_PER_VILLAGE = 3;

	/** REGRA: este apoiador pode ter mais um pedido exclusivo neste lote? */
	public static boolean mayReserve(boolean supporter, boolean hasOneThisBatch, int reservedInVillage) {
		return supporter && !hasOneThisBatch && reservedInVillage < RESERVED_PER_VILLAGE;
	}

	/** REGRA: um pedido exclusivo só se entrega (e só se abre) pelo dono; os pedidos normais são de todos ({@code owner} null). */
	public static boolean mayTakeRequest(UUID owner, UUID player) {
		return owner == null || owner.equals(player);
	}
}
