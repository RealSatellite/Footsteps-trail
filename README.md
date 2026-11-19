# 👣 Footsteps Trail

The **source code and issue tracker** for [Footsteps Trail](https://modrinth.com/mod/footsteps-trail), a lightweight, client-side Minecraft mod that leaves fading footprints behind you as you explore.

The source here is published with each release and is licensed under the [MIT License](LICENSE).

[![Modrinth](https://img.shields.io/badge/Modrinth-Footsteps%20Trail-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/footsteps-trail)
[![CurseForge](https://img.shields.io/badge/CurseForge-Footsteps%20Trail-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/footsteps-trail)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/Wvst4znsgk)
[![YouTube](https://img.shields.io/badge/YouTube-Showcase-FF0000?logo=youtube&logoColor=white)](https://youtu.be/owqkAouaIs4)

### ✅ Official pages

Footsteps Trail is made by **Blue Print**. These are the only official pages:

| Where | Account |
| --- | --- |
| [CurseForge](https://www.curseforge.com/minecraft/mc-mods/footsteps-trail) | Blue_Print |
| [Modrinth](https://modrinth.com/mod/footsteps-trail) | [Mod_MC](https://modrinth.com/user/Mod_MC) |
| [GitHub](https://github.com/RealSatellite/Footsteps-trail) | RealSatellite |
| [YouTube](https://www.youtube.com/@Official-Satellite) | @Official-Satellite |
| [Discord](https://discord.gg/Wvst4znsgk) | Mod MC |

Any other upload of Footsteps Trail is not official.

---

## 🐞 Found a bug or a crash?

[**Open a bug report →**](../../issues/new/choose)

Before you post:

1. **Update to the latest version.** The bug may already be fixed.
2. **Search the [open issues](../../issues).** If someone already reported it, add a 👍 or a comment instead of opening a new one.
3. **Attach your log.** For a crash, upload the crash report from `.minecraft/crash-reports/`. For anything else, upload `.minecraft/logs/latest.log`. Use [mclo.gs](https://mclo.gs) or drag the file into the issue.

## 💡 Suggestions and feedback

Ideas, feature requests and general feedback are welcome. [Open a feedback issue](../../issues/new/choose), or talk about it on [Discord](https://discord.gg/Wvst4znsgk).

## 💬 Questions and help

Not sure whether it's a bug, or need help with a setting? Ask on **[Discord](https://discord.gg/Wvst4znsgk)**. That's the fastest way to get an answer.

---

## 📦 For modpack authors

**You're free to include Footsteps Trail in any modpack.** You don't need to ask, and credit is appreciated.

- **Client-side only.** It's safe on any server, and servers don't need it installed.
- **Works with popular rendering mods:** Sodium, Embeddium, Iris and Oculus (tested with shaders), Indium, Continuity/CTM connected textures, and Sinytra Connector (Forgified Fabric API). With renderers that replace chunk building, like OptiFine or VulkanMod, it automatically uses Surface footprints instead.
- **Configurable defaults.** Ship your own `config/footstepstrail.json` with the pack.
- **Modded blocks.** Map any block ID to a material (sand, snow, mud, stone, …) with `deformationMaterials`, so it takes footprints correctly.

If something conflicts with another mod in your pack, [open a bug report](../../issues/new/choose) and name the pack. Those reports get priority.

## 🧩 Supported versions

| Loader | Minecraft |
| --- | --- |
| Fabric | 1.20.1 · 1.21.1 · 26.3 |
| Forge | 1.12.2 · 1.20.1 |
| NeoForge | 1.21.1 · 26.3 |

---

## 📜 Source code

This repo has the full source of the mod's latest release (currently **1.5.3**). Each release is published here as a single commit, and development happens privately in between.

- **License:** [MIT](LICENSE). You can use, modify and redistribute it, as long as you keep the copyright and license notice.
- **Pull requests:** not accepted, since the code here is a release snapshot. Please report bugs or ideas through [issues](../../issues/new/choose) instead.

**Layout:** `common` holds the shared code. `fabric`, `forge`, `forge_1_12_2`, `fabric_1_21_1`, `neoforge_1_21_1`, `fabric_latest` and `neoforge_latest` are the loader/version builds.

**Building:** this is a multi-project Gradle build, so run `gradlew build` from the root. The modules target different Java versions (from Java 8 for Forge 1.12.2 up to Java 25 for 26.3) through Gradle toolchains, so you need those JDKs installed.

---

Made by **Blue print** · [Modrinth](https://modrinth.com/mod/footsteps-trail) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/footsteps-trail) · [Discord](https://discord.gg/Wvst4znsgk) · [YouTube](https://www.youtube.com/@Official-Satellite)
