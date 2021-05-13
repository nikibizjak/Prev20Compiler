package prev.phase.optimisation.peephole_optimisation;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.*;
import prev.common.report.*;

public class PeepholeOptimisation {

    public static boolean run(ControlFlowGraph graph) {
        boolean hasGraphChanged = false;

        {
            boolean hasChanged = removeUselessExpressionStatements(graph);
            hasGraphChanged = hasGraphChanged || hasChanged;
        }

        {
            boolean hasChanged = removeUselessCopies(graph);
            hasGraphChanged = hasGraphChanged || hasChanged;
        }

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

    public static HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> uses(ControlFlowGraph graph) {
        LivenessAnalysis.run(graph);
		HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> uses = new HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>>();
		for (ControlFlowGraphNode node : graph.nodes) {
            HashSet<ImcTEMP> nodeUses = node.getUses();
            for (ImcTEMP temporary : nodeUses) {
                if (uses.containsKey(temporary)) {
                    uses.get(temporary).add(node);
                } else {
                    HashSet<ControlFlowGraphNode> newUses = new HashSet<ControlFlowGraphNode>();
                    newUses.add(node);
                    uses.put(temporary, newUses);
                }
            }
		}
		return uses;
	}

    private static boolean removeUselessCopies(ControlFlowGraph graph) {
        // If any constant propagation has been performed, hasGraphChanged
        // should change to true.
        boolean hasGraphChanged = false;

        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);
        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> uses = uses(graph);

        Vector<ControlFlowGraphNode> nodesToRemove = new Vector<ControlFlowGraphNode>();

        for (ControlFlowGraphNode node : graph.nodes) {
            if (!(node.statement instanceof ImcMOVE)) continue;

            ImcMOVE moveStatement = (ImcMOVE) node.statement;
            if (!(moveStatement.dst instanceof ImcTEMP)) continue;
            if (!(moveStatement.src instanceof ImcTEMP)) continue;

            // Do not remove statement RV <- ...
            if (((ImcTEMP) moveStatement.dst).temp.equals(graph.codeChunk.frame.RV)) continue;

            // Check if there is only one use of moveStatement.src temporary
            HashSet<ControlFlowGraphNode> sourceUses = uses.get(moveStatement.src);
            if (sourceUses == null) continue;
            if (sourceUses.size() != 1) continue;

            // The current [node] is a copy statement T1 <- T2. If there is only
            // one definition of T2 <- a + b, we can replace current
            // statement with T1 <- a + b.
            HashSet<ControlFlowGraphNode> sourceDefinitions = definitions.get(moveStatement.src);
            if (sourceDefinitions == null) continue;
            if (sourceDefinitions.size() != 1) continue;

            ControlFlowGraphNode sourceDefinitionNode = sourceDefinitions.iterator().next();
            if (!(sourceDefinitionNode.statement instanceof ImcMOVE)) continue;
            ImcExpr sourceExpression = ((ImcMOVE) sourceDefinitionNode.statement).src;
            
            ImcStmt modifiedStatement = new ImcMOVE(moveStatement.dst, sourceExpression);
            Report.debug("  * Replacing statement " + sourceDefinitionNode.statement + " with " + modifiedStatement);
            sourceDefinitionNode.statement = modifiedStatement;
            nodesToRemove.add(node);
            hasGraphChanged = true;

        }

        for (ControlFlowGraphNode node : nodesToRemove) {
            Report.debug("  * Removing statement " + node.statement);
            graph.removeNode(node);
            hasGraphChanged = true;
        }
        
        return hasGraphChanged;
    }

}