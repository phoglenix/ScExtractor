package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnibwapi.types.EventType;
import util.DbConnection;

public class Event {
	private static final Logger LOGGER = Logger.getLogger(Event.class.getName());
	
	public final int eventIdDb;
	public final int frame;
	public final EventType type;
	/** Used except for nukes */
	private final long unitIdDb;
	/** Used only for nukes */
	private final long buildTileId;
	
	public Event(int eventIdDb, int frame, int eventTypeId, long unitIdDb, long buildTileId) {
		this.eventIdDb = eventIdDb;
		this.frame = frame;
		this.type = EventType.getEventType(eventTypeId);
		this.unitIdDb = unitIdDb;
		this.buildTileId = buildTileId;
	}
	
	protected Event(ResultSet rs) throws SQLException {
		this(rs.getInt("eventId"), rs.getInt("frame"), rs.getInt("eventTypeId"),
				getLongCheckNull(rs, "unitId"), getLongCheckNull(rs, "buildTileId"));
	}
	
	private static long getLongCheckNull(ResultSet rs, String columnName) throws SQLException {
		long value = rs.getLong(columnName);
		return rs.wasNull() ? -1 : value;
	}
	
	/** Returns the unit involved in this event, or null if no unit was involved (nukes). */
	public Unit getUnit() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		if (unitIdDb == -1)
			return null;
		try (ResultSet rs = dbc.executeQuery(
				"SELECT * FROM unit WHERE unitId=?", unitIdDb) ) {
			if (rs.next()) {
				return new Unit(rs);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error retrieving unit for event " + eventIdDb, e);
		}
		LOGGER.severe("No unit found with id " + unitIdDb + " for event " + eventIdDb);
		return null;
	}
	
	/** Returns the build tile involved in this event (nukes only), or null otherwise. */
	public BuildTile getBuildTile() {
		if (buildTileId == -1)
			return null;
		try {
			return BuildTile.getBuildTileById(buildTileId);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to get buildTile for event " + eventIdDb, e);
			return null;
		}
	}

	@Override
	public int hashCode() {
		return eventIdDb;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Event other = (Event) obj;
		if (eventIdDb != other.eventIdDb)
			return false;
		return true;
	}
	
}
