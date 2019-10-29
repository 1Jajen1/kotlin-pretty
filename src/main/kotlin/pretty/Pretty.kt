package pretty

import arrow.Kind
import arrow.core.Tuple2
import arrow.core.andThen
import arrow.core.extensions.list.functor.tupleLeft
import arrow.core.toT
import arrow.extension
import arrow.recursion.typeclasses.Birecursive
import arrow.syntax.collections.tail
import arrow.typeclasses.Functor
import arrow.typeclasses.Monoid
import arrow.typeclasses.Semigroup
import pretty.doc.birecursive.birecursive
import pretty.doc.monoid.monoid
import pretty.doc.semigroup.semigroup
import pretty.docf.functor.functor
import pretty.simpledoc.birecursive.birecursive
import pretty.simpledocf.functor.functor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// implements this pretty printer by https://homepages.inf.ed.ac.uk/wadler/papers/prettier/prettier.pdf
// TODO check out https://jyp.github.io/posts/towards-the-prettiest-printer.html#fnref1 for something even better?

class ForDocF private constructor()
typealias DocFOf<F> = Kind<ForDocF, F>

inline fun <F> DocFOf<F>.fix(): DocF<F> = this as DocF<F>

// TODO laziness will improve performance a bit
sealed class DocF<F> : DocFOf<F> {
    class Nil<F> : DocF<F>()
    data class Text<F>(val str: String) : DocF<F>()
    data class Line<F>(val empty: Boolean) : DocF<F>()
    data class Union<F>(val l: F, val r: F) : DocF<F>()
    data class Combined<F>(val l: F, val r: F) : DocF<F>()
    data class Nest<F>(val i: Int, val doc: F) : DocF<F>()
    data class Column<F>(val doc: (Int) -> F): DocF<F>()
    data class Nesting<F>(val doc: (Int) -> F): DocF<F>()

    fun <B> map(f: (F) -> B): DocF<B> = when (this) {
        is Nil -> Nil()
        is Text -> Text(str)
        is Line -> Line(empty)
        is Combined -> Combined(f(l), f(r))
        is Nest -> Nest(i, f(doc))
        is Column -> Column(doc andThen f)
        is Nesting -> Nesting(doc andThen f)
        is Union -> Union(f(l), f(r))
    }

    companion object
}

@extension
interface DocFFunctor : Functor<ForDocF> {
    override fun <A, B> Kind<ForDocF, A>.map(f: (A) -> B): Kind<ForDocF, B> =
        fix().map(f)
}

data class Doc(val unDoc: DocF<Doc>) {

    operator fun plus(other: Doc): Doc = Doc.semigroup().run {
        this@Doc.combine(other)
    }

    companion object
}

@extension
interface DocBirecursive : Birecursive<Doc, ForDocF> {
    override fun FF(): Functor<ForDocF> = DocF.functor()
    override fun Kind<ForDocF, Doc>.embedT(): Doc = Doc(this.fix())
    override fun Doc.projectT(): Kind<ForDocF, Doc> = unDoc
}

@extension
interface DocSemigroup : Semigroup<Doc> {
    override fun Doc.combine(b: Doc): Doc = Doc(DocF.Combined(this, b))
}

@extension
interface DocMonoid : Monoid<Doc>, DocSemigroup {
    override fun empty(): Doc = Doc(DocF.Nil())
}

class ForSimpleDocF private constructor()
typealias SimpleDocFOf<F> = Kind<ForSimpleDocF, F>

inline fun <F> SimpleDocFOf<F>.fix(): SimpleDocF<F> = this as SimpleDocF<F>

sealed class SimpleDocF<F> : SimpleDocFOf<F> {
    class NilF<F> : SimpleDocF<F>()
    data class Line<F>(val i: Int, val doc: F) : SimpleDocF<F>()
    data class Text<F>(val str: String, val doc: F) : SimpleDocF<F>()

    companion object
}

@extension
interface SimpleDocFFunctor : Functor<ForSimpleDocF> {
    override fun <A, B> Kind<ForSimpleDocF, A>.map(f: (A) -> B): Kind<ForSimpleDocF, B> =
        when (val dF = fix()) {
            is SimpleDocF.NilF -> SimpleDocF.NilF()
            is SimpleDocF.Line -> SimpleDocF.Line(dF.i, f(dF.doc))
            is SimpleDocF.Text -> SimpleDocF.Text(dF.str, f(dF.doc))
        }
}

data class SimpleDoc(val unDoc: SimpleDocF<SimpleDoc>) {
    companion object
}

@extension
interface SimpleDocBirecursive : Birecursive<SimpleDoc, ForSimpleDocF> {
    override fun FF(): Functor<ForSimpleDocF> = SimpleDocF.functor()
    override fun Kind<ForSimpleDocF, SimpleDoc>.embedT(): SimpleDoc = SimpleDoc(this.fix())
    override fun SimpleDoc.projectT(): Kind<ForSimpleDocF, SimpleDoc> = unDoc
}

fun SimpleDoc.layout(): String = SimpleDoc.birecursive().run {
    cata {
        when (val dF = it.fix()) {
            is SimpleDocF.NilF -> ""
            is SimpleDocF.Text -> dF.str + dF.doc
            is SimpleDocF.Line -> "\n" + spaces(dF.i) + dF.doc
        }
    }
}

fun Doc.best(w: Int, r: Float): SimpleDoc = be(w, ribbonW(w, r), 0, 0, listOf(0 toT this))

internal fun ribbonW(w: Int, r: Float): Int = max(0, min(w, round(w * r).toInt()))

// TODO recursion scheme?
// Theoretically this is a refold from Doc to SimpleDoc but over what functor?
//  Can't use the SimpleDoc functor because if we match like nest as the first el we cannot return a good SimpleDocF element
internal fun be(w: Int, r: Int, n: Int, k: Int, ls: List<Tuple2<Int, Doc>>): SimpleDoc =
    if (ls.isEmpty()) SimpleDoc(SimpleDocF.NilF())
    else ls.first().let { (i, el) ->
        when (val dF = el.unDoc) {
            is DocF.Nil -> be(w, r, n, k, ls.tail())
            is DocF.Combined -> be(w, r, n, k, listOf(i toT dF.l, i toT dF.r) + ls.tail())
            is DocF.Nest -> be(w, r, n, k, listOf(dF.i + i toT dF.doc) + ls.tail())
            is DocF.Text -> SimpleDoc(SimpleDocF.Text(dF.str, be(w, r, n, k + dF.str.length, ls.tail())))
            is DocF.Line -> SimpleDoc(SimpleDocF.Line(i, be(w, r, i, i, ls.tail())))
            is DocF.Union -> nicest(
                w, r,
                n, k,
                be(w, r, n, k, listOf(i toT dF.l) + ls.tail()),
                be(w, r, n, k, listOf(i toT dF.r) + ls.tail())
            )
            is DocF.Nesting -> be(w, r, n, k, listOf(i toT dF.doc(i)) + ls.tail())
            is DocF.Column -> be(w, r, n, k, listOf(i toT dF.doc(k)) + ls.tail())
        }
    }

fun nicest(w: Int, r: Int, n: Int, k: Int, x: SimpleDoc, y: SimpleDoc): SimpleDoc =
    if (x.fits(min(w - k, r - k + n))) x else y

fun SimpleDoc.fits(w: Int): Boolean =
    SimpleDoc.birecursive().run {
        this@fits.cata<(Int) -> Boolean> {
            { i: Int ->
                i >= 0 && when (val dF = it.fix()) {
                    is SimpleDocF.Text -> dF.doc(i - dF.str.length)
                    else -> true
                }
            }
        }(w)
    }

fun Doc.renderPretty(): SimpleDoc = best(80, 0.4F)

fun Doc.pretty(maxWidth: Int, ribbonWidth: Float): String = best(maxWidth, ribbonWidth).layout()

// primitives
fun nil(): Doc = Doc.monoid().empty()

fun String.text(): Doc = Doc(DocF.Text(this))

fun line(): Doc = Doc(DocF.Line(false))

fun lineBreak(): Doc = Doc(DocF.Line(true))

fun softLine(): Doc = line().group()

fun softLineBreak(): Doc = lineBreak().group()

fun Doc.nest(i: Int): Doc = Doc(DocF.Nest(i, this))

fun Doc.group(): Doc = Doc(DocF.Union(this.flatten(), this))

fun column(f: (Int) -> Doc): Doc = Doc(DocF.Column(f))

fun nesting(f: (Int) -> Doc): Doc = Doc(DocF.Nesting(f))

// combinators
fun Doc.fillBreak(i: Int): Doc = width { w ->
    if (w > i) lineBreak().nest(i) else spaces(i - w).text()
}

fun Doc.fill(i: Int): Doc = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

fun Doc.width(f: (Int) -> Doc): Doc = column { k1 -> this + column { k2 -> f(k2 - k1) } }

fun Doc.indent(i: Int): Doc = (spaces(i).text() + this).hang(i)

fun Doc.hang(i: Int): Doc = nest(i).align()

fun Doc.align(): Doc = column { k ->
    nesting { i -> this.nest(k - i) }
}

fun Doc.flatten(): Doc = Doc.birecursive().run {
    cata {
        // This is why I love recursion schemes!
        when (val dF = it.fix()) {
            is DocF.Line -> if (dF.empty) nil() else " ".text()
            is DocF.Union -> dF.l
            else -> Doc(dF)
        }
    }
}

fun spaces(i: Int): String =
    if (i < 0) ""
    else (0 until i).joinToString("") { " " }

infix fun Doc.spaced(d: Doc): Doc = this + " ".text() + d

infix fun Doc.line(d: Doc): Doc = this + line() + d
infix fun Doc.softLine(d: Doc): Doc = this + softLine() + d
infix fun Doc.lineBreak(d: Doc): Doc = this + lineBreak() + d
infix fun Doc.softLineBreak(d: Doc): Doc = this + softLineBreak() + d

fun List<Doc>.list(): Doc = encloseSep(lBracket(), rBracket(), comma())
fun List<Doc>.tupled(): Doc = encloseSep(lParen(), rParen(), comma())
fun List<Doc>.semiBraces(): Doc = encloseSep(lBrace(), rBrace(), comma())

fun List<Doc>.encloseSep(l: Doc, r: Doc, sep: Doc): Doc = when {
    isEmpty() -> l + r
    size == 1 -> l + first() + r
    else -> ((listOf(l toT this.first()) + this.tail().tupleLeft(sep)).map { (a, b) -> a + b }
        .cat() + r).align()
}

fun List<Doc>.punctuate(p: Doc): List<Doc> = when {
    isEmpty() -> emptyList()
    size == 1 -> listOf(first())
    else -> (first() toT tail()).let { (x, xs) ->
        listOf(x + p) + xs.punctuate(p)
    }
}

fun List<Doc>.foldDoc(f: (Doc, Doc) -> Doc): Doc = when {
    isEmpty() -> nil()
    else -> reduce(f)
}

fun List<Doc>.cat(): Doc = vCat().group()
fun List<Doc>.fillCat(): Doc = foldDoc { a, b -> a softLineBreak b }
fun List<Doc>.hCat(): Doc = foldDoc { a, b -> a + b }
fun List<Doc>.vCat(): Doc = foldDoc { a, b -> a lineBreak b }

fun List<Doc>.sep(): Doc = vSep().group()
fun List<Doc>.fillSep(): Doc = foldDoc { a, b -> a softLine b }
fun List<Doc>.hSep(): Doc = foldDoc { a, b -> a spaced b }
fun List<Doc>.vSep(): Doc = foldDoc  { a, b -> a line b }

fun Doc.enclose(l: Doc, r: Doc): Doc = l + this + r

fun Doc.sQuotes(): Doc = enclose(sQuote(), sQuote())
fun Doc.dQuotes(): Doc = enclose(dQuote(), dQuote())
fun Doc.braces(): Doc = enclose(lBrace(), rBrace())
fun Doc.parens(): Doc = enclose(lParen(), rParen())
fun Doc.brackets(): Doc = enclose(lBracket(), rBracket())
fun Doc.angles(): Doc = enclose(lAngle(), rAngle())

fun lBracket(): Doc = "[".text()
fun rBracket(): Doc = "]".text()
fun lParen(): Doc = "(".text()
fun rParen(): Doc = ")".text()
fun lBrace(): Doc = "{".text()
fun rBrace(): Doc = "}".text()
fun lAngle(): Doc = "<".text()
fun rAngle(): Doc = ">".text()

fun comma(): Doc = ",".text()
fun space(): Doc = " ".text()
fun sQuote(): Doc = "\'".text()
fun dQuote(): Doc = "\"".text()
fun semiColon(): Doc = ";".text()
fun colon(): Doc = ":".text()
fun dot(): Doc = ".".text()
fun backslash(): Doc = "\\".text()
fun equals(): Doc = "=".text()

fun String.doc(): Doc = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        fst.text() + line() + if (fst.length + 1 < length) substring(fst.length + 1).doc() else nil()
    }
}

fun Boolean.doc(): Doc = toString().text()
fun Byte.doc(): Doc = toString().text()
fun Short.doc(): Doc = toString().text()
fun Int.doc(): Doc = toString().text()
fun Long.doc(): Doc = toString().text()
fun Float.doc(): Doc = toString().text()
fun Double.doc(): Doc = toString().text()
