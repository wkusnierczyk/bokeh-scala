package io.continuum.bokeh

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox.Context

private object ModelImpl {
    def macroTransformImpl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
        import c.universe._

        annottees.map(_.tree) match {
            case ClassDef(mods, name, tparams, tpl @ Template(parents, sf, body)) :: companion =>
                val bokeh = q"io.continuum.bokeh"

                val expandedBody = body.flatMap {
                    case q"$prefix = include[$mixin]" =>
                        // XXX: should be c.typecheck(tq"$mixin", c.TYPEMODE)
                        val tpe = c.typeCheck(q"null: $mixin").tpe

                        val fields = tpe.members
                            .filter(_.isModule)
                            .map(_.asModule)
                            .filter(_.typeSignature <:< typeOf[AbstractField])

                        fields.map { field =>
                            val name = newTermName(s"${prefix}_${field.name}")
                            val sig = field.typeSignature
                            val tpe = sig.member(newTypeName("ValueType")).typeSignatureIn(sig)
                            // TODO: add support for precise field type (Vectorized, NonNegative, etc.)
                            q"object $name extends this.Field[$tpe]"
                        }
                    case field => field :: Nil
                }

                val newMethods = List(q"""override def fields: scala.List[$bokeh.FieldRef] = $bokeh.Fields.fields(this)""")
                val decl = ClassDef(mods, name, tparams, Template(parents, sf, expandedBody ++ newMethods))

                c.Expr[Any](Block(decl :: companion, Literal(Constant(()))))
            case _ => c.abort(c.enclosingPosition, "expected a class")
        }
    }
}

class model extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro ModelImpl.macroTransformImpl
}
