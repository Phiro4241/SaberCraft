# Configuration reference

SaberCraft keeps its settings in JSON files in `config/sabercraft/` inside your Minecraft folder (for a server: next to the server JAR). Most of them can be changed in the in-game menu (key **V**), which writes these files for you.

How the files behave:

- They are created on the first start. Missing keys get their built-in value, and the file is written back so it always lists every setting.
- A file that cannot be read is reported in the log; the built-in values are used and the file is **not** overwritten, so you can fix it.
- Times are in **ticks** (20 ticks = 1 second). Multipliers of `1.0` mean the built-in value.
- Edit the files while the game or server is stopped, or use the menu.

This reference is taken from the source code (`AbilityConfig`, `CombatConfig`, `ImmersionConfig`, `CharacterConfig`, `BladeStyleConfig`). If it disagrees with the code, the code wins; please [open an issue](https://github.com/Phiro4241/SaberCraft/issues).

## `abilities.json` (server)

Decided by the server (in single player by the integrated server). Players connected to the server use the server's values.

| Key | Default | Meaning |
| --- | --- | --- |
| `guard_enabled` | `true` | Turns the defensive stance on or off. |
| `guard_block_share` | `0.6` | Share of a frontal hit that the stance blocks, 0..1. |
| `guard_parry_ticks` | `6` | Ticks after raising the stance in which a hit is parried completely. |
| `guard_max_ticks` | `100` | Longest time the stance can be held, in ticks. |
| `guard_cooldown` | `20` | Cooldown after lowering the stance, in ticks. |
| `guard_slowdown` | `-0.4` | Movement speed change while guarding, -1..0. |
| `spin_enabled` | `true` | Turns the spin attack on or off. |
| `spin_cooldown` | `120` | Cooldown of the spin attack, in ticks. |
| `spin_damage_share` | `0.6` | Damage of the spin as a share of the player's attack damage. |
| `spin_radius` | `3.0` | Reach of the spin attack, in blocks. |
| `whirl_cooldown` | `160` | Cooldown of the blade whirl (spin attack of the double-bladed hilt), in ticks. |
| `whirl_damage_share` | `0.3` | Damage of the blade whirl as a share of the attack damage. |
| `whirl_radius` | `3.5` | Reach of the blade whirl, in blocks. |
| `push_enabled` | `true` | Turns the Force push on or off. |
| `push_cooldown` | `240` | Cooldown of the Force push, in ticks. |
| `push_range` | `6.0` | Reach of the Force push, in blocks. |
| `push_strength` | `1.2` | Strength of the Force push, as a multiplier. |
| `push_players` | `true` | Whether the push affects other players. Only if PvP is on. |
| `dash_enabled` | `true` | Turns the dodge and lunge on or off. |
| `dash_cooldown` | `100` | Cooldown of the dodge and lunge, in ticks. |
| `dodge_speed` | `1.0` | Speed of the dodge moves (rolls, lunge, steps) as a multiplier of their built-in speed, 1 = a roll of about 3.5 blocks. Sent to the clients, which move their own player. |
| `dash_damage_share` | `0.5` | Damage of the stab of the lunge and the cut of the spin step, as a share of the attack damage. |
| `grip_enabled` | `true` | Turns the Force grip on or off. |
| `grip_cooldown` | `200` | Cooldown of the Force grip, in ticks. |
| `grip_range` | `10.0` | Reach of the Force grip, in blocks. |
| `grip_max_ticks` | `80` | Longest Force grip, in ticks. |
| `grip_cost` | `15.0` | Stamina cost of the Force grip. |
| `grip_drain` | `0.5` | Stamina per tick while holding a Force grip. |
| `force_players` | `false` | Whether grip and pull work on other players. Only if PvP is on. |
| `pull_enabled` | `true` | Turns the Force pull on or off. |
| `pull_cooldown` | `160` | Cooldown of the Force pull, in ticks. |
| `pull_range` | `12.0` | Reach of the Force pull, in blocks. |
| `pull_strength` | `1.0` | Strength of the Force pull, as a multiplier. |
| `pull_cost` | `25.0` | Stamina cost of the Force pull. |
| `jump_enabled` | `true` | Turns the Force jump on or off. |
| `jump_cooldown` | `100` | Cooldown of the Force jump, in ticks. |
| `jump_strength` | `1.0` | Height of the force jump as a multiplier, 1 = about 5 blocks. Sent to the clients. |
| `jump_cost` | `30.0` | Stamina cost of the Force jump. |
| `deflect_enabled` | `true` | Projectile deflection: the stance, a swing or the staff whirl send projectiles of the entity type tag `sabercraft:deflectable` back. Right after raising the stance or in the first ticks of a swing they fly where the player looks, otherwise they glance off. |
| `deflect_cost` | `6.0` | Stamina cost of the projectile deflection. |
| `stamina_enabled` | `true` | Stamina: blocking, dodging, the spin and the push cost stamina, which comes back after a short rest. Without stamina a block breaks and the other abilities wait. So no defense lasts forever. `false` turns stamina off completely. |
| `stamina_max` | `100.0` | Maximum stamina. |
| `stamina_regen` | `1.0` | Stamina regained per tick once the rest delay is over (20 ticks = 1 second). |
| `stamina_regen_delay` | `20` | Ticks without using stamina before it comes back. |
| `guard_cost` | `5.0` | Stamina cost of the defensive stance. |
| `block_cost_per_damage` | `6.0` | Stamina per point of damage a block takes, changed by the fighting style. |
| `parry_cost` | `4.0` | Stamina cost of the parry. |
| `dodge_cost` | `25.0` | Stamina cost of the dodge. |
| `spin_cost` | `30.0` | Stamina cost of the spin attack. |
| `push_cost` | `25.0` | Stamina cost of the Force push. |
| `parry_lockout` | `16` | Raising the stance again within this many ticks after the last raise gives no parry window, only a normal block. Stops parrying by tapping the key all the time. |
| `counter_ticks` | `30` | Ticks after a parry in which the next own hit is a counter with extra damage. |
| `counter_bonus` | `0.5` | Extra damage of the counter, 0.5 = +50 %. |
| `unbalance_ticks` | `30` | Ticks a parried attacker is off balance (slowed and weakened). |
| `guard_break_cooldown` | `50` | Cooldown of the stance after it was broken, in ticks. |
| `hilt_traits` | `true` | Hilts change the fight a little (damage, guard, parry window, knockback, dodge cooldown). `false`: every hilt plays like the standard one. |
| `duels_enabled` | `true` | Lightsaber duels with `/sabercraft duel <player>`, only between two players who both agreed. `false`: no new challenges, and running duels end. |
| `exhaustion` | `1.0` | Hunger cost (exhaustion) per ability use, like sprinting or jumping. |
| `commands_for_everyone` | `true` | Who may use `/sabercraft convert`, `revert` and `blaster`. `true`: every player, for the item in their own hand. `false`: only operators (permission level 2). |
| `blaster_ammo` | `true` | Every blaster shot needs and uses one plain arrow from the inventory (the off hand first, like a bow). Creative players need none. `false`: blasters fire without ammunition. |
| `blaster_infinity` | `true` | A blaster with Infinity needs one arrow but keeps it, like a bow with Infinity. `false`: Infinity does nothing on blasters, every shot uses an arrow. |
| `finisher_effect` | `true` | The blow that defeats a target with a saber (or ends a duel round) is a finisher: a flash and shock ring in the blade color and its own sound for everyone nearby. |
| `finisher_slow_motion` | `true` | In single player a finisher slows the game down for a moment (half a second at half speed). Never on a server with more than one player, and never while a `/tick rate` is set. |
| `finisher_cooldown` | `40` | Ticks before the same player can land the next finisher. |
| `blasters_enabled` | `true` | Blasters fire. `false`: converted bows do nothing on right-click (and stay blasters). |
| `blaster_damage` | `1.0` | Damage of all blaster bolts, as a multiplier of their built-in damage. |
| `blaster_heat` | `true` | Whether blasters heat up and overheat. `false`: only the fire rate limits them. |
| `blaster_cool_rate` | `2.0` | Heat lost per tick when not firing (out of 100). |
| `blaster_vent_rate` | `1.5` | Heat lost per tick while overheated; firing is blocked until it is down to 0. |
| `droids_enabled` | `true` | Players can call droid companions (menu or `/sabercraft droid`). |
| `droid_limit` | `4` | How many droids one player can have out at the same time (each kind at most once). |
| `droid_cooldown` | `1.0` | Multiplier for the cooldowns of the droid abilities. |
| `droids_hit_players` | `false` | Whether the combat droids (Chopper, B1, K-2SO) may hurt or push other players. |
| `ships_enabled` | `true` | Players can call and fly the X-Wing (menu or `/sabercraft ship`). |
| `ship_speed` | `1.0` | Multiplier for the top speed of the starships. |
| `ship_laser_damage` | `1.0` | Multiplier for the damage of the starship laser cannons. |
| `ship_lasers_hit_players` | `true` | Whether the starship laser cannons may hit other players. |

## `combat_client.json` (client)

| Key | Default | Meaning |
| --- | --- | --- |
| `combat_animations` | `true` | Own attack animations instead of the vanilla swing. |
| `ready_stance` | `true` | Resting and defensive poses while a lit saber is held. |
| `blade_trails` | `true` | Light trail behind the blade during fast movements. |
| `trail_strength` | `1.0` | Brightness of the light trail, 0..2. |
| `particles` | `1` | Amount of sparks and other particles: 0 = none, 1 = normal, 2 = more. |
| `hide_vanilla_sweep` | `true` | Hides vanilla's white sweep arc next to sabers, the light trail replaces it. |
| `ability_hud` | `true` | Shows the ability cooldowns next to the hotbar while a saber is held. |
| `camera_motion` | `1.0` | How much the first-person camera follows rolls and landings, 0 = not at all, 1 = full. |
| `landing_animation` | `true` | Landing animation after higher jumps. |
| `shader_blade` | `true` | While an Iris shader pack is active, draw the blade as a self-lit beam that the pack knows. Off: the plain blade pipelines, which most packs draw almost transparent. |
| `hit_marks` | `true` | Glowing marks in the blade color where a saber struck, for a few seconds. |
| `finisher_effects` | `true` | The shock ring and screen flash of a finishing blow (the sound always plays). |

## `immersion_client.json` (client)

| Key | Default | Meaning |
| --- | --- | --- |
| `space_sky` | `true` | The space sky: stars, nebulae, galaxies, planets and moons instead of the plain night sky. |
| `space_sky_quality` | `1` | Detail of the sky: 0 = low (256 px per side), 1 = medium (512), 2 = high (1024), 3 = ultra (1536). |
| `space_sky_in_end` | `true` | Also a space sky in the End, over the vanilla End sky. |
| `space_sky_with_shaders` | `false` | Keep the space sky while an Iris shader pack is active. Off by default: shader packs draw their own sky, and two skies on top of each other look wrong. |
| `planet` | `""` | Star Wars planet for the Overworld sky: `tatooine`, `coruscant`, `endor`, `hoth`, `mustafar`, `naboo`, or empty for the normal sky. |
| `planet_particles` | `true` | Snow on Hoth and ash on Mustafar drifting through the air. |
| `planet_horizon` | `true` | The far landscape on the planet's horizon and the details in its sky: dunes, giant trees, ice mountains, volcanoes, hills, air traffic, aurora, clouds. Only painted into the sky. |
| `planet_shader_tint` | `true` | With an Iris shader pack: an extra layer in the planet's sky color. Shader packs draw their own sky color, so without it a planet keeps little of its tone. |
| `planet_celestials` | `true` | The planet's own sun and moon: size and color, Tatooine's two suns in different colors, Mustafar's dim red sun, Hoth's small cold one. |
| `planet_transition_seconds` | `2.0` | Seconds a newly chosen planet takes to fade in over the old sky. |
| `music` | `true` | Background music of SaberCraft, instead of the vanilla music. |
| `music_volume` | `0.6` | Volume of the SaberCraft music, 0..1, on top of the master and music sliders. |
| `music_user_files` | `true` | Also play your own `.ogg` files from `config/sabercraft/music/<mood>/`. |
| `music_crossfade_seconds` | `4.0` | Length of the crossfade between two pieces, in seconds. |
| `music_stingers` | `true` | A short piece once after a won fight (victory) and when you fall (defeat), in both music modes. In the Star Wars mode only if there are files in `victory/` or `defeat/`. |
| `planet_ambient` | `true` | The sound of the planet chosen in the Sky menu while you are outdoors in the Overworld: desert wind on Tatooine, the blizzard on Hoth, lava on Mustafar, and so on. |
| `planet_ambient_volume` | `0.5` | Volume of the planet sounds, 0..1, on top of the master and the "Ambient/Environment" slider. |
| `music_mode` | `"sabercraft"` | Which music plays: `sabercraft` for the SaberCraft music above, or `starwars` for the Star Wars mode, which plays your own files from `config/sabercraft/starwars_music/`. The settings of the two modes are separate. |
| `starwars_volume` | `0.7` | Volume of the Star Wars mode, 0..1, on top of the master and music sliders. |
| `starwars_crossfade` | `true` | Crossfade between the tracks of the Star Wars mode; off = a short cut. |
| `starwars_combat` | `true` | Star Wars mode: music in fights, duels and boss fights. |
| `starwars_exploration` | `true` | Star Wars mode: music while exploring and in calm moments. |
| `starwars_character_themes` | `true` | Star Wars mode: a character's theme when they appear (you or another player). |
| `ship_third_person` | `true` | Switch to the third-person view behind the ship when climbing into a starship. |
| `ship_hud` | `true` | The flight display (speed, height, lasers) while flying a starship. |
| `blaster_draw` | `true` | Drawing and holstering a blaster are animated and heard. |
| `menu_transitions` | `true` | The menu fades a page in with a soft sweep when switching areas. |

## `character_client.json` (client)

| Key | Default | Meaning |
| --- | --- | --- |
| `outfit` | `""` | The outfit or character: empty for your plain skin, the id of a built-in outfit or character (`sabercraft:shadow`, `sabercraft:vader`), `profile:<name>` for a custom profile, or `file:<name>.png` for a file of your own in `config/sabercraft/outfits/`. |
| `show_other_outfits` | `true` | Show the outfits other players chose. Off: everyone else looks like their plain skin. |
| `character_size` | `true` | Draw small characters (Yoda) at their size. Only the picture changes, not the hitbox. |

## `blade_styles.json` (client)

One entry per blade color (`sabercraft:red`, `sabercraft:blue`, `sabercraft:green`, `sabercraft:purple`, `sabercraft:yellow`, `sabercraft:white`). Missing entries keep their default, invalid ones are reported in the log and ignored, and values are clamped to a sensible range.

```json
"sabercraft:blue": {
  "core_color": "#F2F7FF",
  "bloom_color": "#8CB8FF",
  "glow_color": "#1E5CFF",
  "length": 1.2,
  "core_radius": 0.022,
  "glow_radius": 0.12,
  "bloom_strength": 1.0,
  "glow_strength": 1.0,
  "shimmer": 0.04
}
```

## Folders for your own content (client)

| Folder | Content | Guide |
| --- | --- | --- |
| `profiles/` | Your own character profiles as `<name>.json`, optionally with a 64×64 skin PNG next to them | [custom-profiles.md](custom-profiles.md) (German) |
| `outfits/` | Your own outfit skins, chosen as `file:<name>.png` | |
| `characters/` | Skin files that replace the look of a built-in character | |
| `music/<mood>/` | Your own `.ogg` files for the built-in music mode | [eigene-musik.md](eigene-musik.md) (German) |
| `starwars_music/` | Your own `.ogg`/`.wav` files for the Star Wars music mode, by situation, planet and character, with `mapping.json` | [eigene-musik.md](eigene-musik.md) (German) |

Music files are only played on your own computer and are never sent to a server or to other players. SaberCraft ships no film music: add only files you are allowed to use.
