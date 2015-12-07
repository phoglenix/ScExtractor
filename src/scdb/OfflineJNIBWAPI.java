package scdb;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import jnibwapi.BWAPIEventListener;
import jnibwapi.JNIBWAPI;
import jnibwapi.Position;
import jnibwapi.types.*;
import jnibwapi.types.BulletType.BulletTypes;
import jnibwapi.types.DamageType.DamageTypes;
import jnibwapi.types.ExplosionType.ExplosionTypes;
import jnibwapi.types.OrderType.OrderTypes;
import jnibwapi.types.RaceType.RaceTypes;
import jnibwapi.types.TechType.TechTypes;
import jnibwapi.types.UnitCommandType.UnitCommandTypes;
import jnibwapi.types.UnitSizeType.UnitSizeTypes;
import jnibwapi.types.UnitType.UnitTypes;
import jnibwapi.types.UpgradeType.UpgradeTypes;
import jnibwapi.types.WeaponType.WeaponTypes;

/**
 * Allows you to use JNIBWAPI types without having to load the data from StarCraft each time. This
 * still has to be connected to StarCraft once to gather all the data, at which point the data is
 * written to a file. To do this, run it as an application. Subsequent uses can simply instantiate
 * the OfflineJNIBWAPI object and it will load the type data. 
 */
@SuppressWarnings("unused")
public class OfflineJNIBWAPI extends JNIBWAPI {
	private static String FILENAME = "OfflineJNIBWAPITypeData.bin";
	private static boolean loaded = false;
	
	/**
	 * Run as a program to gather the data. Just needs to be connected once. Data will be
	 * stored as a {@link OfflineJNIBWAPIData} object and saved to disk.
	 */
	public static void main(String[] args) {
		new ConnectedListener();
	}
	
	private OfflineJNIBWAPI(BWAPIEventListener listener) {
		super(listener, false);
	}

	private void storeTypeData() {
		System.out.println("=====Connected. Gathering JNIBWAPI type data=====");
		OfflineJNIBWAPIData ojd = gatherTypeData();
		try (
				FileOutputStream fos = new FileOutputStream(FILENAME);
				ObjectOutputStream oos = new ObjectOutputStream(fos); ) {
			oos.writeObject(ojd);
			oos.close();
			System.out.println("=====Successfully wrote Offline JNIBWAPI type data=====");
		} catch (IOException e) {
			System.err.println("=====Failed to write Offline JNIBWAPI type data=====");
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private OfflineJNIBWAPIData gatherTypeData() {
		OfflineJNIBWAPIData ojd = new OfflineJNIBWAPIData();
		
		// A bunch of methods in JNIBWAPI will have to be temporarily changed to protected for
		// this to work. They will need to be uncommented if commented.
//		ojd.raceTypes = getRaceTypes();
//		ojd.raceTypeNames = new HashMap<>();
//		ojd.unitTypes = getUnitTypes();
//		ojd.unitTypeNames = new HashMap<>();
//		ojd.requiredUnits = new HashMap<>();
//		ojd.techTypes = getTechTypes();
//		ojd.techTypeNames = new HashMap<>();
//		ojd.upgradeTypes = getUpgradeTypes();
//		ojd.upgradeTypeNames = new HashMap<>();
//		ojd.weaponTypes = getWeaponTypes();
//		ojd.weaponTypeNames = new HashMap<>();
//		ojd.unitSizeTypes = getUnitSizeTypes();
//		ojd.unitSizeTypeNames = new HashMap<>();
//		ojd.bulletTypes = getBulletTypes();
//		ojd.bulletTypeNames = new HashMap<>();
//		ojd.damageTypes = getDamageTypes();
//		ojd.damageTypeNames = new HashMap<>();
//		ojd.explosionTypes = getExplosionTypes();
//		ojd.explosionTypeNames = new HashMap<>();
//		ojd.unitCommandTypes = getUnitCommandTypes();
//		ojd.unitCommandTypeNames = new HashMap<>();
//		ojd.orderTypes = getOrderTypes();
//		ojd.orderTypeNames = new HashMap<>();
//		
//		for (RaceType t : RaceTypes.getAllRaceTypes())
//			ojd.raceTypeNames.put(t.getID(), getRaceTypeName(t.getID()));
//		for (UnitType t : UnitTypes.getAllUnitTypes())
//			ojd.unitTypeNames.put(t.getID(), getUnitTypeName(t.getID()));
//		for (UnitType t : UnitTypes.getAllUnitTypes())
//			ojd.requiredUnits.put(t.getID(),  getRequiredUnits(t.getID()));
//		for (TechType t : TechTypes.getAllTechTypes())
//			ojd.techTypeNames.put(t.getID(), getTechTypeName(t.getID()));
//		for (UpgradeType t : UpgradeTypes.getAllUpgradeTypes())
//			ojd.upgradeTypeNames.put(t.getID(), getUpgradeTypeName(t.getID()));
//		for (WeaponType t : WeaponTypes.getAllWeaponTypes())
//			ojd.weaponTypeNames.put(t.getID(), getWeaponTypeName(t.getID()));
//		for (UnitSizeType t : UnitSizeTypes.getAllUnitSizeTypes())
//			ojd.unitSizeTypeNames.put(t.getID(), getUnitSizeTypeName(t.getID()));
//		for (BulletType t : BulletTypes.getAllBulletTypes())
//			ojd.bulletTypeNames.put(t.getID(), getBulletTypeName(t.getID()));
//		for (DamageType t : DamageTypes.getAllDamageTypes())
//			ojd.damageTypeNames.put(t.getID(), getDamageTypeName(t.getID()));
//		for (ExplosionType t : ExplosionTypes.getAllExplosionTypes())
//			ojd.explosionTypeNames.put(t.getID(), getExplosionTypeName(t.getID()));
//		for (UnitCommandType t : UnitCommandTypes.getAllUnitCommandTypes())
//			ojd.unitCommandTypeNames.put(t.getID(), getUnitCommandTypeName(t.getID()));
//		for (OrderType t : OrderTypes.getAllOrderTypes())
//			ojd.orderTypeNames.put(t.getID(), getOrderTypeName(t.getID()));
		
		if (ojd.raceTypes == null || ojd.raceTypes.length == 0) {
			throw new AssertionError("Type data wasn't initialised!");
		}
		return ojd;
	}
	
	public static synchronized void loadOfflineJNIBWAPIData() throws IOException {
		if (loaded) return;
		File check = new File(FILENAME);
		if (!check.exists()) {
			throw new FileNotFoundException("Cannot find Offline JNIBWAPI Data File at "
					+ check.getAbsolutePath() + ". Failed to load type data.");
		}
		try (
				FileInputStream fis = new FileInputStream(FILENAME);
				ObjectInputStream ois = new ObjectInputStream(fis); ) {
			OfflineJNIBWAPIData ojd = (OfflineJNIBWAPIData) ois.readObject();
			ois.close();
			ojd.loadTypeData();
			// Sanity check
			if (BulletTypes.Acid_Spore.getName() == null) {
				throw new AssertionError("Type data wasn't loaded properly!");
			}
			loaded = true;
		} catch (ClassNotFoundException e) {
			// shouldn't happen
			e.printStackTrace();
		}
		
	}
	
	private static class OfflineJNIBWAPIData implements Serializable {
		private static final long serialVersionUID = 1L;
		
		int[] raceTypes;
		Map<Integer, String> raceTypeNames;
		int[] unitTypes;
		Map<Integer, String> unitTypeNames;
		Map<Integer, int[]> requiredUnits;
		int[] techTypes;
		Map<Integer, String> techTypeNames;
		int[] upgradeTypes;
		Map<Integer, String> upgradeTypeNames;
		int[] weaponTypes;
		Map<Integer, String> weaponTypeNames;
		int[] unitSizeTypes;
		Map<Integer, String> unitSizeTypeNames;
		int[] bulletTypes;
		Map<Integer, String> bulletTypeNames;
		int[] damageTypes;
		Map<Integer, String> damageTypeNames;
		int[] explosionTypes;
		Map<Integer, String> explosionTypeNames;
		int[] unitCommandTypes;
		Map<Integer, String> unitCommandTypeNames;
		int[] orderTypes;
		Map<Integer, String> orderTypeNames;
		
		public int[] getRaceTypes() { return raceTypes; }
		public String getRaceTypeName(int id) { return raceTypeNames.get(id); }
		public int[] getUnitTypes() { return unitTypes; }
		public String getUnitTypeName(int id) { return unitTypeNames.get(id); }
		public int[] getRequiredUnits(int id) { return requiredUnits.get(id); }
		public int[] getTechTypes() { return techTypes; }
		public String getTechTypeName(int id) { return techTypeNames.get(id); }
		public int[] getUpgradeTypes() { return upgradeTypes; }
		public String getUpgradeTypeName(int id) { return upgradeTypeNames.get(id); }
		public int[] getWeaponTypes() { return weaponTypes; }
		public String getWeaponTypeName(int id) { return weaponTypeNames.get(id); }
		public int[] getUnitSizeTypes() { return unitSizeTypes; }
		public String getUnitSizeTypeName(int id) { return unitSizeTypeNames.get(id); }
		public int[] getBulletTypes() { return bulletTypes; }
		public String getBulletTypeName(int id) { return bulletTypeNames.get(id); }
		public int[] getDamageTypes() { return damageTypes; }
		public String getDamageTypeName(int id) { return damageTypeNames.get(id); }
		public int[] getExplosionTypes() { return explosionTypes; }
		public String getExplosionTypeName(int id) { return explosionTypeNames.get(id); }
		public int[] getUnitCommandTypes() { return unitCommandTypes; }
		public String getUnitCommandTypeName(int id) { return unitCommandTypeNames.get(id); }
		public int[] getOrderTypes() { return orderTypes; }
		public String getOrderTypeName(int id) { return orderTypeNames.get(id); }
	

		// COPIED DIRECTLY FROM JNIBWAPI
		private void loadTypeData() {
			// race types
			int[] raceTypeData = getRaceTypes();
			for (int index = 0; index < raceTypeData.length; index += RaceType.numAttributes) {
				int id = raceTypeData[index];
				RaceTypes.getRaceType(id).initialize(raceTypeData, index, getRaceTypeName(id));
			}
			
			// unit types
			int[] unitTypeData = getUnitTypes();
			for (int index = 0; index < unitTypeData.length; index += UnitType.numAttributes) {
				int id = unitTypeData[index];
				UnitTypes.getUnitType(id).initialize(unitTypeData, index, getUnitTypeName(id),
						getRequiredUnits(id));
			}
			
			// tech types
			int[] techTypeData = getTechTypes();
			for (int index = 0; index < techTypeData.length; index += TechType.numAttributes) {
				int id = techTypeData[index];
				TechTypes.getTechType(id).initialize(techTypeData, index, getTechTypeName(id));
			}
			
			// upgrade types
			int[] upgradeTypeData = getUpgradeTypes();
			for (int index = 0; index < upgradeTypeData.length; index += UpgradeType.numAttributes) {
				int id = upgradeTypeData[index];
				UpgradeTypes.getUpgradeType(id).initialize(upgradeTypeData, index, getUpgradeTypeName(id));
			}
			
			// weapon types
			int[] weaponTypeData = getWeaponTypes();
			for (int index = 0; index < weaponTypeData.length; index += WeaponType.numAttributes) {
				int id = weaponTypeData[index];
				WeaponTypes.getWeaponType(id).initialize(weaponTypeData, index, getWeaponTypeName(id));
			}
			
			// unit size types
			int[] unitSizeTypeData = getUnitSizeTypes();
			for (int index = 0; index < unitSizeTypeData.length; index += UnitSizeType.numAttributes) {
				int id = unitSizeTypeData[index];
				UnitSizeTypes.getUnitSizeType(id).initialize(unitSizeTypeData, index, getUnitSizeTypeName(id));
			}
			
			// bullet types
			int[] bulletTypeData = getBulletTypes();
			for (int index = 0; index < bulletTypeData.length; index += BulletType.numAttributes) {
				int id = bulletTypeData[index];
				BulletTypes.getBulletType(id).initialize(bulletTypeData, index, getBulletTypeName(id));
			}
			
			// damage types
			int[] damageTypeData = getDamageTypes();
			for (int index = 0; index < damageTypeData.length; index += DamageType.numAttributes) {
				int id = damageTypeData[index];
				DamageTypes.getDamageType(id).initialize(damageTypeData, index, getDamageTypeName(id));
			}
			
			// explosion types
			int[] explosionTypeData = getExplosionTypes();
			for (int index = 0; index < explosionTypeData.length; index += ExplosionType.numAttributes) {
				int id = explosionTypeData[index];
				ExplosionTypes.getExplosionType(id).initialize(explosionTypeData, index, getExplosionTypeName(id));
			}
			
			// unitCommand types
			int[] unitCommandTypeData = getUnitCommandTypes();
			for (int index = 0; index < unitCommandTypeData.length; index += UnitCommandType.numAttributes) {
				int id = unitCommandTypeData[index];
				UnitCommandTypes.getUnitCommandType(id).initialize(unitCommandTypeData, index, getUnitCommandTypeName(id));
			}
			
			// order types
			int[] orderTypeData = getOrderTypes();
			for (int index = 0; index < orderTypeData.length; index += OrderType.numAttributes) {
				int id = orderTypeData[index];
				OrderTypes.getOrderType(id).initialize(orderTypeData, index, getOrderTypeName(id));
			}
			
			// event types - no extra data to load
		}
		
	}
	
	private static class ConnectedListener implements BWAPIEventListener {
		OfflineJNIBWAPI bwapi;
		
		public ConnectedListener() {
			bwapi = new OfflineJNIBWAPI(this);
			bwapi.start();
		}
		
		@Override
		public void connected() {
			bwapi.storeTypeData();
		}
		
		@Override
		public void matchStart() {}
		@Override
		public void matchFrame() {}
		@Override
		public void keyPressed(int keyCode) {}
		@Override
		public void matchEnd(boolean winner) {}
		@Override
		public void sendText(String text) {}
		@Override
		public void receiveText(String text) {}
		@Override
		public void nukeDetect(Position p) {}
		@Override
		public void nukeDetect() {}
		@Override
		public void playerLeft(int playerID) {}
		@Override
		public void unitCreate(int unitID) {}
		@Override
		public void unitDestroy(int unitID) {}
		@Override
		public void unitDiscover(int unitID) {}
		@Override
		public void unitEvade(int unitID) {}
		@Override
		public void unitHide(int unitID) {}
		@Override
		public void unitMorph(int unitID) {}
		@Override
		public void unitShow(int unitID) {}
		@Override
		public void unitRenegade(int unitID) {}
		@Override
		public void saveGame(String gameName) {}
		@Override
		public void unitComplete(int unitID) {}
		@Override
		public void playerDropped(int playerID) {}		
	}
}
