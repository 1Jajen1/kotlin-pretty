package pretty

import pretty.lazy.AndThen
import pretty.lazy.Eval
import pretty.lazy.flatMap
import pretty.lazy.map

public sealed class DocF<out A> {
    public object Nil : DocF<Nothing>()
    public object Fail : DocF<Nothing>()
    public object Line : DocF<Nothing>()
    public data class Text(val str: String) : DocF<Nothing>()
    public data class Union<A>(val l: Doc<A>, val r: Doc<A>) : DocF<A>()
    public data class Combined<A>(val l: Doc<A>, val r: Doc<A>) : DocF<A>()
    public data class Nest<A>(val i: Int, val doc: Doc<A>) : DocF<A>()
    public data class Column<A>(val doc: AndThen<Int, Doc<A>>) : DocF<A>()
    public data class Nesting<A>(val doc: AndThen<Int, Doc<A>>) : DocF<A>()
    public data class FlatAlt<A>(val l: Doc<A>, val r: Doc<A>) : DocF<A>()
    public data class Annotated<A>(val ann: A, val doc: Doc<A>) : DocF<A>()
    public data class WithPageWidth<A>(val doc: AndThen<PageWidth, Doc<A>>) : DocF<A>()
}

public class Doc<out A>(eval: Eval<DocF<A>>) {
    public val unDoc: Eval<DocF<A>> = eval.memo()

    public fun <B> map(f: (A) -> B): Doc<B> = Doc(unDoc.map {
        when (it) {
            is DocF.Nil -> DocF.Nil
            is DocF.Fail -> DocF.Fail
            is DocF.Line -> DocF.Line
            is DocF.Text -> DocF.Text(it.str)
            is DocF.Union -> DocF.Union(it.l.map(f), it.r.map(f))
            is DocF.Combined -> DocF.Combined(it.l.map(f), it.r.map(f))
            is DocF.Nest -> DocF.Nest(it.i, it.doc.map(f))
            is DocF.Column -> DocF.Column(it.doc.andThen { it.map(f) })
            is DocF.Nesting -> DocF.Nesting(it.doc.andThen { it.map(f) })
            is DocF.FlatAlt -> DocF.FlatAlt(it.l.map(f), it.r.map(f))
            is DocF.Annotated -> DocF.Annotated(f(it.ann), it.doc.map(f))
            is DocF.WithPageWidth -> DocF.WithPageWidth(it.doc.andThen { it.map(f) })
        }
    })

    public companion object {
        public fun empty(): Doc<Nothing> = Doc(Eval.now(DocF.Nil))
    }
}

public operator fun <A> Doc<A>.plus(other: Doc<A>): Doc<A> = Doc(Eval.now(DocF.Combined(this, other)))

public fun <A> Doc<A>.fuse(shallow: Boolean = true): Doc<A> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.Combined -> Eval.defer {
            it.l.fuse(shallow).unDoc.flatMap { lF ->
                it.r.fuse(shallow).unDoc.map { rF ->
                    when {
                        lF is DocF.Text && rF is DocF.Text -> DocF.Text(lF.str + rF.str)
                        lF is DocF.Nil -> rF
                        rF is DocF.Nil -> lF
                        else -> DocF.Combined(Doc(Eval.now(lF)), Doc(Eval.now(rF)))
                    }
                }
            }
        }
        is DocF.Nest ->
            if (it.i == 0) it.doc.unDoc
            else it.doc.unDoc.map { mn ->
                if (mn is DocF.Nest) DocF.Nest(it.i + mn.i, mn.doc)
                else DocF.Nest(it.i, it.doc)
            }
        is DocF.Annotated -> Eval.later {
            DocF.Annotated(it.ann, it.doc.fuse(shallow))
        }
        is DocF.FlatAlt -> Eval.later {
            DocF.FlatAlt(it.l.fuse(shallow), it.r.fuse(shallow))
        }
        is DocF.Union -> Eval.later {
            DocF.Union(it.l.fuse(shallow), it.r.fuse(shallow))
        }
        else -> if (shallow) Eval.now(it) else when (it) {
            is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(it.doc.andThen { it.fuse(false) }))
            is DocF.Nesting -> Eval.now(DocF.Nesting(it.doc.andThen { it.fuse(false) }))
            is DocF.Column -> Eval.now(DocF.Column(it.doc.andThen { it.fuse(false) }))
            else -> Eval.now(it)
        }
    }
})
