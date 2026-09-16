package com.streambd.iptv

import kotlinx.coroutines.runBlocking
import com.dokar.quickjs.quickJs
import org.junit.Test

class QuickJsAwaitTest {
    @Test
    fun testAwait() = runBlocking {
        try {
            quickJs {
                evaluate<Unit>("""
                    await (async function() {
                        var x = 1;
                    })();
                """.trimIndent())
            }
            println("SUCCESS")
        } catch (e: Exception) {
            println("ERROR: \${e.message}")
        }
    }
}
