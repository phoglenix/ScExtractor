package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import jnibwapi.types.EventType;
import jnibwapi.types.OrderType.OrderTypes;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;
import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;

/**
 * Hacky temp interoperation class so I can make events look like actions to cover for the fact that
 * actions are spammed by players and this makes pattern finding difficult.
 */
public class ActionyEvent extends Action {
	private static final Logger LOGGER = Logger.getLogger(ActionyEvent.class.getName());
	
	public final long eventIdDb;
	public final long unitIdDb;
	
	public ActionyEvent(long eventIdDb, long playerReplayIdDb, int frame, int unitTypeId,
			long unitIdDb) {
		// -1 set for actionIdDb, unitGroupId, targetX, targetY
		// delayed always false; ordertype always none
		super(-1, playerReplayIdDb, frame, getUnitCommandTypeId(unitTypeId),
				OrderTypes.None.getID(), -1, unitTypeId, -1, -1, false);
		this.eventIdDb = eventIdDb;
		this.unitIdDb = unitIdDb;
	}
	
	public ActionyEvent(ResultSet rs) throws SQLException {
		this(rs.getLong("eventId"), rs.getLong("playerReplayId"), rs.getInt("frame"), 
				getMorphedUnitTypeId(rs.getInt("unitTypeId"), rs.getInt("eventTypeId")),
				rs.getInt("unitId"));
	}
	
	/** Figure out based on unitTypeId */
	private static int getUnitCommandTypeId(int unitTypeId) {
		UnitType u = UnitTypes.getUnitType(unitTypeId);
		if (u.isBuilding() || u.isAddon()) {
			return UnitCommandTypes.Build.getID();
		} else {
			return UnitCommandTypes.Train.getID();
		}
	}
	
	/**
	 * eventtype 14 == archon warp (for protoss at least)<br>
	 * ==> train archon (if unittype=67)<br>
	 * ==> train dark archon (if unittype=61)
	 */
	private static int getMorphedUnitTypeId(int unitTypeId, int eventTypeId) {
		if (eventTypeId == EventType.UnitMorph.getID()) {
			if (unitTypeId == UnitTypes.Protoss_High_Templar.getID()) {
				return UnitTypes.Protoss_Archon.getID();
			} else if (unitTypeId == UnitTypes.Protoss_Dark_Templar.getID()) {
				return UnitTypes.Protoss_Dark_Archon.getID();
//			} else if (unitTypeId == UnitTypes.Resource_Vespene_Geyser.getID()) {
//				return UnitTypes.Protoss_Assimilator.getID();
			} else {
				LOGGER.warning("Unexpected unit morph: " + unitTypeId);
			}
		}
		return unitTypeId;
	}
	
	@Override
	public int hashCode() {
		return (int) (eventIdDb ^ (eventIdDb >>> 32));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ActionyEvent other = (ActionyEvent) obj;
		if (eventIdDb != other.eventIdDb)
			return false;
		return true;
	}
	
	
}
