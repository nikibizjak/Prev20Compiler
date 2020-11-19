package prev.phase.optimisation.constant_folding;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Expression constant folder.
 */
public class ExpressionConstantFolder implements ImcVisitor<ImcExpr, Object> {

    public ImcExpr visit(ImcBINOP imcBinop, Object argument) {
        ImcExpr first = imcBinop.fstExpr.accept(this, argument);
        ImcExpr second = imcBinop.sndExpr.accept(this, argument);

        if (first instanceof ImcCONST && second instanceof ImcCONST) {

            ImcCONST firstConstant = (ImcCONST) first;
            ImcCONST secondConstant = (ImcCONST) second;

            Long result = null;
            switch (imcBinop.oper) {
                case ADD: result = firstConstant.value + secondConstant.value; break;
                case SUB: result = firstConstant.value - secondConstant.value; break;
                case MUL: result = firstConstant.value * secondConstant.value; break;
                case DIV: result = firstConstant.value / secondConstant.value; break;
                case MOD: result = firstConstant.value % secondConstant.value; break;
                case OR:
                    result = ((firstConstant.value == 1L) || (secondConstant.value == 1L)) ? 1L : 0L;
                    break;
                case AND:
                    result = ((firstConstant.value == 1L) && (secondConstant.value == 1L)) ? 1L : 0L;
                    break;
                case EQU: result = (firstConstant.value == secondConstant.value) ? 1L : 0L; break;
                case NEQ: result = (firstConstant.value != secondConstant.value) ? 1L : 0L; break;
                case LTH: result = (firstConstant.value < secondConstant.value) ? 1L : 0L; break;
                case GTH: result = (firstConstant.value > secondConstant.value) ? 1L : 0L; break;
                case LEQ: result = (firstConstant.value <= secondConstant.value) ? 1L : 0L; break;
                case GEQ: result = (firstConstant.value >= secondConstant.value) ? 1L : 0L; break;
                default: result = null;
            }

            if (result != null)
                return new ImcCONST(result);
            return new ImcBINOP(imcBinop.oper, first, second);
        } else {
            return new ImcBINOP(imcBinop.oper, first, second);
        }
	}
	
	public ImcExpr visit(ImcCALL imcCall, Object argument) {
        Vector<ImcExpr> newArguments = new Vector<ImcExpr>();
        for (ImcExpr expression : imcCall.args()) {
            newArguments.add(expression.accept(this, null));
        }
		return new ImcCALL(imcCall.label, imcCall.offs(), newArguments);
	}

	public ImcExpr visit(ImcCONST imcConst, Object argument) {
		return imcConst;
	}

	public ImcExpr visit(ImcMEM imcMem, Object argument) {
        return new ImcMEM(imcMem.addr.accept(this, argument));
	}

	public ImcExpr visit(ImcNAME imcName, Object argument) {
		return imcName;
	}

	public ImcExpr visit(ImcSEXPR imcSExpr, Object argument) {
        ImcStmt newStatement = imcSExpr.stmt.accept(new StatementConstantFolder(), null);
        ImcExpr newExpression = imcSExpr.expr.accept(this, argument);
		return new ImcSEXPR(newStatement, newExpression);
	}
	
	public ImcExpr visit(ImcTEMP imcTemp, Object argument) {
		return imcTemp;
	}
	
	public ImcExpr visit(ImcUNOP imcUnop, Object argument) {
        ImcExpr newExpression = imcUnop.subExpr.accept(this, argument);
        if (newExpression instanceof ImcCONST) {

            ImcCONST constantExpression = (ImcCONST) newExpression;

            Long result = null;
            switch (imcUnop.oper) {
                case NEG: result = constantExpression.value; break;
                case NOT: result = (constantExpression.value == 1L) ? 0L : 1L;
            }

            if (result != null)
                return new ImcCONST(result);
            return new ImcUNOP(imcUnop.oper, newExpression);
        } else {
            return new ImcUNOP(imcUnop.oper, newExpression);
        }
	}

}