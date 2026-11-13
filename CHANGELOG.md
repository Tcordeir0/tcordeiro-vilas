# Changelog

## 0.3.0 — 13/11/2026

- **Economia em CubCoin** (com o mod CubCoin): TODOS os aldeões — os do jogo, os deste mod, o vendedor ambulante e o Mercador da
  Vila — compram e vendem em notas/moedas em vez de esmeraldas, a 40 CB por esmeralda (o preço do Balcão de Câmbio,
  `emerald_value_cents`). As trocas continuam guardadas em esmeraldas: o ecrã mostra-as em CB (1 ou 2 montes; se não houver forma
  exata, o preço vai escrito no nome), o preço acompanha a procura e os descontos do aldeão, e paga-se com QUALQUER mistura de
  notas/moedas, com troco no inventário. Escolher a troca na lista põe nas casas o dinheiro que a paga. O Cambista não muda.
  Sem o CubCoin (ou com `cubcoin_trades: false`) tudo volta logo às esmeraldas, sem converter nada no mundo. Terrenos, impostos e
  missões também em CB. Acabou o bónus de 10 CB por esmeralda por cima das esmeraldas das missões.
- **Vilas antigas**: com a Mesa numa vila sem Prefeitura, Mercado, Taverna ou Praça, o Prefeito manda construir o que falta, um de
  cada vez, pago pela tesouraria (120/80/100/60 CB), com as estruturas das vilas novas, num sítio livre perto do centro (chão
  natural, nunca em cima de casas, ruas ou terrenos); o Construtor faz à vista bloco a bloco, ou de noite. Só numa vila de verdade
  (estrutura de vila do jogo ou 5+ aldeões). Config `civic_buildings`.
- **Missões em lotes**: a cada 4 dias de jogo (`mission_batch_days`) o quadro do Prefeito troca TODAS as missões; cada missão
  vale uma vez por jogador nesse lote e fica no quadro para os outros; o quadro mostra quanto falta para renovar.
- **Pedidos dos aldeões**: em cada lote, 1 em cada 4 aldeões com profissão (vanilla e deste mod) pede o que a profissão usa — o
  ferreiro ferro ou carvão, o agricultor trigo, batatas ou cenouras, o bibliotecário papel ou livros… — com um «!» por cima da
  cabeça (só se a vila tiver dinheiro para pagar). Clicar abre o pedido (Entregar / Trocar); Shift+clique abre as trocas. Pago
  pela vila em CB (esmeraldas sem o CubCoin), uma vez por jogador por lote, com as regras de mercado ×1,25.
- **A vila cresce com as missões**: cada missão ou pedido feito dá 1 de prestígio; a cada 3 (`prestige_per_resident`) chega um
  morador novo a pé pela borda da vila, se houver cama livre; sem cama, o Prefeito encomenda uma casa simples (casa pequena da vila
  do jogo, 50 CB, regras de sítio das obras). Teto `population_cap` (40). O quadro mostra o progresso.
- **Postes de 4 blocos**: com a API de colunas do Industrial 0.12.2+ (`placeColumn`, por reflexão) os postes da iluminação pública
  têm 4 blocos e as folgas contam essa altura; sem ela, a altura antiga.
- **Correções da revisão**:
  C6 — bichos, aldeões e decorações dentro de terrenos (e da área cívica) protegidos de quem não pode mexer lá: sem bater (nem com
  flechas/poções), trela, balde, barco, tesoura, nome ou tirar itens; monstros não; trocar com aldeões continua livre. O Prefeito
  levado (vivo e carregado noutro sítio) não é substituído: volta pela Mesa; morto, convertido, noutra dimensão ou sumido há 3 dias
  de jogo, chega outro (no máximo 1 por dia) e o antigo perde a profissão quando voltar a carregar.
  A1 — missões pagas primeiro por um fundo das missões (a vila nasce com 200 CB nele, +50 CB por dia, até 400 CB:
  `mission_fund_daily_cents`, `mission_fund_cap_cents`) e depois pela tesouraria, com teto diário (`mission_daily_cap_cents`, 400 CB) e
  cada missão uma vez por jogador em cada lote; recompensa nunca acima do valor de mercado ×1,25 nem do custo de comprar aos aldeões; sem
  pedregulho; decoração paga é da vila (partida, não larga nada); a defesa só conta numa invasão a sério.
  M3 — dono em dívida: a automação congela nos terrenos dele (pistões, ejetores, água, lava, máquinas do Industrial).
  M14 — codecs estritos (obras, missões, áreas cívicas): um elemento estragado é ignorado com aviso, a vila carrega.
  M15 — área cívica protegida (praça da Mesa, Prefeitura, Praça, Mercado, Taverna): só quem pôs a Mesa e os amigos dele
  (`/vila civica amigo adicionar|remover`) e os operadores mexem; de fora nada entra.
  M18 — `/vila terreno amigo remover <nome>` funciona com o amigo desligado (nome guardado no terreno).
- **Prefeito que perdia a profissão**: o que chegava pela Mesa ficava «minecraft:none» (0 de XP e nível 1: o jogo repunha a
  profissão); agora chega com 1 de XP e a vaga da Mesa é confirmada.
- Junta a iluminação pública revista (fios revistos a cada dia e ao recarregar, fonte em campo aberto; `Ground.natural` com
  relva) e o teste no jogo.
- Trocas: o preço base riscado (descontos) aparece na mesma nota de 20 CB; o desconto do Herói da Aldeia conta em esmeraldas.
- Guia: 17 páginas novas (65), livro escrito com a página final «E o resto?» (66); pt-BR, pt-PT e en.
- Testes: 80 GameTests (28 novos) com o CubCoin, `-PnoCubcoin` e `-PnoMods` (com o Industrial 0.12.2); 61 JUnit (16 novos:
  CoinRules, CivicRules, FundRules, GrowthRules, missões, API dos postes); 60 mutações novas (121); teste no jogo
  (`runClientGameTest`, Vilas030NoJogoClientTest).


## 0.2.0 — 12/11/2026

- **Vendedores dos mods irmãos** — só existem com o mod instalado (profissão, posto, receita, trocas e página do guia):
  Mecânico (Industrial; Bancada do Mecânico): fio de cobre, cabos, canos, conectores, chave, melhorias e placa de expansão;
  Ferreiro de Ligas (Forja; Bigorna de Ligas): bronze, aço, cromo, titânio, aço inox, aço rubro, cabo de aço e peças de aço/bronze;
  Marceneiro (Móveis; Bancada do Marceneiro): cadeiras, bancos, mesas, criado-mudo, mesa de centro, sofá, lustre, guarda-roupa e
  cama de casal, envernizados, e pincéis de verniz; Cambista (CubCoin; Balcão do Cambista): esmeraldas ↔ notas ao preço do Balcão
  de Câmbio, sem descontos. Ficheiros de dados com condições da Fabric API (`fabric:all_mods_loaded`).
- **Taverna e pousada** e **Praça com fonte e poço** nas vilas dos 5 biomas. Com o Móveis, um processador de estrutura próprio
  (`tvilas:furniture`) troca marcadores vanilla por móveis (cadeiras, mesas, lustres acesos, cama de casal); sem ele ficam vanilla.
- **Construtores à vista** — golem (montado com ferro e abóbora), muralha e iluminação são feitos por um aldeão Construtor, bloco a
  bloco, com partículas e som; sem ninguém perto, o que falta aparece depois da noite. Só ocupa ar, erva, flores e neve.
- **Muralha encomendada** quando a vila chega a Cidade: quadrado com torres, pago bloco a bloco pela tesouraria; nunca destrói blocos
  de jogador nem entra em terrenos (contorna ou deixa vão; as ruas ficam portões); cancelar/retomar no quadro.
- **Iluminação pública** (Vila ou Cidade): postes ao lado das ruas. Com o Industrial 0.11 (rede aérea): postes `<madeira>_lamp_post`
  pelo bioma (ferro no deserto), ligados por fio aéreo, com 2 painéis solares e uma bateria na Prefeitura; sem ele, lanternas.
- **Novidades no chat** numa linha com ícone (🏗 obras, ☾ noite, 🐴 mercador, 💰 imposto, ⚠/❄/🔓 dívidas).
- **Imposto ajustável** — +0,5 % por melhoria paga, teto 5 %, volta à base com a tesouraria cheia; o quadro mostra porquê.
- **Dívidas** — 3 dias a dever (config) congelam os terrenos do devedor: não constrói nem parte lá, mas nada é apagado; aviso na
  véspera; «Pagar dívida» no quadro (`/vila divida pagar`) descongela.
- **Mercador da Vila** — chega à praça de 5 em 5 dias com um burro de carga, fica 2 dias, stock raro próprio; o Prefeito avisa.
- Guia: página da receita do livro (livro + trigo), 23 páginas novas; pt-BR revisto (até o céu, fica devendo, quem administra,
  quebram, vão embora, ficam vendendo, centavo…). Páginas dos mods só aparecem no Receitário com o mod.
- Testes: 52 GameTests (15 novos) com todos os mods (Industrial 0.11 com postes), `-PnoCubcoin` e `-PnoMods` (lanternas); 17 JUnit novos (45); 30 mutações novas (61).


## 0.1.1 — 12/11/2026

Correções de segurança (revisão automática).

- Terrenos protegidos também «por fora»: água e lava de fora não escorrem para dentro; o fogo de fora não queima nem se espalha para dentro, e a lava de fora não acende fogo lá dentro; explosões só partem blocos de um terreno se forem do dono/amigo (ou sem causador e nascidas no próprio terreno) — creepers e outros monstros nunca; as decorações (molduras, quadros, suportes de armadura) também ficam; pistões não empurram para dentro nem puxam de dentro de um terreno de outro dono; ejetores de fora não disparam para dentro. Dentro do próprio terreno tudo funciona como antes.
- Procura de terrenos por índice por chunk (sem alocações), para estas verificações frequentes.
- Missão de decorar/construir: as posições pagas ficam registadas por vila (até 4096, expiram em 30 dias de jogo) e não voltam a contar — nem partindo e repondo, nem noutra missão, nem para outro jogador. O quadro não repete logo o mesmo bloco/item (ex.: 6 lanternas seguidas de 12 lanternas).
- Missões de trazer: 18 pães (eram 10) e 10 garrafas de mel (eram 4) — comprá-los ao Padeiro/Apicultor e entregar dava lucro. Nova regra testada: nenhuma missão paga mais do que custa comprar os itens aos aldeões.
- Missão de defesa: monstros de geradores (spawner e trial spawner) não contam.
- Guia: a página «Proteção» fala da proteção contra lava, fogo, explosões e pistões vindos de fora.

## 0.1.0 — 12/11/2026

Primeira versão.

- Prefeito: profissão nova de aldeão, Mesa do Prefeito (posto com 1 vaga, uma por vila), quadro da Prefeitura em diálogo.
- Missões (trazer, decorar/construir, defender no alarme) com esmeraldas, CubCoins (opcional) e reputação por vila.
- Terrenos 16×16 protegidos (partir, pôr, baldes, isqueiro, ferramentas), amigos, respeitados pelas máquinas do Industrial.
- Nível da vila (Aldeia, Vila, Cidade) por população e reputação: mais terrenos, missões maiores, reforços do alarme.
- Alarme em invasões e com monstros: sino, até 2 golens por alarme (sem largar ferro, vão-se embora), golens fortalecidos.
- Impostos (cama/dia, 2 % nas trocas com aldeões e entre jogadores no CubCoin), tesouraria e gastos do Prefeito.
- Território de 400 blocos (configurável); vila sem aldeões renasce com a Mesa (Prefeito e moradores grátis).
- Prefeitura e Mercado nas vilas vanilla dos 5 biomas, gerados por script.
- Vendedores: Padeiro, Florista, Taberneiro, Apicultor (trocas data-driven nos 5 níveis).
- Guia: livro (livro + trigo) e páginas para o separador «Guias» do Receitário. Textos em pt-BR, pt-PT e en.
