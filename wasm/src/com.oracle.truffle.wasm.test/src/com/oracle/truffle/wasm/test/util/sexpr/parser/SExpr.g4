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

import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprFloatingLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprIntegerLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprStringLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprSymbolLiteralNode;
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
   : STRING   { $result = new SExprStringLiteralNode($STRING.getText()); }
   | SYMBOL   { $result = new SExprSymbolLiteralNode($SYMBOL.getText()); }
   | INTEGER  { $result = new SExprIntegerLiteralNode(Integer.parseInt($INTEGER.getText())); }
   | FLOATING { $result = new SExprFloatingLiteralNode(Double.parseDouble($FLOATING.getText())); }
   ;

STRING
   : '"' ('\\' . | ~ ('\\' | '"'))* '"'
   ;

WHITESPACE
   : (' ' | '\n' | '\t' | '\r')+ -> skip
   ;

INTEGER
   : ('+' | '-')? (DIGIT)+
   ;

FLOATING
   : ('+' | '-')? (DIGIT)+ ('.' (DIGIT)+)?
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
   | '+'
   | '-'
   | '*'
   | '/'
   | '.'
   | '_'
   ;

fragment DIGIT
   : ('0' .. '9')
   ;

