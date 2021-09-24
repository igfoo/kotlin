// !DUMP_CFG

val x4: (String) -> Unit = run {
    return@run (<!REDUNDANT_LABEL_WARNING!>lambda@<!>{ foo: String ->
        bar(foo)
    })
}

fun bar(s: String) {}
fun <R> run(block: () -> R): R = block()
