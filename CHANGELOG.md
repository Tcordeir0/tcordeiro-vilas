# Changelog

## 0.6.1 — 14/11/2026 — botões

- Textos dos botões que ficavam cortados encurtados nas 3 línguas: «Comprar terreno (%s)», «Contratar 1 dia (%s)», «Correio devolvido (%s)».

## 0.6.0 — 14/11/2026

**A vila lembra-se, vota melhor e enfrenta crises** — tudo só nos aldeões do território de uma Mesa; nada cria dinheiro; tetos em tudo o
que paga, dá itens, dá descontos ou dá reputação; LOD, prazo e plano B como antes.

### Memória, crónica, «você me salvou» e obras com doações

- **Memória dos aldeões**: cada aldeão guarda até 6 recordações (quem o salvou, quem lhe deu um presente de que gostou, quem lhe bateu,
  quem doou para a obra); a mais fraca sai quando entra uma mais forte e o peso decai devagar (como no Dwarf Fortress). Guarda-se só o
  facto; a frase gera-se ao abrir, na língua de quem lê. O menu do morador mostra o que ele se lembra de ti («💭»), e às vezes conta-o
  na conversa.
- **Crónica da vila**: subidas de nível, eleições, festas, obras acabadas, recém-chegados, resgates e obras comunitárias ficam numa
  crónica (até 200 entradas). No quadro, «📜 Crónica» mostra as últimas 12; «Livro da Vila» copia-a para um livro escrito (gasta um
  livro do inventário: nada se cria do nada).
- **«Você me salvou»**: quem mata o monstro que estava a atacar um aldeão (era o alvo dele, ou bateu-lhe há menos de 10 s) é lembrado
  por ele: coração, aceno, «Obrigado, Talys!», mexericos vanilla e +3 de reputação na vila (uma vez por dia por aldeão). E manda uma
  **carta**: com o Móveis, pelo Carteiro até à caixa de correio do teu terreno (um livro escrito com o agradecimento e um presente da
  profissão); sem o Móveis ou sem caixa, uma mensagem e o presente na hora. Uma por jogador por semana; o presente vem dos presentes
  vanilla do Herói da Aldeia, sem esmeraldas. Uma carta de aldeão que não se entrega perde-se (nunca vai parar a outro jogador).
- **Obras comunitárias com doações**: a cada 6 dias sem obra a vila propõe uma — **chafariz**, **coreto**, **farol** ou **ponte**
  (sobre um rio de 3 a 12 blocos perto do centro) — no primeiro sítio que couber. O quadro mostra o que falta; «🧱 Doar materiais»
  tira do inventário só o que ainda falta e dá reputação (1 por 8 itens, até 12 por dia; nunca dinheiro). Com tudo doado, o Construtor
  faz a obra pela fila de obras (à vista ou de noite), só em ar, erva, flores e neve fora dos terrenos — nunca parte nada — e no fim
  põe uma **placa** com os nomes de quem mais doou. A obra fica na crónica.
- Testes: 5 GameTests novos nos 3 modos (com os mods, `-PnoCubcoin`, `-PnoMods`), JUnit das regras (`LoreRules`) e 9 mutações novas
  (memória sem teto, reputação do resgate sem teto, cartas sem teto, presente em dinheiro, doação que tira a mais, reputação das
  doações sem teto, obra que parte blocos, livro da crónica sem gastar um livro): todas mortas.
- Guia: 7 páginas novas no separador Guias.
- Comandos: `/vila cronica`, `/vila cronica livro`, `/vila obra doar` (botões do quadro).

### Eleições revistas e bónus do voto

- **Votação de 3 dias**: as eleições continuam a cada 8 dias, mas a votação dura 3 dias de jogo e conta-se no fim do 3.º; o quadro e
  o ecrã de votar mostram quanto falta («2 d 5 h»).
- **Empate**: ganha o candidato mais querido pela vila (as opiniões dos aldeões sobre ele, das conversas, mais a reputação de quem
  votou nele); se persistir, fica a proposta em vigor; se não houver nenhuma, sorteio com semente (a mesma eleição dá sempre o
  mesmo resultado).
- **Bónus do voto** (nunca dinheiro novo: o que se devolve sai da tesouraria, com tetos):
  - votar dá 5 de reputação com os aldeões da vila, uma vez por eleição;
  - **apoiador** (votou no vencedor), até à eleição seguinte: um **bolo** na posse (item), **−5 % em todas as trocas** com os
    aldeões da vila (reembolso da tesouraria, onde já se aplica o da festa; teto de 20 CB por jogador e 100 CB por mandato, nunca
    mais que a tesouraria) e um **pedido «!» exclusivo** por lote de missões (no máximo 3 por vila; os outros não o entregam);
  - **promessas** para quem votou na proposta vencedora: imposto menor → −20 % no imposto do terreno dele; mais guardas → o posto
    novo fica perto do terreno dele; mercado maior → **banca própria** no mercado semanal, com o nome dele (as vendas entre
    jogadores lá não pagam a taxa); mais festas → **convite VIP** (+5 % de reembolso na festa, dentro dos tetos da festa);
  - **broche «Eu votei»** (item novo, textura 16×16 em estilo vanilla) para todos os que votaram, com a vila e o eleito na
    descrição. Quem estiver desligado recebe os itens quando voltar ao território.
- Testes: 9 GameTests novos (`ElectionGameTests`) nos 3 modos, JUnit `VoteRulesTest` e 34 mutações («0.6 eleições» em
  `tools/mutation_test.py`).
- Guia: «Eleições» e «Votar» revistas; novas «Bônus do voto», «Pedido exclusivo», «Promessas», «Banca e convite» e «Eu votei»
  (pt-BR, pt-PT e en).

### Reconstrução, crises com pedido de ajuda e funeral

- **Reconstrução depois de um ataque**: os blocos partidos por monstros (explosão de creeper, ghast ou wither; zombie que parte uma
  porta) ou queimados pelo fogo, a 96 blocos da Mesa (`rebuild_radius`) e FORA dos terrenos, ficam registados (até 512 por vila,
  `rebuild_max_blocks`). Passado o perigo e um minuto sem estragos, o Construtor repõe-nos de baixo para cima (obra «a reconstrução»,
  à frente das outras), com as regras de obra de sempre: só onde há ar, erva, flores ou neve e nunca num terreno. Nunca uma
  «fotografia» da vila; TNT de jogador e explosões sem causador não contam; baús, caixas, placas e líquidos não se registam. Os blocos
  registados não largam item na explosão (o material fica para a obra: repor não duplica).
- **Crises com pedido de ajuda** (`crises`): **incêndio** (um bloco da vila ardeu: apagar 3 fogos, à mão ou com água; quem acendeu
  fogo na vila nos últimos 5 minutos não recebe), **fome** (menos de 1 ponto de comida por aldeão nos inventários deles, 2 dias
  seguidos: trazer N pães, que vão para os aldeões) e **noite perigosa** (6+ monstros na vila de noite, `crisis_night_monsters`:
  derrotar 4; os de gerador e os dos terrenos não contam). Cada crise aparece no quadro, os aldeões gritam em balões (fogo, pão, «!»)
  e a recompensa sai da TESOURARIA com a regra A1: uma vez por jogador em cada crise, 3–4 jogadores por crise, o teto do dia das
  missões e intervalo de dias entre crises do mesmo tipo. `/vila crise entregar` (botão do quadro).
- **Morte, luto e funeral** (`funerals`): quando morre um aldeão da vila, o Prefeito avisa; no dia seguinte, às 15 h, há funeral na
  Praça com uma campa (pedra lavrada, vela acesa e placa encerada com o epitáfio: nome ou profissão, dias na vila e a causa) num
  sítio livre à volta da Praça (nunca num terreno; até 16 campas). Os amigos (opinião ≥ 10; sem amigos, os 3 vizinhos) vão ao
  funeral e ficam de luto um dia: balão da vela e o gesto novo **luto** (`tvilas_gesture` = 10: cabeça baixa, mãos juntas).
- **Velhice (opcional)**: `villager_aging` DESLIGADA por omissão; ligada, ao fim de `villager_lifespan_days` (120) dias na vila, no
  máximo um por dia, e nunca aldeões com trocas trancadas, com nome, o Prefeito ou crianças.
- Desempenho: tudo por eventos (explosão, fogo, porta, morte) e sensores por vila (noite a cada 10 s, fome 1 vez por dia). Medido no
  GameTest: explosão de força 10 numa vila (≈140 blocos) leva 9–19 ms com o registo (10–26 ms sem ele, a variação é do servidor); o
  segundo da vila a seguir, 0,04–0,3 ms.
- Guia: páginas «Reconstruir», «Crises», «Funeral» e «Velhice (opcional)». pt-BR, pt-PT e en.

### Junção e revisão
- A crónica regista mortes, funerais, crises e a reconstrução; o epitáfio da campa usa a recordação mais forte do aldeão
  («Salvo por Talys», «Amigo de Talys»).
- As crises pagam como as missões: primeiro do fundo das missões, depois da tesouraria, com os mesmos tetos.
- A reconstrução volta a ligar as cercas, paredes e painéis de vidro à volta de cada bloco reposto.
- Revisão de segurança:
  - só paga a quem apaga um fogo de causa NATURAL (um raio sem jogador, a bola de fogo ou a explosão de um monstro, ou o espalhar de
    um destes), fora dos terrenos e sem nenhum jogador a 16 blocos quando nasceu; a causa vem dos mixins (fogo a espalhar-se, raio,
    bola de fogo, explosão) e tudo o resto (isqueiro, carga de fogo, dispensador, lava, projétil de jogador, comandos) não paga. A lista
    dos fogos que pagam fica guardada no mundo, tira os que se apagam e, cheia, falha fechada (os novos não pagam). O «quem acendeu»
    e o pagamento usam a mesma procura de vila;
  - numa explosão, os blocos registados para a reconstrução (e a outra metade das portas e camas) saem antes de a explosão os
    processar, de cima para baixo, sem largar item nem avisar os vizinhos (o vanilla junta os itens de várias posições, por isso
    filtrar ao largar não chegava; tochas, portas e camas apoiadas também não caem como item).
- Pacote `tvilas_animacoes`: o gesto do luto (10) também no pacote; `sh texturas/montar_pack.sh` volta a gerar o .zip.
- Guia: 126 páginas no separador Guias; o livro escrito continua com 100 (as primeiras 98, os comandos e «E o resto?»).
- Config nova: `rebuild`, `rebuild_max_blocks`, `rebuild_radius`, `crises`, `crisis_night_monsters`, `funerals`, `villager_aging`,
  `villager_lifespan_days`. Comandos novos: `/vila cronica`, `/vila cronica livro`, `/vila obra doar`, `/vila crise entregar`.

## 0.5.1 — 13/11/2026

**Gestos dos aldeões a sério** (com o Entity Model Features 3.3.11 e o Fresh Animations): acenar, sim, não, apontar, bater palmas,
martelar, comer, festejar e encolher os ombros.

- O mod manda o gesto (pacote do servidor, só a quem está a 32 blocos) e o cliente regista no EMF `tvilas_gesture` (1 acenar,
  2 festejar, 3 encolher os ombros, 4 sim, 5 não, 6 apontar, 7 bater palmas, 8 martelar, 9 comer) e `tvilas_gesture_time` (0–1).
- **Pacote `tvilas_animacoes`** (para os amigos, não vai no Git): as nossas somas (`texturas/tvilas_animacoes/gestos.json`) postas
  no FIM das animações do Fresh Animations (`villager_animations.jpm`), para correrem depois das dele — na 0.5.0 estavam no
  `villager.jem` e o FA apagava-as. `sh texturas/montar_pack.sh` gera o .zip a partir da cópia do FA que fica em
  `~/Cods/assets_pessoais/texturas/tvilas_animacoes/` (fora do Git). Ativa-se ACIMA do Fresh Animations e do FA+Details.
- **Sem o pacote**, o mod faz os mesmos gestos sozinho: um gancho no fim da animação do EMF soma um pouco à cabeça e aos braços
  (o FA continua a animar). Volta a ver se o pacote está ativo em cada recarga dos pacotes de recursos (ouvinte de recarga).
  Sem o EMF, nada disto carrega e nada se parte.
- Gestos bem visíveis: acenar com os braços acima da cabeça a balançar 3 vezes, festejar com saltinhos e braços no ar, bater
  palmas a abrir e fechar os braços, martelar com 3 golpes, encolher os ombros, sim/não com a cabeça; rx negativo levanta os braços
  e ty negativo sobe (confirmado no jogo).
- Onde aparecem: palmas na festa, martelar no trabalho dos ferreiros/pedreiro/marceneiro/mecânico, comer na Taverna ao fim da
  tarde, acenar nas boas-vindas e à armadura, encolher os ombros perante uma arma, apontar ao pedir caminho, sim/não nas conversas.
- Guia: a página «Gestos» explica o que os aldeões fazem (110 páginas no separador Guias).


## 0.5.0 — 13/11/2026

**A vila ganha vida própria (vanilla+)** — tudo só nos aldeões do território de uma Mesa, com LOD (48/96 blocos), prazo e plano B
em cada tarefa; o dinheiro sai SEMPRE da tesouraria, com tetos; nada global.

- **Carteiro** (profissão nova, só com o Móveis; posto: **Balcão dos Correios**, modelo feito no Blender): Shift+clique →
  «Mandar correio» → escolhe-se um dono de terreno da vila que tenha uma **Caixa de Correio** do Móveis no terreno → 9 espaços;
  ao fechar, a carta fica com o Carteiro (guardada na vila, nunca no aldeão) e paga-se o selo (1 CB, todo para a tesouraria; grátis
  sem o CubCoin). Nas horas de trabalho ele vai a pé, com a **Bolsa do Carteiro** na mão, e põe os itens na caixa (a bandeirinha do
  Móveis sobe e o dono é avisado). Caixa cheia/partida: tenta outra vez; à 3.ª a carta volta e levanta-se no Carteiro. Teto: 3 cartas
  por jogador, 32 por vila. Trocas: papel, livros para escrever, pacotes, bússolas e caixas de correio do Móveis. Sem o Móveis não
  existe (nem o bloco nem a profissão).
- **Mercado semanal na Praça** (`market_every_days`, 7; cada vila no seu dia): o Prefeito avisa na véspera, às 7 h o sino abre o
  mercado; os vendedores da vila montam **bancas** (modelo do Blender, cenário que se apaga no fim e nunca fica no mundo) e
  apregoam; chega um **Vendedor de Fora** (o vendedor ambulante do jogo, que se vai às 15 h). Nas bancas a tesouraria devolve 15 % do
  que se paga (até 15 CB por jogador e 80 CB por mercado; sem o CubCoin, em esmeraldas inteiras).
- **Eleições** (`election_every_days`, 8): no dia de votar o Prefeito apresenta 2 ou 3 candidatos (aldeões da vila), cada um com uma
  proposta — imposto menor (−1 ponto na taxa das trocas, nunca abaixo de 0), mais guardas (+1 posto, pago pela tesouraria), mais
  festas (uma festa extra no mandato) ou mercado maior (teto do reembolso +50 %). Votam no quadro os donos de terreno, um voto cada.
  No dia seguinte o vencedor toma posse: troca de posto com o Prefeito de antes (discurso e foguete) e a proposta fica em vigor.
  Nenhuma proposta cria dinheiro. Sem votos, nada muda.
- **Pescador**: o Pescador do jogo pesca a sério nas horas de trabalho — vai à margem mais perto do posto (16 blocos), cana na mão e
  a **Boia** do Vilas (modelo do Blender; não o anzol do jogo); a boia balança, há puxões com salpicos e som, e cada peixe repõe uma
  troca de venda de peixe dele (no máximo 6 por dia; o jogador continua a pagar).
- **Crianças a brincar**: esconde-esconde (uma conta de costas, as outras escondem-se atrás de alguma coisa e são achadas uma a uma)
  e apanhada, perto da Praça, de dia e fora das horas de aprendiz — só com um jogador a 48 blocos.
- **Missão «decorar a festa»**: quando há festa marcada, o quadro mostra uma 4.ª missão temporária (lanternas, velas ou lã a 24
  blocos da Praça); paga como as outras (fundo e tesouraria, teto do dia, uma vez por jogador) e some quando a festa acaba. O lote de
  4 dias não muda. `/vila missao aceitar|entregar 4`.
- **Contratar ajudante** (menu do morador): um aldeão segue-te durante um dia por 20 CB (`helper_price_cents`; sem o CubCoin, 1
  esmeralda) — TUDO para a tesouraria. Leva uma bolsa de 9 espaços e colhe (e volta a semear) o que está maduro nos TEUS terrenos.
  Nunca entra em terrenos alheios (espera à porta), nunca colhe fora dos teus terrenos, não apanha itens nem abre baús, não sai do
  território. Um por jogador, um contrato por dia, dois por vila; no fim (ou dispensado, ou se morrer) a bolsa volta para ti.
- **O «!» dos pedidos é um balão** por cima da cabeça (os nomes dos aldeões nunca são mexidos; o «!» antigo no nome sai sozinho;
  aldeões com nome dado também podem pedir). `request_name_tag: true` volta ao nome dourado.
- **Gestos por variável do EMF** (Entity Model Features 3.3.11, API `EMFAnimationApi` confirmada no jar): o cliente regista
  `tvilas_gesture` (1 acenar, 2 festejar, 3 encolher os ombros, 4 sim, 5 não, 6 apontar) e `tvilas_gesture_time` (0–1). Só um
  pacote de recursos que as use mexe no aldeão (ver «Decisões» no relatório: o Vilas não traz um pacote derivado do Fresh Animations).
- **Ideias minhas**: pregão do mercado (sino e falas dos vendedores), discurso e foguete do Prefeito eleito, fofoca sobre cartas,
  eleições, mercado e ajudantes.
- Desempenho: mercado e crianças só com um jogador perto da Praça; boia/bancas são mostradores vanilla sem colisão, apagados ao fim e
  ao carregar se forem restos; nada por tick além das tarefas. Medido com 40 aldeões (ver o LEIA-ME do teste no jogo).
- Config: `market_every_days`, `election_every_days`, `helper_price_cents`, `request_name_tag`. Comandos: `/vila eleicao`,
  `/vila votar <1-3>`, `/vila morador <aldeão> correio|carta|devolvido|contratar|bolsa|dispensar` (botões dos menus).
- Guia: 17 páginas novas (110 no separador Guias); o livro escrito leva as primeiras 98, os comandos e «E o resto?» (100).
  pt-BR, pt-PT e en.


## 0.4.0 — 13/11/2026

**Aldeões mais vivos** — só nos aldeões dentro do território de uma Mesa; o horário vanilla, os nomes, a replanta e o clique das
trocas não mudam. O cérebro vanilla de um aldeão só fica em pausa enquanto ele tem uma tarefa do Vilas (todas com prazo e plano B);
o modelo do aldeão não muda (gestos com estados vanilla, que o Fresh Animations já anima).

- **Anti-preso e portas**: as tarefas abrem as portas de madeira do caminho e fecham-nas quando já ninguém passa; 3 s sem andar →
  refaz o caminho, à 3.ª desiste; dois frente a frente: cede o de ID mais baixo. Um vigia leve solta os aldeões vanilla presos.
  Evitam trilhos.
- **Fim de tarde**: das 16 h até à hora de se deitar (cada um à sua, entre as 18 h e as 19 h 30) vão para a Praça ou para a
  Taverna, sentam-se e conversam.
- **Sentar e dormir nos Móveis**: cadeiras, bancos e sofás do Móveis (o assento do próprio Móveis); sem ele, degraus de escada
  (assento invisível do Vilas). Quem não tem cama dorme na cama de casal da Taverna, um de cada lado, e acorda às 6 h 30.
- **Clima**: chuva → Taverna (ou Prefeitura); com o Clima, abaixo de 5 °C → lareira/fogueira acesa (ou Prefeitura); sem o Clima,
  só nos biomas de neve.
- **Guardas**: os golens da vila em postos fixos (Praça, postes, cantos da muralha; 1/2/4 pelo nível), sem patrulha; salário de
  3 CB por dia cada (`guard_wage_cents`), SEMPRE da tesouraria (sem dinheiro, o posto fica vago). O clérigo cura (Regeneração,
  3×/dia) e os ferreiros reparam golens (+25 de vida, 3×/dia). Pânico coletivo: um aldeão atacado por um monstro avisa os vizinhos a
  15 blocos, que fogem para o golem/guarda ou para um jogador amigo.
- **Conversas e fofoca**: dois aldeões que se cruzam param e falam de FACTOS reais (imposto, obras, Mercador, morador novo, festa,
  missões e pedidos do jogador, presentes, terrenos, quem bateu num aldeão); só fala quem sabe, e quem ouve passa a saber. Opinião
  por par (amigável ou resmungão). 1 conversa por aldeão por minuto.
- **Festa na Praça**: quando a vila sobe de nível ou na véspera do Mercador, o Prefeito avisa; no dia, das 12 h às 17 h, saltos, ♪,
  foguetes e um reembolso de 10 % nas trocas com aldeões, pago pela tesouraria (até 20 CB por jogador — sem o CubCoin, 1 esmeralda —
  e 100 CB por festa).
- **Aprendizes**: as crianças escolhem um mestre, seguem-no de dia e herdam a profissão ao crescer.
- **Menu do morador** (Shift+clique com a mão vazia): Trocar, Conversar, Onde fica…? (vira-se, diz a distância e o rumo e mostra
  um rasto de luz só a quem perguntou) e o pedido «!»; mostra o que pensa de ti (Desconfiado, Neutro, Simpático, Amigo).
- **Presentes** (Shift+clique com um item): gostos por profissão, 2 por aldeão por semana, reputação com teto diário; detestado
  volta para ti, sem castigo. Um aldeão Amigo dá um presente de volta (1×/semana; nunca dinheiro).
- **Balões de emoção** (primeiro código de cliente): 16 ícones feitos no Blender e falas curtas por cima da cabeça, só até 32 blocos.
- **Trabalho à vista**: na bancada, o item da profissão na mão, faíscas/fumo/brilhos e o som, a cada 3–7 s.
- **Reações**: espada ou machado perto → recuam com «?»; armadura de ferro → «Um guarda!»; quem bate num aldeão leva olhares
  zangados.
- **Sino**: a Prefeitura toca às 8 h, 12 h e 18 h (só o som: ninguém se esconde); «Convocar reunião» no quadro (Simpático, 1×/dia):
  os aldeões vão à Praça e o Prefeito dá o relatório numa linha.
- **Minhas**: boas-vindas a quem volta depois de 3 dias; lanterna na mão à noite; o quadro mostra a reputação, a festa e os guardas.
- **Desempenho**: LOD (tudo até 48 blocos, o essencial até 96, nada além); lista de aldeões e sítios em cache; nada por tick a não
  ser as tarefas em curso; balões, falas e sons com tempo de espera. Itens de cena nunca caem.
- Config: `villager_life` (desliga tudo), `guard_wage_cents`. Comandos: `/vila morador <aldeão> …` (botões do menu), `/vila reuniao`.
- Guia: 28 páginas novas (93 + «E o resto?»); pt-BR, pt-PT e en.


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
- **Fonte da iluminação em campo aberto**: os painéis e a bateria a ≥ 2 blocos de qualquer bloco não natural (casas, poços e os seus
  telhados, vedações, água) e com céu aberto por cima de todas as partes; a ≤ 8 blocos da Mesa, ou até 16 se não houver sítio.
- **Postes de 4 blocos**: com a API de colunas do Industrial 0.12.2+ (`placeColumn`, por reflexão) os postes da iluminação pública
  têm 4 blocos e as folgas contam essa altura; sem ela, a altura antiga.
- **Correções da revisão**:
  C6 — bichos, aldeões e decorações dentro de terrenos (e da área cívica) protegidos de quem não pode mexer lá: sem bater (nem com
  flechas/poções), trela, balde, barco, tesoura, nome ou tirar itens; monstros não; trocar com aldeões continua livre. O Prefeito
  levado (vivo e carregado noutro sítio) não é substituído: volta pela Mesa; morto, convertido, noutra dimensão ou sumido há 3 dias
  de jogo, chega outro (no máximo 1 por dia) e o antigo perde a profissão quando voltar a carregar.
  A1 — missões pagas primeiro por um fundo das missões (começa a 0; uma vila de verdade — estrutura de vila do jogo ou 5+ aldeões —
  recebe 200 CB UMA vez e +50 CB por dia, até 400 CB:
  `mission_fund_daily_cents`, `mission_fund_cap_cents`) e depois pela tesouraria, com teto diário (`mission_daily_cap_cents`, 400 CB) e
  cada missão uma vez por jogador em cada lote; recompensa nunca acima do valor de mercado ×1,25 nem do custo de comprar aos aldeões; sem
  pedregulho; decoração paga é da vila (partida, não larga nada); a defesa só conta numa invasão a sério.
  M3 — dono em dívida: a automação congela nos terrenos dele (pistões, ejetores, água, lava, máquinas do Industrial).
  M14 — codecs estritos (obras, missões, áreas cívicas): um elemento estragado é ignorado com aviso, a vila carrega.
  Revisão de segurança: uma Mesa no mato não cria dinheiro (sem fundo, missões pagas nem pedidos fora de uma vila de verdade; os
  200 CB não voltam ao repor a Mesa); a posse da Mesa só se ganha com a PRIMEIRA Mesa de uma vila que nasce agora, num sítio onde o
  jogador pode construir — repor a Mesa numa vila que já existia não muda o dono (histórico guardado).
  M15 — área cívica protegida (praça da Mesa, Prefeitura, Praça, Mercado, Taverna): só quem pôs a Mesa e os amigos dele
  (`/vila civica amigo adicionar|remover`) e os operadores mexem; de fora nada entra.
  M18 — `/vila terreno amigo remover <nome>` funciona com o amigo desligado (nome guardado no terreno).
- **Prefeito que perdia a profissão**: o que chegava pela Mesa ficava «minecraft:none» (0 de XP e nível 1: o jogo repunha a
  profissão); agora chega com 1 de XP e a vaga da Mesa é confirmada.
- Junta a iluminação pública revista (fios revistos a cada dia e ao recarregar, fonte em campo aberto; `Ground.natural` com
  relva) e o teste no jogo.
- Trocas: o preço base riscado (descontos) aparece na mesma nota de 20 CB; o desconto do Herói da Aldeia conta em esmeraldas.
- Guia: 17 páginas novas (65), livro escrito com a página final «E o resto?» (66); pt-BR, pt-PT e en.
- Testes: 83 GameTests (31 novos), 5 corridas seguidas em cada modo sem falhas; o teste do aldeão que vira Prefeito ficou
  determinístico (já junto à Mesa).
- 62 JUnit (17 novos: CoinRules, CivicRules, FundRules, GrowthRules, missões, API dos postes); 71 mutações novas (132), todas
  mortas; teste no jogo (`runClientGameTest`, Vilas030NoJogoClientTest).


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
