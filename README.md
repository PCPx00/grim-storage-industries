# Grim Storage Industries

Dead-simple storage for NeoForge 1.21.1 that still scales to end game. No channels, no byte/type limits,
no cable rules. Part of the Grim Industries family with **Grim Soul Industries** and **Grim Pipe Industries**.

Made by **xTri6** for the Nine Craft modpack.

## Status: 0.1.0 (in development)

Phase 1 is done: the **Soulite** material line that every tier of the mod is built from.

| Block / item | Source |
|---|---|
| Soulite Ore, Deepslate Soulite Ore | Overworld, Y -48 to 16 (most common near Y -16). Iron pickaxe. |
| Soul Sand Soulite Ore | Soul Sand Valleys, inside soul sand and soul soil. Drops 2-3 Raw Soulite. Iron pickaxe. |
| Echo Soulite Ore | Deep Dark only, Y -60 to -10. Drops Echo Soulite Shards. Diamond pickaxe. |
| Raw Soulite, Soulite Ingot, Nugget, Dust, blocks | Smelt or blast raw ore; standard 9x compression. |
| Infused Soulite | Soul Infuser (coming next). |
| Echo Soulite Alloy | Infused Soulite + 2 Echo Soulite Shards + Netherite Scrap (shapeless). |
| Singularity Core | 8 Echo Soulite Alloy around a Nether Star. |

All ores support Fortune and Silk Touch, glow faintly, and break with a crystal chime.

### For pack makers

Everything uses the common `c:` tags (`c:ores/soulite`, `c:ingots/soulite`, `c:raw_materials/soulite`,
`c:dusts/soulite`, `c:gems/echo_soulite`, `c:storage_blocks/soulite`, ...), and every recipe, loot table and
ore feature is plain datapack JSON. To change or disable ore generation, override the files in
`data/grimstorage/worldgen/placed_feature/` or `data/grimstorage/neoforge/biome_modifier/` from a datapack or KubeJS.

## Roadmap

1. Soulite material line and worldgen (done)
2. Soul Infuser
3. Network Controller, storage cells (4k to Singularity), terminal
4. Recipe Cards, Assembler, keep-in-stock autocrafting
5. Wireless terminal, Processing Interface, Quantum Link

## Building

Install JDK 21, then double-click `BUILD.bat` (Windows) or run `./gradlew build`. The jar lands in `build/libs/`.

Dev check: `./gradlew runOreCheck` boots a server, generates terrain in all three Soulite locations and logs
PASS/FAIL with the ore counts. It never ships in the release jar.

## License

All Rights Reserved. See [LICENSE](LICENSE).
