package com.shortestclue.debug;

import com.google.common.base.Splitter;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

class ClueMapOverlay extends Overlay implements MouseListener
{
	private static final int HITBOX_PAD = 2;

	// World map widget ids, kept as literals (mirroring the builtin plugins)
	// because the gameval constants only exist when the full API is on the classpath.
	private static final int WORLDMAP = 595;
	private static final int WORLDMAP_MAP_CONTAINER = 38993927;
	private static final int WORLDMAP_BOTTOM_GRAPHIC0 = 38993943;
	private static final int WORLDMAP_TOOLTIP = 38993961;

	private static final int TOOLTIP_OFFSET_HEIGHT = 25;
	private static final int TOOLTIP_OFFSET_WIDTH = 5;
	private static final int TOOLTIP_PADDING_HEIGHT = 1;
	private static final int TOOLTIP_PADDING_WIDTH = 2;
	private static final int TOOLTIP_TEXT_OFFSET_HEIGHT = -2;

	private static final Splitter TOOLTIP_SPLITTER = Splitter.on("<br>").trimResults().omitEmptyStrings();

	private final DebugClueController controller;
	private final Client client;
	private final WorldMapOverlay worldMapOverlay;

	ClueMapOverlay(DebugClueController controller, Client client, WorldMapOverlay worldMapOverlay)
	{
		this.controller = controller;
		this.client = client;
		this.worldMapOverlay = worldMapOverlay;
		setLayer(OverlayLayer.MANUAL);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
		drawAfterInterface(WORLDMAP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget mapWidget = client.getWidget(WORLDMAP_MAP_CONTAINER);
		final Widget bottomBar = client.getWidget(WORLDMAP_BOTTOM_GRAPHIC0);
		if (mapWidget == null || bottomBar == null)
		{
			return null;
		}

		final List<CluePickerPanel.ClueEntry> entries = controller.getVisibleEntries();
		if (entries.isEmpty())
		{
			return null;
		}

		final Rectangle mapRect = mapWidget.getBounds();
		final Point mousePos = client.getMouseCanvasPosition();

		CluePickerPanel.ClueEntry hovered = null;

		for (CluePickerPanel.ClueEntry entry : entries)
		{
			if (entry.getClue() == controller.getFakeClue())
			{
				continue;
			}

			final BufferedImage icon = ClueIcons.tierIcon(entry.getTier());
			for (Point point : iconCenters(entry))
			{
				graphics.setClip(mapRect);
				graphics.drawImage(icon, point.getX() - icon.getWidth() / 2, point.getY() - icon.getHeight() / 2, null);

				if (hovered == null && mousePos != null && iconRect(point, icon).contains(mousePos.getX(), mousePos.getY()))
				{
					hovered = entry;
				}
			}
		}

		final Widget rsTooltip = client.getWidget(WORLDMAP_TOOLTIP);
		if (rsTooltip != null)
		{
			rsTooltip.setHidden(hovered != null);
		}

		if (hovered != null)
		{
			drawTooltip(graphics, hovered.getLabel());
		}

		return null;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() != MouseEvent.BUTTON1 && event.getButton() != MouseEvent.BUTTON3)
		{
			return event;
		}

		final Widget mapWidget = client.getWidget(WORLDMAP_MAP_CONTAINER);
		final Widget bottomBar = client.getWidget(WORLDMAP_BOTTOM_GRAPHIC0);
		if (mapWidget == null || bottomBar == null)
		{
			return event;
		}

		for (CluePickerPanel.ClueEntry entry : controller.getVisibleEntries())
		{
			if (entry.getClue() == controller.getFakeClue())
			{
				continue;
			}

			final BufferedImage icon = ClueIcons.tierIcon(entry.getTier());
			for (Point point : iconCenters(entry))
			{
				if (iconRect(point, icon).contains(event.getPoint()))
				{
					controller.setFakeClue(entry.getClue());
					if (event.getButton() == MouseEvent.BUTTON1)
					{
						event.consume();
					}
					return event;
				}
			}
		}

		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		return event;
	}

	private List<Point> iconCenters(CluePickerPanel.ClueEntry entry)
	{
		final List<Point> points = new ArrayList<>();
		final Widget mapWidget = client.getWidget(WORLDMAP_MAP_CONTAINER);
		if (mapWidget == null)
		{
			return points;
		}

		final Rectangle mapRect = mapWidget.getBounds();

		for (WorldPoint dest : entry.getDests())
		{
			final Point point = worldMapOverlay.mapWorldPointToGraphicsPoint(dest);
			if (point == null || !mapRect.contains(point.getX(), point.getY()))
			{
				continue;
			}

			points.add(point);
		}
		return points;
	}

	private Rectangle iconRect(Point center, BufferedImage icon)
	{
		final int size = icon.getWidth() + HITBOX_PAD * 2;
		return new Rectangle(center.getX() - size / 2, center.getY() - size / 2, size, size);
	}

	private void drawTooltip(Graphics2D graphics, String tooltip)
	{
		final Point mousePos = client.getMouseCanvasPosition();
		if (tooltip == null || tooltip.isEmpty() || mousePos == null)
		{
			return;
		}

		final List<String> rows = TOOLTIP_SPLITTER.splitToList(tooltip);
		if (rows.isEmpty())
		{
			return;
		}

		final Point drawPoint = new Point(mousePos.getX() + TOOLTIP_OFFSET_WIDTH, mousePos.getY() + TOOLTIP_OFFSET_HEIGHT);

		final Rectangle bounds = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		graphics.setClip(bounds);
		graphics.setColor(JagexColors.TOOLTIP_BACKGROUND);
		graphics.setFont(FontManager.getRunescapeFont());
		final FontMetrics fm = graphics.getFontMetrics();
		int width = rows.stream().map(fm::stringWidth).max(Integer::compareTo).get();
		int height = fm.getHeight();

		final Rectangle tooltipRect = new Rectangle(drawPoint.getX() - TOOLTIP_PADDING_WIDTH, drawPoint.getY() - TOOLTIP_PADDING_HEIGHT, width + TOOLTIP_PADDING_WIDTH * 2, height * rows.size() + TOOLTIP_PADDING_HEIGHT * 2);
		graphics.fillRect((int) tooltipRect.getX(), (int) tooltipRect.getY(), (int) tooltipRect.getWidth(), (int) tooltipRect.getHeight());

		graphics.setColor(JagexColors.TOOLTIP_BORDER);
		graphics.drawRect((int) tooltipRect.getX(), (int) tooltipRect.getY(), (int) tooltipRect.getWidth(), (int) tooltipRect.getHeight());

		graphics.setColor(JagexColors.TOOLTIP_TEXT);
		for (int i = 0; i < rows.size(); i++)
		{
			graphics.drawString(rows.get(i), drawPoint.getX(), drawPoint.getY() + TOOLTIP_TEXT_OFFSET_HEIGHT + (i + 1) * height);
		}
	}
}
