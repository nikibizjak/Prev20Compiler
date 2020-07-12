parser grammar PrevParser;

@header {

	package prev.phase.synan;
	
	import java.util.*;
	
	import prev.common.report.*;
	import prev.phase.lexan.*;

	import prev.phase.lexan.LexAn.PrevToken;

	import prev.data.ast.tree.*;
	import prev.data.ast.tree.expr.*;
	import prev.data.ast.tree.stmt.*;
	import prev.data.ast.tree.decl.*;
	import prev.data.ast.tree.type.*;
}

options{
    tokenVocab=PrevLexer;
}

source
	returns [AstTrees<AstDecl> ast]
	: program EOF {
		$ast = $program.ast;
	};

program
	returns [AstTrees<AstDecl> ast]
	: declarations { $ast = $declarations.ast; };

type_declaration
	returns [AstTypeDecl ast]
	: typeToken=TYPE name=IDENTIFIER EQUAL type {
		Location typeLocation = new Location(((PrevToken) $typeToken).location(), $type.ast.location());
		$ast = new AstTypeDecl(typeLocation, $name.getText(), $type.ast);
	}
	;

variable_declaration
	returns [AstVarDecl ast]
	: beg=VARIABLE name=IDENTIFIER COLON type {
		Location location = new Location(((PrevToken) $beg).location(), $type.ast.location());
		$ast = new AstVarDecl(location, $name.getText(), $type.ast);
	}
	;

function_declaration
	returns [AstFunDecl ast]
	: beg=FUNCTION name=IDENTIFIER OPENING_PARENTHESIS function_parameters CLOSING_PARENTHESIS COLON type EQUAL expression {
		Location location = new Location(((PrevToken) $beg).location(), $expression.ast.location());
		$ast = new AstFunDecl(location, $name.getText(), $function_parameters.ast, $type.ast, $expression.ast);
	}
	;

function_parameters
	returns [AstTrees<AstParDecl> ast]
	locals [Vector<AstParDecl> parameters = new Vector<AstParDecl>();]
	: name=IDENTIFIER COLON type {
		Location firstParameterLocation = new Location(((PrevToken) $name).location(), $type.ast.location());
		$parameters.add(new AstParDecl(firstParameterLocation, $name.getText(), $type.ast));
	} (beg=COMMA name=IDENTIFIER COLON type {
		Location currentParameterLocation = new Location(((PrevToken) $beg).location(), $type.ast.location());
		$parameters.add(new AstParDecl(currentParameterLocation, $name.getText(), $type.ast));
	})* {
		$ast = new AstTrees<AstParDecl>($parameters);
	}
	| { $ast = new AstTrees<AstParDecl>(new Vector<AstParDecl>()); }
	;

declaration
	returns [AstDecl ast]
	: type_declaration { $ast = $type_declaration.ast; }
	| variable_declaration { $ast = $variable_declaration.ast; }
	| function_declaration { $ast = $function_declaration.ast; }
	;

declarations
	returns [AstTrees<AstDecl> ast]
	locals [Vector<AstDecl> allDeclarations = new Vector<AstDecl>();]
	: (declaration {
		$allDeclarations.add($declaration.ast);
		$ast = new AstTrees<AstDecl>($allDeclarations);
	})+;

atomic_type
	returns [AstAtomType ast]
	: token = (VOID | CHARACTER | INTEGER | BOOLEAN) {
		Location tokenLocation = ((PrevToken) $token).location();
		AstAtomType.Type tokenType = AstAtomType.Type.valueOf($token.getText().toUpperCase());
		$ast = new AstAtomType(tokenLocation, tokenType);
	};

named_type
	returns [AstNameType ast]
	: token=IDENTIFIER { 
		Location tokenLocation = ((PrevToken) $token).location();
		$ast = new AstNameType(tokenLocation, $token.getText());
	};

array_type
	returns [AstArrType ast]
	: OPENING_SQUARE_BRACKET expression end=CLOSING_SQUARE_BRACKET atomic_type {
		Location location = new Location($atomic_type.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrType(location, $atomic_type.ast, $expression.ast);
	}
	| OPENING_SQUARE_BRACKET expression end=CLOSING_SQUARE_BRACKET named_type {
		Location location = new Location($named_type.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrType(location, $named_type.ast, $expression.ast);
	}
	| OPENING_SQUARE_BRACKET expression end=CLOSING_SQUARE_BRACKET record_type {
		Location location = new Location($record_type.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrType(location, $record_type.ast, $expression.ast);
	}
	| OPENING_SQUARE_BRACKET expression end=CLOSING_SQUARE_BRACKET enclosed_type {
		Location location = new Location($enclosed_type.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrType(location, $enclosed_type.ast, $expression.ast);
	}
	| OPENING_SQUARE_BRACKET expression end=CLOSING_SQUARE_BRACKET arrayType=array_type {
		Location location = new Location($arrayType.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrType(location, $arrayType.ast, $expression.ast);
	}
	;

pointer_type
	returns [AstPtrType ast]
	: beg=CARET type {
		Location location = new Location(((PrevToken) $beg).location(), $type.ast.location());
		$ast = new AstPtrType(location, $type.ast);
	};

record_type
	returns [AstRecType ast]
	locals [Vector<AstCompDecl> records = new Vector<AstCompDecl>();]
	: beg=OPENING_BRACE identifierToken=IDENTIFIER COLON type {
		Location sublocation = new Location(((PrevToken) $identifierToken).location(), $type.ast.location());
		AstCompDecl first = new AstCompDecl(sublocation, $identifierToken.getText(), $type.ast);
		$records.add(first);
	} more=additional_record end=CLOSING_BRACE {
		// Get the full record type location
		Location recordTypeLocation = new Location(((PrevToken) $beg).location(), ((PrevToken) $end).location());		

		// If there are additional records, add them as well
		if ($more.additionalRecords != null)
			$records.addAll($more.additionalRecords);
		
		$ast = new AstRecType(recordTypeLocation, new AstTrees<AstCompDecl>($records));
	};

additional_record
	returns [Vector<AstCompDecl> additionalRecords]
	locals [Vector<AstCompDecl> records = new Vector<AstCompDecl>()]
	: (COMMA identifierToken=IDENTIFIER COLON type {
		Location location = new Location(((PrevToken) $identifierToken).location(), $type.ast.location());
		AstCompDecl additionalRecord = new AstCompDecl(location, $identifierToken.getText(), $type.ast);
		$records.add(additionalRecord);
		$additionalRecords = $records;
	})*;

enclosed_type
	returns [AstType ast]
	: beg=OPENING_PARENTHESIS type end=CLOSING_PARENTHESIS { $ast = $type.ast; };

type
	returns [AstType ast]
	: atomic_type { $ast = $atomic_type.ast; }
	| named_type { $ast = $named_type.ast; }
	| array_type { $ast = $array_type.ast; }
	| pointer_type { $ast = $pointer_type.ast; }
	| record_type { $ast = $record_type.ast; }
	| enclosed_type { $ast = $enclosed_type.ast; }
	;

constant_expression
	returns [AstAtomExpr ast]
	: token=VOID_CONSTANT { $ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.VOID, $token.getText()); }
	| token=NIL_CONSTANT { $ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.POINTER, $token.getText()); }
	| token=BOOLEAN_CONSTANT { $ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.BOOLEAN, $token.getText()); }
	| token=INTEGER_CONSTANT { $ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.INTEGER, $token.getText()); }
	| token=CHAR_CONSTANT {
		String text = $token.getText();
		text = text.substring(1, text.length() - 1);
		$ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.CHAR, text);
	}
	| token=STRING_CONSTANT {
		String text = $token.getText();
		$ast = new AstAtomExpr(((PrevToken) $token).location(), AstAtomExpr.Type.STRING, text);
	};

identifier_expression
	returns [AstNameExpr ast]
	: token=IDENTIFIER { $ast = new AstNameExpr(((PrevToken) $token).location(), $token.getText()); };

function_call_expression
	returns [AstCallExpr ast]
	: name=IDENTIFIER OPENING_PARENTHESIS function_call_arguments end=CLOSING_PARENTHESIS {
		Location location = new Location(((PrevToken) $name).location(), ((PrevToken) $end).location());
		$ast = new AstCallExpr(location, $name.getText(), $function_call_arguments.ast);
	};

function_call_arguments
	returns [AstTrees<AstExpr> ast]
	locals [Vector<AstExpr> expressions = new Vector<AstExpr>()]
	: expression {
		$expressions.add($expression.ast);
		$ast = new AstTrees<AstExpr>($expressions);
	} (COMMA expression {
		$expressions.add($expression.ast);
		$ast = new AstTrees<AstExpr>($expressions);
	})*
	| { $ast = new AstTrees<AstExpr>(new Vector<AstExpr>()); };

compound_expression
	returns [AstStmtExpr ast]
	locals [Vector<AstStmt> statements = new Vector<AstStmt>();]
	: beg=OPENING_BRACE statement {
		$statements.add($statement.ast);
	} SEMICOLON (statement SEMICOLON {
		$statements.add($statement.ast);
	})* end=CLOSING_BRACE {
		Location location = new Location(((PrevToken) $beg).location(), ((PrevToken) $end).location());
		AstTrees<AstStmt> parsedStatements = new AstTrees<AstStmt>($statements);
		$ast = new AstStmtExpr(location, parsedStatements);
	};

typecast_expression
	returns [AstCastExpr ast]
	: beg=OPENING_PARENTHESIS expression COLON type end=CLOSING_PARENTHESIS {
		Location location = new Location(((PrevToken) $beg).location(), ((PrevToken) $end).location());
		$ast = new AstCastExpr(location, $expression.ast, $type.ast);
	};

enclosed_expression
	returns [AstExpr ast]
	: beg=OPENING_PARENTHESIS expression end=CLOSING_PARENTHESIS { $ast = $expression.ast; };

primary_expression
	returns [AstExpr ast]
	: constant_expression { $ast = $constant_expression.ast; }
	| identifier_expression { $ast = $identifier_expression.ast; }
	| function_call_expression { $ast = $function_call_expression.ast; }
	| compound_expression { $ast = $compound_expression.ast; }
	| typecast_expression { $ast = $typecast_expression.ast; }
	| enclosed_expression { $ast = $enclosed_expression.ast; };

declaration_binder_expression
	returns [AstExpr ast]
	: first=declaration_binder_expression WHERE OPENING_BRACE declarations end=CLOSING_BRACE {
		Location location = new Location($first.ast.location(), ((PrevToken) $end).location());
		$ast = new AstWhereExpr(location, $first.ast, $declarations.ast);
	}
	| disjunctive_expression { $ast = $disjunctive_expression.ast; };

disjunctive_expression
	returns [AstExpr ast]
	: first=disjunctive_expression VERTICAL_BAR second=conjunctive_expression {
		Location location = new Location($first.ast.location(), $second.ast.location());
		$ast = new AstBinExpr(location, AstBinExpr.Oper.OR, $first.ast, $second.ast);
	}
	| conjunctive_expression { $ast = $conjunctive_expression.ast; };

conjunctive_expression
	returns [AstExpr ast]
	: first=conjunctive_expression AMPERSAND second=relational_expression {
		Location location = new Location($first.ast.location(), $second.ast.location());
		$ast = new AstBinExpr(location, AstBinExpr.Oper.AND, $first.ast, $second.ast);
	}
	| relational_expression { $ast = $relational_expression.ast; };

relational_expression
	returns [AstExpr ast]
	: first=additive_expression operator=(EQUAL_EQUAL | NOT_EQUAL | LESS_THAN | GREATER_THAN | LESS_THAN_EQUAL | GREATER_THAN_EQUAL) second=additive_expression {
		Location location = new Location($first.ast.location(), $second.ast.location());

		AstBinExpr.Oper operator = AstBinExpr.Oper.MUL;
		switch ($operator.getText()) {
			case "==": operator = AstBinExpr.Oper.EQU; break;
			case "!=": operator = AstBinExpr.Oper.NEQ; break;
			case "<": operator = AstBinExpr.Oper.LTH; break;
			case ">": operator = AstBinExpr.Oper.GTH; break;
			case "<=": operator = AstBinExpr.Oper.LEQ; break;
			case ">=": operator = AstBinExpr.Oper.GEQ; break;
		}

		$ast = new AstBinExpr(location, operator, $first.ast, $second.ast);
	}
	| additive_expression { $ast = $additive_expression.ast; };

additive_expression
	returns [AstExpr ast]
	: first=additive_expression operator=(PLUS | MINUS) second=multiplicative_expression {
		Location location = new Location($first.ast.location(), $second.ast.location());
		AstBinExpr.Oper operator = ($operator.getText().equals("+")) ? AstBinExpr.Oper.ADD : AstBinExpr.Oper.SUB;
		$ast = new AstBinExpr(location, operator, $first.ast, $second.ast);
	}
	| multiplicative_expression { $ast = $multiplicative_expression.ast; };

multiplicative_expression
	returns [AstExpr ast]
	: first=multiplicative_expression operator=(ASTERISK | SLASH | PERCENT) second=prefix_expression {
		Location location = new Location($first.ast.location(), $second.ast.location());
		
		AstBinExpr.Oper operator = AstBinExpr.Oper.MUL;
		switch ($operator.getText()) {
			case "/": operator = AstBinExpr.Oper.DIV; break;
			case "%": operator = AstBinExpr.Oper.MOD; break;
		}
		
		$ast = new AstBinExpr(location, operator, $first.ast, $second.ast);
	}
	| prefix_expression { $ast = $prefix_expression.ast; };

prefix_expression
	returns [AstExpr ast]
	: operator=(EXCLAMATION_POINT | PLUS | MINUS | CARET | NEW | DEL) prefix_expression {
		Location location = new Location(((PrevToken) $operator).location(), $prefix_expression.ast.location());
		
		AstPfxExpr.Oper operator = AstPfxExpr.Oper.ADD;
		switch ($operator.getText()) {
			case "!": operator = AstPfxExpr.Oper.NOT; break;
			case "+": operator = AstPfxExpr.Oper.ADD; break;
			case "-": operator = AstPfxExpr.Oper.SUB; break;
			case "^": operator = AstPfxExpr.Oper.PTR; break;
			case "new": operator = AstPfxExpr.Oper.NEW; break;
			case "del": operator = AstPfxExpr.Oper.DEL; break;
		}

		$ast = new AstPfxExpr(location, operator, $prefix_expression.ast);
	}
	| postfix_expression { $ast = $postfix_expression.ast; };

postfix_expression
	returns [AstExpr ast]
	: first=postfix_expression OPENING_SQUARE_BRACKET arrayIndexDeclarationBinder=declaration_binder_expression end=CLOSING_SQUARE_BRACKET {
		Location location = new Location($first.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrExpr(location, $first.ast, $arrayIndexDeclarationBinder.ast);
	}
	| first=postfix_expression OPENING_SQUARE_BRACKET arrayIndexPrimary=primary_expression CLOSING_SQUARE_BRACKET {
		Location location = new Location($first.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrExpr(location, $first.ast, $arrayIndexPrimary.ast);
	}
	| first=postfix_expression OPENING_SQUARE_BRACKET arrayIndexPostfix=postfix_expression CLOSING_SQUARE_BRACKET {
		Location location = new Location($first.ast.location(), ((PrevToken) $end).location());
		$ast = new AstArrExpr(location, $first.ast, $arrayIndexPostfix.ast);
	}
	| subexpression=postfix_expression end=CARET {
		Location location = new Location($subexpression.ast.location(), ((PrevToken) $end).location());
		$ast = new AstSfxExpr(location, AstSfxExpr.Oper.PTR, $subexpression.ast);
	}
	| recordExpression=postfix_expression PERIOD name=IDENTIFIER {
		Location location = new Location($recordExpression.ast.location(), ((PrevToken) $name).location());
		AstNameExpr nameExpression = new AstNameExpr(((PrevToken) $name).location(), $name.getText());
		$ast = new AstRecExpr(location, $recordExpression.ast, nameExpression);
	}
	| primary_expression { $ast = $primary_expression.ast; };

expression
	returns [AstExpr ast]
	: expr=declaration_binder_expression { $ast = $expr.ast; };

statement
	returns [AstStmt ast]
	: expression { $ast = new AstExprStmt($expression.ast.location(), $expression.ast); }
	| first=expression EQUAL second=expression {
		Location location = new Location($first.ast.location(), $second.ast.location());
		$ast = new AstAssignStmt(location, $first.ast, $second.ast);
	}
	| beg=IF expression THEN thenStatement=statement ELSE elseStatement=statement {
		Location location = new Location(((PrevToken) $beg).location(), $elseStatement.ast.location());
		$ast = new AstIfStmt(location, $expression.ast, $thenStatement.ast, $elseStatement.ast);
	}
	| beg=WHILE expression DO statement {
		Location location = new Location(((PrevToken) $beg).location(), $statement.ast.location());
		$ast = new AstWhileStmt(location, $expression.ast, $statement.ast);
	};