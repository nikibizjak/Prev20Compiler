package prev.phase.livean;

import java.util.HashMap;
import java.util.HashSet;

import prev.data.asm.AsmInstr;
import prev.data.asm.AsmLABEL;
import prev.data.asm.AsmOPER;
import prev.data.asm.Code;
import prev.data.mem.MemLabel;
import prev.data.mem.MemTemp;
import prev.phase.Phase;
import prev.phase.asmgen.AsmGen;

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
		HashMap<MemLabel, Integer> successors = new HashMap<MemLabel, Integer>();
		for (int i = 0; i < code.instrs.size(); i++) {
			AsmInstr instruction = code.instrs.get(i);
			if (instruction instanceof AsmLABEL) {
				successors.put(((AsmLABEL) instruction).label, i);
			}
		}
		
		for (int i = 0; i < code.instrs.size(); i++) {
			AsmInstr instr = code.instrs.get(i);
			if (!(instr instanceof AsmOPER))
				continue;
			AsmOPER instruction = (AsmOPER) instr;
			instruction.removeAllFromIn();
			instruction.removeAllFromOut();
		}

		boolean repeat;
		do {
			repeat = false;

			for (int i = code.instrs.size() - 1; i >= 0; i--) {
				AsmInstr instr = code.instrs.get(i);
				if (!(instr instanceof AsmOPER))
					continue;
				AsmOPER instruction = (AsmOPER) instr;

				HashSet<MemTemp> previousIn = instruction.in();
				HashSet<MemTemp> previousOut = instruction.out();

				// Compute the new out set
				HashSet<MemTemp> newOut = new HashSet<MemTemp>();
				if (instr.jumps().size() < 1) {
					if (i + 1 < code.instrs.size()) {
						// There is only one direct successor (the i+1 instruction)
						newOut.addAll(code.instrs.get(i + 1).in());
					}
				} else {
					// There are multiple possible successors
					for (MemLabel label : instr.jumps()) {
						Integer lineNumber = successors.get(label);
						if (lineNumber == null) {
							continue;
						}
						int line = lineNumber.intValue();
						if (line < code.instrs.size()) {
							newOut.addAll(code.instrs.get(line).in());
						}
					}
				}

				instruction.addOutTemps(newOut);

				// Compute the new in set
				HashSet<MemTemp> newIn = new HashSet<MemTemp>();
				newIn.addAll(instr.out());
				newIn.removeAll(instr.defs());
				newIn.addAll(instr.uses());
				instruction.addInTemps(newIn);

				repeat = repeat || !newIn.equals(previousIn) || !newOut.equals(previousOut);

			}

		} while (repeat);

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
