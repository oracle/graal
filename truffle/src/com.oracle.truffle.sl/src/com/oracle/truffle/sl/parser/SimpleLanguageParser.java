/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.parser;

// DO NOT MODIFY - generated from SimpleLanguage.g4 using "mx create-sl-parser"

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "this-escape"})
public class SimpleLanguageParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, WS=19, COMMENT=20, LINE_COMMENT=21, OP_OR=22, OP_AND=23, OP_COMPARE=24, 
		OP_ADD=25, OP_MUL=26, IDENTIFIER=27, STRING_LITERAL=28, NUMERIC_LITERAL=29;
	public static final int
		RULE_simplelanguage = 0, RULE_function = 1, RULE_block = 2, RULE_statement = 3, 
		RULE_break_statement = 4, RULE_continue_statement = 5, RULE_expression_statement = 6, 
		RULE_debugger_statement = 7, RULE_while_statement = 8, RULE_if_statement = 9, 
		RULE_return_statement = 10, RULE_expression = 11, RULE_logic_term = 12, 
		RULE_logic_factor = 13, RULE_arithmetic = 14, RULE_term = 15, RULE_factor = 16, 
		RULE_member_expression = 17;
	private static String[] makeRuleNames() {
		return new String[] {
			"simplelanguage", "function", "block", "statement", "break_statement", 
			"continue_statement", "expression_statement", "debugger_statement", "while_statement", 
			"if_statement", "return_statement", "expression", "logic_term", "logic_factor", 
			"arithmetic", "term", "factor", "member_expression"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'function'", "'('", "','", "')'", "'{'", "'}'", "'break'", "';'", 
			"'continue'", "'debugger'", "'while'", "'if'", "'else'", "'return'", 
			"'='", "'.'", "'['", "']'", null, null, null, "'||'", "'&&'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, "WS", "COMMENT", "LINE_COMMENT", 
			"OP_OR", "OP_AND", "OP_COMPARE", "OP_ADD", "OP_MUL", "IDENTIFIER", "STRING_LITERAL", 
			"NUMERIC_LITERAL"
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
	public String getGrammarFileName() { return "SimpleLanguage.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SimpleLanguageParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SimplelanguageContext extends ParserRuleContext {
		public List<FunctionContext> function() {
			return getRuleContexts(FunctionContext.class);
		}
		public FunctionContext function(int i) {
			return getRuleContext(FunctionContext.class,i);
		}
		public TerminalNode EOF() { return getToken(SimpleLanguageParser.EOF, 0); }
		public SimplelanguageContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simplelanguage; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitSimplelanguage(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SimplelanguageContext simplelanguage() throws RecognitionException {
		SimplelanguageContext _localctx = new SimplelanguageContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_simplelanguage);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(36);
			function();
			setState(40);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(37);
				function();
				}
				}
				setState(42);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(43);
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
	public static class FunctionContext extends ParserRuleContext {
		public Token s;
		public BlockContext body;
		public List<TerminalNode> IDENTIFIER() { return getTokens(SimpleLanguageParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(SimpleLanguageParser.IDENTIFIER, i);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public FunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitFunction(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionContext function() throws RecognitionException {
		FunctionContext _localctx = new FunctionContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_function);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(45);
			match(T__0);
			setState(46);
			match(IDENTIFIER);
			setState(47);
			_localctx.s = match(T__1);
			setState(56);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(48);
				match(IDENTIFIER);
				setState(53);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__2) {
					{
					{
					setState(49);
					match(T__2);
					setState(50);
					match(IDENTIFIER);
					}
					}
					setState(55);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(58);
			match(T__3);
			setState(59);
			_localctx.body = block();
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
	public static class BlockContext extends ParserRuleContext {
		public Token s;
		public Token e;
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(61);
			_localctx.s = match(T__4);
			setState(65);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 939548292L) != 0)) {
				{
				{
				setState(62);
				statement();
				}
				}
				setState(67);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(68);
			_localctx.e = match(T__5);
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
	public static class StatementContext extends ParserRuleContext {
		public While_statementContext while_statement() {
			return getRuleContext(While_statementContext.class,0);
		}
		public Break_statementContext break_statement() {
			return getRuleContext(Break_statementContext.class,0);
		}
		public Continue_statementContext continue_statement() {
			return getRuleContext(Continue_statementContext.class,0);
		}
		public If_statementContext if_statement() {
			return getRuleContext(If_statementContext.class,0);
		}
		public Return_statementContext return_statement() {
			return getRuleContext(Return_statementContext.class,0);
		}
		public Expression_statementContext expression_statement() {
			return getRuleContext(Expression_statementContext.class,0);
		}
		public Debugger_statementContext debugger_statement() {
			return getRuleContext(Debugger_statementContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_statement);
		try {
			setState(77);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__10:
				enterOuterAlt(_localctx, 1);
				{
				setState(70);
				while_statement();
				}
				break;
			case T__6:
				enterOuterAlt(_localctx, 2);
				{
				setState(71);
				break_statement();
				}
				break;
			case T__8:
				enterOuterAlt(_localctx, 3);
				{
				setState(72);
				continue_statement();
				}
				break;
			case T__11:
				enterOuterAlt(_localctx, 4);
				{
				setState(73);
				if_statement();
				}
				break;
			case T__13:
				enterOuterAlt(_localctx, 5);
				{
				setState(74);
				return_statement();
				}
				break;
			case T__1:
			case IDENTIFIER:
			case STRING_LITERAL:
			case NUMERIC_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(75);
				expression_statement();
				}
				break;
			case T__9:
				enterOuterAlt(_localctx, 7);
				{
				setState(76);
				debugger_statement();
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
	public static class Break_statementContext extends ParserRuleContext {
		public Token b;
		public Break_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_break_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitBreak_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Break_statementContext break_statement() throws RecognitionException {
		Break_statementContext _localctx = new Break_statementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_break_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(79);
			_localctx.b = match(T__6);
			setState(80);
			match(T__7);
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
	public static class Continue_statementContext extends ParserRuleContext {
		public Token c;
		public Continue_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continue_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitContinue_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Continue_statementContext continue_statement() throws RecognitionException {
		Continue_statementContext _localctx = new Continue_statementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_continue_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(82);
			_localctx.c = match(T__8);
			setState(83);
			match(T__7);
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
	public static class Expression_statementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public Expression_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitExpression_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_statementContext expression_statement() throws RecognitionException {
		Expression_statementContext _localctx = new Expression_statementContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_expression_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(85);
			expression();
			setState(86);
			match(T__7);
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
	public static class Debugger_statementContext extends ParserRuleContext {
		public Token d;
		public Debugger_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_debugger_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitDebugger_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Debugger_statementContext debugger_statement() throws RecognitionException {
		Debugger_statementContext _localctx = new Debugger_statementContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_debugger_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(88);
			_localctx.d = match(T__9);
			setState(89);
			match(T__7);
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
	public static class While_statementContext extends ParserRuleContext {
		public Token w;
		public ExpressionContext condition;
		public BlockContext body;
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public While_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_while_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitWhile_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final While_statementContext while_statement() throws RecognitionException {
		While_statementContext _localctx = new While_statementContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_while_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(91);
			_localctx.w = match(T__10);
			setState(92);
			match(T__1);
			setState(93);
			_localctx.condition = expression();
			setState(94);
			match(T__3);
			setState(95);
			_localctx.body = block();
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
	public static class If_statementContext extends ParserRuleContext {
		public Token i;
		public ExpressionContext condition;
		public BlockContext then;
		public BlockContext alt;
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
		}
		public If_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitIf_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final If_statementContext if_statement() throws RecognitionException {
		If_statementContext _localctx = new If_statementContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_if_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(97);
			_localctx.i = match(T__11);
			setState(98);
			match(T__1);
			setState(99);
			_localctx.condition = expression();
			setState(100);
			match(T__3);
			setState(101);
			_localctx.then = block();
			setState(104);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12) {
				{
				setState(102);
				match(T__12);
				setState(103);
				_localctx.alt = block();
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
	public static class Return_statementContext extends ParserRuleContext {
		public Token r;
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public Return_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_return_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitReturn_statement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Return_statementContext return_statement() throws RecognitionException {
		Return_statementContext _localctx = new Return_statementContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_return_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(106);
			_localctx.r = match(T__13);
			setState(108);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 939524100L) != 0)) {
				{
				setState(107);
				expression();
				}
			}

			setState(110);
			match(T__7);
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
	public static class ExpressionContext extends ParserRuleContext {
		public List<Logic_termContext> logic_term() {
			return getRuleContexts(Logic_termContext.class);
		}
		public Logic_termContext logic_term(int i) {
			return getRuleContext(Logic_termContext.class,i);
		}
		public List<TerminalNode> OP_OR() { return getTokens(SimpleLanguageParser.OP_OR); }
		public TerminalNode OP_OR(int i) {
			return getToken(SimpleLanguageParser.OP_OR, i);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_expression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(112);
			logic_term();
			setState(117);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(113);
					match(OP_OR);
					setState(114);
					logic_term();
					}
					} 
				}
				setState(119);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
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
	public static class Logic_termContext extends ParserRuleContext {
		public List<Logic_factorContext> logic_factor() {
			return getRuleContexts(Logic_factorContext.class);
		}
		public Logic_factorContext logic_factor(int i) {
			return getRuleContext(Logic_factorContext.class,i);
		}
		public List<TerminalNode> OP_AND() { return getTokens(SimpleLanguageParser.OP_AND); }
		public TerminalNode OP_AND(int i) {
			return getToken(SimpleLanguageParser.OP_AND, i);
		}
		public Logic_termContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logic_term; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitLogic_term(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Logic_termContext logic_term() throws RecognitionException {
		Logic_termContext _localctx = new Logic_termContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_logic_term);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(120);
			logic_factor();
			setState(125);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,8,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(121);
					match(OP_AND);
					setState(122);
					logic_factor();
					}
					} 
				}
				setState(127);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,8,_ctx);
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
	public static class Logic_factorContext extends ParserRuleContext {
		public List<ArithmeticContext> arithmetic() {
			return getRuleContexts(ArithmeticContext.class);
		}
		public ArithmeticContext arithmetic(int i) {
			return getRuleContext(ArithmeticContext.class,i);
		}
		public TerminalNode OP_COMPARE() { return getToken(SimpleLanguageParser.OP_COMPARE, 0); }
		public Logic_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logic_factor; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitLogic_factor(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Logic_factorContext logic_factor() throws RecognitionException {
		Logic_factorContext _localctx = new Logic_factorContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_logic_factor);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(128);
			arithmetic();
			setState(131);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				{
				setState(129);
				match(OP_COMPARE);
				setState(130);
				arithmetic();
				}
				break;
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
	public static class ArithmeticContext extends ParserRuleContext {
		public List<TermContext> term() {
			return getRuleContexts(TermContext.class);
		}
		public TermContext term(int i) {
			return getRuleContext(TermContext.class,i);
		}
		public List<TerminalNode> OP_ADD() { return getTokens(SimpleLanguageParser.OP_ADD); }
		public TerminalNode OP_ADD(int i) {
			return getToken(SimpleLanguageParser.OP_ADD, i);
		}
		public ArithmeticContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitArithmetic(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticContext arithmetic() throws RecognitionException {
		ArithmeticContext _localctx = new ArithmeticContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_arithmetic);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(133);
			term();
			setState(138);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(134);
					match(OP_ADD);
					setState(135);
					term();
					}
					} 
				}
				setState(140);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
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
	public static class TermContext extends ParserRuleContext {
		public List<FactorContext> factor() {
			return getRuleContexts(FactorContext.class);
		}
		public FactorContext factor(int i) {
			return getRuleContext(FactorContext.class,i);
		}
		public List<TerminalNode> OP_MUL() { return getTokens(SimpleLanguageParser.OP_MUL); }
		public TerminalNode OP_MUL(int i) {
			return getToken(SimpleLanguageParser.OP_MUL, i);
		}
		public TermContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_term; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitTerm(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TermContext term() throws RecognitionException {
		TermContext _localctx = new TermContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_term);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(141);
			factor();
			setState(146);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(142);
					match(OP_MUL);
					setState(143);
					factor();
					}
					} 
				}
				setState(148);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
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
	public static class FactorContext extends ParserRuleContext {
		public FactorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_factor; }
	 
		public FactorContext() { }
		public void copyFrom(FactorContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringLiteralContext extends FactorContext {
		public TerminalNode STRING_LITERAL() { return getToken(SimpleLanguageParser.STRING_LITERAL, 0); }
		public StringLiteralContext(FactorContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitStringLiteral(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumericLiteralContext extends FactorContext {
		public TerminalNode NUMERIC_LITERAL() { return getToken(SimpleLanguageParser.NUMERIC_LITERAL, 0); }
		public NumericLiteralContext(FactorContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitNumericLiteral(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParenExpressionContext extends FactorContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ParenExpressionContext(FactorContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitParenExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NameAccessContext extends FactorContext {
		public TerminalNode IDENTIFIER() { return getToken(SimpleLanguageParser.IDENTIFIER, 0); }
		public List<Member_expressionContext> member_expression() {
			return getRuleContexts(Member_expressionContext.class);
		}
		public Member_expressionContext member_expression(int i) {
			return getRuleContext(Member_expressionContext.class,i);
		}
		public NameAccessContext(FactorContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitNameAccess(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FactorContext factor() throws RecognitionException {
		FactorContext _localctx = new FactorContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_factor);
		try {
			int _alt;
			setState(162);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				_localctx = new NameAccessContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(149);
				match(IDENTIFIER);
				setState(153);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(150);
						member_expression();
						}
						} 
					}
					setState(155);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
				}
				}
				break;
			case STRING_LITERAL:
				_localctx = new StringLiteralContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(156);
				match(STRING_LITERAL);
				}
				break;
			case NUMERIC_LITERAL:
				_localctx = new NumericLiteralContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(157);
				match(NUMERIC_LITERAL);
				}
				break;
			case T__1:
				_localctx = new ParenExpressionContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(158);
				match(T__1);
				setState(159);
				expression();
				setState(160);
				match(T__3);
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
		public Member_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_member_expression; }
	 
		public Member_expressionContext() { }
		public void copyFrom(Member_expressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberCallContext extends Member_expressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public MemberCallContext(Member_expressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitMemberCall(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberFieldContext extends Member_expressionContext {
		public TerminalNode IDENTIFIER() { return getToken(SimpleLanguageParser.IDENTIFIER, 0); }
		public MemberFieldContext(Member_expressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitMemberField(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberIndexContext extends Member_expressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MemberIndexContext(Member_expressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitMemberIndex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberAssignContext extends Member_expressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MemberAssignContext(Member_expressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SimpleLanguageVisitor ) return ((SimpleLanguageVisitor<? extends T>)visitor).visitMemberAssign(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Member_expressionContext member_expression() throws RecognitionException {
		Member_expressionContext _localctx = new Member_expressionContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_member_expression);
		int _la;
		try {
			setState(184);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
				_localctx = new MemberCallContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(164);
				match(T__1);
				setState(173);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 939524100L) != 0)) {
					{
					setState(165);
					expression();
					setState(170);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==T__2) {
						{
						{
						setState(166);
						match(T__2);
						setState(167);
						expression();
						}
						}
						setState(172);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(175);
				match(T__3);
				}
				break;
			case T__14:
				_localctx = new MemberAssignContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(176);
				match(T__14);
				setState(177);
				expression();
				}
				break;
			case T__15:
				_localctx = new MemberFieldContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(178);
				match(T__15);
				setState(179);
				match(IDENTIFIER);
				}
				break;
			case T__16:
				_localctx = new MemberIndexContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(180);
				match(T__16);
				setState(181);
				expression();
				setState(182);
				match(T__17);
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

	public static final String _serializedATN =
		"\u0004\u0001\u001d\u00bb\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0001\u0000\u0001"+
		"\u0000\u0005\u0000\'\b\u0000\n\u0000\f\u0000*\t\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0005\u00014\b\u0001\n\u0001\f\u00017\t\u0001\u0003\u00019\b\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0005\u0002"+
		"@\b\u0002\n\u0002\f\u0002C\t\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0003\u0003N\b\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\ti\b"+
		"\t\u0001\n\u0001\n\u0003\nm\b\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0005\u000bt\b\u000b\n\u000b\f\u000bw\t\u000b\u0001\f\u0001"+
		"\f\u0001\f\u0005\f|\b\f\n\f\f\f\u007f\t\f\u0001\r\u0001\r\u0001\r\u0003"+
		"\r\u0084\b\r\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u0089\b\u000e"+
		"\n\u000e\f\u000e\u008c\t\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0005"+
		"\u000f\u0091\b\u000f\n\u000f\f\u000f\u0094\t\u000f\u0001\u0010\u0001\u0010"+
		"\u0005\u0010\u0098\b\u0010\n\u0010\f\u0010\u009b\t\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0003\u0010\u00a3"+
		"\b\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0005\u0011\u00a9"+
		"\b\u0011\n\u0011\f\u0011\u00ac\t\u0011\u0003\u0011\u00ae\b\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u00b9\b\u0011\u0001\u0011\u0000"+
		"\u0000\u0012\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016"+
		"\u0018\u001a\u001c\u001e \"\u0000\u0000\u00c2\u0000$\u0001\u0000\u0000"+
		"\u0000\u0002-\u0001\u0000\u0000\u0000\u0004=\u0001\u0000\u0000\u0000\u0006"+
		"M\u0001\u0000\u0000\u0000\bO\u0001\u0000\u0000\u0000\nR\u0001\u0000\u0000"+
		"\u0000\fU\u0001\u0000\u0000\u0000\u000eX\u0001\u0000\u0000\u0000\u0010"+
		"[\u0001\u0000\u0000\u0000\u0012a\u0001\u0000\u0000\u0000\u0014j\u0001"+
		"\u0000\u0000\u0000\u0016p\u0001\u0000\u0000\u0000\u0018x\u0001\u0000\u0000"+
		"\u0000\u001a\u0080\u0001\u0000\u0000\u0000\u001c\u0085\u0001\u0000\u0000"+
		"\u0000\u001e\u008d\u0001\u0000\u0000\u0000 \u00a2\u0001\u0000\u0000\u0000"+
		"\"\u00b8\u0001\u0000\u0000\u0000$(\u0003\u0002\u0001\u0000%\'\u0003\u0002"+
		"\u0001\u0000&%\u0001\u0000\u0000\u0000\'*\u0001\u0000\u0000\u0000(&\u0001"+
		"\u0000\u0000\u0000()\u0001\u0000\u0000\u0000)+\u0001\u0000\u0000\u0000"+
		"*(\u0001\u0000\u0000\u0000+,\u0005\u0000\u0000\u0001,\u0001\u0001\u0000"+
		"\u0000\u0000-.\u0005\u0001\u0000\u0000./\u0005\u001b\u0000\u0000/8\u0005"+
		"\u0002\u0000\u000005\u0005\u001b\u0000\u000012\u0005\u0003\u0000\u0000"+
		"24\u0005\u001b\u0000\u000031\u0001\u0000\u0000\u000047\u0001\u0000\u0000"+
		"\u000053\u0001\u0000\u0000\u000056\u0001\u0000\u0000\u000069\u0001\u0000"+
		"\u0000\u000075\u0001\u0000\u0000\u000080\u0001\u0000\u0000\u000089\u0001"+
		"\u0000\u0000\u00009:\u0001\u0000\u0000\u0000:;\u0005\u0004\u0000\u0000"+
		";<\u0003\u0004\u0002\u0000<\u0003\u0001\u0000\u0000\u0000=A\u0005\u0005"+
		"\u0000\u0000>@\u0003\u0006\u0003\u0000?>\u0001\u0000\u0000\u0000@C\u0001"+
		"\u0000\u0000\u0000A?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000"+
		"BD\u0001\u0000\u0000\u0000CA\u0001\u0000\u0000\u0000DE\u0005\u0006\u0000"+
		"\u0000E\u0005\u0001\u0000\u0000\u0000FN\u0003\u0010\b\u0000GN\u0003\b"+
		"\u0004\u0000HN\u0003\n\u0005\u0000IN\u0003\u0012\t\u0000JN\u0003\u0014"+
		"\n\u0000KN\u0003\f\u0006\u0000LN\u0003\u000e\u0007\u0000MF\u0001\u0000"+
		"\u0000\u0000MG\u0001\u0000\u0000\u0000MH\u0001\u0000\u0000\u0000MI\u0001"+
		"\u0000\u0000\u0000MJ\u0001\u0000\u0000\u0000MK\u0001\u0000\u0000\u0000"+
		"ML\u0001\u0000\u0000\u0000N\u0007\u0001\u0000\u0000\u0000OP\u0005\u0007"+
		"\u0000\u0000PQ\u0005\b\u0000\u0000Q\t\u0001\u0000\u0000\u0000RS\u0005"+
		"\t\u0000\u0000ST\u0005\b\u0000\u0000T\u000b\u0001\u0000\u0000\u0000UV"+
		"\u0003\u0016\u000b\u0000VW\u0005\b\u0000\u0000W\r\u0001\u0000\u0000\u0000"+
		"XY\u0005\n\u0000\u0000YZ\u0005\b\u0000\u0000Z\u000f\u0001\u0000\u0000"+
		"\u0000[\\\u0005\u000b\u0000\u0000\\]\u0005\u0002\u0000\u0000]^\u0003\u0016"+
		"\u000b\u0000^_\u0005\u0004\u0000\u0000_`\u0003\u0004\u0002\u0000`\u0011"+
		"\u0001\u0000\u0000\u0000ab\u0005\f\u0000\u0000bc\u0005\u0002\u0000\u0000"+
		"cd\u0003\u0016\u000b\u0000de\u0005\u0004\u0000\u0000eh\u0003\u0004\u0002"+
		"\u0000fg\u0005\r\u0000\u0000gi\u0003\u0004\u0002\u0000hf\u0001\u0000\u0000"+
		"\u0000hi\u0001\u0000\u0000\u0000i\u0013\u0001\u0000\u0000\u0000jl\u0005"+
		"\u000e\u0000\u0000km\u0003\u0016\u000b\u0000lk\u0001\u0000\u0000\u0000"+
		"lm\u0001\u0000\u0000\u0000mn\u0001\u0000\u0000\u0000no\u0005\b\u0000\u0000"+
		"o\u0015\u0001\u0000\u0000\u0000pu\u0003\u0018\f\u0000qr\u0005\u0016\u0000"+
		"\u0000rt\u0003\u0018\f\u0000sq\u0001\u0000\u0000\u0000tw\u0001\u0000\u0000"+
		"\u0000us\u0001\u0000\u0000\u0000uv\u0001\u0000\u0000\u0000v\u0017\u0001"+
		"\u0000\u0000\u0000wu\u0001\u0000\u0000\u0000x}\u0003\u001a\r\u0000yz\u0005"+
		"\u0017\u0000\u0000z|\u0003\u001a\r\u0000{y\u0001\u0000\u0000\u0000|\u007f"+
		"\u0001\u0000\u0000\u0000}{\u0001\u0000\u0000\u0000}~\u0001\u0000\u0000"+
		"\u0000~\u0019\u0001\u0000\u0000\u0000\u007f}\u0001\u0000\u0000\u0000\u0080"+
		"\u0083\u0003\u001c\u000e\u0000\u0081\u0082\u0005\u0018\u0000\u0000\u0082"+
		"\u0084\u0003\u001c\u000e\u0000\u0083\u0081\u0001\u0000\u0000\u0000\u0083"+
		"\u0084\u0001\u0000\u0000\u0000\u0084\u001b\u0001\u0000\u0000\u0000\u0085"+
		"\u008a\u0003\u001e\u000f\u0000\u0086\u0087\u0005\u0019\u0000\u0000\u0087"+
		"\u0089\u0003\u001e\u000f\u0000\u0088\u0086\u0001\u0000\u0000\u0000\u0089"+
		"\u008c\u0001\u0000\u0000\u0000\u008a\u0088\u0001\u0000\u0000\u0000\u008a"+
		"\u008b\u0001\u0000\u0000\u0000\u008b\u001d\u0001\u0000\u0000\u0000\u008c"+
		"\u008a\u0001\u0000\u0000\u0000\u008d\u0092\u0003 \u0010\u0000\u008e\u008f"+
		"\u0005\u001a\u0000\u0000\u008f\u0091\u0003 \u0010\u0000\u0090\u008e\u0001"+
		"\u0000\u0000\u0000\u0091\u0094\u0001\u0000\u0000\u0000\u0092\u0090\u0001"+
		"\u0000\u0000\u0000\u0092\u0093\u0001\u0000\u0000\u0000\u0093\u001f\u0001"+
		"\u0000\u0000\u0000\u0094\u0092\u0001\u0000\u0000\u0000\u0095\u0099\u0005"+
		"\u001b\u0000\u0000\u0096\u0098\u0003\"\u0011\u0000\u0097\u0096\u0001\u0000"+
		"\u0000\u0000\u0098\u009b\u0001\u0000\u0000\u0000\u0099\u0097\u0001\u0000"+
		"\u0000\u0000\u0099\u009a\u0001\u0000\u0000\u0000\u009a\u00a3\u0001\u0000"+
		"\u0000\u0000\u009b\u0099\u0001\u0000\u0000\u0000\u009c\u00a3\u0005\u001c"+
		"\u0000\u0000\u009d\u00a3\u0005\u001d\u0000\u0000\u009e\u009f\u0005\u0002"+
		"\u0000\u0000\u009f\u00a0\u0003\u0016\u000b\u0000\u00a0\u00a1\u0005\u0004"+
		"\u0000\u0000\u00a1\u00a3\u0001\u0000\u0000\u0000\u00a2\u0095\u0001\u0000"+
		"\u0000\u0000\u00a2\u009c\u0001\u0000\u0000\u0000\u00a2\u009d\u0001\u0000"+
		"\u0000\u0000\u00a2\u009e\u0001\u0000\u0000\u0000\u00a3!\u0001\u0000\u0000"+
		"\u0000\u00a4\u00ad\u0005\u0002\u0000\u0000\u00a5\u00aa\u0003\u0016\u000b"+
		"\u0000\u00a6\u00a7\u0005\u0003\u0000\u0000\u00a7\u00a9\u0003\u0016\u000b"+
		"\u0000\u00a8\u00a6\u0001\u0000\u0000\u0000\u00a9\u00ac\u0001\u0000\u0000"+
		"\u0000\u00aa\u00a8\u0001\u0000\u0000\u0000\u00aa\u00ab\u0001\u0000\u0000"+
		"\u0000\u00ab\u00ae\u0001\u0000\u0000\u0000\u00ac\u00aa\u0001\u0000\u0000"+
		"\u0000\u00ad\u00a5\u0001\u0000\u0000\u0000\u00ad\u00ae\u0001\u0000\u0000"+
		"\u0000\u00ae\u00af\u0001\u0000\u0000\u0000\u00af\u00b9\u0005\u0004\u0000"+
		"\u0000\u00b0\u00b1\u0005\u000f\u0000\u0000\u00b1\u00b9\u0003\u0016\u000b"+
		"\u0000\u00b2\u00b3\u0005\u0010\u0000\u0000\u00b3\u00b9\u0005\u001b\u0000"+
		"\u0000\u00b4\u00b5\u0005\u0011\u0000\u0000\u00b5\u00b6\u0003\u0016\u000b"+
		"\u0000\u00b6\u00b7\u0005\u0012\u0000\u0000\u00b7\u00b9\u0001\u0000\u0000"+
		"\u0000\u00b8\u00a4\u0001\u0000\u0000\u0000\u00b8\u00b0\u0001\u0000\u0000"+
		"\u0000\u00b8\u00b2\u0001\u0000\u0000\u0000\u00b8\u00b4\u0001\u0000\u0000"+
		"\u0000\u00b9#\u0001\u0000\u0000\u0000\u0011(58AMhlu}\u0083\u008a\u0092"+
		"\u0099\u00a2\u00aa\u00ad\u00b8";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
