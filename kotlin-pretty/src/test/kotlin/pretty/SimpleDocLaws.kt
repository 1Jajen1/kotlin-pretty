package pretty

import arrow.Kind
import arrow.Kind2
import arrow.core.Eval
import arrow.core.extensions.eq
import arrow.test.UnitSpec
import arrow.test.generators.GenK
import arrow.test.generators.GenK2
import arrow.test.laws.*
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import arrow.typeclasses.EqK2
import io.kotlintest.properties.Gen
import pretty.simpledoc.eq.eq
import pretty.simpledoc.functor.functor
import pretty.simpledoc.monoid.monoid
import pretty.simpledocf.bifunctor.bifunctor
import pretty.simpledocf.functor.functor
import pretty.simpledocf.traverse.traverse

class SimpleDocFTraverseLawTests : UnitSpec() {
    init {
        testLaws(
            TraverseLaws.laws(
                SimpleDocF.traverse(),
                SimpleDocF.genK(Gen.string()),
                SimpleDocF.eqK(String.eq())
            )
        )
    }
}

class SimpleDocFFunctorLawTests : UnitSpec() {
    init {
        testLaws(
            FunctorLaws.laws(
                SimpleDocF.functor(),
                SimpleDocF.genK(Gen.string()),
                SimpleDocF.eqK(String.eq())
            )
        )
    }
}

class SimpleDocFBiFunctorLawTests : UnitSpec() {
    init {
        testLaws(
            BifunctorLaws.laws(
                SimpleDocF.bifunctor(),
                SimpleDocF.genK2(),
                SimpleDocF.eqK2()
            )
        )
    }
}

class SimpleDocFEqKLawTests : UnitSpec() {
    init {
        testLaws(
            EqK2Laws.laws(SimpleDocF.eqK2(), SimpleDocF.genK2()),
            EqKLaws.laws(SimpleDocF.eqK(String.eq()), SimpleDocF.genK(Gen.string())),
            EqLaws.laws(SimpleDocF.eqK(String.eq()).liftEq(String.eq()), SimpleDocF.genK(Gen.string()).genK(Gen.string()).map { it.fix() })
        )
    }
}

class SimpleDocLawTests : UnitSpec() {
    init {
        testLaws(
            FunctorLaws.laws(
                SimpleDoc.functor(),
                SimpleDoc.genK(),
                SimpleDoc.eqK()
            ),
            EqKLaws.laws(SimpleDoc.eqK(), SimpleDoc.genK()),
            EqLaws.laws(SimpleDoc.eq(String.eq()), SimpleDoc.genK().genK(Gen.string()).map { it.fix() }),
            MonoidLaws.laws(
                SimpleDoc.monoid(),
                SimpleDoc.genK().genK(Gen.string()).map { it.fix() },
                SimpleDoc.eq(String.eq())
            )
        )
    }
}

// TODO move eqK instances to main and generators somewhere else
fun SimpleDoc.Companion.genK(): GenK<ForSimpleDoc> = object: GenK<ForSimpleDoc> {
    override fun <A> genK(gen: Gen<A>): Gen<Kind<ForSimpleDoc, A>> =
        SimpleDocF.genK(gen).genK(SimpleDoc.genK().genK(gen)).map {
            SimpleDoc(Eval.now(it as SimpleDocF<A, SimpleDoc<A>>))
        }
}

fun SimpleDoc.Companion.eqK(): EqK<ForSimpleDoc> = object: EqK<ForSimpleDoc> {
    override fun <A> Kind<ForSimpleDoc, A>.eqK(other: Kind<ForSimpleDoc, A>, EQ: Eq<A>): Boolean =
        SimpleDoc.eq(EQ).run { fix().eqv(other.fix()) }
}

fun SimpleDocF.Companion.genK2(): GenK2<ForSimpleDocF> =
    object: GenK2<ForSimpleDocF> {
        override fun <A, B> genK(genA: Gen<A>, genB: Gen<B>): Gen<Kind2<ForSimpleDocF, A, B>> =
            SimpleDocF.genK(genA).genK(genB)
    }

fun SimpleDocF.Companion.eqK2(): EqK2<ForSimpleDocF> =
    object: EqK2<ForSimpleDocF> {
        override fun <A, B> Kind2<ForSimpleDocF, A, B>.eqK(
            other: Kind2<ForSimpleDocF, A, B>,
            EQA: Eq<A>,
            EQB: Eq<B>
        ): Boolean = SimpleDocF.eqK(EQA).liftEq(EQB).run {
            eqv(other)
        }
    }

fun <C> SimpleDocF.Companion.genK(genC: Gen<C>): GenK<SimpleDocFPartialOf<C>> =
    object: GenK<SimpleDocFPartialOf<C>> {
        override fun <A> genK(gen: Gen<A>): Gen<Kind<SimpleDocFPartialOf<C>, A>> =
            Gen.oneOf(
                Gen.create { SimpleDocF.Fail },
                Gen.create { SimpleDocF.Nil },
                Gen.bind(gen, Gen.string()) { a, str -> SimpleDocF.Text(str, a) },
                Gen.bind(gen, Gen.int()) { a, i -> SimpleDocF.Line(i, a) },
                Gen.bind(gen, genC) { a, c -> SimpleDocF.AddAnnotation(c, a) },
                gen.map { SimpleDocF.RemoveAnnotation(it) }
            )
    }

fun <C> SimpleDocF.Companion.eqK(eqC: Eq<C>): EqK<SimpleDocFPartialOf<C>> =
    object: EqK<SimpleDocFPartialOf<C>> {
        override fun <A> Kind<SimpleDocFPartialOf<C>, A>.eqK(
            other: Kind<SimpleDocFPartialOf<C>, A>,
            EQ: Eq<A>
        ): Boolean = other.fix().let { other ->
            when (val it = fix()) {
                is SimpleDocF.Fail -> other is SimpleDocF.Fail
                is SimpleDocF.Nil -> other is SimpleDocF.Nil
                is SimpleDocF.RemoveAnnotation -> (other is SimpleDocF.RemoveAnnotation) &&
                        EQ.run { it.doc.eqv(other.doc) }
                is SimpleDocF.AddAnnotation -> (other is SimpleDocF.AddAnnotation) &&
                        EQ.run { it.doc.eqv(other.doc) } && eqC.run { it.ann.eqv(other.ann) }
                is SimpleDocF.Line -> (other is SimpleDocF.Line) && it.i == other.i &&
                        EQ.run { it.doc.eqv(other.doc) }
                is SimpleDocF.Text -> (other is SimpleDocF.Text) && it.str == other.str &&
                        EQ.run { it.doc.eqv(other.doc) }
            }
        }
    }
