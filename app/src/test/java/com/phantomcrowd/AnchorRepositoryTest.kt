package com.phantomcrowd

import com.phantomcrowd.data.AnchorData
import com.phantomcrowd.data.AnchorRepository
import com.phantomcrowd.data.FirebaseAnchorManager
import com.phantomcrowd.data.LocalStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for AnchorRepository.
 * Validates cloud-first fallback logic and data flow.
 */
class AnchorRepositoryTest {

    private lateinit var localStorageManager: LocalStorageManager
    private lateinit var firebaseAnchorManager: FirebaseAnchorManager
    private lateinit var repository: AnchorRepository

    private val testAnchor = AnchorData(
        id = "test-1",
        latitude = 12.97,
        longitude = 77.59,
        messageText = "Streetlight out near Gate 3",
        category = "safety",
        severity = "HIGH",
        useCase = "WOMENS_SAFETY"
    )

    @Before
    fun setup() {
        localStorageManager = mock()
        firebaseAnchorManager = mock()
        repository = AnchorRepository(localStorageManager, firebaseAnchorManager)
    }

    @Test
    fun `createAnchor saves to local storage and returns anchor`() = runTest {
        val anchor = repository.createAnchor(
            latitude = 12.97,
            longitude = 77.59,
            altitude = 0.0,
            message = "Test issue",
            category = "safety"
        )

        assertNotNull(anchor)
        assertEquals("Test issue", anchor.messageText)
        assertEquals("safety", anchor.category)
        assertEquals(12.97, anchor.latitude, 0.001)
        assertEquals(77.59, anchor.longitude, 0.001)
        verify(localStorageManager).saveAnchor(any())
    }

    @Test
    fun `getNearbyAnchors returns cloud data when available`() = runTest {
        val cloudAnchors = listOf(testAnchor)
        whenever(firebaseAnchorManager.getIssuesNearLocation(any(), any(), any()))
            .thenReturn(cloudAnchors)

        val result = repository.getNearbyAnchors(12.97, 77.59, 50.0)

        assertEquals(1, result.size)
        assertEquals("test-1", result[0].id)
    }

    @Test
    fun `getNearbyAnchors falls back to local when cloud fails`() = runTest {
        whenever(firebaseAnchorManager.getIssuesNearLocation(any(), any(), any()))
            .thenThrow(RuntimeException("Network error"))
        whenever(localStorageManager.loadAnchors())
            .thenReturn(listOf(testAnchor))

        val result = repository.getNearbyAnchors(12.97, 77.59, 50000.0)

        // Should get local anchors when cloud fails
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `getAllAnchors returns cloud data first`() = runTest {
        val cloudAnchors = listOf(testAnchor)
        whenever(firebaseAnchorManager.fetchAllIssues())
            .thenReturn(cloudAnchors)

        val result = repository.getAllAnchors()

        assertEquals(1, result.size)
        assertEquals("Streetlight out near Gate 3", result[0].messageText)
    }

    @Test
    fun `getAllAnchors falls back to local when cloud is empty`() = runTest {
        whenever(firebaseAnchorManager.fetchAllIssues()).thenReturn(emptyList())
        whenever(localStorageManager.loadAnchors()).thenReturn(listOf(testAnchor))

        val result = repository.getAllAnchors()

        assertEquals(1, result.size)
        verify(localStorageManager).loadAnchors()
    }

    @Test
    fun `repository works without firebase manager`() = runTest {
        val repoWithoutFirebase = AnchorRepository(localStorageManager, null)
        whenever(localStorageManager.loadAnchors()).thenReturn(listOf(testAnchor))

        val result = repoWithoutFirebase.getAllAnchors()

        assertEquals(1, result.size)
    }

    @Test
    fun `anchor data has correct defaults`() {
        val anchor = AnchorData()
        assertEquals("general", anchor.category)
        assertEquals("MEDIUM", anchor.severity)
        assertEquals(0, anchor.upvotes)
        assertEquals("", anchor.useCase)
        assertTrue(anchor.id.isNotEmpty())
    }
}
