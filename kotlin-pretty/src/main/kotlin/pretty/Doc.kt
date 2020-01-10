package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.*
import arrow.core.extensions.option.applicative.applicative
import arrow.extension
import arrow.free.*
import arrow.free.extensions.free.monad.monad
import arrow.recursion.coelgotM
import arrow.recursion.elgotM
import arrow.recursion.typeclasses.Birecursive
import arrow.typeclasses.*
import pretty.doc.birecursive.birecursive
import pretty.doc.semigroup.semigroup
import pretty.docf.functor.functor

class ForDocF private constructor()
typealias DocFOf<A, F> = Kind<DocFPartialOf<A>, F>
typealias DocFPartialOf<A> = Kind<ForDocF, A>

inline fun <A, F> DocFOf<A, F>.fix(): DocF<A, F> = this as DocF<A, F>

sealed class DocF<out A, out F> : DocFOf<A, F> {
    object Nil : DocF<Nothing, Nothing>()
    object Fail : DocF<Nothing, Nothing>()
    object Line : DocF<Nothing, Nothing>()
    data class Text<A>(val str: String) : DocF<A, Nothing>()
    data class Union<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Combined<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Nest<A, F>(val i: Int, val doc: F) : DocF<A, F>()
    data class Column<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class Nesting<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class FlatAlt<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Annotated<A, F>(val ann: A, val doc: F) : DocF<A, F>()
    data class WithPageWidth<A, F>(val doc: (PageWidth) -> F) : DocF<A, F>()

    fun <B> map(f: (F) -> B): DocF<A, B> = when (this) {
        is Nil -> Nil
        is Fail -> Fail
        is Text -> Text(str)
        is Line -> Line
        is Combined -> Combined(f(l), f(r))
        is Nest -> Nest(i, f(doc))
        is Column -> Column(AndThen(doc).andThen(f))
        is Nesting -> Nesting(AndThen(doc).andThen(f))
        is Union -> Union(f(l), f(r))
        is FlatAlt -> FlatAlt(f(l), f(r))
        is Annotated -> Annotated(ann, f(doc))
        is WithPageWidth -> WithPageWidth(AndThen(doc).andThen(f))
    }

    companion object
}

@extension
interface DocFFunctor<ANN> : Functor<DocFPartialOf<ANN>> {
    override fun <A, B> Kind<DocFPartialOf<ANN>, A>.map(f: (A) -> B): Kind<DocFPartialOf<ANN>, B> =
        fix().map(f)
}

@extension
interface DocFBifunctor : Bifunctor<ForDocF> {
    override fun <A, B, C, D> Kind2<ForDocF, A, B>.bimap(fl: (A) -> C, fr: (B) -> D): Kind2<ForDocF, C, D> =
        when (val dF = fix()) {
            is DocF.Nil -> DocF.Nil
            is DocF.Fail -> DocF.Fail
            is DocF.Text -> DocF.Text(dF.str)
            is DocF.Line -> DocF.Line
            is DocF.Combined -> DocF.Combined(fr(dF.l), fr(dF.r))
            is DocF.Nest -> DocF.Nest(dF.i, fr(dF.doc))
            is DocF.Column -> DocF.Column(AndThen(dF.doc).andThen(fr))
            is DocF.Nesting -> DocF.Nesting(AndThen(dF.doc).andThen(fr))
            is DocF.Union -> DocF.Union(fr(dF.l), fr(dF.r))
            is DocF.FlatAlt -> DocF.FlatAlt(fr(dF.l), fr(dF.r))
            is DocF.Annotated -> DocF.Annotated(fl(dF.ann), fr(dF.doc))
            is DocF.WithPageWidth -> DocF.WithPageWidth(AndThen(dF.doc).andThen(fr))
        }
}

class ForDoc private constructor()
typealias DocOf<A> = Kind<ForDoc, A>

inline fun <A> DocOf<A>.fix(): Doc<A> = this as Doc<A>

data class Doc<out A>(val unDoc: Eval<DocF<A, Doc<A>>>) : DocOf<A> {
    companion object
}

operator fun <A> Doc<A>.plus(other: Doc<A>): Doc<A> = Doc.semigroup<A>().run {
    this@plus.combine(other)
}

@extension
interface DocFunctor : Functor<ForDoc> {
    override fun <A, B> Kind<ForDoc, A>.map(f: (A) -> B): Kind<ForDoc, B> =
        TODO()
}

@extension
interface DocBirecursive<A> : Birecursive<Doc<A>, DocFPartialOf<A>> {
    override fun FF(): Functor<DocFPartialOf<A>> = DocF.functor()
    override fun Kind<DocFPartialOf<A>, Doc<A>>.embedT(): Doc<A> = Doc(Eval.now(fix()))
    override fun Doc<A>.projectT(): Kind<DocFPartialOf<A>, Doc<A>> = unDoc.value()
}

@extension
interface DocSemigroup<A> : Semigroup<Doc<A>> {
    override fun Doc<A>.combine(b: Doc<A>): Doc<A> = when (val dF = unDoc.value()) {
        is DocF.Text -> when (val dFF = b.unDoc.value()) {
            is DocF.Text -> Doc(Eval.now(DocF.Text(dF.str + dFF.str)))
            else -> Doc(Eval.now(DocF.Combined(this, b)))
        }
        else -> Doc(Eval.now(DocF.Combined(this, b)))
    }
}

@extension
interface DocMonoid<A> : Monoid<Doc<A>>, DocSemigroup<A> {
    override fun empty(): Doc<A> = Doc(Eval.now(DocF.Nil))
}

@extension
interface DocShow<A> : Show<Doc<A>> {
    override fun Doc<A>.show(): String = renderPretty().renderString()
}
