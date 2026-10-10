# Grim Storage Industries

Dead-simple storage for NeoForge 1.21.1 that still scales to end game, with autocrafting built in.
No channels, no byte/type limits, no cable rules: anything touching the network joins it.
Part of the Grim Industries family with **Grim Soul Industries** and **Grim Pipe Industries**.

Made by **xTri6** for the Nine Craft modpack.

## Getting started

Every player gets a **Grim Storage Guide** the first time they join. It explains everything below, with live recipes.
Lost it? Craft one from a book and a Soulite Ingot.

1. Mine **Soulite** (Overworld Y -48 to 16) and smelt it into ingots.
2. Craft a **Network Controller**, a **Soul Drive** and a **Copper Storage Cell**. Put the cell in the drive.
3. Place a **Soul Terminal** touching the network. Open it and start storing.
4. Use **Soul Cable** or **Soulite Glass** to reach blocks that are not touching.

## Blocks

| Block | What it does |
|---|---|
| Network Controller | The heart of a network. Exactly one per network. Open it to see storage use and crafting jobs (and cancel them). |
| Soul Drive | Holds 8 storage cells. Cells keep their contents when moved. |
| Soul Terminal | Browse, search (`@mod` searches by mod), filter with the side tabs (ores, ingots, tools, armor, food, mob drops, farming, building, misc), take, store, order crafts, and craft by hand in the built-in 3x3 grid that refills itself from storage. |
| Soul Cable / Soulite Glass | Connect network blocks. The glass is decorative and see-through. |
| Soul Assembler | Holds 9 Recipe Cards and runs them. Up to 4 Speed Upgrades, each doubling its speed. |
| Processing Interface | Faces any machine (any mod). Sends Processing Card inputs in and pulls the outputs back. |
| Stock Keeper | "Always keep N of this": up to 9 items it keeps crafted. |
| Network Interface | Lets pipes, hoppers and other mods use the network. Items, fluids, energy, chemicals and Source can all be pushed in. Items can only be pulled out if you pick them in its 9 slots; fluids, chemicals, energy and Source each have a Pull switch (off by default). |
| Void Filter | When storage is full, the items you pick are destroyed instead of backing up. |
| Quantum Link | Joins two networks across any distance or dimension. Pair two links with a Soul Tether. Both sides must be loaded. |
| Soul Infuser | The mod's machine: Soulite Ingot + Soul Sand + Quartz makes Infused Soulite. Pipes insert from any side and pull the output. |

## Items

| Item | What it does |
|---|---|
| Storage Cells | Copper 4k, Gold 64k, Diamond 1M, Netherite 64M items. Any mix of item types. |
| Fluid Cells | 256 / 4,096 / 65,536 / 1M buckets. Any mix of fluids. |
| Chemical Cells | Same sizes, for Mekanism gases, infuse types, pigments and slurries (needs Mekanism). |
| Energy Cells | 16M / 256M / 4B / 64B FE. |
| Source Cells | 10k / 100k / 1M / 10M Source (needs Ars Nouveau). |
| Singularity Cell | Unlimited amount of one item. Use it with that item in your off hand to lock it. |
| Recipe Card | Use it, click items into the 3x3 grid, and it saves the crafting recipe. Sneak-use to clear. |
| Processing Card | Same, for machines: what goes in, what comes back. Right-click a slot to add one more. |
| Wireless Terminal | Sneak-use on a controller to bind. 128 block range, same dimension. |
| Singularity Terminal | Wireless terminal with unlimited range, any dimension. |
| Speed Upgrade | Doubles a Soul Assembler's speed. |

**Look and feel:** every screen uses a translucent dark theme by default. Click the sun/moon button in a screen's
corner to switch to light mode; panel opacity is in the mod's Config screen (mod list) or `config/grimstorage-client.toml`.

**Dump key (G by default):** while carrying a bound wireless terminal, press it to send your main inventory
(not hotbar or armor) into the network.

## Autocrafting

Put Recipe Cards in a Soul Assembler on the network. Craftable items show in the terminal with a "Craft" label.
Click one (or ctrl-click / middle-click any craftable item), type an amount, and the network works out the
whole tree: it uses what is stored, crafts what is missing (including sub-recipes), and tells you exactly
what it is missing if it cannot. Machine steps use Processing Cards in a Processing Interface.

## Progression

| Tier | Gate | Unlocks |
|---|---|---|
| 1 | Soulite Ingot | Controller, Drive, Terminal, Cable, Glass, Interface, Copper Cell, Soul Infuser |
| 2 | Soulite + Gold | Gold Cell, Recipe Cards, Soul Assembler, Void Filter |
| 3 | Infused Soulite | Diamond Cell, Speed Upgrade, Stock Keeper, Wireless Terminal, Processing Card |
| 4 | Echo Soulite Alloy | Netherite Cell, Processing Interface, Quantum Link, Soul Tether |
| 5 | Singularity Core | Singularity Cell, Singularity Terminal |

## Ores

| Block | Where |
|---|---|
| Soulite Ore, Deepslate Soulite Ore | Overworld, Y -48 to 16. Iron pickaxe. |
| Soul Sand Soulite Ore | Soul Sand Valleys. Drops 2-3 Raw Soulite. |
| Echo Soulite Ore | Deep Dark only. Drops Echo Soulite Shards. Diamond pickaxe. |

## For pack makers

- The guide's text is in `assets/grimstorage/guide/en_us.json`. Override it with a resource pack to change or translate it
  (add `guide/<language>.json` for other languages). Pages support `**bold**`, `` `highlight` `` and `- ` bullet lists, plus
  `items` and `recipe` fields that are drawn live.
- Turn off the first-join book with `giveGuideOnFirstJoin` in `config/grimstorage-common.toml`.

- Common `c:` tags throughout (`c:ores/soulite`, `c:ingots/soulite`, `c:gems/echo_soulite`, ...).
- Every recipe, loot table and ore feature is datapack JSON. Override files in
  `data/grimstorage/worldgen/placed_feature/` or `data/grimstorage/neoforge/biome_modifier/` to change ore generation.
- Soul Infuser recipes use the `grimstorage:infusing` type, so you can add your own:

```json
{
  "type": "grimstorage:infusing",
  "ingredients": [{"tag": "c:ingots/soulite"}, {"item": "minecraft:soul_sand"}, {"tag": "c:gems/quartz"}],
  "result": {"id": "grimstorage:infused_soulite", "count": 1},
  "time": 100
}
```

## Fluids, chemicals, energy and Source

Put the matching cell in any Soul Drive. In the terminal, the tabs along the top switch between Items, Fluids, Chemicals,
Energy and Source. Click a fluid with an empty bucket (or any tank) to fill it, or click anywhere with a full one to store it;
chemicals work the same way with Mekanism tanks. To move these resources with pipes, use a Network Interface: push in from any
side, and flip its Pull switch for a resource to let pipes take it back out (for example, to power machines from stored FE).

## Known limits

- Crafting jobs in progress are not saved when the world closes; restart the craft after loading.
  Stored items are always saved.
- Quantum Links do not keep chunks loaded; the far side must be loaded (or chunk-loaded) to connect.
- No JEI integration yet, so recipe cards are filled by clicking items in.
- Autocrafting and Processing Cards work with items only; fluids, chemicals, energy and Source are stored and moved, not crafted.

## Building

Install JDK 21, then double-click `BUILD.bat` (Windows) or run `./gradlew build`. The jar lands in `build/libs/`.

Dev checks (never shipped in the release jar):
- `./gradlew runOreCheck` generates terrain and counts ore in every Soulite location.
- `./gradlew runSystemCheck` builds a network in a test world and checks storage, autocrafting, machine processing,
  stock keeping, voiding, pipe access, terminal clicks, card editing and a Nether Quantum Link. Run it twice to
  also check that everything survives a restart.
- `./gradlew runShots` opens the terminal on every filter tab in a real client on that world and saves screenshots.

## License

All Rights Reserved. See [LICENSE](LICENSE).
