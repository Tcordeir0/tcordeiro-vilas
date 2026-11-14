package com.tcordeiro.vilas.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.life.Care;
import com.tcordeiro.vilas.life.Climate;
import com.tcordeiro.vilas.life.Festa;
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
 * Vilas 0.4.0 no jogo (13/11): uma vila de planície posta com {@code /place structure minecraft:village_plains} (com Prefeitura,
 * Taverna e Praça), 40 aldeões, e cada mecânica nova filmada (vídeos à velocidade real: {@code ticks.txt} por pasta) e fotografada.
 * Mede o tick com 40 aldeões, com e sem a vida dos aldeões. A câmara fica sempre em ar livre, com vista até ao alvo. Sem som, pt_br.
 */
public class Vilas040NoJogoClientTest implements FabricClientGameTest {
	static final int Y = -60;
	private final List<String> notes = new ArrayList<>();
	private TestSingleplayerContext sp;
	private BlockPos desk;

	static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void log(String s) {
		System.out.println("[Vilas040NoJogo] " + s);
		notes.add(s);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of("vilas040_" + name).disableCounterPrefix().withDestinationDir(Path.of("fotos")));
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
		if (java.nio.file.Files.exists(java.nio.file.Path.of("vilas050.flag")) || java.nio.file.Files.exists(java.nio.file.Path.of("vilas060.flag"))) return; // 0.5.0/0.6.0: só o teste dessa versão
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
			String[] scenes = {"social", "rain", "cold", "night", "guards", "panic", "care", "chat", "festa", "apprentice", "menu", "gift", "work", "weapon",
					"meeting", "welcome"};
			String only = System.getenv("VILAS040_SCENES");
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
		}
		System.out.println("[Vilas040NoJogo] RESUMO: " + notes);
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

	private void scene(ClientGameTestContext ctx, String sc) {
		MinecraftServer srv = sp.getServer().computeOnServer(s -> s);
		switch (sc) {
			case "social" -> {
				sp.getServer().runCommand("time set 10200");
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(9, 9, 9), c, 10);
				film(ctx, "01_fim_de_tarde_vao_para_a_praca", 200, null);
				ctx.waitTicks(200);
				String seated = sp.getServer().computeOnServer(s -> {
					for (Villager vi : Life.villagers(s.overworld(), village(s)))
						if (vi.isPassenger() && s.overworld().canSeeSky(vi.blockPosition().above(2))) return vi.getX() + ";" + vi.getY() + ";" + vi.getZ();
					return null;
				});
				log("sentado ao ar livre: " + seated);
				if (seated != null) {
					String[] p = seated.split(";");
					Vec3 t = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]) + 0.8, Double.parseDouble(p[2]));
					camAround(ctx, t, 6, 2.5, 15);
					shot(ctx, "02_sentados_na_praca");
					film(ctx, "02_sentados_na_praca_a_conversar", 120, null);
				}
				look(ctx, c.add(9, 9, 9), c, 10);
				shot(ctx, "01b_praca_ao_fim_da_tarde");
				log("tarefas: " + tasks(srv));
			}
			case "rain" -> {
				sp.getServer().runCommand("time set 4000");
				ctx.waitTicks(40);
				Vec3 c = spot(srv, "tavern");
				look(ctx, c.add(12, 12, 12), c, 5);
				sp.getServer().runCommand("weather rain");
				film(ctx, "03_chuva_correm_para_a_taverna", 220, null);
				log("chuva: " + tasks(srv));
				sp.getServer().runCommand("weather clear");
			}
			case "cold" -> {
				sp.getServer().runCommand("time set 4000");
				BlockPos fire = sp.getServer().computeOnServer(s -> {
					BlockPos f = open(s.overworld(), BlockPos.containing(spot(s, "plaza")), 2, 8);
					s.overworld().setBlock(f, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
					Life.rescan(village(s));
					Climate.forced = 0;
					return f;
				});
				Vec3 c = Vec3.atBottomCenterOf(fire);
				look(ctx, c.add(7, 7, 7), c, 5);
				film(ctx, "04_frio_juntam_se_a_fogueira", 220, null);
				shot(ctx, "04_frio_a_volta_da_fogueira");
				log("frio: " + tasks(srv));
				sp.getServer().runOnServer(s -> {
					Climate.forced = Double.NaN;
					s.overworld().setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
				});
			}
			case "night" -> {
				sp.getServer().runCommand("time set 13600");
				ctx.waitTicks(60);
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(8, 8, 8), c, 5);
				film(ctx, "05_noite_lanternas_e_cama", 200, null);
				shot(ctx, "05_noite_lanternas");
				String sleeping = sp.getServer().computeOnServer(s -> {
					int n = 0;
					for (Villager vi : Life.villagers(s.overworld(), village(s)))
						if (vi.isSleeping() && Tasks.get(vi) != null && Tasks.get(vi).kind == Task.Kind.SLEEP) n++;
					return n + " a dormir na cama de casal";
				});
				log("noite: " + sleeping + "; " + tasks(srv));
			}
			case "guards" -> {
				sp.getServer().runCommand("time set 5000");
				String g = sp.getServer().computeOnServer(s -> {
					Village v = village(s);
					v.civic.life.guardDay = -1;
					int n = Care.dailyGuards(s.overworld(), v, places(s), VillageEconomy.day(s.overworld()));
					for (var e : v.civic.life.guards.entrySet())
						if (s.overworld().getEntity(e.getKey()) instanceof IronGolem golem) {
							BlockPos post = BlockPos.of(e.getValue());
							BlockPos away = open(s.overworld(), post.offset(10, 0, 4), 0, 6);
							golem.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
							return n + ";" + post.getX() + ";" + post.getY() + ";" + post.getZ();
						}
					return n + "";
				});
				log("guardas: " + g + " · tesouraria " + sp.getServer().computeOnServer(s -> village(s).economy.treasury));
				if (g.contains(";")) {
					String[] p = g.split(";");
					Vec3 post = new Vec3(Integer.parseInt(p[1]) + 5.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 2.5);
					look(ctx, post.add(6, 10, 10), post, 5);
					film(ctx, "06_guarda_volta_ao_posto", 220, null);
				}
				board(ctx, "07_quadro_guardas_festa_reputacao");
			}
			case "panic" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					Villager victim = null;
					Vec3 c = spot(s, "plaza");
					for (Villager vi : Life.villagers(l, v))
						if (!vi.isBaby() && vi.distanceToSqr(c) < 20 * 20 && l.canSeeSky(vi.blockPosition().above())) victim = vi;
					if (victim == null) return null;
					return victim.getUUID().toString();
				});
				if (r == null) {
					log("pânico: ninguém na praça");
					return;
				}
				UUID id = UUID.fromString(r);
				Vec3 at = sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).position());
				look(ctx, at.add(9, 9, 9), at, 5);
				film(ctx, "08_panico_fogem_para_o_golem", 160, i -> {
					if (i == 10) sp.getServer().runOnServer(s -> {
						ServerLevel l = s.overworld();
						var z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.MOB_SUMMONED);
						var victim = (Villager) l.getEntity(id);
						z.snapTo(victim.getX() + 1.5, victim.getY(), victim.getZ(), 0, 0);
						z.setNoAi(true);
						l.addFreshEntity(z);
						victim.hurtServer(l, l.damageSources().mobAttack(z), 1f);
						z.addTag("vilas040_zombie");
					});
				});
				log("pânico: " + tasks(srv) + " · pânicos " + Care.panics);
				sp.getServer().runCommand("kill @e[tag=vilas040_zombie]");
			}
			case "care" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 4, 12);
					Villager hurt = spawn(l, a, VillagerProfession.FARMER, false);
					hurt.setNoAi(true);
					hurt.setHealth(8);
					Villager cleric = spawn(l, open(l, a.offset(7, 0, 0), 0, 6), VillagerProfession.CLERIC, false);
					IronGolem g = EntityTypes.IRON_GOLEM.create(l, EntitySpawnReason.MOB_SUMMONED);
					BlockPos gp = open(l, a.offset(0, 0, 6), 0, 6);
					g.snapTo(gp.getX() + 0.5, gp.getY(), gp.getZ() + 0.5, 0, 0);
					g.setNoAi(true);
					g.setHealth(40);
					l.addFreshEntity(g);
					Villager smith = spawn(l, open(l, gp.offset(7, 0, 0), 0, 6), VillagerProfession.ARMORER, false);
					Life.relist(v);
					boolean h = Care.tryHeal(l, v, places(s), cleric, List.of(hurt));
					boolean rp = Care.tryRepair(l, v, places(s), smith);
					return a.getX() + ";" + a.getY() + ";" + a.getZ() + ";" + h + ";" + rp + ";" + g.getUUID();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Integer.parseInt(p[0]) + 3, Integer.parseInt(p[1]), Integer.parseInt(p[2]) + 3);
				look(ctx, c.add(5, 5, 5), c, 3);
				film(ctx, "09_clerigo_cura_e_ferreiro_repara_o_golem", 220, null);
				UUID gid = UUID.fromString(p[5]);
				log("cura " + p[3] + ", reparação " + p[4] + "; vida do golem agora " + sp.getServer().computeOnServer(s -> ((IronGolem) s.overworld().getEntity(gid)).getHealth())
						+ " (era 40); curas " + Care.heals + ", reparações " + Care.repairs);
			}
			case "chat" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 3, 14);
					Villager x = spawn(l, a, VillagerProfession.LIBRARIAN, false);
					Villager y = spawn(l, a.offset(2, 0, 0), VillagerProfession.FARMER, false);
					Life.relist(v);
					var f = Talk.fact(l, v, a, "mission", player(s).getGameProfile().name());
					f.knowers.remove(y.getUUID());
					Talk.start(l, v, places(s), x, y);
					return a.getX() + ";" + a.getY() + ";" + a.getZ();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Integer.parseInt(p[0]) + 1.5, Integer.parseInt(p[1]) + 1.6, Integer.parseInt(p[2]) + 0.5);
				look(ctx, c.add(0, 1.0, 3.6), c, 1);
				film(ctx, "10_conversa_sobre_a_missao_do_jogador", 130, null);
				log("conversa: " + Talk.lastLine.getString() + " (conversas " + Talk.talks + ")");
				ctx.waitTicks(40);
				// outra conversa natural, sem forçar
				look(ctx, c.add(0, 1.2, 5), c, 1);
				shot(ctx, "10b_balao_de_fala");
			}
			case "festa" -> {
				sp.getServer().runCommand("time set 6500");
				sp.getServer().runOnServer(s -> {
					Village v = village(s);
					v.civic.life.festa.day = VillageEconomy.day(s.overworld());
					v.civic.life.festa.reason = "level";
					Festa.forceActive = true;
				});
				Vec3 c = spot(srv, "plaza");
				look(ctx, c.add(10, 9, 10), c, 5);
				film(ctx, "11_festa_na_praca", 260, null);
				shot(ctx, "11_festa_na_praca");
				log("festa: " + tasks(srv));
				sp.getServer().runOnServer(s -> {
					Festa.forceActive = null;
					village(s).civic.life.festa.day = -1;
				});
			}
			case "apprentice" -> {
				sp.getServer().runCommand("time set 3000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 6, 16);
					Villager master = spawn(l, a, VillagerProfession.FLETCHER, false);
					Villager kid = spawn(l, open(l, a.offset(8, 0, 0), 0, 4), null, true);
					v.civic.life.apprentices.put(kid.getUUID(), "fletcher");
					Life.relist(v);
					Life.decideAt(l, v, kid, 3000, false);
					return a.getX() + ";" + a.getY() + ";" + a.getZ() + ";" + (Tasks.get(kid) == null ? "-" : Tasks.get(kid).kind) + ";" + master.getUUID();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Integer.parseInt(p[0]) + 4, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
				look(ctx, c.add(0, 6, 8), c, 1);
				film(ctx, "12_aprendiz_segue_o_mestre", 160, null);
				log("aprendiz: " + p[3]);
			}
			case "menu" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 3, 14);
					Villager vi = spawn(l, a, VillagerProfession.LIBRARIAN, false);
					vi.setNoAi(true);
					Life.relist(village(s));
					return vi.getUUID() + ";" + a.getX() + ";" + a.getY() + ";" + a.getZ();
				});
				String[] p = r.split(";");
				UUID id = UUID.fromString(p[0]);
				Vec3 c = new Vec3(Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]) + 1.5, Integer.parseInt(p[3]) + 0.5);
				look(ctx, c.add(0, 0.3, 3), c, 5);
				for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
					ctx.getInput().resizeWindow(size[0], size[1]);
					ctx.waitTicks(8);
					sp.getServer().runOnServer(s -> Resident.menu(player(s), s.overworld(), village(s), (Villager) s.overworld().getEntity(id)));
					ctx.waitTicks(15);
					ctx.getInput().setCursorPos(2, 2);
					ctx.waitTicks(3);
					shot(ctx, "13_menu_do_morador_" + size[0] + "x" + size[1]);
					ctx.runOnClient(mc -> mc.gui.setScreen(null));
					ctx.waitTicks(5);
				}
				ctx.getInput().resizeWindow(1280, 720);
				ctx.waitTicks(10);
				// pedir caminho: vira-se para a Taverna e um rasto de luz (só para quem perguntou)
				chat(ctx, true);
				hud(ctx, true);
				look(ctx, c.add(0, 0.6, 3.5), c, 5);
				film(ctx, "14_pedir_caminho_para_a_taverna", 110, i -> {
					if (i == 5) sp.getServer().runOnServer(s -> {
						var res = Resident.command(player(s), id, "caminho", "tavern");
						player(s).sendSystemMessage(res.message());
					});
				});
				shot(ctx, "14_pedir_caminho_resposta");
				chat(ctx, false);
				hud(ctx, false);
			}
			case "gift" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 3, 14);
					Villager vi = spawn(l, a, VillagerProfession.LIBRARIAN, false);
					vi.setNoAi(true);
					Life.relist(village(s));
					return vi.getUUID() + ";" + a.getX() + ";" + a.getY() + ";" + a.getZ();
				});
				String[] p = r.split(";");
				UUID id = UUID.fromString(p[0]);
				Vec3 c = new Vec3(Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]) + 1.5, Integer.parseInt(p[3]) + 0.5);
				chat(ctx, true);
				hud(ctx, true);
				look(ctx, c.add(0, 0.4, 3), c, 5);
				sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 3)));
				film(ctx, "15_presente_livro_ao_bibliotecario", 100, i -> {
					if (i == 10) sp.getServer().runOnServer(s -> {
						ServerLevel l = s.overworld();
						var pl = player(s);
						Resident.gift(pl, l, village(s), (Villager) l.getEntity(id), pl.getMainHandItem());
					});
					if (i == 60) sp.getServer().runOnServer(s -> {
						ServerLevel l = s.overworld();
						var pl = player(s);
						ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH);
						Resident.gift(pl, l, village(s), (Villager) l.getEntity(id), flesh);
					});
				});
				shot(ctx, "15_presente_resposta");
				chat(ctx, false);
				hud(ctx, false);
				sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			}
			case "work" -> {
				sp.getServer().runCommand("time set 3000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 4, 16);
					l.setBlock(a.east(), Blocks.SMITHING_TABLE.defaultBlockState(), 3);
					Villager vi = spawn(l, a, VillagerProfession.TOOLSMITH, false);
					vi.setNoAi(true);
					vi.setYRot(-90);
					vi.setYBodyRot(-90);
					vi.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(l.dimension(), a.east()));
					vi.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
					Life.relist(village(s));
					return a.getX() + ";" + a.getY() + ";" + a.getZ() + ";" + vi.getUUID();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Integer.parseInt(p[0]) + 1, Integer.parseInt(p[1]) + 1, Integer.parseInt(p[2]) + 0.5);
				look(ctx, c.add(1, 1.5, 3.5), c, 5);
				film(ctx, "16_trabalho_na_bancada_ferramenteiro", 200, null);
				shot(ctx, "16_trabalho_na_bancada");
				log("trabalho: " + Life.works + " vezes");
				sp.getServer().runOnServer(s -> s.overworld().setBlock(new BlockPos(Integer.parseInt(p[0]) + 1, Integer.parseInt(p[1]), Integer.parseInt(p[2])),
						Blocks.AIR.defaultBlockState(), 3));
			}
			case "weapon" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 3, 14);
					for (int k = 0; k < 3; k++) spawn(l, a.offset(k * 2 - 2, 0, 0), VillagerProfession.MASON, false);
					Life.relist(village(s));
					player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
					return a.getX() + ";" + a.getY() + ";" + a.getZ();
				});
				String[] p = r.split(";");
				Vec3 c = new Vec3(Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]) + 1.4, Integer.parseInt(p[2]) + 0.5);
				hud(ctx, true);
				look(ctx, c.add(0, 0.3, 2.6), c, 1);
				film(ctx, "17_espada_na_mao_recuam", 120, null);
				hud(ctx, false);
				log("reações: " + Life.reacts);
				sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			}
			case "meeting" -> {
				sp.getServer().runCommand("time set 5000");
				Vec3 c = spot(srv, "plaza");
				chat(ctx, true);
				hud(ctx, true);
				look(ctx, c.add(9, 9, 9), c, 5);
				film(ctx, "18_reuniao_na_praca", 400, i -> {
					if (i == 5) sp.getServer().runOnServer(s -> {
						Village v = village(s);
						v.reputation.put(player(s).getUUID(), 60);
						v.civic.life.meetingDay = -1;
						var res = Life.callMeeting(player(s), v);
						player(s).sendSystemMessage(res.message());
					});
				});
				shot(ctx, "18_reuniao_relatorio_do_prefeito");
				chat(ctx, false);
				hud(ctx, false);
			}
			case "welcome" -> {
				sp.getServer().runCommand("time set 5000");
				String r = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = village(s);
					BlockPos a = open(l, BlockPos.containing(spot(s, "plaza")), 6, 16);
					Villager friend = spawn(l, a.offset(8, 0, 0), VillagerProfession.BUTCHER, false);
					friend.getGossips().add(player(s).getUUID(), GossipType.MAJOR_POSITIVE, 60);
					Life.relist(v);
					v.civic.life.lastSeen.put(player(s).getUUID(), VillageEconomy.day(l) - 4);
					return a.getX() + ";" + a.getY() + ";" + a.getZ();
				});
				String[] p = r.split(";");
				Vec3 at = new Vec3(Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]) + 1.62, Integer.parseInt(p[2]) + 0.5);
				look(ctx, at, at.add(8, -0.3, 0), 1);
				film(ctx, "19_boas_vindas_de_volta", 200, null);
				log("boas-vindas: " + Life.welcomes);
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
