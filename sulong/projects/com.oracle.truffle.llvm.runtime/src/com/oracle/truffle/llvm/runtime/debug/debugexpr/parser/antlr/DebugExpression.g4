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
 * The parser and lexer need to be generated using 'mx create-parser';
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
fragment DIGIT : [0-9];
fragment CR : '\r';
fragment LF : '\n';
fragment SINGLECOMMA : '\'';
fragment QUOTE : '"';

// Add token declarations here.
// Example:
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
IDENT : LETTER (LETTER | DIGIT)*;
NUMBER : DIGIT+;
FLOATNUMBER : DIGIT+ '.' DIGIT+ ( [eE] [+-] DIGIT+ )?;
CHARCONST : SINGLECOMMA (LETTER|DIGIT) SINGLECOMMA;


WS  : [ \t\r\n]+ -> skip ;

// PRODUCTIONS

//DebugExpr											(. DebugExpressionPair p=null; .)
//=
//Expr<out p> 										(. if(errors.count==0) astRoot =p.getNode(); .)
//.

debugExpr :
  {
  DebugExpressionPair p = null;
  }
  (
  (expr { p = $expr.p; }) EOF                       {if(_syntaxErrors == 0){ astRoot = p.getNode();}}
  );


//PrimExpr<out DebugExpressionPair p>					(. p=null; .)
//=
//ident 												(. p = NF.createVarNode(t.val);.)
//|
//number 												(. p = NF.createIntegerConstant(Integer.parseInt(t.val)); .)
//|
//floatnumber 										(. p = NF.createFloatConstant(Float.parseFloat(t.val)); .)
//|
//charConst											(. p = NF.createCharacterConstant(t.val); .)
//|
//"(" Expr<out p> ")"
//.

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


//Designator<out DebugExpressionPair p>				(. DebugExpressionPair idxPair=null; List<DebugExpressionPair> l; .)
//=
//PrimExpr<out p>
//{
//	 "[" Expr<out idxPair> "]"						(. p = NF.createArrayElement(p, idxPair); .)
//	|
//	 ActPars<out l>									(. p = NF.createFunctionCall(p, l); .)
//	|
//	 "." ident										(. p = NF.createObjectMember(p, t.val); .)
//	|
//	 "->" ident										(. p = NF.createObjectPointerMember(p, t.val); .)
//}
//.

designator returns [DebugExpressionPair p] :
  (
  primExpr { $p = $primExpr.p; }
  )
  ( ('[' expr ']'                             { $p = NF.createArrayElement($p, $expr.p); })
  | (actPars                                  { $p = NF.createFunctionCall($p, $actPars.l); })
  | ('.' (t=IDENT)                            { $p = NF.createObjectMember($p, $t.getText()); })
  | ('->' (t=IDENT)                           { $p = NF.createObjectPointerMember($p, $t.getText()); })
  )*;


//
//ActPars<out List l>									(. DebugExpressionPair p1=null, p2=null; l = new LinkedList<DebugExpressionPair>(); .)
//=
//"("
//[
//	 Expr<out p1> 									(. l.add(p1); .)
//
//	{
//		 "," Expr<out p2> 							(. l.add(p2); .)
//
//	}
//
//]
//")"
//.

actPars returns [List l] :
  {
  $l = new LinkedList<DebugExpressionPair>();
  }
  LAPR ( (expr { $l.add($expr.p); }) (',' expr { $l.add($expr.p); })* )? ')';


//UnaryExpr<out DebugExpressionPair p>				(. p=null; char kind='\0'; DebugExprType typeP=null;.)
//=
//Designator<out p>
//|
//UnaryOp<out kind> CastExpr<out p> 					(. p = NF.createUnaryOpNode(p, kind); .)
//|
//"sizeof" "(" DType<out typeP> ")" 					(. p=NF.createSizeofNode(typeP); .)
//.

unaryExpr returns [DebugExpressionPair p] :
  ( designator                                          { $p = $designator.p; }
  | unaryOP castExpr                                    { $p = NF.createUnaryOpNode($castExpr.p, $unaryOP.kind); }
  | 'sizeof' LAPR dType ')'                             { $p = NF.createSizeofNode($dType.ty); }
  );


//
//UnaryOp<out char kind>								(. kind='\0'; .)
//=
//(
//	 "*"
//	|
//	 "+"
//	|
//	 "-"
//	|
//	 "~"
//	|
//	 "!"
//)
//													(. kind = t.val.charAt(0); .)
//.

unaryOP returns [char kind] :
  t=( '*'
  | '+'
  | '-'
  | '~'
  | '!'
  )                                              { $kind = $t.getText().charAt(0); }
  ;


//CastExpr<out DebugExpressionPair p>					(. DebugExprType typeP=null; DebugExprTypeofNode typeNode=null; .)
//=
//[
//	IF (IsCast()) "("
//		(
//			DType<out typeP>
//		|
//			"typeof" "(" ident						(. typeNode = NF.createTypeofNode(t.val); .)
//			")"
//		)
//		")"
//]
//UnaryExpr<out p> 									(. if(typeP!=null) { p = NF.createCastIfNecessary(p, typeP); }
//														if(typeNode!=null) {p = NF.createPointerCastNode(p, typeNode);} .)
//.

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


//MultExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//CastExpr<out p>
//{
//	 "*" CastExpr<out p1> 							(. p = NF.createArithmeticOp(ArithmeticOperation.MUL, p, p1); .)
//
//	|
//	 "/" CastExpr<out p1> 							(. p = NF.createDivNode(p, p1); .)
//
//	|
//	 "%" CastExpr<out p1> 							(. p = NF.createRemNode(p, p1); .)
//
//}
//.

multExpr returns [DebugExpressionPair p] :
  (
  castExpr { $p = $castExpr.p; }
  )
  ( ('*' castExpr                    { $p = NF.createArithmeticOp(ArithmeticOperation.MUL, $p, $castExpr.p); })
  | ('/' castExpr                    { $p = NF.createDivNode($p, $castExpr.p); })
  | ('%' castExpr                    { $p = NF.createRemNode($p, $castExpr.p); })
  )*;


//AddExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//MultExpr<out p>
//{
//	 "+" MultExpr<out p1> 							(. p = NF.createArithmeticOp(ArithmeticOperation.ADD, p, p1); .)
//
//	|
//	 "-" MultExpr<out p1> 							(.p = NF.createArithmeticOp(ArithmeticOperation.SUB, p, p1); .)
//
//}
//.

addExpr returns [DebugExpressionPair p] :
  (multExpr { $p = $multExpr.p; })
  ( ('+' multExpr { $p = NF.createArithmeticOp(ArithmeticOperation.ADD, $p, $multExpr.p);})
  | ('-' multExpr { $p = NF.createArithmeticOp(ArithmeticOperation.SUB, $p, $multExpr.p);})
  )*;


//ShiftExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//AddExpr<out p>
//{
//	 "<<" AddExpr<out p1> 							(.p = NF.createShiftLeft(p, p1); .)
//
//	|
//	 ">>" AddExpr<out p1> 							(.p = NF.createShiftRight(p, p1); .)
//
//}
//.

shiftExpr returns [DebugExpressionPair p] :
  (addExpr { $p = $addExpr.p; })
  ( ('<<' addExpr { $p = NF.createShiftLeft($p, $addExpr.p); })
  | ('>>' addExpr { $p = NF.createShiftRight($p, $addExpr.p); })
  )*;


//RelExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//ShiftExpr<out p>
//{
//	 "<" ShiftExpr<out p1> 							(. p = NF.createCompareNode(p, CompareKind.LT, p1); .)
//
//	|
//	 ">" ShiftExpr<out p1> 							(. p = NF.createCompareNode(p, CompareKind.GT, p1); .)
//
//	|
//	 "<=" ShiftExpr<out p1> 						(. p = NF.createCompareNode(p, CompareKind.LE, p1); .)
//
//	|
//	 ">=" ShiftExpr<out p1> 						(. p = NF.createCompareNode(p, CompareKind.GE, p1); .)
//
//}
//.

relExpr returns [DebugExpressionPair p] :
  (
  shiftExpr { $p = $shiftExpr.p; }
  )
  ( ('<' shiftExpr                    { $p = NF.createCompareNode($p, CompareKind.LT, $shiftExpr.p); })
  | ('>' shiftExpr                    { $p = NF.createCompareNode($p, CompareKind.GT, $shiftExpr.p); })
  | ('<=' shiftExpr                   { $p = NF.createCompareNode($p, CompareKind.LE, $shiftExpr.p); })
  | ('>=' shiftExpr                   { $p = NF.createCompareNode($p, CompareKind.GE, $shiftExpr.p); })
  )*;


//EqExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//RelExpr<out p>
//{
//	 "==" RelExpr<out p1> 							(. p = NF.createCompareNode(p, CompareKind.EQ, p1); .)
//
//	|
//	 "!=" RelExpr<out p1> 							(. p = NF.createCompareNode(p, CompareKind.NE, p1); .)
//
//}
//.

eqExpr returns [DebugExpressionPair p] :
  (relExpr { $p = $relExpr.p; })
  ( ('==' relExpr { $p = NF.createCompareNode($p, CompareKind.EQ, $relExpr.p); })
  | ('!=' relExpr { $p = NF.createCompareNode($p, CompareKind.NE, $relExpr.p); })
  )*;


//AndExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//EqExpr<out p>
//{
//	 "&" EqExpr<out p1> 							(. p = NF.createArithmeticOp(ArithmeticOperation.AND, p, p1); .)
//
//}
//.

andExpr returns [DebugExpressionPair p] :
  ( eqExpr { $p = $eqExpr.p; } )
  (
    '&' eqExpr { $p = NF.createArithmeticOp(ArithmeticOperation.AND, $p, $eqExpr.p); }
  )*;


//XorExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//AndExpr<out p>
//{
//	 "^" AndExpr<out p1> 							(.	p = NF.createArithmeticOp(ArithmeticOperation.XOR, p, p1); .)
//
//}
//.

xorExpr returns [DebugExpressionPair p] :
  ( andExpr { $p = $andExpr.p; } )
  (
    '^' andExpr { $p = NF.createArithmeticOp(ArithmeticOperation.XOR, $p, $andExpr.p); }
  )*;


//OrExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//XorExpr<out p>
//{
//	 "|" XorExpr<out p1> 							(.  p = NF.createArithmeticOp(ArithmeticOperation.OR, p, p1); .)
//
//}
//.

orExpr returns [DebugExpressionPair p] :
  ( xorExpr { $p = $xorExpr.p; } )
  (
    '|' xorExpr { $p = NF.createArithmeticOp(ArithmeticOperation.OR, $p, $xorExpr.p); }
  )*;


//LogAndExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//OrExpr<out p>
//{
//	 "&&" OrExpr<out p1> 							(. p= NF.createLogicalAndNode(p, p1); .)
//
//}
//.

logAndExpr returns [DebugExpressionPair p] :
  ( orExpr { $p = $orExpr.p; } )
  (
    '&&' orExpr { $p = NF.createLogicalAndNode($p, $orExpr.p); }
  )*;


//LogOrExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//LogAndExpr<out p>
//{
//	 "||" LogAndExpr<out p> 						(. p= NF.createLogicalOrNode(p, p1); .)
//
//}
//.
logOrExpr returns [DebugExpressionPair p] :
  ( logAndExpr { $p = $logAndExpr.p; } )
  (
    '||' logAndExpr { $p = NF.createLogicalOrNode($p, $logAndExpr.p); }
  )*;

//
//Expr<out DebugExpressionPair p>						(. DebugExpressionPair pThen=null, pElse=null; .)
//=
//LogOrExpr<out p>
//[
//	 "?" Expr<out pThen> ":" Expr<out pElse> 		(. p = NF.createTernaryNode(p, pThen, pElse);.)
//
//]
//.
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


//
//DType<out DebugExprType ty>
//=
//BaseType<out ty>
//{
//		 "*" 										(. ty = ty.createPointer(); .)
//
//}
//{
//	 "["
//
//	(
//		 number										(. ty = ty.createArrayType(Integer.parseInt(t.val)); .)
//	|
//													(. ty = ty.createArrayType(-1); .)
//	)
//	 "]"
//}
//.

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


//BaseType<out DebugExprType ty>						(. ty=null; boolean signed=false;.)
//=
//"(" DType<out ty> ")"
//|
//"void" 												(. ty = DebugExprType.getVoidType(); .)
//|
//
//(
//	 "signed" 										(. signed = true; .)
//
//	|
//	 "unsigned" 									(. signed = false; .)
//
//)
//[
//	 "char" 										(. ty = DebugExprType.getIntType(8, signed); .)
//
//	|
//	 "short" 										(. ty = DebugExprType.getIntType(16, signed); .)
//
//	|
//	 "int" 											(. ty = DebugExprType.getIntType(32, signed); .)
//
//	|
//	 "long" 										(. ty = DebugExprType.getIntType(64, signed); .)
//
//]
//|
//"char" 												(. ty = DebugExprType.getIntType(8, false);.)
//|
//"short" 											(. ty = DebugExprType.getIntType(16, true);.)
//|
//"int" 												(. ty = DebugExprType.getIntType(32, true);.)
//|
//"long" 												(. ty = DebugExprType.getIntType(64, true);.)
//[
//	 "double" 										(. ty = DebugExprType.getFloatType(128); .)
//
//]
//|
//"float" 											(. ty = DebugExprType.getFloatType(32);.)
//|
//"double" 											(. ty = DebugExprType.getFloatType(64);.)
//.

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