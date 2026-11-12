#!/usr/bin/env python3
"""Prova de mutação das regras de segurança: estraga de propósito cada regra e confirma que os testes FALHAM.
Se um teste continuar verde com a regra estragada, o teste é vácuo (não protege nada) — o script sai com código 1.

Mutações (cada uma num ficheiro, sempre reposto no fim, mesmo com erro):
  lote protegido   — LotRules.mayModify deixa todos; ignora os amigos; VillageManager.mayModify não olha para o terreno;
                     o UseBlockCallback deixa pôr blocos em terreno alheio;
  limite de golens — canCallGolem sem limite; reforço do alarme sem teto;
  taxa             — taxa de 2 % calculada a 0,2 %.
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
