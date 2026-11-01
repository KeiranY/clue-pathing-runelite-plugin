package com.shortestclue;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ParamID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;

/**
 * Brings the player back to put an emote clue's items in its STASH unit. Once the clue step they were taken out for
 * is done (another step, or no clue), the path goes to the unit first while the player carries all of them. Going
 * far away, teleporting, reading a clue or picking one up skips it for good. Remembered per account. Client thread.
 */
@Singleton
public class StashReturn
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StashReturn.class);

	private static final String CONFIG_GROUP = "shortestclue";
	static final String CONFIG_KEY = "stashTaken";

	/**
	 * Farther than this from the unit, the player has moved on (an emote spot is at most 16 tiles from its unit).
	 */
	static final int FAR_TILES = 32;

	/**
	 * Moving more than this in one tick is a teleport (running moves 2).
	 */
	static final int TELEPORT_TILES = 10;

	private final Client client;
	private final ConfigManager configManager;
	private final ShortestClueConfig config;

	private Taken taken;

	private ClueScroll clue;

	private boolean returning;

	private WorldPoint lastLocation;

	private int lastTick = -1;

	/**
	 * Items taken out of a unit, and the clue step they were taken out for.
	 */
	static final class Taken
	{
		final int unit;
		final String clue;
		final Map<Integer, Integer> items;

		Taken(int unit, String clue, Map<Integer, Integer> items)
		{
			this.unit = unit;
			this.clue = clue;
			this.items = new TreeMap<>(items);
		}

		/**
		 * "unit=28958\nclue=Cheer at Iffie Nitter...\nitems=1007:1,1949:1"
		 */
		String encode()
		{
			return "unit=" + unit + "\nclue=" + clue + "\nitems=" + items.entrySet().stream()
				.map(item -> item.getKey() + ":" + item.getValue())
				.collect(Collectors.joining(","));
		}

		static Taken decode(String text)
		{
			if (text == null)
			{
				return null;
			}
			Map<String, String> lines = new TreeMap<>();
			for (String line : text.split("\n"))
			{
				int equals = line.indexOf('=');
				if (equals > 0)
				{
					lines.put(line.substring(0, equals), line.substring(equals + 1));
				}
			}
			try
			{
				Map<Integer, Integer> items = new TreeMap<>();
				for (String item : lines.getOrDefault("items", "").split(","))
				{
					String[] idCount = item.split(":");
					if (idCount.length == 2)
					{
						items.put(Integer.parseInt(idCount[0]), Integer.parseInt(idCount[1]));
					}
				}
				String clue = lines.get("clue");
				return clue == null || clue.isEmpty() || items.isEmpty() ? null
					: new Taken(Integer.parseInt(lines.get("unit")), clue, items);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof Taken && ((Taken) other).unit == unit && ((Taken) other).clue.equals(clue)
				&& ((Taken) other).items.equals(items);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(unit, clue, items);
		}
	}

	@Inject
	StashReturn(Client client, ConfigManager configManager, ShortestClueConfig config)
	{
		this.client = client;
		this.configManager = configManager;
		this.config = config;
	}

	/**
	 * Loads the logged-in account's items to put back.
	 */
	public void load()
	{
		taken = Taken.decode(configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY));
		returning = false;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(final RuneScapeProfileChanged event)
	{
		load();
	}

	/**
	 * Items came out of a unit. Taken out for the clue step that uses it, they're to be put back once it's done.
	 */
	void tookOut(STASHUnit unit, Map<Integer, Integer> items)
	{
		if (clue instanceof EmoteClue && ((EmoteClue) clue).getStashUnit() == unit && !items.isEmpty())
		{
			save(new Taken(unit.getObjectId(), ((EmoteClue) clue).getText(), items));
		}
		else if (taken != null && taken.unit == unit.getObjectId())
		{
			save(null);
		}
	}

	/**
	 * A unit was found filled: items taken out of it are back.
	 */
	void filled(int unit)
	{
		if (taken != null && taken.unit == unit)
		{
			save(null);
		}
	}

	/**
	 * Each tick, with the current clue: the unit to go back to first, or null.
	 */
	public STASHUnit update(ClueScroll clue)
	{
		this.clue = clue;
		WorldPoint location = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		WorldPoint previous = client.getTickCount() == lastTick + 1 ? lastLocation : null;
		lastLocation = location;
		lastTick = client.getTickCount();

		STASHUnit unit = taken == null ? null : StashUnits.unit(taken.unit);
		returning = unit != null && location != null && config.remindStashPutBack() && !isStep(clue, taken.clue)
			&& carriesAll(StashUnits.carried(client), taken.items);
		if (returning && movedOn(previous, location, unit))
		{
			skip("moved on");
		}
		return returning ? unit : null;
	}

	/**
	 * While going back, reading a clue or picking one up means the player has moved on to it.
	 */
	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event)
	{
		if (!returning)
		{
			return;
		}
		if (("Read".equals(event.getMenuOption()) && isClue(event.getItemId()))
			|| ("Take".equals(event.getMenuOption()) && isGroundItemOption(event.getMenuAction()) && isClue(event.getId())))
		{
			skip(event.getMenuOption() + " a clue");
		}
	}

	private boolean isClue(int itemId)
	{
		// The same test RuneLite's clue plugin uses for clue scroll items
		return itemId > 0 && client.getItemDefinition(itemId).getIntValue(ParamID.CLUE_SCROLL) != -1;
	}

	private static boolean isGroundItemOption(MenuAction action)
	{
		return action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.GROUND_ITEM_SECOND_OPTION
			|| action == MenuAction.GROUND_ITEM_THIRD_OPTION || action == MenuAction.GROUND_ITEM_FOURTH_OPTION
			|| action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
	}

	private void skip(String why)
	{
		log.debug("Not going back to STASH unit {} ({})", StashUnits.unit(taken.unit), why);
		save(null);
		returning = false;
	}

	private void save(Taken taken)
	{
		this.taken = taken;
		if (taken == null)
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY, taken.encode());
		}
	}

	private static boolean isStep(ClueScroll clue, String text)
	{
		return clue instanceof EmoteClue && ((EmoteClue) clue).getText().equals(text);
	}

	static boolean carriesAll(Map<Integer, Integer> carried, Map<Integer, Integer> items)
	{
		return items.entrySet().stream().allMatch(item -> carried.getOrDefault(item.getKey(), 0) >= item.getValue());
	}

	/**
	 * True once the player has gone far from the unit, or teleported since the previous tick (null: not known).
	 */
	static boolean movedOn(WorldPoint previous, WorldPoint location, STASHUnit unit)
	{
		if (previous != null && distance(previous, location) > TELEPORT_TILES)
		{
			return true;
		}
		for (WorldPoint point : unit.getWorldPoints())
		{
			if (distance(point, location) <= FAR_TILES)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Tiles apart, across planes too (a unit upstairs is near).
	 */
	private static int distance(WorldPoint a, WorldPoint b)
	{
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}
}
