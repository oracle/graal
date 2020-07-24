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
package com.oracle.truffle.dsl.processor.expression;

// DO NOT MODIFY - generated from Expression.g4 using "mx create-dsl-parser"

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings("all")
public class ExpressionLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, WS=13, IDENTIFIER=14, NUMERIC_LITERAL=15;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "WS", "LETTER", "NON_ZERO_DIGIT", "DIGIT", 
			"HEX_DIGIT", "OCT_DIGIT", "BINARY_DIGIT", "IDENTIFIER", "NUMERIC_LITERAL"
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


	public ExpressionLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Expression.g4"; }

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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\21\u0084\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\3\2\3\2\3\2\3\3\3\3\3\4"+
		"\3\4\3\4\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3\7\3\b\3\b\3\b\3\t\3\t\3\n\3\n\3"+
		"\13\3\13\3\f\3\f\3\r\3\r\3\16\3\16\3\16\3\16\3\17\5\17P\n\17\3\20\3\20"+
		"\3\21\3\21\3\22\5\22W\n\22\3\23\3\23\3\24\3\24\3\25\3\25\3\25\7\25`\n"+
		"\25\f\25\16\25c\13\25\3\26\3\26\3\26\7\26h\n\26\f\26\16\26k\13\26\3\26"+
		"\3\26\7\26o\n\26\f\26\16\26r\13\26\3\26\7\26u\n\26\f\26\16\26x\13\26\5"+
		"\26z\n\26\3\26\3\26\7\26~\n\26\f\26\16\26\u0081\13\26\5\26\u0083\n\26"+
		"\2\2\27\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17"+
		"\35\2\37\2!\2#\2%\2\'\2)\20+\21\3\2\b\3\2\"\"\6\2&&C\\aac|\3\2\63;\3\2"+
		"\62;\5\2\62;CHch\3\2\629\2\u0087\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2"+
		"\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2"+
		"\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2)\3\2\2\2\2"+
		"+\3\2\2\2\3-\3\2\2\2\5\60\3\2\2\2\7\62\3\2\2\2\t\65\3\2\2\2\13\67\3\2"+
		"\2\2\r:\3\2\2\2\17=\3\2\2\2\21@\3\2\2\2\23B\3\2\2\2\25D\3\2\2\2\27F\3"+
		"\2\2\2\31H\3\2\2\2\33J\3\2\2\2\35O\3\2\2\2\37Q\3\2\2\2!S\3\2\2\2#V\3\2"+
		"\2\2%X\3\2\2\2\'Z\3\2\2\2)\\\3\2\2\2+\u0082\3\2\2\2-.\7~\2\2./\7~\2\2"+
		"/\4\3\2\2\2\60\61\7>\2\2\61\6\3\2\2\2\62\63\7>\2\2\63\64\7?\2\2\64\b\3"+
		"\2\2\2\65\66\7@\2\2\66\n\3\2\2\2\678\7@\2\289\7?\2\29\f\3\2\2\2:;\7?\2"+
		"\2;<\7?\2\2<\16\3\2\2\2=>\7#\2\2>?\7?\2\2?\20\3\2\2\2@A\7#\2\2A\22\3\2"+
		"\2\2BC\7*\2\2C\24\3\2\2\2DE\7+\2\2E\26\3\2\2\2FG\7.\2\2G\30\3\2\2\2HI"+
		"\7\60\2\2I\32\3\2\2\2JK\t\2\2\2KL\3\2\2\2LM\b\16\2\2M\34\3\2\2\2NP\t\3"+
		"\2\2ON\3\2\2\2P\36\3\2\2\2QR\t\4\2\2R \3\2\2\2ST\t\5\2\2T\"\3\2\2\2UW"+
		"\t\6\2\2VU\3\2\2\2W$\3\2\2\2XY\t\7\2\2Y&\3\2\2\2Z[\4\62\63\2[(\3\2\2\2"+
		"\\a\5\35\17\2]`\5\35\17\2^`\5!\21\2_]\3\2\2\2_^\3\2\2\2`c\3\2\2\2a_\3"+
		"\2\2\2ab\3\2\2\2b*\3\2\2\2ca\3\2\2\2dy\7\62\2\2ei\7z\2\2fh\5#\22\2gf\3"+
		"\2\2\2hk\3\2\2\2ig\3\2\2\2ij\3\2\2\2jz\3\2\2\2ki\3\2\2\2lp\7d\2\2mo\5"+
		"\'\24\2nm\3\2\2\2or\3\2\2\2pn\3\2\2\2pq\3\2\2\2qz\3\2\2\2rp\3\2\2\2su"+
		"\5%\23\2ts\3\2\2\2ux\3\2\2\2vt\3\2\2\2vw\3\2\2\2wz\3\2\2\2xv\3\2\2\2y"+
		"e\3\2\2\2yl\3\2\2\2yv\3\2\2\2yz\3\2\2\2z\u0083\3\2\2\2{\177\5\37\20\2"+
		"|~\5!\21\2}|\3\2\2\2~\u0081\3\2\2\2\177}\3\2\2\2\177\u0080\3\2\2\2\u0080"+
		"\u0083\3\2\2\2\u0081\177\3\2\2\2\u0082d\3\2\2\2\u0082{\3\2\2\2\u0083,"+
		"\3\2\2\2\r\2OV_aipvy\177\u0082\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
