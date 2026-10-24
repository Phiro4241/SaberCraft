package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.saber.HolsterController;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Checks the ignite and retract animation and the sound handling in a real client:
 *
 * <ul>
 *   <li>selecting the sword ignites the blade over several ticks, it does not pop in,</li>
 *   <li>selecting another item retracts the blade first, then switches the slot,</li>
 *   <li>the hum starts once, keeps running while the blade is lit and is gone afterwards,</li>
 *   <li>switching between two sabers keeps the blade lit, selecting it again ignites again,</li>
 *   <li>dying retracts the blade, respawning ignites it again,</li>
 *   <li>other entities (a zombie) get the same animation from the normal equipment sync,</li>
 *   <li>the same works over a real network connection to a dedicated server.</li>
 * </ul>
 *
 * <p>Screenshots of the half-extended blade land in {@code build/run/clientGameTest/screenshots}.
 */
public class SaberAnimationGameTest implements FabricClientGameTest {
	private static final int IGNITE_DELAY = SaberAnimations.DEFAULT.igniteDelay();
	private static final int IGNITE_TICKS = SaberAnimations.DEFAULT.igniteTicks();
	private static final int RETRACT_TICKS = SaberAnimations.DEFAULT.retractTicks();

	private final SoundLog sounds = new SoundLog();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			client.getSoundManager().addListener(this.sounds);
		});

		boolean soundEngine = context.computeOnClient(client -> client.getSoundManager().getAvailableSounds().size() > 0);
		SaberCraft.LOGGER.info("Sound events known to the sound manager: {}", soundEngine);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule keep_inventory true");
			server.runCommand("time set day");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:apple");
			server.runCommand("item replace entity @a hotbar.2 with minecraft:netherite_sword");
			context.getInput().lookAt(20.0F, 10.0F);

			this.igniteAndRetract(context);
			this.swings(context);
			this.switchBetweenSabers(context);
			this.filmstrip(context, CameraType.FIRST_PERSON, "first_person");
			this.filmstrip(context, CameraType.THIRD_PERSON_FRONT, "third_person");
			this.deathAndRespawn(context, server);
			this.otherEntities(context, server);
		}

		this.dedicatedServer(context);
		context.runOnClient(client -> client.getSoundManager().removeListener(this.sounds));
	}

	private void igniteAndRetract(ClientGameTestContext context) {
		select(context, 1);
		context.waitTicks(3);
		check(context, mainHand(context) == null, "no blade state while holding an apple");

		this.sounds.clear();
		select(context, 0);
		context.waitTicks(IGNITE_DELAY - 1);
		check(context, extension(context) == 0.0F, "the hilt comes up first, the blade is still in");
		check(context, this.sounds.count("saber.ignite") == 0, "no ignite sound before the ignition");
		context.waitTick();
		float first = extension(context);
		check(context, first > 0.0F && first < 0.8F, "blade starts to come out, not at once: " + first);
		context.waitTick();
		float second = extension(context);
		check(context, second > first && second < 1.0F, "blade keeps growing: " + first + " -> " + second);

		context.waitTicks(IGNITE_TICKS);
		check(context, extension(context) == 1.0F, "blade fully out after the ignite time");
		check(context, this.sounds.count("saber.ignite") == 1, "ignite sound played once: " + this.sounds);
		int hums = this.sounds.count("saber.hum");
		check(context, hums <= 1, "hum started at most once: " + this.sounds);
		context.waitTicks(40);
		check(context, this.sounds.count("saber.hum") == hums, "hum is not restarted while lit: " + this.sounds);
		check(context, context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) == hums, "one hum per lit blade");

		// Put the saber away: the slot change waits until the blade is in the hilt.
		select(context, 1);
		context.waitTick();
		check(context, selectedSlot(context) == 0, "slot change is held back while the blade retracts");
		check(context, context.computeOnClient(client -> HolsterController.INSTANCE.pendingSlot()) == 1, "slot 1 is pending");
		context.waitTick();
		float retracting = extension(context);
		check(context, retracting > 0.0F && retracting < 1.0F, "blade is retracting: " + retracting);

		context.waitTicks(RETRACT_TICKS + 2);
		check(context, selectedSlot(context) == 1, "slot switched after the blade was in");
		check(context, mainHand(context) == null, "blade state removed");
		check(context, this.sounds.count("saber.retract") == 1, "retract sound played once: " + this.sounds);
		check(context, context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) == 0, "no hum left");
		this.assertNoHumPlaying(context);

		// Selecting it again ignites again.
		select(context, 0);
		context.waitTicks(IGNITE_DELAY);
		float again = extension(context);
		check(context, again > 0.0F && again < 1.0F, "re-selecting ignites again: " + again);
		context.waitTicks(IGNITE_TICKS + 1);
		check(context, extension(context) == 1.0F, "blade lit again");
	}

	private void swings(ClientGameTestContext context) {
		this.sounds.clear();
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		// Since Phase 6 the swing sound comes at the strike of the attack move, a few ticks later.
		context.waitTicks(5);
		check(context, this.sounds.count("saber.swing") == 1, "an arm swing plays one swing sound: " + this.sounds);

		// Turning fast with a lit blade also sounds like a swing.
		context.waitTicks(10);
		context.getInput().lookAt(110.0F, 10.0F);
		context.waitTicks(2);
		check(context, this.sounds.count("saber.swing") == 2, "a fast turn plays a swing sound: " + this.sounds);
		context.getInput().lookAt(20.0F, 10.0F);
		context.waitTicks(10);
		check(context, this.sounds.count("saber.hum") == 0, "swinging does not restart the hum: " + this.sounds);
	}

	/**
	 * Screenshots taken one after another while the blade comes out and goes back in. Taking a
	 * screenshot lets the game run on a little, so the file names carry the game ticks since the
	 * slot was changed.
	 */
	private void filmstrip(ClientGameTestContext context, CameraType camera, String name) {
		context.runOnClient(client -> client.options.setCameraType(camera));
		select(context, 1);
		context.waitTicks(15);
		this.frames(context, "ignite_" + name, IGNITE_DELAY + IGNITE_TICKS + 2, () -> select(context, 0));
		context.waitTicks(20);
		this.frames(context, "retract_" + name, RETRACT_TICKS + 6, () -> select(context, 1));
		select(context, 0);
		context.waitTicks(IGNITE_DELAY + IGNITE_TICKS + 5);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void frames(ClientGameTestContext context, String name, int ticks, Runnable start) {
		long startTime = context.computeOnClient(client -> client.level.getGameTime());
		start.run();
		long elapsed = 0;

		while (elapsed < ticks) {
			context.waitTick();
			elapsed = context.computeOnClient(client -> client.level.getGameTime()) - startTime;
			SaberCraft.LOGGER.info("{} after {} ticks: extension {}, slot {}", name, elapsed, extension(context), selectedSlot(context));
			context.takeScreenshot(name + "_t" + elapsed);
		}
	}

	private void switchBetweenSabers(ClientGameTestContext context) {
		this.sounds.clear();
		select(context, 2);
		context.waitTicks(2);
		check(context, selectedSlot(context) == 2, "switching to another saber is not delayed");
		check(context, extension(context) == 1.0F, "blade stays lit when switching saber to saber");
		check(context, this.sounds.count("saber.retract") == 0, "no retract sound saber to saber");
		select(context, 0);
		context.waitTicks(2);
	}

	private void deathAndRespawn(ClientGameTestContext context, TestServerContext server) {
		this.sounds.clear();
		server.runCommand("kill @a");
		context.waitTicks(2);
		check(context, context.computeOnClient(client -> !client.player.isAlive()), "player is dead");
		check(context, context.computeOnClient(client -> {
			SaberHandState state = SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND);
			return state == null || !state.isActive();
		}), "blade retracts on death");
		context.waitTicks(RETRACT_TICKS + 2);
		check(context, context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) == 0, "hum stops on death");

		// The respawned player keeps its entity id but is a new object.
		LivingEntity deadPlayer = context.computeOnClient(client -> client.player);
		context.runOnClient(client -> client.player.respawn());
		context.waitFor(client -> client.player != null && client.player != deadPlayer && client.player.isAlive(), 200);
		context.setScreen(() -> null);
		context.waitTicks(IGNITE_DELAY + IGNITE_TICKS + 2);
		check(context, extension(context) == 1.0F, "blade ignites again after respawn");
		check(context, context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) <= 1, "only one hum after respawn");
		this.assertSingleHumPlaying(context);
	}

	private void otherEntities(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon minecraft:zombie ~2 ~ ~2 {NoAI:1b,PersistenceRequired:1b,equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1}}}");
		context.waitTicks(5);
		float zombie = zombieExtension(context);
		check(context, zombie == 1.0F, "a zombie that comes into view already has its blade out: " + zombie);

		server.runCommand("item replace entity @e[type=minecraft:zombie] weapon.mainhand with minecraft:air");
		context.waitTicks(2);
		float retracting = zombieExtension(context);
		check(context, retracting < 1.0F, "zombie blade retracts when the sword is taken away: " + retracting);
		context.waitTicks(RETRACT_TICKS + 2);
		check(context, zombieExtension(context) == 0.0F, "zombie blade gone");

		server.runCommand("item replace entity @e[type=minecraft:zombie] weapon.mainhand with minecraft:netherite_sword");
		context.waitTicks(IGNITE_DELAY + 1);
		float igniting = zombieExtension(context);
		check(context, igniting > 0.0F && igniting < 1.0F, "zombie blade ignites when it gets a sword: " + igniting);
		server.runCommand("kill @e[type=minecraft:zombie]");
		context.waitTicks(30);
	}

	private void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
				server.runCommand("item replace entity @a hotbar.1 with minecraft:apple");
				select(context, 1);
				context.waitTicks(5);

				select(context, 0);
				connection.waitForServerboundPackets();
				context.waitTicks(IGNITE_DELAY);
				float igniting = extension(context);
				check(context, igniting > 0.0F && igniting < 1.0F, "ignites on a dedicated server: " + igniting);
				context.waitTicks(IGNITE_TICKS + 2);
				check(context, extension(context) == 1.0F, "lit on a dedicated server");

				select(context, 1);
				context.waitTicks(3);
				connection.waitForServerboundPackets();
				check(context, server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()) == 0,
						"server still sees the saber while it retracts");
				context.waitTicks(RETRACT_TICKS + 2);
				connection.waitForServerboundPackets();
				check(context, server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()) == 1,
						"server gets the new slot after the blade is in");
				check(context, context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) == 0, "no hum left on the server");
				takeScreenshot(context, CameraType.FIRST_PERSON, "saber_dedicated_server_put_away");
			}
		}
	}

	private void assertNoHumPlaying(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (SoundInstance hum : this.sounds.hums) {
				if (client.getSoundManager().isActive(hum)) {
					throw new AssertionError("Hum still playing after the blade was put away");
				}
			}
		});
	}

	private void assertSingleHumPlaying(ClientGameTestContext context) {
		context.runOnClient(client -> {
			int playing = 0;

			for (SoundInstance hum : this.sounds.hums) {
				if (client.getSoundManager().isActive(hum)) {
					playing++;
				}
			}

			if (playing > 1) {
				throw new AssertionError(playing + " hums playing at once");
			}
		});
	}

	private static SaberHandState mainHand(ClientGameTestContext context) {
		return context.computeOnClient(client -> SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND));
	}

	private static float extension(ClientGameTestContext context) {
		return context.computeOnClient(client -> SaberTracker.INSTANCE.renderState(client.player, InteractionHand.MAIN_HAND, 1.0F).extension());
	}

	private static float zombieExtension(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity instanceof Zombie zombie) {
					return SaberTracker.INSTANCE.renderState(zombie, InteractionHand.MAIN_HAND, 1.0F).extension();
				}
			}

			throw new AssertionError("No zombie on the client");
		});
	}

	private static int selectedSlot(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
	}

	/** Same as scrolling the mouse wheel to the slot. */
	private static void select(ClientGameTestContext context, int slot) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
	}

	private static void takeScreenshot(ClientGameTestContext context, CameraType camera, String name) {
		context.runOnClient(client -> client.options.setCameraType(camera));
		context.takeScreenshot(name);
	}

	private static void check(ClientGameTestContext context, boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}

	/** Records which SaberCraft sounds the sound engine was asked to play. */
	private static final class SoundLog implements SoundEventListener {
		private final List<String> played = new ArrayList<>();
		private final List<SoundInstance> hums = new ArrayList<>();

		@Override
		public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
			if (!sound.getIdentifier().getNamespace().equals(SaberCraft.MOD_ID)) {
				return;
			}

			this.played.add(sound.getIdentifier().getPath());

			if (sound.getIdentifier().getPath().equals("saber.hum")) {
				this.hums.add(sound);
			}
		}

		int count(String path) {
			return (int) this.played.stream().filter(path::equals).count();
		}

		void clear() {
			this.played.clear();
		}

		@Override
		public String toString() {
			return this.played.toString();
		}
	}
}
