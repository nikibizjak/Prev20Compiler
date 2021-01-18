package prev.phase.optimisation.common_subexpression_elimination;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.available_expressions.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.common.dominators.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.common.report.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

public class CommonSubexpressionElimination {

    public static boolean run(ControlFlowGraph graph) {
        return runAnalysis(graph);
    }

    private static boolean runAnalysis(ControlFlowGraph graph) {
        
        boolean hasGraphChanged = false;
        
        do {

            AvailableExpressionsAnalysis.run(graph);
            LoopFinder.computeDominators(graph);

            hasGraphChanged = false;

            Vector<ControlFlowGraphNode> nodes = new Vector<ControlFlowGraphNode>(graph.nodes);
            // for (ControlFlowGraphNode node : graph.nodes) {
            for (int h = nodes.size() - 1; h >= 0; h--) {
                ControlFlowGraphNode node = nodes.get(h);
                if (!(node.statement instanceof ImcMOVE))
                    continue;
                
                ImcMOVE moveStatement = (ImcMOVE) node.statement;
                if (!(moveStatement.dst instanceof ImcTEMP))
                    continue;
                
                // Statement is of form t <- ... (or MOVE(TEMP(...), ...)). This is
                // a candidate for common subexpression elimination. If MOVE source
                // expression is already in this node available expression, it might
                // be possible to rewrite this statement.
                HashSet<ImcExpr> availableExpressionsIn = node.getAvailableExpressionsIn();
                // System.out.println(node + ": " + availableExpressionsIn);
                ImcExpr sourceExpression = moveStatement.src;
                if (!availableExpressionsIn.contains(sourceExpression))
                    continue;
                
                HashSet<ImcTEMP> sourceTemporaries = TemporaryFinder.getTemporaries(sourceExpression);
                
                Vector<ControlFlowGraphNode> possibleReplacements = new Vector<ControlFlowGraphNode>();
                for (ControlFlowGraphNode dominator : node.getDominators()) {
                    if (!(dominator.statement instanceof ImcMOVE))
                        continue;
                    ImcMOVE dominatorStatement = (ImcMOVE) dominator.statement;
                    // If dominator redefines any of the temporaries that appear in
                    // this node's expression, then we should stop searching for
                    // possible duplicate expressions.
                    HashSet<ImcTEMP> dominatorDefinitions = TemporaryFinder.getTemporaries(dominatorStatement.dst);
                    dominatorDefinitions.retainAll(sourceTemporaries);
                    if (dominatorDefinitions.size() > 0)
                        break;
                    
                    if (dominatorStatement.src.equals(sourceExpression))
                        possibleReplacements.add(dominator);
                }
    
                if (possibleReplacements.size() <= 0)
                    continue;
                
                ControlFlowGraphNode first = possibleReplacements.get(possibleReplacements.size() - 1);
                ImcTEMP temporary = new ImcTEMP(new MemTemp());
                ImcStmt initializeStatement = new ImcMOVE( temporary, sourceExpression );
                ControlFlowGraphNode newNode = new ControlFlowGraphNode(initializeStatement);
                Report.debug("Eliminating subexpression: " + sourceExpression);
                graph.insertBefore(first, newNode);
    
                for (int i = possibleReplacements.size() - 1; i >= 0; i--) {
                    ControlFlowGraphNode current = possibleReplacements.get(i);
                    current.statement = new ImcMOVE( ((ImcMOVE) current.statement).dst, temporary );
                    hasGraphChanged = true;                    
                }
                break;
                
            }

        } while (hasGraphChanged);
        return hasGraphChanged;
    }

}