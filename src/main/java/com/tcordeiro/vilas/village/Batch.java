package com.tcordeiro.vilas.village;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * 0.3.0 — O lote de missões de uma vila: a cada {@code mission_batch_days} dias de jogo o quadro do Prefeito troca TODAS as
 * missões e alguns aldeões com profissão fazem um pedido. Cada missão e cada pedido pode ser feito uma vez por jogador no lote.
 */
public final class Batch {
	/** Ainda nenhum lote. */
	public static final long NONE = Long.MIN_VALUE;

	public static final Codec<Batch> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("id", NONE).forGetter(b -> b.id),
			Codec.string(1, 160).listOf().optionalFieldOf("done", List.of()).forGetter(b -> List.copyOf(b.done)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Mission.CODEC).optionalFieldOf("requests", Map.of()).forGetter(b -> b.requests),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("named", List.of()).forGetter(b -> List.copyOf(b.named)))
			.apply(i, (id, d, r, n) -> {
				Batch b = new Batch();
				b.id = id;
				b.done.addAll(d);
				b.requests.putAll(r);
				b.named.addAll(n);
				return b;
			}));

	/** O lote em vigor (dia de jogo ÷ dias do lote); {@link #NONE} = ainda nenhum. */
	public long id = NONE;
	/** «jogador:missão» (id da missão do quadro) e «jogador:aldeão» (pedidos) já feitos neste lote. */
	public final Set<String> done = new HashSet<>();
	/** Aldeão → o pedido dele neste lote. */
	public final Map<UUID, Mission> requests = new HashMap<>();
	/** Aldeões a quem o Vilas pôs o «!» (para o tirar depois, sem mexer nos nomes que os jogadores deram). */
	public final Set<UUID> named = new HashSet<>();

	public static String key(UUID player, long missionId) {
		return player + ":" + missionId;
	}

	public static String key(UUID player, UUID villager) {
		return player + ":" + villager;
	}
}
