package com.tcordeiro.vilas.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Os mods irmãos com integração OPCIONAL. Nada deles é preciso para compilar (só ids e reflexão), e tudo o que depende deles —
 * profissões, postos de trabalho, receitas, trocas, móveis nas estruturas — só existe se estiverem instalados.
 */
public final class Mods {
	private Mods() {}

	public static final String INDUSTRIAL = "tindustrial", FORJA = "tforja", MOVEIS = "tmoveis", CUBCOIN = "cubcoin";

	public static boolean loaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}
}
