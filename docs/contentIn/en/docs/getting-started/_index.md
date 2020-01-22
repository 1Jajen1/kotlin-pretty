---
title: "Getting started"
date: 2020-01-22
weight: 10
type: "docs"
description: >
    A step-by-step tutorial on how to install and use kotlin-pretty.
---

## Setup
----

### Gradle

Add the following to your `build.gradle`.

```groovy
repositories {
    maven { url "https://dl.bintray.com/jannis/kotlin-pretty" }
}

dependencies {
    implementation "kotlin-pretty:kotlin-pretty:0.5.1"
}
```

## Rendering data

In this section we will go over creating a very basic rich document for a datatype and how to render it. This will not be the prettiest or best layout possible, just a simple example to get started.

### Creating a document from a datatype

Suppose we have an error-report datatype:
```kotlin:ank:silent
data class ErrReport(
    // what happened
    val errorMsg: String,
    // in which file it happened
    val file: String,
    // at what line, or which lines did it happen
    val lineSpan: Pair<Int, Int>
)
```

Now we want to render this to a responsive document which adapts to the maximum width that we give it. In order to do so we will define a method `doc`.

```kotlin:ank:silent
import pretty.*
import pretty.symbols.colon

fun ErrReport.doc(): Doc<Nothing> = "Error in file".text() spaced
    file.text() spaced
    "at".text() spaced
    lineSpan.prettyLineSpan() + colon() + hardLine() +
    errorMsg.reflow()

fun Pair<Int, Int>.prettyLineSpan(): Doc<Nothing> =
    if (first == second) "line $first".text()
    else if (first > second) "line $second to line $first".text()
    else "line $first to line $second".text()
```

There is a lot going on here, especially with all those infix functions, so let us go over it one by one:

The type signature `ErrReport.doc()` hints that we return `Doc<Nothing>`, this means the resulting document is not annotated. The type parameter refers to annotations, you can learn more about that [here]({{< ref "/docs/annotations" >}}). For now that is not important as we will not be using annotations yet.

Next up is `file.text()`: This calls the extension function `String.text(): Doc<Nothing>` which converts a string to an un-annotated document.
> This method has an invariant that it will never be used on a string with a newline. If it is, it will break the layout algorithm and will not produce the best result. Use `String.doc()` instead which properly handles newlines, but comes at a performance cost.

The resulting document is then combined with one of the most basic combinators of kotlin-pretty called `spaced`. As the name suggests it takes two documents and inserts one space between them.

Because rendering a pair of integers needs some additional logic we define another extension function here to help deal with the special cases where the numbers two are in wrong order or are equal.

The next interesting functions are `colon` and `hardline`. `colon` refers to the string `:` and `hardline` to a newline which will never be flattened. TODO add link to learn more about flattening here.

Lastly we have the function `String.reflow()` which replaces each space with a `softLine` which the layout algorithm can either keep as a space or replace with a newline.

This completes our function which turns an error report into a rich, structured document.

### Rendering the document

Now that we have a way to get a rich document, we need a way to actually render it and for the sake of simplicity we are simply going to render it to a string.

#### Generating a layout from a document

The first step is to turn our document `Doc` into a simplified `SimpleDoc` which represents a stream of tokens that encode the final structure. This is done using the built-in method `Doc<A>.layoutPretty(pw: PageWidth): SimpleDoc<A>`. The argument `PageWidth` is a simple datatype that encodes the width constraints for rendering:

```kotlin
sealed class PageWidth {
    // no width constraints
    object Unbounded : PageWidth()
    data class Available(
        // The max width which the algorithm will try and not exceed
        val maxWidth: Int,
        // A fraction which will be applied to the current column width to tweak the max-width setting.
        //  The layout algorithm will then try to not exceed columns of size maxWidth * ribbonFrac
        val ribbonFrac: Float
    ): PageWidth()
}
```

#### Rendering a layout into a string

Now that we have a `SimpleDoc` layout all that is left to do is rendering it. To render any `SimpleDoc<A>` as a string, kotlin-pretty defines an extension method `SimpleDoc<A>.renderString(): String`. 

```kotlin:ank
val report = ErrReport("Failed to do some very important task", "someFile.txt", 4 to 10)

report
    .doc()
    .layoutPretty(PageWidth.Available(80, 0.5F))
    .renderString()
```
And with a much smaller width:
```kotlin:ank
report.doc()
    .layoutPretty(PageWidth.Available(20, 0.5F))
    .renderString()
```

With the larger max width the input stays on two lines and nicely fits the required max-width. However it fails to fit the ribbon-width (`80 * 0.5` in that case), but because there are no alternatives with more newlines the algorithm cannot choose a better layout. With the second much more constrained render this becomes even more obvious. The first line does not fit in 20 characters, but because there is no better layout it has to render it like that.

To make a better layout we should have presented the layout algorithm with more options by using something like `softline` which can be either a space or a newline. You can learn more about those other options in the other sections of the docs. 

## Whats next

- [Api reference]({{< ref "/docs/api-reference" >}}) A documented list of most, if not all methods provided by kotlin-pretty.

TODO add sections here:
- How to guide
- Api reference
- Inner workings + limits