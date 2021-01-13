package prev.phase.optimisation.common.control_flow_graph;

import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import prev.data.lin.*;

public class ControlFlowGraph {

    public final LinCodeChunk codeChunk;
    public LinkedHashSet<ControlFlowGraphNode> nodes;

    public ControlFlowGraph(LinCodeChunk codeChunk) {
        this.codeChunk = codeChunk;
        this.nodes = new LinkedHashSet<ControlFlowGraphNode>();
    }

    public void addNode(ControlFlowGraphNode node) {
        this.nodes.add(node);
    }

    public boolean containsNode(ControlFlowGraphNode node) {
        return this.nodes.contains(node);
    }

    public void addEdge(ControlFlowGraphNode first, ControlFlowGraphNode second) {
        if (!this.containsNode(first))
            this.addNode(first);
        
        if (!this.containsNode(second))
            this.addNode(second);
        
        first.addSuccessor(second);
        second.addPredecessor(first);
    }
    
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
        this.nodes.remove(node);
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

    public void print() {
        if (this.nodes.size() <= 0)
            return;
        
        ControlFlowGraphNode firstNode = this.nodes.iterator().next();
        ControlFlowGraph.print(firstNode, new HashSet<ControlFlowGraphNode>());
    }

    private static void print(ControlFlowGraphNode node, HashSet<ControlFlowGraphNode> alreadyPrinted) {
        System.out.println(node);
        alreadyPrinted.add(node);
        for (ControlFlowGraphNode successor : ((Set<ControlFlowGraphNode>) node.getSuccessors())) {
            if (!alreadyPrinted.contains(successor))
                print(successor, alreadyPrinted);
        }
    }

}