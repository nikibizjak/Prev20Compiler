package prev.phase.livean;

import prev.data.mem.*;
import prev.data.asm.*;
import prev.phase.*;
import prev.phase.asmgen.*;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;

/**
 * Liveness analysis.
 */
public class LiveAn extends Phase {

	public LiveAn() {
		super("livean");
	}

	public void analysis() {
		for (Code code : AsmGen.codes) {
			analysis(code);
		}
	}
	
	public void analysis(Code code) {

		// Construct a map of label -> next instruction, so we can use this to
		// get the successors of jump instructions
		HashMap<MemLabel, AsmInstr> labelSuccessors = new HashMap<MemLabel, AsmInstr>();
		Vector<AsmInstr> instructions = code.instrs;
		for (int i = 0; i < instructions.size(); i++) {
			AsmInstr instruction = instructions.get(i);
			
			// Set the in and out sets to empty sets
			((AsmOPER) instruction).removeAllFromIn();
			((AsmOPER) instruction).removeAllFromOut();

			if (instruction instanceof AsmLABEL && (i + 1) < instructions.size()) {
				labelSuccessors.put(((AsmLABEL) instruction).label, instructions.get(i + 1));
			}
		}

		// The hasChanged variable will start with value of false. If for
		// any of the instructions, the sets will change, it will be set to
		// true, which will signal our function to perform another
		// iteration.
		boolean hasChanged = false;
		do {
			hasChanged = false;
			for (int i = 0; i < instructions.size(); i++) {
				AsmInstr instruction = instructions.get(i);

				HashSet oldIn = instruction.in();
				HashSet oldOut = instruction.out();

				// COMPUTE THE NEW IN SET
				// in(n)  = use(n) union [ out(n) minus def(n) ]
				HashSet<MemTemp> newIn = instruction.out();
				newIn.removeAll(instruction.defs());
				newIn.addAll(instruction.uses());
				instruction.addInTemps(newIn);
				
				// COMPUTE THE NEW OUT SET
				// out(n) = union_{ n' = naslednik n-ja } [ in(n') ]
				HashSet<MemTemp> newOut = new HashSet<MemTemp>();
				if (instruction.jumps().isEmpty() && (i + 1) < instructions.size()) {
					// The instruction i only has one successor, the next
					// instruction at index i + 1
					AsmInstr successor = instructions.get(i + 1);
					newOut.addAll(successor.in());
				} else {
					for (MemLabel label : instruction.jumps()) {
						if (!labelSuccessors.containsKey(label))
							continue;
						AsmInstr successor = labelSuccessors.get(label);
						newOut.addAll(successor.in());
					}
				}
				instruction.addOutTemp(newOut);

				// There was a change for this specific instruction. If
				// the hasChanged variable is not already set to true,
				// set it now.
				hasChanged = hasChanged || !(oldIn.equals(newIn) && oldOut.equals(newOut));
			}
		} while (hasChanged);

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
				logger.addAttribute("code", instr.toString());
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
