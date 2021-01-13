package prev.phase.optimisation.common.tree_replacement;

import java.util.*;

import prev.common.report.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;

public class StatementReplacer implements ImcVisitor<ImcStmt, Replacement> {

    public ImcStmt visit(ImcCJUMP imcCJump, Replacement replacement) {
        ImcExpr newCondition = imcCJump.cond.accept(new ExpressionReplacer(), replacement);
        return new ImcCJUMP(newCondition, imcCJump.posLabel, imcCJump.negLabel);
    }

    public ImcStmt visit(ImcESTMT imcEStmt, Replacement replacement) {
        ImcExpr newExpression = imcEStmt.expr.accept(new ExpressionReplacer(), replacement);
        return new ImcESTMT(newExpression);
    }

    public ImcStmt visit(ImcJUMP imcJump, Replacement replacement) {
        return imcJump;
    }

    public ImcStmt visit(ImcLABEL imcLabel, Replacement replacement) {
        return imcLabel;
    }

    public ImcStmt visit(ImcMOVE imcMove, Replacement replacement) {
        // ImcExpr newDestination = imcMove.dst.accept(new ExpressionReplacer(), replacement);
        ImcExpr newSource = imcMove.src.accept(new ExpressionReplacer(), replacement);
        return new ImcMOVE(imcMove.dst, newSource);
    }

    public ImcStmt visit(ImcSTMTS imcStmts, Replacement replacement) {
        Vector<ImcStmt> newStatements = new Vector<ImcStmt>();
        for (ImcStmt statement : imcStmts.stmts()) {
            ImcStmt newStatement = statement.accept(this, replacement);
            newStatements.add(newStatement);
        }
        return new ImcSTMTS(newStatements);
    }

}
