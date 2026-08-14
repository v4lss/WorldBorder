package me.vals.worldborder.task

import me.vals.worldborder.WorldBorder
import me.vals.worldborder.border.BorderRegion
import me.vals.worldborder.registry.BorderRegistry
import me.vals.worldborder.settings.PluginSettings
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector
import java.util.*
import kotlin.random.Random

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderPatrolTask.kt
 * @created 14/8/2026
 */

class BorderPatrolTask : Runnable {

    override fun run() {
        Bukkit.getOnlinePlayers().forEach { enforce(it, null, teleport = true, notify = true) }
    }

    companion object {
        private val handlingPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

        fun enforce(player: Player, targetLocation: Location?, teleport: Boolean, notify: Boolean): Location? {
            if (!player.isOnline) return null

            val location = targetLocation ?: player.location
            val world = location.world ?: return null
            val border = BorderRegistry[world.name] ?: return null

            if (border.isInside(location.x, location.z)) return null
            if (!handlingPlayers.add(player.uniqueId)) return null

            val correctedLocation = border.correctedPosition(location, PluginSettings.roundByDefault, player.isFlying)
                ?: fallbackLocation(world, border)

            if (notify) player.sendMessage("${ChatColor.RED}You have reached the border!")

            var handlingVehicle = false
            @Suppress("DEPRECATION")
            player.vehicle?.let { vehicle ->
                player.leaveVehicle()
                val verticalOffset = if (vehicle is LivingEntity) 0.0 else vehicle.location.y - location.y
                val vehicleLocation = correctedLocation.clone().apply { y = correctedLocation.y + verticalOffset }
                vehicle.velocity = Vector()
                vehicle.teleport(vehicleLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                Bukkit.getScheduler().runTaskLater(WorldBorder.instance, {
                    handlingPlayers.remove(player.uniqueId)
                    if (player.isOnline) vehicle.passenger = player
                }, 2L)
                handlingVehicle = true
            }

            @Suppress("DEPRECATION")
            player.passenger?.let { passenger ->
                player.eject()
                passenger.teleport(correctedLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                player.sendMessage("${ChatColor.YELLOW}Your passenger has been ejected.")
            }

            if (teleport) player.teleport(correctedLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
            if (!handlingVehicle) handlingPlayers.remove(player.uniqueId)

            return correctedLocation
        }

        private fun fallbackLocation(world: org.bukkit.World, border: BorderRegion): Location {
            val centerX = border.x.toInt()
            val centerZ = border.z.toInt()
            val randomX = centerX + Random.nextInt(-border.radiusX, border.radiusX + 1)
            val randomZ = centerZ + Random.nextInt(-border.radiusZ, border.radiusZ + 1)
            return world.getHighestBlockAt(randomX, randomZ).location
        }
    }
}
