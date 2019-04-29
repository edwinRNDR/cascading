# Cascading

A set of Kotlin annotations and an annotation processor with which one can generate cascadable classes.

This is useful in cases where you want to work with optional values during ingestion but don't want to deal
with optionals in subsequent stages.

`Cascading` generates cascading and resolve functions from an annotated class.

For example, the `@Cascading` annotation can be used as follows.

```
@Cascading
class Foo {
    val a = 1
    val b = 2.0
}
```

This will generate the following code

```
class FooOut(val inFoo: FooIn, val a: Int, val b: Double)

class FooIn {
    var a: Int? = null
    var b: Double? = null

    fun cascade(onto: FooIn): FooIn {
        val result = FooIn()
        result.a = onto.a ?: a
        result.b = onto.b ?: b
        return result
    }

    fun resolve(defaults: Foo): FooOut = FooOut(this, a?:default.a, b?:default.b)
}
```

