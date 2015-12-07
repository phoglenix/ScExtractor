package scdb;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import util.DbConnection;

/**
 * Provide a nice interface for reading from the database. Note that items are not cached at all, so
 * if you get objects from multiple calls they will not be references to the same objects even if
 * they represent the same thing, but they can be compared correctly using equals.
 * 
 * @author Glen Robertson
 * 
 */
public class DbInterface {
	private static final Logger LOGGER = Logger.getLogger(DbInterface.class.getName());
	
	private static final DbInterface instance = new DbInterface();
	
	public static DbInterface getInstance() {
		return instance;
	}
	
	private final DbConnection dbc;
	
	/** Never actually throws the exception, will exit() instead */
	private DbInterface() {
		DbConnection dbc = null;
		try {
			dbc = new DbConnection();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Problem loading properties file", e);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Problem connecting to database", e);
		}
		if (dbc == null) {
			System.exit(1);
		}
		this.dbc = dbc;
		try {
			OfflineJNIBWAPI.loadOfflineJNIBWAPIData();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Unable to load complete BWAPI type data", e);
		}
	}
	
	public DbConnection getDbc() {
		return dbc;
	}

}
