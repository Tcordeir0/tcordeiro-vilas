package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.LotRules;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.registry.VilasProfessions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Tudo o que mexe nas vilas e terrenos de um mundo passa por aqui (servidor). Os números e as regras de segurança estão em
 * {@code logic/} (testados com JUnit); aqui só se liga isso ao mundo.
 */
public final class VillageManager {
	private VillageManager() {}

	/** Resultado de uma ação do jogador: correu bem? e a mensagem a mostrar. */
	public record Result(boolean ok, Component message) {
		public static Result ok(String key, Object... args) {
			return new Result(true, Component.translatable(key, args));
		}

		public static Result fail(String key, Object... args) {
			return new Result(false, Component.translatable(key, args));
		}
	}

	public static VillageData data(ServerLevel level) {
		return VillageData.get(level);
	}

	// ------------------------------------------------------------------ vilas

	public static Village byDesk(ServerLevel level, BlockPos desk) {
		for (Village v : data(level).villages) if (v.center.equals(desk)) return v;
		return null;
	}

	/** A vila que cobre esta posição (a de centro mais perto, se houver várias). */
	public static Village villageAt(ServerLevel level, BlockPos pos) {
		Village best = null;
		for (Village v : data(level).villages)
			if (v.covers(pos.getX(), pos.getZ()) && (best == null || v.distSqr(pos.getX(), pos.getZ()) < best.distSqr(pos.getX(), pos.getZ())))
				best = v;
		return best;
	}

	/** A Mesa da vila ainda existe? (chunk por carregar conta como «existe», para não se pôr outra às cegas) */
	public static boolean deskPresent(ServerLevel level, Village v) {
		return !level.isLoaded(v.center) || level.getBlockState(v.center).is(VilasBlocks.MAYOR_DESK);
	}

	/** Pode pôr-se uma Mesa do Prefeito aqui? Não dentro de uma vila que já tenha a sua (uma Prefeitura por vila). */
	public static boolean canPlaceDesk(ServerLevel level, BlockPos pos) {
		for (Village v : data(level).villages)
			if (!v.center.equals(pos) && v.covers(pos.getX(), pos.getZ()) && deskPresent(level, v)) return false;
		return true;
	}

	/**
	 * Regista a vila desta Mesa. Se a mesa estiver dentro de uma vila cuja Mesa foi partida, adota essa vila (reputação, missões e
	 * terrenos continuam). Devolve null se já houver outra Mesa nesta vila (a mesa fica sem efeito).
	 */
	public static Village found(ServerLevel level, BlockPos desk, int radius) {
		VillageData d = data(level);
		Village same = byDesk(level, desk);
		if (same != null) return same;
		for (Village v : d.villages) {
			if (!v.covers(desk.getX(), desk.getZ())) continue;
			if (deskPresent(level, v)) return null;
			v.center = desk.immutable();
			d.changed();
			return v;
		}
		Village v = new Village(UUID.randomUUID(), desk.immutable(), radius);
		fillMissions(level, v);
		d.villages.add(v);
		d.changed();
		TVilas.LOGGER.info("Nova vila com Prefeitura em {} (raio {})", desk.toShortString(), radius);
		return v;
	}

	public static void fillMissions(ServerLevel level, Village v) {
		while (v.missions.size() < MissionRules.SLOTS.length) v.missions.add(newMission(level, v, v.missions.size(), Set.of()));
	}

	private static Mission newMission(ServerLevel level, Village v, int slot, Set<MissionRules.Template> avoid) {
		MissionRules.Template t = MissionRules.pick(slot, VillageRules.missionTier(v.level), level.getRandom().nextInt(1 << 20), avoid);
		return Mission.of(++v.missionCounter, t);
	}

	/** Troca a missão de uma casa por uma escolhida (usado pelos testes e pelo comando de administração). */
	public static Mission setMission(ServerLevel level, Village v, int slot, MissionRules.Template t) {
		Mission old = v.missions.get(slot);
		v.forget(old);
		Mission m = Mission.of(++v.missionCounter, t);
		v.missions.set(slot, m);
		data(level).changed();
		return m;
	}

	public static boolean isMayor(Entity e) {
		return e instanceof Villager v && v.getVillagerData().profession().is(VilasProfessions.MAYOR);
	}

	public static AABB box(Village v, ServerLevel level) {
		return new AABB(v.center.getX() - v.radius, level.getMinY(), v.center.getZ() - v.radius, v.center.getX() + v.radius + 1,
				level.getMaxY() + 1, v.center.getZ() + v.radius + 1);
	}

	/** Aldeões dentro do raio (o Prefeito conta). */
	public static int countPopulation(ServerLevel level, Village v) {
		return level.getEntitiesOfClass(Villager.class, box(v, level),
				e -> e.isAlive() && v.covers(e.getBlockX(), e.getBlockZ()) && !com.tcordeiro.vilas.build.Builders.isBuilder(e)).size();
	}

	/** Conta a população e sobe o nível se der. Devolve true se subiu. */
	public static boolean updateLevel(ServerLevel level, Village v) {
		v.population = countPopulation(level, v);
		int next = VillageRules.nextLevel(v.level, v.population, v.reputationSum());
		boolean up = next > v.level;
		if (up) {
			v.level = next;
			broadcast(level, v, Component.translatable("tvilas.level.up", Component.translatable(VillageRules.levelKey(next))));
		}
		data(level).changed();
		return up;
	}

	public static List<ServerPlayer> playersIn(ServerLevel level, Village v) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) if (v.covers(p.getBlockX(), p.getBlockZ())) out.add(p);
		return out;
	}

	public static void broadcast(ServerLevel level, Village v, Component msg) {
		for (ServerPlayer p : playersIn(level, v)) p.sendSystemMessage(msg);
	}

	// ------------------------------------------------------------------ reputação

	/**
	 * Dá reputação ao jogador nesta vila (valor nosso, por jogador e por vila — é o que faz subir o nível) e também «boa fama» no
	 * sistema de mexericos do vanilla aos aldeões da vila (gossip MINOR_POSITIVE: preços um pouco mais baixos com eles).
	 */
	public static void addReputation(ServerLevel level, Village v, ServerPlayer p, int amount) {
		v.reputation.merge(p.getUUID(), amount, Integer::sum);
		int gossip = Math.min(amount, 25);
		if (gossip > 0)
			for (Villager villager : level.getEntitiesOfClass(Villager.class, box(v, level), e -> v.covers(e.getBlockX(), e.getBlockZ())))
				villager.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, gossip);
		data(level).changed();
	}

	// ------------------------------------------------------------------ terrenos

	/** O terreno em (x, z), ou null (procura rápida pelo índice por chunk de {@link VillageData#lotAt}). */
	public static Lot lotAt(ServerLevel level, int x, int z) {
		return data(level).lotAt(x, z);
	}

	/**
	 * REGRA DE SEGURANÇA no mundo, para o que vem de fora (líquidos, fogo, pistões, explosões sem jogador): algo que nasce em
	 * {@code from} pode mexer em {@code to}? Fora de terrenos, sim; dentro de um terreno, só se {@code from} estiver num terreno do
	 * mesmo dono (ou de um amigo do terreno de destino) — ver {@link LotRules#mayCross}. Corre muitas vezes (cada passo de um
	 * fluido, cada tick do fogo): sem alocações, e sai logo se não houver terrenos.
	 */
	public static boolean mayCross(ServerLevel level, BlockPos from, BlockPos to) {
		VillageData d = data(level);
		Village civic = d.civicAt(to.getX(), to.getZ());
		if (civic != null) return civic.civic.covers(from.getX(), from.getZ()); // M15: na área cívica, só o que nasce lá dentro
		if (d.lots.isEmpty()) return true;
		Lot target = d.lotAt(to.getX(), to.getZ());
		if (target == null) return true;
		if (frozen(level, target)) return false; // M3: terreno congelado por dívida — pistões, ejetores, água e lava param
		Lot source = d.lotAt(from.getX(), from.getZ());
		if (source == target) return true;
		return LotRules.mayCross(source == null ? null : source.owner, target.owner, target.friends);
	}

	/** 0.3.0 (M3): a automação (pistões, ejetores, máquinas do Industrial) trabalha aqui? Não num terreno congelado por dívida. */
	public static boolean automationAllowed(ServerLevel level, BlockPos pos) {
		Lot lot = lotAt(level, pos.getX(), pos.getZ());
		return lot == null || !frozen(level, lot);
	}

	/** 0.3.0 (M15): a posição está na área cívica de uma vila? */
	public static boolean civic(ServerLevel level, BlockPos pos) {
		return data(level).civicAt(pos.getX(), pos.getZ()) != null;
	}

	/**
	 * REGRA DE SEGURANÇA para explosões: esta explosão pode partir o bloco em {@code pos}? Fora de terrenos, sim. Num terreno:
	 * <ul>
	 * <li>causada por um jogador (TNT que ele acendeu, cristal em que bateu, bola de fogo que devolveu): só se ele puder mexer
	 * no terreno (dono, amigo, operador);</li>
	 * <li>causada por um monstro (creeper, wither, ghast): nunca — os terrenos ficam a salvo dos monstros;</li>
	 * <li>sem causador (TNT acesa por redstone ou fogo, camas, âncoras): pela origem, como os líquidos ({@link #mayCross}).</li>
	 * </ul>
	 */
	public static boolean explosionMayBreak(ServerLevel level, LivingEntity cause, BlockPos origin, BlockPos pos) {
		if (civic(level, pos)) { // M15: a área cívica só cai por um operador ou por uma explosão nascida lá dentro
			if (cause instanceof Player player) return isAdmin(player);
			return cause == null && mayCross(level, origin, pos);
		}
		Lot hit = lotAt(level, pos.getX(), pos.getZ());
		if (hit == null) return true;
		if (cause instanceof Player player) return isAdmin(player) || (!frozen(level, hit) && hit.mayModify(player.getUUID()));
		if (cause != null) return false;
		return mayCross(level, origin, pos);
	}

	public static Village byId(ServerLevel level, UUID id) {
		for (Village v : data(level).villages) if (v.id.equals(id)) return v;
		return null;
	}

	/** O terreno está congelado (o dono deve há dias demais)? */
	public static boolean frozen(ServerLevel level, Lot lot) {
		Village v = byId(level, lot.village);
		return v != null && Debts.frozen(v, lot.owner);
	}

	public static List<Lot> lotsOf(ServerLevel level, Village v) {
		List<Lot> out = new ArrayList<>();
		for (Lot l : data(level).lots) if (l.village.equals(v.id)) out.add(l);
		return out;
	}

	/** Os operadores do servidor (não os jogadores falsos das máquinas) podem mexer em qualquer terreno. */
	static boolean isAdmin(Player actor) {
		return !(actor instanceof net.fabricmc.fabric.api.entity.FakePlayer) && actor.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/**
	 * REGRA DE SEGURANÇA no mundo: este jogador (ou máquina em nome dele) pode partir/pôr blocos nesta posição? Fora de terrenos,
	 * sim; num terreno, só o dono e os amigos (e os operadores).
	 */
	public static boolean mayModify(ServerLevel level, BlockPos pos, Player actor) {
		if (civic(level, pos)) return actor != null && isAdmin(actor); // M15: Prefeitura, Praça… são da vila
		Lot lot = lotAt(level, pos.getX(), pos.getZ());
		if (lot == null) return true;
		if (actor != null && isAdmin(actor)) return true;
		if (frozen(level, lot)) return false; // 0.2.0: terreno congelado por dívida — ninguém constrói até pagar (nada se apaga)
		return lot.mayModify(actor == null ? null : actor.getUUID());
	}

	/**
	 * 0.3.0 (C6) — REGRA DE SEGURANÇA para as entidades: este jogador pode bater/apanhar/usar o que está em {@code pos}? Como
	 * {@link #mayModify}, mas um terreno congelado por dívida não impede o dono de tratar dos seus bichos.
	 */
	public static boolean mayTouch(ServerLevel level, BlockPos pos, Player actor) {
		if (actor != null && isAdmin(actor)) return true;
		if (civic(level, pos)) return false;
		Lot lot = lotAt(level, pos.getX(), pos.getZ());
		return lot == null || lot.mayModify(actor == null ? null : actor.getUUID());
	}

	/** O preço de um terreno nesta vila para este jogador. */
	public static int lotPrice(Village v, UUID player) {
		return LotRules.price(v.level, v.reputationOf(player));
	}

	/** Compra o terreno 16×16 com o jogador no meio, pagando em esmeraldas do inventário. */
	public static Result buyLot(ServerPlayer p) {
		ServerLevel level = p.level();
		Village v = villageAt(level, p.blockPosition());
		if (v == null) return Result.fail("tvilas.lot.no_village");
		LotRules.Area area = LotRules.around(p.getBlockX(), p.getBlockZ());
		if (!LotRules.insideVillage(area, v.center.getX(), v.center.getZ(), v.radius)) return Result.fail("tvilas.lot.outside");
		if (!LotRules.awayFromDesk(area, v.center.getX(), v.center.getZ())) return Result.fail("tvilas.lot.civic");
		for (Village o : data(level).villages) if (o.civic.overlaps(area)) return Result.fail("tvilas.lot.civic");
		List<LotRules.Area> taken = new ArrayList<>();
		for (Lot l : data(level).lots) taken.add(l.area);
		if (LotRules.overlapsAny(area, taken)) return Result.fail("tvilas.lot.overlap");
		int max = VillageRules.maxLots(v.level);
		if (lotsOf(level, v).size() >= max) return Result.fail("tvilas.lot.sold_out", max);
		int price = lotPrice(v, p.getUUID());
		if (!payEmeralds(p, price)) return Result.fail("tvilas.lot.no_money", price(price));
		Lot lot = new Lot(UUID.randomUUID(), v.id, area, p.getUUID(), p.getGameProfile().name(), new HashSet<>());
		data(level).lots.add(lot);
		data(level).changed();
		return Result.ok("tvilas.lot.bought", price(price), area.minX(), area.minZ(), area.maxX(), area.maxZ());
	}

	public static Result addFriend(ServerPlayer owner, ServerPlayer friend) {
		Lot lot = lotAt(owner.level(), owner.getBlockX(), owner.getBlockZ());
		if (lot == null || !lot.owner.equals(owner.getUUID())) return Result.fail("tvilas.lot.not_yours");
		if (friend.getUUID().equals(owner.getUUID())) return Result.fail("tvilas.friend.self");
		lot.friends.add(friend.getUUID());
		lot.friendNames.put(friend.getUUID(), friend.getGameProfile().name());
		data(owner.level()).changed();
		return Result.ok("tvilas.friend.added", friend.getDisplayName());
	}

	public static Result removeFriend(ServerPlayer owner, ServerPlayer friend) {
		return removeFriend(owner, friend.getUUID(), friend.getGameProfile().name());
	}

	/**
	 * 0.3.0 (M18): tira um amigo pelo NOME, esteja ligado ou não — o nome guardado quando foi adicionado, o UUID escrito por
	 * extenso, ou o nome que o servidor conhece (cache de perfis, sem ir à Internet).
	 */
	public static Result removeFriend(ServerPlayer owner, String name) {
		Lot lot = lotAt(owner.level(), owner.getBlockX(), owner.getBlockZ());
		if (lot == null || !lot.owner.equals(owner.getUUID())) return Result.fail("tvilas.lot.not_yours");
		UUID who = lot.friendByName(name);
		if (who == null) {
			ServerPlayer online = owner.level().getServer().getPlayerList().getPlayerByName(name);
			if (online != null) who = online.getUUID();
		}
		if (who == null) {
			var cached = owner.level().getServer().services().nameToIdCache().get(name);
			if (cached.isPresent()) who = cached.get().id();
		}
		if (who == null) return Result.fail("tvilas.friend.not_friend");
		return removeFriend(owner, who, name);
	}

	static Result removeFriend(ServerPlayer owner, UUID who, String name) {
		Lot lot = lotAt(owner.level(), owner.getBlockX(), owner.getBlockZ());
		if (lot == null || !lot.owner.equals(owner.getUUID())) return Result.fail("tvilas.lot.not_yours");
		boolean removed = lot.friends.remove(who);
		lot.friendNames.remove(who);
		data(owner.level()).changed();
		return removed ? Result.ok("tvilas.friend.removed", name) : Result.fail("tvilas.friend.not_friend");
	}

	// ------------------------------------------------------------------ muralha (0.2.0)

	/** Quem decide da muralha: quem tem terreno nesta vila, quem tem reputação aqui, ou um operador. */
	static boolean citizen(ServerPlayer p, Village v) {
		if (isAdmin(p) || v.reputationOf(p.getUUID()) > 0) return true;
		for (Lot l : lotsOf(p.level(), v)) if (l.owner.equals(p.getUUID())) return true;
		return false;
	}

	/** Cancela a muralha: a obra pára; o que já foi construído fica (nada é desfeito). */
	public static Result cancelWall(ServerPlayer p, Village v) {
		if (!citizen(p, v)) return Result.fail("tvilas.wall.not_citizen");
		if (v.extra.wall != Village.Extra.WALL_BUILDING) return Result.fail("tvilas.wall.not_building");
		v.extra.wall = Village.Extra.WALL_CANCELLED;
		v.extra.projects.removeIf(pr -> pr.kind == Project.Kind.WALL);
		data(p.level()).changed();
		VillageNews.announce(p.level(), v, VillageNews.BUILD, "tvilas.news.wall_cancelled", p.getDisplayName());
		return Result.ok("tvilas.wall.cancelled");
	}

	/** Retoma uma muralha cancelada (recomeça do princípio; as colunas já feitas são saltadas sozinhas). */
	public static Result resumeWall(ServerPlayer p, Village v) {
		if (!citizen(p, v)) return Result.fail("tvilas.wall.not_citizen");
		if (v.extra.wall != Village.Extra.WALL_CANCELLED) return Result.fail("tvilas.wall.not_cancelled");
		v.extra.wall = Village.Extra.WALL_BUILDING;
		com.tcordeiro.vilas.build.Builders.start(p.level(), v, com.tcordeiro.vilas.build.WallWork.create(v));
		return Result.ok("tvilas.wall.resumed");
	}

	// ------------------------------------------------------------------ missões

	public static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	public static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
	}

	public static int count(Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	public static void take(Inventory inv, Item item, int amount) {
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(item)) continue;
			int t = Math.min(amount, s.getCount());
			s.shrink(t);
			amount -= t;
		}
		inv.setChanged();
	}

	public static boolean accepted(Village v, UUID player, Mission m) {
		return v.accepted.contains(Village.key(player, m));
	}

	/** Progresso do jogador nesta missão (itens no inventário, blocos postos que ainda lá estão, monstros mortos). */
	public static int progress(ServerPlayer p, Village v, Mission m) {
		return switch (m.kind()) {
			case DELIVER -> count(p.getInventory(), item(m.target()));
			case DEFEND -> v.kills.getOrDefault(Village.key(p.getUUID(), m), 0);
			case DECORATE -> decorateSpots(p, v, m).size();
		};
	}

	/**
	 * As posições que contam para a missão de decorar deste jogador: postas por ele depois de aceitar, com o bloco ainda lá, e
	 * que NUNCA foram pagas antes (por ele ou por outro, nesta ou noutra missão — {@link Village#paidSpots}).
	 */
	static List<Long> decorateSpots(ServerPlayer p, Village v, Mission m) {
		Block b = block(m.target());
		long now = p.level().getGameTime();
		List<Long> out = new ArrayList<>();
		for (long l : v.placed.getOrDefault(Village.key(p.getUUID(), m), List.of())) {
			BlockPos pos = BlockPos.of(l);
			if (!v.paidSpots.isPaid(l, now) && p.level().isLoaded(pos) && p.level().getBlockState(pos).is(b)) out.add(l);
		}
		return out;
	}

	public static Result accept(ServerPlayer p, Village v, int slot) {
		if (slot < 0 || slot >= v.missions.size()) return Result.fail("tvilas.mission.none");
		Mission m = v.missions.get(slot);
		if (!v.accepted.add(Village.key(p.getUUID(), m))) return Result.fail("tvilas.mission.already");
		data(p.level()).changed();
		return Result.ok("tvilas.mission.accepted", describe(m));
	}

	/** Entrega: verifica, tira os itens (se for de trazer), paga, dá reputação e põe uma missão nova nessa casa. */
	public static Result deliver(ServerPlayer p, Village v, int slot) {
		if (slot < 0 || slot >= v.missions.size()) return Result.fail("tvilas.mission.none");
		Mission m = v.missions.get(slot);
		if (m.kind() != MissionRules.Kind.DELIVER && !accepted(v, p.getUUID(), m)) return Result.fail("tvilas.mission.not_accepted");
		int done = progress(p, v, m);
		if (!MissionRules.complete(m.template(), done)) return Result.fail("tvilas.mission.not_done", done, m.count());
		// A1: a tesouraria paga (com teto por dia) e cada jogador faz poucas por dia
		long reward = MissionRules.rewardCents(m.emeralds(), VilasConfig.get().emerald_value_cents);
		Civic c = missionDay(p.level(), v);
		VilasConfig cfg = VilasConfig.get();
		int today = c.doneToday.getOrDefault(p.getUUID(), 0);
		if (!MissionRules.mayPay(v.economy.treasury, c.paidToday, cfg.mission_daily_cap_cents, today, cfg.mission_daily_per_player, reward)) {
			if (today >= cfg.mission_daily_per_player) return Result.fail("tvilas.mission.daily_limit", cfg.mission_daily_per_player);
			if (v.economy.treasury < reward) return Result.fail("tvilas.mission.no_funds", money(reward), money(v.economy.treasury));
			return Result.fail("tvilas.mission.daily_cap", money(cfg.mission_daily_cap_cents));
		}
		if (m.kind() == MissionRules.Kind.DELIVER) take(p.getInventory(), item(m.target()), m.count());
		if (m.kind() == MissionRules.Kind.DECORATE) {
			// as posições pagas ficam marcadas: partir e repor, ou usá-las noutra missão, já não conta
			long now = p.level().getGameTime();
			List<Long> spots = decorateSpots(p, v, m);
			for (int i = 0; i < m.count() && i < spots.size(); i++) v.paidSpots.markPaid(spots.get(i), now);
		}
		v.economy.treasury -= reward;
		c.paidToday += reward;
		c.doneToday.merge(p.getUUID(), 1, Integer::sum);
		pay(p, m);
		addReputation(p.level(), v, p, m.reputation());
		v.forget(m);
		v.missions.set(slot, newMission(p.level(), v, slot, Set.of(m.template())));
		data(p.level()).changed();
		updateLevel(p.level(), v);
		return Result.ok("tvilas.mission.paid", reward(m.emeralds()), m.reputation());
	}

	/** As contas diárias das missões desta vila (recomeçam a cada dia de jogo). */
	static Civic missionDay(ServerLevel level, Village v) {
		Civic c = v.civic;
		long day = VillageEconomy.day(level);
		if (c.missionDay != day) {
			c.missionDay = day;
			c.paidToday = 0;
			c.doneToday.clear();
		}
		return c;
	}

	// ------------------------------------------------------------------ dinheiro (0.3.0: em CubCoin quando ele existe)

	/** Um preço em esmeraldas como se paga: «480 CB» com o CubCoin, «12 esmeraldas» sem ele. */
	public static Component price(int emeralds) {
		return TVilas.CUBCOIN
				? money(com.tcordeiro.vilas.logic.CoinRules.price(emeralds, VilasConfig.get().emerald_value_cents))
				: Component.translatable("tvilas.money.emeralds", emeralds);
	}

	/** A recompensa de uma missão como se recebe. */
	public static Component reward(int emeralds) {
		return price(emeralds);
	}

	/** Cobra um preço em esmeraldas: com o CubCoin em notas/moedas (com troco); sem ele em esmeraldas. False sem tirar nada se não chegar. */
	public static boolean payEmeralds(ServerPlayer p, int emeralds) {
		if (TVilas.CUBCOIN) return com.tcordeiro.vilas.compat.CubCoinPay.pay(p, com.tcordeiro.vilas.logic.CoinRules.price(emeralds, VilasConfig.get().emerald_value_cents));
		if (count(p.getInventory(), Items.EMERALD) < emeralds) return false;
		take(p.getInventory(), Items.EMERALD, emeralds);
		return true;
	}

	private static String cubcoinText(long cents) {
		return cents % 100 == 0 ? (cents / 100) + " CB" : String.format("%d,%02d CB", cents / 100, cents % 100);
	}

	/** Dinheiro da tesouraria/impostos: «12,50 CB» (com ou sem CubCoin a conta é em cêntimos; sem ele paga-se em esmeraldas). */
	public static Component money(long cents) {
		return Component.literal(cubcoinText(cents));
	}

	/**
	 * Paga a missão (o dinheiro já saiu da tesouraria): com o CubCoin, em CubCoins; sem ele, em esmeraldas (classe de
	 * compatibilidade só carregada se o CubCoin existir).
	 */
	static void pay(ServerPlayer p, Mission m) {
		if (TVilas.CUBCOIN) {
			com.tcordeiro.vilas.compat.CubCoinPay.give(p, MissionRules.rewardCents(m.emeralds(), VilasConfig.get().emerald_value_cents));
			return;
		}
		int left = m.emeralds();
		while (left > 0) {
			int n = Math.min(left, 64);
			p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, n), net.minecraft.util.Prediction.SERVER_ONLY);
			left -= n;
		}
	}

	/** «Trazer 24 Trigo», «Pôr 6 Lanternas», «Defender: 5 invasores». */
	public static Component describe(Mission m) {
		return switch (m.kind()) {
			case DELIVER -> Component.translatable("tvilas.mission.deliver", m.count(), item(m.target()).getDefaultInstance().getHoverName());
			case DECORATE -> Component.translatable("tvilas.mission.decorate", m.count(), block(m.target()).getName());
			case DEFEND -> Component.translatable("tvilas.mission.defend", m.count());
		};
	}

	/** Um monstro/invasor morto por um jogador dentro de uma vila em alarme conta para a missão de defesa dele. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		if (!(victim instanceof Enemy)) return;
		ServerLevel level = killer.level();
		Village v = villageAt(level, victim.blockPosition());
		if (v == null) return;
		// 0.3.0 (A1): só conta numa invasão a sério — o invasor é de uma invasão que está nesta vila (o alarme sozinho não chega:
		// monstros trazidos ou de uma quinta de monstros eram missões infinitas). Monstros de gerador nunca contam.
		var raid = victim instanceof net.minecraft.world.entity.raid.Raider r ? r.getCurrentRaid() : null;
		if (!MissionRules.defenseCounts(raid != null, raid != null && v.covers(raid.getCenter().getX(), raid.getCenter().getZ()),
				raid != null && raid.isOver(), victim.entityTags().contains(FROM_SPAWNER_TAG))) return;
		for (Mission m : v.missions)
			if (m.kind() == MissionRules.Kind.DEFEND && accepted(v, killer.getUUID(), m)) {
				v.kills.merge(Village.key(killer.getUUID(), m), 1, Integer::sum);
				data(level).changed();
			}
	}

	/** Etiqueta (entity tag) dos monstros nascidos de um gerador (spawner ou trial spawner): não contam para as missões de defesa. */
	public static final String FROM_SPAWNER_TAG = "tvilas_from_spawner";

	/**
	 * 0.3.0 (A1): um bloco de decoração já pago por uma missão é da vila — partido por um jogador, sai sem largar nada (não se
	 * pode levar e pagar outra vez noutro sítio). Devolve true se o bloco foi consumido (o jogo não o parte de novo).
	 */
	public static boolean consumeDecoration(ServerLevel level, BlockPos pos, Player player) {
		Village v = villageAt(level, pos);
		if (v == null || !v.paidSpots.isPaid(pos.asLong(), level.getGameTime())) return false;
		level.destroyBlock(pos, false, player);
		return true;
	}

	/** Um bloco posto por um jogador dentro da vila conta para a missão de decorar dele (cada posição só uma vez; as já pagas são filtradas em {@link #decorateSpots}). */
	public static void onPlaced(ServerPlayer p, BlockPos pos, BlockState state) {
		ServerLevel level = p.level();
		Village v = villageAt(level, pos);
		if (v == null) return;
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		for (Mission m : v.missions)
			if (m.kind() == MissionRules.Kind.DECORATE && m.target().equals(id.toString()) && accepted(v, p.getUUID(), m)) {
				List<Long> list = v.placed.computeIfAbsent(Village.key(p.getUUID(), m), k -> new ArrayList<>());
				if (!list.contains(pos.asLong())) list.add(pos.asLong());
				data(level).changed();
			}
	}
}
