# Combat

A [Stein Loader](https://github.com/Stein-Project) mod for Minecraft 1.8.9, for PvP: allies and clans, the nearest
enemy and ally on the HUD, and fight automations. Everything is set up on its page in the Panel (Insert), where each
feature has its own key.

- **Team**: allies and enemies by nick or clan tag (the Panel, or a key aimed at a player), the members of your
  clan as allies, name colors by relation in the Tab list and above heads, and the lists from a JSON file at a URL
  (one per clan, with per-server sections). Hits on your clan are cancelled (and can go through to whoever is
  behind), and a key hides your clan from the screen.
- **Combat and movement**: No Hit Delay, Autoclick, toggle sprint, Keep Sprint and sprint in all directions.
- **Automations**: Auto Armor, Auto Refill (from the server's virtual chest), Smart Effect (golden apple, Speed and
  Strength) and Auto Protect (the sword blocking in menus and at low health).
- **HUD**: the nearest enemy, the nearest ally and the list of active features.

Some of these are things servers may detect and punish (the Panel says which); the player decides what to turn on.

## Download

Combat is a third-party mod: it is not on the Stein Client's official list. In the
[Stein Client](https://github.com/Stein-Project/stein-client/releases/latest), add its index under third-party mods
and the client installs and updates it for you:

```
https://github.com/brenoxavier/SteinCombat/releases/latest/download/steinmods.json
```

By hand: download `Combat.steinmod` from the
[latest release](https://github.com/brenoxavier/SteinCombat/releases/latest), put it in `.minecraft/mods` and play
with the **Stein Loader** profile.

## Building

You need **JDK 25 or newer**. The
[Stein SDK](https://github.com/Stein-Project/stein-sdk) Gradle plugin prepares Minecraft 1.8.9 for compiling the
first time, from your own installation or from Mojang.

```sh
./gradlew build            # build/libs/Combat.steinmod (also checks the formatting)
./gradlew spotlessApply    # format the code (google-java-format, AOSP style) and add the licence header
./gradlew installMod       # build and copy into .minecraft/mods
```

Before a Stein SDK release (developing the SDK and the mod together), build against a local SDK: in a clone of
[sdk](https://github.com/Stein-Project/sdk) run `./gradlew build publishPlugin`, then here
`./gradlew build -Pstein.sdkDir=<path to that clone>` (the plugin comes from its `build/maven-repo`, the SDK from
its `build/sdk/stein-sdk.jar`).

## Server plugin

`plugin/` is the clan plugin for server owners (Spigot 1.8.8, Java 8): `/clan` creates clans whose `[ABC]` tag shows
in everyone's Tab list and above heads, with no friendly fire, and whose `sc_ABC` scoreboard team the mod reads as
proof of a clan. It is a build of its own and is not released with the mod:

```sh
./gradlew -p plugin build  # plugin/build/libs/SteinCombat.jar
```

The Spigot API comes from Spigot's Maven repository; offline, pass a local jar with `-Pspigot.jar=<spigot-1.8.8.jar>`.

## Contributing

Bug reports, ideas and pull requests are welcome. Read the
[contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md) first; your first pull
request asks you to sign the [CLA](https://github.com/Stein-Project/.github/blob/main/CLA.md). For a change to this
mod, also check in the game, on a server with clans: the colors in the Tab list and above heads, marking an
ally and an enemy with the keys, the hit on a clanmate cancelled, the hide key, and the HUD elements.

Releases are made by the maintainer: a `v<version>` tag, with `changelog/<version>.en_US.md` (and optionally
`.pt_BR.md`) written first. Each stable release carries the mod's index, `steinmods.json` (see
`.github/workflows/release.yml`).

## Licence

Combat and its server plugin are licensed under the [Mozilla Public License 2.0](LICENSE). Third-party notices are
in [NOTICE](NOTICE). The Stein name and logo are not covered by the licence: see the
[trademark policy](https://github.com/Stein-Project/.github/blob/main/TRADEMARK.md).

Stein is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.

Created by [Breno Xavier](https://github.com/brenoxavier).
