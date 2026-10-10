# Changelog

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
