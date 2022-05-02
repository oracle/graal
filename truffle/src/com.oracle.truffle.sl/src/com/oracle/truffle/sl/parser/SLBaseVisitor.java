package com.oracle.truffle.sl.parser;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLStrings;

public abstract class SLBaseVisitor extends SimpleLanguageOperationsBaseVisitor<Void> {

    protected static Map<TruffleString, RootCallTarget> parseSLImpl(SLSource source, SLBaseVisitor visitor) {
        SimpleLanguageOperationsLexer lexer = new SimpleLanguageOperationsLexer(CharStreams.fromString(source.getSource().getCharacters().toString()));
        SimpleLanguageOperationsParser parser = new SimpleLanguageOperationsParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        BailoutErrorListener listener = new BailoutErrorListener(source.getSource());
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        parser.simplelanguage().accept(visitor);

        if (source.getFunctions() == null) {
            source.setFunctions(visitor.functions);
        }

        return visitor.functions;
    }

    static class LexicalScope {
        int count;
        final LexicalScope parent;
        Map<TruffleString, Integer> names = new HashMap<>();

        public LexicalScope(LexicalScope parent) {
            this.parent = parent;
            count = parent != null ? parent.count : 0;
        }

        public Integer get(TruffleString name) {
            Integer result = names.get(name);
            if (result != null) {
                return result;
            } else if (parent != null) {
                return parent.get(name);
            } else {
                return null;
            }
        }

        public Integer create(TruffleString name) {
            int value = count++;
            names.put(name, value);
            return value;
        }

        public Integer create() {
            return count++;
        }
    }

    protected final SLLanguage language;
    protected final SLSource source;
    protected final TruffleString sourceString;
    protected final Map<TruffleString, RootCallTarget> functions = new HashMap<>();

    protected SLBaseVisitor(SLLanguage language, SLSource source) {
        this.language = language;
        this.source = source;
        sourceString = SLStrings.fromJavaString(source.getSource().getCharacters().toString());
    }

    protected void SemErr(Token token, String message) {
        assert token != null;
        throwParseError(source.getSource(), token.getLine(), token.getCharPositionInLine(), token, message);
    }

    private static final class BailoutErrorListener extends BaseErrorListener {
        private final Source source;

        BailoutErrorListener(Source source) {
            this.source = source;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throwParseError(source, line, charPositionInLine, (Token) offendingSymbol, msg);
        }
    }

    private static void throwParseError(Source source, int line, int charPositionInLine, Token token, String message) {
        int col = charPositionInLine + 1;
        String location = "-- line " + line + " col " + col + ": ";
        int length = token == null ? 1 : Math.max(token.getStopIndex() - token.getStartIndex(), 0);
        throw new SLParseError(source, line, col, length, String.format("Error(s) parsing script:%n" + location + message));
    }

    protected TruffleString asTruffleString(Token literalToken, boolean removeQuotes) {
        int fromIndex = literalToken.getStartIndex();
        int length = literalToken.getStopIndex() - literalToken.getStartIndex() + 1;
        if (removeQuotes) {
            /* Remove the trailing and ending " */
            assert literalToken.getText().length() >= 2 && literalToken.getText().startsWith("\"") && literalToken.getText().endsWith("\"");
            fromIndex += 1;
            length -= 2;
        }
        return sourceString.substringByteIndexUncached(fromIndex * 2, length * 2, SLLanguage.STRING_ENCODING, true);
    }
}
