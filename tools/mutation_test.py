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
                     comprando aos aldeões, monstros de gerador na defesa;
  0.2.0            — a muralha não destrói blocos de jogador nem de terrenos (regra pura, plano, colocação), construtores um
                     a um e de noite, teto e descida do imposto, congelar não apaga nem tira o terreno, pagar descongela,
                     mercador que chega e parte, stock próprio, vendedores só com o mod, móveis só com o Móveis, Cambista sem
                     margem, missões sem lucro com os preços dos ficheiros de trocas.
Uma mutação pode mexer em vários ficheiros (rel = lista de (ficheiro, antes, depois)) e a tarefa pode levar argumentos
(ex.: "runGametest -PnoMods" para as regras que só se veem sem os mods).
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
     "if (cause instanceof Player player) return isAdmin(player) || (!frozen(level, hit) && hit.mayModify(player.getUUID()));",
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
    # 0.2.0 — muralha
    ("muralha: substitui qualquer bloco (regra)", J + "logic/WallRules.java",
     "return air || grassOrFlower || snowLayer;", "return true;", "test"),
    ("muralha: entra nos terrenos (regra)", J + "logic/WallRules.java",
     "if (inLot) return false;", "", "test"),
    ("muralha: assenta em cima de blocos de jogador", J + "build/WallWork.java",
     "if (!Ground.natural(ground)) continue; // bloco de jogador (ou casa): contorna", "", "runGametest"),
    ("muralha: ignora os terrenos no plano e na obra", [
        (J + "build/WallWork.java", "if (VillageManager.lotAt(level, x, z) != null) continue; // nunca em terrenos", ""),
        (J + "build/Ground.java", "boolean inLot = VillageManager.lotAt(level, pos.getX(), pos.getZ()) != null;", "boolean inLot = false;")],
     None, None, "runGametest"),
    ("obras: põem blocos sem ver o que lá está", J + "build/Builders.java",
     "boolean free = Ground.mayBuild(level, pos);", "boolean free = true;", "runGametest"),
    ("obras: o mundo substitui tudo", J + "build/Ground.java",
     "return WallRules.mayReplace(s.isAir(), grassOrFlower(s), s.is(Blocks.SNOW), inLot);", "return !inLot;", "runGametest"),
    # 0.2.0 — construtores
    ("construtor: constrói sem ninguém a ver", J + "build/Builders.java",
     "if (!Ground.watched(level, v, next.pos(), BuildRules.WATCH_RADIUS)) return; // ninguém a ver: fica para a noite", "", "runGametest"),
    ("construtor: põe tudo de uma vez", J + "build/Builders.java",
     "place(level, v, p, next, true);", "place(level, v, p, next, true); while (!p.pending.isEmpty()) place(level, v, p, p.pending.remove(0), true);",
     "runGametest"),
    ("construtor: a noite não constrói nada", J + "build/Builders.java",
     "if (place(level, v, p, next, false)) put++;", "if (false) put++;", "runGametest"),
    ("construtor: espera sem fim (regra)", J + "logic/BuildRules.java",
     "return lastStep < 0 || now - lastStep >= STEP_TICKS ? 1 : 0;", "return lastStep < 0 ? 1 : 0;", "test"),
    # 0.2.0 — imposto
    ("imposto: sem teto", J + "logic/TaxRules.java",
     "if (raised >= top) return new Rate(top, Reason.CAPPED);", "", "test"),
    ("imposto: não baixa com a tesouraria cheia", J + "logic/TaxRules.java",
     "if (fullAt > 0 && treasury >= fullAt) return new Rate(Math.min(b, top), Reason.FULL);", "", "test"),
    ("imposto: as trocas ignoram a taxa ajustada", J + "village/VillageEconomy.java",
     "long tax = TreasuryRules.tradeTax(emeralds * VilasConfig.get().emerald_value_cents, taxRate(v).percent());",
     "long tax = TreasuryRules.tradeTax(emeralds * VilasConfig.get().emerald_value_cents, VilasConfig.get().trade_tax_percent);", "runGametest"),
    ("imposto: as obras pagas não contam", J + "village/VillageEconomy.java",
     "\t\t\t\tv.extra.structures++;\n", "\n", "runGametest"),
    # 0.2.0 — dívidas
    ("dívida: congelado constrói à mesma", J + "village/VillageManager.java",
     "if (frozen(level, lot)) return false; // 0.2.0", "if (false) return false; // 0.2.0", "runGametest"),
    ("dívida: congelar tira o terreno", J + "village/Debts.java",
     "v.extra.frozen.add(who);", "v.extra.frozen.add(who); VillageData.get(level).lots.removeIf(l -> l.owner.equals(who));", "runGametest"),
    ("dívida: congelar apaga os blocos", J + "village/Debts.java",
     "v.extra.frozen.add(who);",
     "v.extra.frozen.add(who); for (Lot l : VillageManager.lotsOf(level, v)) if (l.owner.equals(who)) for (net.minecraft.core.BlockPos q : "
     "net.minecraft.core.BlockPos.betweenClosed(l.area.minX(), level.getMinY(), l.area.minZ(), l.area.maxX(), level.getMaxY(), l.area.maxZ())) "
     "if (!level.getBlockState(q).isAir()) level.removeBlock(q, false);", "runGametest"),
    ("dívida: pagar não descongela", J + "village/Debts.java",
     "boolean was = x.frozen.remove(who);", "boolean was = x.frozen.contains(who);", "runGametest"),
    ("dívida: congela sem esperar (regra)", J + "logic/DebtRules.java",
     "return since >= 0 && daysInDebt(since, today) >= Math.max(1, freezeDays);", "return since >= 0;", "test"),
    # 0.2.0 — mercador
    ("mercador: nunca se vai embora", J + "logic/MerchantRules.java",
     "return present && arrived >= 0 && today - arrived >= Math.max(1, stay);", "return false;", "test"),
    ("mercador: fica com o stock do vanilla", J + "village/TravelingMerchant.java",
     "offers.clear(); // …e troca-as pelas nossas", "", "runGametest"),
    ("mercador: não leva o burro", J + "village/TravelingMerchant.java",
     "for (var id : new java.util.UUID[] {x.merchant, x.donkey}) {", "for (var id : new java.util.UUID[] {x.merchant}) {", "runGametest"),
    # 0.2.0 — vendedores dos mods e móveis
    ("vendedores: posto sem o mod", J + "registry/VilasBlocks.java",
     "return com.tcordeiro.vilas.compat.Mods.loaded(mod) ? block(name, factory, props) : null;", "return block(name, factory, props);",
     "runGametest -PnoMods"),
    ("móveis: o processador mexe sem o Móveis", J + "worldgen/FurnitureProcessor.java",
     "if (current == null || !Mods.loaded(Mods.MOVEIS)) return current;", "if (current == null) return current;", "runGametest -PnoMods"),
    ("móveis: a cama de casal só com um lado", J + "worldgen/FurnitureProcessor.java",
     'return with(b, "side", s.is(LEFT_BED) ? "left" : "right");', 'return with(b, "side", "left");', "runGametest"),
    ("iluminação: a fonte da Prefeitura sem fio (Industrial 0.11)", J + "build/LightingWork.java",
     "if (!poles.isEmpty()) for (BlockPos src : sources) if (kit.link(level, poles.get(0), src)) p.linked++;", "", "runGametest"),
    ("iluminação: postes sem a altura livre", J + "build/Builders.java",
     "for (int i = 1; free && i < at.clear(); i++) free = Ground.mayBuild(level, pos.above(i));", "", "runGametest"),
    ("Cambista: com desconto de reputação", "src/main/resources/data/tvilas/villager_trade/money_changer/1/emerald_note_20.json",
     '"reputation_discount": 0.0', '"reputation_discount": 0.05', "runGametest"),
    ("Cambista: margem no câmbio", "src/main/resources/data/tvilas/villager_trade/money_changer/2/note_10_emerald.json",
     '"count": 3', '"count": 2', "runGametest"),
    ("missões: Padeiro com pão barato dá lucro (ficheiro de trocas)", "src/main/resources/data/tvilas/villager_trade/baker/1/emerald_bread.json",
     '"count": 6', '"count": 12', "runGametest"),
]


def gradle(task):
    r = subprocess.run(["./gradlew", *task.split(), "--offline", "-q"], cwd=ROOT, capture_output=True, text=True)
    return r.returncode == 0


def main():
    killed = 0
    only = sys.argv[1:]  # filtro opcional: só as mutações cujo nome contém um destes textos
    todo = [m for m in MUTATIONS if not only or any(o in m[0] for o in only)]
    for name, rel, old, new, task in todo:
        edits = rel if isinstance(rel, list) else [(rel, old, new)]
        origs = {}
        try:
            for r_, o_, n_ in edits:
                path = os.path.join(ROOT, r_)
                orig = origs.setdefault(path, open(path).read())
                cur = open(path).read()
                assert cur.count(o_) == 1, f"{name}: o trecho a mutar não está (ou está repetido) em {r_}"
                open(path, "w").write(cur.replace(o_, n_))
            survived = gradle(task)
        finally:
            for path, orig in origs.items():
                open(path, "w").write(orig)
        print(f"  {'✗ SOBREVIVEU' if survived else '✓ morta'}  [{task}]  {name}")
        killed += 0 if survived else 1
    print(f"{killed}/{len(todo)} mutações mortas pelos testes")
    sys.exit(0 if killed == len(todo) else 1)


if __name__ == "__main__":
    main()
