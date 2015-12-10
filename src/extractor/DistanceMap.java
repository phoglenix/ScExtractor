package extractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import jnibwapi.BaseLocation;
import jnibwapi.ChokePoint;
import jnibwapi.Map;
import jnibwapi.Position;
import jnibwapi.Position.PosType;

/** Adds distance maps to nearest points of interest, based on a BWAPI map's information */
public class DistanceMap {
	private static final Logger LOGGER = Logger.getLogger(DistanceMap.class.getName());
	/** Internal BWAPI Map */
	private Map map;
	/** Distance in pixels from nearest choke point (for each build tile) */
	private int[] chokeDistMap = null;
	/** Distance in pixels from nearest base location (for each build tile) */
	private int[] baseLocationDistMap = null;
	/** Distance in pixels from nearest start location (for each build tile) */
	private int[] startLocationDistMap = null;

	/** Build a DistanceMap from this BWAPI Map */
	public DistanceMap(Map map) {
		this.map = map;
		
		// calculate distance map for choke points
		List<Position> seeds = new ArrayList<>();
		for (ChokePoint cp : map.getChokePoints()) {
			seeds.add(cp.getCenter());
		}
		chokeDistMap = floodFillDistances(seeds);
		
		// calculate distance map for base locations
		seeds.clear();
		for (BaseLocation bl : map.getBaseLocations()) {
			// Broodwar/BWTA sometimes gives back non-buildable/non-walkable BaseLocations, or even
			// ones outside the map bounds!
			if (map.isBuildable(bl.getCenter())) {
				seeds.add(bl.getCenter());
			} else {
				LOGGER.warning("Base location " + bl.getCenter() + " is not buildable");
			}
		}
		baseLocationDistMap = floodFillDistances(seeds);
		
		// calculate distance map for start locations
		seeds.clear();
		for (BaseLocation bl : map.getBaseLocations()) {
			if (bl.isStartLocation()) {
				if (map.isBuildable(bl.getCenter())) {
					seeds.add(bl.getCenter());
					// No need to log warning, will already be logged for base locations above
				}
			}
		}
		startLocationDistMap = floodFillDistances(seeds);
	}
	
	/** Converts a position to a 1-dimensional build tile array index for this map */
	private int getBuildTileArrayIndex(Position p) {
		return p.getBX() + map.getSize().getBX() * p.getBY();
	}
	
	public int getChokeDist(Position p) {
		if (p.isValid()) {
			return chokeDistMap[getBuildTileArrayIndex(p)];
		}
		else {
			return Integer.MAX_VALUE;
		}
	}
	
	public int getBaseLocationDist(Position p) {
		if (p.isValid()) {
			return baseLocationDistMap[getBuildTileArrayIndex(p)];
		}
		else {
			return Integer.MAX_VALUE;
		}
	}
	
	public int getStartLocationDist(Position p) {
		if (p.isValid()) {
			return startLocationDistMap[getBuildTileArrayIndex(p)];
		}
		else {
			return Integer.MAX_VALUE;
		}
	}
	
	/**
	 * Create an array of build tiles with distances from the seed points (build tile positions).
	 * Distances are calculated over walkable area only. Unreachable area has distance of
	 * Integer.MAX_VALUE.
	 */
	private int[] floodFillDistances(List<Position> seeds) {
		final int tileSizeDiag = (int) Math.sqrt(2 * Map.TILE_SIZE * Map.TILE_SIZE);
		int[] distMap = new int[map.getSize().getBX() * map.getSize().getBY()];
		Arrays.fill(distMap, Integer.MAX_VALUE);
		// Set the distance to 0 at the seeds
		for (Position p : seeds) {
			if (p.isValid()) {
				distMap[getBuildTileArrayIndex(p)] = 0;
			} else {
				LOGGER.warning("Seed point was out of bounds: " + p);
			}
		}
		// Flood fill the walkable map area from the seeds, storing the min distance to each tile
		List<Position> open = new LinkedList<>(seeds);
		while (!open.isEmpty()) {
			Position p = open.remove(0);
			int dist = distMap[getBuildTileArrayIndex(p)];
			// Loop through neighbours
			for (int i = -1; i <= 1; i++) {
				for (int j = -1; j <= 1; j++) {
					if (i == 0 && j == 0) {
						// Skip original point
						continue;
					}
					int x = p.getBX() + i;
					int y = p.getBY() + j;
					int diff;
					if (i == 0 || j == 0) {
						// Vertical / Horizontal neighbours
						diff = Map.TILE_SIZE;
					} else {
						// Diagonal neighbours
						diff = tileSizeDiag;
					}
					// Rely on isLowResWalkable to check boundaries (as well as walkability)
					if (map.isLowResWalkable(new Position(x, y, PosType.BUILD))) {
						int existingDist = distMap[x + map.getSize().getBX() * y];
						if (dist + diff < existingDist) {
							// Update map tile and neighbours
							distMap[x + map.getSize().getBX() * y] = dist + diff;
							open.add(new Position(x, y, PosType.BUILD));
						}
					}
				}
			}
		}
		return distMap;
	}
}
