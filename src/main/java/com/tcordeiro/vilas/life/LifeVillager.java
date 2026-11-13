package com.tcordeiro.vilas.life;

/**
 * 0.4.0 — O estado em memória (não guardado) da vida de um aldeão: a tarefa que o Vilas lhe deu e as pequenas memórias (tempos de
 * espera, item na mão, onde estava há pouco). Posto no {@code Villager} por mixin ({@code VillagerLifeMixin}).
 */
public interface LifeVillager {
	Task tvilas$task();

	void tvilas$setTask(Task task);

	Memo tvilas$memo();

	/** As pequenas memórias de cada aldeão (todas em ticks de jogo; 0 = nunca). */
	final class Memo {
		public long nextTalk, nextShelter, nextReact, nextWork, nextGesture, nextSocial, propUntil, nextFestaJump, nodUntil, nextSound;
		public boolean init, lantern;
		/** Vigia anti-preso dos aldeões que andam pelo vanilla: última posição vista e há quantas vistas não anda. */
		public double wx, wy, wz;
		public int still;
		/** 0.5.0: próxima entrega (Carteiro), próxima pescaria e a margem de pesca (relida de 5 em 5 minutos). */
		public long nextMail, nextFish, fishScan;
		public Fishing.Spot fishSpot;
	}
}
