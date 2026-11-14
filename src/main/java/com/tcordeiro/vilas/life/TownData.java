package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.village.Mission;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * 0.5.0 — O que a vila guarda das novidades da 0.5 (campo {@code town} da vida): o correio por entregar, o mercado semanal, as
 * eleições e a política em vigor, os ajudantes contratados (com a bolsa de cada um) e a missão temporária «decorar a festa». Tudo
 * com tetos de tamanho (um dado estragado não cresce sem fim) e os itens guardados AQUI, nunca no aldeão (não se perdem se ele
 * morrer nem se duplicam).
 */
public final class TownData {
	static final Codec<List<ItemStack>> ITEMS = ItemStack.CODEC.listOf();

	/** Uma carta (com itens) de um jogador para outro: a caixa de correio do terreno do destinatário. */
	public static final class Parcel {
		public static final Codec<Parcel> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("id").forGetter(p -> p.id),
				UUIDUtil.STRING_CODEC.fieldOf("from").forGetter(p -> p.from), Codec.string(0, 32).optionalFieldOf("from_name", "").forGetter(p -> p.fromName),
				UUIDUtil.STRING_CODEC.fieldOf("to").forGetter(p -> p.to), Codec.string(0, 32).optionalFieldOf("to_name", "").forGetter(p -> p.toName),
				Codec.LONG.fieldOf("box").forGetter(p -> p.box),
				ITEMS.optionalFieldOf("items", List.of()).forGetter(p -> p.items),
				Codec.INT.optionalFieldOf("tries", 0).forGetter(p -> p.tries),
				Codec.BOOL.optionalFieldOf("returned", false).forGetter(p -> p.returned),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(p -> p.day),
				Codec.BOOL.optionalFieldOf("npc", false).forGetter(p -> p.npc)) // 0.6.0: carta de um aldeão
				.apply(i, (id, f, fn, t, tn, b, it, tr, re, d, npc) -> {
					Parcel p = new Parcel(id, f, fn, t, tn, b, d);
					for (ItemStack s : it) if (p.items.size() < com.tcordeiro.vilas.logic.TownRules.MAIL_SLOTS && !s.isEmpty()) p.items.add(s);
					p.tries = tr;
					p.returned = re;
					p.npc = npc;
					return p;
				}));

		public final long id, box, day;
		public final UUID from, to;
		public final String fromName, toName;
		public final List<ItemStack> items = new ArrayList<>();
		public int tries;
		public boolean returned;
		/** 0.6.0: carta de um aldeão (o «from» é o aldeão): se não se entregar, perde-se — os itens nunca foram de um jogador. */
		public boolean npc;

		public Parcel(long id, UUID from, String fromName, UUID to, String toName, long box, long day) {
			this.id = id;
			this.from = from;
			this.fromName = fromName;
			this.to = to;
			this.toName = toName;
			this.box = box;
			this.day = day;
		}
	}

	/** O mercado semanal: dia (−1 = nenhum a decorrer), o orçamento gasto, os reembolsos por jogador, o vendedor de fora. */
	public static final class Market {
		public static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("day", -1L).forGetter(m -> m.day),
				Codec.LONG.optionalFieldOf("spent", 0L).forGetter(m -> m.spent),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("rebates", Map.of()).forGetter(m -> m.rebates),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owed", Map.of()).forGetter(m -> m.owed),
				UUIDUtil.STRING_CODEC.optionalFieldOf("trader").forGetter(m -> Optional.ofNullable(m.trader)),
				Codec.LONG.optionalFieldOf("last", -1L).forGetter(m -> m.last))
				.apply(i, (d, s, r, o, t, l) -> {
					Market m = new Market();
					m.day = d;
					m.spent = Math.max(0, s);
					LifeData.capPut(m.rebates, r, LifeData.MAX_MAP);
					LifeData.capPut(m.owed, o, LifeData.MAX_MAP);
					m.trader = t.orElse(null);
					m.last = l;
					return m;
				}));

		public long day = -1, spent, last = -1;
		public final Map<UUID, Long> rebates = new HashMap<>(), owed = new HashMap<>();
		public UUID trader;

		public void reset() {
			spent = 0;
			rebates.clear();
			owed.clear();
		}
	}

	/** Um candidato: o aldeão, o nome com que aparece no quadro e a proposta. */
	public record Candidate(UUID villager, String name, String policy) {
		public static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.STRING_CODEC.fieldOf("villager").forGetter(Candidate::villager), Codec.string(0, 48).fieldOf("name").forGetter(Candidate::name),
				Codec.string(1, 16).fieldOf("policy").forGetter(Candidate::policy)).apply(i, Candidate::new));
	}

	/** As eleições: o dia de votar (−1 = nenhuma aberta), os candidatos, os votos (jogador → candidato), a política em vigor. */
	public static final class Election {
		public static final Codec<Election> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("day", -1L).forGetter(e -> e.day),
				Candidate.CODEC.listOf().optionalFieldOf("candidates", List.of()).forGetter(e -> e.candidates),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("votes", Map.of()).forGetter(e -> e.votes),
				Codec.string(0, 16).optionalFieldOf("policy", "").forGetter(e -> e.policy),
				Codec.string(0, 48).optionalFieldOf("mayor_name", "").forGetter(e -> e.mayorName),
				Codec.LONG.optionalFieldOf("last", -1L).forGetter(e -> e.last),
				Codec.INT.optionalFieldOf("held", 0).forGetter(e -> e.held),
				Codec.BOOL.optionalFieldOf("festa_done", false).forGetter(e -> e.festaDone),
				com.tcordeiro.vilas.life.election.Mandate.CODEC.optionalFieldOf("mandate").forGetter(e -> Optional.of(e.mandate))) // 0.6 eleições
				.apply(i, (d, c, vo, p, mn, la, he, fd, md) -> {
					Election e = new Election();
					e.day = d;
					for (Candidate x : c) if (e.candidates.size() < 3) e.candidates.add(x);
					LifeData.capPut(e.votes, vo, LifeData.MAX_MAP);
					e.policy = com.tcordeiro.vilas.logic.TownRules.POLICIES.contains(p) ? p : "";
					e.mayorName = mn;
					e.last = la;
					e.held = he;
					e.festaDone = fd;
					e.mandate = md.orElseGet(com.tcordeiro.vilas.life.election.Mandate::new); // 0.6 eleições
					return e;
				}));

		public long day = -1, last = -1;
		public final List<Candidate> candidates = new ArrayList<>();
		public final Map<UUID, Integer> votes = new HashMap<>();
		public String policy = "", mayorName = "";
		public int held;
		/** A política «mais festas» já deu a sua festa extra neste mandato? */
		public boolean festaDone;
		/** 0.6 eleições: o mandato em vigor e os bónus do voto. */
		public com.tcordeiro.vilas.life.election.Mandate mandate = new com.tcordeiro.vilas.life.election.Mandate();

		/** A votação está aberta hoje? (0.6 eleições: dura {@link com.tcordeiro.vilas.life.election.VoteRules#VOTE_DAYS} dias) */
		public boolean open(long today) {
			return com.tcordeiro.vilas.life.election.VoteRules.open(day, today) && !candidates.isEmpty();
		}
	}

	/** Um ajudante contratado por um dia: quem, até quando, e a bolsa (os itens são de quem o contratou). */
	public static final class Helper {
		public static final Codec<Helper> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(h -> h.player), Codec.string(0, 32).optionalFieldOf("name", "").forGetter(h -> h.playerName),
				UUIDUtil.STRING_CODEC.fieldOf("villager").forGetter(h -> h.villager),
				Codec.LONG.fieldOf("until").forGetter(h -> h.until),
				ITEMS.optionalFieldOf("bag", List.of()).forGetter(h -> h.bag),
				Codec.BOOL.optionalFieldOf("ended", false).forGetter(h -> h.ended),
				Codec.INT.optionalFieldOf("harvested", 0).forGetter(h -> h.harvested))
				.apply(i, (p, n, v, u, b, e, hv) -> {
					Helper h = new Helper(p, n, v, u);
					for (ItemStack s : b) if (h.bag.size() < com.tcordeiro.vilas.logic.TownRules.HELPER_SLOTS && !s.isEmpty()) h.bag.add(s);
					h.ended = e;
					h.harvested = hv;
					return h;
				}));

		public final UUID player, villager;
		public final String playerName;
		public long until;
		public final List<ItemStack> bag = new ArrayList<>();
		/** O contrato acabou mas a bolsa ainda não foi devolvida (o jogador estava desligado). */
		public boolean ended;
		public int harvested;
		/** Não guardados: quem tem a bolsa aberta (não se colhe entretanto) e o último sítio permitido onde esteve. */
		public transient UUID openBy;
		public transient net.minecraft.core.BlockPos safe;
		/** Não guardados (plano B): plantas a que não conseguiu chegar, desde quando está longe do patrão, próximo aviso. */
		public final transient java.util.Set<Long> skip = new java.util.HashSet<>();
		public transient long farSince = -1, nextWarn, tryPos = Long.MIN_VALUE, trySince;

		public Helper(UUID player, String playerName, UUID villager, long until) {
			this.player = player;
			this.playerName = playerName;
			this.villager = villager;
			this.until = until;
		}
	}

	public static final Codec<TownData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Parcel.CODEC.listOf().optionalFieldOf("mail", List.of()).forGetter(t -> t.mail),
			Codec.LONG.optionalFieldOf("counter", 0L).forGetter(t -> t.counter),
			Market.CODEC.optionalFieldOf("market").forGetter(t -> Optional.of(t.market)),
			Election.CODEC.optionalFieldOf("election").forGetter(t -> Optional.of(t.election)),
			Helper.CODEC.listOf().optionalFieldOf("helpers", List.of()).forGetter(t -> t.helpers),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("hired", Map.of()).forGetter(t -> t.hired),
			Mission.CODEC.optionalFieldOf("decor").forGetter(t -> Optional.ofNullable(t.decor)),
			Codec.LONG.optionalFieldOf("decor_day", -1L).forGetter(t -> t.decorDay),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("catches", Map.of()).forGetter(t -> t.catches),
			Codec.LONG.optionalFieldOf("catch_day", -1L).forGetter(t -> t.catchDay))
			.apply(i, (ma, co, mk, el, he, hi, de, dd, ca, cd) -> {
				TownData t = new TownData();
				for (Parcel p : ma) if (t.mail.size() < 2 * com.tcordeiro.vilas.logic.TownRules.MAIL_PER_VILLAGE) t.mail.add(p); // folga: nunca se perde uma carta
				t.counter = co;
				t.market = mk.orElseGet(Market::new);
				t.election = el.orElseGet(Election::new);
				for (Helper h : he) if (t.helpers.size() < 16) t.helpers.add(h);
				LifeData.capPut(t.hired, hi, LifeData.MAX_MAP);
				t.decor = de.orElse(null);
				t.decorDay = dd;
				LifeData.capPut(t.catches, ca, 64);
				t.catchDay = cd;
				return t;
			}));

	public final List<Parcel> mail = new ArrayList<>();
	public long counter;
	public Market market = new Market();
	public Election election = new Election();
	public final List<Helper> helpers = new ArrayList<>();
	/** Jogador → dia do último contrato (um por dia). */
	public final Map<UUID, Long> hired = new HashMap<>();
	/** A missão temporária «decorar a festa» (null = nenhuma) e o dia da festa a que pertence. */
	public Mission decor;
	public long decorDay = -1;
	/** Pescador → peixes apanhados hoje (repõem trocas, com teto). */
	public final Map<UUID, Integer> catches = new HashMap<>();
	public long catchDay = -1;

	public Helper helperOf(UUID player) {
		for (Helper h : helpers) if (h.player.equals(player) && !h.ended) return h;
		return null;
	}

	public Helper helperByVillager(UUID villager) {
		for (Helper h : helpers) if (h.villager.equals(villager) && !h.ended) return h;
		return null;
	}

	public int activeHelpers() {
		int n = 0;
		for (Helper h : helpers) if (!h.ended) n++;
		return n;
	}

	public int pendingOf(UUID player) {
		int n = 0;
		for (Parcel p : mail) if (p.from.equals(player) && !p.returned) n++;
		return n;
	}

	public int pending() {
		int n = 0;
		for (Parcel p : mail) if (!p.returned) n++;
		return n;
	}
}
