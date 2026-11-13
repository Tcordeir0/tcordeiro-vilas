package com.tcordeiro.vilas.test;

import java.util.List;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.life.Election;
import com.tcordeiro.vilas.life.Festa;
import com.tcordeiro.vilas.life.Fishing;
import com.tcordeiro.vilas.life.Helper;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.LifeVillager;
import com.tcordeiro.vilas.life.Mail;
import com.tcordeiro.vilas.life.Market;
import com.tcordeiro.vilas.life.Play;
import com.tcordeiro.vilas.life.Stage;
import com.tcordeiro.vilas.life.Task;
import com.tcordeiro.vilas.life.Tasks;
import com.tcordeiro.vilas.life.TownData;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/**
 * 0.5.0 — Carteiro, mercado semanal, eleições, pescador, crianças a brincar, missão da festa, ajudante e o «!» em balão: um teste por
 * mecânica, nos 3 modos (com os mods, sem o CubCoin, sem nenhum mod — sem o Móveis não há Carteiro e nada parte). As regras de dinheiro
 * (tetos, tesouraria, uma vez por jogador) e de segurança (terrenos alheios) têm mutações em {@code tools/mutation_test.py}.
 */
public class TownGameTests {
	static Village village(GameTestHelper h) {
		Village v = TestUtil.village(h);
		v.civic.life = new LifeData();
		Life.relist(v);
		return v;
	}

	static void plaza(GameTestHelper h, Village v) {
		BlockPos a = h.absolutePos(new BlockPos(0, 0, 0)), b = h.absolutePos(new BlockPos(8, 0, 8));
		v.civic.put("plaza", new com.tcordeiro.vilas.logic.LotRules.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()),
				Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ())));
		Life.rescan(v);
	}

	static Villager villager(GameTestHelper h, BlockPos at, net.minecraft.resources.ResourceKey<VillagerProfession> prof) {
		Villager vi = h.spawn(EntityTypes.VILLAGER, at);
		if (prof != null) vi.setVillagerData(vi.getVillagerData().withProfession(h.getLevel().registryAccess(), prof).withLevel(2));
		vi.setVillagerXp(1);
		return vi;
	}

	static Block moveis(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(Mods.MOVEIS, path)).orElse(null);
	}

	// ---------------------------------------------------------------- 1) Carteiro

	@GameTest(maxTicks = 400)
	public void postmanDeliversIntoTheMailboxAndTheStampGoesToTheTreasury(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Block mailbox = moveis("mailbox");
		if (mailbox == null) { // sem o Móveis: não há Carteiro, nem o bloco, nem nada a partir
			h.assertFalse(Mail.available(), "sem o Móveis não há Carteiro");
			h.assertFalse(VilasProfessions.registered(VilasProfessions.POSTMAN), "a profissão nem existe");
			h.succeed();
			return;
		}
		ServerPlayer from = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), to = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		Lot lot = TestUtil.lot(h, v, to, 5, 5, 8, 8);
		h.setBlock(new BlockPos(7, 1, 6), mailbox);
		BlockPos box = h.absolutePos(new BlockPos(7, 1, 6));
		h.assertTrue(box.equals(Mail.mailboxIn(level, lot)), "acha a caixa de correio no terreno do destinatário");
		h.assertTrue(Mail.recipients(level, v, from.getUUID()).containsKey(to.getUUID()), "o destinatário aparece na lista");
		h.assertFalse(Mail.recipients(level, v, to.getUUID()).containsKey(to.getUUID()), "ninguém manda cartas a si mesmo");
		from.getInventory().clearContent();
		TestUtil.giveEmeralds(from, 1);
		long treasury = v.economy.treasury, money = TestUtil.money(from);
		var r = Mail.post(from, v, to.getUUID(), to.getGameProfile().name(), box.asLong(), new java.util.ArrayList<>(List.of(new ItemStack(Items.DIRT, 3))));
		h.assertTrue(r.ok(), "carta aceite: " + r.message().getString());
		long stamp = TVilas.CUBCOIN ? TownRules.STAMP_CENTS : 0;
		h.assertValueEqual(v.economy.treasury - treasury, stamp, "o selo vai TODO para a tesouraria (grátis sem o CubCoin)");
		h.assertValueEqual(money - TestUtil.money(from), stamp, "o jogador pagou só o selo");
		Villager pm = villager(h, new BlockPos(2, 1, 2), VilasProfessions.POSTMAN);
		Life.relist(v);
		LifeVillager.Memo m = ((LifeVillager) pm).tvilas$memo();
		h.assertFalse(Mail.tryDeliver(level, v, Life.places(level, v), pm, m, level.getGameTime(), 13000), "de noite não entrega");
		h.assertTrue(Mail.tryDeliver(level, v, Life.places(level, v), pm, m, level.getGameTime(), 3000), "nas horas de trabalho vai entregar");
		h.assertTrue(Mail.carrying(pm) > 0, "leva a carta");
		h.succeedWhen(() -> {
			h.assertTrue(level.getBlockEntity(box) instanceof Container c && c.countItem(Items.DIRT) == 3, "a terra está na caixa de correio");
			h.assertTrue(v.civic.life.town.mail.isEmpty(), "a carta saiu da bolsa");
		});
	}

	@GameTest(maxTicks = 60)
	public void mailComesBackAfterThreeTriesAndItemsAreNeverLost(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer from = TestUtil.playerAt(h, new BlockPos(1, 1, 1));
		from.getInventory().clearContent();
		TestUtil.giveEmeralds(from, 2);
		BlockPos nowhere = h.absolutePos(new BlockPos(6, 1, 6)); // sem caixa
		UUID to = UUID.randomUUID();
		for (int i = 0; i < TownRules.MAIL_PER_PLAYER; i++)
			h.assertTrue(Mail.post(from, v, to, "Ana", nowhere.asLong(), new java.util.ArrayList<>(List.of(new ItemStack(Items.APPLE, 2)))).ok(), "carta " + i);
		var no = Mail.post(from, v, to, "Ana", nowhere.asLong(), new java.util.ArrayList<>(List.of(new ItemStack(Items.APPLE))));
		h.assertFalse(no.ok(), "teto de cartas por entregar por jogador");
		TownData.Parcel p0 = v.civic.life.town.mail.get(0);
		p0.tries = TownRules.MAIL_TRIES;
		if (Mail.available()) {
			Villager pm = villager(h, new BlockPos(2, 1, 2), VilasProfessions.POSTMAN);
			Mail.tryDeliver(level, v, Life.places(level, v), pm, ((LifeVillager) pm).tvilas$memo(), level.getGameTime(), 3000);
			h.assertTrue(p0.returned, "à 3.ª tentativa falhada volta para quem a mandou");
		} else {
			p0.returned = true; // sem o Móveis não há Carteiro; a regra de devolver está nos testes JUnit
		}
		h.assertValueEqual(Mail.returnedFor(v, from.getUUID()), 1, "1 carta devolvida à espera");
		int apples = from.getInventory().countItem(Items.APPLE);
		h.assertTrue(Mail.collectReturned(from, v).ok(), "levanta o correio devolvido");
		h.assertValueEqual(from.getInventory().countItem(Items.APPLE) - apples, 2, "as 2 maçãs voltaram (nunca se perdem)");
		h.assertFalse(Mail.collectReturned(from, v).ok(), "não se levanta duas vezes");
		h.succeed();
	}

	// ---------------------------------------------------------------- 2) mercado semanal

	@GameTest(maxTicks = 60)
	public void marketRebateOnlyAtTheStallsFromTheTreasuryWithCaps(GameTestHelper h) {
		Village v = village(h);
		plaza(h, v);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD, 6), 99, 1, 0.05f);
		long unit = com.tcordeiro.vilas.VilasConfig.get().emerald_value_cents;
		BlockPos stall = h.absolutePos(new BlockPos(4, 1, 4)), far = stall.offset(40, 0, 0);
		Market.forceActive = true;
		try {
			v.economy.treasury = 0;
			h.assertValueEqual(Market.onTrade(p, v, stall, offer), 0L, "tesouraria vazia: nada (nunca dinheiro novo)");
			v.economy.treasury = 100_000;
			h.assertValueEqual(Market.onTrade(p, v, far, offer), 0L, "longe da Praça (fora das bancas): nada");
			long before = v.economy.treasury, money = TestUtil.money(p), paid = 0;
			for (int i = 0; i < 10; i++) paid += Market.onTrade(p, v, stall, offer);
			h.assertTrue(paid > 0, "nas bancas há reembolso");
			h.assertTrue(paid <= TownRules.marketPlayerCap(false, TVilas.CUBCOIN ? 1 : unit), "teto por jogador: " + paid);
			h.assertValueEqual(before - v.economy.treasury, paid, "saiu tudo da tesouraria");
			h.assertValueEqual(TestUtil.money(p) - money, paid, "o jogador recebeu exatamente o que saiu");
			h.assertTrue(v.civic.life.town.market.spent <= TownRules.MARKET_BUDGET, "teto por mercado");
		} finally {
			Market.forceActive = null;
		}
		Market.forceActive = false;
		try {
			h.assertValueEqual(Market.onTrade(p, v, stall, offer), 0L, "fora do mercado, nada");
		} finally {
			Market.forceActive = null;
		}
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void marketSetsUpStallsWithVendorsAndPacksUpAtTheEnd(GameTestHelper h) {
		Village v = village(h);
		plaza(h, v);
		ServerLevel level = h.getLevel();
		TestUtil.playerAt(h, new BlockPos(4, 1, 0));
		Villager baker = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		Life.relist(v);
		Market.forceActive = true;
		Life.tick(level, v);
		int stalls = Market.stalls(v);
		Market.forceActive = null;
		h.assertTrue(stalls >= 1, "montou as bancas: " + stalls);
		h.runAfterDelay(20, () -> {
			Market.forceActive = true;
			for (int i = 0; i < 5; i++) Life.tick(level, v);
			Task t = Tasks.get(baker);
			Market.forceActive = false;
			h.assertTrue(t != null && t.kind == Task.Kind.MARKET, "o agricultor foi para a banca: " + (t == null ? null : t.kind));
			Life.tick(level, v); // acabou o mercado
			Market.forceActive = null;
			h.assertValueEqual(Market.stalls(v), 0, "desmontou as bancas");
			h.assertTrue(Tasks.get(baker) == null || Tasks.get(baker).kind != Task.Kind.MARKET, "o vendedor voltou ao trabalho");
			h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(16),
					Stage::isStage).isEmpty(), "nenhuma banca ficou no mundo");
			h.succeed();
		});
	}

	// ---------------------------------------------------------------- 3) eleições

	@GameTest(maxTicks = 60)
	public void onlyLotOwnersVoteOnceAndTheWinnerBecomesMayorWithoutMoney(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), stranger = TestUtil.playerAt(h, new BlockPos(2, 1, 1));
		TestUtil.lot(h, v, owner, 1, 1, 3, 3);
		Villager mayor = villager(h, new BlockPos(6, 1, 6), VilasProfessions.MAYOR);
		v.civic.mayor = mayor.getUUID();
		Villager farmer = villager(h, new BlockPos(5, 1, 2), VillagerProfession.FARMER), mason = villager(h, new BlockPos(2, 1, 5), VillagerProfession.MASON);
		Life.relist(v);
		long day = VillageEconomy.day(level);
		h.assertTrue(Election.open(level, v, day), "abre a votação com candidatos");
		TownData.Election e = v.civic.life.town.election;
		e.candidates.clear();
		e.candidates.add(new TownData.Candidate(farmer.getUUID(), "Agricultor", TownRules.TAX));
		e.candidates.add(new TownData.Candidate(mason.getUUID(), "Pedreiro", TownRules.GUARDS));
		v.economy.treasury = 50_000;
		long treasury = v.economy.treasury, money = TestUtil.money(owner);
		double tax = VillageEconomy.taxRate(v).percent();
		h.assertFalse(Election.vote(stranger, v, 0).ok(), "sem terreno não vota");
		h.assertTrue(Election.vote(owner, v, 0).ok(), "o dono de terreno vota");
		h.assertFalse(Election.vote(owner, v, 1).ok(), "uma vez só");
		h.assertValueEqual(e.votes.size(), 1, "um voto contado");
		h.assertValueEqual(Election.count(level, v), 0, "ganhou o mais votado");
		h.assertValueEqual(e.policy, TownRules.TAX, "a proposta dele fica em vigor");
		h.assertTrue(VillageManager.isMayor(farmer), "o vencedor é o Prefeito");
		h.assertTrue(mayor.getVillagerData().profession().is(VillagerProfession.FARMER), "o Prefeito de antes fica com o ofício do novo");
		h.assertValueEqual(v.civic.mayor, farmer.getUUID(), "a vila sabe quem é o Prefeito");
		h.assertValueEqual(VillageEconomy.taxRate(v).percent(), Math.max(0, tax - TownRules.TAX_CUT), "imposto menor: −1 ponto (nunca negativo)");
		h.assertValueEqual(v.economy.treasury, treasury, "as eleições não mexem na tesouraria");
		h.assertValueEqual(TestUtil.money(owner), money, "nem no dinheiro dos jogadores");
		h.assertFalse(Election.vote(owner, v, 0).ok(), "depois de contar, a votação fecha");
		h.succeed();
	}

	// ---------------------------------------------------------------- 4) pescador

	@GameTest(maxTicks = 300)
	public void fisherCastsHisOwnBobberAndItNeverStaysBehind(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		for (int x = 5; x <= 7; x++)
			for (int z = 1; z <= 7; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
		Villager fisher = villager(h, new BlockPos(1, 1, 4), VillagerProfession.FISHERMAN); // a 3 blocos da margem: anda até lá
		Life.relist(v);
		LifeVillager.Memo m = ((LifeVillager) fisher).tvilas$memo();
		h.assertTrue(Fishing.find(level, fisher.blockPosition()) != null, "acha a margem");
		h.assertTrue(Fishing.tryFish(level, v, Life.places(level, v), fisher, m, level.getGameTime(), 3000), "vai pescar nas horas de trabalho");
		Task t0 = Tasks.get(fisher);
		h.succeedWhen(() -> {
			Task t = Tasks.get(fisher);
			h.assertTrue(t != null && t.kind == Task.Kind.FISH && t.prop != null && t.prop.isAlive(), "lançou a boia (mostrador do Vilas, não o anzol do jogo): fim="
					+ t0.endReason + " chegou=" + t0.arrived + " alvo=" + t0.target + " está=" + fisher.blockPosition() + " margem=" + m.fishSpot + " esperado=" + h.absolutePos(new BlockPos(4, 1, 4)) + " " + level.getBlockState(h.absolutePos(new BlockPos(4, 1, 4)))
					+ "/" + level.getBlockState(h.absolutePos(new BlockPos(4, 0, 4))) + "/" + level.getFluidState(h.absolutePos(new BlockPos(5, 0, 4))));
			h.assertTrue(com.tcordeiro.vilas.life.Props.holding(fisher), "cana na mão (item de cena)");
			var bobber = t.prop;
			Tasks.end(level, fisher, t, "teste");
			h.assertTrue(bobber.isRemoved(), "a boia some com a tarefa");
		});
	}

	@GameTest(maxTicks = 40)
	public void eachFishRestocksOneFishTradeWithADailyCap(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager fisher = villager(h, new BlockPos(2, 1, 4), VillagerProfession.FISHERMAN);
		var offers = fisher.getOffers();
		offers.clear();
		MerchantOffer fish = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKED_COD, 6), 4, 1, 0.05f);
		MerchantOffer other = new MerchantOffer(new ItemCost(Items.STRING, 20), new ItemStack(Items.EMERALD), 4, 1, 0.05f);
		offers.add(other);
		offers.add(fish);
		for (int i = 0; i < 4; i++) {
			fish.increaseUses();
			other.increaseUses();
		}
		h.assertTrue(Fishing.caught(level, fisher), "um peixe repõe a troca de peixe");
		h.assertValueEqual(fish.getUses(), 0, "a venda de peixe voltou a ter stock");
		h.assertValueEqual(other.getUses(), 4, "a compra de fio não mexe (só peixe)");
		int restocked = 1;
		for (int i = 0; i < 20; i++) {
			fish.increaseUses();
			if (Fishing.caught(level, fisher)) restocked++;
		}
		h.assertValueEqual(restocked, TownRules.CATCHES_PER_DAY, "no máximo " + TownRules.CATCHES_PER_DAY + " por dia");
		h.succeed();
	}

	// ---------------------------------------------------------------- 5) crianças

	@GameTest(maxTicks = 200)
	public void kidsPlayHideAndSeekOnlyWithSomeoneWatching(GameTestHelper h) {
		Village v = village(h);
		plaza(h, v);
		ServerLevel level = h.getLevel();
		Villager a = villager(h, new BlockPos(3, 1, 3), null), b = villager(h, new BlockPos(5, 1, 5), null);
		a.setAge(-24000);
		b.setAge(-24000);
		Life.relist(v);
		Play.startForTest(level, v, List.of(a, b), true);
		h.assertTrue(Play.game(v) != null && Play.game(v).startsWith("hide:2"), "começa o esconde-esconde: " + Play.game(v));
		Task ta = Tasks.get(a), tb = Tasks.get(b);
		h.assertTrue(ta != null && tb != null && ta.kind == Task.Kind.PLAY && tb.kind == Task.Kind.PLAY, "os dois a brincar");
		h.assertTrue(("count".equals(ta.role) && "hide".equals(tb.role)) || ("count".equals(tb.role) && "hide".equals(ta.role)), "um conta, o outro esconde-se");
		h.assertTrue(ta.deadline > level.getGameTime() && ta.deadline <= level.getGameTime() + TownRules.HIDE_TICKS, "o jogo tem prazo");
		// LOD: só brincam com um jogador a 48 blocos da Praça (os testes correm lado a lado: conta-se quem está mesmo perto)
		BlockPos c = Life.places(level, v).plaza().center;
		boolean watched = level.players().stream().anyMatch(pl -> !pl.isSpectator() && pl.blockPosition().closerThan(c, LifeRules.FULL)
				&& v.distSqr(pl.getBlockX(), pl.getBlockZ()) <= (double) (v.radius + LifeRules.REDUCED) * (v.radius + LifeRules.REDUCED));
		Life.tick(level, v);
		h.assertValueEqual(Play.game(v) != null, watched, "o jogo continua só se alguém estiver a ver (perto: " + watched + ")");
		if (!watched) h.assertTrue(Tasks.get(a) == null && Tasks.get(b) == null, "e o vanilla volta");
		Play.stopForTest(level, v);
		Play.startForTest(level, v, List.of(a, b), false);
		h.assertTrue(Play.game(v) != null && Play.game(v).startsWith("tag"), "a apanhada também");
		Play.stopForTest(level, v);
		h.succeed();
	}

	// ---------------------------------------------------------------- 6) missão da festa

	@GameTest(maxTicks = 60)
	public void festaMissionIsTemporaryPaysOnceAndKeepsTheBatch(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		v.civic.life = new LifeData();
		plaza(h, v);
		ServerLevel level = h.getLevel();
		VillageManager.fillMissions(level, v);
		List<Long> ids = v.missions.stream().map(Mission::id).toList();
		long batch = v.civic.batch.id;
		long today = VillageEconomy.day(level);
		h.assertTrue(Festa.schedule(level, v, today + 1, "level"), "festa marcada");
		Mission d = v.civic.life.town.decor;
		h.assertTrue(d != null && d.kind() == com.tcordeiro.vilas.logic.MissionRules.Kind.DECORATE, "4.ª missão: decorar a festa");
		h.assertValueEqual(v.missions.stream().map(Mission::id).toList(), ids, "as 3 do lote não mudam");
		h.assertValueEqual(v.civic.batch.id, batch, "o lote de 4 dias não muda");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		h.assertTrue(VillageManager.accept(p, v, VillageManager.FESTA_SLOT).ok(), "aceita a missão da festa");
		Block b = VillageManager.block(d.target());
		for (int i = 0; i < d.count(); i++) {
			BlockPos rel = new BlockPos(i % 7, 1, 1 + i / 7);
			h.setBlock(rel, b);
			VillageManager.onPlaced(p, h.absolutePos(rel), b.defaultBlockState());
		}
		long money = TestUtil.money(p);
		var r = VillageManager.deliver(p, v, VillageManager.FESTA_SLOT);
		h.assertTrue(r.ok(), "entregue: " + r.message().getString());
		h.assertTrue(TestUtil.money(p) > money, "pago pela vila");
		h.assertFalse(VillageManager.deliver(p, v, VillageManager.FESTA_SLOT).ok(), "uma vez por jogador");
		// a festa acaba: a missão sai do quadro
		Festa.finish(level, v);
		h.assertTrue(v.civic.life.town.decor == null, "depois da festa a missão some");
		h.assertTrue(VillageManager.missionAt(v, VillageManager.FESTA_SLOT) == null, "a 4.ª casa fica vazia");
		h.succeed();
	}

	// ---------------------------------------------------------------- 7) ajudante

	@GameTest(maxTicks = 80)
	public void helperIsPaidToTheTreasuryCappedAndNeverEntersOtherLots(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer me = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), other = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		ServerPlayer third = TestUtil.playerAt(h, new BlockPos(4, 1, 7)), fourth = TestUtil.playerAt(h, new BlockPos(4, 1, 6));
		for (ServerPlayer p : List.of(me, other, third, fourth)) {
			p.getInventory().clearContent();
			TestUtil.giveEmeralds(p, 3);
		}
		Villager a = villager(h, new BlockPos(2, 1, 2), VillagerProfession.FARMER), b = villager(h, new BlockPos(6, 1, 6), VillagerProfession.MASON);
		Villager c = villager(h, new BlockPos(4, 1, 5), VillagerProfession.CLERIC);
		Life.relist(v);
		long treasury = v.economy.treasury, money = TestUtil.money(me);
		var r = Helper.hire(me, level, v, a);
		h.assertTrue(r.ok(), "contratado: " + r.message().getString());
		long paid = money - TestUtil.money(me);
		h.assertTrue(paid > 0, "pagou");
		h.assertValueEqual(v.economy.treasury - treasury, paid, "o preço vai TODO para a tesouraria");
		v.civic.life.town.hired.remove(me.getUUID()); // mesmo sem o teto do dia: um de cada vez
		h.assertFalse(Helper.hire(me, level, v, b).ok(), "um ajudante por jogador");
		v.civic.life.town.hired.put(me.getUUID(), VillageEconomy.day(level));
		h.assertFalse(Helper.hire(other, level, v, a).ok(), "o ajudante de outro não se contrata");
		h.assertTrue(Helper.hire(other, level, v, b).ok(), "outro jogador contrata outro aldeão");
		h.assertFalse(Helper.hire(third, level, v, c).ok(), "no máximo " + TownRules.HELPERS_PER_VILLAGE + " por vila");
		// terrenos: o meu (0..3) e o do outro (5..8)
		TestUtil.lot(h, v, me, 0, 0, 3, 3);
		TestUtil.lot(h, v, other, 5, 5, 8, 8);
		h.assertTrue(Helper.mayStand(level, h.absolutePos(new BlockPos(1, 1, 1)), me.getUUID()), "no meu terreno entra");
		h.assertTrue(Helper.mayStand(level, h.absolutePos(new BlockPos(4, 1, 4)), me.getUUID()), "fora de terrenos (rua) também");
		h.assertFalse(Helper.mayStand(level, h.absolutePos(new BlockPos(7, 1, 7)), me.getUUID()), "no terreno alheio NÃO entra");
		// colher: trigo maduro no meu terreno e no do outro
		BlockPos mine = new BlockPos(1, 1, 2), theirs = new BlockPos(6, 1, 6);
		for (BlockPos rel : List.of(mine, theirs)) {
			h.setBlock(rel.below(), Blocks.FARMLAND);
			h.setBlock(rel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		}
		BlockPos found = Helper.cropNear(level, h.absolutePos(new BlockPos(3, 1, 3)), me.getUUID());
		h.assertTrue(h.absolutePos(mine).equals(found), "só acha o trigo do MEU terreno: " + found);
		h.assertTrue(Helper.cropNear(level, h.absolutePos(new BlockPos(6, 1, 4)), me.getUUID()) == null || !h.absolutePos(theirs).equals(
				Helper.cropNear(level, h.absolutePos(new BlockPos(6, 1, 4)), me.getUUID())), "nunca o do outro");
		TownData.Helper hm = v.civic.life.town.helperOf(me.getUUID());
		Helper.harvestAt(level, a, hm, h.absolutePos(mine));
		h.assertTrue(hm.bag.stream().anyMatch(s -> s.is(Items.WHEAT)), "o trigo foi para a bolsa");
		h.assertTrue(level.getBlockState(h.absolutePos(mine)).is(Blocks.WHEAT), "voltou a semear");
		Helper.harvestAt(level, a, hm, h.absolutePos(theirs));
		h.assertTrue(level.getBlockState(h.absolutePos(theirs)).getValue(CropBlock.AGE) == 7, "o trigo do outro fica lá");
		int wheat = me.getInventory().countItem(Items.WHEAT);
		h.assertTrue(Helper.dismiss(me, v).ok(), "dispensado");
		h.assertTrue(me.getInventory().countItem(Items.WHEAT) > wheat, "a bolsa voltou para mim");
		h.assertTrue(hm.bag.isEmpty(), "e ficou vazia (sem duplicar)");
		h.assertFalse(Helper.hire(me, level, v, c).ok(), "um contrato por dia");
		h.succeed();
	}

	@GameTest(maxTicks = 400)
	public void helperFollowsAndHarvestsOnItsOwn(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer me = TestUtil.playerAt(h, new BlockPos(6, 1, 3));
		me.getInventory().clearContent();
		TestUtil.giveEmeralds(me, 3);
		TestUtil.lot(h, v, me, 0, 0, 8, 8);
		BlockPos crop = new BlockPos(6, 1, 6);
		h.setBlock(crop.below(), Blocks.FARMLAND);
		h.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		Villager a = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		Life.relist(v);
		h.assertTrue(Helper.hire(me, level, v, a).ok(), "contratado");
		TownData.Helper hm = v.civic.life.town.helperOf(me.getUUID());
		h.onEachTick(() -> {
			if (level.getGameTime() % 20 == 0) Life.tick(level, v);
		});
		h.succeedWhen(() -> {
			Task t = Tasks.get(a);
			h.assertTrue(hm.bag.stream().anyMatch(s -> s.is(Items.WHEAT)), "colheu sozinho: tarefa=" + (t == null ? null : t.kind + "/" + t.role + "/" + t.target + "/" + t.endReason)
					+ " está=" + a.blockPosition() + " planta=" + h.absolutePos(crop));
			Helper.dismiss(me, v);
		});
	}

	@GameTest(maxTicks = 500)
	public void helperGivesUpOnACropItCannotReach(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer me = TestUtil.playerAt(h, new BlockPos(1, 1, 5));
		me.getInventory().clearContent();
		TestUtil.giveEmeralds(me, 3);
		TestUtil.lot(h, v, me, 0, 0, 8, 8);
		BlockPos crop = new BlockPos(5, 1, 5);
		h.setBlock(crop.below(), Blocks.FARMLAND);
		h.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		for (int x = 3; x <= 7; x++) // vidro em volta (a 2 blocos), 2 de altura: vê-se mas não se chega
			for (int z = 3; z <= 7; z++)
				if (x == 3 || x == 7 || z == 3 || z == 7) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
		Villager a = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		Life.relist(v);
		h.assertTrue(Helper.hire(me, level, v, a).ok(), "contratado");
		TownData.Helper hm = v.civic.life.town.helperOf(me.getUUID());
		h.onEachTick(() -> {
			if (level.getGameTime() % 20 == 0) Life.tick(level, v);
		});
		h.succeedWhen(() -> {
			Task t0 = Tasks.get(a);
			h.assertTrue(hm.skip.contains(h.absolutePos(crop).asLong()), "plano B: desistiu da planta a que não chega: tarefa=" + (t0 == null ? null
					: t0.kind + "/" + t0.role + "/" + t0.target + "/" + t0.endReason) + " tentou=" + BlockPos.of(hm.tryPos) + " desde " + hm.trySince + " agora "
					+ level.getGameTime() + " está=" + a.blockPosition() + " contrato=" + hm.ended);
			h.assertTrue(level.getBlockState(h.absolutePos(crop)).getValue(CropBlock.AGE) == 7, "e não a colheu através do vidro");
			Task t = Tasks.get(a);
			h.assertTrue(t == null || !"harvest".equals(t.role), "já não está a tentar");
			Helper.dismiss(me, v);
		});
	}

	// ---------------------------------------------------------------- 8) «!» em balão

	@GameTest(maxTicks = 60)
	public void requestMarkIsABalloonAndTheNameIsLeftAlone(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerLevel level = h.getLevel();
		TestUtil.playerAt(h, new BlockPos(4, 1, 2));
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.FARMER);
		vi.setCustomName(Component.literal("!")); // um «!» antigo (0.4) que tinha ficado no nome
		v.civic.batch.requests.put(vi.getUUID(), Mission.of(++v.missionCounter, com.tcordeiro.vilas.logic.MissionRules.request("farmer", 1)));
		v.civic.batch.id = VillagerRequests.batch(level);
		int sent = Balloons.sent;
		VillagerRequests.tick(level, v);
		h.assertTrue(vi.entityTags().contains(VillagerRequests.TAG), "marcado com pedido");
		h.assertFalse(vi.hasCustomName(), "o «!» antigo saiu do nome (e nenhum nome novo é posto)");
		h.assertTrue(Balloons.sent > sent && Balloons.EXCLAIM.equals(Balloons.last.icon()), "o «!» vai como balão");
		Villager named = villager(h, new BlockPos(2, 1, 4), VillagerProfession.FARMER);
		named.setCustomName(Component.literal("Zé"));
		v.civic.batch.requests.put(named.getUUID(), Mission.of(++v.missionCounter, com.tcordeiro.vilas.logic.MissionRules.request("farmer", 2)));
		VillagerRequests.tick(level, v);
		h.assertValueEqual(named.getCustomName().getString(), "Zé", "o nome dado por um jogador nunca muda");
		h.succeed();
	}

	// ---------------------------------------------------------------- 9) gestos (pacote do servidor)

	@GameTest(maxTicks = 20)
	public void gesturesAreSentToPlayersNearby(GameTestHelper h) {
		TestUtil.playerAt(h, new BlockPos(4, 1, 2));
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.FARMER);
		int n = Balloons.gestures;
		com.tcordeiro.vilas.life.Gestures.cheer(vi);
		h.assertTrue(Balloons.gestures > n && Balloons.lastGesture.code() == com.tcordeiro.vilas.life.Gestures.CHEER, "o gesto «festejar» foi mandado");
		h.succeed();
	}

}
