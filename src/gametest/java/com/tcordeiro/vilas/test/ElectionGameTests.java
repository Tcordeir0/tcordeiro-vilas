package com.tcordeiro.vilas.test;

import java.util.Arrays;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.life.Care;
import com.tcordeiro.vilas.life.Election;
import com.tcordeiro.vilas.life.Festa;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.Market;
import com.tcordeiro.vilas.life.Stage;
import com.tcordeiro.vilas.life.TownData;
import com.tcordeiro.vilas.life.election.Mandate;
import com.tcordeiro.vilas.life.election.VoteBonus;
import com.tcordeiro.vilas.life.election.VoteItems;
import com.tcordeiro.vilas.life.election.VoteRules;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

/**
 * 0.6 eleições — Votação de 3 dias, desempate, e TODOS os bónus do voto: reputação (uma vez por eleição), bolo e broche (itens, nunca
 * dinheiro), −5 % do apoiador (da tesouraria, com tetos, só no mandato), as 4 promessas e o pedido «!» exclusivo. Nos 3 modos (com
 * os mods, -PnoCubcoin, -PnoMods). As mutações estão em {@code tools/mutation_test.py} (filtro «0.6 eleições»).
 */
public class ElectionGameTests {
	static final String MAYOR_NAME = "Agricultor";

	/** Uma vila de teste com um Prefeito e dois candidatos (agricultor e pedreiro). */
	static Village town(GameTestHelper h, Villager[] out) {
		Village v = TownGameTests.village(h);
		Villager mayor = TownGameTests.villager(h, new BlockPos(6, 1, 6), VilasProfessions.MAYOR);
		v.civic.mayor = mayor.getUUID();
		out[0] = TownGameTests.villager(h, new BlockPos(5, 1, 2), VillagerProfession.FARMER);
		out[1] = TownGameTests.villager(h, new BlockPos(2, 1, 5), VillagerProfession.MASON);
		Life.relist(v);
		return v;
	}

	/** Uma eleição aberta hoje, à mão, com os dois candidatos (TAX e GUARDS). */
	static TownData.Election ballot(Village v, long day, Villager a, Villager b) {
		TownData.Election e = v.civic.life.town.election;
		e.day = day;
		e.last = day;
		e.candidates.clear();
		e.votes.clear();
		e.candidates.add(new TownData.Candidate(a.getUUID(), MAYOR_NAME, TownRules.TAX));
		e.candidates.add(new TownData.Candidate(b.getUUID(), "Pedreiro", TownRules.GUARDS));
		return e;
	}

	/** Um mandato em vigor, à mão: a proposta {@code policy} e os apoiadores. */
	static Mandate term(Village v, String policy, ServerPlayer... supporters) {
		TownData.Election e = v.civic.life.town.election;
		e.held++;
		e.policy = policy;
		e.mandate.begin(e.held, 0, policy, MAYOR_NAME);
		for (ServerPlayer p : supporters) e.mandate.supporters.add(p.getUUID());
		return e.mandate;
	}

	static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
		return VillageManager.count(p.getInventory(), item);
	}

	// ---------------------------------------------------------------- votação de 3 dias

	@GameTest(maxTicks = 60)
	public void votingStaysOpenThreeDaysAndIsCountedAtTheEndOfTheThird(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), b = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		TestUtil.lot(h, v, a, 0, 0, 3, 3);
		TestUtil.lot(h, v, b, 5, 5, 8, 8);
		long day = VillageEconomy.day(level);
		TownData.Election e = ballot(v, day, c[0], c[1]);
		h.assertTrue(e.open(day + 2), "no 3.º dia ainda se vota");
		h.assertFalse(e.open(day + 3), "no 4.º já não");
		h.assertTrue(Election.vote(a, v, 0).ok(), "vota");
		h.assertTrue(VoteRules.ticksLeft(e.day, level.getGameTime(), VillageEconomy.DAY) > 2 * VillageEconomy.DAY, "o quadro mostra quanto falta (mais de 2 dias)");
		h.assertTrue(VoteBonus.timeLeft(level, v).getContents() instanceof TranslatableContents, "texto de quanto falta");
		Election.daily(level, v, day + 1);
		Election.daily(level, v, day + 2);
		h.assertValueEqual(e.held, 0, "ainda não contou (o 3.º dia não acabou)");
		h.assertTrue(e.open(day + 2), "continua aberta");
		Election.daily(level, v, day + 3); // o 3.º dia acabou
		h.assertValueEqual(e.held, 1, "contou no fim do 3.º dia");
		h.assertValueEqual(e.policy, TownRules.TAX, "a proposta vencedora fica em vigor");
		h.assertFalse(Election.vote(b, v, 1).ok(), "depois de contar já não se vota");
		h.assertTrue(e.mandate.current(e.held), "começou o mandato");
		h.succeed();
	}

	@GameTest(maxTicks = 60)
	public void tieGoesToTheVillagersFavoriteThenToThePolicyInForce(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), b = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		TestUtil.lot(h, v, a, 0, 0, 3, 3);
		TestUtil.lot(h, v, b, 5, 5, 8, 8);
		long day = VillageEconomy.day(level);
		ballot(v, day, c[0], c[1]);
		v.civic.life.town.election.policy = TownRules.TAX; // sem a estima, ganharia a proposta em vigor (a do agricultor)
		Election.vote(a, v, 0);
		Election.vote(b, v, 1);
		v.civic.life.setOpinion(c[0].getUUID(), c[1].getUUID(), 0);
		v.civic.life.setOpinion(UUID.randomUUID(), c[1].getUUID(), 60); // os aldeões gostam mais do pedreiro
		h.assertValueEqual(Election.count(level, v), 1, "empate 1–1: ganha o mais querido pela vila");
		// empate também na estima: fica a proposta em vigor (agora GUARDS, a do pedreiro), mesmo que ele seja o 2.º da lista
		v.civic.life = new LifeData();
		Life.relist(v);
		TownData.Election e = v.civic.life.town.election;
		e.policy = TownRules.GUARDS;
		ballot(v, day, c[0], c[1]);
		e.votes.put(a.getUUID(), 0);
		e.votes.put(b.getUUID(), 1);
		h.assertValueEqual(Election.count(level, v), 1, "empate em tudo: a proposta em vigor");
		h.succeed();
	}

	// ---------------------------------------------------------------- reputação

	@GameTest(maxTicks = 60)
	public void votingGivesReputationOncePerElection(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), stranger = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		TestUtil.lot(h, v, a, 0, 0, 3, 3);
		long day = VillageEconomy.day(level);
		TownData.Election e = ballot(v, day, c[0], c[1]);
		int rep = v.reputationOf(a.getUUID());
		h.assertTrue(Election.vote(a, v, 0).ok(), "vota");
		h.assertValueEqual(v.reputationOf(a.getUUID()), rep + VoteRules.VOTE_REP, "votar dá reputação");
		e.votes.remove(a.getUUID()); // mesmo que o voto fosse apagado e repetido…
		h.assertTrue(Election.vote(a, v, 1).ok(), "volta a votar");
		h.assertValueEqual(v.reputationOf(a.getUUID()), rep + VoteRules.VOTE_REP, "…a reputação é só uma vez por eleição");
		h.assertFalse(Election.vote(stranger, v, 0).ok(), "sem terreno não vota");
		h.assertValueEqual(v.reputationOf(stranger.getUUID()), 0, "nem ganha reputação");
		Election.count(level, v);
		h.assertTrue(Election.open(level, v, day), "eleição nova");
		h.assertTrue(Election.vote(a, v, 0).ok(), "vota na nova");
		h.assertValueEqual(v.reputationOf(a.getUUID()), rep + 2 * VoteRules.VOTE_REP, "numa eleição nova volta a ganhar");
		h.succeed();
	}

	// ---------------------------------------------------------------- bolo e broche: itens, nunca dinheiro

	@GameTest(maxTicks = 60)
	public void supportersGetACakeEveryVoterThePinAndNobodyGetsMoney(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), b = TestUtil.playerAt(h, new BlockPos(7, 1, 7)),
				d = TestUtil.playerAt(h, new BlockPos(1, 1, 7));
		for (ServerPlayer p : new ServerPlayer[] {a, b, d}) p.getInventory().clearContent();
		TestUtil.lot(h, v, a, 0, 0, 2, 2);
		TestUtil.lot(h, v, b, 6, 6, 8, 8);
		TestUtil.lot(h, v, d, 0, 6, 2, 8);
		v.economy.treasury = 50_000;
		long day = VillageEconomy.day(level);
		ballot(v, day, c[0], c[1]);
		TestUtil.giveEmeralds(a, 1);
		Election.vote(a, v, 0);
		Election.vote(b, v, 1);
		Election.vote(d, v, 0);
		long treasury = v.economy.treasury, moneyA = TestUtil.money(a), moneyB = TestUtil.money(b);
		h.assertValueEqual(Election.count(level, v), 0, "ganha o agricultor (2–1)");
		for (ServerPlayer p : new ServerPlayer[] {a, b, d}) VoteBonus.deliver(level, v, p); // quem não estava na lista recebe agora
		h.assertValueEqual(count(a, Items.CAKE), 1, "o apoiador recebe um bolo");
		h.assertValueEqual(count(a, VoteItems.I_VOTED), 1, "e o broche");
		h.assertValueEqual(count(b, Items.CAKE), 0, "quem votou no vencido não recebe bolo");
		h.assertValueEqual(count(b, VoteItems.I_VOTED), 1, "mas recebe o broche");
		h.assertValueEqual(VoteBonus.deliver(level, v, a), 0, "entregue uma vez só");
		h.assertValueEqual(count(a, Items.CAKE), 1, "nada duplica");
		h.assertTrue(VoteBonus.supporter(v, a.getUUID()), "a é apoiador");
		h.assertFalse(VoteBonus.supporter(v, b.getUUID()), "b não é apoiador");
		h.assertValueEqual(v.economy.treasury, treasury, "a posse não mexe na tesouraria");
		h.assertValueEqual(TestUtil.money(a), moneyA, "nem dá dinheiro ao apoiador");
		h.assertValueEqual(TestUtil.money(b), moneyB, "nem aos outros");
		// o broche diz a vila e o eleito
		ItemStack pin = ItemStack.EMPTY;
		for (int i = 0; i < a.getInventory().getContainerSize(); i++) if (a.getInventory().getItem(i).is(VoteItems.I_VOTED)) pin = a.getInventory().getItem(i);
		var lore = pin.get(DataComponents.LORE);
		h.assertTrue(lore != null && lore.lines().size() >= 2, "o broche tem descrição");
		h.assertTrue(lore.lines().get(1).getContents() instanceof TranslatableContents t && Arrays.asList(t.getArgs()).contains(MAYOR_NAME),
				"o nome do eleito no broche");
		h.assertTrue(lore.lines().get(0).getContents() instanceof TranslatableContents t0 && Arrays.asList(t0.getArgs()).contains(v.center.getX()),
				"a vila (a Mesa) no broche");
		h.succeed();
	}

	// ---------------------------------------------------------------- −5 % do apoiador

	@GameTest(maxTicks = 60)
	public void supporterRebateComesFromTheTreasuryWithCapsOnlyDuringTheTerm(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		a.getInventory().clearContent();
		b.getInventory().clearContent();
		Mandate m = term(v, TownRules.TAX, a);
		long unit = VilasConfig.get().emerald_value_cents;
		var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD, 6), 99, 1, 0.05f);
		v.economy.treasury = 0;
		h.assertValueEqual(VoteBonus.onTrade(a, v, offer), 0L, "tesouraria vazia: nada (nunca dinheiro novo)");
		v.economy.treasury = 100_000;
		h.assertValueEqual(VoteBonus.onTrade(b, v, offer), 0L, "quem não votou no vencedor: nada");
		long before = v.economy.treasury, money = TestUtil.money(a), paid = 0;
		for (int i = 0; i < 20; i++) paid += VoteBonus.onTrade(a, v, offer);
		h.assertTrue(paid > 0, "o apoiador recebe 5 % de volta");
		h.assertTrue(paid <= VoteRules.supporterPlayerCap(TVilas.CUBCOIN ? 1 : unit), "teto por jogador: " + paid);
		h.assertTrue(m.spent <= VoteRules.SUPPORTER_BUDGET, "teto do mandato");
		h.assertValueEqual(before - v.economy.treasury, paid, "saiu tudo da tesouraria");
		h.assertValueEqual(TestUtil.money(a) - money, paid, "o jogador recebeu exatamente o que saiu");
		// pelo caminho real das trocas (onde já se aplica o reembolso da festa)
		Mandate fresh = term(v, TownRules.TAX, a);
		VillageEconomy.onVillagerTrade(a, h.absolutePos(new BlockPos(4, 1, 4)), offer);
		h.assertTrue(fresh.rebates.getOrDefault(a.getUUID(), 0L) + fresh.owed.getOrDefault(a.getUUID(), 0L) > 0, "a troca com o aldeão dá o desconto");
		// mandato acabou (houve outra eleição): mais nada
		Mandate old = term(v, TownRules.TAX, a);
		v.civic.life.town.election.held++;
		h.assertFalse(VoteBonus.supporter(v, a.getUUID()), "já não é apoiador");
		VoteBonus.onTrade(a, v, new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 1), 99, 1, 0.05f));
		h.assertValueEqual(old.rebates.getOrDefault(a.getUUID(), 0L) + old.owed.getOrDefault(a.getUUID(), 0L), 0L, "fora do mandato, nada");
		h.succeed();
	}

	// ---------------------------------------------------------------- promessa «mais festas»: convite VIP

	@GameTest(maxTicks = 60)
	public void vipInviteGivesAnExtraInsideTheFestaCaps(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		a.getInventory().clearContent();
		b.getInventory().clearContent();
		term(v, TownRules.FESTAS, a);
		v.economy.treasury = 100_000;
		long unit = VilasConfig.get().emerald_value_cents;
		var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 99, 1, 0.05f);
		LifeData.Festa f = v.civic.life.festa;
		Festa.forceActive = true;
		try {
			long before = v.economy.treasury, money = TestUtil.money(a) + TestUtil.money(b);
			for (int i = 0; i < 2; i++)
				for (ServerPlayer p : new ServerPlayer[] {a, b}) {
					Festa.onTrade(p, v, offer);
					VoteBonus.onTrade(p, v, offer);
				}
			long ga = f.rebates.getOrDefault(a.getUUID(), 0L) + f.owed.getOrDefault(a.getUUID(), 0L);
			long gb = f.rebates.getOrDefault(b.getUUID(), 0L) + f.owed.getOrDefault(b.getUUID(), 0L);
			h.assertTrue(ga > gb, "o convidado VIP recebe mais na festa: " + ga + " > " + gb);
			for (int i = 0; i < 12; i++) {
				Festa.onTrade(a, v, offer);
				VoteBonus.onTrade(a, v, offer);
			}
			long cap = LifeRules.festaPlayerCap(TVilas.CUBCOIN ? 1 : unit);
			h.assertTrue(f.rebates.getOrDefault(a.getUUID(), 0L) + f.owed.getOrDefault(a.getUUID(), 0L) <= cap, "o extra fica dentro do teto da festa");
			h.assertTrue(f.spent <= LifeRules.FESTA_BUDGET, "e do orçamento da festa");
			h.assertValueEqual(before - v.economy.treasury, TestUtil.money(a) + TestUtil.money(b) - money, "tudo sai da tesouraria");
			// apoiador de outra proposta: só o reembolso normal da festa (sem o extra VIP)
			term(v, TownRules.TAX, a);
			f.reset();
			Festa.onTrade(a, v, offer);
			VoteBonus.onTrade(a, v, offer);
			h.assertValueEqual(f.rebates.getOrDefault(a.getUUID(), 0L) + f.owed.getOrDefault(a.getUUID(), 0L),
					LifeRules.festaRebate(unit, 0, LifeRules.FESTA_BUDGET, v.economy.treasury + unit), "sem «mais festas» não há VIP");
		} finally {
			Festa.forceActive = null;
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- promessa «imposto menor»: −20 % no terreno

	@GameTest(maxTicks = 60)
	public void lotTaxCutOnlyForWhoVotedTheTaxPromiseDuringTheTerm(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		term(v, TownRules.TAX, a);
		h.assertValueEqual(VoteBonus.lotTax(v, a.getUUID(), 100), 80L, "−20 % para quem votou em «imposto menor»");
		h.assertValueEqual(VoteBonus.lotTax(v, b.getUUID(), 100), 100L, "os outros pagam tudo");
		// pelo caminho real: 2 camas × 5 dias
		a.getInventory().clearContent();
		if (TVilas.CUBCOIN) CubCoinTrade.give(a, 100);
		TestUtil.lotOverTest(h, v, a);
		EconomyGameTests.bed(h, new BlockPos(2, 1, 2));
		EconomyGameTests.bed(h, new BlockPos(6, 1, 2));
		long day = VillageEconomy.day(level) + 1000;
		VillageEconomy.daily(level, v, day);
		long before = v.economy.treasury, full = 2 * 5 * VilasConfig.get().bed_tax_cents;
		VillageEconomy.daily(level, v, day + 5);
		long cut = VoteRules.lotTax(full, true);
		if (TVilas.CUBCOIN) h.assertValueEqual(v.economy.treasury - before, cut, "cobrou só 80 %");
		else h.assertValueEqual(VillageEconomy.debt(v, a.getUUID()), cut, "deve só 80 %");
		term(v, TownRules.GUARDS, a);
		h.assertValueEqual(VoteBonus.lotTax(v, a.getUUID(), 100), 100L, "outra proposta: sem desconto");
		term(v, TownRules.TAX, b);
		h.assertValueEqual(VoteBonus.lotTax(v, a.getUUID(), 100), 100L, "mandato novo sem ele: sem desconto");
		h.killAllEntitiesOfClass(Villager.class);
		VillageData.get(level).lots.removeIf(l -> l.village.equals(v.id));
		h.succeed();
	}

	// ---------------------------------------------------------------- promessa «mais guardas»: o posto novo perto do terreno

	@GameTest(maxTicks = 60)
	public void newGuardPostGoesNearTheSupportersLot(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		Lot lotB = TestUtil.lot(h, v, b, 0, 0, 2, 2);
		Lot lotA = TestUtil.lot(h, v, a, 6, 6, 8, 8);
		term(v, TownRules.GUARDS, a);
		java.util.List<BlockPos> posts = new java.util.ArrayList<>(java.util.List.of(v.center));
		BlockPos post = VoteBonus.guardPost(level, v, posts, 2);
		h.assertTrue(post != null && lotA.contains(post.getX(), post.getZ()), "o posto novo fica no terreno do apoiador: " + post);
		h.assertFalse(lotB.contains(post.getX(), post.getZ()), "não no de quem não votou na proposta");
		h.assertValueEqual(posts.indexOf(post), 1, "é um dos postos ocupados");
		// pelo caminho real (salário da tesouraria): um golem fica de guarda lá
		v.economy.treasury = 100_000;
		h.spawn(EntityTypes.IRON_GOLEM, new BlockPos(4, 1, 2));
		h.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 6));
		Care.dailyGuards(level, v, Life.places(level, v), VillageEconomy.day(level) + 77);
		h.assertTrue(v.civic.life.guards.containsValue(post.asLong()), "um guarda no posto perto do terreno");
		term(v, TownRules.TAX, a);
		h.assertTrue(VoteBonus.guardPost(level, v, new java.util.ArrayList<>(), 2) == null, "outra proposta: nada");
		term(v, TownRules.GUARDS, b);
		BlockPos other = VoteBonus.guardPost(level, v, new java.util.ArrayList<>(), 2);
		h.assertTrue(other != null && lotB.contains(other.getX(), other.getZ()), "mandato novo: perto do terreno do novo apoiador");
		h.killAllEntitiesOfClass(IronGolem.class);
		h.succeed();
	}

	// ---------------------------------------------------------------- promessa «mercado maior»: banca própria

	@GameTest(maxTicks = 200)
	public void ownStallAtTheMarketWithTheSupportersNameAndNoTaxThere(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		TownGameTests.plaza(h, v);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 0)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 0));
		TestUtil.lot(h, v, a, 0, 0, 1, 1);
		TestUtil.lot(h, v, b, 7, 7, 8, 8);
		term(v, TownRules.MARKET, a);
		BlockPos plaza = h.absolutePos(new BlockPos(4, 1, 4));
		AABB box = new AABB(plaza).inflate(12);
		Market.forceActive = true;
		try {
			Life.tick(level, v);
			var own = level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> e.entityTags().contains(VoteBonus.OWN_STALL_TAG));
			h.assertValueEqual(own.size(), 1, "uma banca própria (só um apoiador)");
			Component name = own.get(0).getCustomName();
			h.assertTrue(name != null && name.getContents() instanceof TranslatableContents t && Arrays.asList(t.getArgs()).contains(a.getGameProfile().name()),
					"com o nome do apoiador");
			h.assertTrue(Stage.isStage(own.get(0)), "é cenário (apaga-se no fim)");
			h.assertValueEqual(VoteBonus.saleTax(level, v, plaza, a.getUUID(), 30), 0L, "as vendas dele lá não pagam taxa");
			h.assertValueEqual(VoteBonus.saleTax(level, v, plaza, b.getUUID(), 30), 30L, "as dos outros pagam");
			h.assertValueEqual(VoteBonus.saleTax(level, v, plaza.offset(60, 0, 0), a.getUUID(), 30), 30L, "longe da Praça paga");
			// pelo caminho real das vendas entre jogadores
			long t0 = v.economy.treasury + VillageEconomy.debt(v, a.getUUID());
			VillageEconomy.onPlayerSale(level, plaza, a.getUUID(), 10_000);
			h.assertValueEqual(v.economy.treasury + VillageEconomy.debt(v, a.getUUID()), t0, "venda na banca própria: sem taxa");
			long t1 = v.economy.treasury + VillageEconomy.debt(v, b.getUUID());
			VillageEconomy.onPlayerSale(level, plaza, b.getUUID(), 10_000);
			h.assertTrue(v.economy.treasury + VillageEconomy.debt(v, b.getUUID()) > t1, "os outros pagam a taxa de sempre");
		} finally {
			Market.forceActive = null;
		}
		Market.forceActive = false;
		try {
			Life.tick(level, v);
			h.assertValueEqual(VoteBonus.saleTax(level, v, plaza, a.getUUID(), 30), 30L, "fora do mercado paga");
		} finally {
			Market.forceActive = null;
		}
		h.runAfterDelay(2, () -> {
			h.assertValueEqual(level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> e.entityTags().contains(VoteBonus.OWN_STALL_TAG)).size(), 0,
					"no fim a banca desaparece");
			h.succeed();
		});
	}

	// ---------------------------------------------------------------- apoiador: pedido «!» exclusivo

	@GameTest(maxTicks = 60)
	public void exclusiveRequestOnlyForItsSupporterOncePerBatch(GameTestHelper h) {
		Villager[] c = new Villager[2];
		Village v = town(h, c);
		ServerLevel level = h.getLevel();
		ServerPlayer a = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), b = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		TestUtil.lot(h, v, a, 0, 0, 1, 1);
		v.economy.treasury = 1_000_000;
		VillagerRequests.tick(level, v);
		v.civic.batch.requests.clear(); // todos livres para o teste
		h.assertValueEqual(VoteBonus.reserveRequests(level, v), 0, "sem mandato não há exclusivos");
		Mandate m = term(v, TownRules.TAX, a);
		h.assertValueEqual(VoteBonus.reserveRequests(level, v), 1, "o apoiador ganha um pedido exclusivo");
		h.assertValueEqual(VoteBonus.reserveRequests(level, v), 0, "um por lote");
		UUID who = m.reserved.keySet().iterator().next();
		h.assertValueEqual(m.reserved.get(who), a.getUUID(), "é dele");
		Mission req = v.civic.batch.requests.get(who);
		h.assertTrue(req != null, "está nos pedidos da vila (com o «!»)");
		var no = VillagerRequests.deliver(b, who);
		h.assertFalse(no.ok(), "outro jogador não o entrega");
		h.assertTrue(no.message().getContents() instanceof TranslatableContents t && t.getKey().equals("tvilas.vote.reserved_other"), "porque é exclusivo");
		a.getInventory().add(new ItemStack(VillageManager.item(req.target()), req.count()));
		h.assertTrue(VillagerRequests.deliver(a, who).ok(), "o apoiador entrega");
		v.civic.life.town.election.held++; // o mandato acabou
		h.assertTrue(VoteBonus.mayTake(v, who, b.getUUID()), "fora do mandato o pedido é de todos");
		h.succeed();
	}
}
