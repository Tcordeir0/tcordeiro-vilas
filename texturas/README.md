# Pacote de recursos «tvilas_animacoes» (gestos dos aldeões)

Gestos dos aldeões do Vilas 0.5.1 (acenar, festejar, encolher os ombros, sim, não, apontar, bater palmas, martelar, comer) POR CIMA do Fresh Animations.
O mod manda os gestos e regista no Entity Model Features as variáveis `tvilas_gesture` e `tvilas_gesture_time`; este pacote usa-as.

- Aqui (público) só está o que é nosso: `tvilas_animacoes/pack.mcmeta` e `tvilas_animacoes/gestos.json`.
- As animações base (`villager_animations.jpm`) são do Fresh Animations (FreshLX) e NÃO estão no Git: ficam em
  `~/Cods/assets_pessoais/texturas/tvilas_animacoes/fa/`. O script põe as nossas somas no FIM das animações do FA.
- `sh texturas/montar_pack.sh` junta os dois e gera `texturas/tvilas_animacoes.zip` (ignorado pelo Git).

Instalar: pôr o .zip em `resourcepacks/` e ativá-lo ACIMA do Fresh Animations (e do FA+Details) na lista dos pacotes.
Precisa do EMF 3.3.11+, do Fresh Animations e do Vilas 0.5. Sem o pacote, o mod mexe nos gestos sozinho (gancho do EMF).
