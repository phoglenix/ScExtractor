package util;

import java.util.Arrays;
import java.util.Map;

import jnibwapi.model.Unit;

/**
 * Stores the attributes of a Unit as a simple int array so that they can be easily iterated over
 * and compared. <b>Note:</b> the attributes which normally give a unit's ID will instead be set to
 * the unit's replayId because unit IDs vary from game to game and are not stored in the DB.
 */
public class UnitAttributes {
	public static int NUM_ATTRIBUTES = 101;
	/** First 58 attributes match Stefan's. Rest are added by Glen */
	public int[] attributes = new int[NUM_ATTRIBUTES];
	
	/** Default constructor with 0 for all attributes. */
	public UnitAttributes() {}
	
	/**
	 * Constructs a UnitAttributes with the attributes of the given Unit. Requires a map of unit IDs
	 * to Units to convert unit IDs to unit replayIDs (which are fixed over multiple games).
	 */
	@SuppressWarnings("deprecation")
	public UnitAttributes(Unit u, Map<Integer, Unit> idToUnit) {
		// Stefan's unit attributes:
		attributes[0] = u.getX();
		attributes[1] = u.getY();
		attributes[2] = (int) (u.getVelocityX() * Unit.fixedScale);
		attributes[3] = (int) (u.getVelocityY() * Unit.fixedScale);
		attributes[4] = (int) (u.getAngle() * Unit.TO_DEGREES);
		attributes[5] = u.getHitPoints();
		attributes[6] = u.getShields();
		attributes[7] = u.getEnergy();
		attributes[8] = u.getKillCount();
		attributes[9] = u.getAcidSporeCount();
		attributes[10] = u.getInterceptorCount();
		attributes[11] = u.getScarabCount();
		attributes[12] = u.getSpiderMineCount();
		attributes[13] = u.getGroundWeaponCooldown();
		attributes[14] = u.getAirWeaponCooldown();
		attributes[15] = u.getSpellCooldown();
		attributes[16] = u.getDefenseMatrixPoints();
		attributes[17] = u.getDefenseMatrixTimer();
		attributes[18] = u.getEnsnareTimer();
		attributes[19] = u.getIrradiateTimer();
		attributes[20] = u.getLockdownTimer();
		attributes[21] = u.getMaelstromTimer();
		attributes[22] = u.getPlagueTimer();
		attributes[23] = u.getRemoveTimer();
		attributes[24] = u.getStasisTimer();
		attributes[25] = safeGetUnitReplayId(u.getTargetUnitID(), idToUnit);
		attributes[26] = u.getOrderID();
		attributes[27] = u.isAttackFrame() ? 1 : 0;
		attributes[28] = u.isExists() ? 1 : 0;
		attributes[29] = u.getStimTimer();
		attributes[30] = u.getBuildTypeID();
		attributes[31] = u.getTargetX();
		attributes[32] = u.getTargetY();
		attributes[33] = safeGetUnitReplayId(u.getOrderTargetUnitID(), idToUnit);
		attributes[34] = u.getSecondaryOrderID();
		attributes[35] = safeGetUnitReplayId(u.getTransportUnitID(), idToUnit);
		attributes[36] = u.isBlind() ? 1 : 0;
		attributes[37] = u.isBurrowed() ? 1 : 0;
		attributes[38] = u.isCarryingGas() ? 1 : 0;
		attributes[39] = u.isCarryingMinerals() ? 1 : 0;
		attributes[40] = u.isCloaked() ? 1 : 0;
		attributes[41] = u.isConstructing() ? 1 : 0;
		attributes[42] = u.isDetected() ? 1 : 0;
		attributes[43] = u.isGatheringGas() ? 1 : 0;
		attributes[44] = u.isGatheringMinerals() ? 1 : 0;
		attributes[45] = u.isInvincible() ? 1 : 0;
		attributes[46] = u.isLifted() ? 1 : 0;
		attributes[47] = u.isMorphing() ? 1 : 0;
		attributes[48] = u.isParasited() ? 1 : 0;
		attributes[49] = u.isPatrolling() ? 1 : 0;
		attributes[50] = u.isRepairing() ? 1 : 0;
		attributes[51] = u.isSieged() ? 1 : 0;
		attributes[52] = u.isStuck() ? 1 : 0;
		attributes[53] = u.isUnderAttack() ? 1 : 0;
		attributes[54] = u.isUnderDarkSwarm() ? 1 : 0;
		attributes[55] = u.isUnderDisruptionWeb() ? 1 : 0;
		attributes[56] = u.isUnderStorm() ? 1 : 0;
		attributes[57] = u.isUnpowered() ? 1 : 0;
		// Added by Glen
		attributes[58] = u.getOrderTimer();
		attributes[59] = u.getTrainingQueueSize();
		attributes[60] = u.getResearchingTechID();
		attributes[61] = u.getUpgradingUpgradeID();
		attributes[62] = u.getRemainingBuildTimer();
		attributes[63] = u.getRemainingTrainTime();
		attributes[64] = u.getRemainingResearchTime();
		attributes[65] = u.getRemainingUpgradeTime();
		attributes[66] = safeGetUnitReplayId(u.getBuildUnitID(), idToUnit);
		attributes[67] = u.getRallyX();
		attributes[68] = u.getRallyY();
		attributes[69] = safeGetUnitReplayId(u.getRallyUnitID(), idToUnit);
		attributes[70] = u.getLoadedUnitsCount();
		attributes[71] = u.getLarvaCount();
		attributes[72] = u.isNukeReady() ? 1 : 0;
		attributes[73] = u.isAccelerating() ? 1 : 0;
		attributes[74] = u.isAttacking() ? 1 : 0;
		attributes[75] = u.isAttackFrame() ? 1 : 0;
		attributes[76] = u.isBeingConstructed() ? 1 : 0;
		attributes[77] = u.isBeingGathered() ? 1 : 0;
		attributes[78] = u.isBeingHealed() ? 1 : 0;
		attributes[79] = u.isBraking() ? 1 : 0;
		attributes[80] = u.isCompleted() ? 1 : 0;
		attributes[81] = u.isDefenseMatrixed() ? 1 : 0;
		attributes[82] = u.isEnsnared() ? 1 : 0;
		attributes[83] = u.isFollowing() ? 1 : 0;
		attributes[84] = u.isHallucination() ? 1 : 0;
		attributes[85] = u.isHoldingPosition() ? 1 : 0;
		attributes[86] = u.isIdle() ? 1 : 0;
		attributes[87] = u.isInterruptable() ? 1 : 0;
		attributes[88] = u.isIrradiated() ? 1 : 0;
		attributes[89] = u.isLoaded() ? 1 : 0;
		attributes[90] = u.isLockedDown() ? 1 : 0;
		attributes[91] = u.isMaelstrommed() ? 1 : 0;
		attributes[92] = u.isMoving() ? 1 : 0;
		attributes[93] = u.isPlagued() ? 1 : 0;
		attributes[94] = u.isStartingAttack() ? 1 : 0;
		attributes[95] = u.isStasised() ? 1 : 0;
		attributes[96] = u.isStimmed() ? 1 : 0;
		attributes[97] = u.isTraining() ? 1 : 0;
		attributes[98] = u.isUpgrading() ? 1 : 0;
		attributes[99] = u.getTypeID();
		attributes[100] = safeGetUnitReplayId(u.getAddOnUnitID(), idToUnit);
		// Note doesn't store player id so be careful if renegaded 
	}
	
	private static int safeGetUnitReplayId(int unitId, Map<Integer, Unit> idToUnit) {
		return idToUnit.get(unitId) != null ? idToUnit.get(unitId).getReplayID() : -1;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(attributes);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnitAttributes other = (UnitAttributes) obj;
		if (!Arrays.equals(attributes, other.attributes))
			return false;
		return true;
	}
	
	
}
