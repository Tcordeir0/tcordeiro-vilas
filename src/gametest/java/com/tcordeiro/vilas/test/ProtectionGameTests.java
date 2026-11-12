package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Terrenos protegidos «por fora» (0.1.1): líquidos, fogo, lava a acender, explosões e pistões vindos de fora de um terreno não
 * mexem nele; o dono continua a poder usar tudo isso dentro do seu terreno.
 * <p>
 * Montagem comum: o terreno do dono cobre as posições relativas x = 5..7 (z = 1..7); a coluna x = 4 é a fronteira por fora.
 */
public class ProtectionGameTests {
	static final int LOT_X0 = 5;

	static Lot lotEast(GameTestHelper h, Village v, ServerPlayer owner) {
		return TestUtil.lot(h, v, owner, LOT_X0, 1, 7, 7);
	}

	static void drop(GameTestHelper h, Lot lot) {
		VillageData.get(h.getLevel()).lots.remove(lot);
	}

	/**
	 * Um canal de pedra na linha z: só as células (4, 1, z), (5, 1, z) e (6, 1, z) estão livres (paredes em z±1 e nas pontas
	 * x = 3 e x = 7), para os líquidos não fugirem para os testes vizinhos.
	 */
	static void channel(GameTestHelper h, int z) {
		for (int x = 3; x <= 7; x++) {
			h.setBlock(new BlockPos(x, 1, z - 1), Blocks.STONE);
			h.setBlock(new BlockPos(x, 1, z + 1), Blocks.STONE);
		}
		h.setBlock(new BlockPos(3, 1, z), Blocks.STONE);
		h.setBlock(new BlockPos(7, 1, z), Blocks.STONE);
	}

	static void liquidStaysOut(GameTestHelper h, Block liquid, int wait) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(6, 1, 1));
		Lot lot = lotEast(h, v, owner);
		channel(h, 4);
		h.setBlock(new BlockPos(4, 1, 4), liquid); // nascente FORA do terreno, encostada à fronteira
		h.runAfterDelay(wait, () -> {
			h.assertBlockPresent(liquid, new BlockPos(4, 1, 4));
			h.assertBlockNotPresent(liquid, new BlockPos(5, 1, 4));
			h.assertBlockNotPresent(liquid, new BlockPos(6, 1, 4));
			drop(h, lot);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void lavaFromOutsideDoesNotFlowIn(GameTestHelper h) {
		liquidStaysOut(h, Blocks.LAVA, 120); // lava: 30 ticks por bloco
	}

	@GameTest(maxTicks = 100)
	public void waterFromOutsideDoesNotFlowIn(GameTestHelper h) {
		liquidStaysOut(h, Blocks.WATER, 40); // água: 5 ticks por bloco
	}

	/** Explode no centro do bloco relativo (x, 1, z), com raio 2 (parte vidro a 1 bloco sempre, não chega aos testes vizinhos). */
	static void explode(GameTestHelper h, net.minecraft.world.entity.Entity source, int x, int z) {
		Vec3 c = Vec3.atCenterOf(h.absolutePos(new BlockPos(x, 1, z)));
		h.getLevel().explode(source, c.x, c.y, c.z, 2.0f, Level.ExplosionInteraction.TNT);
	}

	@GameTest
	public void explosionsOutsideSpareTheLot(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		ServerPlayer stranger = TestUtil.playerAt(h, new BlockPos(1, 1, 7));
		Lot lot = lotEast(h, v, owner);
		BlockPos in = new BlockPos(5, 1, 4), out = new BlockPos(3, 1, 4);

		// TNT sem dono (acesa por redstone) ao lado do terreno: parte o de fora, não o de dentro; as decorações (suporte de
		// armadura) do terreno também ficam, as de fora não
		h.setBlock(in, Blocks.GLASS);
		h.setBlock(out, Blocks.GLASS);
		var standIn = h.spawn(EntityTypes.ARMOR_STAND, new BlockPos(6, 1, 6));
		var standOut = h.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		explode(h, null, 4, 4);
		h.assertBlockNotPresent(Blocks.GLASS, out);
		h.assertBlockPresent(Blocks.GLASS, in);
		h.assertTrue(standIn.isAlive(), "o suporte de armadura do terreno fica");
		h.assertTrue(!standOut.isAlive(), "controlo: o de fora parte-se");
		standIn.discard();

		// TNT acesa por um estranho
		h.setBlock(out, Blocks.GLASS);
		explode(h, new PrimedTnt(h.getLevel(), 0, 0, 0, stranger), 4, 4);
		h.assertBlockNotPresent(Blocks.GLASS, out);
		h.assertBlockPresent(Blocks.GLASS, in);

		// creeper (acendido de propósito por alguém): nem dentro do terreno parte blocos
		Creeper creeper = h.spawn(EntityTypes.CREEPER, new BlockPos(2, 1, 1));
		BlockPos in2 = new BlockPos(7, 1, 4);
		h.setBlock(in2, Blocks.GLASS);
		explode(h, creeper, 6, 4);
		h.assertBlockPresent(Blocks.GLASS, in);
		h.assertBlockPresent(Blocks.GLASS, in2);
		creeper.discard();
		drop(h, lot);
		h.succeed();
	}

	@GameTest
	public void fireFromOutsideDoesNotSpreadIn(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		Lot lot = lotEast(h, v, owner);
		ServerLevel level = h.getLevel();
		int radius = level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, level.getServer());
		try {
			for (int z = 3; z <= 5; z++) {
				h.setBlock(new BlockPos(3, 1, z), Blocks.OAK_PLANKS); // fora (controlo: estas ardem)
				h.setBlock(new BlockPos(5, 1, z), Blocks.OAK_PLANKS); // dentro
			}
			BlockPos fire = new BlockPos(4, 1, 4);
			for (int i = 0; i < 300; i++) {
				h.setBlock(fire, Blocks.FIRE);
				h.getBlockState(fire).tick(level, h.absolutePos(fire), RandomSource.create(i));
			}
			boolean outsideBurned = false;
			for (int z = 3; z <= 5; z++) {
				h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 1, z));
				if (!h.getBlockState(new BlockPos(3, 1, z)).is(Blocks.OAK_PLANKS)) outsideBurned = true;
				for (int y = 1; y <= 5; y++)
					for (int x = LOT_X0; x <= 7; x++) h.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, y, z));
			}
			h.assertTrue(outsideBurned, "controlo: o fogo de fora queimou as tábuas de fora");
		} finally {
			level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, radius, level.getServer());
			clearArea(h);
			drop(h, lot);
		}
		h.succeed();
	}

	@GameTest
	public void lavaOutsideDoesNotIgniteTheLot(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		Lot lot = lotEast(h, v, owner);
		ServerLevel level = h.getLevel();
		int radius = level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, level.getServer());
		try {
			for (int z = 3; z <= 5; z++) {
				h.setBlock(new BlockPos(3, 1, z), Blocks.OAK_PLANKS);
				h.setBlock(new BlockPos(5, 1, z), Blocks.OAK_PLANKS);
			}
			// o «randomTick» de uma lava em (4, 1, 4), fora do terreno, muitas vezes (só olha à volta, não precisa da lava lá)
			BlockPos lava = h.absolutePos(new BlockPos(4, 1, 4));
			for (int i = 0; i < 300; i++) Fluids.LAVA.defaultFluidState().randomTick(level, lava, RandomSource.create(i));
			boolean outsideLit = false;
			for (int z = 3; z <= 5; z++) {
				if (h.getBlockState(new BlockPos(3, 2, z)).is(Blocks.FIRE)) outsideLit = true;
				for (int y = 1; y <= 3; y++)
					for (int x = LOT_X0; x <= 7; x++) h.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, y, z));
			}
			h.assertTrue(outsideLit, "controlo: a lava acendeu fogo fora do terreno");
		} finally {
			level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, radius, level.getServer());
			clearArea(h);
			drop(h, lot);
		}
		h.succeed();
	}

	/** Tira fogo e tábuas do teste (para o fogo não continuar depois). */
	static void clearArea(GameTestHelper h) {
		for (int x = 0; x <= 8; x++)
			for (int y = 1; y <= 5; y++)
				for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
	}

	static void piston(GameTestHelper h, BlockPos rel, boolean sticky) {
		h.setBlock(rel, (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
	}

	@GameTest(maxTicks = 60)
	public void pistonOutsideCannotPushInOrPullOut(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		Lot lot = lotEast(h, v, owner);
		// empurrar: pistão em (3,1,6) virado a leste, tábua em (4,1,6) -> iria para (5,1,6), dentro do terreno
		piston(h, new BlockPos(3, 1, 6), false);
		h.setBlock(new BlockPos(4, 1, 6), Blocks.OAK_PLANKS);
		h.setBlock(new BlockPos(2, 1, 6), Blocks.REDSTONE_BLOCK);
		// puxar: pistão pegajoso em (3,1,2), estende (cabeça em (4,1,2), fora); depois puxa a tábua de (5,1,2), dentro
		piston(h, new BlockPos(3, 1, 2), true);
		h.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);
		h.runAfterDelay(6, () -> {
			h.setBlock(new BlockPos(5, 1, 2), Blocks.OAK_PLANKS);
			h.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		});
		h.runAfterDelay(14, () -> {
			h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 6));
			h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(5, 1, 6));
			h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 1, 2));
			h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 2));
			drop(h, lot);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void dispenserOutsideDoesNotShootIntoTheLot(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		Lot lot = lotEast(h, v, owner);
		BlockPos disp = new BlockPos(4, 1, 2);
		h.setBlock(disp, Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.EAST));
		var be = (net.minecraft.world.level.block.entity.DispenserBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(disp));
		be.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET));
		h.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
		h.runAfterDelay(12, () -> {
			h.assertBlockNotPresent(Blocks.LAVA, new BlockPos(5, 1, 2));
			h.assertTrue(be.getItem(0).is(net.minecraft.world.item.Items.LAVA_BUCKET), "o balde continua no ejetor");
			drop(h, lot);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void ownerStillUsesLavaPistonsAndTntInsideTheLot(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(0, 1, 0));
		Lot lot = TestUtil.lot(h, v, owner, 2, 1, 7, 7); // o terreno inclui a nascente, o pistão e a TNT

		// TNT (sem dono, ou acesa pelo dono) dentro do terreno parte o vidro do terreno
		h.setBlock(new BlockPos(5, 1, 6), Blocks.GLASS);
		h.setBlock(new BlockPos(7, 1, 6), Blocks.GLASS);
		explode(h, null, 6, 6);
		h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(5, 1, 6));
		h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 1, 6));
		h.setBlock(new BlockPos(5, 1, 6), Blocks.GLASS);
		explode(h, new PrimedTnt(h.getLevel(), 0, 0, 0, owner), 6, 6);
		h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(5, 1, 6));

		// lava dentro do terreno escorre normalmente
		channel(h, 4);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.LAVA);
		// pistão dentro do terreno empurra e o pegajoso puxa
		piston(h, new BlockPos(3, 1, 2), true);
		h.setBlock(new BlockPos(4, 1, 2), Blocks.OAK_PLANKS);
		h.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);
		h.runAfterDelay(6, () -> {
			h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 1, 2)); // empurrou
			h.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		});
		h.runAfterDelay(14, () -> h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 2))); // puxou de volta
		h.runAfterDelay(120, () -> {
			h.assertBlockPresent(Blocks.LAVA, new BlockPos(5, 1, 4));
			h.assertBlockPresent(Blocks.LAVA, new BlockPos(6, 1, 4));
			drop(h, lot);
			h.succeed();
		});
	}
}
