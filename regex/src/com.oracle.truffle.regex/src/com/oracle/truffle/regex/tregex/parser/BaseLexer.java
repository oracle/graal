package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.errors.ErrorMessages;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.TBitSet;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseLexer {

    protected static final TBitSet SYNTAX_CHARS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '/', '?', '[', '\\', ']', '^', '{', '|', '}');

    protected final RegexSource source;
    protected final String pattern;
    protected final Encoding encoding;
    protected Token lastToken;
    protected int curStartIndex = 0;
    protected int index = 0;
    protected int nGroups = 1;
    protected boolean identifiedAllGroups = false;
    protected Map<String, Integer> namedCaptureGroups = null;

    public BaseLexer(RegexSource source) {
        this.source = source;
        this.pattern = source.getPattern();
        this.encoding = source.getEncoding();
    }

    public boolean hasNext() {
        return !atEnd();
    }

    public Token next() throws RegexSyntaxException {
        curStartIndex = index;
        Token t = getNext();
        setSourceSection(t, curStartIndex, index);
        lastToken = t;
        return t;
    }

    /**
     * Sets the {@link com.oracle.truffle.api.source.SourceSection} of a given {@link Token} in
     * respect of {@link RegexSource#getSource()}.
     *
     * @param startIndex inclusive start index of the source section in respect of
     *                   {@link RegexSource#getPattern()}.
     * @param endIndex   exclusive end index of the source section in respect of
     *                   {@link RegexSource#getPattern()}.
     */
    private void setSourceSection(Token t, int startIndex, int endIndex) {
        if (source.getOptions().isDumpAutomataWithSourceSections()) {
            // RegexSource#getSource() prepends a slash ('/') to the pattern, so we have to add an
            // offset of 1 here.
            t.setSourceSection(source.getSource().createSection(startIndex + 1, endIndex - startIndex));
        }
    }

    /* input string access */

    protected char curChar() {
        return pattern.charAt(index);
    }

    protected char consumeChar() {
        final char c = pattern.charAt(index);
        advance();
        return c;
    }

    protected boolean findChars(char... chars) {
        if (atEnd()) {
            return false;
        }
        int i = ArrayUtils.indexOf(pattern, index, pattern.length(), chars);
        if (i < 0) {
            index = pattern.length();
            return false;
        }
        index = i;
        return true;
    }

    protected void advance() {
        advance(1);
    }

    protected void retreat() {
        advance(-1);
    }

    private void advance(int len) {
        index += len;
    }

    protected boolean lookahead(String match) {
        if (pattern.length() - index < match.length()) {
            return false;
        }
        return pattern.regionMatches(index, match, 0, match.length());
    }

    protected boolean consumingLookahead(String match) {
        final boolean matches = lookahead(match);
        if (matches) {
            advance(match.length());
        }
        return matches;
    }

    protected boolean atEnd() {
        return index >= pattern.length();
    }

    public int numberOfCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return nGroups;
    }

    public Map<String, Integer> getNamedCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return namedCaptureGroups;
    }

    /**
     * Checks whether this regular expression contains any named capture groups.
     * <p>
     * This method is a way to check whether we are parsing the goal symbol Pattern[~U, +N] or
     * Pattern[~U, ~N] (see the ECMAScript RegExp grammar).
     */
    protected boolean hasNamedCaptureGroups() throws RegexSyntaxException {
        return getNamedCaptureGroups() != null;
    }

    protected void registerCaptureGroup() {
        if (!identifiedAllGroups) {
            nGroups++;
        }
    }

    protected void registerNamedCaptureGroup(String name) {
        if (!identifiedAllGroups) {
            if (namedCaptureGroups == null) {
                namedCaptureGroups = new HashMap<>();
            }
            if (namedCaptureGroups.containsKey(name)) {
                throw syntaxError(ErrorMessages.MULTIPLE_GROUPS_SAME_NAME);
            }
            namedCaptureGroups.put(name, nGroups);
        }
        registerCaptureGroup();
    }

    protected void identifyCaptureGroups() throws RegexSyntaxException {
        // We are counting capture groups, so we only care about '(' characters and special
        // characters which can cancel the meaning of '(' - those include '\' for escapes, '[' for
        // character classes (where '(' stands for a literal '(') and any characters after the '('
        // which might turn into a non-capturing group or a look-around assertion.
        boolean insideCharClass = false;
        final int restoreIndex = index;
        while (findChars('\\', '[', ']', '(')) {
            switch (consumeChar()) {
                case '\\':
                    // skip escaped char
                    advance();
                    break;
                case '[':
                    insideCharClass = true;
                    break;
                case ']':
                    insideCharClass = false;
                    break;
                case '(':
                    if (!insideCharClass) {
                        parseGroupBegin();
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        index = restoreIndex;
    }

    protected abstract Token getNext() throws RegexSyntaxException;

    protected abstract Token parseGroupBegin() throws RegexSyntaxException;

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.charClass, Token.Kind.groupEnd, Token.Kind.backReference);

    protected Token parseQuantifier(char c) throws RegexSyntaxException {
        int min;
        int max = -1;
        boolean greedy;
        if (c == '{') {
            final int resetIndex = index;
            BigInteger literalMin = parseDecimal();
            if (literalMin.compareTo(BigInteger.ZERO) < 0) {
                return countedRepetitionSyntaxError(resetIndex);
            }
            min = literalMin.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0 ? literalMin.intValue() : -1;
            if (consumingLookahead(",}")) {
                greedy = !consumingLookahead("?");
            } else if (consumingLookahead("}")) {
                max = min;
                greedy = !consumingLookahead("?");
            } else {
                BigInteger literalMax;
                if (!consumingLookahead(",") || (literalMax = parseDecimal()).compareTo(BigInteger.ZERO) < 0 || !consumingLookahead("}")) {
                    return countedRepetitionSyntaxError(resetIndex);
                }
                max = literalMax.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0 ? literalMax.intValue() : -1;
                greedy = !consumingLookahead("?");
                if (literalMin.compareTo(literalMax) > 0) {
                    throw syntaxError(ErrorMessages.QUANTIFIER_OUT_OF_ORDER);
                }
            }
        } else {
            greedy = !consumingLookahead("?");
            min = c == '+' ? 1 : 0;
            if (c == '?') {
                max = 1;
            }
        }
        if (lastToken == null) {
            throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        if (lastToken.kind == Token.Kind.quantifier) {
            throw syntaxError(ErrorMessages.QUANTIFIER_ON_QUANTIFIER);
        }
        if (!QUANTIFIER_PREV.contains(lastToken.kind)) {
            throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        return Token.createQuantifier(min, max, greedy);
    }

    protected abstract Token countedRepetitionSyntaxError(int resetIndex) throws RegexSyntaxException;

    private BigInteger parseDecimal() {
        if (atEnd() || !isDecimal(curChar())) {
            return BigInteger.valueOf(-1);
        }
        return parseDecimal(BigInteger.ZERO);
    }

    protected BigInteger parseDecimal(BigInteger firstDigit) {
        BigInteger ret = firstDigit;
        while (!atEnd() && isDecimal(curChar())) {
            ret = ret.multiply(BigInteger.TEN);
            ret = ret.add(BigInteger.valueOf(consumeChar() - '0'));
        }
        return ret;
    }

    protected int parseOctal(int firstDigit) {
        int ret = firstDigit;
        for (int i = 0; !atEnd() && isOctal(curChar()) && i < 2; i++) {
            if (ret * 8 > 255) {
                return ret;
            }
            ret *= 8;
            ret += consumeChar() - '0';
        }
        return ret;
    }

    protected static boolean isDecimal(char c) {
        return '0' <= c && c <= '9';
    }

    protected static boolean isOctal(char c) {
        return '0' <= c && c <= '7';
    }

    protected static boolean isHex(char c) {
        return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
    }

    protected RegexSyntaxException syntaxError(String msg) {
        return RegexSyntaxException.createPattern(source, msg, curStartIndex);
    }
}
