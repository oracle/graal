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

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "this-escape"})
public class SimpleLanguageLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, WS=19, COMMENT=20, LINE_COMMENT=21, OP_OR=22, OP_AND=23, OP_COMPARE=24, 
		OP_ADD=25, OP_MUL=26, IDENTIFIER=27, STRING_LITERAL=28, NUMERIC_LITERAL=29;
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
			"T__17", "WS", "COMMENT", "LINE_COMMENT", "OP_OR", "OP_AND", "OP_COMPARE", 
			"OP_ADD", "OP_MUL", "LETTER", "NON_ZERO_DIGIT", "DIGIT", "HEX_DIGIT", 
			"OCT_DIGIT", "BINARY_DIGIT", "TAB", "STRING_CHAR", "IDENTIFIER", "STRING_LITERAL", 
			"NUMERIC_LITERAL"
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
		"\u0004\u0000\u001d\u00f8\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"+
		"\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"+
		"\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"+
		"\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"+
		"\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"+
		"\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"+
		"\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"+
		"\u0002\u0015\u0007\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017"+
		"\u0002\u0018\u0007\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a"+
		"\u0002\u001b\u0007\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d"+
		"\u0002\u001e\u0007\u001e\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!"+
		"\u0007!\u0002\"\u0007\"\u0002#\u0007#\u0002$\u0007$\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001"+
		"\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001"+
		"\u000e\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0011\u0001"+
		"\u0011\u0001\u0012\u0004\u0012\u0097\b\u0012\u000b\u0012\f\u0012\u0098"+
		"\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0005\u0013\u00a1\b\u0013\n\u0013\f\u0013\u00a4\t\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0005\u0014\u00af\b\u0014\n\u0014\f\u0014\u00b2\t\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0003\u0017\u00c6\b\u0017\u0001\u0018\u0001\u0018\u0001\u0019\u0001\u0019"+
		"\u0001\u001a\u0003\u001a\u00cd\b\u001a\u0001\u001b\u0001\u001b\u0001\u001c"+
		"\u0001\u001c\u0001\u001d\u0003\u001d\u00d4\b\u001d\u0001\u001e\u0001\u001e"+
		"\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001!\u0001!\u0001\"\u0001\"\u0001"+
		"\"\u0005\"\u00e1\b\"\n\"\f\"\u00e4\t\"\u0001#\u0001#\u0005#\u00e8\b#\n"+
		"#\f#\u00eb\t#\u0001#\u0001#\u0001$\u0001$\u0001$\u0005$\u00f2\b$\n$\f"+
		"$\u00f5\t$\u0003$\u00f7\b$\u0001\u00a2\u0000%\u0001\u0001\u0003\u0002"+
		"\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u000f\b\u0011\t\u0013"+
		"\n\u0015\u000b\u0017\f\u0019\r\u001b\u000e\u001d\u000f\u001f\u0010!\u0011"+
		"#\u0012%\u0013\'\u0014)\u0015+\u0016-\u0017/\u00181\u00193\u001a5\u0000"+
		"7\u00009\u0000;\u0000=\u0000?\u0000A\u0000C\u0000E\u001bG\u001cI\u001d"+
		"\u0001\u0000\n\u0003\u0000\t\n\f\r  \u0002\u0000\n\n\r\r\u0002\u0000+"+
		"+--\u0002\u0000**//\u0004\u0000$$AZ__az\u0001\u000019\u0001\u000009\u0003"+
		"\u000009AFaf\u0001\u000007\u0003\u0000\n\n\r\r\"\"\u00fc\u0000\u0001\u0001"+
		"\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001"+
		"\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000"+
		"\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000"+
		"\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000"+
		"\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000"+
		"\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000"+
		"\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000\u001d\u0001\u0000\u0000"+
		"\u0000\u0000\u001f\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000"+
		"\u0000#\u0001\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'"+
		"\u0001\u0000\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0000+\u0001\u0000"+
		"\u0000\u0000\u0000-\u0001\u0000\u0000\u0000\u0000/\u0001\u0000\u0000\u0000"+
		"\u00001\u0001\u0000\u0000\u0000\u00003\u0001\u0000\u0000\u0000\u0000E"+
		"\u0001\u0000\u0000\u0000\u0000G\u0001\u0000\u0000\u0000\u0000I\u0001\u0000"+
		"\u0000\u0000\u0001K\u0001\u0000\u0000\u0000\u0003T\u0001\u0000\u0000\u0000"+
		"\u0005V\u0001\u0000\u0000\u0000\u0007X\u0001\u0000\u0000\u0000\tZ\u0001"+
		"\u0000\u0000\u0000\u000b\\\u0001\u0000\u0000\u0000\r^\u0001\u0000\u0000"+
		"\u0000\u000fd\u0001\u0000\u0000\u0000\u0011f\u0001\u0000\u0000\u0000\u0013"+
		"o\u0001\u0000\u0000\u0000\u0015x\u0001\u0000\u0000\u0000\u0017~\u0001"+
		"\u0000\u0000\u0000\u0019\u0081\u0001\u0000\u0000\u0000\u001b\u0086\u0001"+
		"\u0000\u0000\u0000\u001d\u008d\u0001\u0000\u0000\u0000\u001f\u008f\u0001"+
		"\u0000\u0000\u0000!\u0091\u0001\u0000\u0000\u0000#\u0093\u0001\u0000\u0000"+
		"\u0000%\u0096\u0001\u0000\u0000\u0000\'\u009c\u0001\u0000\u0000\u0000"+
		")\u00aa\u0001\u0000\u0000\u0000+\u00b5\u0001\u0000\u0000\u0000-\u00b8"+
		"\u0001\u0000\u0000\u0000/\u00c5\u0001\u0000\u0000\u00001\u00c7\u0001\u0000"+
		"\u0000\u00003\u00c9\u0001\u0000\u0000\u00005\u00cc\u0001\u0000\u0000\u0000"+
		"7\u00ce\u0001\u0000\u0000\u00009\u00d0\u0001\u0000\u0000\u0000;\u00d3"+
		"\u0001\u0000\u0000\u0000=\u00d5\u0001\u0000\u0000\u0000?\u00d7\u0001\u0000"+
		"\u0000\u0000A\u00d9\u0001\u0000\u0000\u0000C\u00db\u0001\u0000\u0000\u0000"+
		"E\u00dd\u0001\u0000\u0000\u0000G\u00e5\u0001\u0000\u0000\u0000I\u00f6"+
		"\u0001\u0000\u0000\u0000KL\u0005f\u0000\u0000LM\u0005u\u0000\u0000MN\u0005"+
		"n\u0000\u0000NO\u0005c\u0000\u0000OP\u0005t\u0000\u0000PQ\u0005i\u0000"+
		"\u0000QR\u0005o\u0000\u0000RS\u0005n\u0000\u0000S\u0002\u0001\u0000\u0000"+
		"\u0000TU\u0005(\u0000\u0000U\u0004\u0001\u0000\u0000\u0000VW\u0005,\u0000"+
		"\u0000W\u0006\u0001\u0000\u0000\u0000XY\u0005)\u0000\u0000Y\b\u0001\u0000"+
		"\u0000\u0000Z[\u0005{\u0000\u0000[\n\u0001\u0000\u0000\u0000\\]\u0005"+
		"}\u0000\u0000]\f\u0001\u0000\u0000\u0000^_\u0005b\u0000\u0000_`\u0005"+
		"r\u0000\u0000`a\u0005e\u0000\u0000ab\u0005a\u0000\u0000bc\u0005k\u0000"+
		"\u0000c\u000e\u0001\u0000\u0000\u0000de\u0005;\u0000\u0000e\u0010\u0001"+
		"\u0000\u0000\u0000fg\u0005c\u0000\u0000gh\u0005o\u0000\u0000hi\u0005n"+
		"\u0000\u0000ij\u0005t\u0000\u0000jk\u0005i\u0000\u0000kl\u0005n\u0000"+
		"\u0000lm\u0005u\u0000\u0000mn\u0005e\u0000\u0000n\u0012\u0001\u0000\u0000"+
		"\u0000op\u0005d\u0000\u0000pq\u0005e\u0000\u0000qr\u0005b\u0000\u0000"+
		"rs\u0005u\u0000\u0000st\u0005g\u0000\u0000tu\u0005g\u0000\u0000uv\u0005"+
		"e\u0000\u0000vw\u0005r\u0000\u0000w\u0014\u0001\u0000\u0000\u0000xy\u0005"+
		"w\u0000\u0000yz\u0005h\u0000\u0000z{\u0005i\u0000\u0000{|\u0005l\u0000"+
		"\u0000|}\u0005e\u0000\u0000}\u0016\u0001\u0000\u0000\u0000~\u007f\u0005"+
		"i\u0000\u0000\u007f\u0080\u0005f\u0000\u0000\u0080\u0018\u0001\u0000\u0000"+
		"\u0000\u0081\u0082\u0005e\u0000\u0000\u0082\u0083\u0005l\u0000\u0000\u0083"+
		"\u0084\u0005s\u0000\u0000\u0084\u0085\u0005e\u0000\u0000\u0085\u001a\u0001"+
		"\u0000\u0000\u0000\u0086\u0087\u0005r\u0000\u0000\u0087\u0088\u0005e\u0000"+
		"\u0000\u0088\u0089\u0005t\u0000\u0000\u0089\u008a\u0005u\u0000\u0000\u008a"+
		"\u008b\u0005r\u0000\u0000\u008b\u008c\u0005n\u0000\u0000\u008c\u001c\u0001"+
		"\u0000\u0000\u0000\u008d\u008e\u0005=\u0000\u0000\u008e\u001e\u0001\u0000"+
		"\u0000\u0000\u008f\u0090\u0005.\u0000\u0000\u0090 \u0001\u0000\u0000\u0000"+
		"\u0091\u0092\u0005[\u0000\u0000\u0092\"\u0001\u0000\u0000\u0000\u0093"+
		"\u0094\u0005]\u0000\u0000\u0094$\u0001\u0000\u0000\u0000\u0095\u0097\u0007"+
		"\u0000\u0000\u0000\u0096\u0095\u0001\u0000\u0000\u0000\u0097\u0098\u0001"+
		"\u0000\u0000\u0000\u0098\u0096\u0001\u0000\u0000\u0000\u0098\u0099\u0001"+
		"\u0000\u0000\u0000\u0099\u009a\u0001\u0000\u0000\u0000\u009a\u009b\u0006"+
		"\u0012\u0000\u0000\u009b&\u0001\u0000\u0000\u0000\u009c\u009d\u0005/\u0000"+
		"\u0000\u009d\u009e\u0005*\u0000\u0000\u009e\u00a2\u0001\u0000\u0000\u0000"+
		"\u009f\u00a1\t\u0000\u0000\u0000\u00a0\u009f\u0001\u0000\u0000\u0000\u00a1"+
		"\u00a4\u0001\u0000\u0000\u0000\u00a2\u00a3\u0001\u0000\u0000\u0000\u00a2"+
		"\u00a0\u0001\u0000\u0000\u0000\u00a3\u00a5\u0001\u0000\u0000\u0000\u00a4"+
		"\u00a2\u0001\u0000\u0000\u0000\u00a5\u00a6\u0005*\u0000\u0000\u00a6\u00a7"+
		"\u0005/\u0000\u0000\u00a7\u00a8\u0001\u0000\u0000\u0000\u00a8\u00a9\u0006"+
		"\u0013\u0000\u0000\u00a9(\u0001\u0000\u0000\u0000\u00aa\u00ab\u0005/\u0000"+
		"\u0000\u00ab\u00ac\u0005/\u0000\u0000\u00ac\u00b0\u0001\u0000\u0000\u0000"+
		"\u00ad\u00af\b\u0001\u0000\u0000\u00ae\u00ad\u0001\u0000\u0000\u0000\u00af"+
		"\u00b2\u0001\u0000\u0000\u0000\u00b0\u00ae\u0001\u0000\u0000\u0000\u00b0"+
		"\u00b1\u0001\u0000\u0000\u0000\u00b1\u00b3\u0001\u0000\u0000\u0000\u00b2"+
		"\u00b0\u0001\u0000\u0000\u0000\u00b3\u00b4\u0006\u0014\u0000\u0000\u00b4"+
		"*\u0001\u0000\u0000\u0000\u00b5\u00b6\u0005|\u0000\u0000\u00b6\u00b7\u0005"+
		"|\u0000\u0000\u00b7,\u0001\u0000\u0000\u0000\u00b8\u00b9\u0005&\u0000"+
		"\u0000\u00b9\u00ba\u0005&\u0000\u0000\u00ba.\u0001\u0000\u0000\u0000\u00bb"+
		"\u00c6\u0005<\u0000\u0000\u00bc\u00bd\u0005<\u0000\u0000\u00bd\u00c6\u0005"+
		"=\u0000\u0000\u00be\u00c6\u0005>\u0000\u0000\u00bf\u00c0\u0005>\u0000"+
		"\u0000\u00c0\u00c6\u0005=\u0000\u0000\u00c1\u00c2\u0005=\u0000\u0000\u00c2"+
		"\u00c6\u0005=\u0000\u0000\u00c3\u00c4\u0005!\u0000\u0000\u00c4\u00c6\u0005"+
		"=\u0000\u0000\u00c5\u00bb\u0001\u0000\u0000\u0000\u00c5\u00bc\u0001\u0000"+
		"\u0000\u0000\u00c5\u00be\u0001\u0000\u0000\u0000\u00c5\u00bf\u0001\u0000"+
		"\u0000\u0000\u00c5\u00c1\u0001\u0000\u0000\u0000\u00c5\u00c3\u0001\u0000"+
		"\u0000\u0000\u00c60\u0001\u0000\u0000\u0000\u00c7\u00c8\u0007\u0002\u0000"+
		"\u0000\u00c82\u0001\u0000\u0000\u0000\u00c9\u00ca\u0007\u0003\u0000\u0000"+
		"\u00ca4\u0001\u0000\u0000\u0000\u00cb\u00cd\u0007\u0004\u0000\u0000\u00cc"+
		"\u00cb\u0001\u0000\u0000\u0000\u00cd6\u0001\u0000\u0000\u0000\u00ce\u00cf"+
		"\u0007\u0005\u0000\u0000\u00cf8\u0001\u0000\u0000\u0000\u00d0\u00d1\u0007"+
		"\u0006\u0000\u0000\u00d1:\u0001\u0000\u0000\u0000\u00d2\u00d4\u0007\u0007"+
		"\u0000\u0000\u00d3\u00d2\u0001\u0000\u0000\u0000\u00d4<\u0001\u0000\u0000"+
		"\u0000\u00d5\u00d6\u0007\b\u0000\u0000\u00d6>\u0001\u0000\u0000\u0000"+
		"\u00d7\u00d8\u000201\u0000\u00d8@\u0001\u0000\u0000\u0000\u00d9\u00da"+
		"\u0005\t\u0000\u0000\u00daB\u0001\u0000\u0000\u0000\u00db\u00dc\b\t\u0000"+
		"\u0000\u00dcD\u0001\u0000\u0000\u0000\u00dd\u00e2\u00035\u001a\u0000\u00de"+
		"\u00e1\u00035\u001a\u0000\u00df\u00e1\u00039\u001c\u0000\u00e0\u00de\u0001"+
		"\u0000\u0000\u0000\u00e0\u00df\u0001\u0000\u0000\u0000\u00e1\u00e4\u0001"+
		"\u0000\u0000\u0000\u00e2\u00e0\u0001\u0000\u0000\u0000\u00e2\u00e3\u0001"+
		"\u0000\u0000\u0000\u00e3F\u0001\u0000\u0000\u0000\u00e4\u00e2\u0001\u0000"+
		"\u0000\u0000\u00e5\u00e9\u0005\"\u0000\u0000\u00e6\u00e8\u0003C!\u0000"+
		"\u00e7\u00e6\u0001\u0000\u0000\u0000\u00e8\u00eb\u0001\u0000\u0000\u0000"+
		"\u00e9\u00e7\u0001\u0000\u0000\u0000\u00e9\u00ea\u0001\u0000\u0000\u0000"+
		"\u00ea\u00ec\u0001\u0000\u0000\u0000\u00eb\u00e9\u0001\u0000\u0000\u0000"+
		"\u00ec\u00ed\u0005\"\u0000\u0000\u00edH\u0001\u0000\u0000\u0000\u00ee"+
		"\u00f7\u00050\u0000\u0000\u00ef\u00f3\u00037\u001b\u0000\u00f0\u00f2\u0003"+
		"9\u001c\u0000\u00f1\u00f0\u0001\u0000\u0000\u0000\u00f2\u00f5\u0001\u0000"+
		"\u0000\u0000\u00f3\u00f1\u0001\u0000\u0000\u0000\u00f3\u00f4\u0001\u0000"+
		"\u0000\u0000\u00f4\u00f7\u0001\u0000\u0000\u0000\u00f5\u00f3\u0001\u0000"+
		"\u0000\u0000\u00f6\u00ee\u0001\u0000\u0000\u0000\u00f6\u00ef\u0001\u0000"+
		"\u0000\u0000\u00f7J\u0001\u0000\u0000\u0000\f\u0000\u0098\u00a2\u00b0"+
		"\u00c5\u00cc\u00d3\u00e0\u00e2\u00e9\u00f3\u00f6\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
