// Generated from SExpr.g4 by ANTLR 4.7.2

package com.oracle.truffle.wasm.test.util.sexpr.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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

import com.oracle.truffle.wasm.test.util.sexpr.LiteralType;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class SExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		STRING=1, WHITESPACE=2, INTEGER=3, FLOATING=4, FLOATING_BODY=5, SYMBOL=6, 
		LPAREN=7, RPAREN=8, COMMENT=9;
	public static final int
		RULE_root = 0, RULE_sexpr = 1, RULE_list = 2, RULE_atom = 3;
	private static String[] makeRuleNames() {
		return new String[] {
			"root", "sexpr", "list", "atom"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "STRING", "WHITESPACE", "INTEGER", "FLOATING", "FLOATING_BODY", 
			"SYMBOL", "LPAREN", "RPAREN", "COMMENT"
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
	public String getGrammarFileName() { return "SExpr.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }


	// Code added manually to parse S-expressions {-

	private SExprNodeFactory factory;
	private String source;

	public static List<SExprNode> parseSexpressions(String source) {
	    SExprLexer lexer = new SExprLexer(CharStreams.fromString(source));
	    SExprParser parser = new SExprParser(new CommonTokenStream(lexer));
	    parser.factory = new SExprNodeFactory();
	    parser.source = source;
	    parser.root();
	    return parser.factory.getAllExpressions();
	}

	// -} Code added manually to parse S-expressions

	public SExprParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class RootContext extends ParserRuleContext {
		public SexprContext sexpr;
		public TerminalNode EOF() { return getToken(SExprParser.EOF, 0); }
		public List<SexprContext> sexpr() {
			return getRuleContexts(SexprContext.class);
		}
		public SexprContext sexpr(int i) {
			return getRuleContext(SexprContext.class,i);
		}
		public RootContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_root; }
	}

	public final RootContext root() throws RecognitionException {
		RootContext _localctx = new RootContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_root);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(13);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << INTEGER) | (1L << FLOATING) | (1L << SYMBOL) | (1L << LPAREN))) != 0)) {
				{
				{
				setState(8);
				((RootContext)_localctx).sexpr = sexpr();
				 factory.addNode(((RootContext)_localctx).sexpr.result); 
				}
				}
				setState(15);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(16);
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

	public static class SexprContext extends ParserRuleContext {
		public SExprNode result;
		public AtomContext atom;
		public ListContext list;
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public ListContext list() {
			return getRuleContext(ListContext.class,0);
		}
		public SexprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sexpr; }
	}

	public final SexprContext sexpr() throws RecognitionException {
		SexprContext _localctx = new SexprContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_sexpr);
		try {
			setState(24);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case INTEGER:
			case FLOATING:
			case SYMBOL:
				enterOuterAlt(_localctx, 1);
				{
				setState(18);
				((SexprContext)_localctx).atom = atom();
				 ((SexprContext)_localctx).result =  new SExprAtomNode(((SexprContext)_localctx).atom.result); 
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(21);
				((SexprContext)_localctx).list = list();
				 ((SexprContext)_localctx).result =  new SExprListNode(((SexprContext)_localctx).list.result); 
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

	public static class ListContext extends ParserRuleContext {
		public List<SExprNode> result;
		public SexprContext sexpr;
		public TerminalNode LPAREN() { return getToken(SExprParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SExprParser.RPAREN, 0); }
		public List<SexprContext> sexpr() {
			return getRuleContexts(SexprContext.class);
		}
		public SexprContext sexpr(int i) {
			return getRuleContext(SexprContext.class,i);
		}
		public ListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_list; }
	}

	public final ListContext list() throws RecognitionException {
		ListContext _localctx = new ListContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(26);
			match(LPAREN);
			 List<SExprNode> exprs = new ArrayList<SExprNode>(); 
			setState(33);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << INTEGER) | (1L << FLOATING) | (1L << SYMBOL) | (1L << LPAREN))) != 0)) {
				{
				{
				setState(28);
				((ListContext)_localctx).sexpr = sexpr();
				 exprs.add(((ListContext)_localctx).sexpr.result); 
				}
				}
				setState(35);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(36);
			match(RPAREN);
			 ((ListContext)_localctx).result =  exprs; 
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

	public static class AtomContext extends ParserRuleContext {
		public SExprLiteralNode result;
		public Token STRING;
		public Token SYMBOL;
		public Token INTEGER;
		public Token FLOATING;
		public TerminalNode STRING() { return getToken(SExprParser.STRING, 0); }
		public TerminalNode SYMBOL() { return getToken(SExprParser.SYMBOL, 0); }
		public TerminalNode INTEGER() { return getToken(SExprParser.INTEGER, 0); }
		public TerminalNode FLOATING() { return getToken(SExprParser.FLOATING, 0); }
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_atom);
		try {
			setState(47);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(39);
				((AtomContext)_localctx).STRING = match(STRING);
				 ((AtomContext)_localctx).result =  new SExprLiteralNode(((AtomContext)_localctx).STRING.getText(), LiteralType.STRING); 
				}
				break;
			case SYMBOL:
				enterOuterAlt(_localctx, 2);
				{
				setState(41);
				((AtomContext)_localctx).SYMBOL = match(SYMBOL);
				 ((AtomContext)_localctx).result =  new SExprLiteralNode(((AtomContext)_localctx).SYMBOL.getText(), LiteralType.SYMBOL); 
				}
				break;
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(43);
				((AtomContext)_localctx).INTEGER = match(INTEGER);
				 ((AtomContext)_localctx).result =  new SExprLiteralNode(((AtomContext)_localctx).INTEGER.getText(), LiteralType.INTEGER); 
				}
				break;
			case FLOATING:
				enterOuterAlt(_localctx, 4);
				{
				setState(45);
				((AtomContext)_localctx).FLOATING = match(FLOATING);
				 ((AtomContext)_localctx).result =  new SExprLiteralNode(((AtomContext)_localctx).FLOATING.getText(), LiteralType.FLOATING); 
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\13\64\4\2\t\2\4\3"+
		"\t\3\4\4\t\4\4\5\t\5\3\2\3\2\3\2\7\2\16\n\2\f\2\16\2\21\13\2\3\2\3\2\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\5\3\33\n\3\3\4\3\4\3\4\3\4\3\4\7\4\"\n\4\f\4\16"+
		"\4%\13\4\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\62\n\5\3\5\2"+
		"\2\6\2\4\6\b\2\2\2\65\2\17\3\2\2\2\4\32\3\2\2\2\6\34\3\2\2\2\b\61\3\2"+
		"\2\2\n\13\5\4\3\2\13\f\b\2\1\2\f\16\3\2\2\2\r\n\3\2\2\2\16\21\3\2\2\2"+
		"\17\r\3\2\2\2\17\20\3\2\2\2\20\22\3\2\2\2\21\17\3\2\2\2\22\23\7\2\2\3"+
		"\23\3\3\2\2\2\24\25\5\b\5\2\25\26\b\3\1\2\26\33\3\2\2\2\27\30\5\6\4\2"+
		"\30\31\b\3\1\2\31\33\3\2\2\2\32\24\3\2\2\2\32\27\3\2\2\2\33\5\3\2\2\2"+
		"\34\35\7\t\2\2\35#\b\4\1\2\36\37\5\4\3\2\37 \b\4\1\2 \"\3\2\2\2!\36\3"+
		"\2\2\2\"%\3\2\2\2#!\3\2\2\2#$\3\2\2\2$&\3\2\2\2%#\3\2\2\2&\'\7\n\2\2\'"+
		"(\b\4\1\2(\7\3\2\2\2)*\7\3\2\2*\62\b\5\1\2+,\7\b\2\2,\62\b\5\1\2-.\7\5"+
		"\2\2.\62\b\5\1\2/\60\7\6\2\2\60\62\b\5\1\2\61)\3\2\2\2\61+\3\2\2\2\61"+
		"-\3\2\2\2\61/\3\2\2\2\62\t\3\2\2\2\6\17\32#\61";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}