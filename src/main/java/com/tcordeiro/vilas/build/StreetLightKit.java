package com.tcordeiro.vilas.build;

import java.lang.reflect.Method;
import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O que a iluminação pública põe em cada poste — integração OPCIONAL com a rede aérea do Industrial 0.11 (por ids e reflexão:
 * nada do Industrial é preciso para compilar).
 * <ul>
 * <li><b>Com o Industrial 0.11+</b>: postes {@code tindustrial:<madeira>_lamp_post} (3 blocos) escolhidos pelo bioma da vila
 * (planície → carvalho, taiga/neve → abeto, savana → acácia, …) ou {@code tindustrial:iron_street_light} (4 blocos) no deserto.
 * Põe-se só o bloco de baixo e o Industrial completa o resto por cima. Os postes ligam-se com
 * {@code com.tcordeiro.industrial.grid.AerialWires.link(ServerLevel, BlockPos, BlockPos)} (cada poste ao mais perto já ligado,
 * ≤ 16 blocos) e a Prefeitura ganha 2 painéis solares e uma bateria, ligados ao primeiro poste: acendem à noite.</li>
 * <li><b>Sem o Industrial</b> (ou um Industrial sem postes, ≤ 0.10): poste de cerca com lanterna vanilla, sempre acesa.</li>
 * </ul>
 */
public abstract class StreetLightKit {
	public static final String WIRES_CLASS = "com.tcordeiro.industrial.grid.AerialWires";
	public static final Identifier IRON_POLE = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "iron_street_light");
	public static final Identifier SOLAR = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "basic_solar_panel");
	public static final Identifier BATTERY = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "battery");
	/** Vão máximo de um fio entre postes no Industrial (GridMath.POLE_SPAN). */
	public static final int POLE_SPAN = 16;

	public static Identifier woodPole(String wood) {
		return Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, wood + "_lamp_post");
	}

	/** Os blocos a pôr em cada poste, de baixo para cima (com o Industrial só o de baixo: o resto aparece sozinho). */
	public abstract List<BlockState> pole();

	/** Blocos livres precisos por cima do chão para o poste inteiro. */
	public int height() {
		return pole().size();
	}

	/** Este bloco é a base de um poste deste kit? */
	public boolean isPole(BlockState s) {
		return s.is(pole().get(0).getBlock());
	}

	/** A fonte da Prefeitura (painéis, bateria); vazio com as lanternas. */
	public List<BlockState> sources() {
		return List.of();
	}

	/** Estica um fio entre dois pontos (nada com as lanternas). */
	public boolean link(ServerLevel level, BlockPos a, BlockPos b) {
		return false;
	}

	public boolean industrial() {
		return false;
	}

	/** Lanternas vanilla num poste de cerca (sem o Industrial). */
	public static final StreetLightKit LANTERNS = new StreetLightKit() {
		@Override
		public List<BlockState> pole() {
			return List.of(Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
		}
	};

	/** O kit para esta vila: com os postes do Industrial se existirem, senão lanternas. */
	public static StreetLightKit forBiome(String biome) {
		if (!Mods.loaded(Mods.INDUSTRIAL)) return LANTERNS;
		String wood = com.tcordeiro.vilas.logic.LightingRules.woodFor(biome);
		Block pole = BuiltInRegistries.BLOCK.getOptional(wood == null ? IRON_POLE : woodPole(wood))
				.or(() -> BuiltInRegistries.BLOCK.getOptional(woodPole("oak"))).orElse(null);
		if (pole == null) return LANTERNS; // Industrial sem postes (≤ 0.10): lanternas
		return new Industrial(pole, pole == BuiltInRegistries.BLOCK.getValue(IRON_POLE) ? 4 : 3);
	}

	/** Kit de uma vila no mundo. */
	public static StreetLightKit forVillage(ServerLevel level, com.tcordeiro.vilas.village.Village v) {
		return forBiome(level.getBiome(v.center).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains"));
	}

	/** Postes do Industrial, com fio aéreo e a fonte na Prefeitura. */
	static final class Industrial extends StreetLightKit {
		final Block pole;
		final int height;
		final Method link;

		Industrial(Block pole, int height) {
			this.pole = pole;
			this.height = height;
			Method m = null;
			try {
				m = Class.forName(WIRES_CLASS).getMethod("link", ServerLevel.class, BlockPos.class, BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TVilas.LOGGER.info("Vilas: o Industrial não tem fio aéreo ({}): postes soltos", e.toString());
			}
			this.link = m;
		}

		@Override
		public List<BlockState> pole() {
			return List.of(pole.defaultBlockState());
		}

		@Override
		public int height() {
			return height;
		}

		@Override
		public boolean industrial() {
			return true;
		}

		@Override
		public List<BlockState> sources() {
			Block solar = BuiltInRegistries.BLOCK.getOptional(SOLAR).orElse(null), battery = BuiltInRegistries.BLOCK.getOptional(BATTERY).orElse(null);
			if (solar == null || battery == null || link == null) return List.of();
			return List.of(solar.defaultBlockState(), solar.defaultBlockState(), battery.defaultBlockState());
		}

		@Override
		public boolean link(ServerLevel level, BlockPos a, BlockPos b) {
			if (link == null) return false;
			try {
				return (Boolean) link.invoke(null, level, a, b);
			} catch (ReflectiveOperationException | RuntimeException e) {
				TVilas.LOGGER.warn("Vilas: fio aéreo falhou entre {} e {}: {}", a, b, e.toString());
				return false;
			}
		}
	}
}
