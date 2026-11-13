# Vilas do Tcordeiro (`tvilas`)

Mod Fabric para Minecraft Java **26.3** (Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25). A lógica é do servidor; o
cliente precisa do mod para as texturas, os nomes e (0.4.0) os balões de emoção por cima dos aldeões.

- **Prefeito** — aldeão vanilla com profissão própria (`tvilas:mayor`), posto de trabalho na **Mesa do Prefeito** (1 vaga, uma Mesa
  por vila). Clique direito nele ou na Mesa abre o quadro da Prefeitura (diálogo do servidor). Não faz trocas.
- **Missões** — 3 ativas (trazer itens, pôr blocos, defender numa invasão). Pagas pela tesouraria (CubCoins com o CubCoin,
  esmeraldas sem ele; teto diário e 3 por jogador por dia) e
  reputação por jogador e por vila (+ gossip vanilla nos aldeões).
- **Terrenos** — 16×16 com o jogador no meio, dentro do território; só dono e amigos partem/põem blocos (também as máquinas do
  Industrial, que perguntam ao `PlayerBlockBreakEvents.BEFORE` em nome do dono).
- **Nível** — Aldeia → Vila (8 aldeões + 100 de reputação) → Cidade (16 + 400). Nunca desce.
- **Alarme** — invasão ou 3+ monstros: sino, até 2 golens por alarme (+1/+2 com reforço), golens da vila fortalecidos.
- **Impostos e tesouraria** — 1¢ por cama/dia nos terrenos e 2 % das trocas no território (aldeões e vendas entre jogadores
  pelo CubCoin). O Prefeito gasta a tesouraria em moradores, golens e reforço do alarme. Sem CubCoin: esmeraldas inteiras.
- **Território** — 400 blocos à volta da Mesa (configurável). Vila sem aldeões renasce: chega um Prefeito e moradores grátis
  (1/dia, até 3) se houver camas.
- **Prefeitura** (centro) e **Mercado** (casa) nas vilas de planície, savana, deserto, taiga e neve; **Padeiro**, **Florista**,
  **Taberneiro** e **Apicultor** com trocas nos 5 níveis.

- **0.2.0** — vendedores dos mods irmãos (Mecânico, Ferreiro de Ligas, Marceneiro, Cambista: só com o mod), Taverna e Praça
  (mobiladas com o Móveis), construtores à vista (ou de noite), muralha da Cidade, iluminação pública (postes do Industrial 0.11
  ou lanternas), imposto ajustável, terrenos congelados por dívida (nada é apagado) e o Mercador da Vila.
- **0.3.0** — com o CubCoin, todos os aldeões trocam em CubCoin (40 CB por esmeralda, com troco); vilas antigas ganham a
  Prefeitura/Mercado/Taverna/Praça que faltam; bichos dos terrenos e área cívica protegidos; Prefeito levado não é substituído;
  missões em lotes de 4 dias (uma vez por jogador), pedidos dos aldeões com «!», a vila cresce com as missões (prestígio →
  moradores, casa se faltar cama); missões sem lucro infinito; automação congelada com dívida; codecs estritos; tirar amigos
  desligados; postes de 4 blocos com o Industrial 0.12.2+.
- **0.4.0** — aldeões mais vivos (só no território de uma Mesa): fim de tarde na Praça e na Taverna (sentados nos móveis do
  Móveis ou em escadas), deitar aos poucos, cama de casal para quem não tem cama, chuva → Taverna e frio → lareira (Clima),
  guardas em postos fixos pagos pela tesouraria, clérigo que cura e ferreiros que reparam golens, pânico coletivo, conversas e
  fofoca sobre factos reais, festa na Praça (reembolso da tesouraria), aprendizes, menu do morador (Shift+clique), presentes
  com gostos, reputação visível, pedir caminho, balões de emoção (cliente), trabalho à vista, reações a armas, sino com horas e
  reunião; tudo com LOD (48/96 blocos), prazos e plano B.

Configuração: `config/tvilas.json` (`territory_radius`, `trade_tax_percent`, `bed_tax_cents`, `emerald_value_cents`,
`tax_per_structure_percent`, `tax_cap_percent`, `treasury_full_cents`, `debt_freeze_days`, `debt_min_cents`,
`merchant_every_days`, `merchant_stay_days`, `wall`, `cubcoin_trades`, `mission_daily_cap_cents`, `mission_daily_per_player`,
`civic_buildings`, `mission_batch_days`, `prestige_per_resident`, `population_cap`, `mission_fund_daily_cents`,
`mission_fund_cap_cents`, 0.4.0: `villager_life`, `guard_wage_cents`).
Comandos: `/vila`, `/vila info`, `/vila missao aceitar|entregar <1-3>`, `/vila terreno comprar|info`,
`/vila terreno amigo adicionar <jogador>`, `/vila terreno amigo remover <nome>` (também desligado), `/vila divida pagar`, `/vila muralha cancelar|retomar`, `/vila reuniao`, `/vila morador <aldeão> trocar|conversar|pedido|caminho <sítio>` (botões do menu).

## Desenvolvimento

```sh
export JAVA_HOME="$HOME/Library/Application Support/horizon-launcher/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home"
python3 tools/gen_data.py          # modelos, blockstates, receitas, loot, tags, trocas, traduções, guia (VilasGuide.java)
python3 tools/textures.py          # texturas (PIL) dos blocos, das profissões e o ícone
python3 tools/gen_structures.py    # NBT da Prefeitura, Mercado, Taverna e Praça nos 5 biomas
./gradlew build                    # jar em build/libs/ (+ testes JUnit da lógica pura)
./gradlew runGametest              # testes de servidor (sem janela), com o CubCoin
./gradlew runGametest -PnoCubcoin  # os mesmos sem o CubCoin (só esmeraldas)
./gradlew runGametest -PnoMods     # sem nenhum mod opcional (CubCoin, Industrial, Forja, Móveis, Clima)
./gradlew runClientGameTest -Ppublished  # no jogo, com os jars publicados (~/Downloads/Mods_Tcordeiro_MC26.3): vídeos e fotos
python3 tools/mutation_test.py     # prova de mutação das regras de segurança
python3 tools/check_book_layout.py <26.3.jar> src/main/java/com/tcordeiro/vilas/guide/VilasGuide.java src/main/resources/assets/tvilas/lang/pt_br.json guide.tvilas
```

Os NBT só usam blocos vanilla; na Taverna e na Praça o processador `tvilas:furniture` troca marcadores (escada = cadeira,
cerca + placa = mesa, lanterna pendurada = lustre, camas azul-clara + ciano = cama de casal) por móveis do Móveis quando ele está
instalado. As estruturas só aparecem em chunks gerados depois de instalar o mod.
