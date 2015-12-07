package util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DbConnection implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(DbConnection.class.getName());

	/** Properties file to load */
	private static final String PROPERTIES_FILENAME = "databaseConfig.properties";
	/** Whether to prevent the program from performing changes to the DB (eg. insert, delete) */
	private final boolean debugMode;

	// Store most-recently used PreparedStatements to save on construction/garbage collection
	private final MRU<String, PreparedStatement> mru = new MRU<>(200);
	private Connection con = null;
	private boolean connected = false;
	private PreparedStatement getInsertId = null;
	
	public DbConnection() throws IOException, SQLException {
		Properties props = Util.loadProperties(PROPERTIES_FILENAME);
		String dbUrl = Util.getPropertyNotNull(props, "db_url");
		String dbName = Util.getPropertyNotNull(props, "db_name");
		String dbUser = Util.getPropertyNotNull(props, "db_user");
		String dbPw = Util.getPropertyNotNull(props, "db_pw");
		debugMode = Boolean.parseBoolean(Util.getPropertyNotNull(props, "db_debug_mode"));
		
		if (debugMode) {
			LOGGER.warning("DATABASE DEBUG MODE ACTIVE");
		}
		
		con = DriverManager.getConnection(dbUrl, dbUser, dbPw);
		Statement st = con.createStatement();
		// Switch to the chosen DB
		st.executeUpdate("USE " + dbName);
		// Ensure UTF8 is used (so Korean characters are handled correctly)
		st.executeUpdate("SET NAMES utf8");
		st.close();
		connected = true;
		getInsertId = prepare("SELECT last_insert_id()", null);
			
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	public ResultSet executeQuery(String sql, Object data) throws SQLException {
		List<Object> dataList = new ArrayList<>(1);
		dataList.add(data);
		return executeQuery(sql, dataList);
	}
	
	public ResultSet executeQuery(String sql, List<? extends Object> data) throws SQLException {
		PreparedStatement ps = prepare(sql, data);
		return ps.executeQuery();
	}
	
	/** Execute the query and return the value of the first column (usually the ID) as a long. */
	public long queryFirstColumn(String sql, List<? extends Object> data) throws SQLException {
		ResultSet rs = executeQuery(sql, data);
		if (rs.next()) {
			// Get the first column value. Better hope it's the ID!
			long id = rs.getLong(1);
			LOGGER.finer("Found existing id " + id);
			return id;
		}
		return -1;
	}
	
	/**
	 * @param sql the sql query
	 * @param data the items to update in the sql query in place of question marks
	 * @throws SQLException
	 */
	public void executeDelete(String sql, List<Object> data)
			throws SQLException {
		PreparedStatement ps = prepare(sql, data);
		if (debugMode) {
			LOGGER.finer("Debug mode: nothing deleted");
			return;
		}
		ps.executeUpdate();
	}
	
	/**
	 * @param sql the sql query
	 * @param data the items to update in the sql query in place of question marks
	 * @param findExisting whether to first check if the data already exists before updating
	 * @return insert id
	 * @throws SQLException
	 */
	public long executeUpdate(String sql, List<Object> data, boolean findExisting)
			throws SQLException {
		// Convert the UPDATE query into a SELECT query to find the ID
		String select = sql.toLowerCase().replaceAll("update (.*) set (.*) where (.*)",
				"SELECT * FROM $1 WHERE $2 AND $3");
		select = select.replace(",", " AND ");
		if (findExisting) {
			try {
				long firstCol = queryFirstColumn(select, data);
				if (firstCol != -1) {
					return firstCol;
				}
			} catch (SQLException e) {
				LOGGER.log(Level.WARNING, "Querying existing ID failed", e);
			}
		}
		PreparedStatement ps = prepare(sql, data);
		if (debugMode) {
			LOGGER.finer("Debug mode: returned -1 as update ID");
			return -1;
		}
		ps.executeUpdate();
		try {
			return queryFirstColumn(select, data);
		} catch (SQLException e) {
			LOGGER.log(Level.WARNING, "Querying existing ID failed", e);
		}
		return -1;
	}
	
	/**
	 * @param sql the sql query
	 * @param data the items to insert in the sql query in place of question marks
	 * @param findExisting whether to first check if the data already exists before insertion
	 * @return insert id
	 * @throws SQLException
	 */
	public long executeInsert(String sql, List<? extends Object> data, boolean findExisting)
			throws SQLException {
		if (findExisting) {
			// Convert the INSERT query into a SELECT query
			String query = sql.toLowerCase().replaceAll(
					"insert into (.*) \\((.*)\\) values \\((.*)\\)",
					"SELECT * FROM $1 WHERE <SPLIT>$2<SPLIT>$3");
			// Merge the parts originally in brackets
			String[] parts = query.split("<SPLIT>"); // Always 3 parts
			String[] intoParts = parts[1].split(",");
			String[] valuesParts = parts[2].split(",");
			List<String> joinedParts = new ArrayList<>();
			if (intoParts.length != valuesParts.length) {
				throw new SQLException("Different numbers of items found");
			}
			for (int i = 0; i < intoParts.length; i++) {
				joinedParts.add(intoParts[i].trim() + "=" + valuesParts[i].trim());
			}
			query = parts[0] + Util.join(" AND ", joinedParts);
			
			try {
				long firstCol = queryFirstColumn(query, data);
				if (firstCol != -1) {
					return firstCol;
				}
			} catch (SQLException e) {
				LOGGER.log(Level.WARNING, "Querying existing ID failed", e);
			}
		}
		PreparedStatement ps = prepare(sql, data);
		if (debugMode) {
			LOGGER.finer("Debug mode: returned -1 as insert ID");
			return -1;
		}
		ps.executeUpdate();
		ResultSet rs = getInsertId.executeQuery();
		if (rs.next()) {
			try {
				long id = rs.getLong(1);
				LOGGER.finer("Got new id " + id);
				return id;
			} catch (SQLException e) {
				LOGGER.log(Level.WARNING, "Getting insert ID failed", e);
			}
		}
		return -1;
	}
	
	/**
	 * Find and report extra (unexpected) values found in a specific table and column, for a
	 * specific condition.
	 * 
	 * @param maxNumToRemove the maximum number of unexpected values to remove. If there are more
	 * than this amount, will display a warning instead.
	 */
	public void findRemoveExtras(String column, String table, String condition, long conditionValue,
			Set<Long> expectedValues, int maxNumToRemove) throws SQLException {
		List<Object> data = new ArrayList<>();
		data.add(conditionValue);
		ResultSet rs = executeQuery("SELECT " + column + " FROM " + table +
				" WHERE " + condition, data);
		List<Long> extraValues = new ArrayList<>();
		while (rs.next()) {
			long value = rs.getLong(column);
			if (!expectedValues.contains(value)) {
				extraValues.add(value);
			}
		}
		if (extraValues.size() > 0) {
			if (extraValues.size() > maxNumToRemove) {
				LOGGER.warning("Found " + extraValues.size() + " extras in " + table 
						+ ". Too many! Not removed!");
				// Print only some of the extra values because there may be a lot.
				int i = extraValues.size() > 50 ? 50 : extraValues.size();
				LOGGER.info("First " + i + " extras: " + Util.join(extraValues.subList(0, i)));
			} else {
				LOGGER.info("Removing " + extraValues.size() + " extras from " + table);
				for (Long id : extraValues) {
					data.clear();
					data.add(id);
					executeDelete("DELETE FROM " + table + " WHERE " + column + "=?", data);
				}
			}
		}
	}

	/** Note: Do NOT close these manually, they will be closed automatically. */
	private PreparedStatement prepare(String sql, List<? extends Object> data)
			throws SQLException {
		if (!connected) {
			LOGGER.warning("connection closed then used!");
			throw new SQLException("Connection closed then used!");
		}
		PreparedStatement ps;
		if (mru.containsKey(sql)) {
			LOGGER.finest("Getting prepared statement from mru");
			ps = mru.get(sql);
		} else {
			ps = con.prepareStatement(sql);
			LOGGER.finest("Storing prepared statement in mru");
			mru.put(sql, ps);
		}
		ps.clearParameters();
		if (data != null) {
			for (int i = 0; i < data.size(); i++) {
				Object obj = data.get(i);
				if (obj == null) {
					ps.setNull(i + 1, Types.NULL); // Not sure if this will work
				} else if (obj.getClass() == Integer.class) {
					ps.setInt(i + 1, (int) obj);
				} else if (obj.getClass() == Long.class) {
					ps.setLong(i + 1, (long) obj);
				} else if (obj.getClass() == Float.class) {
					ps.setFloat(i + 1, (float) obj);
				} else if (obj.getClass() == Double.class) {
					ps.setDouble(i + 1, (double) obj);
				} else if (obj.getClass() == String.class) {
					ps.setString(i + 1, (String) obj);
				} else if (obj.getClass() == Boolean.class) {
					ps.setBoolean(i + 1, (boolean) obj);
				} else {
					LOGGER.warning("Processing as object: " + obj.getClass());
					ps.setObject(i + 1, obj);
				}
			}
		}
		if (sql.startsWith("SELECT")) {
			// Avoid converting the statement to a string if not going to be logged anyway
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Statement: " + ps.toString());
			}
		} else {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Statement: " + ps.toString());
			}
		}
		return ps;
	}
	
	@Override
	public void close() {
		try {
			if (con != null) {
				con.close();
			}
		} catch (SQLException ex) {
			LOGGER.warning("Exception while closing");
			LOGGER.log(Level.WARNING, ex.getMessage(), ex);
		}
		connected = false;
	}
	
	private static class MRU<K, V extends AutoCloseable> extends LinkedHashMap<K, V> {
		private static final long serialVersionUID = 1L;
		private static final float DEFAULT_LOAD_FACTOR = 0.75f;
		private final int maxEntries;
		
		public MRU(int maxEntries) {
			super((int) (maxEntries / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_LOAD_FACTOR, true);
			this.maxEntries = maxEntries;
		}
		
		@Override
		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			if (size() > maxEntries) {
				remove(eldest.getKey());
				try {
					eldest.getValue().close();
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Failed to close statement", e);
				}
			}
			return false;
		}
	}
	
}
