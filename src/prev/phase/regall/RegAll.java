package prev.phase.regall;

import java.util.*;

import prev.data.mem.*;
import prev.Compiler;
import prev.data.asm.*;
import prev.phase.*;
import prev.phase.asmgen.*;
import prev.phase.livean.*;
import prev.data.semtype.*;

/**
 * Register allocation.
 */
public class RegAll extends Phase {
	
	/** Mapping of temporary variables to registers. */
	public final HashMap<MemTemp, Integer> tempToReg = new HashMap<MemTemp, Integer>();

	LiveAn livenessAnalysis;

	public RegAll() {
		super("regall");
		livenessAnalysis = new LiveAn();
	}

	/**
	 * Construct the interference graph between instructions
	 * @param code
	 * @return
	 */
	private Graph build(Code code) {
		Graph graph = new Graph();

		// First, perform liveness analysis on received code
		livenessAnalysis.analysis(code);

		// Then, add all defined and used temporaries to interference graph
		for (AsmInstr instruction : code.instrs) {
			for (MemTemp use : instruction.uses())
				graph.addNode(use);
			for (MemTemp defines : instruction.defs())
				graph.addNode(defines);
		}

		// Temporary that is defined in instruction interferes with all
		// temporaries in the out set
		for (AsmInstr instruction : code.instrs)
			for (MemTemp definedTemporary : instruction.defs())
				for (MemTemp outTemporary : instruction.out())
					graph.addEdge(definedTemporary, outTemporary);
		
		// The frame pointer (FP) doesn't need to be coloured, so remove it from
		// the graph
		graph.removeNode(code.frame.FP);

		return graph;
	}

	private boolean simplify(Graph interferenceGraph, Stack<Node> stack) {
		// Find a node with degree < Compiler.numberOfRegisters
		for (Node node : interferenceGraph.nodes()) {
			if (node.degree() < Compiler.numberOfRegisters) {
				interferenceGraph.removeNode(node.temporary);
				stack.push(node);
				return true;
			}
		}
		return false;
	}

	private Node spill(Graph interferenceGraph, Stack<Node> stack) {
		int maximumDegree = Compiler.numberOfRegisters;
		Node selectedNode = null;

		for (Node node : interferenceGraph.nodes()) {
			if (node.degree() >= maximumDegree) {
				maximumDegree = node.degree();
				selectedNode = node;
			}
		}

		if (selectedNode == null)
			return selectedNode;
		
		selectedNode.potentialSpill = true;
		interferenceGraph.removeNode(selectedNode.temporary);
		stack.push(selectedNode);
		return selectedNode;
	}

	private void coalesce() {}
	private void freeze() {}

	private Vector<MemTemp> select(Graph reconstructedGraph, HashMap<Node, HashSet<Node>> edges, Stack<Node> stack) {

		Vector<MemTemp> spills = new Vector<MemTemp>();

		boolean coloringFound = true;

		while (!stack.isEmpty()) {
			Node currentNode = stack.pop();

			HashSet<Node> neighbours = reconstructedGraph.nodes();
			neighbours.retainAll(edges.get(currentNode));

			boolean isPossibleColour[] = new boolean[Compiler.numberOfRegisters];
			for (int i = 0; i < isPossibleColour.length; i++)
				isPossibleColour[i] = true;

			reconstructedGraph.addNode(currentNode);
			for (Node neighbour : neighbours) {
				reconstructedGraph.addEdge(currentNode, neighbour);
				if (neighbour.color >= 0)
					isPossibleColour[neighbour.color] = false;
			}

			for (int i = 0; i < isPossibleColour.length; i++) {
				if (isPossibleColour[i]) {
					currentNode.color = i;
					break;
				}
			}

			if (currentNode.color < 0) {
				// No color has been found
				currentNode.actualSpill = true;
				coloringFound = false;
				spills.add(currentNode.temporary);
			}

		}

		return spills;
	}

	private void modifyCode(Code code, Vector<MemTemp> spills) {
		for (MemTemp spill : spills) {
			// A modified set of instructions that we are building.
			Vector<AsmInstr> modifiedInstructions = new Vector<AsmInstr>();
			
			// Compute the offset of this spilled temporary. Each temporary is a
			// register with 8 bytes, so we can simply update code.tempSize by 8.
			// But only compute offset for codes that actually contain
			// instruction that uses or defines spilled temporary.
			long pointerSize = new SemPointer(new SemVoid()).size();
			int overflows = (int) (code.tempSize / pointerSize);
			code.tempSize += pointerSize;
			long offset = -code.frame.locsSize - 2 * pointerSize - overflows * pointerSize - pointerSize;

			for (AsmInstr instruction : code.instrs) {
				boolean usesSpilledTemporary = instruction.uses().contains(spill);
				boolean definesSpilledTemporary = instruction.defs().contains(spill);

				if (usesSpilledTemporary || definesSpilledTemporary) {
					if (usesSpilledTemporary) {
						// Each time an instruction uses the spilled variable,
						// the variable value must first be loaded from memory.
						MemTemp resultTemporary = new MemTemp();

						// We also need to add a new temporary for offset. To
						// load the offset value, we can use the method
						// asmgen.ExprGenerator.loadConstant(MemTemp temporary, long value).
						MemTemp offsetTemporary = new MemTemp();
						Vector<AsmInstr> loadConstantInstructions = ExprGenerator.loadConstant(offsetTemporary, offset);

						// Load the value from memory. This function defines the
						// result temporary and uses the offsetTemporary to
						// compute the offset.
						Vector<MemTemp> definesLoad = new Vector<MemTemp>();
						definesLoad.add(resultTemporary);
						Vector<MemTemp> usesLoad = new Vector<MemTemp>();
						usesLoad.add(offsetTemporary);
						AsmInstr loadInstruction = new AsmOPER("LDO `d0,FP,`s0", usesLoad, definesLoad, null);

						// Replace all occurences of temporary in uses
						Vector<MemTemp> uses = new Vector<MemTemp>();
						for (MemTemp usedTemporary : instruction.uses()) {
							if (usedTemporary == spill) {
								uses.add(resultTemporary);
							} else {
								uses.add(usedTemporary);
							}
						}

						// Construct a new instruction that uses new temporary
						// instruction instead of the original one
						AsmInstr newInstruction = new AsmOPER(((AsmOPER) instruction).instr(), uses, instruction.defs(), instruction.jumps());

						modifiedInstructions.addAll(loadConstantInstructions);
						modifiedInstructions.add(loadInstruction);
						modifiedInstructions.add(newInstruction);

					}
					if (definesSpilledTemporary) {
						// Similarly, if the instruction defines spilled
						// temporary value, we must first calculate the
						// value and then save the value to the memory.
						MemTemp resultTemporary = new MemTemp();

						Vector<MemTemp> defines = new Vector<MemTemp>();
						defines.add(resultTemporary);
						AsmInstr newInstruction = new AsmOPER(((AsmOPER) instruction).instr(), instruction.uses(), defines, instruction.jumps());

						// We also need to add a new temporary for offset. To
						// load the offset value, we can use the method
						// asmgen.ExprGenerator.loadConstant(MemTemp temporary, long value).
						MemTemp offsetTemporary = new MemTemp();
						Vector<AsmInstr> loadConstantInstructions = ExprGenerator.loadConstant(offsetTemporary, offset);

						Vector<MemTemp> usesStore = new Vector<MemTemp>();
						usesStore.add(resultTemporary);
						usesStore.add(offsetTemporary);
						AsmInstr storeInstruction = new AsmOPER("STO `s0,FP,`s1", usesStore, null, null);

						modifiedInstructions.add(newInstruction);
						modifiedInstructions.addAll(loadConstantInstructions);
						modifiedInstructions.add(storeInstruction);

					} 
				} else {
					modifiedInstructions.add(instruction);
				}
			}

			// If the offset is not null after we have visited all
			// instructions, that means we have defined or redefined a
			// temporary in this code. Update its instructions.
			code.instrs.clear();
			code.instrs.addAll(modifiedInstructions);
		}
	}

	public void allocate() {

		for (Code code : AsmGen.codes) {
			
			boolean coloringFound;
			Graph reconstructedGraph = null;
			do {
				
				// STEP 1: BUILD INTERFERENCE GRAPH
				Graph interferenceGraph = this.build(code);
				HashMap<Node, HashSet<Node>> edges = interferenceGraph.edges();
				
				Stack<Node> stack = new Stack<Node>();
				do {
					// STEP 2: PERFORM ONE STEP OF SIMPLIFICATION
					boolean hasChanged;
					do {
						hasChanged = this.simplify(interferenceGraph, stack);
					} while (hasChanged);

					// STEP 3: SPILL
					Node removedNode = this.spill(interferenceGraph, stack);
				} while (!interferenceGraph.isEmpty());

				// STEP 4: SELECT - GRAPH COLORING
				reconstructedGraph = new Graph();
				Vector<MemTemp> spills = this.select(reconstructedGraph, edges, stack);
				coloringFound = spills.size() == 0;

				if (coloringFound)
					break;
				
				// Coloring has not yet been found, the code must be modified
				// STEP 5: MODIFY THE CODE
				this.modifyCode(code, spills);

			} while (!coloringFound);

			// After coloring has been found, actually use register numbers to
			// assign registers. The frame pointer was not present in the graph
			// and is precolored to value 253.
			this.tempToReg.put(code.frame.FP, 253);
			for (Node node : reconstructedGraph.nodes()) {
				this.tempToReg.put(node.temporary, node.color);
			}

		}

	}
	
	public void log() {
		if (logger == null)
			return;
		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("entrylabel", code.entryLabel.name);
			logger.addAttribute("exitlabel", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));
			code.frame.log(logger);
			logger.begElement("instructions");
			for (AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString(tempToReg));
				logger.begElement("temps");
				logger.addAttribute("name", "use");
				for (MemTemp temp : instr.uses()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "def");
				for (MemTemp temp : instr.defs()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "in");
				for (MemTemp temp : instr.in()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "out");
				for (MemTemp temp : instr.out()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
