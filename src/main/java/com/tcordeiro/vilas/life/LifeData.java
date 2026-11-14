package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * 0.4.0 — O que a vida dos aldeões guarda no mundo (campo {@code life} da {@code Civic}): os factos da vila (para as conversas e a
 * fofoca, com quem já os sabe), a opinião de cada par de aldeões, os presentes e agradecimentos (tetos por semana e por dia), os
 * aprendizes, a festa, os guardas e o sino. Tudo com tetos de tamanho: um mundo antigo ou um dado estragado não cresce sem fim.
 */
public final class LifeData {
	public static final int MAX_FACTS = 24, MAX_KNOWERS = 64, MAX_OPINIONS = 256, MAX_MAP = 512;

	/** Um facto real da vila: «o imposto subiu para 2,5 %», «o Talys pagou a dívida». {@code args} são texto simples. */
	public static final class Fact {
		public static final Codec<Fact> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("id").forGetter(f -> f.id), Codec.LONG.fieldOf("day").forGetter(f -> f.day),
				Codec.string(1, 64).fieldOf("key").forGetter(f -> f.key),
				Codec.string(0, 64).listOf().optionalFieldOf("args", List.of()).forGetter(f -> f.args),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("knowers", List.of()).forGetter(f -> List.copyOf(f.knowers)))
				.apply(i, (id, day, key, args, kn) -> {
					Fact f = new Fact(id, day, key, args);
					for (UUID u : kn) if (f.knowers.size() < MAX_KNOWERS) f.knowers.add(u);
					return f;
				}));

		public final long id, day;
		public final String key;
		public final List<String> args;
		public final Set<UUID> knowers = new LinkedHashSet<>();

		public Fact(long id, long day, String key, List<String> args) {
			this.id = id;
			this.day = day;
			this.key = key;
			this.args = List.copyOf(args.size() > 4 ? args.subList(0, 4) : args);
		}

		public boolean learn(UUID who) {
			if (knowers.size() >= MAX_KNOWERS) return false;
			return knowers.add(who);
		}
	}

	/** A festa: dia marcado (−1 = nenhuma), se já se avisou, quanto do orçamento já se gastou e os reembolsos por jogador. */
	public static final class Festa {
		public static final Codec<Festa> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("day", -1L).forGetter(f -> f.day),
				Codec.STRING.optionalFieldOf("reason", "").forGetter(f -> f.reason),
				Codec.LONG.optionalFieldOf("spent", 0L).forGetter(f -> f.spent),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("rebates", Map.of()).forGetter(f -> f.rebates),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owed", Map.of()).forGetter(f -> f.owed),
				Codec.LONG.optionalFieldOf("last", -1L).forGetter(f -> f.last))
				.apply(i, (d, r, s, rb, ow, la) -> {
					Festa f = new Festa();
					f.day = d;
					f.reason = r;
					f.spent = Math.max(0, s);
					f.rebates.putAll(rb);
					f.owed.putAll(ow);
					f.last = la;
					return f;
				}));

		public long day = -1;
		public String reason = "";
		/** Orçamento já gasto em reembolsos nesta festa (cêntimos). */
		public long spent;
		public final Map<UUID, Long> rebates = new HashMap<>();
		/** Sem o CubCoin: o que ainda não chega a uma esmeralda inteira (fica para a próxima troca da mesma festa). */
		public final Map<UUID, Long> owed = new HashMap<>();
		/** O dia da última festa (não se marcam duas no mesmo dia). */
		public long last = -1;

		public void reset() {
			spent = 0;
			rebates.clear();
			owed.clear();
		}
	}

	public static final Codec<LifeData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Fact.CODEC.listOf().optionalFieldOf("facts", List.of()).forGetter(d -> d.facts),
			Codec.LONG.optionalFieldOf("fact_counter", 0L).forGetter(d -> d.factCounter),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("opinions", Map.of()).forGetter(d -> d.opinions),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("gifts", Map.of()).forGetter(d -> d.gifts),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("gift_rep", Map.of()).forGetter(d -> d.giftRep),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("thanks", Map.of()).forGetter(d -> d.thanks),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("thanks_day", Map.of()).forGetter(d -> d.thanksDay),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.string(1, 96)).optionalFieldOf("apprentices", Map.of()).forGetter(d -> d.apprentices),
			Festa.CODEC.optionalFieldOf("festa").forGetter(d -> java.util.Optional.of(d.festa)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("guards", Map.of()).forGetter(d -> d.guards),
			Codec.LONG.optionalFieldOf("guard_day", -1L).forGetter(d -> d.guardDay),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("last_seen", Map.of()).forGetter(d -> d.lastSeen),
			Codec.LONG.optionalFieldOf("last_chime", -1L).forGetter(d -> d.lastChime),
			Codec.LONG.optionalFieldOf("meeting_day", -1L).forGetter(d -> d.meetingDay),
			TownData.CODEC.optionalFieldOf("town").forGetter(d -> java.util.Optional.of(d.town)),
			com.tcordeiro.vilas.life.memory.Lore.CODEC.optionalFieldOf("lore").forGetter(d -> java.util.Optional.of(d.lore))) // 0.6.0 (16.º campo: o máximo)
			.apply(i, (fa, fc, op, gi, gr, th, td, ap, fe, gu, gd, ls, lc, md, tw, lo) -> {
				LifeData d = new LifeData();
				for (Fact f : fa) if (d.facts.size() < MAX_FACTS) d.facts.add(f);
				d.factCounter = fc;
				capPut(d.opinions, op, MAX_OPINIONS);
				capPut(d.gifts, gi, MAX_MAP);
				capPut(d.giftRep, gr, MAX_MAP);
				capPut(d.thanks, th, MAX_MAP);
				capPut(d.thanksDay, td, MAX_MAP);
				capPut(d.apprentices, ap, MAX_MAP);
				d.festa = fe.orElseGet(Festa::new);
				capPut(d.guards, gu, 16);
				d.guardDay = gd;
				capPut(d.lastSeen, ls, MAX_MAP);
				d.lastChime = lc;
				d.meetingDay = md;
				d.town = tw.orElseGet(TownData::new);
				d.lore = lo.orElseGet(com.tcordeiro.vilas.life.memory.Lore::new);
				return d;
			}));

	static <K, V> void capPut(Map<K, V> to, Map<K, V> from, int cap) {
		for (var e : from.entrySet()) {
			if (to.size() >= cap) break;
			to.put(e.getKey(), e.getValue());
		}
	}

	public final List<Fact> facts = new ArrayList<>();
	public long factCounter;
	/** «aldeãoA|aldeãoB» (ordenados) → opinião (−100..100). */
	public final Map<String, Integer> opinions = new HashMap<>();
	/** «jogador:aldeão:semana» → presentes dados. */
	public final Map<String, Integer> gifts = new HashMap<>();
	/** «jogador:dia» → reputação já ganha com presentes nesse dia. */
	public final Map<String, Integer> giftRep = new HashMap<>();
	/** «jogador:aldeão» → semana do último presente de volta; jogador → dia do último. */
	public final Map<String, Long> thanks = new HashMap<>();
	public final Map<UUID, Long> thanksDay = new HashMap<>();
	/** Criança → profissão do mestre que segue (herda-a ao crescer). */
	public final Map<UUID, String> apprentices = new HashMap<>();
	public Festa festa = new Festa();
	/** Golem da guarda → posto (BlockPos.asLong). */
	public final Map<UUID, Long> guards = new HashMap<>();
	public long guardDay = -1;
	/** Jogador → último dia em que esteve no território (boas-vindas). */
	public final Map<UUID, Long> lastSeen = new HashMap<>();
	public long lastChime = -1;
	public long meetingDay = -1;
	/** 0.5.0: correio, mercado, eleições, ajudantes, missão da festa, peixe. */
	public TownData town = new TownData();
	/** 0.6.0: memória dos aldeões, crónica da vila, cartas dos aldeões e obra comunitária. */
	public com.tcordeiro.vilas.life.memory.Lore lore = new com.tcordeiro.vilas.life.memory.Lore();

	public static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	public int opinion(UUID a, UUID b) {
		return opinions.getOrDefault(pair(a, b), 0);
	}

	public void setOpinion(UUID a, UUID b, int value) {
		String k = pair(a, b);
		if (!opinions.containsKey(k) && opinions.size() >= MAX_OPINIONS) opinions.remove(opinions.keySet().iterator().next());
		opinions.put(k, value);
	}

	/** Apaga presentes de semanas e dias antigos (chamado uma vez por dia). */
	public void prune(long day) {
		long week = com.tcordeiro.vilas.logic.LifeRules.week(day);
		gifts.keySet().removeIf(k -> !k.endsWith(":" + week));
		giftRep.keySet().removeIf(k -> !k.endsWith(":" + day));
		thanks.values().removeIf(w -> w < week - 1);
		facts.removeIf(f -> day - f.day > 8);
	}

	public static <K, V> void putCapped(Map<K, V> map, K key, V value) {
		if (!map.containsKey(key) && map.size() >= MAX_MAP) map.remove(map.keySet().iterator().next());
		map.put(key, value);
	}
}
