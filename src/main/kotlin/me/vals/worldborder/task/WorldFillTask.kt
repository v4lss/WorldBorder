package me.vals.worldborder.task

import me.vals.worldborder.border.BorderRegion
import me.vals.worldborder.coordinate.ChunkCoordinate
import me.vals.worldborder.event.FillProgressMessageEvent
import me.vals.worldborder.event.FillTaskCompletedEvent
import me.vals.worldborder.io.RegionFileIndex
import me.vals.worldborder.registry.BorderRegistry
import me.vals.worldborder.settings.PluginSettings
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
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
 * @file WorldFillTask.kt
 * @created 14/8/2026
 */

class WorldFillTask(
    private val server: Server,
    private val notifyPlayer: Player?,
    worldName: String,
    private val fillDistance: Int,
    private val chunksPerRun: Int,
    val tickFrequency: Int,
    private val forceLoad: Boolean
) : Runnable {

    val world: World? = server.getWorld(worldName)

    private var border: BorderRegion? = null
    private var worldData: RegionFileIndex? = null

    var isValid = false
        private set
    var isPaused = false
        private set
    private var pausedForMemory = false

    var taskId = -1

    private var x = 0
    private var z = 0
    private var isZLeg = false
    private var isNegative = false
    private var length = -1
    private var current = 0
    private var insideBorder = true
    private var readyToGo = false

    private val storedChunks = ArrayDeque<ChunkCoordinate>()
    private val originalChunks = mutableSetOf<ChunkCoordinate>()
    private var lastChunk = ChunkCoordinate(0, 0)

    var refX = 0; private set
    var refZ = 0; private set
    var refLength = -1; private set
    var refTotal = 0; private set

    private var lastLegX = 0
    private var lastLegZ = 0
    private var lastLegTotal = 0

    private var lastReport = PluginSettings.now()
    private var lastAutosave = PluginSettings.now()
    private var reportTarget = 0
    private var reportTotal = 0
    private var reportNum = 0

    init {
        val currentWorld = world
        val configuredBorder = BorderRegistry[worldName]

        when {
            currentWorld == null ->
                notify(if (worldName.isEmpty()) "You must specify a world!" else "World \"$worldName\" not found!")

            configuredBorder == null ->
                notify("No border found for world \"$worldName\"!")

            else -> {
                val data = RegionFileIndex.create(currentWorld, notifyPlayer)
                if (data == null) {
                    stop()
                } else {
                    worldData = data
                    val fillBorder = configuredBorder.copy()
                    fillBorder.radiusX += fillDistance
                    fillBorder.radiusZ += fillDistance
                    border = fillBorder

                    x = ChunkCoordinate.blockToChunk(fillBorder.x.toInt())
                    z = ChunkCoordinate.blockToChunk(fillBorder.z.toInt())

                    val chunkWidthX = Math.ceil((fillBorder.radiusX + 16) * 2.0 / 16).toInt()
                    val chunkWidthZ = Math.ceil((fillBorder.radiusZ + 16) * 2.0 / 16).toInt()
                    val biggerWidth = maxOf(chunkWidthX, chunkWidthZ)
                    reportTarget = biggerWidth * biggerWidth + biggerWidth + 1

                    currentWorld.loadedChunks.forEach { originalChunks.add(ChunkCoordinate(it.x, it.z)) }
                    readyToGo = true
                    isValid = true
                }
            }
        }
        if (!isValid) stop()
    }

    override fun run() {
        if (pausedForMemory) {
            if (PluginSettings.isMemoryLow()) return
            pausedForMemory = false
            readyToGo = true
            notify("Available memory is sufficient, automatically continuing.")
        }

        if (!isValid || !readyToGo || isPaused) return

        readyToGo = false
        val loopStart = PluginSettings.now()
        val currentWorld = world ?: return
        val currentBorder = border ?: return
        val data = worldData ?: return

        for (loop in 0 until chunksPerRun) {
            if (isPaused || pausedForMemory) return

            if (PluginSettings.isMemoryLow()) {
                pausedForMemory = true
                notify("Available memory is low, pausing to free up resources.")
                return
            }

            val now = PluginSettings.now()
            if (now > lastReport + 5000) reportProgress()
            if (now > loopStart + 45) {
                readyToGo = true
                return
            }

            while (!currentBorder.isInside(
                    ChunkCoordinate.chunkToBlock(x) + 8.0,
                    ChunkCoordinate.chunkToBlock(z) + 8.0
                )
            ) {
                if (!advance()) return
            }

            insideBorder = true

            if (!forceLoad) {
                while (data.isChunkFullyGenerated(x, z)) {
                    if (!advance()) return
                }
            }

            currentWorld.loadChunk(x, z, true)
            data.markChunkExists(x, z)

            val popX = if (isZLeg) x else x + if (isNegative) -1 else 1
            val popZ = if (isZLeg) z + if (isNegative) 1 else -1 else z

            currentWorld.loadChunk(popX, popZ, false)

            if (lastChunk !in storedChunks && lastChunk !in originalChunks) {
                currentWorld.loadChunk(lastChunk.x, lastChunk.z, false)
                storedChunks.addLast(lastChunk)
            }

            storedChunks.addLast(ChunkCoordinate(popX, popZ))
            storedChunks.addLast(ChunkCoordinate(x, z))

            while (storedChunks.size > 8) {
                val chunk = storedChunks.removeFirst()
                if (chunk !in originalChunks) currentWorld.unloadChunkRequest(chunk.x, chunk.z)
            }

            if (!advance()) return
        }

        readyToGo = true
    }

    private fun advance(): Boolean {
        if (isPaused || pausedForMemory) return false

        reportNum++

        if (!isNegative && current == 0 && length > 3) {
            if (!isZLeg) {
                lastLegX = x
                lastLegZ = z
                lastLegTotal = reportTotal + reportNum
            } else {
                refX = lastLegX
                refZ = lastLegZ
                refTotal = lastLegTotal
                refLength = length - 1
            }
        }

        if (current < length) {
            current++
        } else {
            current = 0
            isZLeg = !isZLeg
            if (isZLeg) {
                isNegative = !isNegative
                length++
            }
        }

        lastChunk = ChunkCoordinate(x, z)

        if (isZLeg) z += if (isNegative) -1 else 1 else x += if (isNegative) -1 else 1

        if (isZLeg && isNegative && current == 0) {
            if (!insideBorder) {
                finish()
                return false
            }
            insideBorder = false
        }

        return true
    }

    fun finish() {
        isPaused = true
        reportProgress()
        world?.save()
        notify("task successfully completed!")
        val finishedWorld = world?.name.orEmpty()
        stop()
        border?.let { Bukkit.getPluginManager().callEvent(FillTaskCompletedEvent(finishedWorld, it)) }
    }

    fun cancel() = stop()

    private fun stop() {
        if (!isValid) return
        readyToGo = false
        isValid = false
        if (taskId != -1) server.scheduler.cancelTask(taskId)
        taskId = -1

        while (storedChunks.isNotEmpty()) {
            val chunk = storedChunks.removeFirst()
            if (chunk !in originalChunks) world?.unloadChunkRequest(chunk.x, chunk.z)
        }
    }

    fun pause(pause: Boolean = !isPaused) {
        isPaused = if (pausedForMemory && !pause) {
            pausedForMemory = false
            false
        } else pause
        if (isPaused) reportProgress()
    }

    private fun reportProgress() {
        lastReport = PluginSettings.now()
        val percentage = ((reportTotal + reportNum) * 100.0 / reportTarget).coerceIn(0.0, 100.0)
        notify("$reportNum more chunks processed (${reportTotal + reportNum} total, " + "~${PluginSettings.coordinateFormat.format(percentage)}%)")

        reportTotal += reportNum
        reportNum = 0

        if (PluginSettings.fillAutosaveFrequency > 0 &&
            lastAutosave + PluginSettings.fillAutosaveFrequency * 1000L < lastReport
        ) {
            lastAutosave = lastReport
            notify("Saving the world to disk, just to be on the safe side.")
            world?.save()
        }
    }

    private fun notify(text: String) {
        val message = "${ChatColor.YELLOW}[Fill] $text"
        PluginSettings.log(message)
        notifyPlayer?.takeIf { it.isOnline }?.sendMessage(message)
        Bukkit.getPluginManager().callEvent(FillProgressMessageEvent(message))

        val availableMB = PluginSettings.availableMemoryMegabytes()
        if (availableMB < PluginSettings.fillMemoryTolerance) {
            pausedForMemory = true
            val memoryMessage = "${ChatColor.RED}[Fill] Available memory is very low (${availableMB}MB), task is pausing. " +
                    "It will automatically continue when memory is freed up."
            PluginSettings.log(memoryMessage)
            notifyPlayer?.takeIf { it.isOnline }?.sendMessage(memoryMessage)
            System.gc()
        } else if (availableMB < PluginSettings.fillMemoryTolerance * 1.5) {
            val warnMessage = "${ChatColor.YELLOW}[Fill] Warning: available memory is getting low (${availableMB}MB)."
            PluginSettings.log(warnMessage)
            notifyPlayer?.takeIf { it.isOnline }?.sendMessage(warnMessage)
        }
    }

}
