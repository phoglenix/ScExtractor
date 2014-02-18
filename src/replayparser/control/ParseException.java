package replayparser.control;

/**
 * Refers to an error parsing a replay text.
 * 
 * @author Andras Belicza
 */
public class ParseException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new ParseException.<br>
	 * This constructor should be used if the parser fails to read the source.
	 */
	public ParseException() {
		super( "Error reading the source." );
	}
	
	/**
	 * Creates a new ParseException.<br>
	 * This constructor should be used if the source contains invalid content.
	 * @param line line where the parse failed
	 */
	public ParseException( final int line ) {
		super( "Parse error in line " + line + "." );
	}
	
}
