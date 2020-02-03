/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
 
/*
 * The parser and lexer need to be generated using 'mx create-parsers';
 */

grammar DebugExpression;

@parser::header
{
// DO NOT MODIFY - generated from DebugExpression.g4 using "mx create-parsers"

import java.util.LinkedList;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.types.Type;

import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory.CompareKind;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprTypeofNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExpressionPair;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
}

@lexer::header
{
// DO NOT MODIFY - generated from DebugExpression.g4 using "mx create-parsers"
}

@parser::members
{

private LLVMExpressionNode astRoot = null;
private DebugExprNodeFactory NF = null;

public boolean IsCast() {
    if (_input.LA(1) == LAPR) {
        int i = 2;
        while (_input.LA(i) == ASTERISC) {
            i++;
        }
        int tokenType = _input.LA(i);
        if(tokenType == SIGNED || tokenType == UNSIGNED || tokenType == INT || tokenType == LONG
            || tokenType == CHAR || tokenType == SHORT || tokenType == FLOAT || tokenType == DOUBLE
            || tokenType == TYPEOF ) return true;
    }
    return false;
}

public void setNodeFactory(DebugExprNodeFactory nodeFactory) {
	if (NF == null) NF = nodeFactory;
}

public int GetErrors() {
	return _syntaxErrors;
}

public LLVMExpressionNode GetASTRoot() {return astRoot; }

}

fragment LETTER : [a-zA-Z];
fragment NONDIGIT : [a-zA-Z_];
fragment DIGIT : [0-9];
fragment CR : '\r';
fragment LF : '\n';
fragment SINGLECOMMA : '\'';
fragment QUOTE : '"';
fragment CCHAR : ~['\\\r\n] | SIMPLE_ESCAPE_SEQUENCE;
fragment SIMPLE_ESCAPE_SEQUENCE : '\\' ['"?abfnrtv\\];

// Token declarations here.
LAPR : '(';
RAPR : ')';
ASTERISC : '*';
PLUS : '+';
MINUS : '-';
DIVIDE : '/';
LOGICOR : '||';
LOGICAND : '&&';
DOT : '.';
POINTER : '->';
EXCLAM : '!';
TILDA : '~';
MODULAR : '%';
SHIFTR : '>>';
SHIFTL : '<<';
GT : '>';
LT : '<';
GTE : '>=';
LTE : '<=';
EQ : '==';
NE : '!=';
AND : '&';
OR : '|';
XOR : '^';
SIGNED : 'signed';
UNSIGNED : 'unsigned';
INT : 'int';
LONG : 'LONG';
SHORT : 'short';
FLOAT : 'float';
DOUBLE : 'double';
CHAR : 'char';
TYPEOF: 'typeof';
IDENT : NONDIGIT (NONDIGIT | DIGIT)*; // C Standard "6.4.2 Identifiers" (still missing "6.4.3 Universal character names")
NUMBER : DIGIT+;
FLOATNUMBER : DIGIT+ '.' DIGIT+ ( [eE] [+-] DIGIT+ )?;
CHARCONST :  SINGLECOMMA CCHAR SINGLECOMMA       // C Standard "6.4.4.4 Character constants"
          |  'L' SINGLECOMMA CCHAR SINGLECOMMA
          |  'u' SINGLECOMMA CCHAR SINGLECOMMA
          |  'U' SINGLECOMMA CCHAR SINGLECOMMA
          ;


WS  : [ \t\r\n]+ -> skip ;

// PRODUCTIONS

debugExpr :
  {
  DebugExpressionPair p = null;
  }
  (
  (expr { p = $expr.p; }) EOF                       {if(_syntaxErrors == 0){ astRoot = p.getNode();}}
  );


primExpr returns [DebugExpressionPair p] :
  {
  $p = null;
  }
  ( (t=IDENT)                                   { $p = NF.createVarNode($t.getText()); }
  | (t=NUMBER)                                  { $p = NF.createIntegerConstant(Integer.parseInt($t.getText())); }
  | (t=FLOATNUMBER)                             { $p = NF.createFloatConstant(Float.parseFloat($t.getText())); }
  | (t=CHARCONST)                               { $p = NF.createCharacterConstant($t.getText()); }
  | LAPR expr ')'                               { $p = $expr.p; }
  )
  ;


designator returns [DebugExpressionPair p] :
  (
  primExpr { $p = $primExpr.p; }
  )
  ( ('[' expr ']'                             { $p = NF.createArrayElement($p, $expr.p); })
  | (actPars                                  { $p = NF.createFunctionCall($p, $actPars.l); })
  | ('.' (t=IDENT)                            { $p = NF.createObjectMember($p, $t.getText()); })
  | ('->' (t=IDENT)                           { $p = NF.createObjectPointerMember($p, $t.getText()); })
  )*;


actPars returns [List l] :
  {
  $l = new LinkedList<DebugExpressionPair>();
  }
  LAPR ( (expr { $l.add($expr.p); }) (',' expr { $l.add($expr.p); })* )? ')';


unaryExpr returns [DebugExpressionPair p] :
  ( designator                                          { $p = $designator.p; }
  | unaryOP castExpr                                    { $p = NF.createUnaryOpNode($castExpr.p, $unaryOP.kind); }
  | 'sizeof' LAPR dType ')'                             { $p = NF.createSizeofNode($dType.ty); }
  );


unaryOP returns [char kind] :
  t=( '*'
  | '+'
  | '-'
  | '~'
  | '!'
  )                                              { $kind = $t.getText().charAt(0); }
  ;


castExpr returns [DebugExpressionPair p] :
  {
  DebugExprType typeP = null;
  DebugExprTypeofNode typeNode = null;
  }
  (
    { IsCast() }?
        (
            LAPR
            (
                (dType { typeP = $dType.ty; })
                | ('typeof' LAPR ((t=IDENT) { typeNode = NF.createTypeofNode($t.getText());}) ')')
            )
            ')'
        )
  )?
  (unaryExpr { $p = $unaryExpr.p; }                 { if (typeP != null) { $p = NF.createCastIfNecessary($p, typeP); }
                                                      if (typeNode != null) { $p = NF.createPointerCastNode($p, typeNode); }
                                                    })
  ;


multExpr returns [DebugExpressionPair p] :
  (
  castExpr { $p = $castExpr.p; }
  )
  ( ('*' castExpr                    { $p = NF.createArithmeticOp(ArithmeticOperation.MUL, $p, $castExpr.p); })
  | ('/' castExpr                    { $p = NF.createDivNode($p, $castExpr.p); })
  | ('%' castExpr                    { $p = NF.createRemNode($p, $castExpr.p); })
  )*;


addExpr returns [DebugExpressionPair p] :
  (multExpr { $p = $multExpr.p; })
  ( ('+' multExpr { $p = NF.createArithmeticOp(ArithmeticOperation.ADD, $p, $multExpr.p);})
  | ('-' multExpr { $p = NF.createArithmeticOp(ArithmeticOperation.SUB, $p, $multExpr.p);})
  )*;


shiftExpr returns [DebugExpressionPair p] :
  (addExpr { $p = $addExpr.p; })
  ( ('<<' addExpr { $p = NF.createShiftLeft($p, $addExpr.p); })
  | ('>>' addExpr { $p = NF.createShiftRight($p, $addExpr.p); })
  )*;


relExpr returns [DebugExpressionPair p] :
  (
  shiftExpr { $p = $shiftExpr.p; }
  )
  ( ('<' shiftExpr                    { $p = NF.createCompareNode($p, CompareKind.LT, $shiftExpr.p); })
  | ('>' shiftExpr                    { $p = NF.createCompareNode($p, CompareKind.GT, $shiftExpr.p); })
  | ('<=' shiftExpr                   { $p = NF.createCompareNode($p, CompareKind.LE, $shiftExpr.p); })
  | ('>=' shiftExpr                   { $p = NF.createCompareNode($p, CompareKind.GE, $shiftExpr.p); })
  )*;


eqExpr returns [DebugExpressionPair p] :
  (relExpr { $p = $relExpr.p; })
  ( ('==' relExpr { $p = NF.createCompareNode($p, CompareKind.EQ, $relExpr.p); })
  | ('!=' relExpr { $p = NF.createCompareNode($p, CompareKind.NE, $relExpr.p); })
  )*;


andExpr returns [DebugExpressionPair p] :
  ( eqExpr { $p = $eqExpr.p; } )
  (
    '&' eqExpr { $p = NF.createArithmeticOp(ArithmeticOperation.AND, $p, $eqExpr.p); }
  )*;


xorExpr returns [DebugExpressionPair p] :
  ( andExpr { $p = $andExpr.p; } )
  (
    '^' andExpr { $p = NF.createArithmeticOp(ArithmeticOperation.XOR, $p, $andExpr.p); }
  )*;


orExpr returns [DebugExpressionPair p] :
  ( xorExpr { $p = $xorExpr.p; } )
  (
    '|' xorExpr { $p = NF.createArithmeticOp(ArithmeticOperation.OR, $p, $xorExpr.p); }
  )*;


logAndExpr returns [DebugExpressionPair p] :
  ( orExpr { $p = $orExpr.p; } )
  (
    '&&' orExpr { $p = NF.createLogicalAndNode($p, $orExpr.p); }
  )*;


logOrExpr returns [DebugExpressionPair p] :
  ( logAndExpr { $p = $logAndExpr.p; } )
  (
    '||' logAndExpr { $p = NF.createLogicalOrNode($p, $logAndExpr.p); }
  )*;


expr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair pThen = null;
  DebugExpressionPair pElse = null;
  }
  (logOrExpr { $p = $logOrExpr.p; })
  (
    '?' (expr { pThen = $expr.p; })
    ':' (expr { pElse = $expr.p; }) { $p = NF.createTernaryNode($p, pThen, pElse); }
  )?;


dType returns [DebugExprType ty] :
  (
  baseType { $ty = $baseType.ty; }
  )
  (
    ('*')                                             { $ty = $ty.createPointer(); }
  )*
  ('['
    ( (t=NUMBER)                                      { $ty = $ty.createArrayType(Integer.parseInt($t.getText()));}
    |                                                 { $ty = $ty.createArrayType(-1); }
    )
  ']')*;


baseType returns [DebugExprType ty] :
  {
  $ty = null;
  boolean signed = false;
  }
  (
  LAPR dType ')'                                                     { $ty = $dType.ty; }
  | 'void'                                                          { $ty = DebugExprType.getVoidType(); }
  | (( 'signed'                                                     { signed = true; }
    | 'unsigned'                                                    { signed = false; }
    )
     ( 'char'                                                       { $ty = DebugExprType.getIntType(8, signed); }
     | 'short'                                                      { $ty = DebugExprType.getIntType(16, signed); }
     | 'int'                                                        { $ty = DebugExprType.getIntType(32, signed); }
     | 'long'                                                       { $ty = DebugExprType.getIntType(64, signed); }
     )?)
  | 'char'                                                          { $ty = DebugExprType.getIntType(8, false); }
  | 'short'                                                         { $ty = DebugExprType.getIntType(16, true); }
  | 'int'                                                           { $ty = DebugExprType.getIntType(32, true); }
  | ( 'long'                                                        { $ty = DebugExprType.getIntType(64, true); }
     ( 'double' )?                                                  { $ty = DebugExprType.getFloatType(128); }
    )
  | 'float'                                                         { $ty = DebugExprType.getFloatType(32); }
  | 'double'                                                        { $ty = DebugExprType.getFloatType(64); }
  )
  ;