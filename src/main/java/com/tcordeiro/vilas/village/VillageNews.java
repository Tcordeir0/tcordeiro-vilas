package com.tcordeiro.vilas.village;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * O Prefeito anuncia as novidades a quem está no território: UMA linha curta no chat, com um ícone à frente (regra do
 * utilizador: nada de mensagens de várias linhas). Ex.: «🏗 A vila está construindo a muralha».
 */
public final class VillageNews {
	private VillageNews() {}

	public static final String BUILD = "🏗", DONE = "✔", NIGHT = "☾", MERCHANT = "🐴", TAX = "💰", WARN = "⚠", FROZEN = "❄", PAID = "🔓",
			LIGHT = "💡";

	/** A linha do anúncio (ícone + texto traduzido). */
	public static MutableComponent line(String icon, String key, Object... args) {
		return Component.literal(icon + " ").append(Component.translatable(key, args));
	}

	/** Anuncia a todos os jogadores no território da vila; devolve a linha (para os testes). */
	public static Component announce(ServerLevel level, Village v, String icon, String key, Object... args) {
		MutableComponent msg = line(icon, key, args);
		List<ServerPlayer> to = VillageManager.playersIn(level, v);
		for (ServerPlayer p : to) p.sendSystemMessage(msg);
		com.tcordeiro.vilas.life.Talk.news(level, v, key, args); // 0.4.0: os aldeões falam das novidades
		return msg;
	}

	/** Uma linha só para um jogador (avisos de dívida). */
	public static Component tell(ServerPlayer p, String icon, String key, Object... args) {
		MutableComponent msg = line(icon, key, args);
		if (p != null) p.sendSystemMessage(msg);
		return msg;
	}
}
