package prev.phase.optimisation.common.reaching_definitions;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.lin.*;

/**
 * Reaching definitions analysis.
 */
public class ReachingDefinitionsAnalysis {

	public static void run(ControlFlowGraph graph) {
		runAnalysis(graph);
	}

	public static HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions(ControlFlowGraph graph) {
		HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = new HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>>();
		for (ControlFlowGraphNode node : graph.nodes) {
			ImcStmt statement = node.statement;
			if (statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP) {
				ImcTEMP definedTemporary = (ImcTEMP) ((ImcMOVE) statement).dst;
				if (definitions.containsKey(definedTemporary)) {
					definitions.get(definedTemporary).add(node);
				} else {
					HashSet<ControlFlowGraphNode> newDefinitions = new HashSet<ControlFlowGraphNode>();
					newDefinitions.add(node);
					definitions.put(definedTemporary, newDefinitions);
				}
			}
		}
		return definitions;
	}

	private static HashSet<ControlFlowGraphNode> generates(ControlFlowGraphNode node) {
		ImcStmt statement = node.statement;
		HashSet<ControlFlowGraphNode> generates = new HashSet<>();
		if (statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP) {
			generates.add(node);
		}
		return generates;
	}

	public static HashSet<ControlFlowGraphNode> kills(ControlFlowGraphNode node, HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions) {
		HashSet<ControlFlowGraphNode> kills = new HashSet<ControlFlowGraphNode>();
		ImcStmt statement = node.statement;
		if (statement instanceof ImcMOVE && ((ImcMOVE) statement).dst instanceof ImcTEMP) {
			ImcTEMP definedTemporary = (ImcTEMP) ((ImcMOVE) statement).dst;
			HashSet<ControlFlowGraphNode> temporaryDefinitions = definitions.get(definedTemporary);
			if (temporaryDefinitions != null) {
				kills.addAll(temporaryDefinitions);
				kills.remove(node);
			}
		}
		return kills;
	}

	private static void runAnalysis(ControlFlowGraph graph) {

		HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = definitions(graph);
		HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>> generates = new HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>>();
		HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>> kills = new HashMap<ControlFlowGraphNode, HashSet<ControlFlowGraphNode>>();
		
		for (ControlFlowGraphNode node : graph.nodes) {
			generates.put(node, ReachingDefinitionsAnalysis.generates(node));
			kills.put(node, ReachingDefinitionsAnalysis.kills(node, definitions));
			node.setReachingDefinitionsIn(new HashSet<ControlFlowGraphNode>());
			node.setReachingDefinitionsOut(new HashSet<ControlFlowGraphNode>());
		}

		boolean hasChanged = false;
		do {
			hasChanged = false;

			for (ControlFlowGraphNode node : graph.nodes) {
				HashSet<ControlFlowGraphNode> oldIn = node.getReachingDefinitionsIn();
				HashSet<ControlFlowGraphNode> oldOut = node.getReachingDefinitionsOut();

				HashSet<ControlFlowGraphNode> newIn = new HashSet<ControlFlowGraphNode>();
				for (ControlFlowGraphNode predecessor : ((HashSet<ControlFlowGraphNode>) node.getPredecessors())) {
					newIn.addAll(predecessor.getReachingDefinitionsOut());
				}

				HashSet<ControlFlowGraphNode> newOut = new HashSet<ControlFlowGraphNode>();
				newOut.addAll(oldIn);
				newOut.removeAll(kills.get(node));
				newOut.addAll(generates.get(node));

				hasChanged = hasChanged || (!newIn.equals(oldIn)) || (!newOut.equals(oldOut));

				node.setReachingDefinitionsIn(newIn);
				node.setReachingDefinitionsOut(newOut);
			}

		} while (hasChanged);
		
	}

}
