// Generated from SExpr.g4 by ANTLR 4.7.2

package com.oracle.truffle.wasm.test.util.sexpr.parser;

import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprFloatingLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprIntegerLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprStringLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprSymbolLiteralNode;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class SExprLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		STRING=1, WHITESPACE=2, INTEGER=3, FLOATING=4, SYMBOL=5, LPAREN=6, RPAREN=7;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"STRING", "WHITESPACE", "INTEGER", "FLOATING", "SYMBOL", "LPAREN", "RPAREN", 
			"SYMBOL_START", "DIGIT"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "STRING", "WHITESPACE", "INTEGER", "FLOATING", "SYMBOL", "LPAREN", 
			"RPAREN"
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


	public SExprLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "SExpr.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\tP\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\3\2\3\2"+
		"\3\2\3\2\7\2\32\n\2\f\2\16\2\35\13\2\3\2\3\2\3\3\6\3\"\n\3\r\3\16\3#\3"+
		"\3\3\3\3\4\5\4)\n\4\3\4\6\4,\n\4\r\4\16\4-\3\5\5\5\61\n\5\3\5\6\5\64\n"+
		"\5\r\5\16\5\65\3\5\3\5\6\5:\n\5\r\5\16\5;\5\5>\n\5\3\6\3\6\3\6\7\6C\n"+
		"\6\f\6\16\6F\13\6\3\7\3\7\3\b\3\b\3\t\5\tM\n\t\3\n\3\n\2\2\13\3\3\5\4"+
		"\7\5\t\6\13\7\r\b\17\t\21\2\23\2\3\2\6\4\2$$^^\5\2\13\f\17\17\"\"\4\2"+
		"--//\7\2,-/\61C\\aac|\2X\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2"+
		"\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\3\25\3\2\2\2\5!\3\2\2\2\7(\3"+
		"\2\2\2\t\60\3\2\2\2\13?\3\2\2\2\rG\3\2\2\2\17I\3\2\2\2\21L\3\2\2\2\23"+
		"N\3\2\2\2\25\33\7$\2\2\26\27\7^\2\2\27\32\13\2\2\2\30\32\n\2\2\2\31\26"+
		"\3\2\2\2\31\30\3\2\2\2\32\35\3\2\2\2\33\31\3\2\2\2\33\34\3\2\2\2\34\36"+
		"\3\2\2\2\35\33\3\2\2\2\36\37\7$\2\2\37\4\3\2\2\2 \"\t\3\2\2! \3\2\2\2"+
		"\"#\3\2\2\2#!\3\2\2\2#$\3\2\2\2$%\3\2\2\2%&\b\3\2\2&\6\3\2\2\2\')\t\4"+
		"\2\2(\'\3\2\2\2()\3\2\2\2)+\3\2\2\2*,\5\23\n\2+*\3\2\2\2,-\3\2\2\2-+\3"+
		"\2\2\2-.\3\2\2\2.\b\3\2\2\2/\61\t\4\2\2\60/\3\2\2\2\60\61\3\2\2\2\61\63"+
		"\3\2\2\2\62\64\5\23\n\2\63\62\3\2\2\2\64\65\3\2\2\2\65\63\3\2\2\2\65\66"+
		"\3\2\2\2\66=\3\2\2\2\679\7\60\2\28:\5\23\n\298\3\2\2\2:;\3\2\2\2;9\3\2"+
		"\2\2;<\3\2\2\2<>\3\2\2\2=\67\3\2\2\2=>\3\2\2\2>\n\3\2\2\2?D\5\21\t\2@"+
		"C\5\21\t\2AC\5\23\n\2B@\3\2\2\2BA\3\2\2\2CF\3\2\2\2DB\3\2\2\2DE\3\2\2"+
		"\2E\f\3\2\2\2FD\3\2\2\2GH\7*\2\2H\16\3\2\2\2IJ\7+\2\2J\20\3\2\2\2KM\t"+
		"\5\2\2LK\3\2\2\2M\22\3\2\2\2NO\4\62;\2O\24\3\2\2\2\17\2\31\33#(-\60\65"+
		";=BDL\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}