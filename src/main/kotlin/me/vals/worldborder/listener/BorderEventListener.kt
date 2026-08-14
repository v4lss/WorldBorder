package me.vals.worldborder.listener

import me.vals.worldborder.registry.BorderRegistry
import me.vals.worldborder.settings.PluginSettings
import me.vals.worldborder.task.BorderPatrolTask
import me.vals.worldborder.task.BorderTimer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderEventListener.kt
 * @created 14/8/2026
 */

class BorderEventListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val corrected = BorderPatrolTask.enforce(event.player, event.to, teleport = false, notify = true) ?: return

        if (event.cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.isCancelled = true
            return
        }

        event.to = corrected
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        BorderPatrolTask.enforce(event.player, event.to, teleport = false, notify = false)?.let { event.to = it }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (BorderTimer.isRunning) return
        PluginSettings.log("Border-checking task was not running! Something on your server apparently killed it. It will now be restarted.")
        BorderTimer.start()
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val world = event.entity.location.world ?: return
        val border = BorderRegistry[world.name] ?: return
        if (!border.isInside(event.entity.location.x, event.entity.location.z)) event.isCancelled = true
    }
}
