package prev.phase.optimisation.common.dominators;

import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraphNode;
import prev.data.imc.code.stmt.*;
import prev.data.imc.code.expr.*;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

public class LoopNode {

    public LoopNode parent;
    
    public ControlFlowGraphNode header;
    public LinkedHashSet<ControlFlowGraphNode> loopItems = new LinkedHashSet<ControlFlowGraphNode>();
    public List<LoopNode> subLoops = new ArrayList<LoopNode>();

    public Preheader preheader;

    public LoopNode(ControlFlowGraphNode header) {
        this.header = header;
    }

    public void addSubLoop(LoopNode loopNode) {
        loopNode.parent = this;
        this.subLoops.add(loopNode);
        this.loopItems.remove(loopNode.header);
        this.loopItems.removeAll(loopNode.loopItems);
        
        this.removeFromParent(loopNode);
    }

    public void removeFromParent(LoopNode loopNode) {        
        LoopNode currentParentNode = this.parent;
        while (currentParentNode != null) {
            currentParentNode.loopItems.remove(loopNode.header);
            currentParentNode.loopItems.removeAll(loopNode.loopItems);
            currentParentNode = currentParentNode.parent;
        }
    }

    public LoopNode nodeContainingHeader(ControlFlowGraphNode header) {
        if (this.loopItems.contains(header))
            return this;
        for (LoopNode subLoop : this.subLoops) {
            LoopNode containingHeader = subLoop.nodeContainingHeader(header);
            if (containingHeader != null)
                return containingHeader;
        }
        return null;
    }

    public boolean containsNode(ControlFlowGraphNode node) {
        if (this.loopItems.contains(node) || this.header.equals(node))
            return true;
        for (LoopNode subLoop : this.subLoops) {
            boolean containsNode = subLoop.containsNode(node);
            if (containsNode)
                return true;
        }
        return false;
    }

    public HashSet<ImcStmt> getLoopStatements() {
        HashSet<ImcStmt> statements = new HashSet<ImcStmt>();
        statements.add(this.header.statement);
        for (ControlFlowGraphNode node : this.loopItems)
            statements.add(node.statement);
        return statements;
    }

    public HashSet<ControlFlowGraphNode> getLoopNodes() {
        HashSet<ControlFlowGraphNode> nodes = new HashSet<ControlFlowGraphNode>();
        nodes.add(this.header);
        nodes.addAll(this.loopItems);
        return nodes;
    }

    public HashSet<ControlFlowGraphNode> getAllNodes() {
        HashSet<ControlFlowGraphNode> allNodes = new HashSet<ControlFlowGraphNode>();
        this.addAllNodes(allNodes);
        return allNodes;
    }

    private void addAllNodes(HashSet<ControlFlowGraphNode> allNodes) {
        allNodes.add(this.header);
        allNodes.addAll(this.loopItems);
        for (LoopNode subLoop : this.subLoops) {
            subLoop.addAllNodes(allNodes);
        }
    }

    public LinkedList<ControlFlowGraphNode> getLoopItems() {
        LinkedList<ControlFlowGraphNode> loopItems = new LinkedList<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : this.loopItems) {
            loopItems.addFirst(node);
        }
        return loopItems;
    }

    public void setPreheader(Preheader preheader) {
        this.preheader = preheader;
    }

    @Override
    public String toString() {
        return String.format("LoopNode(header: %s, items: %s)", this.header, this.loopItems);
    }
}