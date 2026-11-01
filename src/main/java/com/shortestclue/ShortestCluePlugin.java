package com.shortestclue;

import java.util.HashMap;
import java.util.HashSet;
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

	/**
	 * Clue steps that are only reachable through the Wilderness, so their path is
	 * allowed to ignore the user's {@code avoidWilderness} setting. The clue
	 * destination is mandatory, so there is no non-wilderness alternative.
	 */
	private static final Set<WorldPoint> WILDERNESS_REQUIRED_TARGETS = Set.of(
		new WorldPoint(2288, 4702, 0), // Cryptic: Kill the King Black Dragon (#25)
		new WorldPoint(2273, 4695, 0), // Cryptic: the King Black Dragon, for that step's key
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
	 * Any chicken drops the Seers' Village step's key, so the chicken spots from the OSRS Wiki that are near a
	 * medium clue step or a teleport (one spawn tile each); the path takes the quickest one.
	 */
	private static final WorldPoint[] CHICKENS = {
		new WorldPoint(2649, 3441, 0), // Ranging Guild entrance (the game cache's tile)
		new WorldPoint(2512, 3862, 0), // Miscellania Castle courtyard
		new WorldPoint(2818, 3559, 0), // West of the Warriors' Guild
		new WorldPoint(2691, 3273, 0), // Between Ardougne and Witchaven
		new WorldPoint(2680, 3664, 0), // Rellekka
		new WorldPoint(1254, 3690, 0), // Gordon and Mary's farm
		new WorldPoint(2788, 3061, 0), // Tai Bwo Wannai
		new WorldPoint(3230, 3298, 0), // Lumbridge East Farm
		new WorldPoint(3174, 3297, 0), // Lumbridge West Farm
		new WorldPoint(1669, 3637, 0), // South of Kourend Castle
		new WorldPoint(2966, 3346, 0), // White Knights' Castle courtyard
		new WorldPoint(3018, 3289, 0), // South Falador Farm
		new WorldPoint(2736, 3562, 0), // Sinclair Mansion
		new WorldPoint(2554, 3563, 0), // Barbarian Agility Course
		new WorldPoint(3197, 3353, 0), // Champions' Guild
		new WorldPoint(3109, 9569, 0), // Wizards' Tower basement
		new WorldPoint(1653, 3067, 0), // South-west Outer Fortis
		new WorldPoint(1398, 2895, 3), // Aldarin windmill, top floor
		new WorldPoint(1242, 3136, 0)  // Tal Teklan
	};

	/**
	 * Clue steps whose chest or drawers need a key that an NPC drops, keyed by the clue text. Until the key
	 * is in the inventory, the path goes to the NPC first. The key and the NPC's tile come from
	 * the game cache's clue helper table for key steps, except the chickens' tiles.
	 */
	static final Map<String, KeyStep> KEY_STEPS = Map.ofEntries(
		// Guard, East Ardougne
		Map.entry("In a town where thieves steal from stalls, search for some drawers in the upstairs of a house near the bank.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE003_KEY, new WorldPoint(2661, 3306, 0))),
		// Barbarian, Barbarian Village
		Map.entry("The dead, red dragon watches over this chest. He must really dig the view.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE011_KEY, new WorldPoint(3082, 3419, 0))),
		// Man
		Map.entry("In a town where wizards are known to gather, search upstairs in a large house to the north.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE005_KEY, new WorldPoint(2594, 3105, 0))),
		// Wizard, Wizards' Tower
		Map.entry("Probably filled with wizards socks.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE014_KEY, new WorldPoint(3109, 3164, 0))),
		// Guard dog, Handelmort Mansion
		Map.entry("In a town where the guards are armed with maces, search the upstairs rooms of the Public House.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE002_KEY, new WorldPoint(2635, 3313, 0))),
		// Market Guard, Rellekka
		Map.entry("Go to this building to be illuminated, and check the drawers while you are there.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE012_KEY, new WorldPoint(2643, 3677, 0))),
		// Penda, Toad and Chicken in Burthorpe
		Map.entry("Go to the village being attacked by trolls, search the drawers in one of the houses.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE008_KEY, new WorldPoint(2909, 3539, 0))),
		// Pirate, Brimhaven
		Map.entry("Search the upstairs drawers of a house in a village where pirates are known to have a good time.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE007_KEY, new WorldPoint(2794, 3185, 0))),
		// Chicken, anywhere
		Map.entry("In a town where everyone has perfect vision, seek some locked drawers in a house that sits opposite a workshop.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE004_KEY, CHICKENS)),
		// Hill Giant
		Map.entry("A graceful man of many colours, his crates must be full of many delights.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE017_KEY, new WorldPoint(1444, 3613, 0))),
		// Monk, Ardougne Monastery
		Map.entry("You'll need to look for a town with a central fountain. Look for a locked chest in the town's chapel.",
			new KeyStep(ItemID.TRAIL_CLUE_MEDIUM_RIDDLE001_KEY, new WorldPoint(2615, 3209, 0))),
		// King Black Dragon
		Map.entry("Scattered coins and gems fill the floor. The chest you seek is in the north east.",
			new KeyStep(ItemID.TRAIL_ELITE_RIDDLE_KEY32, new WorldPoint(2273, 4695, 0)))
	);

	static final class KeyStep
	{
		final int keyItemId;
		final Set<WorldPoint> npcLocations;

		KeyStep(int keyItemId, WorldPoint... npcLocations)
		{
			this.keyItemId = keyItemId;
			this.npcLocations = Set.of(npcLocations);
		}
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

		if (!newDests.isEmpty() && !newDests.equals(this.currentDests)) {
			pathTo(newDests);
		}

		debug().updateFakeClueMapPoints(clue);
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

	public void pathTo(Set<WorldPoint> targets)
	{
		if (client.getLocalPlayer() == null || targets == null || targets.isEmpty())
		{
			return;
		}

		this.currentDests = new HashSet<>(targets);

		// Only the identified steps override avoidWilderness; every other path
		// sends back the user's current setting so a prior override doesn't leak.
		boolean wildernessRequired = targets.stream().anyMatch(WILDERNESS_REQUIRED_TARGETS::contains);
		String avoidWildernessConfig = configManager.getConfiguration("shortestpath", "avoidWilderness");
		boolean avoidWilderness = avoidWildernessConfig == null || Boolean.parseBoolean(avoidWildernessConfig);

		Map<String, Object> data = new HashMap<>();
		data.put("start", client.getLocalPlayer().getWorldLocation());
		data.put("target", targets);

		Map<String, Object> configOverride = new HashMap<>();
		configOverride.put("avoidWilderness", wildernessRequired ? Boolean.FALSE : avoidWilderness);
		data.put("config", configOverride);
		eventBus.post(new PluginMessage("shortestpath", "path", data));
	}

	private WorldPoint applyRemap(WorldPoint dest)
	{
		return REMAPPED_DESTINATIONS.getOrDefault(dest, dest);
	}

	private boolean inventoryContains(int itemId)
	{
		Item[] items = clueScrollPlugin.getInventoryItems();
		if (items == null)
		{
			return false;
		}
		for (Item item : items)
		{
			if (item.getId() == itemId)
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
		if (clue instanceof CrypticClue)
		{
			KeyStep keyStep = KEY_STEPS.get(((CrypticClue)clue).getText());
			if (keyStep != null && !inventoryContains(keyStep.keyItemId))
			{
				// Get the key first; once it is picked up, the path goes on to the chest
				newDests.clear();
				newDests.addAll(keyStep.npcLocations);
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
