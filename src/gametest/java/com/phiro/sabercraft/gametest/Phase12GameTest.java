package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.droid.DroidClient;
import com.phiro.sabercraft.client.droid.DroidControl;
import com.phiro.sabercraft.client.menu.SaberConvertScreen;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.saber.SaberProfiles;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.client.sound.SaberHumSoundInstance;
import com.phiro.sabercraft.client.sound.SoundVolumes;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.saber.SaberSwords;
import com.phiro.sabercraft.saber.SaberVariant;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 12, the improvements before release 0.1.0.
 *
 * <p>Sabers: only chosen swords are lightsabers. A plain sword (netherite too) has no blade and no
 * hum; the Saber tab offers to make it a lightsaber, the customize screen turns it back into exactly
 * the sword from before. A sword kind left out of {@code saber_swords} is refused, and the server
 * option {@code netherite_swords_are_sabers} brings back the old rule.
 *
 * <p>Sound: the volume factors for sabers, the hum, droids and ships, and the hum growing quieter
 * while the blade is held still.
 *
 * <p>Droids: looking at one of your droids and pressing its key (N) runs its ability, U lets it wait
 * or follow; nothing happens when no droid or a stranger's entity is looked at, with a screen open,
 * or from too far away. A droid never speaks twice right after each other.
 *
 * <p>Ships: the TIE Fighter fires both cannons at once with green bolts, ships take damage from
 * others but not from their pilot, mend over time and explode when destroyed; the owner then waits
 * before calling a new one. The TIE Interceptor can be called too.
 *
 * <p>Screenshots: {@code phase12_*}.
 */
public class Phase12GameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-300, -64, -300, 300, 320, 300);
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:red\"]}]";

	private final SoundLog sounds = new SoundLog();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(5);
			client.getSoundManager().addListener(this.sounds);
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			server.runCommand("clear @a");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			plainSwords(context, server, self);
			chooseInMenu(context, server, self);
			swordList(context, server, self);
			netheriteOption(context, server, self);
			volumes(context);
			humIdle(context, server);
			droidKeys(context, server, self);
			droidVoices(context, server, self);
			tieFighter(context, server, self);
			damage(context, server, self);
			interceptor(context, server, self);
		} finally {
			context.runOnClient(client -> client.getSoundManager().removeListener(this.sounds));
		}

		dedicatedServer(context);
	}

	// Sabers.

	/** Every plain sword stays a normal sword: no saber, no blade. */
	private static void plainSwords(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_sword");
		select(context, 0);
		context.waitTicks(30);
		check(!server.computeOnServer(s -> SaberVariant.canHold(me(s, self).getMainHandItem())), "a plain netherite sword is no saber on the server");
		check(context.computeOnClient(client -> !SaberProfiles.isSaber(client.player.getMainHandItem())), "nor on the client");
		check(extension(context) == 0.0F, "a plain netherite sword has no blade");
		check(context.computeOnClient(client -> SaberTracker.INSTANCE.activeHums()) == 0, "and does not hum");
		context.takeScreenshot("phase12_plain_netherite");
		select(context, 1);
		context.waitTicks(20);
		check(extension(context) == 0.0F, "a plain iron sword has no blade either");
	}

	/** The Saber tab makes the held sword a saber, and the customize screen makes it plain again. */
	private static void chooseInMenu(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_sword[minecraft:damage=11,minecraft:custom_name=\"Klinge\"]");
		context.waitTicks(5);
		ItemStack original = server.computeOnServer(s -> me(s, self).getMainHandItem().copy());
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SABER));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gui.screen() instanceof SaberConvertScreen), "with a plain sword the Saber tab offers to choose it");
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("phase12_convert_screen");
		context.clickScreenButton("sabercraft.menu.convert.button");
		context.waitTicks(10);
		ItemStack saber = server.computeOnServer(s -> me(s, self).getMainHandItem().copy());
		check(SaberVariant.canHold(saber) && saber.is(Items.IRON_SWORD), "the button makes the iron sword a lightsaber");
		check(saber.getDamageValue() == 11 && saber.getHoverName().getString().equals("Klinge"), "with its wear and name");
		check(context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen), "and goes on to hilt and color");
		context.setScreen(() -> null);
		context.waitTicks(40);
		check(extension(context) == 1.0F, "the chosen sword has its blade");
		context.takeScreenshot("phase12_chosen_saber");

		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SABER));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen), "with a saber the Saber tab customizes it");
		context.clickScreenButton("sabercraft.customize.revert");
		context.waitTicks(10);
		ItemStack back = server.computeOnServer(s -> me(s, self).getMainHandItem().copy());
		check(ItemStack.matches(back, original), "\"Normal sword\" gives back exactly the sword from before (" + back.getComponentsPatch() + ")");
		context.setScreen(() -> null);
		context.waitTicks(20);
		check(extension(context) == 0.0F, "and the blade is gone");
	}

	/** A kind of sword left out of saber_swords cannot be chosen. */
	private static void swordList(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> AbilityConfig.get().saberSwords = new ArrayList<>(List.of("minecraft:netherite_sword")));
		String message = server.computeOnServer(s -> SaberSwords.convert(me(s, self), InteractionHand.MAIN_HAND, true).getString());
		check(!server.computeOnServer(s -> SaberVariant.canHold(me(s, self).getMainHandItem())), "an iron sword is refused when only netherite is allowed (" + message + ")");
		select(context, 0);
		context.waitTicks(5);
		server.computeOnServer(s -> SaberSwords.convert(me(s, self), InteractionHand.MAIN_HAND, true));
		check(server.computeOnServer(s -> SaberVariant.canHold(me(s, self).getMainHandItem())), "the allowed netherite sword can be chosen");
		server.computeOnServer(s -> SaberSwords.convert(me(s, self), InteractionHand.MAIN_HAND, false));
		check(server.computeOnServer(s -> !SaberVariant.canHold(me(s, self).getMainHandItem())), "and made plain again");
		server.runOnServer(s -> AbilityConfig.get().saberSwords = new ArrayList<>(AbilityConfig.DEFAULT_SABER_SWORDS));
	}

	/** netherite_swords_are_sabers: held netherite swords become sabers by themselves, unless turned back. */
	private static void netheriteOption(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
		select(context, 0);
		server.runOnServer(s -> AbilityConfig.get().netheriteSwordsAreSabers = true);
		context.waitTicks(25);
		check(server.computeOnServer(s -> SaberVariant.canHold(me(s, self).getMainHandItem())), "with the option a held netherite sword becomes a saber");
		server.computeOnServer(s -> SaberSwords.convert(me(s, self), InteractionHand.MAIN_HAND, false));
		context.waitTicks(25);
		check(server.computeOnServer(s -> !SaberVariant.canHold(me(s, self).getMainHandItem())), "one turned back by hand stays a plain sword");
		server.runOnServer(s -> AbilityConfig.get().netheriteSwordsAreSabers = false);
	}

	// Sound.

	private static void volumes(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ImmersionConfig config = ImmersionConfig.get();
			float saber = config.saberVolume;
			float hum = config.saberHumVolume;
			float droid = config.droidVolume;
			boolean chatter = config.droidChatter;
			float ship = config.shipVolume;

			try {
				config.saberVolume = 0.5F;
				config.saberHumVolume = 0.4F;
				config.droidVolume = 0.3F;
				config.droidChatter = false;
				config.shipVolume = 0.7F;
				check(SoundVolumes.scale("saber.swing") == 0.5F, "the saber slider scales a swing");
				check(Math.abs(SoundVolumes.scale("saber.dark.hum") - 0.2F) < 1e-6, "the hum is scaled by both saber sliders");
				check(SoundVolumes.scale("force.push") == 0.5F, "Force powers follow the saber slider");
				check(SoundVolumes.scale("droid.r2d2.ability") == 0.3F, "the droid slider scales an ability");
				check(SoundVolumes.scale("droid.r2d2.ambient") == 0.0F, "no chatter when it is switched off");
				check(SoundVolumes.scale("ship.tie_fighter.laser") == 0.7F, "the ship slider scales a TIE laser");
				check(SoundVolumes.scale("music.calm") == 1.0F, "music keeps its own sliders");
			} finally {
				config.saberVolume = saber;
				config.saberHumVolume = hum;
				config.droidVolume = droid;
				config.droidChatter = chatter;
				config.shipVolume = ship;
			}

			check(ImmersionConfig.get().saberHumVolume < 1.0F, "the hum is quieter than the other saber sounds by default");
		});
	}

	/** Held still, the hum sinks to half its volume; moving brings it back. */
	private void humIdle(ClientGameTestContext context, TestServerContext server) {
		this.sounds.clear();
		server.runCommand("item replace entity @a hotbar.2 with " + SABER);
		select(context, 2);
		context.waitTicks(40);
		check(this.sounds.hum != null, "the saber hums");
		context.waitTicks(130);
		float still = context.computeOnClient(client -> this.sounds.hum.idleFactor());
		check(still < 0.65F, "held still for a few seconds, the hum gets quieter (" + still + ")");

		for (int i = 0; i < 10; i++) {
			context.getInput().lookAt(180.0F + (i % 2 == 0 ? 40.0F : -40.0F), 0.0F);
			context.waitTick();
		}

		float moving = context.computeOnClient(client -> this.sounds.hum.idleFactor());
		check(moving > 0.9F, "a swing brings the full hum back (" + moving + ")");
		context.getInput().lookAt(180.0F, 0.0F);
		select(context, 1);
		context.waitTicks(20);
	}

	// Droids.

	private static void droidKeys(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitFor(client -> DroidClient.status() != null, 40);
		server.runCommand("tp @a 0 ~ 0 180 0");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword[damage=800]");
		server.computeOnServer(s -> Droids.command(me(s, self), "spawn", DroidType.R2D2.id()));
		context.waitTicks(10);
		server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			DroidEntity droid = Droids.droid(player, DroidType.R2D2);
			droid.setStaying(true);
			droid.snapTo(player.getX(), player.getY(), player.getZ() - 3.0, 0.0F, 0.0F);
			return null;
		});
		context.getInput().lookAt(180.0F, 25.0F);
		server.runOnServer(s -> Droids.resetCooldowns());
		until(context, client -> DroidControl.targetType() == DroidType.R2D2 && DroidClient.count() == 1, 40, "looking at R2-D2 picks it");
		context.waitTicks(5);
		context.takeScreenshot("phase12_droid_hint");

		// The real key.
		int events = context.computeOnClient(client -> DroidClient.eventsSeen());
		context.getInput().pressKey(options -> DroidControl.ABILITY_KEY);
		until(context, client -> DroidClient.eventsSeen() > events, 20, "N on the droid looked at runs its ability");
		check(server.computeOnServer(s -> me(s, self).getMainHandItem().getDamageValue()) < 800, "R2-D2 repaired the sword in hand");

		// U: wait and follow.
		check(server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.R2D2).isStaying()), "R2-D2 waits");
		// A quarter second apart at least, or the second key counts as a bounce of the first.
		context.waitTicks(10);
		context.getInput().pressKey(options -> DroidControl.ORDER_KEY);
		context.waitTicks(5);
		check(server.computeOnServer(s -> !Droids.droid(me(s, self), DroidType.R2D2).isStaying()), "U makes it follow again");
		server.computeOnServer(s -> {
			Droids.droid(me(s, self), DroidType.R2D2).setStaying(true);
			return null;
		});

		// Nothing happens without a droid in view, or with a screen open.
		int sent = context.computeOnClient(client -> DroidControl.sent());
		context.getInput().lookAt(0.0F, 0.0F);
		context.waitTicks(3);
		check(!context.computeOnClient(client -> DroidControl.pressForTest(client, "ability")), "no droid looked at: nothing is sent");
		context.getInput().lookAt(180.0F, 25.0F);
		context.waitTicks(3);
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.DROIDS));
		context.waitTicks(3);
		check(!context.computeOnClient(client -> DroidControl.pressForTest(client, "ability")), "with a menu open the key does nothing");
		context.setScreen(() -> null);
		check(context.computeOnClient(client -> DroidControl.sent()) == sent, "nothing was sent by accident");

		// The server checks again: only its own droid, close and in sight, only ability and wait.
		server.runOnServer(s -> Droids.resetCooldowns());
		int droidId = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.R2D2).getId());
		check(server.computeOnServer(s -> Droids.target(me(s, self), droidId, "dismiss")) == null
				&& server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.R2D2)) != null, "the server only takes ability and wait by look");
		server.runCommand("summon minecraft:husk 0 ~ -2 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(2);
		int husk = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Zombie.class, AREA, Entity::isAlive).getFirst().getId());
		check(server.computeOnServer(s -> Droids.target(me(s, self), husk, "ability")) == null, "a husk is no droid to command");
		server.runCommand("kill @e[type=husk]");
		server.runCommand("tp @a 0 ~ 30 180 25");
		context.waitTicks(5);
		server.runOnServer(s -> Droids.resetCooldowns());
		check(server.computeOnServer(s -> Droids.target(me(s, self), droidId, "ability")) == null, "too far away: refused");
		server.runOnServer(s -> Droids.resetCooldowns());
		check(server.computeOnServer(s -> Droids.target(me(s, self), droidId + 100000, "ability")) == null, "an unknown entity: refused");
		server.runCommand("tp @a 0 ~ 0 180 25");
		context.waitTicks(5);
	}

	/** A droid never speaks twice right after each other; abilities always speak. */
	private void droidVoices(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitTicks(40);
		this.sounds.clear();
		server.computeOnServer(s -> {
			DroidEntity droid = Droids.droid(me(s, self), DroidType.R2D2);
			droid.playVoice("interact", 0.8F);
			droid.playVoice("interact", 0.8F);
			droid.playVoice("ambient", 0.5F);
			return null;
		});
		context.waitTicks(5);
		check(this.sounds.count("droid.r2d2.interact") == 1 && this.sounds.count("droid.r2d2.ambient") == 0,
				"three voices at once: only the first is heard " + this.sounds);
		server.computeOnServer(s -> {
			Droids.droid(me(s, self), DroidType.R2D2).playVoice("ability", 0.8F);
			return null;
		});
		context.waitTicks(5);
		check(this.sounds.count("droid.r2d2.ability") == 1, "the ability still speaks " + this.sounds);
		server.computeOnServer(s -> Droids.command(me(s, self), "dismiss", DroidType.R2D2.id()));
		context.waitTicks(10);
	}

	// Ships.

	/** The TIE Fighter flies and fires both cannons at once, with green bolts. */
	private static void tieFighter(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitFor(client -> ShipClient.status() != null, 40);
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SHIPS));
		context.waitTicks(5);
		context.clickScreenButton(ShipType.TIE_FIGHTER.translationKey());
		context.waitTicks(3);
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("phase12_hangar_tie_fighter");
		context.setScreen(() -> null);

		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(5);
		check(server.computeOnServer(s -> Starships.command(me(s, self), "board", "tie_fighter")) != null, "board answers");
		until(context, client -> ShipClient.piloting(client) != null, 60, "into the TIE Fighter's cockpit");
		check(server.computeOnServer(s -> ship(s).ship()) == ShipType.TIE_FIGHTER, "it is a TIE Fighter");
		check(server.computeOnServer(s -> ship(s).getMaxHealth()) == ShipType.TIE_FIGHTER.hull(), "with its own hull");
		context.getInput().holdKeyFor(options -> options.keyJump, 30);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(30);
		context.takeScreenshot("phase12_tie_fighter_flight");
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().holdKeyFor(options -> options.keyDown, 40);

		int seen = context.computeOnClient(client -> ShipClient.shotsSeen());
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		until(context, client -> ShipClient.shotsSeen() >= seen + 2, 10, "the attack key fires");
		int bolts = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(BlasterBolt.class, AREA, bolt -> bolt.type().id().getPath().equals("tie")).size());
		context.takeScreenshot("phase12_tie_lasers");
		context.waitTicks(20);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		int shots = context.computeOnClient(client -> ShipClient.shotsSeen()) - seen;
		check(bolts >= 2, "both cannons fire at once (" + bolts + " green bolts in the air)");
		check(shots % 2 == 0 || shots > 6, "in pairs (" + shots + " shots)");
		check(ShipType.TIE_FIGHTER.salvo() && !ShipType.TIE_INTERCEPTOR.salvo(), "only the TIE Fighter fires in pairs");
	}

	/** Damage from others, none from the pilot; repair; the explosion and the wait for a new ship. */
	private static void damage(ClientGameTestContext context, TestServerContext server, UUID self) {
		float full = server.computeOnServer(s -> ship(s).getHealth());
		server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			ship.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(me(s, self)), 10.0F);
			return null;
		});
		check(server.computeOnServer(s -> ship(s).getHealth()) == full, "the pilot cannot hurt their own ship");
		int explosions = ShipEntity.explosions();
		server.runCommand("summon minecraft:husk 0 -60 60 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(2);
		server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			Zombie husk = s.overworld().getEntitiesOfClass(Zombie.class, AREA, Entity::isAlive).getFirst();
			ship.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(husk), 8.0F);
			return null;
		});
		float hurt = server.computeOnServer(s -> ship(s).getHealth());
		check(hurt < full, "an enemy's hit damages the hull (" + full + " -> " + hurt + ")");
		context.waitTicks(10);
		context.takeScreenshot("phase12_ship_hit");

		server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			ship.hurtServer(s.overworld(), s.overworld().damageSources().explosion(null, null), 500.0F);
			return null;
		});
		context.waitTicks(5);
		check(ShipEntity.explosions() == explosions + 1, "a destroyed ship explodes");
		check(context.computeOnClient(client -> ShipClient.piloting(client) == null), "the pilot is thrown out");
		check(server.computeOnServer(s -> Starships.liveCount()) == 0, "the ship is gone");
		check(server.computeOnServer(s -> Starships.groundedSeconds(me(s, self))) > 0, "a new ship needs a while");
		check(server.computeOnServer(s -> Starships.command(me(s, self), "call", "tie_fighter")) != null
				&& server.computeOnServer(s -> Starships.liveCount()) == 0, "calling one right away is refused");
		context.takeScreenshot("phase12_ship_destroyed");
		server.runCommand("kill @e[type=husk]");
		server.runOnServer(s -> Starships.resetCooldowns());
		context.waitTicks(60);
	}

	/** The TIE Interceptor can be called and flown. */
	private static void interceptor(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 -60 0 180 0");
		context.waitTicks(5);
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "tie_interceptor"));
		until(context, client -> ShipClient.piloting(client) != null, 60, "into the TIE Interceptor");
		check(server.computeOnServer(s -> ship(s).ship()) == ShipType.TIE_INTERCEPTOR, "it is a TIE Interceptor");
		context.getInput().holdKeyFor(options -> options.keyJump, 20);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(60);
		double top = server.computeOnServer(s -> ship(s).speed());
		context.getInput().releaseKey(options -> options.keyUp);
		check(top > ShipType.XWING.maxSpeed(), "faster than the X-Wing (" + top + ")");
		context.takeScreenshot("phase12_interceptor_flight");
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "tie_interceptor"));
		context.waitTicks(10);
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
				select(context, 0);
				context.waitTicks(10);
				check(extension(context) == 0.0F, "dedicated server: a plain diamond sword has no blade");
				SaberSelection.Result result = context.computeOnClient(client -> SaberSelection.convert(client, InteractionHand.MAIN_HAND, true));
				check(result == SaberSelection.Result.SENT, "dedicated server: the menu's choice is sent (" + result + ")");
				context.waitTicks(10);
				check(server.computeOnServer(s -> SaberVariant.canHold(s.getPlayerList().getPlayers().getFirst().getMainHandItem())),
						"dedicated server: the sword is a lightsaber now");
				context.waitTicks(30);
				check(extension(context) == 1.0F, "dedicated server: and the client draws its blade");

				context.waitFor(client -> ShipClient.status() != null, 40);
				context.runOnClient(client -> ShipClient.command("board", ShipType.TIE_FIGHTER));
				until(context, client -> ShipClient.piloting(client) != null, 60, "dedicated server: into a TIE Fighter");
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				context.waitTicks(10);
			}
		}
	}

	// Helpers.

	private static void select(ClientGameTestContext context, int slot) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
		context.waitTicks(2);
	}

	private static float extension(ClientGameTestContext context) {
		return context.computeOnClient(client -> SaberTracker.INSTANCE.renderState(client.player, InteractionHand.MAIN_HAND, 1.0F).extension());
	}

	private static ShipEntity ship(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive).getFirst();
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static void until(ClientGameTestContext context, java.util.function.Predicate<Minecraft> condition, int ticks, String message) {
		for (int i = 0; i < ticks && !context.computeOnClient(condition::test); i++) {
			context.waitTick();
		}

		check(context.computeOnClient(condition::test), message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}

	/** Records the SaberCraft sounds the engine was asked to play, and the last saber hum. */
	private static final class SoundLog implements SoundEventListener {
		private final List<String> played = new ArrayList<>();
		private SaberHumSoundInstance hum;

		@Override
		public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
			if (!sound.getIdentifier().getNamespace().equals(SaberCraft.MOD_ID)) {
				return;
			}

			this.played.add(sound.getIdentifier().getPath());

			if (sound instanceof SaberHumSoundInstance instance) {
				this.hum = instance;
			}
		}

		int count(String path) {
			return (int) this.played.stream().filter(path::equals).count();
		}

		void clear() {
			this.played.clear();
			this.hum = null;
		}

		@Override
		public String toString() {
			return this.played.toString();
		}
	}
}
