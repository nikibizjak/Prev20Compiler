package prev.phase.optimisation.common.available_expressions;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

/**
 * Temporary finder. Finds temporaries in expressions.
 */
public class TemporaryFinder implements ImcVisitor<Object, HashSet<ImcTEMP>> {

    public static HashSet<ImcTEMP> getTemporaries(ImcInstr instruction) {
        HashSet<ImcTEMP> temporaries = new HashSet<ImcTEMP>();
        instruction.accept(new TemporaryFinder(), temporaries);
        return temporaries;
    }
	
	public Object visit(ImcBINOP binOp, HashSet<ImcTEMP> temporaries) {
        binOp.fstExpr.accept(this, temporaries);
        binOp.sndExpr.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcCALL call, HashSet<ImcTEMP> temporaries) {
        for (ImcExpr argument : call.args())
            argument.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcCJUMP cjump, HashSet<ImcTEMP> temporaries) {
        cjump.cond.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcCONST constant, HashSet<ImcTEMP> temporaries) {
		return null;
	}

	public Object visit(ImcESTMT eStmt, HashSet<ImcTEMP> temporaries) {
        eStmt.expr.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcJUMP jump, HashSet<ImcTEMP> temporaries) {
		return null;
	}

	public Object visit(ImcLABEL label, HashSet<ImcTEMP> temporaries) {
		return null;
	}

	public Object visit(ImcMEM mem, HashSet<ImcTEMP> temporaries) {
        mem.addr.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcMOVE move, HashSet<ImcTEMP> temporaries) {
        move.src.accept(this, temporaries);
        move.dst.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcNAME name, HashSet<ImcTEMP> temporaries) {
		return null;
	}

	public Object visit(ImcSEXPR sExpr, HashSet<ImcTEMP> temporaries) {
        sExpr.stmt.accept(this, temporaries);
        sExpr.expr.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcSTMTS stmts, HashSet<ImcTEMP> temporaries) {
        for (ImcStmt statement : stmts.stmts())
            statement.accept(this, temporaries);
		return null;
	}

	public Object visit(ImcTEMP temp, HashSet<ImcTEMP> temporaries) {
        temporaries.add(temp);
		return null;
	}

	public Object visit(ImcUNOP unOp, HashSet<ImcTEMP> temporaries) {
        unOp.subExpr.accept(this, temporaries);
		return null;
	}

}
