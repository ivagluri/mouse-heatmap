package com.plugheatmap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.util.Map;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HeatmapStorageTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void roundTrip() throws IOException
	{
		Heatmap heatmap = new Heatmap();
		heatmap.resize(0, 0, 100, 50, 5);
		heatmap.add(42, 17, 5);
		Heatmap tab = new Heatmap();
		tab.resize(10, 10, 20, 30, 5);
		tab.add(3, 4, 0);

		Filepath file = Filepath.Unchecked.getRooted(folder.getRoot().toPath()).joinSegment("heatmap.dat");
		HeatmapStorage.write(file, new HeatmapStorage.Saved(heatmap.snapshot(), Map.of(3, tab.snapshot())));
		HeatmapStorage.Saved saved = HeatmapStorage.read(file);

		assertSnapshotEquals(heatmap.snapshot(), saved.getHeatmap());
		assertEquals(1, saved.getTabHeatmaps().size());
		assertSnapshotEquals(tab.snapshot(), saved.getTabHeatmaps().get(3));
		// only the saved file is left behind, no temp files
		assertEquals(1, folder.getRoot().list().length);
	}

	@Test
	public void writeCreatesMissingDirectory() throws IOException
	{
		Heatmap heatmap = new Heatmap();
		heatmap.resize(0, 0, 10, 10, 5);

		Filepath file = Filepath.Unchecked.getRooted(folder.getRoot().toPath())
			.joinSegment("mouseheatmap")
			.joinSegment("heatmap.dat");
		HeatmapStorage.write(file, new HeatmapStorage.Saved(heatmap.snapshot(), Map.of()));

		assertSnapshotEquals(heatmap.snapshot(), HeatmapStorage.read(file).getHeatmap());
	}

	@Test
	public void restoreKeepsHeatWhenRefitted()
	{
		Heatmap heatmap = new Heatmap();
		heatmap.resize(0, 0, 100, 50, 5);
		heatmap.add(42, 17, 5);
		Heatmap.Snapshot before = heatmap.snapshot();

		Heatmap restored = new Heatmap();
		restored.restore(before);
		restored.resize(0, 0, 100, 50, 5);

		assertSnapshotEquals(before, restored.snapshot());
	}

	private static void assertSnapshotEquals(Heatmap.Snapshot expected, Heatmap.Snapshot actual)
	{
		assertEquals(expected.getCellSize(), actual.getCellSize());
		assertEquals(expected.getCols(), actual.getCols());
		assertEquals(expected.getRows(), actual.getRows());
		assertArrayEquals(expected.getHeat(), actual.getHeat(), 0);
	}
}
