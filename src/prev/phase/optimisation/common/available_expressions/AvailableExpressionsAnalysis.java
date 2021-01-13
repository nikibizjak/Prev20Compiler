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

	private static HashSet<ImcExpr> generates(ControlFlowGraphNode node) {
		// In reaching definitions analysis, only two expressions actually
		// generate available expressions:
		//   * MOVE(TEMP(...), BINOP(..., b, c))
		//       generates { BINOP(..., b, c) } - kills(node)
		//   * MOVE(TEMP(...), MEM(...))
		//       generates { MEM(...) } - kills(node)
		ImcStmt statement = node.statement;
		HashSet<ImcExpr> generates = new HashSet<ImcExpr>();
		if (statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP) {
			if (((ImcMOVE) statement).src instanceof ImcBINOP)
				generates.add(((ImcMOVE) statement).src);
			else if (((ImcMOVE) statement).src instanceof ImcMEM)
				generates.add(((ImcMOVE) statement).src);
		}
		return generates;
	}

	public static HashSet<ImcExpr> kills(ControlFlowGraphNode node) throws Report.Error {
		ImcStmt statement = node.statement;
		HashSet<ImcExpr> kills = new HashSet<ImcExpr>();

		if (statement instanceof ImcCJUMP || statement instanceof ImcJUMP || statement instanceof ImcLABEL) {
			// The simplest form - CJUMP, JUMP and LABEL kill nothing
			return kills;
		} else if (statement instanceof ImcMOVE) {
			ImcMOVE moveStatement = (ImcMOVE) statement;
			if (moveStatement.dst instanceof ImcTEMP) {
				if (moveStatement.src instanceof ImcBINOP) {
					return kills;
				} else if (moveStatement.src instanceof ImcMEM) {
					return kills;
				} else if (moveStatement.src instanceof ImcCALL) {
					return kills;
				} else if (moveStatement.src instanceof ImcTEMP) {
					// This is a copy statement, don't throw an exception
					return kills;
				} else if (moveStatement.src instanceof ImcCONST) {
					// This is a t <- const statement, don't throw an exception
					return kills;
				}
				throw new Report.Error("Invalid expression " + moveStatement + ", expected: t <- b + c, t <- M[b] or t <- f(a_1, ..., a_n)");
			} else if (moveStatement.dst instanceof ImcMEM) {
				if (moveStatement.src instanceof ImcTEMP) {
					return kills;
				}
				throw new Report.Error("Invalid expression " + moveStatement + ", expected: M[a] <- b");
			}
			throw new Report.Error("Invalid expression " + moveStatement + ", expected: t <- b + c, t <- M[b], M[a] <- b or t <- f(a_1, ..., a_n)");
		} else if (statement instanceof ImcESTMT) {
			// Function call CALL kills expressions of the form M[x]
			ImcESTMT expressionStatement = (ImcESTMT) statement;
			if (expressionStatement.expr instanceof ImcCALL) {
				return kills;
			}
			throw new Report.Error("Invalid expression: " + expressionStatement + ", expected: f(a_1, ..., a_n)");
		}
		throw new Report.Error("Invalid expression: " + statement);
	}

	private static void runAnalysis(ControlFlowGraph graph) {
		for (ControlFlowGraphNode node : graph.nodes) {
			try {
				kills(node);
			} catch (Report.Error e) {
				System.out.println(e);
			}
		}		
	}

}
