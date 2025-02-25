// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.frontend.info.implementation.typing

import org.bitbucket.inkytonik.kiama.util.Messaging.{Messages, error, noMessages}
import viper.gobra.ast.frontend._
import viper.gobra.frontend.info.base.Type.{BooleanT, ChannelModus, ChannelT, FunctionT, InterfaceT, InternalTupleT, Type}
import viper.gobra.frontend.info.implementation.TypeInfoImpl

trait StmtTyping extends BaseTyping { this: TypeInfoImpl =>

  import viper.gobra.util.Violation._

  lazy val wellDefStmt: WellDefinedness[PStatement] = createWellDef {
    case stmt: PActualStatement => wellDefActualStmt(stmt)
    case stmt: PGhostStatement  => wellDefGhostStmt(stmt)
  }

  private[typing] def wellDefActualStmt(stmt: PActualStatement): Messages = stmt match {

    case PConstDecl(decls) => decls flatMap {
      case n@PConstSpec(typ, right, left) =>
        right.flatMap(isExpr(_).out) ++
          declarableTo.errors(right map exprType, typ map typeSymbType, left map idType)(n)
    }

    case n@PVarDecl(typ, right, left, _) =>
      right.flatMap(isExpr(_).out) ++
        declarableTo.errors(right map exprType, typ map typeSymbType, left map idType)(n)

    case n: PTypeDecl => isType(n.right).out ++ (n.right match {
      case s: PStructType =>
        error(n, s"invalid recursive type ${n.left.name}", cyclicStructDef(s, Some(n.left)))
      case s: PInterfaceType =>
        error(n, s"invalid recursive type ${n.left.name}", cyclicInterfaceDef(s, Some(n.left)))
      case _ => noMessages
    })

    case n@PExpressionStmt(exp) => isExpr(exp).out ++ isExecutable.errors(exp)(n)

    case n@PSendStmt(chn, msg) =>
      isExpr(chn).out ++ isExpr(msg).out ++
        ((exprType(chn), exprType(msg)) match {
          case (ChannelT(elem, ChannelModus.Bi | ChannelModus.Send), t) => assignableTo.errors(t, elem)(n)
          case (chnt, _) => error(n, s"type error: got $chnt but expected send-permitting channel")
        })

    case n@PAssignment(rights, lefts) =>
      rights.flatMap(isExpr(_).out) ++ lefts.flatMap(isExpr(_).out) ++
        lefts.flatMap(a => assignable.errors(a)(a)) ++ multiAssignableTo.errors(rights map exprType, lefts map exprType)(n)

    case n@PAssignmentWithOp(right, op@(_: PShiftLeftOp | _: PShiftRightOp), left) =>
      isExpr(right).out ++ isExpr(left).out ++
        assignable.errors(left)(n) ++ compatibleWithAssOp.errors(exprType(left), op)(n) ++
        assignableTo.errors(exprType(right), UNTYPED_INT_CONST)(n)

    case n@PAssignmentWithOp(right, op, left) =>
      isExpr(right).out ++ isExpr(left).out ++
        assignable.errors(left)(n) ++ compatibleWithAssOp.errors(exprType(left), op)(n) ++
        assignableTo.errors(exprType(right), exprType(left))(n)

    case n@PShortVarDecl(rights, lefts, _) =>
      // TODO: check that at least one of the variables is new
      if (lefts.forall(pointsToData))
        rights.flatMap(isExpr(_).out) ++
          multiAssignableTo.errors(rights map exprType, lefts map idType)(n)
      else error(n, s"at least one assignee in $lefts points to a type")

    case _: PLabeledStmt => noMessages

    case n: PIfStmt => n.ifs.flatMap(ic =>
      isExpr(ic.condition).out ++
        comparableTypes.errors(exprType(ic.condition), BooleanT)(ic)
    )

    case n@PExprSwitchStmt(_, exp, _, dflt) =>
      error(n, s"found more than one default case", dflt.size > 1) ++
        isExpr(exp).out ++ comparableType.errors(exprType(exp))(n)

    case n@tree.parent.pair(PExprSwitchCase(left, _), sw: PExprSwitchStmt) =>
      left.flatMap(e => isExpr(e).out ++ comparableTypes.errors(exprType(e), exprType(sw.exp))(n))

    case n: PTypeSwitchStmt =>
      val firstChecks = error(n, s"found more than one default case", n.dflt.size > 1) ++ isExpr(n.exp).out ++ {
        val et = exprType(n.exp)
        val ut = underlyingType(et)
        error(n, s"type error: got $et but expected underlying interface type", !ut.isInstanceOf[InterfaceT])
      }
      val latterChecks = {
        val expTyp = exprOrTypeType(n.exp)
        n.cases.flatMap(_.left).flatMap {
          case t: PType => implements(typeSymbType(t), expTyp).asReason(t, s"impossible type switch case: ${n.exp} (type $expTyp) cannot have dynamic type $t")
          case e: PExpression => error(e, s"$e is not valid in type switch clauses", !e.isInstanceOf[PNilLit])
        }
      }
      if (firstChecks.isEmpty) latterChecks else firstChecks

    case n@PForStmt(_, cond, _, _, _) => isExpr(cond).out ++ comparableTypes.errors(exprType(cond), BooleanT)(n)

    case _@PShortForRange(range, shorts, _, _) =>
      multiAssignableTo.errors(Vector(miscType(range)), shorts map idType)(range)

    case _@PAssForRange(range, ass, _, _) =>
      multiAssignableTo.errors(Vector(miscType(range)), ass map exprType)(range)

    case n@PGoStmt(exp) => isExpr(exp).out ++ isExecutable.errors(exp)(n)

    case n: PSelectStmt =>
      n.aRec.flatMap(rec =>
        rec.ass.flatMap(isExpr(_).out) ++
          multiAssignableTo.errors(Vector(exprType(rec.recv)), rec.ass.map(exprType))(rec) ++
          rec.ass.flatMap(a => assignable.errors(a)(a))
      ) ++ n.sRec.flatMap(rec =>
        if (rec.shorts.forall(pointsToData))
          multiAssignableTo.errors(Vector(exprType(rec.recv)), rec.shorts map idType)(rec)
        else error(n, s"at least one assignee in ${rec.shorts} points to a type")
      )

    case n@PReturn(exps) =>
      exps.flatMap(isExpr(_).out) ++ {
        if (exps.nonEmpty) {
          val closureImplProof = tryEnclosingClosureImplementationProof(n)
          if (closureImplProof.isEmpty) {
            val res = tryEnclosingCodeRootWithResult(n)
            if (res.isEmpty) return error(n, s"Statement does not root in a CodeRoot")
            if (!(res.get.result.outs forall wellDefMisc.valid)) return error(n, s"return cannot be checked because the enclosing signature is incorrect")
          }
          multiAssignableTo.errors(exps map exprType, returnParamsAndTypes(n).map(_._1))(n)
        } else noMessages // a return without arguments is always well-defined
      }

    case n@PDeferStmt(exp: PExpression) => isExpr(exp).out ++ isExecutable.errors(exp)(n)
    case PDeferStmt(_: PUnfold | _: PFold) => noMessages

    case _: PBlock => noMessages
    case _: PSeq => noMessages

    case n: POutline =>
      val invalidNodes: Vector[Messages] = allChildren(n) collect {
        case n@ (_: POld | _: PLabeledOld) => error(n, "outline statements must not contain old expressions, use a before expression instead.")
        case n: PDeferStmt => error(n, "Currently, outline statements are not allowed to contain defer statements.")
        case n: PReturn => error(n, "outline statements must not contain return statements.")
      }
      error(n, s"pure outline statements are not supported.", n.spec.isPure) ++ invalidNodes.flatten

    case _: PEmptyStmt => noMessages
    case _: PGoto => ???

    case n@PBreak(l) =>
      l match {
        case None =>
          enclosingLoopUntilOutline(n) match {
            case Left(Some(_: POutline)) => error(n, "break must be inside of a loop without an outline statement in between.")
            case Left(_) => error(n, s"break must be inside a loop.")
            case Right(_) => noMessages
          }
        case Some(label) =>
          val maybeLoop = enclosingLabeledLoop(label, n)
          maybeLoop match {
            case Left(Some(_: POutline)) => error(n, "break label must point to an outer labeled loop without an outline statement in between.")
            case Left(_) => error(n, s"break label must point to an outer labeled loop.")
            case Right(_) => noMessages
          }
      }

    case n@PContinue(l) =>
      l match {
        case None =>
          enclosingLoopUntilOutline(n) match {
            case Left(Some(_: POutline)) => error(n, "continue must be inside of a loop without an outline statement in between.")
            case Left(_) => error(n, s"continue must be inside a loop.")
            case Right(_) => noMessages
          }
        case Some(label) =>
          val maybeLoop = enclosingLabeledLoop(label, n)
          maybeLoop match {
            case Left(Some(_: POutline)) => error(n, "continue label must point to an outer labeled loop without an outline statement in between.")
            case Left(_) => error(n, s"continue label must point to an outer labeled loop.")
            case Right(_) => noMessages
          }
      }

    case s => violation(s"$s was not handled")
  }

  private [typing] def returnParamsAndTypes(n: PReturn): Vector[(Type, PParameter)] = {
    val closureImplProof = tryEnclosingClosureImplementationProof(n)
    if (closureImplProof.nonEmpty) {
      (resolve(closureImplProof.get.impl.spec.func) match {
        case Some(AstPattern.Function(id, f)) => (idType(id).asInstanceOf[FunctionT].result, f.result.outs)
        case Some(AstPattern.Closure(id, c)) => (idType(id).asInstanceOf[FunctionT].result, c.result.outs)
        case _ => violation("this case should be unreachable")
      }) match {
        case (InternalTupleT(types), ps) => types zip ps
        case (t, ps) => Vector(t) zip ps
      }
    } else {
      val res = tryEnclosingCodeRootWithResult(n)
      res.get.result.outs map miscType zip res.get.result.outs
    }
  }
}
