package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	
	/** Joins each item in the Stream, separated by sep, into a String */
	public static String join(String sep, Stream<?> s) {
		return s.map(Object::toString).collect(Collectors.joining(", "));
	}
	
	/**
	 * Convenience method: calls {@link #join(String, Stream)} with {@code ", "} as the separator
	 */
	public static String join(Stream<?> s) {
		return s.map(Object::toString).collect(Collectors.joining(", "));
	}
	
	/** Check if <code>line.startsWith(start)</code> ignoring case. */
	public static boolean startsWithIgnoreCase(String line, String start) {
		return line.length() >= start.length()
				&& line.substring(0, start.length()).equalsIgnoreCase(start);
	}
	
	public static  Collector<Object, ?, Integer> countingInt() {
		return Collectors.reducing(0, elt -> 1, Integer::sum);
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
	
	/**
	 * Returns a view of the last N items of input list, or fewer if there are fewer than N items in
	 * the list.
	 */
	public static<T> List<T> lastN(List<T> list, int n) {
		return list.subList(Math.max(0, list.size() - n), list.size());
	}
	
	/**
	 * Returns a substring of the last N characters of the input string, or fewer if there are fewer
	 * than N characters in the string.
	 */
	public static String lastN(String s, int n) {
		return s.substring(Math.max(0, s.length() - n), s.length());
	}
	
	/** A pair of items. Immutable. */
	public static class Pair<F, S> {
		public final F first;
		public final S second;
		
		public Pair(F first, S second) {
			this.first = first;
			this.second = second;
		}
		
		@Override
		public String toString() {
			return "Pair:" + String.valueOf(first) + "," + String.valueOf(second);
		}
	}
	
	/** A triple of items. Immutable. */
	public static class Triple<F, S, T> {
		public final F first;
		public final S second;
		public final T third;
		
		public Triple(F first, S second, T third) {
			this.first = first;
			this.second = second;
			this.third = third;
		}
	}
}
