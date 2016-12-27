# Elevators-v2
### Adds Working Elevators to Minecraft!

Many buildings on my server need a quick way of getting from floor to floor. I've been using Lift so far, but it's no longer being updated and it's finally broken in Bukkit/Spigot 1.10. I've tried pretty much all the Elevator plugins in an attempt to replace it, but they just don't have the features I want, and many just don't work in the first place.

### Summary
This plugin is simple and easy to use, and requires no configuration, similar to Lift, but unlike Lift, it provides a more realistic Elevator experience, with an actual elevator car, doors that open and close, and call buttons to call the elevator to your floor.

Plus, there's an API, which I'm already taking advantage of in another plugin, **[LaunchPadMC](http://dev.bukkit.org/projects/launchpad-mc)**. (It lets you control your elevators with a physical device!)

### Reload Behavior
The only info the plugin stores is the X and Z location of each elevator, under it's unique eID. All elev signs, call signs, floor size and current level, and other data is recalculated on plugin reload. Invalid elevators are automatically deleted from the config, and elevators not in the config do not work (which prevents those without the permission node from creating elevators).

### Permissions
- **elevators.use** Change levels, operate elevators. Default: All Players.
- **elevators.create** Create and destroy elevators and call signs.
- **elevators.reload** Reload plugin (`/elev reload`), receive debugging info.

### Development
Anyway, this plugin took WEEKS to develop, but the initial development went pretty fast, as I prototyped the plugin in ScriptCraft first, but when I tried to port it to Java, I found that it was a bit harder than I thought! (You can find both the JS version and the port [here](https://github.com/Pecacheu/Elevators).) So with Elevators v2, I just started over, using similar code, but taking advantage of Java's more object-oriented nature. You can find the v2 source [here](https://github.com/Pecacheu/Elevators-v2). The license is GNU-GPL.

Find the plugin on [BukkitDev](http://dev.bukkit.org/projects/elevators-v2)!

Also, a video tutorial should be coming soon!

### How To Use
Place signs with [elevator] on line 1 on each floor. They will error (showing [???]) if the floor is too big or an invalid type. (You can change floor types and associated speeds in the config.)

Use line 4 to add a custom floor name. If not specified, the default is in the format `Level [n]`. If the floor name matches this format, and [n] is a valid number, it will be automatically updated whenever the elevator is modified. (In case a floor was added in-between floors, changing the floor numbering.)

If you put [nodoor] on line 3, the block-based door feature will be disabled, and call signs will turn on/off nearby levers with the door timer, allowing you to add piston doors to your elevator. Nearby iron/wooden doors will be opened/closed either way.

You can create call-button signs with [call] on line 1. They will error if they're less than 1 or more than 3 blocks away from the elevator.