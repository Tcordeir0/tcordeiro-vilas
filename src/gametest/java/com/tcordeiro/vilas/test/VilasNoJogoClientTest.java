package com.tcordeiro.vilas.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.GolemWork;
import com.tcordeiro.vilas.build.LightingWork;
import com.tcordeiro.vilas.build.StreetLightKit;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.ui.TownDialog;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.TravelingMerchant;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.worldgen.FurnitureProcessor;
import com.tcordeiro.vilas.worldgen.VillagePoolInjector;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Vilas 0.2.0 no jogo (12/11): Prefeitura (Prefeito com a roupa, o quadro a 1280×720 e 854×480), Taverna e Praça com os móveis do
 * Móveis, o Construtor a pôr blocos 1 a 1 (golem, iluminação; com o chat), a muralha da Cidade a contornar um baú e uma casa de
 * jogador, os 4 vendedores dos mods e as trocas, o Mercador da Vila com o burro, a iluminação pública de noite, e uma vila vanilla
 * de planície gerada com as peças do mod. Sem som, pt_br. Com {@code -PnoMods} as fotos levam o prefixo «semmods_».
 */
public class VilasNoJogoClientTest implements FabricClientGameTest {
	static final int Y = -60;
	/** Onde ficam as peças (canto noroeste, chão em Y − 1). */
	static final BlockPos HALL = new BlockPos(-7, Y - 1, -7), TAVERN = new BlockPos(12, Y - 1, -9), PLAZA = new BlockPos(-9, Y - 1, 13);
	static final int RADIUS = 60; // muralha de meio lado 30
	private final List<String> notes = new ArrayList<>();
	private String prefix = "";

	static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	static Village village(MinecraftServer s) {
		ServerLevel l = s.overworld();
		for (Village v : VillageData.get(l).villages) if (v.radius == RADIUS) return v;
		return null;
	}

	static StructureTemplate template(ServerLevel l, net.minecraft.resources.Identifier id) {
		return l.getStructureTemplateManager().get(id).orElseThrow();
	}

	static void place(ServerLevel l, net.minecraft.resources.Identifier id, BlockPos at) {
		StructureTemplate t = template(l, id);
		// como a peça da pool numa geração real: os jigsaws viram o «final_state» (JigsawReplacementProcessor) e os móveis entram
		t.placeInWorld(l, at, at, new StructurePlaceSettings().addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE)
				.addProcessor(FurnitureProcessor.INSTANCE), l.getRandom(), 2);
	}

	static BlockPos find(ServerLevel l, BlockPos from, net.minecraft.core.Vec3i size, Block b) {
		for (BlockPos p : BlockPos.betweenClosed(from, from.offset(size).offset(-1, -1, -1))) if (l.getBlockState(p).is(b)) return p.immutable();
		return null;
	}

	private void cam(TestSingleplayerContext sp, ClientGameTestContext ctx, double x, double eye, double z, double tx, double ty, double tz, int wait) {
		// como o «/tp … facing» com o desconto dos olhos (1,62), pelo código (só o jogador)
		double dx = tx - x, dy = ty - eye, dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runOnServer(s -> player(s).teleportTo(s.overworld(), x, eye - 1.62, z, java.util.Set.of(), yaw, pitch, false));
		ctx.waitTicks(wait);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot("vilas020_" + prefix + name);
	}

	private TestSingleplayerContext spc;

	private int film(ClientGameTestContext ctx, String dir, int start, int n, Consumer<Integer> each) {
		int f = start;
		StringBuilder ticks = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (each != null) each.accept(i);
			// o servidor anda ao ritmo real enquanto se tiram as fotos: o tick de cada fotograma (para o vídeo sair à velocidade real)
			long t = spc.getServer().computeOnServer(s -> s.overworld().getGameTime());
			ticks.append(String.format("f%04d %d%n", f, t));
			ctx.takeScreenshot(TestScreenshotOptions.of(String.format("f%04d", f++)).disableCounterPrefix().withDestinationDir(Path.of("video", prefix + dir)));
			ctx.waitTicks(1);
		}
		try {
			Path p = Path.of("video", prefix + dir, "ticks.txt");
			java.nio.file.Files.createDirectories(p.getParent());
			java.nio.file.Files.writeString(p, ticks, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		return f;
	}

	private void chat(ClientGameTestContext ctx, boolean show) {
		ctx.runOnClient(mc -> {
			mc.options.chatVisibility().set(show ? net.minecraft.world.entity.player.ChatVisiblity.FULL : net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.broadcastOptions(); // senão o servidor continua a achar o chat escondido e não manda as mensagens
		});
	}

	private void hud(ClientGameTestContext ctx, boolean show) {
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == show) mc.gui.hud.toggle();
		});
	}

	private void log(String s) {
		System.out.println("[VilasNoJogoClientTest] " + s);
		notes.add(s);
	}

	/** Chão de erva, as ruas de terra batida, a Prefeitura, a Taverna e a Praça, a casa e o baú do jogador no perímetro da muralha. */
	static String build(MinecraftServer s) {
		ServerLevel l = s.overworld();
		StringBuilder out = new StringBuilder();
		// ruas: uma de oeste a este (z = 10) e uma de norte a sul (x = 10)
		for (int x = -45; x <= 45; x++) l.setBlock(new BlockPos(x, Y - 1, 10), Blocks.DIRT_PATH.defaultBlockState(), 3);
		for (int z = -45; z <= 45; z++) l.setBlock(new BlockPos(10, Y - 1, z), Blocks.DIRT_PATH.defaultBlockState(), 3);
		place(l, VillagePoolInjector.townHall("plains"), HALL);
		place(l, VillagePoolInjector.tavern("plains"), TAVERN);
		place(l, VillagePoolInjector.plaza("plains"), PLAZA);
		var hall = template(l, VillagePoolInjector.townHall("plains"));
		out.append("prefeitura ").append(hall.getSize()).append(" taverna ").append(template(l, VillagePoolInjector.tavern("plains")).getSize())
				.append(" praça ").append(template(l, VillagePoolInjector.plaza("plains")).getSize());
		BlockPos desk = find(l, HALL, hall.getSize(), VilasBlocks.MAYOR_DESK);
		out.append(" mesa=").append(desk == null ? "?" : desk.toShortString());
		if (desk != null) {
			Village v = VillageManager.found(l, desk, RADIUS); // antes do 1.º tick da Mesa (senão seria o raio da configuração, 400)
			out.append(" vila raio ").append(v.radius);
		}
		// a casa do jogador (tábuas, 4×4) encostada ao perímetro oeste (x = −30) e um baú com coisas no perímetro este (x = 30)
		// a Mesa fica em (−1, −5) (a Prefeitura de planície posta em HALL): perímetro oeste x = −31, este x = 29, norte z = −35, sul z = 25
		for (int x = -31; x <= -28; x++)
			for (int z = -2; z <= 1; z++) {
				l.setBlock(new BlockPos(x, Y - 1, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
				boolean edge = x == -31 || x == -28 || z == -2 || z == 1;
				for (int y = 0; y <= 2; y++) if (edge) l.setBlock(new BlockPos(x, Y + y, z), (y == 1 && (x == -30 || z == -1) ? Blocks.GLASS : Blocks.OAK_PLANKS).defaultBlockState(), 3);
				l.setBlock(new BlockPos(x, Y + 3, z), Blocks.OAK_SLAB.defaultBlockState(), 3);
			}
		l.setBlock(new BlockPos(29, Y, -18), Blocks.CHEST.defaultBlockState(), 3);
		if (l.getBlockEntity(new BlockPos(29, Y, -18)) instanceof ChestBlockEntity c) c.setItem(0, new ItemStack(Items.DIAMOND, 5));
		var pl = player(s);
		pl.getInventory().clearContent();
		pl.getAbilities().flying = true;
		pl.onUpdateAbilities();
		return out.toString();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		boolean mods = Mods.loaded(Mods.MOVEIS) || Mods.loaded(Mods.INDUSTRIAL);
		prefix = mods ? "" : "semmods_";
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		chat(ctx, false);
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(120);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			spc = sp;
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"time set 6000", "gamerule advance_time false", "gamerule spawn_mobs false", "gamerule advance_weather false",
					"weather clear", "gamerule random_tick_speed 0", "gamerule send_command_feedback false", "gamemode creative @a"})
				sp.getServer().runCommand(c);
			cam(sp, ctx, 0, Y + 30, 30, 0, Y, 0, 20);
			sp.getConnection().waitForChunksRender();
			log("montagem: " + sp.getServer().computeOnServer(VilasNoJogoClientTest::build) + " mods=" + mods);
			ctx.waitTicks(150); // a Mesa regista a vila, conta a população e chama o Prefeito (a cada 5 s)
			hud(ctx, false);
			BlockPos desk = sp.getServer().computeOnServer(s -> village(s) == null ? null : village(s).center);
			if (desk == null) throw new AssertionError("sem vila");

			// ---------------------------------------------------------------- 1) Prefeitura, Prefeito e o quadro
			cam(sp, ctx, 0.5, Y + 14, 22, 0.5, Y + 2, 0, 20);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "01a_prefeitura_taverna_praca_de_cima");
			cam(sp, ctx, 0.5, Y + 4, 13, 0.5, Y + 3, 0, 15);
			shot(ctx, "01b_prefeitura_de_frente");
			String mayorInfo = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				var list = l.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(v.center).inflate(30), VillageManager::isMayor);
				if (list.isEmpty()) {
					VillageEconomy.ensureMayor(l, v, l.getGameTime());
					list = l.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(v.center).inflate(30), VillageManager::isMayor);
				}
				if (list.isEmpty()) {
					var nearList = l.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(v.center).inflate(48));
					String near = nearList.size() + " " + nearList.stream().map(e -> e.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("?")
							+ "@" + e.blockPosition().toShortString()).toList();
					String why = "sem Prefeito (aldeões perto=" + near + " mesa=" + l.getBlockState(v.center).getBlock() + " última chamada=" + v.economy.lastMayorCall + ")";
					// para a foto da roupa: um aldeão com a profissão de Prefeito, posto à mão
					Villager m = EntityTypes.VILLAGER.create(l, EntitySpawnReason.COMMAND);
					m.setVillagerData(m.getVillagerData().withProfession(l.registryAccess(), VilasProfessions.MAYOR));
					BlockPos front = v.center.south(2);
					m.setNoAi(true);
					m.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0, 0);
					l.addFreshEntity(m);
					return why + " → Prefeito posto à mão para a foto";
				}
				Villager m = list.get(0);
				// para a foto: parado em frente à Mesa, virado para a câmara
				BlockPos front = v.center.south(2);
				m.setNoAi(true);
				m.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0, 0);
				m.setYHeadRot(0);
				m.setYBodyRot(0);
				return "Prefeito " + m.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("?") + " em " + m.blockPosition().toShortString();
			});
			log(mayorInfo);
			ctx.waitTicks(10);
			BlockPos mayorAt = desk.south(2);
			cam(sp, ctx, mayorAt.getX() + 0.5, Y + 2.2, mayorAt.getZ() + 3.2, mayorAt.getX() + 0.5, Y + 1.3, mayorAt.getZ() + 0.5, 15);
			shot(ctx, "02a_prefeito_roupa_de_frente");
			cam(sp, ctx, mayorAt.getX() + 1.4, Y + 1.8, mayorAt.getZ() + 1.6, mayorAt.getX() + 0.5, Y + 1.5, mayorAt.getZ() + 0.5, 10);
			shot(ctx, "02b_prefeito_cartola_faixa_medalha_perto");
			cam(sp, ctx, mayorAt.getX() + 0.5, Y + 2.0, mayorAt.getZ() - 2.0, mayorAt.getX() + 0.5, Y + 1.3, mayorAt.getZ() + 0.5, 10);
			shot(ctx, "02c_prefeito_costas_e_mesa");
			// o quadro da Prefeitura (diálogo do servidor)
			for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
				ctx.getInput().resizeWindow(size[0], size[1]);
				ctx.waitTicks(10);
				sp.getServer().runOnServer(s -> TownDialog.open(player(s), s.overworld(), village(s)));
				ctx.waitTicks(15);
				String scr = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "nenhum" : mc.gui.screen().getClass().getSimpleName());
				log("quadro " + size[0] + ": ecrã " + scr);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(3);
				shot(ctx, "03_quadro_prefeitura_" + size[0] + "x" + size[1]);
				ctx.getInput().scroll(-10);
				ctx.waitTicks(5);
				shot(ctx, "03_quadro_prefeitura_" + size[0] + "x" + size[1] + "_fim");
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				ctx.waitTicks(5);
			}
			ctx.getInput().resizeWindow(1280, 720);
			ctx.waitTicks(10);

			// ---------------------------------------------------------------- 2) Taverna e Praça (com os móveis do Móveis)
			String furn = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				int chairs = 0, tables = 0, lamps = 0, beds = 0, markers = 0;
				var size = template(l, VillagePoolInjector.tavern("plains")).getSize();
				for (BlockPos p : BlockPos.betweenClosed(TAVERN, TAVERN.offset(size).offset(-1, -1, -1))) {
					BlockState st = l.getBlockState(p);
					String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
					if (id.startsWith("tmoveis:") && id.endsWith("_chair")) chairs++;
					if (id.startsWith("tmoveis:") && id.endsWith("_table")) tables++;
					if (id.equals("tmoveis:chandelier")) lamps++;
					if (id.startsWith("tmoveis:") && id.endsWith("_double_bed")) beds++;
					if (st.is(FurnitureProcessor.LEFT_BED) || st.is(FurnitureProcessor.RIGHT_BED)) markers++;
				}
				return "taverna: cadeiras=" + chairs + " mesas=" + tables + " lustres=" + lamps + " cama de casal (partes)=" + beds + " marcadores de cama=" + markers
						+ " tamanho=" + size;
			});
			log(furn);
			cam(sp, ctx, 20, Y + 12, 14, 20, Y + 1, -2, 20);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "04a_taverna_por_fora");
			// por dentro: tira-se o telhado de vista entrando pelo meio (câmara dentro da sala, alto)
			var tsize = sp.getServer().computeOnServer(s -> template(s.overworld(), VillagePoolInjector.tavern("plains")).getSize());
			double tcx = TAVERN.getX() + tsize.getX() / 2.0, tcz = TAVERN.getZ() + tsize.getZ() / 2.0;
			cam(sp, ctx, tcx, Y + 2.6, tcz + 2.5, tcx, Y + 0.6, tcz - 2, 15);
			shot(ctx, "04b_taverna_dentro_1");
			cam(sp, ctx, tcx - 2.5, Y + 2.6, tcz - 1.5, tcx + 3, Y + 0.5, tcz + 2, 10);
			shot(ctx, "04c_taverna_dentro_2");
			cam(sp, ctx, tcx + 2.5, Y + 2.6, tcz - 2.5, tcx - 3, Y + 0.5, tcz + 2.5, 10);
			shot(ctx, "04d_taverna_dentro_3");
			// os andares de cima (se houver): uma vista de cima de cada camada (sem teto: corte a 5 blocos)
			cam(sp, ctx, tcx, Y + 4.6, tcz + 2.5, tcx, Y + 3.0, tcz - 2, 10);
			shot(ctx, "04e_taverna_andar_de_cima");
			cam(sp, ctx, PLAZA.getX() + 8, Y + 9, PLAZA.getZ() + 20, PLAZA.getX() + 8, Y, PLAZA.getZ() + 7, 20);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "05a_praca_fonte_poco");
			cam(sp, ctx, PLAZA.getX() + 8, Y + 3, PLAZA.getZ() + 12, PLAZA.getX() + 8, Y, PLAZA.getZ() + 7, 10);
			shot(ctx, "05b_praca_de_perto");

			// ---------------------------------------------------------------- 3) Construtor à vista: o golem (com o chat)
			hud(ctx, true);
			chat(ctx, true);
			BlockPos site = sp.getServer().computeOnServer(s -> {
				Project p = GolemWork.create(s.overworld(), village(s));
				return p == null ? null : p.site;
			});
			log("sítio do golem: " + site);
			if (site != null) {
				// a câmara do lado de fora (longe da Mesa), para a Prefeitura não tapar o golem
				double ox = site.getX() - desk.getX(), oz = site.getZ() - desk.getZ(), ol = Math.max(1, Math.sqrt(ox * ox + oz * oz));
				cam(sp, ctx, site.getX() + 0.5 + ox / ol * 8, Y + 3.5, site.getZ() + 0.5 + oz / ol * 8, site.getX() + 0.5, Y + 1.2, site.getZ() + 0.5, 20);
				sp.getConnection().waitForChunksRender();
				int f = film(ctx, "construtor_golem", 0, 5, null);
				sp.getServer().runOnServer(s -> {
					Village v = village(s);
					Project p = GolemWork.create(s.overworld(), v);
					if (p != null) Builders.start(s.overworld(), v, p);
				});
				// até 30 s à velocidade real (o Construtor chega, põe 5 blocos, 1 por segundo, e vai-se embora)
				int[] placed = {0};
				for (int k = 0; k < 600; k++) {
					f = film(ctx, "construtor_golem", f, 1, null);
					if (k % 20 == 0) {
						boolean done = sp.getServer().computeOnServer(s -> village(s).extra.projects.isEmpty());
						if (done && placed[0]++ > 2) break; // mais 2 s depois de acabar (o golem acorda)
					}
					if (k == 140) shot(ctx, "06a_construtor_a_meio_do_golem");
				}
				shot(ctx, "06b_golem_acabado_chat");
				String gs = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					int g = l.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, new net.minecraft.world.phys.AABB(site).inflate(5)).size();
					return "golens perto do sítio=" + g + " obras=" + village(s).extra.projects.size();
				});
				log("golem: " + gs);
			}

			// ---------------------------------------------------------------- 7) iluminação pública (o Construtor põe os postes)
			String light = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				StreetLightKit kit = StreetLightKit.forVillage(l, v);
				Project p = LightingWork.create(l, v);
				if (p == null) return "sem sítios para postes";
				v.extra.lighting = true;
				Builders.start(l, v, p);
				return "kit=" + (kit.industrial() ? "Industrial " + BuiltInRegistries.BLOCK.getKey(kit.pole().get(0).getBlock()) : "lanternas") + " blocos=" + p.total
						+ " 1.º=" + p.pending.get(0).pos().toShortString();
			});
			log("iluminação: " + light);
			BlockPos firstPole = sp.getServer().computeOnServer(s -> {
				Village v = village(s);
				return v.extra.projects.isEmpty() || v.extra.projects.get(0).pending.isEmpty() ? null : v.extra.projects.get(0).pending.get(0).pos();
			});
			if (firstPole != null) {
				cam(sp, ctx, firstPole.getX() + 0.5, Y + 4, firstPole.getZ() + 9, firstPole.getX() + 0.5, Y + 1.5, firstPole.getZ(), 15);
				sp.getConnection().waitForChunksRender();
				film(ctx, "construtor_postes", 0, 500, null);
				shot(ctx, "07a_construtor_postes_a_meio");
			}
			// o resto de uma vez (como depois da noite) e os fios
			int night = sp.getServer().computeOnServer(s -> {
				int n = 0;
				Village v = village(s);
				for (int i = 0; i < 3 && !v.extra.projects.isEmpty(); i++) n += Builders.overnight(s.overworld(), v);
				return n;
			});
			log("iluminação: postos de noite " + night + " blocos; postes=" + sp.getServer().computeOnServer(s -> village(s).extra.poles) + "; " + sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				StreetLightKit kit = StreetLightKit.forVillage(l, v);
				int wires = 0, blocked = 0, near = 0;
				for (long a : v.extra.lightAnchors) {
					if (kit.isPole(l.getBlockState(BlockPos.of(a))) && LightingWork.nearDoor(l, BlockPos.of(a), LightingWork.DOOR_CLEARANCE)) near++;
					for (long b : kit.partners(l, BlockPos.of(a))) {
						wires++;
						if (!kit.wireClear(l, BlockPos.of(a), BlockPos.of(b))) blocked++;
					}
				}
				return "pontas de fio=" + wires + " a passar por blocos=" + blocked + " postes junto a portas=" + near;
			}));
			hud(ctx, false);
			chat(ctx, false);
			ctx.waitTicks(200); // os painéis carregam a bateria de dia
			cam(sp, ctx, 10.5 - 6, Y + 9, 10.5 + 18, 10.5, Y + 1, 10.5 - 6, 20);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "07b_postes_dia");
			sp.getServer().runCommand("time set 18000");
			ctx.waitTicks(80);
			shot(ctx, "07c_postes_noite");
			String lit = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				int on = 0, all = 0;
				for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-50, Y, -50), new BlockPos(50, Y + 5, 50))) {
					BlockState st = l.getBlockState(p);
					String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
					if (!(id.endsWith("_lamp_post") || id.equals("tindustrial:iron_street_light") || st.is(Blocks.LANTERN))) continue;
					for (var prop : st.getProperties())
						if (prop.getName().equals("lit")) {
							all++;
							if ((Boolean) st.getValue(prop)) on++;
						}
					if (st.is(Blocks.LANTERN)) {
						all++;
						on++;
					}
				}
				return on + " de " + all + " (blocos com «lit»)";
			});
			log("de noite acesos: " + lit);
			if (firstPole != null) {
				cam(sp, ctx, firstPole.getX() + 3.5, Y + 3, firstPole.getZ() + 5, firstPole.getX() + 0.5, Y + 2.5, firstPole.getZ(), 10);
				shot(ctx, "07d_poste_de_perto_noite");
			}
			cam(sp, ctx, 6, Y + 3, 4, 0.5, Y + 1, -3, 10);
			shot(ctx, "07e_fonte_prefeitura_noite");
			sp.getServer().runCommand("time set 6000");
			ctx.waitTicks(20);

			// ---------------------------------------------------------------- 4) a muralha da Cidade
			hud(ctx, true);
			chat(ctx, true);
			String wall = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				v.level = VillageRules.CIDADE;
				v.economy.treasury = 5_000_000;
				boolean ok = VillageEconomy.commissionWall(l, v);
				return "muralha encomendada=" + ok + " colunas=" + (v.extra.projects.isEmpty() ? 0 : v.extra.projects.get(0).total);
			});
			log(wall);
			log("muralha, 1.ªs colunas: " + sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				StringBuilder b = new StringBuilder();
				var plan = com.tcordeiro.vilas.logic.WallRules.plan(v.center.getX(), v.center.getZ(), com.tcordeiro.vilas.logic.WallRules.half(v.radius));
				for (int i = 0; i < 3; i++) {
					var c = plan.get(i);
					BlockPos top = com.tcordeiro.vilas.build.Ground.surface(l, c.x(), c.z(), v.center.getY());
					b.append(c).append(" chão=").append(top.toShortString()).append(' ').append(l.getBlockState(top.below()).getBlock())
							.append(" natural=").append(com.tcordeiro.vilas.build.Ground.natural(l.getBlockState(top.below())))
							.append(" pode=").append(com.tcordeiro.vilas.build.Ground.mayBuild(l, top))
							.append(" terreno=").append(VillageManager.lotAt(l, c.x(), c.z()) != null).append(" carregado=").append(l.isLoaded(top)).append("; ");
				}
				var p = v.extra.projects.isEmpty() ? null : v.extra.projects.get(0);
				b.append(" obra=").append(p == null ? "nenhuma" : p.kind + " pendentes=" + p.pending.size() + " coluna=" + p.nextColumn + "/" + p.total);
				return b.toString();
			}));
			// o canto noroeste (a muralha começa no lado norte, de oeste para este)
			cam(sp, ctx, -25, Y + 5, -27, -31, Y + 1, -35, 20);
			sp.getConnection().waitForChunksRender();
			film(ctx, "muralha_aos_poucos", 0, 600, null);
			shot(ctx, "08a_muralha_primeiras_colunas_chat");
			log("muralha depois de 30 s: " + sp.getServer().computeOnServer(s -> {
				Village v = village(s);
				var p = v.extra.projects.isEmpty() ? null : v.extra.projects.get(0);
				return "estado=" + v.extra.wall + " obra=" + (p == null ? "nenhuma" : p.kind + " postos=" + p.placed + " saltados=" + p.skipped + " coluna=" + p.nextColumn + "/" + p.total)
						+ " tesouraria=" + v.economy.treasury + " construtor=" + v.extra.builder;
			}));
			int wallNight = sp.getServer().computeOnServer(s -> {
				int n = 0;
				Village v = village(s);
				for (int i = 0; i < 4 && !v.extra.projects.isEmpty(); i++) n += Builders.overnight(s.overworld(), v);
				return n;
			});
			log("muralha: postos de noite " + wallNight + " blocos; estado=" + sp.getServer().computeOnServer(s -> village(s).extra.wall));
			hud(ctx, false);
			chat(ctx, false);
			cam(sp, ctx, 0, Y + 45, 52, 0, Y, 0, 25);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "08b_muralha_inteira_de_cima");
			cam(sp, ctx, -38, Y + 6, 5, -31, Y + 1, -0.5, 15);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "08c_muralha_contorna_casa_do_jogador");
			cam(sp, ctx, -30.5, Y + 12, -0.5, -31.5, Y, -0.51, 10);
			shot(ctx, "08d_muralha_casa_de_cima");
			cam(sp, ctx, 25, Y + 4, -13, 29.5, Y + 0.5, -18, 15);
			sp.getConnection().waitForChunksRender();
			shot(ctx, "08e_muralha_contorna_bau");
			cam(sp, ctx, 29.5, Y + 10, -18, 29.5, Y, -18.01, 10);
			shot(ctx, "08f_muralha_bau_de_cima");
			String chest = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				boolean c = l.getBlockState(new BlockPos(29, Y, -18)).is(Blocks.CHEST);
				int d = l.getBlockEntity(new BlockPos(29, Y, -18)) instanceof ChestBlockEntity cb ? cb.getItem(0).getCount() : -1;
				boolean house = l.getBlockState(new BlockPos(-31, Y, -2)).is(Blocks.OAK_PLANKS);
				String east = l.getBlockState(new BlockPos(30, Y, -18)).getBlock().toString(), west = l.getBlockState(new BlockPos(-32, Y, -1)).getBlock().toString();
				return "baú=" + c + " diamantes=" + d + " casa=" + house + " fora do baú=" + east + " fora da casa=" + west;
			});
			log("muralha e jogador: " + chest);
			cam(sp, ctx, 10.5, Y + 3, 31, 10.5, Y + 1.5, 25, 15);
			shot(ctx, "08g_muralha_portao_na_rua");

			// ---------------------------------------------------------------- 5) os 4 vendedores dos mods e as trocas
			List<ResourceKey<VillagerProfession>> vendors = new ArrayList<>();
			for (var prof : VilasProfessions.MOD_VENDORS.keySet()) if (VilasProfessions.registered(prof)) vendors.add(prof);
			log("vendedores registados: " + vendors.stream().map(k -> k.identifier().getPath()).toList());
			if (!vendors.isEmpty()) {
				List<Integer> ids = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					List<Integer> out = new ArrayList<>();
					for (int i = 0; i < vendors.size(); i++) {
						var prof = vendors.get(i);
						BlockPos at = new BlockPos(-16 + i * 3, Y, -22);
						l.setBlock(at.north(), VilasProfessions.VENDORS.get(prof).defaultBlockState(), 3);
						Villager v = EntityTypes.VILLAGER.create(l, EntitySpawnReason.COMMAND);
						v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
						v.setNoAi(true);
						v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(), prof).withLevel(2));
						v.setYHeadRot(0);
						v.setYBodyRot(0);
						l.addFreshEntity(v);
						out.add(v.getId());
					}
					return out;
				});
				ctx.waitTicks(20);
				cam(sp, ctx, -16 + (vendors.size() - 1) * 1.5 + 0.5, Y + 2.2, -16.5, -16 + (vendors.size() - 1) * 1.5 + 0.5, Y + 1.0, -22.5, 15);
				sp.getConnection().waitForChunksRender();
				shot(ctx, "09a_vendedores_mecanico_ferreiro_marceneiro_cambista");
				for (int i = 0; i < vendors.size(); i++) {
					int x = -16 + i * 3;
					cam(sp, ctx, x + 0.5, Y + 1.9, -19.6, x + 0.5, Y + 1.4, -22.5, 10);
					shot(ctx, "09b_vendedor_" + vendors.get(i).identifier().getPath());
				}
				// as trocas: clique no vendedor (abre o ecrã de trocas), 1280×720 e 854×480
				for (int i = 0; i < vendors.size(); i++) {
					int id = ids.get(i), x = -16 + i * 3;
					String name = vendors.get(i).identifier().getPath();
					cam(sp, ctx, x + 0.5, Y + 1.62, -20.5, x + 0.5, Y + 1.4, -22.5, 5);
					for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
						ctx.getInput().resizeWindow(size[0], size[1]);
						ctx.waitTicks(8);
						sp.getServer().runOnServer(s -> {
							if (s.overworld().getEntity(id) instanceof Villager v) v.mobInteract(player(s), InteractionHand.MAIN_HAND);
						});
						ctx.waitTicks(12);
						String scr = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "nenhum" : mc.gui.screen().getClass().getSimpleName());
						if (i == 0) log("trocas " + name + " " + size[0] + ": ecrã " + scr);
						ctx.getInput().setCursorPos(2, 2);
						ctx.waitTicks(3);
						shot(ctx, "10_trocas_" + name + "_" + size[0] + "x" + size[1]);
						ctx.runOnClient(mc -> mc.player.closeContainer());
						ctx.waitTicks(5);
					}
				}
				ctx.getInput().resizeWindow(1280, 720);
				ctx.waitTicks(10);
			}

			// ---------------------------------------------------------------- 6) o Mercador da Vila com o burro
			String merchant = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				Village v = village(s);
				long day = VillageEconomy.day(l);
				v.extra.merchantNext = day;
				TravelingMerchant.daily(l, v, day);
				var t = TravelingMerchant.entity(l, v);
				if (t == null) return "não chegou";
				var d = v.extra.donkey == null ? null : l.getEntity(v.extra.donkey);
				// para a foto: na rua, em frente da Prefeitura
				t.setNoAi(true);
				t.snapTo(16.5, Y, 10.5, 0, 0);
				if (d instanceof net.minecraft.world.entity.Mob dm) {
					dm.setNoAi(true);
					dm.snapTo(18.0, Y, 10.5, 0, 0);
				}
				return "mercador em " + t.blockPosition().toShortString() + " burro=" + (d != null) + " trocas=" + t.getOffers().size();
			});
			log(merchant);
			ctx.waitTicks(20);
			cam(sp, ctx, 17.2, Y + 2.0, 15.5, 17.2, Y + 1.0, 10.5, 15);
			shot(ctx, "11a_mercador_da_vila_com_burro");
			cam(sp, ctx, 16.5, Y + 1.62, 12.5, 16.5, Y + 1.4, 10.5, 5);
			for (int[] size : new int[][] {{1280, 720}, {854, 480}}) {
				ctx.getInput().resizeWindow(size[0], size[1]);
				ctx.waitTicks(8);
				sp.getServer().runOnServer(s -> {
					var t = TravelingMerchant.entity(s.overworld(), village(s));
					if (t != null) t.mobInteract(player(s), InteractionHand.MAIN_HAND);
				});
				ctx.waitTicks(12);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(3);
				shot(ctx, "11b_trocas_mercador_" + size[0] + "x" + size[1]);
				ctx.runOnClient(mc -> mc.player.closeContainer());
				ctx.waitTicks(5);
			}
			ctx.getInput().resizeWindow(1280, 720);
			ctx.waitTicks(10);

			// ---------------------------------------------------------------- 8) vilas vanilla de planície GERADAS (/place structure): as peças do
			// mod vêm das pools, os jigsaws têm de desaparecer; numa com Prefeitura, a iluminação pública a sério
			BlockPos[] firstDesk = {null}, firstTavern = {null}, firstPlaza = {null};
			for (int i = 0; i < 16; i++) {
				int vx = i * 600, vz = 3000;
				sp.getServer().runCommand("forceload add " + (vx - 96) + " " + (vz - 96) + " " + (vx + 96) + " " + (vz + 96));
				cam(sp, ctx, vx, Y + 60, vz + 60, vx, Y, vz, 40);
				sp.getServer().runCommand("place structure minecraft:village_plains " + vx + " " + Y + " " + vz);
				ctx.waitTicks(40);
				String found = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					java.util.Map<String, Integer> n = new java.util.TreeMap<>();
					for (BlockPos p : BlockPos.betweenClosed(new BlockPos(vx - 96, Y - 3, vz - 96), new BlockPos(vx + 96, Y + 14, vz + 96))) {
						BlockState st = l.getBlockState(p);
						if (st.isAir() || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT)) continue;
						String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
						if (id.startsWith("tvilas:") || id.startsWith("tmoveis:") || id.equals("minecraft:bell") || id.equals("minecraft:jigsaw")
								|| id.equals("minecraft:structure_void")) n.merge(id, 1, Integer::sum);
						if (st.is(VilasBlocks.MAYOR_DESK) && firstDesk[0] == null) firstDesk[0] = p.immutable();
						// a Taverna: o balcão do Taberneiro com a cama de casal (do Móveis, ou os marcadores sem ele) — o Mercado também tem balcão
						if (firstTavern[0] == null && (id.endsWith("_double_bed") || st.is(FurnitureProcessor.LEFT_BED))) firstTavern[0] = p.immutable();
						if (st.is(Blocks.STONE_BRICK_WALL) && l.getBlockState(p.below()).is(Blocks.STONE_BRICKS) && l.getBlockState(p.below().north()).is(Blocks.WATER)
								&& l.getBlockState(p.above()).is(Blocks.LANTERN) && firstPlaza[0] == null) firstPlaza[0] = p.immutable();
					}
					return n.toString();
				});
				log("vila gerada " + i + " em " + vx + "," + vz + ": " + found);
				sp.getConnection().waitForChunksRender();
				shot(ctx, "12_vila_gerada_" + i + "_de_cima");
			}
			log("peças geradas: Prefeitura=" + firstDesk[0] + " Taverna=" + firstTavern[0] + " Praça=" + firstPlaza[0]);
			for (Object[] piece : new Object[][] {{firstDesk[0], "13a_gerada_prefeitura"}, {firstTavern[0], "13b_gerada_taverna"}, {firstPlaza[0], "13c_gerada_praca"}}) {
				BlockPos q = (BlockPos) piece[0];
				if (q == null) continue;
				for (int[] o : new int[][] {{12, 9, 12}, {-12, 9, -12}, {12, 9, -12}, {-12, 9, 12}}) {
					cam(sp, ctx, q.getX() + o[0], Y + o[1], q.getZ() + o[2], q.getX() + 0.5, Y, q.getZ() + 0.5, 25);
					sp.getConnection().waitForChunksRender();
					shot(ctx, piece[1] + "_" + (o[0] > 0 ? "e" : "o") + (o[2] > 0 ? "s" : "n"));
				}
			}
			if (firstTavern[0] != null) {
				BlockPos q = firstTavern[0];
				cam(sp, ctx, q.getX() + 0.5, Y + 2.4, q.getZ() + 0.5, q.getX() + 3.5, Y + 0.5, q.getZ() + 3.5, 10);
				shot(ctx, "13b_gerada_taverna_dentro_1");
				cam(sp, ctx, q.getX() + 0.5, Y + 2.4, q.getZ() + 0.5, q.getX() - 3.5, Y + 0.5, q.getZ() - 3.5, 10);
				shot(ctx, "13b_gerada_taverna_dentro_2");
			}
			List<BlockPos> genPoles = new ArrayList<>();
			if (firstDesk[0] != null) {
				BlockPos d = firstDesk[0];
				String lit2 = sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = VillageManager.byDesk(l, d);
					if (v == null) v = VillageManager.found(l, d, com.tcordeiro.vilas.VilasConfig.get().radius());
					if (v == null) return "sem vila na Mesa gerada";
					VillageEconomy.ensureMayor(l, v, l.getGameTime());
					Project p = LightingWork.create(l, v);
					if (p == null) return "sem sítios para postes";
					v.extra.lighting = true;
					Builders.start(l, v, p);
					for (int k = 0; k < 3 && !v.extra.projects.isEmpty(); k++) Builders.overnight(l, v);
					StreetLightKit kit = StreetLightKit.forVillage(l, v);
					int poles = 0, nearDoor = 0, badSource = 0, sources = 0, loose = 0;
					for (BlockPos a : p.anchors) {
						BlockState st = l.getBlockState(a);
						if (st.isAir()) continue;
						if (kit.isPole(st)) {
							poles++;
							if (LightingWork.nearDoor(l, a, LightingWork.DOOR_CLEARANCE)) nearDoor++;
						} else {
							sources++;
							if (Math.abs(a.getY() - v.center.getY()) > 1 || !l.getBlockState(a.below()).isFaceSturdy(l, a.below(), Direction.UP)) badSource++;
						}
					}
					for (BlockPos c : BlockPos.betweenClosed(v.center.offset(-50, -4, -50), v.center.offset(50, 10, 50)))
						if (LightingWork.wires(l, c) > 0 && l.getBlockState(c).isAir()) loose++;
					int blocked = 0, wires = 0;
					for (long a : v.extra.lightAnchors)
						for (long b : kit.partners(l, BlockPos.of(a))) {
							wires++;
							if (!kit.wireClear(l, BlockPos.of(a), BlockPos.of(b))) blocked++;
						}
					for (long a : v.extra.lightAnchors) if (kit.isPole(l.getBlockState(BlockPos.of(a)))) genPoles.add(BlockPos.of(a));
					return "fios (pontas)=" + wires + " fios que passam por blocos=" + blocked + " postes=" + poles + " a menos de 2 de uma porta=" + nearDoor + " fonte=" + sources + " fonte fora do chão=" + badSource + " fios soltos=" + loose
							+ " fios=" + p.linked;
				});
				log("iluminação na vila gerada: " + lit2);
				cam(sp, ctx, d.getX() + 14, Y + 9, d.getZ() + 14, d.getX(), Y + 1, d.getZ(), 30);
				sp.getConnection().waitForChunksRender();
				shot(ctx, "14a_gerada_rua_postes_dia");
				cam(sp, ctx, d.getX() - 14, Y + 9, d.getZ() - 14, d.getX(), Y + 1, d.getZ(), 20);
				shot(ctx, "14b_gerada_rua_postes_dia_outro_lado");
				cam(sp, ctx, d.getX() + 5, Y + 3, d.getZ() + 5, d.getX(), Y + 0.5, d.getZ(), 10);
				shot(ctx, "14c_gerada_fonte_ao_pe_da_mesa");
				// de perto, cada poste à altura do fio (para ver que nenhum fio entra em casas)
				for (int k = 0; k < Math.min(8, genPoles.size()); k++) {
					BlockPos q = genPoles.get(k);
					cam(sp, ctx, q.getX() + 4.5, Y + 4.2, q.getZ() + 4.5, q.getX() + 0.5, Y + 2.6, q.getZ() + 0.5, 15);
					sp.getConnection().waitForChunksRender();
					shot(ctx, "14g_gerada_poste_" + k + "_de_perto");
					cam(sp, ctx, q.getX() - 4.5, Y + 4.2, q.getZ() - 4.5, q.getX() + 0.5, Y + 2.6, q.getZ() + 0.5, 10);
					shot(ctx, "14g_gerada_poste_" + k + "_outro_lado");
				}
				// a bateria cheia (os painéis demoram dias de jogo a enchê-la): para a foto de noite
				log("bateria: " + sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					Village v = VillageManager.byDesk(l, d);
					StreetLightKit kit = StreetLightKit.forVillage(l, v);
					for (long a : v.extra.lightAnchors) {
						BlockPos q = BlockPos.of(a);
						if (!kit.isBattery(l.getBlockState(q))) continue;
						try {
							Object be = l.getBlockEntity(q), en = be.getClass().getMethod("energy").invoke(be);
							long cap = ((Number) en.getClass().getMethod("capacity").invoke(en)).longValue();
							en.getClass().getMethod("setStored", long.class).invoke(en, cap);
							return "cheia em " + q.toShortString() + " (" + cap + " J)";
						} catch (ReflectiveOperationException | RuntimeException e) {
							return "não deu para encher: " + e;
						}
					}
					return "sem bateria";
				}));
				ctx.waitTicks(200);
				sp.getServer().runCommand("time set 18000");
				ctx.waitTicks(100);
				cam(sp, ctx, d.getX() + 14, Y + 9, d.getZ() + 14, d.getX(), Y + 1, d.getZ(), 20);
				shot(ctx, "14d_gerada_rua_postes_noite");
				cam(sp, ctx, d.getX() - 14, Y + 9, d.getZ() - 14, d.getX(), Y + 1, d.getZ(), 20);
				shot(ctx, "14e_gerada_rua_postes_noite_outro_lado");
				log("postes acesos de noite (vila gerada): " + sp.getServer().computeOnServer(s -> {
					ServerLevel l = s.overworld();
					int on = 0;
					for (BlockPos q : genPoles)
						for (int k = 0; k < 4; k++) {
							BlockState st = l.getBlockState(q.above(k));
							for (var prop : st.getProperties())
								if (prop.getName().equals("lit") && (Boolean) st.getValue(prop)) {
									on++;
									k = 4;
									break;
								}
						}
					return on + " de " + genPoles.size();
				}));
				for (int k = 0; k < Math.min(5, genPoles.size()); k++) {
					BlockPos q = genPoles.get(k);
					cam(sp, ctx, q.getX() + 3.5, Y + 2.6, q.getZ() + 3.5, q.getX() + 0.5, Y + 2.4, q.getZ() + 0.5, 15);
					sp.getConnection().waitForChunksRender();
					shot(ctx, "14h_gerada_poste_" + k + "_noite_de_perto");
				}
				sp.getServer().runCommand("time set 6000");
				// o Prefeito «a sério» (chegou à Mesa gerada)
				String mayor2 = sp.getServer().computeOnServer(s -> {
					var list = s.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(d).inflate(30), VillageManager::isMayor);
					if (list.isEmpty()) return null;
					Villager m = list.get(0);
					m.setNoAi(true);
					return m.blockPosition().toShortString();
				});
				log("Prefeito na vila gerada: " + mayor2);
				if (mayor2 != null) {
					String[] c = mayor2.split(", ");
					int mx = Integer.parseInt(c[0]), my = Integer.parseInt(c[1]), mz = Integer.parseInt(c[2]);
					cam(sp, ctx, mx + 0.5, my + 1.8, mz + 2.8, mx + 0.5, my + 1.3, mz + 0.5, 15);
					shot(ctx, "14f_gerada_prefeito");
				}
			}
		}
		System.out.println("[VilasNoJogoClientTest] RESUMO: " + notes);
	}
}
