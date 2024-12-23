//Elevators v2, ©2020 Pecacheu. Licensed under GNU GPL 3.0

package net.forestfire.elevators;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.*;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class Conf {
//Global Variables:
public static TreeMap<String,Elevator> elevators = new TreeMap<>();
public static ChuList<ChuList<FallingBlock>> movingFloors=new ChuList<>();
public static BukkitTask CLTMR=null; public static Main plugin=null;
public static final Object API_SYNC=new Object();
private static BukkitTask SVTMR=null;

//Global Config Settings:
public static String TITLE, CALL, ERROR, L_ST, L_END, NODOOR, MSG_GOTO_ST, MSG_GOTO_END,
	MSG_CALL, MSG_NOT_FOUND, MSG_PERM, MSG_PERM_END, NOMV, M_ATLV, ATLV, C_UP, UP, C_DOWN, DOWN;
public static int RADIUS_MAX, MOVE_RES, DOOR_HOLD;
public static ChuList<String> BLOCKS;
public static ChuList<Integer> BL_SPEED;
public static Material DOOR_SET;
public static boolean DEBUG=false;

//Constants:
public static final String MSG_NEW_CONF="§e[Elevators] §bCould not load config. Creating new config file...",
	MSG_ERR_CONF="§e[Elevators] §cError while loading config!", MSG_DBG="§e[Elevators] §r",
	MSG_ERR_ST="§e[Elevators] §eError in §b", MSG_ERR_MID="§e: §c", MSG_DEL_ST="§e[Elevators] §b",
	MSG_DEL_END=" §esaved elevators were deleted because they are invalid!",
	CONFIG_PATH="plugins/Elevators/config.yml";
static final Material AIR = Material.AIR;

static MemoryConfiguration defaults = new MemoryConfiguration();
static void initDefaults(Main _plugin) {
	defaults.set("debug", false);
	defaults.set("title", "&1[&3Elevator&1]");
	defaults.set("call", "&1[&3Call&1]");
	defaults.set("error", "[&4???&r]");
	defaults.set("selStart", "&8> &5");
	defaults.set("selEnd", " &8<");
	defaults.set("noDoor", "&1[nodoor]");
	defaults.set("msgGotoStart", "&eTraveling to &a");
	defaults.set("msgGotoEnd", "&e.");
	defaults.set("msgCall", "&eCalling elevator.");
	defaults.set("msgNotFound", "&cElevator not found! Try recreating it.");
	defaults.set("msgPerm", "&cSorry, you need the &e");
	defaults.set("msgPermEnd", " &cpermission!");
	defaults.set("noMove",   "&4⦿  ⦿  ⦿  ⦿  ⦿  ⦿");
	defaults.set("mAtLevel", "&3⦿  ⦿  ⦿  ⦿  ⦿  ⦿");
	defaults.set("atLevel",  "&2⦿  ⦿  ⦿  ⦿  ⦿  ⦿");
	defaults.set("callUp",   "&3▲  ▲  ▲  ▲  ▲  ▲");
	defaults.set("up",       "&4△  △  △  △  △  △");
	defaults.set("callDown", "&3▼  ▼  ▼  ▼  ▼  ▼");
	defaults.set("down",     "&4▽  ▽  ▽  ▽  ▽  ▽");
	defaults.set("floorMaxRadius", 8);
	defaults.set("updateDelay", 50);
	defaults.set("doorHoldTime", 4000);
	defaults.set("doorBlock", "GLASS_PANE");
	plugin = _plugin;
}

//------------------- Config Save & Load Functions -------------------

static void saveConf(boolean f) {
	if(SVTMR != null) SVTMR.cancel();
	if(f) synchronized(API_SYNC) { doSaveConf(); }
	else SVTMR = plugin.setTimeout(Conf::doSaveConf, 200);
} static void saveConf() { saveConf(false); }

private static void doSaveConf() {
	SVTMR=null; File f=new File(CONFIG_PATH); String data;

	//If Not Found, Create New Config File:
	if(!f.exists()) data=newConf(f); else try { //Read Current Config File:
		StringBuilder d=new StringBuilder(); FileReader r=new FileReader(f); int p=0,l;
		while(p < 3000) { l=r.read(); if(l<0) break; d.append((char)l); p++; }
		r.close(); data=d.toString();
	} catch(IOException e) { err("saveConfig", "IOException while reading file!"); return; }

	if(data.isEmpty()) { err("saveConfig", "Save data string empty!"); return; }

	//Separate And Overwrite BLOCKS Section:
	int bPos=data.lastIndexOf("blockList:"),bEnd=0,nl=0;
	String bStr=data.substring(bPos);
	while(bEnd<600) {
		if(nl==1 && bStr.charAt(bEnd)!=' ') nl=2;
		if(nl==2) { if(bStr.charAt(bEnd)=='\n') break; } else nl=(bStr.charAt(bEnd)=='\n')?1:0;
		bEnd++;
	}
	bEnd+=bPos;

	YamlConfiguration bConf = new YamlConfiguration();
	ConfigurationSection bList = bConf.createSection("blockList");
	for(int i=0,l=BLOCKS.length; i<l; i++) bList.set(BLOCKS.get(i), BL_SPEED.get(i));
	data = data.substring(0,bPos)+bConf.saveToString()+data.substring(bEnd);

	//Separate And Overwrite Elevators Section:
	YamlConfiguration eConf=new YamlConfiguration();
	ConfigurationSection eList=eConf.createSection("elevators");
	for(String k: elevators.keySet()) eList.set(k, elevators.get(k).toSaveData()); //Gen Compressed Elev Data

	//Append New Data And Save File:
	data = data.substring(0, data.lastIndexOf("elevators:"))+eConf.saveToString();
	try {
		Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
		w.write(data); w.close(); dbg("Saved "+elevators.size()+" elevators!");
	} catch(IOException e) { err("saveConfig", "IOException while saving file!"); }
}

private static String newConf(File file) {
	Bukkit.getServer().getConsoleSender().sendMessage(MSG_NEW_CONF);
	try { java.nio.file.Files.createDirectories(file.toPath().getParent()); }
	catch(IOException e) {
		err("newConfig", "IOException while creating directories!");
		return "";
	}
	return unpackFile("config.yml", file);
}

static Object loadConf() { try {
	File f=new File(CONFIG_PATH); YamlConfiguration conf; boolean pf=f.exists();
	if(pf) conf=YamlConfiguration.loadConfiguration(f); else conf=new YamlConfiguration();
	conf.setDefaults(defaults); movingFloors=new ChuList<>(); CLTMR=null;

	//Load Global Settings:
	DEBUG=conf.getBoolean("debug");
	TITLE=conf.getString("title");
	CALL=conf.getString("call");
	ERROR=conf.getString("error");
	L_ST=conf.getString("selStart");
	L_END=conf.getString("selEnd");
	NODOOR=conf.getString("noDoor");

	MSG_GOTO_ST=c(conf.getString("msgGotoStart"));
	MSG_GOTO_END=c(conf.getString("msgGotoEnd"));
	MSG_CALL=c(conf.getString("msgCall"));
	MSG_NOT_FOUND=c(conf.getString("msgNotFound"));
	MSG_PERM=c(conf.getString("msgPerm"));
	MSG_PERM_END=c(conf.getString("msgPermEnd"));

	NOMV=StringEscapeUtils.unescapeJava(conf.getString("noMove"));
	M_ATLV=StringEscapeUtils.unescapeJava(conf.getString("mAtLevel"));
	ATLV=StringEscapeUtils.unescapeJava(conf.getString("atLevel"));
	C_UP=StringEscapeUtils.unescapeJava(conf.getString("callUp"));
	UP=StringEscapeUtils.unescapeJava(conf.getString("up"));
	C_DOWN=StringEscapeUtils.unescapeJava(conf.getString("callDown"));
	DOWN=StringEscapeUtils.unescapeJava(conf.getString("down"));

	RADIUS_MAX=conf.getInt("floorMaxRadius");
	MOVE_RES=conf.getInt("updateDelay");
	DOOR_HOLD=conf.getInt("doorHoldTime");
	DOOR_SET=getMat(conf.getString("doorBlock"));

	ConfigurationSection bList=conf.getConfigurationSection("blockList");
	BLOCKS=new ChuList<>(); BL_SPEED=new ChuList<>();
	if(bList!=null) for(String k: bList.getKeys(false)) { //Remove non-sold blocks:
		if(getMat(k).isSolid()) { BLOCKS.add(k); BL_SPEED.add(bList.getInt(k)); }
	}

	if(BLOCKS.length<1) { //Load Default Block Settings:
		BLOCKS=new ChuList<>("IRON_BLOCK", "GOLD_BLOCK", "EMERALD_BLOCK", "DIAMOND_BLOCK", "LEGACY_STAINED_GLASS", "GLASS");
		BL_SPEED=new ChuList<>(8, 10, 12, 15, 5, 4);
	}

	//Load Compressed Elevator Data:
	elevators.clear(); ConfigurationSection eList=conf.getConfigurationSection("elevators");
	int eCnt=0; if(eList!=null) for(String k: eList.getKeys(false)) {
		Elevator e=Elevator.fromSaveData(eList.getStringList(k));
		if(e!=null) elevators.put(k,e);
		else { eCnt++; err("loadConfig", "fromSaveData returned null at ID "+k); }
	}
	return pf?eCnt:"NOCONF";
} catch(Exception e) { err("loadConfig", e.getMessage()); return e.getMessage(); }}

static void doConfLoad(CommandSender s) {
	Object err=loadConf();
	if(err=="NOCONF") doSaveConf(); //Create New Config.
	else if(err instanceof Integer) { //Loaded Config Successfully.
		if((Integer)err>0) {
			String delMsg=MSG_DEL_ST + err + MSG_DEL_END;
			Bukkit.getServer().getConsoleSender().sendMessage(delMsg);
			if(s instanceof Player) s.sendMessage(delMsg);
		}
		doSaveConf();
	} else if(err!=null) { //Error While Loading Config.
		Bukkit.getServer().getConsoleSender().sendMessage(MSG_ERR_CONF + "\n" + err);
		if(s instanceof Player) s.sendMessage(MSG_ERR_CONF);
	}
	if(s!=null) s.sendMessage("§aElevators Plugin Reloaded!");
}

//-------------------  Useful Functions -------------------

private static Material getMat(String m) throws Exception {
	try { return Material.valueOf(m); }
	catch(IllegalArgumentException e) { throw new Exception("No such material "+m); }
}

static String locToString(Location l) {
	return l.getWorld().getName()+"-"+l.getBlockX()+"-"+l.getBlockZ();
}

//Open/close doors & gates:
static void setDoor(Block b, boolean on) {
	if(b.getBlockData() instanceof Openable) {
		Openable d = (Openable)b.getBlockData();
		if(d instanceof Door && ((Door)d).getHalf() != Bisected.Half.BOTTOM) return;
		d.setOpen(on); b.setBlockData(d); playDoorSound(b,on);
	}
}

//Play door sound effect:
private static void playDoorSound(Block b, boolean on) {
	Location l = b.getLocation(); Openable d = (Openable)b.getBlockData(); Sound s;
	if(d instanceof Gate) s = on?Sound.BLOCK_FENCE_GATE_OPEN:Sound.BLOCK_FENCE_GATE_CLOSE;
	else if(d instanceof TrapDoor) {
		if(b.getType() == Material.IRON_TRAPDOOR) s = on?Sound.BLOCK_IRON_TRAPDOOR_OPEN:Sound.BLOCK_IRON_TRAPDOOR_CLOSE;
		else s = on?Sound.BLOCK_WOODEN_TRAPDOOR_OPEN:Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
	} else if(b.getType() == Material.IRON_DOOR) s = on?Sound.BLOCK_IRON_DOOR_OPEN:Sound.BLOCK_IRON_DOOR_CLOSE;
	else s = on?Sound.BLOCK_WOODEN_DOOR_OPEN:Sound.BLOCK_WOODEN_DOOR_CLOSE;
	l.getWorld().playSound(l,s,1,1);
}

//Set state of levers
static void setPowered(Block b, boolean on) {
	if(b.getBlockData() instanceof Switch s) { s.setPowered(on); b.setBlockData(s); }
}

//Sign Read/Write
static void line(Block b, int i, String str) {
	Sign s=(Sign)b.getState();
	s.getSide(Side.FRONT).line(i,sc(str==null?"":str));
	s.update();
}
static String line(Block b, int i) {
	return cs(((Sign)b.getState()).getSide(Side.FRONT).line(i));
}
static String[] lines(Block b) {
	List<Component> c=((Sign)b.getState()).getSide(Side.FRONT).lines();
	return new String[]{cs(c.get(0)),cs(c.get(1)),cs(c.get(2)),cs(c.get(3))};
}
static void lines(Block b, String[] l) {
	Sign s=(Sign)b.getState(); SignSide ss=s.getSide(Side.FRONT);
	ss.line(0,sc(l[0])); ss.line(1,sc(l[1]));
	ss.line(2,sc(l[2])); ss.line(3,sc(l[3]));
	s.update();
}

//Get block sign is attached to
static Block getSignBlock(Block s) {
	World w=s.getWorld(); int x=s.getX(), y=s.getY(), z=s.getZ();
	BlockFace f=((WallSign)s.getBlockData()).getFacing();
	switch(f) {
		case NORTH: return w.getBlockAt(x,y,z+1); case SOUTH: return w.getBlockAt(x,y,z-1);
		case WEST: return w.getBlockAt(x+1,y,z); default: return w.getBlockAt(x-1,y,z);
	}
}

//Ensure there is a solid block behind the sign
static void addSignBlock(Block s) { setDoorBlock(Conf.getSignBlock(s),true); }
static void setDoorBlock(Block b, boolean on) {
	Material m=b.getType();
	if(on?!m.isSolid():m==Conf.DOOR_SET) b.setType(on?Conf.DOOR_SET:Conf.AIR);
	if(on && b.getBlockData() instanceof MultipleFacing f) { //Connect block faces
		Location l=b.getLocation();
		f.setFace(BlockFace.EAST, !l.clone().add(1,0,0).getBlock().isPassable());
		f.setFace(BlockFace.WEST, !l.clone().add(-1,0,0).getBlock().isPassable());
		f.setFace(BlockFace.NORTH, !l.clone().add(0,0,-1).getBlock().isPassable());
		f.setFace(BlockFace.SOUTH, !l.clone().add(0,0,1).getBlock().isPassable());
		b.setBlockData(f);
	}
}

//Determine if sign or call sign was clicked on:
static boolean isElevSign(Block b, ConfData ref, Player p) {
	if(!hasPerm(p,Main.PERM_USE,true)) return false; if(b.getBlockData() instanceof WallSign && TITLE.equals(line(b,0))) {
		ref.data=Elevator.fromElevSign(b); if(ref.data==null) p.sendMessage(MSG_NOT_FOUND); return (ref.data!=null);
	} return false;
}

static boolean isCallSign(Block b, ConfData ref, Player p) {
	if(!hasPerm(p,Main.PERM_USE,true)) return false; if(b.getBlockData() instanceof WallSign && CALL.equals(line(b,0))) {
		ref.data=Elevator.fromCallSign(b); if(ref.data==null) p.sendMessage(MSG_NOT_FOUND); return (ref.data!=null);
	} return false;
}

static boolean isElevPlayer(Player p, ConfData ref) {
	if(!hasPerm(p,Main.PERM_USE,false)) return false; ref.data=Elevator.fromEntity(p); return (ref.data!=null);
}

//Find first null element in a RaichuList:
static int findFirstEmpty(ChuList<ChuList<FallingBlock>> list) {
	int l=list.length; for(int i=0; i<l; i++) if(list.get(i)==null) return i; return l;
}

//-------------------  PecacheuLib Functions -------------------

static boolean hasPerm(Player p, String perm, boolean m) {
	boolean h=p.hasPermission(perm);
	if(m && !h) p.sendMessage(MSG_PERM+perm+MSG_PERM_END); return h;
}

static String unpackFile(String intPath, File dest) {
	InputStream st=plugin.getResource(intPath);
	String s; try {
		StringBuilder d=new StringBuilder(); int p=0,r;
		while(p < 3000) { r=st.read(); if(r<0) break; d.append((char)r); p++; }
		st.close(); s=d.toString();
		Writer file = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8));
		file.write(s); file.close();
	} catch (Exception e) { err("unpackFile", e.getMessage()); return ""; }
	return s;
}

static String cs(Component c) { return LegacyComponentSerializer.legacyAmpersand().serialize(c); }
static Component sc(String s) { return LegacyComponentSerializer.legacyAmpersand().deserialize(s); }
static String c(String s) { return LegacyComponentSerializer.legacySection().serialize(sc(s)); }
static void dbg(String str) {
	if(DEBUG) {
		String msg=MSG_DBG+str; Bukkit.getConsoleSender().sendMessage(msg);
		Collection<? extends Player> pl=Bukkit.getOnlinePlayers();
		for(Player p: pl) if(hasPerm(p,Main.PERM_RELOAD,false)) p.sendMessage(msg);
	}
}
static void err(String func, String cause) {
	if(DEBUG) {
		String msg=MSG_ERR_ST+func+MSG_ERR_MID+cause; Bukkit.getConsoleSender().sendMessage(msg);
		Collection<? extends Player> pl=Bukkit.getOnlinePlayers();
		for(Player p: pl) if(hasPerm(p,Main.PERM_RELOAD,false)) p.sendMessage(msg);
	}
}
}

class ConfData { public Object data=null; }