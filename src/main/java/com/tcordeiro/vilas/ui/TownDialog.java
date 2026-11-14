package com.tcordeiro.vilas.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * O «quadro» da Prefeitura: um diálogo do jogo (ecrã com botões, enviado pelo servidor — sem código no cliente). Mostra o estado
 * da vila e as 3 missões; os botões correm os comandos {@code /vila ...} como se o jogador os escrevesse.
 */
public final class TownDialog {
	private TownDialog() {}

	public static void open(ServerPlayer p, ServerLevel level, Village v) {
		VillageManager.fillMissions(level, v);
		List<DialogBody> body = new ArrayList<>();
		int lots = VillageManager.lotsOf(level, v).size();
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.status", v.population, v.reputationOf(p.getUUID()), v.reputationSum(),
				lots, VillageRules.maxLots(v.level)), 300));
		var eco = v.economy;
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.treasury", VillageManager.money(eco.treasury),
				VillageManager.money(eco.collected), VillageManager.money(com.tcordeiro.vilas.village.VillageEconomy.debt(v, p.getUUID())),
				com.tcordeiro.vilas.logic.TaxRules.format(com.tcordeiro.vilas.village.VillageEconomy.taxRate(v).percent()), v.radius), 300));
		// 0.3.0: o que falta à vila (o Prefeito manda construir), logo no topo
		if (!v.civic.missing().isEmpty() && com.tcordeiro.vilas.VilasConfig.get().civic_buildings)
		{
			MutableComponent list = Component.empty();
			for (String k : v.civic.missing()) {
				if (!list.getSiblings().isEmpty()) list.append(", ");
				list.append(Component.translatable("tvilas.upgrade." + k));
			}
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.civic_missing", list).withStyle(ChatFormatting.GOLD), 300));
		}
		// 0.2.0: a taxa e porquê, obras, mercador, dívida/congelamento
		var rate = com.tcordeiro.vilas.village.VillageEconomy.taxRate(v);
		var cfg = com.tcordeiro.vilas.VilasConfig.get();
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.tax", com.tcordeiro.vilas.logic.TaxRules.format(rate.percent()),
				Component.translatable(rate.reason().key()), com.tcordeiro.vilas.logic.TaxRules.format(cfg.trade_tax_percent),
				com.tcordeiro.vilas.logic.TaxRules.format(cfg.tax_per_structure_percent), v.extra.structures,
				com.tcordeiro.vilas.logic.TaxRules.format(cfg.tax_cap_percent)).withStyle(ChatFormatting.GRAY), 300));
		for (var pr : v.extra.projects)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.project", Component.translatable(pr.kind.key()), pr.placed,
					pr.kind == com.tcordeiro.vilas.village.Project.Kind.WALL ? pr.nextColumn + "/" + pr.total : Integer.toString(pr.total)), 300));
		if (v.extra.wall == com.tcordeiro.vilas.village.Village.Extra.WALL_DONE)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.wall_done").withStyle(ChatFormatting.GRAY), 300));
		if (v.extra.lighting) body.add(new PlainMessage(Component.translatable("tvilas.dialog.lighting", v.extra.poles).withStyle(ChatFormatting.GRAY), 300));
		if (com.tcordeiro.vilas.village.TravelingMerchant.present(v))
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.merchant_here", v.extra.merchantArrived + cfg.merchant_stay_days), 300));
		else if (v.extra.merchantNext >= 0)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.merchant_next", v.extra.merchantNext).withStyle(ChatFormatting.GRAY), 300));
		long myDebt = com.tcordeiro.vilas.village.VillageEconomy.debt(v, p.getUUID());
		if (com.tcordeiro.vilas.village.Debts.frozen(v, p.getUUID()))
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.frozen").withStyle(ChatFormatting.AQUA), 300));
		var spent = com.tcordeiro.vilas.village.VillageEconomy.expenses(v);
		if (spent.isEmpty()) body.add(new PlainMessage(Component.translatable("tvilas.dialog.no_expenses").withStyle(ChatFormatting.GRAY), 300));
		for (int i = spent.size() - 1; i >= Math.max(0, spent.size() - 3); i--)
			body.add(new PlainMessage(spent.get(i).copy().withStyle(ChatFormatting.GRAY), 300));
		// 0.3.0: as missões pagam pela tesouraria (teto por dia) e cada um faz poucas por dia; o Prefeito levado
		var cfg3 = com.tcordeiro.vilas.VilasConfig.get();
		var civic = v.civic;
		boolean today = civic.missionDay == com.tcordeiro.vilas.village.VillageEconomy.day(level);
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.batch", com.tcordeiro.vilas.village.VillagerRequests.untilNext(level),
				VillageManager.money(today ? civic.paidToday : 0), VillageManager.money(cfg3.mission_daily_cap_cents)).withStyle(ChatFormatting.GRAY), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.prestige", civic.prestige, cfg3.prestige_per_resident, v.population,
				cfg3.population_cap).withStyle(ChatFormatting.GRAY), 300));
		if (!civic.batch.requests.isEmpty())
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.requests", civic.batch.requests.size()).withStyle(ChatFormatting.GRAY), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.mission_fund", VillageManager.money(civic.missionFund),
				VillageManager.money(cfg3.mission_fund_cap_cents)).withStyle(ChatFormatting.GRAY), 300));
		// 0.4.0: o que a vila acha de ti, a festa e os guardas
		int tier = com.tcordeiro.vilas.logic.LifeRules.villageTier(v.reputationOf(p.getUUID()));
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.rep_tier", Component.translatable(com.tcordeiro.vilas.logic.LifeRules.TIER_KEYS[tier])), 300));
		var life = civic.life;
		if (life.festa.day >= 0) body.add(new PlainMessage(Component.translatable("tvilas.dialog.festa", life.festa.day,
				Component.translatable("tvilas.festa." + life.festa.reason)).withStyle(ChatFormatting.LIGHT_PURPLE), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.guards", life.guards.size(),
				com.tcordeiro.vilas.logic.TownRules.guardPostsWithPolicy(com.tcordeiro.vilas.logic.LifeRules.guardPosts(v.level), com.tcordeiro.vilas.life.Election.policy(v)), VillageManager.money(com.tcordeiro.vilas.life.Care.wage())).withStyle(ChatFormatting.GRAY), 300));
		// 0.5.0: mercado, eleições, correio e ajudantes
		var town = life.town;
		long day0 = com.tcordeiro.vilas.village.VillageEconomy.day(level);
		if (com.tcordeiro.vilas.life.Market.today(level, v))
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.market_today", VillageManager.money(com.tcordeiro.vilas.logic.TownRules.marketPlayerCap(
					com.tcordeiro.vilas.logic.TownRules.MARKET.equals(town.election.policy), 1))).withStyle(ChatFormatting.GOLD), 300));
		else if (cfg.market_every_days > 0 && civic.meta.real)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.market_next", com.tcordeiro.vilas.life.Market.next(v, day0)).withStyle(ChatFormatting.GRAY), 300));
		if (town.election.open(day0))
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.election_open", town.election.candidates.size()).withStyle(ChatFormatting.AQUA), 300));
		if (!town.election.policy.isEmpty())
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.policy", town.election.mayorName,
					Component.translatable("tvilas.policy." + town.election.policy)).withStyle(ChatFormatting.GRAY), 300));
		else if (cfg.election_every_days > 0 && civic.meta.real)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.election_next",
					(day0 / cfg.election_every_days + 1) * cfg.election_every_days).withStyle(ChatFormatting.GRAY), 300));
		if (com.tcordeiro.vilas.life.Mail.available() && !town.mail.isEmpty())
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.mail", town.pending()).withStyle(ChatFormatting.GRAY), 300));
		if (town.activeHelpers() > 0)
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.helpers", town.activeHelpers(), com.tcordeiro.vilas.logic.TownRules.HELPERS_PER_VILLAGE)
					.withStyle(ChatFormatting.GRAY), 300));
		if (civic.mayorTaken) body.add(new PlainMessage(Component.translatable("tvilas.dialog.mayor_taken").withStyle(ChatFormatting.RED), 300));
		List<ActionButton> buttons = new ArrayList<>();
		for (int i = 0; i < v.missions.size(); i++) {
			Mission m = v.missions.get(i);
			boolean acc = VillageManager.accepted(v, p.getUUID(), m) || m.kind() == MissionRules.Kind.DELIVER;
			boolean doneIt = v.civic.batch.done.contains(com.tcordeiro.vilas.village.Batch.key(p.getUUID(), m.id()));
			int prog = VillageManager.progress(p, v, m);
			MutableComponent line = Component.translatable("tvilas.dialog.mission", i + 1, VillageManager.describe(m), VillageManager.reward(m.emeralds()), m.reputation());
			if (acc) line.append(Component.translatable("tvilas.dialog.progress", Math.min(prog, m.count()), m.count()).withStyle(ChatFormatting.GRAY));
			if (m.kind() == MissionRules.Kind.DEFEND) line.append(Component.translatable("tvilas.dialog.defend_hint").withStyle(ChatFormatting.GRAY));
			if (doneIt) line.append(Component.translatable("tvilas.dialog.mission_done").withStyle(ChatFormatting.GREEN));
			body.add(new PlainMessage(line, 300));
			if (doneIt) continue; // já feita neste lote: sem botão
			String cmd = acc ? "/vila missao entregar " + (i + 1) : "/vila missao aceitar " + (i + 1);
			Component label = acc ? Component.translatable("tvilas.dialog.deliver", i + 1) : Component.translatable("tvilas.dialog.accept", i + 1);
			buttons.add(button(label, cmd));
		}
		// 0.5.0: a missão temporária da festa (4.ª casa; o lote de 4 dias não muda)
		Mission decor = town.decor;
		if (decor != null) {
			boolean acc = VillageManager.accepted(v, p.getUUID(), decor);
			boolean doneIt = v.civic.batch.done.contains(com.tcordeiro.vilas.village.Batch.key(p.getUUID(), decor.id()));
			MutableComponent line = Component.translatable("tvilas.dialog.festa_mission", VillageManager.describe(decor), VillageManager.FESTA_DECOR_RADIUS,
					VillageManager.reward(decor.emeralds()), decor.reputation()).withStyle(ChatFormatting.LIGHT_PURPLE);
			if (acc) line.append(Component.translatable("tvilas.dialog.progress", Math.min(VillageManager.progress(p, v, decor), decor.count()), decor.count())
					.withStyle(ChatFormatting.GRAY));
			if (doneIt) line.append(Component.translatable("tvilas.dialog.mission_done").withStyle(ChatFormatting.GREEN));
			body.add(new PlainMessage(line, 300));
			if (!doneIt) buttons.add(button(acc ? Component.translatable("tvilas.dialog.festa_deliver") : Component.translatable("tvilas.dialog.festa_accept"),
					acc ? "/vila missao entregar 4" : "/vila missao aceitar 4"));
		}
		com.tcordeiro.vilas.life.crisis.Crises.board(p, level, v, body, buttons, TownDialog::button); // 0.6 crises
		if (town.election.open(day0)) buttons.add(button(Component.translatable("tvilas.dialog.vote"), "/vila eleicao"));
		if (myDebt > 0) buttons.add(button(Component.translatable("tvilas.dialog.pay_debt", VillageManager.money(myDebt)), "/vila divida pagar"));
		if (v.extra.wall == com.tcordeiro.vilas.village.Village.Extra.WALL_BUILDING)
			buttons.add(button(Component.translatable("tvilas.dialog.wall_cancel"), "/vila muralha cancelar"));
		else if (v.extra.wall == com.tcordeiro.vilas.village.Village.Extra.WALL_CANCELLED)
			buttons.add(button(Component.translatable("tvilas.dialog.wall_resume"), "/vila muralha retomar"));
		if (com.tcordeiro.vilas.VilasConfig.get().villager_life && life.meetingDay != com.tcordeiro.vilas.village.VillageEconomy.day(level))
			buttons.add(button(Component.translatable("tvilas.dialog.meeting"), "/vila reuniao"));
		Lot here = VillageManager.lotAt(level, p.getBlockX(), p.getBlockZ());
		if (here == null) {
			buttons.add(button(Component.translatable("tvilas.dialog.buy", VillageManager.price(VillageManager.lotPrice(v, p.getUUID()))), "/vila terreno comprar"));
		} else {
			body.add(new PlainMessage(Component.translatable("tvilas.dialog.lot_here", here.ownerName), 300));
			buttons.add(button(Component.translatable("tvilas.dialog.lot_info"), "/vila terreno info"));
		}
		body.add(new PlainMessage(Component.translatable("tvilas.dialog.friend_hint").withStyle(ChatFormatting.GRAY), 300));
		Component title = Component.translatable("tvilas.dialog.title", Component.translatable(VillageRules.levelKey(v.level)));
		CommonDialogData common = new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 2)));
	}

	private static ActionButton button(Component label, String command) {
		return new ActionButton(new CommonButtonData(label, 150), Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}
}
