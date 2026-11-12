package com.tcordeiro.vilas.build;

import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;

/**
 * O golem da vila, montado à vista: o construtor põe 4 blocos de ferro em T e uma abóbora esculpida por cima — e o golem
 * acorda (como quando um jogador o faz). Se o jogo não o fizer nascer (ex.: um braço ficou bloqueado), os blocos da obra saem e
 * o golem nasce no mesmo sítio.
 */
public final class GolemWork {
	private GolemWork() {}

	/** Um sítio livre perto do centro (3×3×3 de ar por cima de chão natural, fora de terrenos), ou null. */
	static BlockPos site(ServerLevel level, Village v) {
		int max = Math.max(2, Math.min(16, v.radius - 1));
		for (int r = 2; r <= max; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					BlockPos top = Ground.surface(level, v.center.getX() + dx, v.center.getZ() + dz, v.center.getY());
					if (!v.covers(top.getX(), top.getZ()) || !Ground.natural(level.getBlockState(top.below()))) continue;
					if (free(level, top)) return top;
				}
		return null;
	}

	static boolean free(ServerLevel level, BlockPos base) {
		for (int x = -1; x <= 1; x++)
			for (int y = 0; y <= 2; y++)
				for (int z = -1; z <= 1; z++) {
					BlockPos p = base.offset(x, y, z);
					if (!level.getBlockState(p).isAir() || VillageManager.lotAt(level, p.getX(), p.getZ()) != null) return false;
				}
		return true;
	}

	/** A obra do golem (ou null se não há sítio). */
	public static Project create(ServerLevel level, Village v) {
		BlockPos base = site(level, v);
		if (base == null) return null;
		Project p = new Project(Project.Kind.GOLEM, base);
		var iron = Blocks.IRON_BLOCK.defaultBlockState();
		p.pending.add(new Project.Placement(base, iron));
		p.pending.add(new Project.Placement(base.above(), iron));
		p.pending.add(new Project.Placement(base.above().east(), iron));
		p.pending.add(new Project.Placement(base.above().west(), iron));
		p.pending.add(new Project.Placement(base.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH)));
		p.total = p.pending.size();
		return p;
	}

	/** Acabou: se o golem não acordou sozinho, tira os blocos DESTA obra (só ferro e abóbora) e fá-lo nascer. */
	static void finish(ServerLevel level, Village v, Project p) {
		BlockPos b = p.site;
		BlockPos[] ours = {b, b.above(), b.above().east(), b.above().west(), b.above(2)};
		boolean left = false;
		for (BlockPos q : ours) {
			var s = level.getBlockState(q);
			if (s.is(Blocks.IRON_BLOCK) || s.is(Blocks.CARVED_PUMPKIN)) left = true;
		}
		boolean golemNear = !level.getEntitiesOfClass(IronGolem.class, new net.minecraft.world.phys.AABB(b).inflate(3)).isEmpty();
		if (left || !golemNear) {
			for (BlockPos q : ours) {
				var s = level.getBlockState(q);
				if (s.is(Blocks.IRON_BLOCK) || s.is(Blocks.CARVED_PUMPKIN)) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
			}
			if (!golemNear) {
				IronGolem g = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (g != null) {
					g.snapTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0, 0);
					level.addFreshEntity(g);
				}
			}
		}
	}
}
