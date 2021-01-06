package prev.phase.optimisation.common.liveness_analysis;

import java.util.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Machine code generator for ststements.
 */
public class StmtGenerator implements ImcVisitor<Object, StmtGenerator.UsesDefinitions> {
	
	public Object visit(ImcCJUMP cjump, UsesDefinitions data) {
		cjump.cond.accept(new ExprGenerator(), data.uses);
		return null;
	}

	public Object visit(ImcESTMT eStmt, UsesDefinitions data) {
		eStmt.expr.accept(new ExprGenerator(), data.uses);
		return null;
	}

	public Object visit(ImcJUMP jump, UsesDefinitions data) {
		return null;
	}

	public Object visit(ImcLABEL label, UsesDefinitions data) {
		return null;
	}

	public Object visit(ImcMOVE move, UsesDefinitions data) {
		if (move.dst instanceof ImcTEMP)
			move.dst.accept(new ExprGenerator(), data.definitions);
		else
			move.dst.accept(new ExprGenerator(), data.uses);
		
		move.src.accept(new ExprGenerator(), data.uses);
		return null;
	}

	public Object visit(ImcSTMTS stmts, UsesDefinitions data) {
		for (ImcStmt statement : stmts.stmts()) {
			statement.accept(this, data);
		}
		return null;
	}

	static class UsesDefinitions {
		public Vector<ImcTEMP> uses;
		public Vector<ImcTEMP> definitions;
		public UsesDefinitions(Vector<ImcTEMP> uses, Vector<ImcTEMP> definitions) {
			this.uses = uses;
			this.definitions = definitions;
		}
	}

}