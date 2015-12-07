package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import util.DbConnection;

public class BuildTile {
	private static final Logger LOGGER = Logger.getLogger(BuildTile.class.getName());
	
	public static enum GroundHeight {
		Low,
		Low_Doodads,
		High,
		High_Doodads,
		Very_High,
		Very_High_Doodads;
	}
	
	public final long buildTileIdDb;
	public final GroundHeight groundHeight;
	public final boolean buildable;
	/** 16 binary values, ordering is in columns, not rows */
	public final int walkable;
	public final int chokeDist;
	public final int baseLocationDist;
	public final int startLocationDist;
	public final int regionId;
	
	public BuildTile(long buildTileIdDb, int groundHeightId, boolean buildable, int walkable,
			int chokeDist, int baseLocationDist, int startLocationDist, int regionId) {
		this.buildTileIdDb = buildTileIdDb;
		this.groundHeight = GroundHeight.values()[groundHeightId];
		this.buildable = buildable;
		this.walkable = walkable;
		this.chokeDist = chokeDist;
		this.baseLocationDist = baseLocationDist;
		this.startLocationDist = startLocationDist;
		this.regionId = regionId;
	}
	
	protected BuildTile(ResultSet rs) throws SQLException {
		this(rs.getLong("BuildTileID"), rs.getInt("GroundHeightID"), rs.getBoolean("Buildable"),
				rs.getInt("Walkable"), rs.getInt("ChokeDist"), rs.getInt("BaseLocationDist"),
				rs.getInt("StartLocationDist"), rs.getInt("RegionID"));
	}
	
	/** Gets the build tile with the given ID or throws an exception. */
	public static BuildTile getBuildTileById(long buildTileId)
			throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		try (ResultSet rs = dbc.executeQuery(
				"SELECT * FROM buildTile WHERE buildTileId=?", buildTileId) ) {
			if (rs.next()) {
				return new BuildTile(rs);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Error getting buildtile with id " + buildTileId, e);
		}
		throw new SQLException("No buildTile found with id " + buildTileId);
	}
	
	@Override
	public int hashCode() {
		return (int) (buildTileIdDb ^ (buildTileIdDb >>> 32));
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BuildTile other = (BuildTile) obj;
		if (buildTileIdDb != other.buildTileIdDb)
			return false;
		return true;
	}
}
