package com.shortestclue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.dbtable.DBRowConfig;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;

/**
 * Which STASH units are filled, remembered per account, since the game keeps no var for it. The newest news wins,
 * from the game's clue helper, the STASH chart (Watson's noticeboard opens it too) and the player searching a unit.
 * Units are named by their object id, as in RuneLite's {@link STASHUnit}. Client thread.
 */
public class StashUnits
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StashUnits.class);

	private static final String CONFIG_GROUP = "shortestclue";
	static final String CONFIG_KEY = "stashFilled";

	// The STASH chart's client scripts and enums, which RuneLite has no names for
	/** Run by the server when the chart opens; its three ints hold most units' filled bits. */
	static final int CHART_SCRIPT = 1475;
	/** The chart redrawing when a STASH varp or inventory changes, with the same three ints. */
	static final int CHART_REDRAW_SCRIPT = 1476;
	/** Unit -> its tier (1 easy ... 5 master, 6 beginner) and the bit of that tier's built varp, packed. */
	static final int BUILT_ENUM = 1368;
	/** Unit -> a slot of inventory {@link InventoryID#HH_INV}, packed: filled when the slot holds an item. */
	static final int SLOT_ENUM = 1525;
	/** Unit -> which of the three ints (1-3) and the bit that says filled, packed. */
	static final int BIT_ENUM = 1440;

	private static final int[] BUILT_VARPS = {
		VarPlayerID.HH_CONSTRUCTED_EASY, VarPlayerID.HH_CONSTRUCTED_MEDIUM, VarPlayerID.HH_CONSTRUCTED_HARD,
		VarPlayerID.HH_CONSTRUCTED_ELITE, VarPlayerID.HH_CONSTRUCTED_MASTER, VarPlayerID.HH_CONSTRUCTED_BEGINNER
	};

	/**
	 * Units of {@link #SLOT_ENUM} that the chart also counts as filled from a bit of the ints: unit, int (0-2), bit.
	 */
	private static final int[][] SLOT_UNIT_BITS = {
		{ObjectID.HH_ELITE_EXP9, 1, 31},
		{ObjectID.HH_ELITE_EXP10, 2, 0},
		{ObjectID.HH_MASTER021, 2, 11},
		{ObjectID.HH_ELITE_EXP1, 1, 27},
		{ObjectID.HH_ELITE_EXP12, 2, 1},
		{ObjectID.HH_MASTER001, 2, 2},
	};

	/**
	 * The chart never shows the Brittle Isle unit as filled (OSRS Wiki, STASH), so its "empty" isn't believed.
	 */
	private static final int CHART_NEVER_FILLED = ObjectID.HH_MASTER_SAIL;

	/**
	 * A "Search" waits this many ticks for the player to reach the unit and the items to move.
	 */
	private static final int SEARCH_TICKS = 50;
	private static final int SEARCH_DISTANCE = 2;

	private static final Map<Integer, STASHUnit> UNITS = Arrays.stream(STASHUnit.values())
		.collect(Collectors.toMap(STASHUnit::getObjectId, unit -> unit));

	private final Client client;
	private final ConfigManager configManager;

	/**
	 * Unit -> filled (true) or empty (false); units not in it aren't known.
	 */
	private final Map<Integer, Boolean> filled = new TreeMap<>();

	private boolean helperChanged;

	private STASHUnit searched;
	private int searchedTick;
	private Map<Integer, Integer> searchedItems;

	@Inject
	StashUnits(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	/**
	 * True if the unit is built and known to hold its clue's items.
	 */
	public boolean isFilled(STASHUnit unit)
	{
		return unit != null && Boolean.TRUE.equals(filled.get(unit.getObjectId()))
			&& built(unit.getObjectId(), enumValues(BUILT_ENUM), client::getVarpValue);
	}

	/**
	 * Loads the logged-in account's units.
	 */
	public void load()
	{
		filled.clear();
		filled.putAll(decode(configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY)));
		searched = null;
		helperChanged = true;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(final RuneScapeProfileChanged event)
	{
		load();
	}

	@Subscribe
	public void onVarbitChanged(final VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.CLUEHELPER_HIDEYHOLE_FILLED
			|| event.getVarpId() == VarPlayerID.CLUEHELPER_INFOBOX_CLUE)
		{
			// The two may arrive in either order, so they're read on the next tick
			helperChanged = true;
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		if (helperChanged)
		{
			helperChanged = false;
			readHelper();
		}
		if (searched != null)
		{
			checkSearch();
		}
	}

	/**
	 * The game's clue helper tells whether the STASH unit of the clue it shows is filled.
	 */
	private void readHelper()
	{
		int row = client.getVarpValue(VarPlayerID.CLUEHELPER_INFOBOX_CLUE);
		DBRowConfig rowConfig = row > 0 ? client.getDBRowConfig(row) : null;
		if (rowConfig == null || rowConfig.getTableID() != DBTableID.CluehelperClueEmote.ID)
		{
			return;
		}

		Object[] unit = client.getDBTableField(row, DBTableID.CluehelperClueEmote.COL_HIDEY_HOLE_LOC, 0);
		if (unit != null && unit.length > 0 && unit[0] instanceof Integer && UNITS.containsKey(unit[0]))
		{
			news((Integer) unit[0], client.getVarbitValue(VarbitID.CLUEHELPER_HIDEYHOLE_FILLED) == 1, "clue helper");
		}
	}

	/**
	 * The STASH chart opened or redrew: it tells every unit at once.
	 */
	@Subscribe
	public void onScriptPreFired(final ScriptPreFired event)
	{
		if ((event.getScriptId() != CHART_SCRIPT && event.getScriptId() != CHART_REDRAW_SCRIPT)
			|| event.getScriptEvent() == null)
		{
			return;
		}

		// The script's own id comes first, then its arguments
		Object[] arguments = event.getScriptEvent().getArguments();
		if (arguments == null || arguments.length < 4)
		{
			return;
		}
		int[] ints = new int[3];
		for (int i = 0; i < 3; i++)
		{
			if (!(arguments[i + 1] instanceof Integer))
			{
				return;
			}
			ints[i] = (Integer) arguments[i + 1];
		}

		ItemContainer slots = client.getItemContainer(InventoryID.HH_INV);
		IntUnaryOperator slotItem = slots == null ? null : slot ->
		{
			Item item = slots.getItem(slot);
			return item == null ? -1 : item.getId();
		};
		IntUnaryOperator builtEnum = enumValues(BUILT_ENUM);
		IntUnaryOperator slotEnum = enumValues(SLOT_ENUM);
		IntUnaryOperator bitEnum = enumValues(BIT_ENUM);
		for (int unit : UNITS.keySet())
		{
			Boolean isFilled = chartFilled(unit, ints, builtEnum, slotEnum, bitEnum, client::getVarpValue, slotItem);
			if (isFilled != null && (isFilled || unit != CHART_NEVER_FILLED))
			{
				news(unit, isFilled, "STASH chart");
			}
		}
	}

	/**
	 * "Search" on a built unit puts its clue's items in or takes them out, once the player gets there. Any other
	 * click means the player went to do something else.
	 */
	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event)
	{
		STASHUnit unit = UNITS.get(event.getId());
		if (unit != null && isObjectOption(event.getMenuAction()) && "Search".equals(event.getMenuOption())
			&& built(unit.getObjectId(), enumValues(BUILT_ENUM), client::getVarpValue))
		{
			searched = unit;
			searchedTick = client.getTickCount();
			searchedItems = carried();
			return;
		}
		searched = null;
	}

	private static boolean isObjectOption(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	private void checkSearch()
	{
		if (client.getTickCount() - searchedTick > SEARCH_TICKS || client.getLocalPlayer() == null)
		{
			searched = null;
			return;
		}

		Map<Integer, Integer> items = carried();
		if (!isNear(client.getLocalPlayer().getWorldLocation(), searched))
		{
			// Still on the way: a change there is something else
			searchedItems = items;
			return;
		}
		if (items.equals(searchedItems))
		{
			return;
		}

		Boolean put = putIn(searchedItems, items);
		if (put != null)
		{
			news(searched.getObjectId(), put, put ? "items put in" : "items taken out");
		}
		searched = null;
	}

	private static boolean isNear(WorldPoint player, STASHUnit unit)
	{
		for (WorldPoint point : unit.getWorldPoints())
		{
			if (player.getPlane() == point.getPlane()
				&& Math.max(Math.abs(player.getX() - point.getX()), Math.abs(player.getY() - point.getY())) <= SEARCH_DISTANCE)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What the player carries and wears: item id -> count.
	 */
	private Map<Integer, Integer> carried()
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(id);
			if (container != null)
			{
				for (Item item : container.getItems())
				{
					if (item.getId() > 0 && item.getQuantity() > 0)
					{
						items.merge(item.getId(), item.getQuantity(), Integer::sum);
					}
				}
			}
		}
		return items;
	}

	private void news(int unit, boolean isFilled, String source)
	{
		if (Boolean.valueOf(isFilled).equals(filled.put(unit, isFilled)))
		{
			return;
		}
		log.debug("STASH unit {} {} ({})", UNITS.get(unit), isFilled ? "filled" : "empty", source);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY, encode(filled));
	}

	private IntUnaryOperator enumValues(int enumId)
	{
		EnumComposition values = client.getEnum(enumId);
		return key -> values == null ? -1 : values.getIntValue(key);
	}

	/**
	 * Whether a unit is built, the way the STASH chart's script 1479 reads it.
	 */
	static boolean built(int unit, IntUnaryOperator builtEnum, IntUnaryOperator varp)
	{
		int tierBit = builtEnum.applyAsInt(unit);
		int tier = coordX(tierBit);
		return tierBit != -1 && tier >= 1 && tier <= BUILT_VARPS.length
			&& bit(varp.applyAsInt(BUILT_VARPS[tier - 1]), coordY(tierBit));
	}

	/**
	 * Whether the STASH chart shows a unit as filled, the way its script 1479 decides it: true or false, or null when
	 * it can't tell (inventory {@link InventoryID#HH_INV} never arrived). A unit that isn't built holds nothing.
	 *
	 * @param ints     the three ints of {@link #CHART_SCRIPT}
	 * @param slotItem the item in a slot of inventory {@link InventoryID#HH_INV} (-1 for none), or null when the game
	 *                 hasn't sent that inventory
	 */
	static Boolean chartFilled(int unit, int[] ints, IntUnaryOperator builtEnum, IntUnaryOperator slotEnum,
		IntUnaryOperator bitEnum, IntUnaryOperator varp, IntUnaryOperator slotItem)
	{
		if (builtEnum.applyAsInt(unit) == -1)
		{
			return null;
		}
		if (!built(unit, builtEnum, varp))
		{
			return false;
		}

		int slot = slotEnum.applyAsInt(unit);
		if (slot != -1)
		{
			if (slotItem != null && slotItem.applyAsInt(coordX(slot)) != -1)
			{
				return true;
			}
			for (int[] unitBit : SLOT_UNIT_BITS)
			{
				if (unitBit[0] == unit && bit(ints[unitBit[1]], unitBit[2]))
				{
					return true;
				}
			}
			return slotItem == null ? null : false;
		}

		int which = bitEnum.applyAsInt(unit);
		if (which != -1 && coordX(which) >= 1 && coordX(which) <= 3)
		{
			return bit(ints[coordX(which) - 1], coordY(which));
		}
		return null;
	}

	/**
	 * What a "Search" did, from what the player carried and wore before and after: items only gone means put in
	 * (true), items only come means taken out (false); both at once is something else (null).
	 */
	static Boolean putIn(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		boolean gone = false;
		boolean come = false;
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		for (int id : ids)
		{
			int change = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			gone |= change < 0;
			come |= change > 0;
		}
		return gone == come ? null : gone;
	}

	/**
	 * "28958:1,28959:0": unit, 1 filled or 0 empty.
	 */
	static String encode(Map<Integer, Boolean> units)
	{
		return new TreeMap<>(units).entrySet().stream()
			.map(unit -> unit.getKey() + ":" + (unit.getValue() ? 1 : 0))
			.collect(Collectors.joining(","));
	}

	static Map<Integer, Boolean> decode(String text)
	{
		Map<Integer, Boolean> units = new TreeMap<>();
		if (text == null)
		{
			return units;
		}
		for (String part : text.split(","))
		{
			String[] unitFilled = part.trim().split(":");
			if (unitFilled.length == 2 && (unitFilled[1].equals("1") || unitFilled[1].equals("0")))
			{
				try
				{
					units.put(Integer.parseInt(unitFilled[0]), unitFilled[1].equals("1"));
				}
				catch (NumberFormatException e)
				{
					// A damaged part is skipped
				}
			}
		}
		return units;
	}

	private static int coordX(int coord)
	{
		return (coord >> 14) & 0x3FFF;
	}

	private static int coordY(int coord)
	{
		return coord & 0x3FFF;
	}

	private static boolean bit(int value, int bit)
	{
		return bit >= 0 && bit < 32 && (value & (1 << bit)) != 0;
	}
}
