package prev.phase.imclin;

import java.util.*;

import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.visitor.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.lin.*;
import prev.phase.imcgen.ImcGen;
import prev.phase.memory.*;

public class ChunkGenerator extends AstFullVisitor<Object, Object> {

	@Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
		switch (atomExpr.type()) {
		case STRING:
			MemAbsAccess absAccess = Memory.strings.get(atomExpr);
			ImcLin.addDataChunk(new LinDataChunk(absAccess));
			break;
		default:
			break;
		}
		return null;
	}

	@Override
	public Object visit(AstFunDecl funDecl, Object arg) {
		funDecl.expr().accept(this, arg);

		MemFrame frame = Memory.frames.get(funDecl);
		MemLabel entryLabel = new MemLabel();
		MemLabel exitLabel = new MemLabel();
		
		Vector<ImcStmt> canonStmts = new Vector<ImcStmt>();
		canonStmts.add(new ImcLABEL(entryLabel));
		ImcExpr bodyExpr = ImcGen.exprImc.get(funDecl.expr());
		ImcStmt bodyStmt = new ImcMOVE(new ImcTEMP(frame.RV), bodyExpr);
		canonStmts.addAll(bodyStmt.accept(new StmtCanonizer(), null));
		canonStmts.add(new ImcJUMP(exitLabel));
		
		Vector<ImcStmt> linearStmts = linearize (canonStmts);
		ImcLin.addCodeChunk(new LinCodeChunk(frame, linearStmts, entryLabel, exitLabel));
		
		return null;
	}

	@Override
	public Object visit(AstVarDecl varDecl, Object arg) {
		MemAccess access = Memory.accesses.get(varDecl);
		if (access instanceof MemAbsAccess) {
			MemAbsAccess absAccess = (MemAbsAccess) access;
			ImcLin.addDataChunk(new LinDataChunk(absAccess));
		}
		return null;
	}
	
	private Vector<ImcStmt> linearize(Vector<ImcStmt> stmts) {
		// The CALL and STMTS expression problems were already solved by our
		// professor, we have to fix the CJUMP statements

		Vector<ImcStmt> linearizedStatements = new Vector<ImcStmt>();
		for (ImcStmt statement : stmts) {
			// Everything that is not CJUMP should simply be copyed
			if (!(statement instanceof ImcCJUMP)) {
				linearizedStatements.add(statement);
				continue;
			}

			// We have reached the CJUMp statement, 
			ImcCJUMP conditionalJumpStatement = (ImcCJUMP) statement;

			// Here, we have reached the CJUMP statement, create a new negative label
			ImcLABEL newNegativeLabel = new ImcLABEL(new MemLabel());
			
			linearizedStatements.add(new ImcCJUMP(conditionalJumpStatement.cond, conditionalJumpStatement.posLabel, newNegativeLabel.label));
			linearizedStatements.add(newNegativeLabel);
			linearizedStatements.add(new ImcJUMP(conditionalJumpStatement.negLabel));

		}
		return linearizedStatements;

	}

}
