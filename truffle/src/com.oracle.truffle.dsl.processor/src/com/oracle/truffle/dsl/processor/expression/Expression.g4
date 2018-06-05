/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

 /*
  * The parser and lexer need to be generated using "mx create-dsl-parser".
  */

grammar Expression;

@parser::header
{
// DO NOT MODIFY - generated from Expression.g4 using "mx create-dsl-parser"

import com.oracle.truffle.dsl.processor.expression.DSLExpression.*;
}

@lexer::header
{
// DO NOT MODIFY - generated from Expression.g4 using "mx create-dsl-parser"
}

// parser

expression returns [DSLExpression result]
:
f=logic_factor                                   { $result = $f.result; }
EOF
;

logic_factor returns [DSLExpression result]
:
f1=comparison_factor                             { $result = $f1.result; }
(
    op='||'
    f2=comparison_factor                         { $result = new Binary($op.text, $result, $f2.result); }
)?
;

comparison_factor returns [DSLExpression result]
:
f1=negate_factor  { $result = $f1.result; }
(
    op=('<' | '<=' | '>' | '>=' | '==' | '!=')
    f2=negate_factor                             { $result = new Binary($op.text, $result, $f2.result); }
)?;

negate_factor returns [DSLExpression result]
:                                                { boolean negated = false; }
(
    '!'                                          { negated = true; }
)?
f=factor                                         { $result = negated ? new Negate($f.result) : $f.result; }
;

factor returns [DSLExpression result]
:
m=member_expression                              { $result = $m.result; }
|
l=NUMERIC_LITERAL                                { $result = new IntLiteral($l.text); }
|
'('
e=logic_factor                                   { $result = $e.result; }
')'
;

member_expression returns [DSLExpression result] :
id1=IDENTIFIER                                   { $result = new Variable(null, $id1.text); }
(
    '('                                          { List<DSLExpression> parameters = new ArrayList<>(); }
    (
        e1=logic_factor                          { parameters.add($e1.result); }
        (
            ',' e2=logic_factor                  { parameters.add($e2.result); }
        )*
    )?
    ')'                                          { $result = new Call(null, $id1.text, parameters); }
)?
(
    '.' id2=IDENTIFIER                           { $result = new Variable($result, $id2.text); }
    (
         '('                                     { List<DSLExpression> parameters = new ArrayList<>(); }
       	 (
             e1=logic_factor                     { parameters.add($e1.result); }
             (
                 ',' e2=logic_factor             { parameters.add($e2.result); }
             )*
         )?
        ')'                                      { $result = new Call(((Variable) $result).getReceiver(), $id2.text, parameters); }
    )?
)*;

// lexer

WS : [ ] -> skip;
fragment LETTER : [A-Z] | [a-z] | '_' | '$';
fragment NON_ZERO_DIGIT : [1-9];
fragment DIGIT : [0-9];
fragment HEX_DIGIT : [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT : [0-7];
fragment BINARY_DIGIT : '0' | '1';

IDENTIFIER : LETTER (LETTER | DIGIT)*;
NUMERIC_LITERAL : '0' ( 'x' HEX_DIGIT* | 'b' BINARY_DIGIT* | OCT_DIGIT* )? | NON_ZERO_DIGIT DIGIT*;
