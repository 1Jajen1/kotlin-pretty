package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.AndThen
import arrow.core.Eval
import arrow.core.ForFunction0
import arrow.core.Tuple2
import arrow.extension
import arrow.free.*
import arrow.free.extensions.free.functor.functor
import arrow.free.extensions.free.monad.monad
import arrow.mtl.typeclasses.ComposedFunctor
import arrow.mtl.typeclasses.Nested
import arrow.mtl.typeclasses.unnest
import arrow.recursion.typeclasses.Birecursive
import arrow.syntax.function.andThen
import arrow.typeclasses.*
import pretty.doc.birecursive.birecursive
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
    data class WithPageWidth<A, F>(val doc: (PageWidth) -> F) : DocF<A, F>()

    fun <B> map(f: (F) -> B): DocF<A, B> = when (this) {
        is Nil -> Nil()
        is Fail -> Fail()
        is Text -> Text(str)
        is Line -> Line()
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
            is DocF.Nil -> DocF.Nil()
            is DocF.Fail -> DocF.Fail()
            is DocF.Text -> DocF.Text(dF.str)
            is DocF.Line -> DocF.Line()
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

data class Doc<A>(val unDoc: DocF<A, Doc<A>>) : DocOf<A> {

    operator fun plus(other: Doc<A>): Doc<A> = Doc.semigroup<A>().run {
        this@Doc.combine(other)
    }

    companion object
}

@extension
interface DocFunctor : Functor<ForDoc> {
    override fun <A, B> Kind<ForDoc, A>.map(f: (A) -> B): Kind<ForDoc, B> = fix().cata {
        when (it) {
            is DocF.Annotated -> Doc(DocF.Annotated(f(it.ann), it.doc.fix()))
            else -> Doc(it.fix() as DocF<B, Doc<B>>)
            // This is not unsafe because hylo traversed all Annotated already and
            //  all remaining patterns have A as a phantom param so this is a retag
        }
    }
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

// Fun times with kotlin ahead. This is an attempt to be able to use cataM with a stacksafe monad
//  to make it stacksafe. This needs a Traverse impl for DocF which is not possible, but using
//  AndThen + a cheaty traverse implementation it could work.
// This will only ever work with Free<ForFunction0>, do not use it anywhere else
private fun <C>cheatyTraverse() = object: Traverse<DocFPartialOf<C>> {
    override fun <A, B> Kind<DocFPartialOf<C>, A>.foldLeft(b: B, f: (B, A) -> B): B = TODO()
    override fun <A, B> Kind<DocFPartialOf<C>, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> = TODO()

    override fun <A, B> Kind<DocFPartialOf<C>, A>.map(f: (A) -> B): Kind<DocFPartialOf<C>, B> = DocF.functor<C>().run {
        map(f)
    }

    override fun <G, A, B> Kind<DocFPartialOf<C>, A>.traverse(
        AP: Applicative<G>,
        f: (A) -> Kind<G, B>
    ): Kind<G, Kind<DocFPartialOf<C>, B>> = AP.run {
        when (val dF = fix()) {
            is DocF.Fail -> just(DocF.Fail())
            is DocF.Nil -> just(DocF.Nil())
            is DocF.Line -> just(DocF.Line())
            is DocF.Text -> just(DocF.Text(dF.str))
            is DocF.Nest -> f(dF.doc).map { DocF.Nest<C, B>(dF.i, it) }
            is DocF.Combined -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Combined<C, B>(l, r) }
            is DocF.Annotated -> f(dF.doc).map { DocF.Annotated(dF.ann, it) }
            is DocF.Union -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Union<C, B>(l, r) }
            is DocF.FlatAlt -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.FlatAlt<C, B>(l, r) }
            // bs code that will only work with Free
            is DocF.Column -> Trampoline.later {
                DocF.Column<C, B>(AndThen(dF.doc).andThen { (f(it) as Kind<FreePartialOf<ForFunction0>, B>).fix().runT() })
            } as Kind<G, Kind<DocFPartialOf<C>, B>>
            is DocF.Nesting -> Trampoline.later {
                DocF.Nesting<C, B>(AndThen(dF.doc).andThen { (f(it) as Kind<FreePartialOf<ForFunction0>, B>).fix().runT() })
            } as Kind<G, Kind<DocFPartialOf<C>, B>>
            is DocF.WithPageWidth -> Trampoline.later {
                DocF.WithPageWidth<C, B>(AndThen(dF.doc).andThen { (f(it) as Kind<FreePartialOf<ForFunction0>, B>).fix().runT() })
            } as Kind<G, Kind<DocFPartialOf<C>, B>>
        }
    }
}

fun <A, B> Doc<A>.cata(f: (DocF<A, B>) -> B): B = Doc.birecursive<A>().run {
    cataM<FreePartialOf<ForFunction0>, B>(cheatyTraverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }
        .fix().runT()
}

fun <A, B> Doc<A>.para(f: (DocF<A, Tuple2<Doc<A>, B>>) -> B): B = Doc.birecursive<A>().run {
    paraM<FreePartialOf<ForFunction0>, B>(cheatyTraverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }
        .fix().runT()
}