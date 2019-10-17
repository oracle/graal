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
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr;

// DO NOT MODIFY - generated from DebugExpression.g4 using "mx create-parsers"

import java.util.LinkedList;
import java.util.List;

import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory.CompareKind;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprTypeofNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExpressionPair;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@SuppressWarnings("all")
public class DebugExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.1", RuntimeMetaData.VERSION); }

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
	public static final String[] ruleNames = {
		"debugExpr", "primExpr", "designator", "actPars", "unaryExpr", "unaryOP", 
		"castExpr", "multExpr", "addExpr", "shiftExpr", "relExpr", "eqExpr", "andExpr", 
		"xorExpr", "orExpr", "logAndExpr", "logOrExpr", "expr", "dType", "baseType"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'['", "']'", "','", "'sizeof'", "'?'", "':'", "'void'", "'long'", 
		"'('", "')'", "'*'", "'+'", "'-'", "'/'", "'||'", "'&&'", "'.'", "'->'", 
		"'!'", "'~'", "'%'", "'>>'", "'<<'", "'>'", "'<'", "'>='", "'<='", "'=='", 
		"'!='", "'&'", "'|'", "'^'", "'signed'", "'unsigned'", "'int'", "'LONG'", 
		"'short'", "'float'", "'double'", "'char'", "'typeof'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, "LAPR", "RAPR", 
		"ASTERISC", "PLUS", "MINUS", "DIVIDE", "LOGICOR", "LOGICAND", "DOT", "POINTER", 
		"EXCLAM", "TILDA", "MODULAR", "SHIFTR", "SHIFTL", "GT", "LT", "GTE", "LTE", 
		"EQ", "NE", "AND", "OR", "XOR", "SIGNED", "UNSIGNED", "INT", "LONG", "SHORT", 
		"FLOAT", "DOUBLE", "CHAR", "TYPEOF", "IDENT", "NUMBER", "FLOATNUMBER", 
		"CHARCONST", "WS"
	};
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(64);
			_localctx.primExpr = primExpr();
			 prev = _localctx.primExpr.p; _localctx.p =  prev;
			}
			setState(83);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << LAPR) | (1L << DOT) | (1L << POINTER))) != 0)) {
				{
				setState(81);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
					{
					{
					setState(67);
					match(T__0);
					setState(68);
					_localctx.expr = expr();
					setState(69);
					match(T__1);
					 _localctx.p =  NF.createArrayElement(prev, _localctx.expr.p); 
					}
					}
					break;
				case LAPR:
					{
					{
					setState(72);
					_localctx.actPars = actPars();
					 _localctx.p =  NF.createFunctionCall(prev, _localctx.actPars.l); 
					}
					}
					break;
				case DOT:
					{
					{
					setState(75);
					match(DOT);
					{
					setState(76);
					_localctx.t = match(IDENT);
					}
					 _localctx.p =  NF.createObjectMember(prev, _localctx.t.getText()); 
					}
					}
					break;
				case POINTER:
					{
					{
					setState(78);
					match(POINTER);
					{
					setState(79);
					_localctx.t = match(IDENT);
					}
					 _localctx.p =  NF.createObjectPointerMember(prev, _localctx.t.getText()); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(85);
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
			  
			setState(87);
			match(LAPR);
			setState(100);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << LAPR) | (1L << ASTERISC) | (1L << PLUS) | (1L << MINUS) | (1L << EXCLAM) | (1L << TILDA) | (1L << IDENT) | (1L << NUMBER) | (1L << FLOATNUMBER) | (1L << CHARCONST))) != 0)) {
				{
				{
				setState(88);
				_localctx.expr = expr();
				 _localctx.l.add(_localctx.expr.p); 
				}
				setState(97);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__2) {
					{
					{
					setState(91);
					match(T__2);
					setState(92);
					_localctx.expr = expr();
					 _localctx.l.add(_localctx.expr.p); 
					}
					}
					setState(99);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(102);
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
			setState(117);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LAPR:
			case IDENT:
			case NUMBER:
			case FLOATNUMBER:
			case CHARCONST:
				{
				setState(104);
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
				setState(107);
				_localctx.unaryOP = unaryOP();
				setState(108);
				_localctx.castExpr = castExpr();
				 _localctx.p =  NF.createUnaryOpNode(_localctx.castExpr.p, _localctx.unaryOP.kind); 
				}
				break;
			case T__3:
				{
				setState(111);
				match(T__3);
				setState(112);
				match(LAPR);
				setState(113);
				_localctx.dType = dType();
				setState(114);
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
			setState(119);
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
		public DTypeContext dType() {
			return getRuleContext(DTypeContext.class,0);
		}
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
			  DebugExpressionPair prev;
			  
			setState(137);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				{
				 if(IsCast()) 
				{
				setState(124);
				match(LAPR);
				setState(134);
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
					setState(125);
					_localctx.dType = dType();
					 typeP = _localctx.dType.ty; 
					}
					}
					break;
				case TYPEOF:
					{
					{
					setState(128);
					match(TYPEOF);
					setState(129);
					match(LAPR);
					{
					{
					setState(130);
					_localctx.t = match(IDENT);
					}
					 typeNode = NF.createTypeofNode(_localctx.t.getText());
					}
					setState(133);
					match(RAPR);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(136);
				match(RAPR);
				}
				}
				break;
			}
			{
			setState(139);
			_localctx.unaryExpr = unaryExpr();
			prev = _localctx.unaryExpr.p; _localctx.p =  prev;
			 if (typeP != null) { _localctx.p =  NF.createCastIfNecessary(prev, typeP);}
			                                                      if (typeNode != null) { _localctx.p =  NF.createPointerCastNode(prev, typeNode);}
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(144);
			_localctx.castExpr = castExpr();
			 prev = _localctx.castExpr.p; _localctx.p =  prev;
			}
			setState(161);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ASTERISC) | (1L << DIVIDE) | (1L << MODULAR))) != 0)) {
				{
				setState(159);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ASTERISC:
					{
					{
					setState(147);
					match(ASTERISC);
					setState(148);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.MUL, prev, _localctx.castExpr.p); 
					}
					}
					break;
				case DIVIDE:
					{
					{
					setState(151);
					match(DIVIDE);
					setState(152);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createDivNode(prev, _localctx.castExpr.p); 
					}
					}
					break;
				case MODULAR:
					{
					{
					setState(155);
					match(MODULAR);
					setState(156);
					_localctx.castExpr = castExpr();
					 _localctx.p =  NF.createDivNode(prev, _localctx.castExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(163);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(165);
			_localctx.multExpr = multExpr();
			 prev = _localctx.multExpr.p; _localctx.p =  prev;
			}
			setState(178);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==PLUS || _la==MINUS) {
				{
				setState(176);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case PLUS:
					{
					{
					setState(168);
					match(PLUS);
					setState(169);
					_localctx.multExpr = multExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.ADD, prev, _localctx.multExpr.p);
					}
					}
					break;
				case MINUS:
					{
					{
					setState(172);
					match(MINUS);
					setState(173);
					_localctx.multExpr = multExpr();
					 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.SUB, prev, _localctx.multExpr.p);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(180);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(182);
			_localctx.addExpr = addExpr();
			 prev = _localctx.addExpr.p; _localctx.p =  prev;
			}
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==SHIFTR || _la==SHIFTL) {
				{
				setState(193);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case SHIFTR:
					{
					{
					setState(185);
					match(SHIFTR);
					setState(186);
					_localctx.addExpr = addExpr();
					 _localctx.p =  NF.createShiftLeft(prev, _localctx.addExpr.p); 
					}
					}
					break;
				case SHIFTL:
					{
					{
					setState(189);
					match(SHIFTL);
					setState(190);
					_localctx.addExpr = addExpr();
					 _localctx.p =  NF.createShiftRight(prev, _localctx.addExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(197);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(199);
			_localctx.shiftExpr = shiftExpr();
			 prev = _localctx.shiftExpr.p; _localctx.p =  prev;
			}
			setState(220);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << GT) | (1L << LT) | (1L << GTE) | (1L << LTE))) != 0)) {
				{
				setState(218);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LT:
					{
					{
					setState(202);
					match(LT);
					setState(203);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.LT, _localctx.shiftExpr.p); 
					}
					}
					break;
				case GT:
					{
					{
					setState(206);
					match(GT);
					setState(207);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.GT, _localctx.shiftExpr.p); 
					}
					}
					break;
				case LTE:
					{
					{
					setState(210);
					match(LTE);
					setState(211);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.LE, _localctx.shiftExpr.p); 
					}
					}
					break;
				case GTE:
					{
					{
					setState(214);
					match(GTE);
					setState(215);
					_localctx.shiftExpr = shiftExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.GE, _localctx.shiftExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(222);
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			{
			setState(224);
			_localctx.relExpr = relExpr();
			 prev = _localctx.relExpr.p; _localctx.p =  prev;
			}
			setState(237);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NE) {
				{
				setState(235);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case EQ:
					{
					{
					setState(227);
					match(EQ);
					setState(228);
					_localctx.relExpr = relExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.EQ, _localctx.relExpr.p); 
					}
					}
					break;
				case NE:
					{
					{
					setState(231);
					match(NE);
					setState(232);
					_localctx.relExpr = relExpr();
					 _localctx.p =  NF.createCompareNode(prev, CompareKind.NE, _localctx.relExpr.p); 
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(239);
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			{
			setState(241);
			_localctx.eqExpr = eqExpr();
			 prev = _localctx.eqExpr.p; _localctx.p =  prev;
			}
			setState(250);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(244);
				match(AND);
				setState(245);
				_localctx.eqExpr = eqExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.AND, prev, _localctx.eqExpr.p); 
				}
				}
				setState(252);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(254);
			_localctx.andExpr = andExpr();
			 prev = _localctx.andExpr.p; _localctx.p =  prev;
			}
			setState(263);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==XOR) {
				{
				{
				setState(257);
				match(XOR);
				setState(258);
				_localctx.andExpr = andExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.XOR, prev, _localctx.andExpr.p); 
				}
				}
				setState(265);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(267);
			_localctx.xorExpr = xorExpr();
			 prev = _localctx.xorExpr.p; _localctx.p =  prev;
			}
			setState(276);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(270);
				match(OR);
				setState(271);
				_localctx.xorExpr = xorExpr();
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.OR, prev, _localctx.xorExpr.p); 
				}
				}
				setState(278);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(280);
			_localctx.orExpr = orExpr();
			 prev = _localctx.orExpr.p; _localctx.p =  prev;
			}
			setState(289);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LOGICAND) {
				{
				{
				setState(283);
				match(LOGICAND);
				setState(284);
				_localctx.orExpr = orExpr();
				 _localctx.p =  NF.createLogicalAndNode(prev, _localctx.orExpr.p); 
				}
				}
				setState(291);
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

			  DebugExpressionPair prev = null;
			  
			{
			setState(293);
			_localctx.logAndExpr = logAndExpr();
			 prev = _localctx.logAndExpr.p; _localctx.p =  prev;
			}
			setState(302);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LOGICOR) {
				{
				{
				setState(296);
				match(LOGICOR);
				setState(297);
				_localctx.logAndExpr = logAndExpr();
				 _localctx.p =  NF.createLogicalOrNode(prev, _localctx.logAndExpr.p); 
				}
				}
				setState(304);
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
			  DebugExpressionPair prev = null;
			  
			{
			setState(306);
			_localctx.logOrExpr = logOrExpr();
			 prev = _localctx.logOrExpr.p; _localctx.p =  prev;
			}
			setState(319);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(309);
				match(T__4);
				{
				setState(310);
				_localctx.expr = expr();
				 pThen = _localctx.expr.p; 
				}
				setState(313);
				match(T__5);
				{
				setState(314);
				_localctx.expr = expr();
				 pElse = _localctx.expr.p; 
				}
				 _localctx.p =  NF.createTernaryNode(prev, pThen, pElse); 
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

			  DebugExprType tempTy = null;
			  
			{
			setState(322);
			_localctx.baseType = baseType();
			 tempTy = _localctx.baseType.ty; 
			}
			setState(329);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ASTERISC) {
				{
				{
				{
				setState(325);
				match(ASTERISC);
				}
				 _localctx.ty =  tempTy.createPointer(); 
				}
				}
				setState(331);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(341);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(332);
				match(T__0);
				setState(336);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NUMBER:
					{
					{
					setState(333);
					_localctx.t = match(NUMBER);
					}
					 _localctx.ty =  tempTy.createArrayType(Integer.parseInt(_localctx.t.getText()));
					}
					break;
				case T__1:
					{
					 _localctx.ty =  tempTy.createArrayType(-1); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(338);
				match(T__1);
				}
				}
				setState(343);
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
			  
			setState(384);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LAPR:
				{
				setState(345);
				match(LAPR);
				setState(346);
				_localctx.dType = dType();
				setState(347);
				match(RAPR);
				 _localctx.ty =  _localctx.dType.ty; 
				}
				break;
			case T__6:
				{
				setState(350);
				match(T__6);
				 _localctx.ty =  DebugExprType.getVoidType(); 
				}
				break;
			case SIGNED:
			case UNSIGNED:
				{
				{
				setState(356);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case SIGNED:
					{
					setState(352);
					match(SIGNED);
					 signed = true; 
					}
					break;
				case UNSIGNED:
					{
					setState(354);
					match(UNSIGNED);
					 signed = false; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(366);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case CHAR:
					{
					setState(358);
					match(CHAR);
					 _localctx.ty =  DebugExprType.getIntType(8, signed); 
					}
					break;
				case SHORT:
					{
					setState(360);
					match(SHORT);
					 _localctx.ty =  DebugExprType.getIntType(16, signed); 
					}
					break;
				case INT:
					{
					setState(362);
					match(INT);
					 _localctx.ty =  DebugExprType.getIntType(32, signed); 
					}
					break;
				case T__7:
					{
					setState(364);
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
				setState(368);
				match(CHAR);
				 _localctx.ty =  DebugExprType.getIntType(8, false); 
				}
				break;
			case SHORT:
				{
				setState(370);
				match(SHORT);
				 _localctx.ty =  DebugExprType.getIntType(16, true); 
				}
				break;
			case INT:
				{
				setState(372);
				match(INT);
				 _localctx.ty =  DebugExprType.getIntType(32, true); 
				}
				break;
			case T__7:
				{
				{
				setState(374);
				match(T__7);
				 _localctx.ty =  DebugExprType.getIntType(64, true); 
				setState(377);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DOUBLE) {
					{
					setState(376);
					match(DOUBLE);
					}
				}

				 _localctx.ty =  DebugExprType.getFloatType(128); 
				}
				}
				break;
			case FLOAT:
				{
				setState(380);
				match(FLOAT);
				 _localctx.ty =  DebugExprType.getFloatType(32); 
				}
				break;
			case DOUBLE:
				{
				setState(382);
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

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\60\u0185\4\2\t\2"+
		"\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3@\n\3\3\4\3\4\3\4\3\4"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\7\4T\n\4\f\4"+
		"\16\4W\13\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\7\5b\n\5\f\5\16\5e\13"+
		"\5\5\5g\n\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3"+
		"\6\5\6x\n\6\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3"+
		"\b\5\b\u0089\n\b\3\b\5\b\u008c\n\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t"+
		"\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\7\t\u00a2\n\t\f\t\16\t\u00a5"+
		"\13\t\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\7\n\u00b3\n\n\f"+
		"\n\16\n\u00b6\13\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3"+
		"\13\3\13\7\13\u00c4\n\13\f\13\16\13\u00c7\13\13\3\f\3\f\3\f\3\f\3\f\3"+
		"\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\7\f\u00dd\n"+
		"\f\f\f\16\f\u00e0\13\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
		"\7\r\u00ee\n\r\f\r\16\r\u00f1\13\r\3\16\3\16\3\16\3\16\3\16\3\16\3\16"+
		"\3\16\7\16\u00fb\n\16\f\16\16\16\u00fe\13\16\3\17\3\17\3\17\3\17\3\17"+
		"\3\17\3\17\3\17\7\17\u0108\n\17\f\17\16\17\u010b\13\17\3\20\3\20\3\20"+
		"\3\20\3\20\3\20\3\20\3\20\7\20\u0115\n\20\f\20\16\20\u0118\13\20\3\21"+
		"\3\21\3\21\3\21\3\21\3\21\3\21\3\21\7\21\u0122\n\21\f\21\16\21\u0125\13"+
		"\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\7\22\u012f\n\22\f\22\16\22"+
		"\u0132\13\22\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3"+
		"\23\3\23\3\23\5\23\u0142\n\23\3\24\3\24\3\24\3\24\3\24\3\24\7\24\u014a"+
		"\n\24\f\24\16\24\u014d\13\24\3\24\3\24\3\24\3\24\5\24\u0153\n\24\3\24"+
		"\7\24\u0156\n\24\f\24\16\24\u0159\13\24\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\5\25\u0167\n\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\5\25\u0171\n\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\5\25\u017c\n\25\3\25\3\25\3\25\3\25\3\25\5\25\u0183\n\25\3\25\2"+
		"\2\26\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(\2\3\4\2\r\17\25\26"+
		"\2\u01a2\2*\3\2\2\2\4\61\3\2\2\2\6A\3\2\2\2\bX\3\2\2\2\nw\3\2\2\2\fy\3"+
		"\2\2\2\16|\3\2\2\2\20\u0091\3\2\2\2\22\u00a6\3\2\2\2\24\u00b7\3\2\2\2"+
		"\26\u00c8\3\2\2\2\30\u00e1\3\2\2\2\32\u00f2\3\2\2\2\34\u00ff\3\2\2\2\36"+
		"\u010c\3\2\2\2 \u0119\3\2\2\2\"\u0126\3\2\2\2$\u0133\3\2\2\2&\u0143\3"+
		"\2\2\2(\u015a\3\2\2\2*+\b\2\1\2+,\5$\23\2,-\b\2\1\2-.\3\2\2\2./\7\2\2"+
		"\3/\60\b\2\1\2\60\3\3\2\2\2\61?\b\3\1\2\62\63\7,\2\2\63@\b\3\1\2\64\65"+
		"\7-\2\2\65@\b\3\1\2\66\67\7.\2\2\67@\b\3\1\289\7/\2\29@\b\3\1\2:;\7\13"+
		"\2\2;<\5$\23\2<=\7\f\2\2=>\b\3\1\2>@\3\2\2\2?\62\3\2\2\2?\64\3\2\2\2?"+
		"\66\3\2\2\2?8\3\2\2\2?:\3\2\2\2@\5\3\2\2\2AB\b\4\1\2BC\5\4\3\2CD\b\4\1"+
		"\2DU\3\2\2\2EF\7\3\2\2FG\5$\23\2GH\7\4\2\2HI\b\4\1\2IT\3\2\2\2JK\5\b\5"+
		"\2KL\b\4\1\2LT\3\2\2\2MN\7\23\2\2NO\7,\2\2OT\b\4\1\2PQ\7\24\2\2QR\7,\2"+
		"\2RT\b\4\1\2SE\3\2\2\2SJ\3\2\2\2SM\3\2\2\2SP\3\2\2\2TW\3\2\2\2US\3\2\2"+
		"\2UV\3\2\2\2V\7\3\2\2\2WU\3\2\2\2XY\b\5\1\2Yf\7\13\2\2Z[\5$\23\2[\\\b"+
		"\5\1\2\\c\3\2\2\2]^\7\5\2\2^_\5$\23\2_`\b\5\1\2`b\3\2\2\2a]\3\2\2\2be"+
		"\3\2\2\2ca\3\2\2\2cd\3\2\2\2dg\3\2\2\2ec\3\2\2\2fZ\3\2\2\2fg\3\2\2\2g"+
		"h\3\2\2\2hi\7\f\2\2i\t\3\2\2\2jk\5\6\4\2kl\b\6\1\2lx\3\2\2\2mn\5\f\7\2"+
		"no\5\16\b\2op\b\6\1\2px\3\2\2\2qr\7\6\2\2rs\7\13\2\2st\5&\24\2tu\7\f\2"+
		"\2uv\b\6\1\2vx\3\2\2\2wj\3\2\2\2wm\3\2\2\2wq\3\2\2\2x\13\3\2\2\2yz\t\2"+
		"\2\2z{\b\7\1\2{\r\3\2\2\2|\u008b\b\b\1\2}~\b\b\1\2~\u0088\7\13\2\2\177"+
		"\u0080\5&\24\2\u0080\u0081\b\b\1\2\u0081\u0089\3\2\2\2\u0082\u0083\7+"+
		"\2\2\u0083\u0084\7\13\2\2\u0084\u0085\7,\2\2\u0085\u0086\b\b\1\2\u0086"+
		"\u0087\3\2\2\2\u0087\u0089\7\f\2\2\u0088\177\3\2\2\2\u0088\u0082\3\2\2"+
		"\2\u0089\u008a\3\2\2\2\u008a\u008c\7\f\2\2\u008b}\3\2\2\2\u008b\u008c"+
		"\3\2\2\2\u008c\u008d\3\2\2\2\u008d\u008e\5\n\6\2\u008e\u008f\b\b\1\2\u008f"+
		"\u0090\b\b\1\2\u0090\17\3\2\2\2\u0091\u0092\b\t\1\2\u0092\u0093\5\16\b"+
		"\2\u0093\u0094\b\t\1\2\u0094\u00a3\3\2\2\2\u0095\u0096\7\r\2\2\u0096\u0097"+
		"\5\16\b\2\u0097\u0098\b\t\1\2\u0098\u00a2\3\2\2\2\u0099\u009a\7\20\2\2"+
		"\u009a\u009b\5\16\b\2\u009b\u009c\b\t\1\2\u009c\u00a2\3\2\2\2\u009d\u009e"+
		"\7\27\2\2\u009e\u009f\5\16\b\2\u009f\u00a0\b\t\1\2\u00a0\u00a2\3\2\2\2"+
		"\u00a1\u0095\3\2\2\2\u00a1\u0099\3\2\2\2\u00a1\u009d\3\2\2\2\u00a2\u00a5"+
		"\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a4\21\3\2\2\2\u00a5"+
		"\u00a3\3\2\2\2\u00a6\u00a7\b\n\1\2\u00a7\u00a8\5\20\t\2\u00a8\u00a9\b"+
		"\n\1\2\u00a9\u00b4\3\2\2\2\u00aa\u00ab\7\16\2\2\u00ab\u00ac\5\20\t\2\u00ac"+
		"\u00ad\b\n\1\2\u00ad\u00b3\3\2\2\2\u00ae\u00af\7\17\2\2\u00af\u00b0\5"+
		"\20\t\2\u00b0\u00b1\b\n\1\2\u00b1\u00b3\3\2\2\2\u00b2\u00aa\3\2\2\2\u00b2"+
		"\u00ae\3\2\2\2\u00b3\u00b6\3\2\2\2\u00b4\u00b2\3\2\2\2\u00b4\u00b5\3\2"+
		"\2\2\u00b5\23\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b7\u00b8\b\13\1\2\u00b8\u00b9"+
		"\5\22\n\2\u00b9\u00ba\b\13\1\2\u00ba\u00c5\3\2\2\2\u00bb\u00bc\7\30\2"+
		"\2\u00bc\u00bd\5\22\n\2\u00bd\u00be\b\13\1\2\u00be\u00c4\3\2\2\2\u00bf"+
		"\u00c0\7\31\2\2\u00c0\u00c1\5\22\n\2\u00c1\u00c2\b\13\1\2\u00c2\u00c4"+
		"\3\2\2\2\u00c3\u00bb\3\2\2\2\u00c3\u00bf\3\2\2\2\u00c4\u00c7\3\2\2\2\u00c5"+
		"\u00c3\3\2\2\2\u00c5\u00c6\3\2\2\2\u00c6\25\3\2\2\2\u00c7\u00c5\3\2\2"+
		"\2\u00c8\u00c9\b\f\1\2\u00c9\u00ca\5\24\13\2\u00ca\u00cb\b\f\1\2\u00cb"+
		"\u00de\3\2\2\2\u00cc\u00cd\7\33\2\2\u00cd\u00ce\5\24\13\2\u00ce\u00cf"+
		"\b\f\1\2\u00cf\u00dd\3\2\2\2\u00d0\u00d1\7\32\2\2\u00d1\u00d2\5\24\13"+
		"\2\u00d2\u00d3\b\f\1\2\u00d3\u00dd\3\2\2\2\u00d4\u00d5\7\35\2\2\u00d5"+
		"\u00d6\5\24\13\2\u00d6\u00d7\b\f\1\2\u00d7\u00dd\3\2\2\2\u00d8\u00d9\7"+
		"\34\2\2\u00d9\u00da\5\24\13\2\u00da\u00db\b\f\1\2\u00db\u00dd\3\2\2\2"+
		"\u00dc\u00cc\3\2\2\2\u00dc\u00d0\3\2\2\2\u00dc\u00d4\3\2\2\2\u00dc\u00d8"+
		"\3\2\2\2\u00dd\u00e0\3\2\2\2\u00de\u00dc\3\2\2\2\u00de\u00df\3\2\2\2\u00df"+
		"\27\3\2\2\2\u00e0\u00de\3\2\2\2\u00e1\u00e2\b\r\1\2\u00e2\u00e3\5\26\f"+
		"\2\u00e3\u00e4\b\r\1\2\u00e4\u00ef\3\2\2\2\u00e5\u00e6\7\36\2\2\u00e6"+
		"\u00e7\5\26\f\2\u00e7\u00e8\b\r\1\2\u00e8\u00ee\3\2\2\2\u00e9\u00ea\7"+
		"\37\2\2\u00ea\u00eb\5\26\f\2\u00eb\u00ec\b\r\1\2\u00ec\u00ee\3\2\2\2\u00ed"+
		"\u00e5\3\2\2\2\u00ed\u00e9\3\2\2\2\u00ee\u00f1\3\2\2\2\u00ef\u00ed\3\2"+
		"\2\2\u00ef\u00f0\3\2\2\2\u00f0\31\3\2\2\2\u00f1\u00ef\3\2\2\2\u00f2\u00f3"+
		"\b\16\1\2\u00f3\u00f4\5\30\r\2\u00f4\u00f5\b\16\1\2\u00f5\u00fc\3\2\2"+
		"\2\u00f6\u00f7\7 \2\2\u00f7\u00f8\5\30\r\2\u00f8\u00f9\b\16\1\2\u00f9"+
		"\u00fb\3\2\2\2\u00fa\u00f6\3\2\2\2\u00fb\u00fe\3\2\2\2\u00fc\u00fa\3\2"+
		"\2\2\u00fc\u00fd\3\2\2\2\u00fd\33\3\2\2\2\u00fe\u00fc\3\2\2\2\u00ff\u0100"+
		"\b\17\1\2\u0100\u0101\5\32\16\2\u0101\u0102\b\17\1\2\u0102\u0109\3\2\2"+
		"\2\u0103\u0104\7\"\2\2\u0104\u0105\5\32\16\2\u0105\u0106\b\17\1\2\u0106"+
		"\u0108\3\2\2\2\u0107\u0103\3\2\2\2\u0108\u010b\3\2\2\2\u0109\u0107\3\2"+
		"\2\2\u0109\u010a\3\2\2\2\u010a\35\3\2\2\2\u010b\u0109\3\2\2\2\u010c\u010d"+
		"\b\20\1\2\u010d\u010e\5\34\17\2\u010e\u010f\b\20\1\2\u010f\u0116\3\2\2"+
		"\2\u0110\u0111\7!\2\2\u0111\u0112\5\34\17\2\u0112\u0113\b\20\1\2\u0113"+
		"\u0115\3\2\2\2\u0114\u0110\3\2\2\2\u0115\u0118\3\2\2\2\u0116\u0114\3\2"+
		"\2\2\u0116\u0117\3\2\2\2\u0117\37\3\2\2\2\u0118\u0116\3\2\2\2\u0119\u011a"+
		"\b\21\1\2\u011a\u011b\5\36\20\2\u011b\u011c\b\21\1\2\u011c\u0123\3\2\2"+
		"\2\u011d\u011e\7\22\2\2\u011e\u011f\5\36\20\2\u011f\u0120\b\21\1\2\u0120"+
		"\u0122\3\2\2\2\u0121\u011d\3\2\2\2\u0122\u0125\3\2\2\2\u0123\u0121\3\2"+
		"\2\2\u0123\u0124\3\2\2\2\u0124!\3\2\2\2\u0125\u0123\3\2\2\2\u0126\u0127"+
		"\b\22\1\2\u0127\u0128\5 \21\2\u0128\u0129\b\22\1\2\u0129\u0130\3\2\2\2"+
		"\u012a\u012b\7\21\2\2\u012b\u012c\5 \21\2\u012c\u012d\b\22\1\2\u012d\u012f"+
		"\3\2\2\2\u012e\u012a\3\2\2\2\u012f\u0132\3\2\2\2\u0130\u012e\3\2\2\2\u0130"+
		"\u0131\3\2\2\2\u0131#\3\2\2\2\u0132\u0130\3\2\2\2\u0133\u0134\b\23\1\2"+
		"\u0134\u0135\5\"\22\2\u0135\u0136\b\23\1\2\u0136\u0141\3\2\2\2\u0137\u0138"+
		"\7\7\2\2\u0138\u0139\5$\23\2\u0139\u013a\b\23\1\2\u013a\u013b\3\2\2\2"+
		"\u013b\u013c\7\b\2\2\u013c\u013d\5$\23\2\u013d\u013e\b\23\1\2\u013e\u013f"+
		"\3\2\2\2\u013f\u0140\b\23\1\2\u0140\u0142\3\2\2\2\u0141\u0137\3\2\2\2"+
		"\u0141\u0142\3\2\2\2\u0142%\3\2\2\2\u0143\u0144\b\24\1\2\u0144\u0145\5"+
		"(\25\2\u0145\u0146\b\24\1\2\u0146\u014b\3\2\2\2\u0147\u0148\7\r\2\2\u0148"+
		"\u014a\b\24\1\2\u0149\u0147\3\2\2\2\u014a\u014d\3\2\2\2\u014b\u0149\3"+
		"\2\2\2\u014b\u014c\3\2\2\2\u014c\u0157\3\2\2\2\u014d\u014b\3\2\2\2\u014e"+
		"\u0152\7\3\2\2\u014f\u0150\7-\2\2\u0150\u0153\b\24\1\2\u0151\u0153\b\24"+
		"\1\2\u0152\u014f\3\2\2\2\u0152\u0151\3\2\2\2\u0153\u0154\3\2\2\2\u0154"+
		"\u0156\7\4\2\2\u0155\u014e\3\2\2\2\u0156\u0159\3\2\2\2\u0157\u0155\3\2"+
		"\2\2\u0157\u0158\3\2\2\2\u0158\'\3\2\2\2\u0159\u0157\3\2\2\2\u015a\u0182"+
		"\b\25\1\2\u015b\u015c\7\13\2\2\u015c\u015d\5&\24\2\u015d\u015e\7\f\2\2"+
		"\u015e\u015f\b\25\1\2\u015f\u0183\3\2\2\2\u0160\u0161\7\t\2\2\u0161\u0183"+
		"\b\25\1\2\u0162\u0163\7#\2\2\u0163\u0167\b\25\1\2\u0164\u0165\7$\2\2\u0165"+
		"\u0167\b\25\1\2\u0166\u0162\3\2\2\2\u0166\u0164\3\2\2\2\u0167\u0170\3"+
		"\2\2\2\u0168\u0169\7*\2\2\u0169\u0171\b\25\1\2\u016a\u016b\7\'\2\2\u016b"+
		"\u0171\b\25\1\2\u016c\u016d\7%\2\2\u016d\u0171\b\25\1\2\u016e\u016f\7"+
		"\n\2\2\u016f\u0171\b\25\1\2\u0170\u0168\3\2\2\2\u0170\u016a\3\2\2\2\u0170"+
		"\u016c\3\2\2\2\u0170\u016e\3\2\2\2\u0170\u0171\3\2\2\2\u0171\u0183\3\2"+
		"\2\2\u0172\u0173\7*\2\2\u0173\u0183\b\25\1\2\u0174\u0175\7\'\2\2\u0175"+
		"\u0183\b\25\1\2\u0176\u0177\7%\2\2\u0177\u0183\b\25\1\2\u0178\u0179\7"+
		"\n\2\2\u0179\u017b\b\25\1\2\u017a\u017c\7)\2\2\u017b\u017a\3\2\2\2\u017b"+
		"\u017c\3\2\2\2\u017c\u017d\3\2\2\2\u017d\u0183\b\25\1\2\u017e\u017f\7"+
		"(\2\2\u017f\u0183\b\25\1\2\u0180\u0181\7)\2\2\u0181\u0183\b\25\1\2\u0182"+
		"\u015b\3\2\2\2\u0182\u0160\3\2\2\2\u0182\u0166\3\2\2\2\u0182\u0172\3\2"+
		"\2\2\u0182\u0174\3\2\2\2\u0182\u0176\3\2\2\2\u0182\u0178\3\2\2\2\u0182"+
		"\u017e\3\2\2\2\u0182\u0180\3\2\2\2\u0183)\3\2\2\2!?SUcfw\u0088\u008b\u00a1"+
		"\u00a3\u00b2\u00b4\u00c3\u00c5\u00dc\u00de\u00ed\u00ef\u00fc\u0109\u0116"+
		"\u0123\u0130\u0141\u014b\u0152\u0157\u0166\u0170\u017b\u0182";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
