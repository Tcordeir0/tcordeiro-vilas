package com.tcordeiro.vilas.life.projects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.logic.LoreRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * 0.6.0 — Uma obra comunitária (ponte, chafariz, farol ou coreto): o que falta, o que já se doou, quem doou (para a placa) e a
 * reputação das doações já dada hoje. Guardada na vila ({@link com.tcordeiro.vilas.life.memory.Lore#project}).
 */
public final class Community {
	public static final String BRIDGE = "bridge", FOUNTAIN = "fountain", LIGHTHOUSE = "lighthouse", BANDSTAND = "bandstand";
	public static final List<String> KINDS = List.of(FOUNTAIN, BANDSTAND, LIGHTHOUSE, BRIDGE);
	/** 0 = a juntar materiais, 1 = em obra (o Construtor está a fazer), 2 = acabada. */
	public static final int COLLECTING = 0, BUILDING = 1, DONE = 2;

	public static final Codec<Community> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.string(1, 16).fieldOf("kind").forGetter(c -> c.kind),
			BlockPos.CODEC.fieldOf("site").forGetter(c -> c.site),
			Codec.INT.optionalFieldOf("dir", 0).forGetter(c -> c.dir),
			Codec.INT.optionalFieldOf("length", 0).forGetter(c -> c.length),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("needs").forGetter(c -> c.needs),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("have", Map.of()).forGetter(c -> c.have),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.string(0, 32)).optionalFieldOf("donors", Map.of()).forGetter(c -> c.donors),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("given", Map.of()).forGetter(c -> c.given),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("rep_day", Map.of()).forGetter(c -> c.repDay),
			Codec.INT.optionalFieldOf("state", COLLECTING).forGetter(c -> c.state))
			.apply(i, (k, s, d, l, n, h, dn, g, r, st) -> {
				Community c = new Community(KINDS.contains(k) ? k : FOUNTAIN, s, d, Math.max(0, Math.min(16, l)));
				for (var e : n.entrySet()) if (c.needs.size() < 8) c.needs.put(e.getKey(), Math.max(0, Math.min(256, e.getValue())));
				for (var e : h.entrySet()) if (c.needs.containsKey(e.getKey())) c.have.put(e.getKey(), Math.max(0, Math.min(c.needs.get(e.getKey()), e.getValue())));
				for (var e : dn.entrySet()) if (c.donors.size() < LoreRules.MAX_DONORS) c.donors.put(e.getKey(), e.getValue());
				for (var e : g.entrySet()) if (c.given.size() < LoreRules.MAX_DONORS) c.given.put(e.getKey(), e.getValue());
				for (var e : r.entrySet()) if (c.repDay.size() < 64) c.repDay.put(e.getKey(), e.getValue());
				c.state = st;
				return c;
			}));

	public final String kind;
	public final BlockPos site;
	/** Ponte: 0 = ao longo de x, 1 = ao longo de z; e o comprimento (blocos de água a atravessar). */
	public final int dir, length;
	/** Item (id) → quantos são precisos; quantos já se doaram. */
	public final Map<String, Integer> needs = new LinkedHashMap<>(), have = new LinkedHashMap<>();
	/** Quem doou (nome, para a placa) e quantos itens doou. */
	public final Map<UUID, String> donors = new LinkedHashMap<>();
	public final Map<UUID, Integer> given = new LinkedHashMap<>();
	/** «jogador:dia» → reputação já ganha com doações nesse dia. */
	public final Map<String, Integer> repDay = new LinkedHashMap<>();
	public int state = COLLECTING;

	public Community(String kind, BlockPos site, int dir, int length) {
		this.kind = kind;
		this.site = site.immutable();
		this.dir = dir;
		this.length = length;
	}

	public boolean complete() {
		for (var e : needs.entrySet()) if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
		return true;
	}

	/** Os doadores por ordem de quem deu mais (para a placa). */
	public List<String> topDonors() {
		List<UUID> ids = new ArrayList<>(donors.keySet());
		ids.sort((a, b) -> Integer.compare(given.getOrDefault(b, 0), given.getOrDefault(a, 0)));
		List<String> out = new ArrayList<>();
		for (UUID id : ids) out.add(donors.get(id));
		return out;
	}
}
