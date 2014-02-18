package replayparser.model;

import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import jnibwapi.types.OrderType;
import jnibwapi.types.OrderType.OrderTypes;
import jnibwapi.types.UnitCommandType;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;

/**
 * Class modeling an action.
 * 
 * @author Andras Belicza
 */
public class RPAction {
	
	public enum ReplayActions {
		Restart_Game(0x08),
		Select(0x09),
		ShiftSelect(0x0a),
		ShiftDeselect(0x0b),
		Vision(0x0d),
		Ally(0x0e),
		Change_Game_Speed(0x0f),
		Pause_Game(0x10),
		Resume_Game(0x11),
		Use_Cheat(0x12),
		Hotkey(0x13),
		Cancel_Nuke(0x2e), // Doesn't map to an order (but should)
		Start_Game(0x3c),
		Leave_Game(0x57),
		Minimap_Ping(0x58),
		Deselect(0x5b),
		Game_Chat(0x5c),
		None(0xff);
		
		private byte id;
		
		private ReplayActions(byte id) {
			this.id = id;
		}
		private ReplayActions(int id) {
			this.id = (byte)id;
		}
		
		public byte getID() {
			return id;
		}
	}
	
	public static final String HOTKEY_ACTION_SELECT = "Select";
	public static final String HOTKEY_ACTION_ASSIGN = "Assign";
	
	/** Map of action IDs and their names. */
	private static final Map<Byte, String> ACTIONID_TO_NAME = new HashMap<>();
	static {
		// These partially overlap with BWAPI UnitCommandTypes but don't map directly.
		// The overlapping ones have been removed.
		for (ReplayActions ra : ReplayActions.values()) {
			ACTIONID_TO_NAME.put(ra.getID(), ra.name());
		}
		
	}
	public static final Map<Byte, ReplayActions> ID_TO_ACTION = new HashMap<>();
	static {
		for (ReplayActions ra : ReplayActions.values()) {
			ID_TO_ACTION.put((byte) ra.getID(), ra);
		}
	}
	
	public static final Map<Byte, String> GAME_SPEED_MAP = new HashMap<Byte, String>();
	static {
		GAME_SPEED_MAP.put((byte) 0x00, "Slowest");
		GAME_SPEED_MAP.put((byte) 0x01, "Slower");
		GAME_SPEED_MAP.put((byte) 0x02, "Slow");
		GAME_SPEED_MAP.put((byte) 0x03, "Normal");
		GAME_SPEED_MAP.put((byte) 0x04, "Fast");
		GAME_SPEED_MAP.put((byte) 0x05, "Faster");
		GAME_SPEED_MAP.put((byte) 0x06, "Fastest");
	}
	
	/** Iteration when this action was given. */
	public final int frame;
	
	// // Only one of the following 3 will be set at a time // //
	/** Represents a non-BWAPI action, or ReplayActions.None. */
	public final ReplayActions rAction;
	/** The unit command, or CommandTypes.None. */
	public final UnitCommandType unitCommand;
	/** The unit order, or OrderTypes.None. */
	public final OrderType order;
	// // Parameters which may or may not be set // //
	/** Target unitID, typeId, researchID, or upgradeID, or -1 if none set */
	public final int targetId;
	/** position .... special for move and attack Actions. is -1 if none set */
	public final int x;
	/** position .... special for move and attack Actions. is -1 if none set */
	public final int y;
	/** Selected units resulting from a unit selection action, or null if none set */
	public final HashSet<Integer> selectedUnitIds;
	/** Whether the command is a delayed (queued / shift) action, or false if not set */
	public final boolean delayedAction;
	/** Extra parameters. Not used for any of the OrderTypes / UnitCommandTypes actions */
	public final String stringParams;
	
	// /** Extra info for particular actions */
	// public Object extraArg;
	
	/**
	 * Creates a new Action with pre-identified indices.<br>
	 * Subaction, unit and building name indices are unknown.
	 * 
	 * @param iteration iteration of the action
	 * @param parameters parameter string of the action
	 * @param action index determining the action name
	 */
	public RPAction(int iteration, String parameters, ReplayActions action) {
		this(iteration, parameters, action, null);
	}
	
	/**
	 * Creates a new Action with pre-identified indices.<br>
	 * Subaction, unit and building name indices are unknown.
	 * 
	 * @param iteration iteration of the action
	 * @param parameters parameter string of the action
	 * @param action index determining the action name
	 * @param selectedUnitIds set of selected units after this action
	 */
	public RPAction(int iteration, String parameters, ReplayActions action,
			HashSet<Integer> selectedUnitIds) {
		this.frame = iteration;
		this.selectedUnitIds = selectedUnitIds;
		this.targetId = -1;
		this.x = -1;
		this.y = -1;
		this.delayedAction = false;
		this.stringParams = parameters;
		
		this.rAction = action;
		this.unitCommand = UnitCommandTypes.None;
		this.order = OrderTypes.None;
	}
	
	/**
	 * Creates a new Action with pre-identified indices.
	 */
	public RPAction(int iteration, OrderType orderType, int x, int y, int targetUnitId,
			boolean delayedAction) {
		this(iteration, "", ReplayActions.None, UnitCommandTypes.None, orderType, x, y, targetUnitId,
				delayedAction);
	}
	
	/**
	 * Creates a new Action with pre-identified indices.
	 */
	public RPAction(int iteration, UnitCommandType commandType, int targetId, boolean delayedAction) {
		this(iteration, commandType, -1, -1, targetId, delayedAction);
	}
	
	/**
	 * Creates a new Action with pre-identified indices.
	 */
	public RPAction(int iteration, UnitCommandType commandType, int x, int y, int targetId,
			boolean delayedAction) {
		this(iteration, "", ReplayActions.None, commandType, OrderTypes.None, x, y, targetId,
				delayedAction);
	}
	
	/**
	 * Creates a new Action with pre-identified indices.
	 * 
	 * @param iteration iteration of the action
	 * @param parameters parameter string of the action
	 * @param action index determining the action name
	 * @param unitCommand command given in the action
	 * @param orderType order given in the action
	 * @param x x-coordinate of the action
	 * @param y y-coordinate of the action
	 * @param targetID ID of a unit, unitType, techType, or upgradeType depending on the action
	 * @param delayedAction whether the action is immediate or queued
	 */
	public RPAction(int iteration, String parameters, ReplayActions action, UnitCommandType unitCommand,
			OrderType orderType, int x, int y, int targetID, boolean delayedAction) {
		this.frame = iteration;
		this.selectedUnitIds = null;
		this.targetId = targetID;
		this.x = x;
		this.y = y;
		this.delayedAction = delayedAction;
		this.stringParams = parameters;
		
		this.rAction = action;
		this.unitCommand = unitCommand;
		this.order = orderType;
	}
	
	@Override
	public String toString() {
		return toString(null, false);
	}
	
	/** Attribute to cache the value returned by the {@link #toString(String, boolean)} method. */
	private String toStringValue;
	/** Attribute to cache the value returned by the {@link #toString(String, boolean)} method with
	 *  seconds. */
	private String toStringValueSeconds;
	
	/**
	 * Returns the string representation of this action owned by the specified player.
	 * 
	 * @param playerName name of player owning this action
	 * @param timeInSeconds tells if time has to be printed as seconds instead of iteration
	 * @return the string representation of this action owned by the specified player
	 */
	public String toString(final String playerName, final boolean timeInSeconds) {
		if (timeInSeconds && toStringValueSeconds == null
				|| !timeInSeconds && toStringValue == null) {
			String actionName = null;
			
			if (order != OrderTypes.None)
				actionName = order.getName();
			if (actionName == null && unitCommand != UnitCommandTypes.None)
				actionName = unitCommand.getName();
			if (actionName == null && rAction != ReplayActions.None)
				actionName = rAction.name();
			if (actionName == null) {
				actionName = "<not parsed>";
				System.err.println("Action at frame " + frame + " not parsed (for player: "
						+ playerName + ")");
			}
			
			StringBuilder parameters = new StringBuilder();
			if (selectedUnitIds != null) {
				parameters.append(" selectedUnitIds=");
				for (int id : selectedUnitIds)
					parameters.append(id).append(",");
				if (parameters.charAt(parameters.length() - 1) == ',')
					parameters.deleteCharAt(parameters.length() - 1);
			}
			if (x != -1)
				parameters.append(" x=").append(x);
			if (y != -1)
				parameters.append(" y=").append(y);
			if (targetId != -1)
				parameters.append(" targetId=").append(targetId);
			if (delayedAction)
				parameters.append(" delayed-action");
			if (!stringParams.isEmpty())
				parameters.append(" ").append(stringParams);
			
			final StringBuilder actionStringBuilder = new StringBuilder();
			if (timeInSeconds)
				ReplayHeader.formatFrames(frame, actionStringBuilder, true);
			
			final Formatter actionStringFormatter = new Formatter(actionStringBuilder);
			if (playerName == null) {
				if (timeInSeconds) {
					actionStringFormatter.format(" %-13s %s", actionName, parameters.toString());
				} else {
					actionStringFormatter.format("%6d %-13s %s", frame, actionName,
							parameters.toString());
				}
			}
			else {
				if (timeInSeconds) {
					actionStringFormatter.format(" %-25s %-17s %s", playerName, actionName,
							parameters.toString());
				} else {
					actionStringFormatter.format("%6d %-25s %-17s %s", frame, playerName,
							actionName, parameters.toString());
				}
			}
			actionStringFormatter.close();
			
			if (timeInSeconds)
				toStringValueSeconds = actionStringBuilder.toString();
			else
				toStringValue = actionStringBuilder.toString();
		}
		
		return timeInSeconds ? toStringValueSeconds : toStringValue;
	}
	
}
