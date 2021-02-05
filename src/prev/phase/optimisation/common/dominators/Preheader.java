package prev.phase.optimisation.common.dominators;

import prev.phase.optimisation.common.control_flow_graph.*;

public class Preheader {

    /** Loop preheader information (first and last control-flow graph nodes) */
    public ControlFlowGraphNode preheaderStart;
    public ControlFlowGraphNode preheaderEnd;

    /** To append items to preheader, a reference to the graph must be held
     * inside the preheader class. */
    public ControlFlowGraph graph;

    public Preheader(ControlFlowGraph graph, ControlFlowGraphNode start, ControlFlowGraphNode end) {
        this.graph = graph;
        this.preheaderStart = start;
        this.preheaderEnd = end;
    }

    public void append(ControlFlowGraphNode node) {
        this.graph.insertAfter(this.preheaderEnd, node);
        this.preheaderEnd = node;
    }

}