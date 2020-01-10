package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.Eval
import arrow.core.ForFunction0
import arrow.core.Tuple2
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

operator fun <A> SimpleDoc<A>.plus(b: SimpleDoc<A>): SimpleDoc<A> = cata {
    when (it) {
        is SimpleDocF.Nil -> b
        is SimpleDocF.Text -> when (val rdF = it.doc.unDoc.value()) {
            is SimpleDocF.Text -> SimpleDoc.text(it.str + rdF.str, rdF.doc)
            else -> SimpleDoc(Eval.now(it))
        }
        else -> SimpleDoc(Eval.now(it))
    }
}

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
    override fun <A, B> Kind<ForSimpleDoc, A>.map(f: (A) -> B): Kind<ForSimpleDoc, B> = fix().cata {
        when (it) {
            is SimpleDocF.AddAnnotation -> SimpleDoc.addAnnotation(f(it.ann), it.doc.fix())
            else -> SimpleDoc(Eval.now(it.fix() as SimpleDocF<B, SimpleDoc<B>>))
            // safe because cata went through all of them
        }
    }
}

@extension
interface SimpleDocBirecursive<A> : Birecursive<SimpleDoc<A>, SimpleDocFPartialOf<A>> {
    override fun FF(): Functor<SimpleDocFPartialOf<A>> = SimpleDocF.functor()
    override fun Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>>.embedT(): SimpleDoc<A> = SimpleDoc(Eval.now(this.fix()))
    override fun SimpleDoc<A>.projectT(): Kind<SimpleDocFPartialOf<A>, SimpleDoc<A>> = unDoc.value()
}

// TODO use string builder?
fun <A> SimpleDoc<A>.renderString(): String = cata {
    when (it) {
        is SimpleDocF.Fail -> throw IllegalStateException("Encountered fail")
        is SimpleDocF.Nil -> ""
        is SimpleDocF.Text -> it.str + it.doc
        is SimpleDocF.Line -> "\n" + spaces(it.i) + it.doc
        is SimpleDocF.AddAnnotation -> it.doc
        is SimpleDocF.RemoveAnnotation -> it.doc
    }
}


fun <A, B> SimpleDoc<A>.cata(f: (SimpleDocF<A, B>) -> B): B = SimpleDoc.birecursive<A>().run {
    cataM<FreePartialOf<ForFunction0>, B>(SimpleDocF.traverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }.fix().runT()
}

// tailrec version, should be simple enough
fun <A, B> SimpleDoc<A>.renderDecorated(
    combine: (B, B) -> B,
    text: (String) -> B,
    addAnnotation: (A) -> B,
    removeAnnotation: (A) -> B
): B = cata<A, (List<A>) -> TrampolineF<B>> {
    { annotations ->
        Trampoline.defer {
            when (it) {
                is SimpleDocF.AddAnnotation -> it.doc(listOf(it.ann) + annotations).map { rec -> combine(addAnnotation(it.ann), rec) }
                is SimpleDocF.RemoveAnnotation -> when {
                    annotations.isEmpty() -> throw IllegalStateException("Encountered remove annotation without an annotation.")
                    else -> it.doc(annotations.tail()).map { rec -> combine(removeAnnotation(annotations.first()), rec) }
                }
                is SimpleDocF.Line -> it.doc(annotations).map { rec -> combine(text("\n" + spaces(it.i)), rec) }
                is SimpleDocF.Text -> it.doc(annotations).map { rec -> combine(text(it.str), rec) }
                is SimpleDocF.Nil -> Free.just(text(""))
                is SimpleDocF.Fail -> throw IllegalStateException("Encountered Fail. This is a bug in the layout function used to generate the SimpleDoc.")
            }
        }
    }
}(emptyList()).runT()

fun <A, B> SimpleDoc<A>.para(f: (SimpleDocF<A, Tuple2<SimpleDoc<A>, B>>) -> B): B = SimpleDoc.birecursive<A>().run {
    paraM<FreePartialOf<ForFunction0>, B>(SimpleDocF.traverse(), Free.monad()) { Trampoline.later { f(it.fix()) } }.fix().runT()
}
