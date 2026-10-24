package com.phiro.sabercraft.gametest;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberHilt;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.saber.SaberProfiles;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.network.SelectSaberVariantPayload;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Checks the Phase 5 variants in a real client:
 *
 * <ul>
 *   <li>every hilt and color combination has a held model with a blade and an inventory icon
 *       without one, and each color gives the item model its own identity (no stale cache),</li>
 *   <li>the dark hilt has its own animation and sounds, the others keep the default,</li>
 *   <li>the menu (key, buttons, apply) changes only the saber in the hand, other swords keep
 *       their own variant,</li>
 *   <li>the server ignores invalid requests, the creative fallback works,</li>
 *   <li>mobs show the variant of their sword through the normal equipment sync,</li>
 *   <li>the same selection works over a real network connection to a dedicated server,</li>
 *   <li>each color really shows up in its color on screen (pixel check of the screenshots).</li>
 * </ul>
 *
 * <p>Screenshots of every color and every hilt in first and third person, the inventory, the
 * menu and a lineup land in {@code build/run/clientGameTest/screenshots}.
 */
public class SaberVariantGameTest implements FabricClientGameTest {
	private static final int SETTLE = SaberAnimations.HEAVY.igniteDelay() + SaberAnimations.HEAVY.igniteTicks() + 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("time set day");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:apple");
			server.runCommand("item replace entity @a hotbar.2 with minecraft:netherite_sword");
			select(context, 0);
			context.getInput().lookAt(20.0F, 10.0F);
			context.waitTicks(SETTLE);

			this.models(context);
			this.profiles(context);
			this.menu(context, server);
			this.validation(context, server);
			this.creativeFallback(context, server);
			this.mobs(context, server);
			this.colorScreenshots(context, server);
			this.hiltScreenshots(context, server);
			this.inventory(context, server);
			this.lineup(context, server);
		}

		this.dedicatedServer(context);
	}

	private void models(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ItemStack reference = new ItemStack(Items.IRON_SWORD);
			double referenceIcon = size(bounds(client, reference, ItemDisplayContext.GUI));

			for (Identifier hilt : SaberVariant.HILTS) {
				Map<Object, Identifier> identities = new HashMap<>();

				for (Identifier color : SaberVariant.COLORS) {
					ItemStack saber = saber(hilt, color);

					for (ItemDisplayContext held : new ItemDisplayContext[] {
							ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
							ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
							ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
							ItemDisplayContext.THIRD_PERSON_LEFT_HAND
					}) {
						double heldSize = size(bounds(client, saber, held));
						double referenceHeld = size(bounds(client, reference, held));

						// Yoda's blade is short on purpose (Phase 9), the others are clearly longer than a sword.
						double longer = hilt.equals(SaberVariant.YODA) ? 1.1 : 1.3;

						if (heldSize < referenceHeld * longer) {
							throw new AssertionError("No blade on " + hilt + "/" + color + " in " + held + ": " + heldSize + " vs " + referenceHeld);
						}
					}

					double icon = size(bounds(client, saber, ItemDisplayContext.GUI));

					if (icon > referenceIcon * 1.05) {
						throw new AssertionError("Inventory icon of " + hilt + " is too big or has a blade: " + icon + " vs " + referenceIcon);
					}

					TrackingItemStackRenderState state = new TrackingItemStackRenderState();
					client.getItemModelResolver().updateForTopItem(state, saber, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.level, null, 0);
					Identifier previous = identities.put(state.getModelIdentity(), color);

					if (previous != null) {
						throw new AssertionError(hilt + ": " + color + " has the same model identity as " + previous);
					}
				}

				SaberCraft.LOGGER.info("OK: hilt {} has a blade in every hand and color, a plain icon and one identity per color", hilt);
			}

			// The hilt models really differ from each other in the hand.
			Map<String, Identifier> shapes = new HashMap<>();

			for (Identifier hilt : SaberVariant.HILTS) {
				String shape = bounds(client, saber(hilt, SaberVariant.RED), ItemDisplayContext.GUI).toString();
				Identifier same = shapes.put(shape, hilt);
				SaberCraft.LOGGER.info("Hilt {} icon bounds {}", hilt, shape);

				if (same != null) {
					throw new AssertionError(hilt + " has the same icon shape as " + same);
				}
			}
		});
	}

	private void profiles(ClientGameTestContext context) {
		context.runOnClient(client -> {
			check(SaberProfiles.of(new ItemStack(Items.NETHERITE_SWORD)) == SaberProfiles.DEFAULT, "plain sword keeps the Phase 4 profile");
			check(SaberProfiles.of(saber(SaberVariant.GUARDIAN, SaberVariant.BLUE)) == SaberProfiles.DEFAULT, "guardian uses the default profile");
			check(SaberProfiles.of(saber(SaberVariant.DARK_LORD, SaberVariant.RED)) == SaberProfiles.DARK, "dark hilt has its own profile");
			check(SaberProfiles.of(saber(SaberVariant.DOUBLE, SaberVariant.RED)) == SaberProfiles.DOUBLE, "double hilt has its own profile");
			check(client.getSoundManager().getSoundEvent(SaberCraft.id("saber.dark.hum")) != null, "dark hum is registered in sounds.json");
			check(BladeStyles.colorOf(new ItemStack(Items.NETHERITE_SWORD)) == null, "plain sword has no stored color");
			check(BladeStyles.get(SaberVariant.RED).glowColor() == 0xFF1A0A, "red blade unchanged");
			check(BladeStyles.all().size() == SaberVariant.COLORS.size(), "a style for every color");
		});
	}

	/** Opens the menu with the key, picks a hilt and color with the buttons and applies them. */
	private void menu(ClientGameTestContext context, TestServerContext server) {
		select(context, 0);
		context.waitTicks(2);
		context.getInput().pressKey(SaberCraftClient.CUSTOMIZE_KEY);
		context.waitForScreen(SaberCustomizeScreen.class);
		context.clickScreenButton("sabercraft.hilt.sentinel");
		context.clickScreenButton("sabercraft.color.yellow");
		context.waitTicks(2);
		context.takeScreenshot("variant_menu");
		context.clickScreenButton("sabercraft.customize.apply");
		context.waitFor(client -> client.gui.screen() == null, 40);
		context.waitTicks(5);

		SaberVariant expected = new SaberVariant(SaberVariant.SENTINEL, SaberVariant.YELLOW);
		check(serverVariant(server, 0).equals(expected), "menu stored sentinel/yellow on slot 0: " + serverVariant(server, 0));
		check(clientVariant(context, 0).equals(expected), "client sees sentinel/yellow on slot 0");
		check(serverVariant(server, 2).equals(SaberVariant.DEFAULT), "the other sword is unchanged: " + serverVariant(server, 2));

		// Second sword gets its own variant, the first one keeps its own.
		select(context, 2);
		context.waitTicks(2);
		apply(context, InteractionHand.MAIN_HAND, new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED));
		check(serverVariant(server, 2).equals(new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED)), "slot 2 is dark/red");
		check(serverVariant(server, 0).equals(expected), "slot 0 is still sentinel/yellow");

		SaberHandState state = context.computeOnClient(client -> SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND));
		check(state != null && state.profile() == SaberProfiles.DARK, "the held dark saber uses the dark profile");
		select(context, 0);
		context.waitTicks(SETTLE);
	}

	private void validation(ClientGameTestContext context, TestServerContext server) {
		SaberVariant before = serverVariant(server, 0);
		context.runOnClient(client -> ClientPlayNetworking.send(new SelectSaberVariantPayload(
				InteractionHand.MAIN_HAND, new SaberVariant(SaberCraft.id("not_a_hilt"), SaberVariant.RED)
		)));
		context.runOnClient(client -> ClientPlayNetworking.send(new SelectSaberVariantPayload(
				InteractionHand.OFF_HAND, new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.BLUE)
		)));
		context.waitTicks(5);
		check(serverVariant(server, 0).equals(before), "unknown hilt is ignored");
		check(server.computeOnServer(s -> player(s).getOffhandItem().isEmpty()), "request for an empty hand changes nothing");

		// The slot change waits until the blade is back in the hilt.
		select(context, 1);
		context.waitTicks(SETTLE);
		SaberSelection.Result result = context.computeOnClient(client -> SaberSelection.apply(
				client, InteractionHand.MAIN_HAND, new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.BLUE)
		));
		check(result == SaberSelection.Result.NO_SABER, "an apple cannot be customized");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(1).is(Items.APPLE)
				&& !player(s).getInventory().getItem(1).has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA)), "apple unchanged");
		select(context, 0);
		context.waitTicks(SETTLE);
	}

	private void creativeFallback(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
		context.runOnClient(client -> SaberSelection.applyCreative(
				client, InteractionHand.MAIN_HAND, new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.PURPLE)
		));
		context.waitTicks(5);
		check(serverVariant(server, 0).equals(new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.PURPLE)),
				"creative inventory packet stores the variant: " + serverVariant(server, 0));
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);
		// Keep the game mode messages out of the screenshots.
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
	}

	private void mobs(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon minecraft:zombie ~2 ~ ~2 {NoAI:1b,PersistenceRequired:1b,equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1,"
				+ "components:{\"minecraft:custom_model_data\":{strings:[\"sabercraft:dark_lord\",\"sabercraft:purple\"]}}}}}");
		context.waitTicks(5);
		context.runOnClient(client -> {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity instanceof Zombie zombie) {
					SaberHandState state = SaberTracker.INSTANCE.state(zombie, InteractionHand.MAIN_HAND);
					check(state != null && state.profile() == SaberProfiles.DARK, "zombie's dark saber uses the dark profile");
					check(SaberVariant.PURPLE.equals(BladeStyles.colorOf(zombie.getMainHandItem())), "zombie's saber is purple on the client");
					return;
				}
			}

			throw new AssertionError("No zombie on the client");
		});
		server.runCommand("kill @e[type=minecraft:zombie]");
		context.waitTicks(30);
		server.runCommand("kill @e[type=minecraft:item]");
	}

	/**
	 * Every color on the guardian hilt, first person and third person, by day and by night. At
	 * night the screenshot is also checked for pixels of the blade color.
	 */
	private void colorScreenshots(ClientGameTestContext context, TestServerContext server) {

		for (Identifier color : SaberVariant.COLORS) {
			apply(context, InteractionHand.MAIN_HAND, new SaberVariant(SaberVariant.GUARDIAN, color));
			context.waitTicks(3);

			for (String time : new String[] {"day", "night"}) {
				server.runCommand("time set " + (time.equals("night") ? "midnight" : "day"));
				context.waitTicks(3);
				Path firstPerson = screenshot(context, CameraType.FIRST_PERSON, "color_" + color.getPath() + "_first_person_" + time);
				screenshot(context, CameraType.THIRD_PERSON_FRONT, "color_" + color.getPath() + "_third_person_" + time);

				if (time.equals("night")) {
					int pixels = countColor(firstPerson, BladeStyles.get(color));
					check(pixels > 150, color + " blade is visible in its color: " + pixels + " pixels");
				}
			}
		}

		server.runCommand("time set day");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** Every hilt in its own default color, first person and third person. */
	private void hiltScreenshots(ClientGameTestContext context, TestServerContext server) {

		for (SaberHilt hilt : SaberHilts.all().values()) {
			apply(context, InteractionHand.MAIN_HAND, new SaberVariant(hilt.id(), hilt.defaultColor()));
			context.waitTicks(SETTLE);
			String name = "hilt_" + hilt.id().getPath();
			screenshot(context, CameraType.FIRST_PERSON, name + "_first_person");
			screenshot(context, CameraType.THIRD_PERSON_FRONT, name + "_third_person");
			screenshot(context, CameraType.THIRD_PERSON_BACK, name + "_third_person_back");
			server.runCommand("time set midnight");
			context.waitTicks(3);
			screenshot(context, CameraType.THIRD_PERSON_FRONT, name + "_third_person_night");
			server.runCommand("time set day");
			context.waitTicks(3);
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** All hilts in the hotbar and the inventory screen, so the icons can be compared. */
	private void inventory(ClientGameTestContext context, TestServerContext server) {
		int slot = 3;

		for (SaberHilt hilt : SaberHilts.all().values()) {
			server.runCommand("item replace entity @a hotbar." + slot + " with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\""
					+ hilt.id() + "\",\"" + hilt.defaultColor() + "\"]}]");
			slot++;
		}

		context.waitTicks(5);
		context.takeScreenshot("inventory_hotbar");
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(5);
		context.takeScreenshot("inventory_screen");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	/** Armor stands with all six colors in a row, at night. */
	private void lineup(ClientGameTestContext context, TestServerContext server) {
		select(context, 1);
		context.waitTicks(SETTLE);
		int index = 0;

		for (Identifier color : SaberVariant.COLORS) {
			SaberHilt hilt = switch (index) {
				case 0 -> SaberHilts.DARK_LORD;
				case 1 -> SaberHilts.GUARDIAN;
				case 2 -> SaberHilts.SENTINEL;
				case 3 -> SaberHilts.DARK_LORD;
				case 4 -> SaberHilts.GUARDIAN;
				default -> SaberHilts.SENTINEL;
			};
			float x = -3.75F + index * 1.5F;
			server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~" + x + " ~ ~4.5 {Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,"
					+ "Pose:{RightArm:[-75f,0f,0f]},equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1,"
					+ "components:{\"minecraft:custom_model_data\":{strings:[\"" + hilt.id() + "\",\"" + color + "\"]}}}}}");
			index++;
		}

		context.getInput().lookAt(0.0F, 12.0F);
		hideHud(context, true);
		server.runCommand("time set midnight");
		context.waitTicks(10);
		context.takeScreenshot("lineup_night");
		server.runCommand("time set day");
		context.waitTicks(5);
		context.takeScreenshot("lineup_day");
		hideHud(context, false);
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		context.getInput().lookAt(20.0F, 10.0F);
		select(context, 0);
		context.waitTicks(10);
	}

	private void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
				server.runCommand("item replace entity @a hotbar.2 with minecraft:netherite_sword");
				select(context, 0);
				context.waitTicks(5);
				check(context.computeOnClient(client -> ClientPlayNetworking.canSend(SelectSaberVariantPayload.TYPE)),
						"dedicated server with SaberCraft accepts the selection packet");

				SaberSelection.Result result = context.computeOnClient(client -> SaberSelection.apply(
						client, InteractionHand.MAIN_HAND, new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.WHITE)
				));
				check(result == SaberSelection.Result.SENT, "selection is sent to the dedicated server");
				connection.waitForServerboundPackets();
				context.waitTicks(10);
				check(variantOn(server.computeOnServer(s -> player(s).getInventory().getItem(0).copy())).equals(
						new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.WHITE)), "dedicated server stored guardian/white");
				check(clientVariant(context, 0).equals(new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.WHITE)), "client got guardian/white back");
				check(variantOn(server.computeOnServer(s -> player(s).getInventory().getItem(2).copy())).equals(SaberVariant.DEFAULT),
						"other sword on the dedicated server unchanged");
				context.waitTicks(SETTLE);
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				context.waitTicks(3);
				context.takeScreenshot("variant_dedicated_server");
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			}
		}
	}

	// ---------------------------------------------------------------------------------------------

	private static void apply(ClientGameTestContext context, InteractionHand hand, SaberVariant variant) {
		SaberSelection.Result result = context.computeOnClient(client -> SaberSelection.apply(client, hand, variant));
		check(result == SaberSelection.Result.SENT, "selection sent: " + result);
		context.waitTicks(4);
	}

	private static ItemStack saber(Identifier hilt, Identifier color) {
		ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
		new SaberVariant(hilt, color).applyTo(stack);
		return stack;
	}

	private static SaberVariant variantOn(ItemStack stack) {
		Identifier hilt = SaberVariant.hiltOf(stack);
		Identifier color = SaberVariant.colorOf(stack);
		return new SaberVariant(hilt != null ? hilt : SaberVariant.STANDARD, color != null ? color : SaberVariant.RED);
	}

	private static SaberVariant serverVariant(TestServerContext server, int slot) {
		return variantOn(server.computeOnServer(s -> player(s).getInventory().getItem(slot).copy()));
	}

	private static SaberVariant clientVariant(ClientGameTestContext context, int slot) {
		return variantOn(context.computeOnClient(client -> client.player.getInventory().getItem(slot).copy()));
	}

	private static net.minecraft.server.level.ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static AABB bounds(Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
		ItemStackRenderState state = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(state, stack, displayContext, client.level, client.player, 0);
		return state.getModelBoundingBox();
	}

	private static double size(AABB box) {
		return Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
	}

	private static void select(ClientGameTestContext context, int slot) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
	}

	private static void hideHud(ClientGameTestContext context, boolean hidden) {
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != hidden) {
				client.gui.hud.toggle();
			}
		});
	}

	/**
	 * Takes a screenshot from the given camera. In third person the HUD is hidden for a clean
	 * picture; in first person it has to stay, because hiding it also hides the hand.
	 */
	private static Path screenshot(ClientGameTestContext context, CameraType camera, String name) {
		context.runOnClient(client -> client.options.setCameraType(camera));
		hideHud(context, camera != CameraType.FIRST_PERSON);
		context.waitTicks(3);
		Path path = context.takeScreenshot(name);
		hideHud(context, false);
		return path;
	}

	/**
	 * Counts pixels that have the blade's halo color (or, for the white blade, bright neutral
	 * pixels). At night almost nothing else on screen is bright and saturated.
	 */
	private static int countColor(Path file, BladeStyle style) {
		BufferedImage image;

		try {
			image = ImageIO.read(file.toFile());
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + file, e);
		}

		int rgb = style.glowColor();
		float[] target = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		boolean neutral = target[1] < 0.3F;
		float[] hsb = new float[3];
		int count = 0;

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getRGB(x, y);
				Color.RGBtoHSB(pixel >> 16 & 0xFF, pixel >> 8 & 0xFF, pixel & 0xFF, hsb);

				if (neutral) {
					if (hsb[1] < 0.3F && hsb[2] > 0.8F) {
						count++;
					}
				} else {
					float hueDistance = Math.abs(hsb[0] - target[0]);
					hueDistance = Math.min(hueDistance, 1.0F - hueDistance);

					if (hueDistance < 0.06F && hsb[1] > 0.4F && hsb[2] > 0.35F) {
						count++;
					}
				}
			}
		}

		SaberCraft.LOGGER.info("{}: {} pixels of the blade color in {}", style.id(), count, file.getFileName());
		return count;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
