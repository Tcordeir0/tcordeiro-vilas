package com.tcordeiro.vilas.life.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.life.projects.Community;
import com.tcordeiro.vilas.logic.LoreRules;

import net.minecraft.core.UUIDUtil;

/**
 * 0.6.0 — O que a vila guarda da memória (campo {@code lore} da vida): as recordações de cada aldeão (até 6, como no Dwarf
 * Fortress), a crónica da vila (só o FACTO; o texto gera-se ao ler, como as «tales» do RimWorld), os tetos das cartas e dos
 * resgates e a obra comunitária. Tudo com tetos.
 */
public final class Lore {
	public static final int MAX_VILLAGERS = 128, MAX_MAP = 512;

	/** Uma recordação: o tipo («saved», «gift», «hit», «letter», «donated»), quem, o nome, o dia, o peso. */
	public record Memory(String kind, Optional<UUID> who, String name, long day, int weight) {
		public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.string(1, 24).fieldOf("kind").forGetter(Memory::kind), UUIDUtil.STRING_CODEC.optionalFieldOf("who").forGetter(Memory::who),
				Codec.string(0, 48).optionalFieldOf("name", "").forGetter(Memory::name), Codec.LONG.fieldOf("day").forGetter(Memory::day),
				Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(Memory::weight)).apply(i, Memory::new));
	}

	/** Uma entrada da crónica: o dia, a chave e até 4 argumentos (texto simples; «#chave» = tradução). */
	public record Entry(long day, String key, List<String> args) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("day").forGetter(Entry::day), Codec.string(1, 64).fieldOf("key").forGetter(Entry::key),
				Codec.string(0, 64).listOf().optionalFieldOf("args", List.of()).forGetter(Entry::args))
				.apply(i, (d, k, a) -> new Entry(d, k, List.copyOf(a.size() > 4 ? a.subList(0, 4) : a))));
	}

	public static final Codec<Lore> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Memory.CODEC.listOf()).optionalFieldOf("memories", Map.of()).forGetter(l -> l.memories),
			Entry.CODEC.listOf().optionalFieldOf("chronicle", List.of()).forGetter(l -> l.chronicle),
			Codec.LONG.optionalFieldOf("dropped", 0L).forGetter(l -> l.dropped),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("saved", Map.of()).forGetter(l -> l.saved),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("letters", Map.of()).forGetter(l -> l.letters),
			Community.CODEC.optionalFieldOf("project").forGetter(l -> Optional.ofNullable(l.project)),
			Codec.LONG.optionalFieldOf("project_day", -1L).forGetter(l -> l.projectDay),
			Codec.INT.optionalFieldOf("projects_done", 0).forGetter(l -> l.projectsDone))
			.apply(i, (me, ch, dr, sa, le, pr, pd, pn) -> {
				Lore l = new Lore();
				for (var e : me.entrySet()) {
					if (l.memories.size() >= MAX_VILLAGERS) break;
					List<Memory> list = new ArrayList<>(e.getValue().size() > LoreRules.MEMORY_SLOTS ? e.getValue().subList(0, LoreRules.MEMORY_SLOTS) : e.getValue());
					l.memories.put(e.getKey(), list);
				}
				int from = Math.max(0, ch.size() - LoreRules.CHRONICLE_MAX);
				l.chronicle.addAll(ch.subList(from, ch.size()));
				l.dropped = Math.max(0, dr) + from;
				cap(l.saved, sa);
				cap(l.letters, le);
				l.project = pr.orElse(null);
				l.projectDay = pd;
				l.projectsDone = Math.max(0, pn);
				return l;
			}));

	static <K, V> void cap(Map<K, V> to, Map<K, V> from) {
		for (var e : from.entrySet()) {
			if (to.size() >= MAX_MAP) break;
			to.put(e.getKey(), e.getValue());
		}
	}

	public static <K, V> void putCapped(Map<K, V> map, K key, V value) {
		if (!map.containsKey(key) && map.size() >= MAX_MAP) map.remove(map.keySet().iterator().next());
		map.put(key, value);
	}

	/** Aldeão → recordações (até 6). */
	public final Map<UUID, List<Memory>> memories = new HashMap<>();
	/** A crónica (as mais antigas saem; {@code dropped} conta quantas já saíram). */
	public final List<Entry> chronicle = new ArrayList<>();
	public long dropped;
	/** «jogador:aldeão» → dia do último resgate pago em reputação. */
	public final Map<String, Long> saved = new HashMap<>();
	/** Jogador → semana da última carta/presente de um aldeão. */
	public final Map<UUID, Long> letters = new HashMap<>();
	/** A obra comunitária a decorrer (null = nenhuma), o dia em que foi proposta e quantas já se fizeram. */
	public Community project;
	public long projectDay = -1;
	public int projectsDone;
}
