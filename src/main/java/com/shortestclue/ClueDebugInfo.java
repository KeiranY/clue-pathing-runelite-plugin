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
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.BeginnerMapClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
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
		EmoteClue.class, FaloTheBardClue.class, SkillChallengeClue.class,
		CipherClue.class, CoordinateClue.class
	};

	private ClueDebugInfo()
	{
	}

	static List<CluePickerPanel.ClueEntry> buildClueEntries(ClueScrollPlugin clueScrollPlugin, Function<ClueScroll, Set<WorldPoint>> destResolver)
	{
		List<CluePickerPanel.ClueEntry> entries = new ArrayList<>();
		for (ClueScroll clue : enumerateClues())
		{
			Set<WorldPoint> dests;
			String failure = null;
			try
			{
				dests = destResolver.apply(clue);
			}
			catch (Exception e)
			{
				log.warn("Failed to resolve destinations for {}", clue.getClass().getSimpleName(), e);
				dests = Set.of();
				failure = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
			}
			String label = describeClue(clue, dests, clueScrollPlugin);
			if (failure == null && dests.isEmpty())
			{
				label += "  (no location)";
			}
			entries.add(new CluePickerPanel.ClueEntry(tierOf(clue), typeOf(clue), label, dests, clue, failure));
		}
		return entries;
	}

	// Every clue scroll has its own unique item id; the tier is encoded in the
	// generated ItemID constant name (e.g. TRAIL_CLUE_EASY_*, TRAIL_ELITE_*).
	// These are kept as literals (mirroring the builtin clue plugin) so the plugin
	// has no runtime dependency on the gameval constants that only exist when the
	// full API is on the classpath.
	private static final int[] BEGINNER_IDS = {
		23182,
	};

	private static final int[] EASY_IDS = {
		2677, 2678, 2679, 2680, 2681, 2682, 2683, 2684, 2685, 2686, 2687, 2688,
		2689, 2690, 2691, 2692, 2693, 2694, 2695, 2696, 2697, 2698, 2699, 2700,
		2701, 2702, 2703, 2704, 2705, 2706, 2707, 2708, 2709, 2710, 2711, 2712,
		2713, 2714, 2716, 2717, 2719, 2720, 3490, 3491, 3492, 3493, 3494, 3495,
		3496, 3497, 3498, 3499, 3500, 3501, 3502, 3503, 3504, 3505, 3506, 3507,
		3508, 3509, 3510, 3511, 3512, 3513, 3514, 3515, 3516, 3517, 3518, 3519,
		7236, 7237, 7238, 10180, 10181, 10182, 10183, 10184, 10185, 10186, 10187, 10188,
		10189, 10190, 10191, 10192, 10193, 10194, 10195, 10196, 10197, 10198, 10199, 10200,
		10201, 10202, 10203, 10204, 10205, 10206, 10207, 10208, 10209, 10210, 10211, 10212,
		10213, 10214, 10215, 10216, 10217, 10218, 10219, 10220, 10221, 10222, 10223, 10224,
		10225, 10226, 10227, 10228, 10229, 10230, 10231, 10232, 10233, 12162, 12163, 12164,
		12165, 12166, 12167, 12168, 12169, 12170, 12171, 12172, 12173, 12174, 12175, 12176,
		12177, 12178, 12179, 12180, 12181, 12182, 12183, 12184, 12185, 12186, 12187, 12188,
		12189, 12190, 12191, 12192, 19814, 19815, 19816, 19817, 19818, 19819, 19820, 19821,
		19822, 19823, 19824, 19825, 19826, 19827, 19828, 19829, 19830, 19831, 19832, 19833,
		19834, 22001, 23149, 23150, 23151, 23152, 23153, 23154, 23155, 23156, 23157, 23158,
		23159, 23160, 23161, 23162, 23163, 23164, 23165, 23166, 25788, 25789, 28913, 28914,
		29853, 29854, 29860, 29863, 30928, 31268,
	};

	private static final int[] MEDIUM_IDS = {
		2801, 2802, 2803, 2804, 2805, 2806, 2807, 2808, 2809, 2810, 2811, 2812,
		2813, 2814, 2815, 2816, 2817, 2818, 2819, 2820, 2821, 2822, 2823, 2824,
		2825, 2826, 2827, 2828, 2829, 2830, 2831, 2832, 2833, 2834, 2835, 2836,
		2837, 2838, 2839, 2840, 2841, 2842, 2843, 2844, 2845, 2846, 2847, 2848,
		2849, 2850, 2851, 2852, 2853, 2854, 2855, 2856, 2857, 2858, 3582, 3583,
		3584, 3585, 3586, 3587, 3588, 3589, 3590, 3591, 3592, 3593, 3594, 3595,
		3596, 3597, 3598, 3599, 3600, 3601, 3602, 3603, 3604, 3605, 3606, 3607,
		3608, 3609, 3610, 3611, 3612, 3613, 3614, 3615, 3616, 3617, 3618, 7274,
		7275, 7276, 7277, 7278, 7279, 7280, 7281, 7282, 7283, 7284, 7285, 7286,
		7287, 7288, 7289, 7290, 7291, 7292, 7293, 7294, 7295, 7296, 7297, 7298,
		7299, 7300, 7301, 7302, 7303, 7304, 7305, 7306, 7307, 7308, 7309, 7310,
		7311, 7312, 7313, 7314, 7315, 7316, 7317, 7318, 10254, 10255, 10256, 10257,
		10258, 10259, 10260, 10261, 10262, 10263, 10264, 10265, 10266, 10267, 10268, 10269,
		10270, 10271, 10272, 10273, 10274, 10275, 10276, 10277, 10278, 10279, 12021, 12022,
		12023, 12024, 12025, 12026, 12027, 12028, 12029, 12030, 12031, 12032, 12033, 12034,
		12035, 12036, 12037, 12038, 12039, 12040, 12041, 12042, 12043, 12044, 12045, 12046,
		12047, 12048, 12049, 12050, 12051, 12052, 12053, 12054, 12055, 12056, 12057, 12058,
		12059, 12060, 12061, 12062, 12063, 12064, 12065, 12066, 12067, 12068, 12069, 12070,
		12071, 12072, 19734, 19735, 19736, 19737, 19738, 19739, 19740, 19741, 19742, 19743,
		19744, 19745, 19746, 19747, 19748, 19749, 19750, 19751, 19752, 19753, 19754, 19755,
		19756, 19757, 19758, 19759, 19760, 19761, 19762, 19763, 19764, 19765, 19766, 19767,
		19768, 19769, 19770, 19771, 19772, 19773, 19774, 19775, 19776, 19777, 19778, 19779,
		19780, 19781, 23046, 23131, 23132, 23133, 23134, 23135, 23136, 23137, 23138, 23139,
		23140, 23141, 23142, 23143, 25783, 25784, 25785, 28907, 28908, 28909, 29857, 29858,
		29861, 29862, 30933, 30934, 30935, 31274, 31275,
	};

	private static final int[] HARD_IDS = {
		2722, 2723, 2724, 2725, 2726, 2727, 2728, 2729, 2730, 2731, 2732, 2733,
		2734, 2735, 2736, 2737, 2738, 2739, 2740, 2741, 2742, 2743, 2744, 2745,
		2746, 2747, 2748, 2773, 2774, 2775, 2776, 2777, 2778, 2779, 2780, 2781,
		2782, 2783, 2784, 2785, 2786, 2787, 2788, 2789, 2790, 2791, 2792, 2793,
		2794, 2795, 2796, 2797, 2798, 2799, 2800, 3520, 3521, 3522, 3523, 3524,
		3525, 3526, 3527, 3528, 3529, 3530, 3531, 3532, 3533, 3534, 3535, 3536,
		3537, 3538, 3539, 3540, 3541, 3542, 3543, 3544, 3545, 3546, 3547, 3548,
		3549, 3550, 3551, 3552, 3553, 3554, 3555, 3556, 3557, 3558, 3559, 3560,
		3561, 3562, 3563, 3564, 3565, 3566, 3567, 3568, 3569, 3570, 3571, 3572,
		3573, 3574, 3575, 3576, 3577, 3578, 3579, 3580, 3581, 7239, 7240, 7241,
		7242, 7243, 7244, 7245, 7246, 7247, 7248, 7249, 7250, 7251, 7252, 7253,
		7254, 7255, 7256, 7257, 7258, 7259, 7260, 7261, 7262, 7263, 7264, 7265,
		7266, 7267, 7268, 7269, 7270, 7271, 7272, 7273, 10234, 10235, 10236, 10237,
		10238, 10239, 10240, 10241, 10242, 10243, 10244, 10245, 10246, 10247, 10248, 10249,
		10250, 10251, 10252, 10253, 12542, 12543, 12544, 12545, 12546, 12547, 12548, 12549,
		12550, 12551, 12552, 12553, 12554, 12555, 12556, 12557, 12558, 12559, 12560, 12561,
		12562, 12563, 12564, 12565, 12566, 12567, 12568, 12569, 12570, 12571, 12572, 12573,
		12574, 12575, 12576, 12577, 12578, 12579, 12580, 12581, 12582, 12583, 12584, 12585,
		12586, 12587, 12588, 12589, 12590, 12591, 19840, 19841, 19842, 19843, 19844, 19845,
		19846, 19847, 19848, 19849, 19850, 19851, 19852, 19853, 19854, 19855, 19856, 19857,
		19858, 19859, 19860, 19861, 19862, 19863, 19864, 19865, 19866, 19867, 19868, 19869,
		19870, 19871, 19872, 19873, 19874, 19875, 19876, 19877, 19878, 19879, 19880, 19881,
		19882, 19883, 19884, 19885, 19886, 19887, 19888, 19889, 19890, 19891, 19892, 19893,
		19894, 19895, 19896, 19897, 19898, 19899, 19900, 19901, 19902, 19903, 19904, 19905,
		19906, 19907, 19908, 19909, 19910, 19911, 21526, 21527, 23045, 23167, 23168, 23169,
		23170, 23171, 23172, 23173, 23174, 23175, 23176, 23177, 23178, 23179, 23180, 23181,
		24493, 24494, 25790, 25791, 25792, 26566, 28915, 28916, 28917, 28918, 29859, 29864,
		30929, 30930, 30931, 31272, 31273,
	};

	private static final int[] ELITE_IDS = {
		12073, 12074, 12075, 12076, 12077, 12078, 12079, 12080, 12081, 12082, 12083, 12084,
		12085, 12086, 12087, 12088, 12089, 12090, 12091, 12092, 12093, 12094, 12095, 12096,
		12097, 12098, 12099, 12100, 12101, 12102, 12103, 12104, 12105, 12106, 12107, 12108,
		12109, 12110, 12111, 12112, 12113, 12114, 12115, 12116, 12117, 12118, 12119, 12120,
		12121, 12122, 12123, 12124, 12125, 12126, 12127, 12128, 12129, 12130, 12131, 12132,
		12133, 12134, 12135, 12136, 12137, 12138, 12139, 12140, 12141, 12142, 12143, 12144,
		12145, 12146, 12147, 12148, 12149, 12150, 12151, 12152, 12153, 12154, 12155, 12156,
		12157, 12158, 12159, 12160, 12161, 19782, 19783, 19784, 19785, 19786, 19787, 19788,
		19789, 19790, 19791, 19792, 19793, 19794, 19795, 19796, 19797, 19798, 19799, 19800,
		19801, 19802, 19803, 19804, 19805, 19806, 19807, 19808, 19809, 19810, 19811, 19812,
		19813, 21524, 21525, 22000, 23144, 23145, 23146, 23147, 23148, 23770, 24253, 24773,
		25498, 25499, 25786, 25787, 26943, 26944, 28910, 28911, 28912, 29855, 29856, 30932,
		31269, 31270, 31271,
	};

	private static final int[] MASTER_IDS = {
		19835, 19837, 19838, 19839, 20280, 20281, 20282, 23417,
	};

	private static final Map<Integer, String> TIER_BY_ITEM_ID = buildTierMap();

	private static Map<Integer, String> buildTierMap()
	{
		Map<Integer, String> map = new HashMap<>();
		putTier(map, "Beginner", BEGINNER_IDS);
		putTier(map, "Easy", EASY_IDS);
		putTier(map, "Medium", MEDIUM_IDS);
		putTier(map, "Hard", HARD_IDS);
		putTier(map, "Elite", ELITE_IDS);
		putTier(map, "Master", MASTER_IDS);
		return map;
	}

	private static void putTier(Map<Integer, String> map, String tier, int[] ids)
	{
		for (int id : ids)
		{
			map.put(id, tier);
		}
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
		if (clue instanceof CipherClue)
		{
			return ((CipherClue) clue).getItemId();
		}
		if (clue instanceof CoordinateClue)
		{
			return ((CoordinateClue) clue).getItemId();
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
		if (clue instanceof CipherClue)
		{
			return "Cipher";
		}
		if (clue instanceof CoordinateClue)
		{
			return "Coordinate";
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
				else if (value instanceof Map)
				{
					for (Object item : ((Map<?, ?>) value).values())
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

	static String describeClue(ClueScroll clue, Set<WorldPoint> dests, ClueScrollPlugin clueScrollPlugin)
	{
		String text = clueText(clue, clueScrollPlugin);
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

	private static String clueText(ClueScroll clue, ClueScrollPlugin plugin)
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
		if (clue instanceof AnagramClue)
		{
			return describeAnagram((AnagramClue) clue, plugin);
		}
		if (clue instanceof CipherClue)
		{
			return describeCipher((CipherClue) clue, plugin);
		}
		return null;
	}

	// AnagramClue.getText() is not exposed (the text field is @Getter(AccessLevel.NONE));
	// the text/npc/area live behind Function<ClueScrollPlugin, ...> providers, so the
	// plugin is threaded through. Every access is guarded so one failing clue can't
	// break the panel.
	private static String describeAnagram(AnagramClue clue, ClueScrollPlugin plugin)
	{
		StringBuilder sb = new StringBuilder();
		try
		{
			Function<ClueScrollPlugin, String> textProvider = clue.getTextProvider();
			if (textProvider != null)
			{
				String text = textProvider.apply(plugin);
				if (text != null && !text.isEmpty())
				{
					sb.append(text);
				}
			}

			String npc = null;
			try
			{
				String[] npcs = clue.getNpcs(plugin);
				if (npcs != null && npcs.length > 0)
				{
					npc = npcs[0];
				}
			}
			catch (Exception e)
			{
				// some npc providers read varbits and can fail
			}

			String area = clue.getArea();
			boolean hasNpc = npc != null && !npc.isEmpty();
			boolean hasArea = area != null && !area.isEmpty();
			if (hasNpc || hasArea)
			{
				sb.append(" (");
				if (hasNpc)
				{
					sb.append(npc);
				}
				if (hasNpc && hasArea)
				{
					sb.append(", ");
				}
				if (hasArea)
				{
					sb.append(area);
				}
				sb.append(')');
			}
		}
		catch (Exception e)
		{
			// never let a single anagram's text break the panel
		}
		return sb.toString();
	}

	private static String describeCipher(CipherClue clue, ClueScrollPlugin plugin)
	{
		StringBuilder sb = new StringBuilder();
		try
		{
			String text = clue.getText();
			if (text != null && !text.isEmpty())
			{
				sb.append(text);
			}
			String[] npcs = clue.getNpcs(plugin);
			if (npcs != null && npcs.length > 0 && npcs[0] != null && !npcs[0].isEmpty())
			{
				sb.append(" (").append(npcs[0]).append(')');
			}
		}
		catch (Exception e)
		{
			// never let a single cipher's text break the panel
		}
		return sb.toString();
	}
}
