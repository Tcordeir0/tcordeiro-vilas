package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ajudas dos testes. Os testes correm lado a lado no mesmo mundo (5 blocos entre eles), por isso as vilas de teste têm raio
 * {@link #RADIUS} (não se tocam) e os terrenos de teste ficam dentro da área do próprio teste.
 */
final class TestUtil {
	private TestUtil() {}

	static final int RADIUS = 6;

	/** Esquece vilas e terrenos que tenham ficado de testes anteriores nesta posição do mundo. */
	static void clean(GameTestHelper h) {
		VillageData d = VillageData.get(h.getLevel());
		BlockPos c = h.absolutePos(new BlockPos(4, 1, 4));
		d.villages.removeIf(v -> Math.abs(v.center.getX() - c.getX()) <= 12 && Math.abs(v.center.getZ() - c.getZ()) <= 12);
		d.lots.removeIf(l -> l.area.overlaps(new com.tcordeiro.vilas.logic.LotRules.Area(c.getX() - 12, c.getZ() - 12, c.getX() + 12, c.getZ() + 12)));
	}

	/** Chão de pedra 9×9 (y=0) — onde os testes põem coisas. */
	static void floor(GameTestHelper h, int half) {
		for (int x = 4 - half; x <= 4 + half; x++)
			for (int z = 4 - half; z <= 4 + half; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
	}

	/** Uma vila de teste (sem Mesa) com centro em (4,1,4) e raio {@link #RADIUS}. */
	static Village village(GameTestHelper h) {
		clean(h);
		floor(h, 4);
		return VillageManager.found(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), RADIUS);
	}

	static ServerPlayer playerAt(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.setPos(v.x, v.y, v.z);
		return p;
	}

	/** Põe com o item, como um jogador a clicar no topo do bloco de baixo (só BlockItem.place — sem o UseBlockCallback). */
	static InteractionResult placeWithItem(GameTestHelper h, ServerPlayer player, ItemStack stack, BlockPos rel) {
		BlockPos abs = h.absolutePos(rel);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP, abs.below(), false);
		return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
	}

	/** Usa o item no topo do bloco de baixo pelo caminho completo do servidor (passa pelo UseBlockCallback, como um clique real). */
	static InteractionResult useOnTop(GameTestHelper h, ServerPlayer player, ItemStack stack, BlockPos rel) {
		BlockPos abs = h.absolutePos(rel);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP, abs.below(), false);
		return player.gameMode.useItemOn(player, h.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
	}

	static Lot lotOverTest(GameTestHelper h, Village v, ServerPlayer owner) {
		BlockPos a = h.absolutePos(new BlockPos(1, 0, 1)), b = h.absolutePos(new BlockPos(7, 0, 7));
		var area = new com.tcordeiro.vilas.logic.LotRules.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()),
				Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
		Lot lot = new Lot(java.util.UUID.randomUUID(), v.id, area, owner.getUUID(), owner.getGameProfile().name(), new java.util.HashSet<>());
		VillageData.get(h.getLevel()).lots.add(lot);
		return lot;
	}
}
