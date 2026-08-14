package me.vals.worldborder

import me.vals.worldborder.command.BorderCommandExecutor
import me.vals.worldborder.listener.BorderEventListener
import me.vals.worldborder.settings.PluginSettings
import me.vals.worldborder.task.BorderTimer
import org.bukkit.plugin.java.JavaPlugin

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file WorldBorder.kt
 * @created 14/8/2026
 */

class WorldBorder : JavaPlugin() {

    override fun onEnable() {
        PluginSettings.bind(this)
        BorderTimer.start()

        getCommand("wborder")?.executor = BorderCommandExecutor(this)
        server.pluginManager.registerEvents(BorderEventListener(), this)

        val spawn = server.worlds.first().spawnLocation
        PluginSettings.log("For reference, the main world's spawn location is at X: ${PluginSettings.coordinateFormat.format(spawn.x)} " +
                "Y: ${PluginSettings.coordinateFormat.format(spawn.y)} Z: ${PluginSettings.coordinateFormat.format(spawn.z)}")
    }

    override fun onDisable() {
        BorderTimer.stop()
    }

    companion object {
        val instance: WorldBorder
            get() = getPlugin(WorldBorder::class.java)
    }
}
