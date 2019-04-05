package viper.gobra.frontend.info.implementation.property

import viper.gobra.ast.frontend._
import viper.gobra.frontend.info.base.Type._
import viper.gobra.frontend.info.implementation.TypeInfoImpl

trait Assignability extends BaseProperty { this: TypeInfoImpl =>

  import viper.gobra.util.Violation._

  sealed trait AssignModi

  case object SingleAssign extends AssignModi

  case object MultiAssign extends AssignModi

  case object ErrorAssign extends AssignModi

  def assignModi(left: Int, right: Int): AssignModi =
    if (left > 0 && left == right) SingleAssign
    else if (left > right && right == 1) MultiAssign
    else ErrorAssign

  lazy val declarableTo: Property[(Vector[Type], Option[Type], Vector[Type])] =
    createProperty[(Vector[Type], Option[Type], Vector[Type])] {
      case (right, None, left) => multiAssignableTo.result(right, left)
      case (right, Some(t), _) => propForall(right, assignableTo.before((l: Type) => (l, t)))
    }

  lazy val multiAssignableTo: Property[(Vector[Type], Vector[Type])] = createProperty[(Vector[Type], Vector[Type])] {
    case (right, left) =>
      assignModi(left.size, right.size) match {
        case SingleAssign => propForall(right.zip(left), assignableTo)
        case MultiAssign => right.head match {
          case Assign(InternalTupleT(ts)) if ts.size == left.size => propForall(ts.zip(left), assignableTo)
          case t => failedProp(s"got $t but expected tuple type of size ${left.size}")
        }
        case ErrorAssign => failedProp(s"cannot assign ${right.size} to ${left.size} elements")
      }
  }

  lazy val parameterAssignableTo: Property[(Type, Type)] = createProperty[(Type, Type)] {
    case (Argument(InternalTupleT(rs)), Argument(InternalTupleT(ls))) if rs.size == ls.size =>
      propForall(rs zip ls, assignableTo)

    case (r, l) => assignableTo.result(r, l)
  }

  lazy val assignableTo: Property[(Type, Type)] = createFlatProperty[(Type, Type)] {
    case (left, right) => s"$left is not assignable to $right"
  } {
    case (Single(lst), Single(rst)) => (lst, rst) match {
      case (l, r) if identicalTypes(l, r) => true
      case (l, r) if !(l.isInstanceOf[DeclaredT] && r.isInstanceOf[DeclaredT])
        && identicalTypes(underlyingType(l), underlyingType(r)) => true
      case (l, r: InterfaceT) if implements(l, r) => true
      case (ChannelT(le, ChannelModus.Bi), ChannelT(re, _)) if identicalTypes(le, re) => true
      case (NilType, _: PointerT | _: FunctionT | _: SliceT | _: MapT | _: ChannelT | _: InterfaceT) => true
      case _ => false
    }
    case _ => false
  }

  lazy val assignable: Property[PExpression] = createBinaryProperty("assignable") {
    case PIndexedExp(b, _) if exprType(b).isInstanceOf[MapT] => true
    case e => addressable(e)
  }

  lazy val compatibleWithAssOp: Property[(Type, PAssOp)] = createFlatProperty[(Type, PAssOp)] {
    case (t, op) => s"type error: got $t, but expected type compatible with $op"
  } {
    case (Single(IntT), PAddOp() | PSubOp() | PMulOp() | PDivOp() | PModOp()) => true
    case _ => false
  }

  lazy val compositeKeyAssignableTo: Property[(PCompositeKey, Type)] = createProperty[(PCompositeKey, Type)] {
    case (PIdentifierKey(id), t) => assignableTo.result(idType(id), t)
    case (k: PCompositeVal, t) => compositeValAssignableTo.result(k, t)
  }

  lazy val compositeValAssignableTo: Property[(PCompositeVal, Type)] = createProperty[(PCompositeVal, Type)] {
    case (PExpCompositeVal(exp), t) => assignableTo.result(exprType(exp), t)
    case (PLitCompositeVal(lit), t) => literalAssignableTo.result(lit, t)
  }

  lazy val literalAssignableTo: Property[(PLiteralValue, Type)] = createProperty[(PLiteralValue, Type)] {
    case (PLiteralValue(elems), Single(right)) =>
      underlyingType(right) match {
        case StructT(decl) =>
          if (elems.isEmpty) {
            successProp
          } else if (elems.exists(_.key.nonEmpty)) {
            val tmap = (
              decl.embedded.map(e => (e.typ.name, miscType(e.typ))) ++
                decl.fields.map(f => (f.id.name, typeType(f.typ)))
              ).toMap

            failedProp("for struct literals either all or none elements must be keyed"
              , !elems.forall(_.key.nonEmpty)) and
              propForall(elems, createProperty[PKeyedElement] { e =>
                e.key.map {
                  case PIdentifierKey(id) if tmap.contains(id.name) =>
                    compositeValAssignableTo.result(e.exp, tmap(id.name))

                  case v => failedProp(s"got $v but expected field name")
                }.getOrElse(successProp)
              })
          } else if (elems.size == decl.embedded.size + decl.fields.size) {
            propForall(
              elems.map(_.exp).zip(decl.clauses.flatMap {
                case PEmbeddedDecl(typ, _) => Vector(miscType(typ))
                case PFieldDecls(fields) => fields map (f => typeType(f.typ))
              }),
              compositeValAssignableTo
            )
          } else {
            failedProp("number of arguments does not match structure")
          }

        case ArrayT(len, elem) =>
          failedProp("expected integer as keys for array literal"
            , elems.exists(_.key.exists {
              case PExpCompositeVal(exp) => intConstantEval(exp).isEmpty
              case _ => true
            })) and
            propForall(elems.map(_.exp), compositeValAssignableTo.before((c: PCompositeVal) => (c, elem))) and
            failedProp("found overlapping or out-of-bound index arguments"
              , {
                val idxs = constantIndexes(elems)
                idxs.distinct.size == idxs.size && idxs.forall(i => i >= 0 && i < len)
              })

        case SliceT(elem) =>
          failedProp("expected integer as keys for slice literal"
            , elems.exists(_.key.exists {
              case PExpCompositeVal(exp) => intConstantEval(exp).isEmpty
              case _ => true
            })) and
            propForall(elems.map(_.exp), compositeValAssignableTo.before((c: PCompositeVal) => (c, elem))) and
            failedProp("found overlapping or out-of-bound index arguments"
              , {
                val idxs = constantIndexes(elems)
                idxs.distinct.size == idxs.size && idxs.forall(i => i >= 0)
              })

        case MapT(key, elem) =>
          failedProp("for map literals all elements must be keyed"
            , elems.exists(_.key.isEmpty)) and
            propForall(elems.flatMap(_.key), compositeKeyAssignableTo.before((c: PCompositeKey) => (c, key))) and
            propForall(elems.map(_.exp), compositeValAssignableTo.before((c: PCompositeVal) => (c, elem)))

        case t => failedProp(s"cannot assign literal to $t")
      }
    case (l, t) => failedProp(s"cannot assign literal $l to $t")
  }

  private def constantIndexes(vs: Vector[PKeyedElement]): List[BigInt] =
    vs.foldLeft(List(-1: BigInt)) {
      case (last :: rest, PKeyedElement(Some(PExpCompositeVal(exp)), _)) =>
        intConstantEval(exp).getOrElse(last + 1) :: last :: rest

      case (last :: rest, _) => last + 1 :: last :: rest

      case _ => violation("left argument must be non-nil element")
    }.tail
}