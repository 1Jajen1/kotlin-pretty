---
title: "Documentation"
linkTitle: "Documentation"
date: 2020-01-14
weight: 20
---

Kotlin-pretty is an extensible and easy to use pretty-printing library for kotlin. It can be used to render dynamic text in a size constrained environment (e.g. a terminal, message boxes, etc) in a human readable and (hopefully) pretty way.

## Motivation
---

Usuall when outputting text (for example output from tests or debugging (usually `toString` output) or rendering information for users) we have two scenarios:
- We have static text
- We have dynamic text

With static text, we could in theory define an optimal layout by hand, however this is tedious for longer texts and is not maintainable: If we change the beginning just slightly the whole layout may change!

With dynamic text, we can only guess where linebreaks should be. This is rarely optimal and usually leads to either excessive newlines or none. Both makes output difficult to read.

A prettyprinter can help with both of those situations, by taking away the size concerns and providing you with the ability to define dynamic layouts that adapt to a given size. Given that at some point, when we eventually render something, we should have some idea of our output size, we can take this dynamic layout and render it to a fixed optimal layout for our preferred size.

The use cases span from terminal output, to static size constraints on user interfaces, to code layout, to rendering and presenting users data in readable fashion, etc. Basically everwhere where dynamic text meets a possible size constraint.

## How prettyprinters solve these problems
---

In kotlin-pretty we provide a datatype `Doc` which represents a rich text document with some sort of layout. There is a large set of combinators to help modify the content and the resutling layout of a document. In the end this document can be layed out by simple algorithm's, provided by kotlin-pretty, and rendered as `String`, directly to output handles or however you may want as there are easy ways to change and influence the rendering method.

For example, to output a list of lists (the inner list contains words/parts of sentences and one element in the outer list is one individual sentence), one can just write:
```kotlin:ank
import pretty.*

val list = listOf(
    listOf("Hello", "World"),
    listOf("This", "is", "layed out flat", "if", "the", "size", "allows", "it"),
    listOf("Next\nline!")
)
val doc = list.map { xs ->
    xs.map { str -> str.doc() /* Convert strings (which may contain newlines) to docs */ }
        .fillSep() // Fill as much space as possible horizontally, but introduce linebreaks when necessary
        .nest(2) // Nest newlines by 2
}.vSep() // seperate every document vertically
doc.pretty(maxWidth = 80, ribbonWidth = 1F) // Default max width, but no special ribbon width
```
```kotlin:ank
doc.pretty(maxWidth = 20, ribbonWidth = 1F)
```

Kotlin-pretty also provides lots of inbuilt helpers to show basic data:
```kotlin:ank:silent
val nrList = listOf(1,2,3,4,5,1000)
val pairs = listOf("Hello" to 1, "World" to 2, "!" to 300)
val nrDoc = nrList.map { it.doc() }.list() // enclose between brackets and add leading comma
val pairDoc = pairs.map { (t, n) ->
    // text is an alternate constructor to doc that should only be used if the String has no newlines
    //  it offers better performance for larger texts
    t.text().fill(5) spaced "->".text() spaced n.doc()
}.tupled() // enclose between parentheses and add leading comma
```
```kotlin:ank
nrDoc.pretty(maxWidth = 80, ribbonWidth = 0.4F)
```
```kotlin:ank
nrDoc.pretty(maxWidth = 20, ribbonWidth = 0.4F)
```
```kotlin:ank
pairDoc.pretty(maxWidth = 80, ribbonWidth = 1F)
```
```kotlin:ank
pairDoc.pretty(maxWidth = 20, ribbonWidth = 1F)
```
