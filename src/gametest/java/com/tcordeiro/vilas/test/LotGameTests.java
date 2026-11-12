package com.tcordeiro.vilas.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.tcordeiro.vilas.logic.LotRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Terrenos: o comprado bloqueia outros jogadores (partir e pôr) e não bloqueia o dono nem os amigos; as máquinas do Industrial
 * (que perguntam ao PlayerBlockBreakEvents.BEFORE com um jogador falso com o UUID do dono) também obedecem.
 */
public class LotGameTests {
	static boolean mayBreak(GameTestHelper h, net.minecraft.world.entity.player.Player p, BlockPos rel) {
		BlockPos abs = h.absolutePos(rel);
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(h.getLevel(), p, abs, h.getLevel().getBlockState(abs), null);
	}

	@GameTest
	public void lotBlocksStrangerButNotOwnerOrFriend(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(3, 1, 3));
		ServerPlayer friend = TestUtil.playerAt(h, new BlockPos(5, 1, 3));
		ServerPlayer stranger = TestUtil.playerAt(h, new BlockPos(4, 1, 6));
		h.assertTrue(!stranger.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "o estranho não é operador");
		Lot lot = TestUtil.lotOverTest(h, v, owner);
		BlockPos wall = new BlockPos(4, 1, 4);
		h.setBlock(wall, Blocks.OAK_PLANKS);

		// partir
		h.assertTrue(!mayBreak(h, stranger, wall), "o estranho não parte");
		h.assertTrue(!stranger.gameMode.destroyBlock(h.absolutePos(wall)), "partir de verdade também falha");
		h.assertBlockPresent(Blocks.OAK_PLANKS, wall);
		h.assertTrue(mayBreak(h, owner, wall), "o dono parte");
		h.assertTrue(!mayBreak(h, friend, wall), "antes de ser amigo não parte");
		lot.friends.add(friend.getUUID());
		h.assertTrue(mayBreak(h, friend, wall), "o amigo parte");

		// pôr (caminho completo do clique: UseBlockCallback)
		BlockPos spot = new BlockPos(2, 1, 6);
		TestUtil.useOnTop(h, stranger, new ItemStack(Items.COBBLESTONE), spot);
		h.assertBlockNotPresent(Blocks.COBBLESTONE, spot);
		TestUtil.useOnTop(h, friend, new ItemStack(Items.COBBLESTONE), spot);
		h.assertBlockPresent(Blocks.COBBLESTONE, spot);
		BlockPos spot2 = new BlockPos(6, 1, 6);
		TestUtil.useOnTop(h, owner, new ItemStack(Items.COBBLESTONE), spot2);
		h.assertBlockPresent(Blocks.COBBLESTONE, spot2);
		TestUtil.useOnTop(h, stranger, new ItemStack(Items.LAVA_BUCKET), new BlockPos(6, 1, 2));
		h.assertBlockNotPresent(Blocks.LAVA, new BlockPos(6, 1, 2));

		// amigo removido deixa de poder
		lot.friends.remove(friend.getUUID());
		h.assertTrue(!mayBreak(h, friend, wall), "amigo removido já não parte");
		VillageData.get(h.getLevel()).lots.remove(lot);
		h.succeed();
	}

	@GameTest
	public void industrialMachinesAskForTheOwner(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(3, 1, 3));
		Lot lot = TestUtil.lotOverTest(h, v, owner);
		BlockPos ore = new BlockPos(4, 1, 4);
		h.setBlock(ore, Blocks.IRON_ORE);
		// o Industrial pergunta com FakePlayer.get(level, perfil do dono) — mesmo UUID do dono
		var ownersMachine = FakePlayer.get(h.getLevel(), new GameProfile(owner.getUUID(), "[tindustrial]"));
		var strangersMachine = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "[tindustrial]"));
		var ownerless = FakePlayer.get(h.getLevel());
		h.assertTrue(mayBreak(h, ownersMachine, ore), "a máquina do dono trabalha no terreno");
		h.assertTrue(!mayBreak(h, strangersMachine, ore), "a máquina de outro não");
		h.assertTrue(!mayBreak(h, ownerless, ore), "máquina sem dono também não");
		h.assertTrue(mayBreak(h, strangersMachine, new BlockPos(4, 1, 12)), "fora do terreno trabalha normalmente");
		VillageData.get(h.getLevel()).lots.remove(lot);
		h.succeed();
	}

	@GameTest
	public void buyLotPaysAndRespectsRules(GameTestHelper h) {
		// vila «de papel» longe dos outros testes (a compra só precisa da vila e da posição do jogador)
		BlockPos center = h.absolutePos(new BlockPos(4, 1, 4)).offset(20000, 0, 0);
		VillageData d = VillageData.get(h.getLevel());
		d.villages.removeIf(x -> x.center.distManhattan(center) < 200);
		d.lots.removeIf(l -> l.area.overlaps(new LotRules.Area(center.getX() - 100, center.getZ() - 100, center.getX() + 100, center.getZ() + 100)));
		Village v = VillageManager.found(h.getLevel(), center, 64);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.getInventory().clearContent();
		p.setPos(center.getX() + 2.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(!VillageManager.buyLot(p).ok(), "a praça da Prefeitura não se vende");
		p.setPos(center.getX() + 20.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(!VillageManager.buyLot(p).ok(), "sem esmeraldas não compra");
		int price = VillageManager.lotPrice(v, p.getUUID());
		h.assertValueEqual(price, LotRules.price(0, 0), "preço de aldeia sem reputação");
		TestUtil.giveEmeralds(p, price + 1);
		var r = VillageManager.buyLot(p);
		h.assertTrue(r.ok(), "compra");
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 1L, "pagou o preço (em esmeraldas, ou em CubCoins com o CubCoin)");
		Lot lot = VillageManager.lotAt(h.getLevel(), center.getX() + 20, center.getZ());
		h.assertTrue(lot != null && lot.owner.equals(p.getUUID()), "o terreno é dele");
		h.assertValueEqual(lot.area.maxX() - lot.area.minX() + 1, 16, "16 de largura");
		TestUtil.giveEmeralds(p, 64);
		p.setPos(center.getX() + 25.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(!VillageManager.buyLot(p).ok(), "não compra por cima de outro terreno");
		p.setPos(center.getX() + 60.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(!VillageManager.buyLot(p).ok(), "não compra fora da vila");
		// a aldeia só vende 4 terrenos
		int bought = 1;
		for (int dz : new int[] {-40, 40, -20}) {
			p.setPos(center.getX() + 0.5, center.getY(), center.getZ() + dz + 0.5);
			if (VillageManager.buyLot(p).ok()) bought++;
		}
		h.assertValueEqual(bought, 4, "4 terrenos");
		p.setPos(center.getX() - 30.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(!VillageManager.buyLot(p).ok(), "o 5.º terreno não está à venda numa aldeia");
		d.villages.remove(v);
		d.lots.removeIf(l -> l.village.equals(v.id));
		h.succeed();
	}
}
