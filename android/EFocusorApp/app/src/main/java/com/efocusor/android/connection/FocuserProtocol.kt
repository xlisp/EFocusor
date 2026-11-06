package com.efocusor.android.connection

import org.json.JSONObject

/**
 * Full device snapshot taken from the `I#` JSON status reply.
 * Field names match [statusJson] in ESP8266FocuserFirmware.ino.
 */
data class FocuserInfo(
    val firmware: Int = 0,
    val position: Long = 0,
    val target: Long = 0,
    val isMoving: Boolean = false,
    val hold: Boolean = false,
    val reversed: Boolean = false,
    val tempComp: Boolean = false,
    val stepsPerRev: Long = 8160,
    val maxSteps: Long = 816000,
    val maxSpeed: Int = 0,
    val acceleration: Int = 0,
    val tempCoeff: Double = 0.0,
    val lastTemp: Double = 0.0,
    val tempSensorPresent: Boolean = false,
    val homeOffsetSteps: Long = 0,
    val apSsid: String = "",
) {
    val hasTemp: Boolean get() = tempSensorPresent || lastTemp != 0.0
}

/** Lightweight position/moving pair taken from `G#` / `M` / `P` replies. */
data class FocuserStatus(val position: Long, val isMoving: Boolean)

object FocuserProtocol {

    /** `P 1234;M false` -> status. Returns null when the reply is not a status frame. */
    fun parseStatus(reply: String): FocuserStatus? {
        // Status frames look like "P <steps>;M <0|1|true|false>"
        if (!reply.startsWith("P ")) return null
        val semi = reply.indexOf(';')
        if (semi < 0) return null
        val pos = reply.substring(2, semi).trim().toLongOrNull() ?: return null
        val tail = reply.substring(semi + 1).trim()
        if (!tail.startsWith("M ")) return null
        val moving = when (tail.substring(2).trim()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> return null
        }
        return FocuserStatus(pos, moving)
    }

    /** `I#` reply body is a JSON document. */
    fun parseInfo(reply: String): FocuserInfo? {
        val jsonStart = reply.indexOf('{')
        if (jsonStart < 0) return null
        return try {
            val o = JSONObject(reply.substring(jsonStart))
            FocuserInfo(
                firmware = o.optInt("firmware", 0),
                position = o.optLong("positionSteps", 0),
                target = o.optLong("targetSteps", 0),
                isMoving = o.optBoolean("isMoving", false),
                hold = o.optBoolean("hold", false),
                reversed = o.optBoolean("reversed", false),
                tempComp = o.optBoolean("tempComp", false),
                stepsPerRev = o.optLong("stepsPerRev", 8160),
                maxSteps = o.optLong("maxSteps", 816000),
                maxSpeed = o.optInt("maxSpeed", 0),
                acceleration = o.optInt("acceleration", 0),
                tempCoeff = o.optDouble("tempCoeff", 0.0),
                lastTemp = o.optDouble("lastTemp", 0.0),
                tempSensorPresent = o.optBoolean("tempSensorPresent", false),
                homeOffsetSteps = o.optLong("homeOffsetSteps", 0),
                apSsid = o.optString("apSsid", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** True when the reply is an `ERR:...` error frame. */
    fun isErr(reply: String): Boolean = reply.startsWith("ERR:")

    fun errText(reply: String): String = reply.removePrefix("ERR:")
}
