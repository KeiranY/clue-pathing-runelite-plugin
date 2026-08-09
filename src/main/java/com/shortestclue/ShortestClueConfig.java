package com.shortestclue;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("shortestclue")
public interface ShortestClueConfig extends Config
{
	@ConfigSection(
		name = "Debug",
		description = "Settings for testing and debugging clue pathing",
		position = 10
	)
	String debugSection = "debug";

	@ConfigItem(
		keyName = "showDebugPanel",
		name = "Show debug clue picker",
		description = "Adds a sidebar panel that lets you force any clue to be treated as the active clue, for testing path integration.",
		position = 0,
		section = debugSection
	)
	default boolean showDebugPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDebugMapIcons",
		name = "Show clue icons on world map",
		description = "While the debug picker is open, draws an icon on the world map at each currently filtered clue destination. Clicking an icon sets that clue as the active fake clue.",
		position = 1,
		section = debugSection
	)
	default boolean showDebugMapIcons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clearPathOnClueLost",
		name = "Clear path when clue lost",
		description = "Clears the displayed path when the active clue disappears (e.g. the clue scroll is stored or the step is completed).",
		position = 0
	)
	default boolean clearPathOnClueLost()
	{
		return true;
	}
}
