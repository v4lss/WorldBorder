package me.vals.worldborder.border

import me.vals.worldborder.coordinate.ChunkCoordinate
import me.vals.worldborder.settings.PluginSettings
import org.bukkit.Location
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderRegion.kt
 * @created 14/8/2026
 */

class BorderRegion(
    x: Double,
    z: Double,
    radiusX: Int,
    radiusZ: Int,
    var shapeOverride: BorderShape? = null,
    var wrapping: Boolean = false
) {
    var x: Double = x
        set(value) {
            field = value; recalculateX()
        }

    var z: Double = z
        set(value) {
            field = value; recalculateZ()
        }

    var radiusX: Int = radiusX
        set(value) {
            field = value; recalculateX()
        }

    var radiusZ: Int = radiusZ
        set(value) {
            field = value; recalculateZ()
        }

    private var minX = 0.0
    private var maxX = 0.0
    private var minZ = 0.0
    private var maxZ = 0.0
    private var radiusXSquared = 0.0
    private var radiusZSquared = 0.0
    private var halfDiagonalX = 0.0
    private var halfDiagonalZ = 0.0
    private var radiusSquaredQuotient = 0.0

    init {
        recalculateX()
        recalculateZ()
    }

    private fun recalculateX() {
        minX = x - radiusX
        maxX = x + radiusX
        radiusXSquared = radiusX.toDouble() * radiusX
        halfDiagonalX = sqrt(0.5 * radiusXSquared)
        recalculateQuotient()
    }

    private fun recalculateZ() {
        minZ = z - radiusZ
        maxZ = z + radiusZ
        radiusZSquared = radiusZ.toDouble() * radiusZ
        halfDiagonalZ = sqrt(0.5 * radiusZSquared)
        recalculateQuotient()
    }

    private fun recalculateQuotient() {
        radiusSquaredQuotient = if (radiusZSquared != 0.0) radiusXSquared / radiusZSquared else 0.0
    }

    fun copy() = BorderRegion(x, z, radiusX, radiusZ, shapeOverride, wrapping)

    fun averageRadius() = (radiusX + radiusZ) / 2

    fun isInside(
        x: Double,
        z: Double,
        round: Boolean = PluginSettings.roundByDefault
    ): Boolean {
        val effectiveRound =
            shapeOverride?.let { it == BorderShape.ROUND } ?: round
        if (!effectiveRound) return !(x !in minX..maxX || z < minZ || z > maxZ)

        val dx = abs(this.x - x)
        val dz = abs(this.z - z)

        return when {
            dx < halfDiagonalX && dz < halfDiagonalZ -> true
            dx >= radiusX || dz >= radiusZ -> false
            else -> dx * dx + dz * dz * radiusSquaredQuotient < radiusXSquared
        }
    }

    fun isInside(
        location: Location,
        round: Boolean = PluginSettings.roundByDefault
    ) =
        isInside(location.x, location.z, round)

    fun isInside(
        coordinate: ChunkCoordinate,
        round: Boolean = PluginSettings.roundByDefault
    ) =
        isInside(coordinate.x.toDouble(), coordinate.z.toDouble(), round)

    fun correctedPosition(
        location: Location,
        round: Boolean = PluginSettings.roundByDefault,
        flying: Boolean = false
    ): Location? {
        val effectiveRound =
            shapeOverride?.let { it == BorderShape.ROUND } ?: round
        var targetX = location.x
        var targetZ = location.z

        if (!effectiveRound) {
            targetX = when {
                targetX <= minX -> if (wrapping) maxX - PluginSettings.knockback else minX + PluginSettings.knockback
                targetX >= maxX -> if (wrapping) minX + PluginSettings.knockback else maxX - PluginSettings.knockback
                else -> targetX
            }
            targetZ = when {
                targetZ <= minZ -> if (wrapping) maxZ - PluginSettings.knockback else minZ + PluginSettings.knockback
                targetZ >= maxZ -> if (wrapping) minZ + PluginSettings.knockback else maxZ - PluginSettings.knockback
                else -> targetZ
            }
        } else {
            val dx = location.x - x
            val dz = location.z - z
            val distanceUntransformed = sqrt(dx * dx + dz * dz)
            val distanceTransformed = sqrt(dx * dx / radiusXSquared + dz * dz / radiusZSquared)
            val factor = 1 / distanceTransformed - PluginSettings.knockback / distanceUntransformed
            targetX = if (wrapping) x - dx * factor else x + dx * factor
            targetZ = if (wrapping) z - dz * factor else z + dz * factor
        }

        val blockX = Location.locToBlock(targetX)
        val blockZ = Location.locToBlock(targetZ)
        val world = location.world ?: return null
        val chunk = world.getChunkAt(ChunkCoordinate.blockToChunk(blockX), ChunkCoordinate.blockToChunk(blockZ))
        if (!chunk.isLoaded) chunk.load()

        val safeY =
            SafeLocationFinder.find(world, blockX, Location.locToBlock(location.y), blockZ, flying) ?: return null

        return Location(world, floor(targetX) + 0.5, safeY, floor(targetZ) + 0.5, location.yaw, location.pitch)
    }

    override fun toString(): String {
        val radiusText = if (radiusX == radiusZ) "$radiusX" else "${radiusX}x$radiusZ"
        val shapeText = shapeOverride?.let { " (shape override: ${it.label})" } ?: ""
        val wrapText = if (wrapping) " (wrapping)" else ""
        return "radius $radiusText at X: ${PluginSettings.coordinateFormat.format(x)} " +
                "Z: ${PluginSettings.coordinateFormat.format(z)}$shapeText$wrapText"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BorderRegion) return false
        return x == other.x && z == other.z && radiusX == other.radiusX && radiusZ == other.radiusZ
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + radiusX
        result = 31 * result + radiusZ
        return result
    }
}
