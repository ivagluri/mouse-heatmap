package com.plugheatmap;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import lombok.Getter;

/**
 * Grid of heat values covering an area of the canvas, plus a rendered image of it.
 * Only accessed from the client thread.
 */
class Heatmap
{
	// heat below this fraction of the scale fades in rather than popping in at full opacity
	private static final float FADE_IN = 0.2f;

	@Getter
	private int x;
	@Getter
	private int y;
	@Getter
	private int width;
	@Getter
	private int height;
	private int cellSize;
	private int cols;
	private int rows;
	private float[] heat = new float[0];
	private float maxHeat;
	private boolean dirty;

	private final int[] palette = new int[256];
	private BufferedImage cells;
	private BufferedImage image;

	/**
	 * Fits the grid to an area of the canvas. Heat is kept relative to the area's corner, so moving or resizing
	 * the area keeps the overlapping cells. A new cell size clears the heatmap.
	 */
	void resize(int x, int y, int width, int height, int cellSize)
	{
		if (x == this.x && y == this.y && width == this.width && height == this.height && cellSize == this.cellSize)
		{
			return;
		}

		int newCols = (width + cellSize - 1) / cellSize;
		int newRows = (height + cellSize - 1) / cellSize;
		float[] newHeat = new float[newCols * newRows];

		if (cellSize == this.cellSize)
		{
			for (int row = 0; row < Math.min(rows, newRows); row++)
			{
				System.arraycopy(heat, row * cols, newHeat, row * newCols, Math.min(cols, newCols));
			}
		}

		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.cellSize = cellSize;
		cols = newCols;
		rows = newRows;
		heat = newHeat;
		maxHeat = 0;
		for (float h : heat)
		{
			maxHeat = Math.max(maxHeat, h);
		}

		cells = width > 0 && height > 0 ? new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB) : null;
		image = width > 0 && height > 0 ? new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB) : null;
		dirty = true;
	}

	/**
	 * Adds heat around a point relative to the mapped area, falling off linearly with distance.
	 */
	void add(int x, int y, int radius)
	{
		int cx = x / cellSize;
		int cy = y / cellSize;
		int reach = (radius + cellSize - 1) / cellSize;
		float falloff = radius + cellSize;

		for (int row = Math.max(0, cy - reach); row <= Math.min(rows - 1, cy + reach); row++)
		{
			for (int col = Math.max(0, cx - reach); col <= Math.min(cols - 1, cx + reach); col++)
			{
				float dx = (col + 0.5f) * cellSize - x;
				float dy = (row + 0.5f) * cellSize - y;
				float weight = 1 - (float) Math.sqrt(dx * dx + dy * dy) / falloff;
				if (weight > 0)
				{
					int i = row * cols + col;
					heat[i] += weight;
					maxHeat = Math.max(maxHeat, heat[i]);
				}
			}
		}
		dirty = true;
	}

	void decay(float factor)
	{
		if (maxHeat == 0)
		{
			return;
		}

		for (int i = 0; i < heat.length; i++)
		{
			heat[i] *= factor;
		}
		maxHeat *= factor;
		dirty = true;
	}

	void clear()
	{
		heat = new float[cols * rows];
		maxHeat = 0;
		dirty = true;
	}

	/**
	 * Rebuilds the colour ramp: cold to warm over the lower half of the scale, warm to hot over the upper half.
	 */
	void setColors(Color cold, Color warm, Color hot, int opacityPercent)
	{
		float maxAlpha = opacityPercent * 255 / 100f;
		for (int i = 0; i < palette.length; i++)
		{
			float t = i / (float) (palette.length - 1);
			Color c = t < 0.5f ? lerp(cold, warm, t * 2) : lerp(warm, hot, (t - 0.5f) * 2);
			int alpha = Math.round(maxAlpha * Math.min(1, t / FADE_IN));
			palette[i] = alpha << 24 | (c.getRGB() & 0xFFFFFF);
		}
		dirty = true;
	}

	/**
	 * Redraws the image of the mapped area if the heat or colours changed since the last call.
	 */
	void render(boolean logScale, boolean smooth)
	{
		if (!dirty || image == null)
		{
			return;
		}
		dirty = false;

		int[] pixels = ((DataBufferInt) cells.getRaster().getDataBuffer()).getData();
		double scaleMax = logScale ? Math.log1p(maxHeat) : maxHeat;
		for (int i = 0; i < heat.length; i++)
		{
			if (heat[i] <= 0)
			{
				pixels[i] = 0;
				continue;
			}
			double value = logScale ? Math.log1p(heat[i]) : heat[i];
			int index = (int) (value / scaleMax * (palette.length - 1));
			pixels[i] = palette[Math.min(index, palette.length - 1)];
		}

		Graphics2D g = image.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
			? RenderingHints.VALUE_INTERPOLATION_BILINEAR
			: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(cells, 0, 0, cols * cellSize, rows * cellSize, null);
		g.dispose();
	}

	BufferedImage getImage()
	{
		return image;
	}

	private static Color lerp(Color a, Color b, float t)
	{
		return new Color(
			Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
