package com.phiro.sabercraft.story;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: the story's files in the world: {@code sabercraft/story/<uuid>.json} (the progress)
 * and {@code sabercraft/story/<uuid>.stash.dat} (the player's own inventory while they play the
 * story). Both are written to a temporary file first and then moved over the old one, so a crash
 * never leaves half a file.
 */
public final class StoryStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private StoryStore() {
	}

	static Path folder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("sabercraft").resolve("story");
	}

	/** The progress of a player (a new one when there is none or it cannot be read). */
	static StoryProgress load(MinecraftServer server, UUID player) {
		Path file = folder(server).resolve(player + ".json");

		if (!Files.isRegularFile(file)) {
			return new StoryProgress();
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			StoryProgress progress = GSON.fromJson(reader, StoryProgress.class);
			return progress != null ? progress : new StoryProgress();
		} catch (IOException | RuntimeException error) {
			SaberCraft.LOGGER.warn("Could not read the story progress {}", file, error);
			return new StoryProgress();
		}
	}

	/** Whether a player has a saved progress. */
	static boolean exists(MinecraftServer server, UUID player) {
		return Files.isRegularFile(folder(server).resolve(player + ".json"));
	}

	/** Writes a player's progress. */
	static void save(MinecraftServer server, UUID player, StoryProgress progress) {
		Path file = folder(server).resolve(player + ".json");

		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(player + ".json.tmp");

			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(progress, writer);
			}

			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not save the story progress {}", file, error);
		}
	}

	/** Removes a player's progress (to start the story anew). */
	static void delete(MinecraftServer server, UUID player) {
		try {
			Files.deleteIfExists(folder(server).resolve(player + ".json"));
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not delete the story progress of {}", player, error);
		}
	}

	// --- The stash ---------------------------------------------------------------------------------

	private static Path stashFile(MinecraftServer server, UUID player) {
		return folder(server).resolve(player + ".stash.dat");
	}

	/** Whether a player's own inventory is in the stash. */
	static boolean hasStash(MinecraftServer server, UUID player) {
		return Files.isRegularFile(stashFile(server, player));
	}

	/**
	 * Puts the player's whole inventory (main, armour, off hand) into the stash file and empties
	 * it. Refuses (returns false, leaving everything as it is) if there is a stash already or it
	 * cannot be written: nothing may be lost.
	 */
	static boolean stash(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Path file = stashFile(server, player.getUUID());

		if (Files.exists(file)) {
			return false;
		}

		Inventory inventory = player.getInventory();
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ListTag items = new ListTag();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
			CompoundTag entry = new CompoundTag();
			entry.putInt("slot", slot);
			entry.put("item", encoded);
			items.add(entry);
		}

		CompoundTag root = new CompoundTag();
		root.put("items", items);
		root.putInt("xp", player.totalExperience);

		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(player.getUUID() + ".stash.dat.tmp");
			NbtIo.writeCompressed(root, temp);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException error) {
			SaberCraft.LOGGER.error("Could not stash the inventory of {}", player.getPlainTextName(), error);
			return false;
		}

		inventory.clearContent();
		return true;
	}

	/**
	 * Gives the stashed inventory back (what the player holds now, from the story, is dropped
	 * first) and removes the stash. Returns whether there was one.
	 */
	static boolean unstash(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Path file = stashFile(server, player.getUUID());
		@Nullable CompoundTag root;

		try {
			root = Files.exists(file) ? NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()) : null;
		} catch (IOException error) {
			SaberCraft.LOGGER.error("Could not read the stashed inventory of {}; it stays in {}", player.getPlainTextName(), file, error);
			return false;
		}

		if (root == null) {
			return false;
		}

		Inventory inventory = player.getInventory();
		inventory.clearContent();
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);

		for (Tag tag : root.getListOrEmpty("items")) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}

			int slot = entry.getIntOr("slot", -1);
			Tag item = entry.get("item");

			if (item == null || slot < 0 || slot >= inventory.getContainerSize()) {
				continue;
			}

			ItemStack.CODEC.parse(ops, item).resultOrPartial(error -> SaberCraft.LOGGER.warn("A stashed item could not be read: {}", error))
					.ifPresent(stack -> inventory.setItem(slot, stack));
		}

		try {
			Files.move(file, file.resolveSibling(player.getUUID() + ".stash.returned.dat"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not move the returned stash {}", file, error);
		}

		return true;
	}
}
