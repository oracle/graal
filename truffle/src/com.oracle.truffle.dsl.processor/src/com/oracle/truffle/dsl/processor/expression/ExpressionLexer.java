/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

@SuppressWarnings({"all", "this-escape"})
public class ExpressionLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.12.0", RuntimeMetaData.VERSION); }

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
		"\u0004\u0000\u000f\u0082\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"+
		"\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"+
		"\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"+
		"\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"+
		"\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"+
		"\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"+
		"\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001"+
		"\n\u0001\n\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\r\u0003\rN\b\r\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001"+
		"\u0010\u0003\u0010U\b\u0010\u0001\u0011\u0001\u0011\u0001\u0012\u0001"+
		"\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013^\b\u0013\n\u0013"+
		"\f\u0013a\t\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014f\b\u0014"+
		"\n\u0014\f\u0014i\t\u0014\u0001\u0014\u0001\u0014\u0005\u0014m\b\u0014"+
		"\n\u0014\f\u0014p\t\u0014\u0001\u0014\u0005\u0014s\b\u0014\n\u0014\f\u0014"+
		"v\t\u0014\u0003\u0014x\b\u0014\u0001\u0014\u0001\u0014\u0005\u0014|\b"+
		"\u0014\n\u0014\f\u0014\u007f\t\u0014\u0003\u0014\u0081\b\u0014\u0000\u0000"+
		"\u0015\u0001\u0001\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006"+
		"\r\u0007\u000f\b\u0011\t\u0013\n\u0015\u000b\u0017\f\u0019\r\u001b\u0000"+
		"\u001d\u0000\u001f\u0000!\u0000#\u0000%\u0000\'\u000e)\u000f\u0001\u0000"+
		"\u0006\u0001\u0000  \u0004\u0000$$AZ__az\u0001\u000019\u0001\u000009\u0003"+
		"\u000009AFaf\u0001\u000007\u0085\u0000\u0001\u0001\u0000\u0000\u0000\u0000"+
		"\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000"+
		"\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b"+
		"\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001"+
		"\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001"+
		"\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000\u0000\u0017\u0001"+
		"\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000\u0000\'\u0001\u0000"+
		"\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0001+\u0001\u0000\u0000\u0000"+
		"\u0003.\u0001\u0000\u0000\u0000\u00050\u0001\u0000\u0000\u0000\u00073"+
		"\u0001\u0000\u0000\u0000\t5\u0001\u0000\u0000\u0000\u000b8\u0001\u0000"+
		"\u0000\u0000\r;\u0001\u0000\u0000\u0000\u000f>\u0001\u0000\u0000\u0000"+
		"\u0011@\u0001\u0000\u0000\u0000\u0013B\u0001\u0000\u0000\u0000\u0015D"+
		"\u0001\u0000\u0000\u0000\u0017F\u0001\u0000\u0000\u0000\u0019H\u0001\u0000"+
		"\u0000\u0000\u001bM\u0001\u0000\u0000\u0000\u001dO\u0001\u0000\u0000\u0000"+
		"\u001fQ\u0001\u0000\u0000\u0000!T\u0001\u0000\u0000\u0000#V\u0001\u0000"+
		"\u0000\u0000%X\u0001\u0000\u0000\u0000\'Z\u0001\u0000\u0000\u0000)\u0080"+
		"\u0001\u0000\u0000\u0000+,\u0005|\u0000\u0000,-\u0005|\u0000\u0000-\u0002"+
		"\u0001\u0000\u0000\u0000./\u0005<\u0000\u0000/\u0004\u0001\u0000\u0000"+
		"\u000001\u0005<\u0000\u000012\u0005=\u0000\u00002\u0006\u0001\u0000\u0000"+
		"\u000034\u0005>\u0000\u00004\b\u0001\u0000\u0000\u000056\u0005>\u0000"+
		"\u000067\u0005=\u0000\u00007\n\u0001\u0000\u0000\u000089\u0005=\u0000"+
		"\u00009:\u0005=\u0000\u0000:\f\u0001\u0000\u0000\u0000;<\u0005!\u0000"+
		"\u0000<=\u0005=\u0000\u0000=\u000e\u0001\u0000\u0000\u0000>?\u0005!\u0000"+
		"\u0000?\u0010\u0001\u0000\u0000\u0000@A\u0005(\u0000\u0000A\u0012\u0001"+
		"\u0000\u0000\u0000BC\u0005)\u0000\u0000C\u0014\u0001\u0000\u0000\u0000"+
		"DE\u0005,\u0000\u0000E\u0016\u0001\u0000\u0000\u0000FG\u0005.\u0000\u0000"+
		"G\u0018\u0001\u0000\u0000\u0000HI\u0007\u0000\u0000\u0000IJ\u0001\u0000"+
		"\u0000\u0000JK\u0006\f\u0000\u0000K\u001a\u0001\u0000\u0000\u0000LN\u0007"+
		"\u0001\u0000\u0000ML\u0001\u0000\u0000\u0000N\u001c\u0001\u0000\u0000"+
		"\u0000OP\u0007\u0002\u0000\u0000P\u001e\u0001\u0000\u0000\u0000QR\u0007"+
		"\u0003\u0000\u0000R \u0001\u0000\u0000\u0000SU\u0007\u0004\u0000\u0000"+
		"TS\u0001\u0000\u0000\u0000U\"\u0001\u0000\u0000\u0000VW\u0007\u0005\u0000"+
		"\u0000W$\u0001\u0000\u0000\u0000XY\u000201\u0000Y&\u0001\u0000\u0000\u0000"+
		"Z_\u0003\u001b\r\u0000[^\u0003\u001b\r\u0000\\^\u0003\u001f\u000f\u0000"+
		"][\u0001\u0000\u0000\u0000]\\\u0001\u0000\u0000\u0000^a\u0001\u0000\u0000"+
		"\u0000_]\u0001\u0000\u0000\u0000_`\u0001\u0000\u0000\u0000`(\u0001\u0000"+
		"\u0000\u0000a_\u0001\u0000\u0000\u0000bw\u00050\u0000\u0000cg\u0005x\u0000"+
		"\u0000df\u0003!\u0010\u0000ed\u0001\u0000\u0000\u0000fi\u0001\u0000\u0000"+
		"\u0000ge\u0001\u0000\u0000\u0000gh\u0001\u0000\u0000\u0000hx\u0001\u0000"+
		"\u0000\u0000ig\u0001\u0000\u0000\u0000jn\u0005b\u0000\u0000km\u0003%\u0012"+
		"\u0000lk\u0001\u0000\u0000\u0000mp\u0001\u0000\u0000\u0000nl\u0001\u0000"+
		"\u0000\u0000no\u0001\u0000\u0000\u0000ox\u0001\u0000\u0000\u0000pn\u0001"+
		"\u0000\u0000\u0000qs\u0003#\u0011\u0000rq\u0001\u0000\u0000\u0000sv\u0001"+
		"\u0000\u0000\u0000tr\u0001\u0000\u0000\u0000tu\u0001\u0000\u0000\u0000"+
		"ux\u0001\u0000\u0000\u0000vt\u0001\u0000\u0000\u0000wc\u0001\u0000\u0000"+
		"\u0000wj\u0001\u0000\u0000\u0000wt\u0001\u0000\u0000\u0000wx\u0001\u0000"+
		"\u0000\u0000x\u0081\u0001\u0000\u0000\u0000y}\u0003\u001d\u000e\u0000"+
		"z|\u0003\u001f\u000f\u0000{z\u0001\u0000\u0000\u0000|\u007f\u0001\u0000"+
		"\u0000\u0000}{\u0001\u0000\u0000\u0000}~\u0001\u0000\u0000\u0000~\u0081"+
		"\u0001\u0000\u0000\u0000\u007f}\u0001\u0000\u0000\u0000\u0080b\u0001\u0000"+
		"\u0000\u0000\u0080y\u0001\u0000\u0000\u0000\u0081*\u0001\u0000\u0000\u0000"+
		"\u000b\u0000MT]_gntw}\u0080\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
