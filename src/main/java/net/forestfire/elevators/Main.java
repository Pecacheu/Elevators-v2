//Elevators v2, ©2020 Pecacheu. Licensed under GNU GPL 3.0

package net.forestfire.elevators;

import org.bukkit.Bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class Main extends JavaPlugin implements Listener {
static final String PERM_USE    = "elevators.use";
static final String PERM_CREATE = "elevators.create";
static final String PERM_RELOAD = "elevators.reload";
public static Elevator LAST_ELEV;

@Override
public void onEnable() {
	Conf.initDefaults(this); setTimeout(() -> Conf.doConfLoad(null), 200);
	getServer().getPluginManager().registerEvents(this, this);
	Bukkit.getConsoleSender().sendMessage(Conf.MSG_DBG+"§dElevators Plugin Loaded!");
}

@Override
public void onDisable() {
	Conf.saveConf(true); HandlerList.unregisterAll();
}

@Override
public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
	if(c.getName().equalsIgnoreCase("elev")) {
		if(a.length > 0 && a[0].equalsIgnoreCase("reset")) {
			if(LAST_ELEV != null) { LAST_ELEV.resetElevator(a.length > 1); s.sendMessage("Last elevator reset."); }
			else s.sendMessage("§cNo last elevator.");
		} else if(a.length == 1 && a[0].equalsIgnoreCase("reload")) {
			setTimeout(() -> Conf.doConfLoad(s), 200);
		} else s.sendMessage("§cUsage: /elev reload");
		return true;
	}
	return false;
}

//JavaScript-like Timer Functionality:
//Call .cancel() on the returned value to cancel.

public BukkitTask setTimeout(Runnable f, long ms) {
	return new BukkitRunnable() { public void run() { synchronized(Conf.API_SYNC) { f.run(); }}}.runTaskLater(this, ms/50);
}
public BukkitTask setInterval(BukkitRunnable f, long ms) { ms/=50; return f.runTaskTimer(this,ms,ms); }

//------------------- Elevator Create Sign Events -------------------

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onSignChange(SignChangeEvent e) { Block sign = e.getBlock();
	synchronized(Conf.API_SYNC) { if(sign.getBlockData() instanceof WallSign) {
	if(e.getLine(0).equalsIgnoreCase("[call]") && Conf.hasPerm(e.getPlayer(), PERM_CREATE)) { //Call Sign:
		e.setLine(0, "> ERROR <"); e.setLine(1, ""); e.setLine(2, "");

		//Is sign in elevator?
		CSData ret = Elevator.fromCallSign(sign); if(ret==null) { e.setLine(0, Conf.ERROR); Conf.err("onSignChange:CallSign", "No elevator found!"); return; }
		Elevator elev = ret.elev; int csNum = ret.index; if(elev.floor.moving) { e.setCancelled(true);
			sign.setType(Conf.AIR); Conf.err("onSignChange:CallSign", "Elevator is moving!"); return; }

		//Build sign list:
		elev.rebuildCallSignList(sign.getLocation());

		//Update call signs:
		elev.updateCallSigns(elev.getLevel());
		e.setLine(0, Conf.CALL); e.setLine(3, elev.csData.get(csNum));

	} else if(e.getLine(0).equalsIgnoreCase("[elevator]") && Conf.hasPerm(e.getPlayer(), PERM_CREATE)) { //Elevator Sign:
		e.setLine(0, "> ERROR <"); e.setLine(1, "");

		//Is sign in elevator?
		Elevator elev = Elevator.fromElevSign(sign);

		//Build sign list:
		ChuList<Block> sList = Elevator.rebuildSignList(sign.getLocation()); int col=-1;

		if(elev!=null) { //Existing Elevator:
			if(elev.floor.moving) {
				e.setCancelled(true); sign.setType(Conf.AIR); Conf.err("onSignChange:ElevSign", "Elevator is moving!"); return;
			}
			for(int i=0,l=elev.sGroups.length; i<l; i++) { //Check if X and Z match existing sList:
				Block dSign = elev.sGroups.get(i).get(0);
				if(dSign.getX() == sign.getX() && dSign.getZ() == sign.getZ()) { col=i; break; }
			}
			if(col != -1) { //Existing Column:
				for(int k=0,m=elev.sGroups.length; k<m; k++) if(k != col) { //Iterate through sGroups:
					ChuList<Block> oList = elev.sGroups.get(k); int sX = oList.get(0).getX(), sZ = oList.get(0).getZ();
					WallSign d = (WallSign)sList.get(0).getBlockData(); World w = elev.floor.world;
					for(int i=0,l=oList.length; i<l; i++) oList.get(i).setType(Conf.AIR); //Delete old signs in other columns.
					int sl=sList.length; oList=new ChuList<>(sl); for(int i=0; i<sl; i++) { //Rebuild to match new column.
						Block bl = w.getBlockAt(sX, sList.get(i).getY(), sZ); bl.setType(sign.getType());
						bl.setBlockData(d); Conf.addSignBlock(bl); Conf.setSign(bl, Conf.lines(sList.get(i)));
						oList.add(bl);
					}
					elev.sGroups.set(k, oList);
				}
			} else { //New Column:
				ChuList<Block> sRef = elev.sGroups.get(0); int sX = sList.get(0).getX(), sZ = sList.get(0).getZ();
				WallSign d = (WallSign)sList.get(0).getBlockData(); World w = elev.floor.world;
				for(int i=0,l=sList.length; i<l; i++) sList.get(i).setType(Conf.AIR); //Delete old signs in column.
				int sl=sRef.length; sList=new ChuList<>(sl); for(int i=0; i<sl; i++) { //Rebuild to match other columns.
					Block bl = w.getBlockAt(sX, sRef.get(i).getY(), sZ); bl.setType(sign.getType());
					bl.setBlockData(d); Conf.addSignBlock(bl); Conf.setSign(bl, Conf.lines(sRef.get(i)));
					sList.add(bl);
				}
				elev.sGroups.add(sList); Conf.saveConf(); return; //Add new signs to elevator and save.
			}
		} else { //New elevator:
			Floor f=Floor.getFloor(sign, null); if(f==null) {
				e.setLine(0, Conf.ERROR); Conf.err("onSignChange:ElevSign:NewElev", "Floor not found!"); return;
			}
			String eID = Conf.locToString(new Location(f.world, f.xMin, 0, f.zMin));
			elev = new Elevator(f, null, null); Conf.elevators.put(eID, (f.elev=elev));
			e.getPlayer().sendMessage("§eElevator created.");
		}

		//Validate Sign Placement:
		boolean cErr = false; for(int i=0; i<sList.length; i++) {
			if(i > 0 && (sList.get(i).getY() - sList.get(i-1).getY()) < 3) { //Signs too close!
				Conf.setLine(sList.get(i), 0, Conf.ERROR);
				if(sList.get(i).getLocation().equals(sign.getLocation())) cErr=true; sList.remove(i); i--;
			}
		}

		//Update Elevator Data:
		if(col == -1) { elev.sGroups = new ChuList<ChuList<Block>>(sList); } else elev.sGroups.set(col, sList);

		//Line 2 Special Modes:
		if(elev.noDoor) elev.sGroups.get(0).forEach((s) -> Conf.setLine(s, 2, ""));

		//Set modes:
		String sLine = e.getLine(2);
		if(elev.noDoor || sLine.equalsIgnoreCase("[nodoor]")) { //Enable NoDoor Mode:
			elev.noDoor = true; Block first = elev.sGroups.get(0).get(0);
			if(first.equals(sign)) e.setLine(2, Conf.NODOOR);
			else { Conf.setLine(first, 2, Conf.NODOOR); e.setLine(2, ""); }
		} else e.setLine(2, "");

		//Update Sign Level Numbers for Non-Custom-Named Signs:
		for(int k=0,m=elev.sGroups.length; k<m; k++) for(int f=0,d=elev.sGroups.get(k).length; f<d; f++) {
			Block s = elev.sGroups.get(k).get(f);
			if(s.getLocation().equals(sign.getLocation())) { if(e.getLine(3).length()==0) e.setLine(3, "Level "+(f+1)); }
			else if(Conf.lines(s)[3].matches("^Level [0-9]+$")) Conf.setLine(s, 3, "Level "+(f+1));
			else if(s.getY() == sign.getY()) { Conf.setSign(s, e.getLines()); Conf.setLine(s, 0, Conf.TITLE); }
		}

		//Set Elevator Doors, Blocks, Call Signs:
		elev.floor.removeFallingBlocks(); elev.resetElevator(); elev.doorTimer(elev.sGroups.get(0).get(0).getY());
		elev.rebuildCallSignList(null); elev.updateCallSigns(elev.getLevel()); for(int i=0,l=sList.length; i<l; i++)
		{ elev.setDoors(sList.get(i).getY(), i==0); elev.floor.addFloor(sList.get(i).getY()-2, false, true); }

		if(cErr) e.setLine(0, Conf.ERROR); else e.setLine(0, Conf.TITLE); //Display ERROR line if error.
		Conf.saveConf(); //Save Changes To Config.
	}
}}}

//------------------- Elevator Destroy Sign/Block Events -------------------

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockPhysics(BlockPhysicsEvent e) { synchronized(Conf.API_SYNC) {
	Block b = e.getBlock(); if(b.getBlockData() instanceof WallSign) {
		if(Conf.CALL.equals(Conf.lines(b)[0]) || Conf.TITLE.equals(Conf.lines(b)[0])) {
			Conf.addSignBlock(b); e.setCancelled(true); Conf.dbg("CancelSignBreak"); //Prevent sign break.
		}
	}
}}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent e) { synchronized(Conf.API_SYNC) {
	Block b = e.getBlock(); if(b.getBlockData() instanceof WallSign) {
		if(Conf.CALL.equals(Conf.lines(b)[0])) onDestroyCallSign(e, e.getPlayer(), b); //Call Sign
		else if(Conf.TITLE.equals(Conf.lines(b)[0])) onDestroyElevSign(e, e.getPlayer(), b); //Elevator Sign
	} else if(Elevator.fromElevBlock(b) != null) { //Block Door:
		e.setCancelled(true); b.setType(Conf.AIR); Conf.dbg("PreventBDDrop"); //Prevent Survival dupe exploit!
	}
}}

private void onDestroyCallSign(Cancellable e, Player p, Block b) {
	if(Conf.CLTMR != null) { e.setCancelled(true); Conf.err("onBlockBreak:CallSign", "Door timer is running!"); return; }
	if(!Conf.hasPerm(p, PERM_CREATE)) { Conf.err("onBlockBreak:CallSign", "No permission!"); e.setCancelled(true); return; }

	//Is sign in elevator?
	CSData ret = Elevator.fromCallSign(b); if(ret==null) { Conf.err("onBlockBreak:CallSign", "No elevator found!"); return; }
	Elevator elev = ret.elev; int csNum = ret.index;
	if(elev.floor.moving) { e.setCancelled(true); Conf.err("onBlockBreak:CallSign", "Elevator is moving!"); return; }
	Conf.dbg("DestroyCallSign");

	//Build sign list:
	elev.rebuildCallSignList(b.getLocation());
	if(elev.csGroups.get(csNum).remove(elev.csGroups.get(csNum).indexOf(b)) == null) {
		Conf.err("onBlockBreak:CallSign", "Call sign not found in csList!"); return;
	}

	//Update call signs:
	elev.updateCallSigns(elev.getLevel());
}

private void onDestroyElevSign(Cancellable e, Player p, Block b) {
	if(Conf.CLTMR != null) { e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "Door timer is running!"); return; }
	if(!Conf.hasPerm(p, PERM_CREATE)) { e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "No permission!"); return; }

	//Is sign in elevator?
	Elevator elev = Elevator.fromElevSign(b); if(elev==null) { Conf.err("onBlockBreak:ElevSign", "No elevator found!"); return; }
	if(elev.floor.moving) { e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "Elevator is moving!"); return; }
	Conf.dbg("DestroyElevSign");

	//Build sign list:
	ChuList<Block> sList = Elevator.rebuildSignList(b.getLocation()); elev.resetElevator();

	//Find sGroups index:
	int ind = -1; for(int i=0,l=elev.sGroups.length; i<l; i++) if(b.getX() == elev.sGroups.get(i).get(0).getX()
			&& b.getZ() == elev.sGroups.get(i).get(0).getZ()) { ind = i; break; }
	if(ind == -1) { Conf.err("onBlockBreak:ElevSign", "Cannot determine column index."); return; }

	if(elev.sGroups.length > 1) { //Delete Whole Columns:
		for(int i=0,l=sList.length; i<l; i++) sList.get(i).setType(Conf.AIR);
		elev.sGroups.remove(ind); //Remove column from elevator.
	} else { //Only One Column Left:
		int sInd=sList.indexOf(b); if(sInd == -1) {
			Conf.err("onBlockBreak:ElevSign:LastColumn", "Cannot determine subList index."); return;
		}
		Floor f=elev.floor;
		for(int i=0,l=sList.length; i<l; i++) if(i != sInd) f.addFloor(sList.get(i).getY()-2, false, true); //Add floors.
		ChuList<Block> cs = elev.csGroups.get(sInd);
		if(cs != null) for(int h=0,d=cs.length; h<d; h++) cs.get(h).setType(Conf.AIR); //Delete call signs on level.
		if(sList.length <= 1) { //Delete elevator instance. This meeting... never happened.
			elev.selfDestruct(); p.sendMessage("§eElevator destroyed.");
		} else { sList.remove(sInd); elev.sGroups.set(ind, sList); } //Remove sign from elevator.
		int y=b.getY()-2; for(int x=f.xMin; x<=f.xMax; x++) for(int z=f.zMin; z<=f.zMax; z++)
			f.world.getBlockAt(x,y,z).setType(Conf.AIR); //Remove floor.
		Block pBl=p.getLocation().add(0,-1,0).getBlock();
		if(pBl.isEmpty()) pBl.setType(Material.DIRT); //Add block below player.
		elev.remBlockDoor(b.getY()); //Delete block door.
	}

	Conf.saveConf(); //Save Changes To Config.
}

//------------------- Piston Interact Events -------------------

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPistonExtend(BlockPistonExtendEvent e) { synchronized(Conf.API_SYNC) {
	for(Block b: e.getBlocks()) if(Elevator.fromElevBlock(b) != null) {
		e.setCancelled(true); Conf.err("onPistonExtend", "Prevent block destroy!");
	}
}}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPistonRetract(BlockPistonRetractEvent e) { synchronized(Conf.API_SYNC) {
	for(Block b: e.getBlocks()) if(Elevator.fromElevBlock(b) != null) {
		e.setCancelled(true); Conf.err("onPistonRetract", "Prevent block destroy!");
	}
}}

//------------------- Elevator Block-Clicking Events -------------------

@EventHandler(priority = EventPriority.NORMAL)
public void onPlayerInteract(PlayerInteractEvent e) { synchronized(Conf.API_SYNC) { ConfData ref = new ConfData();
	Action act = e.getAction(); if(act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getBlockData() instanceof WallSign) {
		if(Conf.isElevSign(e.getClickedBlock(), ref, e.getPlayer(), PERM_USE) && !((Elevator)ref.data).floor.moving) { //Select Floor:
			Elevator elev=((Elevator)ref.data); LAST_ELEV=elev;
			FList fl=elev.getFloors(); int sn=fl.sn;

			//Increment Floor Number:
			if(e.getPlayer().isSneaking()) { if(--sn<0) sn=fl.fl.length-1; } else if(++sn >= fl.fl.length) sn=0;
			elev.updateFloorName(fl.fl.get(sn));

			e.setCancelled(true);
		} else if(Conf.isCallSign(e.getClickedBlock(), ref, e.getPlayer(), PERM_USE) && !((CSData)ref.data).elev.floor.moving) { //Call Sign Click:
			Elevator elev=((CSData)ref.data).elev; int fLevel = elev.getLevel(), sLevel = e.getClickedBlock().getY()-2;
			LAST_ELEV=elev;

			//Call Elevator to Floor:
			if(fLevel != sLevel) {
				e.getPlayer().sendMessage(Conf.MSG_CALL);
				elev.gotoFloor(fLevel, sLevel, ((CSData)ref.data).index, false);
			} else elev.doorTimer(sLevel+2); //Re-open doors if already on level.

			e.setCancelled(true);
		}} else if((act == Action.RIGHT_CLICK_BLOCK || act == Action.LEFT_CLICK_AIR) && Conf.isElevPlayer(e.getPlayer(), ref, PERM_USE)
			&& (e.getItem() == null || e.getPlayer().isSneaking()) && !((Elevator)ref.data).floor.moving) { //Goto Floor:
		Elevator elev=((Elevator)ref.data);

		//Get Current & Selected Floor:
		int fLev=elev.getLevel(), fSel=elev.getFloors().sn, sLev=elev.sGroups.get(0).get(fSel).getY()-2;

		//Move Elevator:
		if(fLev != sLev) elev.gotoFloor(fLev, sLev, fSel, true);
		else elev.doorTimer(sLev+2); //Re-open doors if already on level.

		e.setCancelled(true);
	} else if(act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getBlockData() instanceof Openable) {
		Elevator elev = Elevator.fromDoor(e.getClickedBlock().getLocation());
		if(elev != null) { e.setCancelled(true); Conf.dbg("CancelDoorOpen"); } //Cancel elevator door click.
	}
}}
}