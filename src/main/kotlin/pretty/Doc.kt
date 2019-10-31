package pretty

import arrow.Kind
import arrow.Kind2
import arrow.extension
import arrow.recursion.hylo
import arrow.recursion.typeclasses.Birecursive
import arrow.syntax.function.andThen
import arrow.typeclasses.*
import pretty.doc.semigroup.semigroup
import pretty.docf.functor.functor

class ForDocF private constructor()
typealias DocFOf<A, F> = Kind<DocFPartialOf<A>, F>
typealias DocFPartialOf<A> = Kind<ForDocF, A>

inline fun <A, F> DocFOf<A, F>.fix(): DocF<A, F> = this as DocF<A, F>

sealed class DocF<A, F> : DocFOf<A, F> {
    class Nil<A, F> : DocF<A, F>()
    class Fail<A, F> : DocF<A, F>()
    class Line<A, F> : DocF<A, F>()
    data class Text<A, F>(val str: String) : DocF<A, F>()
    data class Union<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Combined<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Nest<A, F>(val i: Int, val doc: F) : DocF<A, F>()
    data class Column<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class Nesting<A, F>(val doc: (Int) -> F) : DocF<A, F>()
    data class FlatAlt<A, F>(val l: F, val r: F) : DocF<A, F>()
    data class Annotated<A, F>(val ann: A, val doc: F) : DocF<A, F>()
    data class WithPageWidth<A, F>(val doc: (PageWidth) -> F): DocF<A, F>()

    fun <B> map(f: (F) -> B): DocF<A, B> = when (this) {
        is Nil -> Nil()
        is Fail -> Fail()
        is Text -> Text(str)
        is Line -> Line()
        is Combined -> Combined(f(l), f(r))
        is Nest -> Nest(i, f(doc))
        is Column -> Column(doc andThen f)
        is Nesting -> Nesting(doc andThen f)
        is Union -> Union(f(l), f(r))
        is FlatAlt -> FlatAlt(f(l), f(r))
        is Annotated -> Annotated(ann, f(doc))
        is WithPageWidth -> WithPageWidth(doc andThen f)
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
    override fun <A, B, C, D> Kind2<ForDocF, A, B>.bimap(fl: (A) -> C, fr: (B) -> D): Kind2<ForDocF, C, D> = when (val dF = fix()) {
        is DocF.Nil -> DocF.Nil()
        is DocF.Fail -> DocF.Fail()
        is DocF.Text -> DocF.Text(dF.str)
        is DocF.Line -> DocF.Line()
        is DocF.Combined -> DocF.Combined(fr(dF.l), fr(dF.r))
        is DocF.Nest -> DocF.Nest(dF.i, fr(dF.doc))
        is DocF.Column -> DocF.Column(dF.doc andThen fr)
        is DocF.Nesting -> DocF.Nesting(dF.doc andThen fr)
        is DocF.Union -> DocF.Union(fr(dF.l), fr(dF.r))
        is DocF.FlatAlt -> DocF.FlatAlt(fr(dF.l), fr(dF.r))
        is DocF.Annotated -> DocF.Annotated(fl(dF.ann), fr(dF.doc))
        is DocF.WithPageWidth -> DocF.WithPageWidth(dF.doc andThen fr)
    }
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
                else -> Doc(it.fix() as DocF<B, Doc<B>>)
                // This is not unsafe because hylo traversed all Annotated already and
                //  all remaining patterns have A as a phantom param so this is a retag
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

@extension
interface DocShow<A> : Show<Doc<A>> {
    override fun Doc<A>.show(): String = renderPretty().renderString()
}
