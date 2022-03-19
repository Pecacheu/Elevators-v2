# Elevators-v2
### Because who said mineshafts were the only type of shaft in Minecraft?

Many buildings on servers need a quick way of getting from floor to floor. You can use a stairwell, but those are slow and lame! You can use teleport pads with Command Blocks, but that's just not as elegant-looking or cool as an elevator... Plus there's the issues with giving your builders access to Command Blocks.

I used to use the Lift plugin, but it never quite worked right, is no longer being updated, and has finally broken as of Bukkit/Spigot 1.10. I've tried all the alternative plugins out there for floor-to-floor transport, but finally... I decided to solve the problem myself! Introducing the Elevators plugin. Version 2 now given a complete rewrite in Java instead of ScriptCraftJS.

Find the plugin on [BukkitDev](https://dev.bukkit.org/projects/elevators-v2), or see [GitHub](https://github.com/Pecacheu/Elevators-v2) for the latest updates.

## Summary
This plugin is simple and easy to use, and requires no configuration, similar to Lift, but unlike Lift, it provides a more realistic Elevator experience, with an actual elevator car, doors that open and close, and call buttons to call the elevator to your floor.

Plus, there's an API, which I taking advantage of in another plugin, **[LaunchPadMC](http://dev.bukkit.org/projects/launchpad-mc)**. (It lets you control your server's elevators with a physical Novation Launchpad grid-controller!)

## Reload Behavior
The only info the plugin stores is the X and Z location of each elevator, under it's unique eID. All signs, floor size and current level, and other data is recalculated on plugin reload. Invalid elevators are automatically deleted from the config, and elevators not in the config do not work (which prevents those without the permission node from creating elevators).

## Commands
- `/elev list` List all known elevators.
- `/elev reload` Reload elevators from config.
- `/elev reset` Rest the last-used elevator to a known-good state.

## Permissions
- **elevators.use** Change levels, operate elevators. Default: All Players.
- **elevators.create** Create and destroy elevators and call signs.
- **elevators.reload** Reload plugin (use `/elev`), receive debug info.

## How To Use
Place signs with `[elevator]` on line 1 on each floor. The sign will error (showing [???]) if the floor is too big, too close to another floor, or an invalid type. *(Note: You can change floor types and associated speeds in the config.)*

Use line 4 to add a custom floor name. If not specified, the default is in the format `Level [n]`. If the floor name matches this format, numbering will be automatically updated whenever the elevator is modified, but custom names will be left untouched.

If you put `[nodoor]` on line 3, the block-based door feature will be disabled. Nearby doors and gates will be opened/closed and call signs will activate redstone either way.

## Call Signs
Create 'call buttons' to call your elevator by placing a sign with `[call]` on line 1 around the perimeter of the elevator. They must be within 1 to 3 blocks of the elevator shaft, and can be placed up to 1 block above or 3 blocks below the a floor.

This is useful for placing hidden call signs that activate redstone. Any Redstone Lever next to a call sign will be activated when the doors open, allowing use of piston doors and more!

### User-made Tutorial:
https://www.youtube.com/watch?v=9nzRgzJEpJ0

## My Other Stuff
[MultiWorldPets](https://github.com/Pecacheu/MultiWorldPets) *(Take your pets with you everywhere!)*

[RawColors Resource Pack](https://planetminecraft.com/texture_pack/raw-colors-15-low-contrast-complete-resource-pack) *(Low-Contrast Colors for easy-on-the-eyes play)*

[ForestFire Server](https://forestfire.net) *(Check out our MC server, we have custom plugins!)*

[TabPlus Extension for Chrome](http://chrome.google.com/webstore/detail/tabplus/hfcdmjginkilbcfeffkkggemafdjflhp) (Save the headache and manage your tabs)