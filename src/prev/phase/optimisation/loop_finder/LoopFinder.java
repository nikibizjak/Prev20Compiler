import java.util.*;

public class LoopFinder {

    public static LoopNode computeLoopNestingTree(Graph graph) {
        // First, compute all dominators of all nodes in graph
        computeDominators(graph);

        // Then, use immediate dominator theorem to find immediate dominators
        // for all node in graph
        computeImmediateDominators(graph);

        // Then, find all natural loops in graph and construct a loop-nest tree
        LoopNode loopNestingTree = findLoops(graph);

        return loopNestingTree;
    }

    private static void computeDominators(Graph graph) {
        graph.initialNode.dominators.add(graph.initialNode);

        for (Node node : graph.nodes) {
            if (node == graph.initialNode)
                continue;
            node.dominators = graph.nodes;
        }

        boolean hasChanged;
        do {
            hasChanged = false;
            for (Node node : graph.nodes) {
                if (node == graph.initialNode)
                    continue;
                
                HashSet<Node> newDominators = new HashSet<Node>();
                newDominators.add(node);
    
                HashSet<Node> intersection = null;
                for (Node predecessor : node.predecessors) {
                    if (intersection == null) {
                        intersection = new HashSet<Node>(predecessor.dominators);
                    } else {
                        intersection.retainAll(predecessor.dominators);
                    }
                }
    
                newDominators.addAll(intersection);
                boolean dominatorsChanged = !newDominators.equals(node.dominators);
                hasChanged = dominatorsChanged || hasChanged;

                node.dominators = newDominators;
            }
        } while (hasChanged);
    }

    private static void computeImmediateDominators(Graph graph) {
        for (Node node : graph.nodes) {

            // idiom(n) dominates n
            HashSet<Node> possibleDominators = new HashSet<Node>(node.dominators);

            // idiom(n) is not the same node as n
            possibleDominators.remove(node);

            // idiom(n) does not dominate any other dominator of n
            for (Node dominator : node.dominators) {
                if (dominator == node)
                    continue;
                HashSet<Node> dominatorDominators = new HashSet<Node>(dominator.dominators);
                dominatorDominators.remove(dominator);
                possibleDominators.removeAll(dominatorDominators);
            }

            if (possibleDominators.isEmpty())
                continue;

            // There should only be ONE possible immediate dominator
            node.immediateDominator = possibleDominators.iterator().next();
        }
        
    }

    private static LoopNode findLoops(Graph graph) {
        // A flow-graph edge from a node n to a node h that dominates n is
        // called a back edge. For every back edge there is a corresponding
        // subgraph of the flow graph that is a loop.
        LoopNode mainLoop = new LoopNode(graph.initialNode);
        mainLoop.loopItems.addAll(graph.nodes);

        HashMap<Node, LoopNode> loopHeaders = new HashMap<Node, LoopNode>();
        loopHeaders.put(graph.initialNode, mainLoop);

        // The *natural loop* of a back ednge n -> h, where h dominates n, is
        // the set of nodes x such that h dominates x and there is a path from x
        // to not containing h. The header of this loop will be h.
        for (Node node : graph.nodes) {
            if (node == graph.initialNode)
                continue;
            
            // First, find all back edges from this node
            HashSet<Node> backEdges = new HashSet<Node>(node.successors);
            backEdges.retainAll(node.dominators);

            // If there are any back edges from n -> h, the h is the header of
            // the loop (in our case, the backEdges set already contains a list
            // of all loop headers)
            // Find all nodes on path between h -> n
            for (Node loopHeader : backEdges) {
                
                List<Node> loop = constructLoop(node, loopHeader);

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

    private static ArrayList<Node> constructLoop(Node from, Node to) {
        ArrayList<Node> loop = new ArrayList<Node>();
        loop.add(from);

        Node currentNode = from;
        while (currentNode != null && currentNode != to) {
            currentNode = currentNode.immediateDominator;
            if (loop.contains(currentNode))
                break;

            if (currentNode != null)
                loop.add(currentNode);
        }

        return loop;
    }

}

class LoopNode {

    public LoopNode parent;

    public Node header;
    public Set<Node> loopItems = new HashSet<Node>();
    public List<LoopNode> subLoops = new ArrayList<LoopNode>();

    public LoopNode(Node header) {
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

    public LoopNode nodeContainingHeader(Node header) {
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
        return String.format("LoopNode(header: %s)", this.header);
    }
}

class Graph {
    
    public Node initialNode;
    public Set<Node> nodes;

    public Graph(Node initialNode) {
        this.initialNode = initialNode;

        this.nodes = new HashSet<Node>();
        this.nodes.add(this.initialNode);
    }

    public void addNode(Node node) {
        this.nodes.add(node);
    }

    @Override
    public String toString() {
        return String.format("%s", this.nodes);
    }

}

class Node {
    
    public int value;
    public Set<Node> successors = new HashSet<Node>();
    public Set<Node> predecessors = new HashSet<Node>();
    public Set<Node> dominators = new HashSet<Node>();
    public Node immediateDominator = null;

    public Node(int value) {
        this.value = value;
    }

    public void addSuccessors(Node[] neighbours) {
        for (int i = 0; i < neighbours.length; i++) {
            this.successors.add(neighbours[i]);
            neighbours[i].predecessors.add(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Node(%d)", this.value);
    }

}