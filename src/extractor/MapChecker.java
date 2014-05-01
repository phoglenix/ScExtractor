package extractor;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnibwapi.BaseLocation;
import jnibwapi.ChokePoint;
import jnibwapi.JNIBWAPI;
import jnibwapi.Map;
import jnibwapi.Position;
import jnibwapi.Position.PosType;
import jnibwapi.Region;
import util.DbConnection;
import util.LogManager;
import util.Util;

/**
 * Quick sanity check of all maps to ensure that they seem to make some sense. Most importantly,
 * that DB values match with .jbwta file values, and that region polygons match up with the regions
 * extracted for each build tile.
 * 
 * @author Glen Robertson
 * 
 */
public class MapChecker {
	private static final Logger LOGGER = Logger.getLogger(MapChecker.class.getName());
	private final String[] DB_NAMES = { "sc_pvp", "sc_pvt", "sc_pvz", "sc_tvt", "sc_tvz", "sc_zvz" };
	private static final int MAX_BAD_REGIONS_TO_REPORT = 10;
	
	private final JNIBWAPI fakeJnibwapi;
	private final DbConnection dbc;
	private final String mapDataFolderName = "mapData";
	private final File[] mapDataFiles;
	
	public static void main(String[] args) {
		// Start the logger
		LogManager.initialise();
		
		try {
			MapChecker mc = new MapChecker();
			mc.start();
		} catch (IOException | SQLException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	public MapChecker() throws IOException, SQLException {
		LOGGER.info("Opening and checking folders");
		final File mapDataFolder = new File(mapDataFolderName);
		if (!mapDataFolder.canRead()) {
			throw new IOException("Cannot read '" + mapDataFolder.getAbsolutePath() + "'");
		}
		if (!mapDataFolder.isDirectory()) {
			throw new IOException("'" + mapDataFolder.getAbsolutePath() + "' is not a folder.");
		}
		mapDataFiles = mapDataFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".jbwta");
			}
		});
		if (mapDataFiles == null || mapDataFiles.length == 0) {
			throw new IOException("'" + mapDataFolder.getAbsolutePath()
					+ "' contains no .jbwta files.");
		}
		
		fakeJnibwapi = new JNIBWAPI(null, false);
		LOGGER.info("Make sure debug mode is NOT active below");
		dbc = new DbConnection();
	}
	
	public void start() throws SQLException, IOException {
		// for each db
		for (String dbName : DB_NAMES) {
			LOGGER.info("Starting on DB " + dbName);
			// Have to use "executeDelete" because executeUpdate tries to query the result ID
			// Nothing actually is deleted
			dbc.executeDelete("USE " + dbName, null);
			
			// for each maphash in db
			ResultSet rs = dbc.executeQuery("SELECT * FROM map", null);
			while (rs.next()) {
				// Get map data
				String mapName = rs.getString("mapname");
				String hash = rs.getString("hash");
				LOGGER.info("Starting on map " + mapName + " (" + hash + ")");
				DbData data = new DbData();
				Map map = loadMapData(hash, data);
				if (map == null) {
					LOGGER.warning("Map " + hash + " failed sanity check");
					continue;
				}
				
				// sanity check
				if (sanityCheck(map, data)) {
					LOGGER.info("Map " + hash + " passed sanity check");
				} else {
					LOGGER.warning("Map " + hash + " failed sanity check");
				}
			}
		}
		LOGGER.info("Done");
	}
	
	private boolean sanityCheck(Map map, DbData data) {
		// TODO could also check if all starting units for each player are all in one region
		boolean passing = true;
		// RegionMap
		for (int i = 0; i < map.getSize().getBX() * map.getSize().getBY(); i++) {
			Position p = btIndexToPosition(i, map.getSize());
			int regionId = map.getRegion(p) != null ? map.getRegion(p).getID() : 0;
			if (map.getRegion(p) != null && map.getRegion(p).getID() == 0) {
				LOGGER.severe("THIS ISN'T GOING TO WORK!"); // Never happens? TODO remove
			}
			if (data.regionMap[i] != regionId) {
				LOGGER.warning("Region ID at " + p + " was " + data.regionMap[i]
						+ " expecting " + regionId);
				LOGGER.info("Nearby: " + Util.join(getNeighbourRegionIds(p, map)));
				passing = false;
			}
		}
		// ChokePoints
		for (ChokePoint cp : map.getChokePoints()) {
			// Only recorded in DB to build tile resolution
			Position centerLowRes = lowRes(cp.getCenter());
			if (!data.chokePoints.contains(centerLowRes)) {
				Position closest = findClosest(cp.getCenter(), data.chokePoints);
				LOGGER.warning("Choke point not found: " + centerLowRes + " closest is " + closest);
				passing = false;
			}
		}
		// BaseLocations
		for (BaseLocation bl : map.getBaseLocations()) {
			// Position recorded in DB instead of centre
			if (!data.baseLocations.contains(bl.getPosition())) {
				Position c = findClosest(bl.getPosition(), data.baseLocations);
				LOGGER.warning("Base location not found: " + bl.getPosition() + " closest is " + c);
				// Some base locations were excluded due to being bugged in BWTA so don't consider
				// this a failure, just warn.
			}
		}
		// StartLocations
		for (BaseLocation sl : map.getStartLocations()) {
			// Position recorded in DB instead of centre
			if (!data.startLocations.contains(sl.getPosition())) {
				Position c = findClosest(sl.getPosition(), data.startLocations);
				LOGGER.warning("Start location not found: " + sl.getPosition() + " closest is " + c);
				passing = false;
			}
		}
		// Region polygons
		Position halfBt = new Position(PosType.BUILD.scale / 2, PosType.BUILD.scale / 2);
		int badRegionsCount = 0;
		for (Region r : map.getRegions()) {
			Polygon poly = new Polygon();
			for (Position p : r.getPolygon()) {
				poly.addPoint(p.getPX(), p.getPY());
			}
			Rectangle bounds = poly.getBounds();
			// Force the bounds to the build tile coordinates.
			int minX = ((int) bounds.getMinX()) / PosType.BUILD.scale;
			int minY = ((int) bounds.getMinY()) / PosType.BUILD.scale;
			int maxX = ((int) bounds.getMaxX()) / PosType.BUILD.scale + 1;
			int maxY = ((int) bounds.getMaxY()) / PosType.BUILD.scale + 1;
			for (int i = minX; i < maxX; i++) {
				for (int j = minY; j < maxY; j++) {
					Position p = new Position(i, j, PosType.BUILD).translated(halfBt);
					if (poly.contains(p.getPX(), p.getPY())) {
						// Polygons not exact so allow the region to be any neighbouring region
						if (map.getRegion(p) != r
								&& !getNeighbourRegionIds(p, map).contains(r.getID())) {
							badRegionsCount++;
							if (badRegionsCount > MAX_BAD_REGIONS_TO_REPORT) {
								continue;
							}
							int regionId = map.getRegion(p) != null ? map.getRegion(p).getID() : 0;
							LOGGER.warning("Region " + r.getID() + " expected to contain " + p
									+ " but did not. At that position was region " + regionId);
							LOGGER.info("Nearby: " + Util.join(getNeighbourRegionIds(p, map)));
							passing = false;
						}
					}
				}
			}
		}
		if (badRegionsCount > MAX_BAD_REGIONS_TO_REPORT) {
			LOGGER.severe("Total number of bad regions: " + badRegionsCount);
		}
		return passing;
	}
	
	private Map loadMapData(String mapHash, DbData out) throws SQLException, IOException {
		List<Object> data = new ArrayList<>();
		data.add(mapHash);
		// Get map name from DB
		ResultSet rs = dbc.executeQuery("SELECT * FROM map WHERE hash=?", data);
		if (!rs.next()) {
			LOGGER.severe("Map hash " + mapHash + " not in DB");
			return null;
		}
		String mapName = rs.getString("mapname");
		// Get width/height from DB
		int mapWidth = (int) dbc.queryFirstColumn(
				"SELECT MAX(btileposx) FROM map NATURAL JOIN buildtile WHERE hash=?", data) + 1;
		int mapHeight = (int) dbc.queryFirstColumn(
				"SELECT MAX(btileposy) FROM map NATURAL JOIN buildtile WHERE hash=?", data) + 1;
		Position mapSize = new Position(mapWidth, mapHeight, PosType.BUILD);
		// Get ___data from DB
		int[] heightData = new int[mapSize.getBX() * mapSize.getBY()];
		int[] buildableData = new int[mapSize.getBX() * mapSize.getBY()];
		int[] walkableData = new int[mapSize.getWX() * mapSize.getWY()];
		rs = dbc.executeQuery("SELECT * FROM map NATURAL JOIN buildtile NATURAL JOIN region "
				+ "WHERE hash=? ORDER BY btileposx, btileposy", data);
		for (int i = 0; rs.next(); i++) {
			heightData[i] = rs.getInt("groundheightid");
			buildableData[i] = rs.getInt("buildable");
			// Get a 16 char string representing in binary the walkability of each walk tile on
			// this build tile
			String tempWalkable = Integer.toBinaryString(rs.getInt("walkable"));
			while (tempWalkable.length() < 16) {
				tempWalkable = "0" + tempWalkable;
			}
			for (int j = 0; j < 16; j++) { // 4x4 walk tiles per build tile
				// First find the correct walk tile index of the top-left walk tile
				Position p = btIndexToPosition(i, mapSize);
				int pos = p.getWX() + p.getWY() * mapSize.getWX();
				// Then modify the index based on walk tile position inside the build tile
				// (walk tiles data is in columns inside each build tile)
				pos += mapSize.getWX() * (j % 4);
				pos += j / 4;
				walkableData[pos] = Integer.parseInt(tempWalkable.substring(j, j + 1));
			}
		}
		
		Map map = new Map(mapWidth, mapHeight, mapName, "mapFileName", mapHash, heightData,
				buildableData, walkableData);
		
		// get region and choke point data
		File bwtaFile = new File("mapData" + File.separator + map.getHash() + ".jbwta");
		File mapDir = bwtaFile.getParentFile();
		if (mapDir != null) {
			mapDir.mkdirs();
		}
		
		// load extra data from DB
		out.regionMap = new int[mapSize.getBX() * mapSize.getBY()];
		out.chokePoints = new ArrayList<>();
		out.baseLocations = new ArrayList<>();
		out.startLocations = new ArrayList<>();
		rs = dbc.executeQuery("SELECT * FROM map NATURAL JOIN buildtile NATURAL JOIN region "
				+ "WHERE hash=? ORDER BY btileposy, btileposx", data);
		for (int i = 0; rs.next(); i++) {
			out.regionMap[i] = rs.getInt("scregionid");
			if (rs.getInt("chokedist") == 0) {
				out.chokePoints.add(btIndexToPosition(i, mapSize));
			}
			if (rs.getInt("baselocationdist") == 0) {
				out.baseLocations.add(btIndexToPosition(i, mapSize));
			}
			if (rs.getInt("startlocationdist") == 0) {
				out.startLocations.add(btIndexToPosition(i, mapSize));
			}
		}
		
		// also read from file
		if (!bwtaFile.exists()) {
			LOGGER.severe("BWTA file doesn't exist!");
			return null;
		}
		BufferedReader reader = new BufferedReader(new FileReader(bwtaFile));
		
		int[] regionMapData = readMapData(reader);
		int[] regionData = readMapData(reader);
		int[] chokePointData = readMapData(reader);
		int[] baseLocationData = readMapData(reader);
		HashMap<Integer, int[]> polygons = new HashMap<>();
		// polygons (first integer is ID)
		int[] polygonData;
		while ((polygonData = readMapData(reader)) != null) {
			int[] coordinateData = Arrays.copyOfRange(polygonData, 1, polygonData.length);
			
			polygons.put(polygonData[0], coordinateData);
		}
		
		reader.close();
		
		fakeJnibwapi.tempInitialize(map, regionMapData, regionData, polygons, chokePointData,
				baseLocationData);
		//
		// Temporarily add this to JNIBWAPI for MapChecker to run.
		// Necessary to do this from JNIBWAPI so that isValid() still works.
		// public void tempInitialize(Map map, int[] regionMapData, int[] regionData,
		// HashMap<Integer, int[]> regionPolygons, int[] chokePointData, int[] baseLocationData) {
		// int neverUsedToTriggerAWarningSoIDontForgetAboutThisCode;
		// this.map = map;
		// map.initialize(regionMapData, regionData, regionPolygons, chokePointData,
		// baseLocationData);
		// }
		//
		//
		
		return map;
	}
	
	private static Position btIndexToPosition(int i, Position mapSize) {
		int x = i % mapSize.getBX();
		int y = i / mapSize.getBX();
		return new Position(x, y, PosType.BUILD);
	}
	
	private static Position lowRes(Position highRes) {
		return new Position(highRes.getBX(), highRes.getBY(), PosType.BUILD);
	}
	
	private static List<Integer> getNeighbourRegionIds(Position p, Map map) {
		List<Integer> neighbours = new ArrayList<>();
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				Position pp = p.translated(new Position(i, j, PosType.BUILD));
				if (pp.isValid() && map.getRegion(pp) != null)
					neighbours.add(map.getRegion(pp).getID());
				else
					neighbours.add(0);
			}
		}
		return neighbours;
	}
	
	private static Position findClosest(Position p, List<Position> others) {
		int dist = Integer.MAX_VALUE;
		Position closest = null;
		for (Position cp2 : others) {
			int newDist = cp2.getApproxPDistance(p);
			if (newDist < dist) {
				closest = cp2;
				dist = newDist;
			}
		}
		return closest;
	}
	
	/**
	 * Copied from JNIBWAPI
	 */
	private static int[] readMapData(BufferedReader reader) throws IOException {
		int[] data = new int[0];
		String line = reader.readLine();
		if (line == null)
			return null;
		String[] stringData = line.split(",");
		if (stringData.length > 0 && !stringData[0].equals("")) {
			data = new int[stringData.length];
			for (int i = 0; i < stringData.length; i++) {
				data[i] = Integer.parseInt(stringData[i]);
			}
		}
		return data;
	}
	
	private class DbData {
		public int[] regionMap;
		public List<Position> chokePoints;
		public List<Position> baseLocations;
		public List<Position> startLocations;
	}
	
}
