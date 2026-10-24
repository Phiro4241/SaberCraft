package com.phiro.sabercraft.client.character;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.network.ChooseOutfitPayload;
import com.phiro.sabercraft.network.OutfitSyncPayload;

/**
 * The outfits on this client: your own choice, and the built-in outfits other players chose (as
 * the server tells). An outfit is an extra layer over the normal skin ({@link OutfitLayer}); no
 * skin is replaced, and other players only ever show what they chose themselves. Files of your
 * own ({@code config/sabercraft/outfits/*.png}, 64 x 64 in the skin layout) are only seen by you.
 */
public final class OutfitClient {
	public static final String FILE_PREFIX = "file:";
	private static final Identifier CUSTOM_TEXTURE = SaberCraft.id("outfit_custom");
	private static final Map<UUID, Identifier> OTHERS = new HashMap<>();
	private static @Nullable String loadedFile;
	private static @Nullable DynamicTexture customTexture;

	/** What to draw over a player. */
	public record Look(Identifier texture, boolean coversHead) {
	}

	private OutfitClient() {
	}

	public static void init() {
		CharacterConfig.load();
		ClientPlayNetworking.registerGlobalReceiver(OutfitSyncPayload.TYPE, (payload, context) -> {
			if (payload.full()) {
				OTHERS.clear();
			}

			for (OutfitSyncPayload.Entry entry : payload.entries()) {
				if (entry.outfit().isPresent() && Outfit.byId(entry.outfit().get()).isPresent()) {
					OTHERS.put(entry.player(), entry.outfit().get());
				} else {
					OTHERS.remove(entry.player());
				}
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			OTHERS.clear();
			sendChoice();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OTHERS.clear());
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("outfits");
	}

	/** Your own outfit files, by name. */
	public static List<String> ownFiles() {
		Path dir = folder();

		try {
			Files.createDirectories(dir);

			try (Stream<Path> files = Files.list(dir)) {
				return files.map(file -> file.getFileName().toString())
						.filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
						.sorted()
						.limit(32)
						.toList();
			}
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not read the outfit folder {}: {}", dir, error.toString());
			return List.of();
		}
	}

	/** Chooses an outfit (see {@link CharacterConfig#outfit}), saves it and tells the server. */
	public static void choose(String outfit) {
		CharacterConfig.get().outfit = outfit;
		CharacterConfig.save();
		sendChoice();
	}

	private static void sendChoice() {
		Optional<Identifier> builtIn = builtIn(CharacterConfig.get().outfit).map(Outfit::id);

		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ChooseOutfitPayload.TYPE)) {
			// Files of your own stay on this computer: others then see your plain skin.
			ClientPlayNetworking.send(new ChooseOutfitPayload(builtIn));
		}
	}

	public static Optional<Outfit> builtIn(String choice) {
		Identifier id = choice.isEmpty() || choice.startsWith(FILE_PREFIX) ? null : Identifier.tryParse(choice);
		return id == null ? Optional.empty() : Outfit.byId(id);
	}

	/** The look of a player, or null for the plain skin. */
	public static @Nullable Look look(UUID player) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null && client.player.getUUID().equals(player)) {
			String choice = CharacterConfig.get().outfit;

			if (choice.startsWith(FILE_PREFIX)) {
				Identifier texture = customTexture(choice.substring(FILE_PREFIX.length()));
				return texture == null ? null : new Look(texture, false);
			}

			return builtIn(choice).map(OutfitClient::lookOf).orElse(null);
		}

		if (!CharacterConfig.get().showOtherOutfits) {
			return null;
		}

		Identifier other = OTHERS.get(player);
		return other == null ? null : Outfit.byId(other).map(OutfitClient::lookOf).orElse(null);
	}

	public static Look lookOf(Outfit outfit) {
		return new Look(outfit.id().withPath(path -> "textures/outfit/" + path + ".png"), outfit.coversHead());
	}

	/** Loads a file of your own once (64 x 64 PNG), or null when it cannot be used. */
	private static @Nullable Identifier customTexture(String name) {
		if (name.equals(loadedFile)) {
			return customTexture == null ? null : CUSTOM_TEXTURE;
		}

		loadedFile = name;
		Path file = folder().resolve(name);

		if (customTexture != null) {
			Minecraft.getInstance().getTextureManager().release(CUSTOM_TEXTURE);
			customTexture = null;
		}

		if (!file.normalize().startsWith(folder().normalize()) || !Files.isRegularFile(file)) {
			return null;
		}

		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);

			if (image.getWidth() != 64 || image.getHeight() != 64) {
				SaberCraft.LOGGER.warn("The outfit {} is {} x {} pixels; it needs to be 64 x 64 like a skin", name, image.getWidth(), image.getHeight());
				image.close();
				return null;
			}

			customTexture = new DynamicTexture(() -> "SaberCraft outfit " + name, image);
			Minecraft.getInstance().getTextureManager().register(CUSTOM_TEXTURE, customTexture);
			return CUSTOM_TEXTURE;
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not read the outfit {}: {}", name, error.toString());
			return null;
		}
	}

	/** The outfit the server said another player wears, for tests. */
	public static @Nullable Identifier otherOutfit(UUID player) {
		return OTHERS.get(player);
	}
}
