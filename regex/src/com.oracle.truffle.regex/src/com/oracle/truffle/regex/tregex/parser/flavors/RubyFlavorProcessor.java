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

    // Ruby's predefined character classes.
    // This one is for classes like \w, \s or \d...
    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASSES;
    // ... and this one is the same as the above but restricted to the ASCII range (for use with (?a)).
    private static final Map<Character, CodePointSet> ASCII_CHAR_CLASSES;

    // This map contains the character sets of POSIX character classes like [[:alpha:]] and [[:punct:]].
    private static final Map<String, CodePointSet> UNICODE_POSIX_CHAR_CLASSES;
    // This is the same as above but restricted to ASCII.
    private static final Map<String, CodePointSet> ASCII_POSIX_CHAR_CLASSES;

    // Finally, these are the Unicode character properties (e.g. \p{Alpha}), which are just synonyms
    // for POSIX character classes. Other Unicode character properties (e.g. general categories, scripts...)
    // are implemented differently.
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

    // The behavior of the word-boundary assertions depends on the notion of a word character. Ruby's notion differs
    // from that of ECMAScript and so we cannot compile Ruby word-boundary assertions to ECMAScript word-boundary
    // assertions. Furthermore, the notion of a word character is dependent on whether the Ruby regular expression
    // is set to use the ASCII range only. These are helper constants that we use to implement word-boundary assertions.
    // WORD_BOUNDARY and WORD_NON_BOUNDARY are templates for word-bounary and word-non-boundary assertions,
    // respectively. These templates contain occurrences of \w and \W, which are substituted with the correct notion
    // of a word character during regexp transpilation time.
    public static final Pattern WORD_CHARS_PATTERN = Pattern.compile("\\\\[wW]");
    public static final String WORD_BOUNDARY = "(?:(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$))";
    public static final String WORD_NON_BOUNDARY = "(?:(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w))";

    /**
     * An enumeration of the possible grammatical categories of Ruby regex terms, for use with parsing quantifiers.
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
     * Characters considered as whitespace in Ruby's regex verbose mode.
     */
    private static final CompilationFinalBitSet WHITESPACE = CompilationFinalBitSet.valueOf(' ', '\t', '\n', '\r', '\f');

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
     * A stack of the locally enabled flags. Ruby enables establishing new flags and modifying flags within
     * the scope of certain expressions.
     */
    private final Deque<RubyFlags> flagsStack;
    /**
     * For syntax checking purposes, we need to know if we are inside a lookbehind assertion, where backreferences
     * are not allowed.
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
     * The number of capture groups encountered in the input pattern so far, i.e. the (zero-based) index of the next
     * capture group to be processed.
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
        this.groupIndex = 0;
        this.lastTerm = TermCategory.None;
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
        // lets us use . when translating Ruby's . in multiline mode. The unicode flag lets us use some of the
        // ECMAScript regex escape sequences which are restricted to Unicode regexes. It also lets
        // us reason with a more rigid grammar (as the ECMAScript non-Unicode regexes contain a lot
        // of ambiguous syntactic constructions for backwards compatibility).
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
        if (!match(next)) {
            throw syntaxError("expected " + next);
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    private void mustHaveMore() {
        if (atEnd()) {
            throw syntaxError("unexpected end of pattern");
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
     * Like {@link #emitChar(int)}, but does not do any case-folding.
     *
     * @param inCharClass are we emitting inside a character class?
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
     * Emits a matcher or a character class expression that can match a given character. Since
     * case-folding (IGNORECASE flag) can be enabled, a single character in the pattern could
     * correspond to a variety of different characters in the input.
     *
     * @param codepoint the character to be matched
     */
    private void emitChar(int codepoint) {
        if (!silent) {
            if (getLocalFlags().isIgnoreCase()) {
                emitCharSet(CodePointSet.create(codepoint, codepoint));
            } else {
                emitCharNoCasing(codepoint, false);
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
     * Emits a matcher (either a character class expression or a literal character) that would match the contents of
     * {@code curCharClass}. Case-folding is performed if the IGNORECASE flag is set.
     */
    private void emitCharSet() {
        if (!silent) {
            caseFold();
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

    /**
     * If the IGNORECASE flag is set, this method modifies {@code curCharClass} to contains its closure on case mapping.
     * Otherwise, it should do nothing. Currently, it bails out, because Ruby-style case folding is not implemented.
     */
    private void caseFold() {
        if (!getLocalFlags().isIgnoreCase()) {
            return;
        }
        bailOut("Ruby-style case folding is not supported");
    }

    /**
     * Invert the contents of the character set stored in {@code curCharClass}.
     */
    private void negateCharClass() {
        charClassTmp.clear();
        curCharClass.invert(charClassTmp, inSource.getEncoding());
        CodePointSetAccumulator swap = curCharClass;
        curCharClass = charClassTmp;
        charClassTmp = swap;
    }

    // Error reporting

    private RegexSyntaxException syntaxError(String message) {
        return new RegexSyntaxException(inSource, message);
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

    // First pass - identifying capture groups

    private void scanForCaptureGroups() {
        // character classes (where '(' stands for a literal '(') and any characters after the '('
        // which might turn into a non-capturing group or a look-around assertion.
        final int restorePosition = position;
        numberOfCaptureGroups = 0;
        int charClassDepth = 0;
        while (!atEnd()) {
            switch (consumeChar()) {
                case '\\':
                    // skip control escape sequences, \\cX, \\C-X or \\M-X, which can be nested
                    while (curChar() == 'c' || curChar() == 'C' || curChar() == 'M') {
                        if (curChar() == 'c') {
                            advance(1);
                        } else {
                            advance(2);
                        }
                    }
                    // skip escaped char; if it includes a group name, skip that too
                    int c = consumeChar();
                    switch (c) {
                        case 'k':
                        case 'g':
                            // skip contents of group name (which might contain syntax chars)
                            if (match("<")) {
                                parseGroupReference('>', true, true, c == 'k', true);
                            }
                            break;
                    }
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
                    if (charClassDepth == 0) {
                        if (match("?")) {
                            if (match("<") && curChar() != '=' && curChar() != '!') {
                                String groupName = parseGroupName('>');
                                if (namedCaptureGroups == null) {
                                    namedCaptureGroups = new HashMap<>();
                                    numberOfCaptureGroups = 0;
                                }
                                if (namedCaptureGroups.containsKey(groupName)) {
                                    bailOut("different capture groups with the same name are not supported");
                                }
                                numberOfCaptureGroups++;
                                namedCaptureGroups.put(groupName, numberOfCaptureGroups);
                            } else {
                                match("(");
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
            throw syntaxError("unbalanced parenthesis");
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
        // Every alternative introduces its local copy of options, which can be modified inline, but the modifications
        // have scope only throughout the alternative, i.e. up to the next vertical bar (|) or right parenthesis.
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
        if (assertionEscape()) {
            lastTerm = TermCategory.Assertion;
        } else if (categoryEscape(false)) {
            lastTerm = TermCategory.Atom;
        } else if (backreference()) {
            lastTerm = TermCategory.Atom;
        } else if (namedBackreference()) {
            lastTerm = TermCategory.Atom;
        } else if (lineBreak()) {
            lastTerm = TermCategory.Atom;
        } else if (extendedGraphemeCluster()) {
            lastTerm = TermCategory.Atom;
        } else if (keepCommand()) {
            lastTerm = TermCategory.Assertion;
        } else if (subexpressionCall()) {
            lastTerm = TermCategory.Atom;
        } else if (stringEscape()) {
            lastTerm = TermCategory.Atom;
        } else {
            // characterEscape has to come after assertionEscape because of the ambiguity of \b, which
            // (outside of character classes) is resolved in the favor of the assertion.
            // characterEscape also has to come after backreference because of the ambiguity between
            // backreferences and octal character escapes which must be resolved in favor of
            // backreferences
            characterEscape();
            lastTerm = TermCategory.Atom;
        }
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
                    charSet = UNICODE_CHAR_CLASSES.get(className);
                }
                if (inCharClass) {
                    curCharClass.addSet(charSet);
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
                    boolean negative = (capitalP || caret) && (!capitalP && !caret);
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
                        throw syntaxError(String.format("invalid character property name {%s}", propertySpec));
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
                throw syntaxError("numbered backref/call is not allowed. (use name)");
            }
            if (groupNumber > numberOfCaptureGroups()) {
                throw syntaxError("invalid group reference " + number);
            }
            if (lookbehindDepth > 0) {
                throw syntaxError("invalid pattern in look-behind");
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
        if (curChar() == '-' || isDecDigit(curChar())) {
            if (!allowNumeric) {
                throw syntaxError("invalid group name");
            }
            int sign = match("-") ? -1 : 1;
            groupName =  getMany(RubyFlavorProcessor::isDecDigit);
            try {
                groupNumber = sign * Integer.parseInt(groupName);
                if (groupNumber < 0) {
                    groupNumber = numberOfCaptureGroups() + 1 + groupNumber;
                }
            } catch (NumberFormatException e) {
                throw syntaxError("invalid group name");
            }
            if (containsNamedCaptureGroups()) {
                throw syntaxError("numbered backref/call is not allowed. (use name)");
            }
            if (!ignoreUnresolved && (groupNumber <= 0 || groupNumber > numberOfCaptureGroups())) {
                throw syntaxError("invalid group reference " + groupName);
            }
        } else {
            if (!allowNamed) {
                throw syntaxError("invalid group name");
            }
            groupName = getMany(c -> {
                if (allowLevels) {
                    return c != terminator && c != '+' && c != '-';
                } else {
                    return c != terminator;
                }
            });
            if (groupName.isEmpty()) {
                throw syntaxError("missing group name");
            }
            if (namedCaptureGroups == null || !namedCaptureGroups.containsKey(groupName)) {
                if (ignoreUnresolved) {
                    groupNumber = -1;
                } else {
                    throw syntaxError("unknown group name " + groupName);
                }
            } else {
                groupNumber = namedCaptureGroups.get(groupName);
            }
        }
        if (allowLevels && (curChar() == '+' || curChar() == '-')) {
            advance(); // consume sign
            String level = getMany(RubyFlavorProcessor::isDecDigit);
            if (level.isEmpty()) {
                throw syntaxError("invalid group name");
            }
            bailOut("backreferences to other levels are not supported");
        }
        if (!match(Character.toString(terminator))) {
            throw syntaxError("invalid group name");
        }
        if (lookbehindDepth > 0) {
            throw syntaxError("invalid pattern in look-behind");
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
     * Parses a line-break matcher. We do not support this because it entails support for atomic expressions,
     * i.e. cuts in the backtracking.
     * @return true if parsed correctly
     */
    private boolean lineBreak() {
        if (curChar() == 'R') {
            advance();
            bailOut("line break escape not supported");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parses an extended grapheme cluster. We do not support this because it entails support for atomic expressions,
     * i.e. cuts in the backtracking.
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
     * Parses a keep command. This instructs the regex engine to trim the current match to the current position.
     * ECMAScript regular expressions don't have support for anything of this sort.
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
     * Parses a subexpression call. We do not support this as ECMAScript has no similar notion and operates with finite
     * memory (no stack).
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
     * Parses a string escape, which is an escape sequenes that matches a series of characters. In Ruby, this can be
     * seen when using the \\u{... ... ...} syntax to escape a sequence of characters using their Unicode codepoint
     * values.
     * @return true if parsed correctly
     */
    private boolean stringEscape() {
        if (match("u{")) {
            getMany(c ->  ASCII_POSIX_CHAR_CLASSES.get("space").contains(c));
            while (!match("}")) {
                String code = getMany(RubyFlavorProcessor::isHexDigit);
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxError("unicode escape value " + code + " outside of range 0-0x10FFFF");
                    }
                    emitChar(codePoint);
                } catch (NumberFormatException e) {
                    throw syntaxError("bad escape \\u" + code);
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
     * <li>\a, \b, \e, \f, \n, \r, \t or \v</li>
     * <li>\\</li>
     * <li>an octal escape sequence</li>
     * <li>a hexadecimal escape sequence</li>
     * <li>a unicode escape sequence</li>
     * </ul>
     */
    private void characterEscape() {
        emitChar(silentCharacterEscape());
    }

    /**
     * Like {@link #characterEscape}, but instead of emitting a matcher or a character class
     * expression, it returns the escaped character. This is used when necessary when parsing character classes.
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
                    throw syntaxError("end pattern at control");
                }
                if (ch == 'C' && !match("-")) {
                    throw syntaxError("invalid control-code syntax");
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
            case 'M': {
                if (atEnd()) {
                    throw syntaxError("end pattern at meta");
                }
                if (!match("-")) {
                    throw syntaxError("invalid meta-code syntax");
                }
                if (atEnd()) {
                    throw syntaxError("end pattern at meta");
                }
                int c = consumeChar();
                if (c == '\\') {
                    c = silentCharacterEscape();
                }
                return (c & 0xff) | 0x80;
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
                        throw syntaxError("incomplete escape \\u" + code);
                    }
                }
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxError("unicode escape value \\u" + code + " outside of range 0-0x10FFFF");
                    }
                    return codePoint;
                } catch (NumberFormatException e) {
                    throw syntaxError("bad escape \\u" + code);
                }
            }
            default:
                if (isOctDigit(ch)) {
                    retreat();
                    String code = getUpTo(3, RubyFlavorProcessor::isOctDigit);
                    int codePoint = Integer.parseInt(code, 8);
                    if (codePoint > 0xFF) {
                        throw syntaxError("too big number");
                    }
                    return codePoint;
                } else {
                    return ch;
                }
        }
    }

    /**
     * Parses a character class. The syntax of Ruby character classes is quite different to the one in ECMAScript (set
     * intersections, nested char classes, POSIX brackets...). For that reason, we do not transpile the character class
     * expression piece-by-piece, but we parse it completely to build up a character set representation and then we
     * generate an ECMAScript character class expression that describes that character set.
     * Assumes that the opening {@code '['} was already parsed.
     */
    private void characterClass() {
        curCharClass.clear();
        collectCharClass();
        emitCharSet();
    }

    private void collectCharClass() {
        boolean negated = false;
        if (match("^")) {
            negated = true;
        }
        int firstPosInside = position;
        classBody: while (true) {
            if (atEnd()) {
                throw syntaxError("unterminated character set");
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
                    throw syntaxError("unterminated character set");
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
                    throw syntaxError("bad character range " + inPattern.substring(rangeStart, position));
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
        if (match(":")) {
            if (!collectPosixCharClass()) {
                collectCharClass();
            }
        } else {
            collectCharClass();
        }
        curCharClassBackup.addSet(curCharClass.get());
        releaseCodePointSetAccumulator(curCharClass);
        curCharClass = curCharClassBackup;
    }

    private boolean collectPosixCharClass() {
        int restorePosition = position;
        boolean negated = false;
        if (match("^")) {
            negated = true;
        }
        String className = getMany(c -> c != ':' && c != ']');
        if (match(":]")) {
            if (!UNICODE_POSIX_CHAR_CLASSES.containsKey(className)) {
                throw syntaxError("invalid POSIX bracket type");
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
            position = restorePosition;
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
            if (match("}") || match(",}")) {
                // We did not find a complete quantifier, so we should just emit a string of
                // matchers the individual characters.
                emitString(inPattern.substring(start, position));
                lastTerm = TermCategory.Atom;
                return;
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
                    throw syntaxError("min repeat greater than max repeat");
                }
                if (lowerBound.isPresent()) {
                    emitSnippet(inPattern.substring(start, position));
                } else {
                    // {,upperBound} is invalid in ECMAScript in unicode mode, but always valid in Ruby,
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
                throw syntaxError("nothing to repeat");
            case Quantifier:
                throw syntaxError("multiple repeat");
                // TODO: Nested quantifiers should be supported.
            case Atom:
                if (match("?")) {
                    emitSnippet("?");
                }
                else if (ch != '{' && match("+")) {
                    bailOut("possessive quantifiers not supported");
                }
                lastTerm = TermCategory.Quantifier;
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
            throw syntaxError("missing ), unterminated subpattern");
        }
        if (match("?")) {
            mustHaveMore();
            final int ch1 = consumeChar();
            switch (ch1) {
                case ':':
                    group(false);
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
                    bailOut("atomic groups are not supported");
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
                    throw syntaxError("unknown extension ?" + new String(Character.toChars(ch1)));
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
            throw syntaxError("missing " + terminator + ", unterminated name");
        }
        if (groupName.isEmpty()) {
            throw syntaxError("missing group name");
        }
        return groupName;
    }

    /**
     * Parses a parenthesized comment, assuming that the '(#' prefix was already parsed.
     */
    private void parenComment() {
        getMany(c -> c != ')');
        if (!match(")")) {
            throw syntaxError("missing ), unterminated comment");
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
            throw syntaxError("missing ), unterminated subpattern");
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
        if (positive) {
            emitSnippet("(?=");
        } else {
            emitSnippet("(?!");
        }
        disjunction();
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxError("missing ), unterminated subpattern");
        }
        lastTerm = TermCategory.Assertion;
    }

    /**
     * Just like {@link #lookahead}, but for lookbehind assertions.
     */
    private void lookbehind(boolean positive) {
        if (positive) {
            emitSnippet("(?<=");
        } else {
            emitSnippet("(?<!");
        }
        lookbehindDepth++;
        disjunction();
        lookbehindDepth--;
        if (match(")")) {
            emitSnippet(")");
        } else {
            throw syntaxError("missing ), unterminated subpattern");
        }
        lastTerm = TermCategory.Assertion;
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
            throw syntaxError("invalid group name");
        }
        disjunction();
        if (match("|")) {
            disjunction();
            if (curChar() == '|') {
                throw syntaxError("conditional backref with more than two branches");
            }
        }
        if (!match(")")) {
            throw syntaxError("missing ), unterminated subpattern");
        }
        lastTerm = TermCategory.Atom;
    }

    /**
     * Parses an absent expression. This kind of expression has no counterpart in ECMAScript and so we bail out.
     */
    private void absentExpression() {
        disjunction();
        if (match(")")) {
            bailOut("absent expressions not supported");
        } else {
            throw syntaxError("missing ), unterminated subpattern");
        }
        lastTerm = TermCategory.Atom;
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
                        throw syntaxError("undefined group option");
                    }
                    newFlags = newFlags.delFlag(ch);
                } else {
                    newFlags = newFlags.addFlag(ch);
                }
            } else if (Character.isAlphabetic(ch)) {
                throw syntaxError("undefined group option");
            } else {
                throw syntaxError("missing -, : or )");
            }

            if (atEnd()) {
               throw syntaxError("missing flag, -, : or )");
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
        // Using "open-ended" flag modifiers, e.g. /a(?i)b|c/, makes Ruby wrap the continuation
        // of the flag modifier in parentheses, so that the above regex is equivalent to
        // /a(?i:b|c)/.
        emitSnippet("(?:");
        disjunction();
        emitSnippet(")");
    }
}
