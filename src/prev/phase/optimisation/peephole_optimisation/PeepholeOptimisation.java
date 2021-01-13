package prev.phase.optimisation.peephole_optimisation;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.Vector;

public class PeepholeOptimisation {

    public static boolean run(ControlFlowGraph graph) {
        boolean hasGraphChanged = false;

        boolean hasChanged = removeUselessExpressionStatements(graph);
        hasGraphChanged = hasGraphChanged || hasChanged;

        return hasGraphChanged;
    }

    private static boolean removeUselessExpressionStatements(ControlFlowGraph graph) {
        // If any constant propagation has been performed, hasGraphChanged
        // should change to true.
        boolean hasGraphChanged = false;

        // Remove expression statements that don't contain function call as they
        // are useless
        Vector<ControlFlowGraphNode> uselessExpressionStatements = new Vector<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : graph.nodes) {
            if (node.statement instanceof ImcESTMT) {
                ImcESTMT expressionStatement = (ImcESTMT) node.statement;
                if (!(expressionStatement.expr instanceof ImcCALL)) {
                    uselessExpressionStatements.add(node);
                }
            }
        }

        for (ControlFlowGraphNode node : uselessExpressionStatements) {
            graph.removeNode(node);
        }
        
        hasGraphChanged = uselessExpressionStatements.size() > 0;
        return hasGraphChanged;
    }

}