package com.shortestclue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import org.junit.Test;

public class KillLocationsTest
{
	@Test
	public void everyKillStepIsARuneLiteClueWithoutALocation()
	{
		for (Map.Entry<String, Set<WorldPoint>> entry : ShortestCluePlugin.KILL_LOCATIONS.entrySet())
		{
			CrypticClue clue = CrypticClue.forText(entry.getKey());
			assertNotNull("No RuneLite clue has this text: " + entry.getKey(), clue);
			assertNull("RuneLite already has a location for: " + entry.getKey(), clue.getLocation(null));
			assertFalse(entry.getValue().isEmpty());
		}
	}

	@Test
	public void killStepPathsToEveryPlaceTheMonsterIsFound()
	{
		CrypticClue clue = CrypticClue.forText("Ghostly bones.");
		assertEquals(ShortestCluePlugin.KILL_LOCATIONS.get("Ghostly bones."),
			new ShortestCluePlugin().computeDestinations(clue));
	}

	@Test
	public void barbarianSpiritStepPathsToThePyreOnceTheBonesAreInTheInventory()
	{
		CrypticClue clue = CrypticClue.forText(ShortestCluePlugin.BARBARIAN_SPIRIT_STEP);
		assertNotNull(clue);
		assertNull(clue.getLocation(null));
		assertEquals(ShortestCluePlugin.MANGLED_BONES_BARBARIANS, carrying().computeDestinations(clue));
		assertEquals(ShortestCluePlugin.PYRE_SITES, carrying(ItemID.BRUT_BARBARIAN_BONES_BAD).computeDestinations(clue));
	}

	@Test
	public void barbarianSpiritStepStaysAtThePyreWhileTheSpiritIsUp()
	{
		CrypticClue clue = CrypticClue.forText(ShortestCluePlugin.BARBARIAN_SPIRIT_STEP);
		Set<Integer> carried = new HashSet<>(Set.of(ItemID.BRUT_BARBARIAN_BONES_BAD, ItemID.DRAGON_AXE, ItemID.LOGS, ItemID.TINDERBOX));
		ShortestCluePlugin plugin = carrying(carried);
		plugin.updateBarbarianSpirit(clue);
		assertEquals(ShortestCluePlugin.PYRE_SITES, plugin.computeDestinations(clue));

		// Burning them takes the bones and the logs; the spirit is up and nothing more is needed
		carried.removeAll(Set.of(ItemID.BRUT_BARBARIAN_BONES_BAD, ItemID.LOGS));
		for (int tick = 1; tick < ShortestCluePlugin.BARBARIAN_SPIRIT_TICKS; tick++)
		{
			plugin.updateBarbarianSpirit(clue);
		}
		assertEquals(ShortestCluePlugin.PYRE_SITES, plugin.computeDestinations(clue));
		assertEquals(List.of(), plugin.killItemsNeeded(clue));

		// The spirit's time is up: new bones, and logs to burn them
		plugin.updateBarbarianSpirit(clue);
		assertEquals(ShortestCluePlugin.MANGLED_BONES_BARBARIANS, plugin.computeDestinations(clue));
		assertEquals(List.of(ShortestCluePlugin.KILL_ITEMS.get(clue.getText()).get(1)), plugin.killItemsNeeded(clue));

		// Another step forgets the burned bones
		carried.add(ItemID.BRUT_BARBARIAN_BONES_BAD);
		plugin.updateBarbarianSpirit(clue);
		carried.remove(ItemID.BRUT_BARBARIAN_BONES_BAD);
		plugin.updateBarbarianSpirit(null);
		assertEquals(ShortestCluePlugin.MANGLED_BONES_BARBARIANS, plugin.computeDestinations(clue));
	}

	@Test
	public void everyKillStepWithItemsIsARuneLiteClue()
	{
		for (Map.Entry<String, List<Set<Integer>>> entry : ShortestCluePlugin.KILL_ITEMS.entrySet())
		{
			assertNotNull("No RuneLite clue has this text: " + entry.getKey(), CrypticClue.forText(entry.getKey()));
			assertFalse(entry.getValue().isEmpty());
			for (Set<Integer> group : entry.getValue())
			{
				assertFalse(group.isEmpty());
			}
		}
	}

	@Test
	public void killStepAsksOnlyForItemsThePlayerHasNot()
	{
		CrypticClue spectres = CrypticClue.forText("You'll have to plug your nose if you use this source of herbs.");
		List<Set<Integer>> noseProtection = ShortestCluePlugin.KILL_ITEMS.get(spectres.getText());
		// RuneLite's variations of the Slayer helmet count too
		assertTrue(noseProtection.get(0).contains(ItemID.SLAYER_HELM_I_ARAXYTE));
		assertEquals(noseProtection, carrying().killItemsNeeded(spectres));
		assertEquals(List.of(), carrying(ItemID.SLAYER_HELM_I_ARAXYTE).killItemsNeeded(spectres));

		CrypticClue pyre = CrypticClue.forText(ShortestCluePlugin.BARBARIAN_SPIRIT_STEP);
		assertEquals(3, carrying().killItemsNeeded(pyre).size());
		assertEquals(List.of(ShortestCluePlugin.KILL_ITEMS.get(pyre.getText()).get(1)),
			carrying(ItemID.DRAGON_AXE, ItemID.TINDERBOX).killItemsNeeded(pyre));
	}

	private static ShortestCluePlugin carrying(Integer... itemIds)
	{
		return carrying(Set.of(itemIds));
	}

	/** A plugin that sees the player carrying these items; changes to the set are seen too. */
	private static ShortestCluePlugin carrying(Set<Integer> carried)
	{
		return new ShortestCluePlugin()
		{
			@Override
			boolean hasAny(Set<Integer> wanted)
			{
				return wanted.stream().anyMatch(carried::contains);
			}
		};
	}
}
