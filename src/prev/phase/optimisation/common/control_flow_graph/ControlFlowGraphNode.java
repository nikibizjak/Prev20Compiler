package prev.phase.optimisation.common.control_flow_graph;

import prev.data.imc.code.stmt.*;
import prev.data.imc.code.expr.*;
import prev.common.report.*;
import java.util.*;

public class ControlFlowGraphNode extends Node {

    public ImcStmt statement;
    public Set<ControlFlowGraphNode> predecessors = new HashSet<ControlFlowGraphNode>();
    public Set<ControlFlowGraphNode> successors = new HashSet<ControlFlowGraphNode>();

    /** Sets for liveness analysis */
    private HashSet<ImcTEMP> liveIn = new HashSet<ImcTEMP>();
    private HashSet<ImcTEMP> liveOut = new HashSet<ImcTEMP>();

    /** Sets for usages and definitions */
    private HashSet<ImcTEMP> uses = new HashSet<ImcTEMP>();
    private HashSet<ImcTEMP> defines = new HashSet<ImcTEMP>();

    /** Dominators */
    private HashSet<ControlFlowGraphNode> dominators = new HashSet<ControlFlowGraphNode>();
    ControlFlowGraphNode immediateDominator = null;
    
    public ControlFlowGraphNode(ImcStmt statement) {
        this.statement = statement;
    }
    
    public void addSuccessor(Node node) {
        if (!(node instanceof ControlFlowGraphNode))
            throw new Report.Error("Node is not instance of ControlFlowGraphNode");
        this.successors.add((ControlFlowGraphNode) node);
    }

    public void addPredecessor(Node node) {
        if (!(node instanceof ControlFlowGraphNode))
            throw new Report.Error("Node is not instance of ControlFlowGraphNode");
        this.predecessors.add((ControlFlowGraphNode) node);
    }

    public Set getPredecessors() {
        return new HashSet<ControlFlowGraphNode>(this.predecessors);
    }

    public Set getSuccessors() {
        return new HashSet<ControlFlowGraphNode>(this.successors);
    }

    /** Getters and setters for dominators */
    public HashSet<ControlFlowGraphNode> getDominators() {
        return new HashSet<ControlFlowGraphNode>(this.dominators);
    }

    public void setDominators(HashSet<ControlFlowGraphNode> dominators) {
        this.dominators = dominators;
    }

    public ControlFlowGraphNode getImmediateDominator() {
        return this.immediateDominator;
    }

    public void setImmediateDominator(ControlFlowGraphNode dominator) {
        this.immediateDominator = dominator;
    }

    /** Getters and setters for statement defines and uses */
    public HashSet<ImcTEMP> getUses() {
        return new HashSet<ImcTEMP>(this.uses);
    }

    public void setUses(HashSet<ImcTEMP> uses) {
        this.uses = uses;
    }

    public HashSet<ImcTEMP> getDefines() {
        return new HashSet<ImcTEMP>(this.defines);
    }

    public void setDefines(HashSet<ImcTEMP> defines) {
        this.defines = defines;
    }

    /** Getters and setters for liveness analysis */
    public HashSet<ImcTEMP> getLiveIn() {
        return new HashSet<ImcTEMP>(this.liveIn);
    }

    public void setLiveIn(HashSet<ImcTEMP> liveIn) {
        this.liveIn = liveIn;
    }

    public HashSet<ImcTEMP> getLiveOut() {
        return new HashSet<ImcTEMP>(this.liveOut);
    }

    public void setLiveOut(HashSet<ImcTEMP> liveOut) {
        this.liveOut = liveOut;
    }

    public void clearLiveIn() {
        this.liveIn.clear();
    }

    public void clearLiveOut() {
        this.liveOut.clear();
    }

    @Override
    public String toString() {
        return String.format("Node(%s)", this.statement.toString());
    }
}