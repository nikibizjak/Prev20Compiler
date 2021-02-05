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

    /** Loop nesting tree that should only be computed once */
    private static LoopNode nestingTree = null;

    public static boolean run(ControlFlowGraph graph) {
        boolean hasGraphChanged = false;

        // If loop nesting tree has not yet been computed, compute it now. The
        // nesting tree contains all loops in the current program. The first
        // level of nestingTree is a full program.
        if (nestingTree == null) {
            nestingTree = LoopFinder.findAllLoops(graph);

            // Only add preheaders to all loops ONCE. Exclude the first level of
            // nesting (the whole program).
            for (LoopNode loop : nestingTree.subLoops) {
                addPreheader(graph, loop);
            }
        }

        // Don't optimize the first level of nesting tree.
        for (LoopNode loop : nestingTree.subLoops) {
            // Hoist statements out of the loop
            hasGraphChanged = hasGraphChanged || hoist(graph, loop);
        }

        return hasGraphChanged;
    }

    private static void addPreheader(ControlFlowGraph graph, LoopNode loop) {
        // Visit all subloops first
        for (LoopNode subLoop : loop.subLoops)
            addPreheader(graph, subLoop);

        // Then, add preheader to loop
        ControlFlowGraphNode header = loop.header;
        System.out.println("Loop header: " + loop.header);

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
    private static boolean isLoopInvariant(LoopNode loop, ControlFlowGraphNode node) {
        // The definition d: t <- a_1 + a_2 is loop invariant within loop L if,
        // for each operand a_i:
        //   1. a_i is a constant or
        //   2. all the definitions of a_i that reach d are outside the loop or
        //   3. only one definition of a_i reaches d, and that definition is loop-invariant
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
        HashSet<ControlFlowGraphNode> loopNodes = loop.getLoopNodes();

        // TODO: Write better checks for loop invariance
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
                if (loopNodes.contains(definition)) {
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
                if (!onlyReachingDefinition.equals(node) && isLoopInvariant(loop, onlyReachingDefinition)) {
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
        for (ControlFlowGraphNode node : loop.getLoopItems()) {

            if (!isBinaryOperation(node.statement))
                continue;

            // Skip all statements that are not loop invariant            
            if (!isLoopInvariant(loop, node))
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
        for (ControlFlowGraphNode node : loop.loopItems) {
            Set<ControlFlowGraphNode> successors = node.getSuccessors();
            successors.removeAll(loop.loopItems);
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
        HashSet<ImcTEMP> preheaderLiveOut = loop.preheaderEnd.getLiveOut();
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
            graph.insertAfter(loop.preheaderEnd, node);

            // Because we are now only computing loops once, we must also remove
            // nodes from the loop.
            loop.loopItems.remove(node);
            
            loop.preheaderEnd = node;
            hasGraphChanged = true;
        }

        return hasGraphChanged;
    }

}