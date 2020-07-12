package prev.phase.regall;

import prev.data.mem.*;

import java.util.HashSet;

public class Node {

    public MemTemp temporary;

    public int color = -1;
    public boolean potentialSpill = false;
    public boolean actualSpill = false;

    public HashSet<Node> neighbours;

    public Node(MemTemp temporary) {
        this.temporary = temporary;
        this.neighbours = new HashSet<Node>();
    }

    public int degree() {
        return this.neighbours.size();
    }

    public void addEdge(Node neighbour) {
        this.neighbours.add(neighbour);
    }

    public void removeEdge(Node neighbour) {
        this.neighbours.remove(neighbour);
    }

    @Override
    public String toString() {
        if (this.actualSpill)
            return this.temporary.toString() + "[PS+AS]";
        else if (this.potentialSpill)
            return this.temporary.toString() + "[PS]";
        return this.temporary.toString();
    }

}