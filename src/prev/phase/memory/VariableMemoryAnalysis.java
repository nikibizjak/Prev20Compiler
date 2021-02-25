package prev.phase.memory;

import prev.data.ast.visitor.AstFullVisitor;
import prev.data.mem.MemAbsAccess;
import prev.data.mem.MemFrame;
import prev.data.mem.MemLabel;
import prev.data.mem.MemRelAccess;
import prev.data.semtype.SemChar;
import prev.data.semtype.SemPointer;
import prev.data.semtype.SemType;
import prev.data.semtype.SemVoid;
import prev.phase.seman.SemAn;
import prev.phase.memory.Memory;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.common.report.*;
import prev.data.semtype.*;

/**
 * Compute which variables can be represented in registers and which must be put
 * in memory.
 */
public class VariableMemoryAnalysis extends AstFullVisitor<Object, VariableMemoryAnalysis.Phase> {

    public enum Phase { DECLARATIONS, EXPRESSIONS };

    @Override
	public Object visit(AstTrees<? extends AstTree> trees, Phase phase) {
		for (AstTree t : trees)
			if (t != null)
				t.accept(this, Phase.DECLARATIONS);
        
        for (AstTree t : trees)
            if (t != null)
                t.accept(this, Phase.EXPRESSIONS);
        
		return null;
	}

    @Override
	public Object visit(AstVarDecl variableDeclaration, Phase phase) {
        if (variableDeclaration.type() != null)
            variableDeclaration.type().accept(this, phase);
        
        // When we visit variable declaration, we should assume that this
        // variable can be represented in register. If it is not possible to do
        // so, the result will be updated after.
        // This just ensures that there is a mapping Ast
        if (phase != Phase.DECLARATIONS)
            return null;
        
        SemType variableType = SemAn.isType.get(variableDeclaration.type());
        if (variableType.actualType() instanceof SemArray) {
            Memory.isRegisterRepresentable.put(variableDeclaration, false);
        } else if (variableType.actualType() instanceof SemRecord) {
            Memory.isRegisterRepresentable.put(variableDeclaration, false);
        } else {
            Memory.isRegisterRepresentable.put(variableDeclaration, true);
        }
        
		return null;
	}

    @Override
	public Object visit(AstWhereExpr whereExpr, Phase phase) {
		if (whereExpr.decls() != null)
			whereExpr.decls().accept(this, phase);
		if (whereExpr.expr() != null)
			whereExpr.expr().accept(this, phase);
		return null;
	}

    @Override
	public Object visit(AstPfxExpr prefixExpression, Phase phase) {
		if (prefixExpression.expr() != null)
            prefixExpression.expr().accept(this, phase);
        
        if (phase != Phase.EXPRESSIONS)
            return null;

        if (prefixExpression.oper() != AstPfxExpr.Oper.PTR)
            return null;
        
        AstExpr subexpression = prefixExpression.expr();
        if (!(subexpression instanceof AstNameExpr))
            return null;
        
        // This is a pointer to a variable expression. This means that the
        // variable in subexpression MUST be represented in memory.
        AstNameExpr nameExpression = (AstNameExpr) subexpression;
        AstDecl declaration = SemAn.declaredAt.get(nameExpression);

        if (declaration instanceof AstVarDecl) {
            // This is a pointer to variable
            Memory.isRegisterRepresentable.put(declaration, false);
        }
        
		return null;
	}

}
