package extractor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import replayparser.control.BinRepParser;
import replayparser.model.Player;
import replayparser.model.RPAction;
import replayparser.model.RPAction.ReplayActions;
import replayparser.model.Replay;
import util.DbConnection;
import util.LogManager;
import util.Util;

/**
 * A Java BWAPI client which extracts actions from replays directly (using ReplayParser) and
 * extracts States via BWAPI. Stores the output in a database.
 * 
 * @author Glen Robertson
 */
public class ExtractActions {
	private static final Logger LOGGER = Logger.getLogger(ExtractActions.class.getName());
	/** How many control groups a player can have in-game */
	private static final int NUM_CONTROL_GROUPS = 10;
	/** Properties file to load */
	private static final String PROPERTIES_FILENAME = "extractorConfig.properties";
	/** Skip all replays with names that come before this. Leave blank to skip none. */
	private final String firstToParse; // eg. GG14816.rep
	/** The maximum number of extra/orphaned database entries to remove from one cleanup action */
	private final int maxNumExtrasToRemove;
	
	private final File[] replays;
	
	public static void main(String[] args) {
		// Start the logger
		LogManager.initialise("ExtractActions");
		try {
			ExtractActions ea = new ExtractActions();
			ea.start();
		} catch (IOException | SQLException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	/** Instantiates the Extractor */
	public ExtractActions() throws IOException {
		Properties props = Util.loadProperties(PROPERTIES_FILENAME);
		String replayFolderName = Util.getPropertyNotNull(props, "replay_folder");
		firstToParse = Util.getPropertyNotNull(props, "ea_first_to_parse");
		maxNumExtrasToRemove = Integer.parseInt(
				Util.getPropertyNotNull(props, "ea_max_num_extras_to_remove"));
		
		LOGGER.info("Opening and checking folders");
		final File replayFolder = new File(replayFolderName);
		if (!replayFolder.canRead()) {
			throw new IOException("Cannot read '" + replayFolder.getAbsolutePath() + "'");
		}
		if (!replayFolder.isDirectory()) {
			throw new IOException("'" + replayFolder.getAbsolutePath() + "' is not a folder.");
		}
		replays = replayFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".rep");
			}
		});
		if (replays == null || replays.length == 0) {
			throw new IOException("'" + replayFolder.getAbsolutePath()
					+ "' contains no .rep files.");
		}
	}
	
	/** Start the Extractor. This returns only once finished. */
	public void start() throws IOException, SQLException {
		DbConnection dbc = new DbConnection();
		// Read the actions from all replay files in the folder
		// Then add them all to the database
		for (File f : replays) {
			if (firstToParse.compareTo(f.getName()) > 0) {
				// Skip everything up to the given file
				LOGGER.info("Skipping " + f.getName());
				continue;
			}
			LOGGER.info("Processing " + f.getName());
			// Load and analyse the replay file
			Replay replay = BinRepParser.parseReplay(f, true, false, true, false);
			if (replay != null) {
				// Convert control groups into selects before removing extra selects
				removeControlGroups(replay);
				// Remove selects before removing players, as observers may still select a lot
				removeExtraSelects(replay);
				// Remove observers before determining winner
				removeExtraPlayers(replay);
				// Determine winner before removing non-BWAPI actions (like leaveGame)
				Player winner = determineWinner(replay);
				// Ignore Ally/Vision/Ping/Chat etc for now
				removeNonBwapiActions(replay);
				// Remove selects again now that more actions are removed
				removeExtraSelects(replay);
				storeToDatabase(dbc, replay, f.getName(), winner);
			} else {
				LOGGER.warning("The replay '" + f.getAbsolutePath() + "' could not be loaded.");
			}
		}
		cleanupExtraUnitGroups();
		dbc.close();
		LOGGER.info("Done");
	}
	
	/**
	 * Remove hotkey and extra select actions to simplify the model. After this is run, all hotkey,
	 * shift-select, and shift-deselect actions will be replaced by regular select actions, and any
	 * sequences of 2+ select actions will be replaced by just the last select action.
	 */
	private void removeControlGroups(Replay replay) {
		for (Player player : replay.players) {
			// Initialise the control groups & selected units
			final List<ControlGroup> controlGroups = new ArrayList<>(NUM_CONTROL_GROUPS);
			final Set<Integer> selectedUnitIds = new HashSet<>();
			for (int i = 0; i < NUM_CONTROL_GROUPS; i++) {
				controlGroups.add(new ControlGroup());
			}
			
			// For each action
			int lastFrame = 0;
			for (int i = 0; i < player.actions.size(); i++) {
				RPAction action = player.actions.get(i);
				// Check the frames are actually in increasing order
				if (action.frame < lastFrame) {
					LOGGER.severe("Frames not in order!");
				}
				lastFrame = action.frame;
				
				if (action.rAction == RPAction.ReplayActions.Hotkey) {
					String[] params = action.stringParams.split(",");
					if (params.length != 2) {
						LOGGER.severe("Hotkey action with " + params.length + " params skipped.");
						continue;
					}
					if (RPAction.HOTKEY_ACTION_SELECT.equals(params[0])) {
						selectedUnitIds.clear();
						int groupNum = Integer.parseInt(params[1]);
						boolean skip = false;
						if (groupNum >= NUM_CONTROL_GROUPS) {
							LOGGER.warning("Skipped control group select of group #" + groupNum);
							skip = true;
						} else {
							selectedUnitIds.addAll(controlGroups.get(groupNum).unitIds);
							if (selectedUnitIds.isEmpty()) {
								LOGGER.info("Skipped selecting empty control group:" + groupNum);
								skip = true;
								// Note this won't catch the case where a control group is full of
								// dead units and is selected. In game there will be no effect but
								// here we will make it so that the player has selected a bunch of
								// dead units.
							}
						}
						if (skip) {
							// Remove the hotkey select and move the index back
							player.actions.remove(i);
							i--;
						} else {
							// Replace the Hotkey select with a regular unit select
							player.actions.set(i, new RPAction(action.frame, "",
									RPAction.ReplayActions.Select,
									new HashSet<Integer>(selectedUnitIds)));
						}
					} else if (RPAction.HOTKEY_ACTION_ASSIGN.equals(params[0])) {
						int groupNum = Integer.parseInt(params[1]);
						controlGroups.get(groupNum).unitIds.clear();
						controlGroups.get(groupNum).unitIds.addAll(selectedUnitIds);
						// Remove the hotkey assign and move the index back
						player.actions.remove(i);
						i--;
					} else {
						LOGGER.severe("Hotkey action wasn't select or assign?!");
						continue;
					}
				} else if (action.rAction == RPAction.ReplayActions.Select) {
					selectedUnitIds.clear();
					selectedUnitIds.addAll(action.selectedUnitIds);
				} else if (action.rAction == RPAction.ReplayActions.ShiftSelect) {
					selectedUnitIds.addAll(action.selectedUnitIds);
					// Replace the shift-select with a regular unit select
					player.actions.set(i, new RPAction(action.frame, "",
							RPAction.ReplayActions.Select, new HashSet<Integer>(selectedUnitIds)));
				} else if (action.rAction == RPAction.ReplayActions.ShiftDeselect) {
					selectedUnitIds.removeAll(action.selectedUnitIds);
					// Replace the shift-deselect with a regular unit select
					player.actions.set(i, new RPAction(action.frame, "",
							RPAction.ReplayActions.Select, new HashSet<Integer>(selectedUnitIds)));
				}
			}
		}
	}
	
	/**
	 * Remove sequences of 2+ selects in a row, leaving just the last select in each sequence. These
	 * repeated select actions cannot have any effect (usually just a result of the player checking
	 * on their units).
	 */
	private void removeExtraSelects(Replay replay) {
		for (Player player : replay.players) {
			for (int i = 0; i + 1 < player.actions.size(); i++) {
				RPAction current = player.actions.get(i);
				RPAction next = player.actions.get(i + 1);
				if (current.rAction == RPAction.ReplayActions.Select
						&& next.rAction == RPAction.ReplayActions.Select) {
					// Remove and decrease i accordingly
					player.actions.remove(i);
					i--;
				}
			}
			// Remove trailing select
			int end = player.actions.size() - 1;
			if (end >= 0 && player.actions.get(end).rAction == RPAction.ReplayActions.Select) {
				player.actions.remove(end);
			}
		}
	}
	
	/**
	 * Assuming a two player game. If there are more than two players, this removes the ones with
	 * the fewest actions (probably an observer).
	 */
	private void removeExtraPlayers(Replay replay) {
		// Initialise playerToActionList
		// Ensure only 2 player replays (at least for now)
		if (replay.players.size() > 2) {
			String actCounts = "";
			for (Player p : replay.players) {
				actCounts += p.actions.size() + " ";
			}
			
			// Record the maximum action count of removed players, and compare to the minimum
			// action count of remaining players (safety check)
			int maxActionsRemoved = 0;
			int minActionsRemaining = Integer.MAX_VALUE;
			
			// Find and remove low-action count players until 2 remain
			while (replay.players.size() > 2) {
				int minActions = Integer.MAX_VALUE;
				Player minPlayer = null;
				for (Player p : replay.players) {
					if (p.actions.size() < minActions) {
						minActions = p.actions.size();
						minPlayer = p;
					}
				}
				replay.players.remove(minPlayer);
				maxActionsRemoved = Math.max(maxActionsRemoved, minActions);
				LOGGER.fine("Removed: " + minPlayer + " with " + minActions + " actions");
			}
			
			// Log outcome when players have been removed
			String newActCounts = "";
			for (Player p : replay.players) {
				newActCounts += p.actions.size() + " ";
				minActionsRemaining = Math.min(minActionsRemaining, p.actions.size());
			}
			
			LOGGER.info("More than 2 players. Removed all but the most active 2\n" +
					" Action counts: [ " + actCounts + "] => [ " + newActCounts + "]");
			if (maxActionsRemoved > minActionsRemaining / 2) {
				LOGGER.severe("May have actually been a 3+ player game!");
			}
		}
	}
	
	/**
	 * Returns the winning player in a game, by finding the last player to leave. Notes:
	 * <ul>
	 * <li>this may return an observer if they are still in the players list when called</li>
	 * <li>this will return null if no player is a clear winner (sometimes the match ends without
	 * any players leaving, somehow) - includes team games where there may legitimately be more than
	 * one winner</li>
	 * </ul>
	 */
	private Player determineWinner(Replay replay) {
		Player winner = null;
		int lastLeaveTime = 0;
		for (Player p : replay.players) {
			boolean leaveGameActionFound = false;
			for (RPAction a : p.actions) {
				if (a.rAction == ReplayActions.Leave_Game) {
					leaveGameActionFound = true;
					if (a.frame > lastLeaveTime) {
						winner = p;
						lastLeaveTime = a.frame;
					}
				}
			}
			if (!leaveGameActionFound) {
				// Player never left, must be winner unless other player(s) never left
				if (lastLeaveTime < Integer.MAX_VALUE) {
					winner = p;
					lastLeaveTime = Integer.MAX_VALUE;
				} else {
					// Two players never left! Can't determine winner. Seems to happen quite often.
					return null;
				}
			}
		}
		return winner;
	}
	
	/**
	 * Remove actions containing ReplayActions (not convertable to BWAPI-compatible actions) except
	 * SELECT, leaving only BWAPI-compatible actions (Orders, UnitCommands, and SELECT).
	 */
	private void removeNonBwapiActions(Replay replay) {
		for (Player player : replay.players) {
			// Go through and remove ReplayActions besides SELECT
			for (int i = 0; i < player.actions.size(); i++) {
				RPAction current = player.actions.get(i);
				if (current.rAction != RPAction.ReplayActions.None
						&& current.rAction != RPAction.ReplayActions.Select) {
					// Remove and decrease i accordingly
					player.actions.remove(i);
					i--;
					// Notify about skipping interesting actions
					if (current.rAction != RPAction.ReplayActions.Ally
							&& current.rAction != RPAction.ReplayActions.Game_Chat
							&& current.rAction != RPAction.ReplayActions.Leave_Game
							&& current.rAction != RPAction.ReplayActions.Minimap_Ping
							&& current.rAction != RPAction.ReplayActions.Vision) {
						LOGGER.info("Skipped potentially interesting action: " + current);
					}
				}
			}
		}
	}
	
	private void storeToDatabase(DbConnection dbc, Replay replay, String fileName, Player winner) {
		try {
			List<Object> data = new ArrayList<>();
			Set<Long> allDbPlayerReplayIds = new HashSet<>();
			// Add Replay
			data.clear();
			data.add(fileName);
			data.add(replay.header.gameFrames);
			long replayId = dbc.executeInsert(
					"INSERT INTO replay (replayname, duration) VALUES (?, ?)", data, true);
			// Add PlayerReplay
			for (Player player : replay.players) {
				// Store all actionIds for this playerreplay
				Set<Long> allDbActionIds = new HashSet<>();
				// Store all unitIds for this playerreplay
				Set<Long> allDbUnitIds = new HashSet<>();
				data.clear();
				data.add(player.name);
				data.add(player == winner);
				data.add(player.race);
				data.add(replayId);
				// Getting StartLocations and BuildTiles from BWAPI instead of the replay
				// so don't include StartPositionId here
				
				long playerReplayId = dbc.executeInsert(
						"INSERT INTO playerreplay (playername, winner, raceid, replayid)"
								+ " VALUES (?, ?, ?, ?)", data, true);
				if (playerReplayId == -1) {
					LOGGER.severe("Couldn't get/insert playerreplay: " + Util.join(data));
					return;
				}
				allDbPlayerReplayIds.add(playerReplayId);
				
				long lastSelectedGroupId = -1;
				
				// Add the player's Actions
				for (RPAction action : player.actions) {
					
					// The only ReplayAction at this point should be Select
					if (action.rAction == ReplayActions.Select) {
						List<Long> dbUnitIds = new ArrayList<>();
						// Add the action's units
						for (int repUnitId : action.selectedUnitIds) {
							data.clear();
							data.add(playerReplayId);
							// Getting UnitTypes from BWAPI instead of the replay so leave as
							// default here (DB will default to UnitTypes.None == 228)
							data.add(repUnitId);
							long dbUnitId = dbc.executeInsert("INSERT INTO unit (playerreplayid," +
									" unitreplayid) VALUES (?, ?)", data, true);
							dbUnitIds.add(dbUnitId);
						}
						allDbUnitIds.addAll(dbUnitIds);
						// Find the group if it exists already
						// Works by finding a unitgroupid associated with all units in the group
						// (and no units not in the group, using the "having count" part)
						// NOTE: Groups can have more than 12 units because if a unit dies it isn't
						// recorded in the replay, so you can keep adding units to a group as the
						// old units die off. Groups can also have 0 units (eg. in GG11.rep)
						data.clear();
						data.addAll(dbUnitIds);
						String query = "SELECT unitgroupid FROM unitgroup ";
						if (dbUnitIds.size() > 0) {
							query += "WHERE ";
							for (int i = 0; i < dbUnitIds.size() - 1; i++) {
								query += "unitid=? OR ";
							}
							query += "unitid=? ";
						}
						data.add(dbUnitIds.size());
						query += "GROUP BY unitgroupid HAVING COUNT(unitid)=?";
						long groupId = dbc.queryFirstColumn(query, data);
						// Add the group if it wasn't found
						if (groupId == -1) {
							for (long dbUnitId : dbUnitIds) {
								data.clear();
								if (groupId == -1) {
									data.add(null);
								} else {
									data.add(groupId);
								}
								data.add(dbUnitId);
								groupId = dbc.executeInsert("INSERT INTO unitgroup " +
										"(unitgroupid, unitid) VALUES (?, ?)", data, false);
							}
						}
						if (groupId == -1) {
							// Group can't be made, usually because it's empty
							int num = player.actions.indexOf(action);
							LOGGER.warning("GroupID was -1 for action #" + num + " of "
									+ player.actions.size() + " at frame " + action.frame
									+ " with " + dbUnitIds.size() + " units selected");
							// Next action will have to reuse previous action's unit group because
							// database schema doesn't allow actions without a unit group (in
							// practice this never seems to happen, provided all RPActions are
							// removed)
							continue;
						}
						
						// Store the group ID so subsequent actions can refer to it
						lastSelectedGroupId = groupId;
						continue;
					}
					// Any Action containing a ReplayAction other than None here is unexpected
					if (action.rAction != ReplayActions.None) {
						LOGGER.warning("An action: " + action + " was ignored because it had a new"
								+ " ReplayAction");
						continue;
					}
					// Store the action
					data.clear();
					data.add(playerReplayId);
					data.add(action.frame);
					data.add(action.unitCommand.getID());
					data.add(action.order.getID());
					data.add(lastSelectedGroupId);
					data.add(action.targetId);
					data.add(action.x);
					data.add(action.y);
					data.add(action.delayedAction);
					long dbActionId = dbc.executeInsert("INSERT INTO action (playerreplayid, " +
							"frame, unitcommandtypeid, ordertypeid, unitgroupid, targetid, " +
							"targetx, targety, `delayed`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
							data, true); // Note delayed is a keyword in mysql so needs quotes
					if (dbActionId == -1) {
						LOGGER.warning("ActionID result was -1");
					} else {
						allDbActionIds.add(dbActionId);
					}
				} // foreach Action
				
				// Remove extra actions
				dbc.findRemoveExtras("actionid", "action", "playerreplayid=?", playerReplayId,
						allDbActionIds, maxNumExtrasToRemove);
				
				// Remove extra units
				// (only remove units added by ExtractActions - their unittypeid will be "None")
				dbc.findRemoveExtras("unitid", "unit", "playerreplayid=? AND unittypeid=228",
						playerReplayId, allDbUnitIds, maxNumExtrasToRemove);
				
			} // foreach Player
			
			// Remove extra PlayerReplays
			dbc.findRemoveExtras("playerreplayid", "playerreplay",
					"replayid=? AND playername<>'Neutral'", replayId, allDbPlayerReplayIds,
					maxNumExtrasToRemove);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	/**
	 * Remove unit groups which aren't used by any actions. If less than {@link #maxNumExtrasToRemove}
	 * are found, this will also delete the rows.
	 */
	private void cleanupExtraUnitGroups() {
		try (DbConnection dbc = new DbConnection();) {
			List<Object> data = new ArrayList<>();
			
			String orphanUnitGroups = "unitgroup LEFT JOIN action " +
					"ON unitgroup.unitgroupid=action.unitgroupid " +
					"WHERE action.actionid IS NULL";
			ResultSet rs = dbc.executeQuery(
					"SELECT DISTINCT unitgroup.unitgroupid FROM " + orphanUnitGroups, data);
			List<Long> orphans = new ArrayList<>();
			while (rs.next()) {
				orphans.add(rs.getLong("unitgroupid"));
			}
			if (orphans.size() > 0) {
				if (orphans.size() > maxNumExtrasToRemove) {
					LOGGER.warning("Removing orphaned unitgroups: " + Util.join(orphans));
					dbc.executeDelete("DELETE unitgroup FROM " + orphanUnitGroups, data);
				} else {
					LOGGER.warning("Found orphaned unitgroups: " + Util.join(orphans));
				}
			}
		} catch (SQLException | IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	/** Convenience class for working with control groups. */
	private static class ControlGroup {
		public List<Integer> unitIds;
		
		public ControlGroup() {
			unitIds = new ArrayList<Integer>();
		}
	}
}
