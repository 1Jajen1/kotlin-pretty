package pretty

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.id.applicative.applicative
import arrow.core.extensions.monoid
import arrow.syntax.collections.tail
import arrow.typeclasses.Applicative
import arrow.typeclasses.Monoid
import org.openjdk.jmh.annotations.*
import pretty.symbols.lParen
import pretty.symbols.rParen
import pretty.symbols.space
import propCheck.arbitrary.*
import propCheck.arbitrary.gen.applicative.applicative
import propCheck.arbitrary.gen.monad.monad
import propCheck.instances.arbitrary
import propCheck.instances.mapk.arbitrary.arbitrary
import propCheck.instances.nonemptylist.arbitrary.arbitrary
import java.util.concurrent.TimeUnit

// TODO Move each benchmark to their own files and add more layout related benchmarks
// TODO add catamorphism benchmarks after those have been reworked


// https://github.com/quchen/prettyprinter/blob/master/prettyprinter/bench/LargeOutput.hs
data class Program(val unProgram: Binds)

data class Binds(val unBinds: Map<String, LambdaForm>)
data class LambdaForm(val free: List<String>, val bound: List<String>, val exp: Exp)
sealed class Exp {
    data class Let(val b: Binds, val e: Exp) : Exp()
    data class Case(val exp: Exp, val alt: List<Alt>) : Exp()
    data class AppF(val fName: String, val args: List<String>) : Exp()
    data class AppC(val cName: String, val args: List<String>) : Exp()
    data class AppP(val opName: String, val l: String, val r: String) : Exp()
    data class LitE(val v: Int) : Exp()
}

data class Alt(val con: String, val args: List<String>, val body: Exp)

fun programGen(): Gen<Program> = bindsGen().map { Program(it) }

fun bindsGen(): Gen<Binds> = MapK.arbitrary(String.arbitrary(), Arbitrary(lambdaFormGen()))
    .arbitrary().map { Binds(it) }

fun lambdaFormGen(): Gen<LambdaForm> = Gen.monad().fx.monad {
    val free = !arbitraryASCIIString().listOf().fromTo(0, 2)
    val bound = !arbitraryASCIIString().listOf().fromTo(0, 2)
    val exp = !exprGen()
    LambdaForm(free, bound ,exp)
}.fix()

fun exprGen(): Gen<Exp> = listOf<Gen<Exp>>(
    Gen.monad().fx.monad {
        !unit()
        val binds = !bindsGen()
        val expr = !exprGen()
        Exp.Let(binds, expr)
    }.fix(),
    Gen.monad().fx.monad {
        !unit()
        val exp = !exprGen()
        val alts = !Nel.arbitrary(Arbitrary(altGen())).arbitrary()
        Exp.Case(exp, alts.all)
    }.fix(),
    Gen.applicative().map(arbitraryASCIIString(), arbitraryASCIIString().listOf().fromTo(0, 3)) { (n, a) ->
        Exp.AppF(
            n,
            a
        )
    }.fix(),
    Gen.applicative().map(arbitraryASCIIString(), arbitraryASCIIString().listOf().fromTo(0, 3)) { (n, a) ->
        Exp.AppC(
            n,
            a
        )
    }.fix(),
    Gen.applicative().map(arbitraryASCIIString(), arbitraryASCIIString(), arbitraryASCIIString()) { (n, a, b) ->
        Exp.AppP(
            n,
            a, b
        )
    }.fix(),
    arbitrarySizedInt().map { Exp.LitE(it) }
).map { it.scale { it / 2 } }.let { ls -> Gen.oneOf(*ls.toTypedArray()) }

fun altGen(): Gen<Alt> = Gen.monad().fx.monad {
    val conName = !arbitraryASCIIString().suchThat { it.isNotEmpty() }.map { it[0].toUpperCase() + it.substring(1) }
    val args = !arbitraryASCIIString().listOf().fromTo(0, 3)
    val expr = !exprGen()
    Alt(conName, args, expr)
}.fix()

fun <A> Gen<A>.fromTo(i: Int, j: Int): Gen<A> = Gen.monad().fx.monad {
    val s = !Gen.choose(i toT j, Int.random())
    !this@fromTo.resize(s)
}.fix()

fun Program.show(): Doc<Nothing> = unProgram.show()
fun Binds.show(): Doc<Nothing> = unBinds.toList()
    .map { (k, v) -> k.text() spaced pretty.symbols.equals() spaced v.show() }
    .vSep()
    .align()

fun LambdaForm.show(): Doc<Nothing> = "\\".text() +
        (if (free.isEmpty()) nil() else lParen() + free.map { it.text() }.hSep() + rParen()) +
        (if (bound.isEmpty()) nil() else (if (free.isEmpty()) bound.map { it.text() }.hSep() else space() + bound.map { it.text() }.hSep())) +
        space() + "->".text() + space() + exp.show()

fun Exp.show(): Doc<Nothing> = when (this) {
    is Exp.Let -> listOf(
        "let".text() spaced b.show().align(),
        "in".text() spaced e.show()
    ).vSep().align()
    is Exp.Case -> listOf(
        "case".text() spaced exp.show() spaced "of".text(),
        alt.map { it.show() }.vSep().align().indent(4)
    ).vSep()
    is Exp.AppF -> when {
        args.isEmpty() -> fName.text()
        else -> fName.text() spaced args.map { it.text() }.hSep()
    }
    is Exp.AppC -> when {
        args.isEmpty() -> cName.text()
        else -> cName.text() spaced args.map { it.text() }.hSep()
    }
    is Exp.AppP -> opName.text() spaced l.text() spaced r.text()
    is Exp.LitE -> v.doc()
}
fun Alt.show(): Doc<Nothing> = when {
    args.isEmpty() -> con.text() spaced "->".text() spaced body.show()
    else -> con.text() spaced args.map { it.text() }.hSep() spaced "->".text() spaced body.show()
}

