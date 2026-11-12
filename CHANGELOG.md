# Changelog

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
