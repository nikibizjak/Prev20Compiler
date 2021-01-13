package prev.phase.optimisation.constant_folding;

import java.util.*;

import prev.common.report.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Statement constant folder.
 */
public class StatementConstantFolder implements ImcVisitor<ImcStmt, ImcVisitor<ImcExpr, Object>> {

	public ImcStmt visit(ImcCJUMP imcCJump, ImcVisitor<ImcExpr, Object> expressionVisitor) {
		ImcExpr cond = imcCJump.cond.accept(expressionVisitor, null);
		return new ImcCJUMP(cond, imcCJump.posLabel, imcCJump.negLabel);
	}

	public ImcStmt visit(ImcESTMT imcEStmt, ImcVisitor<ImcExpr, Object> expressionVisitor) {
		ImcExpr expr = imcEStmt.expr.accept(expressionVisitor, null);
		return new ImcESTMT(expr);
	}

	public ImcStmt visit(ImcJUMP imcJump, ImcVisitor<ImcExpr, Object> expressionVisitor) {
		return imcJump;
	}

	public ImcStmt visit(ImcLABEL imcLabel, ImcVisitor<ImcExpr, Object> expressionVisitor) {
		return imcLabel;
	}

	public ImcStmt visit(ImcMOVE imcMove, ImcVisitor<ImcExpr, Object> expressionVisitor) {
        ImcExpr source = imcMove.src.accept(expressionVisitor, null);
        ImcExpr destination = imcMove.dst.accept(expressionVisitor, null);
		return new ImcMOVE(destination, source);
	}

	public ImcStmt visit(ImcSTMTS imcStmts, ImcVisitor<ImcExpr, Object> expressionVisitor) {
		Vector<ImcStmt> result = new Vector<ImcStmt>();
		for (ImcStmt stmt : imcStmts.stmts()) {
			result.add(stmt.accept(this, expressionVisitor));
		}
		return new ImcSTMTS(result);
	}

}
