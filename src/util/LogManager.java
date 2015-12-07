package util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogManager {
	// Manage the top level logger
	private static final Logger LOGGER = Logger.getLogger("");
	private static final int MAX_LOG_SIZE_BYTES = 1024 * 1024 * 100; // 100 MB
	private static final int MAX_NUM_LOG_FILES = 500;
	
	public static void initialise(String logName) {
		initialise(logName, Level.FINE);
	}
	
	public static void initialise(String logName, Level fileLevel) {
		// Initialise the logger
		FileHandler fh;
		try {
			String fullLogName = "logs/" + logName + "%u.%g.log";
			fh = new FileHandler(fullLogName, MAX_LOG_SIZE_BYTES, MAX_NUM_LOG_FILES);
			fh.setEncoding("UTF-8");
			fh.setFormatter(new SimpleFormatter());
			fh.setLevel(fileLevel);
			LOGGER.setLevel(Level.ALL);
			LOGGER.addHandler(fh);
		} catch (SecurityException e) {
			System.err.println("Error initialising Logger: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Error initialising Logger: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
}
