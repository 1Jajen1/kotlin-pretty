package pretty

import arrow.core.Eval
import arrow.core.Function1
import arrow.core.Tuple2
import arrow.core.toT
import arrow.extension
import propCheck.arbitrary.*
import propCheck.arbitrary.gen.applicative.applicative
import propCheck.arbitrary.gen.monad.flatMap
import propCheck.instances.arbitrary
import propCheck.instances.coarbitrary
import propCheck.instances.function1.arbitrary.arbitrary
import propCheck.instances.tuple2.arbitrary.arbitrary
import kotlin.math.floor

@extension
interface PageWidthArbitrary : Arbitrary<PageWidth> {
    override fun arbitrary(): Gen<PageWidth> = Gen.frequency(
        5 toT Gen.applicative().map(
            arbitrarySizedPositiveInt(),
            Gen.choose(0F toT 1F, Float.random())
        ) { (w, r) -> PageWidth.Available(w, r) }.fix(),
        1 toT Gen.elements(PageWidth.Unbounded)
    )
}

// TODO rewrite this with actual operations sooner or later
@extension
interface DocArbitrary<A> : Arbitrary<Doc<A>> {
    fun AA(): Arbitrary<A>

    override fun arbitrary(): Gen<Doc<A>> = genDoc<A>(AA().arbitrary())

    override fun shrink(fail: Doc<A>): Sequence<Doc<A>> = (
            (if ((fail.unDoc.value() is DocF.Fail).not()) emptySequence()
                // sequenceOf(Doc(Eval.now(DocF.Fail)))
            else emptySequence<Doc<A>>()) +
                    when (val dF = fail.unDoc.value()) {
                        is DocF.Fail, is DocF.Nil, is DocF.Line -> emptySequence()
                        is DocF.Text -> String.arbitrary().shrink(dF.str).map { it.text() }
                        is DocF.Nest -> shrinkInt(dF.i).filter { it > 0 }.map { dF.doc.nest(it) } +
                                sequenceOf(0).flatMap { shrink(dF.doc).map { it.nest(dF.i) } }
                        is DocF.FlatAlt -> shrink(dF.l) + shrink(dF.r) + Tuple2.arbitrary(
                            this@DocArbitrary,
                            this@DocArbitrary
                        )
                            .shrink(dF.l toT dF.r).map { (l, r) -> l.flatAlt(r) }
                        is DocF.Combined -> shrink(dF.l) + shrink(dF.r) + Tuple2.arbitrary(
                            this@DocArbitrary,
                            this@DocArbitrary
                        )
                            .shrink(dF.l toT dF.r).map { (l, r) -> l + r }
                        is DocF.Union -> shrink(dF.l) + shrink(dF.r) + Tuple2.arbitrary(
                            this@DocArbitrary,
                            this@DocArbitrary
                        )
                            .shrink(dF.l toT dF.r).map { (l, r) -> Doc(Eval.now(DocF.Union(l, r))) }
                        is DocF.Annotated -> AA().shrink(dF.ann).map {
                            dF.doc.annotate(it)
                        } + shrink(dF.doc)
                        else -> emptySequence()
                    })
}

fun <A> genDoc(genA: Gen<A>): Gen<Doc<A>> = Gen.sized { depth ->
    if (depth <= 1) genTerminal<A>() else genRec<A>(genA).resize(floor(depth.div(2.0)).toInt())
}

fun <A> genTerminal(): Gen<Doc<A>> = Gen.frequency(
    10 toT textGen<A>(),
    5 toT nilGen<A>(),
    5 toT lineGen<A>()
)

fun <A> nilGen(): Gen<Doc<A>> = Gen.elements(nil())

// generate a fail, but only as the first element of a union because there is actually
//  no combinator that can generate a fail on the right side
fun <A> failGen(genA: Gen<A>): Gen<Doc<A>> =
    genDoc(genA).map { Doc(Eval.now(DocF.Union(Doc(Eval.now(DocF.Fail)), it))) }

fun <A> textGen(): Gen<Doc<A>> = arbitraryUnicodeString()
    .map { it.filter { it != '\n' }.text() }

fun <A> lineGen(): Gen<Doc<A>> = Gen.elements(
    line(), lineBreak(), hardLine(), softLine(), softLineBreak()
)

fun <A> genRec(genA: Gen<A>): Gen<Doc<A>> = Gen.frequency(
    10 toT combineGen(genA),
    5 toT flatAltGen(genA),
    5 toT nestGen(genA),
    5 toT annotatedGen(genA),
    3 toT columnGen(genA),
    3 toT nestingGen(genA),
    3 toT withPageWidthGen(genA),
    1 toT unionGen(genA)
    // 1 toT failGen<A>(genA)
)

fun <A> combineGen(genA: Gen<A>): Gen<Doc<A>> = Gen.applicative().map(
    genDoc<A>(genA),
    genDoc<A>(genA)
) { (l, r) -> (l + r) }.fix()

fun <A> unionGen(genA: Gen<A>): Gen<Doc<A>> = Gen.applicative().map(
    genDoc<A>(genA),
    genDoc<A>(genA)
) { (l, r) -> Doc(Eval.now(DocF.Union(l, r))) }.fix()

fun <A> nestGen(genA: Gen<A>): Gen<Doc<A>> =
    genDoc<A>(genA).flatMap { g -> arbitrarySizedPositiveInt().map { g.nest(it) } }

fun <A> flatAltGen(genA: Gen<A>): Gen<Doc<A>> = Gen.applicative().map(
    genDoc<A>(genA),
    genDoc<A>(genA)
) { (l, r) -> Doc(Eval.now(DocF.FlatAlt(l, r))) }.fix()

fun <A> annotatedGen(annGen: Gen<A>): Gen<Doc<A>> = genDoc<A>(annGen)
    .flatMap { g -> annGen.map { g.annotate(it) } }

fun <A> columnGen(genA: Gen<A>): Gen<Doc<A>> = Function1.arbitrary(
    Int.coarbitrary(), Arbitrary(genDoc(genA))
).arbitrary().map { Doc(Eval.now(DocF.Column(it.f))) }

fun <A> nestingGen(genA: Gen<A>): Gen<Doc<A>> = Function1.arbitrary(
    Int.coarbitrary(), Arbitrary(genDoc(genA))
).arbitrary().map { Doc(Eval.now(DocF.Nesting(it.f))) }

fun <A> withPageWidthGen(genA: Gen<A>): Gen<Doc<A>> = Function1.arbitrary(
    object : Coarbitrary<PageWidth> {
        override fun <B> Gen<B>.coarbitrary(a: PageWidth): Gen<B> = when (a) {
            is PageWidth.Available -> variant(a.maxWidth.toLong())
                .variant(a.ribbonFract.toDouble().toRawBits())
                .variant(1)
            is PageWidth.Unbounded -> variant(0)
        }
    }, Arbitrary(genDoc(genA))
).arbitrary().map { Doc(Eval.now(DocF.WithPageWidth(it.f))) }
