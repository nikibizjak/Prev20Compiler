package prev.phase.optimisation.constant_folding;

import java.util.*;

import prev.common.report.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Statement canonizer.
 */
public class StatementConstantFolder implements ImcVisitor<ImcStmt, Object> {

	public ImcStmt visit(ImcCJUMP imcCJump, Object argument) {
		ImcExpr cond = imcCJump.cond.accept(new ExpressionConstantFolder(), null);
		return new ImcCJUMP(cond, imcCJump.posLabel, imcCJump.negLabel);
	}

	public ImcStmt visit(ImcESTMT imcEStmt, Object argument) {
		ImcExpr expr = imcEStmt.expr.accept(new ExpressionConstantFolder(), null);
		return new ImcESTMT(expr);
	}

	public ImcStmt visit(ImcJUMP imcJump, Object argument) {
		return imcJump;
	}

	public ImcStmt visit(ImcLABEL imcLabel, Object argument) {
		return imcLabel;
	}

	public ImcStmt visit(ImcMOVE imcMove, Object argument) {
        ImcExpr source = imcMove.src.accept(new ExpressionConstantFolder(), null);
        ImcExpr destination = imcMove.dst.accept(new ExpressionConstantFolder(), null);
		return new ImcMOVE(destination, source);
	}

	public ImcStmt visit(ImcSTMTS imcStmts, Object argument) {
		Vector<ImcStmt> result = new Vector<ImcStmt>();
		for (ImcStmt stmt : imcStmts.stmts()) {
			result.add(stmt.accept(this, null));
		}
		return new ImcSTMTS(result);
	}

}
