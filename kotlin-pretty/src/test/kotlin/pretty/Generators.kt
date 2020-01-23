package pretty

import arrow.core.ForId
import arrow.core.toT
import propCheck.arbitrary.*

val genPageWidth = Gen.monadGen {
    frequency(
        3 toT mapN(int(0..1_000), float(0.0F..1.0F)) { (w, r) -> PageWidth.Available(w, r) },
        1 toT element(PageWidth.Unbounded)
    )
}

fun <A> genDoc(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    recursive({
        choice(*it.toTypedArray())
    }, listOf(genTerminal()), { listOf(genRec(genA).fromGenT()) })
}

fun <A> genTerminal(): Gen<Doc<A>> = Gen.monadGen {
    frequency(
        10 toT textGen(),
        5 toT nilGen(),
        5 toT lineGen<A>()
    )
}

fun <A> nilGen(): Gen<Doc<A>> = Gen.monadGen { element(nil()) }

fun <A> textGen(): Gen<Doc<A>> = Gen.monadGen {
    latin1().string(0..100).map { it.filter { it != '\n' }.text() }
}

fun <A> lineGen(): Gen<Doc<A>> = Gen.monadGen {
    element(
        line(), lineBreak(), hardLine(), softLine(), softLineBreak()
    )
}

fun <A> genRec(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    frequency(
        10 toT combineGen(genA),
        5 toT flatAltGen(genA),
        5 toT nestGen(genA),
        5 toT annotatedGen(genA),
        5 toT unionGen(genA),
        3 toT columnGen(genA),
        3 toT nestingGen(genA),
        3 toT withPageWidthGen(genA)
    )
}

fun <A> combineGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    subterm2(genDoc(genA), genDoc(genA)) { l, r -> l + r }
}

fun <A> unionGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    genDoc(genA).map { it.group() }
}

fun <A> nestGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    genDoc(genA).flatMap { g -> int(0..100).map { g.nest(it) } }
}

fun <A> flatAltGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    subterm2(genDoc(genA), genDoc(genA)) { l, r -> l .flatAlt(r) }
}

fun <A> annotatedGen(annGen: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    mapN(genDoc(annGen), annGen) { (g, a) -> g.annotate(a) }
}

fun <A> columnGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    genDoc(genA).toFunction(Int.func(), Int.coarbitrary()).map { (f) -> column(f) }
}

fun <A> nestingGen(genA: GenTOf<ForId, A>): GenTOf<ForId, Doc<A>> = Gen.monadGen {
    genDoc(genA).toFunction(Int.func(), Int.coarbitrary()).map { (f) -> nesting(f) }
}

fun <A> withPageWidthGen(genA: GenTOf<ForId, A>): Gen<Doc<A>> = Gen.monadGen {
    genDoc(genA).toFunction(Int.func(), Int.coarbitrary()).map { (f) -> column(f) }
}
