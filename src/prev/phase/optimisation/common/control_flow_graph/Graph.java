package prev.phase.optimisation.common.control_flow_graph;

import java.util.LinkedHashSet;

public abstract class Graph<T extends Node> {

    public LinkedHashSet<T> nodes;

    public Graph() {
        this.nodes = new LinkedHashSet<T>();
    }

    public void addNode(T node) {
        this.nodes.add(node);
    }

    public boolean containsNode(T node) {
        return this.nodes.contains(node);
    }

    public abstract void addEdge(T first, T second);

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        for (T node : this.nodes) {
            buffer.append(node);
            buffer.append(": ");
            buffer.append(node.getSuccessors().toString());
            buffer.append('\n');
        }
        return buffer.toString();
    }

}