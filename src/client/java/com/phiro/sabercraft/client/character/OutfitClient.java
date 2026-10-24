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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.network.ChooseOutfitPayload;
import com.phiro.sabercraft.network.OutfitSyncPayload;

/**
 * The outfits on this client: your own choice, and the built-in outfits other players chose (as
 * the server tells). Other players only ever show what they chose themselves. Files of your own
 * ({@code config/sabercraft/outfits/*.png}, 64 x 64 in the skin layout) are only seen by you.
 *
 * <p>Since Phase 10 an outfit or character replaces the player's skin itself: the
 * {@link PlayerSkin} the game asks the player for ({@code AbstractClientPlayerMixin}) is the
 * character's, with its own texture, model shape and no cape or elytra of the own skin, and the
 * second layer that is shown is the character's ({@code AvatarMixin}). Before, the character was
 * painted as a slightly larger body over the own skin, so whatever of the own skin's second layer
 * was not hidden on purpose (the hat under outfits without a hood, the sleeves in first person,
 * the cape) stayed visible. Now there is just one skin per player, so nothing of the old one can
 * be left, and choosing your own skin again simply asks the game for it again.
 *
 * <p>The film characters (Phase 8) can be given a skin file of your own as well:
 * {@code config/sabercraft/characters/<character>.png} (for example {@code vader.png}) replaces the
 * built-in look of that character on this computer, for you and for everyone else who plays it.
 */
public final class OutfitClient {
	public static final String FILE_PREFIX = "file:";
	private static final Identifier CUSTOM_TEXTURE = SaberCraft.id("outfit_custom");
	private static final Map<UUID, Identifier> OTHERS = new HashMap<>();
	/** Skin files of your own for the film characters, by character; empty when there is none. */
	private static final Map<Identifier, Optional<Identifier>> CHARACTER_SKINS = new HashMap<>();
	private static @Nullable String loadedFile;
	private static @Nullable DynamicTexture customTexture;

	/** Skins made for a texture, so the game gets the same object every frame. */
	private static final Map<Identifier, PlayerSkin> SKINS = new HashMap<>();

	/**
	 * The skin a player is drawn with, and how big the figure is drawn (1 = normal, Phase 9).
	 * All characters and outfits are painted for the wide (Steve) model.
	 */
	public record Look(Identifier texture, float scale) {
		public Look(Identifier texture) {
			this(texture, 1.0F);
		}

		/** The complete skin: this texture, the wide model, and no cape or elytra. */
		public PlayerSkin skin() {
			return SKINS.computeIfAbsent(this.texture, id -> new PlayerSkin(new ClientAsset.ResourceTexture(id, id), null, null, PlayerModelType.WIDE, true));
		}
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

	/** Folder for skin files of your own for the film characters. */
	public static Path characterFolder() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("characters");
	}

	/** The file that replaces the look of a character, whether it exists or not. */
	public static Path characterFile(Outfit character) {
		return characterFolder().resolve(character.id().getPath() + ".png");
	}

	/** Whether a character is drawn with a skin file of your own. */
	public static boolean hasOwnSkin(Outfit character) {
		return character.character() && characterSkin(character) != null;
	}

	/** Forgets the loaded character skin files, so changed files are read again. */
	public static void reloadCharacterSkins() {
		SKINS.clear();

		for (Optional<Identifier> texture : CHARACTER_SKINS.values()) {
			texture.ifPresent(id -> Minecraft.getInstance().getTextureManager().release(id));
		}

		CHARACTER_SKINS.clear();
	}

	private static @Nullable Identifier characterSkin(Outfit character) {
		return CHARACTER_SKINS.computeIfAbsent(character.id(), id -> {
			try {
				Files.createDirectories(characterFolder());
			} catch (IOException error) {
				SaberCraft.LOGGER.warn("Could not create the character folder {}: {}", characterFolder(), error.toString());
			}

			Path file = characterFile(character);

			if (!Files.isRegularFile(file)) {
				return Optional.empty();
			}

			Identifier texture = SaberCraft.id("character_skin/" + id.getPath());
			DynamicTexture loaded = readSkin(file, "SaberCraft character " + id.getPath());

			if (loaded == null) {
				return Optional.empty();
			}

			Minecraft.getInstance().getTextureManager().register(texture, loaded);
			SaberCraft.LOGGER.info("Using your own skin {} for the character {}", file.getFileName(), id.getPath());
			return Optional.of(texture);
		}).orElse(null);
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
				return texture == null ? null : new Look(texture);
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
		Identifier own = outfit.character() ? characterSkin(outfit) : null;
		float scale = CharacterConfig.get().characterSize ? outfit.scale() : 1.0F;
		return new Look(own != null ? own : outfit.id().withPath(path -> "textures/outfit/" + path + ".png"), scale);
	}

	/** The look for a render state, found through the entity id it was made from. */
	public static @Nullable Look lookOf(AvatarRenderState state) {
		Minecraft client = Minecraft.getInstance();
		Entity entity = client.level == null ? null : client.level.getEntity(state.id);
		return entity == null ? null : look(entity.getUUID());
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

		customTexture = readSkin(file, "SaberCraft outfit " + name);

		if (customTexture == null) {
			return null;
		}

		Minecraft.getInstance().getTextureManager().register(CUSTOM_TEXTURE, customTexture);
		return CUSTOM_TEXTURE;
	}

	/** Reads a 64 x 64 PNG in the skin layout, or returns null (and logs why) when it cannot be used. */
	private static @Nullable DynamicTexture readSkin(Path file, String label) {
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);

			if (image.getWidth() != 64 || image.getHeight() != 64) {
				SaberCraft.LOGGER.warn("The skin file {} is {} x {} pixels; it needs to be 64 x 64 like a skin", file.getFileName(), image.getWidth(), image.getHeight());
				image.close();
				return null;
			}

			opaqueBase(image);
			return new DynamicTexture(() -> label, image);
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not read the skin file {}: {}", file.getFileName(), error.toString());
			return null;
		}
	}

	/**
	 * Makes the base layer of a skin file opaque, as vanilla does for every downloaded skin
	 * ({@code SkinTextureDownloader.processLegacySkin}): only the second layer may be see-through.
	 */
	private static void opaqueBase(NativeImage image) {
		int[][] base = {{0, 0, 32, 16}, {0, 16, 64, 32}, {16, 48, 48, 64}};

		for (int[] box : base) {
			for (int y = box[1]; y < box[3]; y++) {
				for (int x = box[0]; x < box[2]; x++) {
					image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
				}
			}
		}
	}

	/** The outfit the server said another player wears, for tests. */
	public static @Nullable Identifier otherOutfit(UUID player) {
		return OTHERS.get(player);
	}
}
