package prev.phase.optimisation.common.control_flow_graph;

import prev.data.imc.code.stmt.*;
import prev.data.imc.code.expr.*;
import prev.common.report.*;
import java.util.*;

public class ControlFlowGraphNode {

    public ImcStmt statement;
    public LinkedHashSet<ControlFlowGraphNode> predecessors = new LinkedHashSet<ControlFlowGraphNode>();
    public LinkedHashSet<ControlFlowGraphNode> successors = new LinkedHashSet<ControlFlowGraphNode>();

    /** Sets for liveness analysis */
    private HashSet<ImcTEMP> liveIn = new HashSet<ImcTEMP>();
    private HashSet<ImcTEMP> liveOut = new HashSet<ImcTEMP>();

    /** Sets for usages and definitions */
    private HashSet<ImcTEMP> uses = new HashSet<ImcTEMP>();
    private HashSet<ImcTEMP> defines = new HashSet<ImcTEMP>();

    /** Sets for reaching definitions analysis */
    private HashSet<ControlFlowGraphNode> reachingDefinitionsIn = new HashSet<ControlFlowGraphNode>();
    private HashSet<ControlFlowGraphNode> reachingDefinitionsOut = new HashSet<ControlFlowGraphNode>();

    /** Dominators */
    private HashSet<ControlFlowGraphNode> dominators = new HashSet<ControlFlowGraphNode>();
    ControlFlowGraphNode immediateDominator = null;
    
    public ControlFlowGraphNode(ImcStmt statement) {
        this.statement = statement;
    }
    
    public void addSuccessor(ControlFlowGraphNode node) {
        this.successors.add((ControlFlowGraphNode) node);
    }

    public void addPredecessor(ControlFlowGraphNode node) {
        this.predecessors.add((ControlFlowGraphNode) node);
    }

    public LinkedHashSet<ControlFlowGraphNode> getPredecessors() {
        return new LinkedHashSet<ControlFlowGraphNode>(this.predecessors);
    }

    public LinkedHashSet<ControlFlowGraphNode> getSuccessors() {
        return new LinkedHashSet<ControlFlowGraphNode>(this.successors);
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

    /** Getters and setters for reaching definitions analysis */
    public HashSet<ControlFlowGraphNode> getReachingDefinitionsIn() {
        return new HashSet<ControlFlowGraphNode>(this.reachingDefinitionsIn);
    }

    public void setReachingDefinitionsIn(HashSet<ControlFlowGraphNode> reachingDefinitionsIn) {
        this.reachingDefinitionsIn = reachingDefinitionsIn;
    }

    public HashSet<ControlFlowGraphNode> getReachingDefinitionsOut() {
        return new HashSet<ControlFlowGraphNode>(this.reachingDefinitionsOut);
    }

    public void setReachingDefinitionsOut(HashSet<ControlFlowGraphNode> reachingDefinitionsOut) {
        this.reachingDefinitionsOut = reachingDefinitionsOut;
    }

    @Override
    public String toString() {
        return String.format("Node(%s)", this.statement.toString());
    }
}