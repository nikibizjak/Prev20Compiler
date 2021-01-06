package prev.phase.optimisation.common.control_flow_graph;

import java.util.Set;

public abstract class Node {

    public abstract void addSuccessor(Node node);
    public abstract void addPredecessor(Node node);

    public abstract Set getPredecessors();
    public abstract Set getSuccessors();

}