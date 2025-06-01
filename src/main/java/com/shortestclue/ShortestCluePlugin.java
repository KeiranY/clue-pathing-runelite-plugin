package com.shortestclue;

import com.google.inject.Provides;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
@PluginDependency(ClueScrollPlugin.class)
public class ShortestCluePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
    private ClueScrollService clueService;

	@Inject
	EventBus eventBus;
	
	private WorldPoint currentDest;

	public ShortestCluePlugin() {
		super();
		this.currentDest = null;
	}

	@Override
	protected void startUp() throws Exception
	{
	}

	@Override
	protected void shutDown() throws Exception
	{
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		ClueScroll clue = clueService.getClue();
		if (clue == null) return;

		WorldPoint newDest = null;
		if (clue instanceof LocationsClueScroll)
		{
			// TODO: Edge case of the Cipher Step for Eluned & Cryptic clue for Viggora - the only cases where "plugin" is currently used
			WorldPoint[] dests = ((LocationsClueScroll)clue).getLocations(null);
			if (dests.length > 0) newDest = dests[0];
		}
		if (clue instanceof LocationClueScroll)
		{
			newDest = ((LocationClueScroll)clue).getLocation(null);
		}
		if (clue instanceof FaloTheBardClue) {
			newDest = new WorldPoint(2689, 3550, 0); // Ripped from the plugin
		}
		if (clue instanceof SkillChallengeClue) {
			// TODO: This is hard coded based on mejrs map but feels like it could be dynamic
			if (((SkillChallengeClue)clue).getNpcs(null)[0] == "Sherlock")
			{
				newDest = new WorldPoint(2735, 3413, 0);
			}
			else
			{
				newDest = new WorldPoint(3208, 3391, 0);
			}
			
		}

		if (newDest != null && !newDest.equals(this.currentDest)) {
			this.currentDest = newDest;

			WorldPoint playerWp = client.getLocalPlayer().getWorldLocation();
			Map<String, Object> data = new HashMap<>();
			data.put("start", playerWp);
			data.put("target", newDest);
			eventBus.post(new PluginMessage("shortestpath", "path", data));
		}
	}

}
