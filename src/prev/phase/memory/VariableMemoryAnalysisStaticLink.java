package prev.phase.memory;

import prev.data.ast.visitor.AstFullVisitor;
import prev.data.mem.MemAbsAccess;
import prev.data.mem.MemFrame;
import prev.data.mem.MemLabel;
import prev.data.mem.MemRelAccess;
import prev.data.semtype.SemChar;
import prev.data.semtype.SemPointer;
import prev.data.semtype.SemType;
import prev.data.semtype.SemVoid;
import prev.phase.seman.SemAn;
import prev.phase.memory.Memory;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.common.report.*;
import prev.data.semtype.*;
import java.util.*;

/**
 * Compute which variables can be represented in registers and which must be put
 * in memory.
 */
public class VariableMemoryAnalysisStaticLink extends AstFullVisitor<Object, VariableMemoryAnalysisStaticLink.Data> {

    public static HashMap<AstDecl, FunctionContext> functionVariables = new HashMap<AstDecl, FunctionContext>();

    public static Data first() {
        return new Data(null, Mode.FIRST);
    }

    public static Data second() {
        return new Data(null, Mode.SECOND);
    }

    static class FunctionContext {
        public final int depth;
        public FunctionContext(int depth) {
            this.depth = depth;
        }
    }

    public enum Mode { FIRST, SECOND };

    static class Data {
        public final FunctionContext context;
        public final Mode mode;
        public Data(FunctionContext context, Mode mode) {
            this.context = context;
            this.mode = mode;
        }
    }

    @Override
    public Object visit(AstFunDecl functionDeclaration, Data data) {
        FunctionContext context = null;
        if (data.context == null) {
            context = new FunctionContext(1);
        } else {
            context = new FunctionContext(data.context.depth + 1);
        }
        Data newData = new Data(context, data.mode);

        // Now that we have created a new functionContext, we can visit our
        // children and pass them this context
        if (functionDeclaration.pars() != null)
            functionDeclaration.pars().accept(this, newData);
        if (functionDeclaration.type() != null)
            functionDeclaration.type().accept(this, newData);
        if (functionDeclaration.expr() != null)
            functionDeclaration.expr().accept(this, newData);

        return null;
    }

    @Override
    public Object visit(AstVarDecl variableDeclaration, Data data) {
        // First visit declaration type and get its semantic type
        if (variableDeclaration.type() != null)
            variableDeclaration.type().accept(this, data);
        
        // This is a global variable.
        if (data.context == null)
            return null;

        functionVariables.put(variableDeclaration, data.context);
        
        return null;
    }

    @Override
    public Object visit(AstNameExpr nameExpression, Data data) {
        if (data.mode != Mode.SECOND)
            return null;
        
        AstDecl declaration = SemAn.declaredAt.get(nameExpression);
        FunctionContext variableFunction = functionVariables.get(declaration);

        if (variableFunction == null) {
            // This variable is not a local variable, skip it
            return null;
        }

        if (variableFunction.depth != data.context.depth) {
            Memory.isRegisterRepresentable.put(declaration, false);
        }
        return null;
    }

}