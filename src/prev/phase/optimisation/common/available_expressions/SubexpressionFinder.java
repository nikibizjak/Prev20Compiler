package prev.phase.optimisation.common.available_expressions;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Subexpression finder. Finds all subexpressions of current expression (except
 * TEMP and CONST).
 */
public class SubexpressionFinder implements ImcVisitor<Object, HashSet<ImcExpr>> {

    public static HashSet<ImcExpr> getAllSubexpressions(ImcInstr instruction) {
        HashSet<ImcExpr> subexpressions = new HashSet<ImcExpr>();
        instruction.accept(new SubexpressionFinder(), subexpressions);
        return subexpressions;
    }
	
	public Object visit(ImcBINOP binOp, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(binOp);
        binOp.fstExpr.accept(this, subexpressions);
        binOp.sndExpr.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcCALL call, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(call);
        for (ImcExpr argument : call.args())
            argument.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcCJUMP cjump, HashSet<ImcExpr> subexpressions) {
        cjump.cond.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcCONST constant, HashSet<ImcExpr> subexpressions) {
		return null;
	}

	public Object visit(ImcESTMT eStmt, HashSet<ImcExpr> subexpressions) {
        eStmt.expr.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcJUMP jump, HashSet<ImcExpr> subexpressions) {
		return null;
	}

	public Object visit(ImcLABEL label, HashSet<ImcExpr> subexpressions) {
		return null;
	}

	public Object visit(ImcMEM mem, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(mem);
        mem.addr.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcMOVE move, HashSet<ImcExpr> subexpressions) {
        move.src.accept(this, subexpressions);
        move.dst.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcNAME name, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(name);
		return null;
	}

	public Object visit(ImcSEXPR sExpr, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(sExpr);
        sExpr.stmt.accept(this, subexpressions);
        sExpr.expr.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcSTMTS stmts, HashSet<ImcExpr> subexpressions) {
        for (ImcStmt statement : stmts.stmts())
            statement.accept(this, subexpressions);
		return null;
	}

	public Object visit(ImcTEMP temp, HashSet<ImcExpr> subexpressions) {
		return null;
	}

	public Object visit(ImcUNOP unOp, HashSet<ImcExpr> subexpressions) {
		subexpressions.add(unOp);
        unOp.subExpr.accept(this, subexpressions);
		return null;
	}

}
