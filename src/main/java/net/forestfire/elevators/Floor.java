//Elevators v2, ©2020 Pecacheu. Licensed under GNU GPL 3.0

package net.forestfire.elevators;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;

public class Floor {
public World world; public int xMin, zMin, xMax, zMax;
public Material fType; public boolean moving; public Elevator elev;

//-- Initialization Functions

public Floor(World _world, int _xMin, int _zMin, int _xMax, int _zMax,
		 Material _fType, boolean _moving, Elevator _elev) {
	world = _world; xMin = _xMin; zMin = _zMin; xMax = _xMax; zMax = _zMax;
	fType = _fType; moving = _moving; elev = _elev;
}

public static Floor getFloor(Block b, Elevator parent) {
	World world=b.getWorld(); int bX=b.getX(), h=b.getY()-2, bZ=b.getZ();
	Material fType=world.getBlockAt(bX, h, bZ).getType();
	if(!Conf.BLOCKS.contains(fType.toString()) || world.getBlockAt(bX, h+1, bZ).getType() != Conf.AIR) {
		Conf.err("getFloor", "No valid block type found!"); return null;
	}

	int xP=1, xN=1, zP=1, zN=1; BlockFace f=((WallSign)b.getBlockData()).getFacing();
	if(f !=  BlockFace.WEST) while(xP <= Conf.RADIUS_MAX) { if(world.getBlockAt(bX+xP, h, bZ).getType() != fType) break; xP++; }
	if(f !=  BlockFace.EAST) while(xN <= Conf.RADIUS_MAX) { if(world.getBlockAt(bX-xN, h, bZ).getType() != fType) break; xN++; }
	if(f != BlockFace.NORTH) while(zP <= Conf.RADIUS_MAX) { if(world.getBlockAt(bX, h, bZ+zP).getType() != fType) break; zP++; }
	if(f != BlockFace.SOUTH) while(zN <= Conf.RADIUS_MAX) { if(world.getBlockAt(bX, h, bZ-zN).getType() != fType) break; zN++; }

	if(xP > Conf.RADIUS_MAX || xN > Conf.RADIUS_MAX || zP > Conf.RADIUS_MAX || zN > Conf.RADIUS_MAX) {
		Conf.err("getFloor", "Maximum floor size exceeded!"); return null;
	}
	int xPos=bX-xN+1, zPos=bZ-zN+1, xMax = bX+xP-1, zMax = bZ+zP-1;
	return new Floor(world, xPos, zPos, xMax, zMax, fType, false, parent);
}

//-- Floor Management Functions

//Creates Floor or MovingFloor at height 'h'
//If MovingFloor, returns new floorID
//Deletes existing floor blocks unless 'noDelete' is true
public int addFloor(double h, boolean isMoving, boolean noDelete, Integer forceID) {
	if(!noDelete) { removeFallingBlocks(); elev.resetElevator(true); }
	if(isMoving) { //Create FallingBlock Floor
		ChuList<FallingBlock> bl=new ChuList<>((xMax-xMin+1)*(zMax-zMin+1)); moving=true;
		for(int x=xMin; x<=xMax; x++) for(int z=zMin; z<=zMax; z++) {
			bl.add(fallingBlock(world, x, h, z, fType));
		}
		int ind; if(forceID!=null) ind = forceID;
		else ind = Conf.findFirstEmpty(Conf.movingFloors);
		Conf.movingFloors.set(ind, bl); return ind;
	} else { //Create Solid Floor
		for(int x=xMin; x<=xMax; x++) for(int z=zMin; z<=zMax; z++) world.getBlockAt(x,(int)h,z).setType(fType);
	} return 0;
}
public int addFloor(double h, boolean isMoving, boolean noDelete) {
	return addFloor(h, isMoving, noDelete, null);
}

//Moves a MovingFloor using floorID
public void moveFloor(int floorID, double h) {
	if(Conf.movingFloors.get(floorID)!=null) {
		ChuList<FallingBlock> bList = Conf.movingFloors.get(floorID);
		for(int i=0,l=bList.length; i<l; i++) bList.get(i).remove();
		addFloor(h, true, true, floorID);
	}
}

//Deletes a MovingFloor instance
public void deleteFloor(int floorID) {
	if(Conf.movingFloors.get(floorID)!=null) {
		ChuList<FallingBlock> bList = Conf.movingFloors.get(floorID);
		for(int i=0,l=bList.length; i<l; i++) bList.get(i).remove();
	} Conf.movingFloors.set(floorID, null);
}

//-- FallingBlock Functions

public static FallingBlock fallingBlock(World world, int x, double y, int z, Material type) {
	FallingBlock fb = world.spawn(new Location(world, x+.5, y, z+.5), FallingBlock.class);
	fb.setBlockData(type.createBlockData()); fb.setGravity(false); fb.setDropItem(false);
	return fb;
}

public void removeFallingBlocks() {
	int yMin=elev.yMin(), yMax=elev.yMax();
	for(Entity e: world.getEntitiesByClass(FallingBlock.class)) {
		Location l=e.getLocation(); if((l.getX()>=xMin-.5 && l.getX()<=xMax+.5) &&
			(l.getY()>=yMin-.5 && l.getY()<=yMax+.5) && (l.getZ()>=zMin-.5 && l.getZ()<=zMax+.5)) e.remove();
	}
}
}