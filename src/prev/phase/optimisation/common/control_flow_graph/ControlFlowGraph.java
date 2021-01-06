package prev.phase.optimisation.common.control_flow_graph;

import java.util.Set;

public class ControlFlowGraph extends DirectedGraph<ControlFlowGraphNode> {
    
    /** Control-flow graph modification operations */
    public void insertBefore(ControlFlowGraphNode node, ControlFlowGraphNode prepend) {
        // Insert `prepend` node before `node` node
        
        // Add edges from all predecessors of `node` to `prepend`
        for (ControlFlowGraphNode predecessor : ((Set<ControlFlowGraphNode>) node.getPredecessors())) {
            predecessor.successors.remove(node);
            node.predecessors.remove(predecessor);
            this.addEdge(predecessor, prepend);
        }

        // Connect prepend -> node
        this.addEdge(prepend, node); 
    }

    public void insertAfter(ControlFlowGraphNode node, ControlFlowGraphNode append) {
        // Insert `append` node after `node` node
        for (ControlFlowGraphNode successor : ((Set<ControlFlowGraphNode>) node.getSuccessors())) {
            node.successors.remove(successor);
            successor.predecessors.remove(node);
            this.addEdge(append, successor);
        }

        // Connect node -> append
        this.addEdge(node, append); 
    }

    public void removeNode(ControlFlowGraphNode node) {
        Set<ControlFlowGraphNode> predecessors = (Set<ControlFlowGraphNode>) node.getPredecessors();
        Set<ControlFlowGraphNode> successors = (Set<ControlFlowGraphNode>) node.getSuccessors();

        for (ControlFlowGraphNode successor : successors) {
            successor.predecessors.remove(node);
        }

        for (ControlFlowGraphNode predecessor : predecessors) {
            predecessor.successors.remove(node);
            for (ControlFlowGraphNode successor : successors) {
                this.addEdge(predecessor, successor);
            }
        }

        node.successors.clear();
        node.predecessors.clear();
    }

}