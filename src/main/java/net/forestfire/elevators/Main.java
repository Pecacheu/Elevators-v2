//Elevators v2, ©2020 Pecacheu. Licensed under GNU GPL 3.0

package net.forestfire.elevators;

import net.kyori.adventure.text.Component;
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
import org.jetbrains.annotations.NotNull;
import java.util.Collection;

public class Main extends JavaPlugin implements Listener {
static final String PERM_USE="elevators.use", PERM_CREATE="elevators.create", PERM_RELOAD="elevators.reload";
public static Elevator LAST_ELEV;

@Override
public void onEnable() {
	Conf.initDefaults(this);
	Conf.doConfLoad(null);
	getServer().getPluginManager().registerEvents(this, this);
	Conf.msg(null, Conf.MSG_DBG+"&dElevators Plugin Loaded!");
}
@Override
public void onDisable() {
	Conf.saveConf(true);
	HandlerList.unregisterAll();
}
@Override
public boolean onCommand(@NotNull CommandSender s, Command c, @NotNull String l, String[] a) {
	if(c.getName().equalsIgnoreCase("elev")) {
		if(a.length == 1 && a[0].equals("list")) {
			Collection<Elevator> el = Conf.elevators.values();
			Conf.msg(s, el.size()+" Elevators:");
			for(Elevator e: el) {
				Floor f = e.floor;
				Block b = e.sGroups.getFirst().getFirst();
				Conf.msg(s, "Elevator in &d"+f.world.getName()+" &rat &b["+b.getX()
					+","+b.getZ()+"]&r; Size: &b"+(f.xMax-f.xMin+1)+"x"+(f.zMax-f.zMin+1));
			}
		} else if(a.length>0 && a[0].equals("reset")) {
			if(LAST_ELEV != null) {
				LAST_ELEV.resetElevator(a.length>1);
				Conf.msg(s, "Last elevator reset.");
			} else Conf.msg(s, "&cNo last elevator.");
		} else if(a.length == 1 && a[0].equals("reload")) {
			setTimeout(() -> Conf.doConfLoad(s), 200);
		} else Conf.msg(s, "&cUsage: /elev <list|reload|reset>");
		return true;
	}
	return false;
}

//JavaScript-like Timer Functionality
//Call .cancel() on the returned value to cancel
public BukkitTask setTimeout(Runnable f, long ms) {
	return new BukkitRunnable() {
		public void run() {synchronized(Conf.API_SYNC) { f.run(); }}
	}.runTaskLater(this, ms/50);
}
public void setInterval(BukkitRunnable f, long ms) { ms/=50; f.runTaskTimer(this,ms,ms); }

private boolean checkLine(SignChangeEvent e, String test) {
	Component l=e.line(0);
	if(l != null) return Conf.cs(l).equalsIgnoreCase(test);
	return false;
}

//------------------- Elevator Create Sign Events -------------------

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onSignChange(SignChangeEvent e) { Block sign = e.getBlock();
	synchronized(Conf.API_SYNC) { if(sign.getBlockData() instanceof WallSign) {
	if(checkLine(e, "[call]") && Conf.hasPerm(e.getPlayer(), PERM_CREATE, true)) { //Call Sign
		e.line(0,Conf.sc("> ERROR <")); e.line(1,Conf.sc("")); e.line(2,Conf.sc(""));

		//Is sign in elevator?
		CSData ret = Elevator.fromCallSign(sign); if(ret==null) {
			e.line(0,Conf.sc(Conf.ERROR));
			Conf.err("onSignChange:CallSign", "No elevator found!"); return;
		}
		Elevator elev = ret.elev; int csNum = ret.index;
		if(elev.floor.moving) {
			e.setCancelled(true); sign.setType(Conf.AIR);
			Conf.err("onSignChange:CallSign", "Elevator is moving!"); return;
		}

		//Build sign list
		elev.rebuildCallSignList(sign.getLocation());

		//Update call signs
		elev.updateCallSigns(elev.getLevel());
		e.line(0,Conf.sc(Conf.CALL)); e.line(3,Conf.sc(elev.csData.get(csNum)));
	} else if(checkLine(e, "[elevator]")
			&& Conf.hasPerm(e.getPlayer(), PERM_CREATE, true)) { //Elevator Sign
		e.line(0,Conf.sc("> ERROR <")); e.line(1,Conf.sc(""));

		//Is sign in elevator?
		Elevator elev=Elevator.fromElevSign(sign); ChuList<Block> sList; int col=-1;

		if(elev!=null) { //Existing Elevator
			if(elev.floor.moving) {
				e.setCancelled(true); sign.setType(Conf.AIR);
				Conf.err("onSignChange:ElevSign", "Elevator is moving!"); return;
			}
			World w=elev.floor.world; int sX=sign.getX(), sY=sign.getY(), sZ=sign.getZ();
			for(int i=0,l=elev.sGroups.length; i<l; i++) { //Check for col XZ match
				Block ds=elev.sGroups.get(i).getFirst(); if(ds.getX()==sX && ds.getZ()==sZ) { col=i; break; }
			}
			if(col != -1) { //Existing Column
				sList=Elevator.rebuildSignList(sign);
				for(int k=0,m=elev.sGroups.length; k<m; k++) if(k != col) { //Rebuild other cols to match
					Block s=elev.sGroups.get(k).getFirst(), b=w.getBlockAt(s.getX(), sY, s.getZ());
					b.setType(s.getType()); b.setBlockData(s.getBlockData());
					Conf.addSignBlock(b); Conf.line(b,0,Conf.TITLE);
					elev.sGroups.set(k,Elevator.rebuildSignList(new Location(w,s.getX(),0,s.getZ())));
				}
			} else { //New Column
				WallSign d=(WallSign)sign.getState().getBlockData();
				Material t=sign.getType(); sign.setType(Conf.AIR);
				ChuList<Block> sRef=elev.sGroups.getFirst(); int sl=sRef.length; sList=new ChuList<>(sl);
				for(int i=0; i<sl; i++) { //Rebuild to match other cols
					Block b=w.getBlockAt(sX, sRef.get(i).getY(), sZ); b.setType(t); b.setBlockData(d);
					Conf.addSignBlock(b); Conf.lines(b,Conf.lines(sRef.get(i))); sList.add(b);
				}
			}
		} else { //New elevator
			Floor f=Floor.getFloor(sign, null); if(f==null) {
				e.line(0,Conf.sc(Conf.ERROR));
				Conf.err("onSignChange:ElevSign:NewElev", "Floor not found!"); return;
			}
			String eID=Conf.locToString(new Location(f.world, f.xMin, 0, f.zMin));
			elev=new Elevator(f, null, null); Conf.elevators.put(eID, (f.elev=elev));
			sList=Elevator.rebuildSignList(sign); Conf.msg(e.getPlayer(), "&eElevator created.");
		}

		//Update Elevator Data
		if(col == -1) elev.sGroups.add(sList); else elev.sGroups.set(col,sList);
		if(elev.noDoor) elev.sGroups.getFirst().forEach((s) -> Conf.line(s,2,"")); //NoDoor

		//Validate Sign Placement
		boolean cErr=false; for(int i=0; i<sList.length; i++) {
			if(i>0 && (sList.get(i).getY()-sList.get(i-1).getY()) < 3) { //Signs too close!
				if(sList.get(i).getY()==sign.getY()) cErr=true;
				for(ChuList<Block> g: elev.sGroups) Conf.lines(g.remove(i),new String[]{Conf.ERROR,"","",""}); i--;
			}
		}

		//Set modes
		String sl=Conf.cs(e.line(2));
		if(elev.noDoor || sl.equalsIgnoreCase("[nodoor]")) { //Enable NoDoor Mode
			elev.noDoor = true; Block f=elev.sGroups.getFirst().getFirst();
			if(f.equals(sign)) e.line(2,Conf.sc(Conf.NODOOR));
			else { Conf.line(f,2,Conf.NODOOR); e.line(2,Conf.sc("")); }
		} else e.line(2,Conf.sc(""));

		//Set Elevator Doors, Blocks, Call Signs
		elev.floor.removeFallingBlocks(); elev.rebuildCallSignList(null);
		elev.resetElevator(); elev.updateSignNames(e); elev.updateCallSigns(elev.getLevel());
		for(int i=0,l=sList.length; i<l; i++) {
			elev.setDoors(i, i==0);
			elev.floor.addFloor(sList.get(i).getY()-2, false, true);
		}
		if(col == -1) elev.doorTimer(0);

		e.line(0,Conf.sc(cErr?Conf.ERROR:Conf.TITLE));
		Conf.saveConf(); //Save Changes To Config
	}
}}}

//------------------- Elevator Destroy Sign/Block Events -------------------

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockPhysics(BlockPhysicsEvent e) { synchronized(Conf.API_SYNC) {
	Block b = e.getBlock(); if(b.getBlockData() instanceof WallSign) {
		if(Conf.CALL.equals(Conf.line(b,0)) || Conf.TITLE.equals(Conf.line(b,0))) {
			Conf.addSignBlock(b); e.setCancelled(true); Conf.dbg("CancelSignBreak"); //Prevent sign break
		}
	}
}}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent e) { synchronized(Conf.API_SYNC) {
	Block b = e.getBlock(); if(b.getBlockData() instanceof WallSign) {
		if(Conf.CALL.equals(Conf.line(b,0))) onDestroyCallSign(e, e.getPlayer(), b); //Call Sign
		else if(Conf.TITLE.equals(Conf.line(b,0))) onDestroyElevSign(e, e.getPlayer(), b); //Elevator Sign
	} else if(Elevator.fromElevBlock(b) != null) { //Block Door
		e.setCancelled(true); b.setType(Conf.AIR); Conf.dbg("PreventBDDrop"); //Prevent Survival dupe exploit!
	}
}}

private void onDestroyCallSign(Cancellable e, Player p, Block b) {
	if(Conf.CLTMR != null) {
		e.setCancelled(true); Conf.err("onBlockBreak:CallSign", "Door timer is running!"); return;
	}
	if(!Conf.hasPerm(p, PERM_CREATE, true)) {
		Conf.err("onBlockBreak:CallSign", "No permission!"); e.setCancelled(true); return;
	}

	//Is sign in elevator?
	CSData ret=Elevator.fromCallSign(b);
	if(ret==null) { Conf.err("onBlockBreak:CallSign", "No elevator found!"); return; }
	Elevator elev=ret.elev; int csNum=ret.index;
	if(elev.floor.moving) {
		e.setCancelled(true); Conf.err("onBlockBreak:CallSign", "Elevator is moving!"); return;
	}
	Conf.dbg("DestroyCallSign");

	//Build sign list
	elev.rebuildCallSignList(null);
	if(elev.csGroups.get(csNum).remove(elev.csGroups.get(csNum).indexOf(b)) == null) {
		Conf.err("onBlockBreak:CallSign", "Call sign not found in csList!"); return;
	}

	//Update call signs
	elev.updateCallSigns(elev.getLevel());
}

private void onDestroyElevSign(Cancellable e, Player p, Block b) {
	if(Conf.CLTMR != null) {
		e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "Door timer is running!"); return;
	}
	if(!Conf.hasPerm(p, PERM_CREATE, true)) {
		e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "No permission!"); return;
	}

	//Is sign in elevator?
	Elevator elev = Elevator.fromElevSign(b);
	if(elev==null) { Conf.err("onBlockBreak:ElevSign", "No elevator found!"); return; }
	if(elev.floor.moving) {
		e.setCancelled(true); Conf.err("onBlockBreak:ElevSign", "Elevator is moving!"); return;
	}
	Conf.dbg("DestroyElevSign");

	//Build sign list
	ChuList<Block> sList = Elevator.rebuildSignList(b); elev.resetElevator();

	//Find sGroups index
	int ind=-1;
	for(int i=0,l=elev.sGroups.length; i<l; i++) {
		if(b.getX() == elev.sGroups.get(i).getFirst().getX()
			&& b.getZ() == elev.sGroups.get(i).getFirst().getZ()) { ind=i; break; }
	}
	if(ind == -1) { Conf.err("onBlockBreak:ElevSign", "Cannot determine column index."); return; }

	if(elev.sGroups.length>1) { //Delete Whole Columns
		for(int i=0,l=sList.length; i<l; i++) sList.get(i).setType(Conf.AIR);
		elev.sGroups.remove(ind); //Remove column from elevator
	} else { //Only One Column Left
		int sInd=sList.indexOf(b); if(sInd == -1) {
			Conf.err("onBlockBreak:ElevSign:LastColumn", "Cannot determine subList index."); return;
		}
		Floor f=elev.floor;
		for(int i=0,l=sList.length; i<l; i++) {
			if(i != sInd) f.addFloor(sList.get(i).getY()-2, false, true); //Add floors
		}
		ChuList<Block> cs = elev.csGroups.get(sInd);
		if(cs != null) for(int h=0,d=cs.length; h<d; h++) cs.get(h).setType(Conf.AIR); //Delete call signs on level
		if(sList.length <= 1) { //Delete elevator. This meeting... never happened
			elev.selfDestruct(); Conf.msg(p, "&eElevator destroyed.");
		} else { sList.remove(sInd); elev.sGroups.set(ind, sList); } //Remove sign from elevator
		int y=b.getY()-2; for(int x=f.xMin; x<=f.xMax; x++) for(int z=f.zMin; z<=f.zMax; z++)
			f.world.getBlockAt(x,y,z).setType(Conf.AIR); //Remove floor
		elev.remBlockDoor(b.getY()); //Delete block door
	}

	Block pBl=p.getLocation().add(0,-1,0).getBlock();
	if(pBl.isEmpty()) pBl.setType(Material.DIRT); //Add block below player
	if(elev.floor != null) { elev.updateSignNames(null); elev.rebuildCallSignList(null); }
	Conf.saveConf(); //Save Changes To Config
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

@EventHandler
public void onPlayerInteract(PlayerInteractEvent e) { synchronized(Conf.API_SYNC) {
	ConfData ref = new ConfData(); Action act = e.getAction();
	if(act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getBlockData() instanceof WallSign) {
		if(Conf.isElevSign(e.getClickedBlock(), ref, e.getPlayer())
				&& !((Elevator)ref.data).floor.moving) { //Select Floor
			Elevator elev=((Elevator)ref.data); LAST_ELEV=elev;
			FList fl=elev.getFloors(); int sn=fl.sn;

			//Increment Floor Number
			if(e.getPlayer().isSneaking()) { if(--sn<0) sn=fl.fl.length-1; }
			else if(++sn >= fl.fl.length) sn=0;
			elev.updateFloorName(fl.fl.get(sn));

			e.setCancelled(true);
		} else if(Conf.isCallSign(e.getClickedBlock(), ref, e.getPlayer())
				&& !((CSData)ref.data).elev.floor.moving) { //Call Sign Click
			Elevator elev=((CSData)ref.data).elev; int from=elev.getFloor(), to=((CSData)ref.data).index;
			LAST_ELEV=elev;

			//Call Elevator to Floor
			if(from != to) {
				Conf.msg(e.getPlayer(), Conf.MSG_CALL);
				elev.gotoFloor(from, to, false);
			} else elev.doorTimer(to); //Re-open doors if already on level

			e.setCancelled(true);
		}
	} else if((act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK
			&& (e.getItem()==null || e.getPlayer().isSneaking()))
			&& Conf.isElevPlayer(e.getPlayer(), ref) && !((Elevator)ref.data).floor.moving) { //Goto Floor
		Elevator elev=((Elevator)ref.data); LAST_ELEV=elev;

		//Get Current & Selected Floor
		int from=elev.getFloor(), to=elev.getFloors().sn;

		//Move Elevator
		if(from != to) elev.gotoFloor(from, to, true);
		else elev.doorTimer(to); //Re-open doors if already on level

		e.setCancelled(true);
	} else if(act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getBlockData() instanceof Openable) {
		Elevator elev = Elevator.fromDoor(e.getClickedBlock().getLocation());
		if(elev != null) { e.setCancelled(true); Conf.dbg("CancelDoorOpen"); } //Cancel elevator door click
	}
}}
}