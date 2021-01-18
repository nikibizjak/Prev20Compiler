package prev.phase.optimisation.common.dominators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraph;
import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraphNode;

public class LoopFinder {

    HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>> dominators = new HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>>();
    HashMap<ControlFlowGraphNode, ControlFlowGraphNode> immediateDominators = new HashMap<ControlFlowGraphNode, ControlFlowGraphNode>();

    public static LoopNode findAllLoops(ControlFlowGraph graph) {
        LoopFinder loopFinder = new LoopFinder();
        return loopFinder.computeLoopNestingTree(graph);
    }

    public static void computeDominators(ControlFlowGraph graph) {
        LoopFinder loopFinder = new LoopFinder();
        loopFinder.dominators(graph);
    }

    private LoopNode computeLoopNestingTree(ControlFlowGraph graph) {
        // First, compute all dominators of all nodes in graph
        this.dominators(graph);

        // Then, use immediate dominator theorem to find immediate dominators
        // for all nodes in graph
        this.computeImmediateDominators(graph);

        // Then, find all natural loops in graph and construct a loop-nest tree
        LoopNode loopNestingTree = findLoops(graph);

        return loopNestingTree;
    }

    private void dominators(ControlFlowGraph graph) {
        Iterator<ControlFlowGraphNode> iterator = graph.nodes.iterator();
        ControlFlowGraphNode initialNode = iterator.next();

        LinkedHashSet<ControlFlowGraphNode> graphNodes = (LinkedHashSet<ControlFlowGraphNode>) graph.nodes;

        for (ControlFlowGraphNode node : graphNodes){
            HashSet<ControlFlowGraphNode> initialDominators = new HashSet<ControlFlowGraphNode>();
            initialDominators.addAll(graph.nodes);
            dominators.put(node, initialDominators);
        }

        HashSet<ControlFlowGraphNode> initialNodeDominators = new HashSet<ControlFlowGraphNode>();
        initialNodeDominators.add(initialNode);
        dominators.put(initialNode, initialNodeDominators);

        boolean hasChanged;
        do {
            hasChanged = false;
            for (ControlFlowGraphNode node : graphNodes) {
                if (node == initialNode)
                    continue;
                
                HashSet<ControlFlowGraphNode> newDominators = new HashSet<ControlFlowGraphNode>();
                newDominators.add(node);
    
                HashSet<ControlFlowGraphNode> intersection = null;
                HashSet<ControlFlowGraphNode> nodePredecessors = (HashSet<ControlFlowGraphNode>) node.getPredecessors();
                for (ControlFlowGraphNode predecessor : nodePredecessors) {
                    if (intersection == null) {
                        intersection = new HashSet<ControlFlowGraphNode>(dominators.get(predecessor));
                    } else {
                        intersection.retainAll(dominators.get(predecessor));
                    }
                }
    
                newDominators.addAll(intersection);
                boolean dominatorsChanged = !newDominators.equals(dominators.get(node));
                hasChanged = dominatorsChanged || hasChanged;

                dominators.put(node, newDominators);
            }
        } while (hasChanged);

        for (ControlFlowGraphNode node : graphNodes) {
            node.setDominators(dominators.get(node));
        }
    }

    private void computeImmediateDominators(ControlFlowGraph graph) {
        LinkedHashSet<ControlFlowGraphNode> graphNodes = (LinkedHashSet<ControlFlowGraphNode>) graph.nodes;
        for (ControlFlowGraphNode node : graphNodes) {

            // idiom(n) dominates n
            HashSet<ControlFlowGraphNode> possibleDominators = new HashSet<ControlFlowGraphNode>(dominators.get(node));

            // idiom(n) is not the same node as n
            possibleDominators.remove(node);

            // idiom(n) does not dominate any other dominator of n
            for (ControlFlowGraphNode dominator : dominators.get(node)) {
                if (dominator == node)
                    continue;
                HashSet<ControlFlowGraphNode> dominatorDominators = new HashSet<ControlFlowGraphNode>(dominators.get(dominator));
                dominatorDominators.remove(dominator);
                possibleDominators.removeAll(dominatorDominators);
            }

            if (possibleDominators.isEmpty())
                continue;

            // There should only be ONE possible immediate dominator
            ControlFlowGraphNode immediateDominator = (ControlFlowGraphNode) possibleDominators.iterator().next();
            immediateDominators.put(node, immediateDominator);
            ((ControlFlowGraphNode) node).setImmediateDominator(immediateDominator);
        }
        
    }

    private LoopNode findLoops(ControlFlowGraph graph) {
        Iterator<ControlFlowGraphNode> iterator = graph.nodes.iterator();
        ControlFlowGraphNode initialNode = iterator.next();

        // A flow-graph edge from a node n to a node h that dominates n is
        // called a back edge. For every back edge there is a corresponding
        // subgraph of the flow graph that is a loop.
        LoopNode mainLoop = new LoopNode(initialNode);
        mainLoop.loopItems.addAll(graph.nodes);

        HashMap<ControlFlowGraphNode, LoopNode> loopHeaders = new HashMap<ControlFlowGraphNode, LoopNode>();
        loopHeaders.put(initialNode, mainLoop);

        // The *natural loop* of a back ednge n -> h, where h dominates n, is
        // the set of nodes x such that h dominates x and there is a path from x
        // to not containing h. The header of this loop will be h.
        LinkedHashSet<ControlFlowGraphNode> graphNodes = (LinkedHashSet<ControlFlowGraphNode>) graph.nodes;
        for (ControlFlowGraphNode node : graphNodes) {
            if (node == initialNode)
                continue;
            
            // First, find all back edges from this node
            HashSet<ControlFlowGraphNode> backEdges = new HashSet<ControlFlowGraphNode>(node.getSuccessors());
            backEdges.retainAll(dominators.get(node));

            // If there are any back edges from n -> h, the h is the header of
            // the loop (in our case, the backEdges set already contains a list
            // of all loop headers)
            // Find all nodes on path between h -> n
            for (ControlFlowGraphNode loopHeader : backEdges) {
                
                List<ControlFlowGraphNode> loop = constructLoop(node, loopHeader);

                if (loopHeaders.containsKey(loopHeader)) {
                    LoopNode currentLoopNode = loopHeaders.get(loopHeader);
                    currentLoopNode.loopItems.addAll(loop);
                    currentLoopNode.removeFromParent(currentLoopNode);
                } else {
                    LoopNode loopContainingHeader = mainLoop.nodeContainingHeader(loopHeader);
                    LoopNode currentLoopNode = new LoopNode(loopHeader);
                    currentLoopNode.loopItems.addAll(loop);
                    loopContainingHeader.addSubLoop(currentLoopNode);
                    loopHeaders.put(loopHeader, currentLoopNode);
                }
            }

        }

        return mainLoop;
    }

    private ArrayList<ControlFlowGraphNode> constructLoop(ControlFlowGraphNode from, ControlFlowGraphNode to) {
        ArrayList<ControlFlowGraphNode> loop = new ArrayList<ControlFlowGraphNode>();
        loop.add(from);

        ControlFlowGraphNode currentNode = from;
        while (currentNode != null && currentNode != to) {
            currentNode = immediateDominators.get(currentNode);
            if (loop.contains(currentNode))
                break;

            if (currentNode != null)
                loop.add(currentNode);
        }

        return loop;
    }

}