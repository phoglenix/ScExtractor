package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jnibwapi.types.*;
import jnibwapi.types.OrderType.OrderTypes;
import jnibwapi.types.TechType.TechTypes;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;
import jnibwapi.types.UnitType.UnitTypes;
import jnibwapi.types.UpgradeType.UpgradeTypes;
import util.DbConnection;

public class Action {
	private static final Logger LOGGER = Logger.getLogger(Action.class.getName());
	
	public final long actionIdDb;
	public final long playerReplayIdDb;
	public final int frame;
	public final UnitCommandType unitCommandType;
	public final OrderType orderType;
	public final long unitGroupId;
	public final int targetId;
	/** Pixel coordinates for movement, BT coordinates for Build */
	public final int targetX;
	/** Pixel coordinates for movement, BT coordinates for Build */
	public final int targetY;
	public final boolean delayed;
	
	public Action(long actionIdDb, long playerReplayIdDb, int frame, int unitCommandTypeId,
			int orderTypeId, long unitGroupId, int targetId, int targetX, int targetY,
			boolean delayed) {
		this.actionIdDb = actionIdDb;
		this.playerReplayIdDb = playerReplayIdDb;
		this.frame = frame;
		this.unitCommandType = UnitCommandTypes.getUnitCommandType(unitCommandTypeId);
		this.orderType = OrderTypes.getOrderType(orderTypeId);
		if (unitCommandType == null || orderType == null) {
			LOGGER.warning(String.format("Invalid unitCommandType %d or OrderType %d in frame %d",
					unitCommandTypeId, orderTypeId, frame));
		}
		if (unitCommandType == UnitCommandTypes.None && orderType == OrderTypes.None) {
			LOGGER.warning(String.format("UnitCommandType and OrderType were both None in frame "
					+ frame));
		}
		this.unitGroupId = unitGroupId;
		this.targetId = targetId;
		this.targetX = targetX;
		this.targetY = targetY;
		this.delayed = delayed;
	}
	
	protected Action(ResultSet rs) throws SQLException {
		this(rs.getLong("actionId"), rs.getLong("playerReplayId"), rs.getInt("frame"),
				rs.getInt("unitCommandTypeId"), rs.getInt("orderTypeId"), rs.getLong("unitGroupId"),
				rs.getInt("targetId"), rs.getInt("targetX"), rs.getInt("targetY"),
				rs.getBoolean("delayed"));
	}
	
	/** Get the PlayerReplay that issued this Action */
	public PlayerReplay getPlayerReplay() throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		
		ResultSet rs = dbc.executeQuery("SELECT * FROM playerreplay WHERE playerReplayId=?",
				playerReplayIdDb);
		if (rs.next()) {
			return new PlayerReplay(rs);
		} else {
			throw new SQLException("No playerReplay found for action " + actionIdDb);
		}
	}
	
	/** The unit(s) that the action was issued to. */
	public List<Unit> getUnitGroup() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Unit> units = new ArrayList<>();
		try (ResultSet rs = dbc.executeQuery("SELECT * FROM unit NATURAL JOIN unitgroup "
				+ "WHERE UnitGroupID=?", unitGroupId); ) {
			while (rs.next()) {
				units.add(new Unit(rs));
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting unitGroup " + unitGroupId, e);
		}
		/*
		 * Some units in the group may no longer exist (because this info isn't known when replays
		 * are being parsed) so filter out any that don't exist in this frame.
		 */
		return units.stream().filter(u -> u.isExisting(frame)).collect(Collectors.toList());
	}
	
	/** Returns null for non-Build actions. */
	public BuildTile getBuildTile(ScMap map) {
		if (unitCommandType != UnitCommandTypes.Build || targetX == -1 || targetY == -1) {
			return null;
		}
		try {
			return map.getBuildTile(targetX, targetY);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting buildTile for action " + actionIdDb, e);
			return null;
		}
	}
	
	/** Currently only for strategic unit command actions */
	public String getTargetIdAsString() {
		if (!isStrategicUnitCommandType())
			throw new IllegalArgumentException("Only for strategic UCTs");
		if (unitCommandType == UnitCommandTypes.Build
				|| unitCommandType == UnitCommandTypes.Build_Addon // Never used in dataset
				|| unitCommandType == UnitCommandTypes.Morph
				|| unitCommandType == UnitCommandTypes.Train) {
			return UnitTypes.getUnitType(targetId).getName();
		}
		if (unitCommandType == UnitCommandTypes.Research) {
			return TechTypes.getTechType(targetId).getName();
		}
		if (unitCommandType == UnitCommandTypes.Upgrade) {
			return UpgradeTypes.getUpgradeType(targetId).getName();
		}
		// Else it's a cancel, so targetID is just a slot number (may be negative)
		return "slot" + targetId;
	}
	
	
	
	public boolean isStrategicUnitCommandType() {
		// Main ones from (PvP) DB are Train, Build, Upgrade
		return unitCommandType == UnitCommandTypes.Build
				|| unitCommandType == UnitCommandTypes.Build_Addon
				|| unitCommandType == UnitCommandTypes.Cancel_Addon
				|| unitCommandType == UnitCommandTypes.Cancel_Construction
				|| unitCommandType == UnitCommandTypes.Cancel_Morph
				|| unitCommandType == UnitCommandTypes.Cancel_Research
				|| unitCommandType == UnitCommandTypes.Cancel_Train
				|| unitCommandType == UnitCommandTypes.Cancel_Train_Slot
				|| unitCommandType == UnitCommandTypes.Cancel_Upgrade
				|| unitCommandType == UnitCommandTypes.Morph
				|| unitCommandType == UnitCommandTypes.Research
				|| unitCommandType == UnitCommandTypes.Train
				|| unitCommandType == UnitCommandTypes.Upgrade;
	}
	
	public boolean isStrategicOrderType() {
		// Only one from (PvP) DB is TrainFighter. Maybe don't use this for now.
		return orderType == OrderTypes.BuildAddon
				|| orderType == OrderTypes.BuildNydusExit
				|| orderType == OrderTypes.BuildProtoss2
				|| orderType == OrderTypes.IncompleteBuilding
				|| orderType == OrderTypes.PlaceAddon
				|| orderType == OrderTypes.PlaceBuilding
				|| orderType == OrderTypes.ResearchTech
				|| orderType == OrderTypes.TrainFighter;
	}

	@Override
	public int hashCode() {
		return (int) (actionIdDb ^ (actionIdDb >>> 32));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Action other = (Action) obj;
		if (actionIdDb != other.actionIdDb)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "Action{" + unitCommandType + ", " + orderType + ", " + targetId + ", " + actionIdDb
				+ ", " + frame + "}";
	}
}
