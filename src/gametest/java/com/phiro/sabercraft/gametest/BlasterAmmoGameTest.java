package com.phiro.sabercraft.gametest;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.client.menu.BlasterMenuScreen;

/**
 * Phase 11: arrows are the ammunition of the blasters, and the blaster menu converts the bow.
 *
 * <p>Ammunition, each through the real right-click of the player where it matters: without an
 * arrow nothing fires (no bolt, no heat, no fire-rate pause, the inventory unchanged, the note on
 * the action bar), tipped and spectral arrows do not count, each shot takes exactly one arrow
 * (the off hand first, then the inventory), clicks faster than the fire rate and several use
 * packets in one tick take one arrow, the repeater's two bolts take one arrow, Infinity keeps the
 * arrows (and does not when the server says so), creative needs none, no real arrow entity ever
 * appears, and a plain bow still draws and shoots arrows as in vanilla. Last on a real dedicated
 * server.
 *
 * <p>Menu: the SaberCraft key with a bow in hand opens the blaster area; clicks on a card and on
 * "convert" make the bow that blaster, another card changes it, "back to bow" gives exactly the
 * bow from before. Screenshots of the gallery, the HUD with the arrow count, the muzzle flash in
 * first and third person and at night: {@code build/run/clientGameTest/screenshots/blaster_*}.
 */
public class BlasterAmmoGameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-40, -64, -40, 40, 320, 40);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			menu(context, server, self);
			noArrows(context, server, self);
			oneArrowPerShot(context, server, self);
			spam(context, server, self);
			repeater(context, server, self);
			infinity(context, server, self);
			creative(context, server, self);
			ruleOff(context, server, self);
			plainBow(context, server, self);
			pictures(context, server, self);
		}

		dedicatedServer(context);
	}

	/** The blaster menu: open it with the key, choose, convert, change, and back to the bow. */
	private static void menu(ClientGameTestContext context, TestServerContext server, UUID self) {
		ItemStack original = server.computeOnServer(s -> {
			ItemStack bow = new ItemStack(Items.BOW);
			bow.enchant(s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 3);
			bow.set(DataComponents.CUSTOM_NAME, Component.literal("Treuer Bogen"));
			bow.setDamageValue(11);
			ServerPlayer player = player(s, self);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, bow.copy());
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 32));
			player.getInventory().setSelectedSlot(0);
			return bow;
		});
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);

		context.getInput().pressKey(SaberCraftClient.CUSTOMIZE_KEY);
		context.waitTicks(5);
		context.runOnClient(client -> client.gui.toastManager().clear());
		check(context.computeOnClient(client -> client.gui.screen() instanceof BlasterMenuScreen), "the SaberCraft key with a bow in hand opens the blaster menu");
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("blaster_menu_gallery");

		context.clickScreenButton(BlasterType.DC15A.translationKey());
		context.waitTicks(30);
		check(context.computeOnClient(client -> ((BlasterMenuScreen) client.gui.screen()).chosen()) == BlasterType.DC15A, "a click on a card chooses the DC-15A");
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("blaster_menu_dc15a");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), original)), "choosing alone does not change the bow");

		context.clickScreenButton("sabercraft.blaster.menu.equip");
		context.waitTicks(5);
		check(server.computeOnServer(s -> BlasterType.of(held(s, self))) == BlasterType.DC15A, "\"convert\" makes the bow a DC-15A on the server");
		check(context.computeOnClient(client -> BlasterType.of(client.player.getMainHandItem())) == BlasterType.DC15A, "the client sees the DC-15A");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.BOW)) == 1, "exactly one bow/blaster, nothing duplicated");

		context.clickScreenButton(BlasterType.BOWCASTER.translationKey());
		context.waitTicks(20);
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("blaster_menu_bowcaster");
		context.clickScreenButton("sabercraft.blaster.menu.equip");
		context.waitTicks(5);
		check(server.computeOnServer(s -> BlasterType.of(held(s, self))) == BlasterType.BOWCASTER, "another card and \"convert\" change it to the bowcaster");

		context.clickScreenButton("sabercraft.blaster.menu.revert");
		context.waitTicks(5);
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), original)),
				"\"back to bow\" gives exactly the bow from before (" + server.computeOnServer(s -> held(s, self).getComponentsPatch().toString()) + ")");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.BOW)) == 1, "still exactly one bow");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 32, "the menu used no arrows");

		// A forged request for an unknown type changes nothing.
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.phiro.sabercraft.network.SelectBlasterPayload(InteractionHand.MAIN_HAND, "x99")));
		context.waitTicks(5);
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), original)), "an unknown type from a client is refused");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(5);
	}

	/** No arrow, no shot: nothing at all happens except the note. */
	private static void noArrows(ClientGameTestContext context, TestServerContext server, UUID self) {
		give(context, server, self, BlasterType.DL44, inventory -> {
			ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 5);
			tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
			inventory.setItem(3, tipped);
			inventory.setItem(4, new ItemStack(Items.SPECTRAL_ARROW, 5));
		});
		context.getInput().lookAt(180.0F, 0.0F);
		context.waitTicks(3);
		int before = bolts(server);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(3);
		check(bolts(server) == before, "without a plain arrow the right-click fires no bolt");
		check(server.computeOnServer(s -> Blasters.heat(self)) == 0.0F, "no heat without a shot");
		check(server.computeOnServer(s -> !player(s, self).getCooldowns().isOnCooldown(held(s, self))), "no fire-rate pause without a shot");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.TIPPED_ARROW) == 5
				&& player(s, self).getInventory().countItem(Items.SPECTRAL_ARROW) == 5), "tipped and spectral arrows are not used");
		check(context.computeOnClient(client -> !client.player.isUsingItem()), "the blaster is not drawn like a bow");
		check(context.computeOnClient(client -> BlasterClient.flash(client.player.getId(), InteractionHand.MAIN_HAND)) == 0.0F, "no muzzle flash without a shot");
		check(context.computeOnClient(client -> net.minecraft.util.Util.getMillis() - BlasterClient.lastEmpty()) < 2000L,
				"the action bar and a dry click say that arrows are missing");
		context.takeScreenshot("blaster_no_ammo");
		// The server refuses on its own too, whatever a client sends.
		check(server.computeOnServer(s -> Blasters.tryFire(player(s, self), InteractionHand.MAIN_HAND, held(s, self), BlasterType.DL44)) == Blasters.Refusal.NO_AMMO,
				"the server refuses a shot without arrows");
		check(arrowEntities(server) == 0, "no arrow entity anywhere");
	}

	/** Each shot takes exactly one arrow, the off hand first, then the inventory. */
	private static void oneArrowPerShot(ClientGameTestContext context, TestServerContext server, UUID self) {
		give(context, server, self, BlasterType.E11, inventory -> {
			inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.ARROW, 2));
			inventory.setItem(20, new ItemStack(Items.ARROW, 3));
		});

		for (int shot = 1; shot <= 5; shot++) {
			ready(context, server, self);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			boolean fired = server.computeOnServer(s -> Blasters.heat(self)) > 0.0F;
			int offhand = server.computeOnServer(s -> player(s, self).getInventory().getItem(Inventory.SLOT_OFFHAND).getCount());
			int inventory = server.computeOnServer(s -> player(s, self).getInventory().getItem(20).getCount());
			int left = 5 - shot;
			check(fired, "shot " + shot + " fired");
			check(offhand + inventory == left, "shot " + shot + " took exactly one arrow (" + left + " left)");
			check(shot > 2 || inventory == 3, "shot " + shot + " took the arrow from the off hand first");
			context.waitTicks(1);
			check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.ARROW)) == left, "shot " + shot + ": the client sees " + left + " arrows");
		}

		ready(context, server, self);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		check(server.computeOnServer(s -> Blasters.heat(self)) == 0.0F, "after the last arrow the blaster stays silent");
		check(arrowEntities(server) == 0, "no arrow entity anywhere");
	}

	/** Clicking faster than the blaster can fire, and many use packets in one tick, take one arrow per real shot. */
	private static void spam(ClientGameTestContext context, TestServerContext server, UUID self) {
		give(context, server, self, BlasterType.BOWCASTER, inventory -> inventory.setItem(5, new ItemStack(Items.ARROW, 20)));

		// Eight clicks as fast as the test can (a press and a tick each, 16 ticks); the bowcaster
		// fires once per 20 ticks.
		for (int i = 0; i < 8; i++) {
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTick();
		}

		context.waitTick();
		int used = 20 - server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW));
		check(used == 1, "eight quick clicks within the bowcaster's fire rate: one shot, " + used + " arrow");

		// Five use requests in the same server tick, as a lagging or cheating client might send.
		ready(context, server, self);
		int fired = server.computeOnServer(s -> {
			int count = 0;

			for (int i = 0; i < 5; i++) {
				if (Blasters.fire(player(s, self), InteractionHand.MAIN_HAND, held(s, self), BlasterType.BOWCASTER)) {
					count++;
				}
			}

			return count;
		});
		check(fired == 1, "five requests in one tick fire once");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 18, "and take one arrow");
	}

	/** The repeater fires two bolts per shot for one arrow. */
	private static void repeater(ClientGameTestContext context, TestServerContext server, UUID self) {
		give(context, server, self, BlasterType.REPEATER, inventory -> inventory.setItem(5, new ItemStack(Items.ARROW, 4)));
		int before = bolts(server);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(1);
		check(bolts(server) - before == 2, "the repeater fires two bolts");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 3, "for one arrow");
	}

	/** Infinity keeps the arrow, but one is still needed; the server can switch that off. */
	private static void infinity(ClientGameTestContext context, TestServerContext server, UUID self) {
		give(context, server, self, BlasterType.A280, inventory -> {
			ItemStack blaster = inventory.getItem(0);
			blaster.enchant(inventory.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY), 1);
		});
		ready(context, server, self);
		check(server.computeOnServer(s -> Blasters.tryFire(player(s, self), InteractionHand.MAIN_HAND, held(s, self), BlasterType.A280)) == Blasters.Refusal.NO_AMMO,
				"Infinity without any arrow does not fire (like a bow)");
		server.computeOnServer(s -> {
			player(s, self).getInventory().setItem(5, new ItemStack(Items.ARROW, 1));
			return null;
		});
		context.waitTicks(2);

		for (int shot = 0; shot < 3; shot++) {
			ready(context, server, self);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
		}

		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 1, "with Infinity three shots keep the one arrow");

		server.computeOnServer(s -> {
			AbilityConfig.get().blasterInfinity = false;
			return null;
		});
		ready(context, server, self);
		check(server.computeOnServer(s -> Blasters.fire(player(s, self), InteractionHand.MAIN_HAND, held(s, self), BlasterType.A280)), "fires with the rule off");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 0, "but uses the arrow when the server turns Infinity off");
		server.computeOnServer(s -> {
			AbilityConfig.get().blasterInfinity = true;
			return null;
		});
	}

	/** Creative players need no arrows. */
	private static void creative(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("gamemode creative @a");
		give(context, server, self, BlasterType.SE14C, inventory -> {
		});
		context.waitTicks(5);
		int before = bolts(server);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		check(bolts(server) > before, "in creative the blaster fires without arrows");
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);
	}

	/** With "blaster_ammo" off the blasters fire without arrows, as in Phase 10. */
	private static void ruleOff(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.computeOnServer(s -> {
			AbilityConfig.get().blasterAmmo = false;
			return null;
		});
		give(context, server, self, BlasterType.REBEL, inventory -> inventory.setItem(5, new ItemStack(Items.ARROW, 3)));
		ready(context, server, self);
		check(server.computeOnServer(s -> Blasters.fire(player(s, self), InteractionHand.MAIN_HAND, held(s, self), BlasterType.REBEL)), "with ammunition off the blaster fires");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 3, "and takes no arrow");
		server.computeOnServer(s -> {
			AbilityConfig.get().blasterAmmo = true;
			return null;
		});
	}

	/** A plain bow is untouched: drawn, shoots an arrow entity, uses one arrow. */
	private static void plainBow(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("kill @e[type=!player]");
		server.computeOnServer(s -> {
			ServerPlayer player = player(s, self);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.BOW));
			player.getInventory().setItem(1, new ItemStack(Items.ARROW, 16));
			return null;
		});
		context.waitTicks(5);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(25);
		check(context.computeOnClient(client -> client.player.isUsingItem()), "a plain bow is still drawn");
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		check(arrowEntities(server) == 1, "a plain bow still shoots one arrow");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 15, "and uses one arrow");
		server.runCommand("kill @e[type=!player]");
	}

	/** Screenshots: the flash in first and third person, by day and at night, and the HUD. */
	private static void pictures(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("summon minecraft:armor_stand 0 ~ -6");
		// A real flash is shorter than taking a picture; hold it at a bright moment.
		context.runOnClient(client -> BlasterClient.holdFlashForTests(5000.0F));

		for (BlasterType type : new BlasterType[] {BlasterType.DL44, BlasterType.DC15A, BlasterType.BOWCASTER}) {
			give(context, server, self, type, inventory -> inventory.setItem(5, new ItemStack(Items.ARROW, 10)));
			context.getInput().lookAt(180.0F, 0.0F);
			context.waitTicks(10);
			context.takeScreenshot("blaster_" + type.id().getPath() + "_hud");
			ready(context, server, self);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTick();
			check(context.computeOnClient(client -> BlasterClient.flash(client.player.getId(), InteractionHand.MAIN_HAND)) > 0.5F, type.id().getPath() + ": the muzzle flash is lit");
			context.takeScreenshot("blaster_" + type.id().getPath() + "_flash_first_person");
			check(context.computeOnClient(client -> BlasterClient.lastShot(client.player.getId(), InteractionHand.MAIN_HAND)) > 0L, type.id().getPath() + ": the client knows the shot");
			context.waitTicks(20);
		}

		server.runCommand("time set midnight");
		context.waitTicks(10);
		ready(context, server, self);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTick();
		context.takeScreenshot("blaster_bowcaster_flash_night");
		context.waitTicks(25);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		ready(context, server, self);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTick();
		context.takeScreenshot("blaster_bowcaster_flash_third_person_night");
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			BlasterClient.holdFlashForTests(0.0F);
		});
		server.runCommand("time set day");
		context.waitTicks(10);
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("gamemode survival @a");
				server.runCommand("clear @a");
				server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
				context.waitTicks(5);
				context.runOnClient(client -> client.getConnection().sendCommand("sabercraft blaster e11"));
				context.waitTicks(10);
				check(context.computeOnClient(client -> BlasterType.of(client.player.getMainHandItem())) == BlasterType.E11, "dedicated server: E-11 in hand");
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(5);
				check(context.computeOnClient(client -> BlasterClient.lastShot(client.player.getId(), InteractionHand.MAIN_HAND)) == 0L,
						"dedicated server: no arrows, no shot");
				server.runCommand("item replace entity @a hotbar.8 with minecraft:arrow 2");
				context.waitTicks(5);

				for (int i = 0; i < 4; i++) {
					context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					context.waitTicks(8);
				}

				context.waitTicks(5);
				check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.ARROW)) == 0, "dedicated server: two arrows are used up");
				check(context.computeOnClient(client -> BlasterClient.lastShot(client.player.getId(), InteractionHand.MAIN_HAND)) > 0L, "dedicated server: the shots were seen");
				context.takeScreenshot("blaster_dedicated_server_empty");
			}
		}
	}

	private interface Setup {
		void apply(Inventory inventory);
	}

	/** A fresh blaster of a type in the selected main hand slot 0, plus what {@code setup} adds. */
	private static void give(ClientGameTestContext context, TestServerContext server, UUID self, BlasterType type, Setup setup) {
		server.runCommand("kill @e[type=snowball]");
		server.computeOnServer(s -> {
			ServerPlayer player = player(s, self);
			player.getInventory().clearContent();
			ItemStack bow = new ItemStack(Items.BOW);
			com.phiro.sabercraft.item.ItemConversion.toBlaster(bow, type);
			player.getInventory().setItem(0, bow);
			player.getInventory().setSelectedSlot(0);
			setup.apply(player.getInventory());
			Blasters.cool(self);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(bow));
			return null;
		});
		// The client has to know the new item before it clicks.
		context.waitTicks(3);
	}

	/** Ready to fire: no fire-rate pause, no heat. */
	private static void ready(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.computeOnServer(s -> {
			ServerPlayer player = player(s, self);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(held(s, self)));
			Blasters.cool(self);
			return null;
		});
		context.waitTicks(2);
	}

	private static int bolts(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntitiesOfClass(BlasterBolt.class, AREA).size());
	}

	private static int arrowEntities(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntitiesOfClass(AbstractArrow.class, AREA).size());
	}

	private static ServerPlayer player(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static ItemStack held(MinecraftServer server, UUID self) {
		return player(server, self).getMainHandItem();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
