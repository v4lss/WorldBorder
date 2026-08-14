package me.vals.worldborder.border

/**
 * Proprietary and Confidential
 * Copyright © 2026 Vals. All rights reserved.
 * <p>
 * Unauthorized use, reproduction, or distribution of this software
 * or any portion of it may result in severe civil and criminal penalties.
 *
 * @author Vals
 * @project Worldborder
 * @file BorderShape.kt
 * @created 14/8/2026
 */

enum class BorderShape(val label: String) {
    ROUND("elliptic/round"),
    SQUARE("rectangular/square");

    companion object {
        fun fromKeyword(keyword: String): BorderShape? = when (keyword.lowercase()) {
            "rectangular", "square" -> SQUARE
            "elliptic", "round" -> ROUND
            else -> null
        }
    }
}
