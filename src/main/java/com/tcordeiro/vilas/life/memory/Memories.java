package com.tcordeiro.vilas.life.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tcordeiro.vilas.logic.LoreRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.6.0 — As recordações de cada aldeão: até {@link LoreRules#MEMORY_SLOTS}, a mais fraca sai quando entra uma mais forte, e o
 * peso decai devagar. Guarda-se só o facto (tipo, quem, dia, peso); a frase gera-se ao abrir o menu, na língua de quem lê.
 */
public final class Memories {
	private Memories() {}

	public static final String SAVED = "saved", GIFT = "gift", HIT = "hit", LETTER = "letter", DONATED = "donated";

	/** Pesos de cada tipo: o resgate fica muito tempo; uma pancada também. */
	public static int weight(String kind) {
		return switch (kind) {
			case SAVED -> 10;
			case HIT -> 6;
			case GIFT -> 4;
			case DONATED -> 3;
			default -> 2;
		};
	}

	/** Grava uma recordação neste aldeão. Devolve true se entrou. */
	public static boolean remember(ServerLevel level, Village v, Villager vi, String kind, UUID who, String name) {
		return remember(v, vi.getUUID(), kind, who, name, VillageEconomy.day(level)) && changed(level);
	}

	static boolean changed(ServerLevel level) {
		VillageData.get(level).changed();
		return true;
	}

	/** A regra, sem mundo (para os testes): entra se houver espaço ou se for mais forte que a mais fraca. */
	public static boolean remember(Village v, UUID villager, String kind, UUID who, String name, long day) {
		Lore l = v.civic.life.lore;
		List<Lore.Memory> list = l.memories.get(villager);
		if (list == null) {
			if (l.memories.size() >= Lore.MAX_VILLAGERS) l.memories.remove(l.memories.keySet().iterator().next());
			list = new ArrayList<>();
			l.memories.put(villager, list);
		}
		int w = weight(kind);
		// a mesma coisa com a mesma pessoa no mesmo dia não se repete
		for (Lore.Memory m : list) if (m.kind().equals(kind) && m.day() == day && m.who().equals(Optional.ofNullable(who))) return false;
		int[] now = new int[list.size()];
		for (int i = 0; i < now.length; i++) now[i] = LoreRules.weightNow(list.get(i).weight(), list.get(i).day(), day);
		int slot = LoreRules.slotFor(now, w);
		Lore.Memory m = new Lore.Memory(kind, Optional.ofNullable(who), name.length() > 48 ? name.substring(0, 48) : name, day, w);
		if (slot == -2) return false;
		if (slot == -1) list.add(m);
		else list.set(slot, m);
		return true;
	}

	public static List<Lore.Memory> of(Village v, UUID villager) {
		return v.civic.life.lore.memories.getOrDefault(villager, List.of());
	}

	/** Este aldeão lembra-se de algo com este jogador? (o tipo mais forte, ou null) */
	public static Lore.Memory about(Village v, UUID villager, UUID player) {
		Lore.Memory best = null;
		for (Lore.Memory m : of(v, villager))
			if (m.who().isPresent() && m.who().get().equals(player) && (best == null || m.weight() > best.weight())) best = m;
		return best;
	}

	/** A frase de uma recordação (gerada agora): «Lembro-me do dia 12, quando o Talys me salvou.» */
	public static Component line(Lore.Memory m) {
		return Component.translatable("tvilas.memory." + m.kind(), m.name(), m.day());
	}

	/** Um aldeão morreu: as recordações dele vão-se (a crónica fica). */
	public static void forget(Village v, UUID villager) {
		v.civic.life.lore.memories.remove(villager);
	}
}
