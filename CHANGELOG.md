# Changelog

## 1.6.0
- JEI: Soul Infuser recipes now show up (look up Infused Soulite, or click the arrow in the infuser). Chemical and Source
  cells are hidden in JEI when Mekanism or Ars Nouveau is not installed.
- Buses take pipes from any mod, on every side. Pipes push items, fluids, energy, chemicals, Source and liquid XP into an
  Import Bus, and pull out of an Export Bus whatever its filter and switches allow. Tested with Grim Pipe Industries omni
  pipes (items both ways, fluids out) and hoppers.
- Soul Cable now connects itself up when placed by commands, structures or other mods, not only by players.
- Every network block is animated with a full-bright glow layer: chasing rings, blinking drive LEDs, a scanning terminal,
  rising stock bars, pulsing cable, spinning bus rings and glinting ore. Soulite blocks and glass shimmer. The controller,
  quantum link and void filter give off soul wisps.
- New cell art. Each tier has its own cap color and 1-4 tier lights; Diamond cells shine, Netherite cells have glowing
  ember seams and the enchant glint. Name colors step up per tier (Gold yellow, Diamond aqua, Netherite purple) and
  tooltips show the tier. New Recipe and Processing Card art.
- Guide: new "Pipes from any mod" and "Telling tiers apart" pages; cable, pipes and Infused Soulite pages updated.

## 1.5.0
- New: **Import Bus** and **Export Bus**. Place one against any block (chest, furnace, tank, generator, any modded machine) and
  it sticks to that face. One bus handles items, fluids, energy, XP, Mekanism chemicals and Ars Nouveau Source.
- The Import Bus pulls everything by default. Facing open air it picks up dropped items and XP orbs, which makes it handy under a mob farm.
- The Export Bus sends only what you pick by default, so it never empties your storage by accident.
- Bus screen: 9 filter slots (buckets and tanks filter their fluid or chemical), Only these / All but these, Exact match /
  Ignore NBT, an on/off switch per resource, and a live status (Working, Idle, Storage full, No network, Nothing attached).
- Built to be lag free: cached block capabilities, a per-run budget, sliced scanning of big inventories, and idle buses back
  off to about once a second until something next to them changes. 255 buses measured at about 0.1 ms per tick in total.
- The network never reaches through a bus's front, so a bus can move things between two separate networks.
- New: **Experience storage**. Experience Cells in four tiers (100k to 2B XP), an Experience tab in the terminal (click to
  take a level, shift-click for 10, right-click to store all your XP), and liquid XP from other mods (`c:experience`) is
  converted at 20 mB per point by buses, the Network Interface and the terminal.
- Network Interface: XP Pull switch, and the Pull switches now always fit in the screen.
- Guide: new Buses chapter (6 pages), an Experience page, and updated pages for pipes, the interface, progression and help.

## 1.4.0
- New: the Grim Storage Guide. Every player gets one the first time they join (turn this off in the common config).
  It can also be crafted from a book and a Soulite Ingot.
- 12 chapters covering everything: Soulite, your first network, the terminal, cells, fluids/energy/chemicals/Source,
  autocrafting, machines, automation, wireless and Quantum Links, progression and troubleshooting.
- Pages show live recipes (crafting, smelting and Soul Infuser) read from the game, so pack changes show up automatically.
- Matches the dark/light theme. Text lives in assets/grimstorage/guide/en_us.json, so packs can edit or translate it.

## 1.3.0
- Store fluids, Mekanism chemicals, energy (FE) and Ars Nouveau Source as well as items.
- 16 new cells: Fluid, Chemical, Energy and Source cells in Copper, Gold, Diamond and Netherite tiers. They go in the same
  Soul Drive as item cells. Chemical cells need Mekanism and Source cells need Ars Nouveau; without those mods they are hidden
  and uncraftable, and nothing else changes.
- Terminal resource tabs along the top: Items, Fluids, Chemicals, Energy (and Source with Ars Nouveau). Fluids and chemicals
  show with their real textures; click with a bucket or Mekanism tank to fill it, or with a full one to store it.
- Network Interface now accepts fluids, energy, chemicals and Source from pipes and machines. New Pull switches decide, per
  resource, whether anything may be pulled back out (off by default).
- Controller shows a gauge for each resource.

## 1.2.0
- New look for every screen: translucent rounded panels, inset slots, flat themed buttons and fields, accent highlights.
- Dark mode is the default. Switch with the sun/moon button in the corner of any screen, or from the mod list's Config
  button, where panel opacity can also be changed.
- Built-in 3x3 crafting grid in the Soul Terminal and both wireless terminals. Grid slots refill themselves from the
  network as you craft, shift-clicking the result crafts up to a full stack, and the x button sends the grid back to storage.
- Terminal side panel with a storage gauge, item type count and the current filter.
- Controller screen redesigned with a usage bar and stat chips.

## 1.1.0
- Terminal filter tabs down the left side: Everything, Ores & Raw Materials, Ingots & Gems, Weapons & Tools, Armor, Food,
  Mob Drops, Farming, Building Blocks and Misc. Hover a tab to see how many item types it holds. The terminal remembers
  your last tab. Tabs use the shared c: tags, so modded items sort themselves.
- Infused Soulite and Echo Soulite Alloy are now tagged as ingots.

## 1.0.0
- Storage network: Network Controller, Soul Drive, Soul Terminal, Soul Cable, Soulite Glass.
- Storage cells: Copper 4k, Gold 64k, Diamond 1M, Netherite 64M, plus the single-item unlimited Singularity Cell.
- Autocrafting: Recipe Cards, Soul Assembler (with Speed Upgrades), full crafting-tree planning with a "missing items" report.
- Machine automation: Processing Cards and the Processing Interface work with any mod's machines.
- Stock Keeper (keep N crafted), Void Filter (destroy overflow), Network Interface (pipe and hopper access).
- Wireless Terminal (128 blocks) and Singularity Terminal (unlimited, any dimension), plus a key to dump your inventory.
- Quantum Link and Soul Tether to join networks across dimensions.
- Soul Infuser machine with its own datapack recipe type.
- Controller screen with storage stats and a crafting job list you can cancel from.

## 0.1.0
- New material: Soulite. Overworld, Deepslate, Soul Sand and Echo (Deep Dark) ores with Fortune and Silk Touch support.
- Raw Soulite, Soulite Ingot, Nugget, Dust, Block of Soulite and Block of Raw Soulite.
- End-game materials: Infused Soulite, Echo Soulite Shard, Echo Soulite Alloy and the Singularity Core.
