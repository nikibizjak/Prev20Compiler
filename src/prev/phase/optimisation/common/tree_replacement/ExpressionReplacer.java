package prev.phase.optimisation.common.tree_replacement;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

public class ExpressionReplacer implements ImcVisitor<ImcExpr, Replacement> {
	
	public ImcExpr visit(ImcBINOP imcBinop, Replacement replacement) {
        if (replacement.replace.equals(imcBinop)) {
            return replacement.replaceWith;
        }
		ImcExpr fstExpr = imcBinop.fstExpr.accept(this, replacement);
		ImcExpr sndExpr = imcBinop.sndExpr.accept(this, replacement);
		return new ImcBINOP(imcBinop.oper, fstExpr, sndExpr);
	}
	
	public ImcExpr visit(ImcCALL imcCall, Replacement replacement) {
        if (replacement.replace.equals(imcCall)) {
            return replacement.replaceWith;
        }

		Vector<ImcExpr> newArguments = new Vector<ImcExpr>();
		for (ImcExpr arg: imcCall.args()) {
			ImcExpr newArgument = arg.accept(this, replacement);
			newArguments.add(newArgument);
		}
		return new ImcCALL(imcCall.label, imcCall.offs(), newArguments);
	}

	public ImcExpr visit(ImcCONST imcConst, Replacement replacement) {
        if (replacement.replace.equals(imcConst)) {
            return replacement.replaceWith;
        }
		return imcConst;
	}

	public ImcExpr visit(ImcMEM imcMem, Replacement replacement) {
        if (replacement.replace.equals(imcMem)) {
            return replacement.replaceWith;
        }
		ImcExpr addr = imcMem.addr.accept(this, replacement);
		return new ImcMEM(addr);
	}

	public ImcExpr visit(ImcNAME imcName, Replacement replacement) {
        if (replacement.replace.equals(imcName)) {
            return replacement.replaceWith;
        }
		return imcName;
	}

	public ImcExpr visit(ImcSEXPR imcSExpr, Replacement replacement) {
        if (replacement.replace.equals(imcSExpr)) {
            return replacement.replaceWith;
        }
		ImcStmt statement = imcSExpr.stmt.accept(new StatementReplacer(), replacement);
        ImcExpr expression = imcSExpr.expr.accept(this, replacement);
        return new ImcSEXPR(statement, expression);
	}
	
	public ImcExpr visit(ImcTEMP imcTemp, Replacement replacement) {
        if (replacement.replace.equals(imcTemp)) {
            return replacement.replaceWith;
        }
		return imcTemp;
	}
	
	public ImcExpr visit(ImcUNOP imcUnop, Replacement replacement) {
        if (replacement.replace.equals(imcUnop)) {
            return replacement.replaceWith;
        }
		ImcExpr subExpr = imcUnop.subExpr.accept(this, replacement);
		return new ImcUNOP(imcUnop.oper, subExpr);
	}

}
