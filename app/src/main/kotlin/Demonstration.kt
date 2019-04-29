package org.openrndr.cascading.main

import org.openrndr.cascading.annotations.Cascading

@Cascading
class Cascadable {
    var something:Int = 0
    var somethingElse:Int = 0
    var doStuff:String = "here I do a thing"
    var optionalStuff:String? = null
    var bla = arrayOf(0)
    var bla2 = arrayOf(0.0)
    var bla3 = arrayOf(0.0f)
    var bla4 = arrayOf("blaa")
    var bla5 : Array<Cascadable>? = null
    var bla6: Cascadable? = null
}

fun main(args: Array<String>) {
    val bla = CascadableIn()
    val out = bla.resolve(Cascadable())
    println(out.doStuff)
    println(out.bla)
}
