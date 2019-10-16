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
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, DIGIT=31, 
		CR=32, LF=33, SINGLECOMMA=34, QUOTE=35, IDENT=36, NUMBER=37, FLOATNUMBER=38, 
		CHARCONST=39, LAPR=40, ASTERISC=41, SIGNED=42, UNSIGNED=43, INT=44, LONG=45, 
		SHORT=46, FLOAT=47, DOUBLE=48, CHAR=49, TYPEOF=50, WS=51;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
		"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
		"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "T__23", "T__24", 
		"T__25", "T__26", "T__27", "T__28", "T__29", "LETTER", "DIGIT", "CR", 
		"LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", 
		"LAPR", "ASTERISC", "SIGNED", "UNSIGNED", "INT", "LONG", "SHORT", "FLOAT", 
		"DOUBLE", "CHAR", "TYPEOF", "WS"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "')'", "'['", "']'", "'.'", "'->'", "','", "'sizeof'", "'+'", "'-'", 
		"'~'", "'!'", "'/'", "'%'", "'>>'", "'<<'", "'<'", "'>'", "'<='", "'>='", 
		"'=='", "'!='", "'&'", "'^'", "'|'", "'&&'", "'||'", "'?'", "':'", "'void'", 
		"'long'", null, "'\r'", "'\n'", "'''", "'\"'", null, null, null, null, 
		"'('", "'*'", "'signed'", "'unsigned'", "'int'", "'LONG'", "'short'", 
		"'float'", "'double'", "'char'", "'typeof'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, "DIGIT", "CR", "LF", "SINGLECOMMA", 
		"QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", "LAPR", "ASTERISC", 
		"SIGNED", "UNSIGNED", "INT", "LONG", "SHORT", "FLOAT", "DOUBLE", "CHAR", 
		"TYPEOF", "WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\65\u0132\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t"+
		" \4!\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t"+
		"+\4,\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64"+
		"\t\64\4\65\t\65\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3"+
		"\b\3\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3"+
		"\16\3\16\3\17\3\17\3\17\3\20\3\20\3\20\3\21\3\21\3\22\3\22\3\23\3\23\3"+
		"\23\3\24\3\24\3\24\3\25\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\30\3\30\3"+
		"\31\3\31\3\32\3\32\3\32\3\33\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3"+
		"\36\3\36\3\36\3\37\3\37\3\37\3\37\3\37\3 \3 \3!\3!\3\"\3\"\3#\3#\3$\3"+
		"$\3%\3%\3&\3&\3&\7&\u00cb\n&\f&\16&\u00ce\13&\3\'\6\'\u00d1\n\'\r\'\16"+
		"\'\u00d2\3(\6(\u00d6\n(\r(\16(\u00d7\3(\3(\6(\u00dc\n(\r(\16(\u00dd\3"+
		"(\3(\3(\6(\u00e3\n(\r(\16(\u00e4\5(\u00e7\n(\3)\3)\3)\5)\u00ec\n)\3)\3"+
		")\3*\3*\3+\3+\3,\3,\3,\3,\3,\3,\3,\3-\3-\3-\3-\3-\3-\3-\3-\3-\3.\3.\3"+
		".\3.\3/\3/\3/\3/\3/\3\60\3\60\3\60\3\60\3\60\3\60\3\61\3\61\3\61\3\61"+
		"\3\61\3\61\3\62\3\62\3\62\3\62\3\62\3\62\3\62\3\63\3\63\3\63\3\63\3\63"+
		"\3\64\3\64\3\64\3\64\3\64\3\64\3\64\3\65\6\65\u012d\n\65\r\65\16\65\u012e"+
		"\3\65\3\65\2\2\66\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31"+
		"\16\33\17\35\20\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65"+
		"\34\67\359\36;\37= ?\2A!C\"E#G$I%K&M\'O(Q)S*U+W,Y-[.]/_\60a\61c\62e\63"+
		"g\64i\65\3\2\7\4\2C\\c|\3\2\62;\4\2GGgg\4\2--//\5\2\13\f\17\17\"\"\2\u0139"+
		"\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2"+
		"\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2"+
		"\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2"+
		"\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2"+
		"\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3"+
		"\2\2\2\2=\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3\2\2"+
		"\2\2K\3\2\2\2\2M\3\2\2\2\2O\3\2\2\2\2Q\3\2\2\2\2S\3\2\2\2\2U\3\2\2\2\2"+
		"W\3\2\2\2\2Y\3\2\2\2\2[\3\2\2\2\2]\3\2\2\2\2_\3\2\2\2\2a\3\2\2\2\2c\3"+
		"\2\2\2\2e\3\2\2\2\2g\3\2\2\2\2i\3\2\2\2\3k\3\2\2\2\5m\3\2\2\2\7o\3\2\2"+
		"\2\tq\3\2\2\2\13s\3\2\2\2\rv\3\2\2\2\17x\3\2\2\2\21\177\3\2\2\2\23\u0081"+
		"\3\2\2\2\25\u0083\3\2\2\2\27\u0085\3\2\2\2\31\u0087\3\2\2\2\33\u0089\3"+
		"\2\2\2\35\u008b\3\2\2\2\37\u008e\3\2\2\2!\u0091\3\2\2\2#\u0093\3\2\2\2"+
		"%\u0095\3\2\2\2\'\u0098\3\2\2\2)\u009b\3\2\2\2+\u009e\3\2\2\2-\u00a1\3"+
		"\2\2\2/\u00a3\3\2\2\2\61\u00a5\3\2\2\2\63\u00a7\3\2\2\2\65\u00aa\3\2\2"+
		"\2\67\u00ad\3\2\2\29\u00af\3\2\2\2;\u00b1\3\2\2\2=\u00b6\3\2\2\2?\u00bb"+
		"\3\2\2\2A\u00bd\3\2\2\2C\u00bf\3\2\2\2E\u00c1\3\2\2\2G\u00c3\3\2\2\2I"+
		"\u00c5\3\2\2\2K\u00c7\3\2\2\2M\u00d0\3\2\2\2O\u00d5\3\2\2\2Q\u00e8\3\2"+
		"\2\2S\u00ef\3\2\2\2U\u00f1\3\2\2\2W\u00f3\3\2\2\2Y\u00fa\3\2\2\2[\u0103"+
		"\3\2\2\2]\u0107\3\2\2\2_\u010c\3\2\2\2a\u0112\3\2\2\2c\u0118\3\2\2\2e"+
		"\u011f\3\2\2\2g\u0124\3\2\2\2i\u012c\3\2\2\2kl\7+\2\2l\4\3\2\2\2mn\7]"+
		"\2\2n\6\3\2\2\2op\7_\2\2p\b\3\2\2\2qr\7\60\2\2r\n\3\2\2\2st\7/\2\2tu\7"+
		"@\2\2u\f\3\2\2\2vw\7.\2\2w\16\3\2\2\2xy\7u\2\2yz\7k\2\2z{\7|\2\2{|\7g"+
		"\2\2|}\7q\2\2}~\7h\2\2~\20\3\2\2\2\177\u0080\7-\2\2\u0080\22\3\2\2\2\u0081"+
		"\u0082\7/\2\2\u0082\24\3\2\2\2\u0083\u0084\7\u0080\2\2\u0084\26\3\2\2"+
		"\2\u0085\u0086\7#\2\2\u0086\30\3\2\2\2\u0087\u0088\7\61\2\2\u0088\32\3"+
		"\2\2\2\u0089\u008a\7\'\2\2\u008a\34\3\2\2\2\u008b\u008c\7@\2\2\u008c\u008d"+
		"\7@\2\2\u008d\36\3\2\2\2\u008e\u008f\7>\2\2\u008f\u0090\7>\2\2\u0090 "+
		"\3\2\2\2\u0091\u0092\7>\2\2\u0092\"\3\2\2\2\u0093\u0094\7@\2\2\u0094$"+
		"\3\2\2\2\u0095\u0096\7>\2\2\u0096\u0097\7?\2\2\u0097&\3\2\2\2\u0098\u0099"+
		"\7@\2\2\u0099\u009a\7?\2\2\u009a(\3\2\2\2\u009b\u009c\7?\2\2\u009c\u009d"+
		"\7?\2\2\u009d*\3\2\2\2\u009e\u009f\7#\2\2\u009f\u00a0\7?\2\2\u00a0,\3"+
		"\2\2\2\u00a1\u00a2\7(\2\2\u00a2.\3\2\2\2\u00a3\u00a4\7`\2\2\u00a4\60\3"+
		"\2\2\2\u00a5\u00a6\7~\2\2\u00a6\62\3\2\2\2\u00a7\u00a8\7(\2\2\u00a8\u00a9"+
		"\7(\2\2\u00a9\64\3\2\2\2\u00aa\u00ab\7~\2\2\u00ab\u00ac\7~\2\2\u00ac\66"+
		"\3\2\2\2\u00ad\u00ae\7A\2\2\u00ae8\3\2\2\2\u00af\u00b0\7<\2\2\u00b0:\3"+
		"\2\2\2\u00b1\u00b2\7x\2\2\u00b2\u00b3\7q\2\2\u00b3\u00b4\7k\2\2\u00b4"+
		"\u00b5\7f\2\2\u00b5<\3\2\2\2\u00b6\u00b7\7n\2\2\u00b7\u00b8\7q\2\2\u00b8"+
		"\u00b9\7p\2\2\u00b9\u00ba\7i\2\2\u00ba>\3\2\2\2\u00bb\u00bc\t\2\2\2\u00bc"+
		"@\3\2\2\2\u00bd\u00be\t\3\2\2\u00beB\3\2\2\2\u00bf\u00c0\7\17\2\2\u00c0"+
		"D\3\2\2\2\u00c1\u00c2\7\f\2\2\u00c2F\3\2\2\2\u00c3\u00c4\7)\2\2\u00c4"+
		"H\3\2\2\2\u00c5\u00c6\7$\2\2\u00c6J\3\2\2\2\u00c7\u00cc\5? \2\u00c8\u00cb"+
		"\5? \2\u00c9\u00cb\5A!\2\u00ca\u00c8\3\2\2\2\u00ca\u00c9\3\2\2\2\u00cb"+
		"\u00ce\3\2\2\2\u00cc\u00ca\3\2\2\2\u00cc\u00cd\3\2\2\2\u00cdL\3\2\2\2"+
		"\u00ce\u00cc\3\2\2\2\u00cf\u00d1\5A!\2\u00d0\u00cf\3\2\2\2\u00d1\u00d2"+
		"\3\2\2\2\u00d2\u00d0\3\2\2\2\u00d2\u00d3\3\2\2\2\u00d3N\3\2\2\2\u00d4"+
		"\u00d6\5A!\2\u00d5\u00d4\3\2\2\2\u00d6\u00d7\3\2\2\2\u00d7\u00d5\3\2\2"+
		"\2\u00d7\u00d8\3\2\2\2\u00d8\u00d9\3\2\2\2\u00d9\u00db\7\60\2\2\u00da"+
		"\u00dc\5A!\2\u00db\u00da\3\2\2\2\u00dc\u00dd\3\2\2\2\u00dd\u00db\3\2\2"+
		"\2\u00dd\u00de\3\2\2\2\u00de\u00e6\3\2\2\2\u00df\u00e0\t\4\2\2\u00e0\u00e2"+
		"\t\5\2\2\u00e1\u00e3\5A!\2\u00e2\u00e1\3\2\2\2\u00e3\u00e4\3\2\2\2\u00e4"+
		"\u00e2\3\2\2\2\u00e4\u00e5\3\2\2\2\u00e5\u00e7\3\2\2\2\u00e6\u00df\3\2"+
		"\2\2\u00e6\u00e7\3\2\2\2\u00e7P\3\2\2\2\u00e8\u00eb\5G$\2\u00e9\u00ec"+
		"\5? \2\u00ea\u00ec\5A!\2\u00eb\u00e9\3\2\2\2\u00eb\u00ea\3\2\2\2\u00ec"+
		"\u00ed\3\2\2\2\u00ed\u00ee\5G$\2\u00eeR\3\2\2\2\u00ef\u00f0\7*\2\2\u00f0"+
		"T\3\2\2\2\u00f1\u00f2\7,\2\2\u00f2V\3\2\2\2\u00f3\u00f4\7u\2\2\u00f4\u00f5"+
		"\7k\2\2\u00f5\u00f6\7i\2\2\u00f6\u00f7\7p\2\2\u00f7\u00f8\7g\2\2\u00f8"+
		"\u00f9\7f\2\2\u00f9X\3\2\2\2\u00fa\u00fb\7w\2\2\u00fb\u00fc\7p\2\2\u00fc"+
		"\u00fd\7u\2\2\u00fd\u00fe\7k\2\2\u00fe\u00ff\7i\2\2\u00ff\u0100\7p\2\2"+
		"\u0100\u0101\7g\2\2\u0101\u0102\7f\2\2\u0102Z\3\2\2\2\u0103\u0104\7k\2"+
		"\2\u0104\u0105\7p\2\2\u0105\u0106\7v\2\2\u0106\\\3\2\2\2\u0107\u0108\7"+
		"N\2\2\u0108\u0109\7Q\2\2\u0109\u010a\7P\2\2\u010a\u010b\7I\2\2\u010b^"+
		"\3\2\2\2\u010c\u010d\7u\2\2\u010d\u010e\7j\2\2\u010e\u010f\7q\2\2\u010f"+
		"\u0110\7t\2\2\u0110\u0111\7v\2\2\u0111`\3\2\2\2\u0112\u0113\7h\2\2\u0113"+
		"\u0114\7n\2\2\u0114\u0115\7q\2\2\u0115\u0116\7c\2\2\u0116\u0117\7v\2\2"+
		"\u0117b\3\2\2\2\u0118\u0119\7f\2\2\u0119\u011a\7q\2\2\u011a\u011b\7w\2"+
		"\2\u011b\u011c\7d\2\2\u011c\u011d\7n\2\2\u011d\u011e\7g\2\2\u011ed\3\2"+
		"\2\2\u011f\u0120\7e\2\2\u0120\u0121\7j\2\2\u0121\u0122\7c\2\2\u0122\u0123"+
		"\7t\2\2\u0123f\3\2\2\2\u0124\u0125\7v\2\2\u0125\u0126\7{\2\2\u0126\u0127"+
		"\7r\2\2\u0127\u0128\7g\2\2\u0128\u0129\7q\2\2\u0129\u012a\7h\2\2\u012a"+
		"h\3\2\2\2\u012b\u012d\t\6\2\2\u012c\u012b\3\2\2\2\u012d\u012e\3\2\2\2"+
		"\u012e\u012c\3\2\2\2\u012e\u012f\3\2\2\2\u012f\u0130\3\2\2\2\u0130\u0131"+
		"\b\65\2\2\u0131j\3\2\2\2\f\2\u00ca\u00cc\u00d2\u00d7\u00dd\u00e4\u00e6"+
		"\u00eb\u012e\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
