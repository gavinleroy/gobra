package viper.gobra.ast.frontend

import viper.gobra.frontend.info.base.{SymbolTable => st}
import viper.gobra.frontend.info.implementation.resolution.MemberPath

object AstPattern {

  sealed trait Pattern

  sealed trait Symbolic {
    def symb: st.Regular
  }

  sealed trait Type extends Pattern

  case class NamedType(id: PIdnUse, symb: st.NamedType) extends Type with Symbolic
  case class PointerType(base: PType) extends Type

  sealed trait Expr extends Pattern

  case class LocalVariable(id: PIdnUse, symb: st.Variable) extends Expr with Symbolic // In the future: with FunctionKind
  case class Deref(base: PExpression) extends Expr
  case class FieldSelection(base: PExpression, id: PIdnUse, path: Vector[MemberPath], symb: st.StructMember) extends Expr with Symbolic
  case class Conversion(typ: PType, arg: Vector[PExpression]) extends Expr
  case class FunctionCall(callee: FunctionKind, args: Vector[PExpression]) extends Expr

  sealed trait FunctionKind extends Expr

  case class Function(id: PIdnUse, symb: st.Function) extends FunctionKind with Symbolic
  case class ReceivedMethod(recv: PExpression, id: PIdnUse, path: Vector[MemberPath], symb: st.Method) extends FunctionKind with Symbolic
  case class MethodExpr(typ: PType, id: PIdnUse, path: Vector[MemberPath], symb: st.Method) extends FunctionKind with Symbolic

  sealed trait Assertion extends Pattern

  case class PredicateCall(predicate: PredicateKind, args: Vector[PExpression]) extends Assertion

  sealed trait PredicateKind extends Assertion

  case class Predicate(id: PIdnUse, symb: st.FPredicate) extends PredicateKind with Symbolic
  case class ReceivedPredicate(recv: PExpression, id: PIdnUse, path: Vector[MemberPath], symb: st.MPredicate) extends PredicateKind with Symbolic
  case class PredicateExpr(typ: PType, id: PIdnUse, path: Vector[MemberPath], symb: st.MPredicate) extends PredicateKind with Symbolic

}