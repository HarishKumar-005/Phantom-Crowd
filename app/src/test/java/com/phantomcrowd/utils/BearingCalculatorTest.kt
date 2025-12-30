package com.phantomcrowd.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BearingCalculatorTest {

    @Test
    fun testCalculateBearing_North() {
        val userLat = 0.0
        val userLon = 0.0
        val targetLat = 1.0
        val targetLon = 0.0

        val bearing = BearingCalculator.calculateBearing(userLat, userLon, targetLat, targetLon)
        assertEquals(0.0, bearing, 0.1)
    }

    @Test
    fun testCalculateBearing_East() {
        val userLat = 0.0
        val userLon = 0.0
        val targetLat = 0.0
        val targetLon = 1.0

        val bearing = BearingCalculator.calculateBearing(userLat, userLon, targetLat, targetLon)
        assertEquals(90.0, bearing, 0.1)
    }

    @Test
    fun testCalculateBearing_South() {
        val userLat = 0.0
        val userLon = 0.0
        val targetLat = -1.0
        val targetLon = 0.0

        val bearing = BearingCalculator.calculateBearing(userLat, userLon, targetLat, targetLon)
        assertEquals(180.0, bearing, 0.1)
    }

    @Test
    fun testCalculateBearing_West() {
        val userLat = 0.0
        val userLon = 0.0
        val targetLat = 0.0
        val targetLon = -1.0

        val bearing = BearingCalculator.calculateBearing(userLat, userLon, targetLat, targetLon)
        assertEquals(270.0, bearing, 0.1)
    }

    @Test
    fun testBearingToCardinal() {
        assertEquals("North", BearingCalculator.bearingToCardinal(0.0))
        assertEquals("North", BearingCalculator.bearingToCardinal(360.0))
        assertEquals("North", BearingCalculator.bearingToCardinal(10.0))
        assertEquals("North", BearingCalculator.bearingToCardinal(350.0))

        assertEquals("East", BearingCalculator.bearingToCardinal(90.0))
        assertEquals("South", BearingCalculator.bearingToCardinal(180.0))
        assertEquals("West", BearingCalculator.bearingToCardinal(270.0))

        assertEquals("North-East", BearingCalculator.bearingToCardinal(45.0))
        assertEquals("South-East", BearingCalculator.bearingToCardinal(135.0))
        assertEquals("South-West", BearingCalculator.bearingToCardinal(225.0))
        assertEquals("North-West", BearingCalculator.bearingToCardinal(315.0))
    }

    @Test
    fun testEdgeCases() {
        // Wraparound check
        assertEquals("North", BearingCalculator.bearingToCardinal(360.0))
        assertEquals("North", BearingCalculator.bearingToCardinal(720.0))
        assertEquals("North", BearingCalculator.bearingToCardinal(-360.0)) // Although implementation uses (bearing + 360) % 360, input might be negative

        // Wait, calculateBearing returns 0-360. But bearingToCardinal takes Double.
        // Let's check bearingToCardinal implementation for negative values.
        // (bearing + 360) % 360 works for -360 < bearing < 0. For < -360 it might fail if not handled?
        // -720 + 360 = -360. -360 % 360 = 0.
        // -370 + 360 = -10. -10 % 360 = -10.
        // The implementation: val normalizedBearing = (bearing + 360) % 360
        // If bearing is -10: (-10 + 360) % 360 = 350. Correct.
        // If bearing is -370: (-370 + 360) = -10. -10 % 360 = -10 (in Kotlin/Java % preserves sign of dividend).
        // So -370 might result in -10.
        // Let's test this hypothesis.
        // If normalizedBearing is -10.
        // when:
        // -10 < 22.5 is true. -> "North". Correct.

        // If bearing is 370: (370+360)%360 = 730%360 = 10. Correct.
    }
}
