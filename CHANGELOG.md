# Changelog

## 1.17.12
- Advancements: a Grim Storage tab with 19 advancements, 10 ordinary and 9 legendary (purple frame). One legendary is hidden until you earn it.
- Wireless Machine Bus: a status line under the title says "No network", "Needs a range card", or "Connected to its network". Once bound with a range card, its recipes reach the network the same way a wired bus's do.
- Network numbers: every network has an 8-digit number, never shared with another network in the same world. The Network Wrench copies and shows it.
- New `/grimstorage_export_machines` (client, JEI required): writes every machine JEI shows a recipe on to a Machine Bus datapack file, so pack makers can cover modded machines without per-mod rules.
- Guide: new pages on advancements, network numbers, the Wireless Machine Bus, and the export command (English, Portuguese, Chinese).
- Fixed: advancements were in the wrong data folder, so none showed up. They now load from `advancement`.
- Fixed: a network's ID could change after a reload if nothing had asked for it yet, which broke its wireless bindings.

## 1.17.11
- Creative Energy Battery: a creative-tab-only energy cell with endless FE. It has no recipe, so it can't be crafted
  in survival. It always gives energy and takes any you send, and reads as "Creative Energy Battery (infinite FE)".
- Cell cards redrawn at 32x32: each tier has its own band and rim colour, and four LEDs show its capacity step
  (copper 1, gold 2, diamond 3, netherite 4). Diamond, netherite and singularity cells pulse.
- Bus screens: when the title and status won't fit on one line (for example "Wireless Machine Bus" with "Needs a
  range card"), the status drops to its own line under the title.
- Fixed: swapping an import bus to an export bus (or back) duplicated its range card. The card is now kept once.

## 1.17.10
- Text that doesn't fit now ends in "..." instead of being cut off mid-letter:
  - Job queue rows in the Soul Terminal (item name, status line, and each step's machine line).
  - Craft toasts: they grow to fit their text, up to a cap, so "Waiting for Clean Iron Slurry" reads in full.
  - Guide page titles, Chest Link and Controller names, machine and bus titles, and the terminal's list entries.
- Bus screens: the status text (for example "No network") lines up with the title.
- Redstone Emitter: the signal text next to the lamp no longer touches the amount box.

## 1.17.9
- Wireless buses bound with the Network Wrench now say why they aren't in the network, instead of just "No network":
  - "Needs a range card" when the device has no range card. The wrench also says so right after binding.
  - "Out of range" when the device has a card but its controller is beyond the card's reach.
- Wrench binding is covered by a system check that clicks the bus with the wrench held, as a player would.

## 1.17.8
- Screen layout pass across every machine and bus GUI, checked with screenshots of each one:
  - The title rule sits higher, so the top row of slots no longer touches it.
  - Machine Bus: the "Processing cards" label is now "Cards" and no longer overlaps "Machine:". The wireless range card
    has its own row above the inventory instead of sitting on top of it.
  - Wireless buses: the range slot sits lower, clear of the speed multiplier.
  - Chest Link: the hint wraps onto two lines inside the panel, and the usage bar only shows when it is linked.
  - Controller: the resource tiles show the amount beside the icon and the name under it, so names no longer spill into
    the next tile.
  - Recipe Card and Processing Card: the hints sit under the grid instead of over its right column.

## 1.17.7
- Wireless bus antennas sit much closer to the bus: they now stick out about 3 pixels instead of 10.
- The antenna tip blinks blue at an even pace, half a second on and half a second off. Before, the bright flash was too
  brief to notice.

## 1.17.6
- Wireless Import, Export and Machine buses no longer have a cable stub or cable arms, and cables don't connect to them.
  Each has an antenna out the back instead, and its tip blinks blue.
- Wireless buses join their network by binding only, so the antenna is purely visual.

## 1.17.5
- Echo Soulite Shard redrawn as a faceted teal crystal with a bright ridge and sparkles.
- Netherite Infused Diamond redrawn as a brilliant-cut gem set in a dark netherite rim with gold trim and purple glints,
  so it stands apart as a rare item.

## 1.17.4
- Speed and range cards redrawn with a bevelled metal frame, a groove, corner studs and a lit panel. Each keeps its colour
  family, so the tiers are still easy to tell apart.
- Speed card keeps its chevrons. Range cards now show signal bars: Low lights one bar, Medium two, High three, and
  Infinite three in gold, with the unlit bars dimmed.

## 1.17.3
- Network Wrench: right-clicking a Wireless Import, Export or Machine Bus now binds it to the wrench's network instead of
  opening the bus GUI. Crouch-right-click still unbinds it. Other buses still open their GUI as before.

## 1.17.2
- Import and Export Buses: Speed Upgrades now stack in one slot, up to 10 (11x speed with a full stack). The eight separate
  slots are gone. Upgrades already in an older world are merged into that slot when it loads.
- Wireless Import, Export and Machine buses: the range card has its own slot, labelled and with a hover tip.
- Bus screen reorganised: filters along the top, then a labelled Speed slot and Range slot on the right, with the switches
  below. Each slot says what goes in it.
- Wireless Machine Bus screen reorganised: a labelled grid of processing cards, with its range card slot labelled beneath.
- Assembler speed is unchanged.

## 1.17.1
- Wireless buses, part 2: the Wireless Import Bus, Wireless Export Bus and Wireless Machine Bus can now be crafted. Each one is the matching bus
  plus an Echo Soulite Shard, Infused Soulite and an Ender Eye.
- Wireless range cards go in a wireless device's upgrade slot (the Wireless Machine Bus has one card slot, beside its
  grid). Each card sets how far the device reaches to its network's controller:
  - Low (ender pearl): `wirelessLowRange` blocks, default 32.
  - Medium (ender eye): `wirelessRange` blocks, default 128.
  - High (infused soulite and ender eye): `wirelessHighRange` blocks, default 512.
  - Infinite (netherite infused diamond and an echo shard): any distance, and any dimension if `wirelessCrossDimension` is on.
- A wireless device with no range card doesn't reach its network.
- Netherite Infused Diamond: diamond and netherite scrap in the Soul Infuser.
- Speed Upgrades still speed up wireless buses. Range cards don't count as speed.
- Server config: `infiniteWirelessCard` now turns the infinite card's reach on or off. Off, it reaches like the high card.
  Its recipe can be removed in a datapack.

## 1.17.0
- Wireless buses, part 1: Network Controllers now have a network ID. The new Network Wrench copies it: right-click a
  Network Controller to take the ID, then right-click a wireless device to bind it to that network. Crouch-right-click a
  wireless device to unbind it.
- New blocks: Wireless Import Bus, Wireless Export Bus and Wireless Machine Bus. A bound device within reach of its
  network's controller works as part of that network, with no cable needed. For now every wireless device reaches
  `wirelessRange` blocks (default 128). Crafting recipes, range cards and energy use come in the next part.
- New server config values (for modpack makers): `wirelessLowRange`, `wirelessHighRange`, `infiniteWirelessCard`,
  `wirelessBindLimit`, `wirelessCrossDimension` and `wirelessEnergyPerTick`. The range cards and energy use these
  settings are not active yet.

## 1.16.13
- Debug report: a new section lists every recipe in the world that makes a dust or grit, grouped by recipe type, with sample
  recipe ids. It shows which recipe type a machine has to read to make an item like obsidian dust or diamond grit.

## 1.16.12
- Debug report: each Machine Bus now lists what its machine makes (the inputs and outputs of up to 120 learned recipes), so a
  report shows whether a recipe such as obsidian dust was learned at all.

## 1.16.11
- Fixed: `/grimstorage debug` disconnected the player with "Failed to encode packet system_chat". The report link tried to open a file on the server, which can't be sent to a player. The report path is now plain text in chat; the report itself is written as before.

## 1.16.10
- Shift-click on the crafting result makes one stack of output at a time, as far as the network and your inventory allow.
  Click again for the next stack.

## 1.16.9
- Shift-click on the crafting result now crafts everything the network can feed the grid at once (up to one stack, and
  only as much as your inventory can hold), instead of one craft at a time. A stack of repeaters no longer takes seconds.
- The crafting grid keeps its recipe while you craft. Its items are refilled from the network in one step, so the recipe
  no longer disappears mid-craft. When the network runs out, the grid shows what is missing, as before.
- System check: the crafting grid test expects the bulk craft and the grid kept.

## 1.16.8
- Fixed: items in the network's crafting section flicker and disappear when you craft with a click or shift-click. An
  item a craft just used up stays listed for a few seconds, so it no longer blinks out and back in.

## 1.16.7
- Fixed: seeds (and any other item in a big tag) weren't offered to a recipe when the tag had more than 32 items. A modpack's
  c:seeds tag can be much longer, so wheat seeds in your network were never used for the recipe. Every item in a tag is
  offered now, up to 512 per ingredient.
- System check: a regression test for ingredient tags longer than 32.

## 1.16.6
- New server setting `assemblerSpeedMultiplier` (default 1.0): how fast Soul Assemblers craft. At 1.0 a stack of 64
  repeaters takes about 32 seconds on one plain Assembler; a pack can raise it, and Speed Upgrades still stack on top.

## 1.16.5
- Machine Bus has an explicit table entry for Iron Furnaces (`ironfurnaces:netherite_furnace` and its metal tiers), so
  they smelt through the Machine Bus with no name guessing.

## 1.16.4
- Press **R** over an item in the terminal to open its recipes in REI too (JEI and EMI already worked).
- Terminal search forgives small typos: "medning" finds Mending, and a 4 to 6 letter word can be one letter off, a
  7 letter or longer word two. Short words still need an exact match, so "stin" does not match "stone".
- The guide, README and CurseForge description cover the Y and R keys and the new search.
- System check: a search test, and a timed check that the terminal's cached network reads stay fast.

## 1.16.3
- Machine Bus reads furnaces from other mods: a furnace whose recipe type can't be read is now learned by its block
  name, the same way other machines are. Before, it learned nothing and gave no sign why.
- "Open wireless terminal" key name is translated in all six languages.

## 1.16.2
- Press R over an item in the terminal to open its recipes in JEI or EMI. (REI is not wired yet.)

## 1.16.1
- Search finds enchanted books and items by their enchantments, so typing "mending" shows the Mending books.
- The terminal keeps its order while counts change, so items no longer jump under the cursor during a craft.
- New keybind, Y by default, opens the Wireless Terminal (change it under Controls, "Open wireless terminal").
- Machine Bus runs any mod's furnace ("iron_furnace" and so on) as a smelting machine, and a blast furnace as blasting.

## 1.16.0
- Big networks stay fast. Each network now remembers its cells and only rebuilds that list when something actually
  changes, and it notices changes per network, so a busy network no longer slows down others or re-scans every
  drive every tick.
- New server config `grimstorage-server.toml`: cell capacities (items and every fluid/chemical/energy/XP/Source
  kind), Machine Bus speed multiplier and Wireless Terminal range can all be changed by pack makers.
- Six new languages for the whole mod and the guide book: Chinese (Simplified), Russian, Portuguese (Brazil),
  Spanish, German and French.
- Guide updated for saved jobs, chemical chains, Mekanism side handling and the new settings.

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
