package pretty

import arrow.core.toT
import org.openjdk.jmh.annotations.*
import propCheck.arbitrary.RandSeed
import java.util.concurrent.TimeUnit

// TODO Change scope so that each iteration has to re-evaluate the doc
// TODO split this for each layout method
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
class LayoutLargeDoc {

    lateinit var largeDoc: Doc<Nothing>

    @Setup(Level.Invocation)
    fun setup() {
        largeDoc = programGen().unGen(RandSeed(1) toT 100).show()
    }

    @Benchmark
    fun layoutPretty(): Unit {
        return largeDoc.layoutPretty(PageWidth.default())
            .evaluate()
    }

    @Benchmark
    fun layoutSmart(): Unit {
        return largeDoc.layoutSmart(PageWidth.default())
            .evaluate()
    }

    @Benchmark
    fun layoutCompact(): Unit {
        return largeDoc.layoutCompact()
            .evaluate()
    }
}

// Fully evaluate a SimpleDoc forcing all evals.
tailrec fun <A> SimpleDoc<A>.evaluate(): Unit = when (val dF = unDoc.value()) {
    is SimpleDocF.Text -> dF.doc.evaluate()
    is SimpleDocF.Line -> dF.doc.evaluate()
    is SimpleDocF.AddAnnotation -> dF.doc.evaluate()
    is SimpleDocF.RemoveAnnotation -> dF.doc.evaluate()
    else -> Unit
}
