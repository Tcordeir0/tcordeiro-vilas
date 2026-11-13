package com.tcordeiro.vilas.life;

import java.util.Optional;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.logic.LifeRules;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 0.4.0 — Balões de emoção e falas curtas por cima da cabeça: o servidor manda UM pacote por evento (entidade, ícone, fala, ticks)
 * só a quem está a {@link LifeRules#BALLOON_RANGE} blocos ou menos; o cliente desenha (ícones 16×16 feitos no Blender). Nada toca
 * no modelo do aldeão, e nada é mandado por tick.
 */
public final class Balloons {
	private Balloons() {}

	public static final String HEART = "heart", EXCLAIM = "exclaim", ALERT = "alert", QUESTION = "question", SLEEP = "sleep", RAIN = "rain",
			COLD = "cold", ANGRY = "angry", COIN = "coin", NOTE = "note", TALK = "talk", WAVE = "wave", PARTY = "party", HEAL = "heal",
			REPAIR = "repair", POINT = "point",
			// 0.5.0: correio, peixe, eleições, esconde-esconde
			MAIL = "mail", FISH = "fish", VOTE = "vote", HIDE = "hide";

	/**
	 * 0.5.0 — Um gesto (para o Entity Model Features, variável {@code tvilas_gesture}): o servidor diz «este aldeão acena / festeja /
	 * encolhe os ombros…» durante uns ticks. Sem o EMF, o cliente ignora. Códigos em {@link Gestures}.
	 */
	public record Gesture(int entity, int code, int ticks) implements CustomPacketPayload {
		public static final Type<Gesture> TYPE = new Type<>(TVilas.id("gesture"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Gesture> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Gesture::entity,
				ByteBufCodecs.VAR_INT, Gesture::code, ByteBufCodecs.VAR_INT, Gesture::ticks, Gesture::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static int gestures;
	public static Gesture lastGesture;

	/** Manda o gesto a quem está perto (≤ 32 blocos). */
	public static int gesture(Entity e, int code, int ticks) {
		if (!(e.level() instanceof ServerLevel level)) return 0;
		Gesture msg = new Gesture(e.getId(), code, ticks);
		lastGesture = msg;
		int n = 0;
		double r2 = LifeRules.BALLOON_RANGE * LifeRules.BALLOON_RANGE;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(e) > r2) continue;
			if (ServerPlayNetworking.canSend(p, Gesture.TYPE)) ServerPlayNetworking.send(p, msg);
			n++;
		}
		if (n > 0) gestures++;
		return n;
	}

	/** Um balão: ícone (pode ser vazio quando é só fala) e fala (opcional). */
	public record Show(int entity, String icon, Optional<Component> text, int ticks) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(TVilas.id("balloon"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Show::entity,
				ByteBufCodecs.stringUtf8(32), Show::icon, ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, Show::text, ByteBufCodecs.VAR_INT,
				Show::ticks, Show::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Gesture.TYPE, Gesture.CODEC);
	}

	/** Contador para os testes: quantos balões foram mandados (a alguém) desde o arranque. */
	public static int sent;
	/** O último balão/fala (para os testes). */
	public static Show last;

	public static int show(Entity e, String icon, int ticks) {
		return send(e, icon, null, ticks);
	}

	public static int say(Entity e, String icon, Component text, int ticks) {
		return send(e, icon, text, ticks);
	}

	/** Manda a quem está perto; devolve a quantos jogadores chegou. */
	public static int send(Entity e, String icon, Component text, int ticks) {
		if (!(e.level() instanceof ServerLevel level)) return 0;
		Show msg = new Show(e.getId(), icon == null ? "" : icon, Optional.ofNullable(text), ticks);
		last = msg;
		int n = 0;
		double r2 = LifeRules.BALLOON_RANGE * LifeRules.BALLOON_RANGE;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(e) > r2) continue;
			if (ServerPlayNetworking.canSend(p, Show.TYPE)) ServerPlayNetworking.send(p, msg);
			n++;
		}
		if (n > 0) sent++;
		return n;
	}
}
