<p align="center"><img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/banner.gif" alt="Grim Storage Industries" width="1200"></p>

**Grim Storage Industries** is a storage mod that is easy on day one and still holds up at the end game. Everything you store lives in one network, and the network can craft for you. There are **no channels, no byte or type limits and no cable rules**. Network blocks join just by touching, and one cable carries everything.

<p align="center"><img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/showcase.gif" alt="Showcase" width="800"></p>

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-features.gif" alt="Features" width="1200">

- **One network for everything:** items, fluids, energy (FE), experience, Mekanism chemicals and Ars Nouveau Source.
- **No limits to learn:** cells count items, not types. No channels, no cable rules, no network size cap.
- **Full autocrafting:** whole crafting trees, sub-recipes, machine steps from any mod, and a clear list of anything missing.
- **Machine Bus:** clip it onto a furnace or a machine from Mekanism, Create, Ender IO, Ars Nouveau, Immersive Engineering and more, and the network learns every recipe it can make, fluids and chemicals included. It keeps the machine fuelled, powered and topped up, takes back every output, and shares big jobs across all your machines. No cards needed.
- **Whole machine lines:** machines that make fluids and chemicals are steps too. Order Iron Crystals and the network runs the Chemical Washer, then the Crystallizer.
- **A real crafting queue:** move jobs up and down, pause them, and keep one player from filling every slot.
- **Craft preview:** see every step, what it uses and what's missing before you order, and get a pop-up when it's done.
- **Any planks will do:** every ingredient accepts any variant the recipe takes, from any mod, and big orders split across what you have.
- **Jobs survive restarts:** crafting jobs are saved with the world and carry on after a reboot.
- **Tells you why:** anything missing comes with the reason, and `/grimstorage debug` writes a report for bug reports.
- **Chest Link:** make any chest, barrel or drawer part of your storage.
- **Redstone Emitter:** a signal when you have enough (or too little) of something, or craft on redstone.
- **Server-ready:** networks belong to the player who built them; trust friends by name.
- **JEI, EMI and REI:** the + button, drag and drop, and search sync work with all three.
- **Import and Export Buses** that clip onto cable, with filters, and pipes from any mod can plug into them.
- **A terminal you'll actually like:** an Everything tab that shows every item and resource in one list, search, `@mod` search, category tabs, a Craftable tab, a Crafting jobs tab that shows every step as it runs, Shift to keep everything in place while you take, search shared with JEI, a size that fits your screen (or Tall and Taller), a built-in crafting grid that refills itself, and dark mode by default.
- **Soul Wrench:** cut or rejoin cable connections, take buses off cable, turn machines, and pick up your blocks.
- **Wireless access** within 128 blocks, or anywhere in any dimension with the Singularity Terminal.
- **Its own ore, Soulite,** and a five-tier progression up to the Singularity Core.
- **Animated, glowing blocks** and cells whose tier you can see at a glance.
- **A guide book** given to every player on first join, with live recipes.
- **Light on your server:** idle buses back off, capabilities are cached, and 255 buses cost about 0.1 ms per tick in testing.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-getting-started.gif" alt="Getting started" width="1200">

1. Mine **Soulite Ore** (Overworld, Y -48 to 16) and smelt it into ingots.
2. Craft a **Network Controller**, a **Soul Drive**, a **Copper Storage Cell** and a **Soul Terminal**.
3. Put the cell in the drive and place everything touching. Open the terminal and start storing.
4. Use **Soul Cable** to reach anything that isn't touching.

The **Grim Storage Guide** you get when you first join explains every block and item, with recipes drawn live from the game.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-storage.gif" alt="Storage cells" width="1200">

| Tier | Item cell | Fluid / chemical cell | Energy cell |
|---|---|---|---|
| Copper | 4,096 items | 256 buckets | 16M FE |
| Gold | 65,536 items | 4,096 buckets | 256M FE |
| Diamond | ~1 million items | 65,536 buckets | 4B FE |
| Netherite | ~67 million items | ~1 million buckets | 64B FE |

- **Singularity Cell:** one item type with **no limit at all**. Great for cobblestone and farm output.
- Experience cells (100k to 2B XP) and Source cells (needs Ars Nouveau) come in the same four tiers.
- Every cell goes in the same **Soul Drive**, and cells keep their contents when moved.
- Each tier has its own color and 1 to 4 lights. Diamond cells shine, and Netherite cells glow with the enchant glint.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-resources.gif" alt="Fluids, energy and XP" width="1200">

- The tabs along the top of the terminal switch between **Items, Fluids, Chemicals, Energy, Source and Experience**.
- Click with an empty bucket or tank to fill it, or with a full one to store it.
- In the Experience tab, click to take a level, shift-click for ten, and right-click to store all of your XP.
- Liquid XP from other mods is converted automatically.
- An Import Bus facing open air soaks up dropped items and XP orbs, which makes it perfect under a mob farm.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-autocrafting.gif" alt="Autocrafting" width="1200">

- **Recipe Cards:** click items into the grid to teach the network a recipe, then put the card in a **Soul Assembler**. Speed Upgrades double its speed, up to 16x.
- **No items needed to write cards:** press JEI's **+** on any recipe and the card is written, crafting or any mod's machine. Drag items from JEI onto any card or filter slot. JEI's + in the terminal fills the crafting grid from storage, and the pencil button writes it onto a card.
- **Machine Bus:** clip it onto a furnace, blast furnace or smoker, or a machine from **Mekanism, Create, Ender IO, Ars Nouveau, Immersive Engineering, Powah, Modern Industrialization, Oritech or Actually Additions**, and it learns every recipe that machine can make, even ones that need a fluid or chemical. No cards at all. It keeps furnaces fuelled, machines powered and tanks filled from your network, takes back every output (extras like sawdust too), opens Mekanism machine sides by itself, and several machines share one big job. Pack makers can add any machine with a datapack file.
- **Chemical chains:** the Machine Bus also learns machines that take and make **fluids and chemicals** (Mekanism's Dissolution Chamber, Chemical Washer, Crystallizer, Oxidizer, Infuser, Separator, Centrifuge, Reaction Chamber and more) and pulls what they make back into your cells. Whole ore lines plan as one craft.
- **Source machines:** a Machine Bus on an Ars Nouveau **Imbuement Chamber** gives it exactly the Source each recipe costs, straight from your Source cells. No jars.
- **Processing Cards** in a **Machine Bus** or **Processing Interface** run machines from **any mod** as steps in a craft.
- **Crafting queue:** in the terminal's crafting tab, move jobs up or down (shift-click to send one to the top), pause and resume them, or cancel them. Jobs at the top get the assemblers and machines first. Each player can run up to 16 jobs on a network at once, changeable in the config.
- When something can be made more than one way, the network picks the way you have ingredients for.
- **Stock Keeper:** "always keep 500 of this", and it crafts to keep it topped up.
- Order a craft from the terminal. The network works out the whole tree, uses what you have, crafts the rest and tells you exactly what's missing.
- **Void Filter:** when storage is full, the items you pick are destroyed instead of backing up your farms.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-buses.gif" alt="Buses and pipes" width="1200">

- **Import Bus (cyan):** takes everything out of a chest, tank, generator or machine and puts it in your network.
- **Export Bus (orange):** sends what you pick from your network into the block.
- Run Soul Cable up to a block and use the bus on it. The bus clips onto the end of the cable, facing the block.
- **Filters:** 9 slots, "Only these" or "All but these", exact match or ignore NBT, and an on/off switch for each resource.
- Placed the wrong one? One button in its screen switches a bus between Import and Export.
- **Speed Upgrades:** up to 8 per bus. Each one adds the base speed again, so a fully upgraded bus is 9x as fast.
- **Pipes from any mod** can push into an Import Bus or pull from an Export Bus. The **Network Interface** works with pipes and hoppers too.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-wireless.gif" alt="Wireless and quantum" width="1200">

- **Wireless Terminal:** link it to your controller and use it anywhere within 128 blocks.
- **Singularity Terminal:** any distance, any dimension.
- **Quick dump:** press **G** while carrying a linked terminal to send your whole inventory into storage. Your hotbar and armor stay put.
- **Quantum Link:** join two places into one network, even across dimensions.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-compat.gif" alt="Compatibility" width="1200">

- **Requires:** NeoForge for Minecraft 1.21.1.
- **Optional:** JEI, EMI or REI (recipe + button, drag and drop, shared search; Soul Infuser recipes get their own JEI page), Mekanism (chemical storage), Ars Nouveau (Source storage). Without them, nothing breaks. The extra cells are just hidden.
- **Any pipe mod** that uses standard NeoForge capabilities works with the buses and the Network Interface.
- **Pack makers:** every recipe, loot table and ore feature is a datapack file, the guide text can be replaced with a resource pack, and the first-join book can be turned off in the config.

<img src="https://raw.githubusercontent.com/PCPx00/grim-storage-industries/main/media/section-family.gif" alt="Grim Industries" width="1200">

Grim Storage Industries is part of the **Grim Industries** family, made for the **Nine Craft** modpack:

- **Grim Soul Industries:** mob farming.
- **Grim Pipe Industries:** item, fluid, energy and chemical pipes. They plug straight into Grim Storage buses.

Made by **xTri6**.
