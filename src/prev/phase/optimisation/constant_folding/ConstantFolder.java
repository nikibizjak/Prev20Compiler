package prev.phase.optimisation.constant_folding;

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

public class ConstantFolder extends AstFullVisitor<Object, Object> {
    
    @Override
	public Object visit(AstFunDecl funDecl, Object arg) {
        funDecl.expr().accept(this, arg);
        ImcExpr bodyExpr = ImcGen.exprImc.get(funDecl.expr());
        ImcExpr newBodyExpression = bodyExpr.accept(new ExpressionConstantFolder(), null);
        ImcGen.exprImc.put(funDecl.expr(), newBodyExpression);
        return null;
	}

}
