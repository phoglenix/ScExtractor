package extractor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jnibwapi.model.Player;
import jnibwapi.model.Region;
import jnibwapi.model.Unit;
import util.UnitAttributes;
import extractor.ExtractStates.PlayerResources;
import extractor.ExtractStates.RegionValues;

/**
 * Holds all the information regarding a match in one place.
 * 
 * @author grob083
 */
public class MatchInfo {
	
	// variables for global values from the DB (per match)
	public long dbMapId = -1;
	public long dbReplayId = -1;
	/** Map player.id -> db PlayerReplayId (globally unique) */
	public final Map<Integer, Long> playerIdToPlayerReplayId = new HashMap<>();
	/** Map unit.id -> db UnitId (globally unique) */
	public final Map<Integer, Long> unitIdToDbId = new HashMap<>();
	/** Map Region -> db RegionId (globally unique) */
	public final HashMap<Region, Long> regionToDbRegionId = new HashMap<>();
	
	// variables for tracking state changes (per match)
	/** Map player.id -> Map(Region -> aggregate costs of Units there) */
	public final HashMap<Integer, HashMap<Region, RegionValues>> playerToRegionToValues =
			new HashMap<>();
	public final HashMap<Integer, PlayerResources> playerToResources = new HashMap<>();
	/** Map unit.id -> Map(player.id -> unit.isVisible) */
	public final Map<Integer, Map<Integer, Boolean>> uIdToPIdToVisibility = new HashMap<>();
	/** Map unit.id -> UnitAttributes */
	public final Map<Integer, UnitAttributes> unitIdToAttributes = new HashMap<>();
	/**
	 * Map Player -> Map(unit.id -> Unit). Records the unit states for each player, at the last
	 * point the player could see the unit.
	 */
	public final Map<Player, Map<Integer, Unit>> lastSeenUnitStates = new HashMap<>();
	/** Map unit.id -> Unit, even if unit no longer exists, for all non-observing players' units */
	public final Map<Integer, Unit> allUnits = new HashMap<>();
	/**
	 * Mapping from players with unrecognised player ID's (usually observers) to their unit count
	 * (so the game can be exited if "observers" are actually making units).
	 */
	public final Map<Integer, Integer> invalidPlayerIdToUnitCount = new HashMap<>();
	/**
	 * Map unit.id -> last frame in which an action was performed on that unit. Cached information
	 * may be out of date (ie. current frame > value in {@link #unitIdToNextActionFrameCached}).
	 */
	public final Map<Integer, Integer> unitIdToLastActionFrameCached = new HashMap<>();
	/**
	 * Map unit.id -> next frame in which an action will be performed on that unit. Cached
	 * information may be out of date (ie. current frame > stored value).
	 */
	public final Map<Integer, Integer> unitIdToNextActionFrameCached = new HashMap<>();
	
	// Variables for tracking/removing orphans in the DB (per match).
	// replay, map, region, buildtile don't need to be checked because DB constraints keep them
	// unique.
	// playerreplay can be checked using playerIdToPlayerReplayId, and
	// unit can be checked using unitIdToDbId.
	public final Set<Long> allAttributeChangeIds = new HashSet<>();
	public final Set<Long> allVisibilityChangeIds = new HashSet<>();
	public final Set<Long> allRegionValueChangeIds = new HashSet<>();
	public final Set<Long> allResourceChangeIds = new HashSet<>();
	public final Set<Long> allEventIds = new HashSet<>();
	
	public MatchInfo(Collection<Player> players) {
		for (Player p : players) {
			if (!p.isNeutral()) {
				playerToRegionToValues.put(p.getID(), new HashMap<Region, RegionValues>());
				lastSeenUnitStates.put(p, new HashMap<Integer, Unit>());
			}
		}
	}
	
}
