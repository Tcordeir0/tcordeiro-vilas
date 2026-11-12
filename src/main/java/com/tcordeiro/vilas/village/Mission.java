package com.tcordeiro.vilas.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.logic.MissionRules;

/** Uma missão ativa no quadro: um número único (para o progresso de cada jogador) e o modelo. */
public record Mission(long id, MissionRules.Kind kind, String target, int count, int emeralds, int reputation, int tier) {
	/** M14: um tipo desconhecido dá erro de leitura (a missão é refeita) em vez de rebentar o carregamento. */
	public static final Codec<MissionRules.Kind> KIND_CODEC = Codec.STRING.comapFlatMap(s -> {
		for (MissionRules.Kind k : MissionRules.Kind.values()) if (k.name().equals(s)) return com.mojang.serialization.DataResult.success(k);
		return com.mojang.serialization.DataResult.error(() -> "missão desconhecida: " + s);
	}, MissionRules.Kind::name);
	public static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Mission::id),
			KIND_CODEC.fieldOf("kind").forGetter(Mission::kind),
			Codec.string(1, 128).fieldOf("target").forGetter(Mission::target),
			Codec.intRange(1, 4096).fieldOf("count").forGetter(Mission::count),
			Codec.intRange(0, 4096).fieldOf("emeralds").forGetter(Mission::emeralds),
			Codec.intRange(0, 100000).fieldOf("reputation").forGetter(Mission::reputation),
			Codec.intRange(1, 3).fieldOf("tier").forGetter(Mission::tier)).apply(i, Mission::new));

	public static Mission of(long id, MissionRules.Template t) {
		return new Mission(id, t.kind(), t.target(), t.count(), t.emeralds(), t.reputation(), t.tier());
	}

	public MissionRules.Template template() {
		return new MissionRules.Template(kind, target, count, emeralds, reputation, tier);
	}
}
