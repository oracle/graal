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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.types.Type;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExpressionPair;
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

/*boolean IsCast() {
	Token peek = scanner.Peek();
	if(la.kind==_lpar) {
	    while(peek.kind==_asterisc) peek=scanner.Peek();
	    int k = peek.kind;
	    if(k==_signed||k==_unsigned||k==_int||k==_long||k==_char||k==_short||k==_float||k==_double||k==_typeof) return true;
	}
	return false;
}*/

public void setNodeFactory(DebugExprNodeFactory nodeFactory) {
	if(NF==null) NF=nodeFactory;
}

//public int GetErrors() {
//	return errors.count;
//}

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
//LAPR : '(';
//ASTERISC : '*';
//SIGNED : 'signed';
//UNSIGNED : 'unsigned';
//INT : 'int';
//LONG : 'LONG';
//SHORT : 'short';
//FLOAT : 'float';
//DOUBLE : 'double';
//CHAR : 'char';
//TYPEOF: 'typeof';

WS  : [ \t\r\n]+ -> skip ;

// PRODUCTIONS

//DebugExpr											(. DebugExpressionPair p=null; .)
//=
//Expr<out p> 										(. if(errors.count==0) astRoot =p.getNode(); .)
//.


debugExpr : IDENT;
//debugExpr : expr;

//
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
  | '(' expr ')'                              { $p = $expr.p}
  )
  ;
//
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
designator : primExpr;


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
//
//UnaryExpr<out DebugExpressionPair p>				(. p=null; char kind='\0'; DebugExprType typeP=null;.)
//=
//Designator<out p>
//|
//UnaryOp<out kind> CastExpr<out p> 					(. p = NF.createUnaryOpNode(p, kind); .)
//|
//"sizeof" "(" DType<out typeP> ")" 					(. p=NF.createSizeofNode(typeP); .)
//.
unaryExpr : designator;
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
castExpr : unaryExpr;
//
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
multExpr : castExpr;
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
addExpr : multExpr;
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
shiftExpr : addExpr;
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
relExpr : shiftExpr;
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
eqExpr : relExpr;
//AndExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//EqExpr<out p>
//{
//	 "&" EqExpr<out p1> 							(. p = NF.createArithmeticOp(ArithmeticOperation.AND, p, p1); .)
//
//}
//.
andExpr : eqExpr;
//
//XorExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//AndExpr<out p>
//{
//	 "^" AndExpr<out p1> 							(.	p = NF.createArithmeticOp(ArithmeticOperation.XOR, p, p1); .)
//
//}
//.
xorExpr : andExpr;
//
//OrExpr<out DebugExpressionPair p>					(. DebugExpressionPair p1=null; .)
//=
//XorExpr<out p>
//{
//	 "|" XorExpr<out p1> 							(.  p = NF.createArithmeticOp(ArithmeticOperation.OR, p, p1); .)
//
//}
//.
orExpr : xorExpr;
//
//LogAndExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//OrExpr<out p>
//{
//	 "&&" OrExpr<out p1> 							(. p= NF.createLogicalAndNode(p, p1); .)
//
//}
//.
logAndExpr : orExpr;
//
//LogOrExpr<out DebugExpressionPair p>				(. DebugExpressionPair p1=null; .)
//=
//LogAndExpr<out p>
//{
//	 "||" LogAndExpr<out p> 						(. p= NF.createLogicalOrNode(p, p1); .)
//
//}
//.
logOrExpr returns [DebugExpressionPair p] : logAndExpr;

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
                                       {DebugExpressionPair pThen = null;
                                        DebugExpressionPair pElse = null;
                                        DebugExpressionPair prev = null}
  (
  logOrExpr { prev = $logOrExpr.p; }
  )?
  (
   '?' (expr {pThen = $expr.p}) ':' (expr {pElse = $expr.p})   {$p = NF.createTernaryNode(prev, pThen, pElse);}
  ) ;


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
