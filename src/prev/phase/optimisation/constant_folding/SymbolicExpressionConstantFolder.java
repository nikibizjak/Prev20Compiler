package prev.phase.optimisation.constant_folding;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Expression constant folder.
 */
public class SymbolicExpressionConstantFolder implements ImcVisitor<ImcExpr, Object> {

    public ImcExpr visit(ImcBINOP imcBinop, Object argument) {
        ImcExpr first = imcBinop.fstExpr.accept(this, argument);
        ImcExpr second = imcBinop.sndExpr.accept(this, argument);

        if (imcBinop.oper == ImcBINOP.Oper.MUL) {
            // 0 * x = 0
            if (first instanceof ImcCONST && ((ImcCONST) first).value == 0L)
                return new ImcCONST(0);
            // x * 0 = 0
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 0L)
                return new ImcCONST(0);
            // 1 * x = x
            else if (first instanceof ImcCONST && ((ImcCONST) first).value == 1L)
                return second;
            // x * 1 = x
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 1L)
                return first;
        } else if (imcBinop.oper == ImcBINOP.Oper.DIV) {
            // x / 1 = x
            if (second instanceof ImcCONST && ((ImcCONST) second).value == 1L)
                return first;
        } else if (imcBinop.oper == ImcBINOP.Oper.ADD || imcBinop.oper == ImcBINOP.Oper.SUB) {
            // 0 + x = 0 - x = x
            if (first instanceof ImcCONST && ((ImcCONST) first).value == 0L)
                return second;
            // x + 0 = x - 0 = x
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 0L)
                return first;
        } else if (imcBinop.oper == ImcBINOP.Oper.OR) {
            // true or x = true
	        if (first instanceof ImcCONST && ((ImcCONST) first).value == 1L)
                return new ImcCONST(1);
            // x or true = true
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 1L)
                return new ImcCONST(1);
            
            // false or x = x
            else if (first instanceof ImcCONST && ((ImcCONST) first).value == 0L)
                return second;
            // x or false = x
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 0L)
                return first;            
        } else if (imcBinop.oper == ImcBINOP.Oper.AND) {
            // false and x = false
            if (first instanceof ImcCONST && ((ImcCONST) first).value == 0L)
                return new ImcCONST(0);
            // x and false = false
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 0L)
                return new ImcCONST(0);
            
            // true and x = x
            else if (first instanceof ImcCONST && ((ImcCONST) first).value == 1L)
                return second;
            // x and true = x
            else if (second instanceof ImcCONST && ((ImcCONST) second).value == 1L)
                return first;
        }

        return new ImcBINOP(imcBinop.oper, first, second);
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
         return new ImcUNOP(imcUnop.oper, newExpression);
	}

}