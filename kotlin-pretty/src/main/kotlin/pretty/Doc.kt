package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.AndThen
import arrow.core.Eval
import arrow.core.extensions.eval.applicative.applicative
import arrow.extension
import arrow.typeclasses.*
import pretty.doc.semigroup.semigroup

class ForDocF private constructor()
typealias DocFOf<A, F> = Kind<DocFPartialOf<A>, F>
typealias DocFPartialOf<A> = Kind<ForDocF, A>

inline fun <A, F> DocFOf<A, F>.fix(): DocF<A, F> = this as DocF<A, F>

sealed class DocF<out A, out F> : DocFOf<A, F> {
    object Nil : DocF<Nothing, Nothing>()
    object Fail : DocF<Nothing, Nothing>()
    object Line : DocF<Nothing, Nothing>()
    data class Text<A>(val str: String) : DocF<A, Nothing>()
    data class Union<F>(val l: F, val r: F) : DocF<Nothing, F>()
    data class Combined<F>(val l: F, val r: F) : DocF<Nothing, F>()
    data class Nest<F>(val i: Int, val doc: F) : DocF<Nothing, F>()
    data class Column<F>(val doc: (Int) -> F) : DocF<Nothing, F>()
    data class Nesting<F>(val doc: (Int) -> F) : DocF<Nothing, F>()
    data class FlatAlt<F>(val l: F, val r: F) : DocF<Nothing, F>()
    data class Annotated<A, F>(val ann: A, val doc: F) : DocF<A, F>()
    data class WithPageWidth<F>(val doc: (PageWidth) -> F) : DocF<Nothing, F>()

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

    override fun toString(): String = pretty()

    companion object
}

operator fun <A> Doc<A>.plus(other: Doc<A>): Doc<A> = Doc.semigroup<A>().run {
    this@plus.combine(other)
}

@extension
interface DocFunctor : Functor<ForDoc> {
    override fun <A, B> Kind<ForDoc, A>.map(f: (A) -> B): Kind<ForDoc, B> = Doc<B>(fix().unDoc.map {
        when (it) {
            is DocF.Annotated -> DocF.Annotated(f(it.ann), it.doc.map(f).fix())
            is DocF.Union -> DocF.Union(it.l.map(f).fix(), it.r.map(f).fix())
            is DocF.Nest -> DocF.Nest(it.i, it.doc.map(f).fix())
            is DocF.FlatAlt -> DocF.FlatAlt(it.l.map(f).fix(), it.r.map(f).fix())
            is DocF.Nesting -> DocF.Nesting(AndThen(it.doc).andThen { it.map(f).fix() })
            is DocF.Column -> DocF.Column(AndThen(it.doc).andThen { it.map(f).fix() })
            is DocF.WithPageWidth -> DocF.WithPageWidth(AndThen(it.doc).andThen { it.map(f).fix() })
            is DocF.Combined -> DocF.Combined(it.l.map(f).fix(), it.r.map(f).fix())
            is DocF.Text -> DocF.Text(it.str)
            is DocF.Fail -> DocF.Fail
            is DocF.Nil -> DocF.Nil
            is DocF.Line -> DocF.Line
        }
    })
}

@extension
interface DocSemigroup<A> : Semigroup<Doc<A>> {
    override fun Doc<A>.combine(b: Doc<A>): Doc<A> = Doc(Eval.now(DocF.Combined(this, b)))
}

@extension
interface DocMonoid<A> : Monoid<Doc<A>>, DocSemigroup<A> {
    override fun empty(): Doc<A> = Doc(Eval.now(DocF.Nil))
}

@extension
interface DocShow<A> : Show<Doc<A>> {
    override fun Doc<A>.show(): String = renderPretty().renderString()
}

fun <A> Doc<A>.fuse(shallow: Boolean): Doc<A> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.Combined -> it.l.unDoc.flatMap { l ->
            when (l) {
                is DocF.Nil -> it.r.unDoc
                is DocF.Text -> it.r.unDoc.flatMap { r ->
                    when (r) {
                        is DocF.Nil -> Eval.now(l)
                        is DocF.Text -> Eval.now(DocF.Text(l.str + r.str))
                        is DocF.Combined -> r.l.unDoc.map { rl ->
                            when (rl) {
                                is DocF.Nil -> DocF.Combined(
                                    Doc(Eval.now(DocF.Text(l.str))), r.r
                                )
                                is DocF.Text -> DocF.Combined(
                                    Doc(Eval.now(DocF.Text(l.str + rl.str))), r.r
                                )
                                else -> DocF.Combined(it.l, it.r.fuse(shallow))
                            }
                        }
                        else -> Eval.now(DocF.Combined(it.l, it.r.fuse(shallow)))
                    }
                }
                is DocF.Combined -> l.r.unDoc.flatMap { lr ->
                    if (lr is DocF.Text)
                        Doc(Eval.now(DocF.Combined(l.l, Doc(Eval.now(DocF.Combined(l.r, it.r))))))
                            .fuse(shallow).unDoc
                    else Eval.now(it)
                }
                else -> Eval.now(it)
            }
        }
        is DocF.Nest ->
            if (it.i == 0) it.doc.unDoc
            else it.doc.unDoc.map { mn ->
                if (mn is DocF.Nest) DocF.Nest(it.i + mn.i, mn.doc)
                else mn
            }
        is DocF.Annotated -> it.doc.unDoc.map {
            if (it is DocF.Nil) DocF.Nil
            else it
        }
        is DocF.FlatAlt -> Eval.later {
            DocF.FlatAlt(it.l.fuse(shallow), it.r.fuse(shallow))
        }
        is DocF.Union -> Eval.later {
            DocF.Union(it.l.fuse(shallow), it.r.fuse(shallow))
        }
        else -> if (shallow) Eval.now(it) else when (it) {
            is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(AndThen(it.doc).andThen { it.fuse(false) }))
            is DocF.Nesting -> Eval.now(DocF.Nesting(AndThen(it.doc).andThen { it.fuse(false) }))
            is DocF.Column -> Eval.now(DocF.Column(AndThen(it.doc).andThen { it.fuse(false) }))
            else -> Eval.now(it)
        }
    }
})
