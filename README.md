# Combat

A Stein Loader mod for Minecraft 1.8.9, for PvP: allies and clans, the nearest enemy and ally on the HUD, and
fight automations. Everything is set up on its page in the Panel (Insert), where each feature has its own key.

- **Team**: allies and enemies by nick or clan tag (the Panel, or a key aimed at a player), the members of your
  clan as allies, name colors by relation in the Tab list and above heads, and the lists from a JSON file at a URL
  (one per clan, with per-server sections). Hits on your clan are cancelled (and can go through to whoever is
  behind), and a key hides your clan from the screen.
- **Combat and movement**: No Hit Delay, Autoclick, toggle sprint, Keep Sprint and sprint in all directions.
- **Automations**: Auto Armor, Auto Refill (from the server's virtual chest), Smart Effect (golden apple, Speed and
  Strength) and Auto Protect (the sword blocking in menus and at low health).
- **HUD**: the nearest enemy, the nearest ally and the list of active features.

Some of these are things servers may detect and punish (the Panel says which); the player decides what to turn on.

This repository is **private** and the mod is proprietary (see [LICENSE](LICENSE)). Players get the `.steinmod`
from its public release repository (the `RELEASE_REPO` variable of this repository, see below) through the
[Stein Client](https://github.com/Stein-Project/stein-client/releases/latest) and the mods index of
[Stein-Project/stein-loader](https://github.com/Stein-Project/stein-loader).

## Building

JDK 17 or newer to run Gradle (the JDK 25 the mod compiles with is downloaded if missing). The
[Stein SDK](https://github.com/Stein-Project/stein-sdk) Gradle plugin prepares Minecraft 1.8.9 for compiling the
first time, from your own installation or from Mojang.

```sh
./gradlew build            # build/libs/Combat.steinmod (also checks the formatting)
./gradlew spotlessApply    # format the code (google-java-format, AOSP style) and add the licence header
./gradlew installMod       # build and copy into .minecraft/mods
```

Before a Stein SDK release, build against the local SDK (`../stein-project/sdk`, built there with
`./gradlew build publishPlugin`): `./gradlew build -Pstein.sdkDir=../stein-project/sdk`.

Combat calls the Loader's engine switches in `dev.xavier.stein.loader.internal.Combat`, which only the official mods
use and which the SDK's API jar leaves out: the mod compiles against the signatures in `src/stubs` (never packed).

Before a release, check in the game, on a server with clans: the colors in the Tab list and above heads, marking an
ally and an enemy with the keys, the hit on a clanmate cancelled, the hide key, and the HUD elements.

## Server plugin

`plugin/` is the clan plugin for server owners (Spigot 1.8.8, Java 8): `/clan` creates clans whose `[ABC]` tag shows
in everyone's Tab list and above heads, with no friendly fire, and whose `sc_ABC` scoreboard team the mod reads as
proof of a clan. It is a build of its own and is not released with the mod:

```sh
./gradlew -p plugin build  # plugin/build/libs/SteinCombat.jar
```

The Spigot API comes from Spigot's Maven repository; offline, pass a local jar with `-Pspigot.jar=<spigot-1.8.8.jar>`.

## Release

A `v<version>` tag (equal to `"version"` in `src/main/resources/steinmod.json`), with
`changelog/<version>.en_US.md` (and optionally `.pt_BR.md`) written first. Running the workflow by hand is a dry run. See
`.github/workflows/release.yml` for the settings it needs: the `RELEASE_REPO` variable (the **public** repository
that hosts the downloads: x4vieer/stein-combat for now) and the `RELEASE_TOKEN` and `DIST_TOKEN` secrets.

## Licence

Proprietary, all rights reserved: see [LICENSE](LICENSE) and [NOTICE](NOTICE). The Stein name and logo are covered by
the [trademark policy](https://github.com/Stein-Project/.github/blob/main/TRADEMARK.md).

Stein is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.

Created by [Breno Xavier](https://github.com/brenoxavier).
