// Generated from SExpr.g4 by ANTLR 4.7.2

package com.oracle.truffle.wasm.test.util.sexpr.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

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
			"NUMBER_", "HEXNUMBER", "HEXNUMBER_", "FRAC", "HEXFRAC", "FLOAT", "HEXFLOAT", 
			"COMMENT"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\13\u00e0\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\3\2\3\2\3\2\3\2\7\2\62\n"+
		"\2\f\2\16\2\65\13\2\3\2\3\2\3\3\6\3:\n\3\r\3\16\3;\3\3\3\3\3\4\3\4\3\4"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\5\4I\n\4\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6"+
		"\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\5\6^\n\6\3\7\3\7\3\7\7\7c\n\7"+
		"\f\7\16\7f\13\7\3\b\3\b\3\t\3\t\3\n\5\nm\n\n\3\13\5\13p\n\13\3\f\3\f\3"+
		"\r\3\r\5\rv\n\r\3\16\6\16y\n\16\r\16\16\16z\3\16\3\16\3\17\3\17\5\17\u0081"+
		"\n\17\3\17\5\17\u0084\n\17\3\20\6\20\u0087\n\20\r\20\16\20\u0088\3\20"+
		"\3\20\3\21\3\21\5\21\u008f\n\21\3\21\5\21\u0092\n\21\3\22\3\22\3\22\3"+
		"\22\3\22\3\22\3\22\3\22\3\22\5\22\u009d\n\22\3\23\3\23\3\23\3\23\3\23"+
		"\3\23\3\23\3\23\3\23\5\23\u00a8\n\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\5\24\u00ba\n\24\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\5\25\u00d4\n\25\3\26\3\26\3\26"+
		"\3\26\7\26\u00da\n\26\f\26\16\26\u00dd\13\26\3\26\3\26\2\2\27\3\3\5\4"+
		"\7\5\t\6\13\7\r\b\17\t\21\n\23\2\25\2\27\2\31\2\33\2\35\2\37\2!\2#\2%"+
		"\2\'\2)\2+\13\3\2\n\4\2$$^^\5\2\13\f\17\17\"\"\f\2##%),-/\61<<>\\^^`|"+
		"~~\u0080\u0080\4\2--//\4\2CHch\4\2GGgg\4\2RRrr\4\2\f\f\17\17\2\u00ee\2"+
		"\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2"+
		"\2\2\17\3\2\2\2\2\21\3\2\2\2\2+\3\2\2\2\3-\3\2\2\2\59\3\2\2\2\7H\3\2\2"+
		"\2\tJ\3\2\2\2\13]\3\2\2\2\r_\3\2\2\2\17g\3\2\2\2\21i\3\2\2\2\23l\3\2\2"+
		"\2\25o\3\2\2\2\27q\3\2\2\2\31u\3\2\2\2\33x\3\2\2\2\35\u0083\3\2\2\2\37"+
		"\u0086\3\2\2\2!\u0091\3\2\2\2#\u009c\3\2\2\2%\u00a7\3\2\2\2\'\u00b9\3"+
		"\2\2\2)\u00d3\3\2\2\2+\u00d5\3\2\2\2-\63\7$\2\2./\7^\2\2/\62\13\2\2\2"+
		"\60\62\n\2\2\2\61.\3\2\2\2\61\60\3\2\2\2\62\65\3\2\2\2\63\61\3\2\2\2\63"+
		"\64\3\2\2\2\64\66\3\2\2\2\65\63\3\2\2\2\66\67\7$\2\2\67\4\3\2\2\28:\t"+
		"\3\2\298\3\2\2\2:;\3\2\2\2;9\3\2\2\2;<\3\2\2\2<=\3\2\2\2=>\b\3\2\2>\6"+
		"\3\2\2\2?@\5\25\13\2@A\5\33\16\2AI\3\2\2\2BC\5\25\13\2CD\7\62\2\2DE\7"+
		"z\2\2EF\3\2\2\2FG\5\37\20\2GI\3\2\2\2H?\3\2\2\2HB\3\2\2\2I\b\3\2\2\2J"+
		"K\5\25\13\2KL\5\13\6\2L\n\3\2\2\2M^\5\'\24\2N^\5)\25\2OP\7k\2\2PQ\7p\2"+
		"\2Q^\7h\2\2RS\7p\2\2ST\7c\2\2T^\7p\2\2UV\7p\2\2VW\7c\2\2WX\7p\2\2XY\7"+
		"<\2\2YZ\7\62\2\2Z[\7z\2\2[\\\3\2\2\2\\^\5\37\20\2]M\3\2\2\2]N\3\2\2\2"+
		"]O\3\2\2\2]R\3\2\2\2]U\3\2\2\2^\f\3\2\2\2_d\5\23\n\2`c\5\23\n\2ac\5\27"+
		"\f\2b`\3\2\2\2ba\3\2\2\2cf\3\2\2\2db\3\2\2\2de\3\2\2\2e\16\3\2\2\2fd\3"+
		"\2\2\2gh\7*\2\2h\20\3\2\2\2ij\7+\2\2j\22\3\2\2\2km\t\4\2\2lk\3\2\2\2m"+
		"\24\3\2\2\2np\t\5\2\2on\3\2\2\2op\3\2\2\2p\26\3\2\2\2qr\4\62;\2r\30\3"+
		"\2\2\2sv\5\27\f\2tv\t\6\2\2us\3\2\2\2ut\3\2\2\2v\32\3\2\2\2wy\5\27\f\2"+
		"xw\3\2\2\2yz\3\2\2\2zx\3\2\2\2z{\3\2\2\2{|\3\2\2\2|}\5\35\17\2}\34\3\2"+
		"\2\2~\u0084\3\2\2\2\177\u0081\7a\2\2\u0080\177\3\2\2\2\u0080\u0081\3\2"+
		"\2\2\u0081\u0082\3\2\2\2\u0082\u0084\5\33\16\2\u0083~\3\2\2\2\u0083\u0080"+
		"\3\2\2\2\u0084\36\3\2\2\2\u0085\u0087\5\31\r\2\u0086\u0085\3\2\2\2\u0087"+
		"\u0088\3\2\2\2\u0088\u0086\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u008a\3\2"+
		"\2\2\u008a\u008b\5!\21\2\u008b \3\2\2\2\u008c\u0092\3\2\2\2\u008d\u008f"+
		"\7a\2\2\u008e\u008d\3\2\2\2\u008e\u008f\3\2\2\2\u008f\u0090\3\2\2\2\u0090"+
		"\u0092\5\37\20\2\u0091\u008c\3\2\2\2\u0091\u008e\3\2\2\2\u0092\"\3\2\2"+
		"\2\u0093\u009d\3\2\2\2\u0094\u0095\5\27\f\2\u0095\u0096\5#\22\2\u0096"+
		"\u009d\3\2\2\2\u0097\u0098\5\27\f\2\u0098\u0099\7a\2\2\u0099\u009a\5\27"+
		"\f\2\u009a\u009b\5#\22\2\u009b\u009d\3\2\2\2\u009c\u0093\3\2\2\2\u009c"+
		"\u0094\3\2\2\2\u009c\u0097\3\2\2\2\u009d$\3\2\2\2\u009e\u00a8\3\2\2\2"+
		"\u009f\u00a0\5\31\r\2\u00a0\u00a1\5%\23\2\u00a1\u00a8\3\2\2\2\u00a2\u00a3"+
		"\5\31\r\2\u00a3\u00a4\7a\2\2\u00a4\u00a5\5\31\r\2\u00a5\u00a6\5%\23\2"+
		"\u00a6\u00a8\3\2\2\2\u00a7\u009e\3\2\2\2\u00a7\u009f\3\2\2\2\u00a7\u00a2"+
		"\3\2\2\2\u00a8&\3\2\2\2\u00a9\u00aa\5\33\16\2\u00aa\u00ab\7\60\2\2\u00ab"+
		"\u00ac\5#\22\2\u00ac\u00ba\3\2\2\2\u00ad\u00ae\5\33\16\2\u00ae\u00af\t"+
		"\7\2\2\u00af\u00b0\5\25\13\2\u00b0\u00b1\5\33\16\2\u00b1\u00ba\3\2\2\2"+
		"\u00b2\u00b3\5\33\16\2\u00b3\u00b4\7\60\2\2\u00b4\u00b5\5#\22\2\u00b5"+
		"\u00b6\t\7\2\2\u00b6\u00b7\5\25\13\2\u00b7\u00b8\5\33\16\2\u00b8\u00ba"+
		"\3\2\2\2\u00b9\u00a9\3\2\2\2\u00b9\u00ad\3\2\2\2\u00b9\u00b2\3\2\2\2\u00ba"+
		"(\3\2\2\2\u00bb\u00bc\7\62\2\2\u00bc\u00bd\7z\2\2\u00bd\u00be\3\2\2\2"+
		"\u00be\u00bf\5\37\20\2\u00bf\u00c0\5%\23\2\u00c0\u00d4\3\2\2\2\u00c1\u00c2"+
		"\7\62\2\2\u00c2\u00c3\7z\2\2\u00c3\u00c4\3\2\2\2\u00c4\u00c5\5\37\20\2"+
		"\u00c5\u00c6\t\b\2\2\u00c6\u00c7\5\25\13\2\u00c7\u00c8\5\33\16\2\u00c8"+
		"\u00d4\3\2\2\2\u00c9\u00ca\7\62\2\2\u00ca\u00cb\7z\2\2\u00cb\u00cc\3\2"+
		"\2\2\u00cc\u00cd\5\37\20\2\u00cd\u00ce\7\60\2\2\u00ce\u00cf\5%\23\2\u00cf"+
		"\u00d0\t\b\2\2\u00d0\u00d1\5\25\13\2\u00d1\u00d2\5\33\16\2\u00d2\u00d4"+
		"\3\2\2\2\u00d3\u00bb\3\2\2\2\u00d3\u00c1\3\2\2\2\u00d3\u00c9\3\2\2\2\u00d4"+
		"*\3\2\2\2\u00d5\u00d6\7=\2\2\u00d6\u00d7\7=\2\2\u00d7\u00db\3\2\2\2\u00d8"+
		"\u00da\n\t\2\2\u00d9\u00d8\3\2\2\2\u00da\u00dd\3\2\2\2\u00db\u00d9\3\2"+
		"\2\2\u00db\u00dc\3\2\2\2\u00dc\u00de\3\2\2\2\u00dd\u00db\3\2\2\2\u00de"+
		"\u00df\b\26\2\2\u00df,\3\2\2\2\30\2\61\63;H]bdlouz\u0080\u0083\u0088\u008e"+
		"\u0091\u009c\u00a7\u00b9\u00d3\u00db\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}