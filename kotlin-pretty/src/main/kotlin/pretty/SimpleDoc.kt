package pretty

internal sealed class SimpleDocF<out A> {
    object Fail : SimpleDocF<Nothing>()
    object Nil : SimpleDocF<Nothing>()
    data class Line<A>(val i: Int, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    data class Text<A>(val str: String, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    data class AddAnnotation<A>(val ann: A, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    data class RemoveAnnotation<A>(val doc: SimpleDoc<A>) : SimpleDocF<A>()

    companion object
}

data class SimpleDoc<out A>internal constructor(internal val unDoc: Eval<SimpleDocF<A>>) {

    override fun toString(): String = "SimpleDoc(unDoc=${unDoc()})"

    companion object {
        fun fail(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Fail))
        fun nil(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Nil))
        fun <A> text(str: String, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Text(str, next)))
        fun <A> line(i: Int, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Line(i, next)))
        fun <A> addAnnotation(ann: A, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.AddAnnotation(ann, next)))
        fun <A> removeAnnotation(next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.RemoveAnnotation(next)))
    }
}

operator fun <A> SimpleDoc<A>.plus(b: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(unDoc.flatMap {
    when (it) {
        is SimpleDocF.Nil -> b.unDoc
        is SimpleDocF.Fail -> Eval.now(SimpleDocF.Fail)
        is SimpleDocF.Text -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.Line -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.AddAnnotation -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.RemoveAnnotation -> Eval.now(it.copy(doc = it.doc + b))
    }
})

fun <A> SimpleDoc<A>.renderString(): String {
    val sb = StringBuilder()
    renderDecorated(Unit, { _, _ -> }, { str -> sb.append(str); Unit }, { _, _ -> })
    return sb.toString()
}

fun <A, B> SimpleDoc<A>.renderDecorated(
    empty: B,
    combine: (B, B) -> B,
    fromString: (String) -> B,
    annotate: (A, B) -> B
): B {
    val anns: ArrayDeque<A> = ArrayDeque()
    tailrec fun SimpleDoc<A>.go(cont: (B) -> B): B =
        when (val dF = unDoc()) {
            is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
            is SimpleDocF.Nil -> cont(empty)
            is SimpleDocF.Text -> dF.doc.go(cont.andThen { combine(fromString(dF.str), it) })
            is SimpleDocF.Line -> dF.doc.go(cont.andThen { combine(fromString("\n${spaces(dF.i)}"), it) })
            is SimpleDocF.AddAnnotation -> {
                anns.add(dF.ann)
                dF.doc.go(cont)
            }
            is SimpleDocF.RemoveAnnotation -> dF.doc.go(cont.andThen { annotate(anns.removeLast(), it) })
        }
    return go { it }
}

fun <A, B> SimpleDoc<A>.renderDecorated(
    empty: B,
    combine: (B, B) -> B,
    fromString: (String) -> B,
    addAnnotation: (A) -> B,
    removeAnnotation: (A) -> B
): B {
    val anns: ArrayDeque<A> = ArrayDeque()
    tailrec fun SimpleDoc<A>.go(cont: (B) -> B): B =
        when (val dF = unDoc()) {
            is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
            is SimpleDocF.Nil -> empty
            is SimpleDocF.Text -> dF.doc.go(cont.andThen { combine(fromString(dF.str), it) })
            is SimpleDocF.Line -> dF.doc.go(cont.andThen { combine(fromString("\n${spaces(dF.i)}"), it) })
            is SimpleDocF.AddAnnotation -> {
                anns.addLast(dF.ann)
                dF.doc.go(cont.andThen { combine(addAnnotation(dF.ann), it) })
            }
            is SimpleDocF.RemoveAnnotation ->
                dF.doc.go(cont.andThen { combine(removeAnnotation(anns.removeLast()), it) })
        }
    return go { it }
}
