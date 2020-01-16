package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.*
import arrow.core.extensions.id.applicative.applicative
import arrow.core.extensions.monoid
import arrow.extension
import arrow.free.*
import arrow.free.extensions.free.monad.monad
import arrow.recursion.typeclasses.Birecursive
import arrow.syntax.collections.tail
import arrow.typeclasses.*
import pretty.simpledoc.birecursive.birecursive
import pretty.simpledocf.functor.functor
import pretty.simpledocf.traverse.traverse

class ForSimpleDocF private constructor()
typealias SimpleDocFOf<A, F> = Kind<SimpleDocFPartialOf<A>, F>
typealias SimpleDocFPartialOf<A> = Kind<ForSimpleDocF, A>

inline fun <A, F> SimpleDocFOf<A, F>.fix(): SimpleDocF<A, F> = this as SimpleDocF<A, F>

sealed class SimpleDocF<out A, out F> : SimpleDocFOf<A, F> {
    object Fail : SimpleDocF<Nothing, Nothing>()
    object Nil : SimpleDocF<Nothing, Nothing>()
    data class Line<F>(val i: Int, val doc: F) : SimpleDocF<Nothing, F>()
    data class Text<F>(val str: String, val doc: F) : SimpleDocF<Nothing, F>()
    data class AddAnnotation<A, F>(val ann: A, val doc: F) : SimpleDocF<A, F>()
    data class RemoveAnnotation<F>(val doc: F) : SimpleDocF<Nothing, F>()

    companion object
}

@extension
interface SimpleDocFFunctor<C> : Functor<SimpleDocFPartialOf<C>> {
    override fun <A, B> Kind<SimpleDocFPartialOf<C>, A>.map(f: (A) -> B): Kind<SimpleDocFPartialOf<C>, B> =
        when (val dF = fix()) {
            is SimpleDocF.Fail -> SimpleDocF.Fail
            is SimpleDocF.Nil -> SimpleDocF.Nil
            is SimpleDocF.Line -> SimpleDocF.Line(dF.i, f(dF.doc))
            is SimpleDocF.Text -> SimpleDocF.Text(dF.str, f(dF.doc))
            is SimpleDocF.AddAnnotation -> SimpleDocF.AddAnnotation(dF.ann, f(dF.doc))
            is SimpleDocF.RemoveAnnotation -> SimpleDocF.RemoveAnnotation(f(dF.doc))
        }
}

@extension
interface SimpleDocFTraverse<C> : Traverse<SimpleDocFPartialOf<C>> {
    override fun <A, B> Kind<SimpleDocFPartialOf<C>, A>.foldLeft(b: B, f: (B, A) -> B): B =
        when (val it = fix()) {
            is SimpleDocF.Fail -> b
            is SimpleDocF.Nil -> b
            is SimpleDocF.Line -> f(b, it.doc)
            is SimpleDocF.Text -> f(b, it.doc)
            is SimpleDocF.AddAnnotation -> f(b, it.doc)
            is SimpleDocF.RemoveAnnotation -> f(b, it.doc)
        }

    override fun <A, B> Kind<SimpleDocFPartialOf<C>, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
        when (val it = fix()) {
            is SimpleDocF.Fail -> lb
            is SimpleDocF.Nil -> lb
            is SimpleDocF.Line -> f(it.doc, lb)
            is SimpleDocF.Text -> f(it.doc, lb)
            is SimpleDocF.AddAnnotation -> f(it.doc, lb)
            is SimpleDocF.RemoveAnnotation -> f(it.doc, lb)
        }

    override fun <G, A, B> Kind<SimpleDocFPartialOf<C>, A>.traverse(
        AP: Applicative<G>,
        f: (A) -> Kind<G, B>
    ): Kind<G, Kind<SimpleDocFPartialOf<C>, B>> = AP.run {
        when (val it = fix()) {
            is SimpleDocF.Fail -> just(SimpleDocF.Fail)
            is SimpleDocF.Nil -> just(SimpleDocF.Nil)
            is SimpleDocF.Line -> f(it.doc).map { b -> SimpleDocF.Line(it.i, b) }
            is SimpleDocF.Text -> f(it.doc).map { b -> SimpleDocF.Text(it.str, b) }
            is SimpleDocF.AddAnnotation -> f(it.doc).map { b -> SimpleDocF.AddAnnotation(it.ann, b) }
            is SimpleDocF.RemoveAnnotation -> f(it.doc).map { b -> SimpleDocF.RemoveAnnotation(b) }
        }
    }
}

@extension
interface SimpleDocFBifunctor : Bifunctor<ForSimpleDocF> {
    override fun <A, B, C, D> Kind2<ForSimpleDocF, A, B>.bimap(fl: (A) -> C, fr: (B) -> D): Kind2<ForSimpleDocF, C, D> =
        when (val dF = fix()) {
            is SimpleDocF.Fail -> SimpleDocF.Fail
            is SimpleDocF.Nil -> SimpleDocF.Nil
            is SimpleDocF.Line -> SimpleDocF.Line(dF.i, fr(dF.doc))
            is SimpleDocF.Text -> SimpleDocF.Text(dF.str, fr(dF.doc))
            is SimpleDocF.AddAnnotation -> SimpleDocF.AddAnnotation(fl(dF.ann), fr(dF.doc))
            is SimpleDocF.RemoveAnnotation -> SimpleDocF.RemoveAnnotation(fr(dF.doc))
        }
}

class ForSimpleDoc private constructor()
typealias SimpleDocOf<A> = Kind<ForSimpleDoc, A>

inline fun <A> SimpleDocOf<A>.fix(): SimpleDoc<A> = this as SimpleDoc<A>

data class SimpleDoc<out A>(val unDoc: Eval<SimpleDocF<A, SimpleDoc<A>>>) : SimpleDocOf<A> {

    override fun toString(): String = "SimpleDoc(unDoc=${unDoc.value()})"

    companion object {
        fun fail(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Fail))
        fun nil(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Nil))
        fun <A> text(str: String, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Text(str, next)))
        fun <A> line(i: Int, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Line(i, next)))
        fun <A> addAnnotation(ann: A, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.AddAnnotation(ann, next)))
        fun <A> removeAnnotation(next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.RemoveAnnotation(next)))
    }
}

operator fun <A> SimpleDoc<A>.plus(b: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(unDoc.map {
    when (it) {
        is SimpleDocF.Nil -> b.unDoc.value()
        is SimpleDocF.Fail -> SimpleDocF.Fail
        is SimpleDocF.Text -> it.copy(doc = it.doc + b)
        is SimpleDocF.Line -> it.copy(doc = it.doc + b)
        is SimpleDocF.AddAnnotation -> it.copy(doc = it.doc + b)
        is SimpleDocF.RemoveAnnotation -> it.copy(doc = it.doc + b)
    }
})

@extension
interface SimpleDocEq<A> : Eq<SimpleDoc<A>> {
    fun EQA(): Eq<A>
    override fun SimpleDoc<A>.eqv(b: SimpleDoc<A>): Boolean = when (val dF = unDoc.value()) {
        is SimpleDocF.Fail -> b.unDoc.value() is SimpleDocF.Fail
        is SimpleDocF.Nil -> b.unDoc.value() is SimpleDocF.Nil
        is SimpleDocF.Text ->
            b.unDoc.value().let { b ->
                b is SimpleDocF.Text &&
                        dF.str == b.str && dF.doc.eqv(b.doc)
            }
        is SimpleDocF.AddAnnotation -> b.unDoc.value().let { b ->
            b is SimpleDocF.AddAnnotation &&
                    EQA().run { dF.ann.eqv(b.ann) } &&
                    dF.doc.eqv(b.doc)
        }
        is SimpleDocF.RemoveAnnotation -> b.unDoc.value().let { b ->
            b is SimpleDocF.RemoveAnnotation &&
                    dF.doc.eqv(b.doc)
        }
        is SimpleDocF.Line -> b.unDoc.value().let { b ->
            b is SimpleDocF.Line &&
                    dF.i == b.i &&
                    dF.doc.eqv(b.doc)
        }
    }
}

@extension
interface SimpleDocShow<A> : Show<SimpleDoc<A>> {
    override fun SimpleDoc<A>.show(): String = renderString()
}

@extension
interface SimpleDocFunctor : Functor<ForSimpleDoc> {
    override fun <A, B> Kind<ForSimpleDoc, A>.map(f: (A) -> B): Kind<ForSimpleDoc, B> =
        SimpleDoc(fix().unDoc.map {
            when (it) {
                is SimpleDocF.AddAnnotation ->
                    SimpleDocF.AddAnnotation(f(it.ann), it.doc.map(f).fix())
                is SimpleDocF.RemoveAnnotation ->
                    SimpleDocF.RemoveAnnotation(it.doc.map(f).fix())
                is SimpleDocF.Line -> SimpleDocF.Line(it.i, it.doc.map(f).fix())
                is SimpleDocF.Text -> SimpleDocF.Text(it.str, it.doc.map(f).fix())
                is SimpleDocF.Nil -> SimpleDocF.Nil
                is SimpleDocF.Fail -> SimpleDocF.Fail
            }
        })
}

@extension
interface SimpleDocBirecursive<A> : Birecursive<SimpleDoc<A>, SimpleDocFPartialOf<A>> {
    override fun FF(): Functor<SimpleDocFPartialOf<A>> = SimpleDocF.functor()
    override fun Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>>.embedT(): SimpleDoc<A> = SimpleDoc(Eval.now(this.fix()))
    override fun SimpleDoc<A>.projectT(): Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>> = unDoc.value()
}

// TODO Benchmark this against cata, a version with a string builder and a direct version
fun <A> SimpleDoc<A>.renderString(): String =
    renderDecorated(String.monoid(), ::identity, { "" }, { "" })

// TODO Benchmark this against cata and a version without renderDecoratedA
fun <A, B> SimpleDoc<A>.renderDecorated(
    MO: Monoid<B>,
    text: (String) -> B,
    addAnnotation: (A) -> B,
    removeAnnotation: (A) -> B
): B = renderDecoratedA(Id.applicative(), MO, text.andThen(::Id), addAnnotation.andThen(::Id), removeAnnotation.andThen(::Id))
    .value()

// TODO Benchmark this against cata
fun <F, A, B> SimpleDoc<A>.renderDecoratedA(
    AP: Applicative<F>,
    MO: Monoid<B>,
    text: (String) -> Kind<F, B>,
    addAnnotation: (A) -> Kind<F, B>,
    removeAnnotation: (A) -> Kind<F, B>
): Kind<F, B> {
    tailrec fun SimpleDoc<A>.go(xs: List<A>, cont: (Kind<F, B>) -> Kind<F, B>): Kind<F, B> = when (val dF = unDoc.value()) {
        is SimpleDocF.Nil -> cont(AP.just(MO.empty()))
        is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
        is SimpleDocF.AddAnnotation -> dF.doc.go(listOf(dF.ann) + xs, AndThen(cont).compose {
            AP.map(addAnnotation(dF.ann), it) { (a, b) -> MO.run { a + b } }
        })
        is SimpleDocF.RemoveAnnotation -> dF.doc.go(xs.tail(), AndThen(cont).compose {
            AP.map(removeAnnotation(xs.first()), it) { (a, b) -> MO.run { a + b } }
        })
        is SimpleDocF.Text -> dF.doc.go(xs, AndThen(cont).compose {
            AP.map(text(dF.str), it) { (a, b) -> MO.run { a + b } }
        })
        is SimpleDocF.Line -> dF.doc.go(xs, AndThen(cont).compose {
            AP.map(text("\n${spaces(dF.i)}"), it) { (a, b) -> MO.run { a + b } }
        })
    }
    return go(emptyList(), ::identity)
}
