//This work is licensed under a GNU General Public License. Visit http://gnu.org/licenses/gpl-3.0-standalone.html for details.
//Pecacheu's Elevator Plugin v2. Copyright (©) 2016, Pecacheu (Bryce Peterson, bbryce.com).

//Pecacheu's Elevator Plugin v2.0! Recreated from scratch!

package com.pecacheu.elevators;

//TODO Always Check TODOs Regularly!
//TODO NODOOR setting for individual levels?

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class Main extends JavaPlugin implements Listener {
	static final String PERM_USE    = "elevators.use";
	static final String PERM_CREATE = "elevators.create";
	static final String PERM_RELOAD = "elevators.reload";
	
	private BukkitTask svTmr = null;
	
	@Override
	public void onEnable() {
		Conf.initDefaults(this);
		//Config Auto-save Timer:
		setTimeout(() -> {
			Conf.doConfigLoad();
			svTmr = setInterval(() -> { Conf.saveConfig(); }, Conf.SAVE_INT*60000);
		}, 200);
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		if(svTmr != null) { svTmr.cancel(); svTmr = null; }
		HandlerList.unregisterAll();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("elev")) {
			if(args.length == 1 && args[0].equalsIgnoreCase("reload")) {
				setTimeout(() -> { Conf.doConfigLoad(sender); }, 200);
			} else sender.sendMessage("§cUsage: /elev reload");
			return true;
		}
		return false;
	}
	
	//JavaScript-like Timer Functionality:
	//Call .cancel() on the returned value to cancel.
	
	public BukkitTask setTimeout(Runnable function, long millis) {
		return new BukkitRunnable() { public void run() { synchronized(Conf.API_SYNC) { function.run(); }}}.runTaskLater(this, millis/50);
	}
	
	public BukkitTask setInterval(Runnable function, long millis) {
		long t=millis/50; return new BukkitRunnable() { public void run() { synchronized(Conf.API_SYNC) { function.run(); }}}.runTaskTimer(this, t, t);
	} public BukkitTask setInterval(BukkitRunnable function, long millis) { long t=millis/50; return function.runTaskTimer(this, t, t); }
	
	//------------------- Elevator Create Sign Events -------------------
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSignChange(SignChangeEvent event) { synchronized(Conf.API_SYNC) { if(!Conf.DISABLED && event.getBlock().getType() == Material.WALL_SIGN) {
		if(event.getLine(0).equalsIgnoreCase("[call]") && Conf.hasPerm(event.getPlayer(), PERM_CREATE)) { //Call Sign:
			event.setLine(0, "> ERROR <");
			event.setLine(1, ""); event.setLine(2, "");
			Block block = event.getBlock();
			
			//Is sign in elevator?
			CSData ret = Elevator.fromCallSign(block); if(ret==null) { event.setLine(0, Conf.ERROR); Conf.err("onSignChange:CallSign", "No elevator found!"); return; }
			Elevator elev = ret.elev; int csNum = ret.index; if(elev.floor.moving) { event.setCancelled(true);
			block.setType(Conf.AIR); Conf.err("onSignChange:CallSign", "Elevator is moving!"); return; }
			
			//Build sign list:
			elev.csGroups = elev.rebuildCallSignList(block.getLocation());
			
			//Update call signs:
			elev.updateCallSigns(elev.getLevel());
			event.setLine(0, Conf.CALL); event.setLine(3, elev.csData.get(csNum));
			
		} else if(event.getLine(0).equalsIgnoreCase("[elevator]") && Conf.hasPerm(event.getPlayer(), PERM_CREATE)) { //Elevator Sign:
			event.setLine(0, "> ERROR <"); event.setLine(1, "");
			Block block = event.getBlock();
			
			//Is sign in elevator?
			Elevator elev = Elevator.fromElevSign(block);
			
			//Build sign list:
			ChuList<Block> sList = Elevator.rebuildSignList(block.getLocation()); int ind = 0;
			
			if(elev!=null) { //Existing Elevator:
				if(elev.floor.moving) { event.setCancelled(true); block.setType(Conf.AIR); Conf.err("onSignChange:ElevSign", "Elevator is moving!"); return; }
				int column = -1; for(int i=0,l=elev.sGroups.length; i<l; i++) { Block dSign = elev.sGroups.get(i)
				.get(0); if(dSign.getX() == block.getX() && dSign.getZ() == block.getZ()) { column = i; break; }} //Check if X and Z match existing sList.
				if(column != -1) { //Existing Column:
					ind = column; for(int k=0,m=elev.sGroups.length; k<m; k++) if(k != ind) { //Iterate through sGroups:
						ChuList<Block> oList = elev.sGroups.get(k); int sX = oList.get(0).getX(), sZ = oList.get(0).getZ(); byte sData = oList.get(0).getData();
						for(int i=0,l=oList.length; i<l; i++) oList.get(i).setType(Conf.AIR); //Delete old signs in other columns.
						oList = new ChuList<Block>(); for(int i=0,l=sList.length; i<l; i++) { Block bl = elev.floor.world.getBlockAt(sX, sList.get(i)
						.getY(), sZ); bl.setType(Material.WALL_SIGN); bl.setData(sData); Conf.setSign(bl, Conf.lines(sList.get(i))); oList.push(bl); } //Rebuild to match new column.
						elev.sGroups.set(k, oList);
					}
				} else { //New Column:
					ChuList<Block> sRef = elev.sGroups.get(0); int sX = sList.get(0).getX(), sZ = sList.get(0).getZ();
					byte sData = sList.get(0).getData(); World world = elev.floor.world;
					for(int i=0,l=sList.length; i<l; i++) sList.get(i).setType(Conf.AIR); //Delete old signs in column.
					sList = new ChuList<Block>(); for(int i=0,l=sRef.length; i<l; i++) { Block bl = world.getBlockAt(sX, sRef.get(i).getY(),
					sZ); bl.setType(Material.WALL_SIGN); bl.setData(sData); Conf.setSign(bl, Conf.lines(sRef.get(i))); sList.push(bl); } //Rebuild to match other columns.
					elev.sGroups.push(sList); setTimeout(() -> { Conf.saveConfig(); }, 200); return; //Add new signs to elevator and save.
				}
			} else { //New elevator:
				Floor fl = Floor.getFloor(block, null); if(fl==null) { event.setLine(0, Conf.ERROR); Conf.err("onSignChange:ElevSign:NewElev", "Floor not found!"); return; }
				String eID = Conf.locToString(new Location(fl.world, fl.xMin, 0, fl.zMin));
				elev = new Elevator(fl, null, null); fl.elev = elev; ind = -1;
				Conf.elevators.put(eID, elev);
			}
			
			//Validate Sign Placement:
			boolean cErr = false; for(int i=0; i<sList.length; i++) {
				if(i > 0 && (sList.get(i).getY() - sList.get(i-1).getY()) < 3) { Conf.setLine(sList.get(i),
				0, Conf.ERROR); if(sList.get(i).getLocation().equals(block.getLocation())) cErr = true; sList.remove(i); i--; } //Signs too close!
			}
			
			//Update Elevator Data:
			if(ind == -1) { elev.sGroups = new ChuList<ChuList<Block>>(sList); } else elev.sGroups.set(ind, sList);
			
			//Special Modes:
			if(event.getLine(2).equalsIgnoreCase("[nodoor]")) { //Enable NoDoor Mode:
				elev.noDoor = true; Block first = elev.sGroups.get(0).get(0);
				if(first.equals(event.getBlock())) event.setLine(2, Conf.NODOOR);
				else { Conf.setLine(first, 2, Conf.NODOOR); event.setLine(2, ""); }
			} else event.setLine(2, "");
			
			//Update Sign Level Numbers for Non-Custom-Named Signs:
			for(int k=0,m=elev.sGroups.length; k<m; k++) for(int f=0,d=elev.sGroups.get(k).length; f<d; f++) {
				Block sign = elev.sGroups.get(k).get(f); if(sign.getLocation().equals(block.getLocation())) { if(event.getLine(3).length()==0) event.setLine(3, "Level "+(f+1)); }
				else if(Conf.lines(sign)[3].matches("^Level [0-9]+$")) Conf.setLine(sign, 3, "Level "+(f+1));
				else if(sign.getY() == block.getY()) { Conf.setSign(sign, event.getLines()); Conf.setLine(sign, 0, Conf.TITLE); }
			}
			
			//Set Elevator Doors, Blocks, Call Signs, and Restore Gravity:
			elev.floor.removeFallingBlocks(); elev.resetElevator(); elev.setEntities(true); elev.doorTimer(elev.sGroups.get(0).get(0).getY());
			elev.csGroups = elev.rebuildCallSignList(); elev.updateCallSigns(elev.getLevel()); for(int i=0,l=sList.length; i<l; i++)
			{ elev.setDoors(sList.get(i).getY(), i==0); elev.floor.addFloor(sList.get(i).getY()-2, false, true); }
			
			if(cErr) event.setLine(0, Conf.ERROR); else event.setLine(0, Conf.TITLE); //Display ERROR line if error.
			setTimeout(() -> { Conf.saveConfig(); }, 200); //Save Changes To Config.
		}
	}}}
	
	//------------------- Elevator Destroy Sign Events -------------------
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlcokBreak(BlockBreakEvent event) { synchronized(Conf.API_SYNC) { if(!Conf.DISABLED && event.getBlock().getType() == Material.WALL_SIGN) {
		if(Conf.CLTMR != null) { event.setCancelled(true); Conf.err("onBlcokBreak", "Door timer is running!"); return; }
		if(Conf.CALL.equals(Conf.lines(event.getBlock())[0]) && Conf.hasPerm(event.getPlayer(), PERM_CREATE)) { //Call Sign:
			Block block = event.getBlock();
			
			//Is sign in elevator?
			CSData ret = Elevator.fromCallSign(block); if(ret==null) { Conf.err("onBlcokBreak:CallSign", "No elevator found!"); return; }
			Elevator elev = ret.elev; int csNum = ret.index;
			if(elev.floor.moving) { event.setCancelled(true); Conf.err("onBlcokBreak:CallSign", "Elevator is moving!"); return; }
			
			//Build sign list:
			elev.csGroups = elev.rebuildCallSignList(block.getLocation());
			if(elev.csGroups.get(csNum).remove(elev.csGroups.get(csNum).indexOf(block)) == null) { Conf.err("onBlcokBreak:CallSign", "Call sign not found in csList!"); return; }
			
			//Update call signs:
			elev.updateCallSigns(elev.getLevel());
			
		} else if(Conf.TITLE.equals(Conf.lines(event.getBlock())[0]) && Conf.hasPerm(event.getPlayer(), PERM_CREATE)) { //Elevator Sign:
			Block block = event.getBlock();
			
			//Is sign in elevator?
			Elevator elev = Elevator.fromElevSign(block); if(elev==null) { Conf.err("onBlcokBreak:ElevSign", "No elevator found!"); return; }
			if(elev.floor.moving) { event.setCancelled(true); Conf.err("onBlcokBreak:ElevSign", "Elevator is moving!"); return; }
			
			//Build sign list:
			ChuList<Block> sList = Elevator.rebuildSignList(block.getLocation()); elev.resetElevator();
			
			//Find sGroups index:
			int ind = -1; for(int i=0,l=elev.sGroups.length; i<l; i++) if(block.getX() == elev.sGroups
			.get(i).get(0).getX() && block.getZ() == elev.sGroups.get(i).get(0).getZ()) { ind = i; break; }
			if(ind == -1) { Conf.err("onBlcokBreak:ElevSign", "Cannot determine column index."); return; }
			
			if(elev.sGroups.length > 1) { //Delete Whole Columns:
				for(int i=0,l=sList.length; i<l; i++) sList.get(i).setType(Conf.AIR);
				elev.sGroups.remove(ind); //Remove column from elevator.
			} else { //Only One Column Left:
				int subInd = sList.indexOf(block); if(subInd == -1) { Conf.err("onBlcokBreak:ElevSign:LastColumn", "Cannot determine subList index."); return; }
				for(int i=0,l=sList.length; i<l; i++) if(i != subInd) elev.floor.addFloor(sList.get(i).getY()-2, false, true); //Add floors.
				if(elev.csGroups.get(subInd) != null) for(int h=0,d=elev.csGroups.get
				(subInd).length; h<d; h++) elev.csGroups.get(subInd).get(h).setType(Conf.AIR); //Delete call signs on level.
				Block pBl = Conf.getBlockBelowPlayer(event.getPlayer()); if(pBl.getType() == Conf.AIR) pBl.setType(elev.floor.fType); //Add block below player.
				if(sList.length <= 1) elev.selfDestruct(); //Delete elevator instance. This meeting... never happened.
				else { sList.remove(subInd); elev.sGroups.set(ind, sList); } //Remove sign from elevator.
			}
			
			setTimeout(() -> { Conf.saveConfig(); }, 200); //Save Changes To Config.
		}
	}}}
	
	//------------------- Elevator Block-Clicking Events -------------------
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event) { synchronized(Conf.API_SYNC) { if(!Conf.DISABLED) { ConfData ref = new ConfData();
		Action act = event.getAction(); if(act == Action.RIGHT_CLICK_BLOCK && !event.isCancelled() && event.getClickedBlock().getType() == Material.WALL_SIGN) {
		if(Conf.isElevSign(event.getClickedBlock(), ref, event.getPlayer(), PERM_USE) && !((Elevator)ref.data).floor.moving) { //Select Floor:
			Elevator elev = ((Elevator)ref.data); ChuList<Block> dsList = elev.sGroups.get(0);
			
			//Get Selected Floor:
			String selName = Conf.lines(dsList.get(0))[1]; int selNum = 0; if(selName.length()!=0)
			selName = selName.substring(Conf.L_ST.length()-(selName.charAt(Conf.L_ST.length()-1)
			!=Conf.L_ST.charAt(Conf.L_ST.length()-1)?2:0),selName.length()-Conf.L_END.length());
			
			//Get List of Floor Names:
			ChuList<String> flNames = new ChuList<String>();
			for(int i=0,l=dsList.length; i<l; i++) { flNames.set(i, Conf.lines
			(dsList.get(i))[3]); if(selName.equals(flNames.get(i))) selNum = i; }
			
			//Update Floor Number:
			if(event.getPlayer().isSneaking()) { selNum--; if(selNum < 0) selNum = flNames.length-1; } else
			{ selNum++; if(selNum >= flNames.length) selNum = 0; } elev.updateFloorName(flNames.get(selNum));
			
			event.setCancelled(true);
		} else if(Conf.isCallSign(event.getClickedBlock(), ref, event.getPlayer(), PERM_USE) && !((CSData)ref.data).elev.floor.moving) { //Call Sign Click:
			Elevator elev = ((CSData)ref.data).elev; int fLevel = elev.getLevel(), sLevel = event.getClickedBlock().getY()-2;
			
			//Call Elevator to Floor:
			if(fLevel != sLevel) {
				event.getPlayer().sendMessage(Conf.MSG_CALL);
				int speed = Conf.BL_SPEED.get(Conf.BLOCKS.indexOf(elev.floor.fType.toString()));
				elev.gotoFloor(fLevel, sLevel, ((CSData)ref.data).index, speed);
			} else elev.doorTimer(sLevel+2); //Re-open doors if already on level.
			
			event.setCancelled(true);
		}} else if(act == Action.RIGHT_CLICK_BLOCK && Conf.isElevPlayer(event.getPlayer(), ref, PERM_USE) && !((Elevator)ref.data).floor.moving) { //Go To Floor:
			Elevator elev = ((Elevator)ref.data); ChuList<Block> dsList = elev.sGroups.get(0);
			
			//Get Current And Selected Floors:
			int fLevel = elev.getLevel(), selNum = 0; String selName = Conf.lines(dsList.get(0))[1];
			if(selName.length()!=0) selName = selName.substring(Conf.L_ST.length()-(selName.charAt(Conf.L_ST
			.length()-1)!=Conf.L_ST.charAt(Conf.L_ST.length()-1)?2:0),selName.length()-Conf.L_END.length());
			
			//Get Floor Name:
			for(int i=0,l=dsList.length; i<l; i++) if(selName.equals(Conf.lines(dsList.get(i))[3])) selNum = i;
			int sLevel = dsList.get(selNum).getY()-2;
			
			//Move Elevator:
			if(fLevel != sLevel) {
				event.getPlayer().sendMessage(Conf.MSG_GOTO_ST+selName+Conf.MSG_GOTO_END);
				int speed = Conf.BL_SPEED.get(Conf.BLOCKS.indexOf(elev.floor.fType.toString()));
				elev.gotoFloor(fLevel, sLevel, selNum, speed);
			} else elev.doorTimer(sLevel+2); //Re-open doors if already on level.
			
			event.setCancelled(true);
		} else if(act == Action.RIGHT_CLICK_BLOCK && Conf.isWoodDoor(event.getClickedBlock())) {
			Elevator elev = Elevator.fromDoor(event.getClickedBlock().getLocation());
			if(elev != null) event.setCancelled(true); //Cancel right-clicks on wooden elevator doors.
		}
	}}}
}