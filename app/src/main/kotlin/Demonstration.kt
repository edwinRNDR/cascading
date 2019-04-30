package org.openrndr.cascading.main

import org.openrndr.cascading.annotations.Cascading

@Cascading
class Cascadable {
    var test00: Int = 0
    var test01: Int = 0
    var test02: String = "here I do a thing"
    var test03: String? = null
    var test04 = arrayOf(0)
    var test05 = arrayOf(0.0)
    var test06 = arrayOf(0.0f)
    var test07 = arrayOf("blaa")
    var test08: Array<Cascadable>? = null
    var test09: Cascadable? = null
    var test10: Double? = null
}

fun main(args: Array<String>) {
    val bla = CascadableIn()
    val out = bla.resolve(Cascadable())
    println(out.test00)
    println(out.test01)
}
