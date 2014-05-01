package extractor;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnibwapi.BWAPIEventListener;
import jnibwapi.JNIBWAPI;
import jnibwapi.Player;
import jnibwapi.Position;
import jnibwapi.Position.PosType;
import jnibwapi.Region;
import jnibwapi.Unit;
import jnibwapi.types.EventType;
import jnibwapi.types.RaceType.RaceTypes;
import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;
import util.DbConnection;
import util.LogManager;
import util.UnitAttributes;
import util.Util;

@SuppressWarnings("deprecation")
public class ExtractStates implements BWAPIEventListener {
	// constants
	private static final Logger LOGGER = Logger.getLogger(ExtractStates.class.getName());
	/** Walk tiles per build tile */
	private static final int WTPBT = 4;
	/** Properties file to load */
	private static final String PROPERTIES_FILENAME = "extractorConfig.properties";
	/** A placeholder region to represent areas of the map not in a BWTA region (eg. oceans) */
	private final Region REGION_NONE;
	
	private static enum ExtractionMode {
		TIMED_FRAMES, ACTION_FRAMES, ATTACK_FRAMES
	}
	
	// user customisable properties
	private final int maxNumExtrasToRemove;
	private final ExtractionMode extractionMode;
	/** The number of frames to skip between each extracted frame */
	private final int frameSkip;
	/**
	 * The number of frames to skip between each extracted frame during attacks in ATTACK_FRAMES
	 * extractionMode
	 */
	private final int attackFrameSkip;
	/**
	 * The number of frames to skip (in multiples of {@link #frameSkip} between each extracted frame
	 * for workers that are considered inactive (see {@link #inactiveUnitTime}). Set to 1 to disable
	 * extra worker frame skipping.
	 */
	private final int frameSkipWorkersMultiplier;
	/**
	 * A unit will be considered inactive (for the purposes of {@link #frameSkipWorkersMultiplier})
	 * if they have not been and are not about to be given an action in this many frames.
	 */
	private final int inactiveUnitTime;
	/** Where to find replay files (same dir as StarCraft is looking in) */
	private final String replayFolder;
	/** Where to put replay good files once processed */
	private final String replayFolderGood;
	/** Where to put replay bad files once processed */
	private final String replayFolderBad;
	/** Where to put replay almost-good files once processed */
	private final String replayFolderAlmost;
	/**
	 * At what percentage-completion to consider a replay "almost good" and store it, as well as
	 * clean up the database. Many replays stop at 99% completion.
	 */
	private final int replayAlmostPercent;
	/**
	 * How often (in %) to print out the progress of the extraction within a match. Set to 0 to
	 * disable.
	 */
	private final int progressPercent;
	/** Whether to draw terrain data on the map */
	private final boolean debugDrawTerrain;
	/** Whether to draw region IDs on the map (very slow) */
	private final boolean debugDrawRegionIds;
	
	// globals held between matches
	private final JNIBWAPI bwapi;
	private final DbConnection dbc;
	
	// per-match variables
	private MatchInfo mi;
	
	public static void main(String[] args) {
		// Start the logger
		LogManager.initialise();
		ExtractStates es;
		try {
			es = new ExtractStates();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return;
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return;
		}
		// Start BWAPI. This call never returns
		es.bwapi.start();
		System.out.println("Ended!");
	}
	
	/**
	 * Instantiates the Extractor
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	public ExtractStates() throws IOException, SQLException {
		Properties props = Util.loadProperties(PROPERTIES_FILENAME);
		maxNumExtrasToRemove = Integer.parseInt(
				Util.getPropertyNotNull(props, "es_max_num_extras_to_remove"));
		extractionMode = ExtractionMode.valueOf(
				Util.getPropertyNotNull(props, "es_extraction_mode"));
		frameSkip = Integer.parseInt(Util.getPropertyNotNull(props, "es_frame_skip"));
		attackFrameSkip = Integer.parseInt(Util.getPropertyNotNull(props, "es_attack_frame_skip"));
		frameSkipWorkersMultiplier = Integer.parseInt(
				Util.getPropertyNotNull(props, "es_frame_skip_workers_multiplier"));
		inactiveUnitTime = Integer.parseInt(
				Util.getPropertyNotNull(props, "es_inactive_unit_time"));
		progressPercent = Integer.parseInt(Util.getPropertyNotNull(props, "es_progress_percent"));
		
		replayFolder = Util.getPropertyNotNull(props, "replay_folder");
		replayFolderGood = Util.getPropertyNotNull(props, "es_replay_folder_good");
		replayFolderBad = Util.getPropertyNotNull(props, "es_replay_folder_bad");
		replayFolderAlmost = Util.getPropertyNotNull(props, "es_replay_folder_almost");
		replayAlmostPercent = Integer.parseInt(
				Util.getPropertyNotNull(props, "es_replay_almost_percent"));
		debugDrawTerrain = Boolean.parseBoolean(
				Util.getPropertyNotNull(props, "es_debug_draw_terrain"));
		debugDrawRegionIds = Boolean.parseBoolean(
				Util.getPropertyNotNull(props, "es_debug_draw_region_ids"));
		
		dbc = new DbConnection();
		bwapi = new JNIBWAPI(this, true);
		int[] regionData = { 0, -1000, -1000 };
		REGION_NONE = new Region(regionData, 0, new int[0]);
	}
	
	@Override
	public void matchStart() {
		bwapi.enablePerfectInformation();
		bwapi.setGameSpeed(0);
		bwapi.setFrameSkip(24); // Don't draw every frame (not the same as recorded frames)
		bwapi.setLatCom(false);
		
		mi = new MatchInfo(bwapi.getPlayers());
		
		String mapFileName = bwapi.getMap().getFileName();
		LOGGER.info("Starting game: " + mapFileName + " : " + bwapi.getMap().getName());
		
		LOGGER.info("Starting recording on " + mapFileName);
		if (!bwapi.isReplay())
			LOGGER.severe("This is not a replay! This should only be run on replays");
		
		try {
			initDbRecording();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error Starting Game", e);
			bwapi.leaveGame();
		}
		LOGGER.info("Map initialisation completed. Starting state extraction...");
	}
	
	@Override
	public void matchFrame() {
		// Debug draw terrain
		if (debugDrawTerrain) {
			bwapi.getMap().drawTerrainData(bwapi);
		}
		// Debug draw region IDs (very slow)
		if (debugDrawRegionIds) {
			for (int i = 0; i < bwapi.getMap().getSize().getBX(); i++) {
				for (int j = 0; j < bwapi.getMap().getSize().getBY(); j++) {
					Position p = new Position(i, j, PosType.BUILD);
					Region r = bwapi.getMap().getRegion(p);
					int regionId = r != null ? r.getID() : 0;
					bwapi.drawText(p, "" + regionId, false);
				}
			}
		}
		
		int frame = bwapi.getFrameCount();
		// Print percentage completion every progressPercent
		if (progressPercent != 0 &&
				(frame + 1) % (bwapi.getReplayFrameTotal() / (100 / progressPercent)) == 0) {
			int percentage = (frame + 1) / (bwapi.getReplayFrameTotal() / 100);
			LOGGER.info(String.format("%3d", percentage) + "% complete");
		}
		if ((extractionMode == ExtractionMode.TIMED_FRAMES && isTimedFrame(frame)) ||
				extractionMode == ExtractionMode.ACTION_FRAMES && isActionFrame(frame) ||
				extractionMode == ExtractionMode.ATTACK_FRAMES && isAttackFrame(frame)) {
			try {
				recordUnitAttributeChanges(frame);
				recordVisibilityChanges(frame);
				recordRegionValueChanges(frame);
				recordResourceChanges(frame);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error Updating State: " + e.getMessage(), e);
			}
		}
		// Frame skip turns off sometimes due to minimising/maximising the window, so just turn
		// it on periodically
		if (frame % (frameSkip * 20) == 0) {
			bwapi.setGameSpeed(0);
			bwapi.setFrameSkip(frameSkip);
		}
	}
	
	@Override
	public void matchEnd(boolean winner) {
		// winner is always false in replays
		LOGGER.info("Finished " + bwapi.getMap().getFileName());
		int frame = bwapi.getFrameCount();
		int total = bwapi.getReplayFrameTotal();
		String result = null;
		String folderName = null;
		if (frame == total) {
			// Didn't end prematurely
			cleanupDatabase();
			result = "good";
			folderName = replayFolderGood;
		} else {
			// Ended prematurely, we may have quit because it couldn't be parsed
			LOGGER.warning(String.format("Ended on frame %d of %d (%d%%)", frame, total,
					100 * frame / total));
			if (100 * frame / total >= replayAlmostPercent) {
				// Many replays stop just before their frame total; should still clean up
				cleanupDatabase();
				result = "almost";
				folderName = replayFolderAlmost;
			} else {
				result = "bad";
				folderName = replayFolderBad;
			}
		}
		String mapFileName = bwapi.getMap().getFileName();
		LOGGER.info("Adding " + mapFileName + " to " + result + " replays");
		// Move the replay to the finished folder (note this will cause BWAPI's auto_menu
		// to hang if it completes a full loop of replays and tries to go back to the first)
		File replay = new File(replayFolder, mapFileName);
		if (replay.exists()) {
			File finishedFolder = new File(replayFolder, folderName);
			finishedFolder.mkdirs();
			File dest = new File(finishedFolder, mapFileName);
			if (dest.exists()) {
				LOGGER.warning("Cannot move, destination file exists: " + dest.getPath());
			} else {
				replay.renameTo(dest);
			}
		} else {
			LOGGER.info("Replay wasn't in expected place to move: " + replay.getAbsolutePath());
		}
	}
	
	@Override
	public void playerLeft(int id) {
		// Check that the player is one we are interested in (ie. not an observer)
		Player p = bwapi.getPlayer(id);
		if (mi.playerIdToPlayerReplayId.get(id) == null) {
			LOGGER.fine("Ignored playerLeft event for player: " + p.getName());
			return;
		}
		// Record the event with one of the player's units to indicate who left
		List<Unit> units = bwapi.getUnits(p);
		if (units.size() > 0) {
			recordEvent(EventType.PlayerLeft, mi.unitIdToDbId.get(units.get(0).getID()), null);
			return;
		} else {
			for (Unit u : mi.allUnits.values()) {
				if (u.getPlayerID() == id && mi.unitIdToDbId.get(u.getID()) != null) {
					recordEvent(EventType.PlayerLeft, mi.unitIdToDbId.get(u.getID()), null);
					return;
				}
			}
		}
	}
	
	@Override
	public void nukeDetect(Position p) {
		// pixel coordinates
		LOGGER.fine("Nuke Detect event at: (" + p.getPX() + "," + p.getPY() + ")");
		List<Object> data = new ArrayList<>();
		data.add(mi.dbMapId);
		data.add(p.getBX());
		data.add(p.getBY());
		long buildTileId = -1;
		try {
			buildTileId = dbc.queryFirstColumn("SELECT * FROM buildtile " +
					"WHERE MapID=? AND BTilePosX=? AND BTilePosY=?", data);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error in nukeDetect", e);
			return;
		}
		recordEvent(EventType.NukeDetect, null, buildTileId);
	}
	
	@Override
	public void unitCreate(int unitID) {
		LOGGER.finer("Unit created: " + unitID + " in frame " + bwapi.getFrameCount());
		Unit u = bwapi.getUnit(unitID);
		if (u == null) {
			LOGGER.warning("Unit was null!");
			return;
		}
		if (!mi.playerIdToPlayerReplayId.containsKey(u.getPlayerID())) {
			LOGGER.fine("Non-player unit created: " + unitID + ". Ignored.");
			return;
		}
		mi.allUnits.put(unitID, u);
		try {
			recordUnit(u);
			recordEvent(EventType.UnitCreate, mi.unitIdToDbId.get(unitID), null);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error creating unit", e);
		}
	}
	
	@Override
	public void unitDestroy(int unitID) {
		// Get info from allUnits as bwapi clears records of destroyed units
		Unit u = mi.allUnits.get(unitID);
		if (u == null || !mi.playerIdToPlayerReplayId.containsKey(u.getPlayerID())) {
			LOGGER.fine("Non-player unit destroyed: " + unitID + ". Ignored.");
			if (bwapi.getUnit(unitID) != null &&
					mi.playerIdToPlayerReplayId.containsKey(bwapi.getUnit(unitID).getPlayerID())) {
				LOGGER.warning("Unit wasn't in allUnits but should have been!");
			}
			return;
		}
		LOGGER.finer("Unit destroyed: " + unitID + "/" + mi.unitIdToDbId.get(unitID));
		recordEvent(EventType.UnitDestroy, mi.unitIdToDbId.get(unitID), null);
	}
	
	@Override
	public void unitMorph(int unitID) {
		LOGGER.fine("Unit morphed: " + unitID + "/" + mi.unitIdToDbId.get(unitID));
		if (!mi.unitIdToDbId.containsKey(unitID)) {
			LOGGER.severe("UnitMorph unit wasn't in unitIdToDbId");
		}
		recordEvent(EventType.UnitMorph, mi.unitIdToDbId.get(unitID), null);
	}
	
	@Override
	public void unitRenegade(int unitID) {
		if (!mi.unitIdToDbId.containsKey(unitID)) {
			Unit u = bwapi.getUnit(unitID);
			if (u == null) {
				LOGGER.fine("UnitRenegade unit wasn't in unitIdToDbId. Also wasn't in bwapi");
			} else {
				LOGGER.fine("UnitRenegade unit wasn't in unitIdToDbId. Must be an observer's unit. "
						+ "RepID: " + u.getReplayID() + " TypeID: " + u.getTypeID());
			}
			return;
		}
		LOGGER.fine("Unit renegaded: " + unitID + "/" + mi.unitIdToDbId.get(unitID)
				+ " in frame " + bwapi.getFrameCount() + "/" + bwapi.getReplayFrameTotal());
		recordEvent(EventType.UnitRenegade, mi.unitIdToDbId.get(unitID), null);
	}
	
	/** create initial entries in DB */
	private void initDbRecording() throws SQLException {
		// SQL query data
		List<Object> data = new ArrayList<>();
		
		// insert map info
		// Create a new entry for this map (or find the existing entry). Name is not used to
		// identify maps as many different versions of maps are called by the same name.
		data.add(bwapi.getMap().getHash());
		ResultSet rs = dbc.executeQuery("SELECT * FROM map WHERE hash=?", data);
		String prevMapName = null;
		if (rs.next()) {
			mi.dbMapId = rs.getInt("mapid");
			prevMapName = rs.getString("mapName");
		}
		
		data.clear();
		data.add(bwapi.getMap().getName());
		data.add(bwapi.getMap().getStartLocations().size());
		data.add(bwapi.getMap().getHash());
		if (mi.dbMapId == -1) {
			mi.dbMapId = (int) dbc.executeInsert(
					"INSERT INTO map (mapName, numStartPos, hash) VALUES (?, ?, ?)", data, true);
		} else {
			if (prevMapName != null && !prevMapName.equals(bwapi.getMap().getName())) {
				// Map names shouldn't be changing unless there is a hash collision
				LOGGER.warning("Map name changed from: " + prevMapName + " to: "
						+ bwapi.getMap().getName());
			}
			dbc.executeUpdate("UPDATE map SET mapName=?, numStartPos=? WHERE hash=?", data, true);
		}
		
		// insert regions. Treat "no region" (0) as another region
		List<Region> regionsPlusOne = new ArrayList<>(bwapi.getMap().getRegions());
		regionsPlusOne.add(REGION_NONE);
		for (Region region : regionsPlusOne) {
			data.clear();
			data.add(mi.dbMapId);
			data.add(region.getID());
			long dbRegionId = dbc.executeInsert(
					"INSERT INTO region (MapID, ScRegionID) VALUES (?, ?)", data, true);
			if (dbRegionId == -1) {
				LOGGER.severe("Region ID was -1 from DB " + Util.join(data));
			}
			mi.regionToDbRegionId.put(region, dbRegionId);
		}
		// Map null to the DB ID for "no region" to simplify getting regions from BWAPI
		mi.regionToDbRegionId.put(null, mi.regionToDbRegionId.get(REGION_NONE));
		
		// insert build tile data
		DistanceMap dMap = new DistanceMap(bwapi.getMap());
		for (int x = 0; x < bwapi.getMap().getSize().getBX(); x++) {
			for (int y = 0; y < bwapi.getMap().getSize().getBY(); y++) {
				// There are 4x4 walk tiles per build tile. Gather walkable info into a bit array
				String walkable = "";
				for (int i = 0; i < WTPBT; i++) {
					for (int j = 0; j < WTPBT; j++) {
						// Note iteration is in columns, not rows
						boolean w = bwapi.getMap().isWalkable(
								new Position(x * WTPBT + i, y * WTPBT + j, PosType.WALK));
						walkable += w ? 1 : 0;
					}
				}
				Position p = new Position(x, y, PosType.BUILD);
				data.clear();
				data.add(mi.dbMapId);
				data.add(x);
				data.add(y);
				data.add(bwapi.getMap().getGroundHeight(p));
				data.add(bwapi.getMap().isBuildable(p));
				data.add(walkable); // Use as binary string (note "b" in queries)
				data.add(dMap.getChokeDist(p));
				data.add(dMap.getBaseLocationDist(p));
				data.add(dMap.getStartLocationDist(p));
				Long regionId = mi.regionToDbRegionId.get(bwapi.getMap().getRegion(p));
				if (regionId == null) {
					Region r = bwapi.getMap().getRegion(p);
					LOGGER.severe("DbRegionID was null for " + r);
				}
				data.add(mi.regionToDbRegionId.get(bwapi.getMap().getRegion(p)));
				long buildTileId = -1;
				try {
					buildTileId = dbc.executeInsert("INSERT INTO buildtile " +
							"(MapID, BTilePosX, BTilePosY, GroundHeightID, Buildable, " +
							"Walkable, ChokeDist, BaseLocationDist, StartLocationDist, RegionID) " +
							"VALUES (?, ?, ?, ?, ?, b?, ?, ?, ?, ?)", data, true);
				} catch (SQLException e) {
					LOGGER.warning("Failed to insert/find buildTile. Trying to update instead.");
					// Find the existing row using only the unique columns
					buildTileId = dbc.queryFirstColumn("SELECT * FROM buildtile " +
							"WHERE MapID=? AND BTilePosX=? AND BTilePosY=?", data.subList(0, 3));
					if (buildTileId == -1) {
						LOGGER.severe("Failed to get existing buildTile entry");
						return;
					}
					// Update the existing build tile
					data.add(buildTileId);
					dbc.executeUpdate("UPDATE buildtile SET GroundHeightID=?, Buildable=?, " +
							"Walkable=b?, ChokeDist=?, BaseLocationDist=?, StartLocationDist=?, " +
							"RegionID=? WHERE BuildTileID=?",
							data.subList(3, data.size()), true);
				}
			}
		}
		
		// Find start position build tile IDs in DB
		Map<Player, Long> playerToStartPosBtId = new HashMap<>();
		for (Player p : bwapi.getPlayers()) {
			if (p.isNeutral()) {
				continue;
			}
			Position startLoc = p.getStartLocation();
			data.clear();
			data.add(mi.dbMapId);
			data.add(startLoc.getBX());
			data.add(startLoc.getBY());
			long buildTileId = dbc.queryFirstColumn("SELECT BuildTileID FROM `buildtile` WHERE " +
					"MapID = ? AND BTilePosX = ? AND BTilePosY = ?", data);
			if (buildTileId == -1) {
				LOGGER.severe("BuildTileID was -1 at startlocation");
				continue;
			}
			playerToStartPosBtId.put(p, buildTileId);
		}
		
		// Check if replay entry exists, and create/update as needed
		data.clear();
		data.add(bwapi.getMap().getFileName());
		mi.dbReplayId = (int) dbc.queryFirstColumn("SELECT * FROM replay WHERE ReplayName=?", data);
		data.clear();
		data.add(mi.dbMapId);
		data.add(bwapi.getReplayFrameTotal());
		data.add(bwapi.getMap().getFileName());
		if (mi.dbReplayId == -1) {
			// create new entry for this replay
			mi.dbReplayId = (int) dbc.executeInsert("INSERT INTO replay " +
					"(`MapID`,`Duration`,`ReplayName`) VALUES (?, ?, ?)", data, false);
			
		} else {
			// Update existing entry (mapid could be null from ExtractActions)
			dbc.executeUpdate(
					"UPDATE replay SET MapID=?, Duration=? WHERE ReplayName=?", data, true);
		}
		
		// create a new DB entry for each player in this game and store a mapping between ids within
		// the game and within the DB
		for (Player p : bwapi.getPlayers()) {
			if (!bwapi.getUnits(p).isEmpty()) {
				long playerReplayId;
				// Insert/update the player replay record
				if (p.isNeutral()) {
					// Neutral players weren't added by ExtractActions, so add them now
					data.clear();
					data.add(p.getName());
					data.add(false);
					data.add(RaceTypes.None.getID());
					data.add(mi.dbReplayId);
					playerReplayId = dbc.executeInsert("INSERT INTO playerreplay " +
							"(PlayerName, Winner, RaceID, ReplayID) VALUES (?, ?, ?, ?)",
							data, true);
				} else {
					// Non-neutral players were added already by ExtractActions so we just want to
					// update the startPosition
					data.clear();
					data.add(playerToStartPosBtId.get(p));
					data.add(p.getName());
					data.add(p.getRaceID());
					data.add(mi.dbReplayId);
					playerReplayId = dbc.executeUpdate("UPDATE playerreplay " +
							"SET StartPosBtId=? " +
							"WHERE PlayerName=? AND RaceID=? AND ReplayID=?", data, true);
				}
				
				if (playerReplayId == -1) {
					// Probably because player was actually an observer, but isObserver seems to be
					// always true in melee games. Rely on ExtractActions to have found the correct
					// players using action counts.
					LOGGER.warning("PlayerReplayId was -1 for: " + Util.join(data) +
							" (probably an observer)");
					continue;
				}
				// create new mapping between this player's ingame ID and DB ID
				mi.playerIdToPlayerReplayId.put(p.getID(), playerReplayId);
			}
		}
		LOGGER.info("#Players found (incl Neutral): " + mi.playerIdToPlayerReplayId.size());
		if (mi.playerIdToPlayerReplayId.size() < 3) {
			LOGGER.severe("Less than 3 players (incl. neutral). Leaving.");
			bwapi.leaveGame();
		}
	}
	
	/** Create unit entry in DB */
	private void recordUnit(Unit unit) throws SQLException {
		long dbUnitId;
		List<Object> data = new ArrayList<>();
		data.add(unit.getTypeID());
		data.add(mi.playerIdToPlayerReplayId.get(unit.getPlayerID()));
		data.add(unit.getReplayID());
		
		if (!mi.playerIdToPlayerReplayId.containsKey(unit.getPlayerID())) {
			String message = "Tried to add unit with invalid player: " + Util.join(data)
					+ " in frame " + bwapi.getFrameCount() + "/" + bwapi.getReplayFrameTotal();
			// Update count of invalid-playerreplay units
			Integer count = mi.invalidPlayerIdToUnitCount.get(unit.getPlayerID());
			if (count == null) {
				count = 0;
			}
			count++;
			mi.invalidPlayerIdToUnitCount.put(unit.getPlayerID(), count);
			// Ignore observers' units (there should be 5 per observer if none more are made). They
			// should be created only at the very start of the game but BWAPI does weird stuff.
			if (count > 10) {
				LOGGER.severe(message);
				bwapi.leaveGame();
			} else {
				LOGGER.warning(message);
			}
			return;
		}
		
		// Update unit type in DB (should be entered already by ExtractActions)
		dbUnitId = dbc.executeUpdate("UPDATE unit SET UnitTypeID=? " +
				"WHERE PlayerReplayID=? AND UnitReplayID=?", data, true);
		if (dbUnitId == -1) {
			// Neutral units will need to be created because they aren't extracted by ExtractActions
			// Other units may need to be created if they were never given orders in the replay
			LOGGER.info(String.format(
					"Unit added to DB: type: %d (%s), playerReplay: %d, unitReplay: %d",
					unit.getTypeID(), UnitTypes.getUnitType(unit.getTypeID()),
					mi.playerIdToPlayerReplayId.get(unit.getPlayerID()), unit.getReplayID()));
			dbUnitId = dbc.executeInsert(
					"INSERT INTO unit (UnitTypeID, PlayerReplayID, UnitReplayID) " +
							"VALUES (?, ?, ?)", data, true);
		}
		if (dbUnitId == -1) {
			LOGGER.severe("Problem getting unit ID from db." + Util.join(data));
			bwapi.leaveGame();
			return;
		}
		mi.unitIdToDbId.put(unit.getID(), dbUnitId);
		// create entry in internal map
		mi.unitIdToAttributes.put(unit.getID(), new UnitAttributes());
		
		// set unit/player visibility
		Map<Integer, Boolean> unitVis = new TreeMap<>();
		for (Player p : bwapi.getPlayers()) {
			if (!p.isNeutral() && mi.playerIdToPlayerReplayId.containsKey(p.getID())) {
				// && !p.isObserver() isObserver always true in replays. BWAPI bug?
				// Just check if the player is in the dbPlayerReplayIdMap (the ones with few
				// actions in 3+ person games will have been excluded by ExtractActions)
				unitVis.put(p.getID(), false);
			}
		}
		mi.uIdToPIdToVisibility.put(unit.getID(), unitVis);
	}
	
	/** Update the DB with any changes to unit attributes since last time step */
	private void recordUnitAttributeChanges(int frame) throws SQLException {
		List<Object> data = new ArrayList<>();
		String select = "SELECT * FROM attributechange WHERE " +
				"UnitID=? AND ChangeTime=? AND AttributeTypeID=?";
		String insert = "INSERT INTO attributechange (`UnitID`,`ChangeTime`,`AttributeTypeID`," +
				"`ChangeVal`) VALUES (?, ?, ?, ?)";
		String update = "UPDATE attributechange SET ChangeVal=? WHERE AttributeChangeID=?";
		for (int unitId : mi.unitIdToAttributes.keySet()) {
			// for each unit, compare its current attribute values to the ones from the previous
			// time step
			Unit currentUnit = mi.allUnits.get(unitId);
			if (currentUnit == null) {
				LOGGER.severe("Unit was null! ID:" + unitId);
				continue;
			}
			if (frameSkipWorkersMultiplier > 1
					&& bwapi.getUnitType(currentUnit.getTypeID()).isWorker()) {
				int actionFrameDist = getClosestActionFrameDist(currentUnit, frame);
				if (actionFrameDist > inactiveUnitTime
						&& frame % (frameSkipWorkersMultiplier * frameSkip) != 0 ) {
					// No recent actions, so skip frameSkipWorkersMultiplier times as many frames
					continue;
				}
			}
				
			UnitAttributes previous = mi.unitIdToAttributes.get(unitId);
			UnitAttributes current = new UnitAttributes(currentUnit, mi.allUnits);
			
			for (int i = 0; i < UnitAttributes.NUM_ATTRIBUTES; i++) {
				if (current.attributes[i] != previous.attributes[i]) {
					data.clear();
					data.add(mi.unitIdToDbId.get(unitId));
					data.add(frame);
					data.add(i);
					
					// Save new value or update an existing incorrect value
					ResultSet rs = dbc.executeQuery(select, data);
					// Check if this change is already in the DB
					if (rs.next()) {
						long id = rs.getLong("AttributeChangeId");
						int attributeValue = rs.getInt("ChangeVal");
						mi.allAttributeChangeIds.add(id);
						// Value doesn't match the database!
						// Change the database's stored value
						if (attributeValue != current.attributes[i]) {
							LOGGER.fine("Attribute " + i + " changed from " + attributeValue
									+ " to " + current.attributes[i]);
							data.clear();
							data.add(current.attributes[i]);
							data.add(id);
							dbc.executeUpdate(update, data, true);
						}
						if (rs.next()) {
							LOGGER.severe("Should only be one row selected by SELECT query");
						}
					} else {
						// Not in the DB, so add it
						data.add(current.attributes[i]);
						long id = dbc.executeInsert(insert, data, false);
						mi.allAttributeChangeIds.add(id);
					}
					// update the stored value
					previous.attributes[i] = current.attributes[i];
				}
			}
		}
	}
	
	private void recordVisibilityChanges(int frame) throws SQLException {
		List<Object> data = new ArrayList<>();
		String query = "INSERT INTO visibilitychange " +
				"(`ViewerID`,`UnitID`,`ChangeTime`,`ChangeVal`) VALUES (?, ?, ?, ?)";
		for (int unitId : mi.uIdToPIdToVisibility.keySet()) {
			Map<Integer, Boolean> pIdToVisibility = mi.uIdToPIdToVisibility.get(unitId);
			Integer toRemovePlayerId = null;
			for (int playerId : pIdToVisibility.keySet()) {
				if (mi.playerIdToPlayerReplayId.get(playerId) == null) {
					toRemovePlayerId = playerId;
					LOGGER.severe("Player ID " + playerId + " didn't have a playerReplayId");
					continue;
				}
				boolean visible = pIdToVisibility.get(playerId);
				if (visible != bwapi.isVisibleToPlayer(
						mi.allUnits.get(unitId), bwapi.getPlayer(playerId))) {
					// record change in DB
					data.clear();
					data.add(mi.playerIdToPlayerReplayId.get(playerId));
					data.add(mi.unitIdToDbId.get(unitId));
					data.add(frame);
					data.add(!visible);
					
					long id = dbc.executeInsert(query, data, true);
					mi.allVisibilityChangeIds.add(id);
					// Update stored value
					pIdToVisibility.put(playerId, !visible);
				}
			}
			if (toRemovePlayerId != null)
				pIdToVisibility.remove(toRemovePlayerId);
		}
	}
	
	/** Update the database with any changes in aggregate region values known to each player */
	private void recordRegionValueChanges(int frame) throws SQLException {
		List<Object> data = new ArrayList<>();
		String insert = "INSERT INTO regionvaluechange (PlayerReplayID, RegionID, Frame) " +
				"VALUES (?, ?, ?)";
		String update = "UPDATE regionvaluechange SET GroundUnitValue=?, BuildingValue=?, " +
				"AirUnitValue=?, EnemyGroundUnitValue=?, EnemyBuildingValue=?, " +
				"EnemyAirUnitValue=?, ResourceValue=? WHERE ChangeID=?";
		
		for (int pid : mi.playerIdToPlayerReplayId.keySet()) {
			Player p = bwapi.getPlayer(pid);
			// Exclude neutral players
			if (p.isNeutral()) {
				continue;
			}
			Map<Integer, Unit> lastSeen = mi.lastSeenUnitStates.get(p);
			// In the first frame, add in all the static mineral and gas information for the map
			if (frame == 0) {
				for (Unit u : bwapi.getNeutralUnits()) {
					if (u.getResources() > 0) {
						// Necessary to copy unit so object doesn't get updated with new values
						lastSeen.put(u.getID(), u.clone());
					}
				}
			}
			
			// Update lastSeenUnitStates with latest info
			for (Unit u : mi.allUnits.values()) {
				if (!u.isExists()) {
					/*
					 * Remove dead units from lastSeenUnitStates - difficult to know if a player saw
					 * a unit die, but seeing as this would usually happen due to player actions we
					 * will assume they know about it. Might be better to use unitDestroy() event
					 * instead and maybe isVisible(x,y) as well if we are looking at the position
					 * the unit is supposed to be at? This will also remove used-up resources which
					 * wouldn't be known to the player: use getStaticMinerals/getStaticGeysers to
					 * fix?
					 */
					lastSeen.remove(u.getID());
				} else if (bwapi.isVisibleToPlayer(u, p)) {
					// Necessary to copy unit so object doesn't get updated with new values
					lastSeen.put(u.getID(), u.clone());
				}
			}
			
			// Update region values
			Map<Region, RegionValues> regionValues = mi.playerToRegionToValues.get(pid);
			Map<Region, RegionValues> newRegionValues = sumRegionValues(p, lastSeen.values());
			
			// Go through each region and update the DB value if changed.
			for (Region r : newRegionValues.keySet()) {
				RegionValues rv = newRegionValues.get(r);
				if (!rv.equals(regionValues.get(r))) {
					data.clear();
					data.add(mi.playerIdToPlayerReplayId.get(p.getID()));
					data.add(mi.regionToDbRegionId.get(r));
					if (mi.regionToDbRegionId.get(r) == null) {
						LOGGER.severe("region db ID was null!");
					}
					data.add(frame);
					long changeId = dbc.executeInsert(insert, data, true);
					if (changeId == -1) {
						LOGGER.severe("Got -1 changeID for regionValueChange " + Util.join(data));
						continue;
					}
					
					// Store the change in the db
					data.clear();
					data.add(rv.groundUnitValue);
					data.add(rv.buildingValue);
					data.add(rv.airUnitValue);
					data.add(rv.enemyGroundUnitValue);
					data.add(rv.enemyBuildingValue);
					data.add(rv.enemyAirUnitValue);
					data.add(rv.resourceValue);
					data.add(changeId);
					long id = dbc.executeUpdate(update, data, true);
					mi.allRegionValueChangeIds.add(id);
					
					// Update the stored values
					regionValues.put(r, rv);
				}
			}
		}
	}
	
	/** Sum up the value of the units and resources in each region */
	private Map<Region, RegionValues> sumRegionValues(Player p, Collection<Unit> lastSeenUnits) {
		Map<Region, RegionValues> newRegionValues = new HashMap<>();
		for (Unit u : lastSeenUnits) {
			Region r = bwapi.getMap().getRegion(u.getTilePosition()); // TODO change to Position
			if (r == null) {
				// We must be in a non-region area
				r = REGION_NONE;
			}
			RegionValues rv = newRegionValues.get(r);
			if (rv == null) {
				rv = new RegionValues();
				newRegionValues.put(r, rv);
			}
			UnitType ut = bwapi.getUnitType(u.getTypeID());
			// Currently have no way of checking allies/enemies during a replay, count only
			// current player's units as allied and all others as enemy
			if (u.getPlayerID() == p.getID()) {
				if (ut.isBuilding()) {
					rv.buildingValue += ut.getMineralPrice() + ut.getGasPrice();
				} else if (ut.isFlyer()) {
					rv.airUnitValue += ut.getMineralPrice() + ut.getGasPrice();
				} else {
					rv.groundUnitValue += ut.getMineralPrice() + ut.getGasPrice();
				}
			} else if (!bwapi.getPlayer(u.getPlayerID()).isNeutral()) { // Exclude neutrals
				if (ut.isBuilding()) {
					rv.enemyBuildingValue += ut.getMineralPrice() + ut.getGasPrice();
				} else if (ut.isFlyer()) {
					rv.enemyAirUnitValue += ut.getMineralPrice() + ut.getGasPrice();
				} else {
					rv.enemyGroundUnitValue += ut.getMineralPrice() + ut.getGasPrice();
				}
			}
			// Count the resources (minerals, gas) in the region. This should count mineral
			// patches, gas geysers, and gas extraction buildings
			rv.resourceValue += u.getResources();
		}
		return newRegionValues;
	}
	
	/**
	 * Record the resources (incl. supply) of each player. Actually recording each time instead of
	 * just the changes as resources likely change almost constantly. @throws SQLException
	 */
	private void recordResourceChanges(int frame) throws SQLException {
		List<Object> data = new ArrayList<>();
		String insert = "INSERT INTO resourcechange (PlayerReplayID, Frame) VALUES (?, ?)";
		String update = "UPDATE resourcechange SET Minerals=?, Gas=?, Supply=?, TotalMinerals=?, " +
				"TotalGas=?, TotalSupply=? WHERE ChangeID=?";
		
		for (int pid : mi.playerIdToPlayerReplayId.keySet()) {
			Player p = bwapi.getPlayer(pid);
			if (p.isNeutral()) {
				continue;
			}
			// Check that resources have changed
			PlayerResources resources = new PlayerResources(p);
			if (resources.equals(mi.playerToResources.get(pid))) {
				continue;
			} else {
				mi.playerToResources.put(pid, resources);
			}
			
			// Ensure there is an entry for this player + frame
			data.clear();
			data.add(mi.playerIdToPlayerReplayId.get(p.getID()));
			data.add(frame);
			long changeId = dbc.executeInsert(insert, data, true);
			if (changeId == -1) {
				LOGGER.severe("-1 changeID for resourcechange: " + Util.join(data));
				continue;
			}
			// Update the entry with the correct values
			data.clear();
			data.add(p.getMinerals());
			data.add(p.getGas());
			data.add(p.getSupplyUsed());
			data.add(p.getCumulativeMinerals());
			data.add(p.getCumulativeGas());
			data.add(p.getSupplyTotal());
			data.add(changeId);
			long id = dbc.executeUpdate(update, data, true);
			mi.allResourceChangeIds.add(id);
		}
	}
	
	/**
	 * Record an event occurrence to the DB.
	 * 
	 * @param eventType must match an eventtype.name in the DB.
	 * @param dbUnitId the subject of the event, or null if not needed. Set to any one of the
	 *        player's units in the case of the PlayerLeft event.
	 * @param dbBuildTileId the location of the event. Used only in NukeDetect events, otherwise
	 *        null.
	 */
	private void recordEvent(EventType eventType, Long dbUnitId, Long dbBuildTileId) {
		if (dbUnitId == null && dbBuildTileId == null) {
			LOGGER.warning("both dbUnitId and buildTileId were null on " + eventType);
			return;
		} else if (dbUnitId != null && dbBuildTileId != null) {
			LOGGER.warning("both dbUnitId and buildTileId were NOT null on " + eventType);
			return;
		}
		try {
			List<Object> data = new ArrayList<>();
			data.add(mi.dbReplayId);
			data.add(bwapi.getFrameCount());
			data.add(eventType.getID());
			data.add(dbUnitId);
			data.add(dbBuildTileId);
			// Because unitId or buildTileId can be null, need to check for this manually
			long id = dbc.queryFirstColumn("SELECT * FROM event WHERE ReplayID=? AND Frame=? " +
					"AND EventTypeID=? AND (UnitID=? AND BuildTileID IS NULL) OR " +
					"(UnitID IS NULL AND BuildTileID=?)", data);
			if (id == -1) {
				id = dbc.executeInsert("INSERT INTO event " +
						"(ReplayID, Frame, EventTypeID, UnitID, BuildTileID) " +
						"VALUES (?, ?, ?, ?, ?)", data, false);
			}
			mi.allEventIds.add(id);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to add event", e);
		}
	}
	
	/** Clean up any duplicates/orphans that shouldn't be in the DB for this replay */
	private void cleanupDatabase() {
		try {
			// Clean up playerReplay
			dbc.findRemoveExtras("playerReplayId", "playerReplay", "replayId=?", mi.dbReplayId,
					new HashSet<>(mi.playerIdToPlayerReplayId.values()), maxNumExtrasToRemove);
			
			// Clean up event
			dbc.findRemoveExtras("eventId", "event", "replayId=?", mi.dbReplayId, mi.allEventIds,
					maxNumExtrasToRemove);
			
			for (long unitId : mi.unitIdToDbId.values()) {
				// Clean up attributeChange
				dbc.findRemoveExtras("attributeChangeId", "attributeChange", "unitId=?", unitId,
						mi.allAttributeChangeIds, maxNumExtrasToRemove);
			}
			
			for (long playerReplayId : mi.playerIdToPlayerReplayId.values()) {
				// Clean up unit
				// ignore "None" type units - they were added by ExtractActions and are indicative
				// of broken replay files
				dbc.findRemoveExtras("unitId", "unit", "playerReplayId=? AND unitTypeId!=228",
						playerReplayId, new HashSet<>(mi.unitIdToDbId.values()),
						maxNumExtrasToRemove);
				
				// Clean up visibilityChange
				dbc.findRemoveExtras("visibilityChangeId", "visibilityChange", "viewerId=?",
						playerReplayId, mi.allVisibilityChangeIds, maxNumExtrasToRemove);
				
				// Clean up regionValueChange
				dbc.findRemoveExtras("changeId", "regionValueChange", "playerReplayId=?",
						playerReplayId, mi.allRegionValueChangeIds, maxNumExtrasToRemove);
				
				// Clean up resourceChange
				dbc.findRemoveExtras("changeId", "resourceChange", "playerReplayId=?",
						playerReplayId, mi.allResourceChangeIds, maxNumExtrasToRemove);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.WARNING, "Error while cleaning up DB", e);
		}
	}
	
	private boolean isTimedFrame(int frame) {
		return frame % frameSkip == 0;
	}
	
	private boolean isActionFrame(int frame) {
		if (frame == 0) {
			return true;
		}
		if (mi.playerIdToPlayerReplayId.size() == 0) {
			LOGGER.warning("No players in this game!?");
			return false;
		}
		String playersPart = "";
		List<Object> data = new ArrayList<>();
		for (long playerreplayid : mi.playerIdToPlayerReplayId.values()) {
			playersPart += "playerreplayid=? OR ";
			data.add(playerreplayid);
		}
		playersPart = playersPart.substring(0, playersPart.length() - 4);
		data.add(bwapi.getFrameCount());
		long count = 0;
		try {
			count = dbc.queryFirstColumn("SELECT COUNT(*) FROM action WHERE " +
					"(" + playersPart + ") AND frame=?", data);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to get action count", e);
		}
		return count > 0;
	}
	
	private boolean isAttackFrame(int frame) {
		for (Unit u : bwapi.getAllUnits()) {
			if (u.isAttacking() || u.isUnderAttack() || u.isAttackFrame() || u.isStartingAttack()) {
				return frame % attackFrameSkip == 0;
			}
		}
		// Record every frameSkip frames regardless of attacks
		return frame % frameSkip == 0;
	}
	
	/**
	 * Returns the "distance" (absolute difference) to the nearest frame (past or future) in which
	 * an action was given to this unit. Only looks up future actions, so when called on a unit for
	 * the first time, assumes current frame is an action frame. Results are cached so the last past
	 * action is remembered.
	 * @throws SQLException 
	 */
	private int getClosestActionFrameDist(Unit unit, int currentFrame) throws SQLException {
		String query = "SELECT frame FROM action NATURAL JOIN unitgroup NATURAL JOIN unit "
				+ "WHERE playerreplayid=? AND unitreplayid=? AND frame>? ORDER BY frame LIMIT 1";
		Integer lastFrame = mi.unitIdToLastActionFrameCached.get(unit.getID());
		Integer nextFrame = mi.unitIdToNextActionFrameCached.get(unit.getID());
		if (nextFrame == null || currentFrame > nextFrame) {
			// Never seen this unit before or time has passed the previously-stored "next" frame
			// Update lastFrame to the old nextFrame now that we have passed it
			lastFrame = nextFrame;
			mi.unitIdToLastActionFrameCached.put(unit.getID(), lastFrame);
			// Look up new nextFrame
			List<Object> data = new ArrayList<>();
			data.add(mi.playerIdToPlayerReplayId.get(unit.getPlayerID()));
			data.add(unit.getReplayID());
			data.add(currentFrame);
			nextFrame = (int) dbc.queryFirstColumn(query, data);
			if (nextFrame == -1) {
				// No next action frame found
				nextFrame = Integer.MAX_VALUE;
			}
			mi.unitIdToNextActionFrameCached.put(unit.getID(), nextFrame);
		}
		if (lastFrame == null) {
			// Never seen this unit before
			lastFrame = currentFrame;
			mi.unitIdToLastActionFrameCached.put(unit.getID(), lastFrame);
		}
		return Math.min(currentFrame - lastFrame, nextFrame - currentFrame);
	}
	
	/** Convenience class for holding all region values together */
	protected static class RegionValues {
		public int groundUnitValue = 0;
		public int buildingValue = 0;
		public int airUnitValue = 0;
		public int enemyGroundUnitValue = 0;
		public int enemyBuildingValue = 0;
		public int enemyAirUnitValue = 0;
		public int resourceValue = 0;
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RegionValues other = (RegionValues) obj;
			if (groundUnitValue != other.groundUnitValue
					|| buildingValue != other.buildingValue
					|| airUnitValue != other.airUnitValue
					|| enemyGroundUnitValue != other.enemyGroundUnitValue
					|| enemyBuildingValue != other.enemyBuildingValue
					|| enemyAirUnitValue != other.enemyAirUnitValue
					|| resourceValue != other.resourceValue)
				return false;
			return true;
		}
	}
	
	/** Convenience class for holding all player resource values together */
	protected static class PlayerResources {
		private int minerals;
		private int gas;
		private int supplyUsed;
		private int cumulativeMinerals;
		private int cumulativeGas;
		private int supplyTotal;
		
		public PlayerResources(Player p) {
			minerals = p.getMinerals();
			gas = p.getGas();
			supplyUsed = p.getSupplyUsed();
			cumulativeMinerals = p.getCumulativeMinerals();
			cumulativeGas = p.getCumulativeGas();
			supplyTotal = p.getSupplyTotal();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PlayerResources other = (PlayerResources) obj;
			if (cumulativeGas != other.cumulativeGas
					|| cumulativeMinerals != other.cumulativeMinerals
					|| gas != other.gas
					|| minerals != other.minerals
					|| supplyTotal != other.supplyTotal
					|| supplyUsed != other.supplyUsed)
				return false;
			return true;
		}
	}
	
	@Override
	public void connected() {}
	
	@Override
	public void sendText(String text) {}
	
	@Override
	public void receiveText(String text) {}
	
	@Override
	public void saveGame(String gameName) {}
	
	@Override
	public void unitComplete(int unitID) {}
	
	@Override
	public void playerDropped(int playerID) {} // Never called
	
	@Override
	public void nukeDetect() {}
	
	@Override
	public void unitDiscover(int unitID) {} // Full map info so irrelevant
	
	@Override
	public void unitEvade(int unitID) {} // Full map info so irrelevant
	
	@Override
	public void unitShow(int unitID) {} // Full map info so irrelevant
	
	@Override
	public void unitHide(int unitID) {} // Full map info so irrelevant
	
	@Override
	public void keyPressed(int keyCode) {}
}
