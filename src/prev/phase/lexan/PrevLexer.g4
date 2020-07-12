lexer grammar PrevLexer;

@header {
	package prev.phase.lexan;
	import prev.common.report.*;
}

@members {
    @Override
	public LexAn.PrevToken nextToken() {
		return (LexAn.PrevToken) super.nextToken();
	}
}

// Constant data types
VOID_CONSTANT: 'none';
NIL_CONSTANT: 'nil';
BOOLEAN_CONSTANT: ('true' | 'false' );
INTEGER_CONSTANT: DIGIT+;
CHAR_CONSTANT: '\'' ASCII_CHARACTER '\'';
STRING_CONSTANT: '"' ASCII_STRING_CHARACTER* '"';

// All possible symbols
OPENING_PARENTHESIS: '(';
CLOSING_PARENTHESIS: ')';
OPENING_BRACE: '{';
CLOSING_BRACE: '}';
OPENING_SQUARE_BRACKET: '[';
CLOSING_SQUARE_BRACKET: ']';
PERIOD: '.';
COMMA: ',';
COLON: ':';
SEMICOLON: ';';
AMPERSAND: '&';
VERTICAL_BAR: '|';
EXCLAMATION_POINT: '!';
EQUAL_EQUAL: '==';
NOT_EQUAL: '!=';
LESS_THAN: '<';
GREATER_THAN: '>';
LESS_THAN_EQUAL: '<=';
GREATER_THAN_EQUAL: '>=';
ASTERISK: '*';
SLASH: '/';
PERCENT: '%';
PLUS: '+';
MINUS: '-';
CARET: '^';
EQUAL: '=';

// All possible keywords
BOOLEAN: 'boolean';
CHARACTER: 'char';
DEL: 'del';
DO: 'do';
ELSE: 'else';
FUNCTION: 'fun';
IF: 'if';
INTEGER: 'integer';
NEW: 'new';
THEN: 'then';
TYPE: 'typ';
VARIABLE: 'var';
VOID: 'void';
WHERE: 'where';
WHILE: 'while';

// Identifiers are checked after keywords and constants to ensure we match them last
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;

// Comments end with newline
COMMENT: '#' (~[\n])* -> skip;

// In Python, it does not matter how the indentation is done (either using spaces or tabs),
// but how do we ensure that tabs are counted as 8 spaces
// We can use the setCharPositionInLine method (https://www.antlr.org/api/Java/org/antlr/v4/runtime/Lexer.html)
// The tab character already counts as 1 character, so we must increment current character index by 7
TAB: '\t' {	setCharPositionInLine((getCharPositionInLine() / 8 + 1) * 8); } -> skip;

// If we match any other character except for tab, we simply skip it
WHITESPACE: (' ' | '\n' | '\r') -> skip;

// Match unbalanced character literals and characters that are too long
CHAR_TYPE_UNBALANCED: '\'' ASCII_CHARACTER+ ~'\'' { if (true) throw new Report.Error(new Location (getLine(), getCharPositionInLine() - getText().length() + 1, getLine(), getCharPositionInLine()), "Unclosed character literal: " + getText()); } -> skip;
CHAR_TYPE_MULTIPLE_CHARACTERS: '\'' ASCII_CHARACTER+ '\'' { if (true) throw new Report.Error(new Location (getLine(), getCharPositionInLine() - getText().length() + 1, getLine(), getCharPositionInLine()), "Too many characters: " + getText()); } -> skip;

// Match strings that are not closed
STRING_UNBALANCED: '"' ASCII_STRING_CHARACTER* ~'"' { if (true) throw new Report.Error(new Location (getLine(), getCharPositionInLine() - getText().length() + 1, getLine(), getCharPositionInLine()), "Unclosed string literal: " + getText()); } -> skip;

// If we find a symbol that we have not yet matched, it should raise an exception
// use getLine and getCharPositionInLine methods to get corresponding line and column
UNRECOGNIZED_CHARACTER: . { if (true) throw new Report.Error(new Location (getLine(), getCharPositionInLine(), getLine(), getCharPositionInLine()), "Unrecognized character: " + getText()); };

// Fragments - not tokens, but can be used in lexical analysis
fragment DIGIT: [0-9];
fragment ASCII_CHARACTER: (' '..'&' | '('..'~' | '\\\'');
fragment ASCII_STRING_CHARACTER: (' ' | '!' | '#'..'~' | '\\"');