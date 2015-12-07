package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jnibwapi.types.RaceType;
import jnibwapi.types.RaceType.RaceTypes;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;
import util.DbConnection;
import util.UnitAttributes.UnitAttribute;

public class PlayerReplay {
	private static final Logger LOGGER = Logger.getLogger(PlayerReplay.class.getName());
	
	// Unchanging data
	public final int playerReplayIdDb;
	public final String name;
	public final boolean winner;
	private final int raceId;
	private final int replayIdDb;
	public final long dbStartPosBtId;
	
	public PlayerReplay(int dbPlayerReplayId, String name, boolean winner, int raceId,
			int dbReplayId, long dbStartPosBtId) {
		this.playerReplayIdDb = dbPlayerReplayId;
		this.name = name;
		this.winner = winner;
		this.raceId = raceId;
		this.replayIdDb = dbReplayId;
		this.dbStartPosBtId = dbStartPosBtId;
		if (dbStartPosBtId == 0 && !"Neutral".equals(name))
			LOGGER.warning("StartPos was 0 for " + name);
	}
	
	public PlayerReplay(ResultSet rs) throws SQLException {
		this(rs.getInt("PlayerReplayId"), rs.getString("PlayerName"), rs.getBoolean("Winner"),
				rs.getInt("RaceId"), rs.getInt("ReplayId"), rs.getLong("StartPosBtId"));
	}
	
	/** Retrieve a PlayerReplay from its ID */
	public static PlayerReplay fromId(long playerReplayIdDb) throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		
		ResultSet rs = dbc.executeQuery("SELECT * FROM playerreplay WHERE playerReplayId=?",
				playerReplayIdDb);
		if (rs.next()) {
			return new PlayerReplay(rs);
		} else {
			throw new SQLException("No playerreplay found for ID " + playerReplayIdDb);
		}
	}
	
	/** Get all actions by this player, in frame order */
	public List<Action> getActions() {
		return getActions(0, Integer.MAX_VALUE);
	}
	
	/**
	 * Get all actions by this player, in frame order, between frameStart and frameEnd (inclusive)
	 */
	public List<Action> getActions(int frameStart, int frameEnd) {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Action> actions = new ArrayList<>();
		List<Object> data = new ArrayList<>();
		data.add(playerReplayIdDb);
		data.add(frameStart);
		data.add(frameEnd);
		ResultSet rs;
		try {
			rs = dbc.executeQuery("SELECT * FROM action WHERE playerReplayId=?"
					+ " AND frame>=? AND frame<=? ORDER BY frame, actionid", data);
			while (rs.next()) {
				actions.add(new Action(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Getting actions failed for player " + playerReplayIdDb, e);
		}
		return actions;
	}
	
	/** Get list of events pretending to be actions! Hacky to avoid spamming clicks */
	public List<ActionyEvent> getActionyEvents() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<ActionyEvent> events = new ArrayList<>();
		List<Object> data = new ArrayList<>();
		data.add(playerReplayIdDb);
		ResultSet rs;
		try {
			rs = dbc.executeQuery("SELECT * FROM event"
					+ " NATURAL JOIN playerreplay NATURAL JOIN unit"
					+ " WHERE frame>0 AND (eventtypeid=12 OR eventtypeid=14)"
					+ " AND playerreplayid=?"
					+ " ORDER BY frame, eventid", data);
			while (rs.next()) {
				events.add(new ActionyEvent(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE,
					"Getting actiony events failed for player " + playerReplayIdDb, e);
		}
		return events;
	}
	
	/**
	 * Get all strategic-level actions, in order, from both the player actions and game events,
	 * depending on which is more reliable to inform about the actual executed actions in-game.
	 * Necessary because of common "spamming" of actions when they are not executable, still
	 * recorded in the actions list for the player.
	 */
	public List<Action> getStrategicActionsAndEvents() {
		// from player actions, use only research / upgrade actions
		// (and only once per research/upgrade)
		Set<Integer> seenResearch = new HashSet<>();
		Set<Integer> seenUpgrade = new HashSet<>();
		List<Action> actions = getActions().stream()
				.filter(a ->
						(a.unitCommandType == UnitCommandTypes.Research
								&& seenResearch.add(a.targetId))
						|| (a.unitCommandType == UnitCommandTypes.Upgrade
								&& seenUpgrade.add(a.targetId)))
				.collect(Collectors.toList());
		// merge based on frame
		actions.addAll(getActionyEvents());
		Collections.sort(actions, (a1, a2) -> a1.frame - a2.frame);
		
		return actions;
	}
	
	/** Gets the latest record of the player's resources up to and including the given frame */
	public Resources getResources(int frame) throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Object> data = new ArrayList<>();
		data.add(playerReplayIdDb);
		data.add(frame);
		ResultSet rs = dbc.executeQuery(
				"SELECT * FROM resourceChange WHERE playerReplayId=? AND frame<=?"
						+ " ORDER BY frame DESC LIMIT 1", data);
		if (rs.next()) {
			return new Resources(rs);
		} else {
			throw new SQLException(String.format("No resources found for player %d in frame %d",
					playerReplayIdDb, frame));
		}
	}
	
	/**
	 * Get all the units belonging to this player this game. Note that they won't all exist at
	 * once, check their attributes in particular frames with
	 * {@link Unit#getAttribute(int, util.UnitAttributes.UnitAttribute)}
	 */
	public List<Unit> getUnits() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Unit> units = new ArrayList<>();
		try (ResultSet rs = dbc.executeQuery(
				"SELECT * FROM unit WHERE playerReplayId=?", playerReplayIdDb) ) {
			while (rs.next()) {
				units.add(new Unit(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error retrieving units for player " + playerReplayIdDb, e);
		}
		return units;
	}
	
	/**
	 * Get all units belonging to this player that exist in a particular frame. Faster than getting
	 * all units and filtering to see if they exist.
	 */
	public List<Unit> getUnitsExisting(int frame) {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Object> data = new ArrayList<>();
		data.add(UnitAttribute.Exists.getId());
		data.add(frame);
		data.add(playerReplayIdDb);
		
		List<Unit> units = new ArrayList<>();
		try (ResultSet rs = dbc.executeQuery(
				"SELECT * FROM attributeChange NATURAL JOIN unit NATURAL JOIN"
				+ " (SELECT unitId, attributeTypeId, max(changeTime) AS changeTime"
				+ "  FROM attributeChange NATURAL JOIN unit"
				+ "  WHERE attributeTypeId=? AND changeTime<=? AND playerReplayId=?"
				+ "  GROUP BY unitId) AS q "
				+ "WHERE changeVal=1", data) ) {
			while (rs.next()) {
				units.add(new Unit(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error retrieving existing units for player "
					+ playerReplayIdDb + " in frame " + frame, e);
		}
		return units;
	}
	
	public boolean canSee(Unit u, int frame) {
		return u.visibleTo(this, frame);
	}
	
	public boolean hasSeen(Unit u, int frame) {
		return u.visibleToBy(this, frame);
	}
	
	public boolean isNeutral() {
		return getRace() == RaceTypes.None;
	}
	
	public RaceType getRace() {
		return RaceTypes.getRaceType(raceId);
	}
	
	/** Get replay for this PlayerReplay */
	public Replay getReplay() throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		
		ResultSet rs = dbc.executeQuery("SELECT * FROM replay WHERE replayId=?", replayIdDb);
		if (rs.next()) {
			return new Replay(rs);
		} else {
			throw new SQLException("No replay found for player " + playerReplayIdDb);
		}
	}
	
	@Override
	public int hashCode() {
		return playerReplayIdDb;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PlayerReplay other = (PlayerReplay) obj;
		if (playerReplayIdDb != other.playerReplayIdDb)
			return false;
		return true;
	}
	
}
