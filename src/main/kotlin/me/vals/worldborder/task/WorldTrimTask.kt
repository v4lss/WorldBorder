package me.vals.worldborder.task

import me.vals.worldborder.border.BorderRegion
import me.vals.worldborder.coordinate.ChunkCoordinate
import me.vals.worldborder.io.RegionFileIndex
import me.vals.worldborder.registry.BorderRegistry
import me.vals.worldborder.settings.PluginSettings
import org.bukkit.ChatColor
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.RandomAccessFile

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file WorldTrimTask.kt
 * @created 14/8/2026
 */

class WorldTrimTask(
    private val server: Server,
    private val notifyPlayer: Player?,
    worldName: String,
    private val trimDistance: Int,
    private val chunksPerRun: Int
) : Runnable {

    companion object {
        private const val CORNER_CHUNKS = 4
        private const val EDGE_CHUNKS = 120
        private const val CORNER_PLUS_EDGE_CHUNKS = CORNER_CHUNKS + EDGE_CHUNKS
        private const val REGION_CHUNKS = 1024
        private const val PROGRESS_ESTIMATE_PER_REGION = 3072
    }

    val world: World? = server.getWorld(worldName)

    private var border: BorderRegion? = null
    private var worldData: RegionFileIndex? = null

    var isValid = false
        private set
    var isPaused = false
        private set
    var taskId = -1

    private var currentRegion = -1
    private var regionX = 0
    private var regionZ = 0
    private var currentChunk = 0
    private var regionChunks = mutableListOf<ChunkCoordinate>()
    private var trimChunks = mutableListOf<ChunkCoordinate>()
    private var counter = 0
    private var readyToGo = false

    private var lastReport = PluginSettings.now()
    private var reportTarget = 0
    private var reportTotal = 0
    private var reportTrimmedRegions = 0
    private var reportTrimmedChunks = 0

    init {
        val currentWorld = world
        val configuredBorder = BorderRegistry[worldName]

        when {
            currentWorld == null -> {
                notify(if (worldName.isEmpty()) "You must specify a world!" else "World \"$worldName\" not found!")
                stop()
            }

            configuredBorder == null -> {
                notify("No border found for world \"$worldName\"!")
                stop()
            }

            else -> {
                val trimBorder = configuredBorder.copy()
                trimBorder.radiusX += trimDistance
                trimBorder.radiusZ += trimDistance
                border = trimBorder

                val data = RegionFileIndex.create(currentWorld, notifyPlayer)
                if (data == null) {
                    stop()
                } else {
                    worldData = data
                    reportTarget = data.regionFileCount * PROGRESS_ESTIMATE_PER_REGION
                    isValid = nextFile()
                    readyToGo = isValid
                }
            }
        }
    }

    override fun run() {
        if (!isValid || !readyToGo || isPaused) return

        readyToGo = false
        val loopStart = PluginSettings.now()
        counter = 0

        while (counter <= chunksPerRun) {
            if (isPaused) return

            val now = PluginSettings.now()
            if (now > lastReport + 5000) reportProgress()
            if (now > loopStart + 45) {
                readyToGo = true
                return
            }

            when {
                regionChunks.isEmpty() -> addCornerChunks()

                currentChunk == CORNER_CHUNKS -> {
                    if (trimChunks.isEmpty()) {
                        counter += 4
                        nextFile()
                        continue
                    }
                    addEdgeChunks()
                    addInnerChunks()
                }

                currentChunk == CORNER_PLUS_EDGE_CHUNKS && trimChunks.size == CORNER_PLUS_EDGE_CHUNKS -> {
                    counter += 16
                    trimChunks = regionChunks
                    unloadTrimChunks()
                    reportTrimmedRegions++

                    val regionFile = worldData?.regionFile(currentRegion)
                    if (regionFile == null || !regionFile.delete()) {
                        notify("Error! Region file which is outside the border could not be deleted: ${regionFile?.name}")
                        wipeChunkPointers()
                    }

                    nextFile()
                    continue
                }

                currentChunk == REGION_CHUNKS -> {
                    counter += 32
                    unloadTrimChunks()
                    wipeChunkPointers()
                    nextFile()
                    continue
                }

                else -> {
                    val chunk = regionChunks[currentChunk]
                    val insideBorder = border?.isInside(
                        ChunkCoordinate.chunkToBlock(chunk.x) + 8.0,
                        ChunkCoordinate.chunkToBlock(chunk.z) + 8.0
                    ) ?: true
                    if (!insideBorder) trimChunks.add(chunk)

                    currentChunk++
                    counter++
                }
            }
        }

        reportTotal += counter
        readyToGo = true
    }

    private fun nextFile(): Boolean {
        reportTotal = currentRegion * PROGRESS_ESTIMATE_PER_REGION
        currentRegion++
        regionX = 0
        regionZ = 0
        currentChunk = 0
        regionChunks = mutableListOf()
        trimChunks = mutableListOf()

        val data = worldData ?: return false
        if (currentRegion >= data.regionFileCount) {
            isPaused = true
            readyToGo = false
            finish()
            return false
        }

        counter += 16

        val coordinate = data.regionFileCoordinates(currentRegion) ?: return false
        regionX = coordinate.x
        regionZ = coordinate.z
        return true
    }

    private fun addCornerChunks() {
        val baseX = ChunkCoordinate.regionToChunk(regionX)
        val baseZ = ChunkCoordinate.regionToChunk(regionZ)
        regionChunks.add(ChunkCoordinate(baseX, baseZ))
        regionChunks.add(ChunkCoordinate(baseX + 31, baseZ))
        regionChunks.add(ChunkCoordinate(baseX, baseZ + 31))
        regionChunks.add(ChunkCoordinate(baseX + 31, baseZ + 31))
    }

    private fun addEdgeChunks() {
        val baseX = ChunkCoordinate.regionToChunk(regionX)
        val baseZ = ChunkCoordinate.regionToChunk(regionZ)
        for (z in 1 until 31) {
            regionChunks.add(ChunkCoordinate(baseX, baseZ + z))
            regionChunks.add(ChunkCoordinate(baseX + 31, baseZ + z))
        }
        for (x in 1 until 31) {
            regionChunks.add(ChunkCoordinate(baseX + x, baseZ))
            regionChunks.add(ChunkCoordinate(baseX + x, baseZ + 31))
        }
        counter += 4
    }

    private fun addInnerChunks() {
        val baseX = ChunkCoordinate.regionToChunk(regionX)
        val baseZ = ChunkCoordinate.regionToChunk(regionZ)
        for (x in 1 until 31) for (z in 1 until 31) regionChunks.add(ChunkCoordinate(baseX + x, baseZ + z))
        counter += 32
    }

    private fun unloadTrimChunks() {
        trimChunks.forEach { chunk ->
            if (world?.isChunkLoaded(chunk.x, chunk.z) == true) world.unloadChunk(chunk.x, chunk.z, false)
        }
        counter += trimChunks.size
    }

    private fun wipeChunkPointers() {
        val regionFile = worldData?.regionFile(currentRegion) ?: return
        if (!regionFile.canWrite() && !regionFile.setWritable(true)) {
            notify("Error! region file is locked and can't be trimmed: ${regionFile.name}")
            return
        }

        val offsetX = ChunkCoordinate.regionToChunk(regionX)
        val offsetZ = ChunkCoordinate.regionToChunk(regionZ)
        var chunkCount = 0

        runCatching {
            RandomAccessFile(regionFile, "rwd").use { file ->
                trimChunks.forEach { chunk ->
                    if (worldData?.chunkExists(chunk.x, chunk.z) == true) {
                        file.seek(4L * ((chunk.x - offsetX) + (chunk.z - offsetZ) * 32))
                        file.writeInt(0)
                        chunkCount++
                    }
                }
            }
            reportTrimmedChunks += chunkCount
        }.onFailure { notify("Error! Could not modify region file to wipe individual chunks: ${regionFile.name}") }

        counter += trimChunks.size
    }

    fun finish() {
        reportTotal = reportTarget
        reportProgress()
        notify("task successfully completed!")
        stop()
    }

    fun cancel() = stop()

    private fun stop() {
        readyToGo = false
        isValid = false
        if (taskId != -1) server.scheduler.cancelTask(taskId)
        taskId = -1
    }

    fun pause(pause: Boolean = !isPaused) {
        isPaused = pause
        if (pause) reportProgress()
    }

    private fun reportProgress() {
        lastReport = PluginSettings.now()
        val percentage = reportTotal * 100.0 / reportTarget
        notify("$reportTrimmedRegions entire region(s) and $reportTrimmedChunks individual chunk(s) trimmed so far " +
                "(${PluginSettings.coordinateFormat.format(percentage)}% done)")
    }

    private fun notify(text: String) {
        val message = "${ChatColor.YELLOW}[Trim] $text"
        PluginSettings.log(message)
        notifyPlayer?.takeIf { it.isOnline }?.sendMessage(message)
    }
}
