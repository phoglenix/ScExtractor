package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;
import util.DbConnection;
import util.UnitAttributes;
import util.UnitAttributes.UnitAttribute;

public class Unit {
	private static final Logger LOGGER = Logger.getLogger(Unit.class.getName());
	
	public final long unitIdDb;
	public final UnitType unitType; 
	
	public Unit(long unitIdDb, int unitTypeId) {
		this.unitIdDb = unitIdDb;
		this.unitType = UnitTypes.getUnitType(unitTypeId);
	}
	
	protected Unit(ResultSet rs) throws SQLException {
		this(rs.getLong("unitId"), rs.getInt("unitTypeId"));
	}
	
	public UnitType getType() {
		return unitType;
	}
	
	/**
	 * Get the latest value for an attribute up to and including the given frame. If no attribute
	 * changes were found, returns 0. See also {@link #getAttributes(int)} and
	 * {@link #isExisting(int)}
	 */
	public int getAttribute(int frame, UnitAttribute attribute) {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		int attributeValue = 0;
		
		List<Object> data = new ArrayList<>();
		data.add(unitIdDb);
		data.add(attribute.getId());
		data.add(frame);
		try (ResultSet rs = dbc.executeQuery("SELECT changeVal FROM attributeChange "
				+ "WHERE unitId=? AND attributeTypeId=? AND changeTime<=? "
				+ "ORDER BY changeTime DESC LIMIT 1", data) ) {
			if (rs.next()) {
				attributeValue = rs.getInt(1);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting attribute " + attribute.getId()
					+ " in frame " + frame, e);
		}
		return attributeValue;
	}
	
	/** Whether the unit exists in a certain frame, for convenience. */
	public boolean isExisting(int frame) {
		return getAttribute(frame, UnitAttribute.Exists) == 1;
	}
	
	/** Get all attributes of this unit up to a particular frame. */
	public UnitAttributes getAttributes(int frame) {
		int[] attributes = new int[UnitAttributes.NUM_ATTRIBUTES];
		for (UnitAttribute attribute : UnitAttribute.values()) {
			attributes[attribute.getId()] = getAttribute(frame, attribute);
		}
		return new UnitAttributes(attributes);
	}
	
	/**
	 * Whether the unit was visible to the given player in the given frame.
	 * 
	 * @see {@link #visibleToBy(PlayerReplay, int)}
	 */
	public boolean visibleTo(PlayerReplay p, int frame) {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		boolean visible = false;
		
		List<Object> data = new ArrayList<>();
		data.add(p.playerReplayIdDb);
		data.add(unitIdDb);
		data.add(frame);
		
		try (ResultSet rs = dbc.executeQuery("SELECT ChangeVal FROM visibilitychange "
				+ "WHERE ViewerID=? AND UnitID=? AND ChangeTime<=? "
				+ "ORDER BY ChangeTime DESC LIMIT 1", data) ) {
			if (rs.next()) {
				visible = rs.getBoolean(1);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting visibility for player " + p.playerReplayIdDb
					+ " and unit " + unitIdDb + " in frame " + frame, e);
		}
		return visible;
	}
	
	/**
	 * Whether the unit was *ever* visible to the given player, before or in the given frame.
	 * 
	 * @see {@link #visibleTo(PlayerReplay, int)}
	 */
	public boolean visibleToBy(PlayerReplay p, int frame) {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		boolean visible = false;
		
		List<Object> data = new ArrayList<>();
		data.add(p.playerReplayIdDb);
		data.add(unitIdDb);
		data.add(frame);
		
		try (ResultSet rs = dbc.executeQuery("SELECT MAX(ChangeVal) FROM visibilitychange "
				+ "WHERE ViewerID=? AND UnitID=? AND ChangeTime<=?", data) ) {
			if (rs.next()) {
				visible = rs.getBoolean(1);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting visibility for player " + p.playerReplayIdDb
					+ " and unit " + unitIdDb + " in frame " + frame, e);
		}
		return visible;
	}
	
	@Override
	public int hashCode() {
		return (int) (unitIdDb ^ (unitIdDb >>> 32));
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Unit other = (Unit) obj;
		if (unitIdDb != other.unitIdDb)
			return false;
		return true;
	}
}
