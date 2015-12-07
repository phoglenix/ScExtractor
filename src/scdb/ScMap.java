package scdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.annotation.XmlID;

import util.DbConnection;

public class ScMap {
	private static final Logger LOGGER = Logger.getLogger(ScMap.class.getName());

	public final int dbMapId;
	public final String mapName;
	public final int numStartPos;
	public final int xSize;
	public final int ySize;
	
	public ScMap(int dbMapId, String mapName, int numStartPos, int xSize, int ySize) {
		this.dbMapId = dbMapId;
		this.mapName = mapName;
		this.numStartPos = numStartPos;
		this.xSize = xSize;
		this.ySize = ySize;
	}
	
	public BuildTile[][] getBuildTiles() {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		BuildTile[][] tiles = new BuildTile[xSize][ySize];
		
		try (ResultSet rs = dbc.executeQuery("SELECT * FROM buildTile WHERE mapId=?", dbMapId); ) {
			while (rs.next()) {
				int x = rs.getInt("BTilePosX");
				int y = rs.getInt("BTilePosY");
				tiles[x][y] = new BuildTile(rs);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Failed to get build tiles on map " + dbMapId, e);
		}
		return tiles;
	}
	
	public BuildTile getBuildTile(int bTilePosX, int bTilePosY) throws SQLException {
		DbConnection dbc = DbInterface.getInstance().getDbc();
		List<Object> data = new ArrayList<>();
		data.add(dbMapId);
		data.add(bTilePosX);
		data.add(bTilePosY);
		
		ResultSet rs = dbc.executeQuery(
				"SELECT * FROM buildTile WHERE mapId=? AND BTilePosX=? AND BTilePosY=?", data);
		if (rs.next()) {
			return new BuildTile(rs);
		} else {
			throw new SQLException("No such build tile found: " + bTilePosX + "," + bTilePosY);
		}
	}
	
	@XmlID
	public String getXmlId() {
		return String.valueOf(dbMapId);
	}

	@Override
	public int hashCode() {
		return dbMapId;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScMap other = (ScMap) obj;
		if (dbMapId != other.dbMapId)
			return false;
		return true;
	}
	
}
