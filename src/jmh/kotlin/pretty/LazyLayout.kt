package pretty

import arrow.core.toT
import org.openjdk.jmh.annotations.*
import propCheck.arbitrary.RandSeed
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
class LazyLayout {

    val largeDoc = programGen().unGen(RandSeed(1) toT 60).show()

    // Current renderPretty
    // This is lazy in union, but nowhere else
    @Benchmark
    fun partialLazyTraversal(): SimpleDoc<Nothing> {
        return largeDoc.renderPretty()
    }

    // a lazy variant that wraps every layer in eval, a lot slower apparently
    @Benchmark
    fun lazyTraversal(): SimpleDoc<Nothing> {
        return largeDoc.renderPrettyLazy()
    }

    // TODO benchmark non-cata code from the test
}
