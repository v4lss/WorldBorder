package me.vals.worldborder.task

import me.vals.worldborder.settings.PluginSettings
import org.bukkit.scheduler.BukkitTask

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderTimer.kt
 * @created 14/8/2026
 */

object BorderTimer {

    private var task: BukkitTask? = null
    private var running = false

    val isRunning: Boolean
        get() = running

    fun start() {
        stop()
        task = PluginSettings.plugin.server.scheduler.runTaskTimer(
            PluginSettings.plugin,
            BorderPatrolTask(),
            PluginSettings.timerTicks.toLong(),
            PluginSettings.timerTicks.toLong()
        )
        running = true
        PluginSettings.log("Border-checking timed task started.")
    }

    fun stop() {
        task?.cancel()
        task = null
        running = false
        PluginSettings.log("Border-checking timed task stopped.")
    }
}
