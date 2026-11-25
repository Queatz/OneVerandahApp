package apap.oneverandah.chat

import apap.oneverandah.images.storage.GeneratedImage
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class ChatSystemTest {
    @Test
    fun `mask generation failure stores the generated talk portrait`() = runBlocking {
        val generated = GeneratedImage(byteArrayOf(1))
        var stored: GeneratedImage? = null
        var compositingCalled = false

        val url = ChatSystem.generateAndStoreTalkPortrait(
            generatePortrait = { generated },
            generateMask = { throw IllegalStateException("mask generation failed") },
            compositeMask = { _, _ ->
                compositingCalled = true
                error("compositing should not run")
            },
            store = { stored = it; "portrait-url" },
        )

        assertEquals("portrait-url", url)
        assertEquals(generated, stored)
        assertFalse(compositingCalled)
    }

    @Test
    fun `mask compositing failure stores the generated talk portrait`() = runBlocking {
        val generated = GeneratedImage(byteArrayOf(1))
        var stored: GeneratedImage? = null

        val url = ChatSystem.generateAndStoreTalkPortrait(
            generatePortrait = { generated },
            generateMask = { GeneratedImage(byteArrayOf(2)) },
            compositeMask = { _, _ -> throw IllegalStateException("mask compositing failed") },
            store = { stored = it; "portrait-url" },
        )

        assertEquals("portrait-url", url)
        assertEquals(generated, stored)
    }

    @Test
    fun `mask cancellation stores the generated talk portrait`() = runBlocking {
        val generated = GeneratedImage(byteArrayOf(1))
        var stored: GeneratedImage? = null

        val url = ChatSystem.generateAndStoreTalkPortrait(
            generatePortrait = { generated },
            generateMask = { throw CancellationException("mask generation timed out") },
            compositeMask = { _, _ -> error("compositing should not run") },
            store = { stored = it; "portrait-url" },
        )

        assertEquals("portrait-url", url)
        assertEquals(generated, stored)
    }

    @Test
    fun `portrait generation failure does not store an image`() = runBlocking {
        var stored: GeneratedImage? = null

        assertFailsWith<IllegalStateException> {
            ChatSystem.generateAndStoreTalkPortrait(
                generatePortrait = { throw IllegalStateException("portrait generation failed") },
                generateMask = { error("mask generation should not run") },
                compositeMask = { _, _ -> error("compositing should not run") },
                store = { stored = it; "portrait-url" },
            )
        }

        assertNull(stored)
    }
}