// !DIAGNOSTICS: -UNUSED_PARAMETER

annotation class ann
val bas = fun ()

fun bar(a: Any) = fun ()

fun outer() {
    bar(fun ())
    bar(<!REDUNDANT_LABEL_WARNING!>l@<!> fun ())
    bar(@ann fun ())
}
