package prev.phase.optimisation.dead_code_elimination;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;
import prev.common.report.*;

public class DeadCodeElimination { 

    public static boolean run(ControlFlowGraph graph) {
        LivenessAnalysis.run(graph);
        
        // If any copy propagation has been performed, hasGraphChanged should
        // change to true.
        boolean hasGraphChanged = false;

        Vector<ControlFlowGraphNode> deadCode = new Vector<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : graph.nodes) {
            if (!statementValidForDeadCodeElimination(node.statement))
                continue;
            
            HashSet<ImcTEMP> statementDefines = node.getDefines();
            if (statementDefines.size() != 1)
                continue;
            
            ImcTEMP definedTemporary = statementDefines.iterator().next();
            if (!node.getLiveOut().contains(definedTemporary)) {
                // This statement is dead-code
                deadCode.add(node);
            }
        }

        for (ControlFlowGraphNode node : deadCode) {
            Report.debug("Removing statement " + node.statement);
            graph.removeNode(node);
            hasGraphChanged = true;
        }

        return hasGraphChanged;
    }

    private static boolean statementValidForDeadCodeElimination(ImcStmt statement) {
        return statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP && (((ImcMOVE) statement).src instanceof ImcTEMP || ((ImcMOVE) statement).src instanceof ImcCONST || ((ImcMOVE) statement).src instanceof ImcMEM || ((ImcMOVE) statement).src instanceof ImcBINOP || ((ImcMOVE) statement).src instanceof ImcUNOP);
    }

}