# Installing SaberCraft

*Platform-neutral guide to copy onto Modrinth, ModDB or a forum post. The same steps are in the [README](../../README.md#installation).*

## What you need

| | Version |
| --- | --- |
| Minecraft Java Edition | **26.2** (other versions do not work) |
| Fabric Loader | **0.19.5** or newer |
| Fabric API | for 26.2 (SaberCraft is built and tested with **0.161.0+26.2**) |
| Java | 25 (the Minecraft launcher brings it) |

No other mods are needed. Forge, NeoForge and Quilt are not supported.

## Single player

1. Download the **Fabric installer** from [fabricmc.net](https://fabricmc.net/use/installer/), run it, choose Minecraft **26.2** and click **Install**.
2. Download **Fabric API** for 26.2 and **SaberCraft** (`sabercraft-<version>.jar`, not the `-sources.jar`).
3. Put both JAR files into the `mods` folder of your Minecraft installation (create it if it does not exist):
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Remove older SaberCraft JARs from the folder.
5. Start the **Fabric** profile (`fabric-loader-…-26.2`) in the Minecraft launcher.
6. In a world: take a sword, press **I** and click **Make lightsaber**. The story mode is in the same menu, first tab **Story**.

## Server

1. Install the Fabric server for 26.2 (the Fabric installer has a **Server** tab).
2. Put Fabric API and SaberCraft into the server's `mods` folder.
3. Every player needs SaberCraft and Fabric API on their own client too.

Each player plays their own copy of the story on a server. Server settings are in `config/sabercraft/abilities.json`.

## Troubleshooting

- **"Incompatible mods" on start:** Fabric API is missing or for another Minecraft version, or the Minecraft version is not 26.2.
- **No lightsaber after pressing I:** hold a sword in your main hand; servers can limit which swords may become sabers.
- **Keys do nothing:** another mod may use the same key. Change it in Options → Controls → Key Binds → SaberCraft.
- **Still stuck?** Open an [issue](https://github.com/Phiro4241/SaberCraft/issues) with your versions and the file `logs/latest.log`.
