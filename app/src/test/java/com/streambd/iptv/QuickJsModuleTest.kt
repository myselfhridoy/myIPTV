package com.streambd.iptv

import kotlinx.coroutines.runBlocking
import com.dokar.quickjs.quickJs
import org.junit.Test

class QuickJsModuleTest {
    @Test
    fun testModule() = runBlocking {
        quickJs {
            evaluate<Unit>("var module = { exports: {} }; var exports = module.exports;")
            evaluate<Unit>("module.exports = async function(x) { return x * 2; };")
            val script = """
                await (async function() {
                    try {
                        var parserFunc;
                        if (typeof module !== 'undefined' && typeof module.exports === 'function') {
                            parserFunc = module.exports;
                        }
                        if (parserFunc) {
                            var r = await parserFunc(10);
                            console.log("Result: " + r);
                        } else {
                            console.log("Not found");
                        }
                    } catch (e) {
                        console.log("Error: " + e);
                    }
                })();
            """.trimIndent()
            
            function("nativeLog") { args ->
                println("LOG: \${args[0]}")
            }
            evaluate<Unit>("globalThis.console = { log: function(m) { nativeLog(m); } };")
            evaluate<Unit>(script)
        }
    }
}
