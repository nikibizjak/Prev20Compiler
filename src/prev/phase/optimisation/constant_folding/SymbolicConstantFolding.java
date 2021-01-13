package prev.phase.optimisation.constant_folding;

import java.util.*;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.visitor.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.lin.*;
import prev.phase.imcgen.ImcGen;
import prev.phase.memory.*;

public class SymbolicConstantFolding extends AstFullVisitor<Object, Object> {
    
    @Override
	public Object visit(AstFunDecl funDecl, Object arg) {
        funDecl.expr().accept(this, arg);
        ImcExpr bodyExpr = ImcGen.exprImc.get(funDecl.expr());

        ImcExpr foldedExpression = bodyExpr.accept(new ExpressionConstantFolder(), null);
        ImcExpr symbolicFoldedExpression = foldedExpression.accept(new SymbolicExpressionConstantFolder(), null);
        
        ImcGen.exprImc.put(funDecl.expr(), symbolicFoldedExpression);
        return null;
    }
    
    public static boolean run(ControlFlowGraph graph) {
        boolean hasChanged = false;
        for (ControlFlowGraphNode node : graph.nodes) {
            ImcStmt newStatement = node.statement.accept(new StatementConstantFolder(), new SymbolicExpressionConstantFolder());
            
            // Check if statement has changed
            hasChanged = hasChanged || !node.statement.toString().equals(newStatement.toString());

            node.statement = newStatement;
        }
        return hasChanged;
    }

}
