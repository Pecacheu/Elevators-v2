//Elevators v2, ©2020 Pecacheu. Licensed under GNU GPL 3.0

package net.forestfire.elevators;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
	floor = _floor; if(_sGroups==null) sGroups = new ChuList<>(); else sGroups = _sGroups;
	if(_csGroups==null) csGroups = new ChuList<>(); else csGroups = _csGroups;
	csData = new ChuList<>();
}

//Data Format: [World, Signs1 X, Signs1 Z, Signs2 X, Signs2 Z...]
public static Elevator fromSaveData(java.util.List<String> data) {
	if(data.size() < 3 || data.size() % 2 == 0) { Conf.err("fromSaveData", "Data length too small or not odd number."); return null; }
	World w=Bukkit.getServer().getWorld(data.get(0)); if(w==null) {
		Conf.err("fromSaveData", "World '"+data.get(0)+"' does not exist!"); return null;
	}
	ChuList<ChuList<Block>> sGroups = new ChuList<>();
	for(int i=1,l=data.size(),sX,sZ; i<l; i+=2) {
		try { sX = Integer.parseInt(data.get(i)); sZ = Integer.parseInt(data.get(i+1)); }
		catch(NumberFormatException e) { Conf.err("fromSaveData", "Cannot convert position data to integer."); return null; }
		ChuList<Block> sList = Elevator.rebuildSignList(w, sX, sZ);
		if(sList.length!=0) sGroups.add(sList);
	}
	if(sGroups.length==0) { Conf.err("fromSaveData", "No elevator signs found!"); return null; }
	Elevator elev = new Elevator(null, sGroups, null); Block dSign = sGroups.get(0).get(0);
	Floor f=Floor.getFloor(w.getBlockAt(dSign.getX(), elev.getLevel(true)+2, dSign.getZ()), elev);
	if(f==null) { Conf.err("fromSaveData", "No elevator floor detected!"); return null; }
	elev.floor=f; elev.rebuildCallSignList(null);
	//Special Modes:
	if(Conf.NODOOR.equals(Conf.lines(dSign)[2])) elev.noDoor = true; //Enable NoDoor Mode.
	return elev;
}

public ChuList<String> toSaveData() {
	ChuList<String> data = new ChuList<>(); data.add(floor.world.getName());
	for(int i=0,l=sGroups.length; i<l; i++) {
		Block dSign = sGroups.get(i).get(0);
		data.add(Integer.toString(dSign.getX())); data.add(Integer.toString(dSign.getZ()));
	}
	return data;
}

//The names Bond. James Bond.
public void selfDestruct() {
	if(floor != null && sGroups.length > 0 && sGroups.get(0).length > 0) { resetElevator(true); setDoors(sGroups.get(0).get(0).getY(), false); }
	for(ChuList<Block> c: csGroups) for(Block s: c) s.setType(Conf.AIR);
	for(String s: Conf.elevators.keySet()) if(this.equals(Conf.elevators.get(s))) Conf.elevators.remove(s);
}

public int yMin() { return sGroups.get(0).get(0).getY()-2; }
public int yMax() { return sGroups.get(0).get(sGroups.get(0).length-1).getY()+1; }

//-- Rebuild Database Functions:

//Locate elev signs at X,Z pos. Include Y pos for new sign detection.
public static ChuList<Block> rebuildSignList(Location l) {
	World w=l.getWorld(); int x=l.getBlockX(), y=l.getBlockY(), z=l.getBlockZ();
	ChuList<Block> sList = new ChuList<>(); for(int h=w.getMinHeight(); h<w.getMaxHeight(); h++) { //Increasing height:
		Block bl = w.getBlockAt(x,h,z); if(bl.getBlockData() instanceof WallSign
			&& (Conf.TITLE.equals(Conf.lines(bl)[0]) || (y!=0 && h==y))) sList.add(bl);
	} return sList;
} public static ChuList<Block> rebuildSignList(World w, int x, int z) { return rebuildSignList(new Location(w, x, 0, z)); }

//Locate all call signs around elevator. Include newLoc for new sign detection.
void rebuildCallSignList(Location newLoc) {
	ChuList<Block> sList = sGroups.get(0); int j=0,g=sList.length; csGroups = new ChuList<>(g);
	for(; j<g; j++) { //Iterate through levels:
		csGroups.set(j, new ChuList<>()); //Scan perimeter for call signs:
		int sY=sList.get(j).getY(),a; for(int dXZ=0; dXZ<4; dXZ++) {
			a=(dXZ==0?0:1);
			for(int xP=floor.xMin-a; xP<floor.xMax+2; xP++) chkSign(floor.world.getBlockAt(xP, sY, floor.zMin-1-dXZ), j, newLoc);
			for(int zP=floor.zMin-a; zP<floor.zMax+2; zP++) chkSign(floor.world.getBlockAt(floor.xMax+1+dXZ, sY, zP), j, newLoc);
			for(int xP=floor.xMax+a; xP>floor.xMin-2; xP--) chkSign(floor.world.getBlockAt(xP, sY, floor.zMax+1+dXZ), j, newLoc);
			for(int zP=floor.zMax+a; zP>floor.zMin-2; zP--) chkSign(floor.world.getBlockAt(floor.xMin-1-dXZ, sY, zP), j, newLoc);
		}
	}
}

private void chkSign(Block s, int i, Location nl) {
	if(s.getBlockData() instanceof WallSign && (Conf.CALL.equals(Conf.lines(s)[0])
		|| (nl!=null && s.getLocation().equals(nl)))) csGroups.get(i).add(s);
}

//-- Find Elevator Functions:

//Get elevator for elev sign, if any.
public static Elevator fromElevSign(Block sign) {
	World w=sign.getWorld(); int x=sign.getX(), z=sign.getZ();
	for(Elevator e: Conf.elevators.values()) { //Iterate through elevators:
		Floor f=e.floor; if(f.world.equals(w) && (x>=f.xMin && x<=f.xMax) && (z>=f.zMin && z<=f.zMax)) return e;
	}
	Conf.err("fromElevSign", "Elevator not detected for sign: "+sign); return null;
}

//Get elevator for call sign, if any.
public static CSData fromCallSign(Block sign) {
	int x=sign.getX(), y=sign.getY(), z=sign.getZ();
	for(Elevator e: Conf.elevators.values()) { //Iterate through elevators:
		Floor f=e.floor; if(!f.world.equals(sign.getWorld())) continue;
		ChuList<Block> sList = e.sGroups.get(0);
		for(int j=0,g=sList.length; j<g; j++) { //Iterate through levels:
			int sY=sList.get(j).getY(); if(y != sY) continue;
			for(int dXZ=0,a; dXZ<4; dXZ++) { //Scan perimeter for call signs:
				a=(dXZ==0?0:1); if((z == f.zMin-1-dXZ && x >= f.xMin-a && x < f.xMax+2) ||
					(x == f.xMax+1+dXZ && z >= f.zMin-a && z < f.zMax+2) ||
					(z == f.zMax+1+dXZ && x <= f.xMax+a && x > f.xMin-2) ||
					(x == f.xMin-1-dXZ && z <= f.zMax+a && z > f.zMin-2)) return new CSData(e,j);
			}
		}
	} Conf.err("fromCallSign", "Elevator not detected for sign: "+sign); return null;
}

//Get elevator for block, if any.
public static Elevator fromElevBlock(Block bl) {
	World w=bl.getWorld(); Material t=bl.getType(); boolean d=(t==Conf.DOOR_SET);
	int x=bl.getX(), y=bl.getY(), z=bl.getZ();
	for(Elevator e: Conf.elevators.values()) { //Iterate through elevators:
		Floor f=e.floor;
		if(!w.equals(f.world) || !d && (t != f.fType || x < f.xMin || x > f.xMax || z < f.zMin || z > f.zMax)) continue;
		for(Block s: e.sGroups.get(0)) { //Iterate through levels:
			int sY=s.getY(); if(d) { //Block Doors:
				if(y < sY-1 || y > sY+1) continue;
				if((z == f.zMin-1 && x >= f.xMin && x < f.xMax+2) || (x == f.xMax+1 && z >= f.zMin && z < f.zMax+2) ||
					(z == f.zMax+1 && x <= f.xMax && x > f.xMin-2) || (x == f.xMin-1 && z <= f.zMax && z > f.zMin-2)) return e;
			} else if(y == sY-2) return e; //Elevator Floors.
		}
	} return null;
}

boolean entityInElev(Entity e) {
	Floor f=floor; Location l=e.getLocation(); double x=l.getX(), y=l.getY(), z=l.getZ();
	return f.world.equals(l.getWorld()) && (x >= f.xMin && x < f.xMax+1) && (y >= this.yMin()-1
			&& y < this.yMax()+1) && (z >= f.zMin && z < f.zMax+1);
}

//Get elevator for entity, if any.
public static Elevator fromEntity(Entity en) {
	for(Elevator e: Conf.elevators.values()) if(e.entityInElev(en)) return e;
	return null;
}

//Get elevator nearby door, if any.
public static Elevator fromDoor(Location l) {
	World w=l.getWorld(); int x=l.getBlockX(), z=l.getBlockZ();
	for(Elevator e: Conf.elevators.values()) { //Iterate through elevators:
		Floor f=e.floor; if(f.world.equals(w) && ((z == f.zMin-1 || z == f.zMax+1) && x >= f.xMin && x < f.xMax+2) ||
			((x == f.xMin-1 || x == f.xMax+1) && z >= f.zMin && z < f.zMax+2)) return e; //Scan perimeter for door
	} return null;
}

//-- Elevator Movement Functions:

//Calculates current floor height.
public int getLevel(boolean noTypeCheck) {
	ChuList<Block> sList = sGroups.get(0); World world = sList.get(0).getWorld(); int xPos = sList.get(0).getX(), zPos = sList.get(0).getZ();
	for(int y=yMin(),l=yMax(); y<l; y++) { Block bl = world.getBlockAt(xPos, y, zPos);
		if(noTypeCheck ? (Conf.BLOCKS.contains(bl.getType().toString())) : (bl.getType() == floor.fType)) return bl.getY();
	} Conf.err("getLevel", "Could not determine floor height! Returning ground floor at "+yMin()); return yMin();
} public int getLevel() { return getLevel(false); }

//Update all elevator call signs.
//fLvl: Current elevator height, fDir: Direction (or set to 2 for doors-closed but not moving), sNum: Destination floor, if any.
public void updateCallSigns(double fLvl, int fDir, int sNum) {
	ChuList<Block> sList = sGroups.get(0); boolean locked = floor.moving;
	ChuList<String> csInd = new ChuList<>(sList.length);
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
	for(int k=0,m=sGroups.length; k<m; k++) for(int f=0,d=sGroups.get(k).length; f<d; f++)
		Conf.setLine(sGroups.get(k).get(f), 1, nFloor);
}

public FList getFloors() {
	ChuList<Block> ds=sGroups.get(0); String s,sel=Conf.lines(ds.get(0))[1]; int n=0;
	if(sel.length()!=0) sel=sel.substring(Conf.L_ST.length(), sel.length()-Conf.L_END.length());
	int i=0,l=ds.length; ChuList<String> fn=new ChuList<>(l);
	for(; i<l; i++) { fn.add(s=Conf.lines(ds.get(i))[3]); if(sel.equals(s)) n=i; }
	return new FList(fn, n);
}

public void doorTimer(int level) {
	setDoors(level, true); if(Conf.CLTMR != null) Conf.CLTMR.cancel();
	Conf.CLTMR = Conf.plugin.setTimeout(() -> {
		setDoors(level, false); Conf.CLTMR = null;
	}, Conf.DOOR_HOLD);
}

//-- Utility Functions:

//Remove all blocks in elevator:
public void resetElevator(boolean noFloor) {
	int yMin = this.yMin(), yMax = this.yMax(); World world = floor.world;
	for(int y=yMin; y<yMax; y++) for(int x=floor.xMin; x<floor.xMax+1; x++) for(int z=floor.zMin; z<floor.zMax+1; z++) {
		Block bl = world.getBlockAt(x,y,z); if(y == yMin && !noFloor) bl.setType(floor.fType);
		else if(!(bl.getBlockData() instanceof WallSign) || (Conf.TITLE.equals(Conf.lines(bl)[0]) && !isKnownSign(bl))) bl.setType(Conf.AIR);
	}
} public void resetElevator() { resetElevator(false); }

//Check if sign is a registered 'elevator' sign:
public boolean isKnownSign(Block sign) {
	for(int i=0,l=sGroups.length; i<l; i++) for(int k=0,b=sGroups.get(i).length; k<b; k++)
		if(sGroups.get(i).get(k).getLocation().equals(sign.getLocation())) return true;
	return false;
}

//Open/close elevator doors.
public void setDoors(int h, boolean on) {
	Floor f=floor; World w=f.world; Block b; for(int yP=h-1; yP<=h+1; yP++) { //Cycle Around Elevator Perimeter:
		for(int xP=f.xMin; xP<f.xMax+2; xP++) { b=w.getBlockAt(xP, yP, f.zMin-1); if(!noDoor) setBDoor(b,on); Conf.setDoor(b,on); }
		for(int zP=f.zMin; zP<f.zMax+2; zP++) { b=w.getBlockAt(f.xMax+1, yP, zP); if(!noDoor) setBDoor(b,on); Conf.setDoor(b,on); }
		for(int xP=f.xMax; xP>f.xMin-2; xP--) { b=w.getBlockAt(xP, yP, f.zMax+1); if(!noDoor) setBDoor(b,on); Conf.setDoor(b,on); }
		for(int zP=f.zMax; zP>f.zMin-2; zP--) { b=w.getBlockAt(f.xMin-1, yP, zP); if(!noDoor) setBDoor(b,on); Conf.setDoor(b,on); }
	}
	int fNum=-1; for(int q=0,m=sGroups.get(0).length; q<m; q++) if(h == sGroups.get(0).get(q).getY()) { fNum=q; break; } //Get level number.
	if(fNum != -1 && csGroups.get(fNum) != null) for(Block s: csGroups.get(fNum)) { //Power Call Signs:
		int sX=s.getX(), sY=s.getY(), sZ=s.getZ(); //Power Nearby Redstone Equipment:
		for(int x=sX-1,mX=sX+1; x<=mX; x++) for(int y=sY-1,mY=sY+1; y<=mY; y++) for(int z=sZ-1,mZ=sZ+1; z<=mZ; z++)
			if(x!=sX || y!=sY || z!=sZ) Conf.setPowered(w.getBlockAt(x,y,z), on);
	}
}

//Open/Close Barrier-Doors
private void setBDoor(Block b, boolean on) {
	if(isCorner(b.getX(),b.getZ())) Conf.setDoorBlock(b, true); else Conf.setDoorBlock(b, !on);
}
private boolean isCorner(int x, int z) {
	Floor f=floor; return ((f.xMax+1-f.xMin<=2) ? (x<f.xMin || x>f.xMax) : (x<=f.xMin || x>=f.xMax))
		&& ((f.zMax+1-f.zMin<=2) ? (z<f.zMin || z>f.zMax) : (z<=f.zMin || z>=f.zMax));
}

//Remove block doors
public void remBlockDoor(int h) {
	Floor f=floor; World w=f.world; for(int yP=h-1; yP<=h+1; yP++) {
		for(int xP=f.xMin; xP<f.xMax+2; xP++) Conf.setDoorBlock(w.getBlockAt(xP, yP, f.zMin-1), false);
		for(int zP=f.zMin; zP<f.zMax+2; zP++) Conf.setDoorBlock(w.getBlockAt(f.xMax+1, yP, zP), false);
		for(int xP=f.xMax; xP>f.xMin-2; xP--) Conf.setDoorBlock(w.getBlockAt(xP, yP, f.zMax+1), false);
		for(int zP=f.zMax; zP>f.zMin-2; zP--) Conf.setDoorBlock(w.getBlockAt(f.xMin-1, yP, zP), false);
	}
}

//-- Elevator Movement:

//Move elevator car from fLevel to sLevel:
//Speed is in blocks-per-second.
public void gotoFloor(int fLevel, int sLevel, int selNum, boolean msg) {
	int speed = Conf.BL_SPEED.get(Conf.BLOCKS.indexOf(floor.fType.toString()));
	moveDir = (sLevel>fLevel); double step = (double)speed * ((double)Conf.MOVE_RES/1000) * (moveDir?1:-1);

	if(Conf.CLTMR != null) { Conf.CLTMR.cancel(); Conf.CLTMR = null; }

	for(int i=0,l=sGroups.get(0).length; i<l; i++) setDoors(sGroups.get(0).get(i).getY(), false);
	updateCallSigns(fLevel, 2); floor.moving = true;

	Conf.dbg("FROM: "+fLevel+", TO: "+sLevel+" ("+selNum+"), STEP: "+step);

	Conf.plugin.setTimeout(() -> {
		int fID = floor.addFloor(fLevel, true, false);
		GotoTimer timer = new GotoTimer(this, fLevel, sLevel, selNum, speed, step, fID, msg);
		Conf.plugin.setInterval(timer, Conf.MOVE_RES);
	}, 500);
}
}

class GotoTimer extends BukkitRunnable {
private final Main pl=Conf.plugin; private final Elevator el;
private final int sLev, sNum, fID; private final double step, accel;
private double fPos; private final ChuList<Entity> eList = new ChuList<>(5);

GotoTimer(Elevator _e, int fp, int sl, int sn, double spd, double st, int id, boolean m) {
	el=_e; step=st; fPos=fp; sLev=sl; sNum=sn; fID=id; accel=spd*(el.moveDir?1:-1)/20;
	FList fl=el.getFloors(); String name=fl.fl.get(fl.sn);
	//Find entities in elevator:
	for(Entity e: el.floor.world.getEntitiesByClass(LivingEntity.class)) if(el.entityInElev(e)) {
		if(m && e instanceof Player) e.sendMessage(Conf.MSG_GOTO_ST+name+Conf.MSG_GOTO_END);
		eList.add(e);
	}
	setEntities(false, 0, fPos);
}

//Turn on/off gravity and adjust height of all entities in elevator
protected void setEntities(boolean grav, double delta, double hSet) {
	Floor f=el.floor; World w=f.world; for(Entity e: eList) { //Iterate through entities:
		e.setGravity(grav); if(e instanceof Player) {
			Player p=(Player)e; if(p.isFlying()) p.setFlying(false);
			p.setAllowFlight(p.getGameMode()==GameMode.CREATIVE || !grav);
		}
		e.setVelocity(new Vector(0,delta,0)); e.setFallDistance(0);
		Location l=e.getLocation(); double dis=l.getY()-(hSet+1); boolean ee=el.entityInElev(e);
		if(!ee || dis<-1.5 || dis>1.5 || delta==0) e.teleport(new Location(w, ee?l.getX():f.xMin+.5,
			hSet+1.1, ee?l.getZ():f.zMin+.5, l.getYaw(), l.getPitch()));
	}
}

public void run() { synchronized(Conf.API_SYNC) {
	el.floor.moveFloor(fID, fPos); el.updateCallSigns(fPos, el.moveDir?1:0, sNum);
	setEntities(false, accel, fPos);
	if(el.moveDir?(fPos >= sLev):(fPos <= sLev)) { //At destination floor:
		this.cancel(); el.floor.deleteFloor(fID); pl.setTimeout(() -> {
			el.floor.addFloor(sLev, false, false); setEntities(true, 0, sLev); //Restore solid floor.
			el.updateCallSigns(sLev+2); pl.setTimeout(() -> {
				el.floor.moving=false; el.updateCallSigns(sLev+2); el.doorTimer(sLev+2);
			}, 500);
		}, 50);
	} else fPos += step;
}}
}

class CSData {
Elevator elev; int index;
public CSData(Elevator e, int i) { elev=e; index=i; }
}

//Hidden Reference? Maybe~
class FList {
public final ChuList<String> fl; public final int sn;
FList(ChuList<String> _fl, int _sn) { fl=_fl; sn=_sn; }
}