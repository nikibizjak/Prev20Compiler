package prev.phase.asmgen;

import java.util.*;
import prev.data.imc.code.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.visitor.*;
import prev.data.mem.*;
import prev.data.asm.*;
import prev.common.report.*;
import prev.data.lin.*;

/**
 * Machine code generator for ststements.
 */
public class StmtGenerator implements ImcVisitor<Vector<AsmInstr>, Object> {
	
	public Vector<AsmInstr> visit(ImcCJUMP cjump, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// The conditional jump instruction will be implemented as such:
		//   * evaluate the condition expression
		//   * if it evaluates to TRUE, we should jump to positive label
		//   * otherwise, we should continue to the next instruction

		// Add both possible jumps (to positive and negative label to our jumps
		// so that we can perform liveness analysis and optimizations)
		Vector<MemLabel> jumps = new Vector<MemLabel>();
		jumps.add(cjump.posLabel);
		jumps.add(cjump.negLabel);

		// If the condition is a simple BINOP(oper, TEMP(...), TEMP(...)), then
		// the conditional statement can be made better.
		if (cjump.cond instanceof ImcBINOP) {
			ImcBINOP conditionBinop = (ImcBINOP) cjump.cond;
			if (conditionBinop.fstExpr instanceof ImcTEMP && conditionBinop.sndExpr instanceof ImcTEMP) {

				MemTemp firstTemporary = ((ImcTEMP) conditionBinop.fstExpr).temp;
				MemTemp secondTemporary = ((ImcTEMP) conditionBinop.sndExpr).temp;

				Vector<AsmInstr> specialInstructions = new Vector<AsmInstr>();
				specialInstructions.add(new AsmOPER("cmp `d0, `s0", AsmGen.temps(secondTemporary, firstTemporary), AsmGen.temps(firstTemporary), null));

				boolean canShortenConditionalJump = true;
				switch (conditionBinop.oper) {
					case EQU:	specialInstructions.add(new AsmOPER("je " + cjump.posLabel.name, null, null, jumps));	break;
					case NEQ:	specialInstructions.add(new AsmOPER("jne " + cjump.posLabel.name, null, null, jumps));	break;
					case LTH:	specialInstructions.add(new AsmOPER("jl " + cjump.posLabel.name, null, null, jumps));	break;
					case GTH:	specialInstructions.add(new AsmOPER("jg " + cjump.posLabel.name, null, null, jumps));	break;
					case LEQ:	specialInstructions.add(new AsmOPER("jle " + cjump.posLabel.name, null, null, jumps));	break;
					case GEQ:	specialInstructions.add(new AsmOPER("jge " + cjump.posLabel.name, null, null, jumps));	break;
					default: canShortenConditionalJump = false;
				}

				if (canShortenConditionalJump) {
					instructions.addAll(specialInstructions);
					return instructions;
				}

			}
		}

		// First, visit condition and get the temporary register that the result
		// is saved to. The result will either be 0 or 1.
		MemTemp conditionTemporary = cjump.cond.accept(new ExprGenerator(), instructions);

		// The negative label is directly after the condition, so we need to
		// check if conditionTemporary contains non-zero value (=1). If it does,
		// jump to positive label, otherwise continue.
		// Add a jump if the conditionTemporary contains nonzero value
		instructions.add(new AsmOPER("cmp `s0, 0", AsmGen.temps(conditionTemporary), AsmGen.temps(conditionTemporary), null));
		instructions.add(new AsmOPER("jne " + cjump.posLabel.name, null, null, jumps));

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcESTMT eStmt, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		MemTemp register = eStmt.expr.accept(new ExprGenerator(), instructions);
		return instructions;
	}

	public Vector<AsmInstr> visit(ImcJUMP jump, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// Add a new unconditional jump command JMP Label
		Vector<MemLabel> jumps = new Vector<MemLabel>();
		jumps.add(jump.label);

		// If the label that we are trying to jump to is the function exit
		// label, make it use the frame return value, so we assign a register to it.
		Vector<MemTemp> uses = new Vector<MemTemp>();
		if (arg instanceof LinCodeChunk) {
			if (jump.label == ((LinCodeChunk) arg).exitLabel)
				uses.add(((LinCodeChunk) arg).frame.RV);
		}

		instructions.add(new AsmOPER("jmp " + jump.label.name, uses, null, jumps));
		
		return instructions;
	}

	public Vector<AsmInstr> visit(ImcLABEL label, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		instructions.add(new AsmLABEL(label.label));
		return instructions;
	}

	public Vector<AsmInstr> visit(ImcMOVE move, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		Vector<MemTemp> uses = new Vector<MemTemp>();
		Vector<MemTemp> defs = new Vector<MemTemp>();

		if (!(move.dst instanceof ImcMEM) && !(move.src instanceof ImcMEM)) {
			// First, use expression generator to visit both move source and
			// destination.
			MemTemp source = move.src.accept(new ExprGenerator(), instructions);
			MemTemp destination = move.dst.accept(new ExprGenerator(), instructions);
			uses.add(source);
			defs.add(destination);

			// The destination and source are both temporaries, so we can simply
			// use move instruction as we normally would.
			instructions.add(new AsmMOVE("mov `d0, `s0", uses, defs));
		} else if (move.src instanceof ImcMEM && move.dst instanceof ImcMEM) {
			// The instruction is MOVE(MEM(...), MEM(...)), so we are
			// loading and storing data at the same time.
			MemTemp sourceTemporary = ((ImcMEM) move.src).addr.accept(new ExprGenerator(), instructions);
			MemTemp destinationTemporary = ((ImcMEM) move.dst).addr.accept(new ExprGenerator(), instructions);

			// This temporary is used to hold the value that we have just
			// loaded from memory 
			MemTemp temporary = new MemTemp();
			defs.add(temporary);
			uses.add(sourceTemporary);
			
			// First, load data from memory to temporary register
			instructions.add(new AsmOPER("mov qword `d0, [`s0]", uses, defs, null));

			uses.add(destinationTemporary);

			// Then, move data from temporary register to memory
			instructions.add(new AsmOPER("mov qword [`s1], `s0", uses, null, null));

		} else if (move.src instanceof ImcMEM) {
			// The instruction is MOVE(..., MEM(...)), which is just loading
			// data from memory.
			MemTemp sourceTemporary = ((ImcMEM) move.src).addr.accept(new ExprGenerator(), instructions);
			MemTemp destinationTemporary = move.dst.accept(new ExprGenerator(), instructions);

			// Load data from memory
			defs.add(destinationTemporary);
			uses.add(sourceTemporary);
			instructions.add(new AsmOPER("mov qword `d0, [`s0]", uses, defs, null));
		} else {
			// Store data to memory
			MemTemp sourceTemporary = move.src.accept(new ExprGenerator(), instructions);
			MemTemp destinationTemporary = ((ImcMEM) move.dst).addr.accept(new ExprGenerator(), instructions);

			uses.add(sourceTemporary);
			uses.add(destinationTemporary);

			instructions.add(new AsmOPER("mov qword [`s1], `s0", uses, null, null));
		}

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcSTMTS stmts, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		for (ImcStmt statement : stmts.stmts()) {
			instructions.addAll(statement.accept(this, arg));
		}
		return instructions;
	}

}
