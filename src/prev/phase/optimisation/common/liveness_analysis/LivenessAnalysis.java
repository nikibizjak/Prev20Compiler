package prev.phase.optimisation.common.liveness_analysis;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.data.imc.code.expr.*;
import java.util.*;

/**
 * Liveness analysis.
 */
public class LivenessAnalysis {
	
	public static void analysis(ControlFlowGraph graph) {
		// Set liveIn and liveOut for each node to empty sets
		for (ControlFlowGraphNode node : graph.nodes) {
			node.clearLiveIn();
			node.clearLiveOut();

			// Also compute definitions and uses of each node
			StmtGenerator.UsesDefinitions usesDefinitions = getUsesDefinitions(node);
			HashSet<ImcTEMP> uses = new HashSet<ImcTEMP>(usesDefinitions.uses);
			HashSet<ImcTEMP> defines = new HashSet<ImcTEMP>(usesDefinitions.definitions);
			node.setUses(uses);
			node.setDefines(defines);
		}

		boolean hasAnyChanged;
		do {

			hasAnyChanged = false;
			
			for (ControlFlowGraphNode node : graph.nodes) {
				
				HashSet<ImcTEMP> oldLiveIn = node.getLiveIn();
				HashSet<ImcTEMP> oldLiveOut = node.getLiveOut();

				// Compute new in as in[n] = use[n] U (out[n] - def[n])
				HashSet<ImcTEMP> newIn = new HashSet<ImcTEMP>();
				newIn.addAll(oldLiveOut);
				newIn.removeAll(node.getDefines());
				newIn.addAll(node.getUses());

				// Compute new out as out[n] = U_{s \in succ[n]} in[s]
				HashSet<ImcTEMP> newOut = new HashSet<ImcTEMP>();
				for (ControlFlowGraphNode successor : node.successors) {
					newOut.addAll(successor.getLiveIn());
				}

				// Store newly computed sets
				node.setLiveIn(newIn);
				node.setLiveOut(newOut);

				hasAnyChanged = hasAnyChanged || !newIn.equals(oldLiveIn) || !newOut.equals(oldLiveOut);

			}

		} while (hasAnyChanged);
	}

	public static StmtGenerator.UsesDefinitions getUsesDefinitions(ControlFlowGraphNode node) {
		Vector<ImcTEMP> definitions = new Vector<ImcTEMP>();
		Vector<ImcTEMP> uses = new Vector<ImcTEMP>();
		StmtGenerator.UsesDefinitions usesDefinitions = new StmtGenerator.UsesDefinitions(uses, definitions);
		node.statement.accept(new StmtGenerator(), usesDefinitions);
		return usesDefinitions;
	}

}
