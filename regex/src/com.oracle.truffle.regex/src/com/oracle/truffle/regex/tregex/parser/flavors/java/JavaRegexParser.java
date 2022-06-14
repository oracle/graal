/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors.java;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.ErrorMessages;
import com.oracle.truffle.regex.errors.RbErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements the parsing and translating of java.util.Pattern regular expressions to ECMAScript
 * regular expressions.
 */
public final class JavaRegexParser implements RegexParser {

    /**
     * Characters that are considered special in ECMAScript regexes. To match these characters, they
     * need to be escaped using a backslash.
     */
    private static final TBitSet SYNTAX_CHARACTERS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}');
    /**
     * Characters that are considered special in ECMAScript regex character classes.
     */
//    private static final TBitSet CHAR_CLASS_SYNTAX_CHARACTERS = TBitSet.valueOf('-', '\\', ']', '^');

    private static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'H', 'S', 'V', 'W', 'd', 'h', 's', 'v', 'w');

    // This map contains the character sets of POSIX character classes
    private static final Map<String, CodePointSet> UNICODE_POSIX_CHAR_CLASSES;
    // This is the same as above but restricted to ASCII.
    private static final Map<String, CodePointSet> ASCII_POSIX_CHAR_CLASSES;

    static {
        CodePointSet asciiRange = CodePointSet.create(0x00, 0x7F);

        UNICODE_POSIX_CHAR_CLASSES = new HashMap<>(5);
        ASCII_POSIX_CHAR_CLASSES = new HashMap<>(5);

        CompilationBuffer buffer = new CompilationBuffer(Encodings.UTF_32);

        CodePointSet alpha = UnicodeProperties.getProperty("Alphabetic");
        CodePointSet digit = UnicodeProperties.getProperty("General_Category=Decimal_Number");
        CodePointSet space = UnicodeProperties.getProperty("White_Space");
        CodePointSet xdigit = CodePointSet.create('0', '9', 'A', 'F', 'a', 'f');
        CodePointSet word = alpha.union(UnicodeProperties.getProperty("General_Category=Nonspacing_Mark")).union(UnicodeProperties.getProperty("General_Category=Enclosing_Mark")).
                union(UnicodeProperties.getProperty("General_Category=Spacing_Mark")).union(digit).union(UnicodeProperties.getProperty("General_Category=Connector_Punctuation"));
        CodePointSet blank = UnicodeProperties.getProperty("General_Category=Space_Separator").union(CodePointSet.create('\t', '\t'));
        CodePointSet cntrl = UnicodeProperties.getProperty("General_Category=Control");
        CodePointSet graph = space.union(UnicodeProperties.getProperty("General_Category=Control")).union(UnicodeProperties.getProperty("General_Category=Surrogate")).union(
                UnicodeProperties.getProperty("General_Category=Unassigned")).createInverse(Encodings.UTF_32);  //
//        CodePointSet print = graph.union(blank).createIntersection(cntrl.createInverse(Encodings.UTF_32), buffer);  //

//        UNICODE_POSIX_CHAR_CLASSES.put("alpha", alpha);
        UNICODE_POSIX_CHAR_CLASSES.put("alnum", alpha.union(digit));  //
//        UNICODE_POSIX_CHAR_CLASSES.put("blank", blank);
//        UNICODE_POSIX_CHAR_CLASSES.put("cntrl", cntrl);
//        UNICODE_POSIX_CHAR_CLASSES.put("digit", digit);
        UNICODE_POSIX_CHAR_CLASSES.put("graph", graph); //
//        UNICODE_POSIX_CHAR_CLASSES.put("lower", UnicodeProperties.getProperty("Lowercase"));
        UNICODE_POSIX_CHAR_CLASSES.put("print", graph.union(blank).subtract(cntrl, buffer));    //
//        UNICODE_POSIX_CHAR_CLASSES.put("punct", UnicodeProperties.getProperty("General_Category=Punctuation").union(UnicodeProperties.getProperty("General_Category=Symbol").subtract(alpha, buffer)));
//        UNICODE_POSIX_CHAR_CLASSES.put("space", space);
//        UNICODE_POSIX_CHAR_CLASSES.put("upper", UnicodeProperties.getProperty("Uppercase"));
        UNICODE_POSIX_CHAR_CLASSES.put("xdigit", xdigit);   //

        UNICODE_POSIX_CHAR_CLASSES.put("word", word);   //
//        UNICODE_POSIX_CHAR_CLASSES.put("ascii", UnicodeProperties.getProperty("ASCII"));

        for (Map.Entry<String, CodePointSet> entry : UNICODE_POSIX_CHAR_CLASSES.entrySet()) {
            ASCII_POSIX_CHAR_CLASSES.put(entry.getKey(), asciiRange.createIntersectionSingleRange(entry.getValue()));
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
     * Characters considered as whitespace in Java's regex verbose mode.
     */
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' '); // '\x0B' = '\u000b'

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
     * For grammar usage to detect proper usage of quantifiers.
     */
    private Token lastToken;

    /**
     * A {@link StringBuilder} hosting the resulting ECMAScript pattern.
     */
    private StringBuilder outPattern;

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
    private final CodePointSetAccumulator fullyFoldableCharacters = new CodePointSetAccumulator();
    /**
     * A temporary buffer for case-folding character classes.
     */
    private final CodePointSetAccumulator charClassTmp = new CodePointSetAccumulator();
    /**
     * When parsing nested character classes, we need several instances of
     * {@link CodePointSetAccumulator}s. In order to avoid having to repeatedly allocate new ones,
     * we return unused instances to this shared pool, to be reused later.
     */
    private final List<CodePointSetAccumulator> charClassPool = new ArrayList<>();

    /**
     * What can be the result of trying to parse a POSIX character class, e.g. [[:alpha:]].
     */
    private enum PosixClassParseResult {    // TODO remove and instead implement nested character classes
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
     * For syntax checking purposes, we need to know if we are inside a lookbehind assertion, where
     * backreferences are not allowed.
     */
    private int lookbehindDepth;

    /**
     * The global flags are the flags given when compiling the regular expression.
     */
    private final JavaFlags globalFlags;
    /**
     * A stack of the locally enabled flags. Ruby enables establishing new flags and modifying flags
     * within the scope of certain expressions.
     */
    private final Deque<JavaFlags> flagsStack;

    /**
     * For syntax checking purposes, we need to maintain some metadata about the current enclosing
     * capture groups.
     */
    private final Deque<JavaRegexParser.Group> groupStack;
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

    private RegexLanguage language;
    private CompilationBuffer compilationBuffer;
    private final RegexASTBuilder astBuilder;

    /**
     * A reusable buffer for storing the codepoint contents of literal strings. We need to scan the
     * entire string before case folding so that we correctly handle cases when several codepoints
     * in sequence can case-unfold to a single codepoint.
     */
    private final IntArrayBuffer codepointsBuffer = new IntArrayBuffer();

    private static RegexFlags makeTRegexFlags(boolean sticky) {
        // We need to set the Unicode flag to true so that character classes will treat the entire
        // Unicode code point range as the set of all characters, not just the UTF-16 code units.
        // We will also need to set the sticky flag to properly reflect both the sticky flag in the
        // incoming regex flags and the any \G assertions used in the expression.
        return RegexFlags.builder().unicode(true).sticky(sticky).build();
    }

    @CompilerDirectives.TruffleBoundary
    public JavaRegexParser(RegexSource inSource, RegexASTBuilder astBuilder) throws RegexSyntaxException {
        this.inSource = inSource;
        this.inPattern = inSource.getPattern();
        this.inFlags = inSource.getFlags();
        this.position = 0;

        this.groupStack = new ArrayDeque<>();
        this.namedCaptureGroups = null;
        this.groupIndex = 0;

        this.globalFlags = new JavaFlags(inFlags);
        this.flagsStack = new LinkedList<>();

        this.astBuilder = astBuilder;
        this.silent = astBuilder == null;
    }

    public static RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        return new JavaRegexParser(source, new RegexASTBuilder(language, source, makeTRegexFlags(false), compilationBuffer));
    }

    public void validate() throws RegexSyntaxException {
        silent = true;
        parseInternal();
    }

    public RegexAST parse() {
        astBuilder.pushRootGroup();
        parseInternal();
        RegexAST ast = astBuilder.popRootGroup();

        // ast.setFlags(...);

        System.out.println(ast.getRoot());

        return ast;
    }

    @Override
    public Map<String, Integer> getNamedCaptureGroups() {
        return Collections.emptyMap();
    }

    @Override
    public AbstractRegexObject getFlags() {
        return globalFlags;
    }

//    @Override
//    public boolean isUnicodePattern() {
//        return false;
//    }


    // The parser
    // equivalent to run() in Ruby

    private void parseInternal() {
        // ...

        flagsStack.push(globalFlags);

        disjunction();

        flagsStack.pop();

        // ...


        // check if we are at the end and if not --> SyntaxError
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
                nextSequence();
            } else {
                break;
            }
        }
    }

    final String wordBoundarySrc = "(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$)";
    final String nonWordBoundarySrc = "(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w)";
    final Function<String, String> includeExtraCases = s -> s.replace("\\w", "[\\w\\u017F\\u212A]").replace("\\W", "[^\\w\\u017F\\u212A]");


    /**
     * An alternative is a sequence of Terms.
     */
    private void alternative() {
        boolean openInlineFlag = true;
        while (!atEnd() && curChar() != '|'/* && curChar() != ')'*/) {
            Token token = term();
            if(token != null) {
                switch (token.kind) {
                    case caret: // java version of it
                        // (?:^|(?<=[\n])(?=.)) only, when multiline flag is set, otherwise just caret
                        if (getLocalFlags().isMultiline()) {
                            pushGroup(); // (?:
                            addCaret(); // ^
                            nextSequence(); // |
                            pushLookBehindAssertion(false); // (?<=
                            addCharClass(CodePointSet.create('\n')); // [\n]
                            popGroup(); // )
                            pushLookAheadAssertion(false); // (?=
                            addCharClass(inSource.getEncoding().getFullSet()); // .
                            popGroup(); // )
                            popGroup(); // )
                        } else {
                            addCaret();
                        }
                        break;
                    case dollar: // java version of it
                        // (?:$|(?=[\n])) only, when multiline flag is set, otherwise just dollar
                        if (getLocalFlags().isMultiline()) {
                            pushGroup(); // (?:
                            addDollar(); // $
                            nextSequence(); // |
                            pushLookAheadAssertion(false); // (?=
                            addCharClass(CodePointSet.create('\n')); // [\n]
                            popGroup(); // )
                            popGroup(); // )
                        } else {
                            /* From doc of Dollar extends Node in java.util.Pattern
                             * Node to anchor at the end of a line or the end of input based on the
                             * multiline mode.
                             *
                             * When not in multiline mode, the $ can only match at the very end
                             * of the input, unless the input ends in a line terminator in which
                             * it matches right before the last line terminator.
                             *
                             * Note that \r\n is considered an atomic line terminator.
                             *
                             * Like ^ the $ operator matches at a position, it does not match the
                             * line terminators themselves.
                             */
//                        (?:$|(?=(?:\r\n|\n)$))
                            pushGroup();    // (?:
                            addDollar();    // $
                            nextSequence(); // |
                            pushLookAheadAssertion(false);  // (?=
                            pushGroup();    // (?:
                            addCharClass(CodePointSet.create('\r'));
                            addCharClass(CodePointSet.create('\n'));
                            nextSequence();
                            addCharClass(CodePointSet.create('\n'));
                            popGroup();
                            addDollar();
                            popGroup();
                            popGroup();
                        }
                        break;
                    case wordBoundary:
                        if (getLocalFlags().isUnicode()) {
                            buildWordBoundaryAssertion(Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE);
                        } else {
                            buildWordBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                        }
////                    final String wordBoundarySrc = "(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$)";
////                    final String nonWordBoundarySrc = "(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w)";
////                    final Function<String, String> includeExtraCases = s -> s.replace("\\w", "[\\w\\u017F\\u212A]").replace("\\W", "[^\\w\\u017F\\u212A]");
//
//                    if (lastToken != null && lastToken.kind == Token.Kind.wordBoundary) {
//                        // ignore
//                        break;
//                    } else if (lastToken != null && lastToken.kind == Token.Kind.nonWordBoundary) {
//                        astBuilder.replaceCurTermWithDeadNode();
//                        break;
//                    }
//                    if (getLocalFlags().isUnicode() && getLocalFlags().isIgnoreCase()) {
//                        astBuilder.addCopy(token, JSRegexParser.parseRootLess(language, includeExtraCases.apply(wordBoundarySrc)));
//                    } else {
//                        astBuilder.addCopy(token, JSRegexParser.parseRootLess(language, wordBoundarySrc));
//                    }
                        break;
                    case nonWordBoundary:
                        if (getLocalFlags().isUnicode()) {
                            buildWordNonBoundaryAssertion(Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE);
                        } else {
                            buildWordNonBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                        }
//                    if (lastToken != null && lastToken.kind == Token.Kind.nonWordBoundary) {
//                        // ignore
//                        break;
//                    } else if (lastToken != null && lastToken.kind == Token.Kind.wordBoundary) {
//                        astBuilder.replaceCurTermWithDeadNode();
//                        break;
//                    }
//                    if (getLocalFlags().isUnicode() && getLocalFlags().isIgnoreCase()) {
//                        astBuilder.addCopy(token, JSRegexParser.parseRootLess(language, includeExtraCases.apply(nonWordBoundarySrc)));
//                    } else {
//                        astBuilder.addCopy(token, JSRegexParser.parseRootLess(language, nonWordBoundarySrc));
//                    }
                        break;
                    case backReference:
                        astBuilder.addBackReference((Token.BackReference) token);
                        break;
                    case quantifier:
                        if (astBuilder.getCurTerm() == null) {
                            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
                        }
                        if (getLocalFlags().isUnicode() && astBuilder.getCurTerm().isLookAheadAssertion()) {
                            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION);
                        }
                        if (astBuilder.getCurTerm().isLookBehindAssertion()) {
                            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION);
                        }
//                    astBuilder.addQuantifier((Token.Quantifier) token);
                        addQuantifier((Token.Quantifier) token);
                        break;
                    case anchor:
                        Token.Anchor anc = (Token.Anchor) token;
                        switch (anc.getAncCps().getHi(0)) {
                            case 'A':
                                addCaret();
                                break;
                            case 'Z':
                                // (?:$|(?=[\r\n]$))
                                pushGroup(); // (?:
                                addDollar(); // $
                                nextSequence(); // |
                                pushLookAheadAssertion(false); // (?=
                                addCharClass(CodePointSet.create('\n', '\n', '\r', '\r')); // [\r\n]
                                addDollar(); // $
                                popGroup(); // )
                                popGroup(); // )
                                break;
                            case 'z':
                                addDollar();
                                break;
                            case 'G':
                                bailOut("\\G anchor is only supported at the beginning of top-level alternatives");
                        }
                    case alternation:   // TODO already handled in function disjunction()
                        break;
                    case inlineFlag:
//                    Token.InlineFlagToken flagToken = (Token.InlineFlagToken) token;
//                    flags = new JavaFlags(((Token.InlineFlagToken) token).getFlags());
                        openInlineFlag = ((Token.InlineFlagToken) token).isOpen();
                        if (!openInlineFlag)
                            astBuilder.pushGroup();
                        System.out.println("term() : " + ((Token.InlineFlagToken) token).getFlags());
                        flagsStack.push(new JavaFlags(((Token.InlineFlagToken) token).getFlags()));

//                    setLocalFlags(new JavaFlags(((Token.InlineFlagToken) token).getFlags()));
                        break;  // TODO check if necessary
                    case captureGroupBegin:
                        astBuilder.pushCaptureGroup(token);
                        break;
                    case nonCaptureGroupBegin:
                        astBuilder.pushGroup(token);
                        break;
                    case lookAheadAssertionBegin:
                        astBuilder.pushLookAheadAssertion(token, ((Token.LookAheadAssertionBegin) token).isNegated());
                        break;
                    case lookBehindAssertionBegin:
                        astBuilder.pushLookBehindAssertion(token, ((Token.LookBehindAssertionBegin) token).isNegated());
                        break;
                    case groupEnd:
                        // TODO if bounded inline flag then remove the highest stack of flags
                        if (!openInlineFlag) {
                            flagsStack.pop();
                            openInlineFlag = true;
                        }
                        else if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                            throw syntaxErrorHere(ErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                        }
                        astBuilder.popGroup(token);
                        break;
                    case charClass:
                        astBuilder.addCharClass((Token.CharacterClass) token);
                        break;

                }
                lastToken = token;
            }
        }
    }

    // The behavior of the word-boundary assertions depends on the notion of a word character.
    // Ruby's notion differs from that of ECMAScript and so we cannot compile Ruby word-boundary
    // assertions to ECMAScript word-boundary assertions. Furthermore, the notion of a word
    // character is dependent on whether the Ruby regular expression is set to use the ASCII range
    // only.
    private void buildWordBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?=\w)|(?<=\w)(?:(?=\W)|$))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        popGroup(); // )
    }

    private void buildWordNonBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?:(?=\W)|$)|(?<=\w)(?=\w))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        popGroup(); // )
    }

    private JavaFlags getLocalFlags() {
        return flagsStack.peek();
    }

//    private void setLocalFlags(JavaFlags newLocalFlags) {
//        flagsStack.pop();
//        flagsStack.push(newLocalFlags);
//    }

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

    private void mustMatch(String next) {       // TODO can be inlined but rename for understanding
        assert "}".equals(next) || ")".equals(next);
        if (!match(next)) {
            throw syntaxErrorHere("}".equals(next) ? RbErrorMessages.EXPECTED_BRACE : RbErrorMessages.EXPECTED_PAREN);      // TODO create JavaErrorMessages, maybe copy them of Java original error messages
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
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

    /**
     * Adds a matcher for a given character. Since case-folding (IGNORECASE flag) can be enabled, a
     * single character in the pattern could correspond to a variety of different characters in the
     * input.
     *
     * @param codepoint the character to be matched
     */
    private void buildChar(int codepoint) {
        if (!silent) {
//            if (getLocalFlags().isIgnoreCase()) {
//                RubyCaseFolding.caseFoldUnfoldString(new int[]{codepoint}, inSource.getEncoding().getFullSet(), astBuilder);
//            } else {
            addChar(codepoint);
//            }
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
    private Token term() {
        int ch = consumeChar();

        System.out.println(ch + " : " + (char) ch);

        if (getLocalFlags().isExtended()) {
            if (WHITESPACE.get(ch)) {
                System.out.println("His");
//                ch = consumeChar();
                return null;
            }
            if (ch == '#') {
                System.out.println("Hi");
                comment();
                return null;
            }
        }

        System.out.println("asdfasdf: " + (char) ch + " - " + ch);

        switch (ch) {
            case '\\':
                return escape();
            case '[':
                return characterClass();
            case '*':
            case '+':
            case '?':
            case '{':
//                quantifier(ch);
//                return Token.createQuantifier(0, 0, false);   // dummy Quantifier as it is already handled in quantifier(ch);
                return parseQuantifier(ch);
            case '.':
//                if (getLocalFlags().isMultiline()) {
//                    addCharClass(inSource.getEncoding().getFullSet());
//                } else {
//                    addCharClass(CodePointSet.create('\n').createInverse(inSource.getEncoding()));
//                }
                if(getLocalFlags().isDotAll())
                    return Token.createCharClass(inSource.getEncoding().getFullSet());
                return Token.createCharClass(Constants.DOT);    // TODO check for DotAll - flag ?
            case '(':
                return parens();    // TODO group() does the closing bracket part already, no need for Token.createGroupEnd()
            case ')':
                return Token.createGroupEnd();
            case '^':
                return Token.createCaret();
            case '$':
                return Token.createDollar();
            default:
                if (getLocalFlags().isIgnoreCase()) {
                    curCharClass.clear();
                    curCharClass.appendRange(ch, ch);

                    boolean wasSingleChar = curCharClass.matchesSingleChar();
                    CaseFoldTable.CaseFoldingAlgorithm caseFolding = getLocalFlags().isUnicode() ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode;
                    CaseFoldTable.applyCaseFold(curCharClass, charClassTmp, caseFolding);

                    CodePointSet cps = curCharClass.toCodePointSet();
                    return Token.createCharClass(cps, wasSingleChar);
                } else {
                    return Token.createCharClass(CodePointSet.create(ch), true);
                }
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
//    private void characterClass() {
//        curCharClassClear();
//        collectCharClass();
//        buildCharClass();
//    }
    private Token characterClass() {
        curCharClassClear();
        collectCharClass();
//        buildCharClass();
        return Token.createCharClass(curCharClass.toCodePointSet(), curCharClass.matchesSingleChar());  // TODO maybe change to !invert && curCharClass.matchesSingleChar()
    }

    private void buildCharClass() {
        addCharClass(curCharClass.toCodePointSet());
    }

    private void collectCharClass() {
        boolean negated = false;
        int beginPos = position - 1;
        if (match("^")) {
            negated = true;
        }
        int firstPosInside = position;
        classBody:
        while (true) {
            if (atEnd()) {
                throw syntaxErrorAt(RbErrorMessages.UNTERMINATED_CHARACTER_SET, beginPos);
            }


            int rangeStart = position;
            Optional<Integer> lowerBound;
            boolean wasNestedCharClass = false;
            int ch = consumeChar();

            int restorePosition = position;
            if (ch == '\\') {
//                advance();
                int ch2 = consumeChar();
                if (ch2 == 'P' || ch2 == 'p') {
                    boolean capitalP = curChar() == 'P';
                    ch2 = consumeChar();
                    if (ch2 == '{') {
                        String propertySpec = getMany(c -> c != '}');
                        if (atEnd()) {
                            position = restorePosition;
                            break classBody;
                        } else {
                            advance();
                        }
                        boolean caret = propertySpec.startsWith("^");
                        boolean negative = (capitalP || caret) && (!capitalP || !caret);
                        if (caret) {
                            propertySpec = propertySpec.substring(1);
                        }
                        CodePointSet property;
                        propertySpec = parseUnicodeCharacterClass(propertySpec);
                        if (UNICODE_POSIX_CHAR_CLASSES.containsKey(propertySpec)) {
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
//                    if (inCharClass) {
//                        curCharClass.addSet(property);
//                        if (getLocalFlags().isIgnoreCase() && !propertySpec.equalsIgnoreCase("ascii")) {
//                            fullyFoldableCharacters.addSet(property);
//                        }
//                    } else {
//                        addCharClass(property);
//                    }
//                    return true;
                        curCharClass.addSet(property);
                    }
                }

                position = restorePosition;
            }


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
                    System.out.println("lower Bound: " + (char) ch);
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
                        lowerBound.ifPresent(this::curCharClassAddCodePoint);
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
                            lowerBound.ifPresent(this::curCharClassAddCodePoint);
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
                    if (lowerBound.isEmpty() || upperBound.isEmpty() || upperBound.get() < lowerBound.get()) {
                        throw syntaxErrorAt(RbErrorMessages.badCharacterRange(inPattern.substring(rangeStart, position)), rangeStart);
                    }
                    curCharClassAddRange(lowerBound.get(), upperBound.get());
                }
            } else lowerBound.ifPresent(this::curCharClassAddCodePoint);
        }
//        if (getLocalFlags().isIgnoreCase()) {
//            caseClosure();
//        }
        if (negated) {
            curCharClass.invert(inSource.getEncoding());
        }

    }

    private void curCharClassClear() {
        curCharClass.clear();
//        if (getLocalFlags().isIgnoreCase()) {
//            fullyFoldableCharacters.clear();
//        }
    }

    private void curCharClassAddCodePoint(int codepoint) {
        curCharClass.addCodePoint(codepoint);
//        if (getLocalFlags().isIgnoreCase()) {
//            fullyFoldableCharacters.addCodePoint(codepoint);
//        }
    }

    private void curCharClassAddCodePoint(int... codepoints) {
        for (int codepoint : codepoints) {
            curCharClassAddCodePoint(codepoint);
        }
    }

    private void curCharClassAddRange(int lower, int upper) {
        curCharClass.addRange(lower, upper);
//        if (getLocalFlags().isIgnoreCase()) {
//            fullyFoldableCharacters.addRange(lower, upper);
//        }
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
//        if (getLocalFlags().isIgnoreCase()) {
//            fullyFoldableCharacters = acquireCodePointSetAccumulator();
//        }
        collectCharClass();
        curCharClassBackup.intersectWith(curCharClass.get());
        curCharClass = curCharClassBackup;
//        if (getLocalFlags().isIgnoreCase()) {
//            foldableCharsBackup.addSet(fullyFoldableCharacters.get());
//            fullyFoldableCharacters = foldableCharsBackup;
//        }
    }

    /**
     * Parses a nested character class.
     *
     * @return true iff a nested character class was found, otherwise, the input should be treated
     * as literal characters
     */
    private boolean nestedCharClass() {
        CodePointSetAccumulator curCharClassBackup = curCharClass;
        curCharClass = acquireCodePointSetAccumulator();
//        PosixClassParseResult parseResult = collectPosixCharClass();
//        if (parseResult == PosixClassParseResult.TryNestedClass) {
        collectCharClass();
//        }
        curCharClassBackup.addSet(curCharClass.get());
        releaseCodePointSetAccumulator(curCharClass);
        curCharClass = curCharClassBackup;
//        return parseResult != PosixClassParseResult.NotNestedClass;
        return true;
    }

    private PosixClassParseResult collectPosixCharClass() {     // TODO Posix Character Classes do not exist like in Ruby [:alpha:]
        int restorePosition = position;
        if (!match(":")) {
            return PosixClassParseResult.TryNestedClass;
        }
        boolean negated = match("^");
        String className = getMany(c -> c != '\\' && c != ':' && c != ']');
        if (className.length() > 20) {
            position = restorePosition;
            return PosixClassParseResult.NotNestedClass;
        }
//        if (match(":]")) {
//            if (!UNICODE_POSIX_CHAR_CLASSES.containsKey(className)) {
//                throw syntaxErrorAt(RbErrorMessages.INVALID_POSIX_BRACKET_TYPE, restorePosition);
//            }
//            CodePointSet charSet;
//            if (getLocalFlags().isAscii()) {
//                charSet = ASCII_POSIX_CHAR_CLASSES.get(className);
//            } else {
//                assert getLocalFlags().isDefault() || getLocalFlags().isUnicode();
//                charSet = getUnicodePosixCharClass(className);
//            }
//            if (negated) {
//                charSet = charSet.createInverse(inSource.getEncoding());
//            }
//            curCharClass.addSet(charSet);
//            if (getLocalFlags().isIgnoreCase() && !getLocalFlags().isAscii() && !className.equals("word") && !className.equals("ascii")) {
//                fullyFoldableCharacters.addSet(charSet);
//            }
//            return PosixClassParseResult.WasNestedPosixClass;
//        } else {
        position = restorePosition;
        return PosixClassParseResult.TryNestedClass;
//        }
    }


    private boolean identifiedAllGroups = false;
    private int nGroups = 1;

    public int numberOfCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return nGroups;
    }

    private boolean findChars(char... chars) {
        if (atEnd()) {
            return false;
        }
        int i = ArrayUtils.indexOf(inPattern, position, inPattern.length(), chars);
        if (i < 0) {
            position = inPattern.length();
            return false;
        }
        position = i;
        return true;
    }

    private void registerCaptureGroup() {
        if (!identifiedAllGroups) {
            nGroups++;
        }
    }

    /**
     * Checks whether this regular expression contains any named capture groups.
     * <p>
     * This method is a way to check whether we are parsing the goal symbol Pattern[~U, +N] or
     * Pattern[~U, ~N] (see the ECMAScript RegExp grammar).
     */
    private boolean hasNamedCaptureGroups() throws RegexSyntaxException {
        return getNamedCaptureGroups() != null;
    }

    private void registerNamedCaptureGroup(String name) {
        if (!identifiedAllGroups) {
            if (namedCaptureGroups == null) {
                namedCaptureGroups = new HashMap<>();
            }
            if (namedCaptureGroups.containsKey(name)) {
                throw syntaxErrorHere(ErrorMessages.MULTIPLE_GROUPS_SAME_NAME);
            }
            namedCaptureGroups.put(name, nGroups);
        }
        registerCaptureGroup();
    }


    private void identifyCaptureGroups() throws RegexSyntaxException {
        // We are counting capture groups, so we only care about '(' characters and special
        // characters which can cancel the meaning of '(' - those include '\' for escapes, '[' for
        // character classes (where '(' stands for a literal '(') and any characters after the '('
        // which might turn into a non-capturing group or a look-around assertion.
        boolean insideCharClass = false;
        final int restoreIndex = position;
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
                        parens();
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        position = restoreIndex;
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
    private Token escape() {
        if (atEnd()) {
            throw syntaxErrorAtEnd(ErrorMessages.ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE);
        }
        int ch = consumeChar();
        int restorePosition = position;
        if ('1' <= ch && ch <= '9') {
            final int restoreIndex = position;
            final int backRefNumber = (ch - '0');
            if (backRefNumber < numberOfCaptureGroups()) {
                return Token.createBackReference(backRefNumber);
            } else if (getLocalFlags().isUnicode()) {
                throw syntaxErrorHere(ErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
            }
            position = restoreIndex;
        }
        switch (ch) {
            case 'A':
            case 'Z':
            case 'z':
            case 'G':
//                addCaret();
                return Token.createAnchor(CodePointSet.create(ch));
//            case 'Z':
////                // (?:$|(?=[\r\n]$))
////                pushGroup(); // (?:
////                addDollar(); // $
////                nextSequence(); // |
////                pushLookAheadAssertion(false); // (?=
////                addCharClass(NEWLINE_RETURN); // [\r\n]
////                addDollar(); // $
////                popGroup(); // )
////                popGroup(); // )
//                return Token.createAnchor("Z", CodePointSet.create('Z'));
//            case 'z':
////                addDollar();
//                return Token.createAnchor("z", CodePointSet.create('z'));
//            case 'G':
////                bailOut("\\G anchor is only supported at the beginning of top-level alternatives");
//                return Token.createAnchor("G", CodePointSet.create('G'));
            case 'k':
                if (getLocalFlags().isUnicode() || hasNamedCaptureGroups()) {
                    if (atEnd()) {
                        throw syntaxErrorHere(ErrorMessages.ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE);
                    }
                    if (consumeChar() != '<') {
                        throw syntaxErrorHere(ErrorMessages.MISSING_GROUP_NAME);
                    }
                    String groupName = parseGroupName('>');
                    // backward reference
                    if (namedCaptureGroups != null && namedCaptureGroups.containsKey(groupName)) {
                        return Token.createBackReference(namedCaptureGroups.get(groupName));
                    }
                    // possible forward reference
                    Map<String, Integer> allNamedCaptureGroups = getNamedCaptureGroups();
                    if (allNamedCaptureGroups != null && allNamedCaptureGroups.containsKey(groupName)) {
                        return Token.createBackReference(allNamedCaptureGroups.get(groupName));
                    }
                    throw syntaxErrorHere(ErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
                } else {
                    return charClass(ch);
                }
            case 'b':
                return Token.createWordBoundary();
            case 'B':
                return Token.createNonWordBoundary();
            case 'p':
            case 'P':   // TODO maybe outsource as the code is duplicated in another function
                retreat();
                boolean capitalP = curChar() == 'P';
                advance();
                if (match("{")) {
                    String propertySpec = getMany(c -> c != '}');
                    if (atEnd()) {
                        position = restorePosition;
                        return Token.createCharClass(CodePointSet.getEmpty());
                    } else {
                        advance();
                    }
                    boolean caret = propertySpec.startsWith("^");
                    boolean negative = (capitalP || caret) && (!capitalP || !caret);
                    if (caret) {
                        propertySpec = propertySpec.substring(1);
                    }
                    CodePointSet property;
                    propertySpec = parseUnicodeCharacterClass(propertySpec);
                    if (UNICODE_POSIX_CHAR_CLASSES.containsKey(propertySpec)) {
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
//                    if (inCharClass) {
//                        curCharClass.addSet(property);
//                        if (getLocalFlags().isIgnoreCase() && !propertySpec.equalsIgnoreCase("ascii")) {
//                            fullyFoldableCharacters.addSet(property);
//                        }
//                    } else {
//                        addCharClass(property);
//                    }
//                    return true;
                    return Token.createCharClass(property);
                } else {
                    position = restorePosition;
                    return Token.createCharClass(CodePointSet.getEmpty());
                }
            default:
                // Here we differentiate the case when parsing one of the six basic pre-defined
                // character classes (\w, \W, \d, \D, \s, \S) and Unicode character property
                // escapes. Both result in sets of characters, but in the former case, we can skip
                // the case-folding step in the `charClass` method and call `Token::createCharClass`
                // directly.
                if (isPredefCharClass(ch)) {
                    return Token.createCharClass(parsePredefCharClass(ch));
//                } else if (getLocalFlags().isUnicode() && (ch == 'p' || ch == 'P')) {
//                    return charClass(parseUnicodeCharacterProperty(ch == 'P'));
                } else {
                    return Token.createCharClass(CodePointSet.create(parseEscapeChar(ch, false)), true);
                }
        }
    }

    private static boolean isPredefCharClass(int c) {
        return PREDEFINED_CHAR_CLASSES.get(c);
    }

    private static String parseUnicodeCharacterClass(String property) {
        String propertyBegin = property.toLowerCase().substring(0, 2);
        if (propertyBegin.equals("is") || propertyBegin.equals("in")) {
            return property.substring(2);
        } else if (property.contains("=")) {
            return property.substring(property.indexOf('=') + 1);
        }
        return property;
    }

    // Note that the CodePointSet returned by this function has already been
    // case-folded and negated.
    // TODO combine the other Escape and PredefCharClass Methods!!!
    private CodePointSet parsePredefCharClass(int c) {
        int restorePosition = position;
        switch (c) {
            case 's':
                if (inSource.getOptions().isU180EWhitespace()) {
                    return Constants.LEGACY_WHITE_SPACE;
                } else {
                    return Constants.WHITE_SPACE;
                }
            case 'S':
                if (inSource.getOptions().isU180EWhitespace()) {
                    return Constants.LEGACY_NON_WHITE_SPACE;
                } else {
                    return Constants.NON_WHITE_SPACE;
                }
            case 'd':
                if (getLocalFlags().isUnicodeCharacterClass()) {
                    return UnicodeProperties.getProperty("Nd");
                } else {
                    return Constants.DIGITS;
                }
            case 'D':
                return Constants.NON_DIGITS;
            case 'w':
                if (getLocalFlags().isUnicodeCharacterClass()) {    // TODO done right? https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#UNICODE_CHARACTER_CLASS
                    // In JavaScript CaseFoldTable.applyCaseFold oder caseClosure(), nimmt alle equivalenten Groß- und Kleinbuchstaben dazu für flag 'u'
                    /*
                    private Token charClass(boolean invert) {
                        boolean wasSingleChar = !invert && curCharClass.matchesSingleChar();
                        if (flags.isIgnoreCase()) {
                            CaseFoldTable.CaseFoldingAlgorithm caseFolding = flags.isUnicode() ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode;
                            CaseFoldTable.applyCaseFold(curCharClass, charClassCaseFoldTmp, caseFolding);
                        }
                        CodePointSet cps = curCharClass.toCodePointSet();
                        return Token.createCharClass(invert ? cps.createInverse(encoding) : cps, wasSingleChar);
                    }

                    TODO wie in JavaScript, also ECMAScript eins zu eins übernehmen
                     */
                    return UNICODE_POSIX_CHAR_CLASSES.get("word");
                } else if (getLocalFlags().isUnicode() && getLocalFlags().isIgnoreCase()) {
                    return Constants.WORD_CHARS_UNICODE_IGNORE_CASE;
                } else {
                    return Constants.WORD_CHARS;
                }
            case 'W':
                if (getLocalFlags().isUnicodeCharacterClass()) {
                    return UNICODE_POSIX_CHAR_CLASSES.get("word").createInverse(Encodings.UTF_32);
                } else if (getLocalFlags().isUnicode() && getLocalFlags().isIgnoreCase()) {
                    return Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE;
                } else {
                    return Constants.NON_WORD_CHARS;
                }
            case 'v':
                return CodePointSet.createNoDedup(
                        0x000a, 0x000d,
                        0x0085, 0x0085,
                        0x2028, 0x2029
                );
            case 'V':
                return CodePointSet.createNoDedup(
                        0x0000, 0x0009,
                        0x000e, 0x0084,
                        0x0086, 0x2027,
                        0x202a, 0x10ffff
                );
            case 'h':
                return CodePointSet.createNoDedup(
                        0x0009, 0x0009,
                        0x0020, 0x0020,
                        0x00a0, 0x00a0,
                        0x1680, 0x1680,
                        0x2000, 0x200a,
                        0x202f, 0x202f,
                        0x205f, 0x205f,
                        0x3000, 0x3000
                );
            case 'H':
                return CodePointSet.createNoDedup(
                        0x0000, 0x0008,
                        0x000a, 0x001f,
                        0x0021, 0x009f,
                        0x00a1, 0x180d,
                        0x180f, 0x1fff,
                        0x202b, 0x202e,
                        0x2030, 0x205e,
                        0x2060, 0x2fff,
                        0x3001, 0x10ffff
                );
            default:
                return null;
//                throw CompilerDirectives.shouldNotReachHere();
        }
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

    // TODO: do something like parseUnicodeEscapeChar() or parseUnicodeCharacterProperty() here too maybe?

    private int parseEscapeChar(int c, boolean inCharClass) throws RegexSyntaxException {
        if (inCharClass && c == 'b') {
            return '\b';
        }
        switch (c) {
            case '0':
                if (getLocalFlags().isUnicode() && !atEnd() && isDecDigit(curChar())) {
                    throw syntaxErrorHere(ErrorMessages.INVALID_ESCAPE);
                }
                if (!getLocalFlags().isUnicode() && !atEnd() && isOctDigit(curChar())) {
                    return parseOctal(0);
                }
                return '\0';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'f':
                return '\f';
            case 'r':
                return '\r';
//            case 's':
////                CodePointSet cps = CodePointSet.create(0x0020, 0x0085, 0x00A0, 0x1680, 0x180E, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000);
////                cps.inv
//
//                curCharClassClear();
//                curCharClassAddRange(0x000A, 0x000D);
//                curCharClassAddRange(0x2000, 0x200A);
//                curCharClassAddCodePoint(0x0020, 0x0085, 0x00A0, 0x1680, 0x180E, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000);
//                addCharClass();
//            case 'c':
//                if (atEnd()) {
//                    retreat();
//                    return escapeCharSyntaxError('\\', ErrorMessages.INVALID_CONTROL_CHAR_ESCAPE);
//                }
//                final char controlLetter = curChar();
//                if (!getLocalFlags().isUnicode() && (isDecDigit(controlLetter) || controlLetter == '_') && inCharClass) {
//                    advance();
//                    return controlLetter % 32;
//                }
//                if (!('a' <= controlLetter && controlLetter <= 'z' || 'A' <= controlLetter && controlLetter <= 'Z')) {
//                    retreat();
//                    return escapeCharSyntaxError('\\', ErrorMessages.INVALID_CONTROL_CHAR_ESCAPE);
//                }
//                advance();
//                return Character.toUpperCase(controlLetter) - ('A' - 1);
//            case 'u': // TODO do this
//                final int unicodeEscape = parseUnicodeEscapeChar();
//                return unicodeEscape < 0 ? c : unicodeEscape;
//            case 'x': // TODO do this
//                final int value = parseHex(2, 2, 0xff, ErrorMessages.INVALID_ESCAPE);
//                return value < 0 ? c : value;
//            case '-':
//                if (!inCharClass) {
//                    return escapeCharSyntaxError(c, ErrorMessages.INVALID_ESCAPE);
//                }
//                return c;
            default:
                if (!getLocalFlags().isUnicode() && isOctDigit(c)) {
                    return parseOctal(c - '0');
                }
                if (!SYNTAX_CHARACTERS.get(c)) {
                    if (getLocalFlags().isUnicode()) {
                        throw syntaxErrorHere(ErrorMessages.INVALID_ESCAPE);
                    }
//                    return escapeCharSyntaxError(c, ErrorMessages.INVALID_ESCAPE);
                }
                return c;
        }
    }

    private int parseOctal(int firstDigit) {
        int ret = firstDigit;
        for (int i = 0; !atEnd() && isOctDigit(curChar()) && i < 2; i++) {
            if (ret * 8 > 255) {
                return ret;
            }
            ret *= 8;
            ret += consumeChar() - '0';
        }
        return ret;
    }

    /**
     * Like {@link #escape}, but restricted to the forms of escapes usable in character classes.
     * This includes character escapes and character class escapes, but not assertion escapes or
     * backreferences.
     *
     * @return {@code Optional.of(ch)} if the escape sequence was a character escape sequence for
     * some character {@code ch}; {@code Optional.empty()} if it was a character class
     * escape sequence
     */
    private Optional<Integer> classEscape() {
//        if (categoryEscape(true)) { // how it used to be
//            return Optional.empty();
//        }

        if (parsePredefCharClass(curChar()) != null) {
            return Optional.empty();
        }

        Optional<Integer> characterEscape = characterEscape(); // Optional.of(parseEscapeChar(curChar(), true));  //characterEscape();
        if (characterEscape.isPresent()) {
            return characterEscape;
        } else {
//            return Optional.of(fetchEscapedChar()); // how it used to be
            return Optional.of(parseEscapeChar(consumeChar(), true));
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
    private Optional<Integer> characterEscape() {   // TODO combine the other escape method with this one?
        int beginPos = position;
        switch (curChar()) {
            case 'x': {
                advance();
                String code = getUpTo(2, JavaRegexParser::isHexDigit);
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
                    code = getMany(JavaRegexParser::isHexDigit);
                    mustMatch("}");
                } else {
                    code = getUpTo(4, JavaRegexParser::isHexDigit);
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
                String code = getUpTo(3, JavaRegexParser::isOctDigit);
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

    private void bailOut(String reason) throws UnsupportedRegexException {
        if (!silent) {
            throw new UnsupportedRegexException(reason);
        }
    }

    /// RegexASTBuilder method wrappers

    private void pushGroup() {
        if (!silent) {
            astBuilder.pushGroup();
        }
    }

    private void pushCaptureGroup() {
        if (!silent) {
            astBuilder.pushCaptureGroup();
        }
    }

    private void pushLookAheadAssertion(boolean negate) {
        if (!silent) {
            astBuilder.pushLookAheadAssertion(negate);
        }
    }

    private void pushLookBehindAssertion(boolean negate) {
        if (!silent) {
            astBuilder.pushLookBehindAssertion(negate);
        }
    }

    private void popGroup() {
        if (!silent) {
            astBuilder.popGroup();
        }
    }

    private void nextSequence() {
        if (!silent) {
            astBuilder.nextSequence();
        }
    }

    private void addCharClass(CodePointSet charSet) {
        if (!silent) {
            astBuilder.addCharClass(charSet);
        }
    }

    private void addChar(int codepoint) {
        if (!silent) {
            astBuilder.addCharClass(CodePointSet.create(codepoint), true);
        }
    }

    private void addBackReference(int groupNumber) {
        if (!silent) {
            astBuilder.addBackReference(groupNumber);
        }
    }

    private void addSubexpressionCall(int groupNumber) {
        if (!silent) {
            astBuilder.addSubexpressionCall(groupNumber);
        }
    }

    private void addCaret() {
        if (!silent) {
            astBuilder.addCaret();
        }
    }

    private void addDollar() {
        if (!silent) {
            astBuilder.addDollar();
        }
    }

    private void addQuantifier(Token.Quantifier quantifier) {
        if (!silent) {
            astBuilder.addQuantifier(quantifier);
        }
    }

    private void addDeadNode() {
        if (!silent) {
            astBuilder.addDeadNode();
        }
    }

    private void wrapCurTermInGroup() {
        if (!silent) {
            astBuilder.wrapCurTermInGroup();
        }
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

    // ---- Quantifier

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.charClass, Token.Kind.groupEnd, Token.Kind.backReference);

    private Token parseQuantifier(int c) throws RegexSyntaxException {
        int min;
        int max = -1;
        boolean greedy;
        if (c == '{') {
            final int resetIndex = position;
            BigInteger literalMin = parseDecimal();
            if (literalMin.compareTo(BigInteger.ZERO) < 0) {
                return countedRepetitionSyntaxError(resetIndex);
            }
            min = literalMin.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0 ? literalMin.intValue() : -1;
            System.out.println("min: " + min);
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
                    throw syntaxErrorHere(ErrorMessages.QUANTIFIER_OUT_OF_ORDER);
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
            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        if (lastToken.kind == Token.Kind.quantifier) {
            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_ON_QUANTIFIER);
        }
        if (!QUANTIFIER_PREV.contains(lastToken.kind)) {
            throw syntaxErrorHere(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        return Token.createQuantifier(min, max, greedy);
    }

    private BigInteger parseDecimal() {
        if (atEnd() || !isDecDigit(curChar())) {
            return BigInteger.valueOf(-1);
        }
        return parseDecimal(BigInteger.ZERO);
    }

    private BigInteger parseDecimal(BigInteger firstDigit) {
        BigInteger ret = firstDigit;
        while (!atEnd() && isDecDigit(curChar())) {
            ret = ret.multiply(BigInteger.TEN);
            ret = ret.add(BigInteger.valueOf(consumeChar() - '0'));
        }
        return ret;
    }

    private Token countedRepetitionSyntaxError(int resetIndex) throws RegexSyntaxException {
        if (getLocalFlags().isUnicode()) {
            throw syntaxErrorHere(ErrorMessages.INCOMPLETE_QUANTIFIER);
        }
        position = resetIndex;
        return charClass('{');
    }

    private Token charClass(int codePoint) {
        if (getLocalFlags().isIgnoreCase()) {
            curCharClass.clear();
            curCharClass.appendRange(codePoint, codePoint);
            return charClass(false);
        } else {
            return Token.createCharClass(CodePointSet.create(codePoint), true);
        }
    }

    private Token charClass(CodePointSet codePointSet) {
        if (getLocalFlags().isIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addSet(codePointSet);
            return charClass(false);
        } else {
            return Token.createCharClass(codePointSet);
        }
    }

    private Token charClass(boolean invert) {
        boolean wasSingleChar = !invert && curCharClass.matchesSingleChar();
        if (getLocalFlags().isIgnoreCase()) {
            CaseFoldTable.CaseFoldingAlgorithm caseFolding = getLocalFlags().isUnicode() ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode;
            CaseFoldTable.applyCaseFold(curCharClass, charClassTmp, caseFolding);
        }
        CodePointSet cps = curCharClass.toCodePointSet();
        return Token.createCharClass(invert ? cps.createInverse(inSource.getEncoding()) : cps, wasSingleChar);
    }

    private boolean consumingLookahead(String match) {  // position messes things up here somehow, fix it, to be tested
        final boolean matches = lookahead(match);
        if (matches) {
//            position--;
            advance(match.length());
        }
        return matches;
    }

    private boolean lookahead(String match) {
        if (inPattern.length() - position < match.length()) {
            return false;
        }
        return inPattern.regionMatches(position, match, 0, match.length());
    }

//    /**
//     * Indicates whether a quantifier is coming up next.
//     */
//    public boolean isQuantifierNext() {
//        if (atEnd()) {
//            return false;
//        }
//        switch (curChar()) {
//            case '*':
//            case '+':
//            case '?':
//                return true;
//            case '{':
//                int oldPosition = position;
//                try {
//                    advance();
//                    if (match("}") || match(",}")) {
//                        return false;
//                    } else {
//                        // lower bound
//                        getMany(JavaRegexParser::isDecDigit);
//                        // upper bound
//                        if (match(",")) {
//                            getMany(JavaRegexParser::isDecDigit);
//                        }
//                        return match("}");
//                    }
//                } finally {
//                    position = oldPosition;
//                }
//            default:
//                return false;
//        }
//    }

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
    private Token parens() {
        if (atEnd()) {
            throw syntaxErrorAtEnd(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        if (match("?")) {
            final int ch1 = consumeChar();
            switch (ch1) {
                case ':':
//                    group(false);
                    return Token.createNonCaptureGroupBegin();

//                case '#':
//                    parenComment();
//                    break;

                case '<': {
                    final int ch2 = consumeChar();
                    switch (ch2) {
                        case '=':
                            return Token.createLookBehindAssertionBegin(false);
//                            lookbehind(false);
                        case '!':
                            return Token.createLookBehindAssertionBegin(true);
//                            lookbehind(true);
                        default:
                            retreat();
                            String groupName = parseGroupName('>');
                            registerNamedCaptureGroup(groupName);
//                            group(true);
                            return Token.createCaptureGroupBegin();
                    }
                }

                case '=':
//                    lookahead(false);
                    return Token.createLookAheadAssertionBegin(false);

                case '!':
//                    lookahead(true);
                    return Token.createLookAheadAssertionBegin(true);

                case '>':
                    if (!inSource.getOptions().isIgnoreAtomicGroups()) {
                        bailOut("atomic groups are not supported");
                    }
//                    group(false);
                    return Token.createNonCaptureGroupBegin();

//                case '(':
//                    conditionalBackreference();
//                    break;
                case '-':       // https://www.regular-expressions.info/refmodifiers.html
                case 'm':
                case 's':
                case 'i':
                case 'x':
                case 'd':
                case 'u':
                case 'U':
                    // use the InlineFlagToken, check if ":" or ")" and deal with it properly
                    // or just use flags() and deal with it internally
                    // --> still needed to be decided
//                    flags(ch1);
//                    registerCaptureGroup();
//                    return Token.createGroupEnd();
                    return inlineFlag(ch1);
                default:
                    throw syntaxErrorAt(RbErrorMessages.unknownExtension(ch1), position - 1);
            }
        } else {
//            group(!containsNamedCaptureGroups());
            registerCaptureGroup();
            return Token.createCaptureGroupBegin();
        }

//        return Token.createCaptureGroupBegin();
    }

    private boolean containsNamedCaptureGroups() {
        return namedCaptureGroups != null;
    }

    /**
     * Just like {@code #lookahead}, but for lookbehind assertions.
     */
    private void lookbehind(boolean negate) {
        pushLookBehindAssertion(negate);
        lookbehindDepth++;
        disjunction();
        lookbehindDepth--;
        if (match(")")) {
            popGroup();
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
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


    private Token inlineFlag(int ch) {
        boolean negative = false;
        System.out.println("1: " + (char) ch);
        if (ch == '-') {
            negative = true;

            ch = curChar();
            System.out.println("2: " + (char) ch);
        }

//        JavaFlags newFlags = getLocalFlags();
        JavaFlags newFlags = getLocalFlags();

//        ch = curChar();
        System.out.println("3: " + (char) ch);

        while (ch != ')' && ch != ':') {
            System.out.println("Char in Flag: " + (char) ch);
            if (JavaFlags.isValidFlagChar(ch)) {
                if (negative) {
                    if (JavaFlags.isTypeFlag(ch)) {
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

        System.out.println("Stuff: " + (char) ch);


        boolean open = ch == ')';

        System.out.println("Flags: " + newFlags.toString());
        ;
        return Token.addInlineFlag(negative, newFlags.toString(), open);
    }

    /**
     * Parses a local flag block or an inline declaration of a global flags. Assumes that the prefix
     * '(?' was already parsed, as well as the first flag which is passed as the argument.
     */

    // TODO adjust to the use of tokens instead of the Ruby version of it
    private void flagsF(int ch0) {
        int ch = ch0;
        JavaFlags newFlags = getLocalFlags();
        boolean negative = false;
        while (ch != ')' && ch != ':') {
            System.out.println("flag: " + (char) ch + " : " + ch);
            if (ch == '-') {
                negative = true;
            } else if (JavaFlags.isValidFlagChar(ch)) {
                if (negative) {
                    if (JavaFlags.isTypeFlag(ch)) {
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

        // necessary?
        if (ch == ')') {
            openEndedLocalFlags(newFlags);
        } else {
            assert ch == ':';
            localFlags(newFlags);
        }
    }

    private void openEndedLocalFlags(JavaFlags newFlags) {
//        setLocalFlags(newFlags);
//        lastTerm = TermCategory.None;
        // Using "open-ended" flag modifiers, e.g. /a(?i)b|c/, makes Java wrap the continuation
        // of the flag modifier in parentheses, so that the above regex is equivalent to
        // /a(?i:b|c)/.
        pushGroup();
        disjunction();
        popGroup();
    }

    /**
     * Parses a block with local flags, assuming that the opening parenthesis, the flags and the ':'
     * have been parsed.
     *
     * @param newFlags - the new set of flags to be used in the block
     */
    private void localFlags(JavaFlags newFlags) {
        flagsStack.push(newFlags);
        group(false);
        flagsStack.pop();
    }

    /**
     * Parses a group, assuming that its opening parenthesis has already been parsed. Note that this
     * is used not only for ordinary capture groups, but also for named capture groups,
     * non-capturing groups or the contents of a local flags block.
     *
     * @param capturing whether or not we should push a capturing group
     */
    private void group(boolean capturing) {
        if (capturing) {
            groupIndex++;
            groupStack.push(new Group(groupIndex));
            pushCaptureGroup();
        } else {
            pushGroup();
        }
        disjunction();
        if (match(")")) {
            popGroup();
            if (capturing) {
                groupStack.pop();
            }
        } else {
            throw syntaxErrorHere(RbErrorMessages.UNTERMINATED_SUBPATTERN);
        }
    }
}
