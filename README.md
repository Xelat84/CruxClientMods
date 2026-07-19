# Crux server — Wurm Unlimited client mods

A suite of client-side mods for Wurm Unlimited, built on the
[WurmClientModLauncher](https://github.com/ago1024/WurmClientModLauncher) by ago1024 (this repo is
a fork of it). Everything ships as one package so there is a single thing to install and update.

## Features

- **Auction house** — an in-client window for the server's player auction exchange.
- **Direct connect** — connect straight into a server without the JavaFX server-browser GUI, using
  connection details from system properties / environment variables.
- **Max actions** — pins the crafting action-queue size to a fixed value (up to 10) regardless of
  Mind Logic skill, matching the server.
- **Recipe examine** — adds an "Examine" entry to the right-click menu in the crafting recipe
  browser that opens the item's recipe card; also refreshes the recipe list on reconnect.
- **Stack placement** — lets the item placer target items already placed on a surface, so items can
  be stacked (e.g. an ingot on top of an ingot) instead of only dropped on the base surface.
- **Wide skill column** — widens the "Level" column of the skill window so longer values stay
  readable.
- Plus the launcher's own bundled mods: server-pack downloading (with versioning), custom map,
  and the connection fix.

## Install

Download the latest release from the
[Releases page](https://github.com/Xelat84/CruxClientMods/releases).

- **First time:** grab **`client-modlauncher-<version>.zip`** and unpack into game client folder. It contains the client patcher, modlauncher and all modifications for Crux server.
- **Updating Crux only:** grab **`crux-clientmod-<version>.zip`** and extract it into your client folder, overwriting the previous Crux files. (Or replace just the jar with the bare
  **`crux-clientmod.jar`**.)

## Building

Requires **JDK 8** and Maven:

```bash
mvn clean install -DskipTests
```

The build is fully self-contained — no external Maven hosts or secrets. The Wurm client API is
provided by signature-only stub jars committed under `repo/` (an in-project Maven repository), so
there is nothing else to install to compile.

## License / credits

Launcher, patcher, `connectionfix`, `custommap` and `serverpacks` originate from ago1024's
[WurmClientModLauncher](https://github.com/ago1024/WurmClientModLauncher); see that project for
their terms. The Crux mods and the client kit are original work in this repo.
