/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.dsl.processor.expression;

// DO NOT MODIFY - generated from Expression.g4 using "mx create-dsl-parser"

import com.oracle.truffle.dsl.processor.expression.DSLExpression.*;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "this-escape"})
public class ExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.12.0", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, WS=13, IDENTIFIER=14, NUMERIC_LITERAL=15;
	public static final int
		RULE_expression = 0, RULE_logic_factor = 1, RULE_comparison_factor = 2, 
		RULE_negate_factor = 3, RULE_factor = 4, RULE_member_expression = 5;
	private static String[] makeRuleNames() {
		return new String[] {
			"expression", "logic_factor", "comparison_factor", "negate_factor", "factor", 
			"member_expression"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'||'", "'<'", "'<='", "'>'", "'>='", "'=='", "'!='", "'!'", "'('", 
			"')'", "','", "'.'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, "WS", "IDENTIFIER", "NUMERIC_LITERAL"
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
	public String getGrammarFileName() { return "Expression.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ExpressionParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionContext extends ParserRuleContext {
		public DSLExpression result;
		public Logic_factorContext f;
		public TerminalNode EOF() { return getToken(ExpressionParser.EOF, 0); }
		public Logic_factorContext logic_factor() {
			return getRuleContext(Logic_factorContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(12);
			_localctx.f = logic_factor();
			 _localctx.result =  _localctx.f.result; 
			setState(14);
			match(EOF);
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
	public static class Logic_factorContext extends ParserRuleContext {
		public DSLExpression result;
		public Comparison_factorContext f1;
		public Token op;
		public Comparison_factorContext f2;
		public List<Comparison_factorContext> comparison_factor() {
			return getRuleContexts(Comparison_factorContext.class);
		}
		public Comparison_factorContext comparison_factor(int i) {
			return getRuleContext(Comparison_factorContext.class,i);
		}
		public Logic_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logic_factor; }
	}

	public final Logic_factorContext logic_factor() throws RecognitionException {
		Logic_factorContext _localctx = new Logic_factorContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_logic_factor);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(16);
			_localctx.f1 = comparison_factor();
			 _localctx.result =  _localctx.f1.result; 
			setState(22);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(18);
				_localctx.op = match(T__0);
				setState(19);
				_localctx.f2 = comparison_factor();
				 _localctx.result =  new Binary((_localctx.op!=null?_localctx.op.getText():null), _localctx.result, _localctx.f2.result); 
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
	public static class Comparison_factorContext extends ParserRuleContext {
		public DSLExpression result;
		public Negate_factorContext f1;
		public Token op;
		public Negate_factorContext f2;
		public List<Negate_factorContext> negate_factor() {
			return getRuleContexts(Negate_factorContext.class);
		}
		public Negate_factorContext negate_factor(int i) {
			return getRuleContext(Negate_factorContext.class,i);
		}
		public Comparison_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison_factor; }
	}

	public final Comparison_factorContext comparison_factor() throws RecognitionException {
		Comparison_factorContext _localctx = new Comparison_factorContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_comparison_factor);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(24);
			_localctx.f1 = negate_factor();
			 _localctx.result =  _localctx.f1.result; 
			setState(30);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 252L) != 0)) {
				{
				setState(26);
				_localctx.op = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 252L) != 0)) ) {
					_localctx.op = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(27);
				_localctx.f2 = negate_factor();
				 _localctx.result =  new Binary((_localctx.op!=null?_localctx.op.getText():null), _localctx.result, _localctx.f2.result); 
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
	public static class Negate_factorContext extends ParserRuleContext {
		public DSLExpression result;
		public FactorContext f;
		public FactorContext factor() {
			return getRuleContext(FactorContext.class,0);
		}
		public Negate_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_negate_factor; }
	}

	public final Negate_factorContext negate_factor() throws RecognitionException {
		Negate_factorContext _localctx = new Negate_factorContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_negate_factor);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 boolean negated = false; 
			setState(35);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__7) {
				{
				setState(33);
				match(T__7);
				 negated = true; 
				}
			}

			setState(37);
			_localctx.f = factor();
			 _localctx.result =  negated ? new Negate(_localctx.f.result) : _localctx.f.result; 
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
	public static class FactorContext extends ParserRuleContext {
		public DSLExpression result;
		public Member_expressionContext m;
		public Token l;
		public Logic_factorContext e;
		public Member_expressionContext member_expression() {
			return getRuleContext(Member_expressionContext.class,0);
		}
		public TerminalNode NUMERIC_LITERAL() { return getToken(ExpressionParser.NUMERIC_LITERAL, 0); }
		public Logic_factorContext logic_factor() {
			return getRuleContext(Logic_factorContext.class,0);
		}
		public FactorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_factor; }
	}

	public final FactorContext factor() throws RecognitionException {
		FactorContext _localctx = new FactorContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_factor);
		try {
			setState(50);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(40);
				_localctx.m = member_expression();
				 _localctx.result =  _localctx.m.result; 
				}
				break;
			case NUMERIC_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(43);
				_localctx.l = match(NUMERIC_LITERAL);
				 _localctx.result =  new IntLiteral((_localctx.l!=null?_localctx.l.getText():null)); 
				}
				break;
			case T__8:
				enterOuterAlt(_localctx, 3);
				{
				setState(45);
				match(T__8);
				setState(46);
				_localctx.e = logic_factor();
				 _localctx.result =  _localctx.e.result; 
				setState(48);
				match(T__9);
				}
				break;
			default:
				throw new NoViableAltException(this);
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
	public static class Member_expressionContext extends ParserRuleContext {
		public DSLExpression result;
		public Token id1;
		public Logic_factorContext e1;
		public Logic_factorContext e2;
		public Token id2;
		public List<TerminalNode> IDENTIFIER() { return getTokens(ExpressionParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(ExpressionParser.IDENTIFIER, i);
		}
		public List<Logic_factorContext> logic_factor() {
			return getRuleContexts(Logic_factorContext.class);
		}
		public Logic_factorContext logic_factor(int i) {
			return getRuleContext(Logic_factorContext.class,i);
		}
		public Member_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_member_expression; }
	}

	public final Member_expressionContext member_expression() throws RecognitionException {
		Member_expressionContext _localctx = new Member_expressionContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_member_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(52);
			_localctx.id1 = match(IDENTIFIER);
			 _localctx.result =  new Variable(null, (_localctx.id1!=null?_localctx.id1.getText():null)); 
			setState(71);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__8) {
				{
				setState(54);
				match(T__8);
				 List<DSLExpression> parameters = new ArrayList<>(); 
				setState(67);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 49920L) != 0)) {
					{
					setState(56);
					_localctx.e1 = logic_factor();
					 parameters.add(_localctx.e1.result); 
					setState(64);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==T__10) {
						{
						{
						setState(58);
						match(T__10);
						setState(59);
						_localctx.e2 = logic_factor();
						 parameters.add(_localctx.e2.result); 
						}
						}
						setState(66);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(69);
				match(T__9);
				 _localctx.result =  new Call(null, (_localctx.id1!=null?_localctx.id1.getText():null), parameters); 
				}
			}

			setState(97);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__11) {
				{
				{
				setState(73);
				match(T__11);
				setState(74);
				_localctx.id2 = match(IDENTIFIER);
				 _localctx.result =  new Variable(_localctx.result, (_localctx.id2!=null?_localctx.id2.getText():null)); 
				setState(93);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__8) {
					{
					setState(76);
					match(T__8);
					 List<DSLExpression> parameters = new ArrayList<>(); 
					setState(89);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 49920L) != 0)) {
						{
						setState(78);
						_localctx.e1 = logic_factor();
						 parameters.add(_localctx.e1.result); 
						setState(86);
						_errHandler.sync(this);
						_la = _input.LA(1);
						while (_la==T__10) {
							{
							{
							setState(80);
							match(T__10);
							setState(81);
							_localctx.e2 = logic_factor();
							 parameters.add(_localctx.e2.result); 
							}
							}
							setState(88);
							_errHandler.sync(this);
							_la = _input.LA(1);
						}
						}
					}

					setState(91);
					match(T__9);
					 _localctx.result =  new Call(((Variable) _localctx.result).getReceiver(), (_localctx.id2!=null?_localctx.id2.getText():null), parameters); 
					}
				}

				}
				}
				setState(99);
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

	public static final String _serializedATN =
		"\u0004\u0001\u000fe\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003"+
		"\u0001\u0017\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u0002\u001f\b\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0003\u0003$\b\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u00043\b\u0004\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005?\b\u0005\n\u0005"+
		"\f\u0005B\t\u0005\u0003\u0005D\b\u0005\u0001\u0005\u0001\u0005\u0003\u0005"+
		"H\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0005\u0005U\b\u0005\n\u0005\f\u0005X\t\u0005\u0003\u0005Z\b\u0005\u0001"+
		"\u0005\u0001\u0005\u0003\u0005^\b\u0005\u0005\u0005`\b\u0005\n\u0005\f"+
		"\u0005c\t\u0005\u0001\u0005\u0000\u0000\u0006\u0000\u0002\u0004\u0006"+
		"\b\n\u0000\u0001\u0001\u0000\u0002\u0007j\u0000\f\u0001\u0000\u0000\u0000"+
		"\u0002\u0010\u0001\u0000\u0000\u0000\u0004\u0018\u0001\u0000\u0000\u0000"+
		"\u0006 \u0001\u0000\u0000\u0000\b2\u0001\u0000\u0000\u0000\n4\u0001\u0000"+
		"\u0000\u0000\f\r\u0003\u0002\u0001\u0000\r\u000e\u0006\u0000\uffff\uffff"+
		"\u0000\u000e\u000f\u0005\u0000\u0000\u0001\u000f\u0001\u0001\u0000\u0000"+
		"\u0000\u0010\u0011\u0003\u0004\u0002\u0000\u0011\u0016\u0006\u0001\uffff"+
		"\uffff\u0000\u0012\u0013\u0005\u0001\u0000\u0000\u0013\u0014\u0003\u0004"+
		"\u0002\u0000\u0014\u0015\u0006\u0001\uffff\uffff\u0000\u0015\u0017\u0001"+
		"\u0000\u0000\u0000\u0016\u0012\u0001\u0000\u0000\u0000\u0016\u0017\u0001"+
		"\u0000\u0000\u0000\u0017\u0003\u0001\u0000\u0000\u0000\u0018\u0019\u0003"+
		"\u0006\u0003\u0000\u0019\u001e\u0006\u0002\uffff\uffff\u0000\u001a\u001b"+
		"\u0007\u0000\u0000\u0000\u001b\u001c\u0003\u0006\u0003\u0000\u001c\u001d"+
		"\u0006\u0002\uffff\uffff\u0000\u001d\u001f\u0001\u0000\u0000\u0000\u001e"+
		"\u001a\u0001\u0000\u0000\u0000\u001e\u001f\u0001\u0000\u0000\u0000\u001f"+
		"\u0005\u0001\u0000\u0000\u0000 #\u0006\u0003\uffff\uffff\u0000!\"\u0005"+
		"\b\u0000\u0000\"$\u0006\u0003\uffff\uffff\u0000#!\u0001\u0000\u0000\u0000"+
		"#$\u0001\u0000\u0000\u0000$%\u0001\u0000\u0000\u0000%&\u0003\b\u0004\u0000"+
		"&\'\u0006\u0003\uffff\uffff\u0000\'\u0007\u0001\u0000\u0000\u0000()\u0003"+
		"\n\u0005\u0000)*\u0006\u0004\uffff\uffff\u0000*3\u0001\u0000\u0000\u0000"+
		"+,\u0005\u000f\u0000\u0000,3\u0006\u0004\uffff\uffff\u0000-.\u0005\t\u0000"+
		"\u0000./\u0003\u0002\u0001\u0000/0\u0006\u0004\uffff\uffff\u000001\u0005"+
		"\n\u0000\u000013\u0001\u0000\u0000\u00002(\u0001\u0000\u0000\u00002+\u0001"+
		"\u0000\u0000\u00002-\u0001\u0000\u0000\u00003\t\u0001\u0000\u0000\u0000"+
		"45\u0005\u000e\u0000\u00005G\u0006\u0005\uffff\uffff\u000067\u0005\t\u0000"+
		"\u00007C\u0006\u0005\uffff\uffff\u000089\u0003\u0002\u0001\u00009@\u0006"+
		"\u0005\uffff\uffff\u0000:;\u0005\u000b\u0000\u0000;<\u0003\u0002\u0001"+
		"\u0000<=\u0006\u0005\uffff\uffff\u0000=?\u0001\u0000\u0000\u0000>:\u0001"+
		"\u0000\u0000\u0000?B\u0001\u0000\u0000\u0000@>\u0001\u0000\u0000\u0000"+
		"@A\u0001\u0000\u0000\u0000AD\u0001\u0000\u0000\u0000B@\u0001\u0000\u0000"+
		"\u0000C8\u0001\u0000\u0000\u0000CD\u0001\u0000\u0000\u0000DE\u0001\u0000"+
		"\u0000\u0000EF\u0005\n\u0000\u0000FH\u0006\u0005\uffff\uffff\u0000G6\u0001"+
		"\u0000\u0000\u0000GH\u0001\u0000\u0000\u0000Ha\u0001\u0000\u0000\u0000"+
		"IJ\u0005\f\u0000\u0000JK\u0005\u000e\u0000\u0000K]\u0006\u0005\uffff\uffff"+
		"\u0000LM\u0005\t\u0000\u0000MY\u0006\u0005\uffff\uffff\u0000NO\u0003\u0002"+
		"\u0001\u0000OV\u0006\u0005\uffff\uffff\u0000PQ\u0005\u000b\u0000\u0000"+
		"QR\u0003\u0002\u0001\u0000RS\u0006\u0005\uffff\uffff\u0000SU\u0001\u0000"+
		"\u0000\u0000TP\u0001\u0000\u0000\u0000UX\u0001\u0000\u0000\u0000VT\u0001"+
		"\u0000\u0000\u0000VW\u0001\u0000\u0000\u0000WZ\u0001\u0000\u0000\u0000"+
		"XV\u0001\u0000\u0000\u0000YN\u0001\u0000\u0000\u0000YZ\u0001\u0000\u0000"+
		"\u0000Z[\u0001\u0000\u0000\u0000[\\\u0005\n\u0000\u0000\\^\u0006\u0005"+
		"\uffff\uffff\u0000]L\u0001\u0000\u0000\u0000]^\u0001\u0000\u0000\u0000"+
		"^`\u0001\u0000\u0000\u0000_I\u0001\u0000\u0000\u0000`c\u0001\u0000\u0000"+
		"\u0000a_\u0001\u0000\u0000\u0000ab\u0001\u0000\u0000\u0000b\u000b\u0001"+
		"\u0000\u0000\u0000ca\u0001\u0000\u0000\u0000\u000b\u0016\u001e#2@CGVY"+
		"]a";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
