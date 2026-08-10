# CBMall

**CBMall** is a Paper plugin for Minecraft **1.21–26.2** that provides protected, claimable mall plots with trusted members, visual selection tools, inactive shop resets, and an item recovery system.

## Features

* Claimable and protected mall plots
* Trusted plot members
* Visual plot border display
* Admin selection wand
* Automatic plot ID assignment
* Manual and automatic shop resets
* Item recovery through `/mallclaim`
* Admin plot assignment and management

## Requirements

* **Java 21**
* **Paper 1.21–26.2**
* **Maven**

## Command Summary

| Command                               | Description                            |
| ------------------------------------- | -------------------------------------- |
| `/mall`                               | Displays player mall commands          |
| `/mall claim <plotid>`                | Claims a mall plot                     |
| `/mall addmember <username>`          | Adds a trusted plot member             |
| `/mall removemember <username>`       | Removes a trusted plot member          |
| `/mall members`                       | Lists trusted plot members             |
| `/mall viewborder`                    | Displays the plot boundary             |
| `/mall unlock`                        | Allows a plot container to be unlocked |
| `/mallclaim`                          | Opens the item recovery interface      |
| `/malladmin wand`                     | Gives the plot selection wand          |
| `/malladmin createshop`               | Creates a plot from the selected area  |
| `/malladmin resetshop <plotid>`       | Resets a plot and returns its items    |
| `/malladmin removeplot <plotid>`      | Removes a plot                         |
| `/malladmin assign <plotid> <player>` | Assigns a plot to a player             |
| `/malladmin unassign <plotid>`        | Unassigns and resets a plot            |

