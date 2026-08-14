package me.vals.worldborder.registry

import me.vals.worldborder.border.BorderRegion
import me.vals.worldborder.border.BorderShape
import me.vals.worldborder.settings.PluginSettings
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderRegistry.kt
 * @created 14/8/2026
 */

object BorderRegistry {

    private val borders = Collections.synchronizedMap(LinkedHashMap<String, BorderRegion>())

    operator fun get(world: String): BorderRegion? = borders[world]

    fun set(world: String, border: BorderRegion) {
        borders[world] = border
        PluginSettings.log("Border set. ${describe(world)}")
    }

    fun set(
        world: String,
        radiusX: Int,
        radiusZ: Int,
        x: Double,
        z: Double,
        shape: BorderShape? = borders[world]?.shapeOverride,
        wrap: Boolean = borders[world]?.wrapping ?: false
    ) = set(world, BorderRegion(x, z, radiusX, radiusZ, shape, wrap))

    fun setByCorners(
        world: String,
        x1: Double,
        z1: Double,
        x2: Double,
        z2: Double,
        shape: BorderShape? = borders[world]?.shapeOverride,
        wrap: Boolean = borders[world]?.wrapping ?: false
    ) {
        val radiusX = abs(x1 - x2) / 2
        val radiusZ = abs(z1 - z2) / 2
        val x = minOf(x1, x2) + radiusX
        val z = minOf(z1, z2) + radiusZ
        set(world, BorderRegion(x, z, radiusX.roundToLong().toInt(), radiusZ.roundToLong().toInt(), shape, wrap))
    }

    fun remove(world: String) {
        borders.remove(world)
        PluginSettings.log("Removed border for world \"$world\".")
    }

    fun clear() {
        borders.clear()
        PluginSettings.log("Removed all borders for all worlds.")
    }

    fun describe(world: String) = borders[world]?.let { "World \"$world\" has border $it" }
        ?: "No border was found for the world \"$world\"."

    fun describeAll(): Set<String> = borders.keys.map { describe(it) }.toSet()
}
