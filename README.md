# CBMall

CBMall is a Paper 1.21-26.2 plugin providing protected, claimable mall plots, trusted members, visual selection tools, inactive shop resets, and an item recovery UI.

Build with Java 21 and Maven:

```bash
mvn package
```

Copy `target/cbmall-1.0.2.jar` into the server's `plugins` folder. See `/mall` and `/malladmin` in game for command help.

Command Usage:

`/mall` - Displays player mall commands
`/mall claim plotid` - Command for claiming a plot, plot ID is displayed upon walking into the plot
`/mall addmember username` - Add selected player to owned plot as a "member", this allows that user to make changes within the plot.
`/mall removemember username` - Remove player from plot
`/mall members` - View list of current plot members
`/mall viewborder` - ...
`/mall unlock` - Can be used to unlock a container within plot, player must also unlock the container via LWC's /unlock command.

`/mallclaim` - Collect items from a reset shop, these items can be from either manual admin resets, or expired shops.

Mall Admin Commands

`/malladmin wand` - Wand for area selection
`/malladmin createshop` - After selecting an area with the wand, use this command to create a shop within that selected area. It auto assigns an ID based on the current amount of shops.
`/malladmin resetshop plotid` - Resets specified plot, items are returned to player via /mallclaim
`/malladmin removeplot plotid` - Removes plot.
`/malladmin assign plotid player` - Manually assigns a plot to a player
`/malladmin unassign plotid` - Unassigns and resets said plot
