package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * 0.5.0 — Eleições: a cada {@code election_every_days} dias (8; 0 = sem eleições) há dia de votar. De manhã o Prefeito anuncia 2 ou
 * 3 candidatos (aldeões da vila com profissão), cada um com uma PROPOSTA: imposto menor (−1 ponto na taxa das trocas), mais guardas
 * (+1 posto, pago pela tesouraria como os outros), mais festas (uma festa extra no mandato, com os tetos de sempre) ou mercado maior
 * (teto do reembolso por jogador +50 %). Votam no quadro os jogadores com TERRENO na vila, um voto cada. No dia seguinte conta-se: o
 * vencedor passa a ser o Prefeito (troca de posto com o Prefeito de antes, se os dois estiverem carregados) e a proposta fica em vigor
 * até à eleição seguinte. Nenhuma proposta põe dinheiro em lado nenhum: só mudam taxas e postos.
 */
public final class Election {
	private Election() {}

	static int every() {
		return VilasConfig.get().election_every_days;
	}

	public static String policy(Village v) {
		return v.civic.life.town.election.policy;
	}

	public static boolean hasLot(ServerLevel level, Village v, UUID player) {
		for (Lot l : VillageManager.lotsOf(level, v)) if (l.owner.equals(player)) return true;
		return false;
	}

	/** Uma vez por dia: conta os votos da eleição de ontem; no dia de votar, apresenta os candidatos. */
	public static void daily(ServerLevel level, Village v, long day) {
		TownData.Election e = v.civic.life.town.election;
		if (e.day >= 0 && e.day < day) count(level, v);
		if (TownRules.electionDay(day, every()) && e.last != day && v.civic.meta.real && !VillageManager.lotsOf(level, v).isEmpty()) open(level, v, day);
		// «mais festas»: a festa extra do mandato, dois dias depois de ganhar
		if (TownRules.FESTAS.equals(e.policy) && !e.festaDone && e.last >= 0 && day >= e.last + 2) {
			if (Festa.schedule(level, v, day + 1, "election")) e.festaDone = true;
		}
	}

	/** Abre a votação: 2 ou 3 candidatos com propostas diferentes. */
	public static boolean open(ServerLevel level, Village v, long day) {
		TownData.Election e = v.civic.life.town.election;
		List<Villager> pool = new ArrayList<>();
		for (Villager o : Life.villagers(level, v)) {
			var p = o.getVillagerData().profession();
			if (!o.isBaby() && !p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT) && !VillageManager.isMayor(o) && !Builders.isBuilder(o))
				pool.add(o);
		}
		if (pool.size() < 2) return false;
		java.util.Random rnd = new java.util.Random(level.getRandom().nextLong());
		Collections.shuffle(pool, rnd);
		List<String> policies = new ArrayList<>(TownRules.POLICIES);
		Collections.shuffle(policies, rnd);
		int n = Math.min(pool.size(), pool.size() >= 3 ? 3 : 2);
		e.candidates.clear();
		e.votes.clear();
		for (int i = 0; i < n; i++) {
			Villager c = pool.get(i);
			e.candidates.add(new TownData.Candidate(c.getUUID(), name(c), policies.get(i)));
			Balloons.show(c, Balloons.VOTE, 80);
		}
		e.day = day;
		e.last = day;
		VillageNews.announce(level, v, "🗳", "tvilas.news.election_open", n);
		Talk.fact(level, v, null, "election", e.candidates.get(0).name(), "#tvilas.policy." + e.candidates.get(0).policy());
		VillageData.get(level).changed();
		opened++;
		return true;
	}

	public static int opened, counted;

	/** O nome com que o candidato aparece: o nome dado (etiqueta) ou a profissão. */
	static String name(Villager c) {
		if (c.hasCustomName() && !c.entityTags().contains(com.tcordeiro.vilas.village.VillagerRequests.TAG)) return c.getCustomName().getString();
		return c.getVillagerData().profession().value().name().getString();
	}

	/** O voto de um jogador (REGRA {@link TownRules#mayVote}: com terreno, uma vez, no dia de votar). */
	public static VillageManager.Result vote(ServerPlayer p, Village v, int choice) {
		ServerLevel level = p.level();
		TownData.Election e = v.civic.life.town.election;
		boolean open = e.open(VillageEconomy.day(level));
		if (!open) return VillageManager.Result.fail("tvilas.election.closed");
		if (choice < 0 || choice >= e.candidates.size()) return VillageManager.Result.fail("tvilas.election.unknown");
		if (!TownRules.mayVote(hasLot(level, v, p.getUUID()), e.votes.containsKey(p.getUUID()), open)) {
			return e.votes.containsKey(p.getUUID()) ? VillageManager.Result.fail("tvilas.election.already") : VillageManager.Result.fail("tvilas.election.no_lot");
		}
		e.votes.put(p.getUUID(), choice);
		TownData.Candidate c = e.candidates.get(choice);
		if (level.getEntity(c.villager()) instanceof Villager cv) {
			Balloons.show(cv, Balloons.HEART, 40);
			Gestures.cheer(cv);
		}
		VillageData.get(level).changed();
		return VillageManager.Result.ok("tvilas.election.voted", c.name(), Component.translatable("tvilas.policy." + c.policy()));
	}

	/** Conta os votos: o vencedor passa a Prefeito e a proposta dele fica em vigor. Sem votos, nada muda. */
	public static int count(ServerLevel level, Village v) {
		TownData.Election e = v.civic.life.town.election;
		int[] votes = new int[e.candidates.size()];
		for (int c : e.votes.values()) if (c >= 0 && c < votes.length) votes[c]++;
		int incumbent = -1;
		for (int i = 0; i < e.candidates.size(); i++) if (e.candidates.get(i).policy().equals(e.policy)) incumbent = i;
		int w = TownRules.winner(votes, incumbent);
		e.day = -1;
		counted++;
		if (w < 0) {
			VillageNews.announce(level, v, "🗳", "tvilas.news.election_none");
			e.candidates.clear();
			e.votes.clear();
			VillageData.get(level).changed();
			return -1;
		}
		TownData.Candidate c = e.candidates.get(w);
		e.policy = c.policy();
		e.mayorName = c.name();
		e.held++;
		e.festaDone = false;
		swearIn(level, v, c);
		VillageNews.announce(level, v, "🗳", "tvilas.news.election_won", c.name(), Component.translatable("tvilas.policy." + c.policy()), votes[w]);
		Talk.fact(level, v, null, "elected", c.name(), "#tvilas.policy." + c.policy());
		e.candidates.clear();
		e.votes.clear();
		VillageData.get(level).changed();
		return w;
	}

	/**
	 * O vencedor toma posse: se ele e o Prefeito de antes estiverem carregados, TROCAM de profissão e de posto (o antigo fica com o
	 * ofício e a bancada do novo; nenhum posto fica com dois donos). Depois, um pequeno discurso e um foguete na Praça.
	 */
	static void swearIn(ServerLevel level, Village v, TownData.Candidate c) {
		if (!(level.getEntity(c.villager()) instanceof Villager winner) || !winner.isAlive()) return;
		Villager old = v.civic.mayor != null && level.getEntity(v.civic.mayor) instanceof Villager m && m.isAlive() && VillageManager.isMayor(m) ? m : null;
		if (old != null && old != winner) {
			VillagerData wd = winner.getVillagerData(), od = old.getVillagerData();
			Optional<GlobalPos> wJob = winner.getBrain().getMemory(MemoryModuleType.JOB_SITE), oJob = old.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			int wXp = winner.getVillagerXp();
			Task t1 = Tasks.get(winner), t2 = Tasks.get(old);
			if (t1 != null) Tasks.end(level, winner, t1, "elected");
			if (t2 != null) Tasks.end(level, old, t2, "elected");
			old.setVillagerData(od.withProfession(wd.profession()).withLevel(wd.level()));
			old.setVillagerXp(Math.max(1, wXp));
			winner.setVillagerData(wd.withProfession(od.profession()).withLevel(1));
			winner.setVillagerXp(1);
			wJob.ifPresentOrElse(j -> old.getBrain().setMemory(MemoryModuleType.JOB_SITE, j), () -> old.getBrain().eraseMemory(MemoryModuleType.JOB_SITE));
			oJob.ifPresentOrElse(j -> winner.getBrain().setMemory(MemoryModuleType.JOB_SITE, j), () -> winner.getBrain().eraseMemory(MemoryModuleType.JOB_SITE));
			v.civic.mayor = winner.getUUID();
			v.civic.mayorSeen = level.getGameTime();
			v.civic.mayorTaken = false;
			swaps++;
		}
		Balloons.say(winner, Balloons.PARTY, Component.translatable("tvilas.election.speech." + c.policy()), 100);
		Gestures.cheer(winner);
		Places.Spot plaza = Life.places(level, v).plaza();
		if (plaza != null) Festa.firework(level, plaza.center);
	}

	public static int swaps;

	// ---------------------------------------------------------------- o ecrã de votar

	public static void openDialog(ServerPlayer p, ServerLevel level, Village v) {
		TownData.Election e = v.civic.life.town.election;
		List<DialogBody> body = new ArrayList<>();
		boolean open = e.open(VillageEconomy.day(level));
		if (!open) body.add(new PlainMessage(Component.translatable("tvilas.election.closed"), 300));
		else {
			body.add(new PlainMessage(Component.translatable("tvilas.election.intro"), 300));
			for (int i = 0; i < e.candidates.size(); i++) {
				TownData.Candidate c = e.candidates.get(i);
				body.add(new PlainMessage(Component.translatable("tvilas.election.candidate", i + 1, c.name(), Component.translatable("tvilas.policy." + c.policy()),
						Component.translatable("tvilas.policy." + c.policy() + ".desc")), 300));
			}
			if (!hasLot(level, v, p.getUUID())) body.add(new PlainMessage(Component.translatable("tvilas.election.no_lot").withStyle(ChatFormatting.RED), 300));
			else if (e.votes.containsKey(p.getUUID()))
				body.add(new PlainMessage(Component.translatable("tvilas.election.you_voted", e.candidates.get(e.votes.get(p.getUUID())).name()).withStyle(ChatFormatting.GREEN), 300));
			body.add(new PlainMessage(Component.translatable("tvilas.election.rules").withStyle(ChatFormatting.GRAY), 300));
		}
		List<ActionButton> buttons = new ArrayList<>();
		if (open && hasLot(level, v, p.getUUID()) && !e.votes.containsKey(p.getUUID()))
			for (int i = 0; i < e.candidates.size(); i++)
				buttons.add(Resident.button(Component.translatable("tvilas.election.vote_for", e.candidates.get(i).name()), "/vila votar " + (i + 1)));
		CommonDialogData common = new CommonDialogData(Component.translatable("tvilas.election.title"), Optional.empty(), true, false, DialogAction.CLOSE, body,
				List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 1)));
	}
}
