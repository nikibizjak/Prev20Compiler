package prev.phase.seman;

import java.util.HashMap;
import java.util.Vector;
import prev.common.report.*;
import prev.data.ast.tree.*;
import prev.data.ast.tree.decl.*;
import prev.data.ast.tree.expr.*;
import prev.data.ast.tree.type.*;
import prev.data.ast.tree.stmt.*;
import prev.data.ast.visitor.*;
import prev.data.semtype.*;

public class TypeResolver extends AstFullVisitor<SemType, TypeResolver.Mode> {

    // We have 5 steps - two for type, one for variable and two for function declarations
    
    // I have also added another step SPECIAL, which is the third step
    // in the third step, the array and record types are resolved, as
    // they have more complicated logic (we cannot have array of voids)
    public enum Mode {
		HEAD, BODY, SPECIAL
    }

    // This is used for record type scopes
    private HashMap<SemRecord, SymbTable> symbolTables = new HashMap<>();

    // General case - AstTrees of AstTree
    @Override
	public SemType visit(AstTrees<? extends AstTree> trees, Mode arg) {
        // First execute phases one and two only on type declarations
        for (AstTree tree : trees)
            if (tree instanceof AstTypeDecl)
                tree.accept(this, Mode.HEAD);

        for (AstTree tree : trees)
            if (tree instanceof AstTypeDecl)
                tree.accept(this, Mode.BODY);
        
        // Phase two-and-a-half is used to resolve records and arrays
        for (AstTree tree : trees)
            if (tree instanceof AstTypeDecl)
                tree.accept(this, Mode.SPECIAL);

        // Phase three in only exexuted on variable declarations
        for (AstTree tree : trees)
            if (tree instanceof AstVarDecl)
                tree.accept(this, Mode.HEAD);            

        // Phases four and five are executed on function declarations
        for (AstTree tree : trees)
            if (tree instanceof AstFunDecl)
                tree.accept(this, Mode.HEAD);
        
        for (AstTree tree : trees)
            if (tree instanceof AstFunDecl)
                tree.accept(this, Mode.BODY);

        return null;
    }

    @Override
    public SemType visit(AstTypeDecl typeDeclaration, Mode phase) {
        switch (phase) {
            case HEAD:
                // This is only step one, simply create a new SemName(type, ?)
                SemAn.declaresType.put(typeDeclaration, new SemName(typeDeclaration.name()));
                break;
            case BODY:
                // In step two, we first have to visit type with our visitor,
                // but we will only do that for types other than arrays and records
                if (typeDeclaration.type() instanceof AstRecType || typeDeclaration.type() instanceof AstArrType)
                    return null;
                
                SemType semanticType = typeDeclaration.type().accept(this, phase);
                
                // Get the SemName that we have saved in the first phase
                SemName semanticName = SemAn.declaresType.get(typeDeclaration);
                semanticName.define(semanticType);
                break;
            case SPECIAL:
                // This is step 2.5, where we will only resolve arrays and records
                // because we will need all other types to already be computed
                if (typeDeclaration.type() instanceof AstRecType || typeDeclaration.type() instanceof AstArrType) {
                    SemType semanticTypeSpecial = typeDeclaration.type().accept(this, phase);
                
                    // Get the SemName that we have saved in the first phase
                    SemName semanticNameSpecial = SemAn.declaresType.get(typeDeclaration);
                    semanticNameSpecial.define(semanticTypeSpecial);
                }
                break;
        }

        return null;
    }

    @Override
    public SemType visit(AstVarDecl variableDeclaration, Mode arg) {
        SemType type = variableDeclaration.type().accept(this, arg);
        if (type.actualType() instanceof SemVoid)
            throw new Report.Error(variableDeclaration, "Variable '" + variableDeclaration.name() + "' must not be of type void.");
        return null;
    }

    @Override
    public SemType visit(AstFunDecl functionDeclaration, Mode phase) {
        switch (phase) {
            case HEAD:
                // In head, only visit function parameters and return type
                for (AstParDecl parameterDeclaration: functionDeclaration.pars()) {
                    parameterDeclaration.accept(this, phase);
                }

                // Check if function return type is actually correct
                SemType functionReturnType = functionDeclaration.type().accept(this, phase);
                if (functionReturnType.actualType() instanceof SemArray || functionReturnType.actualType() instanceof SemRecord)
                    throw new Report.Error(functionDeclaration, "Function '" + functionDeclaration.name() + "' return type must not be record or an array.");
                break;
            case BODY:
                // In body, actually visit function body
                SemType bodyType = functionDeclaration.expr().accept(this, phase);

                // Check if body return type is actually the same as defined return type
                SemType expectedReturnType = SemAn.isType.get(functionDeclaration.type());

                if (!sameType(bodyType, expectedReturnType))
                    throw new Report.Error(functionDeclaration, "Function '" + functionDeclaration.name() + "' should return '" + getTypeName(expectedReturnType.actualType()) + "' but actually returns '" + getTypeName(bodyType.actualType()) + "'.");

                break;
            default: break;
        }
        return null;
    }

    @Override
    public SemType visit(AstParDecl parameterDeclaration, Mode phase) {
        SemType parameterType = parameterDeclaration.type().accept(this, phase);
        if (parameterType.actualType() instanceof SemVoid || parameterType.actualType() instanceof SemArray || parameterType.actualType() instanceof SemRecord)
            throw new Report.Error(parameterDeclaration, "'" + (parameterDeclaration.name()) + "' must not be void, record or an array");
        return null;
    }

    @Override
    public SemType visit(AstAtomType atomType, Mode arg) {
        SemType semanticType;
        switch (atomType.type()) {
            case VOID: semanticType = new SemVoid(); break;
            case CHAR: semanticType = new SemChar(); break;
            case INTEGER: semanticType = new SemInteger(); break;
            case BOOLEAN: semanticType = new SemBoolean(); break;
            default: throw new Report.InternalError();
        }
        SemAn.isType.put(atomType, semanticType);
        return semanticType;
    }

    @Override
    public SemType visit(AstNameType nameType, Mode arg) {
        // We have visited AstNameType, which means that we want to create a new
        // synonym for our type.
        AstDecl declaration = SemAn.declaredAt.get(nameType);

        // There is a possibility that our declaration is not AstTypeDecl but
        // AstFunDecl or AstVarDecl, but I think that has already been taken
        // care of in the NameResolver
        if (!(declaration instanceof AstTypeDecl))
            throw new Report.Error(nameType, "'" + (nameType.name()) + "' must be a type.");

        // Find a type of our AstTypeDeclaration (it must exist because this
        // function will only be called in the second stage of type resolving)
        SemType type = SemAn.declaresType.get((AstTypeDecl) declaration);

        SemAn.isType.put(nameType, type);
        return type;
    }

    @Override
    public SemType visit(AstPtrType pointerType, Mode arg) {
        // First, visit the type that the pointer is pointing to. After it has
        // returned, its isType has been set. Visitor will also return the
        // semantic type of the type pointer points to
        SemType pointingToType = pointerType.baseType().accept(this, arg);

        SemType type = new SemPointer(pointingToType);
        SemAn.isType.put(pointerType, type);
        return type;
    }

    @Override
    public SemType visit(AstArrType arrayType, Mode arg) {
        // This will only work if the array numElems is AstAtomExpr
        if (!(arrayType.numElems() instanceof AstAtomExpr && ((AstAtomExpr) arrayType.numElems()).type() == AstAtomExpr.Type.INTEGER))
            throw new Report.Error(arrayType, "Number of elements of array must be an integer");
        
        /* PROBLEM: When we are constructing a record like so
         * typ BinaryTree = { children: BinaryTree[2] }
         * the BinaryTree has been set to SemName("BinaryTree", null)
         * when we come to the AstArrType resolving, we need to
         * check if the actual type of "BinaryTree" is not void.
         * But we don't know actual type yet, because we have not yet
         * resolved the record type. WHAT DO WE DO?
         */
        SemType arrayElementType = arrayType.elemType().accept(this, arg);
        try {
            if (arrayElementType.actualType() instanceof SemVoid)
                throw new Report.Error(arrayType, "Array type cannot be void");
        } catch (NullPointerException e) {
            // This should only ever be able to happen in the case that I
            // have writen above, do nothing
        }
        
        long numberOfElements = Long.parseLong(((AstAtomExpr) arrayType.numElems()).value());
        if (numberOfElements > Math.pow(2, 63))
            throw new Report.Error(arrayType, "Array element number cannot be this big.");
        else if (numberOfElements <= 0)
            throw new Report.Error(arrayType, "Array element number cannot be negative.");
        
        SemType semanticType = new SemArray(arrayElementType, numberOfElements);
        SemAn.isType.put(arrayType, semanticType);

        return semanticType;
    }

    @Override
    public SemType visit(AstCompDecl componentDeclaration, Mode arg) {
        SemType componentSemanticType = componentDeclaration.type().accept(this, arg);
        return componentSemanticType;
    }

    @Override
    public SemType visit(AstRecType recordType, Mode arg) {
        // We must create a new symbol table, because each record has a
        // different set of types
        SymbTable symbolTable = new SymbTable();

        // In the record type, first visit all component types
        Vector<SemType> semanticTypes = new Vector<>();
        for (AstCompDecl component : recordType.comps()) {
            // Try inserting component name into symbol table, if it raises an
            // exception, the name has already been defined inside this record
            try {
                symbolTable.ins(component.name(), component);
            } catch (SymbTable.CannotInsNameException __) {
				throw new Report.Error(component, "Cannot redefine component '" + (component.name()) + "'.");
            }
            
            // Visit the component type and calculate its semantic type
            SemType componentSemanticType = component.accept(this, arg);
            try {
                if (componentSemanticType.actualType() instanceof SemVoid)
                    throw new Report.Error(recordType, "Record component type cannot not be void.");
                semanticTypes.add(componentSemanticType);
            } catch (NullPointerException e) {
                // The component is probably trying to point at itself
                // we should probably just alow it
                semanticTypes.add(componentSemanticType);
            }
        }

        // Then build new record type from returned types
        SemRecord semanticType = new SemRecord(semanticTypes);
        symbolTables.put(semanticType, symbolTable);

        SemAn.isType.put(recordType, semanticType);
        return semanticType;
    }

    // Value expressions
    @Override
    public SemType visit(AstAtomExpr atomExpression, Mode arg) {
        SemType expressionType;
        switch (atomExpression.type()) {
            case VOID: expressionType = new SemVoid(); break;
            case CHAR: expressionType = new SemChar(); break;
            case INTEGER: expressionType = new SemInteger(); break;
            case BOOLEAN: expressionType = new SemBoolean(); break;
            case POINTER: expressionType = new SemPointer(new SemVoid()); break;
            case STRING: expressionType = new SemPointer(new SemChar()); break;
            default: throw new Report.Error(atomExpression, "Invalid atom expression");
        }
        SemAn.ofType.put(atomExpression, expressionType);
        return expressionType;
    }

    @Override
    public SemType visit(AstPfxExpr prefixExpression, Mode arg) {
        // First, visit expression child and calculate its type
        SemType subexpressionType = prefixExpression.expr().accept(this, arg);

        SemType expressionType = null;

        switch (prefixExpression.oper()) {
            case NOT:
                if (subexpressionType.actualType() instanceof SemBoolean) {
                    expressionType = new SemBoolean();
                } else {
                    throw new Report.Error(prefixExpression, "Cannot negate expression that is not of type boolean.");
                }
                break;
            case ADD:
            case SUB:
                if (subexpressionType.actualType() instanceof SemInteger) {
                    expressionType = new SemInteger();
                } else {
                    throw new Report.Error(prefixExpression, "Cannot negate expression that is not of type integer.");
                }
                break;
            case NEW:
                if (subexpressionType.actualType() instanceof SemInteger) {
                    expressionType = new SemPointer(new SemVoid());
                } else {
                    throw new Report.Error(prefixExpression, "Cannot use the NEW keyword on type that is not integer.");
                }
                break;
            case DEL:
                if (subexpressionType.actualType() instanceof SemPointer) {
                    expressionType = new SemVoid();
                } else {
                    throw new Report.Error(prefixExpression, "Cannot use the DEL keyword on type that is not a pointer.");
                }
                break;
            case PTR:
                expressionType = new SemPointer(subexpressionType);
                break;
        }

        SemAn.ofType.put(prefixExpression, expressionType);
        return expressionType;
    }

    @Override
    public SemType visit(AstSfxExpr suffixExpression, Mode arg) {
        SemType subexpressionType = suffixExpression.expr().accept(this, arg);
        SemType expressionType = null;
        
        if (subexpressionType.actualType() instanceof SemPointer) {
            expressionType = ((SemPointer) subexpressionType.actualType()).baseType();
        } else {
            throw new Report.Error(suffixExpression, "Expression is not a pointer");
        }

        SemAn.ofType.put(suffixExpression, expressionType);
        return expressionType;
    }

    @Override
    public SemType visit(AstBinExpr binaryExpression, Mode arg) {
        SemType firstType = binaryExpression.fstExpr().accept(this, arg);
        SemType secondType = binaryExpression.sndExpr().accept(this, arg);

        SemType expressionType = null;

        switch (binaryExpression.oper()) {
            case OR:
            case AND:
                if (firstType.actualType() instanceof SemBoolean && secondType.actualType() instanceof SemBoolean) {
                    expressionType = new SemBoolean();
                } else {
                    throw new Report.Error(binaryExpression, "Operator '" + binaryExpression.oper() + "' undefined for types.");
                }
                break;
            case EQU:
            case NEQ:
                if (firstType.actualType() instanceof SemBoolean && secondType.actualType() instanceof SemBoolean ||
                firstType.actualType() instanceof SemChar && secondType.actualType() instanceof SemChar ||
                firstType.actualType() instanceof SemInteger && secondType.actualType() instanceof SemInteger ||
                firstType.actualType() instanceof SemPointer && secondType.actualType() instanceof SemPointer) {
                    expressionType = new SemBoolean();
                } else {
                    throw new Report.Error(binaryExpression, "Operator '" + binaryExpression.oper() + "' undefined for types.");
                }
                break;
            case LTH:
            case GTH:
            case LEQ:
            case GEQ:
                if (firstType.actualType() instanceof SemChar && secondType.actualType() instanceof SemChar ||
                firstType.actualType() instanceof SemInteger && secondType.actualType() instanceof SemInteger ||
                firstType.actualType() instanceof SemPointer && secondType.actualType() instanceof SemPointer) {
                    expressionType =  new SemBoolean();
                } else {
                    throw new Report.Error(binaryExpression, "Operator '" + binaryExpression.oper() + "' undefined for types.");
                }
                break;
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
                if (firstType.actualType() instanceof SemInteger && secondType.actualType() instanceof SemInteger) {
                    expressionType = new SemInteger();
                } else {
                    throw new Report.Error(binaryExpression, "Operator '" + binaryExpression.oper() + "' undefined for types.");
                }
                break;
        }

        SemAn.ofType.put(binaryExpression, expressionType);
        return expressionType;
    }

    @Override
    public SemType visit(AstNameExpr nameExpression, Mode arg) {
        // When visiting name expression, we can either visit function argument,
        // variable name or component declaration
        AstDecl declaration = SemAn.declaredAt.get(nameExpression);

        if (declaration instanceof AstVarDecl) {
            // We are trying to access the variable defined in the file
            AstVarDecl variableDeclaration = (AstVarDecl) declaration;
            SemType semanticType = SemAn.isType.get(variableDeclaration.type());
            SemAn.ofType.put(nameExpression, semanticType);
            return semanticType;
        } else if (declaration instanceof AstParDecl) {
            // We are trying to access parameter declared in current function
            AstParDecl parameterDeclaration = (AstParDecl) declaration;
            SemType semanticType = SemAn.isType.get(parameterDeclaration.type());
            SemAn.ofType.put(nameExpression, semanticType);
            return semanticType;
        } else if (declaration instanceof AstCompDecl) {
            AstCompDecl componentDeclaration = (AstCompDecl) declaration;
            SemType semanticType = SemAn.isType.get(componentDeclaration.type());
            SemAn.ofType.put(nameExpression, semanticType);
            return semanticType;
        } else {
            throw new Report.Error(nameExpression, "Unknown declaration type."); 
        }
    }

    @Override
    public SemType visit(AstArrExpr arrayExpression, Mode arg) {
        SemType arrayType = arrayExpression.arr().accept(this, arg);
        if (!(arrayType.actualType() instanceof SemArray))
            throw new Report.Error(arrayExpression, "Array expression must be array type.");

        SemType arrayIndexType = arrayExpression.idx().accept(this, arg);
        if (!(arrayIndexType.actualType() instanceof SemInteger))
            throw new Report.Error(arrayExpression, "Array index must be integer type.");

        SemType semanticType = ((SemArray) arrayType.actualType()).elemType();
        SemAn.ofType.put(arrayExpression, semanticType);
        return semanticType;
    }

    @Override
    public SemType visit(AstRecExpr recordExpression, Mode arg) {
        // Check if the recordExpression record is actually of record type
        SemType recordType = recordExpression.rec().accept(this, arg);
        if (!(recordType.actualType() instanceof SemRecord))
            throw new Report.Error(recordExpression, "Record expression must be record type.");
        
        // Find the correct symbol table for this record
        SymbTable symbolTable = symbolTables.get(recordType.actualType());

        // Check if the name that we want to access to actually exists in this
        // symbol table
        try {
            AstDecl declaration = symbolTable.fnd(recordExpression.comp().name());
            if (!(declaration instanceof AstCompDecl))
                throw new Report.Error(recordExpression, "'" + (recordExpression.comp().name()) + "' is not a record component name.");
            
            AstCompDecl componentDeclaration = (AstCompDecl) declaration;
            AstType componentType = componentDeclaration.type();
            SemType type = SemAn.isType.get(componentType);

            SemAn.declaredAt.put(recordExpression.comp(), declaration);
            
            SemAn.ofType.put(recordExpression, type);
            return type;
            
        } catch (SymbTable.CannotFndNameException __) {
            throw new Report.Error(recordExpression, "Record has no component named '" + (recordExpression.comp().name()) + "'.");
        }
    }

    @Override
    public SemType visit(AstStmtExpr statements, Mode arg) {
        SemType lastType = null;
        for (AstStmt statement : statements.stmts()) {
            lastType = statement.accept(this, arg);
        }
        SemAn.ofType.put(statements, lastType);
        return lastType;
    }

    @Override
    public SemType visit(AstCompoundStmt compoundStatements, Mode arg) {
        SemType lastType = null;
        for (AstStmt statement : compoundStatements.stmts()) {
            lastType = statement.accept(this, arg);
        }
        SemAn.ofType.put(compoundStatements, lastType);
        return lastType;
    }

    @Override
    public SemType visit(AstExprStmt expressionStatement, Mode arg) {
        SemType type = expressionStatement.expr().accept(this, arg);
        SemAn.ofType.put(expressionStatement, type);
        return type;
    }

    @Override
    public SemType visit(AstCastExpr castExpression, Mode arg) {
        SemType castType = castExpression.type().accept(this, arg);
        SemType expressionType = castExpression.expr().accept(this, arg);

        if (!(expressionType.actualType() instanceof SemChar ||
        expressionType.actualType() instanceof SemInteger ||
        expressionType.actualType() instanceof SemPointer))
            throw new Report.Error(castExpression, "Expression type must be either char, integer or pointer.");

        if (!(castType.actualType() instanceof SemChar ||
        castType.actualType() instanceof SemInteger ||
        castType.actualType() instanceof SemPointer))
            throw new Report.Error(castExpression, "Cast type must be either char, integer or pointer.");

        SemAn.ofType.put(castExpression, castType);
        return castType;
    }

    @Override
    public SemType visit(AstCallExpr functionCallExpression, Mode arg) {
        //AstCallExpr(Location location, String name, AstTrees<AstExpr> args)
        AstDecl declaration = SemAn.declaredAt.get(functionCallExpression);
        if (!(declaration instanceof AstFunDecl))
            throw new Report.Error(functionCallExpression, "Identifier '" + functionCallExpression.name() + "' is not a function.");
        AstFunDecl functionDeclaration = (AstFunDecl) declaration;

        // Check if number of arguments matches number of parameters
        if (functionCallExpression.args().size() != functionDeclaration.pars().size())
            throw new Report.Error(functionCallExpression, "Number of arguments when calling function '" + functionCallExpression.name() + "' does not match function declaration.");

        // Check if types of our arguments match
        for (int i = 0; i < functionCallExpression.args().size(); i++) {
            AstExpr expression = functionCallExpression.args().get(i);
            SemType argumentType = expression.accept(this, arg);

            AstParDecl parameterDeclaration = functionDeclaration.pars().get(i);
            SemType parameterType = SemAn.isType.get(parameterDeclaration.type());

            if (!sameType(argumentType, parameterType))
                throw new Report.Error(functionCallExpression, "Argument '" + parameterDeclaration.name() + "' should be of type '" + getTypeName(argumentType) + "' but is of type '" + getTypeName(parameterType) + "'.");
        }

        // We have already checked if function return type is correct (when we visited function declaration)
        SemType functionReturnType = functionDeclaration.type().accept(this, arg);
        SemAn.ofType.put(functionCallExpression, functionReturnType);
        return functionReturnType;
    }

    @Override
    public SemType visit(AstAssignStmt assignmentStatement, Mode arg) {
        SemType destinationType = assignmentStatement.dst().accept(this, arg);        
        if (destinationType.actualType() instanceof SemVoid || destinationType.actualType() instanceof SemRecord || destinationType.actualType() instanceof SemArray)
            throw new Report.Error(assignmentStatement, "Destination type must not be void, record or array");
        
        SemType sourceType = assignmentStatement.src().accept(this, arg);
        if (sourceType.actualType() instanceof SemVoid || sourceType.actualType() instanceof SemRecord || sourceType.actualType() instanceof SemArray)
            throw new Report.Error(assignmentStatement, "Source type must not be void, record or array");
        
        if (!sameType(destinationType, sourceType))
            throw new Report.Error(assignmentStatement, "Assignment source and destination type must not differ.");
        
        SemType semanticType = new SemVoid();
        SemAn.ofType.put(assignmentStatement, semanticType);
        return semanticType;
    }

    @Override
    public SemType visit(AstIfStmt ifStatement, Mode arg) {
        SemType conditionType = ifStatement.cond().accept(this, arg);
        if (!(conditionType.actualType() instanceof SemBoolean))
            throw new Report.Error(ifStatement, "Condition type must be boolean");
        
        ifStatement.thenStmt().accept(this, arg);
        ifStatement.elseStmt().accept(this, arg);
        
        SemType semanticType = new SemVoid();
        SemAn.ofType.put(ifStatement, semanticType);
        return semanticType;
    }

    @Override
    public SemType visit(AstWhileStmt whileStatement, Mode arg) {
        SemType conditionType = whileStatement.cond().accept(this, arg);
        if (!(conditionType.actualType() instanceof SemBoolean))
            throw new Report.Error(whileStatement, "Condition type must be boolean");
        
        whileStatement.bodyStmt().accept(this, arg);
        
        SemType semanticType = new SemVoid();
        SemAn.ofType.put(whileStatement, semanticType);
        return semanticType;
    }

    // THIS IS HERE ONLY BECAUSE PROFESSORS CODE HAS IT REVERSED AND IT DOES NOT WORK
    @Override
	public SemType visit(AstWhereExpr whereExpr, Mode arg) {
		if (whereExpr.decls() != null)
            whereExpr.decls().accept(this, arg);
        
		if (whereExpr.expr() != null) {
            SemType expressionType = whereExpr.expr().accept(this, arg);
            SemAn.ofType.put(whereExpr, expressionType);
            return expressionType;
        }
        
		return null;
    }
    
    // HELPER FUNCTIONS
    private String getTypeName(SemType type) {
        if (type instanceof SemInteger)
            return "int";
        else if (type instanceof SemChar)
            return "char";
        else if (type instanceof SemBoolean)
            return "bool";
        else if (type instanceof SemVoid)
            return "void";
        else if (type instanceof SemRecord)
            return "record";
        else if (type instanceof SemArray)
            return "array";
        else if (type instanceof SemPointer)
            return String.format("pointer(%s)", getTypeName(((SemPointer) type).baseType()));
        else if (type instanceof SemName)
            return getTypeName(((SemName) type).type());
        return "unknown";
    }

    private boolean sameType(SemType first, SemType second) {
        // If the actual types match and we are not dealing with pointers, then
        // the types are the same
        if (first.actualType().getClass() == second.actualType().getClass() && !(first.actualType() instanceof SemPointer))
            return true;
        
        // Otherwise, we have to check if pointers point to the same data type
        else if (first.actualType().getClass() == second.actualType().getClass()) {
            SemPointer firstPointer = (SemPointer) first.actualType();
            SemPointer secondPointer = (SemPointer) second.actualType();
            return sameType(firstPointer.baseType(), secondPointer.baseType());
        }

        return false;
    }

}