# Vilas do Tcordeiro (`tvilas`)

Mod Fabric para Minecraft Java **26.3** (Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25). Só servidor precisa dele
para a lógica; o cliente precisa para as texturas e nomes (não há código de cliente).

- **Prefeito** — aldeão vanilla com profissão própria (`tvilas:mayor`), posto de trabalho na **Mesa do Prefeito** (1 vaga, uma Mesa
  por vila). Clique direito nele ou na Mesa abre o quadro da Prefeitura (diálogo do servidor). Não faz trocas.
- **Missões** — 3 ativas (trazer itens, pôr blocos, defender durante um alarme). Pagam esmeraldas (+ CubCoins com o CubCoin) e
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

Configuração: `config/tvilas.json` (`territory_radius`, `trade_tax_percent`, `bed_tax_cents`, `emerald_value_cents`).
Comandos: `/vila`, `/vila info`, `/vila missao aceitar|entregar <1-3>`, `/vila terreno comprar|info`,
`/vila terreno amigo adicionar|remover <jogador>`.

## Desenvolvimento

```sh
export JAVA_HOME="$HOME/Library/Application Support/horizon-launcher/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home"
python3 tools/gen_data.py          # modelos, blockstates, receitas, loot, tags, trocas, traduções, guia (VilasGuide.java)
python3 tools/textures.py          # texturas (PIL) dos blocos, das profissões e o ícone
python3 tools/gen_structures.py    # NBT da Prefeitura e do Mercado nos 5 biomas
./gradlew build                    # jar em build/libs/ (+ testes JUnit da lógica pura)
./gradlew runGametest              # testes de servidor (sem janela), com o CubCoin
./gradlew runGametest -PnoCubcoin  # os mesmos sem o CubCoin (só esmeraldas)
python3 tools/mutation_test.py     # prova de mutação das regras de segurança
python3 tools/check_book_layout.py <26.3.jar> src/main/java/com/tcordeiro/vilas/guide/VilasGuide.java src/main/resources/assets/tvilas/lang/pt_br.json guide.tvilas
```

As estruturas só usam blocos vanilla (os NBT são fixos; com os Móveis seriam precisas duas versões de cada) e só aparecem em
chunks gerados depois de instalar o mod.
