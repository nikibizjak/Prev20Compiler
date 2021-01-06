package prev.phase.optimisation.common.control_flow_graph;

public class DirectedGraph<T extends Node> extends Graph<T> {

    public void addEdge(T first, T second) {
        if (!this.containsNode(first))
            this.addNode(first);
        
        if (!this.containsNode(second))
            this.addNode(second);
        
        first.addSuccessor(second);
        second.addPredecessor(first);
    }

}