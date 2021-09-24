// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -UNUSED_VARIABLE -UNUSED_VALUE -VARIABLE_WITH_REDUNDANT_INITIALIZER
// SKIP_TXT

// TESTCASE NUMBER: 1
fun case_1() {
    var x: String? = null

    <!REDUNDANT_LABEL_WARNING!>outer@<!> while (x != null) {
        <!REDUNDANT_LABEL_WARNING!>inner@<!> do {
            x = null
        } while (<!SENSELESS_COMPARISON!>x == null<!>)
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String? & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String? & kotlin.Nothing")!>x<!>.length
    }
}
