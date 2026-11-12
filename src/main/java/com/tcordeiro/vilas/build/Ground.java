package com.tcordeiro.vilas.build;

import com.tcordeiro.vilas.logic.WallRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * O que as obras dos construtores podem tocar. REGRA DE SEGURANÇA: só se põe um bloco onde há ar, erva, flores ou uma camada de
 * neve ({@link WallRules#mayReplace}), nunca dentro de um terreno, e só com o chunk carregado. Nada do que já lá está (blocos de
 * jogador, casas, baús) é partido ou substituído.
 */
public final class Ground {
	private Ground() {}

	public static boolean grassOrFlower(BlockState s) {
		return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SMALL_FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
				|| s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.SHORT_DRY_GRASS) || s.is(Blocks.TALL_DRY_GRASS)
				|| s.is(Blocks.BUSH);
	}

	/** REGRA DE SEGURANÇA no mundo: pode pôr-se aqui um bloco de uma obra? */
	public static boolean mayBuild(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
		boolean inLot = VillageManager.lotAt(level, pos.getX(), pos.getZ()) != null;
		BlockState s = level.getBlockState(pos);
		return WallRules.mayReplace(s.isAir(), grassOrFlower(s), s.is(Blocks.SNOW), inLot);
	}

	/** Chão natural (terra, erva, areia, pedra, terracota, cascalho, neve) — não um bloco de jogador nem uma rua. */
	public static boolean natural(BlockState s) {
		return !s.is(Blocks.DIRT_PATH) && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
				|| s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE)
				|| s.is(Blocks.STONE));
	}

	public static boolean street(BlockState s) {
		return s.is(Blocks.DIRT_PATH);
	}

	/** Ar, erva, flores ou neve: o que uma obra pode ocupar (e o que se atravessa à procura do chão). */
	static boolean free(BlockState s) {
		return s.isAir() || grassOrFlower(s) || s.is(Blocks.SNOW);
	}

	/** Até quantos blocos acima do centro da vila se procura o chão por baixo de copas, telhados e tetos. */
	public static final int ABOVE_CENTER = 6;

	/**
	 * O primeiro bloco livre por cima do chão em (x, z), perto da altura {@code refY} (o centro da vila): começa no mais baixo
	 * entre o mapa de alturas e {@code refY} + {@link #ABOVE_CENTER} e desce pelo ar, erva, flores e neve até ao chão (assim não
	 * fica em cima de copas de árvores nem de tetos). Se esse ponto já for sólido (encosta acima do centro), usa o mapa de alturas.
	 */
	public static BlockPos surface(ServerLevel level, int x, int z, int refY) {
		int hm = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos p = new BlockPos(x, Math.min(hm, refY + ABOVE_CENTER), z);
		if (!free(level.getBlockState(p))) p = new BlockPos(x, hm, z);
		while (p.getY() > level.getMinY() && free(level.getBlockState(p.below()))) p = p.below();
		return p;
	}

	/** Jogadores (não espectadores) dentro da vila e a menos de {@code radius} blocos de {@code pos}: estão a ver a obra. */
	public static boolean watched(ServerLevel level, Village v, BlockPos pos, int radius) {
		double r2 = (double) radius * radius;
		for (var p : VillageManager.playersIn(level, v))
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5) <= r2) return true;
		return false;
	}
}
