/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates.
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

import org.graalvm.shadowed.org.antlr.v4.runtime.atn.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.dfa.DFA;
import org.graalvm.shadowed.org.antlr.v4.runtime.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.misc.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "this-escape"})
public class DebugExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 393730L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
	public static class ActParsContext extends ParserRuleContext {
		public List<DebugExpressionPair> l;
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1587200L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2115584L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 251658240L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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

	@SuppressWarnings("CheckReturnValue")
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
				setState(366);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DOUBLE) {
					{
					setState(364);
					match(DOUBLE);
					 _localctx.ty =  DebugExprType.getFloatType(128); 
					}
				}

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
		"\u0004\u0001.\u0177\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001"+
		">\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0005\u0002Q\b\u0002\n\u0002\f\u0002T\t\u0002\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0005\u0003_\b\u0003\n\u0003\f\u0003b\t\u0003\u0003\u0003"+
		"d\b\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004u\b\u0004"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u0086\b\u0006\u0001\u0006"+
		"\u0003\u0006\u0089\b\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007\u009e\b\u0007\n\u0007"+
		"\f\u0007\u00a1\t\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b"+
		"\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0005\b\u00ae\b\b\n\b\f\b\u00b1"+
		"\t\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0005\t\u00be\b\t\n\t\f\t\u00c1\t\t\u0001\n\u0001\n"+
		"\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0005"+
		"\n\u00d6\b\n\n\n\f\n\u00d9\t\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0005\u000b\u00e6\b\u000b\n\u000b\f\u000b\u00e9\t\u000b"+
		"\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0005\f\u00f2"+
		"\b\f\n\f\f\f\u00f5\t\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r"+
		"\u0001\r\u0005\r\u00fe\b\r\n\r\f\r\u0101\t\r\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u010a"+
		"\b\u000e\n\u000e\f\u000e\u010d\t\u000e\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0005\u000f\u0116\b\u000f"+
		"\n\u000f\f\u000f\u0119\t\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0005\u0010\u0122\b\u0010\n"+
		"\u0010\f\u0010\u0125\t\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u0135"+
		"\b\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0005"+
		"\u0012\u013c\b\u0012\n\u0012\f\u0012\u013f\t\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0003\u0012\u0145\b\u0012\u0001\u0012\u0005\u0012"+
		"\u0148\b\u0012\n\u0012\f\u0012\u014b\t\u0012\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0003\u0013\u0159\b\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0003\u0013\u0163\b\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0003\u0013\u016f\b\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0003\u0013\u0175\b\u0013\u0001\u0013\u0000\u0000\u0014"+
		"\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a"+
		"\u001c\u001e \"$&\u0000\u0001\u0002\u0000\u000b\r\u0013\u0014\u0194\u0000"+
		"(\u0001\u0000\u0000\u0000\u0002/\u0001\u0000\u0000\u0000\u0004?\u0001"+
		"\u0000\u0000\u0000\u0006U\u0001\u0000\u0000\u0000\bt\u0001\u0000\u0000"+
		"\u0000\nv\u0001\u0000\u0000\u0000\fy\u0001\u0000\u0000\u0000\u000e\u008e"+
		"\u0001\u0000\u0000\u0000\u0010\u00a2\u0001\u0000\u0000\u0000\u0012\u00b2"+
		"\u0001\u0000\u0000\u0000\u0014\u00c2\u0001\u0000\u0000\u0000\u0016\u00da"+
		"\u0001\u0000\u0000\u0000\u0018\u00ea\u0001\u0000\u0000\u0000\u001a\u00f6"+
		"\u0001\u0000\u0000\u0000\u001c\u0102\u0001\u0000\u0000\u0000\u001e\u010e"+
		"\u0001\u0000\u0000\u0000 \u011a\u0001\u0000\u0000\u0000\"\u0126\u0001"+
		"\u0000\u0000\u0000$\u0136\u0001\u0000\u0000\u0000&\u014c\u0001\u0000\u0000"+
		"\u0000()\u0006\u0000\uffff\uffff\u0000)*\u0003\"\u0011\u0000*+\u0006\u0000"+
		"\uffff\uffff\u0000+,\u0001\u0000\u0000\u0000,-\u0005\u0000\u0000\u0001"+
		"-.\u0006\u0000\uffff\uffff\u0000.\u0001\u0001\u0000\u0000\u0000/=\u0006"+
		"\u0001\uffff\uffff\u000001\u0005*\u0000\u00001>\u0006\u0001\uffff\uffff"+
		"\u000023\u0005+\u0000\u00003>\u0006\u0001\uffff\uffff\u000045\u0005,\u0000"+
		"\u00005>\u0006\u0001\uffff\uffff\u000067\u0005-\u0000\u00007>\u0006\u0001"+
		"\uffff\uffff\u000089\u0005\t\u0000\u00009:\u0003\"\u0011\u0000:;\u0005"+
		"\n\u0000\u0000;<\u0006\u0001\uffff\uffff\u0000<>\u0001\u0000\u0000\u0000"+
		"=0\u0001\u0000\u0000\u0000=2\u0001\u0000\u0000\u0000=4\u0001\u0000\u0000"+
		"\u0000=6\u0001\u0000\u0000\u0000=8\u0001\u0000\u0000\u0000>\u0003\u0001"+
		"\u0000\u0000\u0000?@\u0003\u0002\u0001\u0000@A\u0006\u0002\uffff\uffff"+
		"\u0000AR\u0001\u0000\u0000\u0000BC\u0005\u0001\u0000\u0000CD\u0003\"\u0011"+
		"\u0000DE\u0005\u0002\u0000\u0000EF\u0006\u0002\uffff\uffff\u0000FQ\u0001"+
		"\u0000\u0000\u0000GH\u0003\u0006\u0003\u0000HI\u0006\u0002\uffff\uffff"+
		"\u0000IQ\u0001\u0000\u0000\u0000JK\u0005\u0011\u0000\u0000KL\u0005*\u0000"+
		"\u0000LQ\u0006\u0002\uffff\uffff\u0000MN\u0005\u0012\u0000\u0000NO\u0005"+
		"*\u0000\u0000OQ\u0006\u0002\uffff\uffff\u0000PB\u0001\u0000\u0000\u0000"+
		"PG\u0001\u0000\u0000\u0000PJ\u0001\u0000\u0000\u0000PM\u0001\u0000\u0000"+
		"\u0000QT\u0001\u0000\u0000\u0000RP\u0001\u0000\u0000\u0000RS\u0001\u0000"+
		"\u0000\u0000S\u0005\u0001\u0000\u0000\u0000TR\u0001\u0000\u0000\u0000"+
		"UV\u0006\u0003\uffff\uffff\u0000Vc\u0005\t\u0000\u0000WX\u0003\"\u0011"+
		"\u0000XY\u0006\u0003\uffff\uffff\u0000Y`\u0001\u0000\u0000\u0000Z[\u0005"+
		"\u0003\u0000\u0000[\\\u0003\"\u0011\u0000\\]\u0006\u0003\uffff\uffff\u0000"+
		"]_\u0001\u0000\u0000\u0000^Z\u0001\u0000\u0000\u0000_b\u0001\u0000\u0000"+
		"\u0000`^\u0001\u0000\u0000\u0000`a\u0001\u0000\u0000\u0000ad\u0001\u0000"+
		"\u0000\u0000b`\u0001\u0000\u0000\u0000cW\u0001\u0000\u0000\u0000cd\u0001"+
		"\u0000\u0000\u0000de\u0001\u0000\u0000\u0000ef\u0005\n\u0000\u0000f\u0007"+
		"\u0001\u0000\u0000\u0000gh\u0003\u0004\u0002\u0000hi\u0006\u0004\uffff"+
		"\uffff\u0000iu\u0001\u0000\u0000\u0000jk\u0003\n\u0005\u0000kl\u0003\f"+
		"\u0006\u0000lm\u0006\u0004\uffff\uffff\u0000mu\u0001\u0000\u0000\u0000"+
		"no\u0005\u0004\u0000\u0000op\u0005\t\u0000\u0000pq\u0003$\u0012\u0000"+
		"qr\u0005\n\u0000\u0000rs\u0006\u0004\uffff\uffff\u0000su\u0001\u0000\u0000"+
		"\u0000tg\u0001\u0000\u0000\u0000tj\u0001\u0000\u0000\u0000tn\u0001\u0000"+
		"\u0000\u0000u\t\u0001\u0000\u0000\u0000vw\u0007\u0000\u0000\u0000wx\u0006"+
		"\u0005\uffff\uffff\u0000x\u000b\u0001\u0000\u0000\u0000y\u0088\u0006\u0006"+
		"\uffff\uffff\u0000z{\u0004\u0006\u0000\u0000{\u0085\u0005\t\u0000\u0000"+
		"|}\u0003$\u0012\u0000}~\u0006\u0006\uffff\uffff\u0000~\u0086\u0001\u0000"+
		"\u0000\u0000\u007f\u0080\u0005)\u0000\u0000\u0080\u0081\u0005\t\u0000"+
		"\u0000\u0081\u0082\u0005*\u0000\u0000\u0082\u0083\u0006\u0006\uffff\uffff"+
		"\u0000\u0083\u0084\u0001\u0000\u0000\u0000\u0084\u0086\u0005\n\u0000\u0000"+
		"\u0085|\u0001\u0000\u0000\u0000\u0085\u007f\u0001\u0000\u0000\u0000\u0086"+
		"\u0087\u0001\u0000\u0000\u0000\u0087\u0089\u0005\n\u0000\u0000\u0088z"+
		"\u0001\u0000\u0000\u0000\u0088\u0089\u0001\u0000\u0000\u0000\u0089\u008a"+
		"\u0001\u0000\u0000\u0000\u008a\u008b\u0003\b\u0004\u0000\u008b\u008c\u0006"+
		"\u0006\uffff\uffff\u0000\u008c\u008d\u0006\u0006\uffff\uffff\u0000\u008d"+
		"\r\u0001\u0000\u0000\u0000\u008e\u008f\u0003\f\u0006\u0000\u008f\u0090"+
		"\u0006\u0007\uffff\uffff\u0000\u0090\u009f\u0001\u0000\u0000\u0000\u0091"+
		"\u0092\u0005\u000b\u0000\u0000\u0092\u0093\u0003\f\u0006\u0000\u0093\u0094"+
		"\u0006\u0007\uffff\uffff\u0000\u0094\u009e\u0001\u0000\u0000\u0000\u0095"+
		"\u0096\u0005\u000e\u0000\u0000\u0096\u0097\u0003\f\u0006\u0000\u0097\u0098"+
		"\u0006\u0007\uffff\uffff\u0000\u0098\u009e\u0001\u0000\u0000\u0000\u0099"+
		"\u009a\u0005\u0015\u0000\u0000\u009a\u009b\u0003\f\u0006\u0000\u009b\u009c"+
		"\u0006\u0007\uffff\uffff\u0000\u009c\u009e\u0001\u0000\u0000\u0000\u009d"+
		"\u0091\u0001\u0000\u0000\u0000\u009d\u0095\u0001\u0000\u0000\u0000\u009d"+
		"\u0099\u0001\u0000\u0000\u0000\u009e\u00a1\u0001\u0000\u0000\u0000\u009f"+
		"\u009d\u0001\u0000\u0000\u0000\u009f\u00a0\u0001\u0000\u0000\u0000\u00a0"+
		"\u000f\u0001\u0000\u0000\u0000\u00a1\u009f\u0001\u0000\u0000\u0000\u00a2"+
		"\u00a3\u0003\u000e\u0007\u0000\u00a3\u00a4\u0006\b\uffff\uffff\u0000\u00a4"+
		"\u00af\u0001\u0000\u0000\u0000\u00a5\u00a6\u0005\f\u0000\u0000\u00a6\u00a7"+
		"\u0003\u000e\u0007\u0000\u00a7\u00a8\u0006\b\uffff\uffff\u0000\u00a8\u00ae"+
		"\u0001\u0000\u0000\u0000\u00a9\u00aa\u0005\r\u0000\u0000\u00aa\u00ab\u0003"+
		"\u000e\u0007\u0000\u00ab\u00ac\u0006\b\uffff\uffff\u0000\u00ac\u00ae\u0001"+
		"\u0000\u0000\u0000\u00ad\u00a5\u0001\u0000\u0000\u0000\u00ad\u00a9\u0001"+
		"\u0000\u0000\u0000\u00ae\u00b1\u0001\u0000\u0000\u0000\u00af\u00ad\u0001"+
		"\u0000\u0000\u0000\u00af\u00b0\u0001\u0000\u0000\u0000\u00b0\u0011\u0001"+
		"\u0000\u0000\u0000\u00b1\u00af\u0001\u0000\u0000\u0000\u00b2\u00b3\u0003"+
		"\u0010\b\u0000\u00b3\u00b4\u0006\t\uffff\uffff\u0000\u00b4\u00bf\u0001"+
		"\u0000\u0000\u0000\u00b5\u00b6\u0005\u0017\u0000\u0000\u00b6\u00b7\u0003"+
		"\u0010\b\u0000\u00b7\u00b8\u0006\t\uffff\uffff\u0000\u00b8\u00be\u0001"+
		"\u0000\u0000\u0000\u00b9\u00ba\u0005\u0016\u0000\u0000\u00ba\u00bb\u0003"+
		"\u0010\b\u0000\u00bb\u00bc\u0006\t\uffff\uffff\u0000\u00bc\u00be\u0001"+
		"\u0000\u0000\u0000\u00bd\u00b5\u0001\u0000\u0000\u0000\u00bd\u00b9\u0001"+
		"\u0000\u0000\u0000\u00be\u00c1\u0001\u0000\u0000\u0000\u00bf\u00bd\u0001"+
		"\u0000\u0000\u0000\u00bf\u00c0\u0001\u0000\u0000\u0000\u00c0\u0013\u0001"+
		"\u0000\u0000\u0000\u00c1\u00bf\u0001\u0000\u0000\u0000\u00c2\u00c3\u0003"+
		"\u0012\t\u0000\u00c3\u00c4\u0006\n\uffff\uffff\u0000\u00c4\u00d7\u0001"+
		"\u0000\u0000\u0000\u00c5\u00c6\u0005\u0019\u0000\u0000\u00c6\u00c7\u0003"+
		"\u0012\t\u0000\u00c7\u00c8\u0006\n\uffff\uffff\u0000\u00c8\u00d6\u0001"+
		"\u0000\u0000\u0000\u00c9\u00ca\u0005\u0018\u0000\u0000\u00ca\u00cb\u0003"+
		"\u0012\t\u0000\u00cb\u00cc\u0006\n\uffff\uffff\u0000\u00cc\u00d6\u0001"+
		"\u0000\u0000\u0000\u00cd\u00ce\u0005\u001b\u0000\u0000\u00ce\u00cf\u0003"+
		"\u0012\t\u0000\u00cf\u00d0\u0006\n\uffff\uffff\u0000\u00d0\u00d6\u0001"+
		"\u0000\u0000\u0000\u00d1\u00d2\u0005\u001a\u0000\u0000\u00d2\u00d3\u0003"+
		"\u0012\t\u0000\u00d3\u00d4\u0006\n\uffff\uffff\u0000\u00d4\u00d6\u0001"+
		"\u0000\u0000\u0000\u00d5\u00c5\u0001\u0000\u0000\u0000\u00d5\u00c9\u0001"+
		"\u0000\u0000\u0000\u00d5\u00cd\u0001\u0000\u0000\u0000\u00d5\u00d1\u0001"+
		"\u0000\u0000\u0000\u00d6\u00d9\u0001\u0000\u0000\u0000\u00d7\u00d5\u0001"+
		"\u0000\u0000\u0000\u00d7\u00d8\u0001\u0000\u0000\u0000\u00d8\u0015\u0001"+
		"\u0000\u0000\u0000\u00d9\u00d7\u0001\u0000\u0000\u0000\u00da\u00db\u0003"+
		"\u0014\n\u0000\u00db\u00dc\u0006\u000b\uffff\uffff\u0000\u00dc\u00e7\u0001"+
		"\u0000\u0000\u0000\u00dd\u00de\u0005\u001c\u0000\u0000\u00de\u00df\u0003"+
		"\u0014\n\u0000\u00df\u00e0\u0006\u000b\uffff\uffff\u0000\u00e0\u00e6\u0001"+
		"\u0000\u0000\u0000\u00e1\u00e2\u0005\u001d\u0000\u0000\u00e2\u00e3\u0003"+
		"\u0014\n\u0000\u00e3\u00e4\u0006\u000b\uffff\uffff\u0000\u00e4\u00e6\u0001"+
		"\u0000\u0000\u0000\u00e5\u00dd\u0001\u0000\u0000\u0000\u00e5\u00e1\u0001"+
		"\u0000\u0000\u0000\u00e6\u00e9\u0001\u0000\u0000\u0000\u00e7\u00e5\u0001"+
		"\u0000\u0000\u0000\u00e7\u00e8\u0001\u0000\u0000\u0000\u00e8\u0017\u0001"+
		"\u0000\u0000\u0000\u00e9\u00e7\u0001\u0000\u0000\u0000\u00ea\u00eb\u0003"+
		"\u0016\u000b\u0000\u00eb\u00ec\u0006\f\uffff\uffff\u0000\u00ec\u00f3\u0001"+
		"\u0000\u0000\u0000\u00ed\u00ee\u0005\u001e\u0000\u0000\u00ee\u00ef\u0003"+
		"\u0016\u000b\u0000\u00ef\u00f0\u0006\f\uffff\uffff\u0000\u00f0\u00f2\u0001"+
		"\u0000\u0000\u0000\u00f1\u00ed\u0001\u0000\u0000\u0000\u00f2\u00f5\u0001"+
		"\u0000\u0000\u0000\u00f3\u00f1\u0001\u0000\u0000\u0000\u00f3\u00f4\u0001"+
		"\u0000\u0000\u0000\u00f4\u0019\u0001\u0000\u0000\u0000\u00f5\u00f3\u0001"+
		"\u0000\u0000\u0000\u00f6\u00f7\u0003\u0018\f\u0000\u00f7\u00f8\u0006\r"+
		"\uffff\uffff\u0000\u00f8\u00ff\u0001\u0000\u0000\u0000\u00f9\u00fa\u0005"+
		" \u0000\u0000\u00fa\u00fb\u0003\u0018\f\u0000\u00fb\u00fc\u0006\r\uffff"+
		"\uffff\u0000\u00fc\u00fe\u0001\u0000\u0000\u0000\u00fd\u00f9\u0001\u0000"+
		"\u0000\u0000\u00fe\u0101\u0001\u0000\u0000\u0000\u00ff\u00fd\u0001\u0000"+
		"\u0000\u0000\u00ff\u0100\u0001\u0000\u0000\u0000\u0100\u001b\u0001\u0000"+
		"\u0000\u0000\u0101\u00ff\u0001\u0000\u0000\u0000\u0102\u0103\u0003\u001a"+
		"\r\u0000\u0103\u0104\u0006\u000e\uffff\uffff\u0000\u0104\u010b\u0001\u0000"+
		"\u0000\u0000\u0105\u0106\u0005\u001f\u0000\u0000\u0106\u0107\u0003\u001a"+
		"\r\u0000\u0107\u0108\u0006\u000e\uffff\uffff\u0000\u0108\u010a\u0001\u0000"+
		"\u0000\u0000\u0109\u0105\u0001\u0000\u0000\u0000\u010a\u010d\u0001\u0000"+
		"\u0000\u0000\u010b\u0109\u0001\u0000\u0000\u0000\u010b\u010c\u0001\u0000"+
		"\u0000\u0000\u010c\u001d\u0001\u0000\u0000\u0000\u010d\u010b\u0001\u0000"+
		"\u0000\u0000\u010e\u010f\u0003\u001c\u000e\u0000\u010f\u0110\u0006\u000f"+
		"\uffff\uffff\u0000\u0110\u0117\u0001\u0000\u0000\u0000\u0111\u0112\u0005"+
		"\u0010\u0000\u0000\u0112\u0113\u0003\u001c\u000e\u0000\u0113\u0114\u0006"+
		"\u000f\uffff\uffff\u0000\u0114\u0116\u0001\u0000\u0000\u0000\u0115\u0111"+
		"\u0001\u0000\u0000\u0000\u0116\u0119\u0001\u0000\u0000\u0000\u0117\u0115"+
		"\u0001\u0000\u0000\u0000\u0117\u0118\u0001\u0000\u0000\u0000\u0118\u001f"+
		"\u0001\u0000\u0000\u0000\u0119\u0117\u0001\u0000\u0000\u0000\u011a\u011b"+
		"\u0003\u001e\u000f\u0000\u011b\u011c\u0006\u0010\uffff\uffff\u0000\u011c"+
		"\u0123\u0001\u0000\u0000\u0000\u011d\u011e\u0005\u000f\u0000\u0000\u011e"+
		"\u011f\u0003\u001e\u000f\u0000\u011f\u0120\u0006\u0010\uffff\uffff\u0000"+
		"\u0120\u0122\u0001\u0000\u0000\u0000\u0121\u011d\u0001\u0000\u0000\u0000"+
		"\u0122\u0125\u0001\u0000\u0000\u0000\u0123\u0121\u0001\u0000\u0000\u0000"+
		"\u0123\u0124\u0001\u0000\u0000\u0000\u0124!\u0001\u0000\u0000\u0000\u0125"+
		"\u0123\u0001\u0000\u0000\u0000\u0126\u0127\u0006\u0011\uffff\uffff\u0000"+
		"\u0127\u0128\u0003 \u0010\u0000\u0128\u0129\u0006\u0011\uffff\uffff\u0000"+
		"\u0129\u0134\u0001\u0000\u0000\u0000\u012a\u012b\u0005\u0005\u0000\u0000"+
		"\u012b\u012c\u0003\"\u0011\u0000\u012c\u012d\u0006\u0011\uffff\uffff\u0000"+
		"\u012d\u012e\u0001\u0000\u0000\u0000\u012e\u012f\u0005\u0006\u0000\u0000"+
		"\u012f\u0130\u0003\"\u0011\u0000\u0130\u0131\u0006\u0011\uffff\uffff\u0000"+
		"\u0131\u0132\u0001\u0000\u0000\u0000\u0132\u0133\u0006\u0011\uffff\uffff"+
		"\u0000\u0133\u0135\u0001\u0000\u0000\u0000\u0134\u012a\u0001\u0000\u0000"+
		"\u0000\u0134\u0135\u0001\u0000\u0000\u0000\u0135#\u0001\u0000\u0000\u0000"+
		"\u0136\u0137\u0003&\u0013\u0000\u0137\u0138\u0006\u0012\uffff\uffff\u0000"+
		"\u0138\u013d\u0001\u0000\u0000\u0000\u0139\u013a\u0005\u000b\u0000\u0000"+
		"\u013a\u013c\u0006\u0012\uffff\uffff\u0000\u013b\u0139\u0001\u0000\u0000"+
		"\u0000\u013c\u013f\u0001\u0000\u0000\u0000\u013d\u013b\u0001\u0000\u0000"+
		"\u0000\u013d\u013e\u0001\u0000\u0000\u0000\u013e\u0149\u0001\u0000\u0000"+
		"\u0000\u013f\u013d\u0001\u0000\u0000\u0000\u0140\u0144\u0005\u0001\u0000"+
		"\u0000\u0141\u0142\u0005+\u0000\u0000\u0142\u0145\u0006\u0012\uffff\uffff"+
		"\u0000\u0143\u0145\u0006\u0012\uffff\uffff\u0000\u0144\u0141\u0001\u0000"+
		"\u0000\u0000\u0144\u0143\u0001\u0000\u0000\u0000\u0145\u0146\u0001\u0000"+
		"\u0000\u0000\u0146\u0148\u0005\u0002\u0000\u0000\u0147\u0140\u0001\u0000"+
		"\u0000\u0000\u0148\u014b\u0001\u0000\u0000\u0000\u0149\u0147\u0001\u0000"+
		"\u0000\u0000\u0149\u014a\u0001\u0000\u0000\u0000\u014a%\u0001\u0000\u0000"+
		"\u0000\u014b\u0149\u0001\u0000\u0000\u0000\u014c\u0174\u0006\u0013\uffff"+
		"\uffff\u0000\u014d\u014e\u0005\t\u0000\u0000\u014e\u014f\u0003$\u0012"+
		"\u0000\u014f\u0150\u0005\n\u0000\u0000\u0150\u0151\u0006\u0013\uffff\uffff"+
		"\u0000\u0151\u0175\u0001\u0000\u0000\u0000\u0152\u0153\u0005\u0007\u0000"+
		"\u0000\u0153\u0175\u0006\u0013\uffff\uffff\u0000\u0154\u0155\u0005!\u0000"+
		"\u0000\u0155\u0159\u0006\u0013\uffff\uffff\u0000\u0156\u0157\u0005\"\u0000"+
		"\u0000\u0157\u0159\u0006\u0013\uffff\uffff\u0000\u0158\u0154\u0001\u0000"+
		"\u0000\u0000\u0158\u0156\u0001\u0000\u0000\u0000\u0159\u0162\u0001\u0000"+
		"\u0000\u0000\u015a\u015b\u0005(\u0000\u0000\u015b\u0163\u0006\u0013\uffff"+
		"\uffff\u0000\u015c\u015d\u0005%\u0000\u0000\u015d\u0163\u0006\u0013\uffff"+
		"\uffff\u0000\u015e\u015f\u0005#\u0000\u0000\u015f\u0163\u0006\u0013\uffff"+
		"\uffff\u0000\u0160\u0161\u0005\b\u0000\u0000\u0161\u0163\u0006\u0013\uffff"+
		"\uffff\u0000\u0162\u015a\u0001\u0000\u0000\u0000\u0162\u015c\u0001\u0000"+
		"\u0000\u0000\u0162\u015e\u0001\u0000\u0000\u0000\u0162\u0160\u0001\u0000"+
		"\u0000\u0000\u0162\u0163\u0001\u0000\u0000\u0000\u0163\u0175\u0001\u0000"+
		"\u0000\u0000\u0164\u0165\u0005(\u0000\u0000\u0165\u0175\u0006\u0013\uffff"+
		"\uffff\u0000\u0166\u0167\u0005%\u0000\u0000\u0167\u0175\u0006\u0013\uffff"+
		"\uffff\u0000\u0168\u0169\u0005#\u0000\u0000\u0169\u0175\u0006\u0013\uffff"+
		"\uffff\u0000\u016a\u016b\u0005\b\u0000\u0000\u016b\u016e\u0006\u0013\uffff"+
		"\uffff\u0000\u016c\u016d\u0005\'\u0000\u0000\u016d\u016f\u0006\u0013\uffff"+
		"\uffff\u0000\u016e\u016c\u0001\u0000\u0000\u0000\u016e\u016f\u0001\u0000"+
		"\u0000\u0000\u016f\u0175\u0001\u0000\u0000\u0000\u0170\u0171\u0005&\u0000"+
		"\u0000\u0171\u0175\u0006\u0013\uffff\uffff\u0000\u0172\u0173\u0005\'\u0000"+
		"\u0000\u0173\u0175\u0006\u0013\uffff\uffff\u0000\u0174\u014d\u0001\u0000"+
		"\u0000\u0000\u0174\u0152\u0001\u0000\u0000\u0000\u0174\u0158\u0001\u0000"+
		"\u0000\u0000\u0174\u0164\u0001\u0000\u0000\u0000\u0174\u0166\u0001\u0000"+
		"\u0000\u0000\u0174\u0168\u0001\u0000\u0000\u0000\u0174\u016a\u0001\u0000"+
		"\u0000\u0000\u0174\u0170\u0001\u0000\u0000\u0000\u0174\u0172\u0001\u0000"+
		"\u0000\u0000\u0175\'\u0001\u0000\u0000\u0000\u001f=PR`ct\u0085\u0088\u009d"+
		"\u009f\u00ad\u00af\u00bd\u00bf\u00d5\u00d7\u00e5\u00e7\u00f3\u00ff\u010b"+
		"\u0117\u0123\u0134\u013d\u0144\u0149\u0158\u0162\u016e\u0174";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
