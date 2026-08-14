package me.vals.worldborder.settings

import me.vals.worldborder.WorldBorder
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.text.DecimalFormat
import java.util.logging.Level

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file PluginSettings.kt
 * @created 14/8/2026
 */

object PluginSettings {

    lateinit var plugin: WorldBorder
        private set

    val coordinateFormat = DecimalFormat("0.0")
    var roundByDefault = true
        private set
    var timerTicks = 4
    var fillAutosaveFrequency = 30
    var fillMemoryTolerance = 500
    const val knockback = 3.0

    fun bind(plugin: WorldBorder) {
        this.plugin = plugin
    }

    fun now() = System.currentTimeMillis()

    fun setDefaultShape(round: Boolean) {
        roundByDefault = round
        log("Set default border shape to ${shapeLabel(round)}.")
    }

    fun shapeLabel(round: Boolean = roundByDefault) = if (round) "elliptic/round" else "rectangular/square"

    fun availableMemoryMegabytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1_048_576
    }

    fun isMemoryLow() = availableMemoryMegabytes() < fillMemoryTolerance

    fun hasPermission(player: Player?, node: String, notify: Boolean = true): Boolean {
        if (player == null) return true
        if (player.hasPermission("worldborder.command.$node")) return true
        if (notify) player.sendMessage("${ChatColor.RED}You do not have sufficient permissions.")
        return false
    }

    fun log(text: String, level: Level = Level.INFO) = plugin.logger.log(level, text)
}
