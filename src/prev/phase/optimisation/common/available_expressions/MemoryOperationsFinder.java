package prev.phase.optimisation.common.available_expressions;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Memory operations finder. Finds operations of form MEM(...) in expressions.
 */
public class MemoryOperationsFinder implements ImcVisitor<Object, HashSet<ImcMEM>> {

    public static HashSet<ImcMEM> getMemoryOperations(ImcInstr instruction) {
        HashSet<ImcMEM> memoryOperations = new HashSet<ImcMEM>();
        instruction.accept(new MemoryOperationsFinder(), memoryOperations);
        return memoryOperations;
    }
	
	public Object visit(ImcBINOP binOp, HashSet<ImcMEM> memoryOperations) {
        binOp.fstExpr.accept(this, memoryOperations);
        binOp.sndExpr.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcCALL call, HashSet<ImcMEM> memoryOperations) {
        for (ImcExpr argument : call.args())
            argument.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcCJUMP cjump, HashSet<ImcMEM> memoryOperations) {
        cjump.cond.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcCONST constant, HashSet<ImcMEM> memoryOperations) {
		return null;
	}

	public Object visit(ImcESTMT eStmt, HashSet<ImcMEM> memoryOperations) {
        eStmt.expr.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcJUMP jump, HashSet<ImcMEM> memoryOperations) {
		return null;
	}

	public Object visit(ImcLABEL label, HashSet<ImcMEM> memoryOperations) {
		return null;
	}

	public Object visit(ImcMEM mem, HashSet<ImcMEM> memoryOperations) {
		memoryOperations.add(mem);
		mem.addr.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcMOVE move, HashSet<ImcMEM> memoryOperations) {
        move.src.accept(this, memoryOperations);
        move.dst.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcNAME name, HashSet<ImcMEM> memoryOperations) {
		return null;
	}

	public Object visit(ImcSEXPR sExpr, HashSet<ImcMEM> memoryOperations) {
        sExpr.stmt.accept(this, memoryOperations);
        sExpr.expr.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcSTMTS stmts, HashSet<ImcMEM> memoryOperations) {
        for (ImcStmt statement : stmts.stmts())
            statement.accept(this, memoryOperations);
		return null;
	}

	public Object visit(ImcTEMP temp, HashSet<ImcMEM> memoryOperations) {
		return null;
	}

	public Object visit(ImcUNOP unOp, HashSet<ImcMEM> memoryOperations) {
        unOp.subExpr.accept(this, memoryOperations);
		return null;
	}

}
