package me.vals.worldborder.coordinate

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file ChunkCoordinate.kt
 * @created 14/8/2026
 */

data class ChunkCoordinate(val x: Int, val z: Int) {

    companion object {
        fun blockToChunk(block: Int) = block shr 4
        fun chunkToRegion(chunk: Int) = chunk shr 5
        fun chunkToBlock(chunk: Int) = chunk shl 4
        fun regionToChunk(region: Int) = region shl 5
    }
}
