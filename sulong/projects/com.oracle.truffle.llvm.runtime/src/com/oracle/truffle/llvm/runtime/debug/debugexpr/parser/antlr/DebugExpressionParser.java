/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr;

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

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings("all")
public class DebugExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, LAPR=9, 
		RAPR=10, ASTERISC=11, PLUS=12, MINUS=13, DIVIDE=14, LOGICOR=15, LOGICAND=16, 
		DOT=17, POINTER=18, EXCLAM=19, TILDA=20, MODULAR=21, SHIFTR=22, SHIFTL=23, 
		GT=24, LT=25, GTE=26, LTE=27, EQ=28, NE=29, AND=30, OR=31, XOR=32, SIGNED=33, 
		UNSIGNED=34, INT=35, LONG=36, SHORT=37, FLOAT=38, DOUBLE=39, CHAR=40, 
		TYPEOF=41, IDENT=42, NUMBER=43, FLOATNUMBER=44, CHARCONST=45, WS=46;
	public static final int
		RULE_debugExpr = 0, RULE_primExpr = 1, RULE_designator = 2, RULE_actPars = 3, 
		RULE_unaryExpr = 4, RULE_unaryOP = 5, RULE_castExpr = 6, RULE_multExpr = 7, 
		RULE_addExpr = 8, RULE_shiftExpr = 9, RULE_relExpr = 10, RULE_eqExpr = 11, 
		RULE_andExpr = 12, RULE_xorExpr = 13, RULE_orExpr = 14, RULE_logAndExpr = 15, 
		RULE_logOrExpr = 16, RULE_expr = 17, RULE_dType = 18, RULE_baseType = 19;
	private static String[] makeRuleNames() {
		return new String[] {
			"debugExpr", "primExpr", "designator", "actPars", "unaryExpr", "unaryOP", 
			"castExpr", "multExpr", "addExpr", "shiftExpr", "relExpr", "eqExpr", 
			"andExpr", "xorExpr", "orExpr", "logAndExpr", "logOrExpr", "expr", "dType", 
			"baseType"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'['", "']'", "','", "'sizeof'", "'?'", "':'", "'void'", "'long'", 
			"'('", "')'", "'*'", "'+'", "'-'", "'/'", "'||'", "'&&'", "'.'", "'->'", 
			"'!'", "'~'", "'%'", "'>>'", "'<<'", "'>'", "'<'", "'>='", "'<='", "'=='", 
			"'!='", "'&'", "'|'", "'^'", "'signed'", "'unsigned'", "'int'", "'LONG'", 
			"'short'", "'float'", "'double'", "'char'", "'typeof'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, "LAPR", "RAPR", 
			"ASTERISC", "PLUS", "MINUS", "DIVIDE", "LOGICOR", "LOGICAND", "DOT", 
			"POINTER", "EXCLAM", "TILDA", "MODULAR", "SHIFTR", "SHIFTL", "GT", "LT", 
			"GTE", "LTE", "EQ", "NE", "AND", "OR", "XOR", "SIGNED", "UNSIGNED", "INT", 
			"LONG", "SHORT", "FLOAT", "DOUBLE", "CHAR", "TYPEOF", "IDENT", "NUMBER", 
			"FLOATNUMBER", "CHARCONST", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "DebugExpression.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }



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


	public DebugExpressionParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class DebugExprContext extends ParserRuleContext {
		public ExprContext expr;
		public TerminalNode EOF() { return getToken(DebugExpressionParser.EOF, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public DebugExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_debugExpr; }
	}

	public final DebugExprContext debugExpr() throws RecognitionException {
		DebugExprContext _localctx = new DebugExprContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_debugExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExpressionPair p = null;
			  
			{
			{
			setState(41);
			_localctx.expr = expr();
			 p = _localctx.expr.p; 
			}
			setState(44);
			match(EOF);
			if(_syntaxErrors == 0){ astRoot = p.getNode();}
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PrimExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public Token t;
		public ExprContext expr;
		public TerminalNode LAPR() { return getToken(DebugExpressionParser.LAPR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RAPR() { return getToken(DebugExpressionParser.RAPR, 0); }
		public TerminalNode IDENT() { return getToken(DebugExpressionParser.IDENT, 0); }
		public TerminalNode NUMBER() { return getToken(DebugExpressionParser.NUMBER, 0); }
		public TerminalNode FLOATNUMBER() { return getToken(DebugExpressionParser.FLOATNUMBER, 0); }
		public TerminalNode CHARCONST() { return getToken(DebugExpressionParser.CHARCONST, 0); }
		public PrimExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primExpr; }
	}

	public final PrimExprContext primExpr() throws RecognitionException {
		PrimExprContext _localctx = new PrimExprContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_primExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{

			  _localctx.p =  null;
			  
			setState(61);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
				{
				{
				setState(48);
				_localctx.t = match(IDENT);
				}
				 _localctx.p =  NF.createVarNode(_localctx.t.getText()); 
				}
				break;
			case NUMBER:
				{
				{
				setState(50);
				_localctx.t = match(NUMBER);
				}
				 _localctx.p =  NF.createIntegerConstant(Integer.parseInt(_localctx.t.getText())); 
				}
				break;
			case FLOATNUMBER:
				{
				{
				setState(52);
				_localctx.t = match(FLOATNUMBER);
				}
				 _localctx.p =  NF.createFloatConstant(Float.parseFloat(_localctx.t.getText())); 
				}
				break;
			case CHARCONST:
				{
				{
				setState(54);
				_localctx.t = match(CHARCONST);
				}
				 _localctx.p =  NF.createCharacterConstant(_localctx.t.getText()); 
				}
				break;
			case LAPR:
				{
				setState(56);
				match(LAPR);
				setState(57);
				_localctx.expr = expr();
				setState(58);
				match(RAPR);
				 _localctx.p =  _localctx.expr.p; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DesignatorContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public PrimExprContext primExpr;
		public ExprContext expr;
		public ActParsContext actPars;
		public Token t;
		public PrimExprContext primExpr() {
			return getRuleContext(PrimExprContext.class,0);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<ActParsContext> actPars() {
			return getRuleContexts(ActParsContext.class);
		}
		public ActParsContext actPars(int i) {
			return getRuleContext(ActParsContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(DebugExpressionParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(DebugExpressionParser.DOT, i);
		}
		public List<TerminalNode> POINTER() { return getTokens(DebugExpressionParser.POINTER); }
		public TerminalNode POINTER(int i) {
			return getToken(DebugExpressionParser.POINTER, i);
		}
		public List<TerminalNode> IDENT() { return getTokens(DebugExpressionParser.IDENT); }
		public TerminalNode IDENT(int i) {
			return getToken(DebugExpressionParser.IDENT, i);
		}
		public DesignatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_designator; }
	}

	public final DesignatorContext designator() throws RecognitionException {
		DesignatorContext _localctx = new DesignatorContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_designator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(63);
			_localctx.primExpr = primExpr();
			 _localctx.p =  _localctx.primExpr.p; 
			}
			setState(82);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << LAPR) | (1L << DOT) | (1L << POINTER))) != 0)) {
				{
				setState(80);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
					{
					{
					setState(66);
					match(T__0);
					setState(67);
					_localctx.expr = expr();
					setState(68);
					match(T__1);
					 _localctx.p =  NF.createArrayElement(_localctx.p, _localctx.expr.p); 
					}
					}
					break;
				case LAPR:
					{
					{
					setState(71);
					_localctx.actPars = actPars();
					 _localctx.p =  NF.createFunctionCall(_localctx.p, _localctx.actPars.l); 
					}
					}
					break;
				case DOT:
					{
					{
					setState(74);
					match(DOT);
					{
					setState(75);
					_localctx.t = match(IDENT);
					}
					 _localctx.p =  NF.createObjectMember(_localctx.p, _localctx.t.getText()); 
					}
					}
					break;
				case POINTER:
					{
					{
					setState(77);
					match(POINTER);
					{
					setState(78);
					_localctx.t = match(IDENT);
					}
					 _localctx.p =  NF.createObjectPointerMember(_localctx.p, _localctx.t.getText()); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(84);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ActParsContext extends ParserRuleContext {
		public List l;
		public ExprContext expr;
		public TerminalNode LAPR() { return getToken(DebugExpressionParser.LAPR, 0); }
		public TerminalNode RAPR() { return getToken(DebugExpressionParser.RAPR, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public ActParsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_actPars; }
	}

	public final ActParsContext actPars() throws RecognitionException {
		ActParsContext _localctx = new ActParsContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_actPars);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  _localctx.l =  new LinkedList<DebugExpressionPair>();
			  
			setState(86);
			match(LAPR);
			setState(99);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				{
				{
				setState(87);
				_localctx.expr = expr();
				 _localctx.l.add(_localctx.expr.p); 
				}
				setState(96);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__2) {
					{
					{
					setState(90);
					match(T__2);
					setState(91);
					_localctx.expr = expr();
					 _localctx.l.add(_localctx.expr.p); 
					}
					}
					setState(98);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			}
			setState(101);
			match(RAPR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UnaryExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public DesignatorContext designator;
		public UnaryOPContext unaryOP;
		public CastExprContext castExpr;
		public DTypeContext dType;
		public DesignatorContext designator() {
			return getRuleContext(DesignatorContext.class,0);
		}
		public UnaryOPContext unaryOP() {
			return getRuleContext(UnaryOPContext.class,0);
		}
		public CastExprContext castExpr() {
			return getRuleContext(CastExprContext.class,0);
		}
		public TerminalNode LAPR() { return getToken(DebugExpressionParser.LAPR, 0); }
		public DTypeContext dType() {
			return getRuleContext(DTypeContext.class,0);
		}
		public TerminalNode RAPR() { return getToken(DebugExpressionParser.RAPR, 0); }
		public UnaryExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unaryExpr; }
	}

	public final UnaryExprContext unaryExpr() throws RecognitionException {
		UnaryExprContext _localctx = new UnaryExprContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_unaryExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(116);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LAPR:
			case IDENT:
			case NUMBER:
			case FLOATNUMBER:
			case CHARCONST:
				{
				setState(103);
				_localctx.designator = designator();
				 _localctx.p =  _localctx.designator.p; 
				}
				break;
			case ASTERISC:
			case PLUS:
			case MINUS:
			case EXCLAM:
			case TILDA:
				{
				setState(106);
				_localctx.unaryOP = unaryOP();
				setState(107);
				_localctx.castExpr = castExpr();
				 _localctx.p =  NF.createUnaryOpNode(_localctx.castExpr.p, _localctx.unaryOP.kind); 
				}
				break;
			case T__3:
				{
				setState(110);
				match(T__3);
				setState(111);
				match(LAPR);
				setState(112);
				_localctx.dType = dType();
				setState(113);
				match(RAPR);
				 _localctx.p =  NF.createSizeofNode(_localctx.dType.ty); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UnaryOPContext extends ParserRuleContext {
		public char kind;
		public Token t;
		public TerminalNode ASTERISC() { return getToken(DebugExpressionParser.ASTERISC, 0); }
		public TerminalNode PLUS() { return getToken(DebugExpressionParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(DebugExpressionParser.MINUS, 0); }
		public TerminalNode TILDA() { return getToken(DebugExpressionParser.TILDA, 0); }
		public TerminalNode EXCLAM() { return getToken(DebugExpressionParser.EXCLAM, 0); }
		public UnaryOPContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unaryOP; }
	}

	public final UnaryOPContext unaryOP() throws RecognitionException {
		UnaryOPContext _localctx = new UnaryOPContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_unaryOP);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(118);
			_localctx.t = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ASTERISC) | (1L << PLUS) | (1L << MINUS) | (1L << EXCLAM) | (1L << TILDA))) != 0)) ) {
				_localctx.t = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 _localctx.kind =  _localctx.t.getText().charAt(0); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CastExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public DTypeContext dType;
		public Token t;
		public UnaryExprContext unaryExpr;
		public UnaryExprContext unaryExpr() {
			return getRuleContext(UnaryExprContext.class,0);
		}
		public List<TerminalNode> LAPR() { return getTokens(DebugExpressionParser.LAPR); }
		public TerminalNode LAPR(int i) {
			return getToken(DebugExpressionParser.LAPR, i);
		}
		public List<TerminalNode> RAPR() { return getTokens(DebugExpressionParser.RAPR); }
		public TerminalNode RAPR(int i) {
			return getToken(DebugExpressionParser.RAPR, i);
		}
		public DTypeContext dType() {
			return getRuleContext(DTypeContext.class,0);
		}
		public TerminalNode TYPEOF() { return getToken(DebugExpressionParser.TYPEOF, 0); }
		public TerminalNode IDENT() { return getToken(DebugExpressionParser.IDENT, 0); }
		public CastExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_castExpr; }
	}

	public final CastExprContext castExpr() throws RecognitionException {
		CastExprContext _localctx = new CastExprContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_castExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExprType typeP = null;
			  DebugExprTypeofNode typeNode = null;
			  
			setState(136);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				{
				setState(122);
				if (!( IsCast() )) throw new FailedPredicateException(this, " IsCast() ");
				{
				setState(123);
				match(LAPR);
				setState(133);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__6:
				case T__7:
				case LAPR:
				case SIGNED:
				case UNSIGNED:
				case INT:
				case SHORT:
				case FLOAT:
				case DOUBLE:
				case CHAR:
					{
					{
					setState(124);
					_localctx.dType = dType();
					 typeP = _localctx.dType.ty; 
					}
					}
					break;
				case TYPEOF:
					{
					{
					setState(127);
					match(TYPEOF);
					setState(128);
					match(LAPR);
					{
					{
					setState(129);
					_localctx.t = match(IDENT);
					}
					 typeNode = NF.createTypeofNode(_localctx.t.getText());
					}
					setState(132);
					match(RAPR);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(135);
				match(RAPR);
				}
				}
				break;
			}
			{
			setState(138);
			_localctx.unaryExpr = unaryExpr();
			 _localctx.p =  _localctx.unaryExpr.p; 
			 if (typeP != null) { _localctx.p =  NF.createCastIfNecessary(_localctx.p, typeP); }
			                                                      if (typeNode != null) { _localctx.p =  NF.createPointerCastNode(_localctx.p, typeNode); }
			                                                    
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MultExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public CastExprContext castExpr;
		public List<CastExprContext> castExpr() {
			return getRuleContexts(CastExprContext.class);
		}
		public CastExprContext castExpr(int i) {
			return getRuleContext(CastExprContext.class,i);
		}
		public List<TerminalNode> ASTERISC() { return getTokens(DebugExpressionParser.ASTERISC); }
		public TerminalNode ASTERISC(int i) {
			return getToken(DebugExpressionParser.ASTERISC, i);
		}
		public List<TerminalNode> DIVIDE() { return getTokens(DebugExpressionParser.DIVIDE); }
		public TerminalNode DIVIDE(int i) {
			return getToken(DebugExpressionParser.DIVIDE, i);
		}
		public List<TerminalNode> MODULAR() { return getTokens(DebugExpressionParser.MODULAR); }
		public TerminalNode MODULAR(int i) {
			return getToken(DebugExpressionParser.MODULAR, i);
		}
		public MultExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multExpr; }
	}

	public final MultExprContext multExpr() throws RecognitionException {
		MultExprContext _localctx = new MultExprContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_multExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(142);
			_localctx.castExpr = castExpr();
			 _localctx.p =  _localctx.castExpr.p; 
			}
			setState(159);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ASTERISC) | (1L << DIVIDE) | (1L << MODULAR))) != 0)) {
				{
				setState(157);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ASTERISC:
					{
					{
					setState(145);
					match(ASTERISC);
					setState(146);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.MUL, _localctx.p, _localctx.castExpr.p); 
					}
					}
					break;
				case DIVIDE:
					{
					{
					setState(149);
					match(DIVIDE);
					setState(150);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createDivNode(_localctx.p, _localctx.castExpr.p); 
					}
					}
					break;
				case MODULAR:
					{
					{
					setState(153);
					match(MODULAR);
					setState(154);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createRemNode(_localctx.p, _localctx.castExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(161);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AddExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public MultExprContext multExpr;
		public List<MultExprContext> multExpr() {
			return getRuleContexts(MultExprContext.class);
		}
		public MultExprContext multExpr(int i) {
			return getRuleContext(MultExprContext.class,i);
		}
		public List<TerminalNode> PLUS() { return getTokens(DebugExpressionParser.PLUS); }
		public TerminalNode PLUS(int i) {
			return getToken(DebugExpressionParser.PLUS, i);
		}
		public List<TerminalNode> MINUS() { return getTokens(DebugExpressionParser.MINUS); }
		public TerminalNode MINUS(int i) {
			return getToken(DebugExpressionParser.MINUS, i);
		}
		public AddExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_addExpr; }
	}

	public final AddExprContext addExpr() throws RecognitionException {
		AddExprContext _localctx = new AddExprContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_addExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(162);
			_localctx.multExpr = multExpr();
			 _localctx.p =  _localctx.multExpr.p; 
			}
			setState(175);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==PLUS || _la==MINUS) {
				{
				setState(173);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case PLUS:
					{
					{
					setState(165);
					match(PLUS);
					setState(166);
					_localctx.multExpr = multExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.ADD, _localctx.p, _localctx.multExpr.p);
					}
					}
					break;
				case MINUS:
					{
					{
					setState(169);
					match(MINUS);
					setState(170);
					_localctx.multExpr = multExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.SUB, _localctx.p, _localctx.multExpr.p);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(177);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ShiftExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public AddExprContext addExpr;
		public List<AddExprContext> addExpr() {
			return getRuleContexts(AddExprContext.class);
		}
		public AddExprContext addExpr(int i) {
			return getRuleContext(AddExprContext.class,i);
		}
		public List<TerminalNode> SHIFTL() { return getTokens(DebugExpressionParser.SHIFTL); }
		public TerminalNode SHIFTL(int i) {
			return getToken(DebugExpressionParser.SHIFTL, i);
		}
		public List<TerminalNode> SHIFTR() { return getTokens(DebugExpressionParser.SHIFTR); }
		public TerminalNode SHIFTR(int i) {
			return getToken(DebugExpressionParser.SHIFTR, i);
		}
		public ShiftExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shiftExpr; }
	}

	public final ShiftExprContext shiftExpr() throws RecognitionException {
		ShiftExprContext _localctx = new ShiftExprContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_shiftExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(178);
			_localctx.addExpr = addExpr();
			 _localctx.p =  _localctx.addExpr.p; 
			}
			setState(191);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==SHIFTR || _la==SHIFTL) {
				{
				setState(189);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case SHIFTL:
					{
					{
					setState(181);
					match(SHIFTL);
					setState(182);
					_localctx.addExpr = addExpr();
					 _localctx.p =  NF.createShiftLeft(_localctx.p, _localctx.addExpr.p); 
					}
					}
					break;
				case SHIFTR:
					{
					{
					setState(185);
					match(SHIFTR);
					setState(186);
					_localctx.addExpr = addExpr();
					 _localctx.p =  NF.createShiftRight(_localctx.p, _localctx.addExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(193);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RelExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public ShiftExprContext shiftExpr;
		public List<ShiftExprContext> shiftExpr() {
			return getRuleContexts(ShiftExprContext.class);
		}
		public ShiftExprContext shiftExpr(int i) {
			return getRuleContext(ShiftExprContext.class,i);
		}
		public List<TerminalNode> LT() { return getTokens(DebugExpressionParser.LT); }
		public TerminalNode LT(int i) {
			return getToken(DebugExpressionParser.LT, i);
		}
		public List<TerminalNode> GT() { return getTokens(DebugExpressionParser.GT); }
		public TerminalNode GT(int i) {
			return getToken(DebugExpressionParser.GT, i);
		}
		public List<TerminalNode> LTE() { return getTokens(DebugExpressionParser.LTE); }
		public TerminalNode LTE(int i) {
			return getToken(DebugExpressionParser.LTE, i);
		}
		public List<TerminalNode> GTE() { return getTokens(DebugExpressionParser.GTE); }
		public TerminalNode GTE(int i) {
			return getToken(DebugExpressionParser.GTE, i);
		}
		public RelExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relExpr; }
	}

	public final RelExprContext relExpr() throws RecognitionException {
		RelExprContext _localctx = new RelExprContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_relExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(194);
			_localctx.shiftExpr = shiftExpr();
			 _localctx.p =  _localctx.shiftExpr.p; 
			}
			setState(215);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << GT) | (1L << LT) | (1L << GTE) | (1L << LTE))) != 0)) {
				{
				setState(213);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LT:
					{
					{
					setState(197);
					match(LT);
					setState(198);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.LT, _localctx.shiftExpr.p); 
					}
					}
					break;
				case GT:
					{
					{
					setState(201);
					match(GT);
					setState(202);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.GT, _localctx.shiftExpr.p); 
					}
					}
					break;
				case LTE:
					{
					{
					setState(205);
					match(LTE);
					setState(206);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.LE, _localctx.shiftExpr.p); 
					}
					}
					break;
				case GTE:
					{
					{
					setState(209);
					match(GTE);
					setState(210);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.GE, _localctx.shiftExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(217);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EqExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public RelExprContext relExpr;
		public List<RelExprContext> relExpr() {
			return getRuleContexts(RelExprContext.class);
		}
		public RelExprContext relExpr(int i) {
			return getRuleContext(RelExprContext.class,i);
		}
		public List<TerminalNode> EQ() { return getTokens(DebugExpressionParser.EQ); }
		public TerminalNode EQ(int i) {
			return getToken(DebugExpressionParser.EQ, i);
		}
		public List<TerminalNode> NE() { return getTokens(DebugExpressionParser.NE); }
		public TerminalNode NE(int i) {
			return getToken(DebugExpressionParser.NE, i);
		}
		public EqExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eqExpr; }
	}

	public final EqExprContext eqExpr() throws RecognitionException {
		EqExprContext _localctx = new EqExprContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_eqExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(218);
			_localctx.relExpr = relExpr();
			 _localctx.p =  _localctx.relExpr.p; 
			}
			setState(231);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NE) {
				{
				setState(229);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case EQ:
					{
					{
					setState(221);
					match(EQ);
					setState(222);
					_localctx.relExpr = relExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.EQ, _localctx.relExpr.p); 
					}
					}
					break;
				case NE:
					{
					{
					setState(225);
					match(NE);
					setState(226);
					_localctx.relExpr = relExpr();
					 _localctx.p =  NF.createCompareNode(_localctx.p, CompareKind.NE, _localctx.relExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(233);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AndExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public EqExprContext eqExpr;
		public List<EqExprContext> eqExpr() {
			return getRuleContexts(EqExprContext.class);
		}
		public EqExprContext eqExpr(int i) {
			return getRuleContext(EqExprContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(DebugExpressionParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(DebugExpressionParser.AND, i);
		}
		public AndExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_andExpr; }
	}

	public final AndExprContext andExpr() throws RecognitionException {
		AndExprContext _localctx = new AndExprContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_andExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(234);
			_localctx.eqExpr = eqExpr();
			 _localctx.p =  _localctx.eqExpr.p; 
			}
			setState(243);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(237);
				match(AND);
				setState(238);
				_localctx.eqExpr = eqExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.AND, _localctx.p, _localctx.eqExpr.p); 
				}
				}
				setState(245);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class XorExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public AndExprContext andExpr;
		public List<AndExprContext> andExpr() {
			return getRuleContexts(AndExprContext.class);
		}
		public AndExprContext andExpr(int i) {
			return getRuleContext(AndExprContext.class,i);
		}
		public List<TerminalNode> XOR() { return getTokens(DebugExpressionParser.XOR); }
		public TerminalNode XOR(int i) {
			return getToken(DebugExpressionParser.XOR, i);
		}
		public XorExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_xorExpr; }
	}

	public final XorExprContext xorExpr() throws RecognitionException {
		XorExprContext _localctx = new XorExprContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_xorExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(246);
			_localctx.andExpr = andExpr();
			 _localctx.p =  _localctx.andExpr.p; 
			}
			setState(255);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==XOR) {
				{
				{
				setState(249);
				match(XOR);
				setState(250);
				_localctx.andExpr = andExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.XOR, _localctx.p, _localctx.andExpr.p); 
				}
				}
				setState(257);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class OrExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public XorExprContext xorExpr;
		public List<XorExprContext> xorExpr() {
			return getRuleContexts(XorExprContext.class);
		}
		public XorExprContext xorExpr(int i) {
			return getRuleContext(XorExprContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(DebugExpressionParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(DebugExpressionParser.OR, i);
		}
		public OrExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orExpr; }
	}

	public final OrExprContext orExpr() throws RecognitionException {
		OrExprContext _localctx = new OrExprContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_orExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(258);
			_localctx.xorExpr = xorExpr();
			 _localctx.p =  _localctx.xorExpr.p; 
			}
			setState(267);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(261);
				match(OR);
				setState(262);
				_localctx.xorExpr = xorExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.OR, _localctx.p, _localctx.xorExpr.p); 
				}
				}
				setState(269);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LogAndExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public OrExprContext orExpr;
		public List<OrExprContext> orExpr() {
			return getRuleContexts(OrExprContext.class);
		}
		public OrExprContext orExpr(int i) {
			return getRuleContext(OrExprContext.class,i);
		}
		public List<TerminalNode> LOGICAND() { return getTokens(DebugExpressionParser.LOGICAND); }
		public TerminalNode LOGICAND(int i) {
			return getToken(DebugExpressionParser.LOGICAND, i);
		}
		public LogAndExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logAndExpr; }
	}

	public final LogAndExprContext logAndExpr() throws RecognitionException {
		LogAndExprContext _localctx = new LogAndExprContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_logAndExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(270);
			_localctx.orExpr = orExpr();
			 _localctx.p =  _localctx.orExpr.p; 
			}
			setState(279);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LOGICAND) {
				{
				{
				setState(273);
				match(LOGICAND);
				setState(274);
				_localctx.orExpr = orExpr();
				 _localctx.p =  NF.createLogicalAndNode(_localctx.p, _localctx.orExpr.p); 
				}
				}
				setState(281);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LogOrExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public LogAndExprContext logAndExpr;
		public List<LogAndExprContext> logAndExpr() {
			return getRuleContexts(LogAndExprContext.class);
		}
		public LogAndExprContext logAndExpr(int i) {
			return getRuleContext(LogAndExprContext.class,i);
		}
		public List<TerminalNode> LOGICOR() { return getTokens(DebugExpressionParser.LOGICOR); }
		public TerminalNode LOGICOR(int i) {
			return getToken(DebugExpressionParser.LOGICOR, i);
		}
		public LogOrExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logOrExpr; }
	}

	public final LogOrExprContext logOrExpr() throws RecognitionException {
		LogOrExprContext _localctx = new LogOrExprContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_logOrExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(282);
			_localctx.logAndExpr = logAndExpr();
			 _localctx.p =  _localctx.logAndExpr.p; 
			}
			setState(291);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LOGICOR) {
				{
				{
				setState(285);
				match(LOGICOR);
				setState(286);
				_localctx.logAndExpr = logAndExpr();
				 _localctx.p =  NF.createLogicalOrNode(_localctx.p, _localctx.logAndExpr.p); 
				}
				}
				setState(293);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExprContext extends ParserRuleContext {
		public DebugExpressionPair p;
		public LogOrExprContext logOrExpr;
		public ExprContext expr;
		public LogOrExprContext logOrExpr() {
			return getRuleContext(LogOrExprContext.class,0);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExpressionPair pThen = null;
			  DebugExpressionPair pElse = null;
			  
			{
			setState(295);
			_localctx.logOrExpr = logOrExpr();
			 _localctx.p =  _localctx.logOrExpr.p; 
			}
			setState(308);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(298);
				match(T__4);
				{
				setState(299);
				_localctx.expr = expr();
				 pThen = _localctx.expr.p; 
				}
				setState(302);
				match(T__5);
				{
				setState(303);
				_localctx.expr = expr();
				 pElse = _localctx.expr.p; 
				}
				 _localctx.p =  NF.createTernaryNode(_localctx.p, pThen, pElse); 
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DTypeContext extends ParserRuleContext {
		public DebugExprType ty;
		public BaseTypeContext baseType;
		public Token t;
		public BaseTypeContext baseType() {
			return getRuleContext(BaseTypeContext.class,0);
		}
		public List<TerminalNode> ASTERISC() { return getTokens(DebugExpressionParser.ASTERISC); }
		public TerminalNode ASTERISC(int i) {
			return getToken(DebugExpressionParser.ASTERISC, i);
		}
		public List<TerminalNode> NUMBER() { return getTokens(DebugExpressionParser.NUMBER); }
		public TerminalNode NUMBER(int i) {
			return getToken(DebugExpressionParser.NUMBER, i);
		}
		public DTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dType; }
	}

	public final DTypeContext dType() throws RecognitionException {
		DTypeContext _localctx = new DTypeContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_dType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(310);
			_localctx.baseType = baseType();
			 _localctx.ty =  _localctx.baseType.ty; 
			}
			setState(317);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ASTERISC) {
				{
				{
				{
				setState(313);
				match(ASTERISC);
				}
				 _localctx.ty =  _localctx.ty.createPointer(); 
				}
				}
				setState(319);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(329);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(320);
				match(T__0);
				setState(324);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NUMBER:
					{
					{
					setState(321);
					_localctx.t = match(NUMBER);
					}
					 _localctx.ty =  _localctx.ty.createArrayType(Integer.parseInt(_localctx.t.getText()));
					}
					break;
				case T__1:
					{
					 _localctx.ty =  _localctx.ty.createArrayType(-1); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(326);
				match(T__1);
				}
				}
				setState(331);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BaseTypeContext extends ParserRuleContext {
		public DebugExprType ty;
		public DTypeContext dType;
		public TerminalNode LAPR() { return getToken(DebugExpressionParser.LAPR, 0); }
		public DTypeContext dType() {
			return getRuleContext(DTypeContext.class,0);
		}
		public TerminalNode RAPR() { return getToken(DebugExpressionParser.RAPR, 0); }
		public TerminalNode CHAR() { return getToken(DebugExpressionParser.CHAR, 0); }
		public TerminalNode SHORT() { return getToken(DebugExpressionParser.SHORT, 0); }
		public TerminalNode INT() { return getToken(DebugExpressionParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(DebugExpressionParser.FLOAT, 0); }
		public TerminalNode DOUBLE() { return getToken(DebugExpressionParser.DOUBLE, 0); }
		public TerminalNode SIGNED() { return getToken(DebugExpressionParser.SIGNED, 0); }
		public TerminalNode UNSIGNED() { return getToken(DebugExpressionParser.UNSIGNED, 0); }
		public BaseTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_baseType; }
	}

	public final BaseTypeContext baseType() throws RecognitionException {
		BaseTypeContext _localctx = new BaseTypeContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_baseType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  _localctx.ty =  null;
			  boolean signed = false;
			  
			setState(372);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LAPR:
				{
				setState(333);
				match(LAPR);
				setState(334);
				_localctx.dType = dType();
				setState(335);
				match(RAPR);
				 _localctx.ty =  _localctx.dType.ty; 
				}
				break;
			case T__6:
				{
				setState(338);
				match(T__6);
				 _localctx.ty =  DebugExprType.getVoidType(); 
				}
				break;
			case SIGNED:
			case UNSIGNED:
				{
				{
				setState(344);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case SIGNED:
					{
					setState(340);
					match(SIGNED);
					 signed = true; 
					}
					break;
				case UNSIGNED:
					{
					setState(342);
					match(UNSIGNED);
					 signed = false; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(354);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case CHAR:
					{
					setState(346);
					match(CHAR);
					 _localctx.ty =  DebugExprType.getIntType(8, signed); 
					}
					break;
				case SHORT:
					{
					setState(348);
					match(SHORT);
					 _localctx.ty =  DebugExprType.getIntType(16, signed); 
					}
					break;
				case INT:
					{
					setState(350);
					match(INT);
					 _localctx.ty =  DebugExprType.getIntType(32, signed); 
					}
					break;
				case T__7:
					{
					setState(352);
					match(T__7);
					 _localctx.ty =  DebugExprType.getIntType(64, signed); 
					}
					break;
				case T__0:
				case RAPR:
				case ASTERISC:
					break;
				default:
					break;
				}
				}
				}
				break;
			case CHAR:
				{
				setState(356);
				match(CHAR);
				 _localctx.ty =  DebugExprType.getIntType(8, false); 
				}
				break;
			case SHORT:
				{
				setState(358);
				match(SHORT);
				 _localctx.ty =  DebugExprType.getIntType(16, true); 
				}
				break;
			case INT:
				{
				setState(360);
				match(INT);
				 _localctx.ty =  DebugExprType.getIntType(32, true); 
				}
				break;
			case T__7:
				{
				{
				setState(362);
				match(T__7);
				 _localctx.ty =  DebugExprType.getIntType(64, true); 
				setState(365);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DOUBLE) {
					{
					setState(364);
					match(DOUBLE);
					}
				}

				 _localctx.ty =  DebugExprType.getFloatType(128); 
				}
				}
				break;
			case FLOAT:
				{
				setState(368);
				match(FLOAT);
				 _localctx.ty =  DebugExprType.getFloatType(32); 
				}
				break;
			case DOUBLE:
				{
				setState(370);
				match(DOUBLE);
				 _localctx.ty =  DebugExprType.getFloatType(64); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 6:
			return castExpr_sempred((CastExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean castExpr_sempred(CastExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return  IsCast() ;
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\60\u0179\4\2\t\2"+
		"\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3@\n\3\3\4\3\4\3\4\3\4"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\7\4S\n\4\f\4\16\4"+
		"V\13\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\7\5a\n\5\f\5\16\5d\13\5\5\5"+
		"f\n\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\5\6"+
		"w\n\6\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\b"+
		"\u0088\n\b\3\b\5\b\u008b\n\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\3"+
		"\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\7\t\u00a0\n\t\f\t\16\t\u00a3\13\t\3"+
		"\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\7\n\u00b0\n\n\f\n\16\n\u00b3"+
		"\13\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\7\13\u00c0"+
		"\n\13\f\13\16\13\u00c3\13\13\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3"+
		"\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\7\f\u00d8\n\f\f\f\16\f\u00db\13\f\3"+
		"\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\7\r\u00e8\n\r\f\r\16\r\u00eb"+
		"\13\r\3\16\3\16\3\16\3\16\3\16\3\16\3\16\7\16\u00f4\n\16\f\16\16\16\u00f7"+
		"\13\16\3\17\3\17\3\17\3\17\3\17\3\17\3\17\7\17\u0100\n\17\f\17\16\17\u0103"+
		"\13\17\3\20\3\20\3\20\3\20\3\20\3\20\3\20\7\20\u010c\n\20\f\20\16\20\u010f"+
		"\13\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21\7\21\u0118\n\21\f\21\16\21\u011b"+
		"\13\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22\7\22\u0124\n\22\f\22\16\22\u0127"+
		"\13\22\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23"+
		"\3\23\5\23\u0137\n\23\3\24\3\24\3\24\3\24\3\24\7\24\u013e\n\24\f\24\16"+
		"\24\u0141\13\24\3\24\3\24\3\24\3\24\5\24\u0147\n\24\3\24\7\24\u014a\n"+
		"\24\f\24\16\24\u014d\13\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\5\25\u015b\n\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\5\25\u0165\n\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\5\25\u0170"+
		"\n\25\3\25\3\25\3\25\3\25\3\25\5\25\u0177\n\25\3\25\2\2\26\2\4\6\b\n\f"+
		"\16\20\22\24\26\30\32\34\36 \"$&(\2\3\4\2\r\17\25\26\2\u0196\2*\3\2\2"+
		"\2\4\61\3\2\2\2\6A\3\2\2\2\bW\3\2\2\2\nv\3\2\2\2\fx\3\2\2\2\16{\3\2\2"+
		"\2\20\u0090\3\2\2\2\22\u00a4\3\2\2\2\24\u00b4\3\2\2\2\26\u00c4\3\2\2\2"+
		"\30\u00dc\3\2\2\2\32\u00ec\3\2\2\2\34\u00f8\3\2\2\2\36\u0104\3\2\2\2 "+
		"\u0110\3\2\2\2\"\u011c\3\2\2\2$\u0128\3\2\2\2&\u0138\3\2\2\2(\u014e\3"+
		"\2\2\2*+\b\2\1\2+,\5$\23\2,-\b\2\1\2-.\3\2\2\2./\7\2\2\3/\60\b\2\1\2\60"+
		"\3\3\2\2\2\61?\b\3\1\2\62\63\7,\2\2\63@\b\3\1\2\64\65\7-\2\2\65@\b\3\1"+
		"\2\66\67\7.\2\2\67@\b\3\1\289\7/\2\29@\b\3\1\2:;\7\13\2\2;<\5$\23\2<="+
		"\7\f\2\2=>\b\3\1\2>@\3\2\2\2?\62\3\2\2\2?\64\3\2\2\2?\66\3\2\2\2?8\3\2"+
		"\2\2?:\3\2\2\2@\5\3\2\2\2AB\5\4\3\2BC\b\4\1\2CT\3\2\2\2DE\7\3\2\2EF\5"+
		"$\23\2FG\7\4\2\2GH\b\4\1\2HS\3\2\2\2IJ\5\b\5\2JK\b\4\1\2KS\3\2\2\2LM\7"+
		"\23\2\2MN\7,\2\2NS\b\4\1\2OP\7\24\2\2PQ\7,\2\2QS\b\4\1\2RD\3\2\2\2RI\3"+
		"\2\2\2RL\3\2\2\2RO\3\2\2\2SV\3\2\2\2TR\3\2\2\2TU\3\2\2\2U\7\3\2\2\2VT"+
		"\3\2\2\2WX\b\5\1\2Xe\7\13\2\2YZ\5$\23\2Z[\b\5\1\2[b\3\2\2\2\\]\7\5\2\2"+
		"]^\5$\23\2^_\b\5\1\2_a\3\2\2\2`\\\3\2\2\2ad\3\2\2\2b`\3\2\2\2bc\3\2\2"+
		"\2cf\3\2\2\2db\3\2\2\2eY\3\2\2\2ef\3\2\2\2fg\3\2\2\2gh\7\f\2\2h\t\3\2"+
		"\2\2ij\5\6\4\2jk\b\6\1\2kw\3\2\2\2lm\5\f\7\2mn\5\16\b\2no\b\6\1\2ow\3"+
		"\2\2\2pq\7\6\2\2qr\7\13\2\2rs\5&\24\2st\7\f\2\2tu\b\6\1\2uw\3\2\2\2vi"+
		"\3\2\2\2vl\3\2\2\2vp\3\2\2\2w\13\3\2\2\2xy\t\2\2\2yz\b\7\1\2z\r\3\2\2"+
		"\2{\u008a\b\b\1\2|}\6\b\2\2}\u0087\7\13\2\2~\177\5&\24\2\177\u0080\b\b"+
		"\1\2\u0080\u0088\3\2\2\2\u0081\u0082\7+\2\2\u0082\u0083\7\13\2\2\u0083"+
		"\u0084\7,\2\2\u0084\u0085\b\b\1\2\u0085\u0086\3\2\2\2\u0086\u0088\7\f"+
		"\2\2\u0087~\3\2\2\2\u0087\u0081\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u008b"+
		"\7\f\2\2\u008a|\3\2\2\2\u008a\u008b\3\2\2\2\u008b\u008c\3\2\2\2\u008c"+
		"\u008d\5\n\6\2\u008d\u008e\b\b\1\2\u008e\u008f\b\b\1\2\u008f\17\3\2\2"+
		"\2\u0090\u0091\5\16\b\2\u0091\u0092\b\t\1\2\u0092\u00a1\3\2\2\2\u0093"+
		"\u0094\7\r\2\2\u0094\u0095\5\16\b\2\u0095\u0096\b\t\1\2\u0096\u00a0\3"+
		"\2\2\2\u0097\u0098\7\20\2\2\u0098\u0099\5\16\b\2\u0099\u009a\b\t\1\2\u009a"+
		"\u00a0\3\2\2\2\u009b\u009c\7\27\2\2\u009c\u009d\5\16\b\2\u009d\u009e\b"+
		"\t\1\2\u009e\u00a0\3\2\2\2\u009f\u0093\3\2\2\2\u009f\u0097\3\2\2\2\u009f"+
		"\u009b\3\2\2\2\u00a0\u00a3\3\2\2\2\u00a1\u009f\3\2\2\2\u00a1\u00a2\3\2"+
		"\2\2\u00a2\21\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a4\u00a5\5\20\t\2\u00a5\u00a6"+
		"\b\n\1\2\u00a6\u00b1\3\2\2\2\u00a7\u00a8\7\16\2\2\u00a8\u00a9\5\20\t\2"+
		"\u00a9\u00aa\b\n\1\2\u00aa\u00b0\3\2\2\2\u00ab\u00ac\7\17\2\2\u00ac\u00ad"+
		"\5\20\t\2\u00ad\u00ae\b\n\1\2\u00ae\u00b0\3\2\2\2\u00af\u00a7\3\2\2\2"+
		"\u00af\u00ab\3\2\2\2\u00b0\u00b3\3\2\2\2\u00b1\u00af\3\2\2\2\u00b1\u00b2"+
		"\3\2\2\2\u00b2\23\3\2\2\2\u00b3\u00b1\3\2\2\2\u00b4\u00b5\5\22\n\2\u00b5"+
		"\u00b6\b\13\1\2\u00b6\u00c1\3\2\2\2\u00b7\u00b8\7\31\2\2\u00b8\u00b9\5"+
		"\22\n\2\u00b9\u00ba\b\13\1\2\u00ba\u00c0\3\2\2\2\u00bb\u00bc\7\30\2\2"+
		"\u00bc\u00bd\5\22\n\2\u00bd\u00be\b\13\1\2\u00be\u00c0\3\2\2\2\u00bf\u00b7"+
		"\3\2\2\2\u00bf\u00bb\3\2\2\2\u00c0\u00c3\3\2\2\2\u00c1\u00bf\3\2\2\2\u00c1"+
		"\u00c2\3\2\2\2\u00c2\25\3\2\2\2\u00c3\u00c1\3\2\2\2\u00c4\u00c5\5\24\13"+
		"\2\u00c5\u00c6\b\f\1\2\u00c6\u00d9\3\2\2\2\u00c7\u00c8\7\33\2\2\u00c8"+
		"\u00c9\5\24\13\2\u00c9\u00ca\b\f\1\2\u00ca\u00d8\3\2\2\2\u00cb\u00cc\7"+
		"\32\2\2\u00cc\u00cd\5\24\13\2\u00cd\u00ce\b\f\1\2\u00ce\u00d8\3\2\2\2"+
		"\u00cf\u00d0\7\35\2\2\u00d0\u00d1\5\24\13\2\u00d1\u00d2\b\f\1\2\u00d2"+
		"\u00d8\3\2\2\2\u00d3\u00d4\7\34\2\2\u00d4\u00d5\5\24\13\2\u00d5\u00d6"+
		"\b\f\1\2\u00d6\u00d8\3\2\2\2\u00d7\u00c7\3\2\2\2\u00d7\u00cb\3\2\2\2\u00d7"+
		"\u00cf\3\2\2\2\u00d7\u00d3\3\2\2\2\u00d8\u00db\3\2\2\2\u00d9\u00d7\3\2"+
		"\2\2\u00d9\u00da\3\2\2\2\u00da\27\3\2\2\2\u00db\u00d9\3\2\2\2\u00dc\u00dd"+
		"\5\26\f\2\u00dd\u00de\b\r\1\2\u00de\u00e9\3\2\2\2\u00df\u00e0\7\36\2\2"+
		"\u00e0\u00e1\5\26\f\2\u00e1\u00e2\b\r\1\2\u00e2\u00e8\3\2\2\2\u00e3\u00e4"+
		"\7\37\2\2\u00e4\u00e5\5\26\f\2\u00e5\u00e6\b\r\1\2\u00e6\u00e8\3\2\2\2"+
		"\u00e7\u00df\3\2\2\2\u00e7\u00e3\3\2\2\2\u00e8\u00eb\3\2\2\2\u00e9\u00e7"+
		"\3\2\2\2\u00e9\u00ea\3\2\2\2\u00ea\31\3\2\2\2\u00eb\u00e9\3\2\2\2\u00ec"+
		"\u00ed\5\30\r\2\u00ed\u00ee\b\16\1\2\u00ee\u00f5\3\2\2\2\u00ef\u00f0\7"+
		" \2\2\u00f0\u00f1\5\30\r\2\u00f1\u00f2\b\16\1\2\u00f2\u00f4\3\2\2\2\u00f3"+
		"\u00ef\3\2\2\2\u00f4\u00f7\3\2\2\2\u00f5\u00f3\3\2\2\2\u00f5\u00f6\3\2"+
		"\2\2\u00f6\33\3\2\2\2\u00f7\u00f5\3\2\2\2\u00f8\u00f9\5\32\16\2\u00f9"+
		"\u00fa\b\17\1\2\u00fa\u0101\3\2\2\2\u00fb\u00fc\7\"\2\2\u00fc\u00fd\5"+
		"\32\16\2\u00fd\u00fe\b\17\1\2\u00fe\u0100\3\2\2\2\u00ff\u00fb\3\2\2\2"+
		"\u0100\u0103\3\2\2\2\u0101\u00ff\3\2\2\2\u0101\u0102\3\2\2\2\u0102\35"+
		"\3\2\2\2\u0103\u0101\3\2\2\2\u0104\u0105\5\34\17\2\u0105\u0106\b\20\1"+
		"\2\u0106\u010d\3\2\2\2\u0107\u0108\7!\2\2\u0108\u0109\5\34\17\2\u0109"+
		"\u010a\b\20\1\2\u010a\u010c\3\2\2\2\u010b\u0107\3\2\2\2\u010c\u010f\3"+
		"\2\2\2\u010d\u010b\3\2\2\2\u010d\u010e\3\2\2\2\u010e\37\3\2\2\2\u010f"+
		"\u010d\3\2\2\2\u0110\u0111\5\36\20\2\u0111\u0112\b\21\1\2\u0112\u0119"+
		"\3\2\2\2\u0113\u0114\7\22\2\2\u0114\u0115\5\36\20\2\u0115\u0116\b\21\1"+
		"\2\u0116\u0118\3\2\2\2\u0117\u0113\3\2\2\2\u0118\u011b\3\2\2\2\u0119\u0117"+
		"\3\2\2\2\u0119\u011a\3\2\2\2\u011a!\3\2\2\2\u011b\u0119\3\2\2\2\u011c"+
		"\u011d\5 \21\2\u011d\u011e\b\22\1\2\u011e\u0125\3\2\2\2\u011f\u0120\7"+
		"\21\2\2\u0120\u0121\5 \21\2\u0121\u0122\b\22\1\2\u0122\u0124\3\2\2\2\u0123"+
		"\u011f\3\2\2\2\u0124\u0127\3\2\2\2\u0125\u0123\3\2\2\2\u0125\u0126\3\2"+
		"\2\2\u0126#\3\2\2\2\u0127\u0125\3\2\2\2\u0128\u0129\b\23\1\2\u0129\u012a"+
		"\5\"\22\2\u012a\u012b\b\23\1\2\u012b\u0136\3\2\2\2\u012c\u012d\7\7\2\2"+
		"\u012d\u012e\5$\23\2\u012e\u012f\b\23\1\2\u012f\u0130\3\2\2\2\u0130\u0131"+
		"\7\b\2\2\u0131\u0132\5$\23\2\u0132\u0133\b\23\1\2\u0133\u0134\3\2\2\2"+
		"\u0134\u0135\b\23\1\2\u0135\u0137\3\2\2\2\u0136\u012c\3\2\2\2\u0136\u0137"+
		"\3\2\2\2\u0137%\3\2\2\2\u0138\u0139\5(\25\2\u0139\u013a\b\24\1\2\u013a"+
		"\u013f\3\2\2\2\u013b\u013c\7\r\2\2\u013c\u013e\b\24\1\2\u013d\u013b\3"+
		"\2\2\2\u013e\u0141\3\2\2\2\u013f\u013d\3\2\2\2\u013f\u0140\3\2\2\2\u0140"+
		"\u014b\3\2\2\2\u0141\u013f\3\2\2\2\u0142\u0146\7\3\2\2\u0143\u0144\7-"+
		"\2\2\u0144\u0147\b\24\1\2\u0145\u0147\b\24\1\2\u0146\u0143\3\2\2\2\u0146"+
		"\u0145\3\2\2\2\u0147\u0148\3\2\2\2\u0148\u014a\7\4\2\2\u0149\u0142\3\2"+
		"\2\2\u014a\u014d\3\2\2\2\u014b\u0149\3\2\2\2\u014b\u014c\3\2\2\2\u014c"+
		"\'\3\2\2\2\u014d\u014b\3\2\2\2\u014e\u0176\b\25\1\2\u014f\u0150\7\13\2"+
		"\2\u0150\u0151\5&\24\2\u0151\u0152\7\f\2\2\u0152\u0153\b\25\1\2\u0153"+
		"\u0177\3\2\2\2\u0154\u0155\7\t\2\2\u0155\u0177\b\25\1\2\u0156\u0157\7"+
		"#\2\2\u0157\u015b\b\25\1\2\u0158\u0159\7$\2\2\u0159\u015b\b\25\1\2\u015a"+
		"\u0156\3\2\2\2\u015a\u0158\3\2\2\2\u015b\u0164\3\2\2\2\u015c\u015d\7*"+
		"\2\2\u015d\u0165\b\25\1\2\u015e\u015f\7\'\2\2\u015f\u0165\b\25\1\2\u0160"+
		"\u0161\7%\2\2\u0161\u0165\b\25\1\2\u0162\u0163\7\n\2\2\u0163\u0165\b\25"+
		"\1\2\u0164\u015c\3\2\2\2\u0164\u015e\3\2\2\2\u0164\u0160\3\2\2\2\u0164"+
		"\u0162\3\2\2\2\u0164\u0165\3\2\2\2\u0165\u0177\3\2\2\2\u0166\u0167\7*"+
		"\2\2\u0167\u0177\b\25\1\2\u0168\u0169\7\'\2\2\u0169\u0177\b\25\1\2\u016a"+
		"\u016b\7%\2\2\u016b\u0177\b\25\1\2\u016c\u016d\7\n\2\2\u016d\u016f\b\25"+
		"\1\2\u016e\u0170\7)\2\2\u016f\u016e\3\2\2\2\u016f\u0170\3\2\2\2\u0170"+
		"\u0171\3\2\2\2\u0171\u0177\b\25\1\2\u0172\u0173\7(\2\2\u0173\u0177\b\25"+
		"\1\2\u0174\u0175\7)\2\2\u0175\u0177\b\25\1\2\u0176\u014f\3\2\2\2\u0176"+
		"\u0154\3\2\2\2\u0176\u015a\3\2\2\2\u0176\u0166\3\2\2\2\u0176\u0168\3\2"+
		"\2\2\u0176\u016a\3\2\2\2\u0176\u016c\3\2\2\2\u0176\u0172\3\2\2\2\u0176"+
		"\u0174\3\2\2\2\u0177)\3\2\2\2!?RTbev\u0087\u008a\u009f\u00a1\u00af\u00b1"+
		"\u00bf\u00c1\u00d7\u00d9\u00e7\u00e9\u00f5\u0101\u010d\u0119\u0125\u0136"+
		"\u013f\u0146\u014b\u015a\u0164\u016f\u0176";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
