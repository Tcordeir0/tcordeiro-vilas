package com.tcordeiro.vilas.worldgen;

import com.mojang.serialization.MapCodec;
import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Móveis do mod Móveis dentro das estruturas novas (Taverna e Praça), SEM duas versões de cada NBT.
 * <p>
 * Os ficheiros NBT são fixos e só têm blocos vanilla, escolhidos para servirem de «marcadores» que também ficam bem sozinhos:
 * <ul>
 * <li>escada de madeira (meia de baixo) = cadeira → {@code tmoveis:<madeira>_chair} virada para onde se senta;</li>
 * <li>cerca de madeira com placa de pressão de madeira por cima = mesa → {@code tmoveis:<madeira>_table} (a placa sai);</li>
 * <li>lanterna pendurada = lustre → {@code tmoveis:chandelier} aceso; o tronco descascado de carvalho por cima dele vira um
 * bloco de redstone (o lustre só acende com sinal);</li>
 * <li>duas camas lado a lado, azul-clara (esquerda) e ciano (direita) = cama de casal → as 4 partes de
 * {@code tmoveis:red_double_bed}.</li>
 * </ul>
 * Com o Móveis instalado, este processador (posto só nas peças da Taverna e da Praça) troca os marcadores pelos móveis, com
 * verniz natural para não envelhecerem à chuva; sem o Móveis devolve os blocos tal como estão. A rotação da peça é aplicada
 * depois pelo jogo (os móveis rodam como as escadas e as camas).
 */
public final class FurnitureProcessor implements StructureProcessor {
	public static final FurnitureProcessor INSTANCE = new FurnitureProcessor();
	public static final MapCodec<FurnitureProcessor> CODEC = MapCodec.unit(INSTANCE);

	public static final String WOOD_FINISH = "natural";
	/** Marcadores da cama de casal: lado esquerdo e direito. */
	public static final Block LEFT_BED = Blocks.BED.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE), RIGHT_BED = Blocks.BED.pick(net.minecraft.world.item.DyeColor.CYAN);
	static final String BED_COLOR = "red";

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, TVilas.id("furniture"), CODEC);
	}

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return CODEC;
	}

	@Override
	public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, BlockPos worldPos,
			StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
		if (current == null || !Mods.loaded(Mods.MOVEIS)) return current;
		BlockState out = swap(current.state());
		return out == null ? current : new StructureTemplate.StructureBlockInfo(current.pos(), out, out.hasBlockEntity() ? current.nbt() : null);
	}

	/** O móvel que substitui este marcador (null = não é marcador, fica como está). Público para os testes. */
	public static BlockState swap(BlockState s) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
		if (!id.getNamespace().equals("minecraft")) return null;
		String path = id.getPath();
		if (path.endsWith("_stairs") && s.hasProperty(BlockStateProperties.HALF)
				&& s.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
			Block chair = wooden(path.substring(0, path.length() - "_stairs".length()), "chair");
			if (chair == null) return null;
			Direction look = s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
			return with(with(chair.defaultBlockState(), "facing", look.getSerializedName()), "finish", WOOD_FINISH);
		}
		if (path.endsWith("_fence") && !path.contains("nether")) {
			Block table = wooden(path.substring(0, path.length() - "_fence".length()), "table");
			return table == null ? null : with(table.defaultBlockState(), "finish", WOOD_FINISH);
		}
		if (path.endsWith("_pressure_plate") && !path.contains("weighted") && !path.contains("stone")) return Blocks.AIR.defaultBlockState();
		if (s.is(Blocks.LANTERN) && s.getValue(BlockStateProperties.HANGING)) {
			Block lamp = moveis("chandelier");
			return lamp == null ? null : with(lamp.defaultBlockState(), "lit", "true");
		}
		if (s.is(Blocks.STRIPPED_OAK_WOOD)) return Blocks.REDSTONE_BLOCK.defaultBlockState();
		if (s.is(LEFT_BED) || s.is(RIGHT_BED)) {
			Block bed = moveis(BED_COLOR + "_double_bed");
			if (bed == null) return null;
			BlockState b = bed.defaultBlockState();
			b = with(b, "facing", s.getValue(BlockStateProperties.HORIZONTAL_FACING).getSerializedName());
			b = with(b, "part", s.getValue(BlockStateProperties.BED_PART).getSerializedName());
			return with(b, "side", s.is(LEFT_BED) ? "left" : "right");
		}
		return null;
	}

	static Block moveis(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(Mods.MOVEIS, path)).orElse(null);
	}

	/** {@code tmoveis:<madeira>_<peça>}; madeiras que o Móveis não tem (ex.: arenito do deserto) ficam em carvalho. */
	static Block wooden(String wood, String piece) {
		Block b = moveis(wood + "_" + piece);
		return b != null ? b : moveis("oak_" + piece);
	}

	/** Muda uma propriedade pelo nome (se o bloco a tiver). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState s, String name, String value) {
		Property p = s.getBlock().getStateDefinition().getProperty(name);
		if (p == null) return s;
		var v = p.getValue(value);
		return v.isPresent() ? s.setValue(p, (Comparable) v.get()) : s;
	}
}
