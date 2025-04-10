package com.august.viiin

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class BarcodeInfoRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: BarcodeInfoRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = BarcodeInfoRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchWineInfo returns parsed wine info when response is valid`() = runBlocking {
        // Given
        val jsonResponse = """
            {
              "items": [
                {
                  "title": "Test Wine",
                  "brand": "Fancy Winery"
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // Patch base URL (you’d need to refactor WineInfoRepository to allow custom base URL)
        val testCode = "123456789012"
        val result = repository.fetchBarcodeInfo(testCode)

        // Then
        assertEquals("Test Wine (Fancy Winery)", result)
    }
}