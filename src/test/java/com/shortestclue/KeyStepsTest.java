package com.shortestclue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Map;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import org.junit.Test;

public class KeyStepsTest
{
	@Test
	public void everyKeyStepIsARuneLiteClueWithAChest()
	{
		for (Map.Entry<String, ShortestCluePlugin.KeyStep> entry : ShortestCluePlugin.KEY_STEPS.entrySet())
		{
			CrypticClue clue = CrypticClue.forText(entry.getKey());
			assertNotNull("No RuneLite clue has this text: " + entry.getKey(), clue);
			assertNotNull("RuneLite has no chest location for: " + entry.getKey(), clue.getLocation(null));
			assertFalse("No NPC tile for: " + entry.getKey(), entry.getValue().npcLocations.isEmpty());
			assertFalse(entry.getValue().npcLocations.contains(clue.getLocation(null)));
		}
	}
}
