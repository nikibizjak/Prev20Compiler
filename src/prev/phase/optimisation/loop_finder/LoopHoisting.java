public class LoopHoisting {

    public static void main(String[] args) {

        Node node1 = new Node(1);
        Node node2 = new Node(2);
        Node node3 = new Node(3);
        Node node4 = new Node(4);
        Node node5 = new Node(5);
        Node node6 = new Node(6);
        Node node7 = new Node(7);
        Node node8 = new Node(8);
        Node node9 = new Node(9);
        Node node10 = new Node(10);
        Node node11 = new Node(11);
        Node node12 = new Node(12);

        Graph graph = new Graph(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);
        graph.addNode(node5);
        graph.addNode(node6);
        graph.addNode(node7);
        graph.addNode(node8);
        graph.addNode(node9);
        graph.addNode(node10);
        graph.addNode(node11);
        graph.addNode(node12);

        node1.addSuccessors(new Node[] { node2 });
        node2.addSuccessors(new Node[] { node3, node4 });
        node3.addSuccessors(new Node[] { node2 });
        node4.addSuccessors(new Node[] { node2, node5, node6 });
        node5.addSuccessors(new Node[] { node7, node8 });
        node6.addSuccessors(new Node[] { node7 });
        node7.addSuccessors(new Node[] { node11 });
        node8.addSuccessors(new Node[] { node9 });
        node9.addSuccessors(new Node[] { node8, node10 });
        node10.addSuccessors(new Node[] { node5, node12 });
        node11.addSuccessors(new Node[] { node12 });

        LoopFinder.computeLoopNestingTree(graph);

    }

}