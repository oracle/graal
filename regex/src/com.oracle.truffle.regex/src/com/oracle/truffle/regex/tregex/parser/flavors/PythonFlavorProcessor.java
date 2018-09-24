/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.chardata.CodePointRange;
import com.oracle.truffle.regex.chardata.CodePointSet;
import com.oracle.truffle.regex.chardata.UnicodeCharacterProperties;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class PythonFlavorProcessor implements RegexFlavorProcessor {

    public static final CompilationFinalBitSet SYNTAX_CHARACTERS = CompilationFinalBitSet.valueOf('^', '$', '\\', '.', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|');
    public static final CompilationFinalBitSet CHAR_CLASS_SYNTAX_CHARACTERS = CompilationFinalBitSet.valueOf('\\', ']', '-');

    /**
     * Maps Python's predefined Unicode character classes (d, D, s, S, w, W) to equivalent
     * expressions in ECMAScript regular expressions. The results are not wrapped in brackets and
     * can therefore be directly pasted in to character classes (e.g. when translating [\s,.:]).
     *
     * This map is partial. If no replacement exists, a set from {@link #UNICODE_CHAR_CLASS_SETS}
     * has to be listed out explicitly instead.
     */
    public static final Map<Character, String> UNICODE_CHAR_CLASS_REPLACEMENTS;
    /**
     * Maps Python's predefined Unicode character classes to sets containing the characters to be
     * matched.
     */
    public static final Map<Character, CodePointSet> UNICODE_CHAR_CLASS_SETS;

    static {
        UNICODE_CHAR_CLASS_REPLACEMENTS = new HashMap<>();
        UNICODE_CHAR_CLASS_SETS = new HashMap<>();

        // Digits: \\d
        // Python accepts characters with the Numeric_Type=Decimal property.
        // As of Unicode 11.0.0, these happen to be exactly the characters
        // in the Decimal_Number General Category.
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('d', "\\p{General_Category=Decimal_Number}");

        // Non-digits: \\D
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('D', "\\P{General_Category=Decimal_Number}");

        // \\d and \\D as CodePointSets (currently not needed, included for consistency)
        CodePointSet decimals = UnicodeCharacterProperties.getProperty("General_Category=Decimal_Number");
        CodePointSet nonDecimals = decimals.createInverse();
        UNICODE_CHAR_CLASS_SETS.put('d', decimals);
        UNICODE_CHAR_CLASS_SETS.put('D', nonDecimals);

        // Spaces: \\s
        // Python accepts characters with either the Space_Separator General Category
        // or one of the WS, B or S Bidi_Classes. A close analogue available in
        // ECMAScript regular expressions is the White_Space Unicode property,
        // which is only missing the characters \\u001c-\\u001f (as of Unicode 11.0.0).
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('s', "\\p{White_Space}\u001c-\u001f");

        // Non-spaces: \\S
        // If we are translating an occurrence of \\S inside a character class, we cannot
        // use the negated Unicode character property \\P{White_Space}, because then we would
        // need to subtract the code points \\u001c-\\u001f from the resulting character class,
        // which is not possible in ECMAScript regular expressions. Therefore, we have to expand
        // the definition of the White_Space property, do the set subtraction and then list the
        // contents of the resulting set.
        CodePointSet unicodeSpaces = UnicodeCharacterProperties.getProperty("White_Space");
        CodePointSet spaces = unicodeSpaces.addRange(new CodePointRange('\u001c', '\u001f'));
        CodePointSet nonSpaces = spaces.createInverse();
        UNICODE_CHAR_CLASS_SETS.put('s', spaces);
        UNICODE_CHAR_CLASS_SETS.put('S', nonSpaces);

        // Word characters: \\w
        // As alphabetic characters, Python accepts those in the general category L.
        // As numeric, it takes any character with either Numeric_Type=Decimal,
        // Numeric_Type=Digit or Numeric_Type=Numeric. As of Unicode 11.0.0, this
        // corresponds to the general category Number, along with the following
        // code points:
        // F96B;CJK COMPATIBILITY IDEOGRAPH-F96B;Lo;0;L;53C3;;;3;N;;;;;
        // F973;CJK COMPATIBILITY IDEOGRAPH-F973;Lo;0;L;62FE;;;10;N;;;;;
        // F978;CJK COMPATIBILITY IDEOGRAPH-F978;Lo;0;L;5169;;;2;N;;;;;
        // F9B2;CJK COMPATIBILITY IDEOGRAPH-F9B2;Lo;0;L;96F6;;;0;N;;;;;
        // F9D1;CJK COMPATIBILITY IDEOGRAPH-F9D1;Lo;0;L;516D;;;6;N;;;;;
        // F9D3;CJK COMPATIBILITY IDEOGRAPH-F9D3;Lo;0;L;9678;;;6;N;;;;;
        // F9FD;CJK COMPATIBILITY IDEOGRAPH-F9FD;Lo;0;L;4EC0;;;10;N;;;;;
        // 2F890;CJK COMPATIBILITY IDEOGRAPH-2F890;Lo;0;L;5EFE;;;9;N;;;;;
        String alphaStr = "\\p{General_Category=Letter}";
        String numericStr = "\\p{General_Category=Number}\uf96b\uf973\uf978\uf9b2\uf9d1\uf9d3\uf9fd\\u{2f890}";
        String wordCharsStr = alphaStr + numericStr + "_";
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('w', wordCharsStr);

        // Non-word characters: \\W
        // Similarly as for \\S, we will not be able to produce a replacement string for \\W.
        // We will need to construct the set ourselves.
        CodePointSet alpha = UnicodeCharacterProperties.getProperty("General_Category=Letter");
        CodePointSet numericExtras = CodePointSet.create(0xf96b, 0xf973, 0xf978, 0xf9b2, 0xf9d1, 0xf9d3, 0xf9fd, 0x2f890);
        CodePointSet numeric = UnicodeCharacterProperties.getProperty("General_Category=Number").addSet(numericExtras);
        CodePointSet wordChars = alpha.addSet(numeric).addRange(new CodePointRange('_'));
        CodePointSet nonWordChars = wordChars.createInverse();
        UNICODE_CHAR_CLASS_SETS.put('w', wordChars);
        UNICODE_CHAR_CLASS_SETS.put('W', nonWordChars);
    }

    private enum TermCategory {
        Assertion,
        Atom,
        Quantifier,
        None
    }

    private static final class Lookbehind {
        public final int containedGroups;

        Lookbehind(int containedGroups) {
            this.containedGroups = containedGroups;
        }
    }

    private static final class Group {
        public final int groupNumber;

        Group(int groupNumber) {
            this.groupNumber = groupNumber;
        }
    }

    private static final CompilationFinalBitSet WHITESPACE = CompilationFinalBitSet.valueOf(' ', '\t', '\n', '\r', '\u000b', '\f');

    private static final CodePointSet XID_START = UnicodeCharacterProperties.getProperty("XID_Start").copy().addRange(new CodePointRange('_'));
    private static final CodePointSet XID_CONTINUE = UnicodeCharacterProperties.getProperty("XID_Continue");

    private final String inPattern;
    private final String inFlags;
    private final PythonREMode mode;

    private int position;
    private PythonFlags globalFlags;

    private final Deque<PythonFlags> flagsStack;
    private final StringBuilder outPattern;

    private TermCategory lastTerm;
    private Map<String, Integer> namedCaptureGroups;

    private boolean silent;
    private int groups;
    private Deque<Lookbehind> lookbehindStack;
    private Deque<Group> groupStack;

    @TruffleBoundary
    public PythonFlavorProcessor(RegexSource source, PythonREMode mode) {
        this.inPattern = source.getPattern();
        this.inFlags = source.getGeneralFlags();
        this.mode = mode;
        this.position = 0;
        this.lastTerm = TermCategory.None;
        this.globalFlags = new PythonFlags(inFlags);
        this.flagsStack = new LinkedList<>();
        this.outPattern = new StringBuilder(inPattern.length());
        this.namedCaptureGroups = null;
        this.groups = 0;
        this.lookbehindStack = new ArrayDeque<>();
        this.groupStack = new ArrayDeque<>();
    }

    @Override
    public Map<String, Integer> getNamedCaptureGroups() {
        return namedCaptureGroups;
    }

    @Override
    public boolean isUnicodePattern() {
        return true;
    }

    @TruffleBoundary
    @Override
    public void validate() throws RegexSyntaxException {
        silent = true;
        parse();
    }

    @TruffleBoundary
    @Override
    public RegexSource toECMAScriptRegex() throws RegexSyntaxException, UnsupportedRegexException {
        silent = false;
        parse();
        return new RegexSource(outPattern.toString(), "su");
    }

    private void bailOut(String reason) throws UnsupportedRegexException {
        if (!silent) {
            throw new UnsupportedRegexException(reason);
        }
    }

    private void emitSnippet(String snippet) {
        if (!silent) {
            outPattern.append(snippet);
        }
    }

    private void emitRawCodepoint(int codepoint) {
        if (!silent) {
            outPattern.appendCodePoint(codepoint);
        }
    }

    private int curChar() {
        switch (mode) {
            case Str:
                return inPattern.codePointAt(position);
            case Bytes:
                return inPattern.charAt(position);
            default:
                throw new IllegalStateException();
        }
    }

    private int consumeChar() {
        final int c = curChar();
        advance();
        return c;
    }

    private String getMany(Predicate<Integer> pred) {
        StringBuilder out = new StringBuilder();
        while (!atEnd() && pred.test(curChar())) {
            out.appendCodePoint(consumeChar());
        }
        return out.toString();
    }

    private String getUpTo(int count, Predicate<Integer> pred) {
        StringBuilder out = new StringBuilder();
        int found = 0;
        while (found < count && !atEnd() && pred.test(curChar())) {
            out.appendCodePoint(consumeChar());
            found++;
        }
        return out.toString();
    }

    private void advance() {
        advance(1);
    }

    private void retreat() {
        advance(-1);
    }

    private void advance(int len) {
        switch (mode) {
            case Str:
                position = inPattern.offsetByCodePoints(position, len);
                break;
            case Bytes:
                position += len;
                break;
        }
    }

    private boolean lookahead(String match) {
        if (inPattern.length() - position < match.length()) {
            return false;
        }
        return inPattern.regionMatches(position, match, 0, match.length());
    }

    private boolean consumingLookahead(String match) {
        final boolean matches = lookahead(match);
        if (matches) {
            position += match.length();
        }
        return matches;
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    private PythonFlags getLocalFlags() {
        return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
    }

    private PythonFlags getGlobalFlags() {
        return globalFlags;
    }

    @Override
    public TruffleObject getFlags() {
        return getGlobalFlags();
    }

    private void parse() {
        PythonFlags startFlags;
        globalFlags = globalFlags.fixFlags(mode);

        do {
            startFlags = globalFlags;

            disjunction();

            globalFlags = globalFlags.fixFlags(mode);
        } while (!globalFlags.equals(startFlags));

        if (!atEnd()) {
            assert curChar() == ')';
            throw syntaxErrorAtRel("unbalanced parenthesis", 0);
        }
    }

    private void disjunction() {
        while (true) {
            alternative();

            if (consumingLookahead("|")) {
                emitSnippet("|");
            } else {
                break;
            }
        }
    }

    private void comment() {
        while (!atEnd()) {
            int ch = consumeChar();
            if (ch == '\\' && !atEnd()) {
                advance();
            } else if (ch == '\n') {
                break;
            }
        }
    }

    private boolean assertionEscape() {
        switch (curChar()) {
            case 'A':
                advance();
                emitSnippet("^");
                return true;
            case 'Z':
                advance();
                emitSnippet("$");
                return true;
            case 'b':
                advance();
                if (getLocalFlags().isUnicode()) {
                    // This is the snippet that we want to paste in, but with Python's
                    // definitions of \\w and \\W:
                    // "(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$)"
                    emitSnippet("(?:^|(?<=[");
                    emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get('W'));
                    emitSnippet("]))(?=[");
                    emitSnippet(UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                    emitSnippet("])|(?<=[");
                    emitSnippet(UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                    emitSnippet("])(?:(?=[");
                    emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get('W'));
                    emitSnippet("])|$)");
                } else if (getLocalFlags().isLocale()) {
                    bailOut("locale-specific word boundary assertions not supported");
                } else {
                    emitSnippet("\\b");
                }
                return true;
            case 'B':
                advance();
                if (getLocalFlags().isUnicode()) {
                    // "(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w)"
                    emitSnippet("(?:^|(?<=[");
                    emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get('W'));
                    emitSnippet("]))(?:(?=[");
                    emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get('W'));
                    emitSnippet("])|$)|(?<=[");
                    emitSnippet(UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                    emitSnippet("])(?=[");
                    emitSnippet(UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                    emitSnippet("])");
                } else if (getLocalFlags().isLocale()) {
                    bailOut("locale-specific word boundary assertions not supported");
                } else {
                    emitSnippet("\\B");
                }
                return true;
            default:
                return false;
        }
    }

    private void emitCharNoCasing(int codepoint, boolean inCharClass) {
        CompilationFinalBitSet syntaxChars = inCharClass ? CHAR_CLASS_SYNTAX_CHARACTERS : SYNTAX_CHARACTERS;
        if (syntaxChars.get(codepoint)) {
            emitSnippet("\\");
        }
        emitRawCodepoint(codepoint);
    }

    private void emitChar(int codepoint) {
        emitChar(codepoint, false);
    }

    private void emitChar(int codepoint, boolean inCharClass) {
        if (!silent) {
            if (getLocalFlags().isIgnoreCase()) {
                CodePointSet caseClosure = caseFold(CodePointSet.create(codepoint));
                if (inCharClass) {
                    emitCharSetNoCasing(caseClosure);
                } else {
                    emitSnippet("[");
                    emitCharSetNoCasing(caseClosure);
                    emitSnippet("]");
                }
            } else {
                emitCharNoCasing(codepoint, inCharClass);
            }
        }
    }

    private void emitString(String string) {
        for (int i = 0; i < string.length(); i = string.offsetByCodePoints(i, 1)) {
            emitChar(string.codePointAt(i));
        }
    }

    private void emitCharSetNoCasing(CodePointSet charSet) {
        for (CodePointRange range : charSet.getRanges()) {
            if (range.isSingle()) {
                emitCharNoCasing(range.lo, true);
            } else {
                emitCharNoCasing(range.lo, true);
                emitSnippet("-");
                emitCharNoCasing(range.hi, true);
            }
        }
    }

    private void emitCaseFoldClosure(CodePointSet charSet) {
        if (!silent) {
            CodePointSet closedSet = caseFold(charSet);
            CodePointSet complement = closedSet.createIntersection(charSet.createInverse());
            emitCharSetNoCasing(complement);
        }
    }

    private CodePointSet caseFold(@SuppressWarnings("unused") CodePointSet charSet) {
        if (getLocalFlags().isLocale()) {
            throw new UnsupportedRegexException("locale-specific case folding is not supported");
        }
        throw new UnsupportedRegexException("case folding not yet implemented");
    }

    private boolean categoryEscape(boolean inCharClass) {
        // TODO: Check with asserts that these character classes are closed on case folding,
        // once case folding is implemented.
        switch (curChar()) {
            case 'd':
            case 'D':
            case 's':
            case 'S':
            case 'w':
            case 'W':
                char className = (char) curChar();
                advance();
                if (getLocalFlags().isUnicode()) {
                    if (inCharClass) {
                        if (UNICODE_CHAR_CLASS_REPLACEMENTS.containsKey(className)) {
                            emitSnippet(UNICODE_CHAR_CLASS_REPLACEMENTS.get(className));
                        } else {
                            emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get(className));
                        }
                    } else {
                        if (UNICODE_CHAR_CLASS_REPLACEMENTS.containsKey(className)) {
                            emitSnippet("[" + UNICODE_CHAR_CLASS_REPLACEMENTS.get(className) + "]");
                        } else if (UNICODE_CHAR_CLASS_REPLACEMENTS.containsKey(Character.toLowerCase(className))) {
                            emitSnippet("[^" + UNICODE_CHAR_CLASS_REPLACEMENTS.get(Character.toLowerCase(className)) + "]");
                        } else {
                            emitSnippet("[");
                            emitCharSetNoCasing(UNICODE_CHAR_CLASS_SETS.get(className));
                            emitSnippet("]");
                        }
                    }
                } else if (getLocalFlags().isLocale() && (curChar() == 'w' || curChar() == 'W')) {
                    bailOut("locale-specific definitions of word characters are not supported");
                } else {
                    emitSnippet("\\" + className);
                }
                return true;
            default:
                return false;
        }
    }

    private int characterEscape(boolean inCharClass) {
        switch (curChar()) {
            case 'a':
                advance();
                emitChar('\u0007', inCharClass);
                return '\u0007';
            case 'b':
                advance();
                emitChar('\b', inCharClass);
                return '\b';
            case 'f':
                advance();
                emitChar('\f', inCharClass);
                return '\f';
            case 'n':
                advance();
                emitChar('\n', inCharClass);
                return '\n';
            case 'r':
                advance();
                emitChar('\r', inCharClass);
                return '\r';
            case 't':
                advance();
                emitChar('\t', inCharClass);
                return '\t';
            case 'v':
                advance();
                emitChar('\u000b', inCharClass);
                return '\u000b';
            case '\\':
                advance();
                emitChar('\\', inCharClass);
                return '\\';
            case 'x': {
                advance();
                String code = getUpTo(2, PythonFlavorProcessor::isHexDigit);
                if (code.length() < 2) {
                    throw syntaxErrorAtRel("incomplete escape \\x" + code, 2 + code.length());
                }
                int codepoint = Integer.parseInt(code, 16);
                emitChar(codepoint, inCharClass);
                return codepoint;
            }
            case 'u':
            case 'U':
                // 'u' and 'U' escapes are supported only in 'str' patterns
                if (mode == PythonREMode.Str) {
                    char escapeLead = (char) consumeChar();
                    int escapeLength;
                    switch (escapeLead) {
                        case 'u':
                            escapeLength = 4;
                            break;
                        case 'U':
                            escapeLength = 8;
                            break;
                        default:
                            throw new IllegalStateException();
                    }
                    String code = getUpTo(escapeLength, PythonFlavorProcessor::isHexDigit);
                    if (code.length() < escapeLength) {
                        throw syntaxErrorAtRel("incomplete escape \\" + escapeLead + code, 2 + code.length());
                    }
                    try {
                        int codePoint = Integer.parseInt(code, 16);
                        if (codePoint > 0x10FFFF) {
                            throw syntaxErrorAtRel("unicode escape value \\" + escapeLead + code + " outside of range 0-0x10FFFF", 2 + code.length());
                        }
                        emitChar(codePoint, inCharClass);
                        return codePoint;
                    } catch (NumberFormatException e) {
                        throw syntaxErrorAtRel("bad escape \\" + escapeLead + code, 2 + code.length());
                    }
                } else {
                    // \\u or \\U in 'bytes' patterns
                    throw syntaxErrorAtRel("bad escape \\" + curChar(), 1);
                }
            default:
                if (isOctDigit(curChar())) {
                    String code = getUpTo(3, PythonFlavorProcessor::isOctDigit);
                    int codePoint = Integer.parseInt(code, 8);
                    if (codePoint > 0377) {
                        throw syntaxErrorAtRel("octal escape value \\" + code + " outside of range 0-o377", 1 + code.length());
                    }
                    // Octal escapes are easily mistaken for backreferences in ECMAScript regular
                    // expressions. To avoid confusion, we generate a non-ambiguous hex escape
                    // that works correctly both in unicode (str) and non-unicode (bytes)
                    // regular expressions.
                    emitChar(codePoint, inCharClass);
                    return codePoint;
                } else if (isAsciiLetter(curChar())) {
                    throw syntaxErrorAtRel("bad escape \\" + new String(Character.toChars(curChar())), 1);
                } else {
                    int ch = consumeChar();
                    emitChar(ch, inCharClass);
                    return ch;
                }
        }
    }

    private boolean backreference() {
        if (curChar() >= '1' && curChar() <= '9') {
            String number = getUpTo(2, PythonFlavorProcessor::isDecDigit);
            int groupNumber = Integer.parseInt(number);
            if (groupNumber > groups) {
                throw syntaxErrorAtRel("invalid group reference " + number, number.length());
            }
            for (Group openGroup : groupStack) {
                if (groupNumber == openGroup.groupNumber) {
                    throw syntaxErrorAtRel("cannot refer to an open group", number.length() + 1);
                }
            }
            for (Lookbehind openLookbehind : lookbehindStack) {
                if (groupNumber >= openLookbehind.containedGroups) {
                    throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                }
            }
            if (getLocalFlags().isIgnoreCase()) {
                bailOut("case insensitive backreferences not supported");
            } else {
                emitSnippet("\\" + number);
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isOctDigit(int c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isDecDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private RegexSyntaxException syntaxErrorAtRel(String message, int offset) {
        int atPosition = mode == PythonREMode.Str ? inPattern.offsetByCodePoints(position, -offset) : position - offset;
        return syntaxErrorAtAbs(message, atPosition);
    }

    private RegexSyntaxException syntaxErrorAtAbs(String message, int atPosition) {
        int reportedPosition = mode == PythonREMode.Str ? inPattern.codePointCount(0, atPosition) : atPosition;
        return syntaxError(message, reportedPosition);
    }

    private RegexSyntaxException syntaxErrorHere(String message) {
        return syntaxErrorAtAbs(message, position);
    }

    private RegexSyntaxException syntaxError(String message, int atPosition) {
        return new RegexSyntaxException(inPattern, inFlags, message, atPosition);
    }

    private void escape() {
        if (assertionEscape()) {
            lastTerm = TermCategory.Assertion;
            return;
        }
        if (categoryEscape(false)) {
            lastTerm = TermCategory.Atom;
            return;
        }
        if (backreference()) {
            lastTerm = TermCategory.Atom;
            return;
        }
        characterEscape(false);
        lastTerm = TermCategory.Atom;
    }

    private Optional<Integer> classEscape() {
        if (categoryEscape(true)) {
            return Optional.empty();
        }
        return Optional.of(characterEscape(true));
    }

    private void alternative() {
        while (!atEnd() && curChar() != '|' && curChar() != ')') {
            int ch = consumeChar();

            if (getLocalFlags().isVerbose()) {
                if (WHITESPACE.get(ch)) {
                    continue;
                }
                if (ch == '#') {
                    comment();
                    continue;
                }
            }

            switch (ch) {
                case '\\':
                    escape();
                    break;
                case '[':
                    charClass();
                    lastTerm = TermCategory.Atom;
                    break;
                case '*':
                case '+':
                case '?':
                case '{':
                    quantifier(ch);
                    break;
                case '.':
                    if (getLocalFlags().isDotAll()) {
                        emitSnippet(".");
                    } else {
                        emitSnippet("[^\n]");
                    }
                    lastTerm = TermCategory.Atom;
                    break;
                case '(':
                    parens();
                    break;
                case '^':
                    if (getLocalFlags().isMultiLine()) {
                        emitSnippet("(?:^|(?<=\n))");
                    } else {
                        emitSnippet("^");
                    }
                    lastTerm = TermCategory.Assertion;
                    break;
                case '$':
                    if (getLocalFlags().isMultiLine()) {
                        emitSnippet("(?:$|(?=\n))");
                    } else {
                        emitSnippet("(?:$|(?=\n$))");
                    }
                    lastTerm = TermCategory.Assertion;
                    break;
                case ']':
                case '}':
                    emitChar(ch);
                    lastTerm = TermCategory.Atom;
                    break;
                default:
                    emitChar(ch);
                    lastTerm = TermCategory.Atom;
            }
        }
    }

    private void parens() {
        int start = position - 1;

        if (!atEnd()) {
            final int ch0 = consumeChar();
            switch (ch0) {
                case '?':
                    mustHaveMore();
                    final int ch1 = consumeChar();
                    switch (ch1) {
                        case 'P': {
                            mustHaveMore();
                            final int ch2 = consumeChar();
                            switch (ch2) {
                                case '<': {
                                    String groupName = parseGroupName('>');
                                    group(true, Optional.of(groupName), start);
                                    break;
                                }
                                case '=': {
                                    String groupName = parseGroupName(')');
                                    if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
                                        int groupNumber = namedCaptureGroups.get(groupName);
                                        for (Group openGroup : groupStack) {
                                            if (groupNumber == openGroup.groupNumber) {
                                                throw syntaxErrorAtRel("cannot refer to an open group", groupName.length() + 1);
                                            }
                                        }
                                        for (Lookbehind openLookbehind : lookbehindStack) {
                                            if (groupNumber >= openLookbehind.containedGroups) {
                                                throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                                            }
                                        }
                                        emitSnippet("\\" + groupNumber);
                                        lastTerm = TermCategory.Atom;
                                    } else {
                                        throw syntaxErrorAtRel("unknown group name " + groupName, groupName.length() + 1);
                                    }
                                    break;
                                }
                                default:
                                    throw syntaxErrorAtRel("unknown extension ?P" + new String(Character.toChars(ch2)), 3);
                            }
                            break;
                        }

                        case ':':
                            group(false, Optional.empty(), start);
                            break;

                        case '#':
                            getMany(c -> c != ')');
                            if (!consumingLookahead(")")) {
                                throw syntaxErrorAtAbs("missing ), unterminated comment", start);
                            }
                            break;

                        case '<': {
                            mustHaveMore();
                            final int ch2 = consumeChar();
                            switch (ch2) {
                                case '=':
                                    lookbehind(true, start);
                                    break;
                                case '!':
                                    lookbehind(false, start);
                                    break;
                                default:
                                    throw syntaxErrorAtRel("unknown extension ?<" + new String(Character.toChars(ch2)), 3);
                            }
                            break;
                        }

                        case '=':
                            lookahead(true, start);
                            break;

                        case '!':
                            lookahead(false, start);
                            break;

                        case '(':
                            bailOut("conditional backreference groups not supported");
                            String groupId = getMany(c -> c != ')');
                            if (groupId.isEmpty()) {
                                throw syntaxErrorHere("missing group name");
                            }
                            if (!consumingLookahead(Character.toString(')'))) {
                                throw syntaxErrorAtRel("missing ), unterminated name", groupId.length());
                            }
                            int groupNumber;
                            if (checkGroupName(groupId)) {
                                // group referenced by name
                                if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupId)) {
                                    groupNumber = namedCaptureGroups.get(groupId);
                                } else {
                                    throw syntaxErrorAtRel("unknown group name " + groupId, groupId.length() + 1);
                                }
                            } else {
                                try {
                                    groupNumber = Integer.parseInt(groupId);
                                    if (groupNumber < 0) {
                                        throw new NumberFormatException("negative group number");
                                    }
                                } catch (NumberFormatException e) {
                                    throw syntaxErrorAtRel("bad character in group name " + groupId, groupId.length() + 1);
                                }
                            }
                            if (!lookbehindStack.isEmpty()) {
                                for (Group openGroup : groupStack) {
                                    if (groupNumber == openGroup.groupNumber) {
                                        throw syntaxErrorHere("cannot refer to an open group");
                                    }
                                }
                            }
                            for (Lookbehind openLookbehind : lookbehindStack) {
                                if (groupNumber >= openLookbehind.containedGroups) {
                                    throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
                                }
                            }
                            disjunction();
                            if (consumingLookahead("|")) {
                                disjunction();
                                if (lookahead("|")) {
                                    throw syntaxErrorHere("conditional backref with more than two branches");
                                }
                            }
                            if (!consumingLookahead(")")) {
                                throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
                            }
                            lastTerm = TermCategory.Atom;
                            break;

                        case '-':
                        case 'i':
                        case 'L':
                        case 'm':
                        case 's':
                        case 'x':
                        case 'a':
                        case 't':
                        case 'u':
                            flags(ch1, start);
                            break;

                        default:
                            throw syntaxErrorAtRel("unknown extension ?" + new String(Character.toChars(ch1)), 2);
                    }
                    break;

                default:
                    retreat();
                    group(true, Optional.empty(), start);
            }
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
    }

    private void flags(int ch0, int start) {
        int ch = ch0;
        PythonFlags positiveFlags = PythonFlags.EMPTY_INSTANCE;
        while (PythonFlags.isValidFlagChar(ch)) {
            positiveFlags = positiveFlags.addFlag(ch);
            if (mode == PythonREMode.Str && ch == 'L') {
                throw syntaxErrorHere("bad inline flags: cannot use 'L' flag with a str pattern");
            }
            if (mode == PythonREMode.Bytes && ch == 'u') {
                throw syntaxErrorHere("bad inline flags: cannot use 'u' flag with a bytes pattern");
            }
            if (positiveFlags.numberOfTypeFlags() > 1) {
                throw syntaxErrorHere("bad inline flags: flags 'a', 'u' and 'L' are incompatible");
            }
            if (atEnd()) {
                throw syntaxErrorHere("missing -, : or )");
            }
            ch = consumeChar();
        }
        switch (ch) {
            case ')':
                globalFlags = globalFlags.addFlags(positiveFlags);
                lastTerm = TermCategory.Atom;
                break;
            case ':':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel("bad inline flags: cannot turn on global flag", 1);
                }
                localFlags(positiveFlags, PythonFlags.EMPTY_INSTANCE, start);
                break;
            case '-':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel("bad inline flags: cannot turn on global flag", 1);
                }
                if (atEnd()) {
                    throw syntaxErrorHere("missing flag");
                }
                ch = consumeChar();
                PythonFlags negativeFlags = PythonFlags.EMPTY_INSTANCE;
                while (PythonFlags.isValidFlagChar(ch)) {
                    negativeFlags = negativeFlags.addFlag(ch);
                    if (PythonFlags.TYPE_FLAGS_INSTANCE.hasFlag(ch)) {
                        throw syntaxErrorHere("bad inline flags: cannot turn off flags 'a', 'u' and 'L'");
                    }
                    if (atEnd()) {
                        throw syntaxErrorHere("missing :");
                    }
                    ch = consumeChar();
                }
                if (ch != ':') {
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel("unknown flag", 1);
                    } else {
                        throw syntaxErrorAtRel("missing :", 1);
                    }
                }
                if (negativeFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel("bad inline flags: cannot turn off global flag", 1);
                }
                localFlags(positiveFlags, negativeFlags, start);
                break;
            default:
                if (Character.isAlphabetic(ch)) {
                    throw syntaxErrorAtRel("unknown flag", 1);
                } else {
                    throw syntaxErrorAtRel("missing -, : or )", 1);
                }
        }
    }

    private void localFlags(PythonFlags positiveFlags, PythonFlags negativeFlags, int start) {
        if (positiveFlags.overlaps(negativeFlags)) {
            throw syntaxErrorHere("bad inline flags: flag turned on and off");
        }
        PythonFlags newFlags = getLocalFlags().addFlags(positiveFlags).delFlags(negativeFlags);
        if (positiveFlags.numberOfTypeFlags() > 0) {
            PythonFlags otherTypes = PythonFlags.TYPE_FLAGS_INSTANCE.delFlags(positiveFlags);
            newFlags = newFlags.delFlags(otherTypes);
        }
        flagsStack.push(newFlags);
        group(false, Optional.empty(), start);
        flagsStack.pop();
    }

    private void lookahead(boolean positive, int start) {
        if (positive) {
            emitSnippet("(?=");
        } else {
            emitSnippet("(?!");
        }
        disjunction();
        if (consumingLookahead(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        lastTerm = TermCategory.Assertion;
    }

    private void lookbehind(boolean positive, int start) {
        if (positive) {
            emitSnippet("(?<=");
        } else {
            emitSnippet("(?<!");
        }
        lookbehindStack.push(new Lookbehind(groups + 1));
        disjunction();
        lookbehindStack.pop();
        if (consumingLookahead(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        lastTerm = TermCategory.Assertion;
    }

    private String parseGroupName(char terminator) {
        String groupName = getMany(c -> c != terminator);
        if (groupName.isEmpty()) {
            throw syntaxErrorHere("missing group name");
        }
        if (!consumingLookahead(Character.toString(terminator))) {
            throw syntaxErrorAtRel("missing " + terminator + ", unterminated name", groupName.length());
        }
        if (!checkGroupName(groupName)) {
            throw syntaxErrorAtRel("bad character in group name " + groupName, groupName.length() + 1);
        }
        return groupName;
    }

    private void group(boolean capturing, Optional<String> optName, int start) {
        if (capturing) {
            groups++;
            groupStack.push(new Group(groups));
            emitSnippet("(");
        } else {
            emitSnippet("(?:");
        }
        optName.ifPresent(name -> {
            if (namedCaptureGroups == null) {
                namedCaptureGroups = new HashMap<>();
            }
            if (namedCaptureGroups.containsKey(name)) {
                throw syntaxErrorAtRel(String.format("redefinition of group name '%s' as group %d; was group %d", name, groups, namedCaptureGroups.get(name)), name.length() + 1);
            }
            namedCaptureGroups.put(name, groups);
        });
        disjunction();
        if (consumingLookahead(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        if (capturing) {
            groupStack.pop();
        }
        lastTerm = TermCategory.Atom;
    }

    private void mustHaveMore() {
        if (atEnd()) {
            throw syntaxErrorHere("unexpected end of pattern");
        }
    }

    private static boolean checkGroupName(String groupName) {
        if (groupName.isEmpty()) {
            return false;
        }
        for (int i = 0; i < groupName.length(); i = groupName.offsetByCodePoints(i, 1)) {
            int ch = groupName.codePointAt(i);
            if (i == 0 && !XID_START.contains(ch)) {
                return false;
            }
            if (i > 0 && !XID_CONTINUE.contains(ch)) {
                return false;
            }
        }
        return true;
    }

    private void quantifier(int ch) {
        int start = position - 1;
        if (ch == '{') {
            if (consumingLookahead("}")) {
                emitChar('{');
                emitChar('}');
                lastTerm = TermCategory.Atom;
                return;
            } else if (consumingLookahead(",}")) {
                // Python interprets A{,} as A*, whereas ECMAScript does not accept such a range
                // quantifier.
                quantifier('*');
                return;
            }

            Optional<BigInteger> lowerBound = Optional.empty();
            Optional<BigInteger> upperBound = Optional.empty();
            String lower = getMany(PythonFlavorProcessor::isDecDigit);
            if (!lower.isEmpty()) {
                lowerBound = Optional.of(new BigInteger(lower));
            }
            if (consumingLookahead(",")) {
                String upper = getMany(PythonFlavorProcessor::isDecDigit);
                if (!upper.isEmpty()) {
                    upperBound = Optional.of(new BigInteger(upper));
                }
            } else {
                upperBound = lowerBound;
            }
            if (!consumingLookahead("}")) {
                emitChar('{');
                emitString(inPattern.substring(start + 1, position));
                lastTerm = TermCategory.Atom;
                return;
            }
            if (lowerBound.isPresent() && upperBound.isPresent() && lowerBound.get().compareTo(upperBound.get()) > 0) {
                throw syntaxErrorAtAbs("min repeat greater than max repeat", start);
            }
            emitSnippet(inPattern.substring(start, position));
        } else {
            emitSnippet(new String(Character.toChars(ch)));
        }

        switch (lastTerm) {
            case None:
            case Assertion:
                throw syntaxErrorAtAbs("nothing to repeat", start);
            case Quantifier:
                throw syntaxErrorAtAbs("multiple repeat", start);
            case Atom:
                if (consumingLookahead("?")) {
                    emitSnippet("?");
                }
                lastTerm = TermCategory.Quantifier;
        }
    }

    private void charClass() {
        emitSnippet("[");
        int start = position - 1;
        if (consumingLookahead("^")) {
            emitSnippet("^");
        }
        int firstPosInside = position;
        classBody: while (true) {
            if (atEnd()) {
                throw syntaxErrorAtAbs("unterminated character set", start);
            }
            int rangeStart = position;
            Optional<Integer> lowerBound;
            int ch = consumeChar();
            switch (ch) {
                case ']':
                    if (position == firstPosInside + 1) {
                        emitChar(']');
                        lowerBound = Optional.of((int) ']');
                    } else {
                        emitSnippet("]");
                        break classBody;
                    }
                    break;
                case '\\':
                    lowerBound = classEscape();
                    break;
                default:
                    emitChar(ch);
                    lowerBound = Optional.of(ch);
            }
            if (consumingLookahead("-")) {
                emitSnippet("-");
                if (atEnd()) {
                    throw syntaxErrorAtAbs("unterminated character set", start);
                }
                Optional<Integer> upperBound;
                ch = consumeChar();
                switch (ch) {
                    case ']':
                        emitSnippet("]");
                        break classBody;
                    case '\\':
                        upperBound = classEscape();
                        break;
                    default:
                        emitChar(ch);
                        upperBound = Optional.of(ch);
                }
                if (!lowerBound.isPresent() || !upperBound.isPresent() || upperBound.get() < lowerBound.get()) {
                    throw syntaxErrorAtAbs("bad character range " + inPattern.substring(rangeStart, position), rangeStart);
                }
                if (getLocalFlags().isIgnoreCase()) {
                    emitCaseFoldClosure(CodePointSet.create(new CodePointRange(lowerBound.get(), upperBound.get())));
                }
            } else if (getLocalFlags().isIgnoreCase() && lowerBound.isPresent()) {
                emitCaseFoldClosure(CodePointSet.create(lowerBound.get()));
            }
        }
    }
}
