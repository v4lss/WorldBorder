package me.vals.worldborder.border

import org.bukkit.World

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file SafeLocationFinder.kt
 * @created 14/8/2026
 */

object SafeLocationFinder {

    private val safeOpenBlocks = setOf(
        0, 6, 8, 9, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 55, 59, 63, 64, 65, 66,
        68, 69, 70, 71, 72, 75, 76, 77, 78, 83, 90, 93, 94, 96, 104, 105, 106, 115,
        131, 132, 141, 142, 149, 150, 157, 171
    )
    private val painfulBlocks = setOf(10, 11, 51, 81, 119)
    private const val MIN_Y = -64

    fun find(world: World, x: Int, y: Int, z: Int, flying: Boolean): Double? {
        val maxY = if (world.environment == World.Environment.NETHER) 125 else world.maxHeight - 2
        var below = y
        var above = y

        while (below > MIN_Y || above < maxY) {
            if (below > MIN_Y && isSafe(world, x, below, z, flying)) return below.toDouble()
            if (above < maxY && above != below && isSafe(world, x, above, z, flying)) return above.toDouble()
            below--
            above++
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun isSafe(world: World, x: Int, y: Int, z: Int, flying: Boolean): Boolean {
        val open = world.getBlockTypeIdAt(x, y, z) in safeOpenBlocks &&
                world.getBlockTypeIdAt(x, y + 1, z) in safeOpenBlocks

        if (!open || flying) return open

        val below = world.getBlockTypeIdAt(x, y - 1, z)
        return (below !in safeOpenBlocks || below == 8 || below == 9) && below !in painfulBlocks
    }
}
