package com.tcordeiro.vilas.life.election;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.life.Festa;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.Places;
import com.tcordeiro.vilas.life.Stage;
import com.tcordeiro.vilas.life.TownData;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.registry.VilasItems;
import com.tcordeiro.vilas.village.Batch;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.6 eleições — Os bónus do voto, ligados ao mundo (as contas estão em {@link VoteRules}):
 * <ul>
 * <li><b>reputação</b> por votar, uma vez por eleição;</li>
 * <li><b>apoiador</b> (votou no vencedor), durante o mandato: um bolo na posse (ITEM, nunca dinheiro); −5 % em todas as trocas com
 * os aldeões da vila (reembolso da tesouraria, onde já se aplica o da festa, com tetos); um pedido «!» exclusivo por lote;</li>
 * <li><b>promessas</b> para quem votou na proposta vencedora: −20 % no imposto do terreno, o posto de guarda novo perto do terreno,
 * banca própria no mercado semanal (as vendas lá não pagam a taxa) e convite VIP nas festas (+5 % dentro dos tetos da festa);</li>
 * <li>o broche <b>«Eu votei»</b> para todos os que votaram, com o nome da vila e do eleito.</li>
 * </ul>
 * O dinheiro sai SEMPRE da tesouraria (nunca é criado) e os itens por entregar ficam guardados na vila até o jogador aparecer.
 */
public final class VoteBonus {
	private VoteBonus() {}

	public static final String ICON = "🗳";
	public static final String OWN_STALL_TAG = "tvilas.own_stall";

	static TownData.Election election(Village v) {
		return v.civic.life.town.election;
	}

	public static Mandate mandate(Village v) {
		return election(v).mandate;
	}

	/** REGRA no jogo: é apoiador do mandato em vigor? */
	public static boolean supporter(Village v, UUID player) {
		TownData.Election e = election(v);
		return VoteRules.supporter(e.mandate.supporters.contains(player), e.mandate.term, e.held);
	}

	/** REGRA no jogo: a promessa {@code policy} vale para este jogador? */
	public static boolean promised(Village v, UUID player, String policy) {
		return VoteRules.promised(supporter(v, player), election(v).mandate.policy, policy);
	}

	// ---------------------------------------------------------------- votar: reputação

	/** Depois de um voto válido: reputação com os aldeões da vila, uma vez por eleição. Devolve a reputação dada. */
	public static int onVote(ServerPlayer p, Village v) {
		TownData.Election e = election(v);
		Long given = e.mandate.repGiven.get(p.getUUID());
		int rep = VoteRules.voteReputation(given != null && given == e.day);
		if (rep <= 0) return 0;
		if (!e.mandate.repGiven.containsKey(p.getUUID()) && e.mandate.repGiven.size() >= Mandate.MAX) return 0;
		e.mandate.repGiven.put(p.getUUID(), e.day);
		VillageManager.addReputation(p.level(), v, p, rep);
		return rep;
	}

	// ---------------------------------------------------------------- contar: empate e posse

	/** O que a vila acha de cada candidato: opiniões dos aldeões sobre ele (Talk) + reputação de quem votou nele. */
	public static long[] likes(Village v, TownData.Election e) {
		long[] out = new long[e.candidates.size()];
		LifeData d = v.civic.life;
		for (int i = 0; i < out.length; i++) {
			String id = e.candidates.get(i).villager().toString();
			int op = 0;
			for (var o : d.opinions.entrySet()) {
				String[] pair = o.getKey().split("\\|");
				if (pair.length == 2 && (pair[0].equals(id) || pair[1].equals(id))) op += o.getValue();
			}
			long rep = 0;
			for (var vote : e.votes.entrySet()) if (vote.getValue() == i) rep += Math.max(0, v.reputationOf(vote.getKey()));
			out[i] = VoteRules.likes(op, rep);
		}
		return out;
	}

	/** A semente do sorteio (desempate final): a mesma vila e a mesma eleição dão sempre o mesmo resultado. */
	public static long seed(Village v, TownData.Election e) {
		return v.id.getMostSignificantBits() ^ (v.id.getLeastSignificantBits() * 31) ^ (e.day * 1_000_003L) ^ e.held;
	}

	/**
	 * Posse (chamado por {@code Election.count} com os votos ainda lá): começa o mandato, guarda os apoiadores, prepara o bolo de cada
	 * apoiador e o broche de cada eleitor, e entrega já a quem está ligado.
	 */
	public static void onWin(ServerLevel level, Village v, TownData.Election e, int winner, String mayorName, long day) {
		Mandate m = e.mandate;
		m.begin(e.held, day, e.policy, mayorName);
		for (var vote : e.votes.entrySet()) {
			UUID who = vote.getKey();
			boolean sup = vote.getValue() == winner;
			if (sup && m.supporters.size() < Mandate.MAX) {
				m.supporters.add(who);
				m.give(who, gift());
			}
			m.give(who, badge(v, mayorName, e.held));
		}
		for (UUID who : List.copyOf(m.pending.keySet())) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
			if (p != null) deliver(level, v, p);
		}
		for (UUID who : m.supporters) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
			if (p == null) continue;
			VillageNews.tell(p, ICON, "tvilas.vote.supporter", mayorName);
			if (TownRules.FESTAS.equals(m.policy)) VillageNews.tell(p, "🎉", "tvilas.vote.vip_invite");
		}
		VillageData.get(level).changed();
	}

	/** O presente da posse para cada apoiador: um bolo (um ITEM, nunca dinheiro). */
	public static ItemStack gift() {
		return new ItemStack(Items.CAKE);
	}

	/** O broche «Eu votei»: a vila (nível e coordenadas da Mesa), o eleito e o número da eleição na descrição. */
	public static ItemStack badge(Village v, String mayorName, int term) {
		ItemStack s = new ItemStack(VoteItems.I_VOTED);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("tvilas.vote.badge.town", Component.translatable(VillageRules.levelKey(v.level)), v.center.getX(), v.center.getZ())
				.withStyle(s2 -> s2.withItalic(false).withColor(ChatFormatting.GRAY)));
		lines.add(Component.translatable("tvilas.vote.badge.elected", mayorName).withStyle(s2 -> s2.withItalic(false).withColor(ChatFormatting.GOLD)));
		lines.add(Component.translatable("tvilas.vote.badge.term", term).withStyle(s2 -> s2.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
		s.set(DataComponents.LORE, new ItemLore(lines));
		return s;
	}

	/** Entrega os itens guardados a este jogador (uma vez: saem da lista ao entregar). Devolve quantos itens deu. */
	public static int deliver(ServerLevel level, Village v, ServerPlayer p) {
		Mandate m = mandate(v);
		List<ItemStack> items = m.pending.remove(p.getUUID());
		if (items == null || items.isEmpty()) return 0;
		int n = 0;
		for (ItemStack s : items) {
			if (s.isEmpty()) continue;
			p.getInventory().placeItemBackInInventory(s.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			n++;
		}
		if (n > 0) VillageNews.tell(p, ICON, "tvilas.vote.delivered", n);
		VillageData.get(level).changed();
		return n;
	}

	// ---------------------------------------------------------------- trocas: −5 % do apoiador e o extra VIP da festa

	/**
	 * Uma troca com um aldeão da vila (chamado onde já se aplica o reembolso da festa): o apoiador recebe 5 % de volta da TESOURARIA
	 * (teto por jogador e por mandato); com a promessa «mais festas», durante a festa, mais 5 % dentro dos tetos da festa. Devolve o
	 * total pago agora.
	 */
	public static long onTrade(ServerPlayer p, Village v, MerchantOffer offer) {
		if (!supporter(v, p.getUUID())) return 0;
		ServerLevel level = p.level();
		var mo = CoinTrades.of(offer);
		long unit = VilasConfig.get().emerald_value_cents;
		long paid = (long) (mo.tvilas$emeraldsA() + mo.tvilas$emeraldsB()) * unit;
		boolean coins = TVilas.CUBCOIN;
		long out = 0;
		Mandate m = mandate(v);
		long owed = m.owed.getOrDefault(p.getUUID(), 0L);
		long rebate = VoteRules.rebate(true, paid, VoteRules.SUPPORTER_PERCENT, m.rebates.getOrDefault(p.getUUID(), 0L) + owed,
				VoteRules.supporterPlayerCap(coins ? 1 : unit), VoteRules.SUPPORTER_BUDGET - m.spent - owed, v.economy.treasury - owed);
		if (rebate > 0) {
			long due = owed + rebate;
			long now = LifeRules.payable(due, coins ? 1 : unit);
			LifeData.putCapped(m.owed, p.getUUID(), due - now);
			if (now > 0) {
				v.economy.treasury -= now;
				m.spent += now;
				LifeData.putCapped(m.rebates, p.getUUID(), m.rebates.getOrDefault(p.getUUID(), 0L) + now);
				give(p, now, unit, coins);
				VillageNews.tell(p, ICON, "tvilas.vote.rebate", VillageManager.money(now));
				out += now;
			}
		}
		if (promised(v, p.getUUID(), TownRules.FESTAS) && Festa.active(level, v)) out += vip(p, v, paid, unit, coins);
		VillageData.get(level).changed();
		return out;
	}

	/** O extra VIP na festa: conta nos MESMOS tetos da festa (por jogador, por festa e a tesouraria). */
	static long vip(ServerPlayer p, Village v, long paid, long unit, boolean coins) {
		LifeData.Festa f = v.civic.life.festa;
		long owed = f.owed.getOrDefault(p.getUUID(), 0L);
		long rebate = VoteRules.rebate(true, paid, VoteRules.VIP_EXTRA_PERCENT, f.rebates.getOrDefault(p.getUUID(), 0L) + owed,
				LifeRules.festaPlayerCap(coins ? 1 : unit), LifeRules.FESTA_BUDGET - f.spent - owed, v.economy.treasury - owed);
		if (rebate <= 0) return 0;
		long due = owed + rebate;
		long now = LifeRules.payable(due, coins ? 1 : unit);
		f.owed.put(p.getUUID(), due - now);
		if (now <= 0) return 0;
		v.economy.treasury -= now;
		f.spent += now;
		f.rebates.merge(p.getUUID(), now, Long::sum);
		give(p, now, unit, coins);
		VillageNews.tell(p, "🎉", "tvilas.vote.vip_rebate", VillageManager.money(now));
		return now;
	}

	static void give(ServerPlayer p, long cents, long unit, boolean coins) {
		if (coins) com.tcordeiro.vilas.compat.CubCoinPay.give(p, cents);
		else p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, (int) (cents / unit)), net.minecraft.util.Prediction.SERVER_ONLY);
	}

	// ---------------------------------------------------------------- promessas

	/** «Imposto menor»: o imposto do terreno deste dono, com −20 % se votou na proposta (só durante o mandato). */
	public static long lotTax(Village v, UUID owner, long taxCents) {
		return VoteRules.lotTax(taxCents, promised(v, owner, TownRules.TAX));
	}

	/**
	 * «Mais guardas»: o posto novo fica perto do terreno de um apoiador da proposta (um por dia, à vez). Mete-o na lista na posição
	 * {@code policyPosts − 1}, para ser um dos postos ocupados. Devolve o posto (ou null).
	 */
	public static BlockPos guardPost(ServerLevel level, Village v, List<BlockPos> posts, int policyPosts) {
		if (!TownRules.GUARDS.equals(mandate(v).policy)) return null;
		List<Lot> lots = new ArrayList<>();
		for (Lot l : VillageManager.lotsOf(level, v)) if (promised(v, l.owner, TownRules.GUARDS)) lots.add(l);
		if (lots.isEmpty()) return null;
		lots.sort((a, b) -> a.id.compareTo(b.id));
		Lot lot = lots.get((int) Math.floorMod(VillageEconomy.day(level), (long) lots.size()));
		int x = (lot.area.minX() + lot.area.maxX()) / 2, z = (lot.area.minZ() + lot.area.maxZ()) / 2;
		BlockPos post = new BlockPos(x, Places.ground(level, x, z, v.center.getY()), z);
		posts.add(Math.min(Math.max(0, policyPosts - 1), posts.size()), post);
		return post;
	}

	/** «Mercado maior»: monta as bancas próprias (até 2) dos apoiadores da proposta, com o nome deles. Entram na lista do mercado. */
	public static int marketStalls(ServerLevel level, Village v, List<Entity> stalls, Places.Spot plaza) {
		if (!TownRules.MARKET.equals(mandate(v).policy) || plaza == null) return 0;
		List<Lot> owners = new ArrayList<>();
		for (Lot l : VillageManager.lotsOf(level, v)) {
			boolean dup = false;
			for (Lot o : owners) dup |= o.owner.equals(l.owner);
			if (!dup && promised(v, l.owner, TownRules.MARKET)) owners.add(l);
		}
		owners.sort((a, b) -> a.owner.compareTo(b.owner));
		int n = 0;
		for (int k = 0; k < 8 && n < Math.min(VoteRules.OWN_STALLS, owners.size()); k++) {
			double a = Math.PI / 8 + k * Math.PI / 4;
			int x = plaza.center.getX() + (int) Math.round(Math.cos(a) * 3), z = plaza.center.getZ() + (int) Math.round(Math.sin(a) * 3);
			BlockPos p = new BlockPos(x, Places.ground(level, x, z, plaza.center.getY()), z);
			if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
			boolean apart = true;
			for (Entity e : stalls) apart &= e.blockPosition().distSqr(p) >= 4;
			if (!apart) continue;
			double dx = plaza.center.getX() - p.getX(), dz = plaza.center.getZ() - p.getZ();
			Entity e = Stage.spawn(level, VilasItems.MARKET_STALL, x + 0.5, p.getY() + 1.0, z + 0.5, 2.0f, (float) Math.toDegrees(Math.atan2(-dx, dz)));
			if (e == null) continue;
			Lot owner = owners.get(n);
			e.setCustomName(Component.translatable("tvilas.vote.stall", owner.ownerName).withStyle(ChatFormatting.GOLD));
			e.setCustomNameVisible(true);
			e.addTag(OWN_STALL_TAG);
			stalls.add(e);
			n++;
		}
		return n;
	}

	/** «Mercado maior»: a venda entre jogadores na banca própria (perto da Praça, no mercado) não paga a taxa das trocas. */
	public static long saleTax(ServerLevel level, Village v, BlockPos where, UUID seller, long taxCents) {
		boolean own = promised(v, seller, TownRules.MARKET) && com.tcordeiro.vilas.life.Market.active(level, v) && nearPlaza(level, v, where);
		return VoteRules.ownStallSaleTax(taxCents, own);
	}

	static boolean nearPlaza(ServerLevel level, Village v, BlockPos where) {
		Places.Spot plaza = com.tcordeiro.vilas.life.Life.places(level, v).plaza();
		return plaza != null && where.distSqr(plaza.center) <= (double) TownRules.MARKET_RADIUS * TownRules.MARKET_RADIUS;
	}

	// ---------------------------------------------------------------- pedidos «!» exclusivos

	/**
	 * Uma vez por dia: cada apoiador do mandato em vigor ganha UM pedido exclusivo por lote (no máximo 3 por vila), de um aldeão da
	 * vila que ainda não tenha pedido. Paga-se como os outros (fundo e tesouraria, teto do dia, uma vez). Devolve quantos criou.
	 */
	public static int reserveRequests(ServerLevel level, Village v) {
		Mandate m = mandate(v);
		Batch b = v.civic.batch;
		if (b.id == Batch.NONE || !v.civic.meta.real) return 0;
		if (m.reservedBatch != b.id) {
			m.reserved.clear();
			m.reservedBatch = b.id;
		}
		m.reserved.keySet().removeIf(id -> !b.requests.containsKey(id));
		if (!m.current(election(v).held)) return 0;
		List<Villager> free = new ArrayList<>();
		for (Villager vi : VillagerRequests.candidates(level, v)) if (!b.requests.containsKey(vi.getUUID())) free.add(vi);
		free.sort((a, c) -> a.getUUID().compareTo(c.getUUID()));
		int made = 0;
		for (UUID who : m.supporters) {
			if (free.isEmpty()) break;
			if (!VoteRules.mayReserve(supporter(v, who), m.reserved.containsValue(who), m.reserved.size())) continue;
			Villager vi = free.remove(0);
			MissionRules.Template t = MissionRules.request(VillagerRequests.profession(vi), level.getRandom().nextInt(1 << 20));
			if (t == null) continue;
			b.requests.put(vi.getUUID(), Mission.of(++v.missionCounter, t));
			m.reserved.put(vi.getUUID(), who);
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
			if (p != null) VillageNews.tell(p, ICON, "tvilas.vote.reserved", vi.getVillagerData().profession().value().name());
			made++;
		}
		if (made > 0) VillageData.get(level).changed();
		return made;
	}

	/** REGRA no jogo: este jogador pode abrir/entregar o pedido deste aldeão? (os exclusivos só o apoiador a quem foram dados) */
	public static boolean mayTake(Village v, UUID villager, UUID player) {
		Mandate m = mandate(v);
		UUID owner = m.current(election(v).held) ? m.reserved.get(villager) : null;
		return VoteRules.mayTakeRequest(owner, player);
	}

	/** Dono do pedido exclusivo deste aldeão (null = pedido de todos). */
	public static UUID reservedFor(Village v, UUID villager) {
		Mandate m = mandate(v);
		return m.current(election(v).held) ? m.reserved.get(villager) : null;
	}

	// ---------------------------------------------------------------- quadro

	/** Quanto falta para fechar a votação: «2 d 5 h». */
	public static Component timeLeft(ServerLevel level, Village v) {
		long t = VoteRules.ticksLeft(election(v).day, level.getGameTime(), VillageEconomy.DAY);
		long hours = (t + 999) / 1000;
		return Component.translatable("tvilas.vote.left", hours / 24, hours % 24);
	}

	/** As linhas do quadro de votar sobre os bónus deste jogador (apoiador e promessa). */
	public static List<Component> lines(Village v, UUID player) {
		List<Component> out = new ArrayList<>();
		Mandate m = mandate(v);
		if (!supporter(v, player)) return out;
		out.add(Component.translatable("tvilas.vote.you_support", m.mayor).withStyle(ChatFormatting.GREEN));
		out.add(Component.translatable("tvilas.vote.promise." + m.policy).withStyle(ChatFormatting.GREEN));
		return out;
	}

	/** Para os testes: os apoiadores e as promessas por nome. */
	public static Map<UUID, List<ItemStack>> pending(Village v) {
		return mandate(v).pending;
	}
}
