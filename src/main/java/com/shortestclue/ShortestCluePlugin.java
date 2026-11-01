package com.shortestclue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.Provides;
import com.shortestclue.debug.DebugClueController;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

@PluginDescriptor(
	name = "Shortest Clue",
	description = "Integrates with Shortest Path to find and display the shortest path to your current clue scroll location.",
	tags = {"clue", "path"}
)
@PluginDependency(ClueScrollPlugin.class)
public class ShortestCluePlugin extends Plugin
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShortestCluePlugin.class);

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClueScrollPlugin clueScrollPlugin;

	@Inject
	private ClueScrollService clueService;

	@Inject
	EventBus eventBus;

	@Inject
	private ShortestClueConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapOverlay worldMapOverlay;

	@Inject
	private MouseManager mouseManager;

	private DebugClueController debugController;

	private Set<WorldPoint> currentDests;

	private List<Set<Integer>> currentRequiredItems = List.of();

	/**
	 * Clue steps that are only reachable through the Wilderness, so their path is
	 * allowed to ignore the user's {@code avoidWilderness} setting. The clue
	 * destination is mandatory, so there is no non-wilderness alternative.
	 */
	private static final Set<WorldPoint> WILDERNESS_REQUIRED_TARGETS = Set.of(
		new WorldPoint(2288, 4702, 0), // Cryptic: Kill the King Black Dragon (#25)
		new WorldPoint(2205, 4838, 0), // Emote: Flap at the death altar (#19)
		new WorldPoint(2011, 4712, 1), // Emote: Blow at Iban's temple (#34), currently only reachable via Abyss -> death altar -> portal
		new WorldPoint(2271, 4680, 0), // Emote: Dance in the King Black Dragon's lair
		new WorldPoint(3039, 4834, 0), // Anagram: Dark Mage, centre of the Abyss
		new WorldPoint(3049, 4839, 0), // Cryptic: Dig next to the law rift in the Abyss
		new WorldPoint(1863, 4639, 0)  // Cryptic: Thorgel, a dwarf approaching death (reachable via death altar portal)
	);

	/**
	 * Certain clues report a destination that differs from the walkable tile, so their
	 * destination is remapped before pathing (see https://github.com/runelite/runelite/pull/19302).
	 */
	private static final Map<WorldPoint, WorldPoint> REMAPPED_DESTINATIONS = Map.of(
		// Heckel Funch, same tile but on plane 1
		new WorldPoint(2490, 3488, 0), new WorldPoint(2490, 3488, 1),
		// Ardougne Mill (#22) same tile but on plane 3
		new WorldPoint(2635, 3385, 3), new WorldPoint(2635, 3385, 2),
		// Ambassador Spanfipple, same tile but on plane 1
		new WorldPoint(2979, 3340, 0), new WorldPoint(2979, 3340, 1),
		// Iban's temple (#34) same tile but on plane 1
		new WorldPoint(2011, 4712, 0), new WorldPoint(2011, 4712, 1),
		// Hazelmere, same tile but on plane 1
		new WorldPoint(2677, 3088, 0), new WorldPoint(2677, 3088, 1),

		// Guardian Mummy (#36, #30) redirect to outside pyramid plunder
		new WorldPoint(1934, 4427, 0), new WorldPoint(3289, 2787, 0),
		// 7th room of Pyramid Plunder (#29) redirect to outside pyramid plunder
		new WorldPoint(1944, 4427, 0), new WorldPoint(3289, 2787, 0),
		// Mogre Camp (#24) redirect to Murphy
		new WorldPoint(2953, 9523, 1), new WorldPoint(2668, 3162, 0),
		// Top of the Agility Pyramid (#28) redirect to start of Agility Pyramid
		new WorldPoint(3043, 4697, 3), new WorldPoint(3354, 2829, 0),
		// Port Sarim jail bucket (#11) redirect to Shantay Pass
		new WorldPoint(3013, 3179, 0), new WorldPoint(3305, 3120, 0)
	);

	/**
	 * Elite "kill" steps have no location in RuneLite, so they path to the nearest place the monster is
	 * found, keyed by the clue text. Places are the spawns listed on each monster's OSRS Wiki page, one
	 * tile per group of spawns; areas only open on a Slayer task or during a quest are left out.
	 */
	static final Map<String, Set<WorldPoint>> KILL_LOCATIONS = Map.ofEntries(
		// Aberrant or Deviant spectre (Wiki: Deviant spectre, Aberrant spectre)
		Map.entry("You'll have to plug your nose if you use this source of herbs.", Set.of(
			new WorldPoint(1651, 9989, 0), // Catacombs of Kourend
			new WorldPoint(1609, 10011, 0), // Catacombs of Kourend
			new WorldPoint(2055, 9209, 0), // Deepfin Mine
			new WorldPoint(3428, 3543, 1) // Slayer Tower
		)),
		// Ankou (Wiki: Ankou)
		Map.entry("Ghostly bones.", Set.of(
			new WorldPoint(1643, 9996, 0), // Catacombs of Kourend
			new WorldPoint(2056, 9173, 0), // Deepfin Mine
			new WorldPoint(2977, 3748, 0), // The Forgotten Cemetery
			new WorldPoint(2322, 5227, 0), // Stronghold of Security: Sepulchre of Death
			new WorldPoint(2326, 5201, 0), // Stronghold of Security: Sepulchre of Death
			new WorldPoint(2359, 5241, 0), // Stronghold of Security: Sepulchre of Death
			new WorldPoint(3361, 10076, 0) // Wilderness Slayer Cave
		)),
		// Waterfiend (Wiki: Waterfiend)
		Map.entry("Let's hope you don't meet a watery death when you encounter this fiend.", Set.of(
			new WorldPoint(1752, 5351, 0), // Ancient Cavern
			new WorldPoint(3163, 12454, 0), // Iorwerth Dungeon
			new WorldPoint(3187, 12451, 0), // Iorwerth Dungeon
			new WorldPoint(3179, 12475, 0) // Iorwerth Dungeon
		)),
		// Basilisk (Wiki: Basilisk, Basilisk Knight)
		Map.entry("Reflection is the weakness for these eyes of evil.", Set.of(
			new WorldPoint(2742, 10008, 0), // Fremennik Slayer Dungeon
			new WorldPoint(2467, 10398, 0), // Jormungand's Prison
			new WorldPoint(2421, 10384, 0) // Jormungand's Prison
		)),
		// Rock crab (Wiki: Rock Crab)
		Map.entry("You might have to turn over a few stones to progress.", Set.of(
			new WorldPoint(2687, 3724, 0), // North of Rellekka
			new WorldPoint(2529, 3740, 0), // Waterbirth Island
			new WorldPoint(2521, 3756, 0), // Waterbirth Island
			new WorldPoint(2531, 3721, 0), // Waterbirth Island
			new WorldPoint(1204, 3557, 0), // West of Mount Quidamortem
			new WorldPoint(2456, 10161, 0), // Waterbirth Island Dungeon
			new WorldPoint(2748, 10166, 0), // Brine Rat Cavern
			new WorldPoint(2739, 10153, 0), // Brine Rat Cavern
			new WorldPoint(2706, 10159, 0), // Brine Rat Cavern
			new WorldPoint(2650, 2411, 0) // Charred Island
		)),
		// Bloodveld (Wiki: Bloodveld, Mutated Bloodveld)
		Map.entry("You'll get licked.", Set.of(
			new WorldPoint(2886, 5323, 2), // God Wars Dungeon
			new WorldPoint(2886, 5359, 2), // God Wars Dungeon
			new WorldPoint(2928, 5356, 2), // God Wars Dungeon
			new WorldPoint(2845, 5283, 2), // God Wars Dungeon
			new WorldPoint(2873, 5299, 2), // God Wars Dungeon
			new WorldPoint(2854, 5355, 2), // God Wars Dungeon
			new WorldPoint(2862, 5336, 2), // God Wars Dungeon
			new WorldPoint(3572, 9741, 0), // Meiyerditch Laboratories
			new WorldPoint(3594, 9742, 0), // Meiyerditch Laboratories
			new WorldPoint(3417, 3565, 1), // Slayer Tower
			new WorldPoint(3036, 10139, 0), // Wilderness God Wars Dungeon
			new WorldPoint(2092, 10096, 0), // Buccaneers' Laboratory
			new WorldPoint(1631, 10032, 0), // Catacombs of Kourend
			new WorldPoint(1678, 10073, 0), // Catacombs of Kourend
			new WorldPoint(1691, 10016, 0), // Catacombs of Kourend
			new WorldPoint(3239, 12433, 0) // Iorwerth Dungeon
		)),
		// Aviansie (Wiki: Aviansie)
		Map.entry("You will have to fly high where a sword cannot help you.", Set.of(
			new WorldPoint(2855, 5283, 2), // God Wars Dungeon
			new WorldPoint(3025, 10152, 0) // Wilderness God Wars Dungeon
		)),
		// Green or brutal green dragon (Wiki: Green dragon, Brutal green dragon)
		Map.entry("Mix yellow with blue and add heat, make sure you bring protection.", Set.of(
			new WorldPoint(3339, 3694, 0), // East of the Bone Yard
			new WorldPoint(2982, 3618, 0), // West of the Dark Warriors' Fortress
			new WorldPoint(3140, 3700, 0), // North of the Graveyard of Shadows
			new WorldPoint(3158, 3708, 0), // North of the Graveyard of Shadows
			new WorldPoint(3078, 3810, 0), // South of the Lava Maze
			new WorldPoint(3107, 3812, 0), // South of the Lava Maze
			new WorldPoint(3415, 10068, 0), // Wilderness Slayer Cave
			new WorldPoint(3396, 10119, 0), // Wilderness Slayer Cave
			new WorldPoint(1939, 8991, 1), // Myths' Guild basement
			new WorldPoint(1771, 5340, 0) // Ancient Cavern
		)),
		// Crocodile (Wiki: Crocodile)
		Map.entry("In a while...", Set.of(
			new WorldPoint(3347, 2927, 0), // Along the River Elid
			new WorldPoint(3301, 2920, 0), // Along the River Elid
			new WorldPoint(3268, 2854, 0), // Along the River Elid
			new WorldPoint(3190, 2825, 0), // Near the Ruins of Unkah
			new WorldPoint(3421, 2772, 0) // Near the Ruins of Ullek
		)),
		// Hellhound (Wiki: Hellhound)
		Map.entry("A demon's best friend holds the next step of this clue.", Set.of(
			new WorldPoint(3176, 3950, 0), // Outside the Wilderness Resource Area
			new WorldPoint(3180, 3917, 0), // Outside the Wilderness Resource Area
			new WorldPoint(2740, 9688, 0), // Witchaven Dungeon
			new WorldPoint(2861, 9837, 0), // Taverley Dungeon
			new WorldPoint(1643, 10063, 0), // Catacombs of Kourend
			new WorldPoint(2629, 8864, 0), // Charred Dungeon
			new WorldPoint(1340, 10198, 2), // Karuulm Slayer Dungeon
			new WorldPoint(2078, 10070, 0), // Buccaneers' Laboratory
			new WorldPoint(2881, 5324, 2), // God Wars Dungeon
			new WorldPoint(2837, 5335, 2), // God Wars Dungeon
			new WorldPoint(2892, 5302, 2), // God Wars Dungeon
			new WorldPoint(2915, 5345, 2), // God Wars Dungeon
			new WorldPoint(3440, 10085, 0) // Wilderness Slayer Cave
		)),
		// Frost nagua (Wiki: Frost Nagua)
		Map.entry("Banish a chilling spirit.", Set.of(
			new WorldPoint(1638, 9630, 0) // Ruins of Tapoyauik
		)),
		// Earthen nagua (Wiki: Earthen Nagua)
		Map.entry("Crush the spirited earth.", Set.of(
			new WorldPoint(1291, 9415, 1), // Tonali Cavern
			new WorldPoint(1295, 9370, 0), // Tonali Cavern
			new WorldPoint(1333, 9378, 0) // Tonali Cavern
		))
	);

	/**
	 * The ferocious barbarian spirit step: kill a barbarian in the Ancient Cavern for mangled bones, then burn
	 * them on a pyre ship, where the spirit appears.
	 */
	static final String BARBARIAN_SPIRIT_STEP = "Surround my bones in fire, ontop the wooden pyre. Finally lay me to rest, before my one last test.";

	/** The barbarians that drop the mangled bones (Wiki: Confused barbarian, Lost barbarian). */
	static final Set<WorldPoint> MANGLED_BONES_BARBARIANS = Set.of(
		new WorldPoint(1767, 5345, 0), // Confused barbarian, Ancient Cavern
		new WorldPoint(1761, 5322, 0) // Lost barbarian, Ancient Cavern
	);

	/** The pyre sites by Otto's Grotto (Wiki: Funeral pyre (Barbarian)). */
	static final Set<WorldPoint> PYRE_SITES = Set.of(
		new WorldPoint(2519, 3519, 0),
		new WorldPoint(2507, 3518, 0),
		new WorldPoint(2503, 3499, 0)
	);

	static final Set<Integer> MANGLED_BONES = Set.of(ItemID.BRUT_BARBARIAN_BONES_BAD);

	/**
	 * Game ticks (3 minutes) the path stays at the pyre after the mangled bones leave the inventory: burning them
	 * takes the bones and the logs, and the spirit disappears if not killed within about 3 minutes, after which new
	 * bones are needed (Wiki: Ferocious barbarian spirit).
	 */
	static final int BARBARIAN_SPIRIT_TICKS = 300;

	/** Game ticks left at the pyre since the mangled bones were last carried; 0 off the barbarian spirit step. */
	private int barbarianSpiritTicksLeft;

	/**
	 * Items a kill step can't be done without, keyed by the clue text: every group is needed, and any one item of a
	 * group will do, with RuneLite's variations of it (e.g. every Slayer helmet).
	 */
	static final Map<String, List<Set<Integer>>> KILL_ITEMS = Map.of(
		// Wiki: Aberrant spectre, Deviant spectre (a nose peg or Slayer helmet is required)
		"You'll have to plug your nose if you use this source of herbs.", List.of(
			anyOf(ItemID.SLAYER_NOSEPEG, ItemID.SLAYER_HELM)),
		// Wiki: Basilisk, Basilisk Knight (players must equip a mirror shield or V's shield)
		"Reflection is the weakness for these eyes of evil.", List.of(
			anyOf(ItemID.SLAYER_MIRROR_SHIELD, ItemID.V_SHIELD)),
		// Wiki: Barbarian Training (a pyre ship needs any woodcutting axe, logs and a tinderbox), Axe
		BARBARIAN_SPIRIT_STEP, List.of(
			anyOf(ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE, ItemID.MITHRIL_AXE,
				ItemID.ADAMANT_AXE, ItemID.RUNE_AXE, ItemID.DRAGON_AXE, ItemID.CRYSTAL_AXE,
				ItemID.BRONZE_AXE_2H, ItemID.IRON_AXE_2H, ItemID.STEEL_AXE_2H, ItemID.BLACK_AXE_2H, ItemID.MITHRIL_AXE_2H,
				ItemID.ADAMANT_AXE_2H, ItemID.RUNE_AXE_2H, ItemID.DRAGON_AXE_2H, ItemID.CRYSTAL_AXE_2H,
				ItemID.ANMA_AXE, ItemID.TRAIL_GILDED_AXE, ItemID.INFERNAL_AXE, ItemID._3A_AXE, ItemID._3A_AXE_2H),
			anyOf(ItemID.LOGS, ItemID.ACHEY_TREE_LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS, ItemID.TEAK_LOGS,
				ItemID.ARCTIC_PINE_LOG, ItemID.MAPLE_LOGS, ItemID.MAHOGANY_LOGS, ItemID.YEW_LOGS, ItemID.CAMPHOR_LOGS,
				ItemID.MAGIC_LOGS, ItemID.IRONWOOD_LOGS, ItemID.REDWOOD_LOGS, ItemID.ROSEWOOD_LOGS),
			anyOf(ItemID.TINDERBOX))
	);

	private static Set<Integer> anyOf(int... itemIds)
	{
		// In the order given, which is the order Shortest Path names them in
		Set<Integer> items = new LinkedHashSet<>();
		for (int itemId : itemIds)
		{
			items.addAll(ItemVariationMapping.getVariations(itemId));
		}
		return Collections.unmodifiableSet(items);
	}

	private static final WorldPoint FALO_THE_BARD_LOCATION = new WorldPoint(2689, 3550, 0);
	private static final WorldPoint SHERLOCK_LOCATION = new WorldPoint(2735, 3413, 0);
	private static final WorldPoint CHARLIE_THE_TRAMP_LOCATION = new WorldPoint(3208, 3391, 0);

	@Provides
	ShortestClueConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShortestClueConfig.class);
	}

	public ShortestCluePlugin() {
		super();
		this.currentDests = new HashSet<>();
	}

	private DebugClueController debug()
	{
		if (this.debugController == null)
		{
			this.debugController = new DebugClueController(this, client, config, clientToolbar, clientThread,
				itemManager, worldMapPointManager, overlayManager, worldMapOverlay, mouseManager);
		}
		return this.debugController;
	}

	public ClueScrollPlugin getClueScrollPlugin()
	{
		return clueScrollPlugin;
	}

	@Override
	protected void startUp()
	{
		if (config.showDebugPanel())
		{
			debug().installPanel();
		}
		debug().updateMapOverlay();
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event)
	{
		if (!event.getGroup().equals("shortestclue"))
		{
			return;
		}

		debug().handleConfigChanged(event.getKey(), event.getNewValue());
	}

	@Override
	protected void shutDown()
	{
		clearPathIfOurs();
		if (this.debugController != null)
		{
			this.debugController.shutdown();
			this.debugController = null;
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		ClueScroll clue = debug().getFakeClue() != null ? debug().getFakeClue() : clueService.getClue();
		updateBarbarianSpirit(clue);
		if (clue == null)
		{
			debug().updateFakeClueMapPoints(null);
			if (config.clearPathOnClueLost())
			{
				clearPathIfOurs();
			}
			return;
		}

		final Set<WorldPoint> newDests;
		try
		{
			newDests = computeDestinations(clue);
		}
		catch (Exception e)
		{
			log.warn("Failed to resolve clue destinations for {}", clue.getClass().getSimpleName(), e);
			debug().updateFakeClueMapPoints(null);
			return;
		}

		List<Set<Integer>> requiredItems = requiredItems(clue);
		if (!newDests.isEmpty() && (!newDests.equals(this.currentDests) || !requiredItems.equals(this.currentRequiredItems))) {
			pathTo(newDests, requiredItems);
		}

		debug().updateFakeClueMapPoints(clue);
	}

	/**
	 * The items needed for the clue step, as groups: every group is needed, and any one item of a group will do.
	 */
	private List<Set<Integer>> requiredItems(ClueScroll clue)
	{
		List<Set<Integer>> items = new ArrayList<>();
		if (config.requireItems() && clue.isRequiresSpade())
		{
			items.add(Set.of(ItemID.SPADE));
		}
		if (config.requireItems() && clue instanceof CrypticClue)
		{
			items.addAll(killItemsNeeded((CrypticClue) clue));
		}
		return items;
	}

	/**
	 * The kill step's item groups that the player neither wears nor carries; Shortest Path looks in the bank.
	 */
	List<Set<Integer>> killItemsNeeded(CrypticClue clue)
	{
		if (BARBARIAN_SPIRIT_STEP.equals(clue.getText()) && barbarianSpiritUp())
		{
			return List.of();
		}
		List<Set<Integer>> needed = new ArrayList<>();
		for (Set<Integer> group : KILL_ITEMS.getOrDefault(clue.getText(), List.of()))
		{
			if (!hasAny(group))
			{
				needed.add(group);
			}
		}
		return needed;
	}

	/**
	 * Called once a game tick: on the barbarian spirit step, restarts the spirit's time while the mangled bones are
	 * carried and counts it down once they are gone.
	 */
	void updateBarbarianSpirit(ClueScroll clue)
	{
		if (!(clue instanceof CrypticClue) || !BARBARIAN_SPIRIT_STEP.equals(((CrypticClue) clue).getText()))
		{
			barbarianSpiritTicksLeft = 0;
		}
		else if (hasAny(MANGLED_BONES))
		{
			barbarianSpiritTicksLeft = BARBARIAN_SPIRIT_TICKS;
		}
		else if (barbarianSpiritTicksLeft > 0)
		{
			barbarianSpiritTicksLeft--;
		}
	}

	/** Whether the mangled bones were burned less than the spirit's time ago, so the spirit is at the pyre. */
	private boolean barbarianSpiritUp()
	{
		return barbarianSpiritTicksLeft > 0 && !hasAny(MANGLED_BONES);
	}

	private void clearPathIfOurs()
	{
		if (this.currentDests.isEmpty())
		{
			return;
		}
		this.currentDests.clear();
		eventBus.post(new PluginMessage("shortestpath", "clear", new HashMap<>()));
	}

	/**
	 * Paths to the closest target with all required items, visiting a bank when necessary.
	 */
	public void pathTo(Set<WorldPoint> targets, List<Set<Integer>> requiredItems)
	{
		if (client.getLocalPlayer() == null || targets == null || targets.isEmpty())
		{
			return;
		}

		this.currentDests = new HashSet<>(targets);
		this.currentRequiredItems = List.copyOf(requiredItems);

		// Only the identified steps override avoidWilderness; every other path
		// sends back the user's current setting so a prior override doesn't leak.
		boolean wildernessRequired = targets.stream().anyMatch(WILDERNESS_REQUIRED_TARGETS::contains);
		String avoidWildernessConfig = configManager.getConfiguration("shortestpath", "avoidWilderness");
		boolean avoidWilderness = avoidWildernessConfig == null || Boolean.parseBoolean(avoidWildernessConfig);

		Map<String, Object> data = new HashMap<>();
		data.put("start", client.getLocalPlayer().getWorldLocation());
		data.put("target", targetMaps(targets, requiredItems));

		Map<String, Object> configOverride = new HashMap<>();
		configOverride.put("avoidWilderness", wildernessRequired ? Boolean.FALSE : avoidWilderness);
		data.put("config", configOverride);
		eventBus.post(new PluginMessage("shortestpath", "path", data));
	}

	private static Set<Map<String, Object>> targetMaps(Set<WorldPoint> targets, List<Set<Integer>> requiredItems)
	{
		Set<Map<String, Object>> targetMaps = new HashSet<>();
		for (WorldPoint target : targets)
		{
			Map<String, Object> targetMap = new HashMap<>();
			targetMap.put("point", target);
			if (!requiredItems.isEmpty())
			{
				targetMap.put("requirements", requiredItems);
			}
			targetMaps.add(targetMap);
		}
		return targetMaps;
	}

	private WorldPoint applyRemap(WorldPoint dest)
	{
		return REMAPPED_DESTINATIONS.getOrDefault(dest, dest);
	}

	/**
	 * Whether the player wears or carries any of these items, as RuneLite's clue plugin last saw them.
	 */
	boolean hasAny(Set<Integer> itemIds)
	{
		return containsAny(clueScrollPlugin.getEquippedItems(), itemIds)
			|| containsAny(clueScrollPlugin.getInventoryItems(), itemIds);
	}

	private static boolean containsAny(Item[] items, Set<Integer> itemIds)
	{
		if (items == null)
		{
			return false;
		}
		for (Item item : items)
		{
			if (itemIds.contains(item.getId()))
			{
				return true;
			}
		}
		return false;
	}

	public Set<WorldPoint> computeDestinations(ClueScroll clue)
	{
		Set<WorldPoint> newDests = new HashSet<>();
		if (clue instanceof LocationsClueScroll)
		{
			for (WorldPoint wp : ((LocationsClueScroll)clue).getLocations(clueScrollPlugin))
			{
				if (wp != null)
				{
					newDests.add(applyRemap(wp));
				}
			}
		}
		else if (clue instanceof LocationClueScroll)
		{
			WorldPoint wp = ((LocationClueScroll)clue).getLocation(clueScrollPlugin);
			if (wp != null)
			{
				newDests.add(applyRemap(wp));
			}
		}
		if (clue instanceof CrypticClue && newDests.isEmpty())
		{
			String text = ((CrypticClue)clue).getText();
			if (BARBARIAN_SPIRIT_STEP.equals(text))
			{
				// Bones first; once they are in the inventory, the pyre sites, where the spirit appears when they burn
				newDests.addAll(hasAny(MANGLED_BONES) || barbarianSpiritUp() ? PYRE_SITES : MANGLED_BONES_BARBARIANS);
			}
			else
			{
				newDests.addAll(KILL_LOCATIONS.getOrDefault(text, Set.of()));
			}
		}
		if (clue instanceof FaloTheBardClue) {
			newDests.add(FALO_THE_BARD_LOCATION);
		}
		if (clue instanceof SkillChallengeClue) {
			if (((SkillChallengeClue)clue).getNpcs(clueScrollPlugin)[0].equals("Sherlock"))
			{
				newDests.add(SHERLOCK_LOCATION);
			}
			else
			{
				newDests.add(CHARLIE_THE_TRAMP_LOCATION);
			}
		}
		return newDests;
	}
}
