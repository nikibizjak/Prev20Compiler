package prev.phase.optimisation.common_subexpression_elimination;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.available_expressions.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.common.tree_replacement.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.common.report.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

public class CommonSubexpressionElimination {

    public static boolean run(ControlFlowGraph graph) {
        return runAnalysis(graph);
    }

    private static boolean runAnalysis(ControlFlowGraph graph) {
        
        boolean hasGraphChangedAtLeastOnce = false;
        boolean hasGraphChanged = false;
        
        do {

            AvailableExpressionsAnalysis.run(graph);

            hasGraphChanged = false;

            for (int i = graph.nodes.size() - 1; i >= 0; i--) {
                ControlFlowGraphNode node = graph.nodes.get(i);
                if (!(node.statement instanceof ImcMOVE))
                    continue;
                
                // TODO: Common subexpression elimination SHOULD work on
                // expressions of type MOVE(MEM(...), ...)
                ImcMOVE moveStatement = (ImcMOVE) node.statement;
                if (!(moveStatement.dst instanceof ImcTEMP))
                    continue;
                
                // Statement is of form t <- ... (or MOVE(TEMP(...), ...)). This is
                // a candidate for common subexpression elimination. If MOVE source
                // expression is already in this node available expression, it might
                // be possible to rewrite this statement.
                HashSet<ImcExpr> availableExpressionsIn = node.getAvailableExpressionsIn();

                HashSet<ImcExpr> statementSubexpressions = SubexpressionFinder.getAllSubexpressions(moveStatement.src);
                statementSubexpressions.retainAll(availableExpressionsIn);
                if (statementSubexpressions.size() <= 0)
                    continue;

                for (ImcExpr sourceExpression : statementSubexpressions) {

                    // Find a statement n that defines sourceExpression and there is
                    // no redefinition of temporaries used by sourceExpression (= sourceTemporaries) and
                    // there is no other computation of sourceExpression on path
                    // from n to statement s.
                    HashSet<ImcTEMP> sourceTemporaries = TemporaryFinder.getTemporaries(sourceExpression);

                    Queue<ControlFlowGraphNode> frontier = new LinkedList<ControlFlowGraphNode>();
                    HashSet<ControlFlowGraphNode> alreadyVisited = new HashSet<ControlFlowGraphNode>();
                    frontier.add(node);
                    alreadyVisited.add(node);

                    ControlFlowGraphNode foundNode = null;
                    while (frontier.size() > 0) {
                        ControlFlowGraphNode currentNode = frontier.remove();

                        if (currentNode != node && currentNode.statement instanceof ImcMOVE) {
                            // Check if this node defines any of the temporaries that
                            // appear in sourceTemporaries
                            HashSet<ImcTEMP> currentNodeDefinitions = currentNode.getDefines();
                            currentNodeDefinitions.retainAll(sourceTemporaries);
                            if (currentNodeDefinitions.size() > 0) {
                                break;
                            }

                            // If this node uses the same common subexpression, this is
                            // the first found subexpression, stop finding the next one
                            HashSet<ImcExpr> currentSubexpressions = SubexpressionFinder.getAllSubexpressions(((ImcMOVE) currentNode.statement).src);
                            if (currentSubexpressions.contains(sourceExpression)) {
                                foundNode = currentNode;
                                break;
                            }
                        }
                        
                        for (ControlFlowGraphNode predecessor : currentNode.getPredecessors()) {
                            if (!alreadyVisited.contains(predecessor)) {
                                frontier.add(predecessor);
                                alreadyVisited.add(predecessor);
                            }
                        }
                    }

                    if (foundNode != null) {
                        // A node has been found. Perform replacement.
                        Report.debug("Replacing: " + sourceExpression);

                        // Generate statement n: w <- sourceExpression
                        ImcTEMP temporary = new ImcTEMP(new MemTemp());
                        ImcStmt initializeStatement = new ImcMOVE( temporary, sourceExpression );
                        // Insert newly generated statement into control-flow graph
                        ControlFlowGraphNode newNode = new ControlFlowGraphNode(initializeStatement);
                        graph.insertBefore(foundNode, newNode);
                        Report.debug("  * Inserted new statement: " + initializeStatement);
                        
                        // Modify found statement n': v <- w
                        foundNode.statement = foundNode.statement.accept(new StatementReplacer(), new Replacement(sourceExpression, temporary, true));
                        Report.debug("  * Modified statement after: " + foundNode.statement);

                        // Modify this node statement s': t <- w
                        node.statement = node.statement.accept(new StatementReplacer(), new Replacement(sourceExpression, temporary, true));
                        Report.debug("  * Modified statement after: " + node.statement);

                        hasGraphChanged = true;
                        hasGraphChangedAtLeastOnce = true;
                        
                        // Because the graph has changed, liveness analysis and
                        // available expression analysis must be performed again.
                        break;
                    }

                }

                if (hasGraphChanged)
                    break;
                
            }

        } while (hasGraphChanged);
        return hasGraphChangedAtLeastOnce;
    }

}