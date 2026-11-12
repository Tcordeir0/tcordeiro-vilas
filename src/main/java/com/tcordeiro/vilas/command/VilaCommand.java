package com.tcordeiro.vilas.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
				.then(Commands.literal("terreno")
						.then(Commands.literal("comprar").executes(c -> result(c, VillageManager.buyLot(c.getSource().getPlayerOrException()))))
						.then(Commands.literal("info").executes(VilaCommand::lotInfo))
						.then(Commands.literal("amigo")
								.then(Commands.literal("adicionar").then(Commands.argument("jogador", EntityArgument.player())
										.executes(c -> result(c, VillageManager.addFriend(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "jogador"))))))
								.then(Commands.literal("remover").then(Commands.argument("jogador", EntityArgument.player())
										.executes(c -> result(c, VillageManager.removeFriend(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "jogador"))))))))));
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
