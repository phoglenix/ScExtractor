package replayparser.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class modeling a replay.
 * 
 * @author Belicza Andras
 */
public class Replay {
	
	/** Header of the replay. */
	public final ReplayHeader header;
	
	/** Players in the replay */
	public final List<Player> players;
	/** Formatted text of game chat. */
	public final String gameChat;
	/** Data of the map. */
	public final MapData mapData;
	public List<Map<Integer, RPAction>> badIteration = new ArrayList<>();
	/** init units fields */
	public List<Set<Integer>> initHQID = new ArrayList<>();
	public List<List<Integer>> initLarvaIDs = new ArrayList<>();
	public List<Set<Integer>> initWorkerIDs = new ArrayList<>();
	
	/**
	 * Creates a new Replay.
	 * 
	 * @param replayHeader header of the replay
	 * @param replayActions actions of the replay
	 * @param gameChat formatted text of game chat
	 */
	public Replay(ReplayHeader replayHeader, List<Player> players, String gameChat,
			MapData mapData) {
		this.header = replayHeader;
		this.players = players;
		this.gameChat = gameChat;
		this.mapData = mapData;
	}
	
}
