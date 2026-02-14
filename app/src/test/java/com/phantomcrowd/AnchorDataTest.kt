package com.phantomcrowd

import com.phantomcrowd.data.AnchorData
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AnchorData model.
 * Verifies data class behaviour, defaults, and serialization contract.
 */
class AnchorDataTest {

    @Test
    fun `default anchor has expected values`() {
        val anchor = AnchorData()
        assertEquals("general", anchor.category)
        assertEquals("MEDIUM", anchor.severity)
        assertEquals(0, anchor.upvotes)
        assertEquals("", anchor.useCase)
        assertEquals("", anchor.useCaseCategory)
        assertEquals("PENDING", anchor.status)
        assertEquals("", anchor.messageText)
        assertEquals(0.0, anchor.latitude, 0.0)
        assertEquals(0.0, anchor.longitude, 0.0)
        assertTrue(anchor.id.isNotEmpty())
    }

    @Test
    fun `anchor with custom values retains them`() {
        val anchor = AnchorData(
            id = "custom-id",
            latitude = 12.97,
            longitude = 77.59,
            messageText = "Broken ramp at entrance",
            category = "facility",
            severity = "HIGH",
            useCase = "ACCESSIBILITY",
            useCaseCategory = "BROKEN_RAMP",
            upvotes = 5,
            status = "ACTIVE"
        )

        assertEquals("custom-id", anchor.id)
        assertEquals(12.97, anchor.latitude, 0.001)
        assertEquals(77.59, anchor.longitude, 0.001)
        assertEquals("Broken ramp at entrance", anchor.messageText)
        assertEquals("facility", anchor.category)
        assertEquals("HIGH", anchor.severity)
        assertEquals("ACCESSIBILITY", anchor.useCase)
        assertEquals("BROKEN_RAMP", anchor.useCaseCategory)
        assertEquals(5, anchor.upvotes)
        assertEquals("ACTIVE", anchor.status)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = AnchorData(
            id = "abc",
            messageText = "Original",
            upvotes = 3
        )
        val copy = original.copy(upvotes = 4)

        assertEquals("abc", copy.id)
        assertEquals("Original", copy.messageText)
        assertEquals(4, copy.upvotes)
    }

    @Test
    fun `anchor ids are unique by default`() {
        val a = AnchorData()
        val b = AnchorData()
        assertNotEquals(a.id, b.id)
    }
}
