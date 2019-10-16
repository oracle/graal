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
		T__0=1, T__1=2, T__2=3, T__3=4, DIGIT=5, CR=6, LF=7, SINGLECOMMA=8, QUOTE=9, 
		IDENT=10, NUMBER=11, FLOATNUMBER=12, CHARCONST=13, WS=14;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "LETTER", "DIGIT", "CR", "LF", "SINGLECOMMA", 
		"QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", "WS"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'('", "')'", "'?'", "':'", null, "'\r'", "'\n'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, "DIGIT", "CR", "LF", "SINGLECOMMA", "QUOTE", 
		"IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", "WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\20d\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\3\2\3\2\3\3\3\3\3\4"+
		"\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f"+
		"\3\f\7\f9\n\f\f\f\16\f<\13\f\3\r\6\r?\n\r\r\r\16\r@\3\16\6\16D\n\16\r"+
		"\16\16\16E\3\16\3\16\6\16J\n\16\r\16\16\16K\3\16\3\16\3\16\6\16Q\n\16"+
		"\r\16\16\16R\5\16U\n\16\3\17\3\17\3\17\5\17Z\n\17\3\17\3\17\3\20\6\20"+
		"_\n\20\r\20\16\20`\3\20\3\20\2\2\21\3\3\5\4\7\5\t\6\13\2\r\7\17\b\21\t"+
		"\23\n\25\13\27\f\31\r\33\16\35\17\37\20\3\2\7\4\2C\\c|\3\2\62;\4\2GGg"+
		"g\4\2--//\5\2\13\f\17\17\"\"\2k\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2"+
		"\t\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2"+
		"\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2"+
		"\3!\3\2\2\2\5#\3\2\2\2\7%\3\2\2\2\t\'\3\2\2\2\13)\3\2\2\2\r+\3\2\2\2\17"+
		"-\3\2\2\2\21/\3\2\2\2\23\61\3\2\2\2\25\63\3\2\2\2\27\65\3\2\2\2\31>\3"+
		"\2\2\2\33C\3\2\2\2\35V\3\2\2\2\37^\3\2\2\2!\"\7*\2\2\"\4\3\2\2\2#$\7+"+
		"\2\2$\6\3\2\2\2%&\7A\2\2&\b\3\2\2\2\'(\7<\2\2(\n\3\2\2\2)*\t\2\2\2*\f"+
		"\3\2\2\2+,\t\3\2\2,\16\3\2\2\2-.\7\17\2\2.\20\3\2\2\2/\60\7\f\2\2\60\22"+
		"\3\2\2\2\61\62\7)\2\2\62\24\3\2\2\2\63\64\7$\2\2\64\26\3\2\2\2\65:\5\13"+
		"\6\2\669\5\13\6\2\679\5\r\7\28\66\3\2\2\28\67\3\2\2\29<\3\2\2\2:8\3\2"+
		"\2\2:;\3\2\2\2;\30\3\2\2\2<:\3\2\2\2=?\5\r\7\2>=\3\2\2\2?@\3\2\2\2@>\3"+
		"\2\2\2@A\3\2\2\2A\32\3\2\2\2BD\5\r\7\2CB\3\2\2\2DE\3\2\2\2EC\3\2\2\2E"+
		"F\3\2\2\2FG\3\2\2\2GI\7\60\2\2HJ\5\r\7\2IH\3\2\2\2JK\3\2\2\2KI\3\2\2\2"+
		"KL\3\2\2\2LT\3\2\2\2MN\t\4\2\2NP\t\5\2\2OQ\5\r\7\2PO\3\2\2\2QR\3\2\2\2"+
		"RP\3\2\2\2RS\3\2\2\2SU\3\2\2\2TM\3\2\2\2TU\3\2\2\2U\34\3\2\2\2VY\5\23"+
		"\n\2WZ\5\13\6\2XZ\5\r\7\2YW\3\2\2\2YX\3\2\2\2Z[\3\2\2\2[\\\5\23\n\2\\"+
		"\36\3\2\2\2]_\t\6\2\2^]\3\2\2\2_`\3\2\2\2`^\3\2\2\2`a\3\2\2\2ab\3\2\2"+
		"\2bc\b\20\2\2c \3\2\2\2\f\28:@EKRTY`\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
