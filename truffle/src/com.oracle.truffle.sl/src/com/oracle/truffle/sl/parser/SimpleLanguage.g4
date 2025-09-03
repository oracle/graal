/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/*
 * The parser and lexer need to be generated using "mx create-sl-parser".
 */

grammar SimpleLanguage;

@parser::header
{
// DO NOT MODIFY - generated from SimpleLanguage.g4 using "mx create-sl-parser"
}

@lexer::header
{
// DO NOT MODIFY - generated from SimpleLanguage.g4 using "mx create-sl-parser"
}

// parser 




simplelanguage
	: function function* EOF
	;


function
	: 'function' IDENTIFIER 
		s='(' (IDENTIFIER (',' IDENTIFIER)*)? ')'
		body=block
	;



block
	: s='{' statement* e='}'
	;


statement
	: while_statement
	| break_statement
	| continue_statement
	| if_statement
	| return_statement
	| expression_statement
	| debugger_statement
	;

break_statement
	: b='break'	';'
	;

continue_statement
	: c='continue' ';'
	;

expression_statement
	: expression ';'
	;

debugger_statement
	: d='debugger' ';'
	;

while_statement
	: w='while' '(' condition=expression ')'
		body=block
	;


if_statement
	: i='if' '(' condition=expression ')'
		then=block 
		( 'else' alt=block )?
	;


return_statement
	: r='return' expression? ';'
	;


expression
	: logic_term (OP_OR logic_term)*
	;


logic_term
	: logic_factor (OP_AND logic_factor)*
	;


logic_factor
	: arithmetic (OP_COMPARE arithmetic)?
	;


arithmetic
	: term (OP_ADD term)*
	;


term
	: factor (OP_MUL factor)*
	;


factor
	: IDENTIFIER member_expression* # NameAccess
	| STRING_LITERAL				# StringLiteral
	| NUMERIC_LITERAL				# NumericLiteral
	| '(' expression ')'			# ParenExpression
	;


member_expression
	: '(' ( expression (',' expression)* )? ')' 	# MemberCall
	| '=' expression							  	# MemberAssign
	| '.' IDENTIFIER						      	# MemberField
	| '[' expression ']'							# MemberIndex
	;

// lexer

WS : [ \t\r\n\u000C]+ -> skip;
COMMENT : '/*' .*? '*/' -> skip;
LINE_COMMENT : '//' ~[\r\n]* -> skip;

OP_OR: '||';
OP_AND: '&&';
OP_COMPARE: '<' | '<=' | '>' | '>=' | '==' | '!=';
OP_ADD: '+' | '-';
OP_MUL: '*' | '/';

fragment LETTER : [A-Z] | [a-z] | '_' | '$';
fragment NON_ZERO_DIGIT : [1-9];
fragment DIGIT : [0-9];
fragment HEX_DIGIT : [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT : [0-7];
fragment BINARY_DIGIT : '0' | '1';
fragment TAB : '\t';
fragment STRING_CHAR : ~('"' | '\r' | '\n');

IDENTIFIER : LETTER (LETTER | DIGIT)*;
STRING_LITERAL : '"' STRING_CHAR* '"';
NUMERIC_LITERAL : '0' | NON_ZERO_DIGIT DIGIT*;

