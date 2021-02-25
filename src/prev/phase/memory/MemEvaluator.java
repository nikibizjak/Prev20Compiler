package prev.phase.memory;

import prev.data.ast.tree.decl.AstCompDecl;
import prev.data.ast.tree.decl.AstFunDecl;
import prev.data.ast.tree.decl.AstParDecl;
import prev.data.ast.tree.decl.AstVarDecl;
import prev.data.ast.tree.expr.AstAtomExpr;
import prev.data.ast.tree.expr.AstCallExpr;
import prev.data.ast.tree.expr.AstExpr;
import prev.data.ast.tree.expr.AstPfxExpr;
import prev.data.ast.tree.type.AstRecType;
import prev.data.ast.visitor.AstFullVisitor;
import prev.data.mem.MemAbsAccess;
import prev.data.mem.TemporaryAccess;
import prev.data.mem.MemFrame;
import prev.data.mem.MemLabel;
import prev.data.mem.MemRelAccess;
import prev.data.semtype.SemChar;
import prev.data.semtype.SemPointer;
import prev.data.semtype.SemType;
import prev.data.semtype.SemVoid;
import prev.phase.seman.SemAn;

/**
 * Computing memory layout: frames and accesses.
 */
public class MemEvaluator extends AstFullVisitor<Object, MemEvaluator.Context> {

	/**
	 * The context {@link MemEvaluator} uses while computing function frames and
	 * variable accesses.
	 */
	protected abstract class Context {
	}

	/**
	 * Functional context, i.e., used when traversing function and building a new
	 * frame, parameter acceses and variable acceses.
	 */
	private class FunContext extends Context {
		public int depth = 0;
		public long locsSize = 0;
		public long argsSize = 0;
		public long parsSize = new SemPointer(new SemVoid()).size();
	}

	/**
	 * Record context, i.e., used when traversing record definition and computing
	 * record component acceses.
	 */
	private class RecContext extends Context {
		public long compsSize = 0;
	}

	@Override
	public Object visit(AstAtomExpr atomExpression, Context context) {
		if (atomExpression.type() == AstAtomExpr.Type.STRING) {
			// The string memory access is probably the simplest, we should
			// first create a new label (can be named or anonymous) and
			// calculate our data type length. As string is a sequence of
			// characters, its length should be the number of characters * size
			// of one character. We can initialize memory access with initial
			// value of string value.

			// Create a new anonymous label (Lx)
			MemLabel label = new MemLabel();

			// Calculate the size of our string
			int stringLength = atomExpression.value().length();
			long size = stringLength * (new SemChar()).size();

			MemAbsAccess memoryAccess = new MemAbsAccess(size, label, atomExpression.value());
			Memory.strings.put(atomExpression, memoryAccess);
		}
		return null;
	}


	@Override
	public Object visit(AstFunDecl functionDeclaration, Context context) {
		// If the context is null, that means that this is a function defined in
		// program body (program scope). We have to create a new function
		// context and pass it to children. If the context is non null, create a
		// new context and use its depth to compute new depth.
		FunContext functionContext = new FunContext();
		if (context != null) {
			functionContext.depth = ((FunContext) context).depth + 1;
		} else {
			functionContext.depth = 1;
		}

		// Now that we have created a new functionContext, we can visit our
		// children and pass them this context
		if (functionDeclaration.pars() != null)
			functionDeclaration.pars().accept(this, functionContext);
		if (functionDeclaration.type() != null)
			functionDeclaration.type().accept(this, functionContext);
		if (functionDeclaration.expr() != null)
			functionDeclaration.expr().accept(this, functionContext);

		// After visitor has returned from parameters, function type and
		// expression, we can create a new frame. The children have already
		// changed functionContext parameters, now we can use this values to
		// construct function frame.
		
		// We can actually construct function label from name, because we can
		// not redefine functions. This can only be done for functions that have
		// depth equal one. Every other function must have anonymous label.

		MemLabel functionLabel;
		if (functionContext.depth <= 1) {
			functionLabel = new MemLabel(functionDeclaration.name());
		} else {
			// Create an anonymous function
			functionLabel = new MemLabel();
		}
		// Use function context to construct function memory frame
		MemFrame functionFrame = new MemFrame(functionLabel, functionContext.depth, functionContext.locsSize, functionContext.argsSize);
		
		Memory.frames.put(functionDeclaration, functionFrame);
		return null;
	}

	@Override
	public Object visit(AstVarDecl variableDeclaration, Context context) {
		// First visit declaration type and get its semantic type
		if (variableDeclaration.type() != null)
			variableDeclaration.type().accept(this, context);
		
		// Lets assume that semantic type is not null
		SemType semanticType = SemAn.isType.get(variableDeclaration.type());
		
		// If context is null, this variable is defined in the main program (it
		// is not inside any function). We will be able to access this variable
		// with absolute address.
		if (context == null) {
			// We can create a new label with the name of the variable
			MemLabel variableLabel = new MemLabel(variableDeclaration.name());
			MemAbsAccess memoryAccess = new MemAbsAccess(semanticType.size(), variableLabel);
			Memory.accesses.put(variableDeclaration, memoryAccess);
			return null;
		}
		
		// If the function context is not null, this means that we are inside
		// function, so this variable is local and should be accessed to using
		// relative offset. Here, we must add this variable declaration to
		// Memory.accesses attribute and change the current function context
		// locals size (locsSize variable)
		FunContext functionContext = (FunContext) context;

		Boolean isRegisterRepresentable = Memory.isRegisterRepresentable.get(variableDeclaration);
		if (isRegisterRepresentable != null && isRegisterRepresentable.booleanValue()) {
			Memory.accesses.put(variableDeclaration, new TemporaryAccess());
			return null;
		}

		// Compute the offset (local variables have negative offset values)
		long offset = -functionContext.locsSize - semanticType.size();
		MemRelAccess memoryAccess = new MemRelAccess(semanticType.size(), offset, functionContext.depth);
		Memory.accesses.put(variableDeclaration, memoryAccess);
		
		functionContext.locsSize += semanticType.size();
		
		return null;
	}

	@Override
	public Object visit(AstParDecl parameterDeclaration, Context context) {
		if (parameterDeclaration.type() != null)
			parameterDeclaration.type().accept(this, context);
		
		FunContext functionContext = (FunContext) context;
		
		// If we are visiting parameter declaration, that means we are
		// constructing a new function frame. We must add parameter declaration
		// to Memory.accesses attribute. We can assume that semantic type is
		// never null.
		SemType semanticType = SemAn.isType.get(parameterDeclaration.type());

		// Compute the offset (parameters have positive offset values)
		MemRelAccess memoryAccess = new MemRelAccess(semanticType.size(), functionContext.parsSize, functionContext.depth);
		Memory.accesses.put(parameterDeclaration, memoryAccess);

		functionContext.parsSize += semanticType.size();
		
		return null;
	}

	@Override
	public Object visit(AstCallExpr callExpression, Context context) {
		// Inside the call expression, we should calculate the new
		// context.argsSize as the maximum of size of all arguments of all
		// function calls inside function context
		if (callExpression.args() != null)
			callExpression.args().accept(this, context);
		
		// The context will always be FunContext because we can't have call
		// expression inside record
		FunContext functionContext = (FunContext) context;

		// Calculate the total size of all call arguments
		long totalSize = 0;
		for (AstExpr argument : callExpression.args()) {
			// Get argument semantic type
			SemType semanticType = SemAn.ofType.get(argument);
			totalSize += semanticType.size();
		}

		// Add the size of the SL pointer after arguments
		totalSize += new SemPointer(new SemVoid()).size();
		
		// If total size of function call parameters is bigger that the current
		// arguments size, change it
		functionContext.argsSize = Math.max(functionContext.argsSize, totalSize);

		return null;
	}

	@Override
	public Object visit(AstRecType recordType, Context context) {
		// Everytime we visit a new record type, we should create a new record
		// context and pass it to its component declarations
		RecContext recordContext = new RecContext();

		if (recordType.comps() != null)
			recordType.comps().accept(this, recordContext);

		return null;
	}

	@Override
	public Object visit(AstCompDecl componentDeclaration, Context context) {
		if (componentDeclaration.type() != null)
			componentDeclaration.type().accept(this, context);
		
		SemType semanticType = SemAn.isType.get(componentDeclaration.type());
		
		// When visiting component declaration, the context should be
		// RecContext, as components can only be defined inside records
		RecContext recordContext = (RecContext) context;
		
		// Construct a new relative memory access (the depth should be 0 as
		// defined inside MemRelAccess class)
		MemRelAccess memoryAccess = new MemRelAccess(semanticType.size(), recordContext.compsSize, 0);

		// Update current size inside current context
		recordContext.compsSize += semanticType.size();
		
		Memory.accesses.put(componentDeclaration, memoryAccess);
		
		return null;
	}

	public Object visit(AstPfxExpr prefixExpression, Context context) {
		FunContext functionContext = (FunContext) context;

		// The total size of argument when calling new or del is 2. The first
		// parameter is static link and the second is a size of memory that we
		// want or pointer to memory that we want to free.
		long totalSize = 2 * new SemPointer(new SemVoid()).size();
		
		// If total size of function call parameters is bigger that the current
		// arguments size, change it
		functionContext.argsSize = Math.max(functionContext.argsSize, totalSize);

		return null;
	}

}
