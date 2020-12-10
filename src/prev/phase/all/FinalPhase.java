package prev.phase.all;

import java.io.BufferedWriter;
import java.util.*;
import java.io.*;

import prev.data.mem.*;
import prev.Compiler;
import prev.data.asm.*;
import prev.data.lin.LinDataChunk;
import prev.phase.*;
import prev.phase.asmgen.*;
import prev.phase.imclin.ImcLin;
import prev.phase.livean.*;
import prev.phase.regall.RegAll;
import prev.data.semtype.*;

/**
 * Register allocation.
 */
public class FinalPhase extends Phase {
	
	private HashMap<MemTemp, Integer> registers;
	private HashMap<String, HashMap<Integer, String>> registerNames;

	public FinalPhase(HashMap<MemTemp, Integer> registers, HashMap<String, HashMap<Integer, String>> registerNames) {
		super("all");
		this.registers = registers;
		this.registerNames = registerNames;
	}

	private Vector<String> codeToString(Code code) {
		// In this function, we convert a Code to a list of strings

		Vector<String> instructions = new Vector<String>();

		for (AsmInstr instruction : code.instrs) {

			if (instruction instanceof AsmLABEL) {
				instructions.add(instruction.toString(registers, registerNames) + ":");
			} else {
				instructions.add("\t" + instruction.toString(registers, registerNames));
			}

		}

		return instructions;
	}

	private void generatePrologue(Code code) {
		// This function will directly modify the code in the AsmGen.codes, so
		// it should only be called once
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// Empty line before function label
		instructions.add(new AsmOPER("", null, null, null));
		
		// Function entry label
		instructions.add(new AsmLABEL(code.frame.label));

		// Construct a memory temporary, that will always be mapped to register
		// rax and will be used to store loaded constants. 
		MemTemp temporary = new MemTemp();
		registers.put(temporary, 0);

		instructions.add(new AsmOPER("; prologue", null, null, null));

		// Save old frame pointer
		instructions.add(new AsmOPER("push rbp", null, null, null));

		// Move frame pointer to current stack pointer
		instructions.add(new AsmOPER("mov rbp, rsp", null, null, null));
		
		// Leave space for local variables and saving register values
		long additionalSpace = code.frame.size + code.tempSize + (11 * 8);
		if (additionalSpace != 0L) {
			instructions.addAll(ExprGenerator.loadConstant(temporary, additionalSpace));
			instructions.add(new AsmOPER("sub rsp, rbx", null, null, null));
		}

		// Push all registers to stack
		long offset = code.frame.argsSize + 32 - 8;
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 0*8) + "], rax", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 1*8) + "], rbx", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 2*8) + "], rcx", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 3*8) + "], rdx", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 4*8) + "], r8", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 5*8) + "], r9", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 6*8) + "], r10", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 7*8) + "], r11", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 8*8) + "], r12", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 9*8) + "], r13", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 10*8) + "], r14", null, null, null));
		instructions.add(new AsmOPER("mov [rsp + " + (offset + 11*8) + "], r15", null, null, null));

		instructions.add(new AsmOPER("; end of prologue", null, null, null));

		// The last instruction is the JUMP to function body label
		instructions.add(new AsmOPER("jmp " + code.entryLabel.name, null, null, null));

		code.instrs.addAll(0, instructions);
	}

	private void generateEpilogue(Code code) {
		// This function will directly modify the code in the AsmGen.codes, so
		// it should only be called once
		Vector<AsmInstr> instructions = code.instrs;

		// Add function body exit label
		instructions.add(new AsmLABEL(code.exitLabel));

		instructions.add(new AsmOPER("; epilogue", null, null, null));

		// Store return value to first argument memory location (SL)
		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(code.frame.RV);
		instructions.add(new AsmOPER("mov [rbp + 48], `s0", uses, null, null));

		long offset = code.frame.argsSize + 32 - 8;
		instructions.add(new AsmOPER("mov rax, [rsp + " + (offset + 0*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov rbx, [rsp + " + (offset + 1*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov rcx, [rsp + " + (offset + 2*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov rdx, [rsp + " + (offset + 3*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r8, [rsp + " + (offset + 4*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r9, [rsp + " + (offset + 5*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r10, [rsp + " + (offset + 6*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r11, [rsp + " + (offset + 7*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r12, [rsp + " + (offset + 8*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r13, [rsp + " + (offset + 9*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r14, [rsp + " + (offset + 10*8) + "]", null, null, null));
		instructions.add(new AsmOPER("mov r15, [rsp + " + (offset + 11*8) + "]", null, null, null));

		instructions.add(new AsmOPER("mov rsp, rbp", null, null, null));

		// Store return value to first argument memory location (SL)
		/*Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(code.frame.RV);
		instructions.add(new AsmOPER("mov [rsp + 48], `s0", uses, null, null));*/

		instructions.add(new AsmOPER("pop rbp", null, null, null));

		// instructions.add(new AsmOPER("leave", null, null, null));
		instructions.add(new AsmOPER("ret", null, null, null));
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
		instructions.add("%include \"io64.inc\"");
		instructions.add("bits 64");
		instructions.add("default rel\n");

		instructions.add("section .data");

		for (LinDataChunk dataChunk : ImcLin.dataChunks()) {
			// If the initial value is not null, that means that this data chunk
			// is a string. Escape the string with zero terminator. Otherwise,
			// the data chunk is a normal variable, which is dataChunk.size
			// bytes long. 
			if (dataChunk.init != null) {
				instructions.add(dataChunk.label.name + ":\tdb " + dataChunk.init + ", 0");
			} else {
				// _i:	dq	12
				String instruction = dataChunk.label.name + ":\tdq ";
				for (int i = 0; i < dataChunk.size; i += 8) {
					if (i > 0)
						instruction += ", ";
					instruction += "0";
				}
				instructions.add(instruction);
			}
		}

		instructions.add("\nsection .text");
		instructions.add("global CMAIN");
		instructions.add("CMAIN:");
		instructions.add("\tmov rbp, rsp");
		instructions.add("\tcall _main");
		// Get the return value of the main function
		instructions.add("\tmov rax, [rsp + 32]");
		instructions.add("\tret");

		return instructions;
	}

	// mov rcx, [rbp + 56]
	// call printf

	private Vector<String> generateStandardLibrary() {
		Vector<String> instructions = new Vector<String>();
		
		/*// Function _new. We don't really need to create a new stack frame. So
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
		instructions.addAll(getCharInstructions);*/

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
			// for (String instruction : generateStandardLibrary())
			//	output.write(instruction + "\n");

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
				logger.addAttribute("code", instr.toString(registers, registerNames));
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
