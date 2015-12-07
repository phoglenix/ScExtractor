package scdb;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import util.UnitAttributes.UnitAttribute;

public class TestDbInterface {

	public static void main(String[] args) {
		for (Replay r : Replay.getReplays()) {
			System.out.println(String.format("Replay: %s ID: %d Duration: %d",
					r.replayFileName, r.dbReplayId, r.duration));
			try {
				ScMap m = r.getMap();
				System.out.println(String.format("on Map: %s ID: %d StartPos: %d Size: (%d,%d)",
						m.mapName, m.dbMapId, m.numStartPos, m.xSize, m.ySize));
			} catch (SQLException e) {
				System.out.println("ERROR: unable to get map" + e.getMessage());
			}
			PlayerReplay arbitrary = null; // will be the winner if there is one
			for (PlayerReplay p : r.getPlayers()) {
				System.out.println("Player " + p.name + " is " + p.getRace().getName()
						+ " and has " + p.getUnits().size() + " units throughout the game.");
				if (p.winner) {
					System.out.println(" (and they won!)");
					arbitrary = p;
				}
				if (arbitrary == null) {
					arbitrary = p;
				}
			}
			for (int frame = 0; frame < r.duration; frame += r.duration / 5 + 1) {
				System.out.println("At frame " + frame + ":");
				for (PlayerReplay p : r.getPlayers()) {
					List<Unit> existing = existingOnly(p.getUnits(), frame);
					List<Unit> nonExisting = nonExistingOnly(p.getUnits(), frame);
					List<Unit> visibleUnits = visibleOnly(arbitrary, existing, frame);
					System.out.println(p.name + " had " + existing.size()
							+ " existing units with a total HP of "
							+ sumAttribute(existing, UnitAttribute.Hit_Points, frame)
							+ " while nonexisting units had a total HP of "
							+ sumAttribute(nonExisting, UnitAttribute.Hit_Points, frame) + ".\t"
							+ visibleUnits.size() + " units were visible to " + arbitrary.name);
				}
			}
			
			System.out.println("\n\n\n");
		}
	}
	
	private static List<Unit> existingOnly(List<Unit> units, int frame) {
		List<Unit> existing = new ArrayList<>();
		for (Unit u : units) {
			if (u.isExisting(frame)) {
				existing.add(u);
			}
		}
		return existing;
	}
	
	private static List<Unit> nonExistingOnly(List<Unit> units, int frame) {
		List<Unit> nonExisting = new ArrayList<>();
		for (Unit u : units) {
			if (!u.isExisting(frame)) {
				nonExisting.add(u);
			}
		}
		return nonExisting;
	}
	
	private static List<Unit> visibleOnly(PlayerReplay p, List<Unit> units, int frame) {
		List<Unit> visible = new ArrayList<>();
		for (Unit u : units) {
			if (u.visibleTo(p, frame)) {
				visible.add(u);
			}
		}
		return visible;
	}
	
	private static int sumAttribute(List<Unit> units, UnitAttribute attribute, int frame) {
		int sum = 0;
		for (Unit u : units) {
			sum += u.getAttribute(frame, attribute);
		}
		return sum;
	}
	
}
