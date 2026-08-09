package com.shortestclue;

import java.awt.image.BufferedImage;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

class FakeClueWorldMapPoint extends WorldMapPoint
{
	private final BufferedImage combinedImage;
	private final BufferedImage plainClueImage;

	FakeClueWorldMapPoint(WorldPoint worldPoint, BufferedImage combinedImage, BufferedImage plainClueImage, String tooltip)
	{
		super(worldPoint, combinedImage);
		this.combinedImage = combinedImage;
		this.plainClueImage = plainClueImage;
		setSnapToEdge(true);
		setJumpOnClick(true);
		setName("Shortest Clue");
		setTooltip(tooltip);
		setImagePoint(new Point(combinedImage.getWidth() / 2, combinedImage.getHeight()));
	}

	@Override
	public void onEdgeSnap()
	{
		setImage(this.plainClueImage);
		setImagePoint(null);
	}

	@Override
	public void onEdgeUnsnap()
	{
		setImage(this.combinedImage);
		setImagePoint(new Point(this.combinedImage.getWidth() / 2, this.combinedImage.getHeight()));
	}
}
