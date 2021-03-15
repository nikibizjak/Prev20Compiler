package prev.phase.optimisation.common.control_flow_graph;

import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Vector;
import prev.data.lin.*;

public class ControlFlowGraph {

    public final LinCodeChunk codeChunk;
    public Vector<ControlFlowGraphNode> nodes;
    public HashSet<ControlFlowGraphNode> nodeSet;

    public ControlFlowGraph(LinCodeChunk codeChunk) {
        this.codeChunk = codeChunk;
        this.nodes = new Vector<ControlFlowGraphNode>();
        this.nodeSet = new HashSet<ControlFlowGraphNode>();
    }

    public void addNode(ControlFlowGraphNode node) {
        this.nodes.add(node);
        this.nodeSet.add(node);
    }

    public boolean containsNode(ControlFlowGraphNode node) {
        return this.nodeSet.contains(node);
    }

    public void addEdge(ControlFlowGraphNode first, ControlFlowGraphNode second) {
        if (!this.containsNode(first))
            this.addNode(first);
        
        if (!this.containsNode(second))
            this.addNode(second);
        
        first.addSuccessor(second);
        second.addPredecessor(first);
    }

    public ControlFlowGraphNode initialNode() {
        if (this.nodes.size() <= 0)
            return null;
        return this.nodes.get(0);
    }

    /** Insert `prepend` node before `node` node */
    public void insertBefore(ControlFlowGraphNode node, ControlFlowGraphNode prepend) {
        int nodeIndex = this.nodes.indexOf(node);
        this.nodes.add(nodeIndex, prepend);
        this.nodeSet.add(prepend);
    
        // Add edges from all predecessors of `node` to `prepend`
        for (ControlFlowGraphNode predecessor : node.getPredecessors()) {
            predecessor.successors.remove(node);
            node.predecessors.remove(predecessor);
            this.addEdge(predecessor, prepend);
        }

        // Connect prepend -> node
        this.addEdge(prepend, node);
    }

    /** Insert `append` node after `node` node */
    public void insertAfter(ControlFlowGraphNode node, ControlFlowGraphNode append) {
        int nodeIndex = this.nodes.indexOf(node);
        this.nodes.add(nodeIndex + 1, append);
        this.nodeSet.add(append);
        
        for (ControlFlowGraphNode successor : node.getSuccessors()) {
            node.successors.remove(successor);
            successor.predecessors.remove(node);
            this.addEdge(append, successor);
        }

        // Connect node -> append
        this.addEdge(node, append);
    }
    
    public void removeNode(ControlFlowGraphNode node) {
        this.nodes.remove(node);
        this.nodeSet.remove(node);

        LinkedHashSet<ControlFlowGraphNode> predecessors = node.getPredecessors();
        LinkedHashSet<ControlFlowGraphNode> successors = node.getSuccessors();

        for (ControlFlowGraphNode successor : node.getSuccessors()) {
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
        
        ControlFlowGraphNode firstNode = this.initialNode();
        ControlFlowGraph.print(firstNode, new HashSet<ControlFlowGraphNode>());
    }

    private static void print(ControlFlowGraphNode node, HashSet<ControlFlowGraphNode> alreadyPrinted) {
        System.out.println(node.statement);
        alreadyPrinted.add(node);
        for (ControlFlowGraphNode successor : node.getSuccessors()) {
            if (!alreadyPrinted.contains(successor))
                print(successor, alreadyPrinted);
        }
    }

}