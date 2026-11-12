package com.tcordeiro.vilas.build;

import java.util.List;

import com.tcordeiro.vilas.logic.WallRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A muralha da Cidade, coluna a coluna ({@link WallRules#plan}). Cada coluna assenta no chão natural; se a coluna planeada cai
 * em cima de um bloco de jogador, de um terreno ou de uma rua (as ruas ficam como portões), tenta 1 e 2 blocos para fora
 * (contorna) e, se nenhum servir, salta essa coluna. Os blocos são verificados outra vez ao pô-los ({@link Ground#mayBuild}).
 */
public final class WallWork {
	private WallWork() {}

	public static Project create(Village v) {
		Project p = new Project(Project.Kind.WALL, v.center);
		p.total = WallRules.plan(v.center.getX(), v.center.getZ(), WallRules.half(v.radius)).size();
		return p;
	}

	static BlockState material(ServerLevel level, Village v) {
		var biome = level.getBiome(v.center).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (biome.contains("desert") || biome.contains("badlands")) return Blocks.CUT_SANDSTONE.defaultBlockState();
		if (biome.contains("snow") || biome.contains("ice") || biome.contains("frozen")) return Blocks.POLISHED_DIORITE.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	/** Planeia a próxima coluna que dê (as que não dão contam como saltadas). Devolve false se a muralha já está toda planeada. */
	static boolean expand(ServerLevel level, Village v, Project p) {
		List<WallRules.Column> plan = WallRules.plan(v.center.getX(), v.center.getZ(), WallRules.half(v.radius));
		p.total = plan.size();
		BlockState stone = material(level, v);
		while (p.nextColumn < plan.size()) {
			WallRules.Column c = plan.get(p.nextColumn);
			if (!level.isLoaded(new BlockPos(c.x(), v.center.getY(), c.z()))) return false; // espera que o chunk carregue
			p.nextColumn++;
			BlockPos base = columnBase(level, v, c);
			if (base == null) {
				p.skipped++;
				continue;
			}
			int h = WallRules.height(c);
			for (int y = 0; y < h; y++) p.pending.add(new Project.Placement(base.above(y), stone));
			if (c.tower()) p.pending.add(new Project.Placement(base.above(h), Blocks.LANTERN.defaultBlockState()));
			return true;
		}
		return false;
	}

	/** Onde assenta a coluna (com os desvios para fora), ou null se tem de ser saltada. */
	static BlockPos columnBase(ServerLevel level, Village v, WallRules.Column c) {
		for (int k = 0; k < WallRules.OUTWARD_TRIES; k++) {
			int x = c.x() + c.nx() * k, z = c.z() + c.nz() * k;
			if (VillageManager.lotAt(level, x, z) != null) continue; // nunca em terrenos
			BlockPos top = Ground.surface(level, x, z, v.center.getY());
			BlockState ground = level.getBlockState(top.below());
			if (Ground.street(ground)) return null; // rua: fica um portão
			if (!Ground.natural(ground)) continue; // bloco de jogador (ou casa): contorna
			if (!Ground.mayBuild(level, top)) continue;
			return top;
		}
		return null;
	}
}
