@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun testLambdaLabel() = <!REDUNDANT_LABEL_WARNING!>l@<!> { 42 }

fun testAnonymousFunctionLabel() = <!REDUNDANT_LABEL_WARNING!>l@<!> fun() {}

fun testAnnotatedLambdaLabel() = <!REDUNDANT_LABEL_WARNING!>lambda@<!> @Ann {}

fun testParenthesizedLambdaLabel() = <!REDUNDANT_LABEL_WARNING!>lambda@<!> ( {} )

fun testLabelBoundToInvokeOperatorExpression() = <!REDUNDANT_LABEL_WARNING!>l@<!> { 42 }()

fun testLabelBoundToLambda() = (<!REDUNDANT_LABEL_WARNING!>l@<!> { 42 })()

fun testWhileLoopLabel() {
    <!REDUNDANT_LABEL_WARNING!>L@<!> while (true) {}
}

fun testDoWhileLoopLabel() {
    <!REDUNDANT_LABEL_WARNING!>L@<!> do {} while (true)
}

fun testForLoopLabel(xs: List<Any>) {
    <!REDUNDANT_LABEL_WARNING!>L@<!> for (x in xs) {}
}

fun testValLabel() {
    <!REDUNDANT_LABEL_WARNING!>L@<!> val fn = {}
    fn()
}

fun testHighOrderFunctionCallLabel() {
    <!REDUNDANT_LABEL_WARNING!>L@<!> run {}
}

fun testAnonymousObjectLabel() =
    <!REDUNDANT_LABEL_WARNING!>L@<!> object {}
