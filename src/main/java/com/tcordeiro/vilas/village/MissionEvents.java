package com.tcordeiro.vilas.village;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.block.MayorDeskBlockEntity;
import com.tcordeiro.vilas.ui.TownDialog;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Ligações ao jogo: falar com o Prefeito, mortes (missões de defesa) e os golens chamados não largarem ferro. */
public final class MissionEvents {
	private MissionEvents() {}

	public static void init() {
		// Clique direito no Prefeito: abre a Prefeitura (missões, terrenos, estado da vila) em vez das trocas (ele não troca)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!VillageManager.isMayor(entity) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				Village v = VillageManager.villageAt(level, entity.blockPosition());
				if (v != null) TownDialog.open(sp, level, v);
				else sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("tvilas.mayor.no_desk"), true);
			}
			return InteractionResult.SUCCESS;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer) VillageManager.onKill(killer, entity);
			if (VillageManager.isMayor(entity) && entity.level() instanceof ServerLevel level) VillageEconomy.onMayorDied(level, entity.getUUID());
		});

		// Golens chamados pelo alarme não largam ferro (senão ligar/desligar o alarme seria uma quinta de ferro)
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var e = context.getOptional(LootContextParams.THIS_ENTITY);
			if (e != null && e.entityTags().contains(MayorDeskBlockEntity.CALLED_TAG)) drops.clear();
		});
		TVilas.LOGGER.debug("eventos das missões registados");
	}
}
