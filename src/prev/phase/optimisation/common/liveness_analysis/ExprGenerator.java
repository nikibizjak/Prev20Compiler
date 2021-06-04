package prev.phase.optimisation.common.liveness_analysis;

import java.util.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.visitor.*;
import prev.common.report.*;

/**
 * Machine code generator for expressions.
 */
public class ExprGenerator implements ImcVisitor<Object, Vector<ImcTEMP>> {

	public Object visit(ImcBINOP binOp, Vector<ImcTEMP> data) {
		binOp.fstExpr.accept(this, data);
		binOp.sndExpr.accept(this, data);
		return null;
	}

	public Object visit(ImcCALL call, Vector<ImcTEMP> data) {
		for (ImcExpr argument : call.args())
			argument.accept(this, data);
		return null;
	}

	public Object visit(ImcCONST constant, Vector<ImcTEMP> data) {
		return null;
	}

	public Object visit(ImcMEM mem, Vector<ImcTEMP> data) {
		mem.addr.accept(this, data);
		return null;
	}

	public Object visit(ImcNAME name, Vector<ImcTEMP> data) {
		return null;
	}

	public Object visit(ImcSEXPR sExpr, Vector<ImcTEMP> data) {
		throw new Report.Error("SEXPR statements not allowed in liveness analysis");
	}

	public Object visit(ImcTEMP temp, Vector<ImcTEMP> data) {
		data.add(temp);
		return null;
	}

	public Object visit(ImcUNOP unOp, Vector<ImcTEMP> data) {
		unOp.subExpr.accept(this, data);
		return null;
	}

}