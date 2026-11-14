package com.tcordeiro.vilas.life.election;

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
import net.minecraft.world.item.ItemStack;

/**
 * 0.6 eleições — O mandato em vigor e os bónus do voto (guardado dentro de {@code TownData.Election}, campo {@code mandate}): quem
 * votou no vencedor, os itens por entregar (presente da posse e broche «Eu votei»), o que a tesouraria já devolveu aos apoiadores, os
 * pedidos «!» exclusivos deste lote e quem já recebeu a reputação do voto. Tudo com tetos de tamanho.
 */
public final class Mandate {
	public static final int MAX = 512, MAX_ITEMS = 4;

	public static final Codec<Mandate> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("term", 0).forGetter(m -> m.term),
			Codec.LONG.optionalFieldOf("since", -1L).forGetter(m -> m.since),
			Codec.string(0, 16).optionalFieldOf("policy", "").forGetter(m -> m.policy),
			Codec.string(0, 48).optionalFieldOf("mayor", "").forGetter(m -> m.mayor),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("supporters", List.of()).forGetter(m -> List.copyOf(m.supporters)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf()).optionalFieldOf("pending", Map.of()).forGetter(m -> m.pending),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("rebates", Map.of()).forGetter(m -> m.rebates),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owed", Map.of()).forGetter(m -> m.owed),
			Codec.LONG.optionalFieldOf("spent", 0L).forGetter(m -> m.spent),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC).optionalFieldOf("reserved", Map.of()).forGetter(m -> m.reserved),
			Codec.LONG.optionalFieldOf("reserved_batch", Long.MIN_VALUE).forGetter(m -> m.reservedBatch),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("rep_given", Map.of()).forGetter(m -> m.repGiven))
			.apply(i, (te, si, po, ma, su, pe, rb, ow, sp, re, rbt, rg) -> {
				Mandate m = new Mandate();
				m.term = Math.max(0, te);
				m.since = si;
				m.policy = po;
				m.mayor = ma;
				for (UUID u : su) if (m.supporters.size() < MAX) m.supporters.add(u);
				for (var e : pe.entrySet()) {
					if (m.pending.size() >= MAX) break;
					List<ItemStack> l = new ArrayList<>();
					for (ItemStack s : e.getValue()) if (l.size() < MAX_ITEMS && !s.isEmpty()) l.add(s);
					if (!l.isEmpty()) m.pending.put(e.getKey(), l);
				}
				cap(m.rebates, rb);
				cap(m.owed, ow);
				m.spent = Math.max(0, sp);
				for (var e : re.entrySet()) if (m.reserved.size() < VoteRules.RESERVED_PER_VILLAGE) m.reserved.put(e.getKey(), e.getValue());
				m.reservedBatch = rbt;
				cap(m.repGiven, rg);
				return m;
			}));

	static <K, V> void cap(Map<K, V> to, Map<K, V> from) {
		for (var e : from.entrySet()) {
			if (to.size() >= MAX) break;
			to.put(e.getKey(), e.getValue());
		}
	}

	/** O número do mandato (= {@code held} da eleição que o deu); 0 = nenhum. */
	public int term;
	/** O dia da posse (−1 = nenhum). */
	public long since = -1;
	/** A proposta vencedora e o nome do eleito. */
	public String policy = "", mayor = "";
	/** Quem votou no vencedor (= na proposta vencedora). */
	public final Set<UUID> supporters = new LinkedHashSet<>();
	/** Itens ainda por entregar (o jogador estava desligado): presente da posse e broche «Eu votei». */
	public final Map<UUID, List<ItemStack>> pending = new HashMap<>();
	/** Desconto do apoiador: reembolsos já pagos neste mandato, o que falta para uma esmeralda inteira, total gasto. */
	public final Map<UUID, Long> rebates = new HashMap<>(), owed = new HashMap<>();
	public long spent;
	/** Pedidos «!» exclusivos: aldeão → apoiador, e o lote a que pertencem. */
	public final Map<UUID, UUID> reserved = new HashMap<>();
	public long reservedBatch = Long.MIN_VALUE;
	/** Jogador → dia em que abriu a eleição em que já recebeu a reputação do voto (uma vez por eleição). */
	public final Map<UUID, Long> repGiven = new HashMap<>();

	/** O mandato em vigor é este? ({@code held} = eleições ganhas até agora nesta vila) */
	public boolean current(int held) {
		return term > 0 && term == held;
	}

	/** Começa um mandato novo: esquece os apoiadores, reembolsos e pedidos do anterior (os itens por entregar ficam). */
	public void begin(int term, long day, String policy, String mayor) {
		this.term = term;
		this.since = day;
		this.policy = policy;
		this.mayor = mayor;
		supporters.clear();
		rebates.clear();
		owed.clear();
		spent = 0;
		reserved.clear();
		reservedBatch = Long.MIN_VALUE;
	}

	public void give(UUID player, ItemStack stack) {
		if (stack.isEmpty()) return;
		if (!pending.containsKey(player) && pending.size() >= MAX) return;
		List<ItemStack> l = pending.computeIfAbsent(player, k -> new ArrayList<>());
		if (l.size() < MAX_ITEMS) l.add(stack);
	}
}
