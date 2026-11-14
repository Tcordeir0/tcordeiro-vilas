package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.TreasuryRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.registry.VilasProfessions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

/**
 * Impostos, tesouraria e «renascer» da vila (servidor). As contas estão em {@link TreasuryRules}; aqui liga-se ao mundo.
 * <p>
 * Pagamento: com o CubCoin, em moedas/notas do inventário; sem ele, a dívida acumula em cêntimos e cobra-se em esmeraldas
 * inteiras ({@code emerald_value_cents} cada). Quem não pode pagar (ou não está ligado) fica a dever; paga-se quando houver.
 */
public final class VillageEconomy {
	private VillageEconomy() {}

	/** Raio à volta da Mesa onde se contam camas livres e golens da vila. */
	public static final int CORE_RADIUS = 96;
	public static final long DAY = 24000;

	public static long day(ServerLevel level) {
		return level.getGameTime() / DAY;
	}

	public static long debt(Village v, UUID who) {
		return v.economy.debts.getOrDefault(who, 0L);
	}

	/** Cobra {@code cents} a este jogador para a tesouraria. Devolve quanto entrou já (o resto fica em dívida). */
	public static long charge(ServerLevel level, Village v, UUID who, long cents) {
		if (cents <= 0 || who == null) return 0;
		v.economy.debts.merge(who, cents, Long::sum);
		return settle(level, v, who);
	}

	/** Tenta pagar a dívida deste jogador (se estiver ligado). */
	public static long settle(ServerLevel level, Village v, UUID who) {
		long debt = debt(v, who);
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
		if (debt <= 0 || p == null) return 0;
		long paid = 0;
		if (TVilas.CUBCOIN) {
			long take = Math.min(debt, com.tcordeiro.vilas.compat.CubCoinPay.balance(p));
			if (take > 0 && com.tcordeiro.vilas.compat.CubCoinPay.pay(p, take)) paid = take;
		} else {
			long value = VilasConfig.get().emerald_value_cents;
			long n = Math.min(TreasuryRules.emeraldsFor(debt, value), VillageManager.count(p.getInventory(), Items.EMERALD));
			if (n > 0) {
				VillageManager.take(p.getInventory(), Items.EMERALD, (int) n);
				paid = n * value;
			}
		}
		if (paid > 0) {
			if (debt - paid <= 0) {
				v.economy.debts.remove(who);
				Debts.cleared(level, v, who);
			} else v.economy.debts.put(who, debt - paid);
			v.economy.treasury += paid;
			v.economy.collected += paid;
			VillageData.get(level).changed();
		}
		return paid;
	}

	/** Camas (pontos de interesse «casa») dentro deste terreno. */
	public static int bedsIn(ServerLevel level, Lot lot) {
		int cx = (lot.area.minX() + lot.area.maxX()) / 2, cz = (lot.area.minZ() + lot.area.maxZ()) / 2;
		BlockPos c = new BlockPos(cx, level.getSeaLevel(), cz);
		return (int) level.getPoiManager().getInSquare(h -> h.is(PoiTypes.HOME), c, 12, PoiManager.Occupancy.ANY)
				.filter(r -> lot.contains(r.getPos().getX(), r.getPos().getZ())).count();
	}

	/** Camas perto da Prefeitura (para os moradores). */
	public static int bedsNear(ServerLevel level, Village v) {
		return (int) level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), v.center, coreRadius(v), PoiManager.Occupancy.ANY);
	}

	/** O núcleo da vila: {@link #CORE_RADIUS} blocos (ou o raio, se a vila for mais pequena). */
	public static int coreRadius(Village v) {
		return Math.min(CORE_RADIUS, v.radius);
	}

	static AABB core(Village v) {
		return new AABB(v.center).inflate(coreRadius(v));
	}

	public static int homeGolems(ServerLevel level, Village v) {
		return level.getEntitiesOfClass(IronGolem.class, core(v), g -> g.isAlive() && !g.entityTags().contains(com.tcordeiro.vilas.block.MayorDeskBlockEntity.CALLED_TAG)).size();
	}

	// ------------------------------------------------------------------ trocas

	/** Troca com um aldeão no território: 2 % do valor das esmeraldas da troca, pago por quem trocou. */
	public static void onVillagerTrade(ServerPlayer p, BlockPos where, MerchantOffer offer) {
		ServerLevel level = p.level();
		Village v = VillageManager.villageAt(level, where);
		if (v == null) return;
		// contadas na troca ORIGINAL (com o CubCoin o ecrã mostra-as em notas, mas o valor é o mesmo: 0.3.0)
		int emeralds = com.tcordeiro.vilas.economy.CoinTrades.emeraldsTraded(offer);
		long tax = TreasuryRules.tradeTax(emeralds * VilasConfig.get().emerald_value_cents, taxRate(v).percent());
		charge(level, v, p.getUUID(), tax);
		com.tcordeiro.vilas.life.Festa.onTrade(p, v, offer); // 0.4.0: reembolso da festa (da tesouraria)
		com.tcordeiro.vilas.life.Market.onTrade(p, v, where, offer); // 0.5.0: reembolso nas bancas do mercado (da tesouraria)
		com.tcordeiro.vilas.life.election.VoteBonus.onTrade(p, v, offer); // 0.6 eleições: −5 % do apoiador e extra VIP da festa (da tesouraria)
	}

	/** Venda entre jogadores (CubCoin) no território: 2 % do preço, pago pelo vendedor (que acabou de receber). */
	public static void onPlayerSale(ServerLevel level, BlockPos where, UUID seller, long priceCents) {
		Village v = VillageManager.villageAt(level, where);
		if (v == null) return;
		long tax = TreasuryRules.tradeTax(priceCents, taxRate(v).percent());
		tax = com.tcordeiro.vilas.life.election.VoteBonus.saleTax(level, v, where, seller, tax); // 0.6 eleições: banca própria no mercado
		charge(level, v, seller, tax);
	}

	/** A taxa das trocas em vigor nesta vila (o Prefeito ajusta-a: {@link com.tcordeiro.vilas.logic.TaxRules}). */
	public static com.tcordeiro.vilas.logic.TaxRules.Rate taxRate(Village v) {
		VilasConfig c = VilasConfig.get();
		var r = com.tcordeiro.vilas.logic.TaxRules.rate(c.trade_tax_percent, v.extra.structures, c.tax_per_structure_percent, c.tax_cap_percent,
				v.economy.treasury, c.treasury_full_cents);
		// 0.5.0: o Prefeito eleito com «imposto menor» tira 1 ponto (nunca abaixo de 0)
		String policy = v.civic.life.town.election.policy;
		double cut = com.tcordeiro.vilas.logic.TownRules.taxWithPolicy(r.percent(), policy);
		return cut < r.percent() ? new com.tcordeiro.vilas.logic.TaxRules.Rate(cut, com.tcordeiro.vilas.logic.TaxRules.Reason.POLICY) : r;
	}

	/** Anuncia (uma linha) quando a taxa muda. */
	static void announceRate(ServerLevel level, Village v) {
		var rate = taxRate(v);
		double last = v.extra.lastRate;
		v.extra.lastRate = rate.percent();
		if (last < 0 || Math.abs(last - rate.percent()) < 1e-9) return;
		String key = rate.percent() > last ? "tvilas.news.tax_up" : "tvilas.news.tax_down";
		VillageNews.announce(level, v, VillageNews.TAX, key, com.tcordeiro.vilas.logic.TaxRules.format(rate.percent()),
				Component.translatable(rate.reason().key()));
	}

	// ------------------------------------------------------------------ Prefeito e moradores

	static boolean deskHere(ServerLevel level, Village v) {
		return level.isLoaded(v.center) && level.getBlockState(v.center).is(VilasBlocks.MAYOR_DESK);
	}

	/**
	 * Vila sem Prefeito: se ninguém tem a Mesa e não há desempregados perto para a apanhar, chega um Prefeito (no máximo 1 por dia
	 * de jogo). Assim uma vila sem aldeões, mas com sino e camas, renasce quando se põe a Prefeitura.
	 */
	public static Villager ensureMayor(ServerLevel level, Village v, long now) {
		if (!deskHere(level, v)) return null;
		AABB near = new AABB(v.center).inflate(Math.min(48, v.radius));
		Civic c = v.civic;
		List<Villager> here = level.getEntitiesOfClass(Villager.class, near, e -> e.isAlive() && VillageManager.isMayor(e));
		for (Villager r : here) if (c.retired.contains(r.getUUID())) retire(level, r, c); // um antigo Prefeito que voltou: já não é
		here.removeIf(r -> !VillageManager.isMayor(r));
		if (!here.isEmpty()) {
			if (!here.get(0).getUUID().equals(c.mayor) || c.mayorTaken) {
				c.mayor = here.get(0).getUUID();
				c.mayorTaken = false;
			}
			c.mayorSeen = now;
			VillageData.get(level).changed();
			return null;
		}
		// C6 (0.3.0): o Prefeito conhecido não está aqui. Vivo e carregado noutro sítio (levado num barco, carrinho…): NÃO chega
		// outro (senão capturá-lo dava um aldeão por dia) — volta pela Mesa ou se morrer. Sumido (chunk por carregar) há 3 dias de
		// jogo: chega um novo e o antigo deixa de ser Prefeito quando voltar a aparecer (nunca fica a vila presa para sempre).
		if (c.mayor != null) {
			if (c.mayorSeen < 0) c.mayorSeen = now; // vilas de antes da 0.3.0: conta a partir de agora
			var e = level.getEntity(c.mayor);
			if (e instanceof Villager m && m.isAlive() && VillageManager.isMayor(m)) {
				c.mayorSeen = now;
				if (!c.mayorTaken) c.mayorTaken = true;
				VillageData.get(level).changed();
				return null;
			}
			if (e == null && !com.tcordeiro.vilas.logic.FundRules.mayorGone(c.mayorSeen, now, DAY)) {
				if (!c.mayorTaken) {
					c.mayorTaken = true;
					VillageData.get(level).changed();
				}
				return null;
			}
			if (e == null) c.retired.add(c.mayor); // sumido 3 dias: quando voltar a carregar perde a profissão
			c.mayor = null; // morto, sem posto, ou sumido: esquece-o
			c.mayorTaken = false;
			c.mayorSeen = -1;
		}
		if (!level.getEntitiesOfClass(Villager.class, near, e -> !e.isBaby() && e.getVillagerData().profession().is(VillagerProfession.NONE)).isEmpty())
			return null; // um desempregado vai apanhar a Mesa sozinho (vanilla)
		if (v.economy.lastMayorCall >= 0 && now - v.economy.lastMayorCall < DAY) return null;
		Villager mayor = spawnVillager(level, v);
		if (mayor == null) return null;
		v.economy.lastMayorCall = now;
		c.mayor = mayor.getUUID();
		c.mayorTaken = false;
		c.mayorSeen = now;
		mayor.setVillagerData(mayor.getVillagerData().withProfession(level.registryAccess(), VilasProfessions.MAYOR));
		// 0.3.0: com 0 de XP e nível 1, o comportamento vanilla «ResetProfession» tira-lhe a profissão logo que perde a memória do
		// posto (ficava «minecraft:none»). Com 1 de XP ele já é Prefeito «de carreira» e não a perde.
		mayor.setVillagerXp(1);
		// fica com a vaga da Mesa (como o AcquirePoi vanilla), para nenhum outro aldeão a tirar
		if (!takeDesk(level, v)) {
			level.getPoiManager().release(v.center); // vaga presa por um bilhete velho (aqui não há Prefeito levado: ver acima)
			if (!takeDesk(level, v)) TVilas.LOGGER.warn("Vilas: o Prefeito novo não conseguiu a vaga da Mesa em {}", v.center.toShortString());
		}
		mayor.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), v.center));
		VillageManager.broadcast(level, v, Component.translatable("tvilas.mayor.arrived"));
		VillageData.get(level).changed();
		return mayor;
	}

	static boolean takeDesk(ServerLevel level, Village v) {
		return level.getPoiManager().take(h -> h.is(VilasProfessions.POIS.get(VilasProfessions.MAYOR)), (h, pos) -> pos.equals(v.center), v.center, 1)
				.isPresent();
	}

	/** O Prefeito morreu (ou converteu-se, ou mudou de dimensão): a vila de qualquer dimensão pode receber outro (máx. 1 por dia). */
	public static void onMayorDied(net.minecraft.server.MinecraftServer server, java.util.UUID who) {
		for (ServerLevel level : server.getAllLevels())
			for (Village v : VillageData.get(level).villages)
				if (who.equals(v.civic.mayor)) {
					v.civic.mayor = null;
					v.civic.mayorTaken = false;
					v.civic.mayorSeen = -1;
					VillageData.get(level).changed();
				}
	}

	/** Este aldeão foi Prefeito e já não é (sumido 3 dias, ou saiu da dimensão): perde a profissão e a experiência. */
	public static void retire(ServerLevel level, Villager m, Civic c) {
		if (c != null) c.retired.remove(m.getUUID());
		if (!VillageManager.isMayor(m)) return;
		m.setVillagerXp(0);
		m.setVillagerData(m.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
		m.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		VillageData.get(level).changed();
	}

	/** Um aldeão carregou: se for um antigo Prefeito desta dimensão, perde a profissão. */
	public static void onLoaded(ServerLevel level, net.minecraft.world.entity.Entity e) {
		if (!(e instanceof Villager m) || !VillageManager.isMayor(m)) return;
		for (Village v : VillageData.get(level).villages)
			if (v.civic.retired.contains(m.getUUID())) {
				retire(level, m, v.civic);
				return;
			}
	}

	/**
	 * Clicar na Mesa com o Prefeito levado: se ele estiver carregado em qualquer sítio deste mundo, volta para a Mesa. Devolve a
	 * mensagem a mostrar (null se o Prefeito não foi levado).
	 */
	public static Component recallMayor(ServerLevel level, Village v) {
		Civic c = v.civic;
		if (!c.mayorTaken || c.mayor == null) return null;
		if (level.getEntity(c.mayor) instanceof Villager m && m.isAlive() && VillageManager.isMayor(m)) {
			m.stopRiding();
			m.dropLeash();
			m.teleportTo(v.center.getX() + 0.5, v.center.getY() + 1, v.center.getZ() + 0.5);
			c.mayorTaken = false;
			VillageData.get(level).changed();
			return Component.translatable("tvilas.mayor.recalled");
		}
		return Component.translatable("tvilas.mayor.taken");
	}

	public static Villager spawnVillager(ServerLevel level, Village v) {
		int range = Math.max(1, Math.min(6, v.radius / 2)); // perto da Mesa (e sempre dentro de uma vila pequena)
		return SpawnUtil.trySpawnMob(EntityTypes.VILLAGER, EntitySpawnReason.MOB_SUMMONED, level, v.center, 20, range, 4,
				SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).orElse(null);
	}

	/**
	 * Uma vez por dia de jogo: cobra o imposto de moradia (camas nos terrenos), tenta cobrar as dívidas de quem está ligado, chega
	 * um morador de graça se a vila tem camas e quase ninguém, e o Prefeito gasta a tesouraria numa melhoria. Público para os testes
	 * poderem avançar os dias.
	 */
	public static void daily(ServerLevel level, Village v, long day) {
		com.tcordeiro.vilas.build.CivicWork.refresh(level, v); // 0.3.0: área cívica protegida e os edifícios que a vila já tem
		VillagerRequests.tick(level, v); // 0.3.0: lote de missões (a cada 4 dias) e os «!» dos aldeões com pedidos
		Village.Economy e = v.economy;
		if (e.lastDay < 0) {
			e.lastDay = day;
			VillageData.get(level).changed();
			return;
		}
		long days = day - e.lastDay;
		if (days <= 0) return;
		e.lastDay = day;
		// 0.3.0: o fundo das missões recebe 50 CB por dia, até 400 CB
		VilasConfig cf = VilasConfig.get();
		if (v.civic.meta.real) // revisão de segurança: só numa vila de verdade
			v.civic.missionFund = com.tcordeiro.vilas.logic.FundRules.topUp(v.civic.missionFund, days, cf.mission_fund_daily_cents, cf.mission_fund_cap_cents);
		long perBed = VilasConfig.get().bed_tax_cents;
		for (Lot lot : VillageManager.lotsOf(level, v)) // 0.6 eleições: −20 % no terreno de quem votou em «imposto menor»
			charge(level, v, lot.owner, com.tcordeiro.vilas.life.election.VoteBonus.lotTax(v, lot.owner, TreasuryRules.bedTax(bedsIn(level, lot), days, perBed)));
		for (UUID who : new ArrayList<>(e.debts.keySet())) settle(level, v, who);
		Debts.daily(level, v, day);
		// a noite passou: o que ficou por construir aparece agora
		com.tcordeiro.vilas.build.Builders.overnight(level, v);
		com.tcordeiro.vilas.build.LightingWork.revalidate(level, v); // um fio tapado por uma construção nova é desfeito
		TravelingMerchant.daily(level, v, day);
		commissionWall(level, v);
		com.tcordeiro.vilas.build.CivicWork.commissionNext(level, v); // 0.3.0: Prefeitura/Mercado/Taverna/Praça que faltam
		Growth.step(level, v); // 0.3.0: o prestígio das missões traz moradores (ou encomenda uma casa)
		com.tcordeiro.vilas.life.Life.daily(level, v, day); // 0.4.0: guardas (salário), festa na véspera do Mercador
		com.tcordeiro.vilas.life.crisis.Crises.daily(level, v, day); // 0.6 crises: fome, velhice

		int pop = VillageManager.countPopulation(level, v);
		int beds = bedsNear(level, v);
		if (TreasuryRules.freeResident(pop, beds)) {
			if (spawnVillager(level, v) != null) e.log(day, "tvilas.upgrade.immigrant", 0);
		} else {
			TreasuryRules.Upgrade up = TreasuryRules.choose(e.treasury, pop, beds, homeGolems(level, v) + golemsBeingBuilt(v), e.alarmBonus, v.level,
					v.extra.lighting);
			if (up != null && apply(level, v, up)) {
				e.treasury -= up.cost;
				e.log(day, up.key(), up.cost);
				v.extra.structures++;
				VillageManager.broadcast(level, v, Component.translatable("tvilas.upgrade.bought", Component.translatable(up.key()),
						VillageManager.money(up.cost)));
			}
		}
		// obras acabadas de começar sem ninguém a ver: aparecem já (a compra foi feita durante a noite)
		if (!v.extra.projects.isEmpty() && !com.tcordeiro.vilas.build.Builders.watchedNow(level, v)) com.tcordeiro.vilas.build.Builders.overnight(level, v);
		announceRate(level, v);
		VillageData.get(level).changed();
	}

	static int golemsBeingBuilt(Village v) {
		int n = 0;
		for (Project p : v.extra.projects) if (p.kind == Project.Kind.GOLEM) n++;
		return n;
	}

	/** Uma Cidade encomenda a muralha (uma vez; pode ser cancelada no quadro). */
	public static boolean commissionWall(ServerLevel level, Village v) {
		if (!VilasConfig.get().wall || v.level < com.tcordeiro.vilas.logic.VillageRules.CIDADE || v.extra.wall != Village.Extra.WALL_NONE) return false;
		v.extra.wall = Village.Extra.WALL_BUILDING;
		com.tcordeiro.vilas.build.Builders.start(level, v, com.tcordeiro.vilas.build.WallWork.create(v));
		v.economy.log(day(level), TreasuryRules.Upgrade.WALL.key(), 0);
		return true;
	}

	static boolean apply(ServerLevel level, Village v, TreasuryRules.Upgrade up) {
		return switch (up) {
			case RESIDENT -> spawnVillager(level, v) != null;
			case GOLEM -> {
				// 0.2.0: o construtor monta o golem (ferro + abóbora); sem sítio livre, chega como antes
				Project p = com.tcordeiro.vilas.build.GolemWork.create(level, v);
				if (p != null) {
					com.tcordeiro.vilas.build.Builders.start(level, v, p);
					yield true;
				}
				yield SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, v.center, 20,
						Math.max(1, Math.min(8, v.radius / 2)), 6, SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false).isPresent();
			}
			case LIGHTING -> {
				Project p = com.tcordeiro.vilas.build.LightingWork.create(level, v);
				if (p == null) yield false; // sem ruas: não gasta nada
				v.extra.lighting = true;
				com.tcordeiro.vilas.build.Builders.start(level, v, p);
				yield true;
			}
			case WALL -> false;
			case ALARM -> {
				v.economy.alarmBonus++;
				yield true;
			}
		};
	}

	/** Os últimos gastos, para o diálogo. */
	public static List<Component> expenses(Village v) {
		List<Component> out = new ArrayList<>();
		for (Village.Economy.Expense x : v.economy.expenses)
			out.add(Component.translatable("tvilas.dialog.expense", x.day(), Component.translatable(x.what()), VillageManager.money(x.cents())));
		return out;
	}
}
