///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//		Starcraft Brood War bot By Nathan Burke
//		Built off the tutorial page given by http://sscaitournament.com/index.php?action=tutorial
//
//
//		Jan 30: - started work on bot, managed to get probes to build pylons when nearing max supply
//
//		Jan 31: - Made a Build order, for some reason it goes through the build function a bunch of times before building which slows the game, might be a minerals problem
//
//
//
//		TODO - Create a check for placing buildings better than the given tutorial one - using games default ai for this atm
//			 - Make a way to send probes to build early
//			 - Crazy Lag when my base is being destroyed find out why
//			 - build on scout function to place pylon
//
//		GOAL - Beat Starcraft AI
//			 - Consistently defeat Brendan
//
//		BUGS - Fixed bug where initial pylon wasst being built, caused because I wasnt updating the eff resources after every purchase
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

	private class DrawInfo {
		private Color color;
		private boolean opaque = false;
		private Position pos1;
		private Position pos2;

		private DrawInfo(Position position, Position position2, Color c, boolean b) {
			pos1 = position;
			pos2 = position2;
			color = c;
			opaque = b;
		}
	}

	public static void main(String[] args) {
		new TestBot1().run();
	}

	private Position scoutX, scoutY, dest;

	private ArrayList<UnitType> buildQueue;
	private int effMinerals, effGas; // Amount of minerals/gas with planned
										// spending

	private HashSet<Position> enemyBuildingMemory; // remember where enemy
													// buildings are

	private ArrayList<Position> expansionLocations;

	private Game game;
	private Unit idleGateway, idleRoboticsFacility; // idle Production Buildings

	private Mirror mirror = new Mirror();
	private HashMap<UnitType, Integer> numUnits; // used to track amount of
													// units of various types
	private int plannedMins, plannedGas;
	// private Unit builderUnit,scoutUnit;
	private Unit scoutUnit, builderProbe;

	private Player self;

	ArrayList<DrawInfo> shapes;

	private ArrayList<Position> startLocations; // log all possible base start
												// locations
	// BE AGRESSIVE RAWR

	private void beAggressive() {
		for (Unit myUnit : self.getUnits()) {
			if (!myUnit.getType().isBuilding() && !myUnit.getType().isWorker() && myUnit.isIdle()
					&& myUnit.isCompleted()) {
				for (Position p :enemyBuildingMemory)
				{
					myUnit.attack(p);
					/*if (enemyUnit.getType() == UnitType.Zerg_Lurker) {
						if (!myUnit.canAttack())
							myUnit.move(enemyUnit.getPosition());
					}*/
				}
			}

		}
	}

	// Build building
	private void build(Unit myUnit, UnitType building, TilePosition pos) {
		if (myUnit.isInterruptible() && !myUnit.isConstructing()) {
			plannedMins = building.mineralPrice();
			plannedGas = building.gasPrice();
		}

		calcEffResources();

		if (effMinerals >= 0 && effGas >= 0) {
			// Im guessing theres uninterruptible frames whilst mining which was
			// causing a bug
			if (myUnit.isInterruptible() && !myUnit.isConstructing() && myUnit.canBuild(building)) {
				// TilePosition buildTile = getBuildTile(myUnit, building,
				// self.getStartLocation());
				TilePosition buildTile;

				buildTile = game.getBuildLocation(building, pos);

				if (buildTile != null) {
					myUnit.build(building, buildTile);
					TilePosition p = new TilePosition(buildTile.getX() + building.tileWidth(),
							buildTile.getY() + building.tileHeight());
					shapes.add(new DrawInfo(buildTile.toPosition(), p.toPosition(), Color.Brown, false));
				}
			}
			if (!buildQueue.isEmpty())
				buildQueue.remove(0);
		}
	}

	private void calcEffResources() {
		effMinerals = self.minerals() - plannedMins;
		effGas = self.gas() - plannedGas;
	}

	private void checkEnemies() {
		// always loop over all currently visible enemy units (even though this
		// set is usually empty)
		for (Unit u : game.enemy().getUnits()) {
			// if this unit is in fact a building
			if (u.getType().isBuilding()) {
				// check if we have it's position in memory and add it if we
				// don't
				if (!enemyBuildingMemory.contains(u.getPosition()))
				{game.sendText("added" + u.getPosition());
					enemyBuildingMemory.add(u.getPosition());
				}
			}
		}

		// loop over all the positions that we remember
		for (Position p : enemyBuildingMemory) {
			// compute the TilePosition corresponding to our remembered Position
			//TilePosition tileCorrespondingToP = new TilePosition(p.getX() / 32, p.getY() / 32);
				TilePosition tileCorrespondingToP = p.toTilePosition();
				
			// if that tile is currently visible to us...
			/*if (game.isVisible(tileCorrespondingToP)) {
				// loop over all the visible enemy buildings and find out if at
				// least
				// one of them is still at that remembered position
				boolean buildingStillThere = false;
				for (Unit u : game.enemy().getUnits()) {
					if ((u.getType().isBuilding()) && (u.getPosition() == p)) {
						buildingStillThere = true;
						break;
					}
				}

				// if there is no more any building, remove that position from
				// our memory
				if (buildingStillThere == false) {
					enemyBuildingMemory.remove(p);
					break;
				}
			}*/
		}
	}

	private void executebuildOrder() {
		// make pylons when nearing max supply
		builderProbe = getProbe();
		
		if (self.supplyTotal() - self.supplyUsed() <= 2 && !isBeingConstructed(UnitType.Protoss_Pylon)
				&& self.supplyTotal() < 400) {
			if (!buildQueue.contains(UnitType.Protoss_Pylon))
				buildQueue.add(UnitType.Protoss_Pylon);
		}

		if (self.supplyUsed() >= 16) {
			scout(scoutUnit);
		}

		// maybe do get building calls earlier instead of searching multiple
		// times
		if (numUnits.get(UnitType.Protoss_Nexus) > 0 && self.supplyTotal() > UnitType.Protoss_Nexus.supplyProvided()
				&& numUnits.get(UnitType.Protoss_Gateway) + numQueued(buildQueue, UnitType.Protoss_Gateway) < 3) {
			buildQueue.add(UnitType.Protoss_Gateway);
		}
		if (idleGateway != null && self.supplyTotal() - self.supplyUsed() >= 2
				&& numUnits.get(UnitType.Protoss_Zealot) < 12 && !buildQueue.contains(UnitType.Protoss_Zealot)) {
			buildQueue.add(UnitType.Protoss_Zealot);
		}
		if (idleGateway != null && self.supplyTotal() - self.supplyUsed() >= 2
				&& numUnits.get(UnitType.Protoss_Dragoon) < 12 && !buildQueue.contains(UnitType.Protoss_Dragoon)) {
			buildQueue.add(UnitType.Protoss_Dragoon);
		}
		if (numUnits.get(UnitType.Protoss_Gateway) >= 1 && numUnits.get(UnitType.Protoss_Assimilator)
				+ numQueued(buildQueue, UnitType.Protoss_Assimilator) < 1) {
			buildQueue.add(UnitType.Protoss_Assimilator);
		}
		if (numUnits.get(UnitType.Protoss_Gateway) >= 1 && numUnits.get(UnitType.Protoss_Cybernetics_Core)
				+ numQueued(buildQueue, UnitType.Protoss_Cybernetics_Core) == 0) {
			buildQueue.add(UnitType.Protoss_Cybernetics_Core);
		}
		if (numUnits.get(UnitType.Protoss_Cybernetics_Core) >= 1 && numUnits.get(UnitType.Protoss_Robotics_Facility)
				+ numQueued(buildQueue, UnitType.Protoss_Robotics_Facility) == 0) {
			buildQueue.add(UnitType.Protoss_Robotics_Facility);
		}
		if (numUnits.get(UnitType.Protoss_Robotics_Facility) >= 1 && numUnits.get(UnitType.Protoss_Observatory)
				+ numQueued(buildQueue, UnitType.Protoss_Observatory) == 0) {
			buildQueue.add(UnitType.Protoss_Observatory);
		}
		if (idleRoboticsFacility != null && idleRoboticsFacility.canTrain(UnitType.Protoss_Observer)
				&& self.supplyTotal() - self.supplyUsed() >= UnitType.Protoss_Observer.supplyRequired()
				&& numUnits.get(UnitType.Protoss_Observer) < 1 && !buildQueue.contains(UnitType.Protoss_Observer)) {
			buildQueue.add(UnitType.Protoss_Observer);
		}
		if (!buildQueue.isEmpty()) {
			if (buildQueue.get(0).isBuilding()) {
				// if (!builderUnit.isConstructing() &&
				// builderUnit.isInterruptible())
				// {
				if (builderProbe != null)
				{
					//Position buildX = new Position(builderProbe.getPosition().getX() - 25, builderProbe.getPosition().getY() - 25);
					//Position buildY = new Position(builderProbe.getPosition().getX() + 25, builderProbe.getPosition().getY() + 25);
					game.drawBoxMap(new Position(builderProbe.getPosition().getX() - 25, builderProbe.getPosition().getY() - 25), new Position(builderProbe.getPosition().getX() + 25, builderProbe.getPosition().getY() + 25), Color.Blue);
					build(builderProbe, buildQueue.get(0), self.getStartLocation());
				}
				// }
			} else {
				trainUnit(buildQueue.get(0));
			}
		}
	}

	private Position findNearestExpansion(TilePosition startPos) {
		double dist, pos;
		Position nearest = self.getStartLocation().toPosition();

		if (!expansionLocations.isEmpty()) {
			dist = BWTA.getGroundDistance(startPos, expansionLocations.get(0).toTilePosition());

			for (int i = 0; i < expansionLocations.size(); i++) {
				pos = BWTA.getGroundDistance(startPos, expansionLocations.get(i).toTilePosition());
				if (pos < dist) {
					dist = BWTA.getGroundDistance(self.getStartLocation(), expansionLocations.get(i).toTilePosition());
					nearest = expansionLocations.get(i);
				}
			}
		}
		return nearest;
	}

	private void findScout() {
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType().isWorker()) {
				scoutUnit = myUnit;
				break;
			}
		}
	}

	private void getBaseLocations() {
		for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
			if (baseLocation.isStartLocation()) {
				if (baseLocation.getTilePosition().toPosition() != self.getStartLocation().toPosition())
					startLocations.add(baseLocation.getTilePosition().toPosition());
			} else
				expansionLocations.add(baseLocation.getTilePosition().toPosition());
		}
	}

	// Returns a suitable TilePosition to build a given building type near
	// specified TilePosition aroundTile, or null if not found. (builder
	// parameter is our worker)
	/*private TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
		TilePosition ret = null;
		int maxDist = 3;
		int stopDist = 40;

		// Refinery, Assimilator, Extractor
		if (buildingType.isRefinery()) {
			// This need to be changed for expansions
			aroundTile = self.getStartLocation();
			for (Unit n : game.neutral().getUnits()) {
				if ((n.getType() == UnitType.Resource_Vespene_Geyser)
						&& (Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist)
						&& (Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist))
					return n.getTilePosition();
			}
		}

		while ((maxDist < stopDist) && (ret == null)) {
			for (int i = aroundTile.getX() - maxDist; i <= aroundTile.getX() + maxDist; i++) {
				for (int j = aroundTile.getY() - maxDist; j <= aroundTile.getY() + maxDist; j++) {
					if (game.canBuildHere(new TilePosition(i, j), buildingType, builder, false)) {
						// units that are blocking the tile
						boolean unitsInWay = false;
						for (Unit u : game.getAllUnits()) {
							if (u.getID() == builder.getID())
								continue;
							if ((Math.abs(u.getTilePosition().getX() - i) < 4)
									&& (Math.abs(u.getTilePosition().getY() - j) < 4))
								unitsInWay = true;
						}
						if (!unitsInWay) {
							return new TilePosition(i, j);
						}
						// creep for Zerg
						if (buildingType.requiresCreep()) {
							boolean creepMissing = false;
							for (int k = i; k <= i + buildingType.tileWidth(); k++) {
								for (int l = j; l <= j + buildingType.tileHeight(); l++) {
									if (!game.hasCreep(k, l))
										creepMissing = true;
									break;
								}
							}
							if (creepMissing)
								continue;
						}
						// Psi for Protoss
						if (buildingType.requiresPsi()) {
							boolean psiMissing = false;
							for (int k = i; k <= i + buildingType.tileWidth(); k++) {
								for (int l = j; l <= j + buildingType.tileHeight(); l++) {
									if (!game.hasPowerPrecise(k, l))
										psiMissing = true;
									break;
								}
							}
							if (psiMissing)
								continue;
						}
					}
				}
			}
			maxDist += 1;
		}

		if (ret == null)
			game.printf("Unable to find suitable build position for " + buildingType.toString());
		return ret;
	}*/

	// gets a completed idle building
	private Unit getIdleBuilding(UnitType building) {
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == building && myUnit.isCompleted() && myUnit.isIdle())
				return myUnit;
		}
		return null;
	}

	private Unit getProbe() {
		for (Unit u : self.getUnits()) {
			if (u.getType() == UnitType.Protoss_Probe && !u.isGatheringGas() && u.getID() != scoutUnit.getID())
				return u;
		}
		return null;
	}

	/*
	 * private void findBuilder() { // if theres no builder unit, assign one if
	 * (!numUnits.isEmpty()) game.sendText("Wdawad"); for (Unit myUnit :
	 * self.getUnits()) { if (myUnit.getType().isWorker() &&
	 * myUnit.isCompleted()) { builderUnit = myUnit; break; } } }
	 */
	@SuppressWarnings("serial")
	private void init() {
		effMinerals = 0;
		effGas = 0;
		plannedMins = 0;
		plannedGas = 0;

		buildQueue = new ArrayList<UnitType>();
		idleGateway = null;
		idleRoboticsFacility = null;

		numUnits = new HashMap<UnitType, Integer>() {
			@Override
			public Integer get(Object key) {
				if (containsKey(key)) {
					return super.get(key);
				}
				return 0;
			}
		};
		enemyBuildingMemory = new HashSet<Position>();
		startLocations = new ArrayList<Position>();
		expansionLocations = new ArrayList<Position>();
		dest = null;
		scoutUnit = null;
		builderProbe = null;
		
		shapes = new ArrayList<DrawInfo>();
		// findBuilder();
		findScout();
		getBaseLocations();
	}

	// return true if building is being built, false if not
	private boolean isBeingConstructed(UnitType buildingType) {
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == buildingType)
				if (myUnit.isBeingConstructed())
					return true;
		}
		return false;
	}

	// check how many of the unit type are queued to build and return the number
	private int numQueued(ArrayList<UnitType> queue, UnitType type) {
		int ctr = 0;
		for (UnitType t : queue) {
			if (t == type)
				ctr++;
		}
		return ctr;
	}

	@Override
	public void onFrame() {
		// game.setTextSize(10);
		// game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " +
		// self.getRace());

		StringBuilder units = new StringBuilder("My units:\n");
		
		for (UnitType key : numUnits.keySet())
		{
			if (!key.isBuilding() && !key.isNeutral())
				units.append(key.toString() +":  " + numUnits.get(key) + "\n");
		}
		units.append("Next in Queue: ");

		idleGateway = getIdleBuilding(UnitType.Protoss_Gateway);
		idleRoboticsFacility = getIdleBuilding(UnitType.Protoss_Robotics_Facility);

		// Make a box over the builder for testing purposes
		// builderX= new Position(builderUnit.getPosition().getX()
		// -25,builderUnit.getPosition().getY()-25);
		// builderY = new Position(builderUnit.getPosition().getX()
		// +25,builderUnit.getPosition().getY()+25);
		// game.drawBoxMap(builderX, builderY, Color.Blue);
		// Make a box over the builder for testing purposes
		
		scoutX = new Position(scoutUnit.getPosition().getX() - 25, scoutUnit.getPosition().getY() - 25);
		scoutY = new Position(scoutUnit.getPosition().getX() + 25, scoutUnit.getPosition().getY() + 25);
		game.drawBoxMap(scoutX, scoutY, Color.Yellow);

		// get enemy info
		checkEnemies();
		
		// iterate through my units
		for (Unit myUnit : self.getUnits()) {

			/*if (myUnit.isIdle() && !myUnit.getType().isWorker() && !myUnit.getType().isBuilding() && numUnits.get(UnitType.Protoss_Zealot) < 12 && numUnits.get(UnitType.Protoss_Dragoon) < 6)
			{
				*myUnit.patrol(self.getStartLocation().toPosition());
			}*/
			// if there's enough minerals and not training, train a Probe, 21
			// per base
			if (effMinerals >= 0 && myUnit.getType() == UnitType.Protoss_Nexus && !myUnit.isTraining()
					&& numUnits.get(UnitType.Protoss_Probe) < 21 * numUnits.get(UnitType.Protoss_Nexus)) {
				buildQueue.add(UnitType.Protoss_Probe);
			}

			// if it's a worker and it's idle, send it to the closest mineral
			// patch
			if (myUnit.getType().isWorker() && myUnit.isIdle()) {
				Unit closestMineral = null;

				// find the closest mineral
				for (Unit neutralUnit : game.neutral().getUnits()) {
					if (neutralUnit.getType().isMineralField()) {
						if (closestMineral == null
								|| myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
							closestMineral = neutralUnit;
						}
					}
				}

				// if a mineral patch was found, send the worker to gather it
				if (closestMineral != null) {
					myUnit.gather(closestMineral, false);
				}
			}

			executebuildOrder();

		}
		if (numUnits.get(UnitType.Protoss_Zealot) >= 12 && numUnits.get(UnitType.Protoss_Dragoon) >= 6)
			beAggressive();
		for (DrawInfo di : shapes) {
			game.drawBoxMap(di.pos1, di.pos2, di.color, di.opaque);
		}
		// draw my units on screen
		if (!buildQueue.isEmpty())
			units.append(buildQueue.get(0).toString());
		else
			units.append("null");
		game.drawTextScreen(10, 25, units.toString());
		calcEffResources();
		game.drawTextScreen(430, 20, "Minerals: " + effMinerals + ", Gas:" + effGas + "\n" + "Planned Min: "
				+ plannedMins + ", PLanned Gas: " + plannedGas);
	}

	@Override
	public void onStart() {
		game = mirror.getGame();
		self = game.self();

		game.enableFlag(1); // this allows the user to control units during the
							// game
		// game.setLocalSpeed(value) This changes the game speed to a given
		// (integer) value.
		// Maximum possible speed corresponds to 0, while typical game speed is
		// around 30. SSCAI tournament is played on speed 20.

		// game.sendText("black sheep wall");

		// Use BWTA to analyze map
		// This may take a few minutes if the map is processed first time!
		System.out.println("Analyzing map...");
		BWTA.readMap();
		BWTA.analyze();
		System.out.println("Map data ready");

		// Track Totals
		init();

	}

	@Override
	public void onUnitComplete(Unit unit) {
		// TODO Improve this by picking probes without minerals

		if (unit.getPlayer() == self) {
			if (!unit.getType().isBuilding())
				numUnits.put(unit.getType(), numUnits.get(unit.getType()) + 1);
			// assign 3 workers to harvest gas on completion
			if (unit.getType() == UnitType.Protoss_Assimilator) {
				int count = 0;
				for (Unit myUnit : self.getUnits()) {
					if (myUnit.getType() == UnitType.Protoss_Probe && myUnit != scoutUnit && count < 3) {
						myUnit.gather(unit);
						count++;
					}
				}
			}
			if (unit.getType() == UnitType.Protoss_Gateway) {
				unit.setRallyPoint(findNearestExpansion(self.getStartLocation()));
			}
		}
		// track enemy units
		else {
			numUnits.put(unit.getType(), numUnits.get(unit.getType()) + 1);
		}
	}

	@Override
	public void onUnitCreate(Unit unit) {
		System.out.println("New unit discovered " + unit.getType());

		// Look at players units
		if (unit.getPlayer() == self) {
			if (unit.getType().isBuilding())
				numUnits.put(unit.getType(), numUnits.get(unit.getType()) + 1);
			buildQueue.remove(unit.getType());

			// subtract planned costs once production begins
			if (plannedMins > 0 && plannedGas > 0) {
				plannedMins -= unit.getType().mineralPrice();
				plannedGas -= unit.getType().gasPrice();
				calcEffResources();
			}
		}
	}

	@Override
	public void onUnitDestroy(Unit unit) {
		if (unit.getPlayer() == self) {
			// if (unit.getID() == builderUnit.getID())
			// findBuilder();
			numUnits.put(unit.getType(), numUnits.get(unit.getType()) - 1);
			buildQueue.add(unit.getType());
		}
		// track enemy units
		else {
			numUnits.put(unit.getType(), numUnits.get(unit.getType()) - 1);
		}
	}

	// cuz geysers act funny
	@Override
	public void onUnitMorph(Unit unit) {
		// this will break vs protoss
		if (unit.getPlayer() == self) {
			if (unit.getType().isRefinery()) {
				buildQueue.remove(unit.getType());
				numUnits.put(unit.getType(), numUnits.get(unit.getType()) + 1);
			}
		}
		// track enemy units
		else {
			numUnits.put(unit.getType(), numUnits.get(unit.getType()) + 1);
		}
	}

	private void run() {
		mirror.getModule().setEventListener(this);
		// mirror.getModule().setEventListener(); see event listeners on website
		mirror.startGame();
	}

	// send unit to scout for the enemy
	private void scout(Unit scout) {
		// if (enemyBuildingMemory.isEmpty() && scout.isInterruptible()){
		// if (!scout.isCarryingMinerals())
		// scout.move(startLocations.get(0));
		// }
		// if (scout.getPosition() == startLocations.get(0))
		// {
		// startLocations.remove(0);
		// }
		if (dest == null && !startLocations.isEmpty())
			dest = startLocations.get(0);

		if (!game.isVisible(dest.toTilePosition()))
			scout.move(dest);
		else
		{
			if (enemyBuildingMemory.isEmpty())
			{
				startLocations.remove(dest);
				dest = startLocations.get(0);
			}
			else
			{
				for (Position p: startLocations)
				{
					if (p != dest)
						startLocations.remove(p);
				}
			}
		}
	}

	private void trainUnit(UnitType unitType) {
		calcEffResources();

		if (effMinerals >= 0 && effGas >= 0 && self.supplyTotal() - self.supplyUsed() >= unitType.supplyRequired()){
			for (Unit myUnit : self.getUnits()) {
				if (myUnit.canTrain(unitType) && !myUnit.isTraining()) {
					myUnit.train(unitType);
				}
			}
		}
		if (!buildQueue.isEmpty())
			buildQueue.remove(unitType);
	}

	// Find idle building that can upgrade the specified tech and research it
	private boolean upgradeTech(UpgradeType upType) {
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.isIdle() && myUnit.canUpgrade(upType)) {
				if (effMinerals >= 0 && effGas >= 0) {
					myUnit.upgrade(upType);
					if (myUnit.isUpgrading())
						return true;
				}
			}
		}
		return false;
	}
}