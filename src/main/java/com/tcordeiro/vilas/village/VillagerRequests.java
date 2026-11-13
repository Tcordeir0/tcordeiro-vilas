package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.MissionRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * 0.3.0 — O lote de missões (a cada {@code mission_batch_days} dias o quadro do Prefeito troca TODAS as missões) e os pedidos
 * dos aldeões comuns: em cada lote, 1 em cada 4 aldeões com profissão (vanilla ou deste mod) pede o que a profissão usa — o
 * Ferreiro ferro, o Agricultor trigo ou batatas, o Bibliotecário papel ou livros… Mostra um «!» por cima da cabeça (só se a vila
 * tiver dinheiro para pagar). Clicar abre o pedido; Shift+clique (ou o botão «Trocar» do pedido) abre as trocas normais. Paga a
 * vila (fundo das missões, depois tesouraria), uma vez por jogador em cada lote, com as regras de mercado ×1,25.
 */
public final class VillagerRequests {
	private VillagerRequests() {}

	/** Etiqueta dos aldeões a quem o Vilas pôs o «!» (o nome dado por um jogador nunca é mexido). */
	public static final String TAG = "tvilas.request";
	/** Distância máxima para entregar a um aldeão. */
	public static final double REACH = 8;

	public static long batch(ServerLevel level) {
		return MissionRules.batchOf(VillageEconomy.day(level), VilasConfig.get().mission_batch_days);
	}

	/** Quanto falta para o lote seguinte: «2 d 5 h». */
	public static Component untilNext(ServerLevel level) {
		long t = MissionRules.ticksToNextBatch(level.getGameTime(), VillageEconomy.DAY, VilasConfig.get().mission_batch_days);
		long hours = t / 1000; // 1000 ticks = 1 hora do jogo
		return Component.translatable("tvilas.batch.left", hours / 24, hours % 24);
	}

	/**
	 * A cada 5 s (pela Mesa) e nos testes: se mudou o lote, troca TODAS as missões do quadro e escolhe os aldeões com pedidos; e
	 * mostra/esconde o «!» conforme a vila tem dinheiro para os pagar.
	 */
	public static void tick(ServerLevel level, Village v) {
		long now = batch(level);
		Batch b = v.civic.batch;
		if (b.id != now) {
			boolean first = b.id == Batch.NONE;
			b.id = now;
			b.done.clear();
			clearNames(level, b);
			b.requests.clear();
			if (!first) renewBoard(level, v);
			choose(level, v);
			VillageData.get(level).changed();
		} else if (b.requests.isEmpty() && !candidates(level, v).isEmpty()) {
			// ninguém tinha profissão no início do lote (vila acabada de gerar): escolhe agora, uma vez
			choose(level, v);
			VillageData.get(level).changed();
		}
		showNames(level, v);
	}

	/** Lote novo: as 3 missões do quadro são todas trocadas. */
	static void renewBoard(ServerLevel level, Village v) {
		for (int slot = 0; slot < v.missions.size(); slot++) {
			Mission old = v.missions.get(slot);
			VillageManager.setMission(level, v, slot, MissionRules.pick(slot, com.tcordeiro.vilas.logic.VillageRules.missionTier(v.level),
					level.getRandom().nextInt(1 << 20), java.util.Set.of(old.template())));
		}
		VillageManager.fillMissions(level, v);
	}

	public static String profession(Villager e) {
		return e.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
	}

	/** Os aldeões da vila que podem pedir (com profissão que pede, sem nome dado por jogadores, que não sejam Prefeito nem Construtor). */
	static List<Villager> candidates(ServerLevel level, Village v) {
		return level.getEntitiesOfClass(Villager.class, VillageManager.box(v, level), e -> e.isAlive() && v.covers(e.getBlockX(), e.getBlockZ())
				&& !e.isBaby() && !VillageManager.isMayor(e) && !com.tcordeiro.vilas.build.Builders.isBuilder(e)
				&& !e.getVillagerData().profession().is(VillagerProfession.NONE) && !e.getVillagerData().profession().is(VillagerProfession.NITWIT)
				&& (!e.hasCustomName() || e.entityTags().contains(TAG)) && MissionRules.REQUESTS.containsKey(profession(e)));
	}

	/** Escolhe 1 em cada 4 aldeões com profissão para fazer um pedido neste lote. */
	public static void choose(ServerLevel level, Village v) {
		if (!v.civic.meta.real) return; // revisão de segurança: sem vila de verdade, sem pedidos
		List<Villager> all = new ArrayList<>(candidates(level, v));
		java.util.Collections.shuffle(all, new java.util.Random(level.getRandom().nextLong()));
		int n = MissionRules.requesters(all.size());
		for (int i = 0; i < n; i++) {
			Villager e = all.get(i);
			MissionRules.Template t = MissionRules.request(profession(e), level.getRandom().nextInt(1 << 20));
			if (t != null) v.civic.batch.requests.put(e.getUUID(), Mission.of(++v.missionCounter, t));
		}
	}

	/** O «!» por cima dos aldeões com pedido — só se a vila tiver dinheiro (fundo + tesouraria) para o pagar. */
	static void showNames(ServerLevel level, Village v) {
		Batch b = v.civic.batch;
		long available = v.economy.treasury + v.civic.missionFund;
		for (var e : b.requests.entrySet()) {
			if (!(level.getEntity(e.getKey()) instanceof Villager vi) || !vi.isAlive()) continue;
			boolean show = v.civic.meta.real && available >= MissionRules.rewardCents(e.getValue().emeralds(), VilasConfig.get().emerald_value_cents);
			if (show && !vi.entityTags().contains(TAG)) {
				vi.addTag(TAG);
				vi.setCustomName(Component.literal("!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				vi.setCustomNameVisible(true);
				b.named.add(vi.getUUID());
			} else if (!show && vi.entityTags().contains(TAG)) unname(vi, b);
		}
	}

	static void unname(Villager vi, Batch b) {
		vi.removeTag(TAG);
		vi.setCustomName(null);
		vi.setCustomNameVisible(false);
		b.named.remove(vi.getUUID());
	}

	static void clearNames(ServerLevel level, Batch b) {
		for (UUID id : List.copyOf(b.named)) if (level.getEntity(id) instanceof Villager vi) unname(vi, b);
	}

	/** Um aldeão carregou com o «!» de um lote que já acabou: tira-o. */
	public static void onLoaded(ServerLevel level, Entity e) {
		if (!(e instanceof Villager vi) || !vi.entityTags().contains(TAG)) return;
		for (Village v : VillageData.get(level).villages) if (v.civic.batch.requests.containsKey(vi.getUUID())) return;
		vi.removeTag(TAG);
		vi.setCustomName(null);
		vi.setCustomNameVisible(false);
	}

	/** A vila em que este aldeão tem um pedido, ou null. */
	public static Village villageOf(ServerLevel level, UUID villager) {
		for (Village v : VillageData.get(level).villages) if (v.civic.batch.requests.containsKey(villager)) return v;
		return null;
	}

	/**
	 * Clique num aldeão: com pedido (e «!» visível) e sem Shift, abre o pedido; Shift+clique, ou quem já o fez neste lote, troca
	 * normalmente (PASS).
	 */
	public static InteractionResult onUse(ServerPlayer p, ServerLevel level, Villager vi, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || p.isShiftKeyDown() || !vi.entityTags().contains(TAG)) return InteractionResult.PASS;
		Village v = villageOf(level, vi.getUUID());
		if (v == null || v.civic.batch.done.contains(Batch.key(p.getUUID(), vi.getUUID()))) return InteractionResult.PASS;
		open(p, level, v, vi);
		return InteractionResult.SUCCESS;
	}

	/** O ecrã do pedido (diálogo do servidor): o que pede, quanto paga, quanto tem; botões Entregar e Trocar. */
	public static void open(ServerPlayer p, ServerLevel level, Village v, Villager vi) {
		Mission m = v.civic.batch.requests.get(vi.getUUID());
		if (m == null) return;
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(Component.translatable("tvilas.request.asks", vi.getVillagerData().profession().value().name(),
				VillageManager.describe(m)), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.request.pays", VillageManager.reward(m.emeralds()), m.reputation()), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.request.have", Math.min(VillageManager.count(p.getInventory(), VillageManager.item(m.target())), m.count()),
				m.count()).withStyle(ChatFormatting.GRAY), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.request.once", untilNext(level)).withStyle(ChatFormatting.GRAY), 300));
		List<ActionButton> buttons = new ArrayList<>();
		buttons.add(button(Component.translatable("tvilas.request.deliver"), "/vila pedido entregar " + vi.getUUID()));
		buttons.add(button(Component.translatable("tvilas.request.trade"), "/vila pedido trocar " + vi.getUUID()));
		CommonDialogData common = new CommonDialogData(Component.translatable("tvilas.request.title"), Optional.empty(), true, false, DialogAction.CLOSE,
				body, List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 2)));
	}

	static ActionButton button(Component label, String command) {
		return new ActionButton(new CommonButtonData(label, 150), Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}

	static Villager near(ServerPlayer p, UUID id) {
		return p.level().getEntity(id) instanceof Villager vi && vi.isAlive() && vi.distanceToSqr(p) <= REACH * REACH ? vi : null;
	}

	/** O botão «Trocar»: as trocas normais do aldeão (como Shift+clique). */
	public static VillageManager.Result trade(ServerPlayer p, UUID id) {
		Villager vi = near(p, id);
		if (vi == null) return VillageManager.Result.fail("tvilas.request.too_far");
		vi.mobInteract(p, InteractionHand.MAIN_HAND);
		return new VillageManager.Result(true, Component.empty());
	}

	/** Entrega o pedido: tira os itens, paga da vila (uma vez por jogador neste lote), dá reputação. */
	public static VillageManager.Result deliver(ServerPlayer p, UUID id) {
		ServerLevel level = p.level();
		Villager vi = near(p, id);
		if (vi == null) return VillageManager.Result.fail("tvilas.request.too_far");
		Village v = villageOf(level, id);
		Mission m = v == null ? null : v.civic.batch.requests.get(id);
		if (m == null) return VillageManager.Result.fail("tvilas.request.none");
		int have = VillageManager.count(p.getInventory(), VillageManager.item(m.target()));
		if (have < m.count()) return VillageManager.Result.fail("tvilas.mission.not_done", have, m.count());
		String key = Batch.key(p.getUUID(), id);
		long reward = MissionRules.rewardCents(m.emeralds(), VilasConfig.get().emerald_value_cents);
		VillageManager.Result no = VillageManager.mayPay(level, v, v.civic.batch.done.contains(key), reward);
		if (no != null) return no;
		VillageManager.take(p.getInventory(), VillageManager.item(m.target()), m.count());
		VillageManager.spend(level, v, reward);
		v.civic.batch.done.add(key);
		VillageManager.pay(p, m);
		VillageManager.addReputation(level, v, p, m.reputation());
		VillageManager.missionDone(level, v);
		com.tcordeiro.vilas.life.Talk.fact(level, v, vi.blockPosition(), "request", p.getGameProfile().name(), profession(vi)); // 0.4.0: fofoca
		showNames(level, v);
		VillageData.get(level).changed();
		return VillageManager.Result.ok("tvilas.mission.paid", VillageManager.reward(m.emeralds()), m.reputation());
	}
}
