package prev.phase.imcgen;

import java.util.Stack;
import java.util.Vector;

import prev.data.ast.tree.stmt.AstAssignStmt;
import prev.data.ast.tree.stmt.AstCompoundStmt;
import prev.data.ast.tree.stmt.AstExprStmt;
import prev.data.ast.tree.stmt.AstIfStmt;
import prev.data.ast.tree.stmt.AstStmt;
import prev.data.ast.tree.stmt.AstWhileStmt;
import prev.data.ast.visitor.AstFullVisitor;
import prev.data.imc.code.expr.ImcExpr;
import prev.data.imc.code.stmt.ImcCJUMP;
import prev.data.imc.code.stmt.ImcESTMT;
import prev.data.imc.code.stmt.ImcJUMP;
import prev.data.imc.code.stmt.ImcLABEL;
import prev.data.imc.code.stmt.ImcMOVE;
import prev.data.imc.code.stmt.ImcSTMTS;
import prev.data.imc.code.stmt.ImcStmt;
import prev.data.mem.MemFrame;
import prev.data.mem.MemLabel;

// public class StmtGenerator extends AstNullVisitor<Object, Stack<MemFrame>> {
public class StmtGenerator extends AstFullVisitor<ImcStmt, Stack<MemFrame>> {

	@Override
	public ImcStmt visit(AstExprStmt expressionStatement, Stack<MemFrame> frames) {
		ImcExpr expressionInstruction = expressionStatement.expr().accept(new ExprGenerator(), frames);
		ImcStmt instruction = new ImcESTMT(expressionInstruction);
		ImcGen.stmtImc.put(expressionStatement, instruction);
		return instruction;
	}

	@Override
	public ImcStmt visit(AstAssignStmt assignmentStatement, Stack<MemFrame> frames) {
		ImcExpr destinationInstruction = assignmentStatement.dst().accept(new ExprGenerator(), frames);
		ImcExpr sourceInstruction = assignmentStatement.src().accept(new ExprGenerator(), frames);

		ImcStmt instruction = new ImcMOVE(destinationInstruction, sourceInstruction);
		ImcGen.stmtImc.put(assignmentStatement, instruction);
		return instruction;
	}

	@Override
	public ImcStmt visit(AstCompoundStmt compoundStatement, Stack<MemFrame> frames) {
		Vector<ImcStmt> statementInstructions = new Vector<ImcStmt>();
		
		for (AstStmt statement: compoundStatement.stmts())
			statementInstructions.add(statement.accept(this, frames));
		
		ImcStmt instruction = new ImcSTMTS(statementInstructions);
		ImcGen.stmtImc.put(compoundStatement, instruction);
		return instruction;
	}

	@Override
	public ImcStmt visit(AstIfStmt ifStatement, Stack<MemFrame> frames) {
		// If statement consists of a list of statements
		Vector<ImcStmt> statements = new Vector<ImcStmt>();

		// The if statement if cond then thenStmt else elseStmt should be transformed to
		// 		CJUMP cond pos neg
		// pos	...	; thenStmt code
		// 		JMP end
		// neg	... ; elseStmt code
		// end	... ; label end

		ImcExpr conditionInstruction = ifStatement.cond().accept(new ExprGenerator(), frames);

		// Create a new and positive, negative and end label
		ImcLABEL positiveLabel = new ImcLABEL(new MemLabel());
		ImcLABEL negativeLabel = new ImcLABEL(new MemLabel());
		ImcLABEL endLabel = new ImcLABEL(new MemLabel());

		statements.add(new ImcCJUMP(conditionInstruction, positiveLabel.label, negativeLabel.label));
		statements.add(positiveLabel);
		statements.add(ifStatement.thenStmt().accept(this, frames));
		statements.add(new ImcJUMP(endLabel.label));
		statements.add(negativeLabel);
		statements.add(ifStatement.elseStmt().accept(this, frames));
		statements.add(endLabel);

		ImcStmt instruction = new ImcSTMTS(statements);;
		ImcGen.stmtImc.put(ifStatement, instruction);
		return instruction;
	}

	@Override
	public ImcStmt visit(AstWhileStmt whileStatement, Stack<MemFrame> frames) {
		// While statement consists of a list of statements
		Vector<ImcStmt> statements = new Vector<ImcStmt>();

		// The while statement while cond do bodyStmt should be transformed to
		// cond		CJUMP cond loop end
		// loop		... ; bodyStmt code
		//			JMP start
		// end		... ; end label
		ImcExpr conditionInstruction = whileStatement.cond().accept(new ExprGenerator(), frames);

		ImcLABEL conditionLabel = new ImcLABEL(new MemLabel());
		ImcLABEL loopLabel = new ImcLABEL(new MemLabel());
		ImcLABEL endLabel = new ImcLABEL(new MemLabel());

		statements.add(conditionLabel);
		statements.add(new ImcCJUMP(conditionInstruction, loopLabel.label, endLabel.label));
		statements.add(loopLabel);
		statements.add(whileStatement.bodyStmt().accept(this, frames));
		statements.add(new ImcJUMP(conditionLabel.label));
		statements.add(endLabel);

		ImcStmt instruction = new ImcSTMTS(statements);;
		ImcGen.stmtImc.put(whileStatement, instruction);
		return instruction;
	}

}
