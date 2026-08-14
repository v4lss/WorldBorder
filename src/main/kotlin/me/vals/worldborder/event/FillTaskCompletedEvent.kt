package me.vals.worldborder.event

import me.vals.worldborder.border.BorderRegion
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file FillTaskCompletedEvent.kt
 * @created 14/8/2026
 */

class FillTaskCompletedEvent(val worldName: String, val border: BorderRegion) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
