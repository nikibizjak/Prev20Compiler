package prev.phase.optimisation.common.available_expressions;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.common.report.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

/**
 * Reaching definitions analysis.
 */
public class AvailableExpressionsAnalysis {

	public static void run(ControlFlowGraph graph) {
		runAnalysis(graph);
	}

	private static HashSet<ImcExpr> generates(ControlFlowGraphNode node, HashSet<ImcExpr> allExpressions) {
		// In reaching definitions analysis, only two expressions actually
		// generate available expressions:
		//   * MOVE(TEMP(...), BINOP(..., b, c))
		//       generates { BINOP(..., b, c) } - kills(node)
		//   * MOVE(TEMP(...), MEM(...))
		//       generates { MEM(...) } - kills(node)
		ImcStmt statement = node.statement;
		HashSet<ImcExpr> generates = new HashSet<ImcExpr>();
		if (statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP) {
			if (((ImcMOVE) statement).src instanceof ImcBINOP) {
				generates.add(((ImcMOVE) statement).src);
				generates.removeAll(kills(node, allExpressions));
			} else if (((ImcMOVE) statement).src instanceof ImcMEM) {
				generates.add(((ImcMOVE) statement).src);
				generates.removeAll(kills(node, allExpressions));
			}
		}
		return generates;
	}

	public static HashSet<ImcExpr> containingTemporary(HashSet<ImcExpr> expressions, ImcTEMP temporary) {
		// All expressions in expressions HashSet are on of the following three
		// possible ImcExpr:
		//   * BINOP(..., b, c)
		//   * UNOP(..., b)
		//   * MEM(b)
		HashSet<ImcExpr> containingTemporary = new HashSet<ImcExpr>();
		for (ImcExpr expression : expressions) {
			HashSet<ImcTEMP> containingTemporaries = TemporaryFinder.getTemporaries(expression);
			if (containingTemporaries.contains(temporary))
				containingTemporary.add(expression);
		}
		
		return containingTemporary;
	}

	public static HashSet<ImcExpr> containingMemoryOperations(HashSet<ImcExpr> expressions) {
		HashSet<ImcExpr> containingMemoryOperations = new HashSet<ImcExpr>();
		for (ImcExpr expression : expressions) {
			HashSet<ImcMEM> memoryOperations = MemoryOperationsFinder.getMemoryOperations(expression);
			if (memoryOperations.size() > 0)
				containingMemoryOperations.add(expression);
		}
		return containingMemoryOperations;
	}

	public static HashSet<ImcExpr> kills(ControlFlowGraphNode node, HashSet<ImcExpr> allExpressions) throws Report.Error {
		ImcStmt statement = node.statement;
		HashSet<ImcExpr> kills = new HashSet<ImcExpr>();

		if (statement instanceof ImcCJUMP || statement instanceof ImcJUMP || statement instanceof ImcLABEL) {
			// The simplest form - CJUMP, JUMP and LABEL kill nothing
			return kills;
		} else if (statement instanceof ImcMOVE) {
			ImcMOVE moveStatement = (ImcMOVE) statement;
			if (moveStatement.dst instanceof ImcTEMP) {
				if (moveStatement.src instanceof ImcBINOP) {
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				} else if (moveStatement.src instanceof ImcMEM) {
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				} else if (moveStatement.src instanceof ImcCALL) {
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					kills.addAll(containingMemoryOperations(allExpressions));
					return kills;
				} else if (moveStatement.src instanceof ImcTEMP) {
					// This is a copy statement, don't throw an exception
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				} else if (moveStatement.src instanceof ImcCONST) {
					// This is a t <- const statement, don't throw an exception
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				} else if (moveStatement.src instanceof ImcNAME) {
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				} else if (moveStatement.src instanceof ImcUNOP) {
					kills.addAll(containingTemporary(allExpressions, (ImcTEMP) moveStatement.dst));
					return kills;
				}
				throw new Report.Error("Invalid expression " + moveStatement + ", expected: t <- b + c, t <- M[b] or t <- f(a_1, ..., a_n)");
			} else if (moveStatement.dst instanceof ImcMEM) {
				if (moveStatement.src instanceof ImcTEMP) {
					kills.addAll(containingMemoryOperations(allExpressions));
					return kills;
				} else if (moveStatement.src instanceof ImcCONST) {
					kills.addAll(containingMemoryOperations(allExpressions));
					return kills;
				}
				throw new Report.Error("Invalid expression " + moveStatement + ", expected: M[a] <- b");
			}
			throw new Report.Error("Invalid expression " + moveStatement + ", expected: t <- b + c, t <- M[b], M[a] <- b or t <- f(a_1, ..., a_n)");
		} else if (statement instanceof ImcESTMT) {
			// Function call CALL kills expressions of the form M[x]
			ImcESTMT expressionStatement = (ImcESTMT) statement;
			if (expressionStatement.expr instanceof ImcCALL) {
				kills.addAll(containingMemoryOperations(allExpressions));
				return kills;
			}
			throw new Report.Error("Invalid expression: " + expressionStatement + ", expected: f(a_1, ..., a_n)");
		}
		throw new Report.Error("Invalid expression: " + statement);
	}

	private static HashSet<ImcExpr> getAllExpressions(ControlFlowGraph graph) {
		HashSet<ImcExpr> allExpressions = new HashSet<ImcExpr>();
		for (ControlFlowGraphNode node : graph.nodes) {
			ImcStmt statement = node.statement;
			if (!(statement instanceof ImcMOVE))
				continue;
			
			ImcMOVE moveStatement = (ImcMOVE) statement;
			
			if (!(moveStatement.src instanceof ImcBINOP) && !(moveStatement.src instanceof ImcMEM) && !(moveStatement.src instanceof ImcUNOP))
				continue;
			
			allExpressions.add(moveStatement.src);
		}
		return allExpressions;
	}

	private static void runAnalysis(ControlFlowGraph graph) {
		HashSet<ImcExpr> allExpressions = getAllExpressions(graph);

		// All other in and out sets should contain ALL possible expressions
		// because the algorithm works using the intersection operator.
		for (ControlFlowGraphNode node : graph.nodes) {
			node.setAvailableExpressionsIn(allExpressions);
			node.setAvailableExpressionsOut(allExpressions);
		}

		// Define the in set of the initial node as empty
		graph.initialNode().setAvailableExpressionsIn(new HashSet<ImcExpr>());

		boolean hasChanged = false;
		do {
			hasChanged = false;

			for (ControlFlowGraphNode node : graph.nodes) {
				
				HashSet<ImcExpr> oldIn = node.getAvailableExpressionsIn();
				HashSet<ImcExpr> oldOut = node.getAvailableExpressionsOut();

				HashSet<ImcExpr> newIn = null;
				for (ControlFlowGraphNode predecessor : node.getPredecessors()) {
					if (newIn == null) {
						newIn = predecessor.getAvailableExpressionsOut();
						continue;
					}
					newIn.retainAll(predecessor.getAvailableExpressionsOut());
				}
				if (newIn == null)
					newIn = new HashSet<ImcExpr>();

				HashSet<ImcExpr> newOut = new HashSet<ImcExpr>(oldIn);
				newOut.removeAll(kills(node, allExpressions));
				newOut.addAll(generates(node, allExpressions));

				node.setAvailableExpressionsIn(newIn);
				node.setAvailableExpressionsOut(newOut);

				hasChanged = hasChanged || !oldIn.equals(newIn) || !oldOut.equals(newOut);

			}

		} while (hasChanged);
	}

}
