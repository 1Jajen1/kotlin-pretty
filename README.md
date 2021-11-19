# kotlin-pretty
> A pretty printer library for kotlin

Kotlin-pretty is an extensible and easy to use pretty-printing library for kotlin. It can be used to render dynamic text in a size constrained environment (e.g. a terminal, message boxes, etc) in a human readable and (hopefully) pretty way.

## Motivation

Usuall when outputting text (for example output from tests or debugging (usually `toString` output) or rendering information for users) we have two scenarios:
- We have static text
- We have dynamic text

With static text, we could in theory define an optimal layout by hand, however this is tedious for longer texts and is not maintainable: If we change the beginning just slightly the whole layout may change!

With dynamic text, we can only guess where linebreaks should be. This is rarely optimal and usually leads to either excessive newlines or none. Both makes output difficult to read.

A prettyprinter can help with both of those situations, by taking away the size concerns and providing you with the ability to define dynamic layouts that adapt to a given size. Given that at some point, when we eventually render something, we should have some idea of our output size, we can take this dynamic layout and render it to a fixed optimal layout for our preferred size.

The use cases span from terminal output, to static size constraints on user interfaces, to code layout, to rendering and presenting users data in readable fashion, etc. Basically everwhere where dynamic text meets a possible size constraint.

## How prettyprinters solve these problems

In kotlin-pretty we provide a datatype `Doc` which represents a rich text document with some sort of layout. There is a large set of combinators to help modify the content and the resutling layout of a document. In the end this document can be layed out by simple algorithm's, provided by kotlin-pretty, and rendered as `String`, directly to output handles or however you may want as there are easy ways to change and influence the rendering method.

## Getting started

### Setup

* The use of jitpack is hopefully temporary.

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation "com.github.1Jajen1.kotlin-pretty:kotlin-pretty:0.6.0"
}
```

For a walkthrough on how to use kotlin-pretty, visit the [getting-started guide](https://1jajen1.github.io/kotlin-pretty/docs/getting-started/) in the documentation.

> Most of the documentation outside of the api reference and the starting page is currently under construction.

## Documentation

The documentation is hosted at: https://1jajen1.github.io/kotlin-pretty/docs/

> It is still work in progress, but there should be more than enough to get started.

### Contributing to the docs

The docs are placed under `docs/` and the content is under `contentIn`. This is then processed by [ank](https://github.com/arrow-kt/arrow/tree/master/modules/ank) and served by [hugo](https://gohugo.io/). To serve it in debug mode run: `gradlew runAnk` and (preferrably in a separate window) `hugo server`. Every time you change something in `docs/contentIn` you need to re-run `gradlew runAnk` (if possible I'd like to change that!)

## Feedback

Questions and feedback is very welcome, just open issues on github, or directly ask me anything on the kotlinlang slack (`@jannis`). 

## Credits

Kotlin-pretty is, apart from minor differences and bugfixes, a port of the haskell library [prettyprinter](https://github.com/quchen/prettyprinter). It covers most if not all important aspects of their fantastic api.
