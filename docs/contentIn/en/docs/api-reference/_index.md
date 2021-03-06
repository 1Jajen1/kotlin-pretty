---
title: "Api reference"
date: 2020-01-14
weight: 15
type: "docs"
description: >
    A comprehensive reference of all methods that kotlin-pretty provides.
---
```kotlin:ank:replace
import pretty.*
import pretty.symbols.*
"" // This is only here to provide imports to the whole page. Ank will remove this
```
> This has been mostly adapted from https://hackage.haskell.org/package/prettyprinter-1.5.1/docs/Data-Text-Prettyprint-Doc.html.

## Functions on Doc

Lists functions that operator on and/or return a `Doc<A>`

### Basic

Primitives and simple combinators to create and alter documents:

###### `fun nil(): Doc<Nothing>`

Creates an empty document. The resulting document still has a height of 1. So in combination with e.g. [vCat]({{< relref "#fun-a-listdocavcat-doca" >}}) it can still render as a newline.
```kotlin:ank
listOf("hello".text(), nil(), "world".text()).vCat()
```

---
###### `fun String.text(): Doc<Nothing>`

Create a document which contains the string as text.
> The string should never contain newlines as that would lead to unwanted behaviour. If it can contain newlines, use [doc]({{< relref "#fun-stringdoc-docnothing" >}}) instead.

---
###### `fun String.doc(): Doc<Nothing>`

Create a document which contains the string as text and also handles newlines by replacing them with `hardLine()`

---
###### `fun line(): Doc<Nothing>`

This document advances the layout to the next line and sets the indentation to the current nesting level.
```kotlin:ank
"This is rendered in".text() + line() + "two lines".text()
```
If inside a [group]({{< relref "#fun-a-docagroup-doca" >}}) the newline can be undone and replaced by a space.

---
###### `fun lineBreak(): Doc<Nothing>`

This document advances the layout to the next line and sets the indentation to the current nesting level.
```kotlin:ank
"This is rendered in".text() + lineBreak() + "two lines".text()
```
If inside a [group]({{< relref "#fun-a-docagroup-doca" >}}) the newline can be undone and replaced by [nil]({{< relref "#fun-nil-docnothing" >}}).

---
###### `fun softLine(): Doc<Nothing>`

softLine behaves like a space if the layout fits, otherwise like a newline.
```kotlin:ank
("This is rendered in".text() + softLine() + "one line".text()).pretty(maxWidth = 80)
```
```kotlin:ank
("This is rendered in".text() + softLine() + "two lines".text()).pretty(maxWidth = 20)
```
```kotlin
softLine() == line().group()
```

---
###### `fun softLineBreak(): Doc<Nothing>`

softLineBreak behavious like [nil]({{< relref "#fun-nil-docnothing" >}}) if the layout fits, otherwise like a newline.
```kotlin:ank
("This is rendered in".text() + softLineBreak() + "one line".text()).pretty(maxWidth = 80)
```
```kotlin:ank
("This is rendered in".text() + softLineBreak() + "two lines".text()).pretty(maxWidth = 20)
```
```kotlin
softLineBreak() == lineBreak().group()
```

---
###### `fun hardLine(): Doc<Nothing>`

Insert a new line which cannot be flattened by [group]({{< relref "#fun-a-docagroup-doca" >}}).

---
###### `fun <A> Doc<A>.nest(i: Int): Doc<A>`

Change the layout of a document by increasing the indentation level (of the following lines) by `i`. Negative values are allowed and will decrease the nesting level.
```kotlin:ank
listOf(listOf("Hello".text(), "World".text()).vCat().nest(4), "!".text()).vCat()
```
See also:
- [hang]({{< relref "#fun-a-docahangi-int-doca" >}}) nest relative to the current cursor position, instead of the current nesting level
- [align]({{< relref "#fun-a-docaalign-doca" >}}) set nesting level to the current cursor position
- [indent]({{< relref "#fun-a-docaindenti-int-doca" >}}) directly increase indentation padded with spaces

---
###### `fun <A> Doc<A>.group(): Doc<A>`

Group tries to flatten a document to a single line. If this fails (on hardLine's) or does not fit, the document will be layed out unchanged. This function is the key to building adapting layouts.

See [vCat]({{< relref "#fun-a-listdocavcat-doca" >}}), [line]({{< relref "#fun-a-docalineother-doca-doca" >}}), [flatAlt]({{< relref "#fun-a-docaflataltfallback-doca-doca" >}}) for examples.

---
###### `fun <A> Doc<A>.flatAlt(fallback: Doc<A>): Doc<A>`

Renders a document as is by default, but when inside a [group](./#fun-a-docagroup-doca) it will render the `fallback`. TODO note on how fallbacks affect the rendering and what invariants it should hold.
```kotlin
line() == hardLine().flatAlt(space())
lineBreak() == hardLine().flatAlt(nil())
```

---
### Alignment

This section describes functions that can align their output relative to the current cursor position as opposed to [nest]({{< relref "#fun-a-docanesti-int-doca" >}}) which always aligns to the current nesting level. This means that in terms of the Wadler-Leijen algorithm (which is what is used to layout the document), these functions do not produce an optimal result. They are however immensly useful in practice. Some of these functions are also more expensive to use at the top level, but should be fine in most cases.

###### `fun <A> Doc<A>.align(): Doc<A>`

Align will lay out the document with the current nesting level set to the current column.
```kotlin:ank
"Hello".text() spaced listOf("World".text(), "there".text()).vSep()
```
```kotlin:ank
"Hello".text() spaced listOf("World".text(), "there".text()).vSep().align()
```

---
###### `fun <A> Doc<A>.hang(i: Int): Doc<A>`

Hang lays out the document x with a nesting level set to the current column plus i. Negative values are allowed, and decrease the nesting level.
```kotlin:ank
val doc = "Indenting these words with hang".reflow()
("prefix".text() spaced doc.hang(4)).pretty(maxWidth = 24)
```
```kotlin:ank
val doc = "Indenting these words with nest".reflow()
("prefix".text() spaced doc.nest(4)).pretty(maxWidth = 24)
```
```kotlin
hang(i) == nest(i).align()
```

---
###### `fun <A> Doc<A>.indent(i: Int): Doc<A>`

Indents the document with `i` spaces, starting from the current column.
```kotlin:ank
val doc = "Indent these words using indent".reflow()
("prefix".text() spaced doc.indent(4)).pretty(maxWidth = 24)
```

---
###### `fun <A> List<Doc<A>>.encloseSep(left: Doc<A>, right: Doc<A>, sep: Doc<A>): Doc<A>`

Concatenate the documents between `left` and `right` and add `sep` in between.
```kotlin:ank
val encloseSep = "list".text() spaced listOf(1.doc(), 2.doc(), 10.doc(), 1698.doc())
    .encloseSep(lBracket(), rBracket(), comma())
    .align()
encloseSep.pretty(maxWidth = 80)
```
```kotlin:ank
encloseSep.pretty(maxWidth = 10)
```

If you want to put the separator as a suffix, take a look at [puncuate]({{< relref "#fun-a-listdocapunctuatep-doca-listdoca" >}}).

---
###### `fun <A> List<Doc<A>>.list(): Doc<A>`

Variant of [encloseSep]({{< relref "#fun-a-listdocaenclosesepleft-doca-right-doca-sep-doca-doca" >}}) for list-like output.
```kotlin:ank
val listDoc = listOf(1.doc(), 10.doc(), 100.doc(), 1000.doc(), 10000.doc()).list()
listDoc.pretty(maxWidth = 80)
```
```kotlin:ank
listDoc.pretty(maxWidth = 10)
```

---
###### `fun <A> List<Doc<A>>.tupled(): Doc<A>`

Variant of [encloseSep]({{< relref "#fun-a-listdocaenclosesepleft-doca-right-doca-sep-doca-doca" >}}) for tuple-like output.
```kotlin:ank
val tupleDoc = listOf(1.doc(), 10.doc(), 100.doc(), 1000.doc(), 10000.doc()).tupled()
tupleDoc.pretty(maxWidth = 80)
```
```kotlin:ank
tupleDoc.pretty(maxWidth = 10)
```

---
###### `fun <A> List<Doc<A>>.semiBraces(): Doc<A>`

Variant of [encloseSep]({{< relref "#fun-a-listdocaenclosesepleft-doca-right-doca-sep-doca-doca" >}}) for braced output.
```kotlin:ank
val bracedDoc = listOf(1.doc(), 10.doc(), 100.doc(), 1000.doc(), 10000.doc()).semiBraces()
bracedDoc.pretty(maxWidth = 80)
```
```kotlin:ank
bracedDoc.pretty(maxWidth = 10)
```

---
### Binary and infix functions

###### `fun <A> Doc<A>.plus(other: Doc<A>): Doc<A>`

Concatenate two documents.
```kotlin:ank
"Hello".text() + space() + "world".text()
```

---
###### `fun <A> Doc<A>.spaced(other: Doc<A>): Doc<A>`

Concatenate two documents with a space in between.
```kotlin:ank
"Hello".text() spaced "world".text()
```

---
###### `fun <A> Doc<A>.line(other: Doc<A>): Doc<A>` 

Concatenate two documents with a [line]({{< relref "#fun-a-docalineother-doca-doca" >}}) in between.
```kotlin:ank
"Hello".text() line "world".text()
```

---
###### `fun <A> Doc<A>.lineBreak(other: Doc<A>): Doc<A>`

Concatenate two documents with a [lineBreak]({{< relref "#fun-linebreak-docnothing" >}}) in between.
```kotlin:ank
"Hello".text() lineBreak "world".text()
```

---
###### `fun <A> Doc<A>.softLine(other: Doc<A>): Doc<A>` 

Concatenate two documents with a [softLine]({{< relref "#fun-softline-docnothing" >}}) in between.
```kotlin:ank
"Hello".text() softLine "world".text()
```

---
###### `fun <A> Doc<A>.softLineBreak(other: Doc<A>): Doc<A>`

Concatenate two documents with a [softLineBreak]({{< relref "#fun-softlinebreak-docnothing" >}}) in between.
```kotlin:ank
"Hello ".text() softLineBreak "world".text()
```

---
### List

These functions generalize working over lists of documents. They are separated into two families: `sep` and `cat` where `sep` uses [line]({{< relref "#fun-a-docalineother-doca-doca" >}}) and `cat` uses [lineBreak]({{< relref "#fun-linebreak-docnothing" >}}) to separate content.

###### `fun <A> List<Doc<A>>.foldDoc(f: (Doc<A>, Doc<A>) -> Doc<A>): Doc<A>`

Concatenate the documents in a list given a binary function `f`. Almost all other operators on lists are implemented using this function.

---
###### `fun <F, A> Kind<F, Doc<A>>.foldDoc(FF: Foldable<F>, f: (Doc<A>, Doc<A>) -> Doc<A>): Doc<A>`

Kind polymorphic version of [foldDoc]({{< relref "#fun-a-listdocafolddocf-doca-doca---doca-doca" >}}) that allows folding any foldable structure.

---
#### Sep

Functions from this list will use [line]({{< relref "#fun-a-docalineother-doca-doca" >}}) to insert linebreaks. This means it when used with [group]({{< relref "#fun-a-docagroup-doca" >}}) they will be replaced by spaces.

###### `fun <A> List<Doc<A>>.hSep(): Doc<A>`

Concatenate all documents using [spaced]({{< relref "#fun-a-docaspacedother-doca-doca" >}}). This never introduces newlines on its own.
```kotlin:ank
val hSepDoc = listOf("Hello".text(), "there!".text(), "- Kenobi".text()).hSep()
hSepDoc.pretty(maxWidth = 80)
```
```kotlin:ank
hSepDoc.pretty(maxWidth = 10)
```
For a layout that automatically adds linebreaks, consider [fillSep]({{< relref "#fun-a-listdocafillsep-doca" >}}) instead.

---
###### `fun <A> List<Doc<A>>.vSep(): Doc<A>`

Concatenate all documents vertically using [line]({{< relref "#fun-a-docalineother-doca-doca" >}}).
```kotlin:ank
val vSepDoc = listOf("Text".text(), "to".text(), "lay".text(), "out".text()).vSep()
"prefix".text() spaced vSepDoc
```
```kotlin:ank
"prefix".text() spaced vSepDoc.align()
```
Because later grouping a `vSep` is so common, [sep]({{< relref "#fun-a-listdocasep-doca" >}}) is a built-in which does that.

---
###### `fun <A> List<Doc<A>>.fillSep(): Doc<A>`

Concatenate all documents with [softLine]({{< relref "#fun-a-docasoftlineother-doca-doca" >}}). The resulting document will be as wide as possible, but introduce newlines if it no longer fits.
```kotlin:ank
val fillSepDoc = listOf(
    "Very".text(), "long".text(), "text".text(),
    "to".text(), "lay".text(), "out.".text(),
    "Hello".text(), "world".text(), "example!".text()
).fillSep()
fillSepDoc.pretty(maxWidth = 80)
```
```kotlin:ank
fillSepDoc.pretty(maxWidth = 40)
```

---
###### `fun <A> List<Doc<A>>.sep(): Doc<A>`

Concatenate all documents with [softLine]({{< relref "#fun-a-docasoftlineother-doca-doca" >}}), but calls [group]({{< relref "#fun-a-docagroup-doca" >}}) on the resulting document to flatten it. This is different from [vSep] because it tries to lay out horizontally first, instead of just vertically. This is also different from [fillSep]({{< relref "#fun-a-listdocafillsep-doca" >}}), because it will also flatten the individual documents instead of just the separator.
```kotlin:ank
val sepDoc = listOf("Text".text() line "that".text(), "spans".text(), "multiple".text(), "lines".text()).sep()
sepDoc.pretty(maxWidth = 80)
```
```kotlin:ank
sepDoc.pretty(maxWidth = 20)
```

---
#### Cat

Functions from this list will use [lineBreak]({{< relref "#fun-linebreak-docnothing" >}}) to insert linebreaks. This means it when used with [group]({{< relref "#fun-a-docagroup-doca" >}}) they will be replaced by [nil]({{< relref "#fun-nil-docnothing" >}}).

###### `fun <A> List<Doc<A>>.hCat(): Doc<A>`

Concatenate all documents using [plus]({{< relref "#fun-a-docaplusother-doca-doca" >}}). This never introduces newlines on its own.
```kotlin:ank
val hCatDoc = listOf("Hello".text(), "there!".text(), "- Kenobi".text()).hCat()
hCatDoc.pretty(maxWidth = 80)
```
```kotlin:ank
hCatDoc.pretty(maxWidth = 10)
```
For a layout that automatically adds linebreaks, consider [fillCat]({{< relref "#fun-a-listdocafillcat-doca" >}}) instead.

---
###### `fun <A> List<Doc<A>>.vCat(): Doc<A>`

Concatenate all documents vertically using [lineBreak]({{< relref "#fun-a-docalinebreakother-doca-doca" >}}).
```kotlin:ank
val vCatDoc = listOf("Text".text(), "to".text(), "lay".text(), "out".text()).vCat()
"prefix".text() spaced vCatDoc
```
```kotlin:ank
"prefix".text() spaced vCatDoc.align()
```
Because later grouping a `vCat` is so common, [cat](({{< relref "#fun-a-listdocacat-doca" >}})) is a built-in which does that.

---
###### `fun <A> List<Doc<A>>.fillCat(): Doc<A>`

Concatenate all documents with [softLineBreak](#fun-a-docasoftlinebreakother-doca-doca). The resulting document will be as wide as possible, but introduce newlines if it no longer fits.
```kotlin:ank
val fillCatDoc = listOf(
    "Very".text(), "long".text(), "text".text(),
    "to".text(), "lay".text(), "out.".text(),
    "Hello".text(), "world".text(), "example!".text()
).fillCat()
fillCatDoc.pretty(maxWidth = 80)
```
```kotlin:ank
fillCatDoc.pretty(maxWidth = 40)
```

---
###### `fun <A> List<Doc<A>>.cat(): Doc<A>`

Concatenate all documents with [softLineBreak](#fun-a-docasoftlinebreakother-doca-doca), but calls [group]({{< relref "#fun-a-docagroup-doca" >}}) on the resulting document to flatten it. This is different from [vCat]({{< relref "#fun-a-listdocavcat-doca" >}}) because it tries to lay out horizontally first, instead of just vertically. This is also different from [fillCat]({{< relref "#fun-a-listdocafillcat-doca" >}}), because it will also flatten the individual documents instead of just the separator.
```kotlin:ank
val catDoc = listOf("Text".text() line "that".text(), "spans".text(), "multiple".text(), "lines".text()).cat()
catDoc.pretty(maxWidth = 80)
```
```kotlin:ank
catDoc.pretty(maxWidth = 20)
```

---
### Others

###### `fun <A> List<Doc<A>>.punctuate(p: Doc<A>): List<Doc<A>>`

Append `p` to all but the last document.
```kotlin:ank
val punctuateDoc = listOf("Hello".text(), "world".text(), "example".text())
    .punctuate(comma())
punctuateDoc.hSep().pretty(maxWidth = 80)
```
```kotlin:ank
punctuateDoc.vSep().pretty(maxWidth = 20)
```
If you want to but elements infront of the documents you should use [encloseSep]({{< relref "#fun-a-listdocaenclosesepleft-doca-right-doca-sep-doca-doca" >}}).

---
### Reactive/conditional layouts

Layout documents with access to context such as the current position, the available page width or the current nesting.

###### `fun <A> column(f: (Int) -> Doc<A>): Doc<A>`

Layout a document with information on what the current column is. Used to implement [align]({{< relref "#fun-a-docaalign-doca" >}}).
```kotlin:ank
column { c -> "Columns are".text() spaced c.doc() + "-based".text() }
```
```kotlin:ank
val colDoc = "prefix".text() spaced column { pipe() spaced " <- column".text() spaced it.doc()  }
listOf(0, 4, 8).map { colDoc.indent(it) }.vSep()
```

---
###### `fun <A> nesting(f: (Int) -> Doc<A>): Doc<A>`

Layout a document with information on what the current nesting is. Used to implement [align]({{< relref "#fun-a-docaalign-doca" >}}).
```kotlin:ank
val nestingDoc = "prefix".text() spaced nesting { ("Nested:".text() spaced it.doc()).brackets() }
listOf(0, 4, 8).map { nestingDoc.indent(it) }.vSep()
```

---
###### `fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A>`

Layout a document with information on what the current column width is.
```kotlin:ank
fun <A> annotate(d: Doc<A>): Doc<A> = d.brackets().width { w -> " <- width:".text() spaced w.doc() }
listOf(
    "---".text(), "------".text(), "---".text().indent(3),
    listOf("---".text(), "---".text().indent(4)).vSep()
).map(::annotate).vSep().align()
```

---
###### `fun <A> pageWidth(f: (PageWidth) -> Doc<A>): Doc<A>`

Layout a document with information on what the pagewidth settings are.
```kotlin:ank
fun PageWidth.doc(): Doc<Nothing> = when (this) {
    PageWidth.Unbounded -> "Unbounded".text()
    is PageWidth.Available ->
        "Max width:".text() spaced maxWidth.doc() spaced ", ribbonFrac:".text() spaced ribbonFract.doc()
}
val _pwDoc = "prefix".text() spaced pageWidth { pw -> pw.doc().brackets() }
val pwDoc = listOf(0, 4, 8).map { _pwDoc.indent(it) }.vSep()
pwDoc.pretty(maxWidth = 32)
```
```kotlin:ank
pwDoc.layoutPretty(PageWidth.Unbounded).renderString()
```

---
### Fillers

Fill up available space.

###### `fun <A> Doc<A>.fill(i: Int): Doc<A>`

Lay out a document and append spaces until it has a width of `i`.
```kotlin:ank
val types = listOf("empty" to "Doc", "nest" to "Int -> Doc -> Doc", "fillSep" to "[Doc] -> Doc")
"let".text() spaced types.map { (n, tp) -> n.text().fill(5) spaced "::".text() spaced tp.text() }
    .vCat().align()
```

---
###### `fun <A> Doc<A>.fillBreak(i: Int): Doc<A>`

Lay out a document and append spaces until it has a width of `i` and insert a lineBreak if it exceeds the desired width.
```kotlin:ank
val types = listOf("empty" to "Doc", "nest" to "Int -> Doc -> Doc", "fillSep" to "[Doc] -> Doc")
"let".text() spaced types.map { (n, tp) -> n.text().fillBreak(5) spaced "::".text() spaced tp.text() }
    .vCat().align()
```

---
### Convenience

###### `fun <A> Doc<A>.enclose(l: Doc<A>, r: Doc<A>): Doc<A>`

Surround the document with `l` and `r`.
```kotlin:ank
"Hello".text().enclose("_".text(), "_".text())
```

---
### Bracketing

Common functions to enclose documents.

#### ASCII

###### `fun <A> Doc<A>.sQuotes(): Doc<A>`

```kotlin:ank
"·".text().sQuotes()
```

---
###### `fun <A> Doc<A>.dQuotes(): Doc<A>`

```kotlin:ank
"·".text().dQuotes()
```

---
###### `fun <A> Doc<A>.parens(): Doc<A>`

```kotlin:ank
"·".text().parens()
```

---
###### `fun <A> Doc<A>.angles(): Doc<A>`

```kotlin:ank
"·".text().angles()
```

---
###### `fun <A> Doc<A>.braces(): Doc<A>`

```kotlin:ank
"·".text().braces()
```

---
###### `fun <A> Doc<A>.brackets(): Doc<A>`

```kotlin:ank
"·".text().brackets()
```

---
#### Unicode

###### `fun <A> Doc<A>.d9966quotes(): Doc<A>`

```kotlin:ank
"·".text().d9966quotes()
```

---
###### `fun <A> Doc<A>.d6699quotes(): Doc<A>`

```kotlin:ank
"·".text().d6699quotes()
```

---
###### `fun <A> Doc<A>.s96quotes(): Doc<A>`

```kotlin:ank
"·".text().s96quotes()
```

---
###### `fun <A> Doc<A>.s69quotes(): Doc<A>`

```kotlin:ank
"·".text().s69quotes()
```

---
###### `fun <A> Doc<A>.dGuillemetsOut(): Doc<A>`

```kotlin:ank
"·".text().dGuillemetsOut()
```

---
###### `fun <A> Doc<A>.dGuillemetsIn(): Doc<A>`

```kotlin:ank
"·".text().dGuillemetsIn()
```

---
###### `fun <A> Doc<A>.sGuillemetsOut(): Doc<A>`

```kotlin:ank
"·".text().sGuillemetsOut()
```

---
###### `fun <A> Doc<A>.sGuillemetsIn(): Doc<A>`

```kotlin:ank
"·".text().sGuillemetsIn()
```

---
### Named characters

Some constants for ascii and unicode characters

#### ASCII

Constants for ASCII characters can be found [here](https://github.com/1Jajen1/kotlin-pretty/blob/master/kotlin-pretty/src/main/kotlin/pretty/symbols/Ascii.kt).

#### Unicode

Constants for uncode characters can be found [here](https://github.com/1Jajen1/kotlin-pretty/blob/master/kotlin-pretty/src/main/kotlin/pretty/symbols/Unicode.kt).

### Annotations

Combinators that deal with annotations. Learn more about annotations [here]({{< ref "/docs/annotations" >}}).

###### `fun <A> Doc<A>.annotate(a: A): Doc<A>`

Add an annotation to a document. This can be used to supply additional context to the renderer, e.g. color. This is only relevant for documents that are rendered with a custom renderer. [renderString]({{< relref "#fun-simpledocarenderstring-string" >}}) and derivates will ignore annotations.

---
###### `fun <A> Doc<A>.unAnnotate(): Doc<Nothing>`

Remove annotations from a document.

---
###### `fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B>`

Alter annotations of a document.

---
###### `fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B>`

Alter annotations of a document with the ability to either remove the annotation or add one or more annotations to the document.

--

### Utiltity

###### `fun String.words(): List<Doc<Nothing>>`

Split a string into single words and convert them to `Doc` using [doc]({{< relref "#fun-stringdoc-docnothing" >}}).
```kotlin:ank
"Hello world string".words().vSep()
```

---
###### `fun String.reflow(): Doc<Nothing>`

Replace spaces in a string with [softLine]({{< relref "#fun-softline-docnothing" >}}). This attempts to lay out the string as wide as possible, but introduces newlines when it exceeds max-width.
```kotlin:ank
"Hello world string that is slightly longer than normal".reflow()
```

---
### Optimization

###### `fun <A> Doc<A>.fuse(shallow: Boolean): Doc<A>`

Fuse text nodes in a document if possible. If you document consists of many `"H".text() + "E".text() ...` operations, this will attempt to fuse the text together to reduce the overhead for the layout algorithm. If you use deep fusion `shallow => false`, this will also try to fuse documents returned by [column], [nesting] and [pageWidth]. A fused document is always equal to an unfused on when rendered. Benchmark your code to see if this benefits you or not!

---
## Operations on SimpleDoc

Operations that use `SimpleDoc` instead of `Doc`. To learn more about `SimpleDoc` go [here](TODO).

### Layout

###### `fun Doc<A>.layoutPretty(pw: PageWidth): SimpleDoc<A>`

The default layout algorithm which calculates a layout for a document.

---
###### `fun Doc<A>.layoutSmart(pw: PageWidth): SimpleDoc<A>`

A layout algorithm which uses more lookahead than [layoutPretty]({{< relref "#fun-docalayoutprettypw-pagewidth-simpledoca" >}}). It is slightly slower, but can produce nicer results.
```kotlin:ank
fun <A> Doc<A>.f(): Doc<A> = (("fun(".text() softLineBreak this) + rParen()).hang(2)
val lSmartDoc = listOf("exempli".text(), "gratia".text()).list().align().f().f().f().f().f()
val hr = pipe() + "------------------------".text() + pipe()
listOf(hr, lSmartDoc, hr).vSep().layoutPretty(PageWidth.Available(26, 1F)).renderString()
```
```kotlin:ank
listOf(hr, lSmartDoc, hr).vSep().layoutSmart(PageWidth.Available(26, 1F)).renderString()
```

---
###### `fun Doc<A>.layoutCompact(): SimpleDoc<A>`

A very simple layout algorithm that lays out a document with no extra newlines or indentation. This is useful to render into machine-readable formats.

---
### Render

###### `fun SimpleDoc<A>.renderString(): String`

Render a simple doc as a string. Ignores annotations.

---
###### `fun <A, B> SimpleDoc<A>.renderDecorated(empty: B, combine: (B, B) -> B, fromString: (String) -> B, annotate: (A, B) -> B): B`

Helper to implement basic renders that handle annotations.

For example [renderString]({{< relref "#fun-simpledocarenderstring-string" >}}) is defined as:
```kotlin:ank:silent

fun <A> SimpleDoc<A>.renderString(): String =
    renderDecorated(
        empty = "",
        combine = { a, b -> a + b },
        fromString = { it },
        annotate = { ann, str -> str }
    )
```