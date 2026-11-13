package com.tcordeiro.vilas.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.ui.TownDialog;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /vila} — abre a Prefeitura da vila onde se está; {@code /vila info}; {@code /vila missao aceitar|entregar <1-3>};
 * {@code /vila terreno comprar|info}; {@code /vila terreno amigo adicionar|remover <jogador>}. Todos sem permissões especiais
 * (são os botões do diálogo), mas só funcionam DENTRO de uma vila com Prefeitura.
 */
public final class VilaCommand {
	private VilaCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("vila")
				.executes(c -> open(c))
				.then(Commands.literal("info").executes(VilaCommand::info))
				.then(Commands.literal("missao")
						.then(Commands.literal("aceitar").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(c -> mission(c, true))))
						.then(Commands.literal("entregar").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(c -> mission(c, false)))))
				.then(Commands.literal("divida").then(Commands.literal("pagar").executes(VilaCommand::payDebt)))
				.then(Commands.literal("muralha")
						.then(Commands.literal("cancelar").executes(c -> wall(c, true)))
						.then(Commands.literal("retomar").executes(c -> wall(c, false))))
				// 0.3.0 (M15): o dono da Mesa junta amigos à área cívica (Prefeitura, Praça, Mercado, Taverna)
				.then(Commands.literal("civica").then(Commands.literal("amigo")
						.then(Commands.literal("adicionar").then(Commands.argument("jogador", EntityArgument.player())
								.executes(c -> civic(c, EntityArgument.getPlayer(c, "jogador"), null))))
						.then(Commands.literal("remover").then(Commands.argument("jogador", StringArgumentType.word())
								.executes(c -> civic(c, null, StringArgumentType.getString(c, "jogador")))))))
				// 0.3.0: os botões do pedido de um aldeão («!»)
				.then(Commands.literal("pedido")
						.then(Commands.literal("entregar").then(Commands.argument("aldeao", StringArgumentType.word())
								.executes(c -> request(c, true))))
						.then(Commands.literal("trocar").then(Commands.argument("aldeao", StringArgumentType.word())
								.executes(c -> request(c, false)))))
				.then(Commands.literal("terreno")
						.then(Commands.literal("comprar").executes(c -> result(c, VillageManager.buyLot(c.getSource().getPlayerOrException()))))
						.then(Commands.literal("info").executes(VilaCommand::lotInfo))
						.then(Commands.literal("amigo")
								.then(Commands.literal("adicionar").then(Commands.argument("jogador", EntityArgument.player())
										.executes(c -> result(c, VillageManager.addFriend(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "jogador"))))))
								.then(Commands.literal("remover").then(Commands.argument("jogador", StringArgumentType.word()) // 0.3.0 (M18): por nome, também desligado
											.suggests(VilaCommand::friendNames)
											.executes(c -> result(c, VillageManager.removeFriend(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "jogador"))))))))));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> friendNames(CommandContext<CommandSourceStack> c,
			com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		if (c.getSource().getPlayer() instanceof ServerPlayer p) {
			Lot lot = VillageManager.lotAt(p.level(), p.getBlockX(), p.getBlockZ());
			if (lot != null && lot.owner.equals(p.getUUID()))
				for (var id : lot.friends) b.suggest(lot.friendNames.getOrDefault(id, id.toString()));
		}
		return b.buildFuture();
	}

	private static int civic(CommandContext<CommandSourceStack> c, ServerPlayer add, String remove) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		return result(c, add != null ? VillageManager.addCivicFriend(p, v, add) : VillageManager.removeCivicFriend(p, v, remove));
	}

	private static int request(CommandContext<CommandSourceStack> c, boolean deliver) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		java.util.UUID id;
		try {
			id = java.util.UUID.fromString(StringArgumentType.getString(c, "aldeao"));
		} catch (IllegalArgumentException e) {
			return result(c, VillageManager.Result.fail("tvilas.request.none"));
		}
		var r = deliver ? com.tcordeiro.vilas.village.VillagerRequests.deliver(p, id) : com.tcordeiro.vilas.village.VillagerRequests.trade(p, id);
		if (!deliver && r.ok()) return 1; // abriu as trocas: sem mensagem
		return result(c, r);
	}

	private static Village here(ServerPlayer p) {
		Village v = VillageManager.villageAt(p.level(), p.blockPosition());
		if (v == null) p.sendSystemMessage(Component.translatable("tvilas.cmd.no_village"));
		return v;
	}

	private static int open(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		TownDialog.open(p, p.level(), v);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		p.sendSystemMessage(Component.translatable("tvilas.cmd.info", Component.translatable(VillageRules.levelKey(v.level)), v.population,
				v.reputationSum(), v.reputationOf(p.getUUID()), VillageManager.lotsOf(p.level(), v).size(), VillageRules.maxLots(v.level),
				v.center.getX(), v.center.getY(), v.center.getZ()));
		return 1;
	}

	private static int mission(CommandContext<CommandSourceStack> c, boolean accept) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		int slot = IntegerArgumentType.getInteger(c, "n") - 1;
		return result(c, accept ? VillageManager.accept(p, v, slot) : VillageManager.deliver(p, v, slot));
	}

	private static int payDebt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		return result(c, com.tcordeiro.vilas.village.Debts.pay(p, v));
	}

	private static int wall(CommandContext<CommandSourceStack> c, boolean cancel) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Village v = here(p);
		if (v == null) return 0;
		return result(c, cancel ? VillageManager.cancelWall(p, v) : VillageManager.resumeWall(p, v));
	}

	private static int lotInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Lot lot = VillageManager.lotAt(p.level(), p.getBlockX(), p.getBlockZ());
		if (lot == null) {
			p.sendSystemMessage(Component.translatable("tvilas.lot.none_here"));
			return 0;
		}
		p.sendSystemMessage(Component.translatable("tvilas.lot.info", lot.ownerName, lot.area.minX(), lot.area.minZ(), lot.area.maxX(),
				lot.area.maxZ(), lot.friends.size()));
		return 1;
	}

	private static int result(CommandContext<CommandSourceStack> c, VillageManager.Result r) {
		c.getSource().sendSystemMessage(r.message());
		return r.ok() ? 1 : 0;
	}
}
