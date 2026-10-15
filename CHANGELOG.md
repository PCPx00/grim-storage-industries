# Changelog

## 1.15.2
- The network owner is recognised by their player UUID when managing other players' crafting jobs, so changing
  your Minecraft name no longer takes away control of your own network's jobs.

## 1.15.1
- Network size limit is a config value now, `maxNetworkBlocks` (default 16,384, up to 1,048,576). A network bigger
  than the limit used to be cut off silently; now its Network Controller shows a red warning, and the guide and the
  CurseForge description no longer claim there is no size limit.
- Crafting jobs belong to the player who ordered them: cancel, pause and move up/down now only work for that
  player, the network owner, or a server operator. Anyone with the terminal open used to be able to manage
  another player's jobs on a shared network.
- The mod ships a logo (`logo.png`), so it has an icon in the mod list and on CurseForge, and the LICENSE the
  metadata points at is now included in the jar.

## 1.15.0
- Crafting jobs are saved with the world. A restart, a crash or a server reboot no longer loses them: running jobs
  carry on, paused jobs stay paused, and items already in machines are still counted when they come back. Each step
  finds its Recipe Card or machine again once it loads. Items waiting for room in storage are saved too.
- Every ingredient takes its variants now, not only one per recipe. A recipe asking for any copper, any coal or any
  log uses whichever you have, from any mod, and a big order is split across them when no single one covers it.
  Machine Bus recipes learn every ingredient's choices too (and offer far fewer duplicate recipes because of it).
  Exact Recipe Cards still use only their own items.
- Why something is missing: the craft preview and the chat message now say why under each missing item. For
  example, you have it with other data (damaged, enchanted, named), another mod's look-alike with the same tag that
  this recipe doesn't take, it is in a chest linked as "only put in", it is in your own inventory, or nothing on the
  network makes it.
- New command: `/grimstorage debug`. Look at any block of a network (or carry a linked wireless terminal) and it
  writes a report of the network's blocks, cells, machines, recipes, running jobs, recent problems and your mod list
  to `grimstorage-debug/`. Attach it to bug reports. Operators can add a position.

## 1.14.1
- Fixed: Mekanism recipes that need an infuse type or another chemical (a Basic Control Circuit needs redstone
  infuse) said ingredients were missing even with them in storage. The network only looked for the chemical itself.
  Now a Machine Bus also gives the machine the items Mekanism turns into that chemical: 2 redstone dust for a circuit,
  osmium ingots for an Osmium Compressor, flint for a Purification Chamber, and so on. It uses stored chemical when you
  have it, and the items otherwise.
- Machine Bus on Mekanism machines: also opens a spare side for the slot that takes those items (only a side nobody
  has set).

## 1.14.0
- Machine Bus: machines that make fluids and chemicals work now. The bus pulls what they make into your fluid and
  chemical cells, and fills machines that only take fluids and chemicals with exactly what each run needs, many runs
  at a time.
- Whole machine lines plan as one craft: order Iron Crystals and the network runs the Chemical Washer, then the
  Crystallizer, each with its own Machine Bus. Missing fluids or chemicals are crafted when a machine can make them.
- New Mekanism machines learned: Chemical Dissolution Chamber, Chemical Washer, Chemical Crystallizer, Chemical
  Oxidizer, Chemical Infuser, Solar Neutron Activator, Isotopic Centrifuge, Electrolytic Separator, Pressurized
  Reaction Chamber, Pigment Extractor, Pigment Mixer and Painting Machine. On these the bus also opens spare sides (only
  ones nobody has set) for second inputs and outputs.
- Ars Nouveau Imbuement Chamber: the bus now runs it. It gives the chamber exactly the Source each recipe costs from
  your Source cells (no jars), and recipes that need pedestal items are left out.
- Fixed: a Machine Bus on a one-slot machine could pull an ingredient back out before the machine used it.
- Crafting queue: the terminal's crafting tab has up / down / pause / cancel buttons on every job. Shift-click up
  sends a job to the top. Jobs higher up get the Soul Assemblers and machines first.
- Each player can run up to 16 jobs on a network at once, so one big order can't take every slot on a shared network.
  `maxJobsPerPlayer` and `maxJobsPerNetwork` are in `config/grimstorage-common.toml`.
- Machine steps in the preview and crafting tab show the fluid or chemical they make.

## 1.13.0
- Networks belong to the player who places the controller. Others can't open its blocks, use wireless terminals on
  it, or break or add its blocks. Trust players by name, or open the network to everyone, from the controller (the
  key button). Server operators can always get in. Networks from before 1.13 are open until someone claims them.
- Recipe Cards take any variant the recipe accepts: an oak-plank chest card also crafts from birch. The Any / Exact
  button on the card turns it off. The network picks the variant you have.
- Craft preview: the order window shows every step and what makes it, what it takes from storage, anything missing
  (in red) and about how long it will take, before you confirm.
- Machine Bus: recipes that need a fluid or chemical are learned too, and the bus keeps the machine's tank topped up
  from the network (Mekanism Purification Chamber, Osmium Compressor, Chemical Injection, Metallurgic Infuser; any
  mod's fluid recipes). Missing fluids or chemicals show in the preview.
- Tested in game with Create 6 (Millstone), Ender IO 8 (SAG Mill, Alloy Smelter), Ars Nouveau 5.13 (Imbuement
  Chamber) and Immersive Engineering 12.4 (Crusher). Fixed reading Ender IO's SAG Mill recipes.
- New: Chest Link. Makes any chest, barrel or drawer part of storage: in and out, only in, or only out, and
  optionally filled before the cells.
- New: Redstone Emitter. A signal when an item is at least or below an amount, or crafts it up to the amount while
  powered.
- EMI and REI support: the + button writes cards and fills the terminal grid, items drag onto filter and card slots,
  and the terminal search syncs, just like with JEI.
- Craft alerts: a pop-up when a craft you ordered is done, or has been stuck for a minute (can be turned off in the
  terminal settings).
- Terminal: Alt-click an item to pin it to the top; new "recently added" sort.
- The Machine Bus table no longer mentions Thermal (it has no 1.21.1 release); its entries stay, harmless.

## 1.12.0
- Write cards without having the items:
  - Recipe Card: open it, find the recipe in JEI and press +. The card is written.
  - Processing Card: press + on a recipe of any mod (Mekanism, Create, Thermal, anything JEI shows). What goes in
    and what comes out are filled in, with amounts. Fluids and chemicals are left out, since cards hold items.
  - Drag items from JEI's list onto any card slot or filter slot (buses, Stock Keeper, Void Filter, cells).
- Terminal: JEI's + on a crafting recipe fills the crafting grid from the network (and your inventory), picking
  the ingredient you have most of. Missing ones are marked red in JEI, and the rest still go in.
- Terminal: new pencil button next to the crafting grid writes the grid's recipe onto a Recipe Card you carry
  (a blank one first). The items stay in the grid.
- Card screens tell you about JEI's + button.

## 1.11.0
- Machine Bus works with machines from big mods, no cards needed. It learns every item recipe of:
  - Mekanism: Enrichment Chamber, Crusher, Precision Sawmill, Combiner, Energized Smelter, and all their factories.
  - Thermal, Ender IO, Immersive Engineering, Create (Millstone), Ars Nouveau (Imbuement Chamber), Powah, Modern
    Industrialization, Oritech and Actually Additions.
  - Other mods' machines too, when the machine's name matches its mod's recipes (a "crusher" runs "crushing").
- Machine Bus: keeps powered machines charged from the network's energy cells ("Fuel & power").
- Machine Bus: takes back everything a learned machine makes, extras like Mekanism sawdust included, so outputs
  never clog. Input slots are left alone.
- Machine Bus: finds the machine side that takes items, so it works with machines that have side settings. On
  Mekanism machines it opens the side it touches for items and power by itself (no Configurator needed); sides
  you already set are left alone.
- Recipes that also need a fluid or chemical are left out, so jobs never wait on something the bus can't give.
- Pack makers: add any machine with a datapack file, `data/<pack>/grimstorage_machines/<name>.json`:
  `{"entries": [{"machines": ["mod:machine", "#mod:machine_tag"], "recipe_types": ["mod:recipe_type"]}]}`.
- Machine Bus screen: long machine names shrink to fit.

## 1.10.0
- Terminal: new Crafting jobs tab (last along the top, with a count of running jobs). Every job shows its progress
  and what it is waiting for, and under it every step: which machine makes it (for example "Furnace x2"), how many
  are done and how many are in machines right now. Hover for details, click the x to cancel. The search box filters
  jobs too.
- Terminal: settings (the gear at the top):
  - Sync search with JEI: the terminal and JEI share one search, both ways.
  - Type to search on open: the cursor starts in the search box.
  - Keep search when closed.
  - Size: Auto (fits your screen), Normal (6 rows), Tall (9) or Taller (12). Auto is the default, so big screens
    get a bigger terminal straight away.
  All of them are also in the client config file.
- Job progress now counts finished crafts instead of finished steps, so a long job moves smoothly.
- The "no machine" message now mentions the Machine Bus too.

## 1.9.0
- New: Machine Bus. Clips onto a machine like an Import Bus, and the network uses that machine for autocrafting.
  - On a furnace, blast furnace or smoker (or any modded furnace built on them) it needs no cards: it learns every
    recipe the machine can cook, and they show up as craftable right away.
  - Keeps the furnace fuelled from the network (coal, charcoal, coal blocks, blaze rods, dried kelp blocks), one piece
    at a time so fuel is shared fairly.
  - Only takes results from the output slot.
  - Several Machine Buses on several machines share a job: 64 ore on 4 furnaces cooks 4 times as fast.
  - Holds 9 Processing Cards for any other machine, from any mod.
  - Buttons in its screen turn recipe learning and fuelling on or off.
- Autocrafting: when an item can be made more than one way (charcoal from any log, say), the network now picks a way it
  has the ingredients for, instead of always the first one.
- Processing Cards for the same recipe on several Processing Interfaces or Machine Buses now share the work too.
- Terminal: hold Shift and nothing moves. Shift-click the same spot again and again to keep taking the same item; an
  item that runs out stays in its spot, faded, until you let go.
- Terminal: new Craftable tab (last on the left) shows only what the network can craft. Recipes a furnace learned
  only show there while you have none stored, so they never flood your item list.
- Fix: the "types" count in the terminal only counts items you actually have.

## 1.8.1
- Fix: an Import or Export Bus next to Soul Cable now visibly connects to it from any side (not only its back). The bus
  draws a cable arm toward every side that touches cable or another network block, the same way cable does, and its
  neck only runs to its back when something is there.

## 1.8.0
- Speed Upgrades now go in Import and Export Buses: up to 8 per bus, each adding the base speed again (8 = 9x, over
  1,000 items or 288 buckets a tick). Upgrade slots are on the right of the bus screen, and upgrades drop with the bus.
- New: Soul Wrench. Click a cable connection to cut it (click again to join it back), click a bus on a cable to take it
  off, click a machine to turn it toward the side you clicked (works on most other mods' machines too), and sneak-click
  any Grim Storage block to pick it up.
- Terminal: new Everything tab (first tab along the top) shows items, fluids, chemicals, energy, Source and XP in one
  list. The other tabs are still there.
- Fix: a bus fed by a pipe (for example one facing straight into a pipe end) said "Nothing attached" even while it was
  working. Buses now count pipe traffic and show "Working".
- Fix: pipes from other mods now notice a bus as soon as it is clipped onto a cable next to them, or switched between
  Import and Export.

## 1.7.1
- Every bus screen has a direction button: switch a placed bus between Import (takes things out of the block into the
  network) and Export (sends things from the network into the block) without swapping the item. Free-standing buses
  keep their facing, and cable buses stay on their cable side.
- An Export Bus with nothing picked to send now says "Pick items" instead of "Idle".
- Bus tooltips say which way each one moves things, and placing an Export Bus shows a reminder of what it does.
- Guide: the Buses page now leads with what each bus does (Import is cyan, Export is orange) and how to switch.

## 1.7.0
- Buses now go on Soul Cable. Run cable up to a chest or machine, then use an Import or Export Bus on that block
  (or on the end of the cable): the bus clips onto the cable facing it, and items, fluids, energy, XP, chemicals and Source
  flow straight into or out of the network. Right-click a cable bus for its filter screen; break it to take just the bus off.
- Each cable side holds one bus. The network never passes through a side with a bus, so a cable bus can face another
  network without merging them. Pipes from any mod can plug into a cable bus on the side it faces.
- Soul Cable is thinner (4 px), the same size as a bus's neck.
- Free-standing buses still work (use one on a block with no cable next to it). Existing buses keep their settings.
- Guide: new "Buses on cable" page.

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
