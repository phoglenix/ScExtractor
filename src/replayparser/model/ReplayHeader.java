package replayparser.model;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Class modeling the header of a replay.
 * 
 * @author Andras Belicza
 */
public class ReplayHeader {
	
	// Constants for the header field values
	
	public static final byte GAME_ENGINE_STARCRAFT = (byte) 0x00;
	public static final byte GAME_ENGINE_BROODWAR  = (byte) 0x01;
	
	public static final String[] GAME_ENGINE_NAMES = {
		"Starcraft", "Broodwar"
	};
	
	public static final String[] GAME_ENGINE_SHORT_NAMES = {
		"SC", "BW"
	};
	
	public static final byte RACE_ZERG    = (byte) 0x00;
	public static final byte RACE_TERRAN  = (byte) 0x01;
	public static final byte RACE_PROTOSS = (byte) 0x02;
	
	public static final int RACE_ZERG_INITUNITS_COUNT    = 9;
	public static final int INIT_UNITS_COUNT  			 = 5;
	public static final int INIT_WORKER_COUNT  			 = 4;
	public static final int INIT_LARVA_COUNT  			 = 3;
	
	public static final String[] RACE_NAMES = {
		"Zerg", "Terran", "Protoss"
	};
	
	public static final char[] RACE_CHARACTERS = { 'Z', 'T', 'P' };
	
	public static final String[] IN_GAME_COLOR_NAMES = {
		"red", "blue", "teal", "purple", "orange", "brown", "white", "yellow",
		"green", "pale yellow", "tan", "aqua", "pale green", "blueish gray", "pale yellow", "cyan"
	};
	
	public static final String[] GAME_TYPE_NAMES = {
		null, null, "Melee", "Free for all", "One on one", "Capture the flag", "Greed", "Slaughter",
		null, "Sudden death", "Use map settings", "Team melee", "Team free for all",
		"Team capture the flag", null, "Top vs bottom"
	};
	
	public static final String[] GAME_TYPE_SHORT_NAMES = {
		null, null, "Melee", "FFA", "1vs1", "CTF", "Greed", "Slaughter",
		null, "Sudden death", "UMS", "Team melee", "Team FFA",
		"Team CTF", null, "TvB"
	};
	
	public static final short GAME_TYPE_MELEE         = 0x02;
	public static final short GAME_TYPE_FFA           = 0x03; // Free for all
	public static final short GAME_TYPE_ONE_ON_ONE    = 0x04;
	public static final short GAME_TYPE_CTF           = 0x05; // Capture the flag
	public static final short GAME_TYPE_GREED         = 0x06;
	public static final short GAME_TYPE_SLAUGHTER     = 0x07;
	public static final short GAME_TYPE_SUDDEN_DEATH  = 0x08;
	public static final short GAME_TYPE_UMS           = 0x0a; // Use map settings
	public static final short GAME_TYPE_TEAM_MELEE    = 0x0b;
	public static final short GAME_TYPE_TEAM_FFA      = 0x0c;
	public static final short GAME_TYPE_TEAM_CTF      = 0x0d;
	public static final short GAME_TYPE_TVB           = 0x0f;
	
	public static final int FRAMES_IN_TWO_MINUTES = 120 * 1000 / 42;
	
	// Header fields
	
	public byte     gameEngine;
	public int      gameFrames;
	public Date     saveTime;
	public String   gameName;
	public short    mapWidth;
	public short    mapHeight;
	public short    gameSpeed;
	public short    gameType;
	public short    gameSubType;      // If 3vs5 this is 3, if 7vs1 this is 7
	public String   creatorName;
	public String   mapName;
	public byte[]   playerRecords     = new byte[ 432 ]; // 12 player records, 12*36 bytes
	public int[]    playerColors      = new int[ 8 ]; // Player spot color index (ABGR?)
	public byte[]   playerSpotIndices = new byte[ 8 ]; 
	
	// Derived data from player records:
	public String[] playerNames       = new String[ 12 ];
	public byte[]   playerRaces       = new byte[ 12 ];
	public int[]    playerIds         = new int[ 12 ];
	
	// Calculated data when parsing the replay
	public int[]    playerIdActionsCounts           = new int[ 12 ];
	public int[]    playerIdLastActionFrame         = new int[ 12 ];
	public int[]    playerIdActionsCountBefore2Mins = new int[ 12 ]; // Actions count before 2 minutes 
	
	/**
	 * Converts the specified amount of frames to seconds.
	 * @return the specified amount of frames in seconds
	 */
	public static int convertFramesToSeconds( final int frames ) {
		return (int) (frames * 42l / 1000l); // Might overflow at around 600 hours (when calculating total times from many games); this works up to about 24000 hours
	}
	
	/**
	 * Converts the specified amount of frames given in long to seconds.
	 * @return the specified amount of frames in seconds
	 */
	public static long convertLongFramesToSeconds( final long frames ) {
		return frames * 42l / 1000l;
	}
	
	/**
	 * Converts the specified amount of seconds to frames.
	 * @return the specified amount of seconds in frames
	 */
	public static int convertSecondsToFrames( final int seconds ) {
		return (int) (seconds * 1000l / 42l); // Might overflow at around 600 hours (when calculating total times from many games); this works up to about 24000 hours
	}
	
	/**
	 * Returns the game duration in seconds.
	 * @return the game duration in seconds
	 */
	public int getDurationSeconds() {
		return convertFramesToSeconds( gameFrames );
	}
	
	/**
	 * Converts the specified amount of frames to a human friendly time format.
	 * @param frames amount of frames to be formatted
	 * @param formatBuilder builder to be used to append the output to
	 * @param longFormat tells if the required format is "hh:mm:ss" (no matter how short the game is)
	 * @return the format builder used to append the result
	 */
	public static StringBuilder formatFrames( final int frames, final StringBuilder formatBuilder, final boolean longFormat ) {
		int seconds = convertFramesToSeconds( frames );
		
		final int hours = seconds / 3600;
		if ( longFormat || hours > 0 ) {
			if ( longFormat && hours < 10 )
				formatBuilder.append( 0 );
			formatBuilder.append( hours ).append( ':' );
		}
		
		seconds %= 3600;
		final int minutes = seconds / 60;
		if ( ( longFormat || hours > 0 ) && minutes < 10 )
			formatBuilder.append( 0 );
		formatBuilder.append( minutes ).append( ':' );
		
		seconds %= 60;
		if ( seconds < 10 )
			formatBuilder.append( 0 );
		formatBuilder.append( seconds );
		
		return formatBuilder;
	}
	
	/**
	 * Converts the specified amount of frames given in long to a human friendly time format.
	 * @param frames amount of frames to be formatted
	 * @param formatBuilder builder to be used to append the output to
	 * @param longFormat tells if the required format is "hh:mm:ss" (no matter how short the game is)
	 * @return the format builder used to append the result
	 */
	public static StringBuilder formatLongFrames( final long frames, final StringBuilder formatBuilder, final boolean longFormat ) {
		long seconds = convertLongFramesToSeconds( frames );
		
		final long hours = seconds / 3600;
		if ( longFormat || hours > 0 ) {
			if ( longFormat && hours < 10 )
				formatBuilder.append( 0 );
			formatBuilder.append( hours ).append( ':' );
		}
		
		seconds %= 3600;
		final long minutes = seconds / 60;
		if ( ( longFormat || hours > 0 ) && minutes < 10 )
			formatBuilder.append( 0 );
		formatBuilder.append( minutes ).append( ':' );
		
		seconds %= 60;
		if ( seconds < 10 )
			formatBuilder.append( 0 );
		formatBuilder.append( seconds );
		
		return formatBuilder;
	}
	
	/**
	 * Returns the duration as a human friendly string.
	 * @return the duration as a human friendly string
	 */
	public String getDurationString( final boolean longFormat ) {
		return formatFrames( gameFrames, new StringBuilder(), longFormat ).toString();
	}
	
	/**
	 * Returns the game engine as a string.<br>
	 * This is either the string <code>"Starcraft"</code> or <code>"Broodwar"</code>.
	 * @return the game engine as a string
	 */
	public String getGameEngineString() {
		return gameEngine == GAME_ENGINE_BROODWAR ? "Broodwar" : "Starcraft";
	}
	
	/**
	 * Returns the map size as a string.
	 * @return the map size as a string
	 */
	public String getMapSize() {
		return mapWidth + "x" + mapHeight;
	}
	
	/**
	 * Returns the string listing the player names (comma separated).
	 * @return the string listing the player names (comma separated)
	 */
	public String getPlayerNamesString() {
		final StringBuilder playerNamesBuilder = new StringBuilder();
		
		for ( final String playerName : playerNames )
			if ( playerName != null ) {
				if ( playerNamesBuilder.length() > 0 )
					playerNamesBuilder.append( ", " );
				playerNamesBuilder.append( playerName );
			}
		
		return playerNamesBuilder.toString();
	}
	
	/**
	 * Returns the description of a player specified by his/her name.<br>
	 * The description contains the name, race and APM of the player in the following format:<br>
	 * <code>player_name (R), actions: xxx, APM: yyy</code>
	 * @param playerName name of the player being queried
	 * @return the description of the player
	 */
	public String getPlayerDescription( final String playerName ) {
		final int playerIndex = getPlayerIndexByName( playerName );
		if ( playerIndex < 0 )
			return null;
		
		final int actionsCount = playerIds[ playerIndex ] < playerIdActionsCounts.length ? playerIdActionsCounts[ playerIds[ playerIndex ] ] : 0;
		return playerNames[ playerIndex ] + " (" + RACE_CHARACTERS[ playerRaces[ playerIndex ] ] + "), actions: " + actionsCount + ", APM: " + getPlayerApm( playerIndex );
	}
	
	/**
	 * Returns the index of a player specified by his/her name.
	 * @param playerName name of player to be searched
	 * @return the index of a player specified by his/her name; or -1 if player name not found
	 */
	public int getPlayerIndexByName( final String playerName ) {
		for ( int i = 0; i < playerNames.length; i++ )
			if ( playerNames[ i ] != null && playerNames[ i ].equals( playerName ) )
				return i;
		return -1;
	}
	
	/**
	 * Returns the APM of a player.
	 * @param playerIndex index of the player whose APM to be returned
	 * @return the APM of the specified player
	 */
	public int getPlayerApm( final int playerIndex ) {
		if ( playerIds[ playerIndex ] >= playerIdActionsCounts.length )
			return 0; // Computers
		return getPlayerApmForActionsCount( playerIndex, playerIdActionsCounts[ playerIds[ playerIndex ] ] - playerIdActionsCountBefore2Mins[ playerIds[ playerIndex ] ] );
	}
	
	/**
	 * Calculates the APM of a player for the specified number of actions (useful to calculate EAPM for example). 
	 * @param playerIndex  index of player whose APM to be returned
	 * @param actionsCount number of actions to base APM calcualtion on
	 * @return the APM of a player for the specified number of actions
	 */
	public int getPlayerApmForActionsCount( final int playerIndex, final int actionsCount ) {
		final int activeDurationFrames = getPlayerActiveDurationFrames( playerIndex );
		if ( activeDurationFrames == 0 )
			return 0;
		
		return activeDurationFrames == 0 ? 0 : actionsCount * 1000 * 60 / ( activeDurationFrames * 42 );
	}
	
	/**
	 * Returns the number of frames when the player was active.<br>
	 * The first 2 minutes are omitted.
	 * The last frame is the frame of the last action of the player.
	 * If the player does not have actions after 2 minutes, 0 is returned
	 * @param playerIndex
	 * @return the number of frames when the player was active
	 */
	public int getPlayerActiveDurationFrames( final int playerIndex ) {
		if ( playerIds[ playerIndex ] >= playerIdLastActionFrame.length )
			return 0; // Computers
		final int lastActionFrame = playerIdLastActionFrame[ playerIds[ playerIndex ] ];
		return lastActionFrame < FRAMES_IN_TWO_MINUTES ? 0 : lastActionFrame - FRAMES_IN_TWO_MINUTES;
	}
	
	/**
	 * Prints the replay header information into the specified output writer.
	 * @param output output print writer to be used
	 */
	public void printHeaderInformation( final PrintWriter output ) {
		output.println( "Game engine: " + getGameEngineString() );
		output.println( "Duration: " + getDurationString( false ) );
		output.println( "Saved on: " + saveTime );
		output.println( "Version: " + guessVersionFromDate() );
		output.println( "Game name: " + gameName );
		output.println( "Game Type: " + GAME_TYPE_NAMES[gameType]);
		output.println( "Map size: " + getMapSize() );
		output.println( "Creator name: " + creatorName );
		output.println( "Map name: " + mapName );
		output.println( "Players: " );
		
		// We want to sort players by the number of their actions (which is basically sorting by APM)
		final List< Object[] > playerDescriptionList = new ArrayList< Object[] >( 12 );
		for ( int i = 0; i < playerNames.length; i++ )
			if ( playerNames[ i ] != null ) {
				String colorName;
				try {
					colorName = IN_GAME_COLOR_NAMES[ playerColors[ i ] ];
				}
				catch ( final Exception e ) {
					colorName = "<unknown>";
				}
				final int actionsCount = playerIds[ i ] < playerIdActionsCounts.length ? playerIdActionsCounts[ playerIds[ i ] ] : 0;
				final String playerDescription = "    " + playerNames[ i ] + " (" + RACE_CHARACTERS[ playerRaces[ i ] ] 
				                + "), color: " + colorName + ", actions: " + actionsCount 
				                + ", APM: " + getPlayerApm( i );
				playerDescriptionList.add( new Object[] { actionsCount, playerDescription } );
			}
		
		Collections.sort( playerDescriptionList, new Comparator< Object[] >() {
			public int compare( final Object[] object1, final Object[] object2 ) {
				return -( (Integer) object1[ 0 ] ).compareTo( (Integer) object2[ 0 ] );
			}
		} );
		
		for ( final Object[] playerDescription : playerDescriptionList )
			output.println( (String) playerDescription[ 1 ] );
		
		output.flush();
	}
	
	/** Names of the public Starcraft Broodwar versions. */
	public static final String[] VERSION_NAMES = {
		"1.0", "1.07", "1.08", "1.08b", "1.09", "1.09b", "1.10", "1.11", "1.11b",
		"1.12", "1.12b", "1.13", "1.13b", "1.13c", "1.13d", "1.13e", "1.13f", "1.14",
		"1.15", "1.15.1", "1.15.2", "1.15.3", "1.16", ">=1.16.1"
	};
	
	/** Starcraft release dates and version names. Source: ftp.blizzard.com/pub/broodwar/patches/PC */
	public static final long[] VERSION_RELEASE_DATES = new long[ VERSION_NAMES.length ];
	static {
		final String[] versionReleaseDateStrings = {
			"1998-01-01", "1999-11-02", "2001-05-18", "2001-05-20", "2002-02-06", "2002-02-25",
			"2003-10-14", "2004-04-29", "2004-06-01", "2005-02-17", "2005-02-24", "2005-06-30",
			"2005-08-12", "2005-08-22", "2005-09-06", "2005-09-12", "2006-04-21", "2006-08-01",
			"2007-05-15", "2007-08-20", "2008-01-16", "2008-09-11", "2008-11-25", "2009-01-21",
		};
		try {
			final DateFormat SDF = new SimpleDateFormat( "yyyy-MM-dd" );
			for ( int i = 0; i < versionReleaseDateStrings.length; i++ )
				VERSION_RELEASE_DATES[ i ] = SDF.parse( versionReleaseDateStrings[ i ] ).getTime();
		}
		catch ( final ParseException pe ) {
			pe.printStackTrace();
		}
	}
	
	/** We store guessed version once it has been determined. */
	private String guessedVersion;
	
	/**
	 * Guesses the replay Starcraft version from the save date.
	 * @return the guessed version string
	 */
	public String guessVersionFromDate() {
		if ( guessedVersion == null ) {
			final long saveTime_ = saveTime.getTime();
			
			for ( int i = VERSION_RELEASE_DATES.length - 1; i >= 0; i-- )
				if ( saveTime_ > VERSION_RELEASE_DATES[ i ] ) {
					guessedVersion = VERSION_NAMES[ i ];
					break;
				}
			
			if ( guessedVersion == null )
				guessedVersion = "<unknown>";
		}
		
		return guessedVersion;
	}
	
}
