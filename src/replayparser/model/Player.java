package replayparser.model;

import java.util.List;

/**
 * Class modelling a player.
 * 
 * @author Andras Belicza, Glen Robertson
 */
public class Player {
	
	/** Name of the player. */
	public final String name;
	/** Actions of the player. */
	public final List<RPAction> actions;
	/** The player index in the arrays of player information. Seems to correspond to the player
	 * number in-game. */
	public final int replayIndex;
	/** The player id according to the replay. Doesn't seem to correspond to anything. */
	public final int id;
	/** The player's race, as an index into the RaceTypes enum */
	public final int race;
	
	/**
	 * Creates a new Player.
	 */
	public Player(final String name, final List<RPAction> actions, final ReplayHeader rh) {
		this.name = name;
		this.actions = actions;
		// Initialise other fields from the header
		replayIndex = rh.getPlayerIndexByName(name);
		id = rh.playerIds[replayIndex];
		race = rh.playerRaces[replayIndex];
	}
}
