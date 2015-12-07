package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Unlike other objects in the DB interface, resources are measured at a certain time, so are
 * compared based on value, not identity. UnitAttributes is also compared on value (but is not
 * really part of the DB interface). In both cases, the frame they are from is ignored.
 */
public class Resources {
	public final int frame;
	public final int minerals;
	public final int gas;
	public final int supply;
	public final int totalMinerals;
	public final int totalGas;
	public final int totalSupply;
	
	public Resources(int frame, int minerals, int gas, int supply, int totalMinerals, int totalGas,
			int totalSupply) {
		this.frame = frame;
		this.minerals = minerals;
		this.gas = gas;
		this.supply = supply;
		this.totalMinerals = totalMinerals;
		this.totalGas = totalGas;
		this.totalSupply = totalSupply;
	}
	
	public Resources(ResultSet rs) throws SQLException {
		this(rs.getInt("frame"), rs.getInt("minerals"), rs.getInt("gas"), rs.getInt("supply"),
				rs.getInt("totalMinerals"), rs.getInt("totalGas"), rs.getInt("totalSupply"));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + gas;
		result = prime * result + minerals;
		result = prime * result + supply;
		result = prime * result + totalGas;
		result = prime * result + totalMinerals;
		result = prime * result + totalSupply;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Resources other = (Resources) obj;
		if (gas != other.gas)
			return false;
		if (minerals != other.minerals)
			return false;
		if (supply != other.supply)
			return false;
		if (totalGas != other.totalGas)
			return false;
		if (totalMinerals != other.totalMinerals)
			return false;
		if (totalSupply != other.totalSupply)
			return false;
		return true;
	}
	
}
