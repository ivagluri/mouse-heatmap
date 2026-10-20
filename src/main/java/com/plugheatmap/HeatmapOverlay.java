package com.plugheatmap;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

class HeatmapOverlay extends Overlay
{
	private final HeatmapConfig config;
	private final HeatmapPlugin plugin;

	@Inject
	HeatmapOverlay(HeatmapConfig config, HeatmapPlugin plugin)
	{
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.LOW);
		updateLayer();
	}

	/**
	 * Draws over interfaces when mapping them, otherwise above the 3D scene but below interfaces so they cover it.
	 * The overlay must be re-added to the overlay manager for a change to take effect.
	 */
	void updateLayer()
	{
		setLayer(config.includeInterfaces() ? OverlayLayer.ABOVE_WIDGETS : OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showHeatmap())
		{
			return null;
		}

		Heatmap heatmap = plugin.getHeatmap();
		Heatmap tabHeatmap = plugin.getTabHeatmap();
		if (tabHeatmap == null)
		{
			draw(graphics, heatmap);
			return null;
		}

		// leave the side panel to the open tab's heatmap
		Rectangle panel = new Rectangle(tabHeatmap.getX(), tabHeatmap.getY(), tabHeatmap.getWidth(), tabHeatmap.getHeight());
		Area outsidePanel = new Area(new Rectangle(heatmap.getX(), heatmap.getY(), heatmap.getWidth(), heatmap.getHeight()));
		outsidePanel.subtract(new Area(panel));

		Shape clip = graphics.getClip();
		graphics.clip(outsidePanel);
		draw(graphics, heatmap);
		graphics.setClip(clip);
		draw(graphics, tabHeatmap);
		return null;
	}

	private static void draw(Graphics2D graphics, Heatmap heatmap)
	{
		BufferedImage image = heatmap.getImage();
		if (image != null)
		{
			graphics.drawImage(image, heatmap.getX(), heatmap.getY(), null);
		}
	}
}
