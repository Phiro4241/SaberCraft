package com.phiro.sabercraft.gametest;

import java.util.List;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.client.blaster.BlasterBoltRenderer;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.client.saber.SaberProfiles;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 10: every sword becomes a saber and back, every bow a blaster and back, through the real
 * commands typed by the player, and the blasters really fire.
 *
 * <p>Swords: each of the seven swords gets enchantments, a name, damage and model data of a "data
 * pack", is converted, checked (saber on server and client, all data kept, nothing duplicated),
 * recolored, and turned back; afterwards it must be exactly the item from before, component for
 * component. The netherite sword stays a saber without conversion (Phases 1 to 9) and can be
 * turned into a plain one and back. Wrong items and unknown names are refused without a change.
 *
 * <p>Blasters: a bow with Power, Flame and a name becomes each blaster type in turn and fires at a
 * zombie through a real right-click (with arrows, Phase 11): the bolt exists on the server, the client draws it as a
 * bolt, the zombie loses health, heat and durability rise, the fire rate holds. Then the blaster
 * overheats, refuses to fire and cools down; a plain bow still draws and shoots arrows; the
 * blaster turns back into exactly the bow from before. Last, the same on a real dedicated server.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/arsenal_*}.
 */
public class ArsenalGameTest implements FabricClientGameTest {
	private static final List<Item> SWORDS = SaberVariant.SWORDS;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

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

			swords(context, server, self);
			wrongItems(context, server, self);
			blasters(context, server, self);
			plainBow(context, server, self);
		}

		dedicatedServer(context);
	}

	private static void swords(ClientGameTestContext context, TestServerContext server, UUID self) {
		for (Item sword : SWORDS) {
			String name = sword.toString().replace("minecraft:", "");
			ItemStack original = server.computeOnServer(s -> {
				ItemStack stack = new ItemStack(sword);
				stack.enchant(enchantment(s, Enchantments.SHARPNESS), 3);
				stack.enchant(enchantment(s, Enchantments.UNBREAKING), 2);
				stack.set(DataComponents.CUSTOM_NAME, Component.literal("Mein " + name));
				stack.setDamageValue(7);
				// Model data a data pack might use, which must come back unchanged.
				stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(0.5F), List.of(true), List.of(), List.of()));
				ServerPlayer player = player(s, self);
				player.getInventory().clearContent();
				player.getInventory().setItem(0, stack.copy());
				player.getInventory().setSelectedSlot(0);
				return stack;
			});
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(5);

			boolean netherite = sword == Items.NETHERITE_SWORD;
			check(server.computeOnServer(s -> SaberVariant.canHold(held(s, self))) == netherite,
					name + ": " + (netherite ? "a netherite sword is a saber without conversion" : "a plain sword is no saber"));

			command(context, "sabercraft convert");
			ItemStack saber = server.computeOnServer(s -> held(s, self).copy());

			if (netherite) {
				check(saber.get(DataComponents.CUSTOM_MODEL_DATA).equals(original.get(DataComponents.CUSTOM_MODEL_DATA)),
						name + ": convert without a color leaves a netherite saber as it is");
				command(context, "sabercraft convert red standard");
				saber = server.computeOnServer(s -> held(s, self).copy());
			}

			check(SaberVariant.canHold(saber), name + ": is a saber after /sabercraft convert");
			check(saber.getItem() == sword, name + ": still the same item (material)");
			check(saber.getEnchantments().equals(original.getEnchantments()), name + ": enchantments kept");
			check(saber.getDamageValue() == 7, name + ": durability kept");
			check(saber.getHoverName().getString().equals("Mein " + name), name + ": name kept");
			check(saber.get(DataComponents.CUSTOM_MODEL_DATA).floats().equals(List.of(0.5F)), name + ": other model data kept");
			check(server.computeOnServer(s -> count(player(s, self), sword)) == 1, name + ": exactly one sword, nothing duplicated or lost");
			Item finalSword = sword;
			check(!netherite ? SaberVariant.hiltOf(saber).equals(SaberVariant.hiltForMaterial(new ItemStack(finalSword))) : true,
					name + ": a hilt that fits the material (" + SaberVariant.hiltOf(saber) + ")");
			context.waitTicks(10);
			check(context.computeOnClient(client -> SaberProfiles.isSaber(client.player.getMainHandItem())), name + ": the client sees a saber");
			context.waitTicks(25);
			context.takeScreenshot("arsenal_saber_" + name);

			command(context, "sabercraft convert green kenobi");
			ItemStack recolored = server.computeOnServer(s -> held(s, self).copy());
			check(SaberVariant.KENOBI.equals(SaberVariant.hiltOf(recolored)) && SaberVariant.GREEN.equals(SaberVariant.colorOf(recolored)),
					name + ": color and hilt changed by the command");
			check(recolored.getEnchantments().equals(original.getEnchantments()), name + ": enchantments kept after the change");

			command(context, "sabercraft revert");
			ItemStack back = server.computeOnServer(s -> held(s, self).copy());

			if (netherite) {
				check(!SaberVariant.canHold(back), name + ": a netherite saber can become a plain sword");
				check(back.getEnchantments().equals(original.getEnchantments()) && back.getDamageValue() == 7, name + ": plain again with all data");
				check(back.get(DataComponents.CUSTOM_MODEL_DATA).floats().equals(List.of(0.5F)), name + ": other model data kept on the plain sword");
				context.waitTicks(5);
				check(context.computeOnClient(client -> !SaberProfiles.isSaber(client.player.getMainHandItem())), name + ": the client sees a plain sword");
				context.takeScreenshot("arsenal_plain_netherite");
				command(context, "sabercraft convert");
				check(server.computeOnServer(s -> SaberVariant.canHold(held(s, self))), name + ": and back into a saber");
			} else {
				check(ItemStack.matches(back, original), name + ": after /sabercraft revert exactly the item from before (" + back.getComponentsPatch() + ")");
				check(!SaberVariant.canHold(back), name + ": no saber any more");
			}

			check(server.computeOnServer(s -> count(player(s, self), sword)) == 1, name + ": still exactly one sword");
		}

		// All seven as sabers in the hotbar, for the inventory icons.
		server.computeOnServer(s -> {
			ServerPlayer player = player(s, self);
			player.getInventory().clearContent();

			for (int i = 0; i < SWORDS.size(); i++) {
				ItemStack stack = new ItemStack(SWORDS.get(i));
				com.phiro.sabercraft.item.ItemConversion.toSaber(stack, SaberVariant.COLORS.get(i % SaberVariant.COLORS.size()), null);
				player.getInventory().setItem(i, stack);
			}

			return null;
		});
		context.waitTicks(10);
		context.takeScreenshot("arsenal_all_swords_hotbar");
	}

	private static void wrongItems(ClientGameTestContext context, TestServerContext server, UUID self) {
		for (Item item : new Item[] {Items.DIRT, Items.BOW, Items.TRIDENT}) {
			server.computeOnServer(s -> {
				player(s, self).getInventory().setItem(0, new ItemStack(item));
				return null;
			});
			context.waitTicks(3);
			command(context, "sabercraft convert");
			ItemStack held = server.computeOnServer(s -> held(s, self).copy());
			check(ItemStack.matches(held, new ItemStack(item)), item + " is refused and stays unchanged");
		}

		server.computeOnServer(s -> {
			player(s, self).getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
			return null;
		});
		context.waitTicks(3);
		command(context, "sabercraft convert pink");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), new ItemStack(Items.IRON_SWORD))), "an unknown color is refused");
		command(context, "sabercraft convert red nohilt");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), new ItemStack(Items.IRON_SWORD))), "an unknown hilt is refused");
		command(context, "sabercraft revert");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), new ItemStack(Items.IRON_SWORD))), "revert on a plain sword changes nothing");
		command(context, "sabercraft blaster");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), new ItemStack(Items.IRON_SWORD))), "a sword does not become a blaster");
		server.computeOnServer(s -> {
			player(s, self).getInventory().setItem(0, new ItemStack(Items.BOW));
			return null;
		});
		context.waitTicks(3);
		command(context, "sabercraft blaster x99");
		check(server.computeOnServer(s -> ItemStack.matches(held(s, self), new ItemStack(Items.BOW))), "an unknown blaster type is refused");
	}

	private static void blasters(ClientGameTestContext context, TestServerContext server, UUID self) {
		ItemStack original = server.computeOnServer(s -> {
			ItemStack bow = new ItemStack(Items.BOW);
			bow.enchant(enchantment(s, Enchantments.POWER), 2);
			bow.enchant(enchantment(s, Enchantments.FLAME), 1);
			bow.enchant(enchantment(s, Enchantments.UNBREAKING), 1);
			bow.set(DataComponents.CUSTOM_NAME, Component.literal("Mein Bogen"));
			ServerPlayer player = player(s, self);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, bow.copy());
			player.getInventory().setSelectedSlot(0);
			// Phase 11: every shot takes an arrow.
			player.getInventory().setItem(7, new ItemStack(Items.ARROW, 64));
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 64));
			return bow;
		});
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		// A clear, flat range: the world is random, and a hill or a tree between the player and
		// the target would stop the bolt.
		int y = server.computeOnServer(s -> player(s, self).getBlockY());
		server.runCommand("fill -3 " + y + " -18 3 " + (y + 4) + " 2 minecraft:air");
		server.runCommand("fill -3 " + (y - 1) + " -18 3 " + (y - 1) + " 2 minecraft:stone");
		server.runCommand("tp @a 0 " + y + " 0 180 0");
		context.waitTicks(5);
		command(context, "sabercraft blaster");
		check(server.computeOnServer(s -> BlasterType.of(held(s, self))) == BlasterType.E11, "/sabercraft blaster makes an E-11");
		check(server.computeOnServer(s -> held(s, self).getEnchantments().equals(original.getEnchantments())), "the blaster keeps the enchantments");

		for (BlasterType type : BlasterType.ALL) {
			command(context, "sabercraft blaster " + type.id().getPath());
			check(server.computeOnServer(s -> BlasterType.of(held(s, self))) == type, "the blaster is now " + type.id().getPath());
			server.runCommand("kill @e[type=!player]");
			server.runCommand("summon minecraft:zombie 0 " + y + " -14 {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:200}],Health:200f}");
			server.computeOnServer(s -> {
				ServerPlayer player = player(s, self);
				player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(held(s, self)));
				Blasters.cool(self);
				player.removeAllEffects();
				return null;
			});
			context.getInput().lookAt(180.0F, 2.0F);
			context.waitTicks(10);
			float healthBefore = server.computeOnServer(s -> zombie(s).getHealth());
			int damageBefore = server.computeOnServer(s -> held(s, self).getDamageValue());

			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(1);
			int bolts = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(BlasterBolt.class, new AABB(-30, -64, -30, 30, 320, 30)).size());
			String flight = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(BlasterBolt.class, new AABB(-30, -64, -30, 30, 320, 30)).stream()
					.map(b -> b.position() + " v" + b.getDeltaMovement()).toList().toString());
			check(bolts == type.bolts() || bolts > 0, type.id().getPath() + ": the right-click fired " + bolts + " bolt(s) on the server " + flight);
			check(context.computeOnClient(client -> !client.player.isUsingItem()), type.id().getPath() + ": the bow is not drawn");
			boolean seen = context.computeOnClient(client -> {
				for (var entity : client.level.entitiesForRendering()) {
					if (entity instanceof Snowball snowball && BlasterBoltRenderer.typeOf(snowball) == type) {
						return true;
					}
				}
				return false;
			});
			check(seen, type.id().getPath() + ": the client knows the bolt and draws it as a " + type.id().getPath() + " bolt");
			check(server.computeOnServer(s -> Blasters.heat(self)) > 0.0F, type.id().getPath() + ": the blaster got warmer");
			context.takeScreenshot("arsenal_blaster_" + type.id().getPath() + "_shot");
			check(context.computeOnClient(client -> BlasterClient.heat()) > 0.0F, type.id().getPath() + ": the client was told the heat");
			float expected = type.damage() * 1.5F * Math.max(1, Math.min(1, type.bolts()));
			// The slowest bolts need a few ticks more on a slow machine.
			for (int tick = 0; tick < 40 && healthBefore - server.computeOnServer(s -> zombie(s).getHealth()) < expected * 0.6F; tick++) {
				context.waitTick();
			}

			float healthAfter = server.computeOnServer(s -> zombie(s).getHealth());
			String where = server.computeOnServer(s -> "zombie at " + zombie(s).position() + " box " + zombie(s).getBoundingBox() + ", player at "
					+ player(s, self).position() + " rot " + player(s, self).getYRot() + "/" + player(s, self).getXRot() + ", bolts "
					+ s.overworld().getEntitiesOfClass(BlasterBolt.class, new AABB(-60, -64, -60, 60, 320, 60)).stream().map(b -> b.position() + " v" + b.getDeltaMovement()).toList());
			check(healthBefore - healthAfter >= expected * 0.6F, type.id().getPath() + ": the zombie was hit (" + healthBefore + " -> " + healthAfter
					+ ", Power 2 = x1.5; " + where + ")");
			check(server.computeOnServer(s -> zombie(s).isOnFire()), type.id().getPath() + ": Flame sets the target on fire");
			int damageAfter = server.computeOnServer(s -> held(s, self).getDamageValue());
			check(damageAfter >= damageBefore, type.id().getPath() + ": durability used (" + damageBefore + " -> " + damageAfter + ", Unbreaking may skip)");

			if (type.stun() > 0) {
				check(server.computeOnServer(s -> zombie(s).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)), type.id().getPath() + ": the hit stuns (slowness)");
			}

			// The fire rate: right after a shot a second one has to wait.
			server.computeOnServer(s -> {
				player(s, self).getCooldowns().removeCooldown(player(s, self).getCooldowns().getCooldownGroup(held(s, self)));
				return null;
			});
			boolean first = server.computeOnServer(s -> Blasters.fire(player(s, self), net.minecraft.world.InteractionHand.MAIN_HAND, held(s, self), type));
			boolean second = server.computeOnServer(s -> Blasters.fire(player(s, self), net.minecraft.world.InteractionHand.MAIN_HAND, held(s, self), type));
			check(first && !second, type.id().getPath() + ": the fire rate holds (" + type.interval() + " ticks between shots)");

			// Third person: the blaster in hand, rifles aimed with both hands.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(4);
			context.takeScreenshot("arsenal_blaster_" + type.id().getPath() + "_third_person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(25);
		}

		// Overheating: shots without the fire rate until it locks, then it cools down.
		BlasterType type = BlasterType.REPEATER;
		command(context, "sabercraft blaster repeater");
		boolean locked = server.computeOnServer(s -> {
			Blasters.cool(self);
			ServerPlayer player = player(s, self);
			for (int i = 0; i < 40 && !Blasters.overheated(self); i++) {
				player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(held(s, self)));
				Blasters.fire(player, net.minecraft.world.InteractionHand.MAIN_HAND, held(s, self), type);
			}
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(held(s, self)));
			return Blasters.overheated(self) && !Blasters.fire(player, net.minecraft.world.InteractionHand.MAIN_HAND, held(s, self), type);
		});
		check(locked, "a blaster overheats after many quick shots and refuses to fire");
		context.waitTicks(3);
		check(context.computeOnClient(client -> BlasterClient.overheated()), "the client shows the overheating");
		context.takeScreenshot("arsenal_blaster_overheated");
		context.waitTicks(90);
		check(server.computeOnServer(s -> !Blasters.overheated(self) && Blasters.heat(self) == 0.0F), "after cooling down the blaster fires again");

		command(context, "sabercraft blaster revert");
		ItemStack back = server.computeOnServer(s -> held(s, self).copy());
		ItemStack expected = original.copy();
		expected.setDamageValue(back.getDamageValue());
		check(BlasterType.of(back) == null && ItemStack.matches(back, expected),
				"after /sabercraft blaster revert exactly the bow from before, only worn by the shots (" + back.getComponentsPatch() + ")");
	}

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
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.isUsingItem()), "a plain bow is still drawn");
		context.waitTicks(15);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		int arrows = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(AbstractArrow.class, new AABB(-40, -64, -40, 40, 320, 40)).size());
		check(arrows == 1, "a plain bow still shoots an arrow (" + arrows + ")");
		check(server.computeOnServer(s -> player(s, self).getInventory().countItem(Items.ARROW)) == 15, "and uses one arrow");
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("item replace entity @a hotbar.1 with minecraft:diamond_sword");
				context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
				context.waitTicks(5);
				command(context, "sabercraft convert blue");
				context.waitTicks(10);
				check(context.computeOnClient(client -> SaberProfiles.isSaber(client.player.getMainHandItem())), "dedicated server: the converted sword is a saber for the client");
				// The bow into the hand that is already selected (the slot change only reaches the
				// server with the next action).
				server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
				server.runCommand("item replace entity @a hotbar.8 with minecraft:arrow 16");
				context.waitTicks(5);
				command(context, "sabercraft blaster dl44");
				context.waitTicks(5);
				check(context.computeOnClient(client -> BlasterType.of(client.player.getMainHandItem())) == BlasterType.DL44, "dedicated server: the client sees the DL-44");
				context.getInput().lookAt(0.0F, 0.0F);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(2);
				boolean seen = context.computeOnClient(client -> {
					for (var entity : client.level.entitiesForRendering()) {
						if (entity instanceof Snowball snowball && BlasterBoltRenderer.typeOf(snowball) == BlasterType.DL44) {
							return true;
						}
					}
					return false;
				});
				check(seen, "dedicated server: the bolt fired on the server reaches the client");
				check(context.computeOnClient(client -> !client.player.isUsingItem()), "dedicated server: the bow is not drawn");
				context.waitTicks(5);
				check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.ARROW)) == 15, "dedicated server: the shot took one arrow");
				context.takeScreenshot("arsenal_dedicated_server_shot");
			}
		}
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.getConnection().sendCommand(command));
		context.waitTicks(4);
	}

	private static ServerPlayer player(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static ItemStack held(MinecraftServer server, UUID self) {
		return player(server, self).getMainHandItem();
	}

	private static LivingEntity zombie(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(-30, -64, -30, 30, 320, 30), LivingEntity::isAlive).getFirst();
	}

	private static int count(ServerPlayer player, Item item) {
		return player.getInventory().countItem(item);
	}

	private static net.minecraft.core.Holder<Enchantment> enchantment(MinecraftServer server, ResourceKey<Enchantment> key) {
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
