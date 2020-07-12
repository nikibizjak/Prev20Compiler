package prev.phase.asmgen;

import java.util.*;
import prev.data.mem.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.visitor.*;
import prev.Compiler;
import prev.data.asm.*;

/**
 * Machine code generator for expressions.
 */
public class ExprGenerator implements ImcVisitor<MemTemp, Vector<AsmInstr>> {

	public MemTemp visit(ImcBINOP binOp, Vector<AsmInstr> instructions) {
		MemTemp register = new MemTemp();

		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(binOp.fstExpr.accept(this, instructions));
		uses.add(binOp.sndExpr.accept(this, instructions));

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(register);

		switch (binOp.oper) {
			// The easier operations - the ones that are already implemented in
			// MMIX assebly language
			case OR:	instructions.add(new AsmOPER("OR `d0,`s0,`s1", uses, defines, null));	break;
			case AND:	instructions.add(new AsmOPER("AND `d0,`s0,`s1", uses, defines, null));	break;
			case ADD:	instructions.add(new AsmOPER("ADD `d0,`s0,`s1", uses, defines, null));	break;
			case SUB:	instructions.add(new AsmOPER("SUB `d0,`s0,`s1", uses, defines, null));	break;
			case MUL:	instructions.add(new AsmOPER("MUL `d0,`s0,`s1", uses, defines, null));	break;
			case DIV:	instructions.add(new AsmOPER("DIV `d0,`s0,`s1", uses, defines, null));	break;

			// There is no modulo operator in MMIX, so we need to do it
			// differently. The divison remainder is placed in special
			// remainder register $rR. We need to copy this value to our
			// register.
			case MOD:
				instructions.add(new AsmOPER("DIV `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("GET `d0,rR", null, defines, null));
				break;

			// The comparison operators will need two operations. The first will
			// be the compare operation and the second a transformation from set
			// {-1, 0, 1} to set {0, 1}.
			// The comparison operator CMP $X, $Y, $Z sets the value $X to
			//   * $X = -1, if $Y < $Z
			//   * $X = 0, if $Y = $Z
			//   * $X = 1, if $Y > $Z
			// The second operation will be executed on the same register,
			// because we will want to return this register.
			case EQU:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("ZSZ `d0,`s0,1", defines, defines, null));
				break;
			case NEQ:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("AND `d0,`s0,1", defines, defines, null));
				break;
			case LTH:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("ZSN `d0,`s0,1", defines, defines, null));
				break;
			case GTH:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("ZSP `d0,`s0,1", defines, defines, null));
				break;
			case LEQ:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("ZSNP `d0,`s0,1", defines, defines, null));
				break;
			case GEQ:
				instructions.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defines, null));
				instructions.add(new AsmOPER("ZSNN `d0,`s0,1", defines, defines, null));
				break;

			default:	break;
		}
		return register;
	}

	public MemTemp visit(ImcCALL call, Vector<AsmInstr> instructions) {
		// PROLOGUE - prepare function call, save static link and all arguments
		// to the call stack. Save SL (static link) and arguments to the bottom
		// of the call stack. The value in register $254 is the stack pointer.
		for (int i = 0; i < call.args().size(); i++) {
			ImcExpr argument = call.args().get(i);
			Long offset = call.offs().get(i);

			MemTemp argumentTemporary = argument.accept(this, instructions);
			Vector<MemTemp> uses = new Vector<MemTemp>();
			uses.add(argumentTemporary);

			instructions.add(new AsmOPER("STO `s0,$254," + offset, uses, null, null));
		}

		// BODY - call the function. Use the PUSHJ instruction to push registers
		// and jump to label. The first parameter (currently $0) is the size of
		// our call stack.
		instructions.add(new AsmOPER("PUSHJ " + Compiler.numberOfRegisters + "," + call.label.name, null, null, null));

		// EPILOGUE - get the function return value. The values of registers
		// will be restored when the POP instruction is called. The stack
		// pointer points to our return value. So get data from memory where
		// register $254 points.
		MemTemp returnValueTemporary = new MemTemp();
		Vector <MemTemp> defs = new Vector<MemTemp>();
		defs.add(returnValueTemporary);

		instructions.add(new AsmOPER("LDO `d0,$254,0", null, defs, null));

		return returnValueTemporary;
	}

	public MemTemp visit(ImcCONST constant, Vector<AsmInstr> instructions) {
		// Create a new temporary (= register) where our constant will be stored
		MemTemp register = new MemTemp();
		instructions.addAll(loadConstant(register, constant.value));
		return register;
	}

	public MemTemp visit(ImcMEM mem, Vector<AsmInstr> instructions) {
		// If we encounter MEM(addr), we must load data from address addr to new
		// temporary register. Visit the address expression with expression
		// visitor, which will return a temporary where computed memory address
		// is now computed.
		MemTemp memoryTemporary = new MemTemp();
		MemTemp addressTemporary = mem.addr.accept(this, instructions);

		Vector<MemTemp> uses = new Vector<MemTemp>();
		Vector<MemTemp> defs = new Vector<MemTemp>();

		uses.add(addressTemporary);
		defs.add(memoryTemporary);

		instructions.add(new AsmMOVE("LDO `d0,`s0,0", uses, defs));

		return memoryTemporary;
	}

	public MemTemp visit(ImcNAME name, Vector<AsmInstr> instructions) {
		MemTemp register = new MemTemp();

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(register);

		// Use the instruction LDA $X, $Y to load address $Y (can be label) to
		// register $X.
		instructions.add(new AsmOPER("LDA `d0," + name.label.name, null, defines, null));
		
		return register;
	}

	public MemTemp visit(ImcSEXPR sExpr, Vector<AsmInstr> instructions) {
		// Visit the statement and add all assembly instructions to list of
		// instructions
		instructions.addAll(sExpr.stmt.accept(new StmtGenerator(), null));
		MemTemp register = sExpr.expr.accept(this, instructions);
		return register;
	}

	public MemTemp visit(ImcTEMP temp, Vector<AsmInstr> instructions) {
		return temp.temp;
	}

	public MemTemp visit(ImcUNOP unOp, Vector<AsmInstr> instructions) {
		MemTemp register = new MemTemp();

		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(unOp.subExpr.accept(this, instructions));

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(register);

		// We can implement negation using the NEG $X, Y, $Z, which sets the
		// register $X value to the result of the Y - $Z operation. If we set Y
		// = 0, the operation is a simple integer negation.
		// If we do the same for boolean 0...01 or 0...00, the result is a
		// signed number -1 or -0, which is what we don't want. We can use
		// XOR $X, $Y, Z to set the value of register $X to xor of values $Y
		// and Z. If we set Z = 1, then the result of (1 XOR 1) = 0
		// and (0 XOR 1) = 1.
		switch (unOp.oper) {
			case NOT:
				instructions.add(new AsmOPER("XOR `d0,`s0,1", uses, defines, null));
				break;
			case NEG:
				instructions.add(new AsmOPER("NEG `d0,`s0", uses, defines, null));
				break;
			default: break;
		}

		return register;
	}

	public static Vector<AsmInstr> loadConstant(MemTemp temporary, long value) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		// When loading constant, we need to load 8 bytes (= 1 octa) Loading is
		// done using commands SETH, SETMH, SETML and SETL. We must divide our
		// long (= 8 bytes) into 4 wydes and load them individually into new
		// register. If the number is negative (which can happen in the next
		// phases), we will always need all 4 instructions, because the leftmost
		// bit will be 1 if the number is negative. We can solve this by first
		// loading an absolute value to registers and then negate the number.
		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(temporary);

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(temporary);

		long absoluteValue = Math.abs(value);
		
		// Compute all 4 wydes using bit shifting and bitwise and operations
		int low = (int) (absoluteValue & 0xFFFF);
		int mediumLow = (int) ((absoluteValue >> 16) & 0xFFFF);
		int mediumHigh = (int) ((absoluteValue >> 32) & 0xFFFF);
		int high = (int) ((absoluteValue >> 48) & 0xFFFF);

		// Set the lowest wyde, then use increment to set medium low, medium
		// high and high wydes of our register
		instructions.add(new AsmOPER("SETL `d0," + low, null, defines, null));
		if (mediumLow != 0)
			instructions.add(new AsmOPER("INCML `d0," + mediumLow, uses, defines, null));
		if (mediumHigh != 0)
			instructions.add(new AsmOPER("INCMH `d0," + mediumHigh, uses, defines, null));
		if (high != 0)
			instructions.add(new AsmOPER("INCH `d0," + high, uses, defines, null));
		
		// If the constant that we are trying to load is negative, negate the
		// current register
		if (value < 0)
			instructions.add(new AsmOPER("NEG `d0,`s0", uses, defines, null));

		return instructions;
	}

}
