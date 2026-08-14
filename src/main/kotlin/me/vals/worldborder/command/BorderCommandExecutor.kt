package me.vals.worldborder.command

import me.vals.worldborder.WorldBorder
import me.vals.worldborder.border.BorderShape
import me.vals.worldborder.coordinate.ChunkCoordinate
import me.vals.worldborder.registry.BorderRegistry
import me.vals.worldborder.settings.PluginSettings
import me.vals.worldborder.task.WorldFillTask
import me.vals.worldborder.task.WorldTrimTask
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderCommandExecutor.kt
 * @created 14/8/2026
 */

class BorderCommandExecutor(private val plugin: WorldBorder) : CommandExecutor {

    private val cmdColor = ChatColor.AQUA.toString()
    private val reqColor = ChatColor.GREEN.toString()
    private val optColor = ChatColor.DARK_GREEN.toString()
    private val descColor = ChatColor.WHITE.toString()
    private val headColor = ChatColor.YELLOW.toString()
    private val errColor = ChatColor.RED.toString()

    private var currentFillTask: WorldFillTask? = null
    private var fillWorld = ""
    private var fillFrequency = 20
    private var fillPadding = ChunkCoordinate.chunkToBlock(13)
    private var fillForceLoad = false

    private var currentTrimTask: WorldTrimTask? = null
    private var trimWorld = ""
    private var trimFrequency = 5000
    private var trimPadding = ChunkCoordinate.chunkToBlock(13)

    private data class TaskFlags(val cancel: Boolean, val confirm: Boolean, val pause: Boolean, val frequency: String)

    override fun onCommand(sender: CommandSender, command: Command, label: String, rawArgs: Array<String>): Boolean {
        val player = sender as? Player
        val args = mergeQuotedWorldName(rawArgs)
        val cmd = "$cmdColor${if (player == null) "wb" else "/wb"}"
        val cmdWorld = "$cmdColor${if (player == null) "wb $reqColor<world>" else "/wb $optColor[world]"}$cmdColor"

        when {
            args.size >= 4 && args[1].equals("set", true) -> handleSetWithWorld(sender, player, args)
            args.size >= 2 && args[0].equals("set", true) -> handleSetCurrentWorld(sender, player, args)
            args.size == 6 && args[1].equals("setcorners", true) -> handleSetCornersWithWorld(sender, player, args)
            args.size == 5 && args[0].equals("setcorners", true) && player != null -> handleSetCornersCurrentWorld(sender, player, args)

            (args.size == 3 || args.size == 4) && args[1].equals("radius", true) -> handleRadiusWithWorld(sender, player, args)

            (args.size == 2 || args.size == 3) && args[0].equals("radius", true)
                    && player != null -> handleRadiusCurrentWorld(sender, player, args)

            args.size == 2 && args[1].equals("clear", true) -> handleClearWithWorld(sender, player, args)
            args.size == 1 && args[0].equals("clear", true) && player != null -> handleClearCurrentWorld(sender, player)
            args.size == 2 && args[0].equals("clear", true) && args[1].equals("all", true) -> handleClearAll(sender, player)

            args.size == 1 && args[0].equals("list", true) -> handleList(sender, player)
            args.size == 2 && args[0].equals("shape", true) -> handleShape(sender, player, args)
            args.size == 3 && args[0].equals("wshape", true) -> handleWorldShapeWithWorld(sender, player, args)
            args.size == 2 && args[0].equals("wshape", true) && player != null -> handleWorldShapeCurrentWorld(sender, player, args)

            args.size == 3 && args[0].equals("wrap", true) -> handleWrapWithWorld(sender, player, args)
            args.size == 2 && args[0].equals("wrap", true) && player != null -> handleWrapCurrentWorld(sender, player, args)

            args.size >= 2 && args[1].equals("fill", true) -> handleFillWithWorld(sender, player, args)
            args.isNotEmpty() && args[0].equals("fill", true) -> handleFillCurrentWorld(sender, player, args, cmdWorld)
            args.size >= 2 && args[1].equals("trim", true) -> handleTrimWithWorld(sender, player, args)
            args.isNotEmpty() && args[0].equals("trim", true) -> handleTrimCurrentWorld(sender, player, args, cmdWorld)
            else -> showHelp(sender, player, args, cmd, cmdWorld)
        }

        return true
    }

    private fun mergeQuotedWorldName(args: Array<String>): Array<String> {
        if (args.size <= 2 || !args[0].startsWith("\"")) return args
        if (args[0].endsWith("\"")) {
            val merged = args.copyOf()
            merged[0] = merged[0].substring(1, merged[0].length - 1)
            return merged
        }

        val quoteEnd = args.indexOfFirst { it.endsWith("\"") }.takeIf { it > 0 } ?: return args
        val quoted = args.slice(0..quoteEnd).joinToString(" ").let { it.substring(1, it.length - 1) }
        return (listOf(quoted) + args.slice((quoteEnd + 1) until args.size)).toTypedArray()
    }

    private fun handleSetWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "set")) return

        if (args.size == 4 && !args.last().equals("spawn", true)) {
            sender.sendMessage("${errColor}You have not provided a sufficient number of arguments. Check command list using root /wb command.")
            return
        }

        val world = sender.server.getWorld(args[0])
        if (world == null) {
            sender.sendMessage("${ChatColor.YELLOW}The world you specified (\"${args[0]}\") could not be found on the server, but data for it will be stored anyway.")
        }

        if (applySet(sender, world, player, args, 2))
            sender.sendMessage("${ChatColor.GREEN}Border has been set. ${BorderRegistry.describe(args[0])}")
    }

    private fun handleSetCurrentWorld(sender: CommandSender, initialPlayer: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(initialPlayer, "set")) return
        var player = initialPlayer

        if (player == null) {
            if (!args[args.size - 2].equals("player", true)) {
                sender.sendMessage("${errColor}You must specify a world name from console if not specifying a player name. Check command list using root \"wb\" command.")
                return
            }

            player = Bukkit.getPlayer(args.last())
            if (player == null || !player.isOnline) {
                sender.sendMessage("${errColor}The player you specified (\"${args.last()}\") does not appear to be online.")
                return
            }
        }

        if (applySet(sender, player.world, player, args, 1)) {
            sender.sendMessage("${ChatColor.GREEN}Border has been set. ${BorderRegistry.describe(player.world.name)}")
        }
    }

    private fun applySet(
        sender: CommandSender,
        initialWorld: World?,
        player: Player?,
        data: Array<String>,
        offset: Int
    ): Boolean {
        var world = initialWorld
        var radiusCount = data.size - offset
        val x: Double
        val z: Double

        fun fail(): Boolean {
            sender.sendMessage("${errColor}The radius value(s) must be integers and the x and z values must be numerical.")
            return false
        }

        when {
            data.last().equals("spawn", true) -> {
                val spawnWorld = world ?: return fail()
                val loc = spawnWorld.spawnLocation
                x = loc.x
                z = loc.z
                radiusCount -= 1
            }

            data[data.size - 2].equals("player", true) -> {
                val target = Bukkit.getPlayer(data.last())
                if (target == null || !target.isOnline) {
                    sender.sendMessage("${errColor}The player you specified (\"${data.last()}\") does not appear to be online.")
                    return false
                }
                world = target.world
                x = target.location.x
                z = target.location.z
                radiusCount -= 2
            }

            else -> {
                if (player == null || radiusCount > 2) {
                    x = data[data.size - 2].toDoubleOrNull() ?: return fail()
                    z = data[data.size - 1].toDoubleOrNull() ?: return fail()
                    radiusCount -= 2
                } else {
                    x = player.location.x
                    z = player.location.z
                }
            }
        }

        val targetWorld = world ?: return fail()
        val radiusX = data[offset].toIntOrNull() ?: return fail()
        val radiusZ = if (radiusCount < 2) radiusX else data[offset + 1].toIntOrNull() ?: return fail()

        BorderRegistry.set(targetWorld.name, radiusX, radiusZ, x, z)
        return true
    }

    private fun handleSetCornersWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "set")) return

        val world = args[0]
        if (sender.server.getWorld(world) == null) {
            sender.sendMessage("${ChatColor.YELLOW}The world you specified (\"$world\") could not be found on the server, but data for it will be stored anyway.")
            return
        }

        val coordinates = args.slice(2..5).map { it.toDoubleOrNull() }
        if (coordinates.any { it == null }) {
            sender.sendMessage("${errColor}The x1, z1, x2, and z2 values must be numerical.")
            return
        }

        val (x1, z1, x2, z2) = coordinates.map { it!! }
        BorderRegistry.setByCorners(world, x1, z1, x2, z2)
        if (player != null) sender.sendMessage("${ChatColor.GREEN}Border has been set. ${BorderRegistry.describe(world)}")
    }

    private fun handleSetCornersCurrentWorld(sender: CommandSender, player: Player, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "set")) return

        val world = player.world.name
        val coordinates = args.slice(1..4).map { it.toDoubleOrNull() }
        if (coordinates.any { it == null }) {
            sender.sendMessage("${errColor}The x1, z1, x2, and z2 values must be numerical.")
            return
        }

        val (x1, z1, x2, z2) = coordinates.map { it!! }
        BorderRegistry.setByCorners(world, x1, z1, x2, z2)
        sender.sendMessage("${ChatColor.GREEN}Border has been set. ${BorderRegistry.describe(world)}")
    }

    private fun handleRadiusWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "radius")) return

        val world = args[0]
        val border = BorderRegistry[world] ?: run {
            sender.sendMessage("${errColor}That world (\"$world\") must first have a border set normally.")
            return
        }

        val radiusX = args[2].toIntOrNull() ?: run {
            sender.sendMessage("${errColor}The radius value(s) must be integers.")
            return
        }
        val radiusZ = if (args.size == 4) {
            args[3].toIntOrNull() ?: run {
                sender.sendMessage("${errColor}The radius value(s) must be integers.")
                return
            }
        } else radiusX

        BorderRegistry.set(world, radiusX, radiusZ, border.x, border.z)
        if (player != null) sender.sendMessage("${ChatColor.GREEN}Radius has been set. ${BorderRegistry.describe(world)}")
    }

    private fun handleRadiusCurrentWorld(sender: CommandSender, player: Player, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "radius")) return

        val world = player.world.name
        val border = BorderRegistry[world] ?: run {
            sender.sendMessage("${errColor}This world (\"$world\") must first have a border set normally.")
            return
        }

        val radiusX = args[1].toIntOrNull() ?: run {
            sender.sendMessage("${errColor}The radius value(s) must be integers.")
            return
        }
        val radiusZ = if (args.size == 3) {
            args[2].toIntOrNull() ?: run {
                sender.sendMessage("${errColor}The radius value(s) must be integers.")
                return
            }
        } else radiusX

        BorderRegistry.set(world, radiusX, radiusZ, border.x, border.z)
        sender.sendMessage("${ChatColor.GREEN}Radius has been set. ${BorderRegistry.describe(world)}")
    }

    private fun handleClearWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "clear")) return

        val world = args[0]
        if (BorderRegistry[world] == null) {
            sender.sendMessage("${ChatColor.RED}The world you specified (\"$world\") does not have a border set.")
            return
        }

        BorderRegistry.remove(world)
        if (player != null) sender.sendMessage("${ChatColor.GREEN}Border cleared for world \"$world\".")
    }

    private fun handleClearCurrentWorld(sender: CommandSender, player: Player) {
        if (!PluginSettings.hasPermission(player, "clear")) return

        val world = player.world.name
        if (BorderRegistry[world] == null) {
            sender.sendMessage("${errColor}Your current world (\"$world\") does not have a border set.")
            return
        }

        BorderRegistry.remove(world)
        sender.sendMessage("${ChatColor.GREEN}Border cleared for world \"$world\".")
    }

    private fun handleClearAll(sender: CommandSender, player: Player?) {
        if (!PluginSettings.hasPermission(player, "clear")) return
        BorderRegistry.clear()
        if (player != null) sender.sendMessage("${ChatColor.GREEN}All borders cleared for all worlds.")
    }

    private fun handleList(sender: CommandSender, player: Player?) {
        if (!PluginSettings.hasPermission(player, "list")) return

        sender.sendMessage("${ChatColor.GOLD}Default border shape for all worlds is \"${PluginSettings.shapeLabel()}\".")
        val descriptions = BorderRegistry.describeAll()
        if (descriptions.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}There are no borders currently set.")
            return
        }
        descriptions.forEach { sender.sendMessage("${ChatColor.GOLD}$it") }
    }

    private fun handleShape(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "shape")) return

        val shape = BorderShape.fromKeyword(args[1]) ?: run {
            sender.sendMessage("${ChatColor.RED}You must specify a shape of \"elliptic\"/\"round\" or \"rectangular\"/\"square\".")
            return
        }

        PluginSettings.setDefaultShape(shape == BorderShape.ROUND)
        if (player != null) sender.sendMessage("${ChatColor.GREEN}Default border shape for all worlds is now set to \"${PluginSettings.shapeLabel()}\".")
    }

    private fun handleWorldShapeWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "wshape")) return
        applyWorldShape(sender, player, args[1], args[2])
    }

    private fun handleWorldShapeCurrentWorld(sender: CommandSender, player: Player, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "wshape")) return
        applyWorldShape(sender, player, player.world.name, args[1])
    }

    private fun applyWorldShape(sender: CommandSender, player: Player?, world: String, keyword: String) {
        val border = BorderRegistry[world] ?: run {
            sender.sendMessage("${ChatColor.RED}The world you specified (\"$world\") does not have a border set.")
            return
        }

        val shape = BorderShape.fromKeyword(keyword)
        border.shapeOverride = shape
        BorderRegistry.set(world, border)

        if (player != null) {
            sender.sendMessage("${ChatColor.GREEN}Border shape for world \"$world\" is now set to \"${shape?.label ?: "default"}\".")
        }
    }

    private fun handleWrapWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "wrap")) return
        applyWrap(sender, player, args[1], args[2])
    }

    private fun handleWrapCurrentWorld(sender: CommandSender, player: Player, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "wrap")) return
        applyWrap(sender, player, player.world.name, args[1])
    }

    private fun applyWrap(sender: CommandSender, player: Player?, world: String, keyword: String) {
        val border = BorderRegistry[world] ?: run {
            sender.sendMessage("${ChatColor.RED}The world you specified (\"$world\") does not have a border set.")
            return
        }

        val wrap = parseBoolean(keyword)
        border.wrapping = wrap
        BorderRegistry.set(world, border)

        if (player != null) {
            sender.sendMessage("${ChatColor.GREEN}Border for world \"$world\" is now set to ${if (wrap) "" else "not "}wrap around.")
        }
    }

    private fun parseBoolean(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("y") || lower.startsWith("t") || lower.startsWith("on") ||
                lower.startsWith("+") || lower.startsWith("1")
    }

    private fun parseTaskFlags(args: Array<String>, index: Int): TaskFlags {
        val flag =
            args.getOrNull(index) ?: return TaskFlags(cancel = false, confirm = false, pause = false, frequency = "")
        val cancel = flag.equals("cancel", true) || flag.equals("stop", true)
        val confirm = flag.equals("confirm", true)
        val pause = flag.equals("pause", true)
        val frequency = if (!cancel && !confirm && !pause) flag else ""
        return TaskFlags(cancel, confirm, pause, frequency)
    }

    private fun handleFillWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "fill")) return
        val flags = parseTaskFlags(args, 2)
        val pad = args.getOrElse(3) { "" }
        val forceLoad = args.getOrElse(4) { "" }
        runFill(sender, player, args[0], flags.confirm, flags.cancel, flags.pause, pad, flags.frequency, forceLoad)
    }

    private fun handleFillCurrentWorld(sender: CommandSender, player: Player?, args: Array<String>, cmdWorld: String) {
        if (!PluginSettings.hasPermission(player, "fill")) return
        val flags = parseTaskFlags(args, 1)
        val pad = args.getOrElse(2) { "" }
        val forceLoad = args.getOrElse(3) { "" }
        val world = if (player != null && !flags.cancel && !flags.confirm && !flags.pause) player.world.name else ""

        if (!flags.cancel && !flags.confirm && !flags.pause && world.isEmpty()) {
            sender.sendMessage("${errColor}You must specify a world! Example: $cmdWorld fill $optColor[freq] [pad] [force]")
            return
        }

        runFill(sender, player, world, flags.confirm, flags.cancel, flags.pause, pad, flags.frequency, forceLoad)
    }

    private fun runFill(
        sender: CommandSender,
        player: Player?,
        world: String,
        confirm: Boolean,
        cancel: Boolean,
        pause: Boolean,
        pad: String,
        frequency: String,
        forceLoad: String
    ) {
        if (cancel) {
            sender.sendMessage("${headColor}Cancelling the world map generation task.")
            resetFillDefaults()
            currentFillTask?.cancel()
            return
        }

        if (pause) {
            val task = currentFillTask
            if (task == null || !task.isValid) {
                sender.sendMessage("${headColor}The world map generation task is not currently running.")
                return
            }
            task.pause()
            sender.sendMessage("${headColor}The world map generation task is now ${if (task.isPaused) "" else "un"}paused.")
            return
        }

        if (currentFillTask?.isValid == true) {
            sender.sendMessage("${headColor}The world map generation task is already running.")
            return
        }

        if (pad.isNotEmpty()) {
            fillPadding = pad.toIntOrNull()?.let(Math::abs) ?: run {
                sender.sendMessage("${errColor}The frequency and padding values must be integers.")
                resetFillDefaults()
                return
            }
        }
        if (frequency.isNotEmpty()) {
            fillFrequency = frequency.toIntOrNull()?.let(Math::abs) ?: run {
                sender.sendMessage("${errColor}The frequency and padding values must be integers.")
                resetFillDefaults()
                return
            }
        }
        if (fillFrequency <= 0) {
            sender.sendMessage("${errColor}The frequency value must be greater than zero.")
            resetFillDefaults()
            return
        }
        if (forceLoad.isNotEmpty()) fillForceLoad = parseBoolean(forceLoad)
        if (world.isNotEmpty()) fillWorld = world

        if (confirm) {
            if (fillWorld.isEmpty()) {
                sender.sendMessage("${errColor}You must first use this command successfully without confirming.")
                return
            }
            if (player != null) PluginSettings.log("Filling out world to border at the command of player \"${player.name}\".")

            var ticks = 1
            var repeats = 1
            if (fillFrequency > 20) repeats = fillFrequency / 20 else ticks = 20 / fillFrequency

            val task = WorldFillTask(plugin.server, player, fillWorld, fillPadding, repeats, ticks, fillForceLoad)
            currentFillTask = task

            if (task.isValid) {
                task.taskId =
                    plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, task, ticks.toLong(), ticks.toLong())
                sender.sendMessage("${ChatColor.GREEN}WorldBorder map generation for world \"$fillWorld\" task started.")
            } else {
                sender.sendMessage("${errColor}The world map generation task failed to start.")
            }

            resetFillDefaults()
        } else if (fillWorld.isEmpty()) {
            sender.sendMessage("${errColor}You must first specify a valid world.")
        }
    }

    private fun resetFillDefaults() {
        fillWorld = ""
        fillFrequency = 20
        fillPadding = ChunkCoordinate.chunkToBlock(13)
        fillForceLoad = false
    }

    private fun handleTrimWithWorld(sender: CommandSender, player: Player?, args: Array<String>) {
        if (!PluginSettings.hasPermission(player, "trim")) return
        val flags = parseTaskFlags(args, 2)
        val pad = args.getOrElse(3) { "" }
        runTrim(sender, player, args[0], flags.confirm, flags.cancel, flags.pause, pad, flags.frequency)
    }

    private fun handleTrimCurrentWorld(sender: CommandSender, player: Player?, args: Array<String>, cmdWorld: String) {
        if (!PluginSettings.hasPermission(player, "trim")) return
        val flags = parseTaskFlags(args, 1)
        val pad = args.getOrElse(2) { "" }
        val world = if (player != null && !flags.cancel && !flags.confirm && !flags.pause) player.world.name else ""

        if (!flags.cancel && !flags.confirm && !flags.pause && world.isEmpty()) {
            sender.sendMessage("${errColor}You must specify a world! Example: $cmdWorld trim $optColor[freq] [pad]")
            return
        }

        runTrim(sender, player, world, flags.confirm, flags.cancel, flags.pause, pad, flags.frequency)
    }

    private fun runTrim(
        sender: CommandSender,
        player: Player?,
        world: String,
        confirm: Boolean,
        cancel: Boolean,
        pause: Boolean,
        pad: String,
        frequency: String
    ) {
        if (cancel) {
            sender.sendMessage("${headColor}Cancelling the world map trimming task.")
            resetTrimDefaults()
            currentTrimTask?.cancel()
            return
        }

        if (pause) {
            val task = currentTrimTask
            if (task == null || !task.isValid) {
                sender.sendMessage("${headColor}The world map trimming task is not currently running.")
                return
            }
            task.pause()
            sender.sendMessage("${headColor}The world map trimming task is now ${if (task.isPaused) "" else "un"}paused.")
            return
        }

        if (currentTrimTask?.isValid == true) {
            sender.sendMessage("${headColor}The world map trimming task is already running.")
            return
        }

        if (pad.isNotEmpty()) {
            trimPadding = pad.toIntOrNull()?.let(Math::abs) ?: run {
                sender.sendMessage("${errColor}The frequency and padding values must be integers.")
                resetTrimDefaults()
                return
            }
        }
        if (frequency.isNotEmpty()) {
            trimFrequency = frequency.toIntOrNull()?.let(Math::abs) ?: run {
                sender.sendMessage("${errColor}The frequency and padding values must be integers.")
                resetTrimDefaults()
                return
            }
        }
        if (trimFrequency <= 0) {
            sender.sendMessage("${errColor}The frequency value must be greater than zero.")
            resetTrimDefaults()
            return
        }
        if (world.isNotEmpty()) trimWorld = world

        if (confirm) {
            if (trimWorld.isEmpty()) {
                sender.sendMessage("${errColor}You must first use this command successfully without confirming.")
                return
            }
            if (player != null) PluginSettings.log("Trimming world beyond border at the command of player \"${player.name}\".")

            var ticks = 1
            var repeats = 1
            if (trimFrequency > 20) repeats = trimFrequency / 20 else ticks = 20 / trimFrequency

            val task = WorldTrimTask(plugin.server, player, trimWorld, trimPadding, repeats)
            currentTrimTask = task

            if (task.isValid) {
                task.taskId =
                    plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, task, ticks.toLong(), ticks.toLong())
                sender.sendMessage("${ChatColor.GREEN}WorldBorder map trimming task for world \"$trimWorld\" started.")
            } else {
                sender.sendMessage("${errColor}The world map trimming task failed to start.")
            }

            resetTrimDefaults()
        } else if (trimWorld.isEmpty()) {
            sender.sendMessage("${errColor}You must first specify a valid world.")
        } else {
            val cmd = "$cmdColor${if (player == null) "wb" else "/wb"}"
            sender.sendMessage(
                "${headColor}World trimming task is ready for world \"$trimWorld\", trimming the map past " +
                        "$trimPadding blocks beyond the border (default ${ChunkCoordinate.chunkToBlock(13)}), and the " +
                        "task will try to process up to $trimFrequency chunks per second (default 5000)."
            )
            sender.sendMessage(
                "${headColor}This process can take a while depending on the world's overall size. Also, depending " +
                        "on the chunk processing rate, players may experience lag for the duration."
            )
            sender.sendMessage("${descColor}You should now use $cmd trim confirm$descColor to start the process.")
            sender.sendMessage("${descColor}You can cancel at any time with $cmd trim cancel$descColor, or pause/unpause with $cmd trim pause$descColor.")
        }
    }

    private fun resetTrimDefaults() {
        trimWorld = ""
        trimFrequency = 5000
        trimPadding = ChunkCoordinate.chunkToBlock(13)
    }

    private fun showHelp(sender: CommandSender, player: Player?, args: Array<String>, cmd: String, cmdWorld: String) {
        if (!PluginSettings.hasPermission(player, "help")) return

        var page = if (player == null) 0 else 1
        if (args.size == 1) {
            page = args[0].toIntOrNull() ?: page
            if (page > 4) page = 1
        }

        sender.sendMessage(
            "$headColor${plugin.description.fullName} - commands ($reqColor<required> $optColor[optional]$headColor)" +
                    "${if (page > 0) " $page/4" else ""}:"
        )

        val worldToken = { if (player == null) "$reqColor<world>" else "$optColor[world]" }

        if (page == 0 || page == 1) {
            if (player != null) sender.sendMessage("$cmd set $reqColor<radiusX> $optColor[radiusZ]$descColor - set border, centered on you.")
            sender.sendMessage("$cmdWorld set $reqColor<radiusX> $optColor[radiusZ] <x> <z>$descColor - set border.")
            sender.sendMessage("$cmdWorld set $reqColor<radiusX> $optColor[radiusZ] spawn$descColor - use spawn point.")
            sender.sendMessage("$cmd set $reqColor<radiusX> $optColor[radiusZ] player <name>$descColor - center on player.")
            sender.sendMessage("$cmdWorld setcorners $reqColor<x1> <z1> <x2> <z2>$descColor - set by corners.")
            sender.sendMessage("$cmdWorld radius $reqColor<radiusX> $optColor[radiusZ]$descColor - change radius.")
            sender.sendMessage("$cmd shape $reqColor<elliptic|rectangular>$descColor - set the default shape.")
            sender.sendMessage("$cmd shape $reqColor<round|square>$descColor - same as above.")
            if (page == 1) sender.sendMessage("$cmd 2$descColor - view second page of commands.")
        }
        if (page == 0 || page == 2) {
            sender.sendMessage("$cmdWorld clear$descColor - remove border for this world.")
            sender.sendMessage("$cmd clear all$descColor - remove border for all worlds.")
            sender.sendMessage("$cmd list$descColor - show border information for all worlds.")
            sender.sendMessage("$cmdWorld fill $optColor[freq] [pad] [force]$descColor - fill world to border.")
            sender.sendMessage("$cmdWorld trim $optColor[freq] [pad]$descColor - trim world outside of border.")
            sender.sendMessage("$cmd wrap ${worldToken()}$reqColor <on/off>$descColor - can make border crossings wrap.")
            if (page == 2) sender.sendMessage("$cmd 3$descColor - view third page of commands.")
        }
        if (page == 0 || page == 3) {
            sender.sendMessage("$cmd wshape ${worldToken()}$reqColor <elliptic|rectangular|default>$descColor - shape override for this world.")
            sender.sendMessage("$cmd wshape ${worldToken()}$reqColor <round|square|default>$descColor - same as above.")
            if (page == 3) sender.sendMessage("$cmd 4$descColor - view fourth page of commands.")
        }
        if (page == 0 || page == 4) {
            if (page == 4) sender.sendMessage("$cmd$descColor - view first page of commands.")
        }
    }
}
