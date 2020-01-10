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
    override fun <A, B> Kind<ForDoc, A>.map(f: (A) -> B): Kind<ForDoc, B> = fix().cata {
        when (it) {
            is DocF.Annotated -> Doc(Eval.now(DocF.Annotated(f(it.ann), it.doc.fix())))
            else -> Doc(Eval.now(it as DocF<B, Doc<B>>))
            // This is not unsafe because hylo traversed all Annotated already and
            //  all remaining patterns have A as a phantom param so this is a retag
        }
    }
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

// Fun times with kotlin ahead. This is an attempt to be able to use cataM with a stacksafe monad
//  to make it stacksafe. This needs a Traverse impl for DocF which is not possible, but using
//  AndThen + a cheaty traverse implementation it could work.
// This will only ever work with Free<ForFunction0>, do not use it anywhere else
private fun <C> cheatyTraverse() = object : Traverse<DocFPartialOf<C>> {
    override fun <A, B> Kind<DocFPartialOf<C>, A>.foldLeft(b: B, f: (B, A) -> B): B = TODO()
    override fun <A, B> Kind<DocFPartialOf<C>, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> = TODO()

    override fun <A, B> Kind<DocFPartialOf<C>, A>.map(f: (A) -> B): Kind<DocFPartialOf<C>, B> = DocF.functor<C>().run {
        map(f)
    }

    override fun <G, A, B> Kind<DocFPartialOf<C>, A>.traverse(
        AP: Applicative<G>,
        f: (A) -> Kind<G, B>
    ): Kind<G, Kind<DocFPartialOf<C>, B>> = AP.run {
        val isId = just(0) == Id(0)
        if (isId)
            when (val dF = fix()) {
                is DocF.Fail -> just(DocF.Fail)
                is DocF.Nil -> just(DocF.Nil)
                is DocF.Line -> just(DocF.Line)
                is DocF.Text -> just(DocF.Text(dF.str))
                is DocF.Nest -> f(dF.doc).map { DocF.Nest<C, B>(dF.i, it) }
                is DocF.Combined -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Combined<C, B>(l, r) }
                is DocF.Annotated -> f(dF.doc).map { DocF.Annotated(dF.ann, it) }
                is DocF.Union -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Union<C, B>(l, r) }
                is DocF.FlatAlt -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.FlatAlt<C, B>(l, r) }
                is DocF.Column -> Id(DocF.Column<C, B>(AndThen(dF.doc).andThen {
                    (f(it) as Id<B>).extract()
                })) as Kind<G, DocF<C, B>>
                is DocF.Nesting -> Id(DocF.Nesting<C, B>(AndThen(dF.doc).andThen {
                    (f(it) as Id<B>).extract()
                })) as Kind<G, DocF<C, B>>
                is DocF.WithPageWidth -> Id(DocF.WithPageWidth<C, B>(AndThen(dF.doc).andThen {
                    (f(it) as Id<B>).extract()
                })) as Kind<G, DocF<C, B>>
            }
        else
            when (val dF = fix()) {
                is DocF.Fail -> just(DocF.Fail)
                is DocF.Nil -> just(DocF.Nil)
                is DocF.Line -> just(DocF.Line)
                is DocF.Text -> just(DocF.Text(dF.str))
                is DocF.Nest -> f(dF.doc).map { DocF.Nest<C, B>(dF.i, it) }
                is DocF.Combined -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Combined<C, B>(l, r) }
                is DocF.Annotated -> f(dF.doc).map { DocF.Annotated(dF.ann, it) }
                is DocF.Union -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.Union<C, B>(l, r) }
                is DocF.FlatAlt -> map(f(dF.l), f(dF.r)) { (l, r) -> DocF.FlatAlt<C, B>(l, r) }
                is DocF.Column -> Trampoline.later {
                    DocF.Column<C, B>(AndThen(dF.doc).andThen {
                        (f(it) as TrampolineF<B>).fix().runT()
                    })
                } as Kind<G, DocF<C, B>>
                is DocF.Nesting -> Trampoline.later {
                    DocF.Nesting<C, B>(AndThen(dF.doc).andThen {
                        (f(it) as TrampolineF<B>).fix().runT()
                    })
                } as Kind<G, DocF<C, B>>
                is DocF.WithPageWidth -> Trampoline.later {
                    DocF.WithPageWidth<C, B>(AndThen(dF.doc).andThen {
                        (f(it) as TrampolineF<B>).fix().runT()
                    })
                } as Kind<G, DocF<C, B>>
            }
    }
}

fun <A> Doc<A>.changesUponFlattening(): Option<Doc<A>> {
    tailrec fun go(d: Doc<A>, cont: (Option<Doc<A>>) -> Option<Doc<A>>): Option<Doc<A>> =
        when (val dF = d.unDoc.value()) {
            is DocF.FlatAlt -> cont(dF.r.flatten().some())
            is DocF.Line -> cont(Doc<A>(Eval.now(DocF.Fail)).some())
            is DocF.Union -> go(dF.l, AndThen(cont).compose { it.orElse { dF.l.some() } })
            is DocF.Nest -> go(dF.doc, AndThen(cont).compose { it.map { Doc(Eval.now(DocF.Nest(dF.i, it))) } })
            is DocF.Annotated -> go(dF.doc, AndThen(cont).compose { it.map { Doc(Eval.now(DocF.Annotated(dF.ann, it))) } })

            is DocF.Column -> cont(Doc(Eval.now(DocF.Column(AndThen(dF.doc).andThen { it.flatten() }))).some())
            is DocF.Nesting -> cont(Doc(Eval.now(DocF.Nesting(AndThen(dF.doc).andThen { it.flatten() }))).some())
            is DocF.WithPageWidth -> cont(Doc(Eval.now(DocF.WithPageWidth(AndThen(dF.doc).andThen { it.flatten() }))).some())

            is DocF.Combined -> {
                val lEval = Eval.later { go(dF.l, ::identity) }
                val rEval = Eval.later { go(dF.r, ::identity) }
                lEval.flatMap { l -> l.fold({
                    rEval.map { r -> r.fold({ None }, { rD ->
                        Doc(Eval.now(DocF.Combined(dF.l, rD))).some()
                    }) }
                }, { lD ->
                    rEval.map { r -> r.fold({
                        Doc(Eval.now(DocF.Combined(lD, dF.r))).some()
                    }, { rD ->
                        Doc(Eval.now(DocF.Combined(lD, rD))).some()
                    }) }
                }) }.value().let(cont)
            }

            else -> cont(None)
        }

    return go(this, ::identity)

    /*
    return para<A, () -> Option<Doc<A>>> {
        {
            when (val dF = it) {
                is DocF.FlatAlt -> dF.r.a.flatten().some()
                is DocF.Line -> Doc<A>(DocF.Fail()).some()
                is DocF.Union -> dF.l.b().orElse { dF.l.a.some() }
                is DocF.Nest -> dF.doc.b().map { Doc<A>(DocF.Nest(dF.i, it)) }
                is DocF.Annotated -> dF.doc.b().map { Doc<A>(DocF.Annotated(dF.ann, it)) }

                is DocF.Column -> Doc<A>(DocF.Column(AndThen(dF.doc).andThen { it.a.flatten() })).some()
                is DocF.Nesting -> Doc<A>(DocF.Nesting(AndThen(dF.doc).andThen { it.a.flatten() })).some()
                is DocF.WithPageWidth -> Doc<A>(DocF.WithPageWidth(AndThen(dF.doc).andThen { it.a.flatten() })).some()

                is DocF.Combined -> dF.l.b().fold({
                    dF.r.b().fold({ None }, { r ->
                        Doc<A>(DocF.Combined(dF.l.a, r)).some()
                    })
                }, { l ->
                    dF.r.b().fold({
                        Doc<A>(DocF.Combined(l, dF.r.a)).some()
                    }, { r ->
                        Doc<A>(DocF.Combined(l, r)).some()
                    })
                })

                else -> None
            }
        }
    }.invoke()
     */
}

fun <A, B> Doc<A>.cata(f: (DocF<A, B>) -> B): B = Doc.birecursive<A>().run {
    cataM<FreePartialOf<ForFunction0>, B>(cheatyTraverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }
        .fix().runT()
}

fun <A, B> Doc<A>.para(f: (DocF<A, Tuple2<Doc<A>, B>>) -> B): B = Doc.birecursive<A>().run {
    paraM<FreePartialOf<ForFunction0>, B>(cheatyTraverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }
        .fix().runT()
}