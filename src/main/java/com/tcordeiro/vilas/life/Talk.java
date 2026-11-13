package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.4.0 — Conversas e fofoca sobre FACTOS reais da vila (o imposto mudou, uma obra acabou, chegou o Mercador, o Talys pagou a
 * dívida, fez uma missão, deu um presente, bateu num aldeão…). Cada facto é sabido por quem o viu (aldeões a 16 blocos) e espalha-se
 * de boca em boca: só fala dele quem o sabe, e quem ouve passa a saber. Sem facto, conversa de circunstância (tempo, trabalho).
 */
public final class Talk {
	private Talk() {}

	public static final int WITNESS_RADIUS = 16;
	static final int SMALL = 8, REPLIES = 6, GRUMPY = 3;

	/** Regista um facto; sabem-no os aldeões a 16 blocos de {@code where} (ou do centro, se null) — e o Prefeito. */
	public static LifeData.Fact fact(ServerLevel level, Village v, BlockPos where, String key, String... args) {
		LifeData d = v.civic.life;
		long day = VillageEconomy.day(level);
		// o mesmo facto no mesmo dia não se repete
		for (LifeData.Fact f : d.facts)
			if (f.key.equals(key) && f.day == day && f.args.equals(Arrays.asList(args))) return f;
		LifeData.Fact f = new LifeData.Fact(++d.factCounter, day, key, Arrays.asList(args));
		while (d.facts.size() >= LifeData.MAX_FACTS) d.facts.remove(0);
		d.facts.add(f);
		BlockPos at = where != null ? where : v.center;
		for (Villager w : Life.villagers(level, v))
			if (w.blockPosition().closerThan(at, WITNESS_RADIUS) || com.tcordeiro.vilas.village.VillageManager.isMayor(w)) f.learn(w.getUUID());
		VillageData.get(level).changed();
		return f;
	}

	/** Os factos recentes (≤ 4 dias) que este aldeão sabe. */
	static List<LifeData.Fact> known(Village v, UUID who, long day) {
		List<LifeData.Fact> out = new ArrayList<>();
		for (LifeData.Fact f : v.civic.life.facts) if (day - f.day <= 4 && f.knowers.contains(who)) out.add(f);
		return out;
	}

	/** A fala de {@code speaker} para {@code listener}: um facto que o outro ainda não sabe (o outro fica a saber), ou conversa. */
	public static Component line(ServerLevel level, Village v, Villager speaker, Villager listener) {
		long day = VillageEconomy.day(level);
		RandomSource r = level.getRandom();
		List<LifeData.Fact> facts = known(v, speaker.getUUID(), day);
		LifeData.Fact pick = null;
		for (int i = facts.size() - 1; i >= 0; i--)
			if (!facts.get(i).knowers.contains(listener.getUUID())) {
				pick = facts.get(i);
				break;
			}
		if (pick == null && !facts.isEmpty() && r.nextInt(3) == 0) pick = facts.get(r.nextInt(facts.size()));
		if (pick != null) {
			pick.learn(listener.getUUID());
			VillageData.get(level).changed();
			return factLine(pick);
		}
		return smallTalk(level, speaker, r);
	}

	public static Component factLine(LifeData.Fact f) {
		Object[] args = new Object[f.args.size()];
		for (int i = 0; i < args.length; i++) {
			String a = f.args.get(i);
			args[i] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
		}
		return Component.translatable("tvilas.talk." + f.key, args);
	}

	/** As novidades do Prefeito que viram conversa (o resto, como avisos de dívida a um jogador, não). */
	static final java.util.Set<String> NEWS = java.util.Set.of("tvilas.news.tax_up", "tvilas.news.tax_down", "tvilas.news.merchant_arrived",
			"tvilas.news.newcomer", "tvilas.news.building", "tvilas.news.done", "tvilas.news.wall_cancelled");

	/** Chamado por {@code VillageNews.announce}: guarda a novidade como facto (sabem-na o Prefeito e quem está perto da Mesa). */
	public static void news(ServerLevel level, Village v, String key, Object... args) {
		if (!NEWS.contains(key) || !com.tcordeiro.vilas.VilasConfig.get().villager_life) return;
		String[] a = new String[Math.min(args.length, 4)];
		for (int i = 0; i < a.length; i++) a[i] = arg(args[i]);
		fact(level, v, null, "news." + key.substring("tvilas.news.".length()), a);
	}

	/** Um argumento guardado como texto: as traduções sem argumentos ficam como «#chave» (falam-se na língua de quem ouve). */
	static String arg(Object o) {
		if (o instanceof Component c && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getArgs().length == 0)
			return "#" + t.getKey();
		String s = o instanceof Component c ? c.getString() : String.valueOf(o);
		return s.length() > 64 ? s.substring(0, 64) : s;
	}

	static Component smallTalk(ServerLevel level, Villager speaker, RandomSource r) {
		if (level.isRainingAt(speaker.blockPosition().above(2)) || level.isRaining() && r.nextBoolean()) return Component.translatable("tvilas.talk.rain");
		long t = LifeRules.timeOfDay(level.getOverworldClockTime());
		if (t >= 11000 && r.nextInt(3) == 0) return Component.translatable("tvilas.talk.evening");
		String prof = com.tcordeiro.vilas.village.VillagerRequests.profession(speaker);
		if (r.nextBoolean() && JOBS.contains(prof)) return Component.translatable("tvilas.talk.job." + prof);
		return Component.translatable("tvilas.talk.small." + r.nextInt(SMALL));
	}

	/** Profissões com falas próprias de trabalho. */
	static final List<String> JOBS = List.of("farmer", "librarian", "cleric", "armorer", "weaponsmith", "toolsmith", "butcher", "fisherman", "fletcher",
			"shepherd", "mason", "cartographer", "leatherworker", "baker", "florist", "innkeeper", "beekeeper", "mayor");

	/** A resposta: amigável («Pois é!») ou de mau humor (opinião baixa). */
	public static Component reply(RandomSource r, boolean friendly) {
		return friendly ? Component.translatable("tvilas.talk.reply." + r.nextInt(REPLIES)) : Component.translatable("tvilas.talk.grumpy." + r.nextInt(GRUMPY));
	}

	/** Começa uma conversa entre dois aldeões (os dois param e olham um para o outro); a opinião do par sobe (ou desce). */
	public static void start(ServerLevel level, Village v, Places places, Villager a, Villager b) {
		long now = level.getGameTime();
		LifeData d = v.civic.life;
		int op = d.opinion(a.getUUID(), b.getUUID());
		boolean friendly = LifeRules.friendly(op);
		Task ta = new Task(Task.Kind.TALK, now, now + 140);
		ta.follow = b;
		ta.line = line(level, v, a, b);
		ta.reply = reply(level.getRandom(), friendly);
		ta.icon = friendly ? null : Balloons.ANGRY;
		ta.places = places;
		Task tb = new Task(Task.Kind.TALK, now, now + 140);
		tb.follow = a;
		tb.places = places;
		Tasks.start(level, a, ta);
		Tasks.start(level, b, tb);
		d.setOpinion(a.getUUID(), b.getUUID(), LifeRules.opinionAfterTalk(op, friendly || level.getRandom().nextInt(4) == 0));
		long cd = now + LifeRules.TALK_COOLDOWN + level.getRandom().nextInt(400);
		((LifeVillager) a).tvilas$memo().nextTalk = cd;
		((LifeVillager) b).tvilas$memo().nextTalk = cd;
		talks++;
		lastLine = ta.line;
	}

	/** Para os testes: quantas conversas começaram e a última fala. */
	public static int talks;
	public static Component lastLine;
}
