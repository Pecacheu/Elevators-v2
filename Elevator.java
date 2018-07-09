//This work is licensed under a GNU General Public License. Visit http://gnu.org/licenses/gpl-3.0-standalone.html for details.
//Pecacheu's Elevator Plugin v2. Copyright (�) 2016, Pecacheu (Bryce Peterson, bbryce.com).

package com.pecacheu.elevators;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Elevator {
	public Floor floor;
	public ChuList<ChuList<Block>> sGroups;
	public ChuList<ChuList<Block>> csGroups;
	public boolean noDoor = false, moveDir = false;
	public ChuList<String> csData;
	
	//-- Initialization Functions:
	
	public Elevator(Floor _floor, ChuList<ChuList<Block>> _sGroups, ChuList<ChuList<Block>> _csGroups) {
		floor = _floor; if(_sGroups==null) sGroups = new ChuList<ChuList<Block>>(); else sGroups = _sGroups;
		if(_csGroups==null) csGroups = new ChuList<ChuList<Block>>(); else csGroups = _csGroups;
		csData = new ChuList<String>();
	}
	
	//Data Format: [World, Signs1 X, Signs1 Z, Signs2 X, Signs2 Z...]
	public static Elevator fromSaveData(java.util.List<String> data) { //TODO World could be calculated from eID using locFromString().
		if(data.size() < 3 || data.size() % 2 == 0) { Conf.err("fromSaveData", "Data length too small or not odd number."); return null; }
		World w = Bukkit.getServer().getWorld(data.get(0)); if(w==null) { Conf.err("fromSaveData", "World '"+data.get(0)+"' does not exist!"); return null; }
		ChuList<ChuList<Block>> sGroups = new ChuList<ChuList<Block>>();
		for(int i=1,l=data.size(); i<l; i+=2) {
			int sX, sZ; try { sX = Integer.parseInt(data.get(i)); sZ = Integer.parseInt(data.get(i+1)); } catch
			(NumberFormatException e) { Conf.err("fromSaveData", "Cannot convert position data to integer."); return null; }
			ChuList<Block> sList = Elevator.rebuildSignList(w, sX, sZ);
			if(sList.length!=0) sGroups.push(sList);
		}
		if(sGroups.length==0) { Conf.err("fromSaveData", "No elevator signs found!"); return null; }
		Elevator elev = new Elevator(null, sGroups, null); Block dSign = sGroups.get(0).get(0);
		Floor floor = Floor.getFloor(w.getBlockAt(dSign.getX(), elev.getLevel(true)+2, dSign.getZ()), elev);
		if(floor==null) { Conf.err("fromSaveData", "No elevator floor detected!"); return null; }
		elev.floor = floor; elev.csGroups = elev.rebuildCallSignList();
		//Special Modes:
		if(Conf.NODOOR.equals(Conf.lines(dSign)[2])) elev.noDoor = true; //Enable NoDoor Mode.
		return elev;
	}
	
	public ChuList<String> toSaveData() {
		ChuList<String> data = new ChuList<String>(); data.push(floor.world.getName());
		for(int i=0,l=sGroups.length; i<l; i++) { Block dSign = sGroups.get(i).get(0); data
		.push(Integer.toString(dSign.getX())); data.push(Integer.toString(dSign.getZ())); }
		return data;
	}
	
	//The names Bond. James Bond.
	public void selfDestruct() {
		if(floor != null && sGroups.length > 0 && sGroups.get(0).length > 0) { resetElevator(true); setDoors(sGroups.get(0).get(0).getY(), false); }
		for(int i=0,l=csGroups.length; i<l; i++) for(int h=0,d=csGroups.get(i).length; h<d; h++) csGroups.get(i).get(h).setType(Conf.AIR);
		Object[] eKeys = Conf.elevators.keySet().toArray(); for(int s=0,v=eKeys.length; s<v; s++)
		if(this.equals(Conf.elevators.get(eKeys[s]))) Conf.elevators.remove(eKeys[s]);
	}
	
	public int yMin() { return sGroups.get(0).get(0).getY()-2; }
	public int yMax() { return sGroups.get(0).get(sGroups.get(0).length-1).getY()+1; }
	
	//-- Rebuild Database Functions:
	
	//Locate elev signs at X,Z pos. Include Y pos for new sign detection.
	public static ChuList<Block> rebuildSignList(Location loc) {
		World w = loc.getWorld(); int sX = (int)loc.getX(), sZ = (int)loc.getZ(), bY = (int)loc.getY();
		ChuList<Block> sList = new ChuList<Block>(); for(int h=0; h<256; h++) { //Increasing height:
			Block bl = w.getBlockAt(sX, h, sZ); if(bl.getType() == Material.WALL_SIGN &&
			(Conf.TITLE.equals(Conf.lines(bl)[0]) || (bY!=0 && h == bY))) sList.push(bl);
		} return sList;
	} public static ChuList<Block> rebuildSignList(World w, int x, int z) { return rebuildSignList(new Location(w, x, 0, z)); }
	
	//Locate all call signs around elevator. Include newLoc for new sign detection.
	public ChuList<ChuList<Block>> rebuildCallSignList(Location newLoc) {
		ChuList<ChuList<Block>> csGroups = new ChuList<ChuList<Block>>(); ChuList<Block> sList = sGroups.get(0);
		
		Predicate<Block> checkSign = (bl) -> { return (bl.getType() == Material.WALL_SIGN && (Conf
		.CALL.equals(Conf.lines(bl)[0]) || (newLoc!=null && bl.getLocation().equals(newLoc)))); };
		
		for(int j=0,g=sList.length; j<g; j++) { //Iterate through levels:
			csGroups.set(j, new ChuList<Block>()); //Scan perimeter for call signs:
			int sY = sList.get(j).getY(); for(int dXZ=0; dXZ<3; dXZ++) {
				for(int xP=floor.xMin-1; xP<floor.xMax+2; xP++) { Block bl = floor.world.getBlockAt(xP, sY, floor.zMin-2-dXZ); if(checkSign.test(bl)) csGroups.get(j).push(bl); }
				for(int zP=floor.zMin-1; zP<floor.zMax+2; zP++) { Block bl = floor.world.getBlockAt(floor.xMax+2+dXZ, sY, zP); if(checkSign.test(bl)) csGroups.get(j).push(bl); }
				for(int xP=floor.xMax+1; xP>floor.xMin-2; xP--) { Block bl = floor.world.getBlockAt(xP, sY, floor.zMax+2+dXZ); if(checkSign.test(bl)) csGroups.get(j).push(bl); }
				for(int zP=floor.zMax+1; zP>floor.zMin-2; zP--) { Block bl = floor.world.getBlockAt(floor.xMin-2-dXZ, sY, zP); if(checkSign.test(bl)) csGroups.get(j).push(bl); }
			}
		} return csGroups;
	} public ChuList<ChuList<Block>> rebuildCallSignList() { return rebuildCallSignList(null); }
	
	//-- Find Elevator Functions:
	
	//Get elevator for elev sign, if any.
	public static Elevator fromElevSign(Block sign) {
		World sW = sign.getWorld(); int sX = sign.getX(), sZ = sign.getZ();
		Object[] eKeys = Conf.elevators.keySet().toArray();
		for(int s=0,v=eKeys.length; s<v; s++) { //Iterate through elevators:
			Elevator elev = Conf.elevators.get(eKeys[s]); Floor fl = elev.floor;
			if(fl.world.equals(sW) && (sX >= fl.xMin && sX <= fl.xMax) && (sZ >= fl.zMin && sZ <= fl.zMax)) return elev;
		} Conf.err("fromElevSign", "Elevator not detected for sign: "+sign); return null;
	}
	
	//新添加：由方块获得电梯
	public static Elevator fromElevBlock(Block b) {
		World sW = b.getWorld(); int sX = b.getX(), sZ = b.getZ(), sY = b.getY();
		Object[] eKeys = Conf.elevators.keySet().toArray();
		for(int s=0,v=eKeys.length; s<v; s++) { //Iterate through elevators:
			Elevator elev = Conf.elevators.get(eKeys[s]); Floor fl = elev.floor;
			if(fl.world.equals(sW) && (sX >= fl.xMin - 1 && sX <= fl.xMax + 1) && (sZ >= fl.zMin - 1 && sZ <= fl.zMax + 1) && (sY >= elev.yMin() && sY <= elev.yMax())) return elev;
		} Conf.err("fromElevBlock", "Elevator not detected for block: "+b); return null;
	}
	
	//Get elevator for call sign, if any.
	public static CSData fromCallSign(Block sign) {
		Object[] eKeys = Conf.elevators.keySet().toArray();
		for(int s=0,v=eKeys.length; s<v; s++) { //Iterate through elevators:
			Elevator elev = Conf.elevators.get(eKeys[s]); Floor fl = elev.floor; ChuList<Block> sList = elev.sGroups.get(0);
			for(int j=0,g=sList.length; j<g; j++) { //Iterate through levels:
				int sY = sList.get(j).getY(); for(int dXZ=0; dXZ<3; dXZ++) { //Scan perimeter for call signs:
					for(int xP=fl.xMin-1; xP<fl.xMax+2; xP++) { if(new Location(fl.world, xP, sY, fl.zMin-2-dXZ).equals(sign.getLocation())) return new CSData(elev, j); }
					for(int zP=fl.zMin-1; zP<fl.zMax+2; zP++) { if(new Location(fl.world, fl.xMax+2+dXZ, sY, zP).equals(sign.getLocation())) return new CSData(elev, j); }
					for(int xP=fl.xMax+1; xP>fl.xMin-2; xP--) { if(new Location(fl.world, xP, sY, fl.zMax+2+dXZ).equals(sign.getLocation())) return new CSData(elev, j); }
					for(int zP=fl.zMax+1; zP>fl.zMin-2; zP--) { if(new Location(fl.world, fl.xMin-2-dXZ, sY, zP).equals(sign.getLocation())) return new CSData(elev, j); }
				}
			}
		} Conf.err("fromCallSign", "Elevator not detected for sign: "+sign); return null;
	}
	
	//Get elevator for player, if any.
	public static Elevator fromPlayer(Player pl) {
		World pW = pl.getWorld(); Location loc = pl.getLocation();
		double pX = loc.getX(), pY = loc.getY(), pZ = loc.getZ();
		Object[] eKeys = Conf.elevators.keySet().toArray();
		for(int s=0,v=eKeys.length; s<v; s++) { //Iterate through elevators:
			Elevator elev = Conf.elevators.get(eKeys[s]); Floor fl = elev.floor;
			if(fl.world.equals(pW) && (pX >= fl.xMin && pX < fl.xMax+1) && (pY >= elev.yMin
			()-1 && pY < elev.yMax()+1) && (pZ >= fl.zMin && pZ < fl.zMax+1)) return elev;
		} /*Conf.err("fromPlayer", "Elevator not detected for player: "+pl.getName());*/ return null;
	}
	
	//Get elevator nearby door, if any.
	public static Elevator fromDoor(Location loc) {
		int x = loc.getBlockX(), z = loc.getBlockZ();
		Object[] eKeys = Conf.elevators.keySet().toArray();
		for(int s=0,v=eKeys.length; s<v; s++) { //Iterate through elevators:
			Elevator elev = Conf.elevators.get(eKeys[s]); Floor fl = elev.floor; if(!fl.world.equals(loc.getWorld())) continue;
			//Scan perimeter for door:
			if(z == fl.zMin-1) for(int xP=fl.xMin; xP<fl.xMax+2; xP++) { if(x == xP) return elev; }
			if(x == fl.xMax+1) for(int zP=fl.zMin; zP<fl.zMax+2; zP++) { if(z == zP) return elev; }
			if(z == fl.zMax+1) for(int xP=fl.xMin; xP<fl.xMax+2; xP++) { if(x == xP) return elev; }
			if(x == fl.xMin-1) for(int zP=fl.zMin; zP<fl.zMax+2; zP++) { if(z == zP) return elev; }
		} /*Conf.err("fromDoor", "Elevator not detected for door at: "+loc);*/ return null;
	}
	
	//-- Elevator Movement Functions:
	
	//Calculates current floor height.
	public int getLevel(boolean noTypeCheck) {
		ChuList<Block> sList = sGroups.get(0); World world = sList.get(0).getWorld(); int xPos = sList.get(0).getX(), zPos = sList.get(0).getZ();
		for(int y=yMin(),l=yMax(); y<l; y++) { Block bl = world.getBlockAt(xPos, y, zPos);
			if(noTypeCheck ? (Conf.BLOCKS.indexOf(bl.getType().toString()) != -1) : (bl.getType() == floor.fType)) return bl.getY();
		} Conf.err("getLevel", "Could not determine floor height! Returning ground floor at "+yMin()); return yMin();
	} public int getLevel() { return getLevel(false); }
	
	//Update all elevator call signs.
	//fLvl: Current elevator height, fDir: Direction (or set to 2 for doors-closed but not moving), sNum: Destination floor, if any.
	public void updateCallSigns(double fLvl, int fDir, int sNum) { //TODO Make level detection work better.
		ChuList<Block> sList = sGroups.get(0); boolean locked = floor.moving;
		ChuList<String> csInd = new ChuList<String>(sList.length);
		for(int m=0,k=sList.length,fNum=-1; m<k; m++) { //Iterate through floors:
			String ind = Conf.NOMV; if(fNum == -1 && sList.get(m).getY() >= fLvl) fNum = m; //Get level from floor height.
			if(m == fNum) ind = (fDir==2||locked) ? Conf.M_ATLV : Conf.ATLV; //Elevator is on level.
			else if(locked) { if(fDir>0) { //Going Up.
				if(m > fNum && m == sNum) ind = Conf.C_UP; //Elevator is below us and going to our floor.
				else ind = Conf.UP; //Elevator is above us or not going to our floor.
			} else { //Going Down.
				if(fNum == -1 && m == sNum) ind = Conf.C_DOWN; //Elevator is above us and going to our floor.
				else ind = Conf.DOWN; //Elevator is below us or not going to our floor.
			}}
			csInd.set(m, ind); csData = csInd;
			if(csGroups.get(m)!=null) for(int i=0,l=csGroups.get(m).length; i<l; i++) Conf.setLine(csGroups.get(m).get(i), 3, ind);
		}
	} public void updateCallSigns(double fLvl, int fDir) { updateCallSigns(fLvl, fDir, 0); }
	public void updateCallSigns(double fLvl) { updateCallSigns(fLvl, 0, 0); }
	
	//Update floor name on all elev signs.
	public void updateFloorName(String flName) {
		String nFloor = Conf.L_ST+flName+Conf.L_END;
		for(int k=0,m=sGroups.length; k<m; k++) for(int f=0,d=sGroups.get(k)
		.length; f<d; f++) Conf.setLine(sGroups.get(k).get(f), 1, nFloor);
	}
	
	public void doorTimer(int level) {
		setDoors(level, true); if(Conf.CLTMR != null) Conf.CLTMR
		.cancel(); Conf.CLTMR = Conf.plugin.setTimeout(() -> { setDoors
		(level, false); Conf.CLTMR = null; }, Conf.DOOR_HOLD);
	}
	
	//-- Utility Functions:
	
	//Remove all blocks in elevator:
	public void resetElevator(boolean noFloor) {
		int yMin = this.yMin(), yMax = this.yMax();
		World world = floor.world;
		for(int y=yMin; y<yMax; y++)
			for(int x=floor.xMin; x<floor.xMax+1; x++)
				for(int z=floor.zMin; z<floor.zMax+1; z++) {
					Block bl = world.getBlockAt(x, y, z);
					if(y == yMin && !noFloor) bl.setType(floor.fType);
					else if(bl.getType() != Material.WALL_SIGN || (Conf.TITLE.equals(Conf.lines(bl)[0]) && !isKnownSign(bl))) //TODO Check if it works.
					{BlockState s=bl.getState();s.setType(Conf.AIR);s.update(true);}
		}
	} public void resetElevator() { resetElevator(false); }
	
	//Check if sign is a registered 'elevator' sign:
	public boolean isKnownSign(Block sign) {
		for(int i=0,l=sGroups.length; i<l; i++) for(int k=0,b=sGroups.get(i).length; k<b;
		k++) if(sGroups.get(i).get(k).getLocation().equals(sign.getLocation())) return true;
		return false;
	}
	
	//Open/close elevator doors.
	public void setDoors(int h, boolean onOff) {
		Floor fl = floor; BiPredicate<Integer,Integer> isCorner = (x,z) -> { return ((fl.xMax+1-fl.xMin<=2) ? (x < fl.xMin || x > fl.xMax)
		: (x <= fl.xMin || x >= fl.xMax)) && ((fl.zMax+1-fl.zMin<=2) ? (z < fl.zMin || z > fl.zMax) : (z <= fl.zMin || z >= fl.zMax)); };
		//Open/Close Doors and Barrier-Doors:
		Consumer<Block> setDoor = (d) -> { if(Conf.isDoor(d)) { int dat = d.getData(); if(onOff &&
		dat < 4) d.setData((byte)(dat+4)); else if(!onOff && dat >= 4) d.setData((byte)(dat-4)); }};
		Consumer<Block> setBDoor = (bl) -> { if(isCorner.test(bl.getX(),bl.getZ())) { if(bl.getType() == Conf.AIR) bl.setType
		(Conf.DOOR_SET); } else if(bl.getType() == (onOff?Conf.DOOR_SET:Conf.AIR)) bl.setType(onOff?Conf.AIR:Conf.DOOR_SET); };
		//Cycle Around Elevator Perimeter:
		World world = fl.world; for(int yP=h-1; yP<=h+1; yP++) {
			for(int xP=fl.xMin; xP<fl.xMax+2; xP++) { Block bl = world.getBlockAt(xP, yP, fl.zMin-1); if(!noDoor) setBDoor.accept(bl); if(yP==h-1) setDoor.accept(bl); }
			for(int zP=fl.zMin; zP<fl.zMax+2; zP++) { Block bl = world.getBlockAt(fl.xMax+1, yP, zP); if(!noDoor) setBDoor.accept(bl); if(yP==h-1) setDoor.accept(bl); }
			for(int xP=fl.xMax; xP>fl.xMin-2; xP--) { Block bl = world.getBlockAt(xP, yP, fl.zMax+1); if(!noDoor) setBDoor.accept(bl); if(yP==h-1) setDoor.accept(bl); }
			for(int zP=fl.zMax; zP>fl.zMin-2; zP--) { Block bl = world.getBlockAt(fl.xMin-1, yP, zP); if(!noDoor) setBDoor.accept(bl); if(yP==h-1) setDoor.accept(bl); }
			if(noDoor) break; //If noDoor is on, no need to run this loop more than once.
		}
		if(noDoor) {
			int fNum = -1; for(int q=0,w=sGroups.get(0).length; q<w; q++) if(h == sGroups.get(0).get(q).getY()) { fNum = q; break; } //Get level number.
			if(fNum != -1 && csGroups.get(fNum) != null) for(int a=0,b=csGroups.get(fNum).length; a<b; a++) { //Power Call Signs:
				Block sign = csGroups.get(fNum).get(a); int sX=sign.getX(), sY=sign.getY(), sZ=sign.getZ(); //Power Nearby Redstone Equipment:
				for(int x=sX-1,mX=sX+1; x<=mX; x++) for(int y=sY-1,mY=sY+1; y<=mY; y++) for(int z=sZ-1,mZ=sZ+1;
				z<=mZ; z++) if(x!=sX || y!=sY || z!=sZ) Conf.setPowered(world.getBlockAt(x, y, z), onOff);
			}
		}
	}
	
	//Turn on/off gravity and adjust height of all entities in elevator:
	public void setEntities(boolean gravity, double delta, double hCheck) {
		World world = floor.world; int yMin = this.yMin(), yMax = this.yMax();
		Object[] eList = world.getEntitiesByClass(org.bukkit.entity.LivingEntity.class).toArray(); //Get LivingEntity list.
		for(int i=0,l=eList.length; i<l; i++) { //Iterate through entities:
			Entity e = (Entity)eList[i]; Location loc = e.getLocation(); double eX = loc.getX(), eY = loc.getY(), eZ = loc.getZ();
			if((eX >= floor.xMin && eX < floor.xMax+1) && (eY >= yMin-1 && eY < yMax+1) && (eZ >= floor.zMin && eZ < floor.zMax+1)) {
				e.setGravity(gravity); if(hCheck != 0) {
					e.setVelocity(new Vector(0, delta, 0)); if(delta == 0 || Math.abs(eY-(hCheck+1)) > 5)
					e.teleport(new Location(world, eX, hCheck+1.1, eZ, loc.getYaw(), loc.getPitch()));
				}
			}
		}
	} public void setEntities(boolean gravity, double hSet) { setEntities(gravity, 0, hSet); }
	public void setEntities(boolean gravity) { setEntities(gravity, 0, 0); }
	
	//-- Elevator Movement:
	
	//Move elevator car from fLevel to sLevel:
	//Speed is in blocks-per-second.
	public void gotoFloor(int fLevel, int sLevel, int selNum, int speed) {
		moveDir = (sLevel>fLevel); double step = (double)speed * ((double)Conf.MOVE_RES/1000) * (moveDir?1:-1);
		
		if(Conf.CLTMR != null) { Conf.CLTMR.cancel(); Conf.CLTMR = null; }
		
		for(int i=0,l=sGroups.get(0).length; i<l; i++) setDoors(sGroups.get(0).get(i).getY(), false);
		updateCallSigns(fLevel, 2); floor.moving = true;
		
		Conf.dbg("FROM: "+fLevel+", TO: "+sLevel+"(#"+selNum+"), STEP: "+step);
		
		Conf.plugin.setTimeout(() -> {
			int fID = floor.addFloor(fLevel, true); GotoTimer timer = new GotoTimer();
			timer.set(this, fLevel, sLevel, selNum, speed, step, fID); Conf.plugin.setInterval(timer, Conf.MOVE_RES);
		}, 500);
	}
}

class GotoTimer extends BukkitRunnable {
	private Main plugin = Conf.plugin; private Elevator elev;
	private int sLevel, selNum, fID; private double fPos, step, accel;
	
	public void set(Elevator elev, int fLevel, int sLevel, int selNum, double speed, double step, int fID) {
		this.elev = elev; this.step = step; this.fPos = fLevel; this.sLevel = sLevel; this.selNum = selNum;
		this.fID = fID; this.accel = speed*(elev.moveDir?1:-1)/21; elev.setEntities(false, fPos);
	}
	
	public void run() { synchronized(Conf.API_SYNC) {
		elev.floor.moveFloor(fID, fPos); elev.updateCallSigns(fPos, elev.moveDir?1:0, selNum); elev.setEntities(false, accel, fPos);
		
		if(elev.moveDir?(fPos >= sLevel):(fPos <= sLevel)) { //At destination floor:
			this.cancel(); elev.floor.deleteFloor(fID); elev.floor.addFloor(sLevel, false); //Restore solid floor.
			plugin.setTimeout(() -> { elev.floor.addFloor(sLevel, false); elev.setEntities(true, sLevel); }, 50); //Set floor again, just to be sure.
			elev.updateCallSigns(sLevel+2); plugin.setTimeout(() -> { elev.floor.moving = false; elev.updateCallSigns(sLevel+2); elev.doorTimer(sLevel+2); }, 500);
		} else fPos += step;
	}}
}

class CSData {
	Elevator elev; int index;
	public CSData(Elevator _elev, int _index) {
		elev = _elev; index = _index;
	}
}