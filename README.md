# The Stalker 1.1 (Fabric, Minecraft Java 1.20.1)

A psychological / liminal horror mod. Something tall, pale and silent has noticed you.

## Build
1. Install JDK 17.
2. This project has no Gradle wrapper. Either copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the
   Fabric example mod, or run `gradle wrapper --gradle-version 8.5` once.
3. `./gradlew build` -> jar in `build/libs/`. Drop it in `mods/` with Fabric API 0.92.2+1.20.1.
   (`./gradlew runClient` launches a dev client.)

## How it works
Each player has a hidden **dread** meter. It rises while you are underground, in the dark, or out at night,
and falls in bright daylight, while sleeping, or when other players are within 24 blocks.

| Stage | Behaviour |
|-------|-----------|
| 0 Sighting | Stands 30-50 blocks away in plain sight. Stare at it for ~3s and it vanishes. |
| 1 Stalking | Follows at 14-24 blocks, ducks behind cover whenever you look at it. |
| 2 Hunting  | Freezes while watched, creeps closer when you look away, lunges if it reaches you. Stare it down ~10s and it retreats. Snuffs torches, pulses darkness, heartbeat. |
| 3 The Kill | Drops to all fours and chases at ~sprint speed for 100s. Bright light slows it. Survive, wound it (<30% HP) or kill it (80 HP) and it backs off, then returns later. |

From stage 1 on you also hear things that aren't there: doors, chests, footsteps, a creeper hiss, always behind you.

## Test commands (op)
- `/stalker summon`    spawn it near you now
- `/stalker stage <0-3>` jump to a stage
- `/stalker reset`

Tune thresholds, hunt length and speed in `StalkerConfig.java`.
Regenerate the textures with `python3 tools/make_textures.py` (needs Pillow).

## Notes
- It does not spawn in Peaceful (hostile mobs are removed there). Creative players are valid targets but take no damage.
- The Stalker and dread are not saved: restarting the world resets the stalking.

## Flashlight
Craft: glass pane on top, iron / redstone / iron in the middle row, iron ingot below (`" G "`, `"IRI"`, `" I "`).
Right-click to toggle. While on it fakes dynamic light with invisible light blocks that follow your aim.
Battery lasts 10 minutes of use; repair it with redstone in an anvil. It never breaks, it just goes dark.
Shine it on the Stalker while it hunts you and it slows to 40% speed and attacks half as often.
(If the server crashes while a flashlight is on, a stray invisible light block can be left behind;
`/give @s light` shows them so you can break them.)

## Nightmares
When you get into bed there is a 12% chance of a nightmare, rising to ~80% with high dread (5% for a second try the same night).
Sounds creep closer, then you are woken early (the night is NOT skipped) to one of:
it standing at the foot of your bed, your torches snuffed out and the room gone black, or it outside the window.
The figure is a harmless apparition that fades if you look at it. Nightmares add dread.

## Playing it in Modrinth
Easiest: in the Modrinth App, create an instance with Fabric, Minecraft 1.20.1. In the instance's Content tab,
add **Fabric API** from Modrinth, then "Add content > From file" and pick `the-stalker-1.1.0.jar`.

One-click: after building, run `python3 tools/make_mrpack.py` (needs internet). It makes `the-stalker-1.1.0.mrpack`
(Fabric loader + Fabric API + this mod). In the Modrinth App: Create instance > Import from file.

No JDK? Push this folder to a GitHub repo; the included workflow builds the jar and the .mrpack
(Actions tab > latest run > Artifacts).

Publishing on Modrinth: upload the jar as a new version, loader Fabric, game version 1.20.1,
dependency Fabric API (required). The icon is `src/main/resources/assets/stalker/icon.png`.
