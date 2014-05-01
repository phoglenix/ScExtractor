package jnibwapi;

import jnibwapi.Position.PosType;
import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;

/**
 * Direct port of BWSAL's BuildingPlacer class to Java.
 * 
 * @author Glen Robertson
 */
public class BuildingPlacer {
	/** Reserved tiles */
	private final boolean[][] reserveMap;
	private int buildDistance;
	private final JNIBWAPI bwapi;
	private final int mapWidth;
	private final int mapHeight;
	
	public BuildingPlacer(JNIBWAPI bwapi) {
		mapWidth = bwapi.getMap().getSize().getBX();
		mapHeight = bwapi.getMap().getSize().getBY();
		reserveMap = new boolean[mapWidth][mapHeight];
		buildDistance = 1;
		this.bwapi = bwapi;
	}
	
	/** returns true if we can build this type of unit here. Takes into account reserved tiles. */
	public boolean canBuildHere(Position p, UnitType type) {
		// checkExplored==true to only build on explored area therefore less chance of failure
		if (!bwapi.canBuildHere(null, p, type, true))
			return false;
		for (int x = p.getBX(); x < p.getBX() + type.getTileWidth(); x++)
			for (int y = p.getBY(); y < p.getBY() + type.getTileHeight(); y++)
				if (reserveMap[x][y])
					return false;
		return true;
	}
	
	public boolean canBuildHereWithSpace(Position p, UnitType type) {
		return canBuildHereWithSpace(p, type, buildDistance);
	}
	
	/**
	 * returns true if we can build this type of unit here with the specified amount of space. space
	 * value is stored in buildDistance.
	 */
	public boolean canBuildHereWithSpace(Position p, UnitType type, int buildDist) {
		
		// if we can't build here, we of course can't build here with space
		if (!canBuildHere(p, type))
			return false;
		
		int width = type.getTileWidth();
		int height = type.getTileHeight();
		
		// make sure we leave space for add-ons. These types of units can have addons:
		if (type == UnitTypes.Terran_Command_Center
				|| type == UnitTypes.Terran_Factory
				|| type == UnitTypes.Terran_Starport
				|| type == UnitTypes.Terran_Science_Facility) {
			width += 2;
		}
		int tx = p.getBX();
		int ty = p.getBY();
		int startx = tx - buildDist;
		if (startx < 0)
			return false;
		int starty = ty - buildDist;
		if (starty < 0)
			return false;
		int endx = tx + width + buildDist;
		if (endx > mapWidth)
			return false;
		int endy = ty + height + buildDist;
		if (endy > mapHeight)
			return false;
		
		if (!type.isRefinery()) {
			for (int x = startx; x < endx; x++)
				for (int y = starty; y < endy; y++)
					if (!buildable(new Position(x, y, PosType.BUILD)) || reserveMap[x][y])
						return false;
		}
		
		if (tx > 3) {
			int startx2 = startx - 2;
			if (startx2 < 0)
				startx2 = 0;
			for (int x = startx2; x < startx; x++)
				for (int y = starty; y < endy; y++) {
					for (Unit u : bwapi.getUnitsOnTile(new Position(x, y, PosType.BUILD))) {
						if (!u.isLifted())
						{
							UnitType ut = u.getType();
							if (ut == UnitTypes.Terran_Command_Center
									|| ut == UnitTypes.Terran_Factory
									|| ut == UnitTypes.Terran_Starport
									|| ut == UnitTypes.Terran_Science_Facility) {
								return false;
							}
						}
					}
				}
		}
		return true;
	}
	
	/** returns a valid build location if one exists, scans the map left to right. Null if not found */
	public Position getBuildLocation(UnitType type) {
		for (int x = 0; x < mapWidth; x++)
			for (int y = 0; y < mapHeight; y++) {
				Position p = new Position(x, y, PosType.BUILD);
				if (canBuildHere(p, type))
					return p;
			}
		return null;
	}
	
	public Position getBuildLocationNear(Position p, UnitType type) {
		return getBuildLocationNear(p, type, buildDistance);
	}
	
	/**
	 * Returns a valid build location near the specified tile position. Searches outward in a
	 * spiral. Returns null if not found.
	 */
	public Position getBuildLocationNear(Position p, UnitType type, int buildDist) {
		int length = 1;
		int j = 0;
		boolean first = true;
		int dx = 0;
		int dy = 1;
		while (length < mapWidth) // We'll ride the spiral to the end
		{
			// if we can build here, return this tile position
			if (p.isValid()) {
				if (canBuildHereWithSpace(p, type, buildDist))
					return p;
			}
			
			// otherwise, move to another position
			p = p.translated(new Position(dx, dy, PosType.BUILD));
			// count how many steps we take in this direction
			j++;
			if (j == length) // if we've reached the end, its time to turn
			{
				// reset step counter
				j = 0;
				
				// Spiral out. Keep going.
				if (!first)
					length++; // increment step counter if needed
					
				// first=true for every other turn so we spiral out at the right rate
				first = !first;
				
				// turn counter clockwise 90 degrees:
				if (dx == 0)
				{
					dx = dy;
					dy = 0;
				}
				else
				{
					dy = -dx;
					dx = 0;
				}
			}
			// Spiral out. Keep going.
		}
		return null;
	}
	
	/** returns true if this tile is currently buildable, takes into account units on tile */
	public boolean buildable(Position p) {
		if (!bwapi.getMap().isBuildable(p))
			return false;
		for (Unit u : bwapi.getUnitsOnTile(p)) {
			if (u.getType().isBuilding() && !u.isLifted())
				return false;
		}
		return true;
	}
	
	public void reserveTiles(int tx, int ty, int width, int height) {
		for (int x = tx; x < tx + width && x < mapWidth; x++)
			for (int y = ty; y < ty + height && y < mapHeight; y++)
				reserveMap[x][y] = true;
	}
	
	public void freeTiles(int tx, int ty, int width, int height) {
		for (int x = tx; x < tx + width && x < mapWidth; x++)
			for (int y = ty; y < ty + height && y < mapHeight; y++)
				reserveMap[x][y] = false;
	}
	
	public void setBuildDistance(int distance) {
		buildDistance = distance;
	}
	
	public int getBuildDistance() {
		return buildDistance;
	}
	
	public boolean isReserved(int x, int y) {
		Position p = new Position(x, y, PosType.BUILD);
		if (!p.isValid())
			return false;
		return reserveMap[x][y];
	}
}
