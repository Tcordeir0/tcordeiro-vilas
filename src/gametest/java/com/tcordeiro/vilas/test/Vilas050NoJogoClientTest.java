package com.tcordeiro.vilas.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.life.Election;
import com.tcordeiro.vilas.life.Fishing;
import com.tcordeiro.vilas.life.Helper;
import com.tcordeiro.vilas.life.Mail;
import com.tcordeiro.vilas.life.Play;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Lot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.life.Care;
import com.tcordeiro.vilas.life.Climate;
import com.tcordeiro.vilas.life.Festa;
import com.tcordeiro.vilas.life.Market;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeVillager;
import com.tcordeiro.vilas.life.Places;
import com.tcordeiro.vilas.life.Resident;
import com.tcordeiro.vilas.life.Talk;
import com.tcordeiro.vilas.life.Task;
import com.tcordeiro.vilas.life.Tasks;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.ui.TownDialog;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vilas 0.5.0 no jogo (13/11): Carteiro, mercado semanal, eleições, pescador, crianças a brincar, missão da festa, ajudante, «!» em
 * balão — vídeos à velocidade real e fotos 1280×720 e 854×480 dos ecrãs novos; o tick com 40 aldeões (mercado e crianças incluídos).
 * (Base igual à 0.4.0:)
 * Vilas 0.4.0 no jogo (13/11): uma vila de planície posta com {@code /place structure minecraft:village_plains} (com Prefeitura,
 * Taverna e Praça), 40 aldeões, e cada mecânica nova filmada (vídeos à velocidade real: {@code ticks.txt} por pasta) e fotografada.
 * Mede o tick com 40 aldeões, com e sem a vida dos aldeões. A câmara fica sempre em ar livre, com vista até ao alvo. Sem som, pt_br.
 */
public class Vilas050NoJogoClientTest implements FabricClientGameTest {
	static final int Y = -60;
	private final List<String> notes = new ArrayList<>();
	private TestSingleplayerContext sp;
	private BlockPos desk;

	static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void log(String s) {
		System.out.println("[Vilas050NoJogo] " + s);
		notes.add(s);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of("vilas050_" + name).disableCounterPrefix().withDestinationDir(Path.of("fotos")));
	}

	private int film(ClientGameTestContext ctx, String dir, int n, Consumer<Integer> each) {
		StringBuilder ticks = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (each != null) each.accept(i);
			long t = sp.getServer().computeOnServer(s -> s.overworld().getGameTime());
			ticks.append(String.format("f%04d %d%n", i, t));
			ctx.takeScreenshot(TestScreenshotOptions.of(String.format("f%04d", i)).disableCounterPrefix().withDestinationDir(Path.of("video", dir)));
			ctx.waitTicks(1);
		}
		try {
			Path p = Path.of("video", dir, "ticks.txt");
			java.nio.file.Files.createDirectories(p.getParent());
			java.nio.file.Files.writeString(p, ticks);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		return n;
	}

	// ---------------------------------------------------------------- câmara (sempre em ar livre, com vista até ao alvo)

	static Vec3 clear(ServerLevel l, Vec3 eye, Vec3 target) {
		Vec3 e = eye;
		for (int k = 0; k < 40; k++, e = e.add(0, 1, 0)) {
			BlockPos b = BlockPos.containing(e);
			if (!l.getBlockState(b).isAir() || !l.getBlockState(b.below()).isAir() || !l.getBlockState(b.above()).isAir()) continue;
			if (!l.canSeeSky(b)) continue; // ao ar livre
			var hit = l.clip(new ClipContext(e, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(target) < 1.5) return e;
		}
		return eye.add(0, 40, 0);
	}

	private void camAround(ClientGameTestContext ctx, Vec3 t, double dist, double up, int wait) {
		Vec3 best = sp.getServer().computeOnServer(s -> {
			for (int k = 0; k < 8; k++) {
				double a = Math.PI / 2 + k * Math.PI / 4;
				Vec3 want = new Vec3(t.x + Math.cos(a) * dist, t.y + up, t.z + Math.sin(a) * dist);
				Vec3 got = clear(s.overworld(), want, t);
				if (got.y - want.y <= 2.01) return got;
			}
			return clear(s.overworld(), new Vec3(t.x, t.y + up, t.z + dist), t);
		});
		look(ctx, best, t, wait);
	}

	private void look(ClientGameTestContext ctx, Vec3 e, Vec3 t, int wait) {
		double dx = t.x - e.x, dy = t.y - e.y, dz = t.z - e.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runOnServer(s -> player(s).teleportTo(s.overworld(), e.x, e.y - 1.62, e.z, java.util.Set.of(), yaw, pitch, false));
		ctx.waitTicks(wait);
		sp.getConnection().waitForChunksRender();
	}

	private void chat(ClientGameTestContext ctx, boolean show) {
		ctx.runOnClient(mc -> {
			mc.options.chatVisibility().set(show ? net.minecraft.world.entity.player.ChatVisiblity.FULL : net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.broadcastOptions();
		});
	}

	private void hud(ClientGameTestContext ctx, boolean show) {
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == show) mc.gui.hud.toggle();
		});
	}

	// ---------------------------------------------------------------- ajudas no servidor

	Village village(MinecraftServer s) {
		return VillageManager.byDesk(s.overworld(), desk);
	}

	Places places(MinecraftServer s) {
		return Life.places(s.overworld(), village(s));
	}

	/** Larga todas as tarefas e tempos de espera (entre cenas). */
	void reset(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Village v = village(s);
		for (Villager vi : Life.villagers(l, v)) {
			Task t = Tasks.get(vi);
			if (t != null) Tasks.end(l, vi, t, "cena");
			LifeVillager.Memo m = ((LifeVillager) vi).tvilas$memo();
			m.nextShelter = m.nextSocial = m.nextTalk = m.nextReact = m.nextWork = 0;
			if (vi.isSleeping()) vi.stopSleeping();
		}
		Climate.forced = Double.NaN;
		Climate.forcedRain = null;
		Festa.forceActive = null;
		Life.rescan(v); // a luz do céu já assentou: os sítios cobertos contam
	}

	/** O centro de um sítio da vila (Praça, Taverna…), ou a Mesa. */
	Vec3 spot(MinecraftServer s, String kind) {
		if (!s.isSameThread()) return sp.getServer().computeOnServer(x -> spot(x, kind)); // o mundo só se lê na thread do servidor
		Places p = places(s);
		Places.Spot sp = p.spots.get(kind);
		BlockPos c = sp != null ? sp.center : desk;
		return Vec3.atBottomCenterOf(c);
	}

	/** Um sítio aberto (sem nada à volta) perto de {@code near}, no chão. */
	static BlockPos open(ServerLevel l, BlockPos near, int min, int r) {
		for (int d = min; d <= r; d++)
			for (BlockPos q : BlockPos.betweenClosed(near.offset(-d, -3, -d), near.offset(d, 4, d))) {
				boolean ok = l.getBlockState(q.below()).isSolidRender() && l.canSeeSky(q);
				for (BlockPos o : BlockPos.betweenClosed(q.offset(-2, 0, -2), q.offset(2, 2, 2))) ok &= l.getBlockState(o).isAir();
				if (ok) return q.immutable();
			}
		return near;
	}

	Villager spawn(ServerLevel l, BlockPos at, net.minecraft.resources.ResourceKey<VillagerProfession> prof, boolean baby) {
		Villager v = EntityTypes.VILLAGER.create(l, EntitySpawnReason.MOB_SUMMONED);
		if (prof != null) v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(), prof).withLevel(2));
		v.setVillagerXp(1);
		if (baby) v.setAge(-24000);
		v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		l.addFreshEntity(v);
		return v;
	}

	// ---------------------------------------------------------------- o teste

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!java.nio.file.Files.exists(Path.of("vilas050.flag")) || java.nio.file.Files.exists(Path.of("vilas060.flag"))) return; // só quando se pede (VILAS050=1): as outras versões têm os seus testes
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(120);
		log("mods: Móveis=" + Mods.loaded(Mods.MOVEIS) + " Clima=" + Climate.clima() + " CubCoin=" + Mods.loaded(Mods.CUBCOIN) + " Industrial="
				+ Mods.loaded(Mods.INDUSTRIAL));
		try (TestSingleplayerContext s0 = ctx.worldBuilder().create()) {
			sp = s0;
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"time set 6000", "gamerule advance_time false", "gamerule spawn_mobs false", "gamerule advance_weather false",
					"weather clear", "gamerule random_tick_speed 0", "gamerule send_command_feedback false", "gamemode creative @a"})
				sp.getServer().runCommand(c);
			hud(ctx, false);
			chat(ctx, false);
			sp.getServer().runOnServer(s -> {
				var p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false));
			});
			if (!findVillage(ctx)) {
				log("nenhuma vila com Prefeitura, Taverna e Praça");
				return;
			}
			populate(ctx);
			perf(ctx);
			String[] scenes = {"market", "mail", "election", "fisher", "kids", "festa_decor", "helper", "request", "gestures"};
			String only = System.getenv("VILAS050_SCENES");
			if (only != null && !only.isBlank()) scenes = only.split(",");
			for (String sc : scenes) {
				try {
					sp.getServer().runOnServer(this::reset);
					ctx.waitTicks(5);
					scene(ctx, sc);
				} catch (RuntimeException e) {
					log("cena " + sc + " falhou: " + e);
				}
			}
		} finally {
			Climate.forced = Double.NaN;
			Climate.forcedRain = null;
			Festa.forceActive = null;
			Market.forceActive = null;
		}
		System.out.println("[Vilas050NoJogo] RESUMO: " + notes);
	}

	/** {@code /place structure minecraft:village_plains} até sair uma vila com Prefeitura (Mesa), Taverna e Praça. */
	private boolean findVillage(ClientGameTestContext ctx) {
		for (int i = 0; i < 30; i++) {
			int vx = 3000 + i * 500, vz = 3000;
			sp.getServer().runCommand("forceload add " + (vx - 112) + " " + (vz - 112) + " " + (vx + 112) + " " + (vz + 112));
			look(ctx, new Vec3(vx, Y + 70, vz + 60), new Vec3(vx, Y, vz), 30);
			sp.getServer().runCommand("place structure minecraft:village_plains " + vx + " " + Y + " " + vz);
			ctx.waitTicks(30);
			final int fx = vx, fz = vz;
			BlockPos d = sp.getServer().computeOnServer(s -> {
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(fx - 90, Y - 3, fz - 90), new BlockPos(fx + 90, Y + 14, fz + 90)))
					if (s.overworld().getBlockState(p).is(VilasBlocks.MAYOR_DESK)) return p.immutable();
				return null;
			});
			if (d == null) {
				log("vila " + i + ": sem Prefeitura");
				continue;
			}
			look(ctx, new Vec3(d.getX(), d.getY() + 30, d.getZ() + 20), Vec3.atCenterOf(d), 140); // a Mesa regista a vila e lê as áreas
			String built = sp.getServer().computeOnServer(s -> {
				Village v = VillageManager.byDesk(s.overworld(), d);
				return v == null ? "?" : String.join(",", v.civic.built) + " caixas " + v.civic.boxes.size();
			});
			log("vila " + i + " em " + vx + "," + vz + ": Mesa " + d.toShortString() + " edifícios " + built);
			desk = d;
			// o que falta (Taverna, Praça) é encomendado pelo Prefeito e feito «de noite» de uma vez (a obra da 0.3.0)
			String made = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				v.economy.treasury = 1_000_000;
				v.civic.meta.real = true;
				StringBuilder o = new StringBuilder();
				for (String k : new String[] {"tavern", "plaza"})
					if (!v.civic.built.contains(k)) {
						var pr = com.tcordeiro.vilas.build.CivicWork.commission(l, v, k);
						o.append(k).append(pr == null ? "=sem sítio " : "=encomendada ");
						for (int n = 0; n < 6 && !v.extra.projects.isEmpty(); n++) com.tcordeiro.vilas.build.Builders.overnight(l, v);
					}
				Life.rescan(v);
				return o + "agora " + String.join(",", v.civic.built) + "; sítios " + places(s).spots.keySet();
			});
			log("vila " + i + ": " + made);
			if (made.contains("tavern") && made.contains("plaza") && !made.contains("sem sítio")) return true;
		}
		return false;
	}

	/** 40 aldeões na vila (os da vila e mais), 2 golens, tesouraria e nível Vila. */
	private void populate(ClientGameTestContext ctx) {
		String r = sp.getServer().computeOnServer(s -> {
			ServerLevel l = s.overworld();
			Village v = village(s);
			v.economy.treasury = 200_000;
			v.level = 1;
			v.civic.meta.real = true;
			Life.relist(v);
			Life.rescan(v);
			int have = Life.villagers(l, v).size();
			var profs = List.of(VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC, VillagerProfession.ARMORER,
					VillagerProfession.TOOLSMITH, VillagerProfession.MASON, VillagerProfession.FLETCHER, VillagerProfession.BUTCHER, VillagerProfession.SHEPHERD,
					VillagerProfession.FISHERMAN);
			var rnd = l.getRandom();
			int k = 0;
			while (have + k < 40) {
				BlockPos at = open(l, desk.offset(rnd.nextInt(41) - 20, 0, rnd.nextInt(41) - 20), 0, 6);
				spawn(l, at, k % 8 == 7 ? null : profs.get(k % profs.size()), k % 9 == 8);
				k++;
			}
			for (int g = 0; g < 2; g++) {
				IronGolem golem = EntityTypes.IRON_GOLEM.create(l, EntitySpawnReason.MOB_SUMMONED);
				BlockPos at = open(l, desk.offset(6 + g * 4, 0, 6), 0, 8);
				golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				l.addFreshEntity(golem);
			}
			Life.relist(v);
			Places p = places(s);
			StringBuilder sb = new StringBuilder("aldeões " + have + " + " + k + "; sítios:");
			for (var e : p.spots.entrySet())
				sb.append(" ").append(e.getKey()).append("(assentos ").append(e.getValue().seats.size()).append(", camas ").append(e.getValue().beds.size())
						.append(", coberto ").append(e.getValue().indoor.size()).append(")");
			return sb.toString();
		});
		log(r);
		ctx.waitTicks(100);
	}

	/** O tick com 40 aldeões: 400 ticks com a vida ligada e 400 sem (média do servidor, ms por tick) e o custo da vida por vila. */
	private void perf(ClientGameTestContext ctx) {
		Vec3 c = spot(sp.getServer().computeOnServer(s -> s), "plaza");
		look(ctx, c.add(10, 18, 10), c, 20);
		for (boolean on : new boolean[] {false, true, false, true}) {
			sp.getServer().runOnServer(s -> {
				VilasConfig.get().villager_life = on;
				Market.forceActive = on ? Boolean.TRUE : null; // 0.5.0: com o mercado montado (bancas, vendedores) e as crianças a brincar
				if (!on) reset(s);
				Life.nanos = 0;
				Life.calls = 0;
			});
			sp.getServer().runCommand("time set " + (on ? 10500 : 10500));
			ctx.waitTicks(400);
			String m = sp.getServer().computeOnServer(s -> String.format("vida %s: %d aldeões, tick médio do servidor %.2f ms, vida da vila %.3f ms por segundo (%d chamadas)",
					on ? "LIGADA" : "desligada", Life.villagers(s.overworld(), village(s)).size(), s.getAverageTickTimeNanos() / 1e6,
					Life.calls == 0 ? 0 : Life.nanos / 1e6 / Life.calls, Life.calls));
			log("DESEMPENHO " + m);
		}
		sp.getServer().runOnServer(s -> VilasConfig.get().villager_life = true);
	}

	// ---------------------------------------------------------------- 0.5.0: as cenas

	/** Fotografa o ecrã que {@code open} abre, em 1280×720 e 854×480. */
	private void screen(ClientGameTestContext ctx, String name, Consumer<MinecraftServer> open) {
		for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
			ctx.getInput().resizeWindow(size[0], size[1]);
			ctx.waitTicks(8);
			sp.getServer().runOnServer(open::accept);
			ctx.waitTicks(15);
			ctx.getInput().setCursorPos(2, 2);
			ctx.waitTicks(3);
			shot(ctx, name + "_" + size[0] + "x" + size[1]);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(8);
	}

	/** O quadro (com a lista rolada até ao fim, onde estão as novidades) nos dois tamanhos. */
	private void boardBoth(ClientGameTestContext ctx, String name) {
		for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
			ctx.getInput().resizeWindow(size[0], size[1]);
			ctx.waitTicks(8);
			// as linhas novas (mercado, eleições, missão da festa) estão no fim do quadro: rola até lá
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			sp.getServer().runOnServer(s -> TownDialog.open(player(s), s.overworld(), village(s)));
			ctx.waitTicks(15);
			ctx.getInput().setCursorPos(size[0] / 2, size[1] / 2);
			ctx.waitTicks(2);
			ctx.getInput().scroll(-26);
			ctx.waitTicks(5);
			ctx.getInput().setCursorPos(2, 2);
			ctx.waitTicks(3);
			shot(ctx, name + "_" + size[0] + "x" + size[1]);
			ctx.getInput().setCursorPos(size[0] / 2, size[1] / 2);
			ctx.getInput().scroll(-60);
			ctx.waitTicks(5);
			ctx.getInput().setCursorPos(2, 2);
			ctx.waitTicks(3);
			shot(ctx, name + "_fim_" + size[0] + "x" + size[1]);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(8);
	}

	/** Liga estes pacotes de recursos (os que existirem na pasta do jogo de teste) e recarrega; devolve os ligados. */
	private String packs(ClientGameTestContext ctx, String... ids) {
		String out = ctx.computeOnClient(mc -> {
			var repo = mc.getResourcePackRepository();
			repo.reload();
			java.util.List<String> sel = new java.util.ArrayList<>();
			sel.add("vanilla");
			for (String id : repo.getSelectedIds()) if (!id.startsWith("file/") && !sel.contains(id)) sel.add(id);
			for (String id : ids) if (repo.getAvailableIds().contains(id)) sel.add(id);
			repo.setSelected(sel);
			return String.join(",", repo.getSelectedIds());
		});
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(100);
		return out;
	}

	static Vec3 pos(String s) {
		String[] p = s.split(";");
		return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
	}

	static String str(Vec3 v) {
		return v.x + ";" + v.y + ";" + v.z;
	}

	/** Um terreno de 16×16 de um «vizinho» (outro jogador, desligado) num sítio livre perto da Praça. */
	static Lot neighborLot(ServerLevel l, Village v, BlockPos near, String name) {
		for (int d = 18; d <= 48; d += 6)
			for (int[] o : new int[][] {{d, 0}, {-d, 0}, {0, d}, {0, -d}, {d, d}, {-d, -d}}) {
				BlockPos c = near.offset(o[0], 0, o[1]);
				var area = new com.tcordeiro.vilas.logic.LotRules.Area(c.getX() - 7, c.getZ() - 7, c.getX() + 8, c.getZ() + 8);
				boolean free = VillageData.get(l).lots.stream().noneMatch(x -> x.area.overlaps(area)) && !v.civic.overlaps(area) && v.covers(c.getX(), c.getZ());
				if (!free) continue;
				Lot lot = new Lot(UUID.randomUUID(), v.id, area, UUID.nameUUIDFromBytes(name.getBytes()), name, new java.util.HashSet<>());
				VillageData.get(l).lots.add(lot);
				VillageData.get(l).changed();
				return lot;
			}
		return null;
	}

	private void scene(ClientGameTestContext ctx, String sc) {
		MinecraftServer srv = sp.getServer().computeOnServer(s -> s);
		switch (sc) {
			case "market" -> {
				sp.getServer().runCommand("time set 1500");
				Market.forceActive = true;
				ctx.waitTicks(60); // as bancas montam-se e os vendedores vão para lá
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(10, 9, 10), c, 10);
				film(ctx, "01_mercado_bancas_e_vendedores", 240, null);
				shot(ctx, "01_mercado_na_praca");
				String stalls = sp.getServer().computeOnServer(s -> "bancas " + Market.stalls(village(s)) + " pregões " + Market.cries + " " + tasks(s));
				log("mercado: " + stalls);
				// de perto: um vendedor na banca
				String vend = sp.getServer().computeOnServer(s -> {
					for (Villager vi : Life.villagers(s.overworld(), village(s))) {
						Task t = Tasks.get(vi);
						if (t != null && t.kind == Task.Kind.MARKET && t.arrived) return str(vi.position());
					}
					return null;
				});
				if (vend != null) {
					camAround(ctx, pos(vend).add(0, 1.2, 0), 5, 1.5, 10);
					film(ctx, "02_mercado_vendedor_a_apregoar", 200, null);
					shot(ctx, "02_vendedor_na_banca");
				}
				// troca na banca: o reembolso (chat visível)
				chat(ctx, true);
				hud(ctx, true);
				String r = sp.getServer().computeOnServer(s -> {
					var offer = new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 6),
							new ItemStack(Items.BREAD, 6), 99, 1, 0.05f);
					long got = Market.onTrade(player(s), village(s), BlockPos.containing(spot(s, "plaza")), offer);
					return "reembolso " + got;
				});
				ctx.waitTicks(10);
				shot(ctx, "03_mercado_reembolso_no_chat");
				log("mercado: " + r);
				chat(ctx, false);
				hud(ctx, false);
				boardBoth(ctx, "04_quadro_com_o_mercado");
				Market.forceActive = false;
				ctx.waitTicks(40);
				log("mercado fechado: bancas " + sp.getServer().computeOnServer(s -> Market.stalls(village(s))));
				Market.forceActive = null;
			}
			case "mail" -> {
				if (!Mods.loaded(Mods.MOVEIS)) {
					log("correio: sem o Móveis não há Carteiro (a cena não se filma)");
					return;
				}
				sp.getServer().runCommand("time set 2500");
				String setup = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos plaza = BlockPos.containing(spot(s, "plaza"));
					Lot lot = neighborLot(l, v, plaza, "Ana");
					if (lot == null) return null;
					int cx = (lot.area.minX() + lot.area.maxX()) / 2, cz = lot.area.minZ() + 1;
					BlockPos box = new BlockPos(cx, Places.ground(l, cx, cz, plaza.getY()), cz);
					Block mb = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(Mods.MOVEIS, "mailbox"));
					l.setBlock(box, mb.defaultBlockState(), 3);
					BlockPos at = open(l, plaza, 3, 12);
					Villager pm = spawn(l, at, VilasProfessions.POSTMAN, false);
					Life.relist(v);
					return pm.getUUID() + ";" + box.getX() + ";" + box.getY() + ";" + box.getZ() + ";" + lot.owner;
				});
				if (setup == null) {
					log("correio: sem sítio para o terreno da vizinha");
					return;
				}
				String[] p = setup.split(";");
				UUID pm = UUID.fromString(p[0]), to = UUID.fromString(p[4]);
				BlockPos box = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
				Vec3 pmPos = sp.getServer().computeOnServer(s -> s.overworld().getEntity(pm).position());
				look(ctx, pmPos.add(0, 1.6, 3.2), pmPos.add(0, 1.4, 0), 5);
				sp.getServer().runOnServer(s -> ((Villager) s.overworld().getEntity(pm)).setNoAi(true));
				screen(ctx, "05_menu_do_carteiro", s -> Resident.menu(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(pm)));
				screen(ctx, "06_carteiro_para_quem", s -> Mail.chooseRecipient(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(pm)));
				// a carta: 9 espaços; põe pão e flores e fecha
				for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
					ctx.getInput().resizeWindow(size[0], size[1]);
					ctx.waitTicks(8);
					sp.getServer().runOnServer(s -> {
						ServerPlayer pl = player(s);
						pl.getInventory().add(new ItemStack(Items.BREAD, 5));
						pl.getInventory().add(new ItemStack(Items.POPPY, 3));
						Mail.openLetter(pl, s.overworld(), village(s), (Villager) s.overworld().getEntity(pm), to.toString());
						var menu = pl.containerMenu;
						menu.getSlot(0).set(new ItemStack(Items.BREAD, 5));
						menu.getSlot(1).set(new ItemStack(Items.POPPY, 3));
						menu.broadcastChanges();
					});
					ctx.waitTicks(15);
					ctx.getInput().setCursorPos(2, 2);
					ctx.waitTicks(3);
					shot(ctx, "07_carta_com_itens_" + size[0] + "x" + size[1]);
					if (size[0] == 854) {
						sp.getServer().runOnServer(s -> player(s).closeContainer()); // fecha: a carta fica com o Carteiro
					} else sp.getServer().runOnServer(s -> {
						var menu = player(s).containerMenu;
						menu.getSlot(0).set(ItemStack.EMPTY);
						menu.getSlot(1).set(ItemStack.EMPTY);
						player(s).closeContainer();
					});
					ctx.runOnClient(mc -> mc.gui.setScreen(null));
					ctx.waitTicks(5);
				}
				ctx.getInput().resizeWindow(1280, 720);
				log("correio: cartas por entregar " + sp.getServer().computeOnServer(s -> village(s).civic.life.town.pending()));
				// a entrega: o Carteiro vai a pé com a bolsa até à caixa da vizinha
				sp.getServer().runOnServer(s -> {
					Villager v = (Villager) s.overworld().getEntity(pm);
					v.setNoAi(false);
					Mail.tryDeliver(s.overworld(), village(s), places(s), v, ((LifeVillager) v).tvilas$memo(), s.overworld().getGameTime(), 3000);
				});
				Vec3 mid = pmPos.add(Vec3.atCenterOf(box)).scale(0.5);
				look(ctx, mid.add(8, 12, 8), mid, 5);
				film(ctx, "08_carteiro_leva_a_carta_a_pe", 360, i -> {
					if (i % 60 == 59) {
						Vec3 now = sp.getServer().computeOnServer(s -> s.overworld().getEntity(pm).position());
						look(ctx, now.add(6, 6, 6), now.add(0, 1, 0), 0);
					}
				});
				Vec3 bp = Vec3.atCenterOf(box);
				camAround(ctx, bp, 4, 1.5, 10);
				shot(ctx, "09_carta_na_caixa_bandeirinha");
				String res = sp.getServer().computeOnServer(s -> "entregues " + Mail.delivered + " por entregar " + village(s).civic.life.town.pending()
						+ " caixa " + (s.overworld().getBlockEntity(box) instanceof net.minecraft.world.Container c ? c.countItem(Items.BREAD) + " pães" : "?"));
				log("correio: " + res);
			}
			case "election" -> {
				sp.getServer().runCommand("time set 2000");
				String open = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					// o jogador tem terreno (vota) e há uma votação aberta hoje
					BlockPos plaza = BlockPos.containing(spot(s, "plaza"));
					var area = new com.tcordeiro.vilas.logic.LotRules.Area(plaza.getX() + 30, plaza.getZ() + 30, plaza.getX() + 45, plaza.getZ() + 45);
					VillageData.get(l).lots.add(new Lot(UUID.randomUUID(), v.id, area, player(s).getUUID(), player(s).getGameProfile().name(), new java.util.HashSet<>()));
					boolean ok = Election.open(l, v, VillageEconomy.day(l));
					return ok + " " + v.civic.life.town.election.candidates;
				});
				log("eleições: " + open);
				boardBoth(ctx, "10_quadro_dia_de_eleicoes");
				screen(ctx, "11_votar", s -> Election.openDialog(player(s), s.overworld(), village(s)));
				String voted = sp.getServer().computeOnServer(s -> Election.vote(player(s), village(s), 0).message().getString());
				log("voto: " + voted);
				// o vencedor: discurso e foguete
				String win = sp.getServer().computeOnServer(s -> {
					var e = village(s).civic.life.town.election;
					var c = e.candidates.get(0);
					var en = s.overworld().getEntity(c.villager());
					return en == null ? null : str(en.position());
				});
				if (win != null) {
					Vec3 w = pos(win);
					camAround(ctx, w.add(0, 1.4, 0), 6, 2, 10);
					chat(ctx, true);
					hud(ctx, true);
					film(ctx, "12_eleito_discurso_e_foguete", 160, i -> {
						if (i == 5) sp.getServer().runOnServer(s -> Election.count(s.overworld(), village(s)));
					});
					shot(ctx, "12_novo_prefeito");
					chat(ctx, false);
					hud(ctx, false);
				}
				log("eleições: política " + sp.getServer().computeOnServer(s -> village(s).civic.life.town.election.policy + " prefeito "
						+ village(s).civic.life.town.election.mayorName + " trocas " + Election.swaps));
				boardBoth(ctx, "13_quadro_prefeito_eleito");
			}
			case "fisher" -> {
				sp.getServer().runCommand("time set 2500");
				String setup = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos c = open(l, BlockPos.containing(spot(s, "plaza")).offset(18, 0, 0), 4, 24);
					// um lago pequeno (5×5, 2 de fundo) ao lado de um sítio aberto
					for (int x = 2; x <= 6; x++)
						for (int z = -2; z <= 2; z++)
							for (int y = -2; y <= -1; y++) l.setBlock(c.offset(x, y, z), Blocks.WATER.defaultBlockState(), 3);
					Villager f = spawn(l, c.offset(-3, 0, 0), VillagerProfession.FISHERMAN, false);
					Life.relist(village(s));
					var m = ((LifeVillager) f).tvilas$memo();
					boolean ok = Fishing.tryFish(l, village(s), places(s), f, m, l.getGameTime(), 3000);
					return f.getUUID() + ";" + c.getX() + ";" + c.getY() + ";" + c.getZ() + ";" + ok + ";" + m.fishSpot;
				});
				String[] p = setup.split(";");
				log("pescador: " + setup);
				Vec3 c = new Vec3(Integer.parseInt(p[1]) + 2, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
				look(ctx, c.add(-2, 4, 7), c.add(0, 0.5, 0), 5);
				film(ctx, "14_pescador_vai_a_agua_e_lanca_a_boia", 200, null);
				look(ctx, c.add(-1.5, 2.2, 4), c.add(0.5, 0.6, 0), 5);
				film(ctx, "15_pescador_boia_e_peixe", 420, null);
				shot(ctx, "15_pescador_com_a_cana_e_a_boia");
				log("pescador: lançamentos " + Fishing.casts + " peixes " + Fishing.catches + " trocas repostas " + Fishing.restocks);
			}
			case "kids" -> {
				sp.getServer().runCommand("time set 9300");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos plaza = BlockPos.containing(spot(s, "plaza"));
					for (int i = 0; i < 4; i++) spawn(l, open(l, plaza.offset(i * 2 - 3, 0, 3), 0, 6), null, true);
					Life.relist(village(s));
					return "crianças na vila " + Life.villagers(l, village(s)).stream().filter(Villager::isBaby).count();
				});
				log("crianças: " + r);
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(9, 8, 9), c, 10);
				film(ctx, "16_criancas_esconde_esconde", 400, null);
				shot(ctx, "16_criancas_a_brincar");
				log("crianças: jogos " + Play.games + " achados " + Play.founds + " " + sp.getServer().computeOnServer(s -> Play.game(village(s))));
				sp.getServer().runOnServer(s -> Play.stopForTest(s.overworld(), village(s)));
				sp.getServer().runOnServer(s -> {
					List<Villager> kids = Life.villagers(s.overworld(), village(s)).stream().filter(Villager::isBaby).limit(4).toList();
					Play.startForTest(s.overworld(), village(s), kids, false);
				});
				film(ctx, "17_criancas_apanhada", 300, null);
				log("crianças: apanhados " + Play.tags);
				sp.getServer().runOnServer(s -> Play.stopForTest(s.overworld(), village(s)));
			}
			case "festa_decor" -> {
				sp.getServer().runCommand("time set 3000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					v.civic.life.festa.day = -1;
					v.civic.life.festa.last = -1;
					boolean ok = Festa.schedule(l, v, VillageEconomy.day(l) + 1, "level");
					return ok + " " + v.civic.life.town.decor;
				});
				log("festa: " + r);
				boardBoth(ctx, "18_quadro_missao_da_festa");
				String placed = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					var d = v.civic.life.town.decor;
					if (d == null) return "sem missão";
					VillageManager.accept(player(s), v, VillageManager.FESTA_SLOT);
					BlockPos plaza = BlockPos.containing(spot(s, "plaza"));
					Block b = VillageManager.block(d.target());
					int n = 0;
					for (int i = 0; i < 24 && n < d.count(); i++) {
						BlockPos q = open(l, plaza.offset((i % 6) * 2 - 5, 0, (i / 6) * 2 - 3), 0, 3);
						if (!l.getBlockState(q).isAir()) continue;
						l.setBlock(q, b.defaultBlockState(), 3);
						VillageManager.onPlaced(player(s), q, b.defaultBlockState());
						n++;
					}
					return "postos " + n + " → " + VillageManager.deliver(player(s), v, VillageManager.FESTA_SLOT).message().getString();
				});
				log("festa: " + placed);
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(7, 6, 7), c, 10);
				shot(ctx, "19_praca_decorada_para_a_festa");
				boardBoth(ctx, "20_quadro_missao_da_festa_feita");
			}
			case "helper" -> {
				sp.getServer().runCommand("time set 2500");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					ServerPlayer pl = player(s);
					BlockPos plaza = BlockPos.containing(spot(s, "plaza"));
					VillageData.get(l).lots.removeIf(x -> !x.owner.equals(pl.getUUID())); // só o meu terreno no caminho
					// o terreno do jogador com trigo maduro
					Lot mine = neighborLot(l, v, plaza.offset(0, 0, -10), "eu");
					if (mine != null) {
						VillageData.get(l).lots.remove(mine);
						Lot real = new Lot(mine.id, v.id, mine.area, pl.getUUID(), pl.getGameProfile().name(), new java.util.HashSet<>());
						VillageData.get(l).lots.add(real);
						int cx = (real.area.minX() + real.area.maxX()) / 2, cz = (real.area.minZ() + real.area.maxZ()) / 2;
						int gy = Places.ground(l, cx, cz, plaza.getY());
						for (int x = -2; x <= 2; x++)
							for (int z = -2; z <= 2; z++) {
								BlockPos q = new BlockPos(cx + x, gy, cz + z);
								l.setBlock(q.below(), Blocks.FARMLAND.defaultBlockState(), 3);
								l.setBlock(q, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
							}
					}
					BlockPos at = open(l, plaza, 2, 10);
					Villager vi = spawn(l, at, VillagerProfession.FARMER, false);
					Life.relist(v);
					if (TVilas.CUBCOIN) com.tcordeiro.vilas.compat.CubCoinPay.give(pl, 5000);
					else pl.getInventory().add(new ItemStack(Items.EMERALD, 5));
					pl.teleportTo(l, at.getX() + 0.5, at.getY(), at.getZ() + 3.5, java.util.Set.of(), 180, 10, false);
					return vi.getUUID() + ";" + (mine == null ? "" : (mine.area.minX() + mine.area.maxX()) / 2 + ";" + (mine.area.minZ() + mine.area.maxZ()) / 2);
				});
				String[] p = r.split(";");
				UUID id = UUID.fromString(p[0]);
				Vec3 vp = sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).position());
				look(ctx, vp.add(0, 1.6, 3.2), vp.add(0, 1.4, 0), 5);
				sp.getServer().runOnServer(s -> ((Villager) s.overworld().getEntity(id)).setNoAi(true));
				screen(ctx, "21_menu_contratar_ajudante", s -> Resident.menu(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(id)));
				chat(ctx, true);
				hud(ctx, true);
				sp.getServer().runOnServer(s -> {
					var res = Helper.hire(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(id));
					player(s).sendSystemMessage(res.message());
					((Villager) s.overworld().getEntity(id)).setNoAi(false);
				});
				ctx.waitTicks(10);
				shot(ctx, "22_ajudante_contratado_chat");
				chat(ctx, false);
				hud(ctx, false);
				screen(ctx, "23_menu_do_meu_ajudante", s -> Resident.menu(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(id)));
				// segue o jogador até ao terreno dele e colhe o trigo
				if (p.length >= 3) {
					int tx = Integer.parseInt(p[1]), tz = Integer.parseInt(p[2]);
					Vec3 from = sp.getServer().computeOnServer(s -> player(s).position());
					// a câmara É o jogador (o patrão): ele anda até à beira do trigo e fica lá a olhar (antes a câmara saltava para longe
					// do ajudante — o look() teleporta o jogador — e o ajudante seguia a câmara para o meio do campo)
					int gy = sp.getServer().computeOnServer(s -> Places.ground(s.overworld(), tx, tz + 4, (int) from.y));
					Vec3 stand = new Vec3(tx + 0.5, gy, tz + 4.5), field = new Vec3(tx + 0.5, gy, tz + 0.5);
					film(ctx, "24_ajudante_segue_e_colhe_no_meu_terreno", 640, i -> {
						if (i < 200 && i % 5 == 0) {
							double k = Math.min(1, (i + 5) / 200.0);
							double x = from.x + (stand.x - from.x) * k, z = from.z + (stand.z - from.z) * k;
							int y = sp.getServer().computeOnServer(s -> Places.ground(s.overworld(), (int) Math.floor(x), (int) Math.floor(z), (int) from.y));
							Vec3 hv = sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).position());
							look(ctx, new Vec3(x, y + 1.62, z), hv.add(0, 1.2, 0), 0); // a andar, a olhar para trás para o ajudante
						} else if (i >= 200 && i % 10 == 0) {
							look(ctx, stand.add(0, 1.62, 0), field.add(0, 0.3, 0), 0); // parado à beira do trigo
						}
						if (i % 100 == 0) log("ajudante (diag) f" + i + ": " + sp.getServer().computeOnServer(s -> {
							var e = (Villager) s.overworld().getEntity(id);
							Task t = Tasks.get(e);
							return "jogador " + player(s).blockPosition().toShortString() + " ajudante " + e.blockPosition().toShortString() + " tarefa "
									+ (t == null ? "-" : t.kind + "/" + t.role + "/" + (t.target == null ? "-" : t.target.toShortString())) + " colheitas " + Helper.harvests;
						}));
					});
					log("ajudante: colheitas " + Helper.harvests + " " + sp.getServer().computeOnServer(s -> {
						var h = village(s).civic.life.town.helperOf(player(s).getUUID());
						return h == null ? "sem contrato" : "bolsa " + h.bag;
					}));
				}
				Vec3 hv = sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).position());
				look(ctx, hv.add(0, 1.6, 3), hv.add(0, 1.3, 0), 5);
				screen(ctx, "25_bolsa_do_ajudante", s -> Helper.openBag(player(s), village(s)));
				String back = sp.getServer().computeOnServer(s -> {
					int before = player(s).getInventory().countItem(Items.WHEAT);
					Helper.dismiss(player(s), village(s));
					return "trigo devolvido " + (player(s).getInventory().countItem(Items.WHEAT) - before);
				});
				log("ajudante: " + back);
			}
			case "request" -> {
				sp.getServer().runCommand("time set 3000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos at = open(l, BlockPos.containing(spot(s, "plaza")), 3, 12);
					Villager vi = spawn(l, at, VillagerProfession.FARMER, false);
					vi.setNoAi(true);
					v.civic.batch.requests.put(vi.getUUID(), com.tcordeiro.vilas.village.Mission.of(++v.missionCounter,
							com.tcordeiro.vilas.logic.MissionRules.request("farmer", 3)));
					com.tcordeiro.vilas.village.VillagerRequests.tick(l, v);
					return str(vi.position()) + ";" + vi.hasCustomName();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
				look(ctx, c.add(0, 2.2, 4), c.add(0, 2.1, 0), 10);
				shot(ctx, "26_pedido_com_balao_de_exclamacao");
				film(ctx, "26_pedido_balao", 120, i -> {
					if (i % 40 == 0) sp.getServer().runOnServer(s -> com.tcordeiro.vilas.village.VillagerRequests.tick(s.overworld(), village(s)));
				});
				log("pedido: nome no aldeão? " + p[3]);
			}
			case "market_day" -> {
				// sem forçar: o mercado a cada 2 dias (config) e o tempo avança de verdade (tick sprint) até ao dia dele
				long[] need = sp.getServer().computeOnServer(s -> {
					Market.forceActive = null;
					VilasConfig.get().market_every_days = 2;
					ServerLevel l = s.overworld();
					long today = VillageEconomy.day(l), next = Market.next(village(s), today + (Market.today(l, village(s)) ? 0 : 0));
					return new long[] {today, next, l.getGameTime()};
				});
				log("dia de mercado: hoje " + need[0] + ", próximo " + need[1]);
				// o relógio do jogo avança dia a dia (como se o tempo passasse); a Mesa faz as contas de cada dia sozinha
				boolean ok = false;
				for (long d = need[0] + 1; d <= need[1] && !ok; d++) {
					long day = d;
					sp.getServer().runOnServer(s -> s.getWorldData().overworldData().setGameTime(day * 24000 + 600));
					sp.getServer().runCommand("time set 1500");
					for (int k = 0; k < 20 && !ok; k++) {
						ctx.waitTicks(20);
						ok = sp.getServer().computeOnServer(s -> Market.today(s.overworld(), village(s)));
					}
				}
				ctx.waitTicks(40);
				log("dia de mercado: " + sp.getServer().computeOnServer(s -> "dia " + VillageEconomy.day(s.overworld()) + " hoje há mercado? "
						+ Market.today(s.overworld(), village(s))));
				boardBoth(ctx, "27_quadro_hoje_ha_mercado");
			}
			case "gestures_pack" -> {
				// os pacotes do Talys pela ordem real (options.txt: de baixo para cima) e o nosso «tvilas_animacoes» por cima de todos
				String[] fa = {"file/FA+All_Extensions-v1.10.1.zip", "file/FreshAnimations_v1.11.2_26.1+.zip", "file/FA+Details-v2.4.zip",
						"file/FA+Emissive-v1.7.zip", "file/FA+Objects-v2.1.2.zip", "file/FA+Player-v1.1.zip"};
				String ours = "file/tvilas_animacoes.zip";
				String[] all = java.util.Arrays.copyOf(fa, fa.length + 1);
				all[fa.length] = ours;
				log("gestos: pacotes " + packs(ctx, all).replaceAll("^.*?file/", "file/"));
				sp.getServer().runCommand("time set 3000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos at = open(l, BlockPos.containing(spot(s, "plaza")).offset(30, 0, 0), 0, 24); // campo aberto, sem ninguém à volta
					Villager vi = spawn(l, at, VillagerProfession.FARMER, false);
					vi.setNoAi(true);
					return vi.getId() + ";" + str(vi.position());
				});
				int eid = Integer.parseInt(r.split(";", 2)[0]);
				Vec3 c = pos(r.split(";", 2)[1]);
				String[][] gestures = {{"1", "acenar"}, {"4", "sim"}, {"5", "nao"}, {"6", "apontar"}, {"7", "bater_palmas"}, {"8", "martelar"}, {"9", "comer"},
						{"2", "festejar"}, {"3", "encolher_os_ombros"}};
				String[] labels = {"", "acenar", "festejar", "encolher os ombros", "sim", "não", "apontar", "bater palmas", "martelar", "comer"};
				java.util.function.IntConsumer face = off -> sp.getServer().runOnServer(s -> {
					var e = (Villager) s.overworld().getEntity(eid);
					var pl = player(s);
					float yaw = (float) Math.toDegrees(Math.atan2(-(pl.getX() - e.getX()), pl.getZ() - e.getZ())) + off; // 0 = de frente, 40 = três quartos
					e.setYRot(yaw);
					e.setYBodyRot(yaw);
					e.setYHeadRot(yaw);
				});
				look(ctx, c.add(0, 1.6, -3.2), c.add(0, 1.15, 0), 20); // 3,2 blocos de frente, à altura dos olhos: o aldeão ocupa ~1/3 do ecrã
				log("gestos: câmara a " + sp.getServer().computeOnServer(s -> String.format("%.1f", player(s).distanceTo(s.overworld().getEntity(eid)))) + " blocos");
				for (String[] g : gestures) {
					int code = Integer.parseInt(g[0]);
					for (int angle : new int[] {0, 40}) {
						face.accept(angle);
						ctx.waitTicks(5);
						com.tcordeiro.vilas.client.EmfGestureHook.minArmsDuring = com.tcordeiro.vilas.client.EmfGestureHook.minArmsOutside = Float.MAX_VALUE;
						com.tcordeiro.vilas.client.EmfGestureHook.minArmsYDuring = com.tcordeiro.vilas.client.EmfGestureHook.minArmsYOutside = Float.MAX_VALUE;
						String dir = "gestos_" + g[1] + (angle == 0 ? "_frente" : "_tres_quartos");
						long[] start = {0};
						film(ctx, dir, 90, i -> {
							if (i == 15) start[0] = sp.getServer().computeOnServer(s -> {
								var e = s.overworld().getEntity(eid);
								com.tcordeiro.vilas.life.Balloons.gesture(e, code, 70);
								com.tcordeiro.vilas.life.Balloons.say(e, null, net.minecraft.network.chat.Component.literal(labels[code]), 70);
								return s.overworld().getGameTime();
							});
						});
						try { // o tick em que o gesto começou (para tirar os quadros «antes | durante»)
							java.nio.file.Files.writeString(Path.of("video", dir, "gesto.txt"), start[0] + " 70\n");
						} catch (java.io.IOException e) {
							throw new RuntimeException(e);
						}
						log("gestos (pacote) " + dir + ": braços rx durante " + com.tcordeiro.vilas.client.EmfGestureHook.minArmsDuring + " fora "
								+ com.tcordeiro.vilas.client.EmfGestureHook.minArmsOutside + " · y durante " + com.tcordeiro.vilas.client.EmfGestureHook.minArmsYDuring + " fora "
								+ com.tcordeiro.vilas.client.EmfGestureHook.minArmsYOutside);
					}
				}
				// só o FA (sem o nosso pacote): o gancho do mod faz o gesto por cima
				log("gestos: só o FA " + packs(ctx, fa).replaceAll("^.*?file/", "file/"));
				face.accept(40);
				int before = com.tcordeiro.vilas.client.GestureClient.applied;
				film(ctx, "gestos_so_com_o_fa_gancho_do_mod", 90, i -> {
					if (i == 15) sp.getServer().runOnServer(s -> com.tcordeiro.vilas.life.Balloons.gesture(s.overworld().getEntity(eid), 1, 70));
				});
				log("gestos (só FA): gancho aplicado " + (com.tcordeiro.vilas.client.GestureClient.applied - before) + " vezes · pacote ativo "
						+ ctx.computeOnClient(mc -> com.tcordeiro.vilas.client.EmfGestureHook.packActiveForTest()));
				// sem pacotes nenhuns: nada se parte
				log("gestos: sem pacotes " + packs(ctx).replaceAll("^.*?tvilas", "tvilas"));
				face.accept(40);
				film(ctx, "gestos_sem_pacotes_nada_parte", 90, i -> {
					if (i == 15) sp.getServer().runOnServer(s -> com.tcordeiro.vilas.life.Balloons.gesture(s.overworld().getEntity(eid), 1, 70));
				});
				log("gestos (sem pacotes): sem erros; gancho aplicado no total " + com.tcordeiro.vilas.client.GestureClient.applied);
			}
			case "gestures" -> {
				log("EMF neste jogo de teste: " + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")
						+ " · variáveis registadas: " + com.tcordeiro.vilas.client.GestureClient.registered + " · gestos mandados: " + com.tcordeiro.vilas.life.Balloons.gestures);
			}
			default -> {}
		}
	}


	private void board(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		sp.getServer().runOnServer(s -> TownDialog.open(player(s), s.overworld(), village(s)));
		ctx.waitTicks(15);
		ctx.getInput().setCursorPos(640, 360);
		ctx.waitTicks(2);
		ctx.getInput().scroll(-8);
		ctx.waitTicks(5);
		ctx.getInput().setCursorPos(2, 2);
		ctx.waitTicks(3);
		shot(ctx, name);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	/** Quantos aldeões em cada tarefa (para o registo). */
	String tasks(MinecraftServer srv) {
		return sp.getServer().computeOnServer(s -> {
			java.util.Map<String, Integer> m = new java.util.TreeMap<>();
			int seated = 0, sleeping = 0;
			for (Villager vi : Life.villagers(s.overworld(), village(s))) {
				Task t = Tasks.get(vi);
				m.merge(t == null ? "vanilla" : t.kind.name(), 1, Integer::sum);
				if (vi.isPassenger()) seated++;
				if (vi.isSleeping()) sleeping++;
			}
			return m + " sentados=" + seated + " a dormir=" + sleeping;
		});
	}

	static AABB box(BlockPos c, int r) {
		return new AABB(c).inflate(r);
	}

	static VillageData data(MinecraftServer s) {
		return VillageData.get(s.overworld());
	}
}
