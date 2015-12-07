package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import util.DbConnection;

public class Replay {
	private static final Logger LOGGER = Logger.getLogger(Replay.class.getName());
	
	public final int dbReplayId;
	/** Should be accessed via {@link #getMap()} */
	private final int dbMapId;
	/** Name of the replay file, eg. GG1091.rep */
	public final String replayFileName;
	/** Total number of frames in the game. */
	public final int duration;
	
	public Replay(int dbReplayId, int dbMapId, String replayFileName, int duration) {
		this.dbReplayId = dbReplayId;
		this.dbMapId = dbMapId;
		this.replayFileName = replayFileName;
		this.duration = duration;
	}
	
	public Replay(ResultSet rs) throws SQLException {
		this(rs.getInt("ReplayId"), rs.getInt("MapId"), rs.getString("ReplayName"),
				rs.getInt("Duration"));
	}
	
	/** Get list of all replays from DB */
	public static List<Replay> getReplays() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Replay> replays = new ArrayList<>();
		try (ResultSet rs = dbc.executeQuery("SELECT * FROM replay", null); ) {
			while (rs.next()) {
				replays.add(new Replay(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Getting replay list failed.", e);
		}
		return replays;
	}
	
	/** Get players for this replay */
	public List<PlayerReplay> getPlayers() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<PlayerReplay> players = new ArrayList<>();
		try {
			List<Object> data = new ArrayList<>();
			data.add(dbReplayId);
			ResultSet rs = dbc.executeQuery(
					"SELECT * FROM playerreplay WHERE ReplayID=?", data);
			while (rs.next()) {
				players.add(new PlayerReplay(rs));
			}
			if (players.size() != 3) { // Two players + neutral
				LOGGER.warning(players.size() + " players in replay " + dbReplayId);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to load players in replay " + dbReplayId, e);
		}
		return players;
	}
	
	/** Get the map for this replay */
	public ScMap getMap() throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		// Get the map name and number of start positions
		ResultSet rs = dbc.executeQuery("SELECT * FROM map WHERE mapId=?", dbMapId);
		if (!rs.next()) {
			throw new SQLException("No map found for mapId " + dbMapId);
		}
		String mapName = rs.getString("mapName");
		int numStartPos = rs.getInt("numStartPos");
		
		// Get the map size
		rs = dbc.executeQuery("SELECT MAX(bTilePosX) as maxX, MAX(bTilePosY) as maxY " +
				"FROM buildtile WHERE mapId=?", dbMapId);
		if (!rs.next()) {
			throw new SQLException("No map tiles found for mapId " + dbMapId);
		}
		int xSize = rs.getInt("maxX");
		int ySize = rs.getInt("maxY");
		return new ScMap(dbMapId, mapName, numStartPos, xSize, ySize);
	}
	
	/** Get all events in this replay, in frame order */
	public List<Event> getEvents() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Event> events = new ArrayList<>();
		try (ResultSet rs = dbc.executeQuery(
				"SELECT * FROM event WHERE replayId=? ORDER BY frame, eventId", dbReplayId); ) {
			while (rs.next()) {
				events.add(new Event(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to load events in replay " + dbReplayId, e);
		}
		return events;
	}

	@Override
	public int hashCode() {
		return dbReplayId;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Replay other = (Replay) obj;
		if (dbReplayId != other.dbReplayId)
			return false;
		return true;
	}
	
}
