package com.shortestclue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.util.ImageUtil;

class ClueIcons
{
	private static final int TIER_SIZE = 10;

	private static final Map<String, Color> TIER_COLORS = Map.of(
		"Beginner", new Color(255, 255, 255),
		"Easy", new Color(0, 200, 0),
		"Medium", new Color(0, 200, 200),
		"Hard", new Color(150, 0, 200),
		"Elite", new Color(255, 200, 0),
		"Master", new Color(230, 0, 0),
		"Unknown", new Color(150, 150, 150)
	);

	private static final Map<String, BufferedImage> TIER_ICON_CACHE = new HashMap<>();
	private static BufferedImage combinedIcon;
	private static BufferedImage plainClueIcon;

	private ClueIcons()
	{
	}

	static BufferedImage tierIcon(String tier)
	{
		return TIER_ICON_CACHE.computeIfAbsent(tier == null ? "Unknown" : tier,
			key -> makeCircle(TIER_SIZE, TIER_COLORS.getOrDefault(key, Color.GRAY), true));
	}

	// Master clue scroll, same literal the builtin clue plugin uses (ClueScrollPlugin.getClueScrollImage).
	private static final int MASTER_CLUE_SCROLL = 19835;

	/**
	 * The plain clue scroll sprite, as shown by the builtin clue plugin when its
	 * map icon is edge-snapped.
	 */
	static BufferedImage clueScrollIcon(ItemManager itemManager)
	{
		if (plainClueIcon == null)
		{
			plainClueIcon = itemManager.getImage(MASTER_CLUE_SCROLL);
		}
		return plainClueIcon;
	}

	/**
	 * The map arrow with the clue scroll sprite on top, copied from the builtin
	 * clue plugin's {@code ClueScrollWorldMapPoint}.
	 */
	static BufferedImage fakeClueIcon(ItemManager itemManager)
	{
		if (combinedIcon == null)
		{
			final BufferedImage arrow;
			try
			{
				arrow = ImageUtil.loadImageResource(ClueScrollPlugin.class, "/util/clue_arrow.png");
			}
			catch (RuntimeException e)
			{
				// Fall back to the plain sprite if the client resource is unavailable.
				return clueScrollIcon(itemManager);
			}

			final BufferedImage clueImage = clueScrollIcon(itemManager);
			combinedIcon = new BufferedImage(arrow.getWidth(), arrow.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = combinedIcon.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(arrow, 0, 0, null);
			g.drawImage(clueImage, 0, 0, null);
			g.dispose();
		}
		return combinedIcon;
	}

	private static BufferedImage makeCircle(int size, Color color, boolean border)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillOval(1, 1, size - 2, size - 2);
		if (border)
		{
			g.setColor(Color.BLACK);
			g.setStroke(new BasicStroke(1f));
			g.drawOval(1, 1, size - 2, size - 2);
		}
		g.dispose();
		return image;
	}
}
