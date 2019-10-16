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
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
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
    TokenSource tokenSource = _input.getTokenSource();
	Token peek = tokenSource.nextToken();
	if (peek.getType() == LAPR) {
	    while(peek.getType() == ASTERISC) peek = tokenSource.nextToken();
	    int tokenType = peek.getType();
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

fragment
LETTER : [a-zA-Z];
DIGIT : [0-9];
CR : '\r';
LF : '\n';
SINGLECOMMA : '\'';
QUOTE : '"';


// Add token declarations here.
// Example:
IDENT : LETTER (LETTER | DIGIT)*;
NUMBER : DIGIT+;
FLOATNUMBER : DIGIT+ '.' DIGIT+ ( [eE] [+-] DIGIT+ )?;
CHARCONST : SINGLECOMMA (LETTER|DIGIT) SINGLECOMMA;
LAPR : '(';
ASTERISC : '*';
SIGNED : 'signed';
UNSIGNED : 'unsigned';
INT : 'int';
LONG : 'LONG';
SHORT : 'short';
FLOAT : 'float';
DOUBLE : 'double';
CHAR : 'char';
TYPEOF: 'typeof';

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
  (expr { p = $expr.p; })                        {if(_syntaxErrors == 0) astRoot = p.getNode();}
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
  ( t=IDENT                                   { $p = NF.createVarNode($t.getText()); }
  | t=NUMBER                                  { $p = NF.createIntegerConstant(Integer.parseInt($t.getText())); }
  | t=FLOATNUMBER                             { $p = NF.createFloatConstant(Float.parseFloat($t.getText())); }
  | t=CHARCONST                               { $p = NF.createCharacterConstant($t.getText()); }
  | '(' expr ')'                              { $p = $expr.p; }
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
  {
  DebugExpressionPair idxPair = null;
  List <DebugExpressionPair> list;
  DebugExpressionPair prev = null;
  }
  (
  primExpr { prev = $primExpr.p; }
  )?
  ( '[' expr { idxPair = $expr.p; } ']'      { $p = NF.createArrayElement(prev, idxPair); }
  | (actPars { list = $actPars.l; })         { $p = NF.createFunctionCall(prev, list); }
  | '.' (t=IDENT)                            { $p = NF.createObjectMember(prev, $t.getText()); }
  | '->' (t=IDENT)                           { $p = NF.createObjectPointerMember(prev, $t.getText()); }
  );


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
  DebugExpressionPair p1 = null;
  DebugExpressionPair p2 = null;
  $l = new LinkedList<DebugExpressionPair>();
  }
  '(' (expr { p1 = $expr.p; }) { $l.add(p1); }
  ',' (expr { p2 = $expr.p; }) { $l.add(p2); }
  ')';


//UnaryExpr<out DebugExpressionPair p>				(. p=null; char kind='\0'; DebugExprType typeP=null;.)
//=
//Designator<out p>
//|
//UnaryOp<out kind> CastExpr<out p> 					(. p = NF.createUnaryOpNode(p, kind); .)
//|
//"sizeof" "(" DType<out typeP> ")" 					(. p=NF.createSizeofNode(typeP); .)
//.

unaryExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair prev = null;
  char kind = '\0';
  DebugExprType typeP = null;
  }
  (
  designator { prev = $designator.p; }                                              { $p = prev; }
  | (unaryOP { kind = $unaryOP.kind; }) (castExpr { prev = $castExpr.p; })          { $p = NF.createUnaryOpNode(prev, kind); }
  | 'sizeof' '(' (dType { typeP = $dType.ty; }) ')'                                 { $p = NF.createSizeofNode(typeP); }
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
  DebugExpressionPair prev;
  }

  {if(IsCast())}(
  '('
  ( dType { typeP = $dType.ty; }
  | 'typeof' '(' t=IDENT                          { typeNode = NF.createTypeofNode($t.getText()); }
    ')'
  )?
  ')'?
  )
  unaryExpr {prev = $unaryExpr.p;}                  { if (typeP != null) { $p = NF.createCastIfNecessary(prev, typeP); }
                                                      if (typeNode != null) { $p = NF.createPointerCastNode(prev, typeNode);} }
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
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  castExpr { prev = $castExpr.p; }
  )?
  ( '*' (castExpr { p1 = $castExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.MUL, prev, p1); }
  | '/' (castExpr { p1 = $castExpr.p; })                    { $p = NF.createDivNode(prev, p1); }
  | '%' (castExpr { p1 = $castExpr.p; })                    { $p = NF.createDivNode(prev, p1); }
  );


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
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  multExpr { prev = $multExpr.p; }
  )?
  ( '+' (multExpr { p1 = $multExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.ADD, prev, p1); }
  | '-' (multExpr { p1 = $multExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.SUB, prev, p1); }
  );


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
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  addExpr { prev = $addExpr.p; }
  )?
  ( '>>' (addExpr { p1 = $addExpr.p; })                    { $p = NF.createShiftLeft(prev, p1); }
  | '<<' (addExpr { p1 = $addExpr.p; })                    { $p = NF.createShiftRight(prev, p1); }
  );


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
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  shiftExpr { prev = $shiftExpr.p; }
  )?
  ( '<' (shiftExpr { p1 = $shiftExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.LT, p1); }
  | '>' (shiftExpr { p1 = $shiftExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.GT, p1); }
  | '<=' (shiftExpr { p1 = $shiftExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.LE, p1); }
  | '>=' (shiftExpr { p1 = $shiftExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.GE, p1); }
  );


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
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  relExpr { prev = $relExpr.p; }
  )?
  ( '==' (relExpr { p1 = $relExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.EQ, p1); }
  | '!=' (relExpr { p1 = $relExpr.p; })                    { $p = NF.createCompareNode(prev, CompareKind.NE, p1); }
  );


//AndExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//EqExpr<out p>
//{
//	 "&" EqExpr<out p1> 							(. p = NF.createArithmeticOp(ArithmeticOperation.AND, p, p1); .)
//
//}
//.

andExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  eqExpr { prev = $eqExpr.p; }
  )?
  ( '&' (eqExpr { p1 = $eqExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.AND, prev, p1); }
  );


//XorExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//AndExpr<out p>
//{
//	 "^" AndExpr<out p1> 							(.	p = NF.createArithmeticOp(ArithmeticOperation.XOR, p, p1); .)
//
//}
//.

xorExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  andExpr { prev = $andExpr.p; }
  )?
  ( '^' (andExpr { p1 = $andExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.XOR, prev, p1); }
  );


//OrExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//XorExpr<out p>
//{
//	 "|" XorExpr<out p1> 							(.  p = NF.createArithmeticOp(ArithmeticOperation.OR, p, p1); .)
//
//}
//.

orExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  xorExpr { prev = $xorExpr.p; }
  )?
  ( '|' (xorExpr { p1 = $xorExpr.p; })                    { $p = NF.createArithmeticOp(ArithmeticOperation.OR, prev, p1); }
  );


//LogAndExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//OrExpr<out p>
//{
//	 "&&" OrExpr<out p1> 							(. p= NF.createLogicalAndNode(p, p1); .)
//
//}
//.

logAndExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  orExpr { prev = $orExpr.p; }
  )?
  ( '&&' (orExpr { p1 = $orExpr.p; })                    { $p = NF.createLogicalAndNode(prev, p1); }
  );


//LogOrExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//LogAndExpr<out p>
//{
//	 "||" LogAndExpr<out p> 						(. p= NF.createLogicalOrNode(p, p1); .)
//
//}
//.
logOrExpr returns [DebugExpressionPair p] :
  {
  DebugExpressionPair p1 = null;
  DebugExpressionPair prev = null;
  }
  (
  logAndExpr { prev = $logAndExpr.p; }
  )?
  ( '||' (logAndExpr { p1 = $logAndExpr.p; })                    { $p = NF.createLogicalOrNode(prev, p1); }
  );

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
  DebugExpressionPair prev = null;
  }
  (
  logOrExpr { prev = $logOrExpr.p; }
  )?
  (
   '?' (expr { pThen = $expr.p; }) ':' (expr { pElse = $expr.p; })   { $p = NF.createTernaryNode(prev, pThen, pElse); }
  );


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
  {
  DebugExprType tempTy = null;
  }
  (
  baseType { tempTy = $baseType.ty; }
  )?
  (
  '*'                                               { $ty = tempTy.createPointer(); }
  | '['
    ( t=NUMBER                                      { $ty = tempTy.createArrayType(Integer.parseInt($t.getText()));}
    |                                               { $ty = tempTy.createArrayType(-1); }
    )
  ']'
  );


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
  '(' dType ')'                                                     { $ty = $dType.ty; }
  | 'void'                                                          { $ty = DebugExprType.getVoidType(); }
  | ( 'signed'                                                      { signed = true; }
    | 'unsigned'                                                    { signed = false; }
    )?
     ( 'char'                                                       { $ty = DebugExprType.getIntType(8, signed); }
     | 'short'                                                      { $ty = DebugExprType.getIntType(16, signed); }
     | 'int'                                                        { $ty = DebugExprType.getIntType(32, signed); }
     | 'long'                                                       { $ty = DebugExprType.getIntType(64, signed); }
     )
  | 'char'                                                          { $ty = DebugExprType.getIntType(8, false); }
  | 'short'                                                         { $ty = DebugExprType.getIntType(16, true); }
  | 'int'                                                           { $ty = DebugExprType.getIntType(32, true); }
  | ( 'long'                                                        { $ty = DebugExprType.getIntType(64, true); }
    )?
     ( 'double'                                                     { $ty = DebugExprType.getFloatType(128); }
     )
  | 'float'                                                         { $ty = DebugExprType.getFloatType(32); }
  | 'double'                                                        { $ty = DebugExprType.getFloatType(64); }
  )
  ;