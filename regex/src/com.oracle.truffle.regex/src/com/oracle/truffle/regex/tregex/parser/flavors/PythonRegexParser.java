/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.icu.lang.UCharacter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.chardata.UnicodeCharacterAliases;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.PyErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;
import com.oracle.truffle.regex.tregex.parser.JSRegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexParserGlobals;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * Implements the parsing and validation of Python regular expressions.
 * <p>
 * The implementation strives to be as close as possible to the behavior of the regex parser that
 * ships with Python 3.7, down to the wording of the error messages.
 */
public final class PythonRegexParser implements RegexValidator, RegexParser {

    /**
     * Characters that are considered special in ECMAScript regexes. To match these characters, they
     * need to be escaped using a backslash.
     */
    private static final TBitSet SYNTAX_CHARACTERS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}');
    /**
     * Characters that are considered special in ECMAScript regex character classes.
     */
    private static final TBitSet CHAR_CLASS_SYNTAX_CHARACTERS = TBitSet.valueOf('-', '\\', ']', '^');

    /**
     * Maps Python's predefined Unicode character classes (d, D, s, S, w, W) to equivalent
     * expressions in ECMAScript regular expressions. The results are not wrapped in brackets and
     * can therefore be directly pasted in to character classes (e.g. when translating [\s,.:]).
     *
     * This map is partial. If no replacement exists, a set from {@link #UNICODE_CHAR_CLASS_SETS}
     * has to be listed out explicitly instead.
     */
    private static final Map<Character, String> UNICODE_CHAR_CLASS_REPLACEMENTS;
    /**
     * Maps Python's predefined Unicode character classes to sets containing the characters to be
     * matched.
     */
    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASS_SETS;

    // The behavior of the word-boundary assertions depends on the notion of a word character.
    // Python's notion differs from that of ECMAScript and so we cannot compile Python word-boundary
    // assertions to ECMAScript word-boundary assertions. Furthermore, the notion of a word
    // character is dependent on whether the Python regular expression is set to use the ASCII range
    // only. These are helper constants that we use to implement word-boundary assertions.
    // WORD_BOUNDARY and WORD_NON_BOUNDARY are templates for word-boundary and word-non-boundary
    // assertions, respectively. These templates contain occurrences of \w and \W, which are
    // substituted with the correct notion of a word character during regexp transpilation time.
    public static final Pattern WORD_CHARS_PATTERN = Pattern.compile("\\\\[wW]");
    public static final String WORD_BOUNDARY = "(?:(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$))";
    // Note that in Python, \b and \B are not direct inverses. In an empty string, position 0
    // doesn't match neither \b and \B (i.e. /\B/ does not match where /^$/ would match).
    public static final String WORD_NON_BOUNDARY = "(?:^(?=\\W)|(?<=\\W)$|(?<=\\W)(?=\\W)|(?<=\\w)(?=\\w))";

    private static final String ASCII_WHITESPACE = "\\x09-\\x0d\\x20";
    private static final String ASCII_NON_WHITESPACE = "\\x00-\\x08\\x0e-\\x1f\\x21-\\u{10ffff}";

    static {
        UNICODE_CHAR_CLASS_REPLACEMENTS = new HashMap<>();
        UNICODE_CHAR_CLASS_SETS = new HashMap<>();

        // Digits: \d
        // Python accepts characters with the Numeric_Type=Decimal property.
        // As of Unicode 11.0.0, these happen to be exactly the characters
        // in the Decimal_Number General Category.
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('d', "\\p{General_Category=Decimal_Number}");

        // Non-digits: \D
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('D', "\\P{General_Category=Decimal_Number}");

        // \d and \D as CodePointSets (currently not needed, included for consistency)
        UNICODE_CHAR_CLASS_SETS.put('d', UnicodeProperties.getProperty("General_Category=Decimal_Number"));
        UNICODE_CHAR_CLASS_SETS.put('D', UnicodeProperties.getProperty("General_Category=Decimal_Number").createInverse(Encodings.UTF_32));

        // Spaces: \s
        // Python accepts characters with either the Space_Separator General Category
        // or one of the WS, B or S Bidi_Classes. A close analogue available in
        // ECMAScript regular expressions is the White_Space Unicode property,
        // which is only missing the characters \\u001c-\\u001f (as of Unicode 11.0.0).
        UNICODE_CHAR_CLASS_REPLACEMENTS.put('s', "\\p{White_Space}\u001c-\u001f");

        // Non-spaces: \S
        // If we are translating an occurrence of \S inside a character class, we cannot
        // use the negated Unicode character property \P{White_Space}, because then we would
        // need to subtract the code points \\u001c-\\u001f from the resulting character class,
        // which is not possible in ECMAScript regular expressions. Therefore, we have to expand
        // the definition of the White_Space property, do the set subtraction and then list the
        // contents of the resulting set.
        CodePointSet unicodeSpaces = UnicodeProperties.getProperty("White_Space");
        CodePointSet spaces = unicodeSpaces.union(CodePointSet.createNoDedup('\u001c', '\u001f'));
        CodePointSet nonSpaces = spaces.createInverse(Encodings.UTF_32);
        UNICODE_CHAR_CLASS_SETS.put('s', spaces);
        UNICODE_CHAR_CLASS_SETS.put('S', nonSpaces);

        // Word characters: \w
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

        // Non-word characters: \W
        // Similarly as for \S, we will not be able to produce a replacement string for \W.
        // We will need to construct the set ourselves.
        CodePointSet alpha = UnicodeProperties.getProperty("General_Category=Letter");
        CodePointSet numericExtras = CodePointSet.createNoDedup(0xf96b, 0xf973, 0xf978, 0xf9b2, 0xf9d1, 0xf9d3, 0xf9fd, 0x2f890);
        CodePointSet numeric = UnicodeProperties.getProperty("General_Category=Number").union(numericExtras);
        CodePointSet wordChars = alpha.union(numeric).union(CodePointSet.create('_'));
        CodePointSet nonWordChars = wordChars.createInverse(Encodings.UTF_32);
        UNICODE_CHAR_CLASS_SETS.put('w', wordChars);
        UNICODE_CHAR_CLASS_SETS.put('W', nonWordChars);
    }

    /**
     * An enumeration of the possible grammatical categories of Python regex terms.
     */
    private enum TermCategory {
        /**
         * A beginning-of-string/line, end-of-string/line or (non)-word-boundary assertion.
         */
        Assertion,
        /**
         * A literal character, a character class, a group, a lookahead or a lookbehind.
         */
        Atom,
        /**
         * Any kind of quantifier.
         */
        Quantifier,
        /**
         * Used as the grammatical category when the term in question does not exist.
         */
        None
    }

    /**
     * Metadata about an enclosing lookbehind assertion.
     */
    private static final class Lookbehind {
        /**
         * The index of the first capture group that is (or would be) contained in this lookbehind
         * assertion.
         */
        public final int containedGroups;

        Lookbehind(int containedGroups) {
            this.containedGroups = containedGroups;
        }
    }

    /**
     * Metadata about an enclosing capture group.
     */
    private static final class Group {
        /**
         * The index of the capture group.
         */
        public final int groupNumber;

        Group(int groupNumber) {
            this.groupNumber = groupNumber;
        }
    }

    /**
     * Characters considered as whitespace in Python's regex verbose mode.
     */
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\u000b', '\f', '\r', ' ');

    /**
     * The (slightly modified) version of the XID_Start Unicode property used to check names of
     * capture groups.
     */
    private static final CodePointSet XID_START = UnicodeProperties.getProperty("XID_Start").union(CodePointSet.create('_'));
    /**
     * The XID_Continue Unicode character property.
     */
    private static final CodePointSet XID_CONTINUE = UnicodeProperties.getProperty("XID_Continue");

    /**
     * The source object of the input pattern.
     */
    private final RegexSource inSource;

    /**
     * The source of the input pattern.
     */
    private final String inPattern;
    /**
     * The source of the flags of the input pattern.
     */
    private final String inFlags;
    /**
     * Indicates whether the regex being parsed is a 'str' pattern or a 'bytes' pattern.
     */
    private final PythonREMode mode;
    /**
     * Whether or not the parser should attempt to construct an ECMAScript regex during parsing or
     * not. Setting this to {@code false} is not there to gain efficiency, but to avoid triggering
     * {@link UnsupportedRegexException}s when checking for syntax errors.
     */
    private boolean silent;

    /**
     * The index of the next character in {@link #inPattern} to be parsed.
     */
    private int position;

    /**
     * A {@link StringBuilder} hosting the resulting ECMAScript pattern.
     */
    private final StringBuilder outPattern;

    /**
     * The global flags are the flags given when compiling the regular expression. Note that these
     * flags <em>can</em> be changed inline, in the pattern.
     */
    private PythonFlags globalFlags;
    /**
     * A stack of the locally enabled flags. Python enables the setting and unsetting of the flags
     * for subexpressions of the regex.
     * <p>
     * The currently active flags are at the top, the flags that would become active after the end
     * of the next (?aiLmsux-imsx:...) expression are just below.
     */
    private final Deque<PythonFlags> flagsStack;
    /**
     * For syntax checking purposes, we need to maintain some metadata about the current enclosing
     * lookbehind assertions.
     */
    private final Deque<Lookbehind> lookbehindStack;
    /**
     * For syntax checking purposes, we need to maintain some metadata about the current enclosing
     * capture groups.
     */
    private final Deque<Group> groupStack;
    /**
     * A map from names of capture groups to their indices. Is null if the pattern contained no
     * named capture groups so far.
     */
    private Map<String, Integer> namedCaptureGroups;

    /**
     * The number of capture groups encountered in the input pattern so far.
     */
    private int groups;
    /**
     * The grammatical category of the last term parsed. This is needed to detect improper usage of
     * quantifiers.
     */
    private TermCategory lastTerm;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    private final CodePointSetAccumulator charClassCaseFoldTmp = new CodePointSetAccumulator();
    /**
     * Necessary for the {@link RegexParserGlobals} reference for {@link JSRegexParser}.
     */
    private final RegexLanguage language;
    private final CompilationBuffer compilationBuffer;

    @TruffleBoundary
    private PythonRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.language = language;
        this.inSource = source;
        this.compilationBuffer = compilationBuffer;
        this.inPattern = source.getPattern();
        this.inFlags = source.getFlags();
        this.mode = PythonREMode.fromEncoding(source.getEncoding());
        this.position = 0;
        this.outPattern = new StringBuilder(inPattern.length());
        this.globalFlags = new PythonFlags(inFlags);
        this.flagsStack = new LinkedList<>();
        this.lookbehindStack = new ArrayDeque<>();
        this.groupStack = new ArrayDeque<>();
        this.namedCaptureGroups = null;
        this.groups = 0;
        this.lastTerm = TermCategory.None;
    }

    public static RegexValidator createValidator(RegexSource source) throws RegexSyntaxException {
        return new PythonRegexParser(null, source, null);
    }

    public static RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        return new PythonRegexParser(language, source, compilationBuffer);
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        return AbstractRegexObject.createNamedCaptureGroupMapInt(namedCaptureGroups);
    }

    @Override
    public AbstractRegexObject getFlags() {
        return getGlobalFlags();
    }

    @TruffleBoundary
    @Override
    public void validate() throws RegexSyntaxException {
        silent = true;
        run();
    }

    private RegexSource toECMAScriptRegex() throws RegexSyntaxException, UnsupportedRegexException {
        silent = false;
        run();
        // When translating to ECMAScript, we always the dotAll and unicode flags. The dotAll flag
        // lets us translate Python's dotAll . directly. The unicode flag lets us use some of the
        // ECMAScript regex escape sequences which are restricted to Unicode regexes. It also lets
        // us reason with a more rigid grammar (as the ECMAScript non-Unicode regexes contain a lot
        // of ambiguous syntactic constructions for backwards compatibility). It is fine to use
        // Unicode ECMAScript regexes for both 'str' and 'bytes' patterns. In 'str' patterns, we
        // actually want to match on the individual code points of the Unicode string. In 'bytes'
        // patterns, all characters are in the range 0-255 and so the Unicode flag does not
        // interfere with the matching (no surrogates).
        if (inSource.getOptions().getPythonMethod() == PythonMethod.fullmatch) {
            outPattern.insert(0, "(?:");
            outPattern.append(")$");
        }
        boolean sticky = inSource.getOptions().getPythonMethod() == PythonMethod.match || inSource.getOptions().getPythonMethod() == PythonMethod.fullmatch;
        String outFlags = sticky ? "suy" : "su";
        return new RegexSource(outPattern.toString(), outFlags, inSource.getOptions(), inSource.getSource());
    }

    @Override
    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException, UnsupportedRegexException {
        RegexSource ecmascriptSource = toECMAScriptRegex();
        JSRegexParser ecmascriptParser = new JSRegexParser(language, ecmascriptSource, compilationBuffer, inSource);
        return ecmascriptParser.parse();
    }

    private PythonFlags getLocalFlags() {
        return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
    }

    private PythonFlags getGlobalFlags() {
        return globalFlags;
    }

    /// Input scanning

    private int curChar() {
        switch (mode) {
            case Str:
                return inPattern.codePointAt(position);
            case Bytes:
                return inPattern.charAt(position);
            default:
                throw CompilerDirectives.shouldNotReachHere();
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

    private String getUntil(char terminator, String name) {
        StringBuilder out = new StringBuilder();
        while (!atEnd() && curChar() != terminator) {
            out.appendCodePoint(consumeChar());
        }
        if (out.length() == 0) {
            throw syntaxErrorHere(PyErrorMessages.missing(name));
        }
        if (atEnd()) {
            throw syntaxErrorAtRel(PyErrorMessages.missingUnterminatedName(terminator), out.length());
        }
        if (curChar() == '}') {
            advance();
            return out.toString();
        } else {
            throw syntaxErrorHere(PyErrorMessages.missing(Character.toString(terminator)));
        }
    }

    private void advance() {
        advance(1);
    }

    private void retreat() {
        advance(-1);
    }

    private void retreat(int len) {
        advance(-len);
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

    private boolean match(String match) {
        if (inPattern.regionMatches(position, match, 0, match.length())) {
            position += match.length();
            return true;
        } else {
            return false;
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    private void mustHaveMore() {
        if (atEnd()) {
            throw syntaxErrorHere(PyErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
    }

    /// Emitting the translated regular expression

    private void bailOut(String reason) throws UnsupportedRegexException {
        if (!silent) {
            throw new UnsupportedRegexException(reason);
        }
    }

    /**
     * Emits the argument into the output pattern <em>verbatim</em>. This is useful for syntax
     * characters or for prebaked snippets.
     *
     * @param snippet
     */
    private void emitSnippet(String snippet) {
        if (!silent) {
            outPattern.append(snippet);
        }
    }

    /**
     * Emits the codepoint into the output pattern <em>verbatim</em>. This is a special case of
     * {@link #emitSnippet} that avoids going through the trouble of converting a code point to a
     * {@link String} in Java (i.e. no need for new String(Character.toChars(codepoint))).
     *
     * @param codepoint
     */
    private void emitRawCodepoint(int codepoint) {
        if (!silent) {
            outPattern.appendCodePoint(codepoint);
        }
    }

    /**
     * Like {@link #emitChar(int, boolean)}, but does not do any case-folding.
     */
    private void emitCharNoCasing(int codepoint, boolean inCharClass) {
        if (!silent) {
            TBitSet syntaxChars = inCharClass ? CHAR_CLASS_SYNTAX_CHARACTERS : SYNTAX_CHARACTERS;
            if (syntaxChars.get(codepoint)) {
                emitSnippet("\\");
            }
            emitRawCodepoint(codepoint);
        }
    }

    /**
     * Shorthand for {@link #emitChar}{@code (codepoint, false)}.
     */
    private void emitChar(int codepoint) {
        emitChar(codepoint, false);
    }

    /**
     * Emits a matcher or a character class expression that can match a given character. Since
     * case-folding (IGNORECASE flag) can be enabled, a single character in the pattern could
     * correspond to a variety of different characters in the input.
     *
     * @param codepoint the character to be matched
     * @param inCharClass if {@code false}, emits a matcher matching {@code codepoint}; if
     *            {@code true}, emits a sequence of characters and/or character ranges that can be
     *            used to match {@code codepoint}
     */
    private void emitChar(int codepoint, boolean inCharClass) {
        if (!silent) {
            if (getLocalFlags().isIgnoreCase()) {
                curCharClass.clear();
                curCharClass.addRange(codepoint, codepoint);
                caseFold();
                if (curCharClass.matchesSingleChar()) {
                    emitCharNoCasing(codepoint, inCharClass);
                } else if (inCharClass) {
                    emitCharSetNoCasing();
                } else {
                    emitSnippet("[");
                    emitCharSetNoCasing();
                    emitSnippet("]");
                }
            } else {
                emitCharNoCasing(codepoint, inCharClass);
            }
        }
    }

    /**
     * Emits a series of matchers that would match the characters in {@code string}.
     */
    private void emitString(String string) {
        if (!silent) {
            for (int i = 0; i < string.length(); i = string.offsetByCodePoints(i, 1)) {
                emitChar(string.codePointAt(i));
            }
        }
    }

    /**
     * Emits a character class expression that would match the contents of {@code charSet}.
     * Case-folding is performed if the IGNORECASE flag is set. Since a character class expression
     * is emitted, this is legal only when emitting a character class.
     */
    private void emitCharSet() {
        if (!silent) {
            caseFold();
            emitCharSetNoCasing();
        }
    }

    private void emitCharSetNoCasing() {
        emitCharSetNoCasing(curCharClass);
    }

    /**
     * Like {@link #emitCharSet}, but it does not do any case-folding.
     */
    private void emitCharSetNoCasing(Iterable<Range> charSet) {
        if (!silent) {
            for (Range r : charSet) {
                if (r.isSingle()) {
                    emitCharNoCasing(r.lo, true);
                } else {
                    emitCharNoCasing(r.lo, true);
                    emitSnippet("-");
                    emitCharNoCasing(r.hi, true);
                }
            }
        }
    }

    /**
     * If the IGNORECASE flag is set, this method returns its arguments closed on case-folding.
     * Otherwise, returns its argument.
     */
    private void caseFold() {
        if (!getLocalFlags().isIgnoreCase()) {
            return;
        }
        if (getLocalFlags().isLocale()) {
            bailOut("locale-specific case folding is not supported");
        }
        CaseFoldTable.CaseFoldingAlgorithm caseFolding = getLocalFlags().isUnicode(mode) ? CaseFoldTable.CaseFoldingAlgorithm.PythonUnicode : CaseFoldTable.CaseFoldingAlgorithm.PythonAscii;
        CaseFoldTable.applyCaseFold(curCharClass, charClassCaseFoldTmp, caseFolding);
    }

    /// Error reporting

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
        return RegexSyntaxException.createPattern(inSource, message, atPosition);
    }

    // Character predicates

    private static boolean isAsciiLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isOctDigit(int c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isDecDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // The parser

    private void run() {
        PythonFlags startFlags;

        // The pattern can contain inline switches for global flags. However, these inline switches
        // need to be taken into account when processing whatever came before them too. Therefore,
        // we redo the parse if any inline switches changed the set of active global flags.
        do {
            startFlags = globalFlags;
            disjunction();
        } while (!globalFlags.equals(startFlags));

        globalFlags = globalFlags.fixFlags(inSource, mode);

        if (!atEnd()) {
            assert curChar() == ')';
            throw syntaxErrorAtRel(PyErrorMessages.UNBALANCED_PARENTHESIS, 0);
        }
    }

    /**
     * Disjunction, the topmost syntactic category, is a series of alternatives separated by
     * vertical bars.
     */
    private void disjunction() {
        while (true) {
            alternative();

            if (match("|")) {
                emitSnippet("|");
            } else {
                break;
            }
        }
    }

    /**
     * An alternative is a sequence of Terms.
     */
    private void alternative() {
        while (!atEnd() && curChar() != '|' && curChar() != ')') {
            term();
        }
    }

    /**
     * Parses a term. A term is either:
     * <ul>
     * <li>whitespace (if in vebose mode)</li>
     * <li>a comment (if in verbose mode)</li>
     * <li>an escape sequence</li>
     * <li>a character class</li>
     * <li>a quantifier</li>
     * <li>a group</li>
     * <li>an assertion</li>
     * <li>a literal character</li>
     * </ul>
     */
    private void term() {
        int ch = consumeChar();

        if (getLocalFlags().isVerbose()) {
            if (WHITESPACE.get(ch)) {
                return;
            }
            if (ch == '#') {
                comment();
                return;
            }
        }

        switch (ch) {
            case '\\':
                escape();
                break;
            case '[':
                characterClass();
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
            default:
                emitChar(ch);
                lastTerm = TermCategory.Atom;
        }
    }

    /**
     * A comment starts with a '#' and ends at the end of the line. The leading '#' is assumed to
     * have already been parsed.
     */
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

    /**
     * Escape sequence are special sequences starting with a backslash character. When calling this
     * method, the backslash is assumed to have already been parsed.
     * <p>
     * Valid escape sequences are:
     * <ul>
     * <li>character escapes</li>
     * <li>character class escapes</li>
     * <li>assertion escapes</li>
     * <li>backreferences</li>
     * </ul>
     */
    private void escape() {
        if (atEnd()) {
            throw syntaxErrorAtRel(PyErrorMessages.BAD_ESCAPE_END_OF_PATTERN, 1);
        }
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
        // characterEscape has to come after assertionEscape because of the ambiguity of \b, which
        // (outside of character classes) is resolved in the favor of the assertion
        // characterEscape also has to come after backreference because of the ambiguity between
        // backreferences and octal character escapes which must be resolved in favor of
        // backreferences
        characterEscape(false);
        lastTerm = TermCategory.Atom;
    }

    /**
     * Tries to parse an assertion escape. An assertion escape can be one of the following:
     * <ul>
     * <li>\A (beginning of input)</li>
     * <li>\Z (end of input)</li>
     * <li>\b (word boundary)</li>
     * <li>\B (word non-boundary)</li>
     * </ul>
     *
     * @return {@code true} iff an assertion escape was found
     */
    private boolean assertionEscape() {
        switch (consumeChar()) {
            case 'A':
                emitSnippet("^");
                return true;
            case 'Z':
                emitSnippet("$");
                return true;
            case 'b':
                if (getLocalFlags().isUnicode(mode)) {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                } else if (getLocalFlags().isLocale()) {
                    bailOut("locale-specific word boundary assertions not supported");
                } else {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, "\\w");
                }
                return true;
            case 'B':
                if (getLocalFlags().isUnicode(mode)) {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, UNICODE_CHAR_CLASS_REPLACEMENTS.get('w'));
                } else if (getLocalFlags().isLocale()) {
                    bailOut("locale-specific word boundary assertions not supported");
                } else {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, "\\w");
                }
                return true;
            default:
                retreat();
                return false;
        }
    }

    private void emitWordBoundaryAssertion(String snippetTemplate, String wordCharsStr) {
        Matcher matcher = WORD_CHARS_PATTERN.matcher(snippetTemplate);
        int lastAppendPosition = 0;
        while (matcher.find()) {
            emitSnippet(snippetTemplate.substring(lastAppendPosition, matcher.start()));
            if (matcher.group().equals("\\w")) {
                emitSnippet("[" + wordCharsStr + "]");
            } else {
                assert matcher.group().equals("\\W");
                emitSnippet("[^" + wordCharsStr + "]");
            }
            lastAppendPosition = matcher.end();
        }
        emitSnippet(snippetTemplate.substring(lastAppendPosition));
    }

    /**
     * Tries to parse a character class escape. The following character classes are available:
     * <ul>
     * <li>\d (digits)</li>
     * <li>\D (non-digits)</li>
     * <li>\s (spaces)</li>
     * <li>\S (non-spaces)</li>
     * <li>\w (word characters)</li>
     * <li>\W (non-word characters)</li>
     * </ul>
     *
     * @param inCharClass whether or not this escape was found in (and is being emitted as part of)
     *            a character class
     * @return {@code true} iff a category escape was found
     */
    private boolean categoryEscape(boolean inCharClass) {
        switch (curChar()) {
            case 'd':
            case 'D':
            case 's':
            case 'S':
            case 'w':
            case 'W':
                char className = (char) curChar();
                advance();
                if (getLocalFlags().isUnicode(mode)) {
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
                } else if (getLocalFlags().isLocale() && (className == 'w' || className == 'W')) {
                    bailOut("locale-specific definitions of word characters are not supported");
                } else if ((mode == PythonREMode.Bytes || getLocalFlags().isAscii()) && (className == 's' || className == 'S')) {
                    String snippet = className == 's' ? ASCII_WHITESPACE : ASCII_NON_WHITESPACE;
                    emitSnippet(inCharClass ? snippet : "[" + snippet + "]");
                } else {
                    emitSnippet("\\" + className);
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Tries to parse a backreference.
     *
     * @return {@code true} if a backreference was found
     */
    private boolean backreference() {
        if (curChar() >= '1' && curChar() <= '9') {
            // if there are three octal digits following a backslash,
            // always treat that as an octal escape
            String octalEscape = getUpTo(3, PythonRegexParser::isOctDigit);
            if (octalEscape.length() == 3) {
                int codePoint = Integer.parseInt(octalEscape, 8);
                if (codePoint > 0377) {
                    throw syntaxErrorAtRel(PyErrorMessages.invalidOctalEscape(octalEscape), 1 + octalEscape.length());
                }
                emitChar(codePoint);
                return true;
            } else {
                retreat(octalEscape.length());
            }
            String number = getUpTo(2, PythonRegexParser::isDecDigit);
            int groupNumber = Integer.parseInt(number);
            if (groupNumber > groups) {
                throw syntaxErrorAtRel(PyErrorMessages.invalidGroupReference(number), number.length());
            }
            verifyGroupReference(groupNumber, number);
            if (getLocalFlags().isIgnoreCase()) {
                bailOut("case insensitive backreferences not supported");
            } else {
                emitSnippet("(?:\\" + number + ")");
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Verifies that making a backreference to a certain group is legal in the current context.
     *
     * @param groupNumber the index of the referred group
     * @param groupName the name of the group, for error reporting purposes
     * @throws RegexSyntaxException if the backreference is not valid
     */
    private void verifyGroupReference(int groupNumber, String groupName) throws RegexSyntaxException {
        for (Group openGroup : groupStack) {
            if (groupNumber == openGroup.groupNumber) {
                throw syntaxErrorAtRel(PyErrorMessages.CANNOT_REFER_TO_AN_OPEN_GROUP, groupName.length() + 1);
            }
        }
        for (Lookbehind openLookbehind : lookbehindStack) {
            if (groupNumber >= openLookbehind.containedGroups) {
                throw syntaxErrorHere(PyErrorMessages.CANNOT_REFER_TO_GROUP_DEFINED_IN_THE_SAME_LOOKBEHIND_SUBPATTERN);
            }
        }
    }

    /**
     * Parses a character escape sequence. A character escape sequence can be one of the following:
     * <ul>
     * <li>\a, \b, \f, \n, \r, \t or \v</li>
     * <li>\\</li>
     * <li>an octal escape sequence</li>
     * <li>a hexadecimal escape sequence</li>
     * <li>a unicode escape sequence</li>
     * </ul>
     *
     * @param inCharClass whether the character escaped occurred in (and is being emitted as part
     *            of) a character class
     */
    private void characterEscape(boolean inCharClass) {
        emitChar(silentCharacterEscape(), inCharClass);
    }

    /**
     * Like {@link #characterEscape}, but instead of emitting a matcher or a character class
     * expression, it returns the escaped character. This is used when dealing with case-folding in
     * character classes.
     */
    private int silentCharacterEscape() {
        int ch = consumeChar();
        switch (ch) {
            case 'a':
                return '\u0007';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'v':
                return '\u000b';
            case '\\':
                return '\\';
            case 'x': {
                String code = getUpTo(2, PythonRegexParser::isHexDigit);
                if (code.length() < 2) {
                    throw syntaxErrorAtRel(PyErrorMessages.incompleteEscapeX(code), 2 + code.length());
                }
                int codepoint = Integer.parseInt(code, 16);
                return codepoint;
            }
            case 'u':
            case 'U':
                // 'u' and 'U' escapes are supported only in 'str' patterns
                if (mode == PythonREMode.Str) {
                    char escapeLead = (char) ch;
                    int escapeLength;
                    switch (escapeLead) {
                        case 'u':
                            escapeLength = 4;
                            break;
                        case 'U':
                            escapeLength = 8;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    String code = getUpTo(escapeLength, PythonRegexParser::isHexDigit);
                    if (code.length() < escapeLength) {
                        throw syntaxErrorAtRel(PyErrorMessages.incompleteEscapeU(escapeLead, code), 2 + code.length());
                    }
                    try {
                        int codePoint = Integer.parseInt(code, 16);
                        if (codePoint > 0x10FFFF) {
                            throw syntaxErrorAtRel(PyErrorMessages.invalidUnicodeEscape(escapeLead, code), 2 + code.length());
                        }
                        return codePoint;
                    } catch (NumberFormatException e) {
                        throw syntaxErrorAtRel(PyErrorMessages.incompleteEscapeU(escapeLead, code), 2 + code.length());
                    }
                } else {
                    // \\u or \\U in 'bytes' patterns
                    throw syntaxErrorAtRel(PyErrorMessages.badEscape(curChar()), 1);
                }
            case 'N': {
                if (mode != PythonREMode.Str) {
                    throw syntaxErrorAtRel(PyErrorMessages.badEscape(ch), 2);
                }
                if (!match("{")) {
                    throw syntaxErrorHere(PyErrorMessages.missing("{"));
                }
                String characterName = getUntil('}', "character name");
                int codePoint = lookupCharacterByName(characterName);
                if (codePoint == -1) {
                    throw syntaxErrorAtRel(PyErrorMessages.undefinedCharacterName(characterName), characterName.length() + 4);
                }
                return codePoint;
            }
            default:
                if (isOctDigit(ch)) {
                    retreat();
                    String code = getUpTo(3, PythonRegexParser::isOctDigit);
                    int codePoint = Integer.parseInt(code, 8);
                    if (codePoint > 0377) {
                        throw syntaxErrorAtRel(PyErrorMessages.invalidOctalEscape(code), 1 + code.length());
                    }
                    return codePoint;
                } else if (isAsciiLetter(ch) || isDecDigit(ch)) {
                    throw syntaxErrorAtRel(PyErrorMessages.badEscape(ch), 2);
                } else {
                    return ch;
                }
        }
    }

    public static int lookupCharacterByName(String characterName) {
        // CPython's logic for resolving these character names goes like this:
        // 1) handle Hangul Syllables in region AC00-D7A3
        // 2) handle CJK Ideographs
        // 3) handle character names as given in UnicodeData.txt
        // 4) handle all aliases as given in NameAliases.txt
        // With ICU's UCharacter, we get cases 1), 2) and 3). As for 4), the aliases, ICU only
        // handles aliases of type 'correction'. Therefore, we extract the contents of
        // NameAliases.txt and handle aliases by ourselves.
        String normalizedName = characterName.trim().toUpperCase(Locale.ROOT);
        if (UnicodeCharacterAliases.CHARACTER_ALIASES.containsKey(normalizedName)) {
            return UnicodeCharacterAliases.CHARACTER_ALIASES.get(normalizedName);
        } else {
            return UCharacter.getCharFromName(characterName);
        }
    }

    /**
     * Parses a character class. The syntax is very much the same as in ECMAScript Unicode regexes.
     * Assumes that the opening {@code '['} was already parsed.
     */
    private void characterClass() {
        emitSnippet("[");
        int start = position - 1;
        if (match("^")) {
            emitSnippet("^");
        }
        int firstPosInside = position;
        classBody: while (true) {
            if (atEnd()) {
                throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_CHARACTER_SET, start);
            }
            int rangeStart = position;
            Optional<Integer> lowerBound;
            int ch = consumeChar();
            switch (ch) {
                case ']':
                    if (position == firstPosInside + 1) {
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
                    lowerBound = Optional.of(ch);
            }
            if (match("-")) {
                if (atEnd()) {
                    throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_CHARACTER_SET, start);
                }
                Optional<Integer> upperBound;
                ch = consumeChar();
                switch (ch) {
                    case ']':
                        if (lowerBound.isPresent()) {
                            emitChar(lowerBound.get(), true);
                        }
                        emitChar('-', true);
                        emitSnippet("]");
                        break classBody;
                    case '\\':
                        upperBound = classEscape();
                        break;
                    default:
                        upperBound = Optional.of(ch);
                }
                if (!lowerBound.isPresent() || !upperBound.isPresent() || upperBound.get() < lowerBound.get()) {
                    throw syntaxErrorAtAbs(PyErrorMessages.badCharacterRange(inPattern.substring(rangeStart, position)), rangeStart);
                }
                curCharClass.clear();
                curCharClass.addRange(lowerBound.get(), upperBound.get());
                emitCharSet();
            } else if (lowerBound.isPresent()) {
                emitChar(lowerBound.get(), true);
            }
        }
    }

    /**
     * Like {@link #escape}, but restricted to the forms of escapes usable in character classes.
     * This includes character escapes and character class escapes, but not assertion escapes or
     * backreferences.
     *
     * @return {@code Optional.of(ch)} if the escape sequence was a character escape sequence for
     *         some character {@code ch}; {@code Optional.empty()} if it was a character class
     *         escape sequence
     */
    private Optional<Integer> classEscape() {
        if (categoryEscape(true)) {
            return Optional.empty();
        }
        return Optional.of(silentCharacterEscape());
    }

    /**
     * Parses a quantifier whose first character is the argument {@code ch}.
     */
    private void quantifier(int ch) {
        int start = position - 1;
        if (ch == '{') {
            if (match("}")) {
                // We did not find a complete quantifier, so we should just emit a string of
                // matchers the individual characters.
                emitString(inPattern.substring(start, position));
                lastTerm = TermCategory.Atom;
                return;
            } else if (match(",}")) {
                // Python interprets A{,} as A*, whereas ECMAScript does not accept such a range
                // quantifier.
                emitSnippet("*");
            } else {
                Optional<BigInteger> lowerBound = Optional.empty();
                Optional<BigInteger> upperBound = Optional.empty();
                String lower = getMany(PythonRegexParser::isDecDigit);
                if (!lower.isEmpty()) {
                    lowerBound = Optional.of(new BigInteger(lower));
                }
                if (match(",")) {
                    String upper = getMany(PythonRegexParser::isDecDigit);
                    if (!upper.isEmpty()) {
                        upperBound = Optional.of(new BigInteger(upper));
                    }
                } else {
                    upperBound = lowerBound;
                }
                if (!match("}")) {
                    // We did not find a complete quantifier, so we should just emit a string of
                    // matchers the individual characters.
                    emitString(inPattern.substring(start, position));
                    lastTerm = TermCategory.Atom;
                    return;
                }
                if (lowerBound.isPresent() && upperBound.isPresent() && lowerBound.get().compareTo(upperBound.get()) > 0) {
                    throw syntaxErrorAtAbs(PyErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT, start + 1);
                }
                if (lowerBound.isPresent()) {
                    emitSnippet(inPattern.substring(start, position));
                } else {
                    // {,upperBound} is invalid in JS in unicode mode, but always valid in Python,
                    // so we insert an explicit lower bound 0
                    emitSnippet("{0,");
                    assert inPattern.charAt(start) == '{' && inPattern.charAt(start + 1) == ',';
                    emitSnippet(inPattern.substring(start + 2, position));
                }
            }
        } else {
            emitRawCodepoint(ch);
        }

        switch (lastTerm) {
            case None:
            case Assertion:
                throw syntaxErrorAtAbs(PyErrorMessages.NOTHING_TO_REPEAT, start);
            case Quantifier:
                throw syntaxErrorAtAbs(PyErrorMessages.MULTIPLE_REPEAT, start);
            case Atom:
                if (match("?")) {
                    emitSnippet("?");
                }
                lastTerm = TermCategory.Quantifier;
        }
    }

    /**
     * Parses one of the many syntactic forms that start with a parenthesis, assuming that the
     * parenthesis was already parsed. These consist of the following:
     * <ul>
     * <li>named capture groups (?P<name>...)</li>
     * <li>named backreferences (?P=name)</li>
     * <li>non-capturing groups (?:...)</li>
     * <li>comments (?#...)</li>
     * <li>positive and negative lookbehind assertions, (?<=...) and (?<!...)</li>
     * <li>positive and negative lookahead assertions (?=...) and (?!...)</li>
     * <li>conditional backreferences (?(id/name)yes-pattern|no-pattern)</li>
     * <li>inline local and global flags, (?aiLmsux-imsx:...) and (?aiLmsux)</li>
     * <li>regular capture groups (...)</li>
     * </ul>
     */
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
                                    namedBackreference();
                                    break;
                                }
                                default:
                                    throw syntaxErrorAtRel(PyErrorMessages.unknownExtensionP(ch2), 3);
                            }
                            break;
                        }

                        case ':':
                            group(false, Optional.empty(), start);
                            break;

                        case '#':
                            parenComment();
                            break;

                        case '<': {
                            mustHaveMore();
                            final int ch2 = consumeChar();
                            switch (ch2) {
                                case '=':
                                    lookbehind(true);
                                    break;
                                case '!':
                                    lookbehind(false);
                                    break;
                                default:
                                    throw syntaxErrorAtRel(PyErrorMessages.unknownExtensionLt(ch2), 3);
                            }
                            break;
                        }

                        case '=':
                            lookahead(true);
                            break;

                        case '!':
                            lookahead(false);
                            break;

                        case '(':
                            conditionalBackreference();
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
                            flags(ch1);
                            break;

                        default:
                            throw syntaxErrorAtRel(PyErrorMessages.unknownExtensionQ(ch1), 2);
                    }
                    break;

                default:
                    retreat();
                    group(true, Optional.empty(), start);
            }
        } else {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, start);
        }
    }

    /**
     * Parses a group name terminated by the given character.
     *
     * @return the group name
     */
    private String parseGroupName(char terminator) {
        assert terminator == '>' || terminator == ')';
        String groupName = getMany(c -> c != terminator);
        if (groupName.isEmpty()) {
            throw syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
        }
        if (!match(Character.toString(terminator))) {
            throw syntaxErrorAtRel(terminator == ')' ? PyErrorMessages.UNTERMINATED_NAME : PyErrorMessages.UNTERMINATED_NAME_ANGLE_BRACKET, groupName.length());
        }
        if (!checkGroupName(groupName)) {
            throw syntaxErrorAtRel(PyErrorMessages.badCharacterInGroupName(groupName), groupName.length() + 1);
        }
        return groupName;
    }

    /**
     * Determines whether the given {@link String} is a valid name for a group.
     *
     * @return {@code true} if the argument is a valid group name
     */
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

    /**
     * Parses a named backreference, assuming that the prefix '(?P=' was already parsed.
     */
    private void namedBackreference() {
        String groupName = parseGroupName(')');
        if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
            int groupNumber = namedCaptureGroups.get(groupName);
            verifyGroupReference(groupNumber, groupName);
            emitSnippet("\\" + groupNumber);
            lastTerm = TermCategory.Atom;
        } else {
            throw syntaxErrorAtRel(PyErrorMessages.unknownGroupName(groupName), groupName.length() + 1);
        }
    }

    /**
     * Parses a parenthesized comment, assuming that the '(?#' prefix was already parsed.
     */
    private void parenComment() {
        int start = position - 3;
        getMany(c -> c != ')');
        if (!match(")")) {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_COMMENT, start);
        }
    }

    /**
     * Parses a group, assuming that its opening parenthesis has already been parsed. Note that this
     * is used not only for ordinary capture groups, but also for named capture groups,
     * non-capturing groups or the contents of a local flags block.
     *
     * @param capturing whether or not we should emit a capturing group
     * @param optName the name of the group, if there is any, to be registered by the parser
     * @param start the position in the input pattern where the group starts, used for error
     *            reporting purposes
     */
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
                throw syntaxErrorAtRel(PyErrorMessages.redefinitionOfGroupName(name, groups, namedCaptureGroups.get(name)), name.length() + 1);
            }
            namedCaptureGroups.put(name, groups);
        });
        disjunction();
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, start);
        }
        if (capturing) {
            groupStack.pop();
        }
        lastTerm = TermCategory.Atom;
    }

    /**
     * Parses a lookahead assertion, assuming that the opening parantheses and special characters
     * (either '(?=' or '(?!') have already been parsed.
     *
     * @param positive {@code true} if the assertion to be emitted is a positive lookahead assertion
     */
    private void lookahead(boolean positive) {
        int start = position - 3;
        if (positive) {
            emitSnippet("(?:(?=");
        } else {
            emitSnippet("(?:(?!");
        }
        disjunction();
        if (match(")")) {
            emitSnippet("))");
        } else {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, start);
        }
        lastTerm = TermCategory.Atom;
    }

    /**
     * Just like {@link #lookahead}, but for lookbehind assertions.
     */
    private void lookbehind(boolean positive) {
        int start = position - 4;
        if (positive) {
            emitSnippet("(?:(?<=");
        } else {
            emitSnippet("(?:(?<!");
        }
        lookbehindStack.push(new Lookbehind(groups + 1));
        disjunction();
        lookbehindStack.pop();
        if (match(")")) {
            emitSnippet("))");
        } else {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, start);
        }
        lastTerm = TermCategory.Atom;
    }

    /**
     * Parses a conditional backreference, assuming that the prefix '(?(' was already parsed.
     */
    private void conditionalBackreference() {
        int start = position - 3;
        bailOut("conditional backreference groups not supported");
        String groupId = getMany(c -> c != ')');
        if (groupId.isEmpty()) {
            throw syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
        }
        if (!match(Character.toString(')'))) {
            throw syntaxErrorAtRel(PyErrorMessages.UNTERMINATED_NAME, groupId.length());
        }
        int groupNumber;
        if (checkGroupName(groupId)) {
            // group referenced by name
            if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupId)) {
                groupNumber = namedCaptureGroups.get(groupId);
            } else {
                throw syntaxErrorAtRel(PyErrorMessages.unknownGroupName(groupId), groupId.length() + 1);
            }
        } else {
            try {
                groupNumber = Integer.parseInt(groupId);
                if (groupNumber < 0) {
                    throw new NumberFormatException(PyErrorMessages.NEGATIVE_GROUP_NUMBER);
                }
            } catch (NumberFormatException e) {
                throw syntaxErrorAtRel(PyErrorMessages.badCharacterInGroupName(groupId), groupId.length() + 1);
            }
        }
        if (!lookbehindStack.isEmpty()) {
            verifyGroupReference(groupNumber, groupId);
        }
        disjunction();
        if (match("|")) {
            disjunction();
            if (curChar() == '|') {
                throw syntaxErrorHere(PyErrorMessages.CONDITIONAL_BACKREF_WITH_MORE_THAN_TWO_BRANCHES);
            }
        }
        if (!match(")")) {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, start);
        }
        lastTerm = TermCategory.Atom;
    }

    /**
     * Parses a local flag block or an inline declaration of a global flags. Assumes that the prefix
     * '(?' was already parsed, as well as the first flag which is passed as the argument.
     */
    private void flags(int ch0) {
        int start = position - 3;
        int ch = ch0;
        PythonFlags positiveFlags = PythonFlags.EMPTY_INSTANCE;
        while (PythonFlags.isValidFlagChar(ch)) {
            positiveFlags = positiveFlags.addFlag(ch);
            if (mode == PythonREMode.Str && ch == 'L') {
                throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_USE_L_FLAG_WITH_A_STR_PATTERN);
            }
            if (mode == PythonREMode.Bytes && ch == 'u') {
                throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_USE_U_FLAG_WITH_A_BYTES_PATTERN);
            }
            if (positiveFlags.numberOfTypeFlags() > 1) {
                throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_FLAGS_A_U_AND_L_ARE_INCOMPATIBLE);
            }
            if (atEnd()) {
                throw syntaxErrorHere(PyErrorMessages.MISSING_DASH_COLON_PAREN);
            }
            ch = consumeChar();
        }
        switch (ch) {
            case ')':
                globalFlags = globalFlags.addFlags(positiveFlags);
                break;
            case ':':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_ON_GLOBAL_FLAG, 1);
                }
                localFlags(positiveFlags, PythonFlags.EMPTY_INSTANCE, start);
                break;
            case '-':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_ON_GLOBAL_FLAG, 1);
                }
                if (atEnd()) {
                    throw syntaxErrorHere(PyErrorMessages.MISSING_FLAG);
                }
                ch = consumeChar();
                if (!PythonFlags.isValidFlagChar(ch)) {
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                    } else {
                        throw syntaxErrorAtRel(PyErrorMessages.MISSING_FLAG, 1);
                    }
                }
                PythonFlags negativeFlags = PythonFlags.EMPTY_INSTANCE;
                while (PythonFlags.isValidFlagChar(ch)) {
                    negativeFlags = negativeFlags.addFlag(ch);
                    if (PythonFlags.isTypeFlagChar(ch)) {
                        throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_OFF_FLAGS_A_U_AND_L);
                    }
                    if (atEnd()) {
                        throw syntaxErrorHere(PyErrorMessages.MISSING_COLON);
                    }
                    ch = consumeChar();
                }
                if (ch != ':') {
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                    } else {
                        throw syntaxErrorAtRel(PyErrorMessages.MISSING_COLON, 1);
                    }
                }
                if (negativeFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_OFF_GLOBAL_FLAG, 1);
                }
                localFlags(positiveFlags, negativeFlags, start);
                break;
            default:
                if (Character.isAlphabetic(ch)) {
                    throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                } else {
                    throw syntaxErrorAtRel(PyErrorMessages.MISSING_DASH_COLON_PAREN, 1);
                }
        }
    }

    /**
     * Parses a block with local flags, assuming that the opening parenthesis, the flags and the ':'
     * have been parsed.
     *
     * @param positiveFlags - the flags to be turned on in the block
     * @param negativeFlags - the flags to be turned off in the block
     * @param start - the position in {@link #inPattern} where the block started, for error
     *            reporting purposes
     */
    private void localFlags(PythonFlags positiveFlags, PythonFlags negativeFlags, int start) {
        if (positiveFlags.overlaps(negativeFlags)) {
            throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_FLAG_TURNED_ON_AND_OFF, 1);
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
}
