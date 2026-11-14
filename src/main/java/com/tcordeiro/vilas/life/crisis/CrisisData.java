package com.tcordeiro.vilas.life.crisis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.village.SafeCodecs;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.6 crises — O que a vila guarda da reconstrução, das crises, dos funerais e da velhice (campo {@code crisis} do {@code TownData}).
 * Tudo com tetos: os blocos partidos ({@link CrisisRules#REBUILD_MAX}), as campas, os lutos e os «visto pela primeira vez».
 */
public final class CrisisData {
	public static final int MAX_DEATHS = 8, MAX_MAP = 256, MAX_CRISES = 3;

	/** Um bloco partido por um monstro, uma explosão ou o fogo: onde e como era. */
	public record Broken(long pos, BlockState state) {
		public static final Codec<Broken> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("pos").forGetter(Broken::pos), BlockState.CODEC.fieldOf("state").forGetter(Broken::state)).apply(i, Broken::new));
	}

	/** Uma crise com missão relâmpago: tipo, quando acaba, meta, recompensa, o progresso de cada jogador e quem já recebeu. */
	public static final class Crisis {
		public static final Codec<Crisis> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("id").forGetter(c -> c.id),
				Codec.string(1, 16).fieldOf("type").forGetter(c -> c.type),
				Codec.LONG.fieldOf("start").forGetter(c -> c.start),
				Codec.LONG.fieldOf("until").forGetter(c -> c.until),
				Codec.intRange(1, 64).fieldOf("goal").forGetter(c -> c.goal),
				Codec.intRange(0, 64).fieldOf("emeralds").forGetter(c -> c.emeralds),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(c -> c.progress),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("paid", List.of()).forGetter(c -> List.copyOf(c.paid)),
				Codec.LONG.optionalFieldOf("where", 0L).forGetter(c -> c.where))
				.apply(i, (id, ty, st, un, go, em, pr, pa, wh) -> {
					Crisis c = new Crisis(id, ty, st, un, go, em);
					capPut(c.progress, pr, 64);
					for (UUID u : pa) if (c.paid.size() < 16) c.paid.add(u);
					c.where = wh;
					return c;
				}));

		public final long id, start;
		public final String type;
		public long until;
		public final int goal, emeralds;
		public final Map<UUID, Integer> progress = new HashMap<>();
		public final Set<UUID> paid = new LinkedHashSet<>();
		/** Onde está o problema (BlockPos.asLong): o fogo, para os balões. */
		public long where;

		public Crisis(long id, String type, long start, long until, int goal, int emeralds) {
			this.id = id;
			this.type = type;
			this.start = start;
			this.until = until;
			this.goal = goal;
			this.emeralds = emeralds;
		}
	}

	/** Um aldeão que morreu: para o funeral no dia seguinte e para o epitáfio. */
	public static final class Death {
		public static final Codec<Death> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.STRING_CODEC.fieldOf("villager").forGetter(d -> d.villager),
				Codec.string(0, 48).optionalFieldOf("name", "").forGetter(d -> d.name),
				Codec.string(0, 96).optionalFieldOf("profession", "").forGetter(d -> d.profession),
				Codec.LONG.optionalFieldOf("days", 1L).forGetter(d -> d.days),
				Codec.LONG.fieldOf("day").forGetter(d -> d.day),
				Codec.string(0, 16).optionalFieldOf("cause", "").forGetter(d -> d.cause),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("friends", List.of()).forGetter(d -> d.friends),
				Codec.LONG.optionalFieldOf("grave", Long.MIN_VALUE).forGetter(d -> d.grave),
				Codec.LONG.optionalFieldOf("held", -1L).forGetter(d -> d.held))
				.apply(i, (vi, na, pr, da, dy, ca, fr, gr, he) -> {
					Death d = new Death(vi, na, pr, da, dy, ca);
					for (UUID u : fr) if (d.friends.size() < 8) d.friends.add(u);
					d.grave = gr;
					d.held = he;
					return d;
				}));

		public final UUID villager;
		public final String name, profession, cause;
		public final long days, day;
		/** Os amigos (ficam de luto e vão ao funeral). */
		public final List<UUID> friends = new ArrayList<>();
		/** A campa (BlockPos.asLong; MIN_VALUE = ainda nenhuma) e quando começou o funeral (−1 = ainda não). */
		public long grave = Long.MIN_VALUE, held = -1;

		public Death(UUID villager, String name, String profession, long days, long day, String cause) {
			this.villager = villager;
			this.name = name;
			this.profession = profession;
			this.days = days;
			this.day = day;
			this.cause = cause;
		}
	}

	public static final Codec<CrisisData> CODEC = RecordCodecBuilder.create(i -> i.group(
			SafeCodecs.list(Broken.CODEC, "bloco partido").optionalFieldOf("broken", List.of()).forGetter(d -> d.broken),
			Codec.LONG.optionalFieldOf("last_broken", -1L).forGetter(d -> d.lastBroken),
			SafeCodecs.list(Crisis.CODEC, "crise").optionalFieldOf("crises", List.of()).forGetter(d -> d.crises),
			Codec.unboundedMap(Codec.string(1, 16), Codec.LONG).optionalFieldOf("last_day", Map.of()).forGetter(d -> d.lastDay),
			Codec.INT.optionalFieldOf("hunger_days", 0).forGetter(d -> d.hungerDays),
			Codec.LONG.optionalFieldOf("hunger_checked", -1L).forGetter(d -> d.hungerChecked),
			SafeCodecs.list(Death.CODEC, "morte").optionalFieldOf("deaths", List.of()).forGetter(d -> d.deaths),
			Codec.LONG.listOf().optionalFieldOf("graves", List.of()).forGetter(d -> d.graves),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("mourning", Map.of()).forGetter(d -> d.mourning),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("first_seen", Map.of()).forGetter(d -> d.firstSeen),
			Codec.LONG.optionalFieldOf("counter", 0L).forGetter(d -> d.counter),
			Codec.LONG.optionalFieldOf("aging_day", -1L).forGetter(d -> d.agingDay),
			Codec.LONG.listOf().optionalFieldOf("tainted", List.of()).forGetter(d -> List.copyOf(d.tainted))) // revisão: fogos de jogador
			.apply(i, (br, lb, cr, ld, hd, hc, de, gr, mo, fs, co, ag, ta) -> {
				CrisisData d = new CrisisData();
				for (Broken b : br) if (d.broken.size() < CrisisRules.REBUILD_MAX * 2) d.broken.add(b); // folga: config maior
				d.lastBroken = lb;
				for (Crisis c : cr) if (d.crises.size() < MAX_CRISES) d.crises.add(c);
				capPut(d.lastDay, ld, 8);
				d.hungerDays = hd;
				d.hungerChecked = hc;
				for (Death x : de) if (d.deaths.size() < MAX_DEATHS) d.deaths.add(x);
				for (Long g : gr) if (d.graves.size() < CrisisRules.MAX_GRAVES) d.graves.add(g);
				capPut(d.mourning, mo, MAX_MAP);
				capPut(d.firstSeen, fs, MAX_MAP);
				d.counter = co;
				d.agingDay = ag;
				for (Long t : ta) if (d.tainted.size() < 512) d.tainted.add(t); // MAX_TAINTED
				return d;
			}));

	static <K, V> void capPut(Map<K, V> to, Map<K, V> from, int cap) {
		for (var e : from.entrySet()) {
			if (to.size() >= cap) break;
			to.put(e.getKey(), e.getValue());
		}
	}

	/** Os blocos partidos à espera do Construtor (por ordem de chegada; a obra ordena-os de baixo para cima). */
	public final List<Broken> broken = new ArrayList<>();
	/** Posições já registadas (não guardado; refeito a partir de {@link #broken}). */
	private transient Set<Long> brokenAt;
	public long lastBroken = -1;
	public final List<Crisis> crises = new ArrayList<>();
	/** Tipo de crise → dia da última (para o intervalo). */
	public final Map<String, Long> lastDay = new HashMap<>();
	public int hungerDays;
	public long hungerChecked = -1;
	public final List<Death> deaths = new ArrayList<>();
	public final List<Long> graves = new ArrayList<>();
	/** Aldeão de luto → até quando (game time). */
	public final Map<UUID, Long> mourning = new HashMap<>();
	/** Aldeão → dia em que foi visto pela primeira vez na vila (para os dias de vida). */
	public final Map<UUID, Long> firstSeen = new HashMap<>();
	public long counter, agingDay = -1;
	/**
	 * Revisão de segurança: os fogos (BlockPos.asLong) com origem de jogador — acesos com um jogador a 16 blocos, num terreno, perto de
	 * um dispensador ou de lava, ou espalhados de um fogo destes. Apagá-los não paga nada. Guardado no mundo (sobrevive a reinícios).
	 */
	public static final int MAX_TAINTED = 512;
	public final java.util.Set<Long> tainted = new java.util.LinkedHashSet<>();

	public boolean recorded(long pos) {
		if (brokenAt == null) {
			brokenAt = new java.util.HashSet<>();
			for (Broken b : broken) brokenAt.add(b.pos());
		}
		return brokenAt.contains(pos);
	}

	public void add(Broken b) {
		recorded(b.pos());
		broken.add(b);
		brokenAt.add(b.pos());
	}

	public void clearBroken() {
		broken.clear();
		if (brokenAt != null) brokenAt.clear();
	}

	public Crisis active(String type) {
		for (Crisis c : crises) if (c.type.equals(type)) return c;
		return null;
	}

	public static <K, V> void putCapped(Map<K, V> map, K key, V value) {
		if (!map.containsKey(key) && map.size() >= MAX_MAP) map.remove(map.keySet().iterator().next());
		map.put(key, value);
	}
}
