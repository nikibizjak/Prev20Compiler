package prev.phase.optimisation.common.control_flow_graph;

import java.util.HashMap;
import java.util.Vector;
import java.util.HashSet;
import java.util.LinkedHashSet;
import prev.data.lin.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;

public class ControlFlowGraphBuilder {

    public static ControlFlowGraph build(LinCodeChunk codeChunk) {
        ControlFlowGraph controlFlowGraph = new ControlFlowGraph(codeChunk);

        if (codeChunk.stmts().size() <= 0)
            return controlFlowGraph;

        // First, iterate over all elements once and construct nodes for
        // LABEL's. Those nodes will then be used in the next iteration in JUMP
        // and CJUMP statements as connections in control-flow graph.
        HashMap<MemLabel, ControlFlowGraphNode> labels = new HashMap<MemLabel, ControlFlowGraphNode>();
        for (ImcStmt statement : codeChunk.stmts()) {
            if (statement instanceof ImcLABEL) {
                MemLabel label = ((ImcLABEL) statement).label;
                labels.put(label, new ControlFlowGraphNode(statement));
            }
        }

        Vector<ImcStmt> statements = codeChunk.stmts();

        // Initialize the first statement and the first node
        ImcStmt previousStatement = statements.get(0);
        ControlFlowGraphNode previousNode;
        if (previousStatement instanceof ImcLABEL && labels.containsKey(((ImcLABEL) previousStatement).label)) {
            previousNode = labels.get(((ImcLABEL) previousStatement).label);
        } else {
            previousNode = new ControlFlowGraphNode(previousStatement);
        }

        for (int i = 1; i < statements.size(); i++) {
            ImcStmt currentStatement = statements.get(i);

            // First, construct a new node for this statement
            ControlFlowGraphNode currentNode;
            if (currentStatement instanceof ImcLABEL && labels.containsKey(((ImcLABEL) currentStatement).label)) {
                currentNode = labels.get(((ImcLABEL) currentStatement).label);
            } else {
                currentNode = new ControlFlowGraphNode(currentStatement);
            }

            // The only statements after linearization that can alter the flow
            // of program are the JUMP and CJUMP statements. Any other statement
            // has only one successor - next statement in `statements` list.
            if (previousStatement instanceof ImcJUMP) {
                // Find the label that this JUMP actually jumps to
                MemLabel jumpLabel = ((ImcJUMP) previousStatement).label;
                ControlFlowGraphNode jumpNode = labels.get(jumpLabel);

                // The last statement in linearized code chunk is a jump out of
                // this chunk. Because the label for this is outside of the
                // chunk, it will not get inserted in the `labels` map, so
                // jumpNode will be null.
                if (jumpNode != null)
                    controlFlowGraph.addEdge(previousNode, jumpNode);
            } else if (previousStatement instanceof ImcCJUMP) {
                // The CJUMP statement is similar to JUMP statement, but it can
                // jump to two different label.

                // Handle negative label
                MemLabel negativeJumpLabel = ((ImcCJUMP) previousStatement).negLabel;
                ControlFlowGraphNode negativeJumpNode = labels.get(negativeJumpLabel);
                if (negativeJumpNode != null)
                    controlFlowGraph.addEdge(previousNode, negativeJumpNode);

                // Handle positive label
                MemLabel positiveJumpLabel = ((ImcCJUMP) previousStatement).posLabel;
                ControlFlowGraphNode positiveJumpNode = labels.get(positiveJumpLabel);
                if (positiveJumpNode != null)
                    controlFlowGraph.addEdge(previousNode, positiveJumpNode);
            } else {
                // Add an edge in control-flow graph between previousNode and
                // currentStatement (this will also insert both nodes into the graph).
                controlFlowGraph.addEdge(previousNode, currentNode);
            }

            previousStatement = currentStatement;
            previousNode = currentNode;
        }
        return controlFlowGraph;
    }

    private static void toStatementList(ControlFlowGraphNode currentNode, HashSet<ControlFlowGraphNode> alreadyVisited, Vector<ImcStmt> statements) {
        statements.add(currentNode.statement);
        alreadyVisited.add(currentNode);
        for (ControlFlowGraphNode successor : ((LinkedHashSet<ControlFlowGraphNode>) currentNode.getSuccessors())) {
            if (!alreadyVisited.contains(successor))
                toStatementList(successor, alreadyVisited, statements);
        }
    }

    public static Vector<ImcStmt> toStatements(ControlFlowGraph graph) {
        Vector<ImcStmt> statements = new Vector<ImcStmt>();
        ControlFlowGraphNode initialNode = graph.nodes.iterator().next();
        toStatementList(initialNode, new HashSet<ControlFlowGraphNode>(), statements);
        return statements;
    }

    public static ControlFlowGraph fromStatements(Vector<ImcStmt> statements) {
		MemFrame frame = new MemFrame(new MemLabel("graph"), 0, 0L, 0L);
		LinCodeChunk codeChunk = new LinCodeChunk(frame, statements, null, null);
		ControlFlowGraph controlFlowGraph = ControlFlowGraphBuilder.build(codeChunk);
		return controlFlowGraph;
    }

}