package pretty

import arrow.core.*
import arrow.core.extensions.list.foldable.foldRight
import arrow.core.extensions.list.functor.tupleLeft
import arrow.recursion.coelgot
import arrow.recursion.coelgotM
import arrow.recursion.elgot
import arrow.syntax.collections.tail
import pretty.doc.functor.functor
import pretty.doc.functor.map
import pretty.doc.monoid.monoid
import pretty.docf.functor.functor

fun <A> Doc<A>.renderPretty(): SimpleDoc<A> = layoutPretty(PageWidth.Available(80, 0.4F))

fun <A> Doc<A>.pretty(maxWidth: Int, ribbonWidth: Float): String =
    layoutPretty(PageWidth.Available(maxWidth, ribbonWidth)).renderString()

// primitives
fun nil(): Doc<Nothing> = Doc(Eval.now(DocF.Nil))

fun String.text(): Doc<Nothing> = Doc(Eval.now(DocF.Text(this)))

fun line(): Doc<Nothing> = hardLine().flatAlt(" ".text())

fun lineBreak(): Doc<Nothing> = hardLine().flatAlt(nil())

fun softLine(): Doc<Nothing> = line().group()

fun softLineBreak(): Doc<Nothing> = lineBreak().group()

fun hardLine(): Doc<Nothing> = Doc(Eval.now(DocF.Line))

fun <A> Doc<A>.nest(i: Int): Doc<A> = Doc(Eval.now(DocF.Nest(i, this)))

fun <A> Doc<A>.group(): Doc<A> = changesUponFlattening().fold({ this }, {
    Doc(Eval.now(DocF.Union(it, this@group)))
})

fun <A> column(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Column(f)))

fun <A> nesting(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Nesting(f)))

fun <A> Doc<A>.flatAlt(other: Doc<A>): Doc<A> = Doc(Eval.now(DocF.FlatAlt(this, other)))

fun <A> Doc<A>.annotate(ann: A): Doc<A> = Doc(Eval.now(DocF.Annotated(ann, this)))

fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B> = Doc.functor().run {
    map(f).fix()
}

fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B> = cata {
    when (it) {
        is DocF.Annotated -> f(it.ann).foldRight(it.doc.unDoc) { a, acc ->
            acc.map { DocF.Annotated(a, Doc(Eval.now(it))) }
        }.let(::Doc)
        else -> Doc(Eval.now(it as DocF<B, Doc<B>>))
        // should be a safe cast because cata already transformed everything but annotations up to this level
        //  and everything but annotations has A as a phantom generic
    }
}

fun <A> Doc<A>.unAnnotate(): Doc<Nothing> = alterAnnotations { emptyList<Nothing>() }

// combinators
fun <A> Doc<A>.fillBreak(i: Int): Doc<A> = width { w ->
    if (w > i) lineBreak().nest(i) else spaces(i - w).text()
}

fun <A> Doc<A>.fill(i: Int): Doc<A> = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A> = column { k1 -> this + column { k2 -> f(k2 - k1) } }

fun <A> Doc<A>.indent(i: Int): Doc<A> = (spaces(i).text() + this).hang(i)

fun <A> Doc<A>.hang(i: Int): Doc<A> = nest(i).align()

fun <A> Doc<A>.align(): Doc<A> = column { k ->
    nesting { i -> this.nest(k - i) }
}

fun <A> Doc<A>.flatten(): Doc<A> = Doc(Eval.later {
    when (val dF = unDoc.value()) {
        is DocF.FlatAlt -> dF.r.flatten().unDoc.value()
        is DocF.Union -> dF.l.flatten().unDoc.value()
        is DocF.Line -> DocF.Fail
        is DocF.Annotated -> DocF.Annotated(dF.ann, dF.doc.flatten())
        is DocF.Combined -> DocF.Combined(dF.l.flatten(), dF.r.flatten())
        is DocF.Nest -> DocF.Nest(dF.i, dF.doc.flatten())
        is DocF.Column -> DocF.Column(AndThen(dF.doc).andThen { it.flatten() })
        is DocF.Nesting -> DocF.Nesting(AndThen(dF.doc).andThen { it.flatten() })
        is DocF.WithPageWidth -> DocF.WithPageWidth(AndThen(dF.doc).andThen { it.flatten() })
        else -> dF
    }
})

/* para {
    when (it) {
        is DocF.Union -> it.l.b // left side of union is already flattened
        is DocF.FlatAlt -> it.r.b // flattened right side
        is DocF.Line -> Doc(Eval.now(DocF.Fail))
        else -> Doc(Eval.now(it.map { it.b })) // Ignore all other cases and just take the folded result
    }
} */

fun spaces(i: Int): String =
    if (i < 0) ""
    else (0 until i).joinToString("") { " " }

infix fun <A> Doc<A>.spaced(d: Doc<A>): Doc<A> = this + " ".text() + d

infix fun <A> Doc<A>.line(d: Doc<A>): Doc<A> = this + line() + d
infix fun <A> Doc<A>.softLine(d: Doc<A>): Doc<A> = this + softLine() + d
infix fun <A> Doc<A>.lineBreak(d: Doc<A>): Doc<A> = this + lineBreak() + d
infix fun <A> Doc<A>.softLineBreak(d: Doc<A>): Doc<A> = this + softLineBreak() + d

fun <A> List<Doc<A>>.list(): Doc<A> = encloseSep(
    (lBracket() + space()).flatAlt(lBracket()),
    (rBracket() + space()).flatAlt(rBracket()),
    comma() + space()
).group()

fun <A> List<Doc<A>>.tupled(): Doc<A> = encloseSep(
    (lParen() + space()).flatAlt(lParen()),
    (rParen() + space()).flatAlt(rParen()),
    comma() + space()
).group()

fun <A> List<Doc<A>>.semiBraces(): Doc<A> = encloseSep(
    (lBrace() + space()).flatAlt(lBrace()),
    (rBrace() + space()).flatAlt(rBrace()),
    comma() + space()
).group()

fun <A> List<Doc<A>>.encloseSep(l: Doc<A>, r: Doc<A>, sep: Doc<A>): Doc<A> = when {
    isEmpty() -> l + r
    size == 1 -> l + first() + r
    else -> ((listOf(l toT this.first()) + this.tail().tupleLeft(sep)).map { (a, b) -> a + b }
        .cat() + r).align()
}

fun <A> List<Doc<A>>.punctuate(p: Doc<A>): List<Doc<A>> = when {
    isEmpty() -> emptyList()
    size == 1 -> listOf(first())
    else -> (first() toT tail()).let { (x, xs) ->
        listOf(x + p) + xs.punctuate(p)
    }
}

fun <A> List<Doc<A>>.foldDoc(f: (Doc<A>, Doc<A>) -> Doc<A>): Doc<A> = when {
    isEmpty() -> nil()
    else -> reduce(f)
}

fun <A> List<Doc<A>>.cat(): Doc<A> = vCat().group()
fun <A> List<Doc<A>>.fillCat(): Doc<A> = foldDoc { a, b -> a softLineBreak b }
fun <A> List<Doc<A>>.hCat(): Doc<A> = foldDoc { a, b -> a + b }
fun <A> List<Doc<A>>.vCat(): Doc<A> = foldDoc { a, b -> a lineBreak b }

fun <A> List<Doc<A>>.sep(): Doc<A> = vSep().group()
fun <A> List<Doc<A>>.fillSep(): Doc<A> = foldDoc { a, b -> a softLine b }
fun <A> List<Doc<A>>.hSep(): Doc<A> = foldDoc { a, b -> a spaced b }
fun <A> List<Doc<A>>.vSep(): Doc<A> = foldDoc { a, b -> a line b }

fun <A> Doc<A>.enclose(l: Doc<A>, r: Doc<A>): Doc<A> = l + this + r

fun <A> Doc<A>.sQuotes(): Doc<A> = enclose(sQuote(), sQuote())
fun <A> Doc<A>.dQuotes(): Doc<A> = enclose(dQuote(), dQuote())
fun <A> Doc<A>.braces(): Doc<A> = enclose(lBrace(), rBrace())
fun <A> Doc<A>.parens(): Doc<A> = enclose(lParen(), rParen())
fun <A> Doc<A>.brackets(): Doc<A> = enclose(lBracket(), rBracket())
fun <A> Doc<A>.angles(): Doc<A> = enclose(lAngle(), rAngle())

fun lBracket(): Doc<Nothing> = "[".text()
fun rBracket(): Doc<Nothing> = "]".text()
fun lParen(): Doc<Nothing> = "(".text()
fun rParen(): Doc<Nothing> = ")".text()
fun lBrace(): Doc<Nothing> = "{".text()
fun rBrace(): Doc<Nothing> = "}".text()
fun lAngle(): Doc<Nothing> = "<".text()
fun rAngle(): Doc<Nothing> = ">".text()

fun comma(): Doc<Nothing> = ",".text()
fun space(): Doc<Nothing> = " ".text()
fun sQuote(): Doc<Nothing> = "\'".text()
fun dQuote(): Doc<Nothing> = "\"".text()
fun semiColon(): Doc<Nothing> = ";".text()
fun colon(): Doc<Nothing> = ":".text()
fun dot(): Doc<Nothing> = ".".text()
fun backslash(): Doc<Nothing> = "\\".text()
fun equals(): Doc<Nothing> = "=".text()
fun pipe(): Doc<Nothing> = "|".text()

fun String.doc(): Doc<Nothing> = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        if (fst.length >= length) fst.text()
        else fst.text() + hardLine() + substring(fst.length + 1).doc()
    }
}

fun Boolean.doc(): Doc<Nothing> = toString().text()
fun Byte.doc(): Doc<Nothing> = toString().text()
fun Short.doc(): Doc<Nothing> = toString().text()
fun Int.doc(): Doc<Nothing> = toString().text()
fun Long.doc(): Doc<Nothing> = toString().text()
fun Float.doc(): Doc<Nothing> = toString().text()
fun Double.doc(): Doc<Nothing> = toString().text()
