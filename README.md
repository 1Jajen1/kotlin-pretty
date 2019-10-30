# kotlin-prettify
> A pretty printer library for kotlin

```groovy
repositories {
    maven { url 'https://dl.bintray.com/jannis/kotlin-pretty' }
}

dependencies {
    implementation "kotlin-pretty:kotlin-pretty:0.3"
}
```

Example:
```kotlin
data class Tree(val root: String, val branches: List<Tree>)

fun Tree.show(): Doc<Nothing> =
    (root.text<Nothing>() + ":".text() softLine branches
        .map { it.show().nest(2) }
        .encloseSep(lBracket(), line<Nothing>() + rBracket(), comma())
        .nest(root.length)).group()

val example = Tree(
    "H",
    listOf(
        Tree(
            "E",
            listOf(
                Tree(
                    "L",
                    listOf(
                        Tree("L", listOf(Tree("O", emptyList())))
                    )
                ),
                Tree("LL", emptyList())
            )
        ),
        Tree("World", emptyList())
    )
)

fun main() {
    example.show().pretty(maxWidth = 80, ribbonWidth = 0.4F).also(::println)
}
// prints =>
H: [ E: [ L: [ L: [ O: [  ] ] ]
        , LL: [  ]
        ]
   , World: [  ]
   ]
```