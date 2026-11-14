package com.tcordeiro.vilas.test;

import java.util.UUID;

import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.Talk;
import com.tcordeiro.vilas.life.memory.Chronicle;
import com.tcordeiro.vilas.life.memory.Lore;
import com.tcordeiro.vilas.life.memory.Memories;
import com.tcordeiro.vilas.life.memory.Rescue;
import com.tcordeiro.vilas.life.projects.Community;
import com.tcordeiro.vilas.life.projects.Projects;
import com.tcordeiro.vilas.logic.LoreRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * 0.6.0 — Memória dos aldeões, crónica, «você me salvou» (carta e presente) e obras comunitárias com doações, nos 3 modos. As
 * regras de economia (uma vez por dia/semana, só o que falta, reputação com teto, nada de dinheiro, nunca partir blocos) têm
 * mutações em {@code tools/mutation_test.py}.
 */
public class Vilas060LoreGameTests {
	static Village village(GameTestHelper h) {
		Village v = TestUtil.village(h);
		v.civic.life = new LifeData();
		Life.relist(v);
		return v;
	}

	@GameTest(maxTicks = 100)
	public void memoryKeepsSixStrongest(GameTestHelper h) {
		Village v = village(h);
		UUID vi = UUID.randomUUID(), p = UUID.randomUUID();
		h.assertTrue(Memories.remember(v, vi, Memories.SAVED, p, "Talys", 1), "o resgate entra");
		for (int i = 0; i < 8; i++) Memories.remember(v, vi, Memories.LETTER, UUID.randomUUID(), "x" + i, 1 + i);
		h.assertValueEqual(Memories.of(v, vi).size(), LoreRules.MEMORY_SLOTS, "no máximo 6 recordações");
		h.assertTrue(Memories.about(v, vi, p) != null && Memories.about(v, vi, p).kind().equals(Memories.SAVED), "o resgate (forte) não sai por cartas (fracas)");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void rescueRewardsOncePerDayAndSendsOneGift(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager vi = h.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
		vi.setVillagerData(vi.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(2));
		Zombie z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 6));
		z.setTarget(vi);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(5, 1, 5));
		Life.relist(v);
		long money = TestUtil.money(p);
		int rep0 = v.reputationOf(p.getUUID());
		int gossip0 = vi.getGossips().getReputation(p.getUUID(), t -> t == GossipType.MINOR_POSITIVE);
		Rescue.onDeath(z, level.damageSources().playerAttack(p));
		h.assertValueEqual(v.reputationOf(p.getUUID()) - rep0, LoreRules.SAVE_REP, "reputação na vila");
		h.assertTrue(vi.getGossips().getReputation(p.getUUID(), t -> t == GossipType.MINOR_POSITIVE) > gossip0, "o aldeão gosta mais do jogador");
		h.assertTrue(Memories.about(v, vi.getUUID(), p.getUUID()) != null, "lembra-se de quem o salvou");
		h.assertTrue(v.civic.life.lore.chronicle.stream().anyMatch(e -> e.key().equals("saved")), "fica na crónica");
		int books = VillageManager.count(p.getInventory(), Items.WRITTEN_BOOK);
		boolean mail = !v.civic.life.town.mail.isEmpty();
		h.assertTrue(books == 1 || mail, "a carta chegou (na mão ou pelo Carteiro)");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.EMERALD), 0, "o presente nunca é dinheiro");
		// outra vez no mesmo dia: lembra-se, mas sem mais reputação nem outra carta
		Zombie z2 = h.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 6));
		z2.setTarget(vi);
		Rescue.onDeath(z2, level.damageSources().playerAttack(p));
		h.assertValueEqual(v.reputationOf(p.getUUID()) - rep0, LoreRules.SAVE_REP, "reputação uma vez por dia");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.WRITTEN_BOOK), books, "uma carta por semana");
		h.assertValueEqual(TestUtil.money(p), money, "nada de dinheiro");
		// um monstro que não atacava ninguém não conta
		Zombie z3 = h.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 7));
		int saves = Rescue.saves;
		Rescue.onDeath(z3, level.damageSources().playerAttack(p));
		h.assertValueEqual(Rescue.saves, saves, "sem resgate");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void chronicleFromFactsAndBookCostsABook(GameTestHelper h) {
		Village v = village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		Talk.fact(h.getLevel(), v, null, "festa");
		Talk.fact(h.getLevel(), v, null, "gift", "Talys", "farmer");
		h.assertTrue(v.civic.life.lore.chronicle.stream().anyMatch(e -> e.key().equals("festa")), "a festa entra na crónica");
		h.assertTrue(v.civic.life.lore.chronicle.stream().noneMatch(e -> e.key().equals("gift")), "um presente não");
		h.assertTrue(!Chronicle.book(p, v).ok(), "sem livro não há cópia");
		p.getInventory().add(new ItemStack(Items.BOOK, 1));
		h.assertTrue(Chronicle.book(p, v).ok(), "com um livro, sim");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.BOOK), 0, "o livro foi gasto");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.WRITTEN_BOOK), 1, "e saiu o Livro da Vila");
		for (int i = 0; i < LoreRules.CHRONICLE_MAX + 20; i++) Chronicle.add(v, i, "news.newcomer");
		h.assertValueEqual(v.civic.life.lore.chronicle.size(), LoreRules.CHRONICLE_MAX, "a crónica tem teto");
		h.succeed();
	}

	static Community fountain(GameTestHelper h, Village v) {
		Community c = new Community(Community.FOUNTAIN, h.absolutePos(new BlockPos(2, 1, 2)), 0, 0);
		c.needs.putAll(Projects.needs(Community.FOUNTAIN, 0));
		v.civic.life.lore.project = c;
		return c;
	}

	@GameTest(maxTicks = 100)
	public void donationTakesOnlyWhatIsMissingAndNeverMoney(GameTestHelper h) {
		Village v = village(h);
		Community c = fountain(h, v);
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(5, 1, 4));
		a.getInventory().add(new ItemStack(Items.STONE_BRICKS, 10));
		long treasury = v.economy.treasury, money = TestUtil.money(a);
		h.assertTrue(Projects.donate(a, v).ok(), "doou");
		h.assertValueEqual(c.have.get("minecraft:stone_bricks"), 10, "10 de 16");
		b.getInventory().add(new ItemStack(Items.STONE_BRICKS, 64));
		b.getInventory().add(new ItemStack(Items.STONE_BRICK_WALL, 2));
		Projects.donate(b, v);
		h.assertValueEqual(VillageManager.count(b.getInventory(), Items.STONE_BRICKS), 58, "só tirou os 6 que faltavam");
		h.assertTrue(c.state == Community.COLLECTING, "falta a lanterna");
		h.assertTrue(!Projects.donate(b, v).ok(), "sem nada do que falta: não doa");
		a.getInventory().add(new ItemStack(Items.LANTERN, 3));
		Projects.donate(a, v);
		h.assertValueEqual(VillageManager.count(a.getInventory(), Items.LANTERN), 2, "uma lanterna só");
		h.assertTrue(c.state == Community.BUILDING, "está tudo: começa a obra");
		h.assertTrue(v.extra.projects.stream().anyMatch(p -> p.kind == Project.Kind.COMMUNITY), "o Construtor tem a obra na fila");
		h.assertValueEqual(v.economy.treasury, treasury, "a tesouraria não mexe");
		h.assertValueEqual(TestUtil.money(a), money, "ninguém ganha dinheiro");
		h.assertTrue(v.reputationOf(a.getUUID()) <= LoreRules.DONATION_REP_DAY, "reputação com teto");
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void projectNeverBreaksBlocksAndLeavesAPlaque(GameTestHelper h) {
		Village v = village(h);
		Community c = fountain(h, v);
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(8, 1, 8));
		h.setBlock(new BlockPos(4, 1, 2), Blocks.COBBLESTONE); // um bloco de alguém onde ia o rebordo
		c.have.putAll(c.needs);
		c.donors.put(a.getUUID(), a.getGameProfile().name());
		c.given.put(a.getUUID(), 19);
		Projects.start(h.getLevel(), v, c);
		for (int i = 0; i < 3 && !v.extra.projects.isEmpty(); i++) Builders.overnight(h.getLevel(), v);
		h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 2));
		h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(2, 1, 2));
		h.assertBlockPresent(Blocks.LANTERN, new BlockPos(4, 3, 4));
		h.assertTrue(v.civic.life.lore.project == null && v.civic.life.lore.projectsDone == 1, "obra acabada");
		BlockPos sign = Projects.lastSign;
		h.assertTrue(sign != null && h.getLevel().getBlockEntity(sign) instanceof SignBlockEntity s
				&& s.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(1).getString().equals(a.getGameProfile().name()), "a placa tem o nome de quem doou");
		h.assertTrue(v.civic.life.lore.chronicle.stream().anyMatch(e -> e.key().equals("project_done")), "fica na crónica");
		h.succeed();
	}

	static Lore unused() {
		return null;
	}

	static long day(GameTestHelper h) {
		return VillageEconomy.day(h.getLevel());
	}
}
