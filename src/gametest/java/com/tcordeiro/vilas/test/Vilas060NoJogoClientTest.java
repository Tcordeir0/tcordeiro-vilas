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
 * Vilas 0.6.0 no jogo (14/11): memória e «você me salvou» (carta e presente), crónica da vila, obra comunitária com doações e placa
 * (e, depois do merge, as eleições revistas e as crises). Vídeos à velocidade real de cada mecânica, com a câmara a 3–4 blocos e ao ar
 * livre, e fotos 1280×720 e 854×480 dos ecrãs novos. Base igual à 0.5.0. Sem som, pt_br. Só corre com {@code vilas060.flag}.
 */
public class Vilas060NoJogoClientTest implements FabricClientGameTest {
	static final int Y = -60;
	private final List<String> notes = new ArrayList<>();
	private TestSingleplayerContext sp;
	private BlockPos desk;

	static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void log(String s) {
		System.out.println("[Vilas060NoJogo] " + s);
		notes.add(s);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of("vilas060_" + name).disableCounterPrefix().withDestinationDir(Path.of("fotos")));
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
		if (!java.nio.file.Files.exists(Path.of("vilas060.flag"))) return; // só quando se pede (VILAS060=1): as outras versões têm os seus testes
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
			String[] scenes = {"rescue", "chronicle", "project"};
			String only = System.getenv("VILAS060_SCENES");
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
		System.out.println("[Vilas060NoJogo] RESUMO: " + notes);
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


	/** Filma {@code n} fotogramas, um a cada {@code step} ticks (o mkvideo.py dá a cada um a duração real). */
	private void filmEvery(ClientGameTestContext ctx, String dir, int n, int step, Consumer<Integer> each) {
		StringBuilder ticks = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (each != null) each.accept(i);
			long t = sp.getServer().computeOnServer(s -> s.overworld().getGameTime());
			ticks.append(String.format("f%04d %d%n", i, t));
			ctx.takeScreenshot(TestScreenshotOptions.of(String.format("f%04d", i)).disableCounterPrefix().withDestinationDir(Path.of("video", dir)));
			ctx.waitTicks(step);
		}
		try {
			Path p = Path.of("video", dir, "ticks.txt");
			java.nio.file.Files.createDirectories(p.getParent());
			java.nio.file.Files.writeString(p, ticks);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	private UUID savedVillager;

	private void scene(ClientGameTestContext ctx, String sc) {
		MinecraftServer srv = sp.getServer().computeOnServer(s -> s);
		switch (sc) {
			case "rescue" -> {
				sp.getServer().runCommand("time set 6000");
				String setup = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos at = open(l, BlockPos.containing(spot(s, "plaza")), 6, 20);
					Villager vi = spawn(l, at, VillagerProfession.FARMER, false);
					var z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.MOB_SUMMONED);
					z.snapTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 90, 0);
					z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // não arde ao sol
					l.addFreshEntity(z);
					z.setTarget(vi);
					Life.relist(v);
					savedVillager = vi.getUUID();
					return str(vi.position()) + "|" + z.getUUID();
				});
				String[] p = setup.split("\\|");
				Vec3 at = pos(p[0]);
				UUID zid = UUID.fromString(p[1]);
				look(ctx, at.add(0.5, 1.8, 3.6), at.add(1.5, 1.0, 0), 5);
				filmEvery(ctx, "01_voce_me_salvou", 110, 2, i -> {
					if (i == 30) sp.getServer().runOnServer(s -> {
						if (s.overworld().getEntity(zid) instanceof net.minecraft.world.entity.LivingEntity z)
							z.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player(s)), 100f);
					});
				});
				shot(ctx, "01_obrigado_voce_me_salvou");
				log("resgate: salvos " + com.tcordeiro.vilas.life.memory.Rescue.saves + " reputação paga " + com.tcordeiro.vilas.life.memory.Rescue.rewards
						+ " cartas pelo Carteiro " + com.tcordeiro.vilas.life.memory.Rescue.letters + " presentes na hora " + com.tcordeiro.vilas.life.memory.Rescue.gifts);
				chat(ctx, true);
				hud(ctx, true);
				ctx.waitTicks(5);
				shot(ctx, "02_carta_ou_presente_no_chat");
				chat(ctx, false);
				hud(ctx, false);
				sp.getServer().runOnServer(s -> ((Villager) s.overworld().getEntity(savedVillager)).setNoAi(true));
				screen(ctx, "03_menu_lembra_se_de_ti", s -> Resident.menu(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(savedVillager)));
				sp.getServer().runOnServer(s -> ((Villager) s.overworld().getEntity(savedVillager)).setNoAi(false));
			}
			case "chronicle" -> {
				sp.getServer().runOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					com.tcordeiro.vilas.life.Talk.fact(l, v, null, "festa");
					com.tcordeiro.vilas.life.Talk.fact(l, v, null, "level", "#tvilas.level.vila");
				});
				screen(ctx, "04_cronica_da_vila", s -> com.tcordeiro.vilas.life.memory.Chronicle.open(player(s), village(s)));
				String b = sp.getServer().computeOnServer(s -> {
					player(s).getInventory().add(new ItemStack(Items.BOOK));
					return com.tcordeiro.vilas.life.memory.Chronicle.book(player(s), village(s)).message().getString();
				});
				log("crónica: " + sp.getServer().computeOnServer(s -> village(s).civic.life.lore.chronicle.size()) + " entradas; livro: " + b);
			}
			case "project" -> {
				sp.getServer().runCommand("time set 3000");
				String made = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					v.civic.life.lore.project = null;
					var c = com.tcordeiro.vilas.life.projects.Projects.propose(l, v, com.tcordeiro.vilas.village.VillageEconomy.day(l));
					return c == null ? null : c.kind + ";" + c.site.getX() + ";" + c.site.getY() + ";" + c.site.getZ();
				});
				if (made == null) {
					log("obra: nenhum sítio para uma obra nesta vila");
					return;
				}
				String[] p = made.split(";");
				BlockPos site = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
				log("obra proposta: " + p[0] + " em " + site.toShortString());
				boardBoth(ctx, "05_quadro_obra_e_doar");
				String d = sp.getServer().computeOnServer(s -> {
					ServerPlayer pl = player(s);
					var c = village(s).civic.life.lore.project;
					for (var e : c.needs.entrySet()) pl.getInventory().add(new ItemStack(VillageManager.item(e.getKey()), e.getValue() + 4));
					return com.tcordeiro.vilas.life.projects.Projects.donate(pl, village(s)).message().getString();
				});
				log("doação: " + d);
				chat(ctx, true);
				hud(ctx, true);
				ctx.waitTicks(5);
				shot(ctx, "06_doacao_no_chat");
				chat(ctx, false);
				hud(ctx, false);
				Vec3 c = Vec3.atBottomCenterOf(site).add(2, 0, 2);
				camAround(ctx, c.add(0, 1, 0), 6.5, 3.5, 10);
				filmEvery(ctx, "02_construtor_faz_a_obra", 200, 4, null);
				String left = sp.getServer().computeOnServer(s -> {
					Village v = village(s);
					for (int n = 0; n < 4 && !v.extra.projects.isEmpty(); n++) com.tcordeiro.vilas.build.Builders.overnight(s.overworld(), v);
					return "obras na fila " + v.extra.projects.size() + " feitas " + v.civic.life.lore.projectsDone + " placa " + com.tcordeiro.vilas.life.projects.Projects.lastSign;
				});
				log("obra: " + left);
				ctx.waitTicks(20);
				camAround(ctx, c.add(0, 1, 0), 6.5, 3.5, 20);
				shot(ctx, "07_obra_pronta");
				BlockPos sign = sp.getServer().computeOnServer(s -> com.tcordeiro.vilas.life.projects.Projects.lastSign);
				if (sign != null) {
					look(ctx, Vec3.atCenterOf(sign).add(0, 0.6, -2.6), Vec3.atCenterOf(sign).add(0, 0.2, 0), 15);
					shot(ctx, "08_placa_com_os_doadores");
				}
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
