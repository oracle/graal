/*
 * The MIT License
 *
 * Copyright (c) 2008 Robert Stehwien
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
Port to Antlr4 by Tom Everett
*/
grammar SExpr;

@header {
package com.oracle.truffle.wasm.test.util.sexpr.parser;

import com.oracle.truffle.wasm.test.util.sexpr.LiteralType;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;
}

@parser::members {
// Code added manually to parse S-expressions {-

private SExprNodeFactory factory;
private String source;

public static List<SExprNode> parseSexpressions(String source) {
    SExprLexer lexer = new SExprLexer(CharStreams.fromString(source));
    SExprParser parser = new SExprParser(new CommonTokenStream(lexer));
    parser.factory = new SExprNodeFactory();
    parser.source = source;
    parser.root();
    return parser.factory.getAllExpressions();
}

// -} Code added manually to parse S-expressions
}

root
   :
    (sexpr { factory.addNode($sexpr.result); })*
     EOF
   ;

sexpr returns [SExprNode result]
   : atom { $result = new SExprAtomNode($atom.result); }
   | list { $result = new SExprListNode($list.result); }
   ;

list returns [List<SExprNode> result]
   : LPAREN { List<SExprNode> exprs = new ArrayList<SExprNode>(); }
    (sexpr  { exprs.add($sexpr.result); })*
     RPAREN { $result = exprs; }
   ;

atom returns [SExprLiteralNode result]
   : STRING   { $result = new SExprLiteralNode($STRING.getText(), LiteralType.STRING); }
   | SYMBOL   { $result = new SExprLiteralNode($SYMBOL.getText(), LiteralType.SYMBOL); }
   | INTEGER  { $result = new SExprLiteralNode($INTEGER.getText(), LiteralType.INTEGER); }
   | FLOATING { $result = new SExprLiteralNode($FLOATING.getText(), LiteralType.FLOATING); }
   ;

STRING
   : '"' ('\\' . | ~ ('\\' | '"'))* '"'
   ;

WHITESPACE
   : (' ' | '\n' | '\t' | '\r')+ -> skip
   ;

INTEGER
   : SIGN NUMBER
   | SIGN '0x' HEXNUMBER
   ;

FLOATING
   : SIGN FLOATING_BODY
   ;

FLOATING_BODY
   : FLOAT
   | HEXFLOAT
   | 'inf'
   | 'nan'
   | 'nan:0x' HEXNUMBER
   ;

SYMBOL
   : SYMBOL_START (SYMBOL_START | DIGIT)*
   ;

LPAREN
   : '('
   ;

RPAREN
   : ')'
   ;

fragment SYMBOL_START
   : ('a' .. 'z')
   | ('A' .. 'Z')
   | '_'
   | '.'
   | '+'
   | '-'
   | '*'
   | '/'
   | '\\'
   | '^'
   | '~'
   | '='
   | '<'
   | '>'
   | '!'
   | '?'
   | '@'
   | '#'
   | '$'
   | '%'
   | '&'
   | '|'
   | ':'
   | '\''
   | '`'
   ;

fragment SIGN
   : ('+' | '-')?
   ;

fragment DIGIT
   : ('0' .. '9')
   ;

fragment HEXDIGIT
   : DIGIT
   | ('A' .. 'F')
   | ('a' .. 'f')
   ;

fragment NUMBER
   : DIGIT+ NUMBER_
   ;

fragment NUMBER_
   : /* epsilon */
   | ('_')? NUMBER
   ;

fragment HEXNUMBER
   : HEXDIGIT+ HEXNUMBER_
   ;

fragment HEXNUMBER_
   : /* epsilon */
   | ('_')? HEXNUMBER
   ;

fragment FRAC
   : /* epsilon */
   | DIGIT FRAC
   | DIGIT '_' DIGIT FRAC
   ;

fragment HEXFRAC
   : /* epsilon */
   | HEXDIGIT HEXFRAC
   | HEXDIGIT '_' HEXDIGIT HEXFRAC
   ;

fragment FLOAT
   : NUMBER '.' FRAC
   | NUMBER ('E' | 'e') SIGN NUMBER
   | NUMBER '.' FRAC ('E' | 'e') SIGN NUMBER
   ;

fragment HEXFLOAT
   : '0x' HEXNUMBER HEXFRAC
   | '0x' HEXNUMBER ('P' | 'p') SIGN NUMBER
   | '0x' HEXNUMBER '.' HEXFRAC ('P' | 'p') SIGN NUMBER
   ;

COMMENT
   : ';;' ~[\r\n]* -> skip
   ;