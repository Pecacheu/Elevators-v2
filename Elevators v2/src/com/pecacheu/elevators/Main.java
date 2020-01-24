//This work is licensed under a GNU General Public License. Visit http://gnu.org/licenses/gpl-3.0-standalone.html for details.
//Pecacheu's Elevator Plugin v2. Copyright (©) 2016, Pecacheu (Bryce Peterson, bbryce.com).

//Pecacheu's Elevator Plugin v2.0! Recreated from scratch!

package com.pecacheu.elevators;

import java.util.Iterator;

import org.bukkit.Bukkit;

//TODO Always Check TODOs Regularly!
//TODO NODOOR setting for individual levels?

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.material.MaterialData;
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
		Bukkit.getConsoleSender().sendMessage(Conf.MSG_DBG+"§dElevators Plugin Loaded!");
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
	}
	public BukkitTask setInterval(BukkitRunnable function, long millis) { long t=millis/50; return function.runTaskTimer(this, t, t); }

	//------------------- Elevator Create Sign Events -------------------

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSignChange(SignChangeEvent event) { synchronized(Conf.API_SYNC) { if(!Conf.DISABLED && event.getBlock().getType() == Material.OAK_WALL_SIGN) {
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
						if(elev.floor.moving) {
							event.setCancelled(true); block.setType(Conf.AIR); Conf.err("onSignChange:ElevSign", "Elevator is moving!"); return;
						}
						int column = -1; for(int i=0,l=elev.sGroups.length; i<l; i++) { //Check if X and Z match existing sList:
							Block dSign = elev.sGroups.get(i).get(0);
							if(dSign.getX() == block.getX() && dSign.getZ() == block.getZ()) { column = i; break; }
						}
						if(column != -1) { //Existing Column:
							ind = column; for(int k=0,m=elev.sGroups.length; k<m; k++) if(k != ind) { //Iterate through sGroups:
									ChuList<Block> oList = elev.sGroups.get(k); int sX = oList.get(0).getX(), sZ = oList.get(0).getZ();
									byte sData = sList.get(0).getData(); World w = elev.floor.world;
									for(int i=0,l=oList.length; i<l; i++) oList.get(i).setType(Conf.AIR); //Delete old signs in other columns.
									oList = new ChuList<Block>(); for(int i=0,l=sList.length; i<l; i++) { //Rebuild to match new column.
										Block bl = w.getBlockAt(sX, sList.get(i).getY(), sZ);
										Conf.addSignBlock(bl,sData); bl.setType(Material.OAK_WALL_SIGN);
										MaterialData bd = bl.getState().getData();
										bd.setData(sData); Conf.setSign(bl, Conf.lines(sList.get(i))); oList.push(bl);
									}
									elev.sGroups.set(k, oList);
								}
						} else { //New Column:
							ChuList<Block> sRef = elev.sGroups.get(0);
							int sX = sList.get(0).getX(), sZ = sList.get(0).getZ();
							byte sData = sList.get(0).getData();
							World w = elev.floor.world;
							for (int i = 0, l = sList.length; i < l; i++)
								sList.get(i).setType(Conf.AIR); //Delete old signs in column.
							sList = new ChuList<Block>();
							for (int i = 0, l = sRef.length; i < l; i++) { //Rebuild to match other columns.
								Block bl = w.getBlockAt(sX, sRef.get(i).getY(), sZ);
								BlockState blockState = bl.getState();
								MaterialData blockData = blockState.getData();
								Conf.addSignBlock(bl, sData);
								bl.setType(Material.OAK_WALL_SIGN);
								blockData.setData(sData);
								Conf.setSign(bl, Conf.lines(sRef.get(i)));
								sList.push(bl);
							}
							elev.sGroups.push(sList);
							setTimeout(() -> {
								Conf.saveConfig();
							}, 200);
							return; //Add new signs to elevator and save.
						}
					} else { //New elevator:
						Floor fl = Floor.getFloor(block, null);
						if (fl == null) {
							event.setLine(0, Conf.ERROR);
							Conf.err("onSignChange:ElevSign:NewElev", "Floor not found!");
							return;
						}
						String eID = Conf.locToString(new Location(fl.world, fl.xMin, 0, fl.zMin));
						elev = new Elevator(fl, null, null);
						fl.elev = elev;
						ind = -1;
						Conf.elevators.put(eID, elev);
						event.getPlayer().sendMessage("�eElevator created.");
					}

					//Validate Sign Placement:
					boolean cErr = false;
					for (int i = 0; i < sList.length; i++) {
						if (i > 0 && (sList.get(i).getY() - sList.get(i - 1).getY()) < 3) {
							Conf.setLine(sList.get(i),
									0, Conf.ERROR);
							if (sList.get(i).getLocation().equals(block.getLocation())) cErr = true;
							sList.remove(i);
							i--;
						} //Signs too close!
					}

					//Update Elevator Data:
					if (ind == -1) {
						elev.sGroups = new ChuList<ChuList<Block>>(sList);
					} else elev.sGroups.set(ind, sList);

					//Line 2 Special Modes:
					if (elev.noDoor) {
						elev.sGroups.get(0).forEach((s) -> {
							Conf.setLine(s, 2, "");
						});
					}
					//Set modes:
					String sLine = event.getLine(2);
					if (elev.noDoor || sLine.equalsIgnoreCase("[nodoor]")) { //Enable NoDoor Mode:
						elev.noDoor = true;
						Block first = elev.sGroups.get(0).get(0);
						if (first.equals(event.getBlock())) event.setLine(2, Conf.NODOOR);
						else {
							Conf.setLine(first, 2, Conf.NODOOR);
							event.setLine(2, "");
						}
					} else event.setLine(2, "");

					//Update Sign Level Numbers for Non-Custom-Named Signs:
					for (int k = 0, m = elev.sGroups.length; k < m; k++)
						for (int f = 0, d = elev.sGroups.get(k).length; f < d; f++) {
							Block sign = elev.sGroups.get(k).get(f);
							if (sign.getLocation().equals(block.getLocation())) {
								if (event.getLine(3).length() == 0) event.setLine(3, "Level " + (f + 1));
							} else if (Conf.lines(sign)[3].matches("^Level [0-9]+$"))
								Conf.setLine(sign, 3, "Level " + (f + 1));
							else if (sign.getY() == block.getY()) {
								Conf.setSign(sign, event.getLines());
								Conf.setLine(sign, 0, Conf.TITLE);
							}
						}

					//Set Elevator Doors, Blocks, Call Signs, and Restore Gravity:
					elev.floor.removeFallingBlocks();
					elev.resetElevator();
					elev.setEntities(true);
					elev.doorTimer(elev.sGroups.get(0).get(0).getY());
					elev.csGroups = elev.rebuildCallSignList();
					elev.updateCallSigns(elev.getLevel());
					for (int i = 0, l = sList.length; i < l; i++) {
						elev.setDoors(sList.get(i).getY(), i == 0);
						elev.floor.addFloor(sList.get(i).getY() - 2, false, true);
					}

					if (cErr) event.setLine(0, Conf.ERROR);
					else event.setLine(0, Conf.TITLE); //Display ERROR line if error.
					setTimeout(() -> {
						Conf.saveConfig();
					}, 200); //Save Changes To Config.
				}
			}
		}
	}

	//------------------- Elevator Destroy Sign/Block Events -------------------

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		synchronized (Conf.API_SYNC) {
			if (!Conf.DISABLED) {
				Block b = event.getBlock();
				if (b.getType() == Material.OAK_WALL_SIGN) {
					if (Conf.CALL.equals(Conf.lines(b)[0]) || Conf.TITLE.equals(Conf.lines(b)[0]))
						event.setCancelled(true); //Prevent sign break.
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		synchronized (Conf.API_SYNC) {
			if (!Conf.DISABLED) {
				Block b = event.getBlock();
				if (b.getType() == Material.OAK_WALL_SIGN) {
					if (Conf.CALL.equals(Conf.lines(b)[0])) onDestroyCallSign(event, event.getPlayer(), b); //Call Sign
					else if (Conf.TITLE.equals(Conf.lines(b)[0]))
						onDestroyElevSign(event, event.getPlayer(), b); //Elevator Sign
				} else if (Elevator.fromElevBlock(b) != null) { //Block Door:
					event.setCancelled(true);
					b.setType(Conf.AIR); //Prevent door block grief.
				}
			}
		}
	}

	private void onDestroyCallSign(Cancellable e, Player p, Block b) {
		if (Conf.CLTMR != null) {
			e.setCancelled(true);
			Conf.err("onBlockBreak:CallSign", "Door timer is running!");
			return;
		}
		if (!Conf.hasPerm(p, PERM_CREATE)) {
			Conf.err("onBlockBreak:CallSign", "No permission!");
			e.setCancelled(true);
			return;
		}

		//Is sign in elevator?
		CSData ret = Elevator.fromCallSign(b);
		if (ret == null) {
			Conf.err("onBlockBreak:CallSign", "No elevator found!");
			return;
		}
		Elevator elev = ret.elev;
		int csNum = ret.index;
		if (elev.floor.moving) {
			e.setCancelled(true);
			Conf.err("onBlockBreak:CallSign", "Elevator is moving!");
			return;
		}

		//Build sign list:
		elev.csGroups = elev.rebuildCallSignList(b.getLocation());
		if (elev.csGroups.get(csNum).remove(elev.csGroups.get(csNum).indexOf(b)) == null) {
			Conf.err("onBlockBreak:CallSign", "Call sign not found in csList!");
			return;
		}

		//Update call signs:
		elev.updateCallSigns(elev.getLevel());
	}

	private void onDestroyElevSign(Cancellable e, Player p, Block b) {
		if (Conf.CLTMR != null) {
			e.setCancelled(true);
			Conf.err("onBlockBreak:ElevSign", "Door timer is running!");
			return;
		}
		if (!Conf.hasPerm(p, PERM_CREATE)) {
			e.setCancelled(true);
			Conf.err("onBlockBreak:ElevSign", "No permission!");
			return;
		}

		//Is sign in elevator?
		Elevator elev = Elevator.fromElevSign(b);
		if (elev == null) {
			Conf.err("onBlockBreak:ElevSign", "No elevator found!");
			return;
		}
		if (elev.floor.moving) {
			e.setCancelled(true);
			Conf.err("onBlockBreak:ElevSign", "Elevator is moving!");
			return;
		}

		//Build sign list:
		ChuList<Block> sList = Elevator.rebuildSignList(b.getLocation());
		elev.resetElevator();

		//Find sGroups index:
		int ind = -1;
		for (int i = 0, l = elev.sGroups.length; i < l; i++)
			if (b.getX() == elev.sGroups
					.get(i).get(0).getX() && b.getZ() == elev.sGroups.get(i).get(0).getZ()) {
				ind = i;
				break;
			}
		if (ind == -1) {
			Conf.err("onBlockBreak:ElevSign", "Cannot determine column index.");
			return;
		}

		if (elev.sGroups.length > 1) { //Delete Whole Columns:
			for (int i = 0, l = sList.length; i < l; i++) sList.get(i).setType(Conf.AIR);
			elev.sGroups.remove(ind); //Remove column from elevator.
		} else { //Only One Column Left:
			int subInd = sList.indexOf(b);
			if (subInd == -1) {
				Conf.err("onBlockBreak:ElevSign:LastColumn", "Cannot determine subList index.");
				return;
			}
			Floor fl = elev.floor;
			for (int i = 0, l = sList.length; i < l; i++)
				if (i != subInd) fl.addFloor(sList.get(i).getY() - 2, false, true); //Add floors.
			ChuList<Block> cs = elev.csGroups.get(subInd);
			if (cs != null)
				for (int h = 0, d = cs.length; h < d; h++) cs.get(h).setType(Conf.AIR); //Delete call signs on level.
			if (sList.length <= 1) {
				elev.selfDestruct();
				p.sendMessage("�eElevator destroyed.");
			} //Delete elevator instance. This meeting... never happened.
			else {
				sList.remove(subInd);
				elev.sGroups.set(ind, sList);
			} //Remove sign from elevator.
			int y = b.getY() - 2;
			for (int x = fl.xMin; x <= fl.xMax; x++)
				for (int z = fl.zMin; z <= fl.zMax; z++) fl.world.getBlockAt(x, y, z).setType(Conf.AIR); //Remove floor.
			Block pBl = Conf.getBlockBelowPlayer(p);
			if (pBl.getType() == Conf.AIR) pBl.setType(Material.DIRT); //Add block below player.
			elev.remBlockDoor(b.getY()); //Delete block door.
		}

		setTimeout(() -> {
			Conf.saveConfig();
		}, 200); //Save Changes To Config.
	}

	//------------------- Piston Interact Events -------------------

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		synchronized (Conf.API_SYNC) {
			if (!Conf.DISABLED) {
				Iterator<Block> i = event.getBlocks().iterator();
				while (i.hasNext()) {
					if (Elevator.fromElevBlock(i.next()) != null) {
						event.setCancelled(true);
						Conf.err("onPistonExtend", "Prevent block destory!");
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		synchronized (Conf.API_SYNC) {
			if (!Conf.DISABLED) {
				Iterator<Block> i = event.getBlocks().iterator();
				while (i.hasNext()) {
					if (Elevator.fromElevBlock(i.next()) != null) {
						event.setCancelled(true);
						Conf.err("onPistonRetract", "Prevent block destory!");
					}
				}
			}
		}
	}

	//------------------- Elevator Block-Clicking Events -------------------

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event) {
		synchronized (Conf.API_SYNC) {
			if (!Conf.DISABLED) {
				ConfData ref = new ConfData();
				Action act = event.getAction();
				if (act == Action.RIGHT_CLICK_BLOCK && !event.isCancelled() && event.getClickedBlock().getType() == Material.OAK_WALL_SIGN) {
					if (Conf.isElevSign(event.getClickedBlock(), ref, event.getPlayer(), PERM_USE) && !((Elevator) ref.data).floor.moving) { //Select Floor:
						Elevator elev = ((Elevator) ref.data);
						ChuList<Block> dsList = elev.sGroups.get(0);

						//Get Selected Floor:
						String selName = Conf.lines(dsList.get(0))[1];
						int selNum = 0;
						if (selName.length() != 0)
							selName = selName.substring(Conf.L_ST.length() - (selName.charAt(Conf.L_ST.length() - 1)
									!= Conf.L_ST.charAt(Conf.L_ST.length() - 1) ? 2 : 0), selName.length() - Conf.L_END.length());

						//Get List of Floor Names:
						ChuList<String> flNames = new ChuList<String>();
						for (int i = 0, l = dsList.length; i < l; i++) {
							flNames.set(i, Conf.lines
									(dsList.get(i))[3]);
							if (selName.equals(flNames.get(i))) selNum = i;
						}

						//Update Floor Number:
						if (event.getPlayer().isSneaking()) {
							selNum--;
							if (selNum < 0) selNum = flNames.length - 1;
						} else {
							selNum++;
							if (selNum >= flNames.length) selNum = 0;
						}
						elev.updateFloorName(flNames.get(selNum));

						event.setCancelled(true);
					} else if (Conf.isCallSign(event.getClickedBlock(), ref, event.getPlayer(), PERM_USE) && !((CSData) ref.data).elev.floor.moving) { //Call Sign Click:
						Elevator elev = ((CSData) ref.data).elev;
						int fLevel = elev.getLevel(), sLevel = event.getClickedBlock().getY() - 2;

						//Call Elevator to Floor:
						if (fLevel != sLevel) {
							event.getPlayer().sendMessage(Conf.MSG_CALL);
							int speed = Conf.BL_SPEED.get(Conf.BLOCKS.indexOf(elev.floor.fType.toString()));
							elev.gotoFloor(fLevel, sLevel, ((CSData) ref.data).index, speed);
						} else elev.doorTimer(sLevel + 2); //Re-open doors if already on level.

						event.setCancelled(true);
					}
				} else if ((act == Action.RIGHT_CLICK_BLOCK || act == Action.LEFT_CLICK_AIR) && Conf.isElevPlayer(event.getPlayer(), ref, PERM_USE) //Go To Floor:
						&& (event.getItem() == null || event.getPlayer().isSneaking()) && !((Elevator) ref.data).floor.moving) {
					Elevator elev = ((Elevator) ref.data);
					ChuList<Block> dsList = elev.sGroups.get(0);

					//Get Current And Selected Floors:
					int fLevel = elev.getLevel(), selNum = 0;
					String selName = Conf.lines(dsList.get(0))[1];
					if (selName.length() != 0)
						selName = selName.substring(Conf.L_ST.length() - (selName.charAt(Conf.L_ST
								.length() - 1) != Conf.L_ST.charAt(Conf.L_ST.length() - 1) ? 2 : 0), selName.length() - Conf.L_END.length());

					//Get Floor Name:
					for (int i = 0, l = dsList.length; i < l; i++)
						if (selName.equals(Conf.lines(dsList.get(i))[3])) selNum = i;
					int sLevel = dsList.get(selNum).getY() - 2;

					//Reset Passenger Gravity:
					elev.setEntities(true, fLevel, false);

					//Move Elevator:
					if (act != Action.LEFT_CLICK_AIR) {
						if (fLevel != sLevel) {
							event.getPlayer().sendMessage(Conf.MSG_GOTO_ST + selName + Conf.MSG_GOTO_END);
							int speed = Conf.BL_SPEED.get(Conf.BLOCKS.indexOf(elev.floor.fType.toString()));
							elev.gotoFloor(fLevel, sLevel, selNum, speed);
						} else elev.doorTimer(sLevel + 2); //Re-open doors if already on level.
					}

					event.setCancelled(true);
				} else if (act == Action.RIGHT_CLICK_BLOCK && Conf.isWoodDoor(event.getClickedBlock())) {
					Elevator elev = Elevator.fromDoor(event.getClickedBlock().getLocation());
					if (elev != null) event.setCancelled(true); //Cancel right-clicks on wooden elevator doors.
				}
			}
		}
	}
}