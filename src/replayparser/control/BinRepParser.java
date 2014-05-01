package replayparser.control;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jnibwapi.types.OrderType.OrderTypes;
import jnibwapi.types.TechType.TechTypes;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;
import jnibwapi.types.UnitType.UnitTypes;
import replayparser.model.MapData;
import replayparser.model.Player;
import replayparser.model.RPAction;
import replayparser.model.RPAction.ReplayActions;
import replayparser.model.Replay;
import replayparser.model.ReplayHeader;

/**
 * Replay parser to produce a {@link Replay} java object from a binary replay file.
 * 
 * @author Andras Belicza
 */
public class BinRepParser {
	
	// public static Vector<Integer> frames = new Vector<Integer>();
	// public static Vector<Action> trained = new Vector<Action>();
	private static final Charset CHARSET = Charset.forName("Cp949");
	private static final byte DELAYED_ACTION = 0x01;
	private static final short UNIT_ID_NONE = 0;
	// private static List<Set<Integer>> playerUnitIDs = new ArrayList<>();
	private static List<Set<Integer>> playerBuildingIDs = new ArrayList<>();
	private static List<Set<Integer>> selectedIDs = new ArrayList<>();
	private static List<Set<Integer>> initHQID = new ArrayList<>();
	private static List<Set<Integer>> initWorkerIDs = new ArrayList<>();
	private static List<List<Integer>> initLarvaIDs = new ArrayList<>();
	
	private static List<Set<Integer>> posBadIteration = new ArrayList<>();
	private static List<Map<Integer, RPAction>> badIteration = new ArrayList<>();
	
	// private static HashSet<Integer> union = new HashSet<Integer>();
//	private static int[] initUnitsCounter = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
//	private static int[] initUnitsCount = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
//	private static int[] initWorkerCounter = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
//	private static int[] initLarvaCounter = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	
	/** Size of the header section */
	public static final int HEADER_SIZE = 0x279;
	
	/** Mask for mapping of units index */
	public static final int INDEX_MASK = 0x7FF;
	
	/**
	 * Wrapper class to build the game chat.
	 * 
	 * @author Andras Belicza
	 */
	private static class GameChatWrapper {
		
		/** <code>StringBuilder</code> for the game chat. */
		public final StringBuilder gameChatBuilder;
		/** Map from the player IDs to their name. */
		public final Map<Integer, String> playerIndexNameMap;
		/** Message buffer to be used to extract messages. */
		public final byte[] messageBuffer = new byte[80];
		
		/**
		 * Creates a new GameChatWrapper.
		 */
		public GameChatWrapper(final String[] playerNames, final int[] playerIds) {
			gameChatBuilder = new StringBuilder();
			
			playerIndexNameMap = new HashMap<Integer, String>();
			for (int i = 0; i < playerNames.length; i++)
				if (playerNames[i] != null && playerIds[i] != 0xff) // Computers are listed with
																	// playerId values of 0xff.
					playerIndexNameMap.put(i, playerNames[i]);
		}
	}
	
	/**
	 * Parses a binary replay file.
	 * 
	 * @param replayFile replay file to be parsed
	 * @param parseCommandsSection tells if player actions have to be parsed from the commands
	 *        section
	 * @param parseGameChat tells if game chat has to be parsed
	 * @param parseMapDataSection tells if map data section has to be parsed
	 * @param parseMapTileData tells if map tile data section has to be parsed
	 * @return a {@link Replay} object describing the replay; or <code>null</code> if replay cannot
	 *         be parsed
	 */
	@SuppressWarnings({ "unchecked" })
	public static Replay parseReplay(final File replayFile, final boolean parseCommandsSection,
			final boolean parseGameChat, final boolean parseMapDataSection,
			final boolean parseMapTileData) {
		// playerUnitIDs = new ArrayList<>();
		playerBuildingIDs = new ArrayList<>();
		selectedIDs = new ArrayList<>();
		initHQID = new ArrayList<>();
		initWorkerIDs = new ArrayList<>();
		initLarvaIDs = new ArrayList<>();
		
		posBadIteration = new ArrayList<>();
		badIteration = new ArrayList<>();
		
		// union = new HashSet<Integer>();
		
//		for (int i = 0; i < 12; i++) {
//			initUnitsCounter[i] = 0;
//			initUnitsCount[i] = 0;
//			initWorkerCounter[i] = 0;
//			initLarvaCounter[i] = 0;
//		}
		
		BinReplayUnpacker unpacker = null;
		try {
			unpacker = new BinReplayUnpacker(replayFile);
			
			// Replay ID section
			if (Integer.reverseBytes(ByteBuffer.wrap(unpacker.unpackSection(4)).getInt()) != 0x53526572)
				return null; // Not a replay file
				
			// Replay header section
			final byte[] headerData = unpacker.unpackSection(HEADER_SIZE);
			final ByteBuffer headerBuffer = ByteBuffer.wrap(headerData);
			headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
			
			final ReplayHeader replayHeader = new ReplayHeader();
			replayHeader.gameEngine = headerData[0x00];
			
			replayHeader.gameFrames = headerBuffer.getInt(0x01);
			replayHeader.saveTime = new Date(headerBuffer.getInt(0x08) * 1000l);
			
			replayHeader.gameName = getZeroPaddedString(headerData, 0x18, 28);
			
			replayHeader.mapWidth = headerBuffer.getShort(0x34);
			replayHeader.mapHeight = headerBuffer.getShort(0x36);
			
			replayHeader.gameSpeed = headerBuffer.getShort(0x3a);
			replayHeader.gameType = headerBuffer.getShort(0x3c);
			replayHeader.gameSubType = headerBuffer.getShort(0x3e);
			
			replayHeader.creatorName = getZeroPaddedString(headerData, 0x48, 24);
			
			// NOTE WAS 26 CHANGED TO 32 BY GLEN
			replayHeader.mapName = getZeroPaddedString(headerData, 0x61, 32);
			
			replayHeader.playerRecords = Arrays.copyOfRange(headerData, 0xa1, 0xa1 + 432);
			for (int i = 0; i < replayHeader.playerColors.length; i++)
				replayHeader.playerColors[i] = headerBuffer.getInt(0x251 + i * 4);
			replayHeader.playerSpotIndices = Arrays.copyOfRange(headerData, 0x271, 0x271 + 8);
			
			// Derived data from player records:
			for (int i = 0; i < 12; i++) {
				final String playerName = getZeroPaddedString(replayHeader.playerRecords,
						i * 36 + 11, 25);
				if (playerName.length() > 0)
					replayHeader.playerNames[i] = playerName;
				replayHeader.playerRaces[i] = replayHeader.playerRecords[i * 36 + 9];
//				initUnitsCount[i] = ReplayHeader.INIT_UNITS_COUNT;
				replayHeader.playerIds[i] = replayHeader.playerRecords[i * 36 + 4] & 0xff;
				// playerUnitIDs.add(i, new HashSet<Integer>());
				playerBuildingIDs.add(i, new HashSet<Integer>());
				selectedIDs.add(i, new HashSet<Integer>());
				initHQID.add(i, new HashSet<Integer>());
				initLarvaIDs.add(i, new ArrayList<Integer>());
				initWorkerIDs.add(i, new HashSet<Integer>());
				posBadIteration.add(i, new HashSet<Integer>());
				badIteration.add(i, new HashMap<Integer, RPAction>());
			}
			
			if (!parseCommandsSection)
				return new Replay(replayHeader, null, null, null);
			
			// Player commands length section
			final int playerCommandsLength = Integer.reverseBytes(ByteBuffer.wrap(
					unpacker.unpackSection(4)).getInt());
			
			// Player commands section
			final ByteBuffer commandsBuffer = ByteBuffer.wrap(unpacker
					.unpackSection(playerCommandsLength));
			// System.out.print("Buffer " + commandsBuffer.toString());
			commandsBuffer.order(ByteOrder.LITTLE_ENDIAN);
			
			List<RPAction>[] playerActionLists = null;
			GameChatWrapper gameChatWrapper = null;
			if (parseGameChat)
				gameChatWrapper = new GameChatWrapper(replayHeader.playerNames,
						replayHeader.playerIds);
			if (parseCommandsSection) {
				// This will be indexed by playerId!
				playerActionLists = new ArrayList[replayHeader.playerNames.length];
				for (int i = 0; i < playerActionLists.length; i++)
					playerActionLists[i] = new ArrayList<RPAction>();
			}
			
			while (commandsBuffer.position() < playerCommandsLength) {
				final int frame = commandsBuffer.getInt();
				int commandBlocksLength = commandsBuffer.get() & 0xff;
				final int commandBlocksEndPos = commandsBuffer.position() + commandBlocksLength;
				
				while (commandsBuffer.position() < commandBlocksEndPos) {
					final int playerId = commandsBuffer.get() & 0xff;
					final RPAction action = readNextAction(frame, commandsBuffer,
							commandBlocksEndPos, gameChatWrapper, playerId);
					if (action != null) {
						// If playerId is outside the index range, throw the implicit exception and
						// fail to parse replay, else it may contain incorrect actions which may
						// lead to false hack reports!
						replayHeader.playerIdActionsCounts[playerId]++;
						if (frame < ReplayHeader.FRAMES_IN_TWO_MINUTES)
							replayHeader.playerIdActionsCountBefore2Mins[playerId]++;
						if (playerActionLists != null)
							playerActionLists[playerId].add(action);
						for (int plID = 0; plID < playerId; plID++)
							if (posBadIteration.get(plID).contains(frame)) {
								badIteration.get(playerId).put(frame, action);
								for (RPAction action2 : playerActionLists[plID])
									badIteration.get(plID).put(frame, action2);
							}
					}
				}
			}
			
			// Fill the last action frames array
			if (playerActionLists != null)
				for (int i = 0; i < playerActionLists.length; i++) {
					final List<RPAction> playerActionList = playerActionLists[i];
					if (!playerActionList.isEmpty())
						replayHeader.playerIdLastActionFrame[i] = playerActionList
								.get(playerActionList.size() - 1).frame;
				}
			
			List<Player> players = null;
			if (parseCommandsSection) {
				players = new ArrayList<Player>();
				for (int i = 0; i < replayHeader.playerNames.length; i++)
					// Computers are listed with playerId values of 0xff, but no actions are
					// recorded from them.
					if (replayHeader.playerNames[i] != null && replayHeader.playerIds[i] != 0xff)
						players.add(new Player(replayHeader.playerNames[i],
								playerActionLists[replayHeader.playerIds[i]], replayHeader));
			}
			
			MapData mapData = parseMapTileData ? new MapData() : null;
			if (parseMapDataSection) {
				// Map data length section
				final int mapDataLength = Integer.reverseBytes(ByteBuffer.wrap(
						unpacker.unpackSection(4)).getInt());
				
				// Map data section
				final ByteBuffer mapDataBuffer = ByteBuffer.wrap(unpacker
						.unpackSection(mapDataLength));
				mapDataBuffer.order(ByteOrder.LITTLE_ENDIAN);
				
				final byte[] sectionNameBuffer = new byte[4];
				/** Name of the dimension section in the map data replay section. */
				final String SECTION_NAME_DIMENSION = "DIM ";
				/** Name of the tile section in the map data replay section. */
				final String SECTION_NAME_MTXM = "MTXM";
				/** Name of the tile set section in the map data replay section. */
				final String SECTION_NAME_ERA = "ERA ";
				/** Name of the unit section in the map data replay section. */
				final String SECTION_NAME_UNIT = "UNIT";
				// Other sections: OWNR,UNIS,PUPx,UNIx,DD2,UNIT,SIDE,MRGN,ERA,MASK,PTEC,DIM
				while (mapDataBuffer.position() < mapDataLength) {
					mapDataBuffer.get(sectionNameBuffer);
					final String sectionName = new String(sectionNameBuffer, "US-ASCII");
					final int sectionLength = mapDataBuffer.getInt();
					final int sectionEndPos = mapDataBuffer.position() + sectionLength;
					
					if (sectionName.equals(SECTION_NAME_UNIT)) {
						if (parseMapTileData) {
							while (mapDataBuffer.position() < sectionEndPos) {
								// 36 bytes per unit
								final int unitEndPos = mapDataBuffer.position() + 36;
								mapDataBuffer.getInt(); // unknown
								final short x = mapDataBuffer.getShort();
								final short y = mapDataBuffer.getShort();
								final short type = mapDataBuffer.getShort();
								mapDataBuffer.getShort(); // unknown
								mapDataBuffer.getShort(); // special properties flag
								mapDataBuffer.getShort(); // valid elements flag
								final byte owner = mapDataBuffer.get();
								
								if (type == UnitTypes.Resource_Mineral_Field.getID()
										|| type == UnitTypes.Resource_Mineral_Field_Type_2.getID()
										|| type == UnitTypes.Resource_Mineral_Field_Type_3.getID()) {
									mapData.mineralFieldList.add(new short[] { x, y });
								}
								else if (type == UnitTypes.Resource_Vespene_Geyser.getID()) {
									mapData.geyserList.add(new short[] { x, y });
								}
								else if (type == UnitTypes.Special_Start_Location.getID()) {
									mapData.startLocationList.add(new int[] { x, y, owner });
								}
								// We might not processed all unit data
								if (mapDataBuffer.position() < unitEndPos)
									mapDataBuffer.position(unitEndPos < mapDataLength ? unitEndPos
											: mapDataLength);
							}
						}
					}
					else if (sectionName.equals(SECTION_NAME_DIMENSION)) {
						// If map has a non-standard size, the replay header contains invalid map
						// size, this is the correct one
						final short newWidth = mapDataBuffer.getShort();
						final short newHeight = mapDataBuffer.getShort();
						// Sometimes newWidth and newHeight is 0, we don't want to overwrite the
						// size with wrong values!
						// And sometimes it contains some insane values, we just ignore them
						if (newWidth <= 256 && newHeight <= 256) {
							if (newWidth > replayHeader.mapWidth)
								replayHeader.mapWidth = newWidth;
							if (newHeight > replayHeader.mapHeight)
								replayHeader.mapHeight = newHeight;
						}
						if (!parseMapTileData)
							break; // We only needed the dimension section
					}
					else if (sectionName.equals(SECTION_NAME_MTXM)) {
						if (parseMapTileData) {
							final int maxI = sectionLength / 2; // This is map_width*map_height
							// Sometimes map is broken into multiple sections. The first one is the
							// biggest (whole map size), but the beginning of map is empty
							// The subsequent MTXM sections will fill the whole at the beginning.
							if (mapData.tiles == null)
								mapData.tiles = new short[maxI];
							for (int i = 0; i < maxI; i++)
								mapData.tiles[i] = mapDataBuffer.getShort();
						}
					}
					else if (sectionName.equals(SECTION_NAME_ERA)) {
						if (parseMapTileData)
							mapData.tileSet = mapDataBuffer.getShort();
					}
					// Part or all the section might be unprocessed, skip the unprocessed bytes
					if (mapDataBuffer.position() < sectionEndPos)
						mapDataBuffer.position(sectionEndPos < mapDataLength ? sectionEndPos
								: mapDataLength);
				}
				// We might have skipped some parts of map data, so we position to the end
				if (mapDataBuffer.position() < mapDataLength) 
					mapDataBuffer.position(mapDataLength);
			}
			Replay rReplay = new Replay(replayHeader, players, gameChatWrapper == null ? null
					: gameChatWrapper.gameChatBuilder.toString(), mapData);
			// rReplay.playerToUnitIds = playerUnitIDs;
			rReplay.initHQID = initHQID;
			rReplay.badIteration = badIteration;
			rReplay.initLarvaIDs = initLarvaIDs;
			rReplay.initWorkerIDs = initWorkerIDs;
			return rReplay;
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (unpacker != null)
				unpacker.close();
		}
	}
	
	/**
	 * Returns a string from a "C" style buffer array.<br>
	 * That means we take the bytes of a string form a buffer until we find a 0x00 terminating
	 * character.
	 * 
	 * @param data data to read from
	 * @param offset offset to read from
	 * @param length max length of the string
	 * @return the zero padded string read from the buffer
	 */
	private static String getZeroPaddedString(final byte[] data, final int offset, final int length) {
		String string = new String(data, offset, length, CHARSET);
		
		int firstNullCharPos = string.indexOf(0x00);
		if (firstNullCharPos >= 0)
			string = string.substring(0, firstNullCharPos);
		
		return string;
	}
	
	/**
	 * Reads the next action in the commands buffer.<br>
	 * Only parses actions which are important in hack detection.
	 * 
	 * @param frame frame of the action
	 * @param commandsBuffer commands buffer to be read from
	 * @param commandBlocksEndPos end position of the current command blocks
	 * @param gameChatWrapper game chat wrapper to be used if game chat is desired
	 * @return the next action object
	 */
	private static RPAction readNextAction(final int frame, final ByteBuffer commandsBuffer,
			final int commandBlocksEndPos, final GameChatWrapper gameChatWrapper, final int playerId) {
		final byte actionId = commandsBuffer.get();
		
		RPAction action = null;
		int skipBytes = 0;
		
		switch (actionId) {
			case (byte) 0x09: // Select units
			case (byte) 0x0a: // Shift select units
			case (byte) 0x0b: { // Shift deselect units
				selectedIDs.get(playerId).clear();
				int unitsCount = commandsBuffer.get() & 0xff;
				HashSet<Integer> selectedUnitIds = new HashSet<Integer>();
				for (; unitsCount > 0; unitsCount--) {
					short unitID = commandsBuffer.getShort();
					try {
						int clearUnitID = getJniBwapiID(unitID);
						selectedIDs.get(playerId).add(new Integer(clearUnitID));
						selectedUnitIds.add(clearUnitID);
					} catch (UnitIDException e) {
						e.printStackTrace();
					}
				}
				
				action = new RPAction(frame, "", RPAction.ID_TO_ACTION.get(actionId),
						selectedUnitIds);
				break;
			}
			case (byte) 0x0c: { // Build
				final byte orderType = commandsBuffer.get(); // Ignored, generalised to "Build"
				if (orderType != 25 && orderType != 30 && orderType != 31 && orderType != 36
						&& orderType != 46 && orderType != 71) {
					System.out.println("BinRepParser: A build order was given with order: "
							+ orderType);
					// Always seems to be 31 (BuildProtoss1?) in PvP
					// With Terran in game, seems to be mostly 30 (PlaceBuilding) or 36 (PlaceAddon)
					// and occasional 71 (BuildingLand) - - shouldn't be a build action?
					// With Zerg in game, has 25 (DroneStartBuild)
					// and occasional 46 (BuildNydusExit) - - shouldn't be a build action?
				}
				final short tileX = commandsBuffer.getShort();
				final short tileY = commandsBuffer.getShort();
				final short typeId = commandsBuffer.getShort();
				// string = tileX + "," + tileY + "," + RPAction.UNIT_ID_NAME_MAP.get( unitId )
				
				action = new RPAction(frame, UnitCommandTypes.Build, tileX, tileY, typeId, false);
				// action = new RPAction( frame, UnitCommandType.UnitCommandTypes.Build,
				// RPAction.ID_TO_UNITTYPE.get( (int)typeId ), tileX, tileY, -1 );
				// trained.add(action);
				posBadIteration.get(playerId).add(frame);
				break;
			}
			case (byte) 0x0d: { // Vision
				final byte data1 = commandsBuffer.get();
				final byte data2 = commandsBuffer.get();
				action = new RPAction(frame, convertToHexString(data1, data2),
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x0e: { // Ally
				final byte data1 = commandsBuffer.get();
				final byte data2 = commandsBuffer.get();
				final byte data3 = commandsBuffer.get();
				final byte data4 = commandsBuffer.get();
				action = new RPAction(frame, convertToHexString(data1, data2, data3, data4),
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x0f: { // Change game speed
				final byte speed = commandsBuffer.get();
				action = new RPAction(frame, RPAction.GAME_SPEED_MAP.get(speed),
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x13: { // Hotkey
				selectedIDs.get(playerId).clear();
				final byte type = commandsBuffer.get();
				final byte slot = commandsBuffer.get();
				action = new RPAction(frame,
						(type == (byte) 0x00 ? RPAction.HOTKEY_ACTION_ASSIGN
								: RPAction.HOTKEY_ACTION_SELECT) + "," + slot,
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x14: { // Move (Right click)
				short posX = commandsBuffer.getShort();
				short posY = commandsBuffer.getShort();
				short unitId = commandsBuffer.getShort();
				// Move to (posX;posY) if this is 0xffff, or move to this unit if it's a valid unit
				// id (if it's not 0xffff)
				@SuppressWarnings("unused")
				final short typeId = commandsBuffer.getShort(); // What is this used for?
				final byte commandType = commandsBuffer.get();
				// System.out.println(frame + " Skipped " + skipBytes + " Caused by id[" + blockId+
				// "] Move " + unitId + " ");
				// System.out.println(unitId + " "+(unitId == 0) );
				try {
					int bwapiUnitId = -1;
					if (unitId != UNIT_ID_NONE) {
						bwapiUnitId = getJniBwapiID(unitId);
					}
					action = new RPAction(frame, UnitCommandTypes.Move, posX, posY, bwapiUnitId,
							commandType == DELAYED_ACTION);
//					if (initWorkerCounter[playerId] < ReplayHeader.INIT_WORKER_COUNT) {
//						for (Integer id : selectedIDs.get(playerId)) {
//							if (!initWorkerIDs.get(playerId).contains(id)) {
//								initWorkerIDs.get(playerId).add(id);
//								initWorkerCounter[playerId]++;
//							}
//						}
//					}
				} catch (UnitIDException e) {
					e.printStackTrace();
				}
				break;
			}
			case (byte) 0x15: { // Attack/Right Click/Cast Magic/Use ability
				final short posX = commandsBuffer.getShort();
				final short posY = commandsBuffer.getShort();
				final short targetUnitId = commandsBuffer.getShort();
				// (posX;posY) if targetUnitId is 0xffff, or target this unit if it's a valid
				// unit id (if it's not 0xffff)
				@SuppressWarnings("unused")
				final short unitType = commandsBuffer.getShort();
				// Usually unitType is set to 228 (None) but sometimes is set to an actual unit
				// type. In these cases the orderType seems to be set to 9 (attack shrouded)
				final int orderType = ((int)commandsBuffer.get() + 256) % 256;
				final byte commandType = commandsBuffer.get();
				// Type2: commandType 0x00 for normal attack, 0x01 for shift attack
				
				// System.out.println("unitType: " + Action.UNIT_ID_NAME_MAP.get(unitId));
				// switch (orderType) {
				// case (byte) 0x00:
				// case (byte) 0x06: // Move with right click or Move by click move icon
				// // actionNameIndex = RPAction.ANI_MOVE ;
				// playerUnitIDs.get(playerId).addAll(selectedIDs.get(playerId));
				// break;
				// }
				int bwapiUnitId = -1;
				if (targetUnitId != UNIT_ID_NONE) {
					try {
						bwapiUnitId = getJniBwapiID(targetUnitId);
					} catch (UnitIDException e) {
						e.printStackTrace();
					}
				}
				// These should map directly to OrderTypes
				if (OrderTypes.getOrderType(orderType) == null) {
					System.err.println("WARNING: Unknown order id:" + orderType);
					action = new RPAction(frame, "orderTypeId:" + orderType,
							ReplayActions.None);
				} else {
					action = new RPAction(frame, OrderTypes.getOrderType(orderType),
							posX, posY, bwapiUnitId, commandType == DELAYED_ACTION);
				}
				break;
			}
			case (byte) 0x1f: { // Train
				final short typeId = commandsBuffer.getShort();
				action = new RPAction(frame, UnitCommandTypes.Train, typeId, false);
				// trained.add(action);
				// System.out.println("new Unit: " + Action.UNIT_ID_NAME_MAP.get(unitId));
				playerBuildingIDs.get(playerId).addAll(selectedIDs.get(playerId));
				posBadIteration.get(playerId).add(frame);
//				if (initUnitsCounter[playerId] < initUnitsCount[playerId]) {
//					for (Integer id : selectedIDs.get(playerId)) {
//						if (initHQID.get(playerId).add(id)) {
//							initUnitsCounter[playerId]++;
//						}
//					}
//				}
				break;
			}
			case (byte) 0x20: { // Cancel train
				final short unitId = commandsBuffer.getShort();
				// System.out.println(frame + " Skipped " + skipBytes + " Caused by id[" + blockId+
				// "] Cancel train" );
				try {
					action = new RPAction(frame, UnitCommandTypes.Cancel_Train,
							getJniBwapiID(unitId), false);
				} catch (UnitIDException e) {
					e.printStackTrace();
				}
				break;
			}
			case (byte) 0x23: { // Morph (Unit)
				final short typeId = commandsBuffer.getShort();
				action = new RPAction(frame, UnitCommandTypes.Morph, typeId, false);
				for (Integer id : selectedIDs.get(playerId)) {
					if (initLarvaIDs.get(playerId).contains(id)) {
						// set the element at the end
						initLarvaIDs.get(playerId).remove(id);
						initLarvaIDs.get(playerId).add(id);
					}
//					if (initLarvaCounter[playerId] < ReplayHeader.INIT_LARVA_COUNT) {
//						if (initLarvaIDs.get(playerId).add(id)) {
//							initLarvaCounter[playerId]++;
//						}
//					}
				}
				break;
			}
			case (byte) 0x30: { // Research
				final byte researchId = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Research, researchId, false);
				// System.out.println("Babab " + blockId + " " + frame + " " +
				// Action.RESEARCH_ID_NAME_MAP.get(researchId));
				break;
			}
			case (byte) 0x32: { // Upgrade
				final byte upgradeId = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Upgrade, upgradeId, false);
				break;
			}
			case (byte) 0x1e: { // Return cargo
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Return_Cargo, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x21: { // Cloak
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Cloak, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x22: { // Decloak
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Decloak, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x25: { // Unsiege
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Unsiege, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x26: { // Siege
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Siege, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x28: { // Unload all
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Unload_All, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x2b: { // Hold position
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Hold_Position, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x2c: { // Burrow
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Burrow, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x2d: { // Unburrow
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Unburrow, -1,
						commandType == DELAYED_ACTION);
				break;
			}
			case (byte) 0x1a: { // Stop
				final byte commandType = commandsBuffer.get();
				action = new RPAction(frame, UnitCommandTypes.Stop, -1,
						commandType == DELAYED_ACTION);
				// final String params = blockId == (byte) 0x1a || blockId == (byte) 0x1e || blockId
				// == (byte) 0x28 || blockId == (byte) 0x2b ? ( commandType == 0x00 ? "Instant" :
				// "Queued" ) : "";
				// action = new RPAction( frame, params, blockId );
				break;
			}
			case (byte) 0x35: { // Morph building (zerg)
				final short typeId = commandsBuffer.getShort();
				action = new RPAction(frame, UnitCommandTypes.Morph, typeId, false);
				break;
			}
			case (byte) 0x57: { // Leave game
				final byte reason = commandsBuffer.get();
				action = new RPAction(frame, reason == (byte) 0x01 ? "Quit"
						: (reason == (byte) 0x06 ? "Dropped" : ""),
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x29: { // Unload
				final short unitId = commandsBuffer.getShort();
				try {
					action = new RPAction(frame, UnitCommandTypes.Unload, getJniBwapiID(unitId),
							false);
				} catch (UnitIDException e) {
					e.printStackTrace();
				}
				// action = new RPAction( frame, "", blockId );
				break;
			}
			case (byte) 0x58: { // Minimap ping
				final short posX = commandsBuffer.getShort();
				final short posY = commandsBuffer.getShort();
				action = new RPAction(frame, "(" + posX + "," + posY + ")",
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x12: { // Use Cheat
				final byte data1 = commandsBuffer.get();
				final byte data2 = commandsBuffer.get();
				final byte data3 = commandsBuffer.get();
				final byte data4 = commandsBuffer.get();
				action = new RPAction(frame, convertToHexString(data1, data2, data3, data4),
						RPAction.ID_TO_ACTION.get(actionId));
				break;
			}
			case (byte) 0x2f: { // Lift
				final short posX = commandsBuffer.getShort(); // Landing tile coordinates?
				final short posY = commandsBuffer.getShort();
				// Coordinates seem to be set (not -1) every time. No separate Land action. :S
				action = new RPAction(frame, UnitCommandTypes.Lift, posX, posY, -1, false);
				break;
			}
			case (byte) 0x18: { // Cancel
				action = new RPAction(frame, UnitCommandTypes.Cancel_Construction, -1, false);
				break;
			}
			case (byte) 0x19: { // Cancel hatch (morph)
				action = new RPAction(frame, UnitCommandTypes.Cancel_Morph, -1, false);
				break;
			}
			case (byte) 0x33: { // Cancel upgrade
				action = new RPAction(frame, UnitCommandTypes.Cancel_Upgrade, -1, false);
				break;
			}
			case (byte) 0x34: { // Cancel addon
				action = new RPAction(frame, UnitCommandTypes.Cancel_Addon, -1, false);
				break;
			}
			case (byte) 0x1b: { // Carrier Stop Order
				action = new RPAction(frame, UnitCommandTypes.Cancel_Train, -1, false);
				break;
			}
			case (byte) 0x1c: { // Reaver Stop Order
				action = new RPAction(frame, UnitCommandTypes.Cancel_Train, -1, false);
				break;
			}
			case (byte) 0x27: { // Build interceptor/scarab
				// No equivalent command type. Will map to Train with the appropriate unitType for
				// an interceptor / scarab depending on the unit doing the training.
				action = new RPAction(frame, OrderTypes.TrainFighter, -1, -1, -1, false);
				break;
			}
			case (byte) 0x2a: { // Merge archon
				action = new RPAction(frame, UnitCommandTypes.Use_Tech,
						TechTypes.Archon_Warp.getID(), false);
				break;
			}
			case (byte) 0x2e: { // Cancel nuke
				// No equivalent order/tech/command
				action = new RPAction(frame, "", RPAction.ID_TO_ACTION.get(actionId));
				System.err.println("A Cancel Nuke action was not converted into an order");
				break;
			}
			case (byte) 0x31: { // Cancel research
				action = new RPAction(frame, UnitCommandTypes.Cancel_Research, -1, false);
				break;
			}
			case (byte) 0x36: { // Stim
				action = new RPAction(frame, UnitCommandTypes.Use_Tech,
						TechTypes.Stim_Packs.getID(), false);
				break;
			}
			case (byte) 0x5a: { // Merge dark archon
				action = new RPAction(frame, UnitCommandTypes.Use_Tech,
						TechTypes.Dark_Archon_Meld.getID(), false);
				break;
			}
			case (byte) 0x5c: { // Game Chat (as of 1.16)
				if (gameChatWrapper == null) {
					skipBytes = 81; // 1 byte for player index, and 80 bytes of message characters
					// System.out.println(frame + " Skiped " + skipBytes + " Caused by id[" +
					// blockId+ "] Game Chat (as of 1.16)" );
				}
				else {
					if (gameChatWrapper.gameChatBuilder.length() > 0)
						gameChatWrapper.gameChatBuilder.append("\r\n");
					ReplayHeader.formatFrames(frame, gameChatWrapper.gameChatBuilder, false);
					gameChatWrapper.gameChatBuilder.append(" - ").append(
							gameChatWrapper.playerIndexNameMap.get(commandsBuffer.get() & 0xff));
					commandsBuffer.get(gameChatWrapper.messageBuffer);
					gameChatWrapper.gameChatBuilder.append(": ").append(
							getZeroPaddedString(gameChatWrapper.messageBuffer, 0,
									gameChatWrapper.messageBuffer.length));
				}
				break;
			}
			default: { // We don't know how to handle actions, we have to skip the whole time frame
						// which means we might lose some actions!
				
				// System.out.println(((byte) 0x1e)==blockId);
				// System.out.println("!!!!!!!!!Re default" + + blockId + " " + frame);
				skipBytes = commandBlocksEndPos - commandsBuffer.position();
				// System.out.println(frame + " Skiped " + skipBytes + " Caused by id[" + blockId+
				// "]  ÄÄÄÄHH" );
				
				break;
			}
		}
		
		if (skipBytes > 0) {
			commandsBuffer.position(commandsBuffer.position() + skipBytes);
			// System.out.println(frame + " Skiped " + skipBytes + " Caused by id[frameend] " );
		}
		
		if (actionId == (byte) 0x5c) // Game chat is not a "real" action
			return null;
		
		if (action == null)
			action = new RPAction(frame, "Unknown", RPAction.ID_TO_ACTION.get(actionId));
		
		return action;
	}
	
	/**
	 * Converts bytes to hex string separating bytes with spaces.
	 * 
	 * @return the bytes converted to string separated with spaces
	 */
	private static String convertToHexString(final byte... data) {
		final StringBuilder sb = new StringBuilder(data.length * 2);
		
		for (int i = 0; i < data.length;) {
			sb.append(Integer.toHexString((data[i] >> 4) & 0x0f).toUpperCase());
			sb.append(Integer.toHexString(data[i] & 0x0f).toUpperCase());
			
			if (++i < data.length)
				sb.append(' ');
		}
		
		return sb.toString();
	}
	
	/**
	 * Converts short code to id of JNIBWAPI.
	 * 
	 * @param the code which is to be converted
	 * @return the JNIBWAPI id. Hope so.
	 */
	
	public static int getJniBwapiID(short code) throws UnitIDException {
		// MODIFIED BY GLEN: just returns code now (this matches with BWAPI codes) unless it has
		// overflowed, in which case, return the two's complement value
		if (code < 0) {
			return code + (int) (1 << 16);
		}
		return code;
		//
		// int id = (code & 0x7FF) - 1;
		// if (id <= 1700) {
		// id = 1700 - id;
		// return (short) id;
		// }
		// else {
		// throw new UnitIDException(code, id);
		// }
	}
	
}
