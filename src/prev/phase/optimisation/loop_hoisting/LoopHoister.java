package prev.phase.optimisation.loop_hoisting;

import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraphNode;
import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraph;
import prev.phase.optimisation.common.dominators.LoopFinder;
import prev.phase.optimisation.common.dominators.LoopNode;
import prev.phase.optimisation.common.liveness_analysis.LivenessAnalysis;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.MemLabel;

import java.util.*;

public class LoopHoister {

    public static void print(ControlFlowGraphNode node, HashSet<ControlFlowGraphNode> alreadyPrinted) {
        // System.out.println(node + ", in: " + node.getLiveIn() + ", out: " + node.getLiveOut());
        // System.out.println(node + ", dominators: " + node.getImmediateDominator());
        System.out.println(node);
        alreadyPrinted.add(node);
        for (ControlFlowGraphNode successor : ((Set<ControlFlowGraphNode>) node.getSuccessors())) {
            if (!alreadyPrinted.contains(successor))
                print(successor, alreadyPrinted);
        }
    }

    public static void run(ControlFlowGraph graph) {       
        // print(graph.nodes.iterator().next(), new HashSet<ControlFlowGraphNode>());
        
        // First, compute loop nesting tree (containing all loops in current
        // program). The first level of nestingTree is a full program.
        LoopNode nestingTree = LoopFinder.findAllLoops(graph);

        // Add preheaders to all loops (excluding the first level of nesting).
        for (LoopNode loop : nestingTree.subLoops) {
            // Add preheader nodes to all loops
            addPreheader(graph, loop);
        }

        // Then, perform liveness analysis on control-flow graph (this will also
        // make sure that our preheader live-out is computed)
        LivenessAnalysis.analysis(graph);

        // Don't optimize the first level of nesting tree.
        for (LoopNode loop : nestingTree.subLoops) {
            // Hoist statements out of the loop
            hoist(graph, loop);
        }
    }

    private static void addPreheader(ControlFlowGraph graph, LoopNode loop) {
        // Visit all subloops first
        for (LoopNode subLoop : loop.subLoops)
            addPreheader(graph, subLoop);

        // Then, add preheader to loop
        ControlFlowGraphNode header = loop.header;

        // Construct a new control-flow graph node that will be inserted BEFORE
        // loop header
        ControlFlowGraphNode preheader = new ControlFlowGraphNode(new ImcLABEL(new MemLabel("preheader")));
        loop.preheaderStart = preheader;
        loop.preheaderEnd = preheader;

        for (ControlFlowGraphNode predecessor : ((Set<ControlFlowGraphNode>) header.getPredecessors())) {
            if (loop.loopItems.contains(predecessor))
                continue;
            graph.insertAfter(predecessor, preheader);
        }
    }

    /** Whether or not, the statement is loop invariant */
    private static boolean isLoopInvariant(ImcStmt statement) {
        // The definition d: t <- a_1 + a_2 is loop invariant within loop L if,
        // for each operand a_i:
        //   1. a_i is a constant or
        //   2. all the definitions of a_i that reach d are outside the loop or
        //   3. only one definition of a_i reaches d, and that definition is loop-invariant

        return false;
    }

    /** Whether or not, the statement is of form t <- a + b */
    private static boolean isBinaryOperation(ImcStmt statement) {
        return statement instanceof ImcMOVE
            && ((ImcMOVE) statement).dst instanceof ImcTEMP
            && ((ImcMOVE) statement).src instanceof ImcBINOP;
    }

    private static void hoist(ControlFlowGraph graph, LoopNode loop) {
        // Visit all subloops first
        for (LoopNode subLoop : loop.subLoops)
            hoist(graph, subLoop);

        // Definition d: t <- a + b can be hoisted to the end of the loop
        // preheader if:
        // 1. d dominates all loop exits at which t is live-out
        // 2. and there is only one definition of t in the loop
        // 3. and t is not live-out of the loop preheader

        // Construct a list of all possible TEMPs that can be moved out from the
        // loop. The TEMPS that won't match all three conditions will be removed.
        HashSet<ControlFlowGraphNode> hoistingCandidates = new HashSet<ControlFlowGraphNode>();
        HashSet<ImcTEMP> alreadyDefined = new HashSet<ImcTEMP>();
        System.out.println("STEP 1");
        for (ControlFlowGraphNode node : loop.loopItems) {

            if (!isBinaryOperation(node.statement))
                continue;

            // Skip all statements that are not loop invariant            
            if (!isLoopInvariant(node.statement))
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
                System.out.println("There is more than one one definition of " + definitions + " in the loop");
                alreadyDefined.addAll(node.getDefines());
                continue;
            }

            // Node is a good candidate for hoisting
            hoistingCandidates.add(node);
            alreadyDefined.addAll(node.getDefines());
        }
        System.out.println("Hoisting candidates: " + hoistingCandidates);

        System.out.println("Finding loop exits");
        // Find loop exits. Loop exit is a ControlFlowGraphNode which can jump
        // to a ControlFlowGraphNode that is not in loop.loopItems
        HashSet<ControlFlowGraphNode> loopExits = new HashSet<ControlFlowGraphNode>();
        for (ControlFlowGraphNode node : loop.loopItems) {
            Set<ControlFlowGraphNode> successors = node.getSuccessors();
            successors.removeAll(loop.loopItems);
            if (successors.size() > 0)
                loopExits.add(node);
        }
        System.out.println("Loop exits: " + loopExits);

        System.out.println("STEP 2");
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
                        System.out.println(
                                node + " does not dominate loop exit at which " + definedTemporary + " is live out");
                        invalidHoistingCandidates.add(node);
                        continue;
                    }
                }
            }
        }
        System.out.println("Invalid hoisting candidates: " + invalidHoistingCandidates);
        hoistingCandidates.removeAll(invalidHoistingCandidates);

        System.out.println("STEP 3");
        // 3. and t is not live-out of the loop preheader
        invalidHoistingCandidates.clear();
        HashSet<ImcTEMP> preheaderLiveOut = loop.preheaderEnd.getLiveOut();
        System.out.println("Preheader live out: " + preheaderLiveOut);
        for (ControlFlowGraphNode node : hoistingCandidates) {
            ImcTEMP definedTemporary = node.getDefines().iterator().next();
            System.out.println(node + " defines " + definedTemporary);
            if (preheaderLiveOut.contains(definedTemporary)) {
                System.out.println(definedTemporary + " is live-out of the loop preheader");
                // t is live out of the loop preheader
                invalidHoistingCandidates.add(node);
            }
        }
        System.out.println("Invalid hoisting candidates: " + invalidHoistingCandidates);
        hoistingCandidates.removeAll(invalidHoistingCandidates);

        System.out.println("LOOP BEFORE:");
        print(loop.preheaderStart, new HashSet<ControlFlowGraphNode>());
        System.out.println();

        // HOIST CANDIDATES OUT OF THE LOOP
        for (ControlFlowGraphNode node : hoistingCandidates) {
            System.out.println("HOISTING: " + node);
            graph.removeNode(node);
            graph.insertAfter(loop.preheaderEnd, node);
            loop.preheaderEnd = node;
        }

        System.out.println("LOOP AFTER");
        print(loop.preheaderStart, new HashSet<ControlFlowGraphNode>());
        System.out.println();
    }

}