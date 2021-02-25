package prev.phase.imcgen;

import java.util.*;

import prev.common.report.*;
import prev.data.ast.tree.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.visitor.*;
import prev.data.semtype.*;
import prev.data.mem.*;
import prev.phase.memory.*;
import prev.phase.seman.*;
import prev.data.imc.code.stmt.*;
import prev.data.imc.code.expr.*;

// public class ExprGenerator extends AstNullVisitor<Object, Stack<MemFrame>> {
public class ExprGenerator extends AstFullVisitor<ImcExpr, Stack<MemFrame>> {

	public ImcExpr addressFromMemoryDeclaration(AstMemDecl declaration, Stack<MemFrame> frames) {
		// This function returns the address instruction for variable, component
		// and parameter declarations (for children of AstMemDecl class)

		// Get the memory access from this variable declaration, it can either
		// be absolute or relative
		MemAccess memoryAccess = Memory.accesses.get(declaration);

		if (memoryAccess instanceof MemAbsAccess) {
			// The absolute access is easier, we simply return NAME(Label(access))
			MemAbsAccess absoluteMemoryAccess = (MemAbsAccess) memoryAccess;
			return new ImcNAME(absoluteMemoryAccess.label);
		} else {
			// The access is relative, calculate its address from function frame
			MemRelAccess relativeMemoryAccess = (MemRelAccess) memoryAccess;
			
			// The access is done inside the function at the top of the stack, get the MemFrame
			MemFrame currentFunction = frames.peek();

			// The offset is already calculated in the relativeMemoryAccess.offset,
			// convert it to CONST instruction
			ImcExpr offsetInstruction = new ImcCONST(relativeMemoryAccess.offset);

			// Create an access to the frame pointer temporary value
			ImcExpr framePointer = new ImcTEMP(currentFunction.FP);
			
			// We know that the access is at depth relativeMemoryAccess.depth
			// and the function is at depth currentFunction.depth
			// So we need to climb up currentFunction.depth - relativeMemoryAccess.depth times
			int accessDepth = currentFunction.depth - relativeMemoryAccess.depth;
			for (int i = 0; i < accessDepth; i++)
				framePointer = new ImcMEM(framePointer);

			// Add the offset to the frame pointer
			return new ImcBINOP(ImcBINOP.Oper.ADD, framePointer, offsetInstruction);
		}
	}

	@Override
	public ImcExpr visit(AstVarDecl variableDeclaration, Stack<MemFrame> frames) {
		// The variable, component and parameter declaration should all compute
		// address using the addressFromMemoryDeclaration function
		return addressFromMemoryDeclaration(variableDeclaration, frames);
	}

	@Override
	public ImcExpr visit(AstCompDecl componentDeclaration, Stack<MemFrame> frames) {
		// The variable, component and parameter declaration should all compute
		// address using the addressFromMemoryDeclaration function
		return addressFromMemoryDeclaration(componentDeclaration, frames);
	}

	@Override
	public ImcExpr visit(AstParDecl parameterDeclaration, Stack<MemFrame> frames) {
		// The variable, component and parameter declaration should all compute
		// address using the addressFromMemoryDeclaration function
		return addressFromMemoryDeclaration(parameterDeclaration, frames);
	}

	@Override
	public ImcExpr visit(AstNameExpr nameExpression, Stack<MemFrame> frames) {
		// When visiting name expression, first find where it was declared
		AstDecl declaration = SemAn.declaredAt.get(nameExpression);

		if (declaration instanceof AstMemDecl) {
			MemAccess access = Memory.accesses.get((AstMemDecl) declaration);
			if (access instanceof TemporaryAccess) {
				return new ImcTEMP(((TemporaryAccess) access).temporary);
			}
		}

		// The declaration can either be record component, variable or function
		// parameter, each of the definitions can be visited and should return
		// the address where it is declared. Here we want to access the value at
		// the returned address, so we wrap it in MEM(address)
		ImcExpr accessInstruction = declaration.accept(this, frames);
		ImcExpr instruction = new ImcMEM(accessInstruction);

		ImcGen.exprImc.put(nameExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstArrExpr arrayExpression, Stack<MemFrame> frames) {
		// First, visit the array expression, it can be name expression, record
		// expression, ... In any case it should return the MEM(...), so get
		// the memory address that the MEM object is trying to access.
		ImcExpr arrayInstruction = arrayExpression.arr().accept(this, frames);
		if (!(arrayInstruction instanceof ImcMEM))
			throw new Report.Error(arrayExpression, "Instruction is not MEM!");

		// Now that we have memory access for the first element of the array,
		// get the actual address of the first element
		ImcExpr arrayAddressInstruction = ((ImcMEM) arrayInstruction).addr;
		
		// Visit the index instruction, which will return an instruction
		ImcExpr indexInstruction = arrayExpression.idx().accept(this, frames);

		// Get array element type so we can use this to compute where the i-th
		// element of the array is (val(idx) * sizeof(type))
		SemType namedType = SemAn.ofType.get(arrayExpression.arr());
		SemType arrayType = ((SemArray) namedType.actualType()).elemType();
		
		// Finally, construct the instruction for accessing the i-th element of
		// the array. Use the address of the first element and move i times size
		// of element of the array to the right
		ImcExpr newIndexInstruction = new ImcBINOP(ImcBINOP.Oper.MUL, indexInstruction, new ImcCONST(arrayType.size()));
		ImcExpr newArrayAccessInstruction = new ImcBINOP(ImcBINOP.Oper.ADD, arrayAddressInstruction, newIndexInstruction);

		ImcExpr instruction = new ImcMEM(newArrayAccessInstruction);
		ImcGen.exprImc.put(arrayExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstRecExpr recordExpression, Stack<MemFrame> frames) {		
		ImcExpr recordInstruction = recordExpression.rec().accept(this, frames);
		if (!(recordInstruction instanceof ImcMEM))
			throw new Report.Error(recordExpression, "The access to record is not MEM?");
		ImcExpr recordAddressInstruction = ((ImcMEM) recordInstruction).addr;

		recordExpression.comp().accept(this, frames);

		AstMemDecl componentDeclaration = (AstMemDecl) SemAn.declaredAt.get(recordExpression.comp());
		MemRelAccess relativeMemoryAccess = (MemRelAccess) Memory.accesses.get(componentDeclaration);
		ImcExpr componentOffset = new ImcCONST(relativeMemoryAccess.offset);
		ImcExpr newAddress = new ImcBINOP(ImcBINOP.Oper.ADD, recordAddressInstruction, componentOffset);

		//ImcExpr recordAddressInstruction = ((ImcMEM) arrayInstruction).addr;
		ImcExpr instruction = new ImcMEM(newAddress);
		ImcGen.exprImc.put(recordExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstAtomExpr atomExpression, Stack<MemFrame> frames) {
		ImcExpr instruction = null;
		switch (atomExpression.type()) {
			case VOID:
				// The none instruction should return an undefined value
				instruction = new ImcCONST(18290);
				break;
			case CHAR:
				// First, convert string to character (get the first character)
				char character = atomExpression.value().charAt(0);
				// Convert character to integer, then to long
				instruction = new ImcCONST((long)((int) character));
				break;
			case INTEGER:
				// Integers are simple, simply convert string to integer
				instruction = new ImcCONST(Long.parseLong(atomExpression.value()));
				break;
			case BOOLEAN:
				// Boolean with value "true" is 1, otherwise 0
				boolean booleanValue = atomExpression.value().equals("true");
				// If boolean value is true, take long 1, otherwise 0
				instruction = new ImcCONST(booleanValue ? 1L : 0L);
				break;
			case POINTER:
				// The nil instruction is a pointer to zero
				instruction = new ImcCONST(0);
				break;
			case STRING:
				// Use the rule A1 for accessing strings in memory
				// First, find the absolute access from memory evaluator
				MemAbsAccess absoluteAccess = Memory.strings.get(atomExpression);
				// Then, use the absolute access label and construct an
				// instruction that returns the address that the label is mapped to
				instruction = new ImcNAME(absoluteAccess.label);
				break;
		}
		ImcGen.exprImc.put(atomExpression, instruction);
		return instruction;
	}

	public ImcExpr callFunctionNewDel(String functionName, ImcExpr subexpressionInstruction, Stack<MemFrame> frames) {
		// We first visit the subexpression and then pretend that we are
		// calling a function new with result of the subexpression as
		// argument
		Vector<ImcExpr> arguments = new Vector<ImcExpr>();
		Vector<Long> offsets = new Vector<Long>();
		offsets.add(0L);

		// Get the calling function (current function) frame pointer and
		// add its static link to list of arguments
		MemFrame callingFunction = frames.peek();
		arguments.add(new ImcTEMP(callingFunction.FP));

		// Now add a second parameter, the subexpression instructions
		offsets.add(0L + new SemPointer(new SemVoid()).size());
		arguments.add(subexpressionInstruction);

		// The final instruction is a function call to a function named new
		return new ImcCALL(new MemLabel(functionName), offsets, arguments);
	}

	@Override
	public ImcExpr visit(AstPfxExpr prefixExpression, Stack<MemFrame> frames) {
		// First visit the subexpression and compute its instructions
		ImcExpr subexpressionInstruction = prefixExpression.expr().accept(this, frames);

		ImcExpr instruction = null;
		switch (prefixExpression.oper()) {
			case ADD:
				instruction = subexpressionInstruction;
				break;
			case SUB:
				instruction = new ImcUNOP(ImcUNOP.Oper.NEG, subexpressionInstruction);
				break;
			case NOT:
				instruction = new ImcUNOP(ImcUNOP.Oper.NOT, subexpressionInstruction);
				break;
			case PTR:
				// We are trying to get the address of the expression prefixExpression
				// remove the MEM instruction in front of it
				if (!(subexpressionInstruction instanceof ImcMEM))
					throw new Report.Error(prefixExpression, "Cannot access address of expression.");
				instruction = ((ImcMEM) subexpressionInstruction).addr;
				break;
			case NEW:
				instruction = callFunctionNewDel("new", subexpressionInstruction, frames);
				break;
			case DEL:
				instruction = callFunctionNewDel("del", subexpressionInstruction, frames);
				break;
		}
		ImcGen.exprImc.put(prefixExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstSfxExpr suffixExpression, Stack<MemFrame> frames) {
		// We are trying to get the value of the expression
		ImcExpr subexpressionInstruction = suffixExpression.expr().accept(this, frames);
		ImcExpr instruction = new ImcMEM(subexpressionInstruction);
		ImcGen.exprImc.put(suffixExpression, instruction);
		return instruction;
	}
	
	@Override
	public ImcExpr visit(AstBinExpr binaryExpression, Stack<MemFrame> frames) {
		ImcExpr firstInstruction = binaryExpression.fstExpr().accept(this, frames);
		ImcExpr secondInstruction = binaryExpression.sndExpr().accept(this, frames);

		// First, convert the operation from BinaryExpression.Oper enum to
		// String and then use String to construct a ImcBINOP.Oper enum
		// The BinaryExpression.Oper and ImcBINOP.Oper must match for this to work
		String operation = binaryExpression.oper().name();
		ImcBINOP.Oper operator = ImcBINOP.Oper.valueOf(operation);

		ImcExpr instruction = new ImcBINOP(operator, firstInstruction, secondInstruction);
		
		ImcGen.exprImc.put(binaryExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstCallExpr callExpression, Stack<MemFrame> frames) {
		// First get the called function declaration
		AstFunDecl functionDeclaration = (AstFunDecl) SemAn.declaredAt.get(callExpression);

		// Then, we should get the function frame from Memory.frames. This will
		// be pushed to the stack, but before, we have to calculate offsets and
		// argument values
		MemFrame functionFrame = Memory.frames.get(functionDeclaration);

		// The function is called from the last frame in the frames stack, we
		// have to somehow find a function that has a depth that is one lower
		// than current function frame. This will in our case always be a
		// function in stack above our function (CodeGenerator.java)
		MemFrame parentFrame = frames.peek();
		
		// If called function is one depth below the parent frame, it should
		// simply send its own frame pointer
		// If both functions are on the same depth, the calling function should
		// pass the MEM(TEMP(FP))
		// If calling function is deeper than called function, it should pass
		// the MEM(MEM(TEMP(FP)))
		ImcExpr staticLink = null;
		if (functionFrame.depth == 1)
			staticLink = new ImcCONST(0);
		else {
			staticLink = new ImcTEMP(parentFrame.FP);
			for (int i = 0; i <= parentFrame.depth - functionFrame.depth; i++)
				staticLink = new ImcMEM(staticLink);
		}

		// Compute the instructions for argument one after the other and add
		// them to a vector of arguments
		Vector<ImcExpr> argumentInstructions = new Vector<ImcExpr>();
		Vector<Long> argumentOffsets = new Vector<Long>();

		// The last argument is always static link, add it first
		argumentInstructions.add(staticLink);
		argumentOffsets.add(0L);

		long currentOffset = new SemPointer(new SemVoid()).size();
		for (AstExpr argument : callExpression.args()) {
			argumentInstructions.add(argument.accept(this, frames));
			argumentOffsets.add(currentOffset);

			// To compute offset for the next argument, first get the argument
			// semantic type, then get its size
			SemType semanticType = SemAn.ofType.get(argument);
			currentOffset += semanticType.actualType().size();
		}
		
		ImcExpr instruction = new ImcCALL(functionFrame.label, argumentOffsets, argumentInstructions);
		ImcGen.exprImc.put(callExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstStmtExpr statementExpression, Stack<MemFrame> frames) {
		Vector<ImcStmt> statementInstructions = new Vector<ImcStmt>();
		// Add all statement instructions except for the last one
		AstTrees<AstStmt> statements = statementExpression.stmts();
		for (int i = 0; i < statements.size() - 1; i++) {
			AstStmt statement = statements.get(i);
			ImcStmt statementInsctuction = statement.accept(new StmtGenerator(), frames);
			statementInstructions.add(statementInsctuction);
		}

		// We have visited all but the last statement, if the last statement is
		// a expression statement, that means we have to evaluate it and return
		// its value. Otherwise, we can add the last statement to the vector of
		// statement instructions.
		AstStmt lastStatement = statements.get(statements.size() - 1);
		ImcStmt lastStatementInsctuction = lastStatement.accept(new StmtGenerator(), frames);
		// The return value is 
		ImcExpr lastExpression = null;
		if (lastStatementInsctuction instanceof ImcESTMT) {
			// Get the last statement expression from instruction
			lastExpression = ((ImcESTMT) lastStatementInsctuction).expr;
		} else {
			// The last statement is not expression, return undefined value (404)
			statementInstructions.add(lastStatementInsctuction);
			lastExpression = new ImcCONST(404);
		}

		ImcStmt statementsInstruction = new ImcSTMTS(statementInstructions);
		ImcExpr instruction = new ImcSEXPR(statementsInstruction, lastExpression);
		ImcGen.exprImc.put(statementExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstCastExpr castExpression, Stack<MemFrame> frames) {
		ImcExpr instruction = castExpression.expr().accept(this, frames);
		castExpression.type().accept(this, frames);

		// If the semantic type is character, we should use the module operator
		// on previous instruction
		SemType type = SemAn.isType.get(castExpression.type());
		if (type.actualType() instanceof SemChar) {
			instruction = new ImcBINOP(ImcBINOP.Oper.MOD, instruction, new ImcCONST(256));
		}

		ImcGen.exprImc.put(castExpression, instruction);
		return instruction;
	}

	@Override
	public ImcExpr visit(AstWhereExpr whereExpression, Stack<MemFrame> frames) {
		whereExpression.decls().accept(new CodeGenerator(), frames);
		ImcExpr instruction = whereExpression.expr().accept(this, frames);
		ImcGen.exprImc.put(whereExpression, instruction);
		return instruction;
	}

}
