# RTX Remix for Minecraft 26.3

Play Minecraft Java Edition 26.3 with real-time path tracing on a GeForce RTX
GPU, powered by NVIDIA RTX Remix.

This is an unofficial community port of
[NVIDIA's RTX Remix for Minecraft](https://github.com/NVIDIAGameWorks/rtx-remix-minecraft),
which targets Minecraft 1.21.1, to Minecraft 26.3. NVIDIA does not maintain or
support this port. Report problems here, not upstream.

## Status

This is a pre-release. It loads worlds and path-traces terrain, water, sky,
entities, particles and the first-person hands. Clouds, block entities, the
block outline, the Copper Beacon, camera paths and the in-game settings are
ported but have had little testing on 26.3.

Not ported yet:

- **Rain and snow streaks.** Remix's weather lighting still applies, so
  surfaces get wet and the sky darkens, but the falling streaks and flakes are
  not drawn over the Remix image.
- **Flames on held items.** Torches and lanterns in hand show no flame.
- **Particles that use item textures.** Block and particle-atlas particles
  render; item-textured ones are skipped.
- **Bed materials.** Beds get no RTX materials yet.
- **The automated tests and most docs.** These still describe the 1.21.1
  build.
- **ARM64.** Releases carry the x64 runtime only.

The upstream mod was tuned against one showcase world, and blocks, mobs and
dimensions that world never reaches have had less care. The same holds here,
plus whatever 26.3 changed that this port has not met yet.

## Download

Get the release archive from this fork's
[Releases page](https://github.com/yeezybeezydeezy/rtx-remix-minecraft/releases).
It holds the installer and the mod jar, which carries the RTX Remix runtime.

## Install

1. Extract the release archive.
2. Double-click `install.bat` and follow the menu.
3. Launch the **NVIDIA RTX Remix for Minecraft** instance in Prism Launcher,
   or the profile of the same name in the Microsoft launcher.

The installer sets up Fabric Loader 0.20.1, Fabric API and, where needed,
Java 25 and a portable Prism Launcher. Running it over an install of the
upstream 1.21.1 release moves that Prism instance to 26.3; back up its worlds
first. The archive's `README.md` and `INSTALLER_REFERENCE.md` cover every
option.

### Installing By Hand

To add the mod to a Fabric instance you already have, for example in
[Prism Launcher](https://prismlauncher.org/):

1. Create an instance for Minecraft 26.3 with Fabric Loader 0.20.1 or newer.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) 0.165.0+26.3 or
   newer.
3. Put Fabric API and the mod jar, from the archive's `mods` folder, in the
   instance's `mods` folder.
4. Launch. The first start unpacks the RTX Remix runtime, about 1 GB, into
   the game folder, so it takes longer than usual.

## Requirements

- Windows 10 or 11, x64. This port has no ARM64 build yet.
- Minecraft Java Edition 26.3, on a Microsoft account that owns the game.
- Java 25. Minecraft 26.3 ships with it, and launchers pick it automatically.
- A GeForce RTX 30-series or newer GPU with NVIDIA driver 616.92 or newer.
- About 3 GB of free disk space while installing; about 2 GB stays in use.
- An internet connection while the installer runs.
- 16 GB of system memory is recommended.

## Showcase World And Art Pack

Upstream offers two optional extras, and both load on 26.3:

- **The showcase world**, the map the renderer was tuned against, was built for
  the upstream project by BlockWorks: [website](https://www.blockworks.uk/),
  [YouTube](https://www.youtube.com/@BlockWorksYT),
  [X](https://x.com/BlockWorksYT), and
  [Instagram](https://www.instagram.com/blockworks/). Minecraft 26.3 upgrades
  it the first time it opens; keep a copy if you want the original.
- **The RTX art pack**, a LabPBR texture resource pack made by an art team at
  NVIDIA. It was made for 1.21.1, so Minecraft lists it as made for an older
  version; enable it anyway.

The developer launcher in [`tools/`](tools/README.md) downloads both, but it
still targets the 1.21.1 build. To install them into a 26.3 instance by hand,
pull the packages pinned in [`tools/config.json`](tools/config.json) with
Packman, then:

- copy the world into the instance's `saves` folder,
- copy the art pack into its `resourcepacks` folder, and
- copy the world package's `cameras` files into
  `camera-paths/<save folder name>/` in the game folder, for the flythroughs.

## What It Does

**Every block, mob, and item is lit by a path tracer.** Light bounces off walls,
through glass, and out of torches the way it does in a film renderer, so shadows,
reflections, and the colour a nearby block casts on its neighbours are all
computed rather than painted in.

**The look follows the game.** Time of day, weather, and being underwater each
bring their own lighting, and a map author can attach a look to a place in the
world.

**A new block.** The Copper Beacon is a directional lamp you can place or
carry, and its beam is a real light that picks out walls, rain, and anything
standing in it.

**Camera flythroughs.** Record a path by walking it, then replay it, for a video
or for an exact before-and-after comparison.

**A settings screen in the game.** Distances, framerate, sky, lighting,
exposure, and materials, all in Options, with a button into Remix's own
Graphics Settings for the quality tier.

## Crash Reports And Privacy

The mod collects no data of its own. The RTX Remix runtime it carries can send
basic session information and crash reports to NVIDIA. Its First Use Guide asks
about both, with both on by default, and the Permissions tab of the RTX Remix
Graphics Settings menu changes them at any time.
[Crash Reports And Privacy](docs/PLAYING.md#crash-reports-and-privacy) lists
what is sent.

## Building From Source

Development happens on the `port/26.3` branch. You need:

- JDK 25.
- Visual Studio 2022 or newer with the C++ workload, for the JNI bridge.
- An internet connection the first time, for Gradle dependencies and the
  Packman-hosted Remix runtime.

Build the win64 release archive, installer and jar, with:

```bash
.\gradlew.bat releaseArchive -Parch=win64 -x test -x compileTestJava
```

The archive and jar land in `build/distributions/`. Tests are skipped
because they do not compile on 26.3 yet. If JDK 25 is installed somewhere
Gradle does not search, point an environment variable such as `JDK25` at it
and add `-Porg.gradle.java.installations.fromEnv=JDK25`.

[Getting Started](docs/GETTING_STARTED.md) covers the full development setup,
but it still describes the 1.21.1 build.

### What Changed For 26.3

- The build moved to unobfuscated 26.3 with Mojang names, Fabric Loom 1.19,
  Gradle 9.8 and Java 25.
- Remix attaches to 26.3's SDL3 window and presents in place of the OpenGL
  backend's buffer swap.
- Terrain, entity, block entity, first-person, particle, sky, cloud and
  outline capture follow 26.3's extract, submit and frame-graph rendering.
- Animated textures are forwarded from 26.3's GPU-side sprite animation, and
  atlas copies account for its per-sprite padding.
- Mixins whose purpose 26.3 made redundant, mostly vanilla performance
  patches, were dropped.

## Source Layout

| Path | Purpose |
|---|---|
| `src/` | Mod implementation and JVM tests |
| `rtxremix-java/` | Minecraft-agnostic Java facade and JNI bridge over the RTX Remix C API |
| `tools/` | Developer launcher, benchmarks, profiling, SDK staging, and development installation |
| `packman/` | Packman bootstrap and runtime package pins |
| `packaging/` | Installer, runtime defaults, and distribution payload |
| `docs/` | Player, authoring, and source-build guides, written for upstream's 1.21.1 build |

## Contributing

Fixes for the gaps listed under [Status](#status) are the most useful
contributions. Fork this repository, branch from `port/26.3`, and open a pull
request against it. Sign off each commit with `git commit -s` to certify it
under the [Developer Certificate of Origin](https://developercertificate.org/).
Changes that also apply to 1.21.1 belong upstream; see
[CONTRIBUTING.md](CONTRIBUTING.md) for upstream's guidelines.

## License

This project is licensed under the Apache License, Version 2.0 (`Apache-2.0`).
See the [LICENSE](LICENSE) file. Attribution notices are in [NOTICE](NOTICE),
and third-party software distributed with this project is documented in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Release jars also carry
the RTX Remix runtime, whose NVIDIA components are covered by their own license
terms; THIRD_PARTY_NOTICES.md lists them.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
MICROSOFT. Minecraft is a trademark of Mojang Synergies AB.

NVIDIA, GeForce, and RTX are trademarks and/or registered trademarks of NVIDIA
Corporation in the U.S. and other countries. This port is not affiliated with
or endorsed by NVIDIA.
