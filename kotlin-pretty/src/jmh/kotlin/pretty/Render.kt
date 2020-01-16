package pretty

import arrow.core.*
import arrow.core.extensions.id.applicative.applicative
import arrow.core.extensions.monoid
import arrow.syntax.collections.tail
import arrow.typeclasses.Monoid
import org.openjdk.jmh.annotations.*
import propCheck.arbitrary.RandSeed
import java.util.concurrent.TimeUnit

// TODO Change scope so that each iteration has to re-evaluate the doc
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
class RenderLargeDoc {

    lateinit var largeDoc: SimpleDoc<Nothing>

    @Setup(Level.Invocation)
    fun setup() {
        largeDoc = programGen().unGen(RandSeed(1) toT 60).show()
            .layoutPretty(PageWidth.default())
    }

    @Benchmark
    fun renderString(): String {
        return largeDoc.renderString()
    }

    @Benchmark
    fun renderDecoratedWithString(): String {
        return largeDoc.renderDecorated(String.monoid(), ::identity, { "" }, { "" })
    }

    @Benchmark
    fun renderDecoratedAWithIdAndString(): String {
        return largeDoc.renderDecoratedA(
            Id.applicative(), String.monoid(),
            ::Id, { Id("") }, { Id("") }
        ).value()
    }

    @Benchmark
    fun renderStringDirect(): String {
        return largeDoc.renderStringDirect(::identity)
    }

    @Benchmark
    fun renderDecoratedDirect(): String {
        return largeDoc.renderDecoratedDirect(String.monoid(), ::identity, { "" }, { "" })
    }

    @Benchmark
    fun renderStringBuilderDirect(): String {
        val sb = StringBuilder()
        largeDoc.renderStringbuilderDirect(sb)

        return sb.toString()
    }
}

tailrec fun <A> SimpleDoc<A>.renderStringbuilderDirect(sb: StringBuilder): Unit = when (val dF = unDoc.value()) {
    is SimpleDocF.AddAnnotation -> dF.doc.renderStringbuilderDirect(sb)
    is SimpleDocF.RemoveAnnotation -> dF.doc.renderStringbuilderDirect(sb)
    is SimpleDocF.Nil -> { sb.append(""); Unit }
    is SimpleDocF.Fail -> TODO("Uncaught fail")
    is SimpleDocF.Text -> { sb.append(dF.str); dF.doc.renderStringbuilderDirect(sb) }
    is SimpleDocF.Line -> { sb.append("\n${spaces(dF.i)}"); dF.doc.renderStringbuilderDirect(sb) }
}

tailrec fun <A> SimpleDoc<A>.renderStringDirect(cont: (String) -> String): String = when (val dF = unDoc.value()) {
    is SimpleDocF.AddAnnotation -> dF.doc.renderStringDirect(cont)
    is SimpleDocF.RemoveAnnotation -> dF.doc.renderStringDirect(cont)
    is SimpleDocF.Nil -> cont("")
    is SimpleDocF.Fail -> TODO("Uncaught fail")
    is SimpleDocF.Text -> dF.doc.renderStringDirect(AndThen(cont).compose { dF.str + it })
    is SimpleDocF.Line -> dF.doc.renderStringDirect(AndThen(cont).compose { "\n${spaces(dF.i)}" + it })
}

fun <A, B> SimpleDoc<A>.renderDecoratedDirect(
    MO: Monoid<B>,
    text: (String) -> B,
    addAnnotation: (A) -> B,
    removeAnnotation: (A) -> B
): B {
    tailrec fun SimpleDoc<A>.go(xs: List<A>, cont: (B) -> B): B = when (val dF = unDoc.value()) {
        is SimpleDocF.Nil -> cont(MO.empty())
        is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
        is SimpleDocF.AddAnnotation -> dF.doc.go(listOf(dF.ann) + xs, AndThen(cont).compose {
            MO.run { addAnnotation(dF.ann) + it }
        })
        is SimpleDocF.RemoveAnnotation -> dF.doc.go(xs.tail(), AndThen(cont).compose {
            MO.run { removeAnnotation(xs.first()) + it }
        })
        is SimpleDocF.Text -> dF.doc.go(xs, AndThen(cont).compose {
            MO.run { text(dF.str) + it }
        })
        is SimpleDocF.Line -> dF.doc.go(xs, AndThen(cont).compose {
            MO.run { text("\n${spaces(dF.i)}") + it }
        })
    }
    return go(emptyList(), ::identity)
}
