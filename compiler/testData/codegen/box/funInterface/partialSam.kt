// IGNORE_BACKEND: WASM
// WASM_MUTE_REASON: SAM_CONVERSIONS
// !LANGUAGE: +NewInference +FunctionalInterfaceConversion +SamConversionPerArgument +SamConversionForKotlinFunctions
// WITH_RUNTIME

fun interface Fn<T, R> {
    fun run(s: String, i: Int, t: T): R
}

class J {
    fun runConversion(f1: Fn<String, Int>, f2: Fn<Int, String>): Int {
        return f1.run("Bar", 1, f2.run("Foo", 42, 239))
    }
}

fun box(): String {
    val j = J()
    var x = ""

    val fsi = object : Fn<String, Int> {
        override fun run(s: String, i: Int, t: String): Int {
            x += "$s$i$t "
            return i
        }
    }

    val fis = object : Fn<Int, String> {
        override fun run(s: String, i: Int, t: Int): String {
            x += "$s$i$t "
            return s
        }
    }

    val r1 = j.runConversion(fsi) { s, i, ti -> x += "L$s$i$ti "; "L$s"}
    val r2 = j.runConversion({ s, i, ts -> x += "L$s$i$ts"; i }, fis)

    if (r1 != 1) return "fail r1: $r1"
    if (r2 != 1) return "fail r2: $r2"

    if (x != "LFoo42239 Bar1LFoo Foo42239 LBar1Foo") return x

    return "OK"
}