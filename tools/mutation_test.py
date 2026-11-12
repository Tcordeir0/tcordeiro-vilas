#!/usr/bin/env python3
"""Prova de mutação das regras de segurança: estraga de propósito cada regra e confirma que os testes FALHAM.
Se um teste continuar verde com a regra estragada, o teste é vácuo (não protege nada) — o script sai com código 1.

Mutações (cada uma num ficheiro, sempre reposto no fim, mesmo com erro):
  lote protegido   — LotRules.mayModify deixa todos; ignora os amigos; VillageManager.mayModify não olha para o terreno;
                     o UseBlockCallback deixa pôr blocos em terreno alheio;
  limite de golens — canCallGolem sem limite; reforço do alarme sem teto;
  taxa             — taxa de 2 % calculada a 0,2 %;
  0.1.1 fronteiras — líquidos, lava a acender, fogo, explosões (blocos, monstros, jogadores, decorações), pistões, ejetores
                     e o índice por chunk;
  0.1.1 missões    — posições de decorar já pagas, limite das posições pagas, pick sem repetir o alvo, missões que davam lucro
                     comprando aos aldeões, monstros de gerador na defesa.
Uso: JAVA_HOME=... python3 tools/mutation_test.py
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = "src/main/java/com/tcordeiro/vilas/"

MUTATIONS = [
    ("lote: mayModify deixa qualquer um", J + "logic/LotRules.java",
     "return actor.equals(owner) || friends.contains(actor);", "return true;", "test"),
    ("lote: amigos ignorados", J + "logic/LotRules.java",
     "return actor.equals(owner) || friends.contains(actor);", "return actor.equals(owner);", "test"),
    ("lote: o mundo não consulta o terreno", J + "village/VillageManager.java",
     "Lot lot = lotAt(level, pos.getX(), pos.getZ());\n\t\tif (lot == null) return true;",
     "Lot lot = lotAt(level, pos.getX(), pos.getZ());\n\t\tif (true) return true;", "runGametest"),
    ("lote: pôr blocos não é verificado", J + "protect/LotProtection.java",
     "if (target == null || VillageManager.mayModify(level, target, player)) return InteractionResult.PASS;",
     "if (true) return InteractionResult.PASS;", "runGametest"),
    ("golens: sem limite por alarme", J + "logic/AlarmRules.java",
     "if (calledThisAlarm >= cap) return false;", "", "test"),
    ("golens: reforço sem teto", J + "logic/AlarmRules.java",
     "return MAX_GOLEMS + Math.max(0, Math.min(2, alarmBonus));", "return MAX_GOLEMS + Math.max(0, alarmBonus);", "test"),
    ("golens: o alarme ignora o limite", J + "block/MayorDeskBlockEntity.java",
     "AlarmRules.canCallGolem(true, a.golems, a.lastGolem, now, AlarmRules.cap(v.economy.alarmBonus))",
     "AlarmRules.canCallGolem(true, 0, a.lastGolem, now, AlarmRules.cap(v.economy.alarmBonus))", "runGametest"),
    ("taxa: 0,2 % em vez de 2 %", J + "logic/TreasuryRules.java",
     "valueCents * percent / 100.0", "valueCents * percent / 1000.0", "test"),
    # 0.1.1 — terrenos protegidos por fora
    ("fronteira: mayCross deixa tudo passar", J + "logic/LotRules.java",
     "if (targetOwner == null) return true;\n\t\treturn mayModify(targetOwner, targetFriends, sourceOwner);",
     "return true;", "test"),
    ("fronteira: o mundo não compara os donos", J + "village/VillageManager.java",
     "return LotRules.mayCross(source == null ? null : source.owner, target.owner, target.friends);", "return true;", "runGametest"),
    ("índice por chunk: não se refaz quando os terrenos mudam", J + "village/VillageData.java",
     "if (indexedMods != lots.mods()) reindex();", "if (indexedMods == -1) reindex();", "runGametest"),
    ("líquidos: escorrem para dentro", J + "mixin/protect/FlowingFluidMixin.java",
     "if (level instanceof ServerLevel sl && !VillageManager.mayCross(", "if (false && level instanceof ServerLevel sl && !VillageManager.mayCross(",
     "runGametest"),
    ("lava: acende fogo dentro", J + "mixin/protect/LavaFluidMixin.java",
     "return VillageManager.mayCross(level, lavaPos, firePos);", "return true;", "runGametest"),
    ("fogo: queima blocos do terreno", J + "mixin/protect/FireBlockMixin.java",
     "return !(level instanceof ServerLevel sl) || VillageManager.mayCross(sl, firePos, target);", "return true;", "runGametest"),
    ("fogo: espalha-se para dentro", J + "mixin/protect/FireBlockMixin.java",
     "return VillageManager.mayCross(level, firePos, target);", "return true;", "runGametest"),
    ("explosões: lista de blocos sem filtro", J + "mixin/protect/ServerExplosionMixin.java",
     "cir.getReturnValue().removeIf(", "if (false) cir.getReturnValue().removeIf(", "runGametest"),
    ("explosões: jogador estranho parte o terreno", J + "village/VillageManager.java",
     "if (cause instanceof Player player) return isAdmin(player) || hit.mayModify(player.getUUID());",
     "if (cause instanceof Player player) return true;", "runGametest"),
    ("explosões: monstros partem o terreno", J + "village/VillageManager.java",
     "if (cause != null) return false;", "", "runGametest"),
    ("explosões: decorações do terreno partem-se", J + "mixin/protect/ServerExplosionMixin.java",
     "if (!(entity instanceof BlockAttachedEntity) && !(entity instanceof ArmorStand)) return false;", "if (true) return false;",
     "runGametest"),
    ("pistões: sem verificação", J + "mixin/protect/PistonStructureResolverMixin.java",
     "if (!cir.getReturnValueZ() ||", "if (true ||", "runGametest"),
    ("pistões: puxar de dentro passa (só olha ao destino)", J + "mixin/protect/PistonStructureResolverMixin.java",
     "if (!VillageManager.mayCross(sl, pistonPos, p) || !VillageManager.mayCross(",
     "if (!VillageManager.mayCross(", "runGametest"),
    ("ejetores: disparam para dentro", J + "mixin/protect/DispenserBlockMixin.java",
     "ci.cancel();", "{}", "runGametest"),
    # 0.1.1 — missões
    ("decorar: posições já pagas contam", J + "village/VillageManager.java",
     "if (!v.paidSpots.isPaid(l, now) && p.level()", "if (p.level()", "runGametest"),
    ("decorar: a entrega não marca as posições", J + "village/VillageManager.java",
     "for (int i = 0; i < m.count() && i < spots.size(); i++) v.paidSpots.markPaid(spots.get(i), now);", "", "runGametest"),
    ("posições pagas: nunca dão como pagas", J + "logic/PaidSpots.java",
     "return at != null && now - at < TTL;", "return false;", "test"),
    ("posições pagas: crescem sem limite", J + "logic/PaidSpots.java",
     "if (spots.size() > MAX || now - e.getValue() >= TTL) it.remove();", "if (now - e.getValue() >= TTL) it.remove();", "test"),
    ("missões: pick repete o alvo", J + "logic/MissionRules.java",
     "for (Template a : avoid) fresh.removeIf(t -> t.target.equals(a.target));", "fresh.removeAll(avoid);", "test"),
    ("missões: pão comprado ao Padeiro dá lucro", J + "logic/MissionRules.java",
     '"minecraft:bread", 18, 3, 10, 1)', '"minecraft:bread", 10, 3, 10, 1)', "test"),
    ("missões: mel comprado ao Apicultor dá lucro", J + "logic/MissionRules.java",
     '"minecraft:honey_bottle", 10, 5, 18, 2)', '"minecraft:honey_bottle", 4, 5, 18, 2)', "test"),
    ("defesa: monstros de gerador contam", J + "village/VillageManager.java",
     "if (victim.entityTags().contains(FROM_SPAWNER_TAG)) return;", "", "runGametest"),
    ("gerador: não marca os monstros", J + "mixin/protect/SpawnerMobsMixin.java",
     "entity.addTag(VillageManager.FROM_SPAWNER_TAG);", "", "runGametest"),
]


def gradle(task):
    r = subprocess.run(["./gradlew", task, "--offline", "-q"], cwd=ROOT, capture_output=True, text=True)
    return r.returncode == 0


def main():
    killed = 0
    for name, rel, old, new, task in MUTATIONS:
        path = os.path.join(ROOT, rel)
        orig = open(path).read()
        assert orig.count(old) == 1, f"{name}: o trecho a mutar não está (ou está repetido) em {rel}"
        try:
            open(path, "w").write(orig.replace(old, new))
            survived = gradle(task)
        finally:
            open(path, "w").write(orig)
        print(f"  {'✗ SOBREVIVEU' if survived else '✓ morta'}  [{task}]  {name}")
        killed += 0 if survived else 1
    print(f"{killed}/{len(MUTATIONS)} mutações mortas pelos testes")
    sys.exit(0 if killed == len(MUTATIONS) else 1)


if __name__ == "__main__":
    main()
