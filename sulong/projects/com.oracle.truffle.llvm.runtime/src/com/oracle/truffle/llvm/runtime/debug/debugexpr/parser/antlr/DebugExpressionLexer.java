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

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings("all")
public class DebugExpressionLexer extends Lexer {
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
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
		"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
		"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "LETTER", "DIGIT", 
		"CR", "LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", 
		"CHARCONST", "LAPR", "ASTERISC", "SIGNED", "UNSIGNED", "INT", "LONG", 
		"SHORT", "FLOAT", "DOUBLE", "CHAR", "TYPEOF", "WS"
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


	public DebugExpressionLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "DebugExpression.g4"; }

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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2.\u010e\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3"+
		"\b\3\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3"+
		"\16\3\16\3\17\3\17\3\17\3\20\3\20\3\20\3\21\3\21\3\22\3\22\3\23\3\23\3"+
		"\23\3\24\3\24\3\24\3\25\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\30\3\30\3"+
		"\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3"+
		"\37\7\37\u00a7\n\37\f\37\16\37\u00aa\13\37\3 \6 \u00ad\n \r \16 \u00ae"+
		"\3!\6!\u00b2\n!\r!\16!\u00b3\3!\3!\6!\u00b8\n!\r!\16!\u00b9\3!\3!\3!\6"+
		"!\u00bf\n!\r!\16!\u00c0\5!\u00c3\n!\3\"\3\"\3\"\5\"\u00c8\n\"\3\"\3\""+
		"\3#\3#\3$\3$\3%\3%\3%\3%\3%\3%\3%\3&\3&\3&\3&\3&\3&\3&\3&\3&\3\'\3\'\3"+
		"\'\3\'\3(\3(\3(\3(\3(\3)\3)\3)\3)\3)\3)\3*\3*\3*\3*\3*\3*\3+\3+\3+\3+"+
		"\3+\3+\3+\3,\3,\3,\3,\3,\3-\3-\3-\3-\3-\3-\3-\3.\6.\u0109\n.\r.\16.\u010a"+
		"\3.\3.\2\2/\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33"+
		"\17\35\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\2\63\32\65\33\67\34"+
		"9\35;\36=\37? A!C\"E#G$I%K&M\'O(Q)S*U+W,Y-[.\3\2\7\4\2C\\c|\3\2\62;\4"+
		"\2GGgg\4\2--//\5\2\13\f\17\17\"\"\2\u0115\2\3\3\2\2\2\2\5\3\2\2\2\2\7"+
		"\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2"+
		"\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2"+
		"\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2"+
		"\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2"+
		"\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2"+
		"C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\2K\3\2\2\2\2M\3\2\2\2\2O\3"+
		"\2\2\2\2Q\3\2\2\2\2S\3\2\2\2\2U\3\2\2\2\2W\3\2\2\2\2Y\3\2\2\2\2[\3\2\2"+
		"\2\3]\3\2\2\2\5_\3\2\2\2\7a\3\2\2\2\tc\3\2\2\2\13e\3\2\2\2\rh\3\2\2\2"+
		"\17j\3\2\2\2\21q\3\2\2\2\23s\3\2\2\2\25u\3\2\2\2\27w\3\2\2\2\31y\3\2\2"+
		"\2\33{\3\2\2\2\35}\3\2\2\2\37\u0080\3\2\2\2!\u0083\3\2\2\2#\u0085\3\2"+
		"\2\2%\u0087\3\2\2\2\'\u008a\3\2\2\2)\u008d\3\2\2\2+\u0090\3\2\2\2-\u0093"+
		"\3\2\2\2/\u0095\3\2\2\2\61\u0097\3\2\2\2\63\u0099\3\2\2\2\65\u009b\3\2"+
		"\2\2\67\u009d\3\2\2\29\u009f\3\2\2\2;\u00a1\3\2\2\2=\u00a3\3\2\2\2?\u00ac"+
		"\3\2\2\2A\u00b1\3\2\2\2C\u00c4\3\2\2\2E\u00cb\3\2\2\2G\u00cd\3\2\2\2I"+
		"\u00cf\3\2\2\2K\u00d6\3\2\2\2M\u00df\3\2\2\2O\u00e3\3\2\2\2Q\u00e8\3\2"+
		"\2\2S\u00ee\3\2\2\2U\u00f4\3\2\2\2W\u00fb\3\2\2\2Y\u0100\3\2\2\2[\u0108"+
		"\3\2\2\2]^\7+\2\2^\4\3\2\2\2_`\7]\2\2`\6\3\2\2\2ab\7_\2\2b\b\3\2\2\2c"+
		"d\7\60\2\2d\n\3\2\2\2ef\7/\2\2fg\7@\2\2g\f\3\2\2\2hi\7.\2\2i\16\3\2\2"+
		"\2jk\7u\2\2kl\7k\2\2lm\7|\2\2mn\7g\2\2no\7q\2\2op\7h\2\2p\20\3\2\2\2q"+
		"r\7-\2\2r\22\3\2\2\2st\7/\2\2t\24\3\2\2\2uv\7\u0080\2\2v\26\3\2\2\2wx"+
		"\7#\2\2x\30\3\2\2\2yz\7\61\2\2z\32\3\2\2\2{|\7\'\2\2|\34\3\2\2\2}~\7@"+
		"\2\2~\177\7@\2\2\177\36\3\2\2\2\u0080\u0081\7>\2\2\u0081\u0082\7>\2\2"+
		"\u0082 \3\2\2\2\u0083\u0084\7>\2\2\u0084\"\3\2\2\2\u0085\u0086\7@\2\2"+
		"\u0086$\3\2\2\2\u0087\u0088\7>\2\2\u0088\u0089\7?\2\2\u0089&\3\2\2\2\u008a"+
		"\u008b\7@\2\2\u008b\u008c\7?\2\2\u008c(\3\2\2\2\u008d\u008e\7?\2\2\u008e"+
		"\u008f\7?\2\2\u008f*\3\2\2\2\u0090\u0091\7#\2\2\u0091\u0092\7?\2\2\u0092"+
		",\3\2\2\2\u0093\u0094\7A\2\2\u0094.\3\2\2\2\u0095\u0096\7<\2\2\u0096\60"+
		"\3\2\2\2\u0097\u0098\t\2\2\2\u0098\62\3\2\2\2\u0099\u009a\t\3\2\2\u009a"+
		"\64\3\2\2\2\u009b\u009c\7\17\2\2\u009c\66\3\2\2\2\u009d\u009e\7\f\2\2"+
		"\u009e8\3\2\2\2\u009f\u00a0\7)\2\2\u00a0:\3\2\2\2\u00a1\u00a2\7$\2\2\u00a2"+
		"<\3\2\2\2\u00a3\u00a8\5\61\31\2\u00a4\u00a7\5\61\31\2\u00a5\u00a7\5\63"+
		"\32\2\u00a6\u00a4\3\2\2\2\u00a6\u00a5\3\2\2\2\u00a7\u00aa\3\2\2\2\u00a8"+
		"\u00a6\3\2\2\2\u00a8\u00a9\3\2\2\2\u00a9>\3\2\2\2\u00aa\u00a8\3\2\2\2"+
		"\u00ab\u00ad\5\63\32\2\u00ac\u00ab\3\2\2\2\u00ad\u00ae\3\2\2\2\u00ae\u00ac"+
		"\3\2\2\2\u00ae\u00af\3\2\2\2\u00af@\3\2\2\2\u00b0\u00b2\5\63\32\2\u00b1"+
		"\u00b0\3\2\2\2\u00b2\u00b3\3\2\2\2\u00b3\u00b1\3\2\2\2\u00b3\u00b4\3\2"+
		"\2\2\u00b4\u00b5\3\2\2\2\u00b5\u00b7\7\60\2\2\u00b6\u00b8\5\63\32\2\u00b7"+
		"\u00b6\3\2\2\2\u00b8\u00b9\3\2\2\2\u00b9\u00b7\3\2\2\2\u00b9\u00ba\3\2"+
		"\2\2\u00ba\u00c2\3\2\2\2\u00bb\u00bc\t\4\2\2\u00bc\u00be\t\5\2\2\u00bd"+
		"\u00bf\5\63\32\2\u00be\u00bd\3\2\2\2\u00bf\u00c0\3\2\2\2\u00c0\u00be\3"+
		"\2\2\2\u00c0\u00c1\3\2\2\2\u00c1\u00c3\3\2\2\2\u00c2\u00bb\3\2\2\2\u00c2"+
		"\u00c3\3\2\2\2\u00c3B\3\2\2\2\u00c4\u00c7\59\35\2\u00c5\u00c8\5\61\31"+
		"\2\u00c6\u00c8\5\63\32\2\u00c7\u00c5\3\2\2\2\u00c7\u00c6\3\2\2\2\u00c8"+
		"\u00c9\3\2\2\2\u00c9\u00ca\59\35\2\u00caD\3\2\2\2\u00cb\u00cc\7*\2\2\u00cc"+
		"F\3\2\2\2\u00cd\u00ce\7,\2\2\u00ceH\3\2\2\2\u00cf\u00d0\7u\2\2\u00d0\u00d1"+
		"\7k\2\2\u00d1\u00d2\7i\2\2\u00d2\u00d3\7p\2\2\u00d3\u00d4\7g\2\2\u00d4"+
		"\u00d5\7f\2\2\u00d5J\3\2\2\2\u00d6\u00d7\7w\2\2\u00d7\u00d8\7p\2\2\u00d8"+
		"\u00d9\7u\2\2\u00d9\u00da\7k\2\2\u00da\u00db\7i\2\2\u00db\u00dc\7p\2\2"+
		"\u00dc\u00dd\7g\2\2\u00dd\u00de\7f\2\2\u00deL\3\2\2\2\u00df\u00e0\7k\2"+
		"\2\u00e0\u00e1\7p\2\2\u00e1\u00e2\7v\2\2\u00e2N\3\2\2\2\u00e3\u00e4\7"+
		"N\2\2\u00e4\u00e5\7Q\2\2\u00e5\u00e6\7P\2\2\u00e6\u00e7\7I\2\2\u00e7P"+
		"\3\2\2\2\u00e8\u00e9\7u\2\2\u00e9\u00ea\7j\2\2\u00ea\u00eb\7q\2\2\u00eb"+
		"\u00ec\7t\2\2\u00ec\u00ed\7v\2\2\u00edR\3\2\2\2\u00ee\u00ef\7h\2\2\u00ef"+
		"\u00f0\7n\2\2\u00f0\u00f1\7q\2\2\u00f1\u00f2\7c\2\2\u00f2\u00f3\7v\2\2"+
		"\u00f3T\3\2\2\2\u00f4\u00f5\7f\2\2\u00f5\u00f6\7q\2\2\u00f6\u00f7\7w\2"+
		"\2\u00f7\u00f8\7d\2\2\u00f8\u00f9\7n\2\2\u00f9\u00fa\7g\2\2\u00faV\3\2"+
		"\2\2\u00fb\u00fc\7e\2\2\u00fc\u00fd\7j\2\2\u00fd\u00fe\7c\2\2\u00fe\u00ff"+
		"\7t\2\2\u00ffX\3\2\2\2\u0100\u0101\7v\2\2\u0101\u0102\7{\2\2\u0102\u0103"+
		"\7r\2\2\u0103\u0104\7g\2\2\u0104\u0105\7q\2\2\u0105\u0106\7h\2\2\u0106"+
		"Z\3\2\2\2\u0107\u0109\t\6\2\2\u0108\u0107\3\2\2\2\u0109\u010a\3\2\2\2"+
		"\u010a\u0108\3\2\2\2\u010a\u010b\3\2\2\2\u010b\u010c\3\2\2\2\u010c\u010d"+
		"\b.\2\2\u010d\\\3\2\2\2\f\2\u00a6\u00a8\u00ae\u00b3\u00b9\u00c0\u00c2"+
		"\u00c7\u010a\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
