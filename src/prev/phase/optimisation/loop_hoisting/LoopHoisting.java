package prev.phase.optimisation.loop_hoisting;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.dominators.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.common.report.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.MemLabel;
import prev.data.mem.*;

import java.util.*;

public class LoopHoisting {

    /** A mapping between loop header and preheader object containing preheader
     * nodes */
    private static HashMap<ControlFlowGraphNode, Preheader> preheaders = new HashMap<ControlFlowGraphNode, Preheader>();

    public static boolean run(ControlFlowGraph graph) {
        boolean hasGraphChanged = false;

        // The nesting tree contains all loops in the current program. The
        // first level of nestingTree is a full program.
        LoopNode nestingTree = LoopFinder.findAllLoops(graph);

        // Only add preheaders to all loops ONCE. Exclude the first level of
        // nesting (the whole program).
        for (LoopNode loop : nestingTree.subLoops) {
            addPreheader(graph, loop);
        }

        // Don't optimize the first level of nesting tree.
        for (LoopNode loop : nestingTree.subLoops) {
            // Hoist statements out of the loop
            hasGraphChanged = hasGraphChanged || hoist(graph, loop);
        }

        return hasGraphChanged;
    }

    public static void addPreheader(ControlFlowGraph graph, LoopNode loop) {
        // Visit all subloops first
        for (LoopNode subLoop : loop.subLoops)
            addPreheader(graph, subLoop);

        // Then, add preheader to loop
        ControlFlowGraphNode header = loop.header;

        Preheader preheader = preheaders.get(header);
        if (preheader == null) {
            // Preheader does not yet exist. Construct a new one.

            // Construct a new control-flow graph node that will be inserted
            // before loop header.
            MemLabel preheaderLabel = MemLabel.uniqueFromName("preheader");
            ControlFlowGraphNode firstPreheaderNode = new ControlFlowGraphNode(new ImcLABEL(preheaderLabel));

            MemLabel headerLabel = ((ImcLABEL) header.statement).label;
            ControlFlowGraphNode lastPreheaderNode = new ControlFlowGraphNode(new ImcJUMP(headerLabel));

            // graph.insertBefore(header, firstPreheaderNode);
            for (ControlFlowGraphNode predecessor : header.getPredecessors()) {
                if (!loop.containsNode(predecessor)) {
                    graph.insertAfter(predecessor, firstPreheaderNode);
                }
            }
            
            // Remove all loop exits from predecessors of first preheader node
            for (ControlFlowGraphNode predecessor : firstPreheaderNode.getPredecessors()) {
                if (loop.containsNode(predecessor)) {
                    // This is a loop exit, remove it
                    firstPreheaderNode.predecessors.remove(predecessor);
                    predecessor.successors.remove(firstPreheaderNode);
                }
            }
            
            graph.insertAfter(firstPreheaderNode, lastPreheaderNode);
            preheader = new Preheader(graph, firstPreheaderNode, lastPreheaderNode);
        }

        loop.setPreheader(preheader);
        preheaders.put(header, preheader);
    }

    /** Whether or not, the statement is loop invariant */
    public static boolean isLoopInvariant(LoopNode loop, ControlFlowGraphNode node, HashSet<ControlFlowGraphNode> alreadyVisited) {
        // The definition d: t <- a_1 + a_2 is loop invariant within loop L if,
        // for each operand a_i:
        //   1. a_i is a constant or
        //   2. all the definitions of a_i that reach d are outside the loop or
        //   3. only one definition of a_i reaches d, and that definition is loop-invariant
        if (alreadyVisited.contains(node))
            return false;
        
        alreadyVisited.add(node);

        if (!(node.statement instanceof ImcMOVE))
            return false;
        
        ImcMOVE move = (ImcMOVE) node.statement;
        
        HashSet<ImcExpr> subexpressions = new HashSet<ImcExpr>();
        
        if (move.src instanceof ImcBINOP) {
            ImcBINOP binaryOperation = (ImcBINOP) move.src;
            subexpressions.add(binaryOperation.fstExpr);
            subexpressions.add(binaryOperation.sndExpr);
        } else if (move.src instanceof ImcUNOP) {
            ImcUNOP unaryOperation = (ImcUNOP) move.src;
            subexpressions.add(unaryOperation.subExpr);
        } else {
            return false;
        }

        HashSet<ControlFlowGraphNode> reachingDefinitions = node.getReachingDefinitionsIn();

        for (ImcExpr subexpression : subexpressions) {
            // Check for condition 1: is subexpression constant
            if (subexpression instanceof ImcCONST) {
                continue;
            }

            // Check for condition 2: all the definitions of a_i that reach
            // d are outside the loop
            boolean allDefinitionsOutsideLoop = true;
            HashSet<ControlFlowGraphNode> subexpressionDefinitions = new HashSet<ControlFlowGraphNode>();
            for (ControlFlowGraphNode definition : reachingDefinitions) {
                if (!definition.getDefines().contains(subexpression))
                    continue;
                subexpressionDefinitions.add(definition);
                // One definition of a_i that reaches d is inside the loop
                if (loop.containsNode(definition)) {
                    allDefinitionsOutsideLoop = false;
                    break;
                }
            }
            if (allDefinitionsOutsideLoop) {
                continue;
            }
            
            // Check for condition 3: only one definition of a_i reaches d,
            // and that definition is loop-invariant
            if (subexpressionDefinitions.size() == 1) {
                ControlFlowGraphNode onlyReachingDefinition = subexpressionDefinitions.iterator().next();
                if (!onlyReachingDefinition.statement.equals(node.statement) && isLoopInvariant(loop, onlyReachingDefinition, alreadyVisited)) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    /** Whether or not, the statement is of form t <- a + b */
    private static boolean isBinaryOperation(ImcStmt statement) {
        return statement instanceof ImcMOVE
            && ((ImcMOVE) statement).dst instanceof ImcTEMP
            && ((ImcMOVE) statement).src instanceof ImcBINOP;
    }

    private static boolean hoist(ControlFlowGraph graph, LoopNode loop) {
        boolean hasGraphChanged = false;

        // Visit all subloops first
        for (LoopNode subLoop : loop.subLoops)
            hasGraphChanged = hasGraphChanged || hoist(graph, subLoop);
        
        // Then, perform liveness analysis on control-flow graph (this will also
        // make sure that our preheader live-out is computed)
        LivenessAnalysis.analysis(graph);
        ReachingDefinitionsAnalysis.run(graph);

        // Definition d: t <- a + b can be hoisted to the end of the loop
        // preheader if:
        // 1. d dominates all loop exits at which t is live-out
        // 2. and there is only one definition of t in the loop
        // 3. and t is not live-out of the loop preheader

        // Construct a list of all possible TEMPs that can be moved out from the
        // loop. The TEMPS that won't match all three conditions will be removed.
        LinkedHashSet<ControlFlowGraphNode> hoistingCandidates = new LinkedHashSet<ControlFlowGraphNode>();
        HashSet<ImcTEMP> alreadyDefined = new HashSet<ImcTEMP>();
        HashSet<ControlFlowGraphNode> loopNodes = loop.getAllNodes();

        // Iterate over all nodes in graph, but skip those that are not inside
        // the loop. This way, the order of the instructions is preserved. If we
        // iterated over loopNodes.allNodes(), the statements would be hoisted
        // in incorrect order, causing the program to break.
        for (ControlFlowGraphNode node : graph.nodes) {
            if (!loopNodes.contains(node))
                continue;
            
            if (!isBinaryOperation(node.statement))
                continue;

            // Skip all statements that are not loop invariant            
            if (!isLoopInvariant(loop, node, new HashSet<ControlFlowGraphNode>()))
                continue;

            HashSet<ImcTEMP> definitions = node.getDefines();
            if (definitions.size() > 1) {
                // More than one variable is defined, this is not a good
                // candidate for hoisting
                alreadyDefined.addAll(definitions);
                continue;
            }

            definitions.retainAll(alreadyDefined);
            if (definitions.size() > 0) {
                // One of the temporaries is redefined, this is not a good
                // candidate for hoisting.
                alreadyDefined.addAll(node.getDefines());
                continue;
            }

            // Node is a good candidate for hoisting
            hoistingCandidates.add(node);
            alreadyDefined.addAll(node.getDefines());
        }

        // Find loop exits. Loop exit is a ControlFlowGraphNode which can jump
        // to a ControlFlowGraphNode that is not in loop.loopItems
        HashSet<ControlFlowGraphNode> loopExits = new HashSet<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : loop.getAllNodes()) {
            Set<ControlFlowGraphNode> successors = node.getSuccessors();
            successors.removeAll(loop.getAllNodes());
            if (successors.size() > 0)
                loopExits.add(node);
        }

        HashSet<ControlFlowGraphNode> invalidHoistingCandidates = new HashSet<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : hoistingCandidates) {
            // 1. d dominates all loop exits at which t is live-out
            // There is ONLY ONE definedTemporary in defs[node]
            ImcTEMP definedTemporary = node.getDefines().iterator().next();
            for (ControlFlowGraphNode loopExit : loopExits) {
                if (loopExit.getLiveOut().contains(definedTemporary)) {
                    // This is a loop exit at which t is live-out
                    if (!loopExit.getDominators().contains(node)) {
                        // d does not dominate loop exit at which t is live out,
                        // DONT HOIST
                        invalidHoistingCandidates.add(node);
                        continue;
                    }
                }
            }
        }
        hoistingCandidates.removeAll(invalidHoistingCandidates);

        // 3. and t is not live-out of the loop preheader
        invalidHoistingCandidates.clear();
        HashSet<ImcTEMP> preheaderLiveOut = loop.preheader.preheaderEnd.getLiveOut();
        for (ControlFlowGraphNode node : hoistingCandidates) {
            ImcTEMP definedTemporary = node.getDefines().iterator().next();
            if (preheaderLiveOut.contains(definedTemporary)) {
                // t is live out of the loop preheader
                invalidHoistingCandidates.add(node);
            }
        }
        hoistingCandidates.removeAll(invalidHoistingCandidates);

        // HOIST CANDIDATES OUT OF THE LOOP
        for (ControlFlowGraphNode node : hoistingCandidates) {
            Report.debug("Hoisting node: " + node);
            graph.removeNode(node);
            loop.preheader.append(node);
            hasGraphChanged = true;
        }

        return hasGraphChanged;
    }

}