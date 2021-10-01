// TARGET_BACKEND: JVM
// WITH_RUNTIME

class V<T : Number>(y: T) {
    @JvmField
    var x: T = y
}

fun check(a: V<Float>, b: V<Float>): Boolean =
    a.x != b.x

fun box(): String =
    if (check(V(1.0f), V(2.0f))) "OK" else "Fail"
