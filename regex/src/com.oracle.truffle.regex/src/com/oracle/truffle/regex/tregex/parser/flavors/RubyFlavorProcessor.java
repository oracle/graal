/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Implements the parsing and translating of Ruby regular expressions to ECMAScript regular
 * expressions.
 *
 * @see RegexFlavorProcessor
 */
public final class RubyFlavorProcessor implements RegexFlavorProcessor {

    /**
     * Characters that are considered special in ECMAScript regexes. To match these characters, they
     * need to be escaped using a backslash.
     */
    private static final CompilationFinalBitSet SYNTAX_CHARACTERS = CompilationFinalBitSet.valueOf('^', '$', '\\', '.', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|');
    /**
     * Characters that are considered special in ECMAScript regex character classes.
     */
    private static final CompilationFinalBitSet CHAR_CLASS_SYNTAX_CHARACTERS = CompilationFinalBitSet.valueOf('\\', ']', '-', '^');

    /**
     * Maps Python's predefined Unicode character classes to sets containing the characters to be
     * matched.
     */
    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASSES;
    private static final Map<Character, CodePointSet> ASCII_CHAR_CLASSES;

    private static final Map<String, CodePointSet> UNICODE_POSIX_CHAR_CLASSES;
    private static final Map<String, CodePointSet> ASCII_POSIX_CHAR_CLASSES;

    private static final Map<String, CodePointSet> POSIX_PROPERTIES;

    static {
        CodePointSet ASCII_RANGE = CodePointSet.create(0x00, 0x7F);
        CodePointSet NON_ASCII_RANGE = CodePointSet.create(0x80, Character.MAX_CODE_POINT);

        UNICODE_CHAR_CLASSES = new HashMap<>(8);
        ASCII_CHAR_CLASSES = new HashMap<>(8);

        CodePointSet alpha = UnicodeProperties.getProperty("Alphabetic");
        CodePointSet digit = UnicodeProperties.getProperty("General_Category=Decimal_Number");
        CodePointSet space = UnicodeProperties.getProperty("White_Space");
        CodePointSet xdigit = CodePointSet.create('0', '9', 'A', 'F', 'a', 'f');
        CodePointSet word = alpha.union(UnicodeProperties.getProperty("General_Category=Mark")).union(digit).union(UnicodeProperties.getProperty("General_Category=Connector_Punctuation")).union(UnicodeProperties.getProperty("Join_Control"));
        UNICODE_CHAR_CLASSES.put('d', digit);
        UNICODE_CHAR_CLASSES.put('h', xdigit);
        UNICODE_CHAR_CLASSES.put('s', space);
        UNICODE_CHAR_CLASSES.put('w', word);

        for (char ctypeChar : new Character[] { 'd', 'h', 's', 'w' }) {
            CodePointSet charSet = UNICODE_CHAR_CLASSES.get(ctypeChar);
            char complementCTypeChar = Character.toUpperCase(ctypeChar);
            CodePointSet complementCharSet = charSet.createInverse(Encodings.UTF_32);
            UNICODE_CHAR_CLASSES.put(complementCTypeChar, complementCharSet);
            ASCII_CHAR_CLASSES.put(ctypeChar, ASCII_RANGE.createIntersectionSingleRange(charSet));
            ASCII_CHAR_CLASSES.put(complementCTypeChar, complementCharSet.union(NON_ASCII_RANGE));
        }

        UNICODE_POSIX_CHAR_CLASSES = new HashMap<>(14);
        ASCII_POSIX_CHAR_CLASSES = new HashMap<>(14);
        CompilationBuffer buffer = new CompilationBuffer(Encodings.UTF_32);

        CodePointSet blank = UnicodeProperties.getProperty("General_Category=Space_Separator").union(CodePointSet.create('\t', '\t'));
        CodePointSet cntrl = UnicodeProperties.getProperty("General_Category=Control");
        CodePointSet graph = space.union(UnicodeProperties.getProperty("General_Category=Control")).union(UnicodeProperties.getProperty("General_Category=Surrogate")).union(UnicodeProperties.getProperty("General_Category=Unassigned")).createInverse(Encodings.UTF_32);
        UNICODE_POSIX_CHAR_CLASSES.put("alpha", alpha);
        UNICODE_POSIX_CHAR_CLASSES.put("alnum", alpha.union(digit));
        UNICODE_POSIX_CHAR_CLASSES.put("blank", blank);
        UNICODE_POSIX_CHAR_CLASSES.put("cntrl", cntrl);
        UNICODE_POSIX_CHAR_CLASSES.put("digit", digit);
        UNICODE_POSIX_CHAR_CLASSES.put("graph", graph);
        UNICODE_POSIX_CHAR_CLASSES.put("lower", UnicodeProperties.getProperty("Lowercase"));
        UNICODE_POSIX_CHAR_CLASSES.put("print", graph.union(blank).subtract(cntrl, buffer));
        UNICODE_POSIX_CHAR_CLASSES.put("punct", UnicodeProperties.getProperty("General_Category=Punctuation").union(UnicodeProperties.getProperty("General_Category=Symbol").subtract(alpha, buffer)));
        UNICODE_POSIX_CHAR_CLASSES.put("space", space);
        UNICODE_POSIX_CHAR_CLASSES.put("upper", UnicodeProperties.getProperty("Uppercase"));
        UNICODE_POSIX_CHAR_CLASSES.put("xdigit", xdigit);

        UNICODE_POSIX_CHAR_CLASSES.put("word", word);
        UNICODE_POSIX_CHAR_CLASSES.put("ascii", UnicodeProperties.getProperty("ASCII"));

        for (Map.Entry<String, CodePointSet> entry: UNICODE_POSIX_CHAR_CLASSES.entrySet()) {
            ASCII_POSIX_CHAR_CLASSES.put(entry.getKey(), ASCII_RANGE.createIntersectionSingleRange(entry.getValue()));
        }

        POSIX_PROPERTIES = new HashMap<>(14);
        POSIX_PROPERTIES.put("Alnum", UNICODE_POSIX_CHAR_CLASSES.get("alnum"));
        POSIX_PROPERTIES.put("Alpha", UNICODE_POSIX_CHAR_CLASSES.get("alpha"));
        POSIX_PROPERTIES.put("Blank", UNICODE_POSIX_CHAR_CLASSES.get("blank"));
        POSIX_PROPERTIES.put("Cntrl", UNICODE_POSIX_CHAR_CLASSES.get("cntrl"));
        POSIX_PROPERTIES.put("Digit", UNICODE_POSIX_CHAR_CLASSES.get("digit"));
        POSIX_PROPERTIES.put("Graph", UNICODE_POSIX_CHAR_CLASSES.get("graph"));
        POSIX_PROPERTIES.put("Lower", UNICODE_POSIX_CHAR_CLASSES.get("lower"));
        POSIX_PROPERTIES.put("Print", UNICODE_POSIX_CHAR_CLASSES.get("print"));
        POSIX_PROPERTIES.put("Punct", UNICODE_POSIX_CHAR_CLASSES.get("punct"));
        POSIX_PROPERTIES.put("Space", UNICODE_POSIX_CHAR_CLASSES.get("space"));
        POSIX_PROPERTIES.put("Upper", UNICODE_POSIX_CHAR_CLASSES.get("upper"));
        POSIX_PROPERTIES.put("XDigit", UNICODE_POSIX_CHAR_CLASSES.get("xdigit"));
        POSIX_PROPERTIES.put("Word", UNICODE_POSIX_CHAR_CLASSES.get("word"));
        POSIX_PROPERTIES.put("ASCII", UNICODE_POSIX_CHAR_CLASSES.get("ascii"));
    }

    public static final Pattern WORD_CHARS_PATTERN = Pattern.compile("\\\\[wW]");
    public static final String WORD_BOUNDARY = "(?:(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$))";
    public static final String WORD_NON_BOUNDARY = "(?:(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w))";

    /**
     * An enumeration of the possible grammatical categories of Python regex terms.
     */
    private enum TermCategory {
        /**
         * A lookahead, lookbehind, beginning-of-string/line, end-of-string/line or
         * (non)-word-boundary assertion.
         */
        Assertion,
        /**
         * A literal character, a character class or a group.
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
    private static final CompilationFinalBitSet WHITESPACE = CompilationFinalBitSet.valueOf(' ', '\t', '\n', '\r', '\u000b', '\f');

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
     * Indicates whether the regex will be passed in as a byte array.
     */
    private final boolean bytes;
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
     * The global flags are the flags given when compiling the regular expression.
     */
    private final RubyFlags globalFlags;
    /**
     * A stack of the locally enabled flags. Python enables the setting and unsetting of the flags
     * for subexpressions of the regex.
     * <p>
     * The currently active flags are at the top, the flags that would become active after the end
     * of the next (?aiLmsux-imsx:...) expression are just below.
     */
    private final Deque<RubyFlags> flagsStack;
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

    /**
     * The contents of the character class that is currently being parsed.
     */
    private CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    /**
     * A temporary buffer for case folding and inverting character classes.
     */
    private CodePointSetAccumulator charClassTmp = new CodePointSetAccumulator();
    /**
     * When parsing nested character classes, we need several instances of {@link CodePointSetAccumulator}s. In order
     * to avoid having to repeatedly allocate new ones, we return unused instances to this shared pool, to be reused
     * later.
     */
    private final List<CodePointSetAccumulator> charClassPool = new ArrayList<>();

    @TruffleBoundary
    public RubyFlavorProcessor(RegexSource source, boolean bytes) {
        this.inSource = source;
        this.inPattern = source.getPattern();
        this.inFlags = source.getFlags();
        this.bytes = bytes;
        this.position = 0;
        this.outPattern = new StringBuilder(inPattern.length());
        this.globalFlags = new RubyFlags(inFlags);
        this.flagsStack = new LinkedList<>();
        this.lookbehindStack = new ArrayDeque<>();
        this.groupStack = new ArrayDeque<>();
        this.namedCaptureGroups = null;
        this.groups = 0;
        this.lastTerm = TermCategory.None;
    }

    @Override
    public int getNumberOfCaptureGroups() {
        // include capture group 0
        return groups + 1;
    }

    @Override
    public Map<String, Integer> getNamedCaptureGroups() {
        return namedCaptureGroups;
    }

    @Override
    public TruffleObject getFlags() {
        return globalFlags;
    }

    @Override
    public boolean isUnicodePattern() {
        // We always return true; see the comment in #toECMAScriptRegex.
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
        // When translating to ECMAScript, we always the dotAll and unicode flags. The dotAll flag
        // lets us translate Python's dotAll . directly. The unicode flag lets us use some of the
        // ECMAScript regex escape sequences which are restricted to Unicode regexes. It also lets
        // us reason with a more rigid grammar (as the ECMAScript non-Unicode regexes contain a lot
        // of ambiguous syntactic constructions for backwards compatibility). It is fine to use
        // Unicode ECMAScript regexes for both 'str' and 'bytes' patterns. In 'str' patterns, we
        // actually want to match on the individual code points of the Unicode string. In 'bytes'
        // patterns, all characters are in the range 0-255 and so the Unicode flag does not
        // interfere with the matching (no surrogates).
        return new RegexSource(outPattern.toString(), globalFlags.isSticky() ? "suy" : "su", inSource.getEncoding());
    }

    private RubyFlags getLocalFlags() {
        return flagsStack.peek();
    }

    private void setLocalFlags(RubyFlags newLocalFlags) {
        flagsStack.pop();
        flagsStack.push(newLocalFlags);
    }

    /// Input scanning

    private int curChar() {
        if (bytes) {
            return inPattern.charAt(position);
        } else {
            return inPattern.codePointAt(position);
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

    private void retreat(int len) {
        advance(-len);
    }

    private void advance(int len) {
        if (bytes) {
            position += len;
        } else {
            position = inPattern.offsetByCodePoints(position, len);
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

    private void mustMatch(String match) {
        if (!match(match)) {
            throw syntaxErrorHere("expected " + match);
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    private void mustHaveMore() {
        if (atEnd()) {
            throw syntaxErrorHere("unexpected end of pattern");
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
            CompilationFinalBitSet syntaxChars = inCharClass ? CHAR_CLASS_SYNTAX_CHARACTERS : SYNTAX_CHARACTERS;
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

    private void emitCharSet(CodePointSet charSet) {
        if (!silent) {
            curCharClass.clear();
            curCharClass.addSet(charSet);
            emitSnippet("[");
            emitCharSet();
            emitSnippet("]");
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
        bailOut("Ruby-style case folding is not supported");
    }

    private void negateCharClass() {
        charClassTmp.clear();
        curCharClass.invert(charClassTmp, inSource.getEncoding());
        CodePointSetAccumulator swap = curCharClass;
        curCharClass = charClassTmp;
        charClassTmp = swap;
    }

    /// Error reporting

    private RegexSyntaxException syntaxErrorAtRel(String message, int offset) {
        int atPosition = !bytes ? inPattern.offsetByCodePoints(position, -offset) : position - offset;
        return syntaxErrorAtAbs(message, atPosition);
    }

    private RegexSyntaxException syntaxErrorAtAbs(String message, int atPosition) {
        int reportedPosition = !bytes ? inPattern.codePointCount(0, atPosition) : atPosition;
        return syntaxError(message, reportedPosition);
    }

    private RegexSyntaxException syntaxErrorHere(String message) {
        return syntaxErrorAtAbs(message, position);
    }

    private RegexSyntaxException syntaxError(String message, int atPosition) {
        return new RegexSyntaxException(inSource, message, atPosition);
    }

    // Character predicates

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

    private void parse() {
        flagsStack.push(globalFlags);
        disjunction();
        flagsStack.pop();

        if (!atEnd()) {
            assert curChar() == ')';
            throw syntaxErrorAtRel("unbalanced parenthesis", 0);
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
        flagsStack.push(getLocalFlags());
        while (!atEnd() && curChar() != '|' && curChar() != ')') {
            term();
        }
        flagsStack.pop();
    }

    /**
     * Parses a term. A term is either:
     * <ul>
     * <li>whitespace (if in extended mode)</li>
     * <li>a comment (if in extended mode)</li>
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

        if (getLocalFlags().isExtended()) {
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
                if (getLocalFlags().isMultiline()) {
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
                emitSnippet("(?:^|(?<=[\\n])(?=.))");
                lastTerm = TermCategory.Assertion;
                break;
            case '$':
                emitSnippet("(?:$|(?=[\\n]))");
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
     * <li>named backreferences</li>
     * <li>extended grapheme clusters</li>
     * <li>subexpression calls</li>
     * </ul>
     */
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
        if (namedBackreference()) {
            lastTerm = TermCategory.Atom;
            return;
        }
        if (extendedGraphemeCluster()) {
            lastTerm = TermCategory.Atom;
            return;
        }
        if (subexpressionCall()) {
            lastTerm = TermCategory.Atom;
            return;
        }
        if (stringEscape()) {
            lastTerm = TermCategory.Atom;
            return;
        }
        // characterEscape has to come after assertionEscape because of the ambiguity of \b, which
        // (outside of character classes) is resolved in the favor of the assertion.
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
                emitSnippet("(?:$|(?=[\\r\\n]$))");
                return true;
            case 'z':
                emitSnippet("$");
                return true;
            case 'G':
                bailOut("\\G escape sequence is not supported");
                return true;
            case 'b':
                if (getLocalFlags().isAscii()) {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, ASCII_CHAR_CLASSES.get('w'), ASCII_CHAR_CLASSES.get('W'));
                } else {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, UNICODE_CHAR_CLASSES.get('w'), UNICODE_CHAR_CLASSES.get('W'));
                }
                return true;
            case 'B':
                if (getLocalFlags().isAscii()) {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, ASCII_CHAR_CLASSES.get('w'), ASCII_CHAR_CLASSES.get('W'));
                } else {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, UNICODE_CHAR_CLASSES.get('w'), UNICODE_CHAR_CLASSES.get('W'));
                }
                return true;
            default:
                retreat();
                return false;
        }
    }

    private void emitWordBoundaryAssertion(String snippetTemplate, CodePointSet wordChars, CodePointSet nonWordChars) {
        Matcher matcher = WORD_CHARS_PATTERN.matcher(snippetTemplate);
        int lastAppendPosition = 0;
        while (matcher.find()) {
            emitSnippet(snippetTemplate.substring(lastAppendPosition, matcher.start()));
            if (matcher.group().equals("\\w")) {
                emitCharSet(wordChars);
            } else {
                assert matcher.group().equals("\\W");
                emitCharSet(nonWordChars);
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
            case 'h':
            case 'H':
            case 's':
            case 'S':
            case 'w':
            case 'W':
                char className = (char) curChar();
                advance();
                CodePointSet charSet;
                if (getLocalFlags().isAscii() || getLocalFlags().isDefault()) {
                    charSet = ASCII_CHAR_CLASSES.get(className);
                } else {
                    assert getLocalFlags().isUnicode();
                    charSet = UNICODE_CHAR_CLASSES.get(className);
                }
                if (inCharClass) {
                    curCharClass.addSet(charSet);
                } else {
                    emitCharSet(charSet);
                }
                return true;
            case 'p':
                if (match("p{")) {
                    String propertySpec = getMany(c -> c != '}');
                    if (atEnd()) {
                        retreat(propertySpec.length() + 2);
                        return false;
                    } else {
                        advance();
                    }
                    boolean negative = propertySpec.startsWith("^");
                    if (negative) {
                        propertySpec = propertySpec.substring(1);
                    }
                    CodePointSet property;
                    if (POSIX_PROPERTIES.containsKey(propertySpec)) {
                        property = POSIX_PROPERTIES.get(propertySpec);
                    } else if (UnicodeProperties.isSupportedGeneralCategory(propertySpec)) {
                        property = UnicodeProperties.getProperty("General_Category=" + propertySpec);
                    } else if (UnicodeProperties.isSupportedScript(propertySpec)) {
                        property = UnicodeProperties.getProperty("Script=" + propertySpec);
                    } else if (!UnicodeProperties.isSupportedProperty(propertySpec)) {
                        throw syntaxErrorAtRel(String.format("invalid character property name {%s}", propertySpec), propertySpec.length());
                    } else {
                        property = UnicodeProperties.getProperty(propertySpec);
                    }
                    if (negative) {
                        property = property.createInverse(Encodings.UTF_32);
                    }
                    if (inCharClass) {
                        curCharClass.addSet(property);
                    } else {
                        emitCharSet(property);
                    }
                    return true;
                } else {
                    return false;
                }
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
            String number = getUpTo(2, RubyFlavorProcessor::isDecDigit);
            int groupNumber = Integer.parseInt(number);
            if (groupNumber > groups) {
                throw syntaxErrorAtRel("invalid group reference " + number, number.length());
            }
            emitBackreference(groupNumber);
            return true;
        } else {
            return false;
        }
    }

    private boolean namedBackreference() {
        if (match("k<")) {
            String groupName = parseGroupName('>');
            if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
                int groupNumber = namedCaptureGroups.get(groupName);
                emitBackreference(groupNumber);
            } else {
                throw syntaxErrorAtRel("unknown group name " + groupName, groupName.length() + 1);
            }
            return true;
        } else {
            return false;
        }
    }

    private void emitBackreference(int groupNumber) {
        if (isCaptureGroupOpen(groupNumber)) {
            // Ruby syntax allows references to an open capture group. However, such a reference can
            // never match anything as the capture group is reset on entry.
            emitSnippet("[]");
        } else if (getLocalFlags().isIgnoreCase()) {
            bailOut("case insensitive backreferences not supported");
        } else {
            emitSnippet("\\" + groupNumber);
        }
    }

    private boolean isCaptureGroupOpen(int groupNumber) {
        for (Group openGroup : groupStack) {
            if (groupNumber == openGroup.groupNumber) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies that making a backreference to a certain group is legal in the current context.
     *
     * @param groupNumber the index of the referred group
     * @param groupName the name of the group, for error reporting purposes
     * @throws RegexSyntaxException if the backreference is not valid
     */
    private void verifyGroupReference(int groupNumber, String groupName) throws RegexSyntaxException {
        for (Lookbehind openLookbehind : lookbehindStack) {
            if (groupNumber >= openLookbehind.containedGroups) {
                throw syntaxErrorHere("cannot refer to group defined in the same lookbehind subpattern");
            }
        }
    }

    private boolean extendedGraphemeCluster() {
        if (curChar() == 'X') {
            advance();
            bailOut("extended grapheme cluster escape not supported");
            return true;
        } else {
            return false;
        }
    }

    private boolean subexpressionCall() {
        if (match("g<")) {
            String groupName = parseGroupName('>');
            if (namedCaptureGroups == null || !namedCaptureGroups.containsKey(groupName)) {
                throw syntaxErrorAtRel(String.format("undefined name <%s> reference", groupName), groupName.length() + 1);
            }
            bailOut("subexpression calls not supported");
            return true;
        } else {
            return false;
        }
    }

    private boolean stringEscape() {
        if (match("u{")) {
            getMany(c -> WHITESPACE.get(c));
            while (!match("}")) {
                String code = getMany(RubyFlavorProcessor::isHexDigit);
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxErrorHere("unicode escape value " + code + " outside of range 0-0x10FFFF");
                    }
                    emitChar(codePoint);
                } catch (NumberFormatException e) {
                    throw syntaxErrorHere("bad escape \\u" + code);
                }
                getMany(c -> WHITESPACE.get(c));
            }
            return true;
        } else {
            return false;
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
            case 'e':
                return '\u001b';
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
            case 'c':
            case 'C': {
                if (atEnd()) {
                    throw syntaxErrorHere("end pattern at control");
                }
                if (ch == 'C' && !match("-")) {
                    throw syntaxErrorHere("invalid control-code syntax");
                }
                int c = consumeChar();
                if (c == '?') {
                    return 0177;
                }
                if (c == '\\') {
                    c = silentCharacterEscape();
                }
                return c & 0x9f;
            }
            case 'x': {
                String code = getUpTo(2, RubyFlavorProcessor::isHexDigit);
                return Integer.parseInt(code, 16);
            }
            case 'u': {
                String code;
                if (match("{")) {
                    code = getMany(RubyFlavorProcessor::isHexDigit);
                    mustMatch("}");
                } else {
                    code = getUpTo(4, RubyFlavorProcessor::isHexDigit);
                    if (code.length() < 4) {
                        throw syntaxErrorAtRel("incomplete escape \\u" + code, 2 + code.length());
                    }
                }
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxErrorAtRel("unicode escape value \\u" + code + " outside of range 0-0x10FFFF", 2 + code.length());
                    }
                    return codePoint;
                } catch (NumberFormatException e) {
                    throw syntaxErrorAtRel("bad escape \\u" + code, 2 + code.length());
                }
            }
            default:
                if (isOctDigit(ch)) {
                    retreat();
                    String code = getUpTo(3, RubyFlavorProcessor::isOctDigit);
                    int codePoint = Integer.parseInt(code, 8);
                    if (codePoint > 0377) {
                        throw syntaxErrorAtRel("octal escape value \\" + code + " outside of range 0-o377", 1 + code.length());
                    }
                    return codePoint;
                } else {
                    return ch;
                }
        }
    }

    /**
     * Parses a character class. The syntax is very much the same as in ECMAScript Unicode regexes.
     * Assumes that the opening {@code '['} was already parsed.
     */
    private void characterClass() {
        curCharClass.clear();
        collectCharClass();

        emitSnippet("[");
        emitCharSet();
        emitSnippet("]");
    }

    private void collectCharClass() {
        int start = position - 1;
        boolean negated = false;
        if (match("^")) {
            negated = true;
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
                        lowerBound = Optional.of((int) ']');
                    } else {
                        break classBody;
                    }
                    break;
                case '\\':
                    lowerBound = classEscape();
                    break;
                case '[':
                    nestedCharClass();
                    lowerBound = Optional.empty();
                    break;
                case '&':
                    if (match("&")) {
                        CodePointSetAccumulator curCharClassBackup = curCharClass;
                        curCharClass = acquireCodePointSetAccumulator();
                        collectCharClass();
                        curCharClassBackup.intersectWith(curCharClass.get());
                        curCharClass = curCharClassBackup;
                        break classBody;
                    }
                default:
                    lowerBound = Optional.of(ch);
            }
            if (match("-")) {
                if (atEnd()) {
                    throw syntaxErrorAtAbs("unterminated character set", start);
                }
                Optional<Integer> upperBound;
                ch = consumeChar();
                switch (ch) {
                    case ']':
                        if (lowerBound.isPresent()) {
                            curCharClass.addCodePoint(lowerBound.get());
                        }
                        curCharClass.addCodePoint('-');
                        break classBody;
                    case '\\':
                        upperBound = classEscape();
                        break;
                    case '[':
                        nestedCharClass();
                        upperBound = Optional.empty();
                        break;
                    default:
                        upperBound = Optional.of(ch);
                }
                if (!lowerBound.isPresent() || !upperBound.isPresent() || upperBound.get() < lowerBound.get()) {
                    throw syntaxErrorAtAbs("bad character range " + inPattern.substring(rangeStart, position), rangeStart);
                }
                curCharClass.addRange(lowerBound.get(), upperBound.get());
            } else if (lowerBound.isPresent()) {
                curCharClass.addCodePoint(lowerBound.get());
            }
        }
        if (negated) {
            negateCharClass();
        }
    }

    private CodePointSetAccumulator acquireCodePointSetAccumulator() {
        if (charClassPool.isEmpty()) {
            return new CodePointSetAccumulator();
        } else {
            CodePointSetAccumulator accumulator = charClassPool.remove(charClassPool.size() - 1);
            accumulator.clear();
            return accumulator;
        }
    }

    private void releaseCodePointSetAccumulator(CodePointSetAccumulator accumulator) {
        charClassPool.add(accumulator);
    }

    private void nestedCharClass() {
        CodePointSetAccumulator curCharClassBackup = curCharClass;
        curCharClass = acquireCodePointSetAccumulator();
        if (curChar() == ':') {
            advance();
            tryThis(this::collectPosixCharClass, this::collectCharClass);
        } else {
            collectCharClass();
        }
        curCharClassBackup.addSet(curCharClass.get());
        releaseCodePointSetAccumulator(curCharClass);
        curCharClass = curCharClassBackup;
    }

    private void tryThis(Supplier<Boolean> thisFirst, Runnable elseThis) {
        int branchPoint = position;
        if (!thisFirst.get()) {
            position = branchPoint;
            elseThis.run();
        }
    }

    private boolean collectPosixCharClass() {
        boolean negated = false;
        if (match("^")) {
            negated = true;
        }
        String className = getMany(c -> c != ':' && c != ']');
        if (match(":]")) {
            if (!UNICODE_POSIX_CHAR_CLASSES.containsKey(className)) {
                throw syntaxErrorAtRel("invalid POSIX bracket type", className.length());
            }
            CodePointSet charSet;
            if (getLocalFlags().isAscii()) {
                charSet = ASCII_POSIX_CHAR_CLASSES.get(className);
            } else {
                assert getLocalFlags().isDefault() || getLocalFlags().isUnicode();
                charSet = UNICODE_POSIX_CHAR_CLASSES.get(className);
            }
            charSet.appendRangesTo(curCharClass.get(), 0, charSet.size());
            if (negated) {
                negateCharClass();
            }
            return true;
        } else {
            return false;
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
                String lower = getMany(RubyFlavorProcessor::isDecDigit);
                if (!lower.isEmpty()) {
                    lowerBound = Optional.of(new BigInteger(lower));
                }
                if (match(",")) {
                    String upper = getMany(RubyFlavorProcessor::isDecDigit);
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
                    throw syntaxErrorAtAbs("min repeat greater than max repeat", start);
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
                throw syntaxErrorAtAbs("nothing to repeat", start);
            case Quantifier:
                throw syntaxErrorAtAbs("multiple repeat", start);
            case Atom:
                if (match("?")) {
                    emitSnippet("?");
                }
                if (match("+")) {
                    bailOut("possessive quantifiers not supported");
                }
                lastTerm = TermCategory.Quantifier;
        }
    }

    private Optional<Boolean> containsNamedCaptureGroups = Optional.empty();

    private boolean containsNamedCaptureGroups() {
        if (containsNamedCaptureGroups.isPresent()) {
            return containsNamedCaptureGroups.get();
        } else {
            // We are counting capture groups, so we only care about '(' characters and special
            // characters which can cancel the meaning of '(' - those include '\' for escapes, '[' for
            // character classes (where '(' stands for a literal '(') and any characters after the '('
            // which might turn into a non-capturing group or a look-around assertion.
            final int restorePosition = position;
            try {
                int charClassDepth = 0;
                while (!atEnd()) {
                    switch (consumeChar()) {
                        case '\\':
                            // skip escaped char
                            advance();
                            break;
                        case '[':
                            charClassDepth++;
                            if (!match("]")) {
                                match("^]");
                            }
                            break;
                        case ']':
                            charClassDepth--;
                            break;
                        case '(':
                            if (charClassDepth == 0 && match("?<")) {
                                containsNamedCaptureGroups = Optional.of(true);
                                return true;
                            }
                            break;
                        default:
                            break;
                    }
                }
                containsNamedCaptureGroups = Optional.of(false);
                return false;
            } finally {
                position = restorePosition;
            }
        }
    }

    /**
     * Parses one of the many syntactic forms that start with a parenthesis, assuming that the
     * parenthesis was already parsed. These consist of the following:
     * <ul>
     * <li>non-capturing groups (?:...)</li>
     * <li>comments (?#...)</li>
     * <li>positive and negative lookbehind assertions, (?<=...) and (?<!...)</li>
     * <li>positive and negative lookahead assertions (?=...) and (?!...)</li>
     * <li>named capture groups (?P<name>...)</li>
     * <li>atomic groups (?>...)</li>
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
                                    retreat();
                                    String groupName = parseGroupName('>');
                                    containsNamedCaptureGroups = Optional.of(true);
                                    group(true, Optional.of(groupName), start);
                                    break;
                            }
                            break;
                        }

                        case '=':
                            lookahead(true);
                            break;

                        case '!':
                            lookahead(false);
                            break;

                        case '>':
                            bailOut("atomic groups are not supported");
                            group(false, Optional.empty(), start);
                            break;

                        case '(':
                            conditionalBackreference();
                            break;

                        case '~':
                            absentExpression();
                            break;
                        case '-':
                        case 'm':
                        case 'i':
                        case 'x':
                        case 'a':
                        case 'd':
                        case 'u':
                            flags(ch1);
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

    /**
     * Parses a group name terminated by the given character.
     *
     * @return the group name
     */
    private String parseGroupName(char terminator) {
        String groupName = getMany(c -> c != terminator);
        if (!match(Character.toString(terminator))) {
            throw syntaxErrorAtRel("missing " + terminator + ", unterminated name", groupName.length());
        }
        checkGroupName(groupName);
        return groupName;
    }

    /**
     * Determines whether the given {@link String} is a valid name for a group.
     */
    private void checkGroupName(String groupName) throws RegexSyntaxException {
        if (groupName.isEmpty()) {
            throw syntaxErrorAtRel("missing group name", 1);
        }
    }

    /**
     * Parses a parenthesized comment, assuming that the '(#' prefix was already parsed.
     */
    private void parenComment() {
        int start = position - 2;
        getMany(c -> c != ')');
        if (!match(")")) {
            throw syntaxErrorAtAbs("missing ), unterminated comment", start);
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
        if (containsNamedCaptureGroups() && !optName.isPresent()) {
            capturing = false;
        }
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
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
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
            emitSnippet("(?=");
        } else {
            emitSnippet("(?!");
        }
        disjunction();
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        lastTerm = TermCategory.Assertion;
    }

    /**
     * Just like {@link #lookahead}, but for lookbehind assertions.
     */
    private void lookbehind(boolean positive) {
        int start = position - 4;
        if (positive) {
            emitSnippet("(?<=");
        } else {
            emitSnippet("(?<!");
        }
        lookbehindStack.push(new Lookbehind(groups + 1));
        disjunction();
        lookbehindStack.pop();
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        lastTerm = TermCategory.Assertion;
    }

    /**
     * Parses a conditional backreference, assuming that the prefix '(?(' was already parsed.
     */
    private void conditionalBackreference() {
        int start = position - 3;
        bailOut("conditional backreference groups not supported");
        String groupId = getMany(c -> c != ')');
        if (groupId.isEmpty()) {
            throw syntaxErrorHere("missing group name");
        }
        if (!match(Character.toString(')'))) {
            throw syntaxErrorAtRel("missing ), unterminated name", groupId.length());
        }
        int groupNumber;
        if (groupId.startsWith("<") && groupId.endsWith(">")) {
            // group referenced by name
            String groupName = groupId.substring(1, groupId.length() - 1);
            checkGroupName(groupName);
            if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
                groupNumber = namedCaptureGroups.get(groupName);
            } else {
                throw syntaxErrorAtRel("unknown group name " + groupName, groupName.length() + 1);
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
            // TODO: Handle conditional backreferences in open capture groups
            verifyGroupReference(groupNumber, groupId);
        }
        disjunction();
        if (match("|")) {
            disjunction();
            if (curChar() == '|') {
                throw syntaxErrorHere("conditional backref with more than two branches");
            }
        }
        if (!match(")")) {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
        }
        lastTerm = TermCategory.Atom;
    }

    private void absentExpression() {
        int start = position - 3;
        disjunction();
        if (match(")")) {
            bailOut("absent expressions not supported");
        } else {
            throw syntaxErrorAtAbs("missing ), unterminated subpattern", start);
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
        RubyFlags newFlags = getLocalFlags();
        boolean negative = false;
        while (ch != ')' && ch != ':') {
            if (ch == '-') {
                negative = true;
            } else if (RubyFlags.isValidFlagChar(ch)) {
                if (negative) {
                    if (RubyFlags.isTypeFlag(ch)) {
                        throw syntaxErrorAtRel("undefined group option", 1);
                    }
                    newFlags = newFlags.delFlag(ch);
                } else {
                    newFlags = newFlags.addFlag(ch);
                }
            } else if (Character.isAlphabetic(ch)) {
                throw syntaxErrorAtRel("undefined group option", 1);
            } else {
                throw syntaxErrorAtRel("missing -, : or )", 1);
            }

            if (atEnd()) {
               throw syntaxErrorHere("missing flag, -, : or )");
            }
            ch = consumeChar();
        }

        if (ch == ')') {
            openEndedLocalFlags(newFlags);
        } else {
            assert ch == ':';
            localFlags(newFlags, start);
        }
    }

    /**
     * Parses a block with local flags, assuming that the opening parenthesis, the flags and the ':'
     * have been parsed.
     *
     * @param newFlags - the new set of flags to be used in the block
     * @param start - the position in {@link #inPattern} where the block started, for error
     *            reporting purposes
     */
    private void localFlags(RubyFlags newFlags, int start) {
        flagsStack.push(newFlags);
        group(false, Optional.empty(), start);
        flagsStack.pop();
    }

    private void openEndedLocalFlags(RubyFlags newFlags) {
        setLocalFlags(newFlags);
        // Using "open-ended" flag modifiers, e.g. /a(?i)b|c/, makes Ruby wrap the continuation
        // of the flag modifier in parentheses, so that the above regex is equivalent to
        // /a(?i:b|c)/.
        emitSnippet("(?:");
        disjunction();
        emitSnippet(")");
    }
}
