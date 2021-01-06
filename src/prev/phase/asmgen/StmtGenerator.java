package prev.phase.asmgen;

import java.util.Vector;

import prev.data.asm.AsmInstr;
import prev.data.asm.AsmLABEL;
import prev.data.asm.AsmMOVE;
import prev.data.asm.AsmOPER;
import prev.data.imc.code.expr.ImcMEM;
import prev.data.imc.code.stmt.ImcCJUMP;
import prev.data.imc.code.stmt.ImcESTMT;
import prev.data.imc.code.stmt.ImcJUMP;
import prev.data.imc.code.stmt.ImcLABEL;
import prev.data.imc.code.stmt.ImcMOVE;
import prev.data.imc.code.stmt.ImcSTMTS;
import prev.data.imc.code.stmt.ImcStmt;
import prev.data.imc.visitor.ImcVisitor;
import prev.data.lin.LinCodeChunk;
import prev.data.mem.MemLabel;
import prev.data.mem.MemTemp;

/**
 * Machine code generator for ststements.
 */
public class StmtGenerator implements ImcVisitor<Vector<AsmInstr>, Object> {
	
	public Vector<AsmInstr> visit(ImcCJUMP cjump, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();

		// First, visit condition and get the temporary register that the result
		// is saved to. The result will either be 0 or 1.
		MemTemp conditionTemporary = cjump.cond.accept(new ExprGenerator(), instructions);

		// The negative label is directly after the condition, so we need to
		// check if conditionTemporary contains non-zero value (=1). If it does,
		// jump to positive label, otherwise continue.
		Vector<MemLabel> jumps = new Vector<MemLabel>();
		jumps.add(cjump.posLabel);
		jumps.add(cjump.negLabel);

		Vector<MemTemp> uses = new Vector<MemTemp>();
		uses.add(conditionTemporary);

		// Add a jump if the conditionTemporary contains nonzero value
		instructions.add(new AsmOPER("BNZ `s0," + cjump.posLabel.name, uses, null, jumps));

		return instructions;
	}

	public Vector<AsmInstr> visit(ImcESTMT eStmt, Object arg) {
		Vector<AsmInstr> instructions = new Vector<AsmInstr>();
		eStmt.expr.accept(new ExprGenerator(), instructions);
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

		instructions.add(new AsmOPER("JMP " + jump.label.name, uses, null, jumps));
		
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

			// If destination and source of the move command are not MEM
			// instruction, then we are simply moving data from one register to
			// another. We can use SET $X, $Y or OR $X, $Y, 0
			instructions.add(new AsmMOVE("SET `d0,`s0", uses, defs));
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
			
			// We can use the LDO $X, $Y, $Z, which loads data from memory
			// location $X + $Z to register $X.
			instructions.add(new AsmOPER("LDO `d0,`s0,0", uses, defs, null));

			uses.add(destinationTemporary);

			instructions.add(new AsmOPER("STO `s0,`s1,0", uses, null, null));

		} else if (move.src instanceof ImcMEM) {
			// The instruction is MOVE(..., MEM(...)), which is just loading
			// data from memory.
			MemTemp sourceTemporary = ((ImcMEM) move.src).addr.accept(new ExprGenerator(), instructions);
			MemTemp destinationTemporary = move.dst.accept(new ExprGenerator(), instructions);

			// We can use the LDO $X, $Y, $Z, which loads data from memory
			// location $X + $Z to register $X.
			defs.add(destinationTemporary);
			uses.add(sourceTemporary);
			instructions.add(new AsmOPER("LDO `d0,`s0,0", uses, defs, null));
		} else {
			// The instruction is MOVE(MEM(...), ...), so we are storing data to
			// the memory. To store the entire register data, we can use the
			// store octa: STO $X, $Y, $Z command (store data from register $X
			// to memory location $Y + $Z)
			MemTemp sourceTemporary = move.src.accept(new ExprGenerator(), instructions);
			MemTemp destinationTemporary = ((ImcMEM) move.dst).addr.accept(new ExprGenerator(), instructions);

			uses.add(sourceTemporary);
			uses.add(destinationTemporary);
			
			instructions.add(new AsmOPER("STO `s0,`s1,0", uses, null, null));
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
