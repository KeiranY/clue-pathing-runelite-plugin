package com.shortestclue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

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
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
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

	@Inject
	private StashUnits stashUnits;

	@Inject
	private StashOverlay stashOverlay;

	@Inject
	private StashReturn stashReturn;

	private DebugClueController debugController;

	private Set<WorldPoint> currentDests;

	private List<Set<Integer>> currentRequiredItems = List.of();

	private EmoteClue emoteItemsClue;

	private Item[] emoteItemsCarried;

	private List<Set<Integer>> emoteItems = List.of();

	/**
	 * Whether the emote clue's STASH unit is built but not known to be filled or empty.
	 */
	private volatile boolean stashUnknown;

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

	boolean isStashUnknown()
	{
		return stashUnknown;
	}

	@Override
	protected void startUp()
	{
		eventBus.register(stashUnits);
		eventBus.register(stashReturn);
		overlayManager.add(stashOverlay);
		clientThread.invoke(() ->
		{
			stashUnits.load();
			stashReturn.load();
		});
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
		eventBus.unregister(stashUnits);
		eventBus.unregister(stashReturn);
		overlayManager.remove(stashOverlay);
		stashUnknown = false;
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
		stashUnknown = config.requireItems() && clue instanceof EmoteClue
			&& stashUnits.isUnknown(((EmoteClue) clue).getStashUnit());

		// The items taken out of a STASH unit for the last step go back in first
		STASHUnit putBack = stashReturn.update(clue);
		if (putBack != null)
		{
			Set<WorldPoint> unitTiles = new HashSet<>(Arrays.asList(putBack.getWorldPoints()));
			if (!unitTiles.equals(this.currentDests) || !this.currentRequiredItems.isEmpty())
			{
				pathTo(unitTiles, List.of());
			}
			debug().updateFakeClueMapPoints(clue);
			return;
		}

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
		if (config.requireItems() && clue instanceof EmoteClue)
		{
			items.addAll(emoteItems((EmoteClue) clue));
		}
		return items;
	}

	/**
	 * The emote clue's items that the player neither wears nor carries, worked out again only when the clue or
	 * those items change. None when they wait in the clue's STASH unit.
	 */
	private List<Set<Integer>> emoteItems(EmoteClue clue)
	{
		if (stashUnits.isFilled(clue.getStashUnit()))
		{
			return List.of();
		}

		Item[] carried = carriedItems();
		if (clue != emoteItemsClue || !Arrays.equals(carried, emoteItemsCarried))
		{
			emoteItemsClue = clue;
			emoteItemsCarried = carried;
			emoteItems = itemsNeeded(clue.getItemRequirements(), carried, client.getItemCount(),
				id -> client.getItemDefinition(id).getNote() != -1);
		}
		return emoteItems;
	}

	/**
	 * What the player wears, then what they carry, the way RuneLite's emote clue overlay checks them.
	 */
	private Item[] carriedItems()
	{
		Item[] worn = clueScrollPlugin.getEquippedItems() != null ? clueScrollPlugin.getEquippedItems() : new Item[0];
		Item[] inventory = clueScrollPlugin.getInventoryItems() != null ? clueScrollPlugin.getInventoryItems() : new Item[0];
		return withItems(worn, Arrays.asList(inventory));
	}

	/**
	 * The items still needed for the requirements that what the player wears and carries doesn't meet, as groups:
	 * every group is needed, and any one item of a group will do (e.g. any charge of a ring). When several sets
	 * will do, one of them is asked for. A requirement no item can meet ("nothing on your head") is left out.
	 * The bank isn't looked at: Shortest Path checks it, and visits a bank first when the items are there.
	 *
	 * @param noted whether an item id is a noted copy, which can't be worn, so it's never asked for
	 */
	static List<Set<Integer>> itemsNeeded(ItemRequirement[] requirements, Item[] carried, int itemCount, IntPredicate noted)
	{
		List<Set<Integer>> groups = new ArrayList<>();
		for (ItemRequirement requirement : requirements)
		{
			if (requirement.fulfilledBy(carried))
			{
				continue;
			}

			List<Item> candidates = new ArrayList<>();
			for (int id = 0; id < itemCount; id++)
			{
				if (requirement.fulfilledBy(id) && !noted.test(id))
				{
					candidates.add(new Item(id, 1));
				}
			}
			if (!requirement.fulfilledBy(withItems(carried, candidates)))
			{
				continue;
			}

			// One way to meet it: keep only the items it can't do without, e.g. one item for "any of these"
			List<Item> picked = new ArrayList<>(candidates);
			for (Item item : candidates)
			{
				picked.remove(item);
				if (!requirement.fulfilledBy(withItems(carried, picked)))
				{
					picked.add(item);
				}
			}

			// Each picked item may be swapped for any item that does the same job, e.g. another charge
			for (Item item : picked)
			{
				List<Item> others = new ArrayList<>(picked);
				others.remove(item);
				Set<Integer> group = new LinkedHashSet<>();
				for (Item candidate : candidates)
				{
					others.add(candidate);
					if (requirement.fulfilledBy(withItems(carried, others)))
					{
						group.add(candidate.getId());
					}
					others.remove(others.size() - 1);
				}
				groups.add(group);
			}
		}
		return groups;
	}

	private static Item[] withItems(Item[] items, List<Item> more)
	{
		Item[] all = Arrays.copyOf(items, items.length + more.size());
		for (int i = 0; i < more.size(); i++)
		{
			all[items.length + i] = more.get(i);
		}
		return all;
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
