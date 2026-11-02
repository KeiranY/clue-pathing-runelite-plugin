package com.shortestclue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Asks the player to read the STASH chart when it isn't known whether the emote clue's STASH unit holds its items.
 */
class StashOverlay extends OverlayPanel
{
	private final ShortestCluePlugin plugin;

	@Inject
	StashOverlay(ShortestCluePlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isStashUnknown())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("STASH unit: not known")
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Read Watson's noticeboard in Hosidius or the S.T.A.S.H chart in your house to check it.")
			.build());
		return super.render(graphics);
	}
}
