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
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExpressionPair;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@SuppressWarnings("all")
public class DebugExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		DIGIT=10, CR=11, LF=12, SINGLECOMMA=13, QUOTE=14, IDENT=15, NUMBER=16, 
		FLOATNUMBER=17, CHARCONST=18, WS=19;
	public static final int
		RULE_debugExpr = 0, RULE_primExpr = 1, RULE_designator = 2, RULE_actPars = 3, 
		RULE_unaryExpr = 4, RULE_castExpr = 5, RULE_multExpr = 6, RULE_addExpr = 7, 
		RULE_shiftExpr = 8, RULE_relExpr = 9, RULE_eqExpr = 10, RULE_andExpr = 11, 
		RULE_xorExpr = 12, RULE_orExpr = 13, RULE_logAndExpr = 14, RULE_logOrExpr = 15, 
		RULE_expr = 16;
	public static final String[] ruleNames = {
		"debugExpr", "primExpr", "designator", "actPars", "unaryExpr", "castExpr", 
		"multExpr", "addExpr", "shiftExpr", "relExpr", "eqExpr", "andExpr", "xorExpr", 
		"orExpr", "logAndExpr", "logOrExpr", "expr"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'('", "')'", "'['", "']'", "'.'", "'->'", "','", "'?'", "':'", 
		null, "'\r'", "'\n'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, "DIGIT", "CR", 
		"LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", 
		"WS"
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
			setState(35);
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
			setState(53);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
				{
				setState(40);
				_localctx.t = match(IDENT);
				 _localctx.p =  NF.createVarNode(_localctx.t.getText()); 
				}
				break;
			case NUMBER:
				{
				setState(42);
				_localctx.t = match(NUMBER);
				 _localctx.p =  NF.createIntegerConstant(Integer.parseInt(_localctx.t.getText())); 
				}
				break;
			case FLOATNUMBER:
				{
				setState(44);
				_localctx.t = match(FLOATNUMBER);
				 _localctx.p =  NF.createFloatConstant(Float.parseFloat(_localctx.t.getText())); 
				}
				break;
			case CHARCONST:
				{
				setState(46);
				_localctx.t = match(CHARCONST);
				 _localctx.p =  NF.createCharacterConstant(_localctx.t.getText()); 
				}
				break;
			case T__0:
				{
				setState(48);
				match(T__0);
				setState(49);
				_localctx.expr = expr();
				setState(50);
				match(T__1);
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
			  
			setState(59);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				{
				setState(56);
				_localctx.primExpr = primExpr();
				 prev = _localctx.primExpr.p; 
				}
				break;
			}
			setState(78);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
				{
				setState(61);
				match(T__2);
				setState(62);
				_localctx.expr = expr();
				 idxPair = _localctx.expr.p; 
				setState(64);
				match(T__3);
				 _localctx.p =  NF.createArrayElement(prev, idxPair); 
				}
				break;
			case T__0:
				{
				{
				setState(67);
				_localctx.actPars = actPars();
				 list = _localctx.actPars.l; 
				}
				 _localctx.p =  NF.createFunctionCall(prev, list); 
				}
				break;
			case T__4:
				{
				setState(72);
				match(T__4);
				{
				setState(73);
				_localctx.t = match(IDENT);
				}
				 _localctx.p =  NF.createObjectMember(prev, _localctx.t.getText()); 
				}
				break;
			case T__5:
				{
				setState(75);
				match(T__5);
				{
				setState(76);
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
			  
			setState(81);
			match(T__0);
			{
			setState(82);
			_localctx.expr = expr();
			 p1 = _localctx.expr.p; 
			}
			 _localctx.l.add(p1); 
			setState(86);
			match(T__6);
			{
			setState(87);
			_localctx.expr = expr();
			 p2 = _localctx.expr.p; 
			}
			 _localctx.l.add(p2); 
			setState(91);
			match(T__1);
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
		public DesignatorContext designator() {
			return getRuleContext(DesignatorContext.class,0);
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
			setState(93);
			designator();
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
		public UnaryExprContext unaryExpr() {
			return getRuleContext(UnaryExprContext.class,0);
		}
		public CastExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_castExpr; }
	}

	public final CastExprContext castExpr() throws RecognitionException {
		CastExprContext _localctx = new CastExprContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_castExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(95);
			unaryExpr();
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
		public CastExprContext castExpr() {
			return getRuleContext(CastExprContext.class,0);
		}
		public MultExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multExpr; }
	}

	public final MultExprContext multExpr() throws RecognitionException {
		MultExprContext _localctx = new MultExprContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_multExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(97);
			castExpr();
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
		public MultExprContext multExpr() {
			return getRuleContext(MultExprContext.class,0);
		}
		public AddExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_addExpr; }
	}

	public final AddExprContext addExpr() throws RecognitionException {
		AddExprContext _localctx = new AddExprContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_addExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(99);
			multExpr();
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
		public AddExprContext addExpr() {
			return getRuleContext(AddExprContext.class,0);
		}
		public ShiftExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shiftExpr; }
	}

	public final ShiftExprContext shiftExpr() throws RecognitionException {
		ShiftExprContext _localctx = new ShiftExprContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_shiftExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(101);
			addExpr();
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
		public ShiftExprContext shiftExpr() {
			return getRuleContext(ShiftExprContext.class,0);
		}
		public RelExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relExpr; }
	}

	public final RelExprContext relExpr() throws RecognitionException {
		RelExprContext _localctx = new RelExprContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_relExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(103);
			shiftExpr();
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
		public RelExprContext relExpr() {
			return getRuleContext(RelExprContext.class,0);
		}
		public EqExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eqExpr; }
	}

	public final EqExprContext eqExpr() throws RecognitionException {
		EqExprContext _localctx = new EqExprContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_eqExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(105);
			relExpr();
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
		enterRule(_localctx, 22, RULE_andExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(107);
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
		enterRule(_localctx, 24, RULE_xorExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(109);
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
		enterRule(_localctx, 26, RULE_orExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(111);
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
		enterRule(_localctx, 28, RULE_logAndExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(113);
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
		enterRule(_localctx, 30, RULE_logOrExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(115);
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
		enterRule(_localctx, 32, RULE_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  DebugExpressionPair pThen = null;
			  DebugExpressionPair pElse = null;
			  DebugExpressionPair prev = null;
			  
			setState(121);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__2) | (1L << T__4) | (1L << T__5) | (1L << IDENT) | (1L << NUMBER) | (1L << FLOATNUMBER) | (1L << CHARCONST))) != 0)) {
				{
				setState(118);
				_localctx.logOrExpr = logOrExpr();
				 prev = _localctx.logOrExpr.p; 
				}
			}

			{
			setState(123);
			match(T__7);
			{
			setState(124);
			_localctx.expr = expr();
			 pThen = _localctx.expr.p; 
			}
			setState(127);
			match(T__8);
			{
			setState(128);
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

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\25\u0088\4\2\t\2"+
		"\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\5\38\n\3\3\4\3\4\3\4\3\4\5\4>\n\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3"+
		"\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\5\4Q\n\4\3\5\3\5\3\5\3\5\3\5\3"+
		"\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n"+
		"\3\13\3\13\3\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\22"+
		"\3\22\3\22\3\22\5\22|\n\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22"+
		"\3\22\3\22\2\2\23\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"\2\2\2\177"+
		"\2$\3\2\2\2\4\67\3\2\2\2\69\3\2\2\2\bR\3\2\2\2\n_\3\2\2\2\fa\3\2\2\2\16"+
		"c\3\2\2\2\20e\3\2\2\2\22g\3\2\2\2\24i\3\2\2\2\26k\3\2\2\2\30m\3\2\2\2"+
		"\32o\3\2\2\2\34q\3\2\2\2\36s\3\2\2\2 u\3\2\2\2\"w\3\2\2\2$%\b\2\1\2%&"+
		"\5\"\22\2&\'\b\2\1\2\'(\3\2\2\2()\b\2\1\2)\3\3\2\2\2*+\7\21\2\2+8\b\3"+
		"\1\2,-\7\22\2\2-8\b\3\1\2./\7\23\2\2/8\b\3\1\2\60\61\7\24\2\2\618\b\3"+
		"\1\2\62\63\7\3\2\2\63\64\5\"\22\2\64\65\7\4\2\2\65\66\b\3\1\2\668\3\2"+
		"\2\2\67*\3\2\2\2\67,\3\2\2\2\67.\3\2\2\2\67\60\3\2\2\2\67\62\3\2\2\28"+
		"\5\3\2\2\29=\b\4\1\2:;\5\4\3\2;<\b\4\1\2<>\3\2\2\2=:\3\2\2\2=>\3\2\2\2"+
		">P\3\2\2\2?@\7\5\2\2@A\5\"\22\2AB\b\4\1\2BC\7\6\2\2CD\b\4\1\2DQ\3\2\2"+
		"\2EF\5\b\5\2FG\b\4\1\2GH\3\2\2\2HI\b\4\1\2IQ\3\2\2\2JK\7\7\2\2KL\7\21"+
		"\2\2LQ\b\4\1\2MN\7\b\2\2NO\7\21\2\2OQ\b\4\1\2P?\3\2\2\2PE\3\2\2\2PJ\3"+
		"\2\2\2PM\3\2\2\2Q\7\3\2\2\2RS\b\5\1\2ST\7\3\2\2TU\5\"\22\2UV\b\5\1\2V"+
		"W\3\2\2\2WX\b\5\1\2XY\7\t\2\2YZ\5\"\22\2Z[\b\5\1\2[\\\3\2\2\2\\]\b\5\1"+
		"\2]^\7\4\2\2^\t\3\2\2\2_`\5\6\4\2`\13\3\2\2\2ab\5\n\6\2b\r\3\2\2\2cd\5"+
		"\f\7\2d\17\3\2\2\2ef\5\16\b\2f\21\3\2\2\2gh\5\20\t\2h\23\3\2\2\2ij\5\22"+
		"\n\2j\25\3\2\2\2kl\5\24\13\2l\27\3\2\2\2mn\5\26\f\2n\31\3\2\2\2op\5\30"+
		"\r\2p\33\3\2\2\2qr\5\32\16\2r\35\3\2\2\2st\5\34\17\2t\37\3\2\2\2uv\5\36"+
		"\20\2v!\3\2\2\2w{\b\22\1\2xy\5 \21\2yz\b\22\1\2z|\3\2\2\2{x\3\2\2\2{|"+
		"\3\2\2\2|}\3\2\2\2}~\7\n\2\2~\177\5\"\22\2\177\u0080\b\22\1\2\u0080\u0081"+
		"\3\2\2\2\u0081\u0082\7\13\2\2\u0082\u0083\5\"\22\2\u0083\u0084\b\22\1"+
		"\2\u0084\u0085\3\2\2\2\u0085\u0086\b\22\1\2\u0086#\3\2\2\2\6\67=P{";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
