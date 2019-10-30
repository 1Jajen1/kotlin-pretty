package pretty

import arrow.Kind
import arrow.core.Eval
import arrow.core.extensions.list.foldable.foldRight
import arrow.core.extensions.list.functor.tupleLeft
import arrow.core.toT
import arrow.extension
import arrow.recursion.hylo
import arrow.recursion.typeclasses.Birecursive
import arrow.syntax.collections.tail
import arrow.syntax.function.andThen
import arrow.typeclasses.Functor
import arrow.typeclasses.Monoid
import arrow.typeclasses.Semigroup
import pretty.doc.birecursive.birecursive
import pretty.doc.functor.functor
import pretty.doc.monoid.monoid
import pretty.doc.semigroup.semigroup
import pretty.docf.functor.functor
import pretty.docf.functor.map
import pretty.simpledoc.birecursive.birecursive
import pretty.simpledocf.functor.functor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// implements this pretty printer by https://homepages.inf.ed.ac.uk/wadler/papers/prettier/prettier.pdf
// TODO check out https://jyp.github.io/posts/towards-the-prettiest-printer.html#fnref1 for something even better?

class ForDocF private constructor()
typealias DocFOf<A, F> = Kind<DocFPartialOf<A>, F>
typealias DocFPartialOf<A> = Kind<ForDocF, A>

inline fun <A, F> DocFOf<A, F>.fix(): DocF<A, F> = this as DocF<A, F>

sealed class DocF<A, F> : DocFOf<A, F> {
    class Nil<A, F> : DocF<A, F>()
    class Fail<A, F> : DocF<A, F>()
    data class Text<A, F>(val str: String) : DocF<A, F>()
    data class Line<A, F>(val empty: Boolean) : DocF<A, F>()
    data class Union<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Combined<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Nest<A, F>(val i: Int, val doc: F) : DocF<A, F>()
    data class Column<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class Nesting<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class FlatAlt<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Annotated<A, F>(val ann: A, val doc: F) : DocF<A, F>()

    fun <B> map(f: (F) -> B): DocF<A, B> = when (this) {
        is Nil -> Nil()
        is Fail -> Fail()
        is Text -> Text(str)
        is Line -> Line(empty)
        is Combined -> Combined(f(l), f(r))
        is Nest -> Nest(i, f(doc))
        is Column -> Column(doc andThen f)
        is Nesting -> Nesting(doc andThen f)
        is Union -> Union(f(l), f(r))
        is FlatAlt -> FlatAlt(f(l), f(r))
        is Annotated -> Annotated(ann, f(doc))
    }

    companion object
}

@extension
interface DocFFunctor<ANN> : Functor<DocFPartialOf<ANN>> {
    override fun <A, B> Kind<DocFPartialOf<ANN>, A>.map(f: (A) -> B): Kind<DocFPartialOf<ANN>, B> =
        fix().map(f)
}

class ForDoc private constructor()
typealias DocOf<A> = Kind<ForDoc, A>

inline fun <A> DocOf<A>.fix(): Doc<A> = this as Doc<A>

data class Doc<A>(val unDoc: DocF<A, Doc<A>>) : DocOf<A> {

    operator fun plus(other: Doc<A>): Doc<A> = Doc.semigroup<A>().run {
        this@Doc.combine(other)
    }

    companion object
}

@extension
interface DocFunctor : Functor<ForDoc> {
    override fun <A, B> Kind<ForDoc, A>.map(f: (A) -> B): Kind<ForDoc, B> =
        fix().hylo({
            when (val dF = it.fix()) {
                is DocF.Annotated -> Doc(DocF.Annotated(f(dF.ann), dF.doc.fix()))
                else -> it.fix() as Kind<ForDoc, B>
                // This is not unsafe because hylo traversed all Annotated already and
                //  all remaining patterns have A as a phantom param
            }
        }, { it.unDoc }, DocF.functor())
}

@extension
interface DocBirecursive<A> : Birecursive<Doc<A>, DocFPartialOf<A>> {
    override fun FF(): Functor<DocFPartialOf<A>> = DocF.functor()
    override fun Kind<DocFPartialOf<A>, Doc<A>>.embedT(): Doc<A> = Doc(this.fix())
    override fun Doc<A>.projectT(): Kind<DocFPartialOf<A>, Doc<A>> = unDoc
}

@extension
interface DocSemigroup<A> : Semigroup<Doc<A>> {
    override fun Doc<A>.combine(b: Doc<A>): Doc<A> = Doc(DocF.Combined(this, b))
}

@extension
interface DocMonoid<A> : Monoid<Doc<A>>, DocSemigroup<A> {
    override fun empty(): Doc<A> = Doc<A>(DocF.Nil())
}

class ForSimpleDocF private constructor()
typealias SimpleDocFOf<A, F> = Kind<SimpleDocFPartialOf<A>, F>
typealias SimpleDocFPartialOf<A> = Kind<ForSimpleDocF, A>

inline fun <A, F> SimpleDocFOf<A, F>.fix(): SimpleDocF<A, F> = this as SimpleDocF<A, F>

sealed class SimpleDocF<A, F> : SimpleDocFOf<A, F> {
    class NilF<A, F> : SimpleDocF<A, F>()
    data class Line<A, F>(val i: Int, val doc: F) : SimpleDocF<A, F>()
    data class Text<A, F>(val str: String, val doc: F) : SimpleDocF<A, F>()
    data class AddAnnotation<A, F>(val ann: A, val doc: F) : SimpleDocF<A, F>()
    data class RemoveAnnotation<A, F>(val doc: F) : SimpleDocF<A, F>()

    companion object
}

@extension
interface SimpleDocFFunctor<C> : Functor<SimpleDocFPartialOf<C>> {
    override fun <A, B> Kind<SimpleDocFPartialOf<C>, A>.map(f: (A) -> B): Kind<SimpleDocFPartialOf<C>, B> =
        when (val dF = fix()) {
            is SimpleDocF.NilF -> SimpleDocF.NilF()
            is SimpleDocF.Line -> SimpleDocF.Line(dF.i, f(dF.doc))
            is SimpleDocF.Text -> SimpleDocF.Text(dF.str, f(dF.doc))
            is SimpleDocF.AddAnnotation -> SimpleDocF.AddAnnotation(dF.ann, f(dF.doc))
            is SimpleDocF.RemoveAnnotation -> SimpleDocF.RemoveAnnotation(f(dF.doc))
        }
}

class ForSimpleDoc private constructor()
typealias SimpleDocOf<A> = Kind<ForSimpleDoc, A>

inline fun <A> SimpleDocOf<A>.fix(): SimpleDoc<A> = this as SimpleDoc<A>

data class SimpleDoc<A>(val unDoc: SimpleDocF<A, SimpleDoc<A>>) : SimpleDocOf<A> {

    operator fun plus(other: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc.birecursive<A>().run {
        cata {
            when (val dF = it.fix()) {
                is SimpleDocF.NilF -> other
                else -> SimpleDoc(dF) // TODO is this correct?
            }
        }
    }

    companion object {
        fun <A> nil(): SimpleDoc<A> = SimpleDoc(SimpleDocF.NilF())
        fun <A> text(str: String, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(SimpleDocF.Text(str, next))
        fun <A> line(i: Int, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(SimpleDocF.Line(i, next))
        fun <A> addAnnotation(ann: A, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(SimpleDocF.AddAnnotation(ann, next))
        fun <A> removeAnnotation(next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(SimpleDocF.RemoveAnnotation(next))
    }
}

@extension
interface SimpleDocBirecursive<A> : Birecursive<SimpleDoc<A>, SimpleDocFPartialOf<A>> {
    override fun FF(): Functor<SimpleDocFPartialOf<A>> = SimpleDocF.functor()
    override fun Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>>.embedT(): SimpleDoc<A> = SimpleDoc(this.fix())
    override fun SimpleDoc<A>.projectT(): Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>> = unDoc
}

fun <A> SimpleDoc<A>.layout(): String = SimpleDoc.birecursive<A>().run {
    cata {
        when (val dF = it.fix()) {
            is SimpleDocF.NilF -> ""
            is SimpleDocF.Text -> dF.str + dF.doc
            is SimpleDocF.Line -> "\n" + spaces(dF.i) + dF.doc
            is SimpleDocF.AddAnnotation -> dF.doc
            is SimpleDocF.RemoveAnnotation -> dF.doc
        }
    }
}

fun <A> Doc<A>.best(w: Int, r: Float): SimpleDoc<A> = be(w, ribbonW(w, r), 0, 0, Step.Cons(0, this, Step.Empty()))

internal fun ribbonW(w: Int, r: Float): Int = max(0, min(w, round(w * r).toInt()))

sealed class Step<A> {
    class Empty<A> : Step<A>()
    data class Cons<A>(val i: Int, val el: Doc<A>, val tail: Step<A>): Step<A>()
    data class UndoAnnotation<A>(val tail: Step<A>): Step<A>()
}

// TODO recursion scheme?
// Theoretically this is a refold from Doc<A> to SimpleDoc but over what functor?
//  Can't use the SimpleDoc functor because if we match like nest as the first el we cannot return a good SimpleDocF element
internal fun <A> be(w: Int, r: Int, n: Int, k: Int, ls: Step<A>): SimpleDoc<A> = when (ls) {
    is Step.Empty -> SimpleDoc.nil()
    is Step.UndoAnnotation -> SimpleDoc.removeAnnotation(be(w, r, n, k, ls.tail))
    is Step.Cons -> when (val dF = ls.el.unDoc) {
        is DocF.Fail -> TODO()
        is DocF.Nil -> be(w, r, n, k, ls.tail)
        is DocF.Combined -> be(w, r, n, k, Step.Cons(ls.i, dF.l, Step.Cons(ls.i, dF.r, ls.tail)))
        is DocF.Nest -> be(w, r, n, k, Step.Cons(ls.i + dF.i, dF.doc, ls.tail))
        is DocF.Text -> SimpleDoc(SimpleDocF.Text(dF.str, be(w, r, n, k + dF.str.length, ls.tail)))
        is DocF.Line -> SimpleDoc(SimpleDocF.Line(ls.i, be(w, r, ls.i, ls.i, ls.tail)))
        is DocF.Union -> nicest(
            w, r,
            n, k,
            be(w, r, n, k, Step.Cons(ls.i, dF.l, ls.tail)),
            be(w, r, n, k, Step.Cons(ls.i, dF.r, ls.tail))
        )
        is DocF.Nesting -> be(w, r, n, k, Step.Cons(ls.i, dF.doc(ls.i), ls.tail))
        is DocF.Column -> be(w, r, n, k, Step.Cons(ls.i, dF.doc(k), ls.tail))
        is DocF.Annotated -> SimpleDoc.addAnnotation(
            dF.ann,
            be(w, r, n, k, Step.Cons(ls.i, dF.doc, Step.UndoAnnotation(ls.tail)))
        )
        is DocF.FlatAlt -> be(w, r, n, k, Step.Cons(ls.i, dF.l, ls.tail))
    }
}

fun <A> nicest(w: Int, r: Int, n: Int, k: Int, x: SimpleDoc<A>, y: SimpleDoc<A>): SimpleDoc<A> =
    if (x.fits(min(w - k, r - k + n))) x else y

fun <A> SimpleDoc<A>.fits(w: Int): Boolean =
    SimpleDoc.birecursive<A>().run {
        this@fits.cata<(Int) -> Boolean> {
            { i: Int ->
                i >= 0 && when (val dF = it.fix()) {
                    is SimpleDocF.Text -> dF.doc(i - dF.str.length)
                    else -> true
                }
            }
        }(w)
    }

fun <A> Doc<A>.renderPretty(): SimpleDoc<A> = best(80, 0.4F)

fun <A> Doc<A>.pretty(maxWidth: Int, ribbonWidth: Float): String = best(maxWidth, ribbonWidth).layout()

// primitives
fun <A> nil(): Doc<A> = Doc.monoid<A>().empty()

fun <A> String.text(): Doc<A> = Doc<A>(DocF.Text(this))

fun <A> line(): Doc<A> = Doc<A>(DocF.Line(false))

fun <A> lineBreak(): Doc<A> = Doc(DocF.Line(true))

fun <A> softLine(): Doc<A> = line<A>().group()

fun <A> softLineBreak(): Doc<A> = lineBreak<A>().group()

fun <A> Doc<A>.nest(i: Int): Doc<A> = Doc<A>(DocF.Nest(i, this))

fun <A> Doc<A>.group(): Doc<A> = Doc<A>(DocF.Union(this.flatten(), this))

fun <A> column(f: (Int) -> Doc<A>): Doc<A> = Doc<A>(DocF.Column(f))

fun <A> nesting(f: (Int) -> Doc<A>): Doc<A> = Doc<A>(DocF.Nesting(f))

fun <A> Doc<A>.flatAlt(other: Doc<A>): Doc<A> = Doc<A>(DocF.FlatAlt(this, other))

fun <A> Doc<A>.annotate(ann: A): Doc<A> = Doc(DocF.Annotated(ann, this))

fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B> = Doc.functor().run {
    map(f).fix()
}

fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B> = Doc.birecursive<A>().run {
    cata {
        when (val dF = it.fix()) {
            is DocF.Annotated -> f(dF.ann).foldRight(Eval.now(dF.doc)) { a, acc ->
                acc.map { Doc(DocF.Annotated(a, it)) }
            }.value()
            else -> Doc(it as DocF<B, Doc<B>>) // should be a safe cast
        }
    }
}

// combinators
fun <A> Doc<A>.fillBreak(i: Int): Doc<A> = width { w ->
    if (w > i) lineBreak<A>().nest(i) else spaces(i - w).text()
}

fun <A> Doc<A>.fill(i: Int): Doc<A> = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A> = column { k1 -> this + column { k2 -> f(k2 - k1) } }

fun <A> Doc<A>.indent(i: Int): Doc<A> = (spaces(i).text<A>() + this).hang(i)

fun <A> Doc<A>.hang(i: Int): Doc<A> = nest(i).align()

fun <A> Doc<A>.align(): Doc<A> = column { k ->
    nesting { i -> this.nest(k - i) }
}

fun <A> Doc<A>.flatten(): Doc<A> = Doc.birecursive<A>().run {
    para {
        // This is why I love recursion schemes!
        when (val dF = it.fix()) {
            is DocF.Line -> if (dF.empty) nil() else " ".text()
            is DocF.Union -> dF.l.b
            is DocF.FlatAlt -> dF.r.a
            else -> Doc(it.map { it.b })
        }
    }
}

fun spaces(i: Int): String =
    if (i < 0) ""
    else (0 until i).joinToString("") { " " }

infix fun <A> Doc<A>.spaced(d: Doc<A>): Doc<A> = this + " ".text() + d

infix fun <A> Doc<A>.line(d: Doc<A>): Doc<A> = this + line() + d
infix fun <A> Doc<A>.softLine(d: Doc<A>): Doc<A> = this + softLine() + d
infix fun <A> Doc<A>.lineBreak(d: Doc<A>): Doc<A> = this + lineBreak() + d
infix fun <A> Doc<A>.softLineBreak(d: Doc<A>): Doc<A> = this + softLineBreak() + d

fun <A> List<Doc<A>>.list(): Doc<A> = encloseSep(lBracket(), rBracket(), comma())
fun <A> List<Doc<A>>.tupled(): Doc<A> = encloseSep(lParen(), rParen(), comma())
fun <A> List<Doc<A>>.semiBraces(): Doc<A> = encloseSep(lBrace(), rBrace(), comma())

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

fun <A> lBracket(): Doc<A> = "[".text()
fun <A> rBracket(): Doc<A> = "]".text()
fun <A> lParen(): Doc<A> = "(".text()
fun <A> rParen(): Doc<A> = ")".text()
fun <A> lBrace(): Doc<A> = "{".text()
fun <A> rBrace(): Doc<A> = "}".text()
fun <A> lAngle(): Doc<A> = "<".text()
fun <A> rAngle(): Doc<A> = ">".text()

fun <A> comma(): Doc<A> = ",".text()
fun <A> space(): Doc<A> = " ".text()
fun <A> sQuote(): Doc<A> = "\'".text()
fun <A> dQuote(): Doc<A> = "\"".text()
fun <A> semiColon(): Doc<A> = ";".text()
fun <A> colon(): Doc<A> = ":".text()
fun <A> dot(): Doc<A> = ".".text()
fun <A> backslash(): Doc<A> = "\\".text()
fun <A> equals(): Doc<A> = "=".text()
fun <A> pipe(): Doc<A> = "|".text()

fun <A> String.doc(): Doc<A> = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        fst.text<A>() + line() + if (fst.length + 1 < length) substring(fst.length + 1).doc() else nil()
    }
}

fun <A> Boolean.doc(): Doc<A> = toString().text()
fun <A> Byte.doc(): Doc<A> = toString().text()
fun <A> Short.doc(): Doc<A> = toString().text()
fun <A> Int.doc(): Doc<A> = toString().text()
fun <A> Long.doc(): Doc<A> = toString().text()
fun <A> Float.doc(): Doc<A> = toString().text()
fun <A> Double.doc(): Doc<A> = toString().text()
