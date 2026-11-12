package com.tcordeiro.vilas;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcordeiro.vilas.logic.TreasuryRules;
import com.tcordeiro.vilas.logic.VillageRules;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuração do servidor: {@code config/tvilas.json} (criado na 1.ª vez com os valores por omissão).
 * <ul>
 * <li>{@code territory_radius} — raio do território da vila em blocos (mínimo 64; por omissão 400);</li>
 * <li>{@code trade_tax_percent} — taxa sobre as trocas no território, em % (por omissão 2);</li>
 * <li>{@code bed_tax_cents} — imposto de moradia por cama e por dia de jogo, em cêntimos de CubCoin (por omissão 1);</li>
 * <li>{@code emerald_value_cents} — quanto vale uma esmeralda em cêntimos (por omissão 4000 = 40 CB, o preço do Balcão de
 * Câmbio do CubCoin). Sem o CubCoin os impostos acumulam em cêntimos e cobram-se em esmeraldas inteiras.</li>
 * <li>0.2.0 — {@code tax_per_structure_percent} (0,5: o Prefeito sobe a taxa das trocas por cada melhoria paga),
 * {@code tax_cap_percent} (5: teto), {@code treasury_full_cents} (100000 = 1000 CB: tesouraria cheia, a taxa volta à base);</li>
 * <li>{@code debt_freeze_days} (3: dias em dívida até o terreno congelar; 0 = nunca), {@code debt_min_cents} (100: dívida mínima
 * que conta);</li>
 * <li>{@code merchant_every_days} (5) e {@code merchant_stay_days} (2): o mercador viajante da vila;</li>
 * <li>{@code wall} (true): o Prefeito encomenda a muralha quando a vila chega a Cidade.</li>
 * </ul>
 */
public final class VilasConfig {
	public int territory_radius = VillageRules.DEFAULT_RADIUS;
	public double trade_tax_percent = TreasuryRules.DEFAULT_TRADE_TAX_PERCENT;
	public long bed_tax_cents = TreasuryRules.DEFAULT_BED_TAX_CENTS;
	public long emerald_value_cents = TreasuryRules.DEFAULT_EMERALD_CENTS;
	public double tax_per_structure_percent = com.tcordeiro.vilas.logic.TaxRules.DEFAULT_PER_STRUCTURE;
	public double tax_cap_percent = com.tcordeiro.vilas.logic.TaxRules.DEFAULT_CAP;
	public long treasury_full_cents = com.tcordeiro.vilas.logic.TaxRules.DEFAULT_FULL_CENTS;
	public int debt_freeze_days = com.tcordeiro.vilas.logic.DebtRules.DEFAULT_FREEZE_DAYS;
	public long debt_min_cents = com.tcordeiro.vilas.logic.DebtRules.DEFAULT_MIN_CENTS;
	public int merchant_every_days = com.tcordeiro.vilas.logic.MerchantRules.DEFAULT_EVERY_DAYS;
	public int merchant_stay_days = com.tcordeiro.vilas.logic.MerchantRules.DEFAULT_STAY_DAYS;
	public boolean wall = true;

	private static VilasConfig current = new VilasConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static VilasConfig get() {
		return current;
	}

	public int radius() {
		return Math.max(VillageRules.MIN_RADIUS, territory_radius);
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("tvilas.json");
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					VilasConfig c = GSON.fromJson(r, VilasConfig.class);
					if (c != null) current = c;
				}
			}
			try (Writer w = Files.newBufferedWriter(file)) {
				GSON.toJson(current, w); // grava também as chaves novas
			}
		} catch (IOException | RuntimeException e) {
			TVilas.LOGGER.warn("Vilas: não consegui ler/gravar {} ({}); uso os valores por omissão", file, e.toString());
		}
	}
}
