---
title: "Api reference"
date: 2020-01-14
weight: 15
type: "docs"
description: >
    A comprehensive reference of all methods that kotlin-pretty provides.
---

> This has been mostly adapted from https://hackage.haskell.org/package/prettyprinter-1.5.1/docs/Data-Text-Prettyprint-Doc.html.

## Functions on Doc

Lists functions that operator on and/or return a `Doc<A>`

### Basic

Primitives and simple combinators to create and alter documents:

###### `fun nil(): Doc<Nothing>`

Creates an empty document. The resulting document still has a height of 1. So in combination with e.g. [vCat](TODO) it can still render as a newline.
```kotlin
listOf("hello".text(), nil(), "world".text()).vCat() // =>
// hello
// 
// world
```

---
###### `fun String.text(): Doc<Nothing>`

Create a document which contains the string as text.
> The string should never contain newlines as that would lead to unwanted behaviour. If it can contain newlines, use [doc()](./#fun-stringdoc-docnothing) instead.

---
###### `fun String.doc(): Doc<Nothing>`

Create a document which contains the string as text and also handles newlines by replacing them with `hardLine()`

---
###### `fun line(): Doc<Nothing>`

This document advances the layout to the next line and sets the indentation to the current nesting level.
```kotlin
"This is rendered in".text() + line() + "two lines".text() // =>
// This is rendered in
// two lines
```
If inside a [group()](./#fun-a-docagroup-doca) the newline can be undone and replaced by a space.

---
###### `fun lineBreak(): Doc<Nothing>`

This document advances the layout to the next line and sets the indentation to the current nesting level.
```kotlin
"This is rendered in".text() + lineBreak() + "two lines".text() // =>
// This is rendered in
// two lines
```
If inside a [group()](./#fun-a-docagroup-doca) the newline can be undone and replaced by [nil()](./#fun-nil-docnothing).

---
###### `fun softLine(): Doc<Nothing>`

softLine behaves like a space if the layout fits, otherwise like a newline.
```kotlin
"This is rendered in".text() + softLine() + "one line".text() // => width 80
// This is rendered in one line
"This is rendered in".text() + softLine() + "two lines".text() // => width 20
// This is rendered
// in two lines
softLine() == line().group()
```

---
###### `fun softLineBreak(): Doc<Nothing>`

softLineBreak behavious like [nil()](./#fun-nil-docnothing) if the layout fits, otherwise like a newline.
```kotlin
"This is rendered in".text() + softLineBreak() + "one line".text() // => width 80
// This is renderedin one line
"This is rendered in".text() + softLineBreak() + "two lines".text() // => width 20
// This is rendered
// in two lines
softLineBreak() == lineBreak().group()
```

---
###### `fun hardLine(): Doc<Nothing>`

Insert a new line which cannot be flattened by [group](./#fun-a-docagroup-doca).

---
###### `fun <A> Doc<A>.nest(i: Int): Doc<A>`

Change the layout of a document by increasing the indentation level (of the following lines) by `i`. Negative values are allowed and will decrease the nesting level.
```kotlin
listOf(listOf("Hello".text(), "World".text()).vCat().nest(4), "!".text()).vCat() // =>
// Hello
//     world
// !
```
See also:
- [hang](TODO) nest relative to the current cursor position, instead of the current nesting level
- [align](TODO) set nesting level to the current cursor position
- [indent](TODO) directly increase indentation padded with spaces

---
###### `fun <A> Doc<A>.group(): Doc<A>`

Group tries to flatten a document to a single line. If this fails (on hardLine's) or does not fit, the document will be layed out unchanged. This function is the key to building adapting layouts.

See [vCat](TODO), [line](./#fun-line-docnothing), [flatAlt](TODO) for examples.

---
###### `fun <A> Doc<A>.flatAlt(fallback: Doc<A>): Doc<A>`

Renders a document as is by default, but when inside a [group](./#fun-a-docagroup-doca) it will render the `fallback`. TODO note on how fallbacks affect the rendering and what invariants it should hold.
```kotlin
line() == hardLine().flatAlt(space())
lineBreak() == hardLine().flatAlt(nil())
```

---
### Alignment

This section describes functions that can align their output relative to the current cursor position as opposed to [nest](TODO) which always aligns to the current nesting level. This means that in terms of the Wadler-Leijen algorithm (which is what is used to layout the document), these functions do not produce an optimal result. They are however immensly useful in practice. Some of these functions are also more expensive to use at the top level, but should be fine in most cases.

###### `fun <A> Doc<A>.align(): Doc<A>`

Align will lay out the document with the current nesting level set to the current column.
```kotlin:ank
"Hello".text() spaced listOf("World".text(), "there".text()).vSep() // =>
```
```kotlin:ank
"Hello".text() spaced listOf("World".text(), "there".text()).vSep().align() // =>
```

---
###### `fun <A> Doc<A>.hang(i: Int): Doc<A>`

Hang lays out the document x with a nesting level set to the current column plus i. Negative values are allowed, and decrease the nesting level.
```kotlin:ank
```

### Infix/binary Don't forget the line aliases with infix that I have^^

### List

#### Sep

#### Cat

### Others

### Reactive/conditional layouts

### Filler

### Convenience

### Bracketing

### Named characters

#### ASCII

#### Unicode

### Annotations

### Optimization

## Operations on SimpleDoc

### Layout

### Render