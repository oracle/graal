// Generated from SExpr.g4 by ANTLR 4.7.2

package com.oracle.truffle.wasm.test.util.sexpr.parser;

import com.oracle.truffle.wasm.test.util.sexpr.LiteralType;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprAtomNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprListNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprLiteralNode;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;

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
		STRING=1, WHITESPACE=2, INTEGER=3, FLOATING=4, FLOATING_BODY=5, SYMBOL=6, 
		LPAREN=7, RPAREN=8, COMMENT=9;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"STRING", "WHITESPACE", "INTEGER", "FLOATING", "FLOATING_BODY", "SYMBOL", 
			"LPAREN", "RPAREN", "SYMBOL_START", "SIGN", "DIGIT", "HEXDIGIT", "NUMBER", 
			"HEXNUMBER", "FRAC", "HEXFRAC", "FLOAT", "HEXFLOAT", "COMMENT"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\13\u00e4\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\3\2\3\2\3\2\3\2\7\2.\n\2\f\2\16\2\61\13\2\3"+
		"\2\3\2\3\3\6\3\66\n\3\r\3\16\3\67\3\3\3\3\3\4\3\4\3\4\3\4\3\4\3\4\3\4"+
		"\3\4\3\4\5\4E\n\4\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6"+
		"\3\6\3\6\3\6\3\6\3\6\3\6\5\6Z\n\6\3\7\3\7\3\7\7\7_\n\7\f\7\16\7b\13\7"+
		"\3\b\3\b\3\t\3\t\3\n\5\ni\n\n\3\13\5\13l\n\13\3\f\3\f\3\r\3\r\5\rr\n\r"+
		"\3\16\6\16u\n\16\r\16\16\16v\3\16\6\16z\n\16\r\16\16\16{\3\16\3\16\6\16"+
		"\u0080\n\16\r\16\16\16\u0081\5\16\u0084\n\16\3\17\6\17\u0087\n\17\r\17"+
		"\16\17\u0088\3\17\6\17\u008c\n\17\r\17\16\17\u008d\3\17\3\17\6\17\u0092"+
		"\n\17\r\17\16\17\u0093\5\17\u0096\n\17\3\20\3\20\3\20\3\20\3\20\3\20\3"+
		"\20\3\20\3\20\5\20\u00a1\n\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\5\21\u00ac\n\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\22\5\22\u00be\n\22\3\23\3\23\3\23\3\23\3\23"+
		"\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23"+
		"\3\23\3\23\3\23\3\23\3\23\5\23\u00d8\n\23\3\24\3\24\3\24\3\24\7\24\u00de"+
		"\n\24\f\24\16\24\u00e1\13\24\3\24\3\24\2\2\25\3\3\5\4\7\5\t\6\13\7\r\b"+
		"\17\t\21\n\23\2\25\2\27\2\31\2\33\2\35\2\37\2!\2#\2%\2\'\13\3\2\n\4\2"+
		"$$^^\5\2\13\f\17\17\"\"\7\2,-/\61C\\aac|\4\2--//\4\2CHch\4\2GGgg\4\2R"+
		"Rrr\4\2\f\f\17\17\2\u00f6\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2"+
		"\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\'\3\2\2\2\3"+
		")\3\2\2\2\5\65\3\2\2\2\7D\3\2\2\2\tF\3\2\2\2\13Y\3\2\2\2\r[\3\2\2\2\17"+
		"c\3\2\2\2\21e\3\2\2\2\23h\3\2\2\2\25k\3\2\2\2\27m\3\2\2\2\31q\3\2\2\2"+
		"\33\u0083\3\2\2\2\35\u0095\3\2\2\2\37\u00a0\3\2\2\2!\u00ab\3\2\2\2#\u00bd"+
		"\3\2\2\2%\u00d7\3\2\2\2\'\u00d9\3\2\2\2)/\7$\2\2*+\7^\2\2+.\13\2\2\2,"+
		".\n\2\2\2-*\3\2\2\2-,\3\2\2\2.\61\3\2\2\2/-\3\2\2\2/\60\3\2\2\2\60\62"+
		"\3\2\2\2\61/\3\2\2\2\62\63\7$\2\2\63\4\3\2\2\2\64\66\t\3\2\2\65\64\3\2"+
		"\2\2\66\67\3\2\2\2\67\65\3\2\2\2\678\3\2\2\289\3\2\2\29:\b\3\2\2:\6\3"+
		"\2\2\2;<\5\25\13\2<=\5\33\16\2=E\3\2\2\2>?\5\25\13\2?@\7\62\2\2@A\7z\2"+
		"\2AB\3\2\2\2BC\5\35\17\2CE\3\2\2\2D;\3\2\2\2D>\3\2\2\2E\b\3\2\2\2FG\5"+
		"\25\13\2GH\5\13\6\2H\n\3\2\2\2IZ\5#\22\2JZ\5%\23\2KL\7k\2\2LM\7p\2\2M"+
		"Z\7h\2\2NO\7p\2\2OP\7c\2\2PZ\7p\2\2QR\7p\2\2RS\7c\2\2ST\7p\2\2TU\7<\2"+
		"\2UV\7\62\2\2VW\7z\2\2WX\3\2\2\2XZ\5\35\17\2YI\3\2\2\2YJ\3\2\2\2YK\3\2"+
		"\2\2YN\3\2\2\2YQ\3\2\2\2Z\f\3\2\2\2[`\5\23\n\2\\_\5\23\n\2]_\5\27\f\2"+
		"^\\\3\2\2\2^]\3\2\2\2_b\3\2\2\2`^\3\2\2\2`a\3\2\2\2a\16\3\2\2\2b`\3\2"+
		"\2\2cd\7*\2\2d\20\3\2\2\2ef\7+\2\2f\22\3\2\2\2gi\t\4\2\2hg\3\2\2\2i\24"+
		"\3\2\2\2jl\t\5\2\2kj\3\2\2\2kl\3\2\2\2l\26\3\2\2\2mn\4\62;\2n\30\3\2\2"+
		"\2or\5\27\f\2pr\t\6\2\2qo\3\2\2\2qp\3\2\2\2r\32\3\2\2\2su\5\27\f\2ts\3"+
		"\2\2\2uv\3\2\2\2vt\3\2\2\2vw\3\2\2\2w\u0084\3\2\2\2xz\5\27\f\2yx\3\2\2"+
		"\2z{\3\2\2\2{y\3\2\2\2{|\3\2\2\2|}\3\2\2\2}\177\7a\2\2~\u0080\5\27\f\2"+
		"\177~\3\2\2\2\u0080\u0081\3\2\2\2\u0081\177\3\2\2\2\u0081\u0082\3\2\2"+
		"\2\u0082\u0084\3\2\2\2\u0083t\3\2\2\2\u0083y\3\2\2\2\u0084\34\3\2\2\2"+
		"\u0085\u0087\5\31\r\2\u0086\u0085\3\2\2\2\u0087\u0088\3\2\2\2\u0088\u0086"+
		"\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u0096\3\2\2\2\u008a\u008c\5\31\r\2"+
		"\u008b\u008a\3\2\2\2\u008c\u008d\3\2\2\2\u008d\u008b\3\2\2\2\u008d\u008e"+
		"\3\2\2\2\u008e\u008f\3\2\2\2\u008f\u0091\7a\2\2\u0090\u0092\5\31\r\2\u0091"+
		"\u0090\3\2\2\2\u0092\u0093\3\2\2\2\u0093\u0091\3\2\2\2\u0093\u0094\3\2"+
		"\2\2\u0094\u0096\3\2\2\2\u0095\u0086\3\2\2\2\u0095\u008b\3\2\2\2\u0096"+
		"\36\3\2\2\2\u0097\u00a1\3\2\2\2\u0098\u0099\5\27\f\2\u0099\u009a\5\37"+
		"\20\2\u009a\u00a1\3\2\2\2\u009b\u009c\5\27\f\2\u009c\u009d\7a\2\2\u009d"+
		"\u009e\5\27\f\2\u009e\u009f\5\37\20\2\u009f\u00a1\3\2\2\2\u00a0\u0097"+
		"\3\2\2\2\u00a0\u0098\3\2\2\2\u00a0\u009b\3\2\2\2\u00a1 \3\2\2\2\u00a2"+
		"\u00ac\3\2\2\2\u00a3\u00a4\5\31\r\2\u00a4\u00a5\5!\21\2\u00a5\u00ac\3"+
		"\2\2\2\u00a6\u00a7\5\31\r\2\u00a7\u00a8\7a\2\2\u00a8\u00a9\5\31\r\2\u00a9"+
		"\u00aa\5!\21\2\u00aa\u00ac\3\2\2\2\u00ab\u00a2\3\2\2\2\u00ab\u00a3\3\2"+
		"\2\2\u00ab\u00a6\3\2\2\2\u00ac\"\3\2\2\2\u00ad\u00ae\5\33\16\2\u00ae\u00af"+
		"\7\60\2\2\u00af\u00b0\5\37\20\2\u00b0\u00be\3\2\2\2\u00b1\u00b2\5\33\16"+
		"\2\u00b2\u00b3\t\7\2\2\u00b3\u00b4\5\25\13\2\u00b4\u00b5\5\33\16\2\u00b5"+
		"\u00be\3\2\2\2\u00b6\u00b7\5\33\16\2\u00b7\u00b8\7\60\2\2\u00b8\u00b9"+
		"\5\37\20\2\u00b9\u00ba\t\7\2\2\u00ba\u00bb\5\25\13\2\u00bb\u00bc\5\33"+
		"\16\2\u00bc\u00be\3\2\2\2\u00bd\u00ad\3\2\2\2\u00bd\u00b1\3\2\2\2\u00bd"+
		"\u00b6\3\2\2\2\u00be$\3\2\2\2\u00bf\u00c0\7\62\2\2\u00c0\u00c1\7z\2\2"+
		"\u00c1\u00c2\3\2\2\2\u00c2\u00c3\5\35\17\2\u00c3\u00c4\5!\21\2\u00c4\u00d8"+
		"\3\2\2\2\u00c5\u00c6\7\62\2\2\u00c6\u00c7\7z\2\2\u00c7\u00c8\3\2\2\2\u00c8"+
		"\u00c9\5\35\17\2\u00c9\u00ca\t\b\2\2\u00ca\u00cb\5\25\13\2\u00cb\u00cc"+
		"\5\33\16\2\u00cc\u00d8\3\2\2\2\u00cd\u00ce\7\62\2\2\u00ce\u00cf\7z\2\2"+
		"\u00cf\u00d0\3\2\2\2\u00d0\u00d1\5\35\17\2\u00d1\u00d2\7\60\2\2\u00d2"+
		"\u00d3\5!\21\2\u00d3\u00d4\t\b\2\2\u00d4\u00d5\5\25\13\2\u00d5\u00d6\5"+
		"\33\16\2\u00d6\u00d8\3\2\2\2\u00d7\u00bf\3\2\2\2\u00d7\u00c5\3\2\2\2\u00d7"+
		"\u00cd\3\2\2\2\u00d8&\3\2\2\2\u00d9\u00da\7=\2\2\u00da\u00db\7=\2\2\u00db"+
		"\u00df\3\2\2\2\u00dc\u00de\n\t\2\2\u00dd\u00dc\3\2\2\2\u00de\u00e1\3\2"+
		"\2\2\u00df\u00dd\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0\u00e2\3\2\2\2\u00e1"+
		"\u00df\3\2\2\2\u00e2\u00e3\b\24\2\2\u00e3(\3\2\2\2\32\2-/\67DY^`hkqv{"+
		"\u0081\u0083\u0088\u008d\u0093\u0095\u00a0\u00ab\u00bd\u00d7\u00df\3\b"+
		"\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}