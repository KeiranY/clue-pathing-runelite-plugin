package com.shortestclue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;

/**
 * Static helpers that turn the builtin clue plugin's clue lists into the debug
 * panel entries (tier, type, label and destinations).
 */
final class ClueDebugInfo
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClueDebugInfo.class);

	private static final Class<?>[] CLUE_LIST_CLASSES = new Class<?>[] {
		CrypticClue.class, AnagramClue.class, MapClue.class, BeginnerMapClue.class,
		EmoteClue.class, FaloTheBardClue.class, SkillChallengeClue.class
	};

	private static final Map<Integer, String> TIER_BY_ITEM_ID = buildTierMap();

	private ClueDebugInfo()
	{
	}

	static List<CluePickerPanel.ClueEntry> buildClueEntries(Function<ClueScroll, Set<WorldPoint>> destResolver)
	{
		List<CluePickerPanel.ClueEntry> entries = new ArrayList<>();
		for (ClueScroll clue : enumerateClues())
		{
			final Set<WorldPoint> dests;
			try
			{
				dests = destResolver.apply(clue);
			}
			catch (Exception e)
			{
				log.warn("Failed to resolve destinations for {}", clue.getClass().getSimpleName(), e);
				continue;
			}
			if (dests.isEmpty())
			{
				continue;
			}
			entries.add(new CluePickerPanel.ClueEntry(tierOf(clue), typeOf(clue), describeClue(clue, dests), dests, clue));
		}
		return entries;
	}

	private static Map<Integer, String> buildTierMap()
	{
		Map<Integer, String> map = new HashMap<>();
		try
		{
			// Every clue scroll has its own unique item id; the tier is encoded
			// in the generated ItemID constant name (e.g. TRAIL_CLUE_EASY_*, TRAIL_ELITE_*).
			for (Field field : ItemID.class.getFields())
			{
				if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()))
				{
					continue;
				}
				String name = field.getName();
				String tier = null;
				if (name.startsWith("TRAIL_BEGINNER") || name.startsWith("TRAIL_CLUE_BEGINNER"))
				{
					tier = "Beginner";
				}
				else if (name.startsWith("TRAIL_EASY") || name.startsWith("TRAIL_CLUE_EASY"))
				{
					tier = "Easy";
				}
				else if (name.startsWith("TRAIL_MEDIUM") || name.startsWith("TRAIL_CLUE_MEDIUM"))
				{
					tier = "Medium";
				}
				else if (name.startsWith("TRAIL_HARD") || name.startsWith("TRAIL_CLUE_HARD"))
				{
					tier = "Hard";
				}
				else if (name.startsWith("TRAIL_ELITE") || name.startsWith("TRAIL_CLUE_ELITE"))
				{
					tier = "Elite";
				}
				else if (name.startsWith("TRAIL_MASTER") || name.startsWith("TRAIL_CLUE_MASTER"))
				{
					tier = "Master";
				}
				if (tier != null)
				{
					map.putIfAbsent(field.getInt(null), tier);
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			log.warn("Failed to build clue tier map", e);
		}
		return map;
	}

	private static String tierOf(ClueScroll clue)
	{
		if (clue instanceof BeginnerMapClue)
		{
			return "Beginner";
		}
		if (clue instanceof HotColdClue)
		{
			return ((HotColdClue) clue).isBeginner() ? "Beginner" : "Master";
		}
		if (clue instanceof FaloTheBardClue || clue instanceof SkillChallengeClue)
		{
			return "Master";
		}

		Integer itemId = itemIdOf(clue);
		if (itemId != null)
		{
			String tier = TIER_BY_ITEM_ID.get(itemId);
			if (tier != null)
			{
				return tier;
			}
		}
		return "Unknown";
	}

	private static Integer itemIdOf(ClueScroll clue)
	{
		if (clue instanceof MapClue)
		{
			return ((MapClue) clue).getItemId();
		}
		if (clue instanceof EmoteClue)
		{
			return ((EmoteClue) clue).getItemId();
		}
		if (clue instanceof AnagramClue)
		{
			return ((AnagramClue) clue).getItemId();
		}
		if (clue instanceof CrypticClue)
		{
			for (int id : ((CrypticClue) clue).getItemIds())
			{
				return id;
			}
		}
		return null;
	}

	private static String typeOf(ClueScroll clue)
	{
		if (clue instanceof CrypticClue)
		{
			return "Cryptic";
		}
		if (clue instanceof AnagramClue)
		{
			return "Anagram";
		}
		if (clue instanceof MapClue)
		{
			return "Map";
		}
		if (clue instanceof EmoteClue)
		{
			return "Emote";
		}
		if (clue instanceof FaloTheBardClue)
		{
			return "Falo the Bard";
		}
		if (clue instanceof HotColdClue)
		{
			return "Hot & Cold";
		}
		if (clue instanceof SkillChallengeClue)
		{
			return "Skill Challenge";
		}
		return clue.getClass().getSimpleName();
	}

	private static List<ClueScroll> enumerateClues()
	{
		List<ClueScroll> clues = new ArrayList<>();
		for (Class<?> cls : CLUE_LIST_CLASSES)
		{
			try
			{
				Field field = cls.getDeclaredField("CLUES");
				field.setAccessible(true);
				Object value = field.get(null);
				if (value instanceof List)
				{
					for (Object item : (List<?>) value)
					{
						if (item instanceof ClueScroll)
						{
							clues.add((ClueScroll) item);
						}
					}
				}
			}
			catch (ReflectiveOperationException e)
			{
				log.warn("Failed to enumerate clues from {}", cls.getName(), e);
			}
		}

		try
		{
			for (Field field : HotColdClue.class.getDeclaredFields())
			{
				if (!Modifier.isStatic(field.getModifiers()) || !ClueScroll.class.isAssignableFrom(field.getType()))
				{
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value instanceof ClueScroll)
				{
					clues.add((ClueScroll) value);
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			log.warn("Failed to enumerate HotColdClue singletons", e);
		}

		return clues;
	}

	static String describeClue(ClueScroll clue, Set<WorldPoint> dests)
	{
		String text = clueText(clue);
		StringBuilder sb = new StringBuilder();
		if (text != null && !text.isEmpty())
		{
			sb.append(text);
		}
		if (!dests.isEmpty())
		{
			WorldPoint wp = dests.iterator().next();
			sb.append(" [").append(wp.getX()).append(", ").append(wp.getY()).append(", ").append(wp.getPlane()).append(']');
		}
		return sb.toString();
	}

	private static String clueText(ClueScroll clue)
	{
		if (clue instanceof CrypticClue)
		{
			return ((CrypticClue) clue).getText();
		}
		if (clue instanceof EmoteClue)
		{
			return ((EmoteClue) clue).getText();
		}
		if (clue instanceof FaloTheBardClue)
		{
			return ((FaloTheBardClue) clue).getText();
		}
		if (clue instanceof HotColdClue)
		{
			return ((HotColdClue) clue).getText();
		}
		if (clue instanceof SkillChallengeClue)
		{
			return ((SkillChallengeClue) clue).getChallenge();
		}
		return null;
	}
}
