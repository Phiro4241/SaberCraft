package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.menu.TitleStyle;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 24: the title screen, and the story's polish after Phiro's playthrough. (The blade lock's
 * scene played through, her flight in the TIE and the escape film seen whole are in
 * {@link StoryGameTest}; here their skipped variants.)
 *
 * <ul>
 * <li>Title screen: SaberCraft's background, a saying, the buttons (with the mouse on one), the
 * world list and the options still open, the multiplayer button still works; off: vanilla.</li>
 * <li>Shouts top left: the same one not twice, at most a few waiting, old ones gone.</li>
 * <li>No small talk while one is hunted (the reactor's technician), again once it is over.</li>
 * <li>While hunted no crawl way and no detention door: the player is told why; afterwards open.</li>
 * <li>The detention block's guard keeps watch and can be overpowered from behind (a scene), not
 * from the front; he stays down.</li>
 * <li>The saber is not dropped in the story; droids left behind or on a lift come back.</li>
 * <li>The corridor out of the reactor is guarded at once; the reactor shudders after the saber.</li>
 * <li>The blade lock's scene skipped: the second phase at once, once only; the escape film
 * skipped: on to the epilogue.</li>
 * <li>Nobody gets onto the roofs: every place one can walk to stands in the rooms.</li>
 * <li>Old files of every step of the finale still load.</li>
 * </ul>
 */
public class Phase24GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;

	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		this.title();
		context.runOnClient(client -> {
			client.options.renderDistance().set(10);
			client.options.setCameraType(CameraType.FIRST_PERSON);

			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			this.run(context, singleplayer);
		}

		// After the world: back on the title screen, still SaberCraft's.
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("phase24_title_after_world");

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE24 PROBLEM: {}", problem));
			throw new AssertionError("Phase 24: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.roofs();
			this.shouts();
			this.talks();
			this.pursuit();
			this.takedown();
			this.saberKept();
			this.droids();
			this.corridorGuards();
			this.instability();
			this.bossSkipped();
			this.tieShots();
			this.escapeSkipped();
			this.oldFiles();
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE24 aborted", e);
		} finally {
			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	// --- 13: the title screen ---------------------------------------------------------------------------

	private void title() {
		this.context.waitForScreen(TitleScreen.class);
		this.context.waitTicks(40);
		check(this.context.computeOnClient(client -> TitleStyle.enabled()), "the SaberCraft title screen is off by default");
		this.context.takeScreenshot("phase24_title");
		int saying = this.context.computeOnClient(client -> TitleStyle.lastSayingForTest());
		check(saying >= 0 && saying < TitleStyle.SAYINGS, "no SaberCraft saying is shown (" + saying + ")");
		check(this.context.computeOnClient(client -> TitleStyle.twinkleCountForTest()) >= 20, "too few twinkling stars");
		// Every saying is translated in both languages.
		List<String> missing = this.context.computeOnClient(client -> {
			List<String> list = new ArrayList<>();

			for (int i = 0; i < TitleStyle.SAYINGS; i++) {
				String key = "sabercraft.title.splash." + i;

				if (TitleStyle.saying(i).getString().equals(key)) {
					list.add(key);
				}
			}

			return list;
		});
		check(missing.isEmpty(), "sayings without a text: " + missing);

		// The mouse on the first button: it lights up.
		double[] at = this.buttonCentre("menu.singleplayer");

		if (at == null) {
			this.problem("the title screen has no singleplayer button");
		} else {
			this.context.getInput().setCursorPos(at[0], at[1]);
			this.context.waitTicks(12);
			this.context.takeScreenshot("phase24_title_hover");
			this.context.getInput().setCursorPos(2.0, 2.0);
		}

		// The buttons still do what they did.
		this.context.clickScreenButton("menu.singleplayer");
		this.context.waitTicks(20);
		String singleplayer = this.screenName();
		check(!singleplayer.equals("TitleScreen") && !singleplayer.isEmpty(), "the singleplayer button does not open the world list (" + singleplayer + ")");
		this.context.takeScreenshot("phase24_menu_worlds");
		this.context.setScreen(() -> new TitleScreen());
		this.context.waitTicks(10);
		this.context.clickScreenButton("menu.options");
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> client.gui.screen() instanceof OptionsScreen), "the options button does not open the options");
		this.context.takeScreenshot("phase24_menu_options");
		this.context.setScreen(() -> new TitleScreen());
		this.context.waitTicks(10);
		this.context.clickScreenButton("menu.multiplayer");
		this.context.waitTicks(10);
		String multiplayer = this.screenName();
		check(!multiplayer.equals("TitleScreen") && !multiplayer.isEmpty(), "the multiplayer button does not open its screen (" + multiplayer + ")");
		SaberCraft.LOGGER.info("PHASE24 TITLE: singleplayer opens {}, multiplayer opens {}", singleplayer, multiplayer);
		this.context.setScreen(() -> new TitleScreen());
		this.context.waitTicks(10);

		// Switched off: the vanilla title screen.
		this.context.runOnClient(client -> ImmersionConfig.get().titleScreen = false);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase24_title_off");
		this.context.runOnClient(client -> ImmersionConfig.get().titleScreen = true);
		this.context.waitTicks(4);
	}

	private @Nullable double[] buttonCentre(String key) {
		return this.context.computeOnClient(client -> {
			Screen screen = client.gui.screen();

			if (screen == null) {
				return null;
			}

			double scale = client.getWindow().getGuiScale();

			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractButton button && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
					return new double[] {(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
				}
			}

			return null;
		});
	}

	private String screenName() {
		return this.context.computeOnClient(client -> client.gui.screen() == null ? "" : client.gui.screen().getClass().getSimpleName());
	}

	// --- 10: the roofs ------------------------------------------------------------------------------------

	/**
	 * Every place one can get to on foot in the Star Destroyer of the story (from the hangar, the
	 * corridor, the reactor, the lab, the detention block, the command deck and the bridge) stands
	 * inside the rooms: nobody gets onto the roof of the bridge or of anything else.
	 */
	private void roofs() {
		this.startAt(5, "m6.duel");
		List<Vec3> starts = List.of(new Vec3(40.5, H, -28.5), new Vec3(40.5, H, -8.0), new Vec3(40.5, H, 2.0), new Vec3(31.5, H, 3.5), new Vec3(50.5, H, -8.5),
				new Vec3(6.5, H, -4.5), new Vec3(28.8, R, 25.0), new Vec3(44.5, R, 41.0),
				new Vec3(40.5, DestroyerInterior.DECK + 1, DestroyerInterior.COMMAND_BACK - 3.5), new Vec3(40.5, DestroyerInterior.DECK + 1, 80.0),
				new Vec3(40.5, DestroyerInterior.DECK + 1, 68.0));
		List<String> lines = this.on(s -> scan(s, this.session(s).destroyer, starts, 4.6, java.util.Set.of(H - 1, R - 1, DestroyerInterior.DECK, DestroyerInterior.PIT),
				java.util.Set.of("reactor")));
		int ff = com.phiro.sabercraft.interior.FalconInterior.FLOOR + 1;
		List<Vec3> falcon = List.of(new Vec3(35.5, ff, 31.5), new Vec3(21.5, ff, 28.5), new Vec3(43.5, ff, 25.5));
		lines.addAll(this.on(s -> scan(s, this.session(s).falcon, falcon, 2.6, java.util.Set.of(ff - 1), java.util.Set.of("ramp", "top_turret", "bottom_turret")))
				.stream().map(line -> "falcon " + line).toList());
		lines.forEach(line -> SaberCraft.LOGGER.info("PHASE24 ROOF {}", line));
		List<String> high = lines.stream().filter(line -> line.contains("odd ") && (line.contains(" high") || line.contains(" wall") || line.contains(" outside")
				|| line.contains(" roof"))).toList();
		check(high.isEmpty(), "places to walk on outside the rooms (roofs, walls), or places in them taken for a roof: " + high);
		this.roofSetBack();
	}

	/**
	 * The safety net: whoever stands on the bridge's roof anyway (thrown up there, a jump nobody
	 * foresaw) is set back at once where they last stood in a room.
	 */
	private void roofSetBack() {
		this.startAt(5, "m6.duel");
		this.put(new Vec3(40.5, DestroyerInterior.DECK + 1, 80.0), 0.0F, 0.0F);
		this.context.waitTicks(10);
		// The top of the bridge's ceiling over that spot.
		double top = this.on(s -> {
			ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			BlockPos origin = this.session(s).destroyer;

			for (double y = DestroyerInterior.BRIDGE_TOP - 1; y < DestroyerInterior.BRIDGE_TOP + 6; y += 0.0625) {
				Vec3 at = new Vec3(origin.getX() + 40.5, origin.getY() + y, origin.getZ() + 80.0);

				if (ShipInteriors.standsFirmForTest(level, at)) {
					return y;
				}
			}

			return Double.NaN;
		});
		check(!Double.isNaN(top), "no roof over the bridge to stand on (for the test)");

		if (Double.isNaN(top)) {
			return;
		}

		int before = this.on(s -> ShipInteriors.roofsLeftForTest());
		this.put(new Vec3(40.5, top, 80.0), 0.0F, 0.0F);
		this.context.waitTicks(12);
		Vec3 local = this.on(s -> this.me(s).position().subtract(Vec3.atLowerCornerOf(this.session(s).destroyer)));
		int after = this.on(s -> ShipInteriors.roofsLeftForTest());
		SaberCraft.LOGGER.info("PHASE24 ROOF set back: put on the roof at y {}, now at {} ({} time(s))", top, local, after - before);
		check(after > before && local.y < DestroyerInterior.BRIDGE_TOP - 1, "left standing on the bridge's roof (at " + local + ")");
	}

	/**
	 * Where one can walk in that plot (every door and hatch open), by room: the cells well above
	 * their room's floor, on a wall's top, or outside every room above a floor's level are odd.
	 */
	private static List<String> scan(MinecraftServer s, BlockPos origin, List<Vec3> starts, double highAbove, java.util.Set<Integer> floors,
			java.util.Set<String> tall) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		InteriorPlan plan = ShipInteriors.planAt(origin.getX() + 0.5, origin.getZ() + 0.5);
		List<String> out = new ArrayList<>();
		// Every door and hatch open for the search (closed they are walls); closed again after it, in the same tick.
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> closed = new java.util.HashMap<>();

		for (InteriorPlan.Door door : plan.doors()) {
			for (BlockPos cell : BlockPos.betweenClosed(door.x0(), door.y0(), door.z0(), door.x1(), door.y1(), door.z1())) {
				closed.put(origin.offset(cell).immutable(), level.getBlockState(origin.offset(cell)));
			}
		}

		if (plan instanceof DestroyerInterior) {
			for (BlockPos hatch : List.of(DestroyerInterior.HIDEOUT_HATCH, DestroyerInterior.DETENTION_HATCH, DestroyerInterior.MAINTENANCE_HATCH)) {
				closed.put(origin.offset(hatch).immutable(), level.getBlockState(origin.offset(hatch)));
			}
		}

		closed.keySet().forEach(cell -> level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16));

		for (Vec3 start : starts) {
			out.add(String.format(Locale.ROOT, "from %s: %d cells", start, StoryTesting.reachable(level, origin, List.of(start), 400_000, true).size()));
		}

		List<double[]> cells = StoryTesting.reachable(level, origin, starts, 400_000, true);
		// Tops outside every room one could stand on (a roof, a wall's top), and those near a place one can reach.
		AABB all = null;

		for (InteriorPlan.Room room : plan.rooms()) {
			all = all == null ? room.area() : all.minmax(room.area());
		}

		all = all.inflate(3.0);
		Map<Long, double[]> tops = new java.util.HashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		for (int x = (int) all.minX; x <= (int) all.maxX; x++) {
			for (int z = (int) all.minZ; z <= (int) all.maxZ; z++) {
				for (int y = (int) all.minY; y <= (int) all.maxY; y++) {
					at.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);

					if (shape.isEmpty()) {
						continue;
					}

					double top = y + shape.max(net.minecraft.core.Direction.Axis.Y);
					AABB body = new AABB(origin.getX() + x + 0.2, origin.getY() + top + 0.001, origin.getZ() + z + 0.2, origin.getX() + x + 0.8,
							origin.getY() + top + 1.8, origin.getZ() + z + 0.8);

					if (level.noCollision(body) && plan.room(new Vec3(x + 0.5, top + 0.1, z + 0.5)) == null
							&& !floors.contains((int) Math.floor(top - 0.01))) {
						tops.put(((long) (x + 1000) << 40) | ((long) (z + 1000) << 20) | (long) Math.round(top * 8), new double[] {x, z, top});
					}
				}
			}
		}

		out.add("standable tops outside the rooms: " + tops.size());
		Map<String, int[]> near = new TreeMap<>();

		for (double[] top : tops.values()) {
			for (double[] cell : cells) {
				double dx = Math.abs(cell[0] - top[0]);
				double dz = Math.abs(cell[1] - top[1]);
				double up = top[2] - cell[2];

				if (dx <= 3 && dz <= 3 && up > -2.0 && up <= 1.6) {
					String key = String.format(Locale.ROOT, "top y%.2f near feet %.2f (up %.2f)", top[2], cell[2], up);
					int[] g = near.computeIfAbsent(key, k -> new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE});
					g[0]++;
					g[1] = Math.min(g[1], (int) top[0]);
					g[2] = Math.max(g[2], (int) top[0]);
					g[3] = Math.min(g[3], (int) top[1]);
					g[4] = Math.max(g[4], (int) top[1]);
					break;
				}
			}
		}

		near.forEach((key, g) -> out.add(String.format(Locale.ROOT, "near %s: %d tops, x %d..%d, z %d..%d", key, g[0], g[1], g[2], g[3], g[4])));
		closed.forEach((cell, state) -> level.setBlock(cell, state, 2 | 16));
		Map<String, double[]> rooms = new TreeMap<>();
		Map<String, int[]> groups = new TreeMap<>();
		out.add("cells " + cells.size());

		for (double[] cell : cells) {
			String ground = StoryTesting.ground(level, origin, cell);
			Vec3 local = new Vec3(cell[0] + 0.5, cell[2] + 0.1, cell[1] + 0.5);
			InteriorPlan.Room room = plan.room(local);
			String id = room == null ? "none" : room.id();
			double[] r = rooms.computeIfAbsent(id, k -> new double[] {0, Double.MAX_VALUE, -Double.MAX_VALUE});
			r[0]++;
			r[1] = Math.min(r[1], cell[2]);
			r[2] = Math.max(r[2], cell[2]);
			// High: well above the floor of its room (not in tall rooms, where the floors are many).
			boolean high = room != null && !tall.contains(id) && cell[2] > room.area().minY + highAbove;
			boolean floorLevel = floors.contains((int) Math.floor(cell[2] - 0.01));
			boolean wall = !floorLevel && (ground.equals("gray_concrete") || ground.equals("polished_deepslate") || ground.equals("polished_blackstone"));
			int lowest = floors.stream().min(Integer::compare).orElse(0) + 1;
			boolean outside = room == null && !floorLevel && cell[2] > lowest + 1.6;
			// The safety net must never take a place one may walk to for a roof (nor its edges).
			boolean roof = false;

			for (double[] off : new double[][] {{0, 0}, {-0.45, -0.45}, {-0.45, 0.45}, {0.45, -0.45}, {0.45, 0.45}}) {
				double x = cell[0] + 0.5 + off[0];
				double z = cell[1] + 0.5 + off[1];
				AABB body = new AABB(origin.getX() + x - 0.3, origin.getY() + cell[2] + 0.01, origin.getZ() + z - 0.3, origin.getX() + x + 0.3,
						origin.getY() + cell[2] + 1.8, origin.getZ() + z + 0.3);

				if ((off[0] == 0 || level.noCollision(body)) && plan.onRoof(new Vec3(x, cell[2], z))) {
					roof = true;
				}
			}

			if (high || wall || outside || roof) {
				String key = String.format(Locale.ROOT, "%s y%.2f in %s%s%s%s%s", ground, cell[2], id, high ? " high" : "", wall ? " wall" : "", outside ? " outside" : "",
						roof ? " roof" : "");
				int[] g = groups.computeIfAbsent(key, k -> new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE});
				g[0]++;
				g[1] = Math.min(g[1], (int) cell[0]);
				g[2] = Math.max(g[2], (int) cell[0]);
				g[3] = Math.min(g[3], (int) cell[1]);
				g[4] = Math.max(g[4], (int) cell[1]);
			}
		}

		rooms.forEach((id, r) -> out.add(String.format(Locale.ROOT, "room %s: %d cells, feet %.2f..%.2f", id, (int) r[0], r[1], r[2])));
		groups.forEach((key, g) -> out.add(String.format(Locale.ROOT, "odd %s: %d cells, x %d..%d, z %d..%d", key, g[0], g[1], g[2], g[3], g[4])));
		return out;
	}

	// --- 3: the shouts top left ---------------------------------------------------------------------------

	private void shouts() {
		this.startAt(5, "m6.door");
		this.context.waitTicks(20);
		int droppedBefore = this.context.computeOnClient(client -> StoryClient.linesDroppedForTest());
		this.context.runOnClient(client -> {
			for (int i = 0; i < 5; i++) {
				StoryClient.lineForTest("trooper", "sabercraft.story.shout.halt", true);
			}
		});
		int waiting = this.context.computeOnClient(client -> StoryClient.linesWaitingForTest());
		check(waiting <= 1, "the same shout waits " + waiting + " times");
		this.context.runOnClient(client -> {
			for (String key : List.of("intruder", "hands_up", "alarm_call", "officer_halt", "officer_call", "mouse_again")) {
				StoryClient.lineForTest("trooper", "sabercraft.story.shout." + key, true);
			}
		});
		waiting = this.context.computeOnClient(client -> StoryClient.linesWaitingForTest());
		check(waiting <= 3, waiting + " shouts wait to be shown (at most a few should)");
		check(this.context.computeOnClient(client -> StoryClient.linesDroppedForTest()) > droppedBefore, "no shout was left out");
		this.context.takeScreenshot("phase24_shouts");
		// Old ones go: after a while nothing of it is left.
		this.context.waitTicks(260);
		waiting = this.context.computeOnClient(client -> StoryClient.linesWaitingForTest());
		check(waiting == 0, waiting + " old shouts are still there after 13 seconds");
		// Many troopers seeing the player: their shouts are held back on the server too.
		int heldBefore = this.on(s -> this.session(s).shoutsHeldBack);
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);

			for (Figure figure : session.figures) {
				if (figure.hostile && figure.mode != Figure.DOWN) {
					StoryTesting.detect(session, figure);
				}
			}
		});
		this.context.waitTicks(40);
		int held = this.on(s -> this.session(s).shoutsHeldBack) - heldBefore;
		int shown = this.context.computeOnClient(client -> StoryClient.linesWaitingForTest());
		SaberCraft.LOGGER.info("PHASE24 SHOUTS: held back on the server {}, waiting on the client {}", held, shown);
		check(shown <= 3, shown + " shouts wait after several troopers saw the player");
	}

	// --- 1: no small talk while hunted ------------------------------------------------------------------

	private void talks() {
		this.startAt(5, "m6.door");
		this.context.waitTicks(20);
		Integer tech = this.on(s -> {
			Figure figure = this.session(s).figure("tech");
			return figure == null ? null : figure.id;
		});

		if (tech == null) {
			this.startAt(3, "m4.download");
			this.context.waitTicks(20);
			tech = this.on(s -> {
				Figure figure = this.session(s).figure("tech");
				return figure == null ? null : figure.id;
			});
		}

		if (tech == null) {
			this.problem("no technician in the reactor to talk to");
			return;
		}

		int id = tech;
		check(this.on(s -> this.session(s).chapter.talksNow(this.session(s), this.session(s).figure("tech"))), "the technician does not talk while all is calm");
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure seer = session.figures.stream().filter(f -> f.observer && f.mode != Figure.DOWN && !f.key.equals("tech")).findFirst().orElse(null);

			if (seer != null) {
				StoryTesting.detect(session, seer);
			}
		});
		this.context.waitTicks(10);
		check(this.on(s -> this.session(s).danger()), "being seen does not count as danger");
		check(!this.on(s -> this.session(s).chapter.talksNow(this.session(s), this.session(s).figure("tech"))), "the technician makes small talk while the troopers hunt the player");
		boolean talked = this.on(s -> Story.talk(this.session(s), this.me(s), id));
		check(!talked, "a talk with the technician begins in the alarm");
		check(this.on(s -> this.session(s).dialog == null), "a dialog opened in the alarm");
		// After the alarm: he talks again.
		this.calm();
		check(this.on(s -> this.session(s).chapter.talksNow(this.session(s), this.session(s).figure("tech"))), "the technician does not talk again after the alarm");
	}

	/** Ends every alarm and fight (as after a search that found nothing). */
	private void calm() {
		for (int i = 0; i < 60 && this.on(s -> this.session(s).danger()); i++) {
			this.server.runOnServer(s -> {
				StorySession session = this.session(s);
				StoryTesting.unseen(session, 1_000_000);
				StoryTesting.endAlarm(session);
			});
			this.context.waitTicks(10);
		}

		if (this.on(s -> this.session(s).danger())) {
			this.server.runOnServer(s -> this.session(s).alarm = 0);
			this.context.waitTicks(5);
		}

		check(!this.on(s -> this.session(s).danger()), "the alarm does not end");
	}

	// --- 4: no crawl way and no detention door while hunted -----------------------------------------------

	private void pursuit() {
		this.startAt(4, "m5.route");
		this.context.waitTicks(20);
		this.server.runOnServer(s -> this.session(s).set("trust.hale"));
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure seer = session.figures.stream().filter(f -> f.observer && f.mode != Figure.DOWN).findFirst().orElse(null);

			if (seer != null) {
				StoryTesting.detect(session, seer);
			}
		});
		this.context.waitTicks(5);
		check(this.on(s -> StoryTesting.pursued(this.session(s))), "being seen does not count as being hunted");
		// The detention block's door (Hale expects the player) stays shut while hunted.
		int doorX = DestroyerInterior.CX - 4;
		int doorZ = DestroyerInterior.DETENTION_DOOR_Z0;
		this.put(new Vec3(doorX + 0.5, H, doorZ + 2.0), 180.0F, 0.0F);
		check(this.on(s -> Story.locked(this.session(s).destroyer, doorX, H, doorZ)), "the detention door opens while the player is hunted");
		// A crawl way's hatch: not into it.
		int refused = this.on(s -> Story.hatchesRefusedForTest());
		BlockPos hatch = DestroyerInterior.HIDEOUT_HATCH;
		this.put(new Vec3(hatch.getX() + 0.5, H, hatch.getZ() + 1.6), 180.0F, 0.0F);
		boolean shut = this.on(s -> Story.hatchClosed(this.me(s), this.session(s).destroyer, hatch, net.minecraft.core.Direction.SOUTH));
		check(shut, "a crawl way opens while the player is hunted");
		check(this.on(s -> Story.hatchesRefusedForTest()) > refused, "the hatch is not refused for the chase");
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase24_hunted_hatch");
		// Once they lost the player: the door opens again.
		this.calm();
		check(!this.on(s -> StoryTesting.pursued(this.session(s))), "still hunted after the alarm");
		check(!this.on(s -> Story.locked(this.session(s).destroyer, doorX, H, doorZ)), "the detention door stays shut after the chase");
	}

	// --- 5: the detention block's guard -------------------------------------------------------------------

	private void takedown() {
		// (Also an old file at this step: he stands at his post, not down.)
		this.startAt(4, "m5.r2");
		this.context.waitTicks(30);
		Vec3 post = this.on(s -> {
			Figure guard = this.session(s).figure("det_guard");
			return guard == null ? null : guard.position();
		});

		if (post == null) {
			this.problem("no guard in the detention block at m5.r2");
			return;
		}

		SaberCraft.LOGGER.info("PHASE24 GUARD at {}", post);
		check(post.distanceTo(new Vec3(34.5, H, 7.6)) < 1.0, "the guard is not at his post (" + post + ")");
		// He keeps watch: his look changes over time (door, room).
		float yaw0 = this.on(s -> this.session(s).figure("det_guard").yaw);
		boolean turned = false;

		for (int i = 0; i < 30 && !turned; i++) {
			this.context.waitTicks(10);
			turned = Math.abs(net.minecraft.util.Mth.wrapDegrees(this.on(s -> this.session(s).figure("det_guard").yaw) - yaw0)) > 30.0F;
		}

		check(turned, "the guard does not look round");
		// From the front: he sees it and raises the alarm.
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure guard = session.figure("det_guard");
			Vec3 front = guard.position().add(guard.look().scale(1.6));
			this.teleport(s, session, front, guard.yaw + 180.0F);
			StoryTesting.takedown(session);
		});
		int takedowns = this.on(s -> StoryTesting.takedowns());
		check(this.on(s -> this.session(s).figure("det_guard").mode != Figure.DOWN), "the guard is overpowered from the front");
		// Again, from behind (a fresh start).
		this.startAt(4, "m5.r2");
		this.context.waitTicks(30);
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure guard = session.figure("det_guard");
			Vec3 back = guard.position().subtract(guard.look().scale(1.5));
			this.teleport(s, session, back, guard.yaw);
			StoryTesting.takedown(session);
		});
		check(this.on(s -> StoryTesting.takedowns()) > takedowns, "the guard cannot be overpowered from behind");
		check("takedown".equals(this.film(10)), "the scene of the takedown does not play");
		this.context.waitTicks(6);
		this.context.takeScreenshot("phase24_takedown_1_grab");
		this.context.waitTicks(16);
		this.context.takeScreenshot("phase24_takedown_2_blow");
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase24_takedown_3_down");

		for (int i = 0; i < 40 && this.context.computeOnClient(client -> StoryCinema.active()); i++) {
			this.context.waitTicks(2);
		}

		check(this.on(s -> this.session(s).figure("det_guard").mode == Figure.DOWN), "the guard is not down after the scene");
		check(this.on(s -> !this.session(s).has("tmp.film") && !this.session(s).has("tmp.takedown")), "the scene does not end on the server");
		check(this.on(s -> this.session(s).alarm == 0), "the takedown raises the alarm");
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase24_takedown_4_after");
	}

	// --- 7: the saber stays ---------------------------------------------------------------------------------

	private void saberKept() {
		this.startAt(5, "m6.escape1");
		this.context.waitTicks(20);
		this.server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
		this.context.waitTicks(5);
		int kept = this.on(s -> Story.sabersKeptForTest());
		this.context.runOnClient(client -> client.player.drop(true));
		this.context.waitTicks(10);
		check(this.on(s -> Story.sabersKeptForTest()) > kept, "the saber was not kept when thrown");
		check(this.on(s -> this.me(s).getInventory().contains(stack -> stack.is(Items.NETHERITE_SWORD))), "the saber is gone from the inventory");
		check(this.on(s -> this.me(s).level().getEntitiesOfClass(ItemEntity.class, this.me(s).getBoundingBox().inflate(8.0),
				item -> item.getItem().is(Items.NETHERITE_SWORD)).isEmpty()), "the saber lies on the floor");
	}

	// --- 6: droids left behind come back ----------------------------------------------------------------------

	private void droids() {
		this.startAt(5, "m6.reactor");
		// (A file made for the test has only the step: the droids recruited before are set here.)
		this.flags("droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.context.waitTicks(60);
		List<DroidType> crew = this.on(s -> Droids.crew(this.me(s)));
		SaberCraft.LOGGER.info("PHASE24 DROIDS: {}", crew);

		if (crew.isEmpty()) {
			this.problem("no droids with the player at m6.reactor");
			return;
		}

		DroidType type = crew.get(0);
		int returned = this.on(s -> Story.droidsReturnedForTest());
		// Left far behind (sent to the other end of the ship).
		this.server.runOnServer(s -> {
			DroidEntity droid = Droids.droid(this.me(s), type);
			BlockPos origin = this.session(s).destroyer;

			if (droid != null) {
				droid.teleportTo(origin.getX() + 40.5, origin.getY() + H, origin.getZ() - 40.5);
			}
		});
		boolean back = false;

		for (int i = 0; i < 30 && !back; i++) {
			this.context.waitTicks(10);
			back = this.on(s -> {
				DroidEntity droid = Droids.droid(this.me(s), type);
				return droid != null && droid.distanceTo(this.me(s)) < 10.0;
			});
		}

		check(back, "a droid left far behind does not come back");
		SaberCraft.LOGGER.info("PHASE24 DROIDS: brought back by the story {} time(s) (else it came by itself)", this.on(s -> Story.droidsReturnedForTest()) - returned);
		// On a lift's pad: brought off it.
		returned = this.on(s -> Story.droidsReturnedForTest());
		this.server.runOnServer(s -> {
			DroidEntity droid = Droids.droid(this.me(s), type);
			BlockPos origin = this.session(s).destroyer;

			if (droid != null) {
				droid.teleportTo(origin.getX() + DestroyerInterior.CX + 0.5, origin.getY() + H, origin.getZ() + DestroyerInterior.LIFT_Z0 + 1.5);
			}
		});
		boolean off = false;

		for (int i = 0; i < 30 && !off; i++) {
			this.context.waitTicks(10);
			off = this.on(s -> {
				DroidEntity droid = Droids.droid(this.me(s), type);
				return droid != null && !DroidEntity.onLift(droid.level(), droid.blockPosition()) && droid.distanceTo(this.me(s)) < 10.0;
			});
		}

		check(off, "a droid on a lift's pad stays there");
	}

	// --- 9: the corridor's guards ----------------------------------------------------------------------------

	private void corridorGuards() {
		this.startAt(5, "m6.escape1");
		this.context.waitTicks(10);
		List<String> guards = this.on(s -> this.session(s).figures.stream().filter(f -> f.key.equals("h4") || f.key.equals("h4b"))
				.map(f -> String.format(Locale.ROOT, "%s %.1f,%.1f,%.1f mode %d", f.key, f.x, f.y, f.z, f.mode)).toList());
		SaberCraft.LOGGER.info("PHASE24 CORRIDOR: {}", guards);
		check(guards.size() == 2, "the corridor out of the reactor is not guarded at once (" + guards + ")");
		// They hold their posts while nobody comes (the player in the lab, far below).
		this.context.waitTicks(80);
		List<Double> moved = this.on(s -> this.session(s).figures.stream().filter(f -> f.key.equals("h4") || f.key.equals("h4b"))
				.map(f -> f.key.equals("h4") ? f.position().distanceTo(new Vec3(48.0, H, 9.0)) : f.position().distanceTo(new Vec3(47.3, H, 4.2))).toList());
		check(moved.size() == 2 && moved.stream().allMatch(d -> d < 1.5), "the corridor's guards leave their posts unprovoked (" + moved + ")");
		// Coming up the corridor: they are there, rifles up.
		this.put(new Vec3(47.5, H, 14.0), 180.0F, 0.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase24_corridor_guards");
	}

	// --- 12: the failing reactor ---------------------------------------------------------------------------------

	private void instability() {
		this.startAt(5, "m6.escape1");
		this.flags("saber.taken");
		int shudders = this.on(s -> StoryTesting.shudders());
		int rumbles = this.context.computeOnClient(client -> StoryClient.rumblesForTest());
		int level = this.on(s -> StoryTesting.instability(this.session(s)));
		this.context.waitTicks(260);
		check(level >= 1, "the reactor is not failing after the saber was taken (level " + level + ")");
		check(this.on(s -> StoryTesting.shudders()) > shudders, "the ship does not shudder after the saber was taken");
		check(this.context.computeOnClient(client -> StoryClient.rumblesForTest()) > rumbles, "the client feels no shudder");
		this.context.takeScreenshot("phase24_reactor_failing");
		this.startAt(5, "m6.escape2");
		this.flags("saber.taken");
		check(this.on(s -> StoryTesting.instability(this.session(s))) > level || level >= 3, "the failure does not grow on the way to the hangar");
		this.startAt(5, "m6.door");
		check(this.on(s -> StoryTesting.instability(this.session(s))) == 0, "the reactor fails before the saber is taken");
	}

	// --- 11: the blade lock's scene, skipped ------------------------------------------------------------------

	/**
	 * Pictures of the moments by her TIE in the film of her flight (only the client's camera and
	 * the parked TIE: she stands where the duel left her), to see that the TIE is in the picture.
	 */
	private void tieShots() {
		this.startAt(5, "m6.duel");
		// (Far enough from where the duel's film begins: that one is not wanted here.)
		this.put(new Vec3(30.5, H, -33.0), 90.0F, 0.0F);
		this.context.waitTicks(20);
		Vec3 tie = this.on(s -> DestroyerInterior.ESCAPE_TIE.add(Vec3.atLowerCornerOf(this.session(s).destroyer)));
		this.context.runOnClient(client -> StoryCinema.startForTest("duel_end", tie));

		for (int age : new int[] {150, 164, 176, 188, 200}) {
			this.context.runOnClient(client -> StoryCinema.seekForTest(age));
			this.context.waitTicks(2);
			this.context.takeScreenshot("phase24_tie_" + age);
		}

		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(10);
		// And the escape seen from outside: the ring of the core's blast, the jump.
		this.context.runOnClient(client -> StoryCinema.startForTest("escape"));

		for (int age : new int[] {130, 290, 336, 352, 362}) {
			this.context.runOnClient(client -> StoryCinema.seekForTest(age));
			this.context.waitTicks(2);
			this.context.takeScreenshot("phase24_escape_" + age);
		}

		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(10);
	}

	private void bossSkipped() {
		this.startAt(5, "m6.duel");
		this.flags("saber.taken");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		// Where the duel's film begins (facing her).
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms("duel", 300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return;
		}

		int scenes = this.on(s -> StoryTesting.bossScenes());
		float health = this.on(s -> this.me(s).getHealth());
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		check(this.on(s -> StoryTesting.bossScenes()) == scenes + 1, "the blade lock's scene does not begin at half her strength");
		check("boss_mid".equals(this.film(20)), "the client does not play the blade lock's scene");
		this.context.waitTicks(10);
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, "boss_mid", 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(10);
		check(!this.on(s -> this.session(s).has("tmp.boss_mid")), "the scene does not end when skipped");
		check(!this.on(s -> this.session(s).has("tmp.film")), "the fight still waits after the skipped scene");
		check(this.on(s -> StoryTesting.phase(this.session(s).figure("shadow")) == 2), "the second phase does not begin after the skipped scene");
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> StoryClient.duelPhaseForTest()) == 2, "the client does not know the second phase after the skip");
		check(this.context.computeOnClient(client -> StoryClient.twinBlade()), "her second blade is not out after the skip");
		check(this.on(s -> this.me(s).getHealth()) >= health - 0.01F, "the player was hurt in the scene");
		// Not twice.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		this.context.waitTicks(5);
		check(this.on(s -> StoryTesting.bossScenes()) == scenes + 1, "the blade lock's scene plays a second time");
		this.context.takeScreenshot("phase24_boss_phase2");
	}

	// --- 12: the escape film, skipped -----------------------------------------------------------------------------

	private void escapeSkipped() {
		this.startAt(5, "m6.jump");
		int escapes = this.on(s -> StoryTesting.escapes());
		this.server.runOnServer(s -> this.session(s).set("tmp.jump_go"));
		this.context.waitTicks(10);
		check(this.on(s -> StoryTesting.escapes()) == escapes + 1, "the escape film does not begin with the jump");
		check("escape".equals(this.film(20)), "the client does not play the escape film");
		this.context.waitTicks(40);
		this.context.takeScreenshot("phase24_escape_skip_before");
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, "escape", 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(10);
		check(!this.on(s -> this.session(s).has("tmp.film")), "the story still waits for the skipped film");
		this.waitStep("m6.epilogue", 400);
	}

	// --- 14: old files ---------------------------------------------------------------------------------------------

	private void oldFiles() {
		for (String step : List.of("m6.plan", "m6.reactor", "m6.door", "m6.lab", "m6.saber", "m6.escape1", "m6.escape2", "m6.duel", "m6.falcon", "m6.jump",
				"m6.epilogue")) {
			this.startAt(5, step);
		}

		for (String step : List.of("m5.escape", "m5.route", "m5.r2", "m5.cell1", "m5.back")) {
			this.startAt(4, step);
		}

	}

	// --- Helpers ------------------------------------------------------------------------------------------------------

	/** Sets these marks of the story (what a real file of that step has; a file made for the test has only the step). */
	private void flags(String... flags) {
		this.server.runOnServer(s -> {
			for (String flag : flags) {
				this.session(s).set(flag);
			}
		});
	}

	/** The film the client plays (waiting up to that many ticks for one to begin). */
	private @Nullable String film(int ticks) {
		for (int i = 0; i < ticks; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				return film;
			}

			this.context.waitTick();
		}

		return null;
	}

	/** Skips that film (and any other the story plays meanwhile) until the story stops waiting, at most that many ticks. */
	private void skipFilms(String name, int ticks) {
		for (int i = 0; i < ticks / 5; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
				this.context.runOnClient(client -> StoryCinema.skipForTest());
			} else if (this.on(s -> this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s).dialog != null)) {
				// His words (the film was seen before): on through them.
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
	}

	private void teleport(MinecraftServer server, StorySession session, Vec3 local, float yaw) {
		ServerPlayer player = this.me(server);
		Vec3 world = local.add(Vec3.atLowerCornerOf(session.destroyer));
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(), yaw, 0.0F, true);
	}

	/** Continues the story at that step (a file saved there). */
	private void startAt(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
		this.context.waitTicks(60);
		this.waitStep(step, 60);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
		check(this.on(s -> !Story.playing(this.me(s))), "still in the story after leaving");
	}

	private void waitStep(String step, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the story is not at " + step + " (at " + this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))) + ")");
	}

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE24 CHECK FAILED: {}", problem);
		this.problems.add(problem);
	}

	private @Nullable StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}

	private <T> T on(Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private @Nullable ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}
}
