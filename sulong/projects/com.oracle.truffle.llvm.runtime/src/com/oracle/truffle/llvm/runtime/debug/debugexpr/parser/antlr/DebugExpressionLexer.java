/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, LAPR=9, 
		RAPR=10, ASTERISC=11, PLUS=12, MINUS=13, DIVIDE=14, LOGICOR=15, LOGICAND=16, 
		DOT=17, POINTER=18, EXCLAM=19, TILDA=20, MODULAR=21, SHIFTR=22, SHIFTL=23, 
		GT=24, LT=25, GTE=26, LTE=27, EQ=28, NE=29, AND=30, OR=31, XOR=32, SIGNED=33, 
		UNSIGNED=34, INT=35, LONG=36, SHORT=37, FLOAT=38, DOUBLE=39, CHAR=40, 
		TYPEOF=41, IDENT=42, NUMBER=43, FLOATNUMBER=44, CHARCONST=45, WS=46;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "LETTER", 
			"NONDIGIT", "DIGIT", "CR", "LF", "SINGLECOMMA", "QUOTE", "CCHAR", "SIMPLE_ESCAPE_SEQUENCE", 
			"LAPR", "RAPR", "ASTERISC", "PLUS", "MINUS", "DIVIDE", "LOGICOR", "LOGICAND", 
			"DOT", "POINTER", "EXCLAM", "TILDA", "MODULAR", "SHIFTR", "SHIFTL", "GT", 
			"LT", "GTE", "LTE", "EQ", "NE", "AND", "OR", "XOR", "SIGNED", "UNSIGNED", 
			"INT", "LONG", "SHORT", "FLOAT", "DOUBLE", "CHAR", "TYPEOF", "IDENT", 
			"NUMBER", "FLOATNUMBER", "CHARCONST", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'['", "']'", "','", "'sizeof'", "'?'", "':'", "'void'", "'long'", 
			"'('", "')'", "'*'", "'+'", "'-'", "'/'", "'||'", "'&&'", "'.'", "'->'", 
			"'!'", "'~'", "'%'", "'>>'", "'<<'", "'>'", "'<'", "'>='", "'<='", "'=='", 
			"'!='", "'&'", "'|'", "'^'", "'signed'", "'unsigned'", "'int'", "'LONG'", 
			"'short'", "'float'", "'double'", "'char'", "'typeof'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, "LAPR", "RAPR", 
			"ASTERISC", "PLUS", "MINUS", "DIVIDE", "LOGICOR", "LOGICAND", "DOT", 
			"POINTER", "EXCLAM", "TILDA", "MODULAR", "SHIFTR", "SHIFTL", "GT", "LT", 
			"GTE", "LTE", "EQ", "NE", "AND", "OR", "XOR", "SIGNED", "UNSIGNED", "INT", 
			"LONG", "SHORT", "FLOAT", "DOUBLE", "CHAR", "TYPEOF", "IDENT", "NUMBER", 
			"FLOATNUMBER", "CHARCONST", "WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\60\u014f\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t"+
		" \4!\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t"+
		"+\4,\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64"+
		"\t\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\3\2\3\2\3\3\3\3\3\4\3\4\3\5"+
		"\3\5\3\5\3\5\3\5\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3"+
		"\t\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3\20"+
		"\3\20\3\21\3\21\5\21\u009d\n\21\3\22\3\22\3\22\3\23\3\23\3\24\3\24\3\25"+
		"\3\25\3\26\3\26\3\27\3\27\3\30\3\30\3\31\3\31\3\31\3\32\3\32\3\32\3\33"+
		"\3\33\3\34\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3 \3 \3 \3!\3!\3!\3"+
		"\"\3\"\3#\3#\3$\3$\3$\3%\3%\3%\3&\3&\3&\3\'\3\'\3\'\3(\3(\3)\3)\3*\3*"+
		"\3+\3+\3+\3+\3+\3+\3+\3,\3,\3,\3,\3,\3,\3,\3,\3,\3-\3-\3-\3-\3.\3.\3."+
		"\3.\3.\3/\3/\3/\3/\3/\3/\3\60\3\60\3\60\3\60\3\60\3\60\3\61\3\61\3\61"+
		"\3\61\3\61\3\61\3\61\3\62\3\62\3\62\3\62\3\62\3\63\3\63\3\63\3\63\3\63"+
		"\3\63\3\63\3\64\3\64\3\64\7\64\u0116\n\64\f\64\16\64\u0119\13\64\3\65"+
		"\6\65\u011c\n\65\r\65\16\65\u011d\3\66\6\66\u0121\n\66\r\66\16\66\u0122"+
		"\3\66\3\66\6\66\u0127\n\66\r\66\16\66\u0128\3\66\3\66\3\66\6\66\u012e"+
		"\n\66\r\66\16\66\u012f\5\66\u0132\n\66\3\67\3\67\3\67\3\67\3\67\3\67\3"+
		"\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\3\67\5\67\u0147"+
		"\n\67\38\68\u014a\n8\r8\168\u014b\38\38\2\29\3\3\5\4\7\5\t\6\13\7\r\b"+
		"\17\t\21\n\23\2\25\2\27\2\31\2\33\2\35\2\37\2!\2#\2%\13\'\f)\r+\16-\17"+
		"/\20\61\21\63\22\65\23\67\249\25;\26=\27?\30A\31C\32E\33G\34I\35K\36M"+
		"\37O Q!S\"U#W$Y%[&]\'_(a)c*e+g,i-k.m/o\60\3\2\n\4\2C\\c|\5\2C\\aac|\3"+
		"\2\62;\6\2\f\f\17\17))^^\f\2$$))AA^^cdhhppttvvxx\4\2GGgg\4\2--//\5\2\13"+
		"\f\17\17\"\"\2\u0151\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2"+
		"\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2%\3\2\2\2\2\'\3\2\2"+
		"\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2"+
		"\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2"+
		"\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\2K\3\2\2\2\2"+
		"M\3\2\2\2\2O\3\2\2\2\2Q\3\2\2\2\2S\3\2\2\2\2U\3\2\2\2\2W\3\2\2\2\2Y\3"+
		"\2\2\2\2[\3\2\2\2\2]\3\2\2\2\2_\3\2\2\2\2a\3\2\2\2\2c\3\2\2\2\2e\3\2\2"+
		"\2\2g\3\2\2\2\2i\3\2\2\2\2k\3\2\2\2\2m\3\2\2\2\2o\3\2\2\2\3q\3\2\2\2\5"+
		"s\3\2\2\2\7u\3\2\2\2\tw\3\2\2\2\13~\3\2\2\2\r\u0080\3\2\2\2\17\u0082\3"+
		"\2\2\2\21\u0087\3\2\2\2\23\u008c\3\2\2\2\25\u008e\3\2\2\2\27\u0090\3\2"+
		"\2\2\31\u0092\3\2\2\2\33\u0094\3\2\2\2\35\u0096\3\2\2\2\37\u0098\3\2\2"+
		"\2!\u009c\3\2\2\2#\u009e\3\2\2\2%\u00a1\3\2\2\2\'\u00a3\3\2\2\2)\u00a5"+
		"\3\2\2\2+\u00a7\3\2\2\2-\u00a9\3\2\2\2/\u00ab\3\2\2\2\61\u00ad\3\2\2\2"+
		"\63\u00b0\3\2\2\2\65\u00b3\3\2\2\2\67\u00b5\3\2\2\29\u00b8\3\2\2\2;\u00ba"+
		"\3\2\2\2=\u00bc\3\2\2\2?\u00be\3\2\2\2A\u00c1\3\2\2\2C\u00c4\3\2\2\2E"+
		"\u00c6\3\2\2\2G\u00c8\3\2\2\2I\u00cb\3\2\2\2K\u00ce\3\2\2\2M\u00d1\3\2"+
		"\2\2O\u00d4\3\2\2\2Q\u00d6\3\2\2\2S\u00d8\3\2\2\2U\u00da\3\2\2\2W\u00e1"+
		"\3\2\2\2Y\u00ea\3\2\2\2[\u00ee\3\2\2\2]\u00f3\3\2\2\2_\u00f9\3\2\2\2a"+
		"\u00ff\3\2\2\2c\u0106\3\2\2\2e\u010b\3\2\2\2g\u0112\3\2\2\2i\u011b\3\2"+
		"\2\2k\u0120\3\2\2\2m\u0146\3\2\2\2o\u0149\3\2\2\2qr\7]\2\2r\4\3\2\2\2"+
		"st\7_\2\2t\6\3\2\2\2uv\7.\2\2v\b\3\2\2\2wx\7u\2\2xy\7k\2\2yz\7|\2\2z{"+
		"\7g\2\2{|\7q\2\2|}\7h\2\2}\n\3\2\2\2~\177\7A\2\2\177\f\3\2\2\2\u0080\u0081"+
		"\7<\2\2\u0081\16\3\2\2\2\u0082\u0083\7x\2\2\u0083\u0084\7q\2\2\u0084\u0085"+
		"\7k\2\2\u0085\u0086\7f\2\2\u0086\20\3\2\2\2\u0087\u0088\7n\2\2\u0088\u0089"+
		"\7q\2\2\u0089\u008a\7p\2\2\u008a\u008b\7i\2\2\u008b\22\3\2\2\2\u008c\u008d"+
		"\t\2\2\2\u008d\24\3\2\2\2\u008e\u008f\t\3\2\2\u008f\26\3\2\2\2\u0090\u0091"+
		"\t\4\2\2\u0091\30\3\2\2\2\u0092\u0093\7\17\2\2\u0093\32\3\2\2\2\u0094"+
		"\u0095\7\f\2\2\u0095\34\3\2\2\2\u0096\u0097\7)\2\2\u0097\36\3\2\2\2\u0098"+
		"\u0099\7$\2\2\u0099 \3\2\2\2\u009a\u009d\n\5\2\2\u009b\u009d\5#\22\2\u009c"+
		"\u009a\3\2\2\2\u009c\u009b\3\2\2\2\u009d\"\3\2\2\2\u009e\u009f\7^\2\2"+
		"\u009f\u00a0\t\6\2\2\u00a0$\3\2\2\2\u00a1\u00a2\7*\2\2\u00a2&\3\2\2\2"+
		"\u00a3\u00a4\7+\2\2\u00a4(\3\2\2\2\u00a5\u00a6\7,\2\2\u00a6*\3\2\2\2\u00a7"+
		"\u00a8\7-\2\2\u00a8,\3\2\2\2\u00a9\u00aa\7/\2\2\u00aa.\3\2\2\2\u00ab\u00ac"+
		"\7\61\2\2\u00ac\60\3\2\2\2\u00ad\u00ae\7~\2\2\u00ae\u00af\7~\2\2\u00af"+
		"\62\3\2\2\2\u00b0\u00b1\7(\2\2\u00b1\u00b2\7(\2\2\u00b2\64\3\2\2\2\u00b3"+
		"\u00b4\7\60\2\2\u00b4\66\3\2\2\2\u00b5\u00b6\7/\2\2\u00b6\u00b7\7@\2\2"+
		"\u00b78\3\2\2\2\u00b8\u00b9\7#\2\2\u00b9:\3\2\2\2\u00ba\u00bb\7\u0080"+
		"\2\2\u00bb<\3\2\2\2\u00bc\u00bd\7\'\2\2\u00bd>\3\2\2\2\u00be\u00bf\7@"+
		"\2\2\u00bf\u00c0\7@\2\2\u00c0@\3\2\2\2\u00c1\u00c2\7>\2\2\u00c2\u00c3"+
		"\7>\2\2\u00c3B\3\2\2\2\u00c4\u00c5\7@\2\2\u00c5D\3\2\2\2\u00c6\u00c7\7"+
		">\2\2\u00c7F\3\2\2\2\u00c8\u00c9\7@\2\2\u00c9\u00ca\7?\2\2\u00caH\3\2"+
		"\2\2\u00cb\u00cc\7>\2\2\u00cc\u00cd\7?\2\2\u00cdJ\3\2\2\2\u00ce\u00cf"+
		"\7?\2\2\u00cf\u00d0\7?\2\2\u00d0L\3\2\2\2\u00d1\u00d2\7#\2\2\u00d2\u00d3"+
		"\7?\2\2\u00d3N\3\2\2\2\u00d4\u00d5\7(\2\2\u00d5P\3\2\2\2\u00d6\u00d7\7"+
		"~\2\2\u00d7R\3\2\2\2\u00d8\u00d9\7`\2\2\u00d9T\3\2\2\2\u00da\u00db\7u"+
		"\2\2\u00db\u00dc\7k\2\2\u00dc\u00dd\7i\2\2\u00dd\u00de\7p\2\2\u00de\u00df"+
		"\7g\2\2\u00df\u00e0\7f\2\2\u00e0V\3\2\2\2\u00e1\u00e2\7w\2\2\u00e2\u00e3"+
		"\7p\2\2\u00e3\u00e4\7u\2\2\u00e4\u00e5\7k\2\2\u00e5\u00e6\7i\2\2\u00e6"+
		"\u00e7\7p\2\2\u00e7\u00e8\7g\2\2\u00e8\u00e9\7f\2\2\u00e9X\3\2\2\2\u00ea"+
		"\u00eb\7k\2\2\u00eb\u00ec\7p\2\2\u00ec\u00ed\7v\2\2\u00edZ\3\2\2\2\u00ee"+
		"\u00ef\7N\2\2\u00ef\u00f0\7Q\2\2\u00f0\u00f1\7P\2\2\u00f1\u00f2\7I\2\2"+
		"\u00f2\\\3\2\2\2\u00f3\u00f4\7u\2\2\u00f4\u00f5\7j\2\2\u00f5\u00f6\7q"+
		"\2\2\u00f6\u00f7\7t\2\2\u00f7\u00f8\7v\2\2\u00f8^\3\2\2\2\u00f9\u00fa"+
		"\7h\2\2\u00fa\u00fb\7n\2\2\u00fb\u00fc\7q\2\2\u00fc\u00fd\7c\2\2\u00fd"+
		"\u00fe\7v\2\2\u00fe`\3\2\2\2\u00ff\u0100\7f\2\2\u0100\u0101\7q\2\2\u0101"+
		"\u0102\7w\2\2\u0102\u0103\7d\2\2\u0103\u0104\7n\2\2\u0104\u0105\7g\2\2"+
		"\u0105b\3\2\2\2\u0106\u0107\7e\2\2\u0107\u0108\7j\2\2\u0108\u0109\7c\2"+
		"\2\u0109\u010a\7t\2\2\u010ad\3\2\2\2\u010b\u010c\7v\2\2\u010c\u010d\7"+
		"{\2\2\u010d\u010e\7r\2\2\u010e\u010f\7g\2\2\u010f\u0110\7q\2\2\u0110\u0111"+
		"\7h\2\2\u0111f\3\2\2\2\u0112\u0117\5\25\13\2\u0113\u0116\5\25\13\2\u0114"+
		"\u0116\5\27\f\2\u0115\u0113\3\2\2\2\u0115\u0114\3\2\2\2\u0116\u0119\3"+
		"\2\2\2\u0117\u0115\3\2\2\2\u0117\u0118\3\2\2\2\u0118h\3\2\2\2\u0119\u0117"+
		"\3\2\2\2\u011a\u011c\5\27\f\2\u011b\u011a\3\2\2\2\u011c\u011d\3\2\2\2"+
		"\u011d\u011b\3\2\2\2\u011d\u011e\3\2\2\2\u011ej\3\2\2\2\u011f\u0121\5"+
		"\27\f\2\u0120\u011f\3\2\2\2\u0121\u0122\3\2\2\2\u0122\u0120\3\2\2\2\u0122"+
		"\u0123\3\2\2\2\u0123\u0124\3\2\2\2\u0124\u0126\7\60\2\2\u0125\u0127\5"+
		"\27\f\2\u0126\u0125\3\2\2\2\u0127\u0128\3\2\2\2\u0128\u0126\3\2\2\2\u0128"+
		"\u0129\3\2\2\2\u0129\u0131\3\2\2\2\u012a\u012b\t\7\2\2\u012b\u012d\t\b"+
		"\2\2\u012c\u012e\5\27\f\2\u012d\u012c\3\2\2\2\u012e\u012f\3\2\2\2\u012f"+
		"\u012d\3\2\2\2\u012f\u0130\3\2\2\2\u0130\u0132\3\2\2\2\u0131\u012a\3\2"+
		"\2\2\u0131\u0132\3\2\2\2\u0132l\3\2\2\2\u0133\u0134\5\35\17\2\u0134\u0135"+
		"\5!\21\2\u0135\u0136\5\35\17\2\u0136\u0147\3\2\2\2\u0137\u0138\7N\2\2"+
		"\u0138\u0139\5\35\17\2\u0139\u013a\5!\21\2\u013a\u013b\5\35\17\2\u013b"+
		"\u0147\3\2\2\2\u013c\u013d\7w\2\2\u013d\u013e\5\35\17\2\u013e\u013f\5"+
		"!\21\2\u013f\u0140\5\35\17\2\u0140\u0147\3\2\2\2\u0141\u0142\7W\2\2\u0142"+
		"\u0143\5\35\17\2\u0143\u0144\5!\21\2\u0144\u0145\5\35\17\2\u0145\u0147"+
		"\3\2\2\2\u0146\u0133\3\2\2\2\u0146\u0137\3\2\2\2\u0146\u013c\3\2\2\2\u0146"+
		"\u0141\3\2\2\2\u0147n\3\2\2\2\u0148\u014a\t\t\2\2\u0149\u0148\3\2\2\2"+
		"\u014a\u014b\3\2\2\2\u014b\u0149\3\2\2\2\u014b\u014c\3\2\2\2\u014c\u014d"+
		"\3\2\2\2\u014d\u014e\b8\2\2\u014ep\3\2\2\2\r\2\u009c\u0115\u0117\u011d"+
		"\u0122\u0128\u012f\u0131\u0146\u014b\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
