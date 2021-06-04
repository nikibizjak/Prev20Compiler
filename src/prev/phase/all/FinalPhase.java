package prev.phase.all;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Vector;

import prev.Compiler;
import prev.data.asm.AsmInstr;
import prev.data.asm.AsmLABEL;
import prev.data.asm.AsmOPER;
import prev.data.asm.Code;
import prev.data.lin.LinDataChunk;
import prev.data.mem.MemFrame;
import prev.data.mem.MemLabel;
import prev.data.mem.MemTemp;
import prev.phase.Phase;
import prev.phase.asmgen.AsmGen;
import prev.phase.asmgen.ExprGenerator;
import prev.phase.imclin.ImcLin;

/**
 * Register allocation.
 */
public class FinalPhase extends Phase {
	
	private HashMap<MemTemp, Integer> registers;

	public FinalPhase(HashMap<MemTemp, Integer> registers) {
		super("all");
		this.registers = registers;
	}

	private Vector<String> codeToString(Code code) {
		// In this function, we convert a Code to a list of strings

		Vector<String> instructions = new Vector<String>();

		AsmLABEL lastLabel = null;
		for (AsmInstr instruction : code.instrs) {

			if (instruction instanceof AsmLABEL) {
				if (lastLabel != null) {
					// There are two labels one after another, we must add a
					// NOOP instruction. The MMIX's no operation instruction is
					// SWYM instead of NOP as one would expect... The arguments
					// to the SWYM instruction are irrelevant.
					instructions.add(lastLabel.toString(registers) + "\tSWYM 0,0,0");
				}
				lastLabel = (AsmLABEL) instruction;
			} else {
				if (lastLabel != null) {
					instructions.add(lastLabel.toString(registers) + "\t" + instruction.toString(registers));
					lastLabel = null;
				} else {
					instructions.add("\t" + instruction.toString(registers));
				}
			}

		}

		return instructions;
	}

	private void generatePrologue(Code code) {
		// This function will directly modify the code in the AsmGen.codes, so
		// it should only be called once
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// Construct a memory temporary, that will always be mapped to register
		// $0 and will be used to store loaded constants. 
		MemTemp temporary = new MemTemp();
		registers.put(temporary, 0);

		// Function entry label
		instructions.add(new AsmLABEL(code.frame.label));

		// Save old frame pointer
		long oldFramePointerOffset = code.frame.locsSize + 8;
		instructions.addAll(ExprGenerator.loadConstant(temporary, oldFramePointerOffset));
		instructions.add(new AsmOPER("SUB $0,SP,$0", null, null, null));
		instructions.add(new AsmOPER("STO FP,$0,0", null, null, null));

		// Save return address
		instructions.add(new AsmOPER("SUB $0,$0,8", null, null, null));
		instructions.add(new AsmOPER("GET $1,rJ", null, null, null));
		instructions.add(new AsmOPER("STO $1,$0,0", null, null, null));

		// Update frame pointer
		instructions.add(new AsmOPER("SET FP,SP", null, null, null));

		// Update stack pointer
		long totalFrameSize = code.frame.size + code.tempSize;
		instructions.addAll(ExprGenerator.loadConstant(temporary, totalFrameSize));
		instructions.add(new AsmOPER("SUB SP,SP,$0", null, null, null));

		// The last instruction is the JUMP to function body label
		instructions.add(new AsmOPER("JMP " + code.entryLabel.name, null, null, null));
		code.instrs.addAll(0, instructions);
	}

	private void generateEpilogue(Code code) {
		// This function will directly modify the code in the AsmGen.codes, so
		// it should only be called once
		Vector<AsmInstr> instructions = code.instrs;

		// Construct a memory temporary, that will always be mapped to register
		// $0 and will be used to store loaded constants. 
		MemTemp temporary = new MemTemp();
		registers.put(temporary, 0);

		// Add function body exit label
		instructions.add(new AsmLABEL(code.exitLabel));

		// Store return value
		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(code.frame.RV);
		instructions.add(new AsmOPER("STO `s0,FP,0", uses, null, null));

		// Set the stack pointer to current frame pointer
		instructions.add(new AsmOPER("SET SP,FP", null, null, null));

		// Load back the old frame pointer from function frame and set current
		// frame pointer to the loaded value.
		long oldFramePointerOffset = code.frame.locsSize + 8;
		instructions.addAll(ExprGenerator.loadConstant(temporary, oldFramePointerOffset));
		instructions.add(new AsmOPER("SUB $0,SP,$0", null, null, null));
		instructions.add(new AsmOPER("LDO FP,$0,0", null, null, null));
		
		// Set the special register rJ (the return address register) to the
		// actual return address, which is saved in our function frame.
		instructions.add(new AsmOPER("SUB $0,$0,8", null, null, null));
		instructions.add(new AsmOPER("LDO $0,$0,0", null, null, null));
		instructions.add(new AsmOPER("PUT rJ,$0", null, null, null));
		
		// Add the POP instruction that will tell the MMIX that we want to
		// return from function
		instructions.add(new AsmOPER("POP " + Compiler.numberOfRegisters + ",0", null, null, null));
	}

	private Vector<String> generateBootstrapRoutine() {
		Vector<String> instructions = new Vector<String>();

		// Add three global registers that can then be used instead of register
		// numbers.
		// Stack pointer = register $254
		// Frame pointer = register $253
		// Heap pointer = register $252
		// The heap pointer will be initialized after all the static variables,
		// so that the heap will start after the static data and will not
		// override anything.
		instructions.add("SP\tGREG\t#4000000000000000");
		instructions.add("FP\tGREG\t0\n");

		// After it, add the static data segment
		instructions.add("\tLOC\tData_Segment");

		// Add a base address, so variable addressing works
		instructions.add("\tGREG\t@");

		// Input buffer for reading text from input
		instructions.add("InputSize\tIS\t64");
		instructions.add("InputBuffer\tOCTA\t0");
		instructions.add("\tLOC\tInputBuffer+InputSize");
		instructions.add("BufferPosition\tOCTA\t@");
		instructions.add("InputArgs\tOCTA\tInputBuffer,InputSize\n");

		for (LinDataChunk dataChunk : ImcLin.dataChunks()) {
			// If the initial value is not null, that means that this data chunk
			// is a string. Escape the string with zero terminator. Otherwise,
			// the data chunk is a normal variable, which is dataChunk.size
			// bytes long. 
			if (dataChunk.init != null) {
				instructions.add(dataChunk.label.name + "\tOCTA\t" + dataChunk.init + ",0");
			} else {
				String instruction = dataChunk.label.name + "\tOCTA\t";
				for (int i = 0; i < dataChunk.size; i += 8) {
					if (i > 0)
						instruction += ",";
					instruction += "0";
				}
				instructions.add(instruction);
			}
		}

		// After all the data has been added, we add another global register for
		// heap. It is initialized to current location (@).
		instructions.add("HP\tGREG\t@\n");

		// Program instructions should start after the location #100
		instructions.add("\tLOC\t#100\n");

		// Add a Main label that MMIX runs first
		instructions.add("Main\tPUSHJ\t$" + Compiler.numberOfRegisters + ",_main");

		// After the _main function is called, load the result to register $255
		// which is used for return values. After the Halt subroutine will be
		// called, the program will halt with return value = value of register $255.
		instructions.add("\tLDO\t$255,$254,0");

		// Add exit instruction to our program
		instructions.add("\tTRAP\t0,Halt,0\n");

		return instructions;
	}

	private Vector<String> generatePutCharFunction() {

		MemFrame putCharFrame = new MemFrame(new MemLabel("putChar"), 0, 0, 0);
		MemLabel entryLabel = new MemLabel();
		MemLabel exitLabel = new MemLabel();
		// To output a character, we first need to allocate 2 bytes of memory
		// for the character and string terminator 0. Then we can use system
		// call to TRAP 0,Fputs,StdOut. Instead of allocating memory, we can
		// override the next octa in our stack frame and then restore it.
		Vector<AsmInstr> putCharInstructions = new Vector<AsmInstr>();
		// Load the next 64 bits from memory to register $0, this will be used
		// to restore data after we print it
		putCharInstructions.add(new AsmLABEL(entryLabel));
		putCharInstructions.add(new AsmOPER("LDO $0,FP,16", null, null, null));

		// Set the next octa after character to 0
		putCharInstructions.add(new AsmOPER("SETL $1,0", null, null, null));
		putCharInstructions.add(new AsmOPER("STO $1,FP,16", null, null, null));

		// Get the address of the character that we want to print (the character
		// has 1B, so we need to move 15B from frame pointer)
		putCharInstructions.add(new AsmOPER("ADD $255,FP,15", null, null, null));
		
		// Actually print data
		putCharInstructions.add(new AsmOPER("TRAP 0,Fputs,StdOut", null, null, null));

		// Restore the memory
		putCharInstructions.add(new AsmOPER("STO $0,FP,16", null, null, null));

		Code putCharCode = new Code(putCharFrame, entryLabel, exitLabel, putCharInstructions);
		registers.put(putCharCode.frame.RV, 255);

		generatePrologue(putCharCode);
		generateEpilogue(putCharCode);

		return codeToString(putCharCode);
	}

	private Vector<String> generateGetCharFunction() {

		MemFrame getCharFrame = new MemFrame(new MemLabel("getChar"), 0, 0, 0);
		MemLabel entryLabel = new MemLabel();
		MemLabel exitLabel = new MemLabel();
		// To output a character, we first need to allocate 2 bytes of memory
		// for the character and string terminator 0. Then we can use system
		// call to TRAP 0,Fputs,StdOut. Instead of allocating memory, we can
		// override the next octa in our stack frame and then restore it.
		Vector<AsmInstr> getCharInstructions = new Vector<AsmInstr>();

		getCharInstructions.add(new AsmLABEL(entryLabel));

		// If the value of BufferPosition is smaller than its address, increase
		// it by one byte.
		getCharInstructions.add(new AsmOPER("LDO\t$0,BufferPosition", null, null, null));
		getCharInstructions.add(new AsmOPER("LDA\t$1,BufferPosition", null, null, null));

		getCharInstructions.add(new AsmOPER("CMP\t$2,$0,$1", null, null, null));
		getCharInstructions.add(new AsmOPER("ZSN $2,$2,1", null, null, null));

		MemLabel label = new MemLabel();
		getCharInstructions.add(new AsmOPER("BP $2," + label.name, null, null, null));

		getCharInstructions.add(new AsmOPER("LDA\t$255,InputArgs", null, null, null));
		getCharInstructions.add(new AsmOPER("\tTRAP\t0,Fgets,StdIn", null, null, null));
		getCharInstructions.add(new AsmOPER("LDA\t$0,InputBuffer", null, null, null));
		getCharInstructions.add(new AsmOPER("LDA\t$1,BufferPosition", null, null, null));
		getCharInstructions.add(new AsmOPER("STO\t$0,$1,0", null, null, null));

		getCharInstructions.add(new AsmLABEL(label));
		getCharInstructions.add(new AsmOPER("LDO\t$0,BufferPosition", null, null, null));
		getCharInstructions.add(new AsmOPER("LDB\t$255,$0", null, null, null));
		getCharInstructions.add(new AsmOPER("SETL\t$1,0", null, null, null));
		getCharInstructions.add(new AsmOPER("STB\t$1,$0", null, null, null));

		getCharInstructions.add(new AsmOPER("ADD\t$0,$0,1", null, null, null));
		getCharInstructions.add(new AsmOPER("LDA\t$1,BufferPosition", null, null, null));
		getCharInstructions.add(new AsmOPER("STO\t$0,$1,0", null, null, null));

		Code getCharCode = new Code(getCharFrame, entryLabel, exitLabel, getCharInstructions);
		registers.put(getCharCode.frame.RV, 255);

		generatePrologue(getCharCode);
		generateEpilogue(getCharCode);

		return codeToString(getCharCode);
	}

	private Vector<String> generateStandardLibrary() {
		Vector<String> instructions = new Vector<String>();
		
		// Function _new. We don't really need to create a new stack frame. So
		// simply get the first argument (number of bytes that we want to
		// reserve) and move the heap pointer down. Then set the old heap
		// pointer as the return value.
		instructions.add("_new\tLDO\t$0,SP,8");
		instructions.add("\tSTO\tHP,SP,0");
		instructions.add("\tADD\tHP,HP,$0");
		instructions.add("\tPOP\t" + Compiler.numberOfRegisters + ",0\n");

		// Function _del. This function should do nothing.
		instructions.add("_del\tPOP\t" + Compiler.numberOfRegisters + ",0\n");

		// Function _exit. This function halts the program and sets the exit
		// code to 1.
		instructions.add("_exit\tSETL\t$255,1");
		instructions.add("\tTRAP\t0,Halt,0\n");

		// Function _putchar
		Vector<String> putCharInstructions = generatePutCharFunction();
		instructions.addAll(putCharInstructions);

		// Function _getchar
		Vector<String> getCharInstructions = generateGetCharFunction();
		instructions.addAll(getCharInstructions);

		return instructions;
	}

	public Vector<String> generateCode() {
		Vector<String> instructions = new Vector<String>();

		// For each code in generated codes, generate prologue and epilogue
		// and write them to file
		for (Code code : AsmGen.codes) {

			// Don't compile putChar and getChar methods because we have
			// already defined them in standard library
			if (code.frame.label.name.equals("_putChar") ||
				code.frame.label.name.equals("_getChar") ||
				code.frame.label.name.equals("_exit"))
				continue;
			
			// Generate PROLOGUE and EPILOGUE
			generatePrologue(code);
			generateEpilogue(code);

			instructions.addAll(codeToString(code));

		}

		return instructions;
	}

	public void run() {
		// The final phase should do the following tasks:
		//   * add epilogue and prologue to every compiled function
		//   * add the bootstrap routine that calls _main
		//   * add the standard library (new, del, and the basic IO functions)
		//   * write the program to the output file
		
		try {
			// Open output file for writing
			File outputFile = new File(Compiler.cmdLineArgValue("--dst-file-name"));
			BufferedWriter output = new BufferedWriter(new FileWriter(outputFile));

			// Generate BOOTSTRAP code and write it to file
			for (String instruction : generateBootstrapRoutine())
				output.write(instruction + "\n");

			// Generate STANDARD LIBRARY and write it to file
			for (String instruction : generateStandardLibrary())
				output.write(instruction + "\n");

			// Generate PROLOGUE and EPILOGUE for each function and write it to
			// file
			for (String instruction : generateCode())
				output.write(instruction + "\n");

			output.close();
		} catch (Exception e) {
			System.out.println(e);
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
				logger.addAttribute("code", instr.toString(registers));
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
