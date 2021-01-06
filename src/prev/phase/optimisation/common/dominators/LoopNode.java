package prev.phase.optimisation.common.dominators;

import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraphNode;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;

public class LoopNode {

    public LoopNode parent;
    
    public ControlFlowGraphNode header;
    public Set<ControlFlowGraphNode> loopItems = new LinkedHashSet<ControlFlowGraphNode>();
    public List<LoopNode> subLoops = new ArrayList<LoopNode>();

    /** Loop preheader information (first and last control-flow graph nodes) */
    public ControlFlowGraphNode preheaderStart;
    public ControlFlowGraphNode preheaderEnd;

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

    @Override
    public String toString() {
        return String.format("LoopNode(header: %s, items: %s)", this.header, this.loopItems);
    }
}