# kotlin-prettify
> A pretty printer library for kotlin

```groovy
repositories {
    maven { url 'https://dl.bintray.com/jannis/kotlin-pretty' }
}

dependencies {
    implementation "kotlin-pretty:kotlin-pretty:0.1"
}
```

Example:
```kotlin
data class Tree(val root: String, val branches: List<Tree>)

fun Tree.show(): Doc =
    (root.text() + ":".text() space branches.showBranches().nest(root.length))

fun List<Tree>.showBranches(): Doc = when {
    isEmpty() -> "[]".text()
    else -> showTrees().bracket("[", "]").nest(1)
}

fun List<Tree>.showTrees(): Doc = when (size) {
    1 -> first().show()
    else -> (first() to drop(1)).let { (x, xs) ->
        x.show() + ",".text() newline xs.showTrees()
    }
}

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
    example.show().pretty(maxWidth = 30).also(::println)
}
// prints =>
H: [
    E: [
        L: [ L: [ O: [] ] ],
        LL: []
      ],
    World: []
  ]
```