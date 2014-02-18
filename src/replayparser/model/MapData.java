package replayparser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Class modeling some data about the map.
 * 
 * @author Belicza Andras
 * Not used by Glen (bwapi does this better)
 */
public class MapData {
	
	/** Defines the tile set of the map. */
	public short   tileSet = -1;
	
	/** Map tile data: width x height elements. */
	public short[] tiles;
	
	/** Mineral positions on the map, {x,y} pixel coordinates. */
	public List< short[] > mineralFieldList  = new ArrayList< short[] >();
	
	/** Vespene geyser positions on the map, {x,y} pixel coordinates. */
	public List< short[] > geyserList        = new ArrayList< short[] >();
	
	/** Start locations on the map, {owner,x,y} pixel coordinates. */
	public List< int[] >   startLocationList = new ArrayList< int[]   >();
	
}
