package prev.phase.optimisation.constant_propagation;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.phase.optimisation.dead_code_elimination.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.constant_folding.*;
import prev.phase.optimisation.common.tree_replacement.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

public class ConstantPropagation {

    private static ImcCONST getConstantValue(ImcStmt statement) {
        if (statement instanceof ImcMOVE) {
            ImcMOVE move = (ImcMOVE) statement;
            if (move.dst instanceof ImcTEMP && move.src instanceof ImcCONST) {
                return (ImcCONST) move.src;
            }
        }
        return null;
    }

    public static boolean run(ControlFlowGraph graph) {

        // If any constant propagation has been performed, hasGraphChanged
        // should change to true.
        boolean hasGraphChanged = false;

        ReachingDefinitionsAnalysis.run(graph);
        LivenessAnalysis.run(graph);

        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);

        for (ControlFlowGraphNode node : graph.nodes) {
            HashSet<ImcTEMP> uses = node.getUses();

            for (ImcTEMP usedTemporary : uses) {
                HashSet<ControlFlowGraphNode> temporaryDefinitions = definitions.get(usedTemporary);
                if (temporaryDefinitions == null)
                    continue;

                // We know that t is constant in n if:
                //   * d reaches n
                //   * no other definitions of t reach n

                HashSet<ControlFlowGraphNode> reachingDefinitions = node.getReachingDefinitionsIn();
                reachingDefinitions.retainAll(temporaryDefinitions);
                boolean canPerformConstantPropagation = reachingDefinitions.size() == 1;

                if (canPerformConstantPropagation) {
                    ImcCONST constantValue = getConstantValue(reachingDefinitions.iterator().next().statement);
                    if (constantValue != null) {
                        node.statement = node.statement.accept(new StatementReplacer(), new Replacement(usedTemporary, constantValue));
                        hasGraphChanged = true;
                    }
                }
            }
        }

        return hasGraphChanged;

    }

}