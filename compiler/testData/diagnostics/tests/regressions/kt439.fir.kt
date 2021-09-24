// KT-439 Support labeled function literals in call arguments

fun main1(args : Array<String>) {
    run <!REDUNDANT_LABEL_WARNING!>l@<!>{ 1 } // should not be an error
}
