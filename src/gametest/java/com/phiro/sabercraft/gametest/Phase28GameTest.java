package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.client.HintCard;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.music.StoryMusic;
import com.phiro.sabercraft.client.story.CoverScreen;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.client.story.StoryInput;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StoryDifficulty;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 28: the last polish of chapter 1, after Phiro's playtest. The parts a test can play in the
 * real game (the feel stays for the manual test list in {@code docs/phase-28-status.md}):
 *
 * <ol>
 * <li>The hint cards: at the left, never over the mission panel, at every size of screen (1).</li>
 * <li>Without Hale's trust: the objective says the way, the main door says why it stays shut, a
 * click on Kestrel's hatch sends M-31 through its grille, the marker leads through the crawl way,
 * and its end reaches R2-V7's step (3).</li>
 * <li>Mission 2's side way: the cover shut from the far side, the screwdriver in its toolbox (kept
 * through a capture), the close-up with the four screws turned out with the mouse, the cover off
 * and kept, the way free; again from a fresh start; the toolbox in later missions; the marker (4).</li>
 * <li>The reactor's lift: not while one moves on it, after standing still a moment, not again at
 * once (2).</li>
 * <li>The catwalk's technician: his round of looks, the hint, a lure that takes him off his post,
 * and back to his round (5).</li>
 * <li>The duel: blocks strain the guard (one holds, many break it, by the difficulty) (6); her
 * throws once or twice a fight (10); her ring's lasting state (11); the droids waiting aside (8);
 * no saber left lying after her flight or a restart (9); the music to the end (7).</li>
 * </ol>
 */
public class Phase28GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:blue\"]}]";

	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
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

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE28 PROBLEM: {}", problem));
			throw new AssertionError("Phase 28: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.part("hints", this::hints);
			this.part("refuse", this::refuse);
			this.part("cover", this::cover);
			this.part("coverAgain", this::coverAgain);
			this.part("lift", this::lift);
			this.part("catwalk", this::catwalk);
			this.part("strain", this::strain);
			this.part("boss", this::boss);
			this.part("jump", this::jump);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE28 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keyShift);
			context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			context.getInput().releaseMouse(0);
			context.runOnClient(client -> {
				if (client.gui.screen() instanceof CoverScreen) {
					client.gui.setScreen(null);
				}
			});

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE28_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE28_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE28 part {}", name);
			part.run();
		}
	}

	// --- 1: the hint cards -------------------------------------------------------------------------------------

	private void hints() {
		this.enter(1, "m2.drop");
		this.unseen();
		this.context.waitTicks(20);
		int reserved = this.context.computeOnClient(client -> StoryClient.topRightReserved());
		check(reserved > 20, "the mission panel is not shown (or reserves nothing at the top right: " + reserved + ")");
		// Where a card would stand at screens from small to large (GUI pixels), against the panel shown now.
		int[][] sizes = {{320, 240}, {360, 200}, {427, 240}, {480, 270}, {570, 320}, {640, 360}, {854, 480}, {960, 540}};
		String bad = this.context.computeOnClient(client -> {
			StringBuilder out = new StringBuilder();
			Component title = Component.translatable("sabercraft.hint.title");
			Component message = Component.translatable("sabercraft.hint.vent_inside", Component.literal("[W]"), Component.literal("[Rechtsklick]"));
			int bottom = StoryClient.topRightReserved();

			for (int[] size : sizes) {
				int width = size[0];
				int height = size[1];
				int edge = StoryClient.objectivesLeftEdge(width);
				HintCard.Layout card = HintCard.layout(client.font, title, message, width, height, edge, StoryClient.radioBottom(width));

				if (card.overlaps(edge, 0, width, bottom)) {
					out.append(width).append('x').append(height).append(": over the mission panel; ");
				}

				if (card.x() < 0 || card.y() < 0 || card.x() + card.width() > width || card.y() + card.height() > height) {
					out.append(width).append('x').append(height).append(": off the screen; ");
				}

				if (card.message().isEmpty() || card.width() < 100) {
					out.append(width).append('x').append(height).append(": too narrow to read; ");
				}

				SaberCraft.LOGGER.info("PHASE28 HINT CARD at {}x{}: x {} y {} w {} h {} (panel from x {} down to y {})", width, height, card.x(), card.y(), card.width(),
						card.height(), edge, bottom);

				// "Checkpoint saved" (in German, the longer word) with a two-line comlink line shown:
				// clear of the comlink box and of the panel (the comlink box drew over it).
				int radioBottom = 8 + Math.max(32 + 4, 14 + 2 * 10);
				int radioRight = StoryHud.radioRight(width);
				Component saved = Component.literal("Checkpoint gespeichert");
				int[] box = StoryHud.checkpointBox(client.font, saved, width, radioBottom, radioRight, edge, bottom);
				boolean overRadio = box[0] < radioRight && box[1] < radioBottom;
				boolean overPanel = box[2] > edge && box[1] < bottom;

				if (overRadio || overPanel || box[0] < 0 || box[2] > width) {
					out.append(width).append('x').append(height).append(": checkpoint notice ").append(java.util.Arrays.toString(box)).append(overRadio ? " under the comlink line" : "")
							.append(overPanel ? " under the mission panel" : "").append("; ");
				}
			}

			return out.toString();
		});
		check(bad.isEmpty(), "the hint card: " + bad);

		// The mission's title card at its start: on a narrow screen below the panel (a picture); a
		// hint card waits until the title is gone.
		Component title = Component.translatable("sabercraft.hint.title");
		Component message = Component.translatable("sabercraft.hint.vent_inside", Component.literal("[W]"), Component.literal("[Rechtsklick]"));
		this.replay(1, "m2.kestrel");
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> StoryClient.titleShowing()), "no title card at the start of a mission");
		this.context.runOnClient(client -> {
			HintCard.clearForTest();
			HintCard.showForTest(title, message);
		});
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase28_title_and_panel");
		check(this.context.computeOnClient(client -> HintCard.shownForTest() == null && HintCard.pendingForTest() > 0), "a hint card shows over the mission's title card");

		for (int i = 0; i < 160 && this.context.computeOnClient(client -> StoryClient.titleShowing()); i++) {
			this.context.waitTick();
		}

		// A real card, drawn: at real sizes of the window and GUI scales (854×480 at 1 and 2, 640×480 at 2).
		int scale = this.context.computeOnClient(client -> client.options.guiScale().get());
		int[][] windows = {{854, 480, 1}, {854, 480, 2}, {640, 480, 2}};

		for (int[] window : windows) {
			this.context.getInput().resizeWindow(window[0], window[1]);
			this.context.runOnClient(client -> {
				client.options.guiScale().set(window[2]);
				client.resizeGui();
				HintCard.clearForTest();
				HintCard.showForTest(title, message);
			});
			this.context.waitTicks(20);
			String drawn = this.context.computeOnClient(client -> {
				HintCard.Layout card = HintCard.shownForTest();
				int width = client.getWindow().getGuiScaledWidth();
				int height = client.getWindow().getGuiScaledHeight();

				if (card == null) {
					return "not drawn (" + width + "x" + height + ")";
				}

				int edge = StoryClient.objectivesLeftEdge(width);
				return card.overlaps(edge, 0, width, StoryClient.topRightReserved()) ? "over the mission panel (" + width + "x" + height + ")"
						: card.x() + card.width() > width || card.y() + card.height() > height ? "off the screen (" + width + "x" + height + ")" : "";
			});
			String name = window[0] + "x" + window[1] + "_gui" + window[2];
			check(drawn.isEmpty(), "the hint card drawn at " + name + ": " + drawn);
			this.context.takeScreenshot("phase28_hint_card_" + name);
		}

		this.context.getInput().resizeWindow(854, 480);
		this.context.runOnClient(client -> {
			HintCard.clearForTest();
			client.options.guiScale().set(scale);
			client.resizeGui();
		});
		this.context.waitTicks(5);
	}

	// --- 3: the detention block without Hale's trust ---------------------------------------------------------------

	private void refuse() {
		this.enter(4, "m5.route");
		this.unseen();
		// Trusting him (the other answer): his way, the main door opens.
		this.flags("trust.hale", "know.hale_trap");
		String trusting = this.on(s -> StoryTesting.objective(this.session(s)));
		check(!trusting.endsWith("route_hatch") && !trusting.endsWith("route_crawl"), "trusting Hale, the objective names Kestrel's way (" + trusting + ")");
		int doorX = DestroyerInterior.CX - 4;
		int doorZ = DestroyerInterior.DETENTION_DOOR_Z0;
		check(!this.on(s -> Story.locked(this.session(s).destroyer, doorX, H, doorZ)), "trusting Hale, the detention block's door stays shut");
		this.server.runOnServer(s -> this.session(s).unset("trust.hale"));
		// Not trusting him, the intercept read: the objective says the way (Kestrel's hatch, M-31).
		String objective = this.on(s -> StoryTesting.objective(this.session(s)));
		check("sabercraft.story.obj.m5.route_hatch".equals(objective), "without Hale's trust the objective does not name Kestrel's hatch (" + objective + ")");
		// The main door: shut, and the player there is told why.
		this.put(new Vec3(doorX + 1.6, H, doorZ + 1.0), 90.0F, 0.0F);
		check(this.on(s -> Story.locked(this.session(s).destroyer, doorX, H, doorZ)), "without Hale's trust the detention block's door opens");
		check(this.hintSeen("sabercraft.story.hint.detention_main", 20), "at the shut detention door nothing says why (" + this.clientHint() + ")");
		this.context.takeScreenshot("phase28_detention_door");
		// The marker from the hideout: the hatch.
		this.put(new Vec3(20.5, H, -2.5), 90.0F, 10.0F);
		Vec3 marker = this.markerLocal();
		check(marker != null && marker.distanceTo(new Vec3(22.6, H + 0.5, -3.5)) < 0.6, "in the hideout the marker does not lead to Kestrel's hatch (" + marker + ")");
		check(this.on(s -> StoryTesting.storyDroids(this.session(s)).contains(DroidType.MOUSE)), "M-31 is not with the player in Mission 5");
		// M-31 far away (left waiting in the maintenance room): the click says to fetch him.
		this.server.runOnServer(s -> {
			Vec3 far = new Vec3(3.5, H, -5.5).add(Vec3.atLowerCornerOf(this.session(s).destroyer));
			Droids.place(this.me(s), DroidType.MOUSE, far, 0.0F, true);
		});
		this.context.waitTicks(5);

		if (!this.on(s -> Droids.droid(this.me(s), DroidType.MOUSE) != null)) {
			this.problem("M-31 cannot be put in the maintenance room");
			return;
		}
		BlockPos hatch = DestroyerInterior.HIDEOUT_HATCH;
		this.put(new Vec3(21.6, H, -3.5), 270.0F, 38.0F);
		this.hatchClick();
		check(this.hintSeen("sabercraft.story.hint.hatch_m31", 10), "a click on the bolted hatch without M-31 near does not say to fetch him (" + this.clientHint() + ")");
		check(!this.on(s -> this.session(s).has("tmp.m31_unbolt")), "M-31 goes to the hatch from far away");
		// M-31 here: a click sends him through the grille; a moment later the hatch is free.
		this.server.runOnServer(s -> {
			Vec3 near = new Vec3(20.5, H, -2.5).add(Vec3.atLowerCornerOf(this.session(s).destroyer));
			Droids.place(this.me(s), DroidType.MOUSE, near, 90.0F, false);
		});
		this.context.waitTicks(5);
		this.hatchClick();
		check(this.on(s -> this.session(s).has("tmp.m31_unbolt")) || this.on(s -> this.session(s).has("hatch.f")), "a click on the hatch with M-31 near does not send him");
		check(this.hintSeen("sabercraft.story.hint.hatch_m31_go", 10) || this.on(s -> this.session(s).has("hatch.f")), "the click does not say M-31 goes ("
				+ this.clientHint() + ")");
		boolean free = false;

		for (int i = 0; i < 80 && !free; i++) {
			this.context.waitTick();
			free = this.on(s -> this.session(s).has("hatch.f"));
		}

		check(free, "M-31 does not unbolt Kestrel's hatch");
		check(!this.on(s -> Story.locked(this.session(s).destroyer, hatch.getX(), hatch.getY(), hatch.getZ())), "Kestrel's hatch stays bolted after M-31");
		check("sabercraft.story.obj.m5.route_crawl".equals(this.on(s -> StoryTesting.objective(this.session(s)))), "after the hatch the objective does not say to crawl on ("
				+ this.on(s -> StoryTesting.objective(this.session(s))) + ")");
		// A click now opens it.
		this.hatchClick();
		check(this.on(s -> this.level(s).getBlockState(this.world(s, hatch)).isAir()), "the unbolted hatch does not open with a click");
		// In the crawl way: the marker leads on to its end at the detention block (not back to the hatch).
		this.put(new Vec3(24.5, H, 0.5), 0.0F, 10.0F);
		this.context.waitTicks(10);
		check(this.on(s -> this.me(s).getPose()) == Pose.SWIMMING, "in Kestrel's crawl way the player does not crawl");
		Vec3 on = this.markerLocal();
		check(on != null && on.distanceTo(new Vec3(DestroyerInterior.DETENTION_HATCH.getX() + 0.5, H + 0.5, DestroyerInterior.DETENTION_HATCH.getZ() + 0.5)) < 0.8,
				"in Kestrel's crawl way the marker does not lead to its end (" + on + ")");
		// Its end: R2-V7's step (through the crawl way, not Hale's trap).
		this.put(new Vec3(24.5, H, 5.0), 0.0F, 10.0F);
		this.waitStep("m5.r2", 80);
		check(!this.on(s -> this.session(s).has("m5.trapped")), "the crawl way led into Hale's trap");
	}

	/** A right click on a hatch (the crosshair on it); the hint the server gave, if any, comes to the client. */
	private void hatchClick() {
		this.context.waitTicks(4);
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(6);
	}

	// --- 4: Mission 2's side way and its cover ----------------------------------------------------------------------

	private void cover() {
		this.enter(1, "m2.drop");
		this.unseen();
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		BlockPos cover = StoryTesting.coverCell();
		BlockPos box = StoryTesting.toolboxCell();
		check(this.on(s -> this.level(s).getBlockState(this.world(s, cover)).is(Blocks.BARRIER)), "the side way's cover is not shut at the start");
		check(this.on(s -> this.level(s).getBlockState(this.world(s, box)).is(Blocks.BARRIER)), "the toolbox is not there");
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") != null), "the toolbox cannot be used");
		int[] counts = this.on(s -> StoryTesting.cover());

		// From the crawl way of flight control (the way in): screwed tight from the other side.
		this.put(new Vec3(10.5, H, -28.5), 270.0F, 5.0F);
		this.context.waitTicks(6);
		check(this.on(s -> this.me(s).getPose()) == Pose.SWIMMING, "at the cover's back the player does not crawl");
		this.click("vent_cover");
		check(this.hintSeen("sabercraft.story.hint.cover_locked", 10), "from the way in the cover does not say it is screwed on from the other side (" + this.clientHint() + ")");
		check(this.on(s -> StoryTesting.cover())[1] == counts[1], "the close-up opens from the cover's back");
		check(!this.context.computeOnClient(client -> client.gui.screen() instanceof CoverScreen), "the close-up shows from the cover's back");
		this.context.getInput().holdKeyFor(options -> options.keyUp, 20);
		this.context.waitTicks(2);
		check(this.on(s -> this.session(s).local(Story.DESTROYER).x) < cover.getX(), "the player crawls through the shut cover from the west");
		// From the hangar's side, without the screwdriver: four screws, no tool.
		this.put(new Vec3(12.6, H, -28.5), 90.0F, 5.0F);
		this.context.waitTicks(6);
		this.click("vent_cover");
		check(this.hintSeen("sabercraft.story.hint.cover_tool", 10), "without the screwdriver the cover does not say a tool is needed (" + this.clientHint() + ")");
		check(!this.context.computeOnClient(client -> client.gui.screen() instanceof CoverScreen), "the close-up shows without the screwdriver");
		check(!this.on(s -> StoryTesting.unscrewed(this.session(s), this.me(s))), "the cover comes off without the screwdriver");
		this.context.getInput().holdKeyFor(options -> options.keyUp, 20);
		this.context.waitTicks(2);
		check(this.on(s -> this.session(s).local(Story.DESTROYER).x) > cover.getX() + 0.8, "the player crawls through the shut cover from the east");

		// The toolbox by the containers: the screwdriver, taken and kept.
		this.takeDriver();
		check(this.on(s -> this.session(s).has(StoryTesting.driver())), "the screwdriver is not taken from the toolbox");
		check(this.on(s -> StoryTesting.keptNow(this.session(s))).contains(StoryTesting.driver()), "the screwdriver is not kept at once");
		check(this.on(s -> this.session(s).journal.contains("driver")), "the journal does not tell of the screwdriver");
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") == null), "the empty toolbox can still be used");
		this.capture();
		check(this.on(s -> this.session(s).has(StoryTesting.driver())), "caught, the screwdriver is lost");
		this.unseen();

		// The close-up from the hangar's side with it: four screws, top left, top right, bottom right, bottom left.
		this.put(new Vec3(12.6, H, -28.5), 90.0F, 5.0F);
		this.context.waitTicks(6);
		int[] screens = this.context.computeOnClient(client -> CoverScreen.countsForTest());
		this.click("vent_cover");
		boolean open = false;

		for (int i = 0; i < 20 && !open; i++) {
			this.context.waitTick();
			open = this.context.computeOnClient(client -> client.gui.screen() instanceof CoverScreen);
		}

		check(open, "the close-up of the cover does not open with the screwdriver");
		check(this.on(s -> StoryTesting.cover())[1] == counts[1] + 1, "the close-up is not counted on the server");

		if (!open) {
			return;
		}

		// A comlink line waits while the screws are turned (it drew over the close-up's heading).
		this.server.runOnServer(s -> this.session(s).radio("kestrel", "sabercraft.story.radio.caught.m2"));
		this.context.waitTicks(8);
		int[] radio = this.context.computeOnClient(client -> StoryClient.radioAgeForTest());
		this.context.waitTicks(20);
		int[] later = this.context.computeOnClient(client -> StoryClient.radioAgeForTest());
		check(radio[0] == later[0] && (radio[0] >= 0 || later[1] >= 1), "a comlink line runs on while the cover is open (" + java.util.Arrays.toString(radio) + " then "
				+ java.util.Arrays.toString(later) + ")");
		this.context.takeScreenshot("phase28_cover_closeup");
		String[] where = {"top left", "top right", "bottom right", "bottom left"};

		for (int i = 0; i < 4; i++) {
			int[] screw = this.context.computeOnClient(client -> CoverScreen.nextScrewForTest());
			int[] centre = this.context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth() / 2, client.getWindow().getGuiScaledHeight() / 2 + 6});

			if (screw == null) {
				this.problem("no screw marked for the " + where[i] + " one");
				break;
			}

			boolean left = screw[0] < centre[0];
			boolean top = screw[1] < centre[1];
			check(left == (i == 0 || i == 3) && top == (i <= 1), "the screw marked " + (i + 1) + ". is not the " + where[i] + " one");
			int scale = this.context.computeOnClient(client -> client.getWindow().getGuiScale());
			this.context.getInput().setCursorPos((screw[0] + 0.5) * scale, (screw[1] + 0.5) * scale);
			this.context.waitTicks(1);
			this.context.getInput().holdMouse(0);
			this.context.waitTicks(i == 1 ? 8 : 22);

			if (i == 1) {
				// Halfway: the screw turns while the button is held (a picture of it).
				this.context.takeScreenshot("phase28_cover_turning");
				this.context.waitTicks(14);
			}

			this.context.getInput().releaseMouse(0);
			this.context.waitTicks(2);
			int done = this.context.computeOnClient(client -> CoverScreen.screwsForTest());
			check(done == i + 1 || done == -1 && i == 3, "holding the mouse button on the " + where[i] + " screw does not turn it out (" + done + " out)");
		}

		boolean off = false;

		for (int i = 0; i < 40 && !off; i++) {
			this.context.waitTick();
			off = this.on(s -> this.session(s).has(StoryTesting.coverOpen()));
		}

		check(off, "the cover does not come off after the four screws");
		check(this.context.computeOnClient(client -> CoverScreen.countsForTest())[1] == screens[1] + 1, "the close-up does not finish");
		check(!this.context.computeOnClient(client -> client.gui.screen() instanceof CoverScreen), "the close-up stays open after the cover came off");
		check(this.on(s -> StoryTesting.cover())[2] == counts[2] + 1, "the cover's coming off is not counted");
		check(this.on(s -> this.level(s).getBlockState(this.world(s, cover)).isAir()), "the cover's barrier stays after it came off");
		check(this.on(s -> StoryTesting.keptNow(this.session(s))).contains(StoryTesting.coverOpen()), "the cover's coming off is not kept at once");
		this.context.waitTicks(10);
		int[] after = this.context.computeOnClient(client -> StoryClient.radioAgeForTest());
		check(after[0] > 0, "the comlink line that waited does not go on after the close-up (" + java.util.Arrays.toString(after) + ")");
		this.context.takeScreenshot("phase28_cover_off");
		// The way is free: on west into the crawl way of flight control.
		this.context.getInput().holdKeyFor(options -> options.keyUp, 30);
		this.context.waitTicks(2);
		double x = this.on(s -> this.session(s).local(Story.DESTROYER).x);
		check(x < cover.getX() + 0.2, "the side way stays blocked after the cover came off (x " + String.format(Locale.ROOT, "%.2f", x) + ")");
		// Caught again: the cover stays off.
		this.capture();
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		check(this.on(s -> this.session(s).has(StoryTesting.coverOpen())) && this.on(s -> this.level(s).getBlockState(this.world(s, cover)).isAir()),
				"caught after opening it, the cover is shut again");
		this.unseen();
	}

	/** Takes the screwdriver from the toolbox with a right click (and reads its line). */
	private void takeDriver() {
		Vec3 point = this.on(s -> {
			Vec3 world = StoryTesting.point(this.session(s), "toolbox");
			return world == null ? null : world.subtract(Vec3.atLowerCornerOf(this.session(s).destroyer));
		});

		if (point == null) {
			this.problem("nothing to take the screwdriver from");
			return;
		}

		// A free place beside the box (south, east, west, north of it), the eye on it.
		Vec3 stand = null;

		for (Vec3 offset : List.of(new Vec3(0.0, 0.0, 1.4), new Vec3(1.4, 0.0, 0.0), new Vec3(-1.4, 0.0, 0.0), new Vec3(0.0, 0.0, -1.4))) {
			Vec3 at = new Vec3(point.x + offset.x, H, point.z + offset.z);
			boolean free = this.on(s -> {
				Vec3 world = at.add(Vec3.atLowerCornerOf(this.session(s).destroyer));
				return this.level(s).noCollision(new AABB(world.x - 0.3, world.y, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3))
						&& !this.level(s).noCollision(new AABB(world.x - 0.3, world.y - 0.2, world.z - 0.3, world.x + 0.3, world.y - 0.01, world.z + 0.3));
			});

			if (free) {
				stand = at;
				break;
			}
		}

		if (stand == null) {
			this.problem("no place to stand by the toolbox");
			return;
		}

		Vec3 eye = stand.add(0.0, 1.62, 0.0);
		this.put(stand, yaw(eye, point), pitch(eye, point));
		this.click("toolbox");
		this.converse();
		this.context.waitTicks(4);
	}

	/** Phase 28: the loop again from a fresh start of Mission 2, and the toolbox in later missions; the marker in the side way. */
	private void coverAgain() {
		// Mission 2 played again from the menu: its start as it was (the cover shut, the screwdriver in the box).
		this.enter(1, "m2.drop");
		this.flags(StoryTesting.driver(), StoryTesting.coverOpen());
		this.replay(1, "m2.kestrel");
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		check(!this.on(s -> this.session(s).has(StoryTesting.coverOpen())) && this.on(s -> this.level(s).getBlockState(this.world(s, StoryTesting.coverCell())).is(Blocks.BARRIER)),
				"the cover is not shut when Mission 2 is played again");
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") != null), "no screwdriver in the toolbox when Mission 2 is played again");
		// The way back (Kestrel's marker): in the side way with the cover shut: back out of the
		// hatch without the screwdriver, to the cover with it.
		this.enter(1, "m2.return");
		this.unseen();
		this.put(new Vec3(14.5, H, -28.5), 90.0F, 5.0F);
		Vec3 marker = this.markerLocal();
		check(marker != null && Math.abs(marker.x - (DestroyerInterior.HANGAR_HATCH.getX() + 1.0)) < 0.8 && Math.abs(marker.z - -28.5) < 0.8,
				"in the shut side way without the screwdriver the marker does not lead back out (" + marker + ")");
		this.flags(StoryTesting.driver());
		marker = this.markerLocal();
		check(marker != null && Math.abs(marker.x - (StoryTesting.coverCell().getX() + 1.0)) < 0.8 && Math.abs(marker.z - -28.5) < 0.8,
				"in the shut side way with the screwdriver the marker does not lead to the cover (" + marker + ")");
		this.server.runOnServer(s -> this.session(s).unset(StoryTesting.driver()));

		// Twice more round the loop, each from the mission played again: take it, unscrew it (as the
		// close-up's end says), the way free.
		for (int round = 0; round < 2; round++) {
			int r = round;
			this.replay(1, "m2.kestrel");
			this.enter(1, "m2.return");
			this.unseen();
			this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
			check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") != null) && !this.on(s -> this.session(s).has(StoryTesting.coverOpen())),
					"round " + r + ": not as at the mission's start (the screwdriver in its box, the cover shut)");
			this.takeDriver();
			check(this.on(s -> this.session(s).has(StoryTesting.driver())), "round " + r + ": the screwdriver is not taken");
			// (Too far off: the server does not take the client's word for it.)
			this.put(new Vec3(16.5, H, -28.5), 90.0F, 5.0F);
			this.context.waitTicks(4);
			check(!this.on(s -> StoryTesting.unscrewed(this.session(s), this.me(s))), "round " + r + ": the cover comes off from far away");
			this.put(new Vec3(12.6, H, -28.5), 90.0F, 5.0F);
			this.context.waitTicks(4);
			check(this.on(s -> StoryTesting.unscrewed(this.session(s), this.me(s))), "round " + r + ": the cover does not come off");
			this.context.getInput().holdKeyFor(options -> options.keyUp, 30);
			this.context.waitTicks(2);
			check(this.on(s -> this.session(s).local(Story.DESTROYER).x) < StoryTesting.coverCell().getX() + 0.2, "round " + r + ": the way stays blocked");
		}

		// The next mission with the screwdriver never taken: still in its box (no softlock, no hurry).
		this.replay(1, "m2.kestrel");
		this.enter(2, "m3.plan");
		this.unseen();
		check(!this.on(s -> this.session(s).has(StoryTesting.driver())), "Mission 3 begins with a screwdriver never taken");
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") != null), "the screwdriver is not in its toolbox in Mission 3");
		this.flags(StoryTesting.driver());
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") == null), "the toolbox can be used again once the screwdriver is taken");
	}

	/** Plays that mission again from the story's menu (its start as kept), until its first step is there. */
	private void replay(int mission, String first) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.MISSION, "", mission));

		for (int i = 0; i < 200; i++) {
			this.context.waitTick();

			if (first.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				this.context.waitTicks(10);
				return;
			}
		}

		this.problem("playing mission " + (mission + 1) + " again does not begin at " + first + " (at " + this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))) + ")");
	}

	// --- 2: the reactor's lift ------------------------------------------------------------------------------------------

	private void lift() {
		this.enter(5, "m6.reactor");
		this.unseen();
		List<InteriorPlan.Lift> lifts = InteriorPlan.of("star_destroyer").lifts();
		InteriorPlan.Lift up = lifts.get(3);
		InteriorPlan.Lift down = lifts.get(2);
		check(up.slow() && down.slow(), "the reactor's lift goes at once (no delay)");
		check(up.ticks() >= 20 && up.cooldown() >= 40, "the reactor's lift waits too little (" + up.ticks() + " / " + up.cooldown() + ")");
		Vec3 bottom = up.area().getCenter();
		Vec3 a = new Vec3(bottom.x - 0.35, up.area().minY, bottom.z - 0.3);
		Vec3 b = new Vec3(bottom.x + 0.35, up.area().minY, bottom.z + 0.3);
		this.put(a, 0.0F, 0.0F);
		int rides = ShipInteriors.liftRidesForTest();
		// Fighting on it (moving all the time): it does not go.
		for (int i = 0; i < 80; i++) {
			Vec3 to = i % 4 < 2 ? b : a;
			this.server.runOnServer(s -> this.teleport(s, this.session(s), to, 0.0F));
			this.context.waitTick();
		}

		check(ShipInteriors.liftRidesForTest() == rides, "the reactor's lift goes while the player moves on it");
		check(this.on(s -> this.session(s).local(Story.DESTROYER).y) < R + 1.0, "the reactor's lift took the player up while fighting on it");
		// Standing still: it goes after a moment.
		int ticks = 0;

		for (; ticks < 120 && ShipInteriors.liftRidesForTest() == rides; ticks++) {
			this.context.waitTick();
		}

		this.context.waitTicks(4);
		Vec3 at = this.on(s -> this.session(s).local(Story.DESTROYER));
		SaberCraft.LOGGER.info("PHASE28 LIFT: up after standing still {} ticks, at {}", ticks, at);
		check(ShipInteriors.liftRidesForTest() == rides + 1 && Math.abs(at.y - up.arrival().y) < 0.6, "standing still on the reactor's lift, it does not go up (" + at + ")");
		check(ticks >= up.ticks() - 2, "the reactor's lift goes too soon (" + ticks + " ticks)");
		// At once onto the lift down: not again right away.
		Vec3 top = down.area().getCenter();
		this.server.runOnServer(s -> this.teleport(s, this.session(s), new Vec3(top.x, down.area().minY, top.z), 180.0F));
		this.context.waitTicks(down.ticks() + 20);
		check(ShipInteriors.liftRidesForTest() == rides + 1, "the reactor's lift goes again right after a ride");
		int more = 0;

		for (; more < down.cooldown() + down.ticks() + 60 && ShipInteriors.liftRidesForTest() == rides + 1; more++) {
			this.context.waitTick();
		}

		this.context.waitTicks(4);
		at = this.on(s -> this.session(s).local(Story.DESTROYER));
		check(ShipInteriors.liftRidesForTest() == rides + 2 && Math.abs(at.y - down.arrival().y) < 0.6, "after its pause the reactor's lift does not go down (" + at + ")");
		SaberCraft.LOGGER.info("PHASE28 LIFT: down again after {} more ticks", more + down.ticks() + 20);
	}

	// --- 5: the catwalk's technician ------------------------------------------------------------------------------------

	private void catwalk() {
		this.enter(5, "m6.reactor");
		this.unseen();
		String key = StoryTesting.catwalkKey();
		Figure tech = this.on(s -> this.session(s).figure(key));

		if (tech == null) {
			this.problem("no technician on the reactor's catwalk (" + key + ")");
			return;
		}

		Vec3 post = StoryTesting.catwalkPost();
		check(tech.position().distanceTo(post) < 0.6, "the catwalk's technician is not at his post (" + tech.position() + ")");
		check(StoryTesting.hearing(tech) >= 10.0, "the catwalk's technician does not hear further (" + StoryTesting.hearing(tech) + ")");
		// The first time on the gallery's landing: the hint how to get past him.
		this.put(new Vec3(47.0, H, 13.5), 90.0F, 5.0F);
		check(this.hintSeen("sabercraft.story.hint.catwalk_tech", 30), "on the gallery's landing no hint about the technician (" + this.clientHint() + ")");
		this.context.takeScreenshot("phase28_catwalk_tech");
		// His round: the console (facing the bridge), then the readings on the wall (his back to it).
		for (int look : new int[] {1, 0}) {
			int wait = this.on(s -> (int) (StoryTesting.catwalkWatchAt(this.session(s).time(), look, this.session(s).figure(key).id) - this.session(s).time()));
			this.context.waitTicks(Math.max(0, wait) + 6);
			float yaw = this.on(s -> this.session(s).figure(key).yaw);
			check(Math.abs(Mth.wrapDegrees(yaw - StoryTesting.catwalkYaw(look))) < 6.0F, "the catwalk's technician does not hold his look " + look + " (yaw " + yaw + ")");

			if (look == 1) {
				// Turned to the wall: the bridge is behind him.
				this.put(new Vec3(36.5, H, 16.5), 270.0F, 0.0F);
				float behind = this.on(s -> StoryTesting.seesLooking(this.session(s), this.session(s).figure(key), StoryTesting.catwalkYaw(1)));
				float facing = this.on(s -> StoryTesting.seesLooking(this.session(s), this.session(s).figure(key), StoryTesting.catwalkYaw(0)));
				SaberCraft.LOGGER.info("PHASE28 CATWALK: on the bridge he sees the player at the console {}, at the wall {}", facing, behind);
				check(behind == 0.0F, "turned to the wall, the technician still sees the bridge (" + behind + ")");
				this.put(new Vec3(47.0, H, 13.5), 90.0F, 5.0F);
			}
		}

		// M-31's noise at the gallery landing's south-east corner: he goes to see, off his post a while.
		int ways = StoryTesting.lureWays();
		int[] went = this.on(s -> StoryTesting.noise(this.session(s), new Vec3(48.5, H, 16.5)));
		check(went[0] >= 1 && this.on(s -> this.session(s).figure(key).mode != Figure.ROUTINE), "the catwalk's technician does not go to see M-31's noise");
		double first = 0.0;
		double most = 0.0;
		int away = 0;

		for (int i = 0; i < 60; i++) {
			this.context.waitTicks(10);
			double off = this.on(s -> this.session(s).figure(key).position().distanceTo(post));
			most = Math.max(most, off);

			if (i < 10) {
				first = Math.max(first, off);
			}

			if (off > 2.0) {
				away += 10;
			} else if (i > 20 && this.on(s -> this.session(s).figure(key).mode == Figure.ROUTINE)) {
				break;
			}
		}

		SaberCraft.LOGGER.info("PHASE28 CATWALK LURE: off his post {} within 5 s, {} at most, away {} s, lure ways {}", String.format(Locale.ROOT, "%.1f", first),
				String.format(Locale.ROOT, "%.1f", most), away / 20.0, StoryTesting.lureWays() - ways);
		check(first > 2.5, "M-31's noise does not draw the catwalk's technician off his post (" + String.format(Locale.ROOT, "%.1f", first) + ")");
		check(away >= 100, "the lured technician is back at his post too soon (" + away / 20.0 + " s)");
		boolean back = false;

		for (int i = 0; i < 90 && !back; i++) {
			this.context.waitTicks(10);
			back = this.on(s -> this.session(s).figure(key).mode == Figure.ROUTINE && this.session(s).figure(key).position().distanceTo(post) < 0.6);
		}

		check(back, "the catwalk's technician does not come back to his post after the noise");
		float yaw0 = this.on(s -> this.session(s).figure(key).yaw);
		boolean turned = false;

		for (int i = 0; i < 40 && !turned; i++) {
			this.context.waitTicks(10);
			turned = Math.abs(Mth.wrapDegrees(this.on(s -> this.session(s).figure(key).yaw) - yaw0)) > 30.0F;
		}

		check(turned, "back at his post, the catwalk's technician does not take up his round");
	}

	// --- 6: the guard's strain in the duel ----------------------------------------------------------------------------------

	private void strain() {
		if (!this.duel()) {
			return;
		}

		check(AbilityConfig.get().staminaEnabled, "stamina is off in the test's settings");
		check(!this.on(s -> StoryTesting.ringOn(this.session(s).figure("shadow"))), "her ring turns in the first phase");
		this.server.runOnServer(s -> {
			StoryTesting.guard(this.session(s).figure("shadow"), 4000);
			SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax);
			this.before(s, 2.3);
		});
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		boolean guarding = false;

		for (int i = 0; i < 30 && !guarding; i++) {
			this.context.waitTick();
			guarding = this.on(s -> SaberAbilities.isGuarding(this.me(s)));
		}

		check(guarding, "the test cannot raise the guard in the duel");

		if (!guarding) {
			this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			return;
		}

		// (Long enough up that no block is a perfect parry.)
		this.context.waitTicks(10);
		int[] client = this.context.computeOnClient(c -> StoryClient.guardStrainsForTest());
		List<String> outcomes = new ArrayList<>();

		for (int blow = 0; blow < 10 && !outcomes.contains("broken"); blow++) {
			int[] before = this.on(s -> this.counts(this.session(s)));
			this.server.runOnServer(s -> {
				this.before(s, 2.3);
				Figure f = this.session(s).figure("shadow");
				StoryTesting.guard(f, 4000);
				StoryTesting.blow(f, 1, 4);
			});
			int[] after = before;

			for (int i = 0; i < 20 && java.util.Arrays.equals(after, before); i++) {
				this.context.waitTick();
				after = this.on(s -> this.counts(this.session(s)));
			}

			String outcome = after[3] > before[3] ? "broken" : after[2] > before[2] ? "strained" : after[1] > before[1] ? "perfect" : after[0] > before[0] ? "held"
					: after[4] > before[4] ? "hurt" : "nothing";
			outcomes.add(outcome);
			float stamina = this.on(s -> SaberAbilities.stamina(this.me(s)));
			SaberCraft.LOGGER.info("PHASE28 STRAIN: blow {}: {} (stamina {})", blow + 1, outcome, String.format(Locale.ROOT, "%.1f", stamina));

			if (!"broken".equals(outcome) && !this.on(s -> SaberAbilities.isGuarding(this.me(s)))) {
				this.problem("the guard came down by itself during the blows (" + outcomes + ")");
				break;
			}

			this.context.waitTicks(2);
		}

		SaberCraft.LOGGER.info("PHASE28 STRAIN on Normal: {}", outcomes);
		check(!outcomes.isEmpty() && "held".equals(outcomes.get(0)), "a single block does not hold (" + outcomes + ")");
		check(outcomes.contains("strained") && outcomes.indexOf("strained") < Math.max(0, outcomes.indexOf("broken")), "the guard does not shake before it breaks (" + outcomes + ")");
		// (Phase 29: the stricter guard of her fight: holding it costs, blows in a row cost more each; a few blocks still hold.)
		check(outcomes.contains("broken") && outcomes.indexOf("broken") >= 2, "blocking all the time does not break the guard, or breaks it too soon (" + outcomes + ")");
		check(this.on(s -> StoryTesting.guardBroken(this.session(s))), "after the guard broke it can be raised at once");
		check(!this.on(s -> SaberAbilities.isGuarding(this.me(s))), "the broken guard stays up");
		this.context.waitTicks(3);
		int[] seen = this.context.computeOnClient(c -> StoryClient.guardStrainsForTest());
		check(seen[0] > client[0] && seen[1] > client[1], "the client does not feel the guard shake and break (" + seen[0] + "/" + seen[1] + ")");
		this.context.takeScreenshot("phase28_guard_broken");
		// While it is down, her next blow comes through (the key still held).
		int[] before = this.on(s -> this.counts(this.session(s)));
		this.server.runOnServer(s -> {
			this.before(s, 2.3);
			Figure f = this.session(s).figure("shadow");
			StoryTesting.guard(f, 4000);
			StoryTesting.blow(f, 1, 4);
		});
		this.context.waitTicks(10);
		check(this.on(s -> this.counts(this.session(s)))[4] > before[4], "with the guard broken her blow does not come through");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(50);
		check(!this.on(s -> StoryTesting.guardBroken(this.session(s))), "the broken guard does not recover");
		// By the difficulty: how many of her blows a fresh guard holds.
		int[] held = this.on(s -> new int[] {StoryTesting.blocksHeld(this.me(s), StoryDifficulty.EASY, 95.0F), StoryTesting.blocksHeld(this.me(s), StoryDifficulty.NORMAL, 95.0F),
				StoryTesting.blocksHeld(this.me(s), StoryDifficulty.HARD, 95.0F)});
		SaberCraft.LOGGER.info("PHASE28 STRAIN: blocks a fresh guard holds: easy {}, normal {}, hard {}", held[0], held[1], held[2]);
		check(held[0] > held[1] && held[1] > held[2], "the difficulty does not change how long the guard holds (" + held[0] + "/" + held[1] + "/" + held[2] + ")");
		check(held[2] >= 3, "on Hard the guard breaks after too few blocks (" + held[2] + ")");
		this.server.runOnServer(s -> SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax));
	}

	/** Blocks, perfect parries, guards shaking, broken; blows that came through (the duel's counters). */
	private int[] counts(StorySession s) {
		return new int[] {s.counters.getOrDefault("blocked", 0), s.counters.getOrDefault("perfect", 0), s.counters.getOrDefault("guard_strained", 0),
				s.counters.getOrDefault("guard_broken", 0), s.counters.getOrDefault("duel_hurt", 0)};
	}

	// --- 7-11: the duel's second phase, her throws and ring, the droids, the end ----------------------------------------------------

	private void boss() {
		if (!this.duel()) {
			return;
		}

		// The droids wait behind the hangar's door (not in the arena, not in her films' shots).
		this.context.waitTicks(50);
		String droids = this.on(s -> {
			StringBuilder out = new StringBuilder();
			StorySession session = this.session(s);
			Vec3 corner = Vec3.atLowerCornerOf(session.destroyer);

			for (DroidType type : StoryTesting.storyDroids(session)) {
				DroidEntity droid = Droids.droid(this.me(s), type);

				if (droid == null) {
					out.append(type).append(" missing; ");
				} else if (!droid.isStaying() || droid.position().distanceTo(StoryTesting.duelWait(type).add(corner)) > 1.2) {
					out.append(type).append(" at ").append(droid.position().subtract(corner)).append(droid.isStaying() ? " (waiting)" : " (following)").append("; ");
				}
			}

			return out.toString();
		});
		check(droids.isEmpty(), "in the duel the droids do not wait aside: " + droids);
		check(this.on(s -> StoryTesting.storyDroids(this.session(s)).size()) >= 2, "M-31 and R2-V7 are not both in the duel's mission");
		// Half her strength: the blade lock's scene (the ring still), then her second phase.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		check(!this.on(s -> StoryTesting.ringOn(this.session(s).figure("shadow"))), "her ring turns in the blade lock");
		this.film(20);
		this.context.waitTicks(10);
		this.skip("boss_mid");
		check(this.on(s -> StoryTesting.phase(this.session(s).figure("shadow"))) == 2, "the second phase does not begin");
		// Her second phase as it comes (the player stands his ground, guard down): her throws, her ring.
		this.server.runOnServer(s -> this.before(s, 5.0));
		int id = this.on(s -> this.session(s).figure("shadow").id);
		int[] ring0 = this.on(s -> StoryTesting.ringAndGuard());
		List<Integer> throwsAt = new ArrayList<>();
		int samples = 0;
		int onSamples = 0;
		int spinning = 0;
		int spinningSeen = 0;
		int offs = 0;
		boolean was = false;
		int firstOn = -1;
		int thrown = 0;
		Vec3 lastAt = null;
		int standing = 0;
		int longestStand = 0;

		for (int t = 0; t < 1400; t += 10) {
			this.context.waitTicks(10);
			Object[] now = this.on(s -> {
				Figure f = this.session(s).figure("shadow");
				return f == null ? null : new Object[] {StoryTesting.thrown(f), StoryTesting.ringOn(f), this.session(s).has("tmp.duel"), f.position(),
						f.position().distanceTo(this.session(s).local(Story.DESTROYER))};
			});

			if (now == null || !(boolean) now[2]) {
				this.problem("the duel ended by itself while the player only stood there (at " + t / 20 + " s)");
				break;
			}

			// Phase 28: she never stands still for long while the player is out of her reach.
			Vec3 her = (Vec3) now[3];

			if (lastAt != null && her.distanceTo(lastAt) < 0.05 && (double) now[4] > 3.5) {
				standing += 10;
				longestStand = Math.max(longestStand, standing);
			} else {
				standing = 0;
			}

			lastAt = her;

			if ((int) now[0] > thrown) {
				thrown = (int) now[0];
				throwsAt.add(t / 20);
			}

			boolean on = (boolean) now[1];

			if (on && firstOn < 0) {
				firstOn = t;
			}

			if (firstOn >= 0) {
				samples++;
				onSamples += on ? 1 : 0;

				if (on && was) {
					spinning++;
					spinningSeen += this.context.computeOnClient(client -> MeshAudit.ringSpeed(id)) > 0.01F ? 1 : 0;
				}
			}

			if (was && !on) {
				offs++;
			}

			was = on;

			if (t % 40 == 0) {
				double apart = this.on(s -> this.session(s).figure("shadow").position().distanceTo(this.session(s).local(Story.DESTROYER)));
				SaberCraft.LOGGER.info("PHASE28 BOSS at {} s: {}, {} apart, {}", t / 20.0, this.on(s -> StoryTesting.throwState(this.session(s), this.session(s).figure("shadow"))),
						String.format(Locale.ROOT, "%.1f", apart), this.on(s -> StoryTesting.sight(this.session(s), this.session(s).figure("shadow"), this.me(s))));
			}

			// Keep the player near her (she pushes him round), on open floor she can walk to (toward
			// the middle of the hangar), never through a wall.
			if (t % 200 == 190) {
				this.server.runOnServer(s -> {
					StorySession session = this.session(s);
					Figure f = session.figure("shadow");
					Vec3 to = StoryTesting.openSpot(session, f, 5.0, new Vec3(40.5, f.y, -40.5));
					this.teleport(s, session, to, (float) Math.toDegrees(Math.atan2(-(f.x - to.x), f.z - to.z)));
				});
			}
		}

		int[] ring1 = this.on(s -> StoryTesting.ringAndGuard());
		int counted = this.on(s -> this.session(s).counters.getOrDefault("throws", 0));
		SaberCraft.LOGGER.info("PHASE28 BOSS: throws {} at {} s (counted {}); ring first on at {} s, on in {} of {} samples, off {} times, client turning {} of {}; set going {}, stopped {}",
				thrown, throwsAt, counted, firstOn / 20.0, onSamples, samples, offs, spinningSeen, spinning, ring1[0] - ring0[0], ring1[1] - ring0[1]);
		SaberCraft.LOGGER.info("PHASE28 BOSS: stood still at most {} s while the player was out of reach", longestStand / 20.0);
		check(longestStand < 200, "she stands still for " + longestStand / 20 + " s while the player is out of her reach (stuck?)");
		check(thrown >= 1 && thrown <= StoryTesting.throwsMax(), "she throws her staff " + thrown + " times in 70 s of her second phase (once or twice wanted)");
		check(!throwsAt.isEmpty() && throwsAt.get(0) <= 40, "her first throw comes late (" + throwsAt + " s)");
		check(firstOn >= 0, "her ring never turns in the second phase");
		check(samples == 0 || onSamples >= samples * 0.7, "her ring stops too often (on in " + onSamples + " of " + samples + " samples)");
		check(offs <= 3, "her ring keeps switching on and off (" + offs + " times off in 70 s)");
		check(spinning == 0 || spinningSeen >= spinning * 0.9, "the client does not turn her ring while it is on (" + spinningSeen + " of " + spinning + ")");
		this.facing();
		this.context.takeScreenshot("phase28_ring");

		// Phase 28: out of her sight round a corner (she in the passage north of the hangar, the
		// player in the hangar beside its door) she comes the way round, no longer standing at the
		// wall between for the rest of the fight.
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure f = session.figure("shadow");
			f.x = 38.5;
			f.y = H;
			f.z = -6.0;
			this.teleport(s, session, new Vec3(31.0, H, -31.5), 0.0F);
		});
		this.context.waitTicks(2);
		String start = this.on(s -> StoryTesting.sight(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		check(start.startsWith("sees false"), "round the corner she still sees the player at the start (" + start + ")");
		int came = -1;

		for (int t = 0; t < 400 && came < 0; t += 10) {
			this.context.waitTicks(10);
			double apart = this.on(s -> this.session(s).figure("shadow").position().distanceTo(this.session(s).local(Story.DESTROYER)));

			if (apart < 4.5) {
				came = t;
			}
		}

		SaberCraft.LOGGER.info("PHASE28 BOSS ROUND THE CORNER: {}; she came to the player after {} s", start, came < 0 ? "never" : came / 20.0);
		check(came >= 0, "out of her sight round a corner she does not come to the player within 20 s");

		// The end: she is beaten, disarmed (the ring stops), the music stays tense; her saber goes with her.
		this.defeat();

		if (this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("she is not beaten");
			return;
		}

		check(!this.on(s -> {
			Figure f = this.session(s).figure("shadow");
			return f != null && StoryTesting.ringOn(f);
		}), "her ring still turns after she is disarmed");
		check(this.context.computeOnClient(client -> StoryClient.looseHiltForTest()), "the client does not draw her struck saber");
		this.film(40);
		String during = this.musicNow("music.story_tension", 60);
		check("music.story_tension".equals(during), "after her defeat the music is not tense (" + during + ")");
		this.context.waitTicks(20);
		this.skip("duel_end");
		this.waitStep("m6.falcon", 120);
		this.context.waitTicks(10);
		check(!this.context.computeOnClient(client -> StoryClient.looseHiltForTest()), "her saber still lies on the floor after her flight");
		String falcon = this.musicNow("music.story_escape", 120);
		check("music.story_escape".equals(falcon), "on the way to the Falcon the music is not the escape's (" + falcon + ")");
		String back = this.on(s -> {
			StringBuilder out = new StringBuilder();

			for (DroidType type : StoryTesting.storyDroids(this.session(s))) {
				DroidEntity droid = Droids.droid(this.me(s), type);

				if (droid != null && droid.isStaying()) {
					out.append(type).append(' ');
				}
			}

			return out.toString();
		});
		check(back.isEmpty(), "after the duel the droids still wait (" + back + ")");
		this.context.takeScreenshot("phase28_after_duel");
		// Back at the duel (a restart, a death, a load): no saber lying from before.
		this.enter(5, "m6.duel");
		this.context.waitTicks(10);
		check(!this.context.computeOnClient(client -> StoryClient.looseHiltForTest()), "after a restart her saber lies on the floor again");
		check(this.on(s -> StoryTesting.dropped(this.session(s))) == null, "after a restart the server still has her saber lying there");
	}

	/** The duel begun: the story at it, the films and her words skipped, the player with a saber and unhurt. */
	private boolean duel() {
		this.enter(5, "m6.duel");
		// (Both droids with the player, as at this point of the story: the test's file came from other parts.)
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return false;
		}

		this.converse();
		this.context.waitTicks(10);
		return true;
	}

	/** Beats her (strokes into her openings) until the duel is over. */
	private void defeat() {
		this.server.runOnServer(s -> StoryTesting.lastStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		this.context.waitTicks(5);

		for (int i = 0; i < 60 && this.on(s -> this.session(s).has("tmp.duel")); i++) {
			this.server.runOnServer(s -> {
				Figure shadow = this.session(s).figure("shadow");

				if (shadow != null && shadow.hostile && !"lock".equals(StoryTesting.action(shadow))) {
					this.before(s, 2.0);
					StoryTesting.open(shadow);
				}
			});
			this.context.waitTicks(2);
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(8);
		}
	}

	// --- 7: the music to the end: triumph only once the Falcon is away ------------------------------------------------------------

	private void jump() {
		this.enter(5, "m6.jump");
		this.unseen();
		this.converse();
		String before = this.musicNow("music.story_launch", 160);
		check("music.story_launch".equals(before), "aboard the Falcon before the jump the music is not tense (" + before + ")");
		int jumps = com.phiro.sabercraft.interior.InteriorJump.jumpsForTest();
		this.flags("tmp.jump_go");
		String film = this.film(60);
		check("escape".equals(film), "the escape's film does not begin (" + film + ")");
		this.context.waitTicks(20);
		this.skip("escape");
		// Phase 31: the story's jump inside the Falcon stays (only the open world's hyperspace went).
		for (int i = 0; i < 20 && com.phiro.sabercraft.interior.InteriorJump.jumpsForTest() == jumps; i++) {
			this.context.waitTick();
		}

		check(com.phiro.sabercraft.interior.InteriorJump.jumpsForTest() > jumps, "the Falcon does not jump to hyperspace after the escape");
		this.context.waitTicks(3);
		BlockPos falcon = this.on(s -> {
			StorySession session = this.session(s);
			return session == null ? null : session.falcon;
		});
		check(falcon != null && this.on(s -> com.phiro.sabercraft.interior.InteriorJump.running(falcon)), "the Falcon's jump is not running");
		check(falcon != null && this.context.computeOnClient(client -> com.phiro.sabercraft.client.interior.InteriorClient.jump(falcon, client.level) != null),
				"the client does not show the Falcon's jump");
		String away = this.musicNow("music.story_triumph", 100);
		check("music.story_triumph".equals(away), "once the Falcon is away the music is not triumphant (" + away + ")");
	}

	// --- Helpers ------------------------------------------------------------------------------------------------------------

	/** Waits (that many ticks at most) for the music the client plays to become that piece; returns what plays then. */
	private @Nullable String musicNow(String piece, int ticks) {
		String playing = null;

		for (int i = 0; i <= ticks; i += 5) {
			playing = this.context.computeOnClient(client -> StoryMusic.playing());

			if (piece.equals(playing)) {
				return playing;
			}

			this.context.waitTicks(5);
		}

		return playing;
	}

	/** Whether the client shows that story hint over the hotbar within that many ticks. */
	private boolean hintSeen(String key, int ticks) {
		for (int i = 0; i <= ticks; i++) {
			if (key.equals(this.clientHint())) {
				return true;
			}

			this.context.waitTick();
		}

		return false;
	}

	private @Nullable String clientHint() {
		return this.context.computeOnClient(client -> StoryClient.hintForTest());
	}

	/** A right click on a thing of the story (the crosshair on it); the use is sent itself if the click found nothing (a problem). */
	private void click(String id) {
		this.context.waitTicks(4);
		int used = this.context.computeOnClient(client -> StoryInput.usedForTest());
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(6);

		if (this.context.computeOnClient(client -> StoryInput.usedForTest()) == used) {
			String target = this.context.computeOnClient(client -> {
				StoryInput.Target t = StoryInput.target(client, 1.0F);
				return t == null ? "nothing" : t.id() + "/" + t.figure();
			});
			SaberCraft.LOGGER.warn("PHASE28: click on {} failed: {}", id, this.context.computeOnClient(StoryInput::explainForTest));
			this.problem("a right click on " + id + " used nothing (the crosshair found " + target + ")");
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, id, 0));
			this.context.waitTicks(6);
		}
	}

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static float pitch(Vec3 from, Vec3 to) {
		double flat = Math.hypot(to.x - from.x, to.z - from.z);
		return (float) -Math.toDegrees(Math.atan2(to.y - from.y, flat));
	}

	/** Caught: back at the last checkpoint. */
	private void capture() {
		int restores = this.on(s -> StoryTesting.restores(this.session(s)));
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));

		for (int i = 0; i < 100 && this.on(s -> StoryTesting.restores(this.session(s))) == restores; i++) {
			this.context.waitTick();
		}

		// (Until the screen has faded in again.)
		this.context.waitTicks(50);
	}

	/** Answers every line of the open dialog (the first answer) until it ends. */
	private void converse() {
		for (int i = 0; i < 80; i++) {
			if (this.on(s -> this.session(s) == null || StoryTesting.line(this.session(s)).isEmpty())) {
				return;
			}

			int answers = this.on(s -> StoryTesting.answers(this.session(s)).size());
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.ANSWER, "", answers > 0 ? 0 : -1));
			this.context.waitTicks(3);
		}

		this.problem("a dialog does not end: " + this.on(s -> StoryTesting.line(this.session(s))));
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

	/** Skips that film as the player does (the key: the server is told, the client stops it). */
	private void skip(String film) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
	}

	/** Skips every film the story plays until the duel begins, at most that many ticks. */
	private void skipFilms(int ticks) {
		for (int i = 0; i < ticks / 5; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
				this.context.runOnClient(client -> StoryCinema.skipForTest());
			} else if (this.on(s -> this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
	}

	/** Puts the player that far in front of her, facing her. */
	private void before(MinecraftServer s, double distance) {
		StorySession session = this.session(s);
		Figure f = session.figure("shadow");
		Vec3 her = f.position();
		Vec3 me = session.local(Story.DESTROYER);
		Vec3 away = new Vec3(me.x - her.x, 0.0, me.z - her.z);
		away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 to = her.add(away.scale(distance));
		this.teleport(s, session, to, (float) Math.toDegrees(Math.atan2(away.x, -away.z)));
	}

	/** Turns the player to her (for the pictures). */
	private void facing() {
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure f = session.figure("shadow");

			if (f == null) {
				return;
			}

			Vec3 me = session.local(Story.DESTROYER);
			float yaw = (float) Math.toDegrees(Math.atan2(-(f.x - me.x), f.z - me.z));
			this.teleport(s, session, me, yaw);
		});
		this.context.waitTicks(2);
	}

	/** Where the marker points now (plot space of the Star Destroyer), or null. */
	private @Nullable Vec3 markerLocal() {
		this.context.waitTicks(4);
		return this.on(s -> {
			StorySession session = this.session(s);
			Vec3 marker = StoryTesting.marker(session);
			return marker == null ? null : marker.subtract(Vec3.atLowerCornerOf(session.destroyer));
		});
	}

	/** Continues the story at that step (a file saved there), as soon as it is there. */
	private void enter(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));

		for (int i = 0; i < 160; i++) {
			this.context.waitTick();

			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				this.context.waitTicks(2);
				return;
			}
		}

		throw new AssertionError("the story does not continue at " + step);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(30);
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

	/** Nobody sees the player (the test goes where it likes). */
	private void unseen() {
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 100000));
	}

	private void flags(String... flags) {
		this.server.runOnServer(s -> {
			for (String flag : flags) {
				this.session(s).set(flag);
			}
		});
	}

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Puts the player there at once, on the server (plot space). */
	private void teleport(MinecraftServer server, StorySession session, Vec3 local, float yaw) {
		ServerPlayer player = this.me(server);
		Vec3 world = local.add(Vec3.atLowerCornerOf(session.destroyer));
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(), yaw, 0.0F, true);
	}

	private BlockPos world(MinecraftServer s, BlockPos local) {
		return this.session(s).destroyer.offset(local);
	}

	private ServerLevel level(MinecraftServer s) {
		return s.getLevel(ShipInteriors.DIMENSION);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE28 CHECK FAILED: {}", problem);
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
