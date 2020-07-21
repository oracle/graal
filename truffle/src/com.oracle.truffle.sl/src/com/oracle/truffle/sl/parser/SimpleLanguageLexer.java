/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings("all")
public class SimpleLanguageLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, WS=31, COMMENT=32, 
		LINE_COMMENT=33, IDENTIFIER=34, STRING_LITERAL=35, NUMERIC_LITERAL=36;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "T__23", "T__24", 
			"T__25", "T__26", "T__27", "T__28", "T__29", "WS", "COMMENT", "LINE_COMMENT", 
			"LETTER", "NON_ZERO_DIGIT", "DIGIT", "HEX_DIGIT", "OCT_DIGIT", "BINARY_DIGIT", 
			"TAB", "STRING_CHAR", "IDENTIFIER", "STRING_LITERAL", "NUMERIC_LITERAL"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'function'", "'('", "','", "')'", "'{'", "'}'", "'break'", "';'", 
			"'continue'", "'debugger'", "'while'", "'if'", "'else'", "'return'", 
			"'||'", "'&&'", "'<'", "'<='", "'>'", "'>='", "'=='", "'!='", "'+'", 
			"'-'", "'*'", "'/'", "'='", "'.'", "'['", "']'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, "WS", "COMMENT", "LINE_COMMENT", 
			"IDENTIFIER", "STRING_LITERAL", "NUMERIC_LITERAL"
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


	public SimpleLanguageLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "SimpleLanguage.g4"; }

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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2&\u0110\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5"+
		"\3\6\3\6\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\n\3\n\3\n\3\n\3\n\3"+
		"\n\3\n\3\n\3\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\f\3\f\3"+
		"\f\3\f\3\f\3\f\3\r\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\17"+
		"\3\17\3\17\3\17\3\20\3\20\3\20\3\21\3\21\3\21\3\22\3\22\3\23\3\23\3\23"+
		"\3\24\3\24\3\25\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\27\3\30\3\30\3\31"+
		"\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3 \6"+
		" \u00c5\n \r \16 \u00c6\3 \3 \3!\3!\3!\3!\7!\u00cf\n!\f!\16!\u00d2\13"+
		"!\3!\3!\3!\3!\3!\3\"\3\"\3\"\3\"\7\"\u00dd\n\"\f\"\16\"\u00e0\13\"\3\""+
		"\3\"\3#\5#\u00e5\n#\3$\3$\3%\3%\3&\5&\u00ec\n&\3\'\3\'\3(\3(\3)\3)\3*"+
		"\3*\3+\3+\3+\7+\u00f9\n+\f+\16+\u00fc\13+\3,\3,\7,\u0100\n,\f,\16,\u0103"+
		"\13,\3,\3,\3-\3-\3-\7-\u010a\n-\f-\16-\u010d\13-\5-\u010f\n-\3\u00d0\2"+
		".\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20"+
		"\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\359\36;\37"+
		"= ?!A\"C#E\2G\2I\2K\2M\2O\2Q\2S\2U$W%Y&\3\2\n\5\2\13\f\16\17\"\"\4\2\f"+
		"\f\17\17\6\2&&C\\aac|\3\2\63;\3\2\62;\5\2\62;CHch\3\2\629\6\2\f\f\17\17"+
		"$$^^\2\u010f\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2"+
		"\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2"+
		"\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2"+
		"\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2"+
		"\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3"+
		"\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2U\3\2\2"+
		"\2\2W\3\2\2\2\2Y\3\2\2\2\3[\3\2\2\2\5d\3\2\2\2\7f\3\2\2\2\th\3\2\2\2\13"+
		"j\3\2\2\2\rl\3\2\2\2\17n\3\2\2\2\21t\3\2\2\2\23v\3\2\2\2\25\177\3\2\2"+
		"\2\27\u0088\3\2\2\2\31\u008e\3\2\2\2\33\u0091\3\2\2\2\35\u0096\3\2\2\2"+
		"\37\u009d\3\2\2\2!\u00a0\3\2\2\2#\u00a3\3\2\2\2%\u00a5\3\2\2\2\'\u00a8"+
		"\3\2\2\2)\u00aa\3\2\2\2+\u00ad\3\2\2\2-\u00b0\3\2\2\2/\u00b3\3\2\2\2\61"+
		"\u00b5\3\2\2\2\63\u00b7\3\2\2\2\65\u00b9\3\2\2\2\67\u00bb\3\2\2\29\u00bd"+
		"\3\2\2\2;\u00bf\3\2\2\2=\u00c1\3\2\2\2?\u00c4\3\2\2\2A\u00ca\3\2\2\2C"+
		"\u00d8\3\2\2\2E\u00e4\3\2\2\2G\u00e6\3\2\2\2I\u00e8\3\2\2\2K\u00eb\3\2"+
		"\2\2M\u00ed\3\2\2\2O\u00ef\3\2\2\2Q\u00f1\3\2\2\2S\u00f3\3\2\2\2U\u00f5"+
		"\3\2\2\2W\u00fd\3\2\2\2Y\u010e\3\2\2\2[\\\7h\2\2\\]\7w\2\2]^\7p\2\2^_"+
		"\7e\2\2_`\7v\2\2`a\7k\2\2ab\7q\2\2bc\7p\2\2c\4\3\2\2\2de\7*\2\2e\6\3\2"+
		"\2\2fg\7.\2\2g\b\3\2\2\2hi\7+\2\2i\n\3\2\2\2jk\7}\2\2k\f\3\2\2\2lm\7\177"+
		"\2\2m\16\3\2\2\2no\7d\2\2op\7t\2\2pq\7g\2\2qr\7c\2\2rs\7m\2\2s\20\3\2"+
		"\2\2tu\7=\2\2u\22\3\2\2\2vw\7e\2\2wx\7q\2\2xy\7p\2\2yz\7v\2\2z{\7k\2\2"+
		"{|\7p\2\2|}\7w\2\2}~\7g\2\2~\24\3\2\2\2\177\u0080\7f\2\2\u0080\u0081\7"+
		"g\2\2\u0081\u0082\7d\2\2\u0082\u0083\7w\2\2\u0083\u0084\7i\2\2\u0084\u0085"+
		"\7i\2\2\u0085\u0086\7g\2\2\u0086\u0087\7t\2\2\u0087\26\3\2\2\2\u0088\u0089"+
		"\7y\2\2\u0089\u008a\7j\2\2\u008a\u008b\7k\2\2\u008b\u008c\7n\2\2\u008c"+
		"\u008d\7g\2\2\u008d\30\3\2\2\2\u008e\u008f\7k\2\2\u008f\u0090\7h\2\2\u0090"+
		"\32\3\2\2\2\u0091\u0092\7g\2\2\u0092\u0093\7n\2\2\u0093\u0094\7u\2\2\u0094"+
		"\u0095\7g\2\2\u0095\34\3\2\2\2\u0096\u0097\7t\2\2\u0097\u0098\7g\2\2\u0098"+
		"\u0099\7v\2\2\u0099\u009a\7w\2\2\u009a\u009b\7t\2\2\u009b\u009c\7p\2\2"+
		"\u009c\36\3\2\2\2\u009d\u009e\7~\2\2\u009e\u009f\7~\2\2\u009f \3\2\2\2"+
		"\u00a0\u00a1\7(\2\2\u00a1\u00a2\7(\2\2\u00a2\"\3\2\2\2\u00a3\u00a4\7>"+
		"\2\2\u00a4$\3\2\2\2\u00a5\u00a6\7>\2\2\u00a6\u00a7\7?\2\2\u00a7&\3\2\2"+
		"\2\u00a8\u00a9\7@\2\2\u00a9(\3\2\2\2\u00aa\u00ab\7@\2\2\u00ab\u00ac\7"+
		"?\2\2\u00ac*\3\2\2\2\u00ad\u00ae\7?\2\2\u00ae\u00af\7?\2\2\u00af,\3\2"+
		"\2\2\u00b0\u00b1\7#\2\2\u00b1\u00b2\7?\2\2\u00b2.\3\2\2\2\u00b3\u00b4"+
		"\7-\2\2\u00b4\60\3\2\2\2\u00b5\u00b6\7/\2\2\u00b6\62\3\2\2\2\u00b7\u00b8"+
		"\7,\2\2\u00b8\64\3\2\2\2\u00b9\u00ba\7\61\2\2\u00ba\66\3\2\2\2\u00bb\u00bc"+
		"\7?\2\2\u00bc8\3\2\2\2\u00bd\u00be\7\60\2\2\u00be:\3\2\2\2\u00bf\u00c0"+
		"\7]\2\2\u00c0<\3\2\2\2\u00c1\u00c2\7_\2\2\u00c2>\3\2\2\2\u00c3\u00c5\t"+
		"\2\2\2\u00c4\u00c3\3\2\2\2\u00c5\u00c6\3\2\2\2\u00c6\u00c4\3\2\2\2\u00c6"+
		"\u00c7\3\2\2\2\u00c7\u00c8\3\2\2\2\u00c8\u00c9\b \2\2\u00c9@\3\2\2\2\u00ca"+
		"\u00cb\7\61\2\2\u00cb\u00cc\7,\2\2\u00cc\u00d0\3\2\2\2\u00cd\u00cf\13"+
		"\2\2\2\u00ce\u00cd\3\2\2\2\u00cf\u00d2\3\2\2\2\u00d0\u00d1\3\2\2\2\u00d0"+
		"\u00ce\3\2\2\2\u00d1\u00d3\3\2\2\2\u00d2\u00d0\3\2\2\2\u00d3\u00d4\7,"+
		"\2\2\u00d4\u00d5\7\61\2\2\u00d5\u00d6\3\2\2\2\u00d6\u00d7\b!\2\2\u00d7"+
		"B\3\2\2\2\u00d8\u00d9\7\61\2\2\u00d9\u00da\7\61\2\2\u00da\u00de\3\2\2"+
		"\2\u00db\u00dd\n\3\2\2\u00dc\u00db\3\2\2\2\u00dd\u00e0\3\2\2\2\u00de\u00dc"+
		"\3\2\2\2\u00de\u00df\3\2\2\2\u00df\u00e1\3\2\2\2\u00e0\u00de\3\2\2\2\u00e1"+
		"\u00e2\b\"\2\2\u00e2D\3\2\2\2\u00e3\u00e5\t\4\2\2\u00e4\u00e3\3\2\2\2"+
		"\u00e5F\3\2\2\2\u00e6\u00e7\t\5\2\2\u00e7H\3\2\2\2\u00e8\u00e9\t\6\2\2"+
		"\u00e9J\3\2\2\2\u00ea\u00ec\t\7\2\2\u00eb\u00ea\3\2\2\2\u00ecL\3\2\2\2"+
		"\u00ed\u00ee\t\b\2\2\u00eeN\3\2\2\2\u00ef\u00f0\4\62\63\2\u00f0P\3\2\2"+
		"\2\u00f1\u00f2\7\13\2\2\u00f2R\3\2\2\2\u00f3\u00f4\n\t\2\2\u00f4T\3\2"+
		"\2\2\u00f5\u00fa\5E#\2\u00f6\u00f9\5E#\2\u00f7\u00f9\5I%\2\u00f8\u00f6"+
		"\3\2\2\2\u00f8\u00f7\3\2\2\2\u00f9\u00fc\3\2\2\2\u00fa\u00f8\3\2\2\2\u00fa"+
		"\u00fb\3\2\2\2\u00fbV\3\2\2\2\u00fc\u00fa\3\2\2\2\u00fd\u0101\7$\2\2\u00fe"+
		"\u0100\5S*\2\u00ff\u00fe\3\2\2\2\u0100\u0103\3\2\2\2\u0101\u00ff\3\2\2"+
		"\2\u0101\u0102\3\2\2\2\u0102\u0104\3\2\2\2\u0103\u0101\3\2\2\2\u0104\u0105"+
		"\7$\2\2\u0105X\3\2\2\2\u0106\u010f\7\62\2\2\u0107\u010b\5G$\2\u0108\u010a"+
		"\5I%\2\u0109\u0108\3\2\2\2\u010a\u010d\3\2\2\2\u010b\u0109\3\2\2\2\u010b"+
		"\u010c\3\2\2\2\u010c\u010f\3\2\2\2\u010d\u010b\3\2\2\2\u010e\u0106\3\2"+
		"\2\2\u010e\u0107\3\2\2\2\u010fZ\3\2\2\2\r\2\u00c6\u00d0\u00de\u00e4\u00eb"+
		"\u00f8\u00fa\u0101\u010b\u010e\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
