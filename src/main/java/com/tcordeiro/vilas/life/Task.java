package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * 0.4.0 — Uma tarefa que o Vilas dá a um aldeão (o cérebro vanilla fica em pausa enquanto dura): o quê, para onde, até quando
 * (prazo SEMPRE), com que pressa, e o estado do caminho (anti-preso, portas que abriu, assento, cama).
 */
public final class Task {
	public enum Kind {
		/** Convívio na Praça ou na Taverna (fim da tarde, até à hora de se deitar). */
		SOCIAL,
		/** Abrigo da chuva (Taverna) ou do frio (lareira/Prefeitura). */
		SHELTER,
		/** Dormir na cama de casal do Móveis (quem não tem cama). */
		SLEEP,
		/** Pânico coletivo: fugir para um guarda/golem ou para um jogador conhecido. */
		FLEE,
		/** Conversa com outro aldeão (parados, a olhar um para o outro). */
		TALK,
		/** Festa na Praça (saltos, música). */
		FESTA,
		/** O clérigo vai curar; o ferreiro vai reparar um golem. */
		HEAL, REPAIR,
		/** Uma criança aprendiz segue o mestre. */
		FOLLOW,
		/** Pedir caminho: virar-se para o sítio e «apontar». */
		POINT,
		/** Ir a um sítio e parar (reunião, recuar de uma arma, boas-vindas). */
		GO, MEETING, WELCOME
	}

	public final Kind kind;
	public final long started;
	public long deadline;
	public BlockPos target;
	/** Entidade seguida (mestre, ferido, golem, jogador, parceiro de conversa); o alvo anda com ela. */
	public Entity follow;
	public double speed = 0.5, reach = 1.6;
	public BlockPos seat, bed;
	public boolean seated, sleeping, arrived;
	/** Anti-preso: última posição, ticks parado, caminhos refeitos. */
	public double lx, ly, lz;
	public int still, replans;
	/** Até quando fica parado a ceder passagem. */
	public long waitUntil;
	/** Portas de madeira abertas por esta tarefa (fecham-se quando já ninguém está a passar). */
	public final List<BlockPos> doors = new ArrayList<>(2);
	/** Conversa: a fala, a resposta (do outro) e quando cada uma se diz; festa: próximo sítio. */
	public Component line, reply;
	public long nextStage;
	public int stage;
	/** Pedir caminho: quem perguntou (o rasto de partículas só ele vê). */
	public UUID asker;
	/** O sítio do convívio/abrigo a que volta se o assento falhar (plano B). */
	public BlockPos fallback;
	public String icon;
	/** Os sítios da vila (para soltar o assento/cama reservados no fim). */
	public Places places;
	/** Porque acabou (para os testes): «done», «deadline», «stuck», «hurt», «trade», «far»… */
	public String endReason;

	public Task(Kind kind, long now, long deadline) {
		this.kind = kind;
		this.started = now;
		this.deadline = deadline;
	}

	public Task to(BlockPos target, double reach) {
		this.target = target;
		this.reach = reach;
		return this;
	}

	public Task speed(double s) {
		this.speed = s;
		return this;
	}
}
