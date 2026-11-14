package com.tcordeiro.vilas;

import com.tcordeiro.vilas.command.VilaCommand;
import com.tcordeiro.vilas.protect.LotProtection;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.MissionEvents;
import com.tcordeiro.vilas.worldgen.VillagePoolInjector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vilas do Tcordeiro: Prefeito (aldeão com profissão própria), missões, terrenos protegidos, nível da vila, alarme,
 * Prefeitura e Mercado nas vilas vanilla e 4 vendedores novos.
 */
public class TVilas implements ModInitializer {
	public static final String MOD_ID = "tvilas";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** O CubCoin está instalado? (as missões pagam também em CubCoins; sem ele, só esmeraldas) */
	public static final boolean CUBCOIN = FabricLoader.getInstance().isModLoaded("cubcoin");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		VilasConfig.load();
		VilasBlocks.init();
		com.tcordeiro.vilas.registry.VilasItems.init(); // 0.5.0: itens de cena (bolsa, boia, banca)
		VilasProfessions.init();
		LotProtection.init();
		MissionEvents.init();
		com.tcordeiro.vilas.life.LifeEvents.init(); // 0.4.0: depois dos cliques do Prefeito e dos pedidos
		VilaCommand.init();
		com.tcordeiro.vilas.life.crisis.Crises.init(); // 0.6 crises (depois do /vila: junta-lhe o «crise»)
		VillagePoolInjector.init();
	}
}
