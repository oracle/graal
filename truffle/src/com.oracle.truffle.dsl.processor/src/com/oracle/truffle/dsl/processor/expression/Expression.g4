/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
