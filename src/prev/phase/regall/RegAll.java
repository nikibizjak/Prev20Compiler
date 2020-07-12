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

	LiveAn livean;

	public RegAll() {
		super("regall");
		livean = new LiveAn();
	}

	private HashMap<MemTemp, HashSet<MemTemp>> copyInterferenceGraph(HashMap<MemTemp, HashSet<MemTemp>> interferenceGraph) {
		HashMap<MemTemp, HashSet<MemTemp>> newInterferenceGraph = new HashMap<MemTemp, HashSet<MemTemp>>();
		for (MemTemp temporary : interferenceGraph.keySet()) {
			HashSet<MemTemp> mappings = new HashSet<MemTemp>(interferenceGraph.get(temporary));
			newInterferenceGraph.put(temporary, mappings);
		}
		return newInterferenceGraph;
	}

	public HashMap<MemTemp, HashSet<MemTemp>> buildInterferenceGraph(Code code) {
		HashMap<MemTemp, HashSet<MemTemp>> interferenceGraph = new HashMap<MemTemp, HashSet<MemTemp>>();

		// Re-run liveness analysis because the code might have been changed
		livean.analysis(code);

		for (AsmInstr instruction : code.instrs) {
			for (MemTemp temporary : instruction.defs())
				interferenceGraph.put(temporary, new HashSet<MemTemp>());
			for (MemTemp temporary : instruction.uses())
				interferenceGraph.put(temporary, new HashSet<MemTemp>());
		}

		for (AsmInstr instruction : code.instrs) {
			HashSet<MemTemp> outTemporaries = instruction.out();

			for (MemTemp temporary : outTemporaries) {
				if (!interferenceGraph.containsKey(temporary))
					interferenceGraph.put(temporary, new HashSet<MemTemp>());
				
				HashSet<MemTemp> set = interferenceGraph.get(temporary);
				set.addAll(outTemporaries);
			}
		}

		for (MemTemp temporary : interferenceGraph.keySet()) {
			interferenceGraph.get(temporary).remove(temporary);
			interferenceGraph.get(temporary).remove(code.frame.FP);
		}

		// We don't want to color FP, so we remove it from graph now
		interferenceGraph.remove(code.frame.FP);

		return interferenceGraph;
	}

	public boolean simplify(HashMap<MemTemp, HashSet<MemTemp>> interferenceGraph, Stack<MemTemp> temporaryStack) {
		MemTemp selectedTemporary = null;
		for (MemTemp temporary : interferenceGraph.keySet()) {
			HashSet<MemTemp> neighbourhood = interferenceGraph.get(temporary);
			if (neighbourhood.size() < Compiler.numberOfRegisters) {
				// Choose this temporary
				selectedTemporary = temporary;
				break;
			}
		}

		if (selectedTemporary == null) {
			// There is no node that has degree < R
			return false;
		} else {
			// There is a node that has the degree < R
			temporaryStack.push(selectedTemporary);
			
			HashSet<MemTemp> neighbourhood = interferenceGraph.get(selectedTemporary);
			// Remove all connections between selectedTemporary -> t'
			interferenceGraph.remove(selectedTemporary);
			// Remove all inverse connections between t' -> selectedTemporary
			for (MemTemp neighbour : neighbourhood) {
				interferenceGraph.get(neighbour).remove(selectedTemporary);
			}
		}
		
		return true;
	}

	public MemTemp spill(HashMap<MemTemp, HashSet<MemTemp>> interferenceGraph, Stack<MemTemp> temporaryStack) {
		// Select a node for spilling, it can be any node that has degree > R
		MemTemp selectedTemporary = null;
		for (MemTemp temporary : interferenceGraph.keySet()) {
			HashSet<MemTemp> neighbourhood = interferenceGraph.get(temporary);
			if (neighbourhood.size() >= Compiler.numberOfRegisters) {
				// Choose this temporary
				selectedTemporary = temporary;
				break;
			}
		}

		temporaryStack.push(selectedTemporary);
		
		HashSet<MemTemp> neighbourhood = interferenceGraph.get(selectedTemporary);
		// Remove all connections between selectedTemporary -> t'
		interferenceGraph.remove(selectedTemporary);
		// Remove all inverse connections between t' -> selectedTemporary
		for (MemTemp neighbour : neighbourhood) {
			interferenceGraph.get(neighbour).remove(selectedTemporary);
		}
		
		return selectedTemporary;
	}

	public Vector<MemTemp> select(Code code, Stack<MemTemp> temporaryStack, HashMap<MemTemp, HashSet<MemTemp>> neighbours) {
		Vector<MemTemp> spills = new Vector<MemTemp>();

		// Set of current nodes (temporaries) in graph
		HashSet<MemTemp> temporariesInGraph = new HashSet<MemTemp>();

		// Pre-color the frame pointer
		tempToReg.put(code.frame.FP, Integer.valueOf(253));

		while (!temporaryStack.empty()) {
			MemTemp currentTemporary = temporaryStack.pop();

			// Set of POSSIBLE NEIGHBOURS, not all neighbours are in the graph yet
			HashSet<MemTemp> possibleNeighbours = neighbours.get(currentTemporary);
			// Set of neighbours that are currently in the graph
			Set<MemTemp> currentNeighbours = new HashSet<MemTemp>(temporariesInGraph);
			currentNeighbours.retainAll(possibleNeighbours);

			// Try to colour this node with each possible colour [0,
			// numberOfRegisters). If we can select a colour, then colour it and
			// continue. Otherwise, this is a spill.
			boolean occupiedColors[] = new boolean[Compiler.numberOfRegisters];
			for (MemTemp neighbour : currentNeighbours) {
				int neighbourColor = tempToReg.get(neighbour).intValue();
				if (neighbourColor >= 0 && neighbourColor < Compiler.numberOfRegisters)
					occupiedColors[neighbourColor] = true;
			}

			int color = -1;
			for (int i = 0; i < Compiler.numberOfRegisters; i++) {
				if (!occupiedColors[i]) {
					color = i;
					break;
				}
			}

			// If no color can be assigned to current temporary, that means that
			// the temporary can not be colored. The code must be modified.
			if (color < 0) {
				spills.add(currentTemporary);
				continue;
			}
			
			tempToReg.put(currentTemporary, Integer.valueOf(color));
			temporariesInGraph.add(currentTemporary);
		}

		return spills;
	}

	public void modifyCode(Code code, Vector<MemTemp> spills) {
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

			boolean coloringFound = true;
			do {
				Stack<MemTemp> temporaryStack = new Stack<MemTemp>();

				// STEP 1: BUILD INTERFERENCE GRAPH
				HashMap<MemTemp, HashSet<MemTemp>> originalInterferenceGraph = buildInterferenceGraph(code);
				HashMap<MemTemp, HashSet<MemTemp>> interferenceGraph = copyInterferenceGraph(originalInterferenceGraph);
				
				Vector<MemTemp> potentialSpills = new Vector<MemTemp>();

				while (!interferenceGraph.isEmpty()) {
					// STEP 2: SIMPLIFY
					boolean repeatSimplification;
					do {
						repeatSimplification = simplify(interferenceGraph, temporaryStack);
					} while (repeatSimplification);

					if (interferenceGraph.isEmpty())
						break;

					// STEP 3: SPILL
					MemTemp spilledTemporary = spill(interferenceGraph, temporaryStack);
					potentialSpills.add(spilledTemporary);
				}

				// STEP 4: SELECT
				Vector<MemTemp> spills = select(code, temporaryStack, originalInterferenceGraph);
				coloringFound = spills.size() <= 0;
				if (coloringFound)
					break;

				// STEP 5: MODIFYING CODE
				// Each time the temporary Tn is defined (or redefined), we save it
				// to our function frame. Each time it is used, we restore its value
				// from memory.
				modifyCode(code, spills);

			} while(!coloringFound);

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
