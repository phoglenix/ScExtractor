package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public class Util {
	/** Joins each item in the Iterable, separated by sep, into a String */
	public static String join(String sep, Iterable<?> it) {
		StringBuilder sb = new StringBuilder();
		for (Object o : it) {
			sb.append(o);
			sb.append(sep);
		}
		if (sb.length() >= sep.length()) {
			sb.delete(sb.length() - sep.length(), sb.length());
		}
		return sb.toString();
	}
	
	/**
	 * Convenience method: calls {@link #join(String, Iterable)} with {@code ", "} as the separator
	 */
	public static String join(Iterable<?> it) {
		return join(", ", it);
	}
	
	/**
	 * Loads the file from the disc as an ordered set of strings. File is expected to be
	 * newline-separated entries as created by {@link #saveSetToDisc(Set, String)}.
	 */
	public static Set<String> loadSetFromDisc(String filename) throws IOException {
		Set<String> set = new LinkedHashSet<>();
		File file = new File(filename);
		if (!file.canRead()) {
			System.out.println("loadSetFromDisc: File not readable: " + file.getAbsolutePath()
					+ ". Creating it.");
			file.createNewFile();
			return set;
		}
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line;
		while ((line = br.readLine()) != null) {
			set.add(line);
		}
		br.close();
		return set;
	}
	
	/**
	 * Saves the set of strings to disc as newline-separated entries. Entries can be read again
	 * using {@link #loadSetFromDisc(String)}.
	 */
	public static void saveSetToDisc(Set<String> set, String filename) throws IOException {
		File file = new File(filename);
		file.createNewFile();
		if (!file.canWrite()) {
			throw new IOException("Cannot write to file: " + file.getAbsolutePath());
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		for (String s : set) {
			bw.write(s);
			bw.newLine();
		}
		bw.close();
	}
	
	/** Loads the given properties file from disc. */
	public static Properties loadProperties(String filename) throws IOException {
		try (FileInputStream fis = new FileInputStream(filename)) {
			Properties prop = new Properties();
			prop.load(fis);
			return prop;
		}
	}
	
	/**
	 * Gets the named property from the given properties object. Never returns null. Throws a
	 * RuntimeException if the property is not found.
	 */
	public static String getPropertyNotNull(Properties props, String property) {
		String retVal = props.getProperty(property);
		if (retVal == null) {
			throw new RuntimeException("Property '" + property + "' not found in properties");
		}
		return retVal;
	}
	
}
