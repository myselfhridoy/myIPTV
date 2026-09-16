package com.streambd.iptv

import kotlinx.coroutines.runBlocking
import com.dokar.quickjs.quickJs

fun main() = runBlocking {
    val jsCode = """
        module.exports = async function(type, tmdbId, season, episode) {
            console.log("Fetching: " + tmdbId);
            return [{url: "foo", quality: "HD", provider: "11plus"}];
        };
    """.trimIndent()
    
    val scriptWrapper = """
        await (async function() {
            try {
                var parserFunc;
                if (typeof module !== 'undefined' && typeof module.exports === 'function') {
                    parserFunc = module.exports;
                }
                
                if (typeof parserFunc === 'function') {
                    var result = parserFunc("movie", "872906", null, null);
                    if (result instanceof Promise) {
                        result = await result;
                    }
                    nativeSend(JSON.stringify(result || []));
                } else {
                    nativeError("Parser is not a function");
                    nativeSend("[]");
                }
            } catch (e) {
                nativeError(e.toString());
                nativeSend("[]");
            }
        })();
    """.trimIndent()

    var scriptResult: String? = null
    try {
        quickJs {
            function("nativeSend") { args ->
                scriptResult = args[0] as String
            }
            function("nativeError") { args ->
                println("ERROR: \${args.joinToString(" ")}")
            }
            evaluate<Unit>("globalThis.console = { log: function(m) { println('LOG: ' + m); } };")
            evaluate<Unit>("var module = { exports: {} }; var exports = module.exports;")
            evaluate<Unit>(jsCode)
            evaluate<Unit>(scriptWrapper)
        }
    } catch (e: Exception) {
        println("EXCEPTION: \${e.message}")
    }
    
    println("FINAL RESULT: \$scriptResult")
}
