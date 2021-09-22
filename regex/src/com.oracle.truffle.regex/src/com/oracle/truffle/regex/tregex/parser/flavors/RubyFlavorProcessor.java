/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.RbErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

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
    static final TBitSet SYNTAX_CHARACTERS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}');
    /**
     * Characters that are considered special in ECMAScript regex character classes.
     */
    static final TBitSet CHAR_CLASS_SYNTAX_CHARACTERS = TBitSet.valueOf('-', '\\', ']', '^');

    // Ruby's predefined character classes.
    // This one is for classes like \w, \s or \d...
    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASSES;
    // ... and this one is the same as the above but restricted to the ASCII range (for use with
    // (?a)).
    private static final Map<Character, CodePointSet> ASCII_CHAR_CLASSES;

    // This map contains the character sets of POSIX character classes like [[:alpha:]] and
    // [[:punct:]].
    private static final Map<String, CodePointSet> UNICODE_POSIX_CHAR_CLASSES;
    // This is the same as above but restricted to ASCII.
    private static final Map<String, CodePointSet> ASCII_POSIX_CHAR_CLASSES;

    static {
        CodePointSet asciiRange = CodePointSet.create(0x00, 0x7F);
        CodePointSet nonAsciiRange = CodePointSet.create(0x80, Character.MAX_CODE_POINT);

        UNICODE_CHAR_CLASSES = new HashMap<>(8);
        ASCII_CHAR_CLASSES = new HashMap<>(8);

        CodePointSet alpha = UnicodeProperties.getProperty("Alphabetic");
        CodePointSet digit = UnicodeProperties.getProperty("General_Category=Decimal_Number");
        CodePointSet space = UnicodeProperties.getProperty("White_Space");
        CodePointSet xdigit = CodePointSet.create('0', '9', 'A', 'F', 'a', 'f');
        CodePointSet word = alpha.union(UnicodeProperties.getProperty("General_Category=Mark")).union(digit).union(UnicodeProperties.getProperty("General_Category=Connector_Punctuation")).union(
                        UnicodeProperties.getProperty("Join_Control"));
        UNICODE_CHAR_CLASSES.put('d', digit);
        UNICODE_CHAR_CLASSES.put('h', xdigit);
        UNICODE_CHAR_CLASSES.put('s', space);
        UNICODE_CHAR_CLASSES.put('w', word);

        for (char ctypeChar : new Character[]{'d', 'h', 's', 'w'}) {
            CodePointSet charSet = UNICODE_CHAR_CLASSES.get(ctypeChar);
            char complementCTypeChar = Character.toUpperCase(ctypeChar);
            CodePointSet complementCharSet = charSet.createInverse(Encodings.UTF_32);
            UNICODE_CHAR_CLASSES.put(complementCTypeChar, complementCharSet);
            ASCII_CHAR_CLASSES.put(ctypeChar, asciiRange.createIntersectionSingleRange(charSet));
            ASCII_CHAR_CLASSES.put(complementCTypeChar, complementCharSet.union(nonAsciiRange));
        }

        UNICODE_POSIX_CHAR_CLASSES = new HashMap<>(14);
        ASCII_POSIX_CHAR_CLASSES = new HashMap<>(14);
        CompilationBuffer buffer = new CompilationBuffer(Encodings.UTF_32);

        CodePointSet blank = UnicodeProperties.getProperty("General_Category=Space_Separator").union(CodePointSet.create('\t', '\t'));
        CodePointSet cntrl = UnicodeProperties.getProperty("General_Category=Control");
        CodePointSet graph = space.union(UnicodeProperties.getProperty("General_Category=Control")).union(UnicodeProperties.getProperty("General_Category=Surrogate")).union(
                        UnicodeProperties.getProperty("General_Category=Unassigned")).createInverse(Encodings.UTF_32);
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

        for (Map.Entry<String, CodePointSet> entry : UNICODE_POSIX_CHAR_CLASSES.entrySet()) {
            ASCII_POSIX_CHAR_CLASSES.put(entry.getKey(), asciiRange.createIntersectionSingleRange(entry.getValue()));
        }
    }

    // The behavior of the word-boundary assertions depends on the notion of a word character.
    // Ruby's notion differs from that of ECMAScript and so we cannot compile Ruby word-boundary
    // assertions to ECMAScript word-boundary assertions. Furthermore, the notion of a word
    // character is dependent on whether the Ruby regular expression is set to use the ASCII range
    // only. These are helper constants that we use to implement word-boundary assertions.
    // WORD_BOUNDARY and WORD_NON_BOUNDARY are templates for word-boundary and word-non-boundary
    // assertions, respectively. These templates contain occurrences of \w and \W, which are
    // substituted with the correct notion of a word character during regexp transpilation time.
    public static final Pattern WORD_CHARS_PATTERN = Pattern.compile("\\\\[wW]");
    public static final String WORD_BOUNDARY = "(?:(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$))";
    public static final String WORD_NON_BOUNDARY = "(?:(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w))";

    /**
     * An enumeration of the possible grammatical categories of Ruby regex terms, for use with
     * parsing quantifiers.
     */
    private enum TermCategory {
        /**
         * A lookahead or lookbehind assertion.
         */
        LookAroundAssertion,
        /**
         * An assertion other than lookahead or lookbehind, e.g. beginning-of-string/line,
         * end-of-string/line or (non)-word-boundary assertion.
         */
        OtherAssertion,
        /**
         * A literal character, a character class or a group.
         */
        Atom,
        /**
         * A term followed by any kind of quantifier.
         */
        Quantifier,
        /**
         * Used as the grammatical category when the term in question does not exist.
         */
        None
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

    private static final class Quantifier {
        public static final int INFINITY = -1;

        public int lower;
        public int upper;
        public boolean greedy;

        Quantifier(int lower, int upper, boolean greedy) {
            this.lower = lower;
            this.upper = upper;
            this.greedy = greedy;
        }

        @Override
        public String toString() {
            StringBuilder output = new StringBuilder();
            if (lower == 0 && upper == INFINITY) {
                output.append("*");
            } else if (lower == 1 && upper == INFINITY) {
                output.append("+");
            } else if (lower == 0 && upper == 1) {
                output.append("?");
            } else {
                output.append("{");
                output.append(lower);
                output.append(",");
                if (upper != INFINITY) {
                    output.append(upper);
                }
                output.append("}");
            }
            if (!greedy) {
                output.append("?");
            }
            return output.toString();
        }
    }

    /**
     * What can be the result of trying to parse a POSIX character class, e.g. [[:alpha:]].
     */
    private enum PosixClassParseResult {
        /**
         * We successfully parsed a (nested) POSIX character class.
         */
        WasNestedPosixClass,
        /**
         * We haven't found a POSIX character class, but we should check for a regular nested
         * character class, e.g. [a[b]].
         */
        TryNestedClass,
        /**
         * We haven't found a POSIX character class. Furthermore, we should *not* treat this as a
         * nested character class, but interpret the opening bracket as a literal character.
         */
        NotNestedClass
    }

    /**
     * Characters considered as whitespace in Ruby's regex verbose mode.
     */
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' ');

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
     * A stack of the locally enabled flags. Ruby enables establishing new flags and modifying flags
     * within the scope of certain expressions.
     */
    private final Deque<RubyFlags> flagsStack;
    /**
     * For syntax checking purposes, we need to know if we are inside a lookbehind assertion, where
     * backreferences are not allowed.
     */
    private int lookbehindDepth;
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
     * A set of capture groups names which occur repeatedly in the expression. Backreferences to
     * such capture groups can refer to either of the homonymous capture groups, depending on which
     * of them matched most recently. Such backreferences are not supported in TRegex.
     */
    private Set<String> ambiguousCaptureGroups;

    /**
     * The number of capture groups encountered in the input pattern so far, i.e. the (zero-based)
     * index of the next capture group to be processed.
     */
    private int groupIndex;
    /**
     * The total number of capture groups present in the expression.
     */
    private int numberOfCaptureGroups;

    /**
     * The grammatical category of the last term parsed. This is needed to detect improper usage of
     * quantifiers.
     */
    private TermCategory lastTerm;

    /**
     * The position within the output stream {@link #outPattern} at which starts the translation of
     * the last translated term. The range [ lastTermOutPosition, position ), gives the part of the
     * output buffer that's occupied by the last term.
     */
    private int lastTermOutPosition;

    /**
     * The contents of the character class that is currently being parsed.
     */
    private CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    /**
     * The characters which are allowed to be full case-foldable (i.e. they are allowed to cross the
     * ASCII boundary) in this character class. This set is constructed as the set of all characters
     * that are included in the character class by being mentioned either:
     * <ul>
     * <li>literally, as in [a]</li>
     * <li>as part of a range, e.g. [a-c]</li>
     * <li>through a POSIX character property other than [[:word:]] and [[:ascii:]]</li>
     * <li>through a Unicode property other than \p{Ascii}</li>
     * <li>through a character type other than \w or \W</li>
     * </ul>
     * This includes character mentioned inside negations, intersections and other nested character
     * classes.
     */
    private CodePointSetAccumulator fullyFoldableCharacters = new CodePointSetAccumulator();
    /**
     * A temporary buffer for case-folding character classes.
     */
    private CodePointSetAccumulator charClassTmp = new CodePointSetAccumulator();
    /**
     * When parsing nested character classes, we need several instances of
     * {@link CodePointSetAccumulator}s. In order to avoid having to repeatedly allocate new ones,
     * we return unused instances to this shared pool, to be reused later.
     */
    private final List<CodePointSetAccumulator> charClassPool = new ArrayList<>();

    /**
     * A reusable buffer for storing the codepoint contents of literal strings. We need to scan the
     * entire string before case folding so that we correctly handle cases when several codepoints
     * in sequence can case-unfold to a single codepoint.
     */
    private final IntArrayBuffer codepointsBuffer = new IntArrayBuffer();

    @TruffleBoundary
    public RubyFlavorProcessor(RegexSource source) {
        this.inSource = source;
        this.inPattern = source.getPattern();
        this.inFlags = source.getFlags();
        this.position = 0;
        this.outPattern = new StringBuilder(inPattern.length());
        this.globalFlags = new RubyFlags(inFlags);
        this.flagsStack = new LinkedList<>();
        this.lookbehindDepth = 0;
        this.groupStack = new ArrayDeque<>();
        this.namedCaptureGroups = null;
        this.ambiguousCaptureGroups = null;
        this.groupIndex = 0;
        this.lastTerm = TermCategory.None;
        this.lastTermOutPosition = -1;
    }

    @Override
    public int getNumberOfCaptureGroups() {
        // include capture group 0
        return numberOfCaptureGroups + 1;
    }

    @Override
    public Map<String, Integer> getNamedCaptureGroups() {
        return namedCaptureGroups;
    }

    @Override
    public AbstractRegexObject getFlags() {
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
        // lets us use . when translating Ruby's . in multiline mode. The unicode flag lets us use
        // some of the ECMAScript regex escape sequences which are restricted to Unicode regexes.
        // It also lets us reason with a more rigid grammar (as the ECMAScript non-Unicode regexes
        // contain a lot of ambiguous syntactic constructions for backwards compatibility).
        return new RegexSource(outPattern.toString(), globalFlags.isSticky() ? "suy" : "su", inSource.getOptions(), inSource.getSource());
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
        return inPattern.codePointAt(position);
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
        if (atEnd()) {
            throw syntaxErrorAtEnd(RbErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
        advance(1);
    }

    private void retreat() {
        advance(-1);
    }

    private void advance(int len) {
        position = inPattern.offsetByCodePoints(position, len);
    }

    private boolean match(String next) {
        if (inPattern.regionMatches(position, next, 0, next.length())) {
            position += next.length();
            return true;
        } else {
            return false;
        }
    }

    private void mustMatch(String next) {
        assert "}".equals(next) || ")".equals(next);
        if (!match(next)) {
            throw syntaxErrorHere("}".equals(next) ? RbErrorMessages.EXPECTED_BRACE : RbErrorMessages.EXPECTED_PAREN);
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
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
     */
    private void emitSnippet(CharSequence snippet) {
        if (!silent) {
            outPattern.append(snippet);
        }
    }

    /**
     * Like {@link #emitChar(int)}, but does not do any case-folding.
     *
     * @param inCharClass are we emitting inside a character class?
     */
    private void emitCharNoCasing(int codepoint, boolean inCharClass) {
        if (!silent) {
            TBitSet syntaxChars = inCharClass ? CHAR_CLASS_SYNTAX_CHARACTERS : SYNTAX_CHARACTERS;
            if (syntaxChars.get(codepoint)) {
                emitSnippet("\\");
            }
            outPattern.appendCodePoint(codepoint);
        }
    }

    /**
     * Emits a matcher or a character class expression that can match a given character. Since
     * case-folding (IGNORECASE flag) can be enabled, a single character in the pattern could
     * correspond to a variety of different characters in the input.
     *
     * @param codepoint the character to be matched
     */
    private void emitChar(int codepoint) {
        if (!silent) {
            if (getLocalFlags().isIgnoreCase()) {
                emitSnippet(RubyCaseFolding.caseFoldUnfoldString(new int[]{codepoint}, inSource.getEncoding().getFullSet()));
            } else {
                emitCharNoCasing(codepoint, false);
            }
        }
    }

    /**
     * Emits a matcher (either a character class expression or a literal character) that would match
     * the contents of {@code curCharClass}.
     */
    private void emitCharSet() {
        if (!silent) {
            if (curCharClass.matchesSingleChar()) {
                emitCharNoCasing(curCharClass.get().getLo(0), false);
            } else {
                emitSnippet("[");
                for (Range r : curCharClass) {
                    if (r.isSingle()) {
                        emitCharNoCasing(r.lo, true);
                    } else {
                        emitCharNoCasing(r.lo, true);
                        emitSnippet("-");
                        emitCharNoCasing(r.hi, true);
                    }
                }
                emitSnippet("]");
            }
        }
    }

    /**
     * Emits a matcher that matches character in a given set.
     */
    private void emitCharSet(CodePointSet charSet) {
        if (!silent) {
            curCharClass.clear();
            curCharClass.addSet(charSet);
            emitCharSet();
        }
    }

    // Error reporting

    private RegexSyntaxException syntaxErrorAtEnd(String message) {
        return RegexSyntaxException.createPattern(inSource, message, inPattern.length() - 1);
    }

    private RegexSyntaxException syntaxErrorHere(String message) {
        return RegexSyntaxException.createPattern(inSource, message, position);
    }

    private RegexSyntaxException syntaxErrorAt(String message, int pos) {
        return RegexSyntaxException.createPattern(inSource, message, pos);
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

    static boolean isAscii(int c) {
        return c < 128;
    }

    // First pass - identifying capture groups

    private void scanForCaptureGroups() {
        // We scan the source of the regex and search for '(' characters, which mark capture
        // groups. However, we have to watch out for exceptions, e.g. character classes (where
        // '(' stands for a literal '(') or syntax characters following a '(' which might turn
        // it into a non-capturing group, a look-around assertion or some other construction.
        final int restorePosition = position;
        numberOfCaptureGroups = 0;
        int charClassDepth = 0;
        while (!atEnd()) {
            switch (consumeChar()) {
                case '\\':
                    switch (curChar()) {
                        case 'k':
                        case 'g':
                            // skip contents of group name (which might contain syntax chars)
                            int c = consumeChar();
                            if (match("<")) {
                                parseGroupReference('>', true, true, c == 'k', true);
                            }
                            break;
                        default:
                            while (match("c") || match("C-") || match("M-")) {
                                // skip control escape sequences, \\cX, \\C-X or \\M-X, which can be
                                // nested
                            }
                            // skip escaped char
                            advance();
                    }
                    break;
                case '[':
                    charClassDepth++;
                    // Closing brackets are treated as literals if they are the first char
                    // in a char class.
                    if (!match("]")) {
                        match("^]");
                    }
                    break;
                case ']':
                    charClassDepth--;
                    break;
                case '(':
                    if (charClassDepth == 0) {
                        if (match("?")) {
                            if (match("<")) {
                                if (curChar() == '=' || curChar() == '!') {
                                    // look-behind
                                    break;
                                }
                                String groupName = parseGroupName('>');
                                if (namedCaptureGroups == null) {
                                    namedCaptureGroups = new HashMap<>();
                                    ambiguousCaptureGroups = new HashSet<>();
                                    numberOfCaptureGroups = 0;
                                }
                                if (namedCaptureGroups.containsKey(groupName)) {
                                    ambiguousCaptureGroups.add(groupName);
                                }
                                numberOfCaptureGroups++;
                                namedCaptureGroups.put(groupName, numberOfCaptureGroups);
                            } else if (match("(")) {
                                if (match("<")) {
                                    parseGroupReference('>', true, true, true, true);
                                } else if (match("'")) {
                                    parseGroupReference('\'', true, true, true, true);
                                } else if (isDecDigit(curChar())) {
                                    parseGroupReference(')', true, false, true, true);
                                }
                            }
                        } else {
                            if (namedCaptureGroups == null) {
                                numberOfCaptureGroups++;
                            }
                        }
                    }
                    break;
                case '#':
                    if (charClassDepth == 0) {
                        if (globalFlags.isExtended()) {
                            int endOfLine = inPattern.indexOf('\n', position);
                            if (endOfLine >= 0) {
                                position = endOfLine + 1;
                            } else {
                                position = inPattern.length();
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        position = restorePosition;
    }

    private int numberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    private boolean containsNamedCaptureGroups() {
        return namedCaptureGroups != null;
    }

    // The parser

    private void parse() {
        scanForCaptureGroups();

        flagsStack.push(globalFlags);
        disjunction();
        flagsStack.pop();

        if (!atEnd()) {
            assert curChar() == ')';
            throw syntaxErrorHere(RbErrorMessages.UNBALANCED_PARENTHESIS);
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
                lastTerm = TermCategory.None;
                lastTermOutPosition = -1;
            } else {
                break;
            }
        }
    }

    /**
     * An alternative is a sequence of Terms.
     */
    private void alternative() {
        // Every alternative introduces its local copy of options, which can be modified inline, but
        // the modifications have scope only throughout the alternative, i.e. up to the next
        // vertical bar (|) or right parenthesis.
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
        int outPosition = outPattern.length();
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
                lastTermOutPosition = outPosition;
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
                lastTermOutPosition = outPosition;
                break;
            case '(':
                parens();
                break;
            case '^':
                emitSnippet("(?:^|(?<=[\\n])(?=.))");
                lastTerm = TermCategory.OtherAssertion;
                lastTermOutPosition = outPosition;
                break;
            case '$':
                emitSnippet("(?:$|(?=[\\n]))");
                lastTerm = TermCategory.OtherAssertion;
                lastTermOutPosition = outPosition;
                break;
            default:
                string(ch);
                // NB: string sets lastTerm and lastTermOutPosition itself
        }
    }

    private void string(int firstCodepoint) {
        codepointsBuffer.clear();
        codepointsBuffer.add(firstCodepoint);
        string();
    }

    private void string(String prefix) {
        codepointsBuffer.clear();
        prefix.codePoints().forEach(codepointsBuffer::add);
        string();
    }

    /**
     * Parses a string of literal characters. Should be called via {@link #string(int)} or
     * {@link #string(String)}, which both clear the {@link #codepointsBuffer}.
     */
    private void string() {
        int outPosition = outPattern.length();

        stringLoop: while (!atEnd() && curChar() != '|' && curChar() != ')') {
            int ch = consumeChar();

            if (getLocalFlags().isExtended()) {
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
                    if (isProperEscapeNext()) {
                        retreat();
                        break stringLoop;
                    }
                    codepointsBuffer.add(fetchEscapedChar());
                    break;
                case '[':
                case '*':
                case '+':
                case '?':
                case '{':
                case '.':
                case '(':
                case '^':
                case '$':
                    retreat();
                    break stringLoop;
                default:
                    codepointsBuffer.add(ch);
            }
        }

        boolean isQuantifierNext = isQuantifierNext();
        int last = codepointsBuffer.get(codepointsBuffer.length() - 1);

        if (!silent) {
            if (isQuantifierNext) {
                codepointsBuffer.setLength(codepointsBuffer.length() - 1);
            }

            if (getLocalFlags().isIgnoreCase()) {
                String caseFoldSnippet = RubyCaseFolding.caseFoldUnfoldString(codepointsBuffer.toArray(), inSource.getEncoding().getFullSet());
                emitSnippet(caseFoldSnippet);
            } else {
                for (int i = 0; i < codepointsBuffer.length(); i++) {
                    emitCharNoCasing(codepointsBuffer.get(i), false);
                }
            }

            if (isQuantifierNext) {
                outPosition = outPattern.length();
                emitChar(last);
            }
        }

        lastTerm = TermCategory.Atom;
        lastTermOutPosition = outPosition;
    }

    /**
     * Indicates whether the following is a proper escape sequence, which cannot be a part of a
     * string. Those are all escape sequences with the difference of those handled by
     * {@link #fetchEscapedChar()}.
     */
    public boolean isProperEscapeNext() {
        boolean oldSilent = silent;
        int oldPosition = position;
        try {
            silent = true;
            return assertionEscape() || categoryEscape(false) || backreference() || namedBackreference() || lineBreak() || extendedGraphemeCluster() || keepCommand() || subexpressionCall() ||
                            stringEscape() || characterEscape().isPresent();
        } finally {
            silent = oldSilent;
            position = oldPosition;
        }
    }

    /**
     * Indicates whether a quantifier is coming up next.
     */
    public boolean isQuantifierNext() {
        if (atEnd()) {
            return false;
        }
        switch (curChar()) {
            case '*':
            case '+':
            case '?':
                return true;
            case '{':
                int oldPosition = position;
                try {
                    advance();
                    if (match("}") || match(",}")) {
                        return false;
                    } else {
                        // lower bound
                        getMany(RubyFlavorProcessor::isDecDigit);
                        // upper bound
                        if (match(",")) {
                            getMany(RubyFlavorProcessor::isDecDigit);
                        }
                        if (!match("}")) {
                            return false;
                        }
                        return true;
                    }
                } finally {
                    position = oldPosition;
                }
            default:
                return false;
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
     * <li>assertion escapes</li>
     * <li>character class escapes</li>
     * <li>backreferences</li>
     * <li>named backreferences</li>
     * <li>line breaks</li>
     * <li>extended grapheme clusters</li>
     * <li>keep commands</li>
     * <li>subexpression calls</li>
     * <li>string escapes</li>
     * <li>character escapes</li>
     * </ul>
     */
    private void escape() {
        int outPosition = outPattern.length();
        if (assertionEscape()) {
            lastTerm = TermCategory.OtherAssertion;
            lastTermOutPosition = outPosition;
        } else if (categoryEscape(false)) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (backreference()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (namedBackreference()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (lineBreak()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (extendedGraphemeCluster()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (keepCommand()) {
            lastTerm = TermCategory.OtherAssertion;
            lastTermOutPosition = outPosition;
        } else if (subexpressionCall()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else if (stringEscape()) {
            lastTerm = TermCategory.Atom;
            lastTermOutPosition = outPosition;
        } else {
            // characterEscape has to come after assertionEscape because of the ambiguity of \b,
            // which (outside of character classes) is resolved in the favor of the assertion.
            // characterEscape also has to come after backreference because of the ambiguity between
            // backreferences and octal character escapes which must be resolved in favor of
            // backreferences
            Optional<Integer> characterEscape = characterEscape();
            if (characterEscape.isPresent()) {
                emitChar(characterEscape.get());
                lastTerm = TermCategory.Atom;
                lastTermOutPosition = outPosition;
            } else {
                string(fetchEscapedChar());
                // NB: string sets lastTerm and lastTermOutPosition itself
            }
        }
    }

    private CodePointSet getUnicodeCharClass(char className) {
        if (inSource.getEncoding() == Encodings.ASCII) {
            return ASCII_CHAR_CLASSES.get(className);
        }

        return trimToEncoding(UNICODE_CHAR_CLASSES.get(className));
    }

    private CodePointSet getUnicodePosixCharClass(String className) {
        if (inSource.getEncoding() == Encodings.ASCII) {
            return ASCII_POSIX_CHAR_CLASSES.get(className);
        }

        return trimToEncoding(UNICODE_POSIX_CHAR_CLASSES.get(className));
    }

    private CodePointSet trimToEncoding(CodePointSet codePointSet) {
        return inSource.getEncoding().getFullSet().createIntersectionSingleRange(codePointSet);
    }

    /**
     * Tries to parse an assertion escape. An assertion escape can be one of the following:
     * <ul>
     * <li>\A (beginning of input)</li>
     * <li>\Z (end of input, before trailing newline, if any)</li>
     * <li>\z (end of input)</li>
     * <li>\G (end of previous match)</li>
     * <li>\b (word boundary)</li>
     * <li>\B (word non-boundary)</li>
     * </ul>
     *
     * @return {@code true} iff an assertion escape was found
     */
    private boolean assertionEscape() {
        int restorePosition = position;
        switch (consumeChar()) {
            case 'A':
                emitSnippet("(?:^)");
                return true;
            case 'Z':
                emitSnippet("(?:$|(?=[\\r\\n]$))");
                return true;
            case 'z':
                emitSnippet("(?:$)");
                return true;
            case 'G':
                bailOut("\\G escape sequence is not supported");
                return true;
            case 'b':
                if (getLocalFlags().isAscii()) {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, ASCII_CHAR_CLASSES.get('w'), ASCII_CHAR_CLASSES.get('W'));
                } else {
                    emitWordBoundaryAssertion(WORD_BOUNDARY, getUnicodeCharClass('w'), getUnicodeCharClass('W'));
                }
                return true;
            case 'B':
                if (getLocalFlags().isAscii()) {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, ASCII_CHAR_CLASSES.get('w'), ASCII_CHAR_CLASSES.get('W'));
                } else {
                    emitWordBoundaryAssertion(WORD_NON_BOUNDARY, getUnicodeCharClass('w'), getUnicodeCharClass('W'));
                }
                return true;
            default:
                position = restorePosition;
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
     * <li>\d (hexadecimal digits)</li>
     * <li>\D (not hexadecimal digits)</li>
     * <li>\s (spaces)</li>
     * <li>\S (non-spaces)</li>
     * <li>\w (word characters)</li>
     * <li>\W (non-word characters)</li>
     * <li>\p{...} (Unicode properties)</li>
     * </ul>
     *
     * @param inCharClass whether or not this escape was found in (and is being emitted as part of)
     *            a character class
     * @return {@code true} iff a category escape was found
     */
    private boolean categoryEscape(boolean inCharClass) {
        int restorePosition = position;
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
                    charSet = getUnicodeCharClass('w');
                }
                if (inCharClass) {
                    curCharClass.addSet(charSet);
                    if (getLocalFlags().isIgnoreCase() && className != 'w' && className != 'W') {
                        fullyFoldableCharacters.addSet(charSet);
                    }
                } else {
                    emitCharSet(charSet);
                }
                return true;
            case 'p':
            case 'P':
                boolean capitalP = curChar() == 'P';
                advance();
                if (match("{")) {
                    String propertySpec = getMany(c -> c != '}');
                    if (atEnd()) {
                        position = restorePosition;
                        return false;
                    } else {
                        advance();
                    }
                    boolean caret = propertySpec.startsWith("^");
                    boolean negative = (capitalP || caret) && (!capitalP || !caret);
                    if (caret) {
                        propertySpec = propertySpec.substring(1);
                    }
                    CodePointSet property;
                    if (UNICODE_POSIX_CHAR_CLASSES.containsKey(propertySpec.toLowerCase())) {
                        property = getUnicodePosixCharClass(propertySpec.toLowerCase());
                    } else if (UnicodeProperties.isSupportedGeneralCategory(propertySpec, true)) {
                        property = trimToEncoding(UnicodeProperties.getProperty("General_Category=" + propertySpec, true));
                    } else if (UnicodeProperties.isSupportedScript(propertySpec, true)) {
                        property = trimToEncoding(UnicodeProperties.getProperty("Script=" + propertySpec, true));
                    } else if (UnicodeProperties.isSupportedProperty(propertySpec, true)) {
                        property = trimToEncoding(UnicodeProperties.getProperty(propertySpec, true));
                    } else {
                        bailOut("unsupported Unicode property " + propertySpec);
                        // So that the property variable is always written to.
                        property = CodePointSet.getEmpty();
                    }
                    if (negative) {
                        property = property.createInverse(Encodings.UTF_32);
                    }
                    if (inCharClass) {
                        curCharClass.addSet(property);
                        if (getLocalFlags().isIgnoreCase() && !propertySpec.equalsIgnoreCase("ascii")) {
                            fullyFoldableCharacters.addSet(property);
                        }
                    } else {
                        emitCharSet(property);
                    }
                    return true;
                } else {
                    position = restorePosition;
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
        int restorePosition = position;
        if (curChar() >= '1' && curChar() <= '9') {
            // Joni only considers backreferences numbered <= 1000.
            String number = getUpTo(4, RubyFlavorProcessor::isDecDigit);
            int groupNumber = Integer.parseInt(number);
            if (groupNumber > 1000) {
                position = restorePosition;
                return false;
            }
            if (containsNamedCaptureGroups()) {
                throw syntaxErrorAt(RbErrorMessages.NUMBERED_BACKREF_CALL_IS_NOT_ALLOWED, restorePosition);
            }
            if (groupNumber > numberOfCaptureGroups()) {
                throw syntaxErrorAt(RbErrorMessages.invalidGroupReference(number), restorePosition);
            }
            if (lookbehindDepth > 0) {
                throw syntaxErrorAt(RbErrorMessages.INVALID_PATTERN_IN_LOOK_BEHIND, restorePosition);
            }
            if (groupNumber > groupIndex && groupNumber >= 10) {
                // forward references >= 10 are interpreted as octal escapes instead
                position = restorePosition;
                return false;
            }
            emitBackreference(groupNumber);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Tries to parse a named backreference (e.g. {@code \k<foo>}).
     *
     * @return {@code true} if a named backreference was found
     */
    private boolean namedBackreference() {
        if (match("k<")) {
            int groupNumber = parseGroupReference('>', true, true, true, false);
            emitBackreference(groupNumber);
            return true;
        } else {
            return false;
        }
    }

    private int parseGroupReference(char terminator, boolean allowNumeric, boolean allowNamed, boolean allowLevels, boolean ignoreUnresolved) {
        String groupName;
        int groupNumber;
        int beginPos = position;
        if (curChar() == '-' || isDecDigit(curChar())) {
            if (!allowNumeric) {
                throw syntaxErrorHere(RbErrorMessages.INVALID_GROUP_NAME);
            }
            int sign = match("-") ? -1 : 1;
            groupName = getMany(RubyFlavorProcessor::isDecDigit);
            try {
                groupNumber = sign * Integer.parseInt(groupName);
                if (groupNumber < 0) {
                    groupNumber = numberOfCaptureGroups() + 1 + groupNumber;
                }
            } catch (NumberFormatException e) {
                throw syntaxErrorAt(RbErrorMessages.INVALID_GROUP_NAME, beginPos);
            }
            if (containsNamedCaptureGroups()) {
                throw syntaxErrorAt(RbErrorMessages.NUMBERED_BACKREF_CALL_IS_NOT_ALLOWED, beginPos);
            }
            if (!ignoreUnresolved && (groupNumber <= 0 || groupNumber > numberOfCaptureGroups())) {
                throw syntaxErrorAt(RbErrorMessages.invalidGroupReference(groupName), beginPos);
            }
        } else {
            if (!allowNamed) {
                throw syntaxErrorAt(RbErrorMessages.INVALID_GROUP_NAME, beginPos);
            }
            groupName = getMany(c -> {
                if (allowLevels) {
                    return c != terminator && c != '+' && c != '-';
                } else {
                    return c != terminator;
                }
            });
            if (groupName.isEmpty()) {
                throw syntaxErrorAt(RbErrorMessages.MISSING_GROUP_NAME, beginPos);
            }
            if (namedCaptureGroups == null || !namedCaptureGroups.containsKey(groupName)) {
                if (ignoreUnresolved) {
                    groupNumber = -1;
                } else {
                    throw syntaxErrorAt(RbErrorMessages.unknownGroupName(groupName), beginPos);
                }
            } else {
                if (ambiguousCaptureGroups.contains(groupName)) {
                    bailOut("backreferences to multiple homonymous named capture groups are not supported");
                }
                groupNumber = namedCaptureGroups.get(groupName);
            }
        }
        if (allowLevels && (curChar() == '+' || curChar() == '-')) {
            advance(); // consume sign
            String level = getMany(RubyFlavorProcessor::isDecDigit);
            if (level.isEmpty()) {
                throw syntaxErrorAt(RbErrorMessages.INVALID_GROUP_NAME, beginPos);
            }
            bailOut("backreferences to other levels are not supported");
        }
        if (!match(Character.toString(terminator))) {
            throw syntaxErrorAt(RbErrorMessages.INVALID_GROUP_NAME, beginPos);
        }
        if (lookbehindDepth > 0) {
            throw syntaxErrorAt(RbErrorMessages.INVALID_PATTERN_IN_LOOK_BEHIND, beginPos);
        }
        return groupNumber;
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
     * Parses a line-break matcher. We do not support this because it entails support for atomic
     * expressions, i.e. cuts in the backtracking.
     * 
     * @return true if parsed correctly
     */
    private boolean lineBreak() {
        if (curChar() == 'R') {
            advance();
            // When matching \\x0d, we check that it is not followed by \\x0a to emulate the
            // atomic group in the original Ruby expansion: (?>\x0d\x0a|[\x0a-\x0d\x85\u2028\u2029])
            if (inSource.getEncoding().isUnicode()) {
                emitSnippet("(?:\\x0d\\x0a|\\x0d(?!\\x0a)|[\\x0a-\\x0c\\x85\\u2028\\u2029])");
            } else {
                emitSnippet("(?:\\x0d\\x0a|\\x0d(?!\\x0a)|[\\x0a-\\x0c])");
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses an extended grapheme cluster. We do not support this because it entails support for
     * atomic expressions, i.e. cuts in the backtracking.
     * 
     * @return true if parsed correctly
     */
    private boolean extendedGraphemeCluster() {
        if (curChar() == 'X') {
            advance();
            bailOut("extended grapheme cluster escape not supported");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses a keep command. This instructs the regex engine to trim the current match to the
     * current position. ECMAScript regular expressions don't have support for anything of this
     * sort.
     * 
     * @return true if parsed correctly
     */
    private boolean keepCommand() {
        if (curChar() == 'K') {
            advance();
            bailOut("keep command not supported");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses a subexpression call. We do not support this as ECMAScript has no similar notion and
     * operates with finite memory (no stack).
     * 
     * @return true if parsed correctly
     */
    private boolean subexpressionCall() {
        if (match("g<")) {
            parseGroupReference('>', true, true, false, false);
            bailOut("subexpression calls not supported");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses a string escape, which is an escape sequence that matches a series of characters. In
     * Ruby, this can be seen when using the \\u{... ... ...} syntax to escape a sequence of
     * characters using their Unicode codepoint values.
     * 
     * @return true if parsed correctly
     */
    private boolean stringEscape() {
        int beginPos = position - 1;
        if (match("u{")) {
            getMany(c -> ASCII_POSIX_CHAR_CLASSES.get("space").contains(c));
            while (!match("}")) {
                String code = getMany(RubyFlavorProcessor::isHexDigit);
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxErrorAt(RbErrorMessages.invalidUnicodeEscape(code), beginPos);
                    }
                    emitChar(codePoint);
                } catch (NumberFormatException e) {
                    throw syntaxErrorAt(RbErrorMessages.badEscape(code), beginPos);
                }
                getMany(c -> WHITESPACE.get(c));
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses an escaped codepoint. This definition is distinct from {@link #characterEscape()},
     * because the escape sequences below do not break strings (i.e. they can be found inside
     * strings and should be case-unfolded along with their surroundings).
     *
     * This method assumes that the leading backslash was already consumed.
     *
     * This method handles the following escape sequences:
     * <ul>
     * <li>\a, \b, \e. \f, \n, \r, \t and \v</li>
     * <li>\cX, \C-X and \M-X control characters</li>
     * <li>syntax character escapes like \., \* or \\</li>
     * <li>any superfluous uses of backslash, e.g. \: or \"</li>
     * </ul>
     * 
     * @return the escaped codepoint
     */
    private int fetchEscapedChar() {
        int beginPos = position;
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
            case 'c':
            case 'C': {
                if (atEnd()) {
                    throw syntaxErrorAt(RbErrorMessages.END_PATTERN_AT_CONTROL, beginPos);
                }
                if (ch == 'C' && !match("-")) {
                    throw syntaxErrorAt(RbErrorMessages.INVALID_CONTROL_CODE_SYNTAX, beginPos);
                }
                int c = consumeChar();
                if (c == '?') {
                    return 0177;
                }
                if (c == '\\') {
                    c = fetchEscapedChar();
                }
                return c & 0x9f;
            }
            case 'M': {
                if (atEnd()) {
                    throw syntaxErrorAt(RbErrorMessages.END_PATTERN_AT_META, beginPos);
                }
                if (!match("-")) {
                    throw syntaxErrorAt(RbErrorMessages.INVALID_META_CODE_SYNTAX, beginPos);
                }
                if (atEnd()) {
                    throw syntaxErrorAt(RbErrorMessages.END_PATTERN_AT_META, beginPos);
                }
                int c = consumeChar();
                if (c == '\\') {
                    c = fetchEscapedChar();
                }
                return (c & 0xff) | 0x80;
            }
            default:
                return ch;
        }
    }

    /**
     * Parses a character escape sequence. A character escape sequence can be one of the following:
     * <ul>
     * <li>a hexadecimal escape sequence</li>
     * <li>a unicode escape sequence</li>
     * <li>an octal escape sequence</li>
     * </ul>
     */
    private Optional<Integer> characterEscape() {
        int beginPos = position;
        switch (curChar()) {
            case 'x': {
                advance();
                String code = getUpTo(2, RubyFlavorProcessor::isHexDigit);
                int byteValue = Integer.parseInt(code, 16);
                if (byteValue > 0x7F) {
                    // This is a non-ASCII byte escape. The escaped character might be part of a
                    // multibyte sequece. These sequences are encoding specific and supporting
                    // them would mean having to include decoders for all of Ruby's encodings.
                    // Fortunately, TruffleRuby decodes these for us and replaces them with
                    // verbatim characters or other forms of escape. Therefore, this can be
                    // trigerred by either:
                    // *) TruffleRuby's ClassicRegexp#preprocess was not called on the input
                    // *) TruffleRuby's ClassicRegexp#preprocess emitted a non-ASCII \\x escape
                    bailOut("unsupported multibyte escape");
                }
                return Optional.of(byteValue);
            }
            case 'u': {
                advance();
                String code;
                if (match("{")) {
                    code = getMany(RubyFlavorProcessor::isHexDigit);
                    mustMatch("}");
                } else {
                    code = getUpTo(4, RubyFlavorProcessor::isHexDigit);
                    if (code.length() < 4) {
                        throw syntaxErrorAt(RbErrorMessages.incompleteEscape(code), beginPos);
                    }
                }
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxErrorAt(RbErrorMessages.invalidUnicodeEscape(code), beginPos);
                    }
                    return Optional.of(codePoint);
                } catch (NumberFormatException e) {
                    throw syntaxErrorAt(RbErrorMessages.badEscape(code), beginPos);
                }
            }
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7': {
                String code = getUpTo(3, RubyFlavorProcessor::isOctDigit);
                int codePoint = Integer.parseInt(code, 8);
                if (codePoint > 0xFF) {
                    throw syntaxErrorAt(RbErrorMessages.TOO_BIG_NUMBER, beginPos);
                }
                return Optional.of(codePoint);
            }
            default:
                return Optional.empty();
        }
    }

    /**
     * Parses a character class. The syntax of Ruby character classes is quite different to the one
     * in ECMAScript (set intersections, nested char classes, POSIX brackets...). For that reason,
     * we do not transpile the character class expression piece-by-piece, but we parse it completely
     * to build up a character set representation and then we generate an ECMAScript character class
     * expression that describes that character set. Assumes that the opening {@code '['} was
     * already parsed.
     */
    private void characterClass() {
        curCharClassClear();
        collectCharClass();
        if (getLocalFlags().isIgnoreCase()) {
            List<String> multiCodePointExpansions = caseClosureMultiCodePoint();
            if (multiCodePointExpansions.size() > 0) {
                emitSnippet("(?:");
                emitCharSet();
                emitSnippet("|");
                emitSnippet(String.join("|", multiCodePointExpansions));
                emitSnippet(")");
            } else {
                emitCharSet();
            }
        } else {
            emitCharSet();
        }
    }

    private void collectCharClass() {
        boolean negated = false;
        int beginPos = position - 1;
        if (match("^")) {
            negated = true;
        }
        int firstPosInside = position;
        classBody: while (true) {
            if (atEnd()) {
                throw syntaxErrorAt(RbErrorMessages.UNTERMINATED_CHARACTER_SET, beginPos);
            }
            int rangeStart = position;
            Optional<Integer> lowerBound;
            boolean wasNestedCharClass = false;
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
                    if (nestedCharClass()) {
                        wasNestedCharClass = true;
                        lowerBound = Optional.empty();
                    } else {
                        lowerBound = Optional.of(ch);
                    }
                    break;
                case '&':
                    if (match("&")) {
                        charClassIntersection();
                        break classBody;
                    } else {
                        lowerBound = Optional.of(ch);
                    }
                    break;
                default:
                    lowerBound = Optional.of(ch);
            }
            // a hyphen following a nested char class is never interpreted as a range operator
            if (!wasNestedCharClass && match("-")) {
                if (atEnd()) {
                    throw syntaxErrorAt(RbErrorMessages.UNTERMINATED_CHARACTER_SET, beginPos);
                }
                Optional<Integer> upperBound;
                ch = consumeChar();
                switch (ch) {
                    case ']':
                        if (lowerBound.isPresent()) {
                            curCharClassAddCodePoint(lowerBound.get());
                        }
                        curCharClassAddCodePoint('-');
                        break classBody;
                    case '\\':
                        upperBound = classEscape();
                        break;
                    case '[':
                        if (nestedCharClass()) {
                            wasNestedCharClass = true;
                            upperBound = Optional.empty();
                        } else {
                            upperBound = Optional.of(ch);
                        }
                        break;
                    case '&':
                        if (match("&")) {
                            if (lowerBound.isPresent()) {
                                curCharClassAddCodePoint(lowerBound.get());
                            }
                            curCharClassAddCodePoint('-');
                            charClassIntersection();
                            break classBody;
                        } else {
                            upperBound = Optional.of(ch);
                        }
                        break;
                    default:
                        upperBound = Optional.of(ch);
                }
                // if the right operand of a range operator was a nested char class, Ruby drops
                // both the left operand and the range operator
                if (!wasNestedCharClass) {
                    if (!lowerBound.isPresent() || !upperBound.isPresent() || upperBound.get() < lowerBound.get()) {
                        throw syntaxErrorAt(RbErrorMessages.badCharacterRange(inPattern.substring(rangeStart, position)), rangeStart);
                    }
                    curCharClassAddRange(lowerBound.get(), upperBound.get());
                }
            } else if (lowerBound.isPresent()) {
                curCharClassAddCodePoint(lowerBound.get());
            }
        }
        if (getLocalFlags().isIgnoreCase()) {
            caseClosure();
        }
        if (negated) {
            curCharClass.invert(inSource.getEncoding());
        }
    }

    private void curCharClassClear() {
        curCharClass.clear();
        if (getLocalFlags().isIgnoreCase()) {
            fullyFoldableCharacters.clear();
        }
    }

    private void curCharClassAddCodePoint(int codepoint) {
        curCharClass.addCodePoint(codepoint);
        if (getLocalFlags().isIgnoreCase()) {
            fullyFoldableCharacters.addCodePoint(codepoint);
        }
    }

    private void curCharClassAddRange(int lower, int upper) {
        curCharClass.addRange(lower, upper);
        if (getLocalFlags().isIgnoreCase()) {
            fullyFoldableCharacters.addRange(lower, upper);
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

    private void charClassIntersection() {
        CodePointSetAccumulator curCharClassBackup = curCharClass;
        CodePointSetAccumulator foldableCharsBackup = fullyFoldableCharacters;
        curCharClass = acquireCodePointSetAccumulator();
        if (getLocalFlags().isIgnoreCase()) {
            fullyFoldableCharacters = acquireCodePointSetAccumulator();
        }
        collectCharClass();
        curCharClassBackup.intersectWith(curCharClass.get());
        curCharClass = curCharClassBackup;
        if (getLocalFlags().isIgnoreCase()) {
            foldableCharsBackup.addSet(fullyFoldableCharacters.get());
            fullyFoldableCharacters = foldableCharsBackup;
        }
    }

    /**
     * Parses a nested character class.
     *
     * @return true iff a nested character class was found, otherwise, the input should be treated
     *         as literal characters
     */
    private boolean nestedCharClass() {
        CodePointSetAccumulator curCharClassBackup = curCharClass;
        curCharClass = acquireCodePointSetAccumulator();
        PosixClassParseResult parseResult = collectPosixCharClass();
        if (parseResult == PosixClassParseResult.TryNestedClass) {
            collectCharClass();
        }
        curCharClassBackup.addSet(curCharClass.get());
        releaseCodePointSetAccumulator(curCharClass);
        curCharClass = curCharClassBackup;
        return parseResult != PosixClassParseResult.NotNestedClass;
    }

    private PosixClassParseResult collectPosixCharClass() {
        int restorePosition = position;
        if (!match(":")) {
            return PosixClassParseResult.TryNestedClass;
        }
        boolean negated = false;
        if (match("^")) {
            negated = true;
        }
        String className = getMany(c -> c != '\\' && c != ':' && c != ']');
        if (className.length() > 20) {
            position = restorePosition;
            return PosixClassParseResult.NotNestedClass;
        }
        if (match(":]")) {
            if (!UNICODE_POSIX_CHAR_CLASSES.containsKey(className)) {
                throw syntaxErrorAt(RbErrorMessages.INVALID_POSIX_BRACKET_TYPE, restorePosition);
            }
            CodePointSet charSet;
            if (getLocalFlags().isAscii()) {
                charSet = ASCII_POSIX_CHAR_CLASSES.get(className);
            } else {
                assert getLocalFlags().isDefault() || getLocalFlags().isUnicode();
                charSet = getUnicodePosixCharClass(className);
            }
            if (negated) {
                charSet = charSet.createInverse(inSource.getEncoding());
            }
            curCharClass.addSet(charSet);
            if (getLocalFlags().isIgnoreCase() && !getLocalFlags().isAscii() && !className.equals("word") && !className.equals("ascii")) {
                fullyFoldableCharacters.addSet(charSet);
            }
            return PosixClassParseResult.WasNestedPosixClass;
        } else {
            position = restorePosition;
            return PosixClassParseResult.TryNestedClass;
        }
    }

    /**
     * Calls the argument on any element of the character class which has a case-folding.
     */
    private void caseFoldCharClass(BiConsumer<Integer, int[]> caseFoldItem) {
        if (curCharClass.get().size() < RubyCaseFoldingData.CASE_FOLD.size()) {
            for (Range r : curCharClass) {
                RubyCaseFoldingData.CASE_FOLD.subMap(r.lo, r.hi + 1).forEach((Integer from, int[] to) -> {
                    caseFoldItem.accept(from, to);
                });
            }
        } else {
            RubyCaseFoldingData.CASE_FOLD.forEach((Integer from, int[] to) -> {
                if (curCharClass.get().contains(from)) {
                    caseFoldItem.accept(from, to);
                }
            });
        }
    }

    private boolean acceptableCaseFold(int from, int to) {
        // Characters which are not "fully case-foldable" are only treated as equivalent if the
        // relation doesn't cross the ASCII boundary.
        return fullyFoldableCharacters.get().contains(from) || isAscii(from) == isAscii(to);
    }

    /**
     * This method modifies {@code curCharClass} to contains its closure on case mapping.
     */
    private void caseClosure() {
        charClassTmp.clear();

        caseFoldCharClass((from, to) -> {
            if (to.length == 1) {
                // Add the case-folded version to the character class...
                if (acceptableCaseFold(from, to[0])) {
                    charClassTmp.addCodePoint(to[0]);
                }
            }
            // ... and also any characters which case-fold to the same.
            for (int unfolding : RubyCaseUnfoldingTrie.findSingleCharUnfoldings(to)) {
                if (unfolding != from && acceptableCaseFold(from, unfolding)) {
                    charClassTmp.addCodePoint(unfolding);
                }
            }
        });

        // We also handle all the characters which might have no case-folding, i.e. they case-fold
        // to themselves.
        for (Range r : curCharClass) {
            for (int codepoint = r.lo; codepoint <= r.hi; codepoint++) {
                for (int unfolding : RubyCaseUnfoldingTrie.findSingleCharUnfoldings(codepoint)) {
                    if (acceptableCaseFold(codepoint, unfolding)) {
                        charClassTmp.addCodePoint(unfolding);
                    }
                }
            }
        }

        // Only include characters that are admissible in the given encoding.
        charClassTmp.intersectWith(inSource.getEncoding().getFullSet());

        curCharClass.addSet(charClassTmp.get());
    }

    private List<String> caseClosureMultiCodePoint() {
        List<String> multiCodePointExpansions = new ArrayList<>();

        caseFoldCharClass((from, to) -> {
            if (to.length > 1) {
                assert !isAscii(from);
                multiCodePointExpansions.add(RubyCaseFolding.caseFoldUnfoldString(to, inSource.getEncoding().getFullSet(), !fullyFoldableCharacters.get().contains(from)));
            }
        });

        return multiCodePointExpansions;
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
        Optional<Integer> characterEscape = characterEscape();
        if (characterEscape.isPresent()) {
            return characterEscape;
        } else {
            return Optional.of(fetchEscapedChar());
        }
    }

    /**
     * Parses a quantifier whose first character is the argument {@code ch}.
     */
    private void quantifier(int ch) {
        Quantifier quantifier;
        int start = position - 1;
        if (ch == '{') {
            if (match("}") || match(",}")) {
                // We did not find a complete quantifier, so we should just emit a string of
                // matchers the individual characters.
                string(inPattern.substring(start, position));
                return;
            } else {
                Optional<BigInteger> lowerBound = Optional.empty();
                Optional<BigInteger> upperBound = Optional.empty();
                boolean canBeNonGreedy = true;
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
                    canBeNonGreedy = false;
                }
                if (!match("}")) {
                    // We did not find a complete quantifier, so we should just emit a string of
                    // matchers the individual characters.
                    string(inPattern.substring(start, position));
                    return;
                }
                if (lowerBound.isPresent() && upperBound.isPresent() && lowerBound.get().compareTo(upperBound.get()) > 0) {
                    throw syntaxErrorAt(RbErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT, start);
                }
                boolean greedy = true;
                if (canBeNonGreedy && match("?")) {
                    greedy = false;
                }
                quantifier = new Quantifier(lowerBound.orElse(BigInteger.ZERO).intValue(),
                                upperBound.orElse(BigInteger.valueOf(Quantifier.INFINITY)).intValue(),
                                greedy);
            }
        } else {
            int lower;
            int upper;
            switch (ch) {
                case '*':
                    lower = 0;
                    upper = Quantifier.INFINITY;
                    break;
                case '+':
                    lower = 1;
                    upper = Quantifier.INFINITY;
                    break;
                case '?':
                    lower = 0;
                    upper = 1;
                    break;
                default:
                    throw new IllegalStateException("should not reach here");
            }
            boolean greedy = true;
            if (match("?")) {
                greedy = false;
            } else if (match("+")) {
                bailOut("possessive quantifiers not supported");
            }
            quantifier = new Quantifier(lower, upper, greedy);
        }

        switch (lastTerm) {
            case None:
                throw syntaxErrorAt(RbErrorMessages.NOTHING_TO_REPEAT, start);
            case Quantifier:
                wrapLastTerm("(?:", ")" + quantifier.toString());
                break;
            case LookAroundAssertion:
                // A lookaround assertion might contain capture groups and thus have side effects.
                // ECMAScript regular expressions do not accept extraneous empty matches. Therefore,
                // an expression like /(?:(?=(a)))?/ would capture the 'a' in a capture group in
                // Ruby but it would not do so in ECMAScript. To avoid this, we bail out on
                // quantifiers on complex assertions (i.e. lookaround assertions), which might
                // contain capture groups.
                // NB: This could be made more specific. We could target only lookaround assertions
                // which contain capture groups and only when the quantifier is actually optional
                // (min = 0, such as ?, *, or {,x}).
                bailOut("quantifiers on lookaround assertions not supported");
                lastTerm = TermCategory.Quantifier;
                break;
            case OtherAssertion:
            case Atom:
                emitSnippet(quantifier.toString());
                lastTerm = TermCategory.Quantifier;
                // lastTermOutPosition stays the same: the term with its quantifier is considered as
                // the last term
                break;
        }
    }

    private void wrapLastTerm(String prefix, String suffix) {
        if (!silent) {
            outPattern.insert(lastTermOutPosition, prefix);
            emitSnippet(suffix);
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
        if (atEnd()) {
            throw syntaxErrorAtEnd(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        if (match("?")) {
            final int ch1 = consumeChar();
            switch (ch1) {
                case ':':
                    group(false);
                    break;

                case '#':
                    parenComment();
                    break;

                case '<': {
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
                            parseGroupName('>');
                            group(true);
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
                    if (!inSource.getOptions().isIgnoreAtomicGroups()) {
                        bailOut("atomic groups are not supported");
                    }
                    group(false);
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
                    throw syntaxErrorAt(RbErrorMessages.unknownExtension(ch1), position - 1);
            }
        } else {
            group(!containsNamedCaptureGroups());
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
            throw syntaxErrorHere(RbErrorMessages.unterminatedName(terminator));
        }
        if (groupName.isEmpty()) {
            throw syntaxErrorHere(RbErrorMessages.MISSING_GROUP_NAME);
        }
        return groupName;
    }

    /**
     * Parses a parenthesized comment, assuming that the '(#' prefix was already parsed.
     */
    private void parenComment() {
        int beginPos = position - 2;
        while (true) {
            if (atEnd()) {
                throw syntaxErrorAt(RbErrorMessages.UNTERMINATED_COMMENT, beginPos);
            }
            int ch = consumeChar();
            if (ch == '\\' && !atEnd()) {
                advance();
            } else if (ch == ')') {
                break;
            }
        }
    }

    /**
     * Parses a group, assuming that its opening parenthesis has already been parsed. Note that this
     * is used not only for ordinary capture groups, but also for named capture groups,
     * non-capturing groups or the contents of a local flags block.
     *
     * @param capturing whether or not we should emit a capturing group
     */
    private void group(boolean capturing) {
        int outPosition = outPattern.length();
        if (capturing) {
            groupIndex++;
            groupStack.push(new Group(groupIndex));
            emitSnippet("(");
        } else {
            emitSnippet("(?:");
        }
        disjunction();
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        if (capturing) {
            groupStack.pop();
        }
        lastTerm = TermCategory.Atom;
        lastTermOutPosition = outPosition;
    }

    /**
     * Parses a lookahead assertion, assuming that the opening parantheses and special characters
     * (either '(?=' or '(?!') have already been parsed.
     *
     * @param positive {@code true} if the assertion to be emitted is a positive lookahead assertion
     */
    private void lookahead(boolean positive) {
        int outPosition = outPattern.length();
        if (positive) {
            emitSnippet("(?:(?=");
        } else {
            emitSnippet("(?:(?!");
        }
        disjunction();
        if (match(")")) {
            emitSnippet("))");
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        lastTerm = TermCategory.LookAroundAssertion;
        lastTermOutPosition = outPosition;
    }

    /**
     * Just like {@link #lookahead}, but for lookbehind assertions.
     */
    private void lookbehind(boolean positive) {
        int outPosition = outPattern.length();
        if (positive) {
            emitSnippet("(?:(?<=");
        } else {
            emitSnippet("(?:(?<!");
        }
        lookbehindDepth++;
        disjunction();
        lookbehindDepth--;
        if (match(")")) {
            emitSnippet("))");
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        lastTerm = TermCategory.LookAroundAssertion;
        lastTermOutPosition = outPosition;
    }

    /**
     * Parses a conditional backreference, assuming that the prefix '(?(' was already parsed.
     */
    private void conditionalBackreference() {
        bailOut("conditional backreference groups not supported");
        if (match("<")) {
            parseGroupReference('>', true, true, true, false);
            mustMatch(")");
        } else if (match("'")) {
            parseGroupReference('\'', true, true, true, false);
            mustMatch(")");
        } else if (isDecDigit(curChar())) {
            parseGroupReference(')', true, false, true, false);
        } else {
            throw syntaxErrorHere(RbErrorMessages.INVALID_GROUP_NAME);
        }
        disjunction();
        if (match("|")) {
            disjunction();
            if (curChar() == '|') {
                throw syntaxErrorHere(RbErrorMessages.CONDITIONAL_BACKREF_WITH_MORE_THAN_TWO_BRANCHES);
            }
        }
        if (!match(")")) {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        lastTerm = TermCategory.Atom;
        lastTermOutPosition = -1; // bail out
    }

    /**
     * Parses an absent expression. This kind of expression has no counterpart in ECMAScript and so
     * we bail out.
     */
    private void absentExpression() {
        disjunction();
        if (match(")")) {
            bailOut("absent expressions not supported");
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        lastTerm = TermCategory.Atom;
        lastTermOutPosition = -1; // bail out
    }

    /**
     * Parses a local flag block or an inline declaration of a global flags. Assumes that the prefix
     * '(?' was already parsed, as well as the first flag which is passed as the argument.
     */
    private void flags(int ch0) {
        int ch = ch0;
        RubyFlags newFlags = getLocalFlags();
        boolean negative = false;
        while (ch != ')' && ch != ':') {
            if (ch == '-') {
                negative = true;
            } else if (RubyFlags.isValidFlagChar(ch)) {
                if (negative) {
                    if (RubyFlags.isTypeFlag(ch)) {
                        throw syntaxErrorHere(RbErrorMessages.UNDEFINED_GROUP_OPTION);
                    }
                    newFlags = newFlags.delFlag(ch);
                } else {
                    newFlags = newFlags.addFlag(ch);
                }
            } else if (Character.isAlphabetic(ch)) {
                throw syntaxErrorHere(RbErrorMessages.UNDEFINED_GROUP_OPTION);
            } else {
                throw syntaxErrorHere(RbErrorMessages.MISSING_DASH_COLON_PAREN);
            }

            if (atEnd()) {
                throw syntaxErrorAtEnd(RbErrorMessages.MISSING_FLAG_DASH_COLON_PAREN);
            }
            ch = consumeChar();
        }

        if (ch == ')') {
            openEndedLocalFlags(newFlags);
        } else {
            assert ch == ':';
            localFlags(newFlags);
        }
    }

    /**
     * Parses a block with local flags, assuming that the opening parenthesis, the flags and the ':'
     * have been parsed.
     *
     * @param newFlags - the new set of flags to be used in the block
     */
    private void localFlags(RubyFlags newFlags) {
        flagsStack.push(newFlags);
        group(false);
        flagsStack.pop();
    }

    private void openEndedLocalFlags(RubyFlags newFlags) {
        setLocalFlags(newFlags);
        lastTerm = TermCategory.None;
        lastTermOutPosition = -1;
        // Using "open-ended" flag modifiers, e.g. /a(?i)b|c/, makes Ruby wrap the continuation
        // of the flag modifier in parentheses, so that the above regex is equivalent to
        // /a(?i:b|c)/.
        emitSnippet("(?:");
        disjunction();
        emitSnippet(")");
    }
}
