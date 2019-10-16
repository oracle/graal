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
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, DIGIT=24, 
		CR=25, LF=26, SINGLECOMMA=27, QUOTE=28, IDENT=29, NUMBER=30, FLOATNUMBER=31, 
		CHARCONST=32, LAPR=33, ASTERISC=34, SIGNED=35, UNSIGNED=36, INT=37, LONG=38, 
		SHORT=39, FLOAT=40, DOUBLE=41, CHAR=42, TYPEOF=43, WS=44;
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
		null, "')'", "'['", "']'", "'.'", "'->'", "','", "'sizeof'", "'+'", "'-'", 
		"'~'", "'!'", "'/'", "'%'", "'>>'", "'<<'", "'<'", "'>'", "'<='", "'>='", 
		"'=='", "'!='", "'?'", "':'", null, "'\r'", "'\n'", "'''", "'\"'", null, 
		null, null, null, "'('", "'*'", "'signed'", "'unsigned'", "'int'", "'LONG'", 
		"'short'", "'float'", "'double'", "'char'", "'typeof'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		"DIGIT", "CR", "LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", 
		"CHARCONST", "LAPR", "ASTERISC", "SIGNED", "UNSIGNED", "INT", "LONG", 
		"SHORT", "FLOAT", "DOUBLE", "CHAR", "TYPEOF", "WS"
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
			if(_syntaxErrors == 0) astRoot = p.getNode();
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
			setState(59);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
				{
				setState(46);
				_localctx.t = match(IDENT);
				 _localctx.p =  NF.createVarNode(_localctx.t.getText()); 
				}
				break;
			case NUMBER:
				{
				setState(48);
				_localctx.t = match(NUMBER);
				 _localctx.p =  NF.createIntegerConstant(Integer.parseInt(_localctx.t.getText())); 
				}
				break;
			case FLOATNUMBER:
				{
				setState(50);
				_localctx.t = match(FLOATNUMBER);
				 _localctx.p =  NF.createFloatConstant(Float.parseFloat(_localctx.t.getText())); 
				}
				break;
			case CHARCONST:
				{
				setState(52);
				_localctx.t = match(CHARCONST);
				 _localctx.p =  NF.createCharacterConstant(_localctx.t.getText()); 
				}
				break;
			case LAPR:
				{
				setState(54);
				match(LAPR);
				setState(55);
				_localctx.expr = expr();
				setState(56);
				match(T__0);
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
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public PrimExprContext primExpr() {
			return getRuleContext(PrimExprContext.class,0);
		}
		public ActParsContext actPars() {
			return getRuleContext(ActParsContext.class,0);
		}
		public TerminalNode IDENT() { return getToken(DebugExpressionParser.IDENT, 0); }
		public DesignatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_designator; }
	}

	public final DesignatorContext designator() throws RecognitionException {
		DesignatorContext _localctx = new DesignatorContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_designator);
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExpressionPair idxPair = null;
			  List <DebugExpressionPair> list;
			  DebugExpressionPair prev = null;
			  
			setState(65);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				{
				setState(62);
				_localctx.primExpr = primExpr();
				 prev = _localctx.primExpr.p; 
				}
				break;
			}
			setState(84);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
				{
				setState(67);
				match(T__1);
				setState(68);
				_localctx.expr = expr();
				 idxPair = _localctx.expr.p; 
				setState(70);
				match(T__2);
				 _localctx.p =  NF.createArrayElement(prev, idxPair); 
				}
				break;
			case LAPR:
				{
				{
				setState(73);
				_localctx.actPars = actPars();
				 list = _localctx.actPars.l; 
				}
				 _localctx.p =  NF.createFunctionCall(prev, list); 
				}
				break;
			case T__3:
				{
				setState(78);
				match(T__3);
				{
				setState(79);
				_localctx.t = match(IDENT);
				}
				 _localctx.p =  NF.createObjectMember(prev, _localctx.t.getText()); 
				}
				break;
			case T__4:
				{
				setState(81);
				match(T__4);
				{
				setState(82);
				_localctx.t = match(IDENT);
				}
				 _localctx.p =  NF.createObjectPointerMember(prev, _localctx.t.getText()); 
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

	public static class ActParsContext extends ParserRuleContext {
		public List l;
		public ExprContext expr;
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
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair p2 = null;
			  _localctx.l =  new LinkedList<DebugExpressionPair>();
			  
			setState(87);
			match(LAPR);
			{
			setState(88);
			_localctx.expr = expr();
			 p1 = _localctx.expr.p; 
			}
			 _localctx.l.add(p1); 
			setState(92);
			match(T__5);
			{
			setState(93);
			_localctx.expr = expr();
			 p2 = _localctx.expr.p; 
			}
			 _localctx.l.add(p2); 
			setState(97);
			match(T__0);
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

			  DebugExpressionPair prev = null;
			  char kind = '\0';
			  DebugExprType typeP = null;
			  
			setState(120);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case T__3:
			case T__4:
			case IDENT:
			case NUMBER:
			case FLOATNUMBER:
			case CHARCONST:
			case LAPR:
				{
				setState(100);
				_localctx.designator = designator();
				 prev = _localctx.designator.p; 
				 _localctx.p =  prev; 
				}
				break;
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case ASTERISC:
				{
				{
				setState(104);
				_localctx.unaryOP = unaryOP();
				 kind = _localctx.unaryOP.kind; 
				}
				{
				setState(107);
				_localctx.castExpr = castExpr();
				 prev = _localctx.castExpr.p; 
				}
				 _localctx.p =  NF.createUnaryOpNode(prev, kind); 
				}
				break;
			case T__6:
				{
				setState(112);
				match(T__6);
				setState(113);
				match(LAPR);
				{
				setState(114);
				_localctx.dType = dType();
				 typeP = _localctx.dType.ty; 
				}
				setState(117);
				match(T__0);
				 _localctx.p =  NF.createSizeofNode(typeP); 
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
			setState(122);
			_localctx.t = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << ASTERISC))) != 0)) ) {
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
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExprType typeP = null;
			  DebugExprTypeofNode typeNode = null;
			  DebugExpressionPair prev;
			  
			if(IsCast())
			{
			setState(127);
			match(LAPR);
			setState(136);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				{
				setState(128);
				_localctx.dType = dType();
				 typeP = _localctx.dType.ty; 
				}
				break;
			case 2:
				{
				setState(131);
				match(TYPEOF);
				setState(132);
				match(LAPR);
				setState(133);
				_localctx.t = match(IDENT);
				 typeNode = NF.createTypeofNode(_localctx.t.getText()); 
				setState(135);
				match(T__0);
				}
				break;
			}
			setState(139);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(138);
				match(T__0);
				}
			}

			}
			setState(141);
			_localctx.unaryExpr = unaryExpr();
			prev = _localctx.unaryExpr.p;
			 if (typeP != null) { _localctx.p =  NF.createCastIfNecessary(prev, typeP); }
			                                                      if (typeNode != null) { _localctx.p =  NF.createPointerCastNode(prev, typeNode);} 
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			setState(149);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LAPR) {
				{
				setState(146);
				_localctx.castExpr = castExpr();
				 prev = _localctx.castExpr.p; 
				}
			}

			setState(169);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ASTERISC:
				{
				setState(151);
				match(ASTERISC);
				{
				setState(152);
				_localctx.castExpr = castExpr();
				 p1 = _localctx.castExpr.p; 
				}
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.MUL, prev, p1); 
				}
				break;
			case T__11:
				{
				setState(157);
				match(T__11);
				{
				setState(158);
				_localctx.castExpr = castExpr();
				 p1 = _localctx.castExpr.p; 
				}
				 _localctx.p =  NF.createDivNode(prev, p1); 
				}
				break;
			case T__12:
				{
				setState(163);
				match(T__12);
				{
				setState(164);
				_localctx.castExpr = castExpr();
				 p1 = _localctx.castExpr.p; 
				}
				 _localctx.p =  NF.createDivNode(prev, p1); 
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			setState(175);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__11) | (1L << T__12) | (1L << LAPR) | (1L << ASTERISC))) != 0)) {
				{
				setState(172);
				_localctx.multExpr = multExpr();
				 prev = _localctx.multExpr.p; 
				}
			}

			setState(189);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__7:
				{
				setState(177);
				match(T__7);
				{
				setState(178);
				_localctx.multExpr = multExpr();
				 p1 = _localctx.multExpr.p; 
				}
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.ADD, prev, p1); 
				}
				break;
			case T__8:
				{
				setState(183);
				match(T__8);
				{
				setState(184);
				_localctx.multExpr = multExpr();
				 p1 = _localctx.multExpr.p; 
				}
				 _localctx.p =  NF.createArithmeticOp(ArithmeticOperation.SUB, prev, p1); 
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__11) | (1L << T__12) | (1L << LAPR) | (1L << ASTERISC))) != 0)) {
				{
				setState(192);
				_localctx.addExpr = addExpr();
				 prev = _localctx.addExpr.p; 
				}
			}

			setState(209);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__13:
				{
				setState(197);
				match(T__13);
				{
				setState(198);
				_localctx.addExpr = addExpr();
				 p1 = _localctx.addExpr.p; 
				}
				 _localctx.p =  NF.createShiftLeft(prev, p1); 
				}
				break;
			case T__14:
				{
				setState(203);
				match(T__14);
				{
				setState(204);
				_localctx.addExpr = addExpr();
				 p1 = _localctx.addExpr.p; 
				}
				 _localctx.p =  NF.createShiftRight(prev, p1); 
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

			  DebugExpressionPair p1 = null;
			  DebugExpressionPair prev = null;
			  
			setState(215);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << LAPR) | (1L << ASTERISC))) != 0)) {
				{
				setState(212);
				_localctx.shiftExpr = shiftExpr();
				 prev = _localctx.shiftExpr.p; 
				}
			}

			setState(241);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__15:
				{
				setState(217);
				match(T__15);
				{
				setState(218);
				_localctx.shiftExpr = shiftExpr();
				 p1 = _localctx.shiftExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.LT, p1); 
				}
				break;
			case T__16:
				{
				setState(223);
				match(T__16);
				{
				setState(224);
				_localctx.shiftExpr = shiftExpr();
				 p1 = _localctx.shiftExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.GT, p1); 
				}
				break;
			case T__17:
				{
				setState(229);
				match(T__17);
				{
				setState(230);
				_localctx.shiftExpr = shiftExpr();
				 p1 = _localctx.shiftExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.LE, p1); 
				}
				break;
			case T__18:
				{
				setState(235);
				match(T__18);
				{
				setState(236);
				_localctx.shiftExpr = shiftExpr();
				 p1 = _localctx.shiftExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.GE, p1); 
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
			  
			setState(247);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << LAPR) | (1L << ASTERISC))) != 0)) {
				{
				setState(244);
				_localctx.relExpr = relExpr();
				 prev = _localctx.relExpr.p; 
				}
			}

			setState(261);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__19:
				{
				setState(249);
				match(T__19);
				{
				setState(250);
				_localctx.relExpr = relExpr();
				 p1 = _localctx.relExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.EQ, p1); 
				}
				break;
			case T__20:
				{
				setState(255);
				match(T__20);
				{
				setState(256);
				_localctx.relExpr = relExpr();
				 p1 = _localctx.relExpr.p; 
				}
				 _localctx.p =  NF.createCompareNode(prev, CompareKind.NE, p1); 
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

	public static class AndExprContext extends ParserRuleContext {
		public EqExprContext eqExpr() {
			return getRuleContext(EqExprContext.class,0);
		}
		public AndExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_andExpr; }
	}

	public final AndExprContext andExpr() throws RecognitionException {
		AndExprContext _localctx = new AndExprContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_andExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(263);
			eqExpr();
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
		public AndExprContext andExpr() {
			return getRuleContext(AndExprContext.class,0);
		}
		public XorExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_xorExpr; }
	}

	public final XorExprContext xorExpr() throws RecognitionException {
		XorExprContext _localctx = new XorExprContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_xorExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(265);
			andExpr();
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
		public XorExprContext xorExpr() {
			return getRuleContext(XorExprContext.class,0);
		}
		public OrExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orExpr; }
	}

	public final OrExprContext orExpr() throws RecognitionException {
		OrExprContext _localctx = new OrExprContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_orExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(267);
			xorExpr();
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
		public OrExprContext orExpr() {
			return getRuleContext(OrExprContext.class,0);
		}
		public LogAndExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logAndExpr; }
	}

	public final LogAndExprContext logAndExpr() throws RecognitionException {
		LogAndExprContext _localctx = new LogAndExprContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_logAndExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			orExpr();
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
		public LogAndExprContext logAndExpr() {
			return getRuleContext(LogAndExprContext.class,0);
		}
		public LogOrExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logOrExpr; }
	}

	public final LogOrExprContext logOrExpr() throws RecognitionException {
		LogOrExprContext _localctx = new LogOrExprContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_logOrExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(271);
			logAndExpr();
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
			  
			setState(277);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << LAPR) | (1L << ASTERISC))) != 0)) {
				{
				setState(274);
				_localctx.logOrExpr = logOrExpr();
				 prev = _localctx.logOrExpr.p; 
				}
			}

			{
			setState(279);
			match(T__21);
			{
			setState(280);
			_localctx.expr = expr();
			 pThen = _localctx.expr.p; 
			}
			setState(283);
			match(T__22);
			{
			setState(284);
			_localctx.expr = expr();
			 pElse = _localctx.expr.p; 
			}
			 _localctx.p =  NF.createTernaryNode(prev, pThen, pElse); 
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
		public DTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dType; }
	}

	public final DTypeContext dType() throws RecognitionException {
		DTypeContext _localctx = new DTypeContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_dType);
		try {
			enterOuterAlt(_localctx, 1);
			{
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
		public BaseTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_baseType; }
	}

	public final BaseTypeContext baseType() throws RecognitionException {
		BaseTypeContext _localctx = new BaseTypeContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_baseType);
		try {
			enterOuterAlt(_localctx, 1);
			{
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3.\u0128\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3>\n\3\3\4\3\4\3\4\3\4\5\4D\n\4"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\5"+
		"\4W\n\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\6\3\6\3"+
		"\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6"+
		"\3\6\5\6{\n\6\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b"+
		"\5\b\u008b\n\b\3\b\5\b\u008e\n\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\5\t\u0098"+
		"\n\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3"+
		"\t\3\t\5\t\u00ac\n\t\3\n\3\n\3\n\3\n\5\n\u00b2\n\n\3\n\3\n\3\n\3\n\3\n"+
		"\3\n\3\n\3\n\3\n\3\n\3\n\3\n\5\n\u00c0\n\n\3\13\3\13\3\13\3\13\5\13\u00c6"+
		"\n\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\5\13"+
		"\u00d4\n\13\3\f\3\f\3\f\3\f\5\f\u00da\n\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f"+
		"\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\5"+
		"\f\u00f4\n\f\3\r\3\r\3\r\3\r\5\r\u00fa\n\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
		"\3\r\3\r\3\r\3\r\3\r\5\r\u0108\n\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21"+
		"\3\21\3\22\3\22\3\23\3\23\3\23\3\23\5\23\u0118\n\23\3\23\3\23\3\23\3\23"+
		"\3\23\3\23\3\23\3\23\3\23\3\23\3\24\3\24\3\25\3\25\3\25\2\2\26\2\4\6\b"+
		"\n\f\16\20\22\24\26\30\32\34\36 \"$&(\2\3\4\2\n\r$$\2\u012e\2*\3\2\2\2"+
		"\4=\3\2\2\2\6?\3\2\2\2\bX\3\2\2\2\ne\3\2\2\2\f|\3\2\2\2\16\177\3\2\2\2"+
		"\20\u0093\3\2\2\2\22\u00ad\3\2\2\2\24\u00c1\3\2\2\2\26\u00d5\3\2\2\2\30"+
		"\u00f5\3\2\2\2\32\u0109\3\2\2\2\34\u010b\3\2\2\2\36\u010d\3\2\2\2 \u010f"+
		"\3\2\2\2\"\u0111\3\2\2\2$\u0113\3\2\2\2&\u0123\3\2\2\2(\u0125\3\2\2\2"+
		"*+\b\2\1\2+,\5$\23\2,-\b\2\1\2-.\3\2\2\2./\b\2\1\2/\3\3\2\2\2\60\61\7"+
		"\37\2\2\61>\b\3\1\2\62\63\7 \2\2\63>\b\3\1\2\64\65\7!\2\2\65>\b\3\1\2"+
		"\66\67\7\"\2\2\67>\b\3\1\289\7#\2\29:\5$\23\2:;\7\3\2\2;<\b\3\1\2<>\3"+
		"\2\2\2=\60\3\2\2\2=\62\3\2\2\2=\64\3\2\2\2=\66\3\2\2\2=8\3\2\2\2>\5\3"+
		"\2\2\2?C\b\4\1\2@A\5\4\3\2AB\b\4\1\2BD\3\2\2\2C@\3\2\2\2CD\3\2\2\2DV\3"+
		"\2\2\2EF\7\4\2\2FG\5$\23\2GH\b\4\1\2HI\7\5\2\2IJ\b\4\1\2JW\3\2\2\2KL\5"+
		"\b\5\2LM\b\4\1\2MN\3\2\2\2NO\b\4\1\2OW\3\2\2\2PQ\7\6\2\2QR\7\37\2\2RW"+
		"\b\4\1\2ST\7\7\2\2TU\7\37\2\2UW\b\4\1\2VE\3\2\2\2VK\3\2\2\2VP\3\2\2\2"+
		"VS\3\2\2\2W\7\3\2\2\2XY\b\5\1\2YZ\7#\2\2Z[\5$\23\2[\\\b\5\1\2\\]\3\2\2"+
		"\2]^\b\5\1\2^_\7\b\2\2_`\5$\23\2`a\b\5\1\2ab\3\2\2\2bc\b\5\1\2cd\7\3\2"+
		"\2d\t\3\2\2\2ez\b\6\1\2fg\5\6\4\2gh\b\6\1\2hi\b\6\1\2i{\3\2\2\2jk\5\f"+
		"\7\2kl\b\6\1\2lm\3\2\2\2mn\5\16\b\2no\b\6\1\2op\3\2\2\2pq\b\6\1\2q{\3"+
		"\2\2\2rs\7\t\2\2st\7#\2\2tu\5&\24\2uv\b\6\1\2vw\3\2\2\2wx\7\3\2\2xy\b"+
		"\6\1\2y{\3\2\2\2zf\3\2\2\2zj\3\2\2\2zr\3\2\2\2{\13\3\2\2\2|}\t\2\2\2}"+
		"~\b\7\1\2~\r\3\2\2\2\177\u0080\b\b\1\2\u0080\u0081\b\b\1\2\u0081\u008a"+
		"\7#\2\2\u0082\u0083\5&\24\2\u0083\u0084\b\b\1\2\u0084\u008b\3\2\2\2\u0085"+
		"\u0086\7-\2\2\u0086\u0087\7#\2\2\u0087\u0088\7\37\2\2\u0088\u0089\b\b"+
		"\1\2\u0089\u008b\7\3\2\2\u008a\u0082\3\2\2\2\u008a\u0085\3\2\2\2\u008a"+
		"\u008b\3\2\2\2\u008b\u008d\3\2\2\2\u008c\u008e\7\3\2\2\u008d\u008c\3\2"+
		"\2\2\u008d\u008e\3\2\2\2\u008e\u008f\3\2\2\2\u008f\u0090\5\n\6\2\u0090"+
		"\u0091\b\b\1\2\u0091\u0092\b\b\1\2\u0092\17\3\2\2\2\u0093\u0097\b\t\1"+
		"\2\u0094\u0095\5\16\b\2\u0095\u0096\b\t\1\2\u0096\u0098\3\2\2\2\u0097"+
		"\u0094\3\2\2\2\u0097\u0098\3\2\2\2\u0098\u00ab\3\2\2\2\u0099\u009a\7$"+
		"\2\2\u009a\u009b\5\16\b\2\u009b\u009c\b\t\1\2\u009c\u009d\3\2\2\2\u009d"+
		"\u009e\b\t\1\2\u009e\u00ac\3\2\2\2\u009f\u00a0\7\16\2\2\u00a0\u00a1\5"+
		"\16\b\2\u00a1\u00a2\b\t\1\2\u00a2\u00a3\3\2\2\2\u00a3\u00a4\b\t\1\2\u00a4"+
		"\u00ac\3\2\2\2\u00a5\u00a6\7\17\2\2\u00a6\u00a7\5\16\b\2\u00a7\u00a8\b"+
		"\t\1\2\u00a8\u00a9\3\2\2\2\u00a9\u00aa\b\t\1\2\u00aa\u00ac\3\2\2\2\u00ab"+
		"\u0099\3\2\2\2\u00ab\u009f\3\2\2\2\u00ab\u00a5\3\2\2\2\u00ac\21\3\2\2"+
		"\2\u00ad\u00b1\b\n\1\2\u00ae\u00af\5\20\t\2\u00af\u00b0\b\n\1\2\u00b0"+
		"\u00b2\3\2\2\2\u00b1\u00ae\3\2\2\2\u00b1\u00b2\3\2\2\2\u00b2\u00bf\3\2"+
		"\2\2\u00b3\u00b4\7\n\2\2\u00b4\u00b5\5\20\t\2\u00b5\u00b6\b\n\1\2\u00b6"+
		"\u00b7\3\2\2\2\u00b7\u00b8\b\n\1\2\u00b8\u00c0\3\2\2\2\u00b9\u00ba\7\13"+
		"\2\2\u00ba\u00bb\5\20\t\2\u00bb\u00bc\b\n\1\2\u00bc\u00bd\3\2\2\2\u00bd"+
		"\u00be\b\n\1\2\u00be\u00c0\3\2\2\2\u00bf\u00b3\3\2\2\2\u00bf\u00b9\3\2"+
		"\2\2\u00c0\23\3\2\2\2\u00c1\u00c5\b\13\1\2\u00c2\u00c3\5\22\n\2\u00c3"+
		"\u00c4\b\13\1\2\u00c4\u00c6\3\2\2\2\u00c5\u00c2\3\2\2\2\u00c5\u00c6\3"+
		"\2\2\2\u00c6\u00d3\3\2\2\2\u00c7\u00c8\7\20\2\2\u00c8\u00c9\5\22\n\2\u00c9"+
		"\u00ca\b\13\1\2\u00ca\u00cb\3\2\2\2\u00cb\u00cc\b\13\1\2\u00cc\u00d4\3"+
		"\2\2\2\u00cd\u00ce\7\21\2\2\u00ce\u00cf\5\22\n\2\u00cf\u00d0\b\13\1\2"+
		"\u00d0\u00d1\3\2\2\2\u00d1\u00d2\b\13\1\2\u00d2\u00d4\3\2\2\2\u00d3\u00c7"+
		"\3\2\2\2\u00d3\u00cd\3\2\2\2\u00d4\25\3\2\2\2\u00d5\u00d9\b\f\1\2\u00d6"+
		"\u00d7\5\24\13\2\u00d7\u00d8\b\f\1\2\u00d8\u00da\3\2\2\2\u00d9\u00d6\3"+
		"\2\2\2\u00d9\u00da\3\2\2\2\u00da\u00f3\3\2\2\2\u00db\u00dc\7\22\2\2\u00dc"+
		"\u00dd\5\24\13\2\u00dd\u00de\b\f\1\2\u00de\u00df\3\2\2\2\u00df\u00e0\b"+
		"\f\1\2\u00e0\u00f4\3\2\2\2\u00e1\u00e2\7\23\2\2\u00e2\u00e3\5\24\13\2"+
		"\u00e3\u00e4\b\f\1\2\u00e4\u00e5\3\2\2\2\u00e5\u00e6\b\f\1\2\u00e6\u00f4"+
		"\3\2\2\2\u00e7\u00e8\7\24\2\2\u00e8\u00e9\5\24\13\2\u00e9\u00ea\b\f\1"+
		"\2\u00ea\u00eb\3\2\2\2\u00eb\u00ec\b\f\1\2\u00ec\u00f4\3\2\2\2\u00ed\u00ee"+
		"\7\25\2\2\u00ee\u00ef\5\24\13\2\u00ef\u00f0\b\f\1\2\u00f0\u00f1\3\2\2"+
		"\2\u00f1\u00f2\b\f\1\2\u00f2\u00f4\3\2\2\2\u00f3\u00db\3\2\2\2\u00f3\u00e1"+
		"\3\2\2\2\u00f3\u00e7\3\2\2\2\u00f3\u00ed\3\2\2\2\u00f4\27\3\2\2\2\u00f5"+
		"\u00f9\b\r\1\2\u00f6\u00f7\5\26\f\2\u00f7\u00f8\b\r\1\2\u00f8\u00fa\3"+
		"\2\2\2\u00f9\u00f6\3\2\2\2\u00f9\u00fa\3\2\2\2\u00fa\u0107\3\2\2\2\u00fb"+
		"\u00fc\7\26\2\2\u00fc\u00fd\5\26\f\2\u00fd\u00fe\b\r\1\2\u00fe\u00ff\3"+
		"\2\2\2\u00ff\u0100\b\r\1\2\u0100\u0108\3\2\2\2\u0101\u0102\7\27\2\2\u0102"+
		"\u0103\5\26\f\2\u0103\u0104\b\r\1\2\u0104\u0105\3\2\2\2\u0105\u0106\b"+
		"\r\1\2\u0106\u0108\3\2\2\2\u0107\u00fb\3\2\2\2\u0107\u0101\3\2\2\2\u0108"+
		"\31\3\2\2\2\u0109\u010a\5\30\r\2\u010a\33\3\2\2\2\u010b\u010c\5\32\16"+
		"\2\u010c\35\3\2\2\2\u010d\u010e\5\34\17\2\u010e\37\3\2\2\2\u010f\u0110"+
		"\5\36\20\2\u0110!\3\2\2\2\u0111\u0112\5 \21\2\u0112#\3\2\2\2\u0113\u0117"+
		"\b\23\1\2\u0114\u0115\5\"\22\2\u0115\u0116\b\23\1\2\u0116\u0118\3\2\2"+
		"\2\u0117\u0114\3\2\2\2\u0117\u0118\3\2\2\2\u0118\u0119\3\2\2\2\u0119\u011a"+
		"\7\30\2\2\u011a\u011b\5$\23\2\u011b\u011c\b\23\1\2\u011c\u011d\3\2\2\2"+
		"\u011d\u011e\7\31\2\2\u011e\u011f\5$\23\2\u011f\u0120\b\23\1\2\u0120\u0121"+
		"\3\2\2\2\u0121\u0122\b\23\1\2\u0122%\3\2\2\2\u0123\u0124\3\2\2\2\u0124"+
		"\'\3\2\2\2\u0125\u0126\3\2\2\2\u0126)\3\2\2\2\23=CVz\u008a\u008d\u0097"+
		"\u00ab\u00b1\u00bf\u00c5\u00d3\u00d9\u00f3\u00f9\u0107\u0117";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
