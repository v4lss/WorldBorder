package me.vals.worldborder.io

import me.vals.worldborder.coordinate.ChunkCoordinate
import me.vals.worldborder.settings.PluginSettings
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.File
import java.io.RandomAccessFile
import java.util.*

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file RegionFileIndex.kt
 * @created 14/8/2026
 */

class RegionFileIndex private constructor(
    private val notifyPlayer: Player?,
    val regionFolder: File,
    val regionFiles: List<File>
) {
    private val regionChunkExistence = Collections.synchronizedMap(HashMap<ChunkCoordinate, BitSet>())

    val regionFileCount get() = regionFiles.size

    fun regionFile(index: Int): File? = regionFiles.getOrNull(index)

    fun regionFileCoordinates(index: Int): ChunkCoordinate? {
        val file = regionFile(index) ?: return null
        val parts = file.name.split(".")
        return runCatching { ChunkCoordinate(parts[1].toInt(), parts[2].toInt()) }
            .onFailure { notify("Error! Region file found with abnormal name: ${file.name}") }
            .getOrNull()
    }

    fun chunkExists(x: Int, z: Int): Boolean {
        val region = ChunkCoordinate(ChunkCoordinate.chunkToRegion(x), ChunkCoordinate.chunkToRegion(z))
        return regionData(region)[offset(x, z)]
    }

    fun isChunkFullyGenerated(x: Int, z: Int) =
        chunkExists(x, z) && chunkExists(x + 1, z) && chunkExists(x - 1, z) &&
                chunkExists(x, z + 1) && chunkExists(x, z - 1)

    fun markChunkExists(x: Int, z: Int) {
        val region = ChunkCoordinate(ChunkCoordinate.chunkToRegion(x), ChunkCoordinate.chunkToRegion(z))
        regionData(region).set(offset(x, z))
    }

    private fun offset(x: Int, z: Int) = Math.floorMod(x, REGION_SIZE) + Math.floorMod(z, REGION_SIZE) * REGION_SIZE

    private fun regionData(region: ChunkCoordinate): BitSet = regionChunkExistence.getOrPut(region) {
        val bits = BitSet(REGION_CHUNKS)
        regionFiles.withIndex()
            .firstOrNull { (index, _) -> regionFileCoordinates(index) == region }
            ?.let { (_, file) ->
                runCatching {
                    RandomAccessFile(file, "r").use { data ->
                        repeat(REGION_CHUNKS) { chunkIndex -> if (data.readInt() != 0) bits.set(chunkIndex) }
                    }
                }.onFailure { notify("Error! Could not read region file to find generated chunks: ${file.name}") }
            }
        bits
    }

    private fun notify(text: String) {
            val message = "${ChatColor.YELLOW}[WorldData] $text"
        PluginSettings.log(message)
        if (notifyPlayer?.isOnline == true) notifyPlayer.sendMessage(message)
    }

    companion object {
        private const val REGION_SIZE = 32
        private const val REGION_CHUNKS = REGION_SIZE * REGION_SIZE

        fun create(world: World, notifyPlayer: Player?): RegionFileIndex? {
            var folder = File(world.worldFolder, "region")

            if (!folder.isDirectory) {
                folder = world.worldFolder
                    .listFiles { file -> file.isDirectory && file.name.startsWith("DIM", ignoreCase = true) }
                    ?.map { File(it, "region") }
                    ?.firstOrNull { it.isDirectory }
                    ?: return fail(
                        notifyPlayer,
                        "Could not validate folder for world's region files. Looked in ${world.worldFolder.path} " +
                                "for valid DIM* folder with a region folder in it."
                    )
            }

            val regionFiles =
                folder.listFiles { file -> file.isFile && file.name.lowercase(Locale.ROOT).endsWith(".mca") }
                    ?.toList().orEmpty()
                    .ifEmpty {
                        folder.listFiles { file -> file.isFile && file.name.lowercase(Locale.ROOT).endsWith(".mcr") }
                            ?.toList().orEmpty()
                    }

            if (regionFiles.isEmpty()) {
                return fail(notifyPlayer, "Could not find any region files. Looked in: ${folder.path}")
            }

            return RegionFileIndex(notifyPlayer, folder, regionFiles)
        }

        private fun fail(notifyPlayer: Player?, text: String): RegionFileIndex? {
        val message = "${ChatColor.YELLOW}[WorldData] $text"
            PluginSettings.log(message)
            if (notifyPlayer?.isOnline == true) notifyPlayer.sendMessage(message)
            return null
        }
    }
}
