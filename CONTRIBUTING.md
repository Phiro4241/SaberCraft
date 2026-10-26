# Contributing to SaberCraft

Thanks for your interest in SaberCraft! Bug reports, ideas and pull requests are all welcome. This page explains how to get set up and what a good contribution looks like.

> [!IMPORTANT]
> SaberCraft has **no open-source license yet** (`fabric.mod.json` says *All Rights Reserved*, see the [README](README.md#legal)). Please **open an issue before you start on a larger change**, so we can agree on it and on how it will be included.

## Reporting bugs

1. Check the [open issues](https://github.com/Phiro4241/SaberCraft/issues) first.
2. Open a [bug report](https://github.com/Phiro4241/SaberCraft/issues/new?template=bug_report.yml) and fill in:
   - Minecraft, Fabric Loader, Fabric API and SaberCraft versions
   - single player, LAN or server (and whether the server has SaberCraft)
   - other mods, especially Iris, Sodium or shader packs
   - steps to reproduce, what you expected and what happened
   - `logs/latest.log` (and a crash report from `crash-reports/` if there is one)
3. Screenshots or a short video help a lot for visual and animation bugs.

Please do **not** report security problems in public issues; see [SECURITY.md](SECURITY.md).

## Suggesting features

Open a [feature request](https://github.com/Phiro4241/SaberCraft/issues/new?template=feature_request.yml). Describe what you want to do in game and why; mockups or film references (described in words, no copyrighted files) are welcome. Planned work is listed in [docs/folgephasen.md](docs/folgephasen.md) and [docs/phase-11-starships.md](docs/phase-11-starships.md) (German).

## Setting up

You need **JDK 25** and Git. Everything else (Gradle, Minecraft, Fabric) is downloaded by the Gradle wrapper.

```bash
git clone https://github.com/Phiro4241/SaberCraft.git
cd SaberCraft
./gradlew build        # builds build/libs/sabercraft-<version>.jar
./gradlew genSources   # optional: decompiled Minecraft sources for your IDE
./gradlew runClient    # starts Minecraft with the mod
```

IntelliJ IDEA works well: open the folder as a Gradle project and use the generated run configurations.

### Project layout

```
src/main/       Code for client and server: items, abilities, blasters, droids, ships, duels, commands, networking
src/client/     Client only: blade rendering, animation, sounds, music, sky, menus, keys, HUDs
src/gametest/   Fabric client game tests with screenshots
tools/          Python scripts that generate models, textures, sounds, music, the icon and the banner
docs/           Development notes per phase (German), guides, images and media
```

Key places:

| Area | Where |
| --- | --- |
| Blade look | `src/client/.../client/blade/`, `client/render/` (`BladeMesh`, `SaberRenderTypes`, `ShaderCompat`) |
| Ignition, hum, holstering | `src/client/.../client/saber/`, `client/sound/` |
| Hilt and color on the item | `src/main/.../saber/SaberVariant.java` (stored in `minecraft:custom_model_data`) |
| Fighting styles and moves | `src/client/resources/assets/sabercraft/combat/moves.json`, `client/combat/` |
| Server-side abilities | `src/main/.../ability/` |
| Blasters | `src/main/.../blaster/`, `client/blaster/` |
| Droids and starships | `src/main/.../droid/`, `ship/`, `client/droid/`, `client/ship/` |
| Menu (key V) | `src/client/.../client/menu/` |
| Settings files | `AbilityConfig`, `CombatConfig`, `ImmersionConfig`, `CharacterConfig`, `BladeStyleConfig` (see [docs/configuration.md](docs/configuration.md)) |

### Assets are generated

Models, textures, sounds and music are **not** drawn or recorded by hand. They are produced by the scripts in `tools/` (they need Python 3 with `numpy` and `Pillow`, the sound and music scripts also `ffmpeg`), for example:

```bash
python3 tools/gen_hilts.py      # hilt models, textures and item model
python3 tools/synth_sounds.py   # all sound effects
python3 tools/gen_banner.py     # docs/media/banner.png
```

If you change an asset, change its generator and commit both. **Do not add assets you did not make yourself**: no ripped film sounds or music, no textures or models from other mods or packs, no official artwork or logos.

## Running the tests

The tests are Fabric client game tests: they start a real Minecraft client, create a world, press keys, click menus, check the results and take screenshots.

```bash
# Linux without a display
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest
# with a display (opens a game window)
./gradlew runClientGameTest
```

A full run takes about 30 to 40 minutes. Screenshots are written to `build/run/clientGameTest/screenshots/`. Test classes are listed in `src/gametest/resources/fabric.mod.json`.

New behavior should come with a test in `src/gametest/` that exercises it the way a player would (real key presses, real menu clicks), and a screenshot if it is visual.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep the change focused. Do not mix refactorings with features.
3. Match the surrounding code: tabs for indentation, English names and comments, comments that explain *why*.
4. Run `./gradlew build` and the game tests that cover your change locally. Say in the PR what you ran and what you could not test (for example multiplayer or shader packs).
5. Fill in the pull request template.

### Continuous integration

GitHub Actions builds the mod and runs the full client game test suite. The project runs on the free Actions quota, so please run the tests locally first and avoid pushing many small commits just to trigger CI. Changes that only touch documentation do not need a CI run.

## Style for documentation

- The README is in English, with a short German version in [README.de.md](README.de.md). The development notes in `docs/` are in German.
- Only describe what the code really does. Mark experimental features as such and name what is untested.

## Code of conduct

Be kind and constructive. Disagree about code, not about people. Harassment or discrimination is not tolerated.
