package com.shortestclue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

/**
 * Owns all the debug features: the clue picker sidebar panel, the fake clue, the
 * tier-coloured world map icons and their click handling. The plugin only wires
 * this in and reads {@link #getFakeClue()} back for pathing.
 */
class DebugClueController
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DebugClueController.class);

	private final ShortestCluePlugin plugin;
	private final Client client;
	private final ShortestClueConfig config;
	private final ClientToolbar clientToolbar;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final WorldMapPointManager worldMapPointManager;
	private final OverlayManager overlayManager;
	private final WorldMapOverlay worldMapOverlay;
	private final MouseManager mouseManager;

	private volatile ClueScroll fakeClue;
	private NavigationButton navButton;
	private CluePickerPanel cluePanel;
	private boolean panelPending;
	private ClueMapOverlay clueMapOverlay;

	private volatile List<CluePickerPanel.ClueEntry> visibleEntries = new ArrayList<>();

	DebugClueController(ShortestCluePlugin plugin, Client client, ShortestClueConfig config,
		ClientToolbar clientToolbar, ClientThread clientThread, ItemManager itemManager,
		WorldMapPointManager worldMapPointManager, OverlayManager overlayManager,
		WorldMapOverlay worldMapOverlay, MouseManager mouseManager)
	{
		this.plugin = plugin;
		this.client = client;
		this.config = config;
		this.clientToolbar = clientToolbar;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.worldMapPointManager = worldMapPointManager;
		this.overlayManager = overlayManager;
		this.worldMapOverlay = worldMapOverlay;
		this.mouseManager = mouseManager;
	}

	void installPanel()
	{
		if (this.navButton != null || this.panelPending)
		{
			return;
		}

		// Resolving clue destinations reads varbits and must happen on the client
		// thread, then the Swing panel is built back on the EDT.
		this.panelPending = true;
		this.clientThread.invoke(() ->
		{
			final List<CluePickerPanel.ClueEntry> entries = ClueDebugInfo.buildClueEntries(this.plugin::computeDestinations);
			SwingUtilities.invokeLater(() ->
			{
				this.panelPending = false;
				if (!this.config.showDebugPanel() || this.navButton != null)
				{
					return;
				}

				this.cluePanel = new CluePickerPanel(entries, this::setFakeClue, () -> setFakeClue(null), this::onPanelFilterChange);
				this.navButton = NavigationButton.builder()
					.icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
					.tooltip("Shortest Clue debug")
					.panel(this.cluePanel)
					.priority(100)
					.build();
				this.clientToolbar.addNavigation(this.navButton);
			});
		});
	}

	void removePanel()
	{
		setFakeClue(null);
		this.panelPending = false;
		if (this.navButton != null)
		{
			this.clientToolbar.removeNavigation(this.navButton);
			this.navButton = null;
		}
		this.cluePanel = null;
	}

	void shutdown()
	{
		removePanel();
		worldMapPointManager.removeIf(FakeClueWorldMapPoint.class::isInstance);
		if (this.clueMapOverlay != null)
		{
			overlayManager.remove(this.clueMapOverlay);
			mouseManager.unregisterMouseListener(this.clueMapOverlay);
			this.clueMapOverlay = null;
		}
	}

	void handleConfigChanged(String key, String value)
	{
		if (key.equals("showDebugPanel"))
		{
			if (Boolean.parseBoolean(value))
			{
				installPanel();
			}
			else
			{
				removePanel();
			}
			updateMapOverlay();
		}
		else if (key.equals("showDebugMapIcons"))
		{
			updateMapOverlay();
		}
	}

	void setFakeClue(ClueScroll clue)
	{
		this.fakeClue = clue;
		if (this.cluePanel != null)
		{
			final CluePickerPanel panel = this.cluePanel;
			SwingUtilities.invokeLater(() -> panel.setFakeClue(clue));
		}
	}

	ClueScroll getFakeClue()
	{
		return this.fakeClue;
	}

	List<CluePickerPanel.ClueEntry> getVisibleEntries()
	{
		return this.visibleEntries;
	}

	void onPanelFilterChange(List<CluePickerPanel.ClueEntry> entries)
	{
		this.visibleEntries = new ArrayList<>(entries);
	}

	void updateMapOverlay()
	{
		boolean wanted = config.showDebugPanel() && config.showDebugMapIcons();
		if (wanted && this.clueMapOverlay == null)
		{
			this.clueMapOverlay = new ClueMapOverlay(this, client, worldMapOverlay);
			overlayManager.add(this.clueMapOverlay);
			mouseManager.registerMouseListener(this.clueMapOverlay);
		}
		else if (!wanted && this.clueMapOverlay != null)
		{
			overlayManager.remove(this.clueMapOverlay);
			mouseManager.unregisterMouseListener(this.clueMapOverlay);
			this.clueMapOverlay = null;
		}
	}

	void updateFakeClueMapPoints(ClueScroll clue)
	{
		worldMapPointManager.removeIf(FakeClueWorldMapPoint.class::isInstance);
		if (clue == null)
		{
			return;
		}

		final Set<WorldPoint> dests;
		try
		{
			dests = plugin.computeDestinations(clue);
		}
		catch (Exception e)
		{
			log.warn("Failed to resolve destinations for fake clue {}", clue.getClass().getSimpleName(), e);
			return;
		}
		if (dests.isEmpty())
		{
			return;
		}

		String tooltip = ClueDebugInfo.describeClue(clue, dests);
		for (WorldPoint dest : dests)
		{
			worldMapPointManager.add(new FakeClueWorldMapPoint(dest,
				ClueIcons.fakeClueIcon(itemManager), ClueIcons.clueScrollIcon(itemManager), tooltip));
		}
	}
}
