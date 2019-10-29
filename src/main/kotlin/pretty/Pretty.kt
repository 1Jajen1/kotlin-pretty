package pretty

import arrow.Kind
import arrow.core.Tuple2
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

// implements this pretty printer by https://homepages.inf.ed.ac.uk/wadler/papers/prettier/prettier.pdf
// TODO check out https://jyp.github.io/posts/towards-the-prettiest-printer.html#fnref1 for something even better?

class ForDocF private constructor()
typealias DocFOf<F> = Kind<ForDocF, F>

inline fun <F> DocFOf<F>.fix(): DocF<F> = this as DocF<F>

// TODO laziness will improve performance a bit
sealed class DocF<F> : DocFOf<F> {
    class Nil<F> : DocF<F>()
    data class Text<F>(val str: String) : DocF<F>()
    class Line<F> : DocF<F>()
    data class Union<F>(val l: F, val r: F) : DocF<F>()
    data class Combined<F>(val l: F, val r: F) : DocF<F>()
    data class Nest<F>(val i: Int, val doc: F) : DocF<F>()

    fun <B> map(f: (F) -> B): DocF<B> = when (this) {
        is Nil -> Nil()
        is Text -> Text(str)
        is Line -> Line()
        is Combined -> Combined(f(l), f(r))
        is Nest -> Nest(i, f(doc))
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

fun nil(): Doc = Doc.monoid().empty()

fun String.text(): Doc = Doc(DocF.Text(this))

fun line(): Doc = Doc(DocF.Line())

fun Doc.nest(i: Int): Doc = Doc(DocF.Nest(i, this))

fun Doc.group(): Doc = Doc(DocF.Union(this.flatten(), this))

fun Doc.flatten(): Doc = Doc.birecursive().run {
    cata {
        when (val dF = it.fix()) {
            is DocF.Line -> Doc(DocF.Text(" "))
            is DocF.Union -> dF.l
            else -> Doc(dF)
        }
    }
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
            is SimpleDocF.Line -> "\n".padEnd(dF.i) + dF.doc
        }
    }
}

fun Doc.best(w: Int, k: Int): SimpleDoc = be(w, k, listOf(0 toT this))

// TODO recursion scheme?
internal fun be(w: Int, k: Int, ls: List<Tuple2<Int, Doc>>): SimpleDoc =
    if (ls.isEmpty()) SimpleDoc(SimpleDocF.NilF())
    else ls.first().let { (i, el) ->
        when (val dF = el.unDoc) {
            is DocF.Nil -> be(w, k, ls.tail())
            is DocF.Combined -> be(w, k, listOf(i toT dF.l, i toT dF.r) + ls.tail())
            is DocF.Nest -> be(w, k, listOf(dF.i + i toT dF.doc) + ls.tail())
            is DocF.Text -> SimpleDoc(SimpleDocF.Text(dF.str, be(w, k + dF.str.length, ls.tail())))
            is DocF.Line -> SimpleDoc(SimpleDocF.Line(i, be(w, i, ls.tail())))
            is DocF.Union -> better(
                w,
                k,
                be(w, k, listOf(i toT dF.l) + ls.tail()),
                be(w, k, listOf(i toT dF.r) + ls.tail())
            )
        }
    }

fun better(w: Int, k: Int, x: SimpleDoc, y: SimpleDoc): SimpleDoc =
    if (x.fits(w - k)) x else y

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

fun Doc.pretty(maxWidth: Int): String = best(maxWidth, 0).layout()

// utilities
infix fun Doc.space(d: Doc): Doc = this + " ".text() + d

infix fun Doc.newline(d: Doc): Doc = this + line() + d

fun List<Doc>.foldDoc(f: (Doc, Doc) -> Doc): Doc = when {
    isEmpty() -> nil()
    size == 1 -> first()
    else -> f(first(), tail().foldDoc(f))
}

fun List<Doc>.spread(): Doc = foldDoc { a, b -> a space b }
fun List<Doc>.stack(): Doc = foldDoc { a, b -> a newline b }

fun Doc.bracket(l: String, r: String): Doc =
    (l.text() + (line() + this).nest(2) + line() + r.text()).group()

infix fun Doc.spaceOrNewline(d: Doc): Doc =
    this + (Doc(DocF.Union(" ".text(), line()))) + d

fun String.fillWords(): Doc = split(" ").filter { it.none { it.isWhitespace() } }
    .map { it.text() }.foldDoc { a, b -> a spaceOrNewline b }

fun List<Doc>.fill(): Doc = when {
    isEmpty() -> nil()
    size == 1 -> first()
    else -> (first() toT tail()).let { (x, xs) ->
        Doc(
            DocF.Union(
                x.flatten() space xs.fill(),
                x newline xs.fill()
            )
        )
    }
}
