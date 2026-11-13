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
 * <li>0.3.0 — {@code cubcoin_trades} (true): com o CubCoin, todos os aldeões trocam em CubCoin em vez de esmeraldas;
 * {@code mission_daily_cap_cents} (40000 = 400 CB: o máximo que a tesouraria paga em missões por dia; 0 = sem teto) e
 * {@code mission_batch_days} (4: o quadro e os pedidos dos aldeões renovam-se; cada um uma vez por jogador em cada lote);
 * {@code prestige_per_resident} (3 missões trazem um morador) e {@code population_cap} (40); {@code civic_buildings} (true): numa vila
 * antiga o Prefeito manda construir a Prefeitura, o Mercado, a Taverna e a Praça que faltam; {@code mission_fund_daily_cents}
 * (5000) e {@code mission_fund_cap_cents} (40000): o fundo das missões (a vila nasce com 200 CB nele).</li>
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
	public boolean cubcoin_trades = true;
	public long mission_daily_cap_cents = 40000;
	public boolean civic_buildings = true;
	public int mission_batch_days = com.tcordeiro.vilas.logic.MissionRules.BATCH_DAYS;
	public int prestige_per_resident = com.tcordeiro.vilas.logic.GrowthRules.DEFAULT_PER_RESIDENT;
	public int population_cap = com.tcordeiro.vilas.logic.GrowthRules.DEFAULT_CAP;
	public long mission_fund_daily_cents = com.tcordeiro.vilas.logic.FundRules.DAILY_CENTS;
	public long mission_fund_cap_cents = com.tcordeiro.vilas.logic.FundRules.CAP_CENTS;
	/** 0.4.0: a vida dos aldeões no território (rotina, conversas, festa, menu do morador…); false desliga tudo. */
	public boolean villager_life = true;
	/** 0.4.0: salário diário de cada guarda (cêntimos), pago pela tesouraria. */
	public long guard_wage_cents = com.tcordeiro.vilas.logic.LifeRules.GUARD_WAGE_CENTS;
	/** 0.5.0: mercado semanal na Praça a cada N dias (0 = sem mercado). */
	public int market_every_days = com.tcordeiro.vilas.logic.TownRules.MARKET_EVERY_DAYS;
	/** 0.5.0: eleições a cada N dias (0 = sem eleições). */
	public int election_every_days = com.tcordeiro.vilas.logic.TownRules.ELECTION_EVERY_DAYS;
	/** 0.5.0: preço de um ajudante por um dia (cêntimos), todo para a tesouraria. */
	public long helper_price_cents = com.tcordeiro.vilas.logic.TownRules.HELPER_PRICE_CENTS;
	/** 0.5.0: true = o «!» dos pedidos volta a ser o nome dourado (como até à 0.4) em vez do balão. */
	public boolean request_name_tag = false;

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
