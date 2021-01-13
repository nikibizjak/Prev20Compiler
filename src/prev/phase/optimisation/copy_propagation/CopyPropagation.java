package prev.phase.optimisation.copy_propagation;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.phase.optimisation.dead_code_elimination.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.common.tree_replacement.*;
import prev.phase.optimisation.constant_folding.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

public class CopyPropagation {

    private static ImcTEMP getCopyTemporary(ImcStmt statement) {
        if (statement instanceof ImcMOVE) {
            ImcMOVE move = (ImcMOVE) statement;
            if (move.dst instanceof ImcTEMP && move.src instanceof ImcTEMP) {
                return (ImcTEMP) move.src;
            }
        }
        return null;
    }

    public static boolean isInvalid(ControlFlowGraphNode currentNode, ControlFlowGraphNode finishNode, ImcTEMP temp, boolean isInvalidPath, HashSet<ControlFlowGraphNode> alreadyVisited) {
        alreadyVisited.add(currentNode);

        // If we have reached finishNode, stop
        if (currentNode == finishNode)
            return isInvalidPath;

        isInvalidPath = isInvalidPath || currentNode.getDefines().contains(temp);
        
        for (ControlFlowGraphNode predecessor : currentNode.getPredecessors()) {
            if (!alreadyVisited.contains(predecessor)) {
                boolean found = isInvalid(predecessor, finishNode, temp, isInvalidPath, alreadyVisited);
                if (found)
                    return true;
            }
        }
        return false;
    }

    public static boolean run(ControlFlowGraph graph) {

        // If any copy propagation has been performed, hasGraphChanged should
        // change to true.
        boolean hasGraphChanged = false;

        ReachingDefinitionsAnalysis.run(graph);
        LivenessAnalysis.run(graph);

        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);

        // Suppose we have a statement d: t <- z, and another statement n that
        // uses t, such as  n: y <- t + x. We can rewrite n as n: y <- z + x if:
        //   1. d reaches n and
        //   2. no other definition of t reaches n and
        //   3. there is no definition of z on any path from d to n

        for (ControlFlowGraphNode node : graph.nodes) {
            HashSet<ImcTEMP> uses = node.getUses();

            for (ImcTEMP usedTemporary : uses) {
                HashSet<ControlFlowGraphNode> temporaryDefinitions = definitions.get(usedTemporary);
                if (temporaryDefinitions == null)
                    continue;

                // Check rules 1 and 2
                HashSet<ControlFlowGraphNode> reachingDefinitions = node.getReachingDefinitionsIn();
                reachingDefinitions.retainAll(temporaryDefinitions);

                boolean canPerformCopyPropagation = reachingDefinitions.size() == 1;
                if (!canPerformCopyPropagation)
                    continue;

                ControlFlowGraphNode definitionNode = reachingDefinitions.iterator().next();

                ImcTEMP replacementTemporary = getCopyTemporary(definitionNode.statement);
                if (replacementTemporary == null)
                    continue;

                // Check rule 3
                HashSet<ControlFlowGraphNode> set = new HashSet<ControlFlowGraphNode>();
                boolean isInvalidPath = isInvalid(node, definitionNode, replacementTemporary, false, set);
                if (isInvalidPath)
                    continue;

                node.statement = node.statement.accept(new StatementReplacer(), new Replacement(usedTemporary, replacementTemporary));
            }
        }

        return hasGraphChanged;
    }

}