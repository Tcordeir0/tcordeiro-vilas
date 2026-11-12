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
		List<ActionButton> buttons = new ArrayList<>();
		for (int i = 0; i < v.missions.size(); i++) {
			Mission m = v.missions.get(i);
			boolean acc = VillageManager.accepted(v, p.getUUID(), m) || m.kind() == MissionRules.Kind.DELIVER;
			int prog = VillageManager.progress(p, v, m);
			MutableComponent line = Component.translatable("tvilas.dialog.mission", i + 1, VillageManager.describe(m), m.emeralds(), m.reputation());
			if (acc) line.append(Component.translatable("tvilas.dialog.progress", Math.min(prog, m.count()), m.count()).withStyle(ChatFormatting.GRAY));
			if (m.kind() == MissionRules.Kind.DEFEND) line.append(Component.translatable("tvilas.dialog.defend_hint").withStyle(ChatFormatting.GRAY));
			body.add(new PlainMessage(line, 300));
			String cmd = acc ? "/vila missao entregar " + (i + 1) : "/vila missao aceitar " + (i + 1);
			Component label = acc ? Component.translatable("tvilas.dialog.deliver", i + 1) : Component.translatable("tvilas.dialog.accept", i + 1);
			buttons.add(button(label, cmd));
		}
		if (myDebt > 0) buttons.add(button(Component.translatable("tvilas.dialog.pay_debt", VillageManager.money(myDebt)), "/vila divida pagar"));
		if (v.extra.wall == com.tcordeiro.vilas.village.Village.Extra.WALL_BUILDING)
			buttons.add(button(Component.translatable("tvilas.dialog.wall_cancel"), "/vila muralha cancelar"));
		else if (v.extra.wall == com.tcordeiro.vilas.village.Village.Extra.WALL_CANCELLED)
			buttons.add(button(Component.translatable("tvilas.dialog.wall_resume"), "/vila muralha retomar"));
		Lot here = VillageManager.lotAt(level, p.getBlockX(), p.getBlockZ());
		if (here == null) {
			buttons.add(button(Component.translatable("tvilas.dialog.buy", VillageManager.lotPrice(v, p.getUUID())), "/vila terreno comprar"));
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
