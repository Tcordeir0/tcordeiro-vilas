package com.tcordeiro.vilas.test;

import java.util.ArrayList;
import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.CivicWork;
import com.tcordeiro.vilas.build.LightingWork;
import com.tcordeiro.vilas.build.StreetLightKit;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.ui.TownDialog;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vilas 0.3.0 no jogo (13/11): trocas de um aldeão vanilla em CubCoin (1280×720 e 854×480) e um pagamento com troco; uma vila
 * gerada SEM Prefeitura onde se põe a Mesa à mão: o Prefeito chega (e continua Prefeito), o quadro diz o que falta e o Construtor
 * faz a Prefeitura à vista; a iluminação pública dessa vila de dia e de noite (postes acesos de perto) e a fonte com um fio.
 * A câmara é posta sempre em ar livre, com linha de vista até ao alvo ({@link #clear}). Sem som, pt_br.
 */
public class Vilas030NoJogoClientTest implements FabricClientGameTest {
	static final int Y = -60;
	private final List<String> notes = new ArrayList<>();
	private TestSingleplayerContext sp;

	static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void log(String s) {
		System.out.println("[Vilas030NoJogo] " + s);
		notes.add(s);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot("vilas030_" + name);
	}

	/**
	 * A câmara em ar livre a olhar para o alvo: sobe a posição pedida até o bloco dos olhos (e o de baixo) serem ar e não haver
	 * nenhum bloco entre os olhos e o alvo (só se aceita bater num bloco a menos de 1,5 do alvo — o próprio alvo).
	 */
	static Vec3 clear(ServerLevel l, Vec3 eye, Vec3 target) {
		Vec3 e = eye;
		for (int k = 0; k < 40; k++, e = e.add(0, 1, 0)) {
			BlockPos b = BlockPos.containing(e);
			if (!l.getBlockState(b).isAir() || !l.getBlockState(b.below()).isAir() || !l.getBlockState(b.above()).isAir()) continue;
			var hit = l.clip(new ClipContext(e, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(target) < 1.5) return e;
		}
		return eye.add(0, 40, 0);
	}

	/**
	 * A câmara à volta do alvo, à distância {@code dist} e à altura dos olhos {@code up} acima dele: tenta 8 lados e fica no primeiro
	 * com ar livre e vista limpa sem subir mais de 2 blocos (senão, o primeiro lado, a subir até ver).
	 */
	private void camAround(ClientGameTestContext ctx, double tx, double ty, double tz, double dist, double up, double lookY, int wait) {
		Vec3 target = new Vec3(tx, ty + lookY, tz);
		Vec3 best = sp.getServer().computeOnServer(s -> {
			for (int k = 0; k < 8; k++) {
				double a = Math.PI / 2 + k * Math.PI / 4;
				Vec3 want = new Vec3(tx + Math.cos(a) * dist, ty + up, tz + Math.sin(a) * dist);
				Vec3 got = clear(s.overworld(), want, target);
				if (got.y - want.y <= 2.01) return got;
			}
			return null;
		});
		if (best == null) {
			cam(ctx, tx, ty + up, tz + dist, tx, ty + lookY, tz, wait);
			return;
		}
		cam(ctx, best.x, best.y, best.z, tx, ty + lookY, tz, wait);
	}

	private void cam(ClientGameTestContext ctx, double x, double eye, double z, double tx, double ty, double tz, int wait) {
		Vec3 target = new Vec3(tx, ty, tz);
		Vec3 e = sp.getServer().computeOnServer(s -> clear(s.overworld(), new Vec3(x, eye, z), target));
		double dx = tx - e.x, dy = ty - e.y, dz = tz - e.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runOnServer(s -> player(s).teleportTo(s.overworld(), e.x, e.y - 1.62, e.z, java.util.Set.of(), yaw, pitch, false));
		ctx.waitTicks(wait);
		sp.getConnection().waitForChunksRender();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(120);
		try (TestSingleplayerContext s0 = ctx.worldBuilder().create()) {
			sp = s0;
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"time set 6000", "gamerule advance_time false", "gamerule spawn_mobs false", "gamerule advance_weather false",
					"weather clear", "gamerule random_tick_speed 0", "gamerule send_command_feedback false", "gamemode creative @a"})
				sp.getServer().runCommand(c);
			ctx.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			sp.getServer().runOnServer(s -> {
				var p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			trades(ctx);
			oldVillage(ctx);
		}
		System.out.println("[Vilas030NoJogo] RESUMO: " + notes);
	}

	// ---------------------------------------------------------------- 1) aldeão vanilla em CubCoin, com troco

	private void trades(ClientGameTestContext ctx) {
		log("CubCoin=" + TVilas.CUBCOIN + " conversão=" + CoinTrades.active());
		int id = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Villager v = EntityTypes.VILLAGER.create(l, EntitySpawnReason.COMMAND);
			v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(), VillagerProfession.FARMER).withLevel(2));
			v.setNoAi(true);
			v.snapTo(0.5, Y, -3.5, 0, 0);
			l.addFreshEntity(v);
			v.getOffers();
			var p = player(s);
			p.getInventory().clearContent();
			CubCoinTrade.give(p, 10000); // uma nota de 100 CB
			p.getInventory().add(CubCoinTrade.money(25, 3));
			return v.getId();
		});
		cam(ctx, 0.5, Y + 1.62, -0.5, 0.5, Y + 1.4, -3.5, 10);
		for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
			ctx.getInput().resizeWindow(size[0], size[1]);
			ctx.waitTicks(8);
			sp.getServer().runOnServer(s -> {
				if (s.overworld().getEntity(id) instanceof Villager v) v.mobInteract(player(s), InteractionHand.MAIN_HAND);
			});
			ctx.waitTicks(15);
			ctx.getInput().setCursorPos(2, 2);
			ctx.waitTicks(3);
			shot(ctx, "01_agricultor_trocas_em_CB_" + size[0] + "x" + size[1]);
			if (size[0] == 1280) {
				// pagar o pão (pede 1 esmeralda = 40 CB) com a nota de 100 CB: troca pronta, depois tirada, com o troco no inventário
				String before = sp.getServer().computeOnServer(s -> {
					var p = player(s);
					if (!(p.containerMenu instanceof MerchantMenu menu)) return "sem ecrã de trocas";
					int idx = -1;
					for (int i = menu.getOffers().size() - 1; i >= 0; i--) if (CoinTrades.of(menu.getOffers().get(i)).tvilas$emeraldsA() > 0) idx = i;
					if (idx < 0) return "sem troca a pagar";
					menu.setSelectionHint(idx);
					// a nota sai do inventário para a casa de pagamento (como o jogador a arrastar)
					for (int i = 3; i < 39; i++)
						if (CubCoinTrade.isMoney(menu.getSlot(i).getItem()) && CubCoinTrade.value(menu.getSlot(i).getItem()) == 10000) {
							menu.getSlot(0).set(menu.getSlot(i).getItem().copy());
							menu.getSlot(i).set(ItemStack.EMPTY);
							break;
						}
					menu.slotsChanged(menu.getSlot(0).container);
					menu.broadcastChanges();
					return "troca " + idx + ": preço " + CoinTrades.of(menu.getOffers().get(idx)).tvilas$emeraldsA() * CoinTrades.emeraldCents()
							+ "¢, carteira " + CubCoinTrade.balance(p) + "¢ + casa " + CubCoinTrade.value(menu.getSlot(0).getItem()) + "¢, resultado="
							+ menu.getSlot(2).getItem();
				});
				log(before);
				ctx.waitTicks(10);
				shot(ctx, "02a_pagamento_nota_100_na_casa");
				String after = sp.getServer().computeOnServer(s -> {
					var p = player(s);
					if (!(p.containerMenu instanceof MerchantMenu menu)) return "sem ecrã";
					menu.clicked(2, 0, ContainerInput.QUICK_MOVE, p);
					menu.broadcastChanges();
					return "depois: carteira " + CubCoinTrade.balance(p) + "¢, casa " + (menu.getSlot(0).getItem().isEmpty() ? 0 : CubCoinTrade.value(menu.getSlot(0).getItem()))
							+ "¢ (troco no inventário)";
				});
				log(after);
				ctx.waitTicks(10);
				shot(ctx, "02b_pagamento_com_troco_no_inventario");
			}
			ctx.runOnClient(mc -> mc.player.closeContainer());
			ctx.waitTicks(5);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(10);
	}

	// ---------------------------------------------------------------- 2) vila gerada sem Prefeitura: Mesa à mão, Prefeito, quadro, obra, postes

	private void oldVillage(ClientGameTestContext ctx) {
		BlockPos center = null;
		for (int i = 0; i < 24 && center == null; i++) {
			int vx = 2000 + i * 600, vz = 4000;
			sp.getServer().runCommand("forceload add " + (vx - 96) + " " + (vz - 96) + " " + (vx + 96) + " " + (vz + 96));
			cam(ctx, vx, Y + 60, vz + 60, vx, Y, vz, 30);
			sp.getServer().runCommand("place structure minecraft:village_plains " + vx + " " + Y + " " + vz);
			ctx.waitTicks(30);
			final int fx = vx, fz = vz;
			BlockPos bell = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				BlockPos b = null;
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(fx - 80, Y - 3, fz - 80), new BlockPos(fx + 80, Y + 14, fz + 80))) {
					var st = l.getBlockState(p);
					if (st.is(VilasBlocks.MAYOR_DESK)) return null; // já tem Prefeitura: não é «antiga»
					if (b == null && st.is(net.minecraft.world.level.block.Blocks.BELL)) b = p.immutable();
				}
				return b;
			});
			log("vila gerada " + i + " em " + vx + "," + vz + ": sino=" + bell);
			if (bell != null) center = bell;
		}
		if (center == null) {
			log("nenhuma vila sem Prefeitura nas 24 tentativas");
			return;
		}
		final BlockPos bell = center;
		// a Mesa à mão, ao lado do sino (num sítio livre por cima do chão)
		BlockPos desk = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			for (BlockPos p : BlockPos.betweenClosed(bell.offset(-4, -2, -4), bell.offset(4, 2, 4)))
				if (l.getBlockState(p).isAir() && l.getBlockState(p.above()).isAir() && l.getBlockState(p.below()).isSolidRender()) {
					BlockPos d = p.immutable();
					l.setBlock(d, VilasBlocks.MAYOR_DESK.defaultBlockState(), 3);
					Village v = VillageManager.found(l, d, 80);
					v.civic.owner = player(s).getUUID();
					v.economy.treasury = 100_000;
					v.level = 1; // Vila: compra a iluminação
					return d;
				}
			return null;
		});
		log("Mesa posta em " + desk);
		if (desk == null) return;
		cam(ctx, desk.getX() + 0.5, desk.getY() + 6, desk.getZ() + 8.5, desk.getX() + 0.5, desk.getY(), desk.getZ() + 0.5, 20);
		ctx.waitTicks(260); // a Mesa regista a vila (área cívica, o que falta) e chama o Prefeito
		String mayor = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			var list = l.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(desk).inflate(48), e -> e.isAlive() && VillageManager.isMayor(e));
			return list.isEmpty() ? "sem Prefeito; falta=" + v.civic.missing()
					: "Prefeito " + list.get(0).getUUID() + " " + list.get(0).getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("?")
							+ " xp=" + list.get(0).getVillagerXp() + " em " + list.get(0).blockPosition().toShortString() + "; falta=" + v.civic.missing();
		});
		log(mayor);
		ctx.waitTicks(400);
		String still = sp.getServer().computeOnServer(s -> {
			var list = s.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(desk).inflate(48), e -> e.isAlive() && VillageManager.isMayor(e));
			if (list.isEmpty()) return null;
			Villager m = list.get(0);
			m.setNoAi(true);
			BlockPos front = desk.south(2);
			if (s.overworld().getBlockState(front).isAir()) m.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0, 0);
			m.setYRot(0);
			m.setYHeadRot(0);
			m.setYBodyRot(0); // virado para sul (para a câmara)
			return m.getBlockX() + ";" + m.getBlockY() + ";" + m.getBlockZ() + " " + m.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		});
		log("400 ticks depois, Prefeito: " + still);
		if (still != null) {
			String[] c = still.split(" ")[0].split(";");
			double mx = Double.parseDouble(c[0]) + 0.5, my = Double.parseDouble(c[1]), mz = Double.parseDouble(c[2]) + 0.5;
			cam(ctx, mx, my + 1.7, mz + 2.6, mx, my + 1.4, mz, 15);
			shot(ctx, "03_prefeito_com_profissao");
		}
		// o quadro: o que falta
		for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
			ctx.getInput().resizeWindow(size[0], size[1]);
			ctx.waitTicks(8);
			sp.getServer().runOnServer(s -> TownDialog.open(player(s), s.overworld(), VillageManager.byDesk(s.overworld(), desk)));
			ctx.waitTicks(15);
			ctx.getInput().setCursorPos(2, 2);
			ctx.waitTicks(3);
			shot(ctx, "04_quadro_o_que_falta_" + size[0] + "x" + size[1]);
			ctx.getInput().setCursorPos(size[0] / 2.0, size[1] / 2.0); // o rato por cima do texto, para a roda o deslizar
			ctx.waitTicks(2);
			ctx.getInput().scroll(-6);
			ctx.waitTicks(5);
			shot(ctx, "04_quadro_lote_prestigio_" + size[0] + "x" + size[1]);
			ctx.getInput().scroll(-20);
			ctx.waitTicks(5);
			shot(ctx, "04_quadro_missoes_" + size[0] + "x" + size[1]);
			ctx.getInput().setCursorPos(2, 2);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(10);
		// os pedidos dos aldeões: o «!» por cima da cabeça e o ecrã do pedido
		String req = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			com.tcordeiro.vilas.village.VillagerRequests.tick(l, v);
			if (v.civic.batch.requests.isEmpty()) { // a vila acabou de ser gerada: os aldeões ainda arranjam emprego — um lote novo
				v.civic.batch.id--;
				com.tcordeiro.vilas.village.VillagerRequests.tick(l, v);
			}
			for (var e : v.civic.batch.requests.entrySet())
				if (l.getEntity(e.getKey()) instanceof Villager vi && vi.entityTags().contains(com.tcordeiro.vilas.village.VillagerRequests.TAG)) {
					vi.setNoAi(true);
					// para a foto: num sítio aberto ao pé da Mesa (o «!» vê-se de longe; atrás de uma banca não)
					for (BlockPos q : BlockPos.betweenClosed(desk.offset(-8, -1, -8), desk.offset(8, 2, 8))) {
						if (q.distSqr(desk) < 9) continue;
						boolean open = true;
						for (BlockPos o : BlockPos.betweenClosed(q.offset(-2, 0, -2), q.offset(2, 3, 2))) open &= l.getBlockState(o).isAir();
						if (open && l.getBlockState(q.below()).isSolidRender()) {
							vi.snapTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, 0, 0);
							break;
						}
					}
					vi.setYRot(0);
					vi.setYHeadRot(0);
					vi.setYBodyRot(0);
					return vi.getUUID() + ";" + vi.getBlockX() + ";" + vi.getBlockY() + ";" + vi.getBlockZ() + ";" + e.getValue().target() + "x" + e.getValue().count();
				}
			return null;
		});
		log("pedidos: " + sp.getServer().computeOnServer(s -> VillageManager.byDesk(s.overworld(), desk).civic.batch.requests.size()) + "; com «!»: " + req);
		if (req != null) {
			String[] r = req.split(";");
			java.util.UUID rid = java.util.UUID.fromString(r[0]);
			double rx = Integer.parseInt(r[1]) + 0.5, ry = Integer.parseInt(r[2]), rz = Integer.parseInt(r[3]) + 0.5;
			ctx.waitTicks(5);
			String[] r2 = sp.getServer().computeOnServer(s -> s.overworld().getEntity(rid) instanceof Villager vi
					? vi.getX() + ";" + vi.getY() + ";" + vi.getZ() : r[1] + ";" + r[2] + ";" + r[3]).split(";");
			rx = Double.parseDouble(r2[0]);
			ry = Double.parseDouble(r2[1]);
			rz = Double.parseDouble(r2[2]);
			ctx.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); // com o HUD escondido (F1) os nomes por cima das cabeças não aparecem
			});
			camAround(ctx, rx, ry, rz, 4.0, 2.0, 2.0, 15);
			shot(ctx, "08a_aldeao_com_pedido_exclamacao");
			ctx.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
				ctx.getInput().resizeWindow(size[0], size[1]);
				ctx.waitTicks(8);
				sp.getServer().runOnServer(s -> {
					ServerLevel l = s.overworld();
					if (l.getEntity(rid) instanceof Villager vi)
						com.tcordeiro.vilas.village.VillagerRequests.open(player(s), l, VillageManager.byDesk(l, desk), vi);
				});
				ctx.waitTicks(15);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(3);
				shot(ctx, "08b_ecra_do_pedido_" + size[0] + "x" + size[1]);
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				ctx.waitTicks(5);
			}
			ctx.getInput().resizeWindow(1280, 720);
			ctx.waitTicks(10);
		}
		// a vila cresce: prestígio bastante → um morador novo chega a pé pela borda
		String arrived = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			v.civic.prestige = com.tcordeiro.vilas.VilasConfig.get().prestige_per_resident;
			var step = com.tcordeiro.vilas.village.Growth.step(l, v);
			var list = l.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(desk).inflate(90), e -> e.entityTags().contains(com.tcordeiro.vilas.village.Growth.TAG));
			return step + (list.isEmpty() ? "" : ";" + list.get(0).getBlockX() + ";" + list.get(0).getBlockY() + ";" + list.get(0).getBlockZ());
		});
		log("crescer: " + arrived);
		if (arrived.contains(";")) {
			String[] a = arrived.split(";");
			double ax = Integer.parseInt(a[1]) + 0.5, ay = Integer.parseInt(a[2]), az = Integer.parseInt(a[3]) + 0.5;
			ctx.waitTicks(60); // já a caminho da praça
			String now = sp.getServer().computeOnServer(s -> {
				var list = s.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(desk).inflate(90), e -> e.entityTags().contains(com.tcordeiro.vilas.village.Growth.TAG));
				return list.isEmpty() ? null : list.get(0).getBlockX() + ";" + list.get(0).getBlockY() + ";" + list.get(0).getBlockZ();
			});
			if (now != null) {
				String[] b2 = now.split(";");
				ax = Integer.parseInt(b2[0]) + 0.5;
				ay = Integer.parseInt(b2[1]);
				az = Integer.parseInt(b2[2]) + 0.5;
			}
			double dx = desk.getX() + 0.5 - ax, dz = desk.getZ() + 0.5 - az, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			cam(ctx, ax + 4 * dx / len, ay + 2.2, az + 4 * dz / len, ax, ay + 1.2, az, 10);
			shot(ctx, "09a_morador_novo_a_chegar");
			cam(ctx, ax - 7 * dx / len, ay + 5, az - 7 * dz / len, desk.getX() + 0.5, desk.getY() + 1, desk.getZ() + 0.5, 10);
			shot(ctx, "09b_morador_novo_a_caminho_da_praca");
		}
		// a obra: o Prefeito encomenda o primeiro edifício que falta; o Construtor faz à vista
		String work = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			Project p = CivicWork.commissionNext(l, v);
			if (p == null) return null;
			return p.kind + ";" + p.site.getX() + ";" + p.site.getY() + ";" + p.site.getZ() + ";" + p.total;
		});
		log("obra: " + work);
		if (work != null) {
			String[] w = work.split(";");
			double sx = Double.parseDouble(w[1]) + 0.5, sy = Double.parseDouble(w[2]), sz = Double.parseDouble(w[3]) + 0.5;
			// para a foto: mais de metade já feita (como se o Construtor andasse nisto há uns minutos); o resto é à vista
			sp.getServer().runOnServer(s -> {
				ServerLevel l = s.overworld();
				Project p = VillageManager.byDesk(l, desk).extra.projects.get(0);
				int n = p.pending.size() * 55 / 100;
				for (int k = 0; k < n && !p.pending.isEmpty(); k++) {
					var at = p.pending.remove(0);
					if (l.getBlockState(at.pos()).isAir()) l.setBlock(at.pos(), at.state(), 2 | 16);
					p.placed++;
				}
			});
			cam(ctx, sx + 12, sy + 9, sz + 12, sx, sy + 1, sz, 20);
			ctx.waitTicks(500); // o Construtor chega e põe 1 bloco por segundo
			String prog = sp.getServer().computeOnServer(s -> {
				Village v = VillageManager.byDesk(s.overworld(), desk);
				return v.extra.projects.isEmpty() ? "acabada" : v.extra.projects.get(0).kind + " " + v.extra.projects.get(0).placed + "/" + v.extra.projects.get(0).total
						+ " construtor=" + v.extra.builder;
			});
			log("obra à vista: " + prog);
			cam(ctx, sx + 12, sy + 9, sz + 12, sx, sy + 1, sz, 5);
			shot(ctx, "05a_construtor_a_fazer_" + w[0].toLowerCase());
			// o Construtor de perto
			String b = sp.getServer().computeOnServer(s -> {
				Village v = VillageManager.byDesk(s.overworld(), desk);
				return v.extra.builder != null && s.overworld().getEntity(v.extra.builder) instanceof Villager bv ? bv.blockPosition().toShortString() : null;
			});
			if (b != null) {
				String[] c = b.split(", ");
				double bx = Integer.parseInt(c[0]) + 0.5, by = Integer.parseInt(c[1]), bz = Integer.parseInt(c[2]) + 0.5;
				// de frente para ele: do lado da obra (ele olha para o bloco que põe)
				double ux = sx - bx, uz = sz - bz, len = Math.max(0.1, Math.sqrt(ux * ux + uz * uz));
				cam(ctx, bx + 3.5 * ux / len, by + 2.2, bz + 3.5 * uz / len, bx, by + 1.2, bz, 10);
				shot(ctx, "05b_construtor_de_perto");
				cam(ctx, bx - 6 * ux / len, by + 4, bz - 6 * uz / len, sx, sy + 2, sz, 10);
				shot(ctx, "05b2_construtor_e_a_obra");
			}
			sp.getServer().runOnServer(s -> Builders.overnight(s.overworld(), VillageManager.byDesk(s.overworld(), desk)));
			ctx.waitTicks(20);
			cam(ctx, sx + 14, sy + 12, sz + 14, sx, sy + 2, sz, 20);
			shot(ctx, "05c_edificio_acabado");
		}
		// a iluminação pública desta vila (Vila): obra de noite, depois de dia e de noite
		String light = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			Project p = LightingWork.create(l, v);
			if (p == null) return "sem ruas para postes";
			v.extra.lighting = true;
			Builders.start(l, v, p);
			int put = Builders.overnight(l, v);
			return "postes=" + v.extra.poles + " blocos=" + put + " âncoras=" + v.extra.lightAnchors.size();
		});
		log("iluminação: " + light);
		List<BlockPos> poles = new ArrayList<>(), source = new ArrayList<>();
		String lit = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = VillageManager.byDesk(l, desk);
			StreetLightKit kit = StreetLightKit.forVillage(l, v);
			for (long a : v.extra.lightAnchors) {
				BlockPos q = BlockPos.of(a);
				if (kit.isBattery(l.getBlockState(q))) source.add(q);
				else poles.add(q);
			}
			return "fonte=" + source + " fios na fonte=" + (source.isEmpty() ? -1 : LightingWork.wires(l, source.get(0))) + " postes=" + poles.size();
		});
		log(lit);
		ctx.waitTicks(200);
		// a bateria cheia (os painéis demoram dias de jogo a enchê-la): para as fotos de noite
		log("bateria: " + sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			for (BlockPos q : source)
				try {
					Object be = l.getBlockEntity(q), en = be.getClass().getMethod("energy").invoke(be);
					long cap = ((Number) en.getClass().getMethod("capacity").invoke(en)).longValue();
					en.getClass().getMethod("setStored", long.class).invoke(en, cap);
					return "cheia em " + q.toShortString() + " (" + cap + " J)";
				} catch (ReflectiveOperationException | RuntimeException e) {
					return "não deu para encher: " + e;
				}
			return "sem bateria";
		}));
		if (!poles.isEmpty()) {
			BlockPos q = poles.get(0);
			cam(ctx, q.getX() + 8.5, q.getY() + 6, q.getZ() + 8.5, q.getX() + 0.5, q.getY() + 2, q.getZ() + 0.5, 20);
			shot(ctx, "06a_postes_de_dia");
		}
		if (!source.isEmpty()) {
			BlockPos q = source.get(0);
			for (int[] d : new int[][] {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
				cam(ctx, q.getX() + 0.5 + d[0], q.getY() + 2.5, q.getZ() + 0.5 + d[1], q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 15);
				shot(ctx, "07_fonte_um_fio_" + (d[0] > 0 ? "e" : "o") + (d[1] > 0 ? "s" : "n"));
			}
		}
		ctx.waitTicks(100);
		sp.getServer().runCommand("time set 18000");
		ctx.waitTicks(120);
		String on = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			int n = 0;
			for (BlockPos q : poles)
				for (int k = 0; k < 6; k++) {
					var st = l.getBlockState(q.above(k));
					boolean any = false;
					for (var prop : st.getProperties())
						if (prop.getName().equals("lit") && (Boolean) st.getValue(prop)) any = true;
					if (any || st.getLightEmission() > 0) {
						n++;
						break;
					}
				}
			return n + " de " + poles.size() + " a dar luz";
		});
		log("de noite: " + on);
		for (int k = 0; k < Math.min(4, poles.size()); k++) {
			BlockPos q = poles.get(k);
			BlockPos lamp = sp.getServer().computeOnServer(s -> {
				for (int y = 6; y >= 0; y--) if (s.overworld().getBlockState(q.above(y)).getLightEmission() > 0) return q.above(y);
				return q.above(3);
			});
			camAround(ctx, lamp.getX() + 0.5, lamp.getY(), lamp.getZ() + 0.5, 3.5, 0.2, 0.4, 20); // de perto, em ar livre, a ver o lampião
			shot(ctx, "06b_poste_" + k + "_aceso_de_noite_perto");
		}
		if (!poles.isEmpty()) {
			BlockPos q = poles.get(0);
			cam(ctx, q.getX() + 10.5, q.getY() + 8, q.getZ() + 10.5, q.getX() + 0.5, q.getY() + 2, q.getZ() + 0.5, 20);
			shot(ctx, "06c_rua_de_noite");
		}
		sp.getServer().runCommand("time set 6000");
		ctx.waitTicks(10);
	}
}
