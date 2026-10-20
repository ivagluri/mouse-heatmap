package com.plugheatmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.Value;
import net.runelite.client.util.Filepath;

/**
 * Reads and writes saved heatmaps as a gzipped binary file. Does blocking IO, so keep it off the client thread.
 */
class HeatmapStorage
{
	private static final int MAGIC = 0x4D484D50; // "MHMP"
	private static final int VERSION = 1;
	// guards against allocating huge arrays from a corrupt file
	private static final int MAX_CELLS = 10_000_000;

	@Value
	static class Saved
	{
		Heatmap.Snapshot heatmap;
		Map<Integer, Heatmap.Snapshot> tabHeatmaps;
	}

	static void write(Filepath file, Saved saved) throws IOException
	{
		Filepath temp = file.getParent().createTempFile("heatmap", ".tmp");
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(temp.openOutputStream()))))
		{
			out.writeInt(MAGIC);
			out.writeInt(VERSION);
			writeSnapshot(out, saved.getHeatmap());
			out.writeInt(saved.getTabHeatmaps().size());
			for (Map.Entry<Integer, Heatmap.Snapshot> tab : saved.getTabHeatmaps().entrySet())
			{
				out.writeInt(tab.getKey());
				writeSnapshot(out, tab.getValue());
			}
			out.close();
			// replace in one step so a crash mid-write can't leave a truncated file
			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		finally
		{
			temp.deleteIfExists();
		}
	}

	static Saved read(Filepath file) throws IOException
	{
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(file.openInputStream()))))
		{
			if (in.readInt() != MAGIC || in.readInt() != VERSION)
			{
				throw new IOException("Unrecognised heatmap file");
			}

			Heatmap.Snapshot heatmap = readSnapshot(in);
			int tabCount = in.readInt();
			Map<Integer, Heatmap.Snapshot> tabHeatmaps = new HashMap<>();
			for (int i = 0; i < tabCount; i++)
			{
				tabHeatmaps.put(in.readInt(), readSnapshot(in));
			}
			return new Saved(heatmap, tabHeatmaps);
		}
	}

	private static void writeSnapshot(DataOutputStream out, Heatmap.Snapshot snapshot) throws IOException
	{
		out.writeInt(snapshot.getCellSize());
		out.writeInt(snapshot.getCols());
		out.writeInt(snapshot.getRows());
		for (float h : snapshot.getHeat())
		{
			out.writeFloat(h);
		}
	}

	private static Heatmap.Snapshot readSnapshot(DataInputStream in) throws IOException
	{
		int cellSize = in.readInt();
		int cols = in.readInt();
		int rows = in.readInt();
		if (cellSize <= 0 || cols < 0 || rows < 0 || (long) cols * rows > MAX_CELLS)
		{
			throw new IOException("Corrupt heatmap file");
		}

		float[] heat = new float[cols * rows];
		for (int i = 0; i < heat.length; i++)
		{
			heat[i] = in.readFloat();
		}
		return new Heatmap.Snapshot(cellSize, cols, rows, heat);
	}
}
