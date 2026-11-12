package com.tcordeiro.vilas.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.logic.MissionRules;

/** Uma missão ativa no quadro: um número único (para o progresso de cada jogador) e o modelo. */
public record Mission(long id, MissionRules.Kind kind, String target, int count, int emeralds, int reputation, int tier) {
	public static final Codec<MissionRules.Kind> KIND_CODEC = Codec.STRING.xmap(s -> MissionRules.Kind.valueOf(s), MissionRules.Kind::name);
	public static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Mission::id),
			KIND_CODEC.fieldOf("kind").forGetter(Mission::kind),
			Codec.STRING.fieldOf("target").forGetter(Mission::target),
			Codec.INT.fieldOf("count").forGetter(Mission::count),
			Codec.INT.fieldOf("emeralds").forGetter(Mission::emeralds),
			Codec.INT.fieldOf("reputation").forGetter(Mission::reputation),
			Codec.INT.fieldOf("tier").forGetter(Mission::tier)).apply(i, Mission::new));

	public static Mission of(long id, MissionRules.Template t) {
		return new Mission(id, t.kind(), t.target(), t.count(), t.emeralds(), t.reputation(), t.tier());
	}

	public MissionRules.Template template() {
		return new MissionRules.Template(kind, target, count, emeralds, reputation, tier);
	}
}
