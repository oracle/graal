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
		DIGIT=10, CR=11, LF=12, SINGLECOMMA=13, QUOTE=14, IDENT=15, NUMBER=16, 
		FLOATNUMBER=17, CHARCONST=18, WS=19;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
		"LETTER", "DIGIT", "CR", "LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", 
		"FLOATNUMBER", "CHARCONST", "WS"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'('", "')'", "'['", "']'", "'.'", "'->'", "','", "'?'", "':'", 
		null, "'\r'", "'\n'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, "DIGIT", "CR", 
		"LF", "SINGLECOMMA", "QUOTE", "IDENT", "NUMBER", "FLOATNUMBER", "CHARCONST", 
		"WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\25y\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6"+
		"\3\7\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3\16\3"+
		"\16\3\17\3\17\3\20\3\20\3\21\3\21\3\21\7\21N\n\21\f\21\16\21Q\13\21\3"+
		"\22\6\22T\n\22\r\22\16\22U\3\23\6\23Y\n\23\r\23\16\23Z\3\23\3\23\6\23"+
		"_\n\23\r\23\16\23`\3\23\3\23\3\23\6\23f\n\23\r\23\16\23g\5\23j\n\23\3"+
		"\24\3\24\3\24\5\24o\n\24\3\24\3\24\3\25\6\25t\n\25\r\25\16\25u\3\25\3"+
		"\25\2\2\26\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\2\27\f\31\r\33"+
		"\16\35\17\37\20!\21#\22%\23\'\24)\25\3\2\7\4\2C\\c|\3\2\62;\4\2GGgg\4"+
		"\2--//\5\2\13\f\17\17\"\"\2\u0080\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2"+
		"\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3"+
		"\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2"+
		"\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\3+\3\2\2\2"+
		"\5-\3\2\2\2\7/\3\2\2\2\t\61\3\2\2\2\13\63\3\2\2\2\r\65\3\2\2\2\178\3\2"+
		"\2\2\21:\3\2\2\2\23<\3\2\2\2\25>\3\2\2\2\27@\3\2\2\2\31B\3\2\2\2\33D\3"+
		"\2\2\2\35F\3\2\2\2\37H\3\2\2\2!J\3\2\2\2#S\3\2\2\2%X\3\2\2\2\'k\3\2\2"+
		"\2)s\3\2\2\2+,\7*\2\2,\4\3\2\2\2-.\7+\2\2.\6\3\2\2\2/\60\7]\2\2\60\b\3"+
		"\2\2\2\61\62\7_\2\2\62\n\3\2\2\2\63\64\7\60\2\2\64\f\3\2\2\2\65\66\7/"+
		"\2\2\66\67\7@\2\2\67\16\3\2\2\289\7.\2\29\20\3\2\2\2:;\7A\2\2;\22\3\2"+
		"\2\2<=\7<\2\2=\24\3\2\2\2>?\t\2\2\2?\26\3\2\2\2@A\t\3\2\2A\30\3\2\2\2"+
		"BC\7\17\2\2C\32\3\2\2\2DE\7\f\2\2E\34\3\2\2\2FG\7)\2\2G\36\3\2\2\2HI\7"+
		"$\2\2I \3\2\2\2JO\5\25\13\2KN\5\25\13\2LN\5\27\f\2MK\3\2\2\2ML\3\2\2\2"+
		"NQ\3\2\2\2OM\3\2\2\2OP\3\2\2\2P\"\3\2\2\2QO\3\2\2\2RT\5\27\f\2SR\3\2\2"+
		"\2TU\3\2\2\2US\3\2\2\2UV\3\2\2\2V$\3\2\2\2WY\5\27\f\2XW\3\2\2\2YZ\3\2"+
		"\2\2ZX\3\2\2\2Z[\3\2\2\2[\\\3\2\2\2\\^\7\60\2\2]_\5\27\f\2^]\3\2\2\2_"+
		"`\3\2\2\2`^\3\2\2\2`a\3\2\2\2ai\3\2\2\2bc\t\4\2\2ce\t\5\2\2df\5\27\f\2"+
		"ed\3\2\2\2fg\3\2\2\2ge\3\2\2\2gh\3\2\2\2hj\3\2\2\2ib\3\2\2\2ij\3\2\2\2"+
		"j&\3\2\2\2kn\5\35\17\2lo\5\25\13\2mo\5\27\f\2nl\3\2\2\2nm\3\2\2\2op\3"+
		"\2\2\2pq\5\35\17\2q(\3\2\2\2rt\t\6\2\2sr\3\2\2\2tu\3\2\2\2us\3\2\2\2u"+
		"v\3\2\2\2vw\3\2\2\2wx\b\25\2\2x*\3\2\2\2\f\2MOUZ`ginu\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
