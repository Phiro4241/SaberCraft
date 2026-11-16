package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 20, the parts of the story's overhaul that are looked at on their own (the story played
 * through is {@link StoryGameTest}):
 *
 * <ul>
 * <li>The film of the Falcon's arrival at the Tenebris: each shot's picture ({@code phase20_film_*}),
 * the camera out in the film's scene, the HUD hidden, back to the player at the end.</li>
 * </ul>
 */
public class Phase20GameTest implements FabricClientGameTest {
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
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
			context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
			context.waitTicks(60);
			check(this.on(s -> session(s) != null && "m1.briefing".equals(StoryTesting.step(session(s)))), "the story does not start at the briefing");

			this.film();

			context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
			context.waitTicks(40);
		} finally {
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.AUTO));
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE20 PROBLEM: {}", problem));
			throw new AssertionError("Phase 20: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	// --- The film --------------------------------------------------------------------------------

	/** The film of the arrival: two pictures of each shot, and the camera's way back. */
	private void film() {
		ClientGameTestContext context = this.context;
		context.runOnClient(client -> StoryCinema.startForTest("arrival"));
		context.waitTicks(2);
		check(context.computeOnClient(client -> StoryCinema.active()), "the film does not start");
		check(context.computeOnClient(client -> client.gui.hud.isHidden()), "the HUD is shown during the film");
		double away = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(client.player.position()));
		check(away > 300.0, "the film's camera is not out in its scene (" + Math.round(away) + " blocks from the player)");
		int[] moments = {3, 30, 80, 140, 180, 225, 260, 300, 340, 372, 405, 440};

		for (int moment : moments) {
			context.runOnClient(client -> StoryCinema.seekForTest(moment));
			context.waitTicks(1);
			context.takeScreenshot(String.format(java.util.Locale.ROOT, "phase20_film_%03d", moment));
		}

		int drawn = context.computeOnClient(client -> ArrivalFilm.drawnForTest());
		check(drawn >= moments.length, "the film's scene is not drawn (" + drawn + " frames)");
		context.runOnClient(client -> StoryCinema.seekForTest(ArrivalFilm.LENGTH - 2));
		context.waitTicks(6);
		check(!context.computeOnClient(client -> StoryCinema.active()), "the film does not end");
		check(!context.computeOnClient(client -> client.gui.hud.isHidden()), "the HUD stays hidden after the film");
		double back = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()));
		check(back < 1.0, "the camera does not come back to the player after the film (" + back + ")");
		SaberCraft.LOGGER.info("PHASE20: film of the arrival: {} frames drawn", drawn);
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private void check(boolean ok, String problem) {
		if (!ok) {
			SaberCraft.LOGGER.error("PHASE20 CHECK FAILED: {}", problem);
			this.problems.add(problem);
		}
	}

	private <T> T on(java.util.function.Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}

	private StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}
}
