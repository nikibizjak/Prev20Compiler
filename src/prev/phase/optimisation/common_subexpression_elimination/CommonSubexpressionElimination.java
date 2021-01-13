package prev.phase.optimisation.common_subexpression_elimination;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.available_expressions.*;

public class CommonSubexpressionElimination {

    public static boolean run(ControlFlowGraph graph) {
        return runAnalysis(graph);
    }

    private static boolean runAnalysis(ControlFlowGraph graph) {
        AvailableExpressionsAnalysis.run(graph);
        return false;
    }

}