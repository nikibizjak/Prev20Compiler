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

	public static Vector<AsmInstr> divide(ImcBINOP.Oper operation, MemTemp dividend, MemTemp divisor) {
		// The division and modulo operators are a bit tricky on Intel CISC
		// processor. We can only divide using two special registers - rax and
		// rdx. That means, that we have to save the rax and rdx register
		// contents before performing division and then restore their values
		// after the operation has been performed.
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// Save the rax and rdx register values to stack
		instructions.add(new AsmOPER("push rax", null, null, null));
		instructions.add(new AsmOPER("push rdx", null, null, null));

		// First, move the dividend to the rax register
		// instructions.add(new AsmMOVE("mov rax, `s0", AsmGen.temps(dividend), null));
		instructions.add(new AsmOPER("mov rax, `s0", AsmGen.temps(dividend), null, null));

		// Then, use the cqo instruction that doubles the size of the operand in
		// register RAX by means of sign extension and stores the result in
		// registers RDX:RAX.
		instructions.add(new AsmOPER("cqo", null, null, null));

		// Perform the signed division by the divisor temporary
		instructions.add(new AsmOPER("idiv `s0", AsmGen.temps(divisor), null, null));

		// The quotient is saved in register rax and the remainder is saved in
		// register rdx. Depending on the type of operation we should move the
		// correct result to the resulting temporary.
		if (operation == ImcBINOP.Oper.DIV) {
			instructions.add(new AsmOPER("mov `d0, rax", null, AsmGen.temps(dividend), null));
		} else if (operation == ImcBINOP.Oper.MOD) {
			instructions.add(new AsmOPER("mov `d0, rdx", null, AsmGen.temps(dividend), null));
		}

		// Restore rax and rdx register values from the stack
		instructions.add(new AsmOPER("pop rdx", null, null, null));
		instructions.add(new AsmOPER("pop rax", null, null, null));

		return instructions;
	}

	public static Vector<AsmInstr> compare(ImcBINOP.Oper operation, MemTemp first, MemTemp second) {
		// The simplest comparison operations are actually not that simple.
		// Intel x64 processors have a command for comparison (cmp), that
		// compares values of two registers and sets multiple flags. The
		// flags can then be used to set the lowest bit of destination
		// register. Because ONLY the lowest byte is set, we must first set
		// the register value to 0.
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		Vector<MemTemp> resultRegister = AsmGen.temps(first);

		instructions.add(new AsmOPER("cmp `d0, `s1", AsmGen.temps(first, second), resultRegister, null));
		instructions.add(new AsmOPER("mov `d0, qword 0", AsmGen.temps(first), resultRegister, null));

		switch (operation) {
			case EQU:	instructions.add(new AsmOPER("sete `db0", resultRegister, resultRegister, null));	break;
			case NEQ:	instructions.add(new AsmOPER("setne `db0", resultRegister, resultRegister, null));	break;
			case LTH:	instructions.add(new AsmOPER("setl `db0", resultRegister, resultRegister, null));	break;
			case GTH:	instructions.add(new AsmOPER("setg `db0", resultRegister, resultRegister, null));	break;
			case LEQ:	instructions.add(new AsmOPER("setle `db0", resultRegister, resultRegister, null));	break;
			case GEQ:	instructions.add(new AsmOPER("setge `db0", resultRegister, resultRegister, null));	break;
			default: break;
		}

		return instructions;
	}

	public MemTemp visit(ImcBINOP binOp, Vector<AsmInstr> instructions) {

		// In Intel 64 assembly, the binary operations have the form of
		// oper dst src, which translates to dst <- dst oper src.
		MemTemp resultRegister = binOp.fstExpr.accept(this, instructions);
		MemTemp operandRegister = binOp.sndExpr.accept(this, instructions);

		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(resultRegister);
		uses.add(operandRegister);

		Vector<MemTemp> defines = new Vector<MemTemp>();
		defines.add(resultRegister);

		switch (binOp.oper) {

			// The easier operations that can be performed using a single instruction
			case OR:	instructions.add(new AsmOPER("or `d0, `s1", uses, defines, null));	break;
			case AND:	instructions.add(new AsmOPER("and `d0, `s1", uses, defines, null));	break;
			case ADD:	instructions.add(new AsmOPER("add `d0, `s1", uses, defines, null));	break;
			case SUB:	instructions.add(new AsmOPER("sub `d0, `s1", uses, defines, null));	break;
			case MUL:	instructions.add(new AsmOPER("imul `d0, `s1", uses, defines, null));	break;

			// The division and modulo operators require more work, so a new
			// function is defined
			case DIV:
			case MOD:
				instructions.addAll(divide(binOp.oper, resultRegister, operandRegister));
				break;
			
			// Integer comparison is also a special case, call a compare
			// function
			case EQU:
			case NEQ:
			case LTH:
			case GTH:
			case LEQ:
			case GEQ:
				instructions.addAll(compare(binOp.oper, resultRegister, operandRegister));
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

		instructions.add(new AsmMOVE("mov qword `d0, [`s0]", AsmGen.temps(addressTemporary), AsmGen.temps(memoryTemporary)));

		return memoryTemporary;
	}

	public MemTemp visit(ImcNAME name, Vector<AsmInstr> instructions) {
		MemTemp register = new MemTemp();

		// Use the lea (load effective address) command to load the address of
		// label into our temporary
		instructions.add(new AsmOPER("lea `d0, [" + name.label.name + "]", null, AsmGen.temps(register), null));
		
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
		Vector<MemTemp> defines = AsmGen.temps(register);

		// The original Appel's Tree language does not support unary operations.
		// They should be implemented using binary operations (eg. -x = BINOP(SUB, 0, x))
		// In our compiler, we will implement them using xor and neg instructions.
		switch (unOp.oper) {
			case NOT:	instructions.add(new AsmOPER("xor `d0, 1", defines, defines, null));	break;
			case NEG:	instructions.add(new AsmOPER("neg `d0", defines, defines, null));		break;
			default: break;
		}

		return register;
	}

	public static Vector<AsmInstr> loadConstant(MemTemp temporary, long value) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		// The Intel's 64 assembly supports loading 64 bit immediate values to
		// registers. We can simply use mov instruction. If the value is
		// negative, the register should be negated.
		long absoluteValue = Math.abs(value);
		
		Vector<MemTemp> defines = AsmGen.temps(temporary);

		instructions.add(new AsmOPER("mov `d0, qword " + absoluteValue, null, defines, null));
		if (value < 0)
			instructions.add(new AsmOPER("neg `d0", defines, defines, null));

		return instructions;
	}

}
