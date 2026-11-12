package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.tcordeiro.vilas.TVilas;

/**
 * 0.3.0 (M14): listas guardadas com elementos ESTRITOS — um elemento inválido (estragado à mão, de uma versão futura) é
 * ignorado com um aviso no registo, e o resto da vila carrega. Sem isto, ou rebentava o carregamento, ou se aceitava lixo.
 */
public final class SafeCodecs {
	private SafeCodecs() {}

	public static <T> Codec<List<T>> list(Codec<T> element, String what) {
		return new Codec<>() {
			@Override
			public <O> DataResult<Pair<List<T>, O>> decode(DynamicOps<O> ops, O input) {
				return ops.getList(input).map(stream -> {
					List<T> out = new ArrayList<>();
					stream.accept(e -> element.parse(ops, e)
							.resultOrPartial(msg -> TVilas.LOGGER.warn("Vilas: {} inválido ignorado ({})", what, msg)).ifPresent(out::add));
					return Pair.of(out, input);
				});
			}

			@Override
			public <O> DataResult<O> encode(List<T> list, DynamicOps<O> ops, O prefix) {
				return element.listOf().encode(list, ops, prefix);
			}
		};
	}
}
