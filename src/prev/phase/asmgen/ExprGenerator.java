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

		MemTemp resultRegister = binOp.fstExpr.accept(this, instructions);
		MemTemp operandRegister = binOp.sndExpr.accept(this, instructions);

		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(resultRegister);
		uses.add(operandRegister);

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(resultRegister);

		switch (binOp.oper) {
			// The easier operations - the ones that are already implemented in
			// MMIX assebly language
			case OR:	instructions.add(new AsmOPER("or `d0, `s1", uses, defines, null));	break;
			case AND:	instructions.add(new AsmOPER("and `d0, `s1", uses, defines, null));	break;
			case ADD:	instructions.add(new AsmOPER("add `d0, `s1", uses, defines, null));	break;
			case SUB:	instructions.add(new AsmOPER("sub `d0, `s1", uses, defines, null));	break;
			case MUL:	instructions.add(new AsmOPER("imul `d0, `s1", uses, defines, null));	break;

			// The DIV and MOD operations can only be executed in special registers
			// we can divide 64 bit number in registers EDX:EAX with number stored in r8 like so:
			// mov	edx, upper 32 bits of dividend
			// mov	eax, lower 32 bits of dividend
			// mov	r8, divisor
			// idiv	r8
			// the result is saved in:
			//   * RAX <- Quotient
			//   * RDX <- Remainder
			case DIV:
				instructions.add(new AsmOPER("push rax", null, null, null));
				instructions.add(new AsmOPER("push rdx", null, null, null));
				Vector<MemTemp> usesDivision = new Vector<MemTemp>();
				usesDivision.add(resultRegister);
				instructions.add(new AsmOPER("mov rax, `s0", usesDivision, null, null));
				instructions.add(new AsmOPER("cqo", null, null, null));
				Vector<MemTemp> usesDivision2 = new Vector<MemTemp>();
				usesDivision2.add(operandRegister);
				instructions.add(new AsmOPER("idiv `s0", usesDivision2, defines, null));
				instructions.add(new AsmOPER("mov `d0, rax", null, usesDivision, null));
				instructions.add(new AsmOPER("pop rdx", null, null, null));
				instructions.add(new AsmOPER("pop rax", null, null, null));
				break;
			case MOD:
				instructions.add(new AsmOPER("push rax", null, null, null));
				instructions.add(new AsmOPER("push rdx", null, null, null));
				Vector<MemTemp> usesDivision3 = new Vector<MemTemp>();
				usesDivision3.add(resultRegister);
				instructions.add(new AsmOPER("mov rax, `s0", usesDivision3, null, null));
				instructions.add(new AsmOPER("cqo", null, null, null));
				Vector<MemTemp> usesDivision4 = new Vector<MemTemp>();
				usesDivision4.add(operandRegister);
				instructions.add(new AsmOPER("idiv `s0", usesDivision4, defines, null));
				instructions.add(new AsmOPER("mov `d0, rdx", null, usesDivision3, null));
				instructions.add(new AsmOPER("pop rdx", null, null, null));
				instructions.add(new AsmOPER("pop rax", null, null, null));
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
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("sete `db0", defines, defines, null));
				break;
			case NEQ:
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("setne `db0", defines, defines, null));
				break;
			case LTH:
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("setl `db0", defines, defines, null));
				break;
			case GTH:
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("setg `db0", defines, defines, null));
				break;
			case LEQ:
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("setle `db0", defines, defines, null));
				break;
			case GEQ:
				instructions.add(new AsmOPER("cmp `d0, `s1", uses, defines, null));
				instructions.add(new AsmOPER("mov `d0, qword 0", defines, defines, null));
				instructions.add(new AsmOPER("setge `db0", defines, defines, null));
				break;

			default:	break;
		}
		return resultRegister;
	}

	public MemTemp visit(ImcCALL call, Vector<AsmInstr> instructions) {
		// By the Microsoft x64 calling convention, the caller function is
		// responsible for two things:
		//   1. The calling function allocates 32B of "shadow space" on the
		//      stack before calling the function
		//   2. Saving the function arguments to the stack (or passing them in
		//      registers to be more performant).
		//   3. Calling the function. When we use call statement, the return
		//      address is pushed to the stack
		//   4. Getting the result from stack
		//   5. Popping the stack after call
		instructions.add(new AsmOPER("; Calling function", null, null, null));

		// Add function call arguments to the stack. The first function argument
		// is ALWAYS static link.
		for (int i = 0; i < call.args().size(); i++) {
			ImcExpr argument = call.args().get(i);
			Long offset = call.offs().get(i);

			MemTemp argumentTemporary = argument.accept(this, instructions);
			Vector<MemTemp> uses = new Vector<MemTemp>();
			uses.add(argumentTemporary);

			instructions.add(new AsmOPER("mov [rsp + " + (32 + offset) + "], `s0", uses, null, null));
		}

		// Actually call the function (the return address is pushed to the stack
		// right before the call)
		instructions.add(new AsmOPER("call " + call.label.name, null, null, null));

		// After the called function finishes, get its return value from the
		// stack. The return value is written at the position of the first
		// argument (static link).
		MemTemp returnValueTemporary = new MemTemp();
		Vector <MemTemp> defs = new Vector<MemTemp>();
		defs.add(returnValueTemporary);

		instructions.add(new AsmOPER("mov `d0, [rsp + 32]", null, defs, null));

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

		instructions.add(new AsmMOVE("mov qword `d0, [`s0]", uses, defs));

		return memoryTemporary;
	}

	public MemTemp visit(ImcNAME name, Vector<AsmInstr> instructions) {
		MemTemp register = new MemTemp();

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(register);

		// Use the instruction LDA $X, $Y to load address $Y (can be label) to
		// register $X.
		instructions.add(new AsmOPER("lea `d0, [" + name.label.name + "]", null, defines, null));
		
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
		MemTemp register = unOp.subExpr.accept(this, instructions);
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
				instructions.add(new AsmOPER("xor `d0, 1", defines, defines, null));
				break;
			case NEG:
				instructions.add(new AsmOPER("neg `d0", defines, defines, null));
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
		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(temporary);

		long absoluteValue = Math.abs(value);
		
		instructions.add(new AsmOPER("mov `d0, qword " + absoluteValue, null, defines, null));
		
		// If the constant that we are trying to load is negative, negate the
		// current register
		if (value < 0)
			instructions.add(new AsmOPER("neg `d0", defines, defines, null));

		return instructions;
	}

}
