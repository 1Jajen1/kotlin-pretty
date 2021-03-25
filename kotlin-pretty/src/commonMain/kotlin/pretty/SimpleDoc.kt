package pretty

import pretty.lazy.AndThen
import pretty.lazy.Eval
import pretty.lazy.flatMap

public sealed class SimpleDocF<out A> {
    public object Fail : SimpleDocF<Nothing>()
    public object Nil : SimpleDocF<Nothing>()
    public data class Line<A>(val i: Int, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    public data class Text<A>(val str: String, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    public data class AddAnnotation<A>(val ann: A, val doc: SimpleDoc<A>) : SimpleDocF<A>()
    public data class RemoveAnnotation<A>(val doc: SimpleDoc<A>) : SimpleDocF<A>()
}

public class SimpleDoc<out A>(eval: Eval<SimpleDocF<A>>) {
    public val unDoc: Eval<SimpleDocF<A>> = eval.memo()

    public companion object {
        public fun fail(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Fail))
        public fun nil(): SimpleDoc<Nothing> = SimpleDoc(Eval.now(SimpleDocF.Nil))
        public fun <A> text(str: String, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Text(str, next)))
        public fun <A> line(i: Int, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.Line(i, next)))
        public fun <A> addAnnotation(ann: A, next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.AddAnnotation(ann, next)))
        public fun <A> removeAnnotation(next: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(Eval.now(SimpleDocF.RemoveAnnotation(next)))
    }
}

public operator fun <A> SimpleDoc<A>.plus(b: SimpleDoc<A>): SimpleDoc<A> = SimpleDoc(unDoc.flatMap {
    when (it) {
        is SimpleDocF.Nil -> b.unDoc
        is SimpleDocF.Fail -> Eval.now(SimpleDocF.Fail)
        is SimpleDocF.Text -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.Line -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.AddAnnotation -> Eval.now(it.copy(doc = it.doc + b))
        is SimpleDocF.RemoveAnnotation -> Eval.now(it.copy(doc = it.doc + b))
    }
})

public fun <A> SimpleDoc<A>.renderString(): String {
    var curr = unDoc()
    val buf = StringBuilder()
    while (true) {
        when (curr) {
            is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
            is SimpleDocF.Nil -> return buf.toString()
            is SimpleDocF.Text -> {
                buf.append(curr.str)
                curr = curr.doc.unDoc()
            }
            is SimpleDocF.Line -> {
                buf.append("\n${spaces(curr.i)}")
                curr = curr.doc.unDoc()
            }
            is SimpleDocF.AddAnnotation -> {
                curr = curr.doc.unDoc()
            }
            is SimpleDocF.RemoveAnnotation -> {
                curr = curr.doc.unDoc()
            }
        }
    }
}

// TODO Rework ...
public fun <A, B> SimpleDoc<A>.renderDecorated(
    empty: B,
    combine: (B, B) -> B,
    fromString: (String) -> B,
    annotate: (A, B) -> B
): B {
    val anns: ArrayDeque<A> = ArrayDeque()
    tailrec fun SimpleDoc<A>.go(cont: AndThen<B, B>): B =
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
    return go(AndThen { it })
}

public fun <A, B> SimpleDoc<A>.renderDecorated(
    empty: B,
    combine: (B, B) -> B,
    fromString: (String) -> B,
    addAnnotation: (A) -> B,
    removeAnnotation: (A) -> B
): B {
    val anns: ArrayDeque<A> = ArrayDeque()
    tailrec fun SimpleDoc<A>.go(cont: AndThen<B, B>): B =
        when (val dF = unDoc()) {
            is SimpleDocF.Fail -> throw IllegalStateException("Unexpected SimpleDoc.Fail in render")
            is SimpleDocF.Nil -> cont(empty)
            is SimpleDocF.Text -> dF.doc.go(cont.andThen { combine(fromString(dF.str), it) })
            is SimpleDocF.Line -> dF.doc.go(cont.andThen { combine(fromString("\n${spaces(dF.i)}"), it) })
            is SimpleDocF.AddAnnotation -> {
                anns.addLast(dF.ann)
                dF.doc.go(cont.andThen { combine(addAnnotation(dF.ann), it) })
            }
            is SimpleDocF.RemoveAnnotation ->
                dF.doc.go(cont.andThen { combine(removeAnnotation(anns.removeLast()), it) })
        }
    return go(AndThen { it })
}
