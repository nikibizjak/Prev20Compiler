package prev.phase.seman;

import prev.common.report.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.decl.AstDecl;
import prev.data.ast.tree.decl.AstVarDecl;
import prev.data.ast.tree.decl.AstParDecl;
import prev.data.ast.tree.stmt.AstAssignStmt;
import prev.data.ast.visitor.*;
import prev.data.semtype.SemPointer;
import prev.data.semtype.SemType;

/**
 * Address resolver.
 * 
 * The address resolver finds out which expressions denote lvalues and leaves
 * the information in {@link SemAn#isAddr}.
 */
public class AddrResolver extends AstFullVisitor<Object, Object> {

	@Override
	public Object visit(AstNameExpr nameExpr, Object arg) {
		// When visiting name expression, first get declaration where this
		// variable had been defined and check if it is variable or parameter
		// declaration. If true, the isAddr should be set to true.
		AstDecl declaration = SemAn.declaredAt.get(nameExpr);
		if (declaration instanceof AstVarDecl || declaration instanceof AstParDecl) {
			SemAn.isAddr.put(nameExpr, true);
		}
		
		return null;
	}

	@Override
	public Object visit(AstSfxExpr sfxExpr, Object arg) {
		if (sfxExpr.expr() != null)
			sfxExpr.expr().accept(this, arg);
		
		if (sfxExpr.oper() == AstSfxExpr.Oper.PTR) {
			// Check if the sfxExpr.expr() is of type SemPointer(something), if
			// so, this expression is address
			SemType expressionType = SemAn.ofType.get(sfxExpr.expr()).actualType();
			if (expressionType != null && expressionType instanceof SemPointer) {
				SemAn.isAddr.put(sfxExpr, true);
			}
		}
		return null;
	}

	@Override
	public Object visit(AstArrExpr arrExpr, Object arg) {
		if (arrExpr.arr() != null)
			arrExpr.arr().accept(this, arg);
		if (arrExpr.idx() != null)
			arrExpr.idx().accept(this, arg);

		Boolean isAddress = SemAn.isAddr.get(arrExpr.arr());
		if (isAddress != null && isAddress.booleanValue()) {
			SemAn.isAddr.put(arrExpr, true);
		}
		
		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, Object arg) {
		if (recExpr.rec() != null)
			recExpr.rec().accept(this, arg);
		if (recExpr.comp() != null)
			recExpr.comp().accept(this, arg);
		
		Boolean isAddress = SemAn.isAddr.get(recExpr.rec());
		if (isAddress != null && isAddress.booleanValue()) {
			SemAn.isAddr.put(recExpr, true);
		}

		return null;
	}

	@Override
	public Object visit(AstAssignStmt assignmentStatement, Object arg) {
		if (assignmentStatement.dst() != null)
			assignmentStatement.dst().accept(this, arg);
		if (assignmentStatement.src() != null)
			assignmentStatement.src().accept(this, arg);
		
		// When assigning data, we must check whether the assignment destination
		// is an address. If not, we should throw an error.
		Boolean isLValue = SemAn.isAddr.get(assignmentStatement.dst());
		if (isLValue == null || !isLValue.booleanValue())
			throw new Report.Error(assignmentStatement, "Cannot assign to non lvalue.");
		
		return null;
	}

}
