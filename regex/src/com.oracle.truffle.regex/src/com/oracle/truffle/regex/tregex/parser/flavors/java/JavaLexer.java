package com.oracle.truffle.regex.tregex.parser.flavors.java;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.ErrorMessages;
import com.oracle.truffle.regex.errors.JavaErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyCaseFoldingData;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyCaseUnfoldingTrie;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class JavaLexer {

    /**
     * Characters that are considered special in Java regexes. To match these characters, they
     * need to be escaped using a backslash.
     */
    private static final TBitSet SYNTAX_CHARACTERS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}');

    private static final TBitSet SYNTAX_CHARS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '/', '?', '[', '\\', ']', '^', '{', '|', '}');

    private static final CodePointSet ID_START = UnicodeProperties.getProperty("ID_Start");
    private static final CodePointSet ID_CONTINUE = UnicodeProperties.getProperty("ID_Continue");

    /**
     * A stack of the locally enabled flags. Java enables establishing new flags and modifying flags
     * within the scope of certain expressions.
     */
    private final Deque<JavaFlags> flagsStack;

    /**
     * Characters considered as whitespace in Java's regex extended mode.
     */
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' '); // '\x0B' = '\u000b'

    /**
     * Characters considered as predefined character classes in Java.
     */
    private static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'H', 'S', 'V', 'W', 'd', 'h', 's', 'v', 'w');

    /**
     * This map contains the character sets of POSIX character classes
     */
    private static final Map<String, CodePointSet> UNICODE_POSIX_CHAR_CLASSES;

    /**
     * This is the same as above but restricted to ASCII
     */
    private static final Map<String, CodePointSet> ASCII_POSIX_CHAR_CLASSES;

    // Filling the two maps above
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
                UnicodeProperties.getProperty("General_Category=Unassigned")).createInverse(Encodings.UTF_32);

        UNICODE_POSIX_CHAR_CLASSES.put("alnum", alpha.union(digit));
        UNICODE_POSIX_CHAR_CLASSES.put("graph", graph);
        UNICODE_POSIX_CHAR_CLASSES.put("print", graph.union(blank).subtract(cntrl, buffer));
        UNICODE_POSIX_CHAR_CLASSES.put("xdigit", xdigit);
        UNICODE_POSIX_CHAR_CLASSES.put("word", word);

        for (Map.Entry<String, CodePointSet> entry : UNICODE_POSIX_CHAR_CLASSES.entrySet()) {
            ASCII_POSIX_CHAR_CLASSES.put(entry.getKey(), asciiRange.createIntersectionSingleRange(entry.getValue()));
        }
    }

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
    private final CodePointSetAccumulator charClassTmp = new CodePointSetAccumulator();
    /**
     * When parsing nested character classes, we need several instances of
     * {@link CodePointSetAccumulator}s. In order to avoid having to repeatedly allocate new ones,
     * we return unused instances to this shared pool, to be reused later.
     */
    private final List<CodePointSetAccumulator> charClassPool = new ArrayList<>();

    private final RegexSource source;
    private final String pattern;
    private final JavaFlags flags;
    private final Encodings.Encoding encoding;
    private Token lastToken;
    private int curStartIndex = 0;
    private int index = 0;
    private int nGroups = 1;
    private boolean identifiedAllGroups = false;
    private Map<String, Integer> namedCaptureGroups = null;
    //    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    private final CodePointSetAccumulator charClassCaseFoldTmp = new CodePointSetAccumulator();

    public JavaLexer(RegexSource source, JavaFlags flags) {
        this.source = source;
        this.pattern = source.getPattern();
        this.flags = flags;
        this.encoding = source.getEncoding();


        this.flagsStack = new LinkedList<>();
        flagsStack.push(flags);
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
     * Returns the last token's position in the pattern string.
     */
    public int getLastTokenPosition() {
        return curStartIndex;
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

//    private char curChar() {
//        return pattern.charAt(index);
//    }

    private int curChar() {
        return pattern.codePointAt(index);
    }

    private char consumeChar() {
        final char c = pattern.charAt(index);
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

    private boolean findChars(char... chars) {
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

    private void advance() {
        advance(1);
    }

    private void retreat() {
        advance(-1);
    }

    private void advance(int len) {
        index += len;
    }

    private boolean lookahead(String match) {
        if (pattern.length() - index < match.length()) {
            return false;
        }
        return pattern.regionMatches(index, match, 0, match.length());
    }

    private boolean consumingLookahead(String match) {
        final boolean matches = lookahead(match);
        if (matches) {
            advance(match.length());
        }
        return matches;
    }

    private boolean match(String next) {
        if (pattern.regionMatches(index, next, 0, next.length())) {
            index += next.length();
            return true;
        } else {
            return false;
        }
    }

    private void mustMatchClosingCurlyBracket() {
        if (!match("}")) {
            throw syntaxErrorHere(JavaErrorMessages.EXPECTED_BRACE);
        }
    }

    private boolean atEnd() {
        return index >= pattern.length();
    }

    /* lexer */

    private Token getNext() throws RegexSyntaxException {
        final int ch = consumeChar();

        if (getLocalFlags().isExtended()) {
            if (WHITESPACE.get(ch)) {
                return null;
            }
            if (ch == '#') {
                comment();
                return null;
            }
        }

        switch (ch) {
            case '\\':
                return escape();
            case '[':
                return characterClass();
            case '*':
            case '+':
            case '?':
            case '{':
                return parseQuantifier(ch);
            case '.':
//                if (getLocalFlags().isMultiline()) {
//                    addCharClass(inSource.getEncoding().getFullSet());
//                } else {
//                    addCharClass(CodePointSet.create('\n').createInverse(inSource.getEncoding()));
//                }
                if (getLocalFlags().isDotAll())
                    return Token.createCharClass(source.getEncoding().getFullSet());
                return Token.createCharClass(Constants.DOT);
            case '(':
                return parens();
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

    private JavaFlags getLocalFlags() {
        return flagsStack.peek();
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

    // Error reporting

    private RegexSyntaxException syntaxErrorAtEnd(String message) {
        return RegexSyntaxException.createPattern(source, message, pattern.length() - 1);
    }

    private RegexSyntaxException syntaxErrorHere(String message) {
        return RegexSyntaxException.createPattern(source, message, index);
    }

    private RegexSyntaxException syntaxErrorAt(String message, int pos) {
        return RegexSyntaxException.createPattern(source, message, pos);
    }

    private void bailOut(String reason) throws UnsupportedRegexException {
        throw new UnsupportedRegexException(reason);
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
        int restoreIndex = index;
        if ('1' <= ch && ch <= '9') {
            final int restoreIn = index;
            final int backRefNumber = (ch - '0');
            if (backRefNumber < numberOfCaptureGroups()) {
                return Token.createBackReference(backRefNumber);
            } else if (getLocalFlags().isUnicode()) {
                throw syntaxErrorHere(ErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
            }
            index = restoreIn;
        }
        switch (ch) {
            case 'A':
            case 'Z':
            case 'z':
            case 'G':
//                addCaret();
                return Token.createAnchor(CodePointSet.create(ch));
            case 'k':
                if (getLocalFlags().isUnicode() || hasNamedCaptureGroups()) {
                    if (atEnd()) {
                        throw syntaxErrorHere(ErrorMessages.ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE);
                    }
                    if (consumeChar() != '<') {
                        throw syntaxErrorHere(ErrorMessages.MISSING_GROUP_NAME);
                    }
                    String groupName = parseGroupName();
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
            case 'P':
                retreat();
                boolean capitalP = curChar() == 'P';
                advance();
                if (match("{")) {
                    String propertySpec = getMany(c -> c != '}');
                    if (atEnd()) {
                        index = restoreIndex;
                        return Token.createCharClass(CodePointSet.getEmpty());
                    } else {
                        advance();
                    }
                    unicodeParseProperty(capitalP, propertySpec);
                    CodePointSet property = unicodeParseProperty(capitalP, propertySpec);
//                    if (inCharClass) {
//                        curCharClass.addSet(property);
                    if (getLocalFlags().isIgnoreCase() && !propertySpec.equalsIgnoreCase("ascii")) {
                        fullyFoldableCharacters.addSet(property);
                    }
//                    } else {
//                        addCharClass(property);
//                    }
//                    return true;
                    return Token.createCharClass(property);
                } else {
                    index = restoreIndex;
                    return Token.createCharClass(CodePointSet.getEmpty());
                }
            default:
                // Here we differentiate the case when parsing one of the six basic pre-defined
                // character classes (\w, \W, \d, \D, \s, \S) and Unicode character property
                // escapes. Both result in sets of characters, but in the former case, we can skip
                // the case-folding step in the `charClass` method and call `Token::createCharClass`
                // directly.
                if (isPredefCharClass(ch)) {        // TODO combine those two paths and make the function return Token.createCharClass?
                    return Token.createCharClass(parsePredefCharClass(ch));
                } else {
                    return Token.createCharClass(CodePointSet.create(parseEscapeChar(ch, false)), true);
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
    private Token characterClass() {
        curCharClassClear();
        collectCharClass();
        return Token.createCharClass(curCharClass.toCodePointSet(), curCharClass.matchesSingleChar());  // TODO maybe change to !invert && curCharClass.matchesSingleChar()
    }

    private void collectCharClass() {
        boolean negated = false;
        int beginPos = index - 1;
        if (match("^")) {
            negated = true;
        }
        int firstPosInside = index;
        classBody:
        while (true) {
            if (atEnd()) {
                throw syntaxErrorAt(JavaErrorMessages.UNTERMINATED_CHARACTER_SET, beginPos);
            }

            int rangeStart = index;
            Optional<Integer> lowerBound;
            boolean wasNestedCharClass = false;
            int ch = consumeChar();

            int restorePosition = index;
            if (ch == '\\') {
//                advance();
                int ch2 = consumeChar();
                if (ch2 == 'P' || ch2 == 'p') {
                    boolean capitalP = curChar() == 'P';
                    ch2 = consumeChar();
                    if (ch2 == '{') {
                        String propertySpec = getMany(c -> c != '}');
                        if (atEnd()) {
                            index = restorePosition;
                            break;
                        } else {
                            advance();
                        }
                        CodePointSet property = unicodeParseProperty(capitalP, propertySpec);
//                    if (inCharClass) {
//                        curCharClass.addSet(property);
                        if (getLocalFlags().isIgnoreCase() && !propertySpec.equalsIgnoreCase("ascii")) {
                            fullyFoldableCharacters.addSet(property);
                        }
//                    } else {
//                        addCharClass(property);
//                    }
//                    return true;
                        curCharClass.addSet(property);
                    }
                }

                index = restorePosition;
            }


            switch (ch) {
                case ']':
                    if (index == firstPosInside + 1) {
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
                    throw syntaxErrorAt(JavaErrorMessages.UNTERMINATED_CHARACTER_SET, beginPos);
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
                        throw syntaxErrorAt(JavaErrorMessages.badCharacterRange(pattern.substring(rangeStart, index)), rangeStart);
                    }
                    curCharClassAddRange(lowerBound.get(), upperBound.get());
                }
            } else lowerBound.ifPresent(this::curCharClassAddCodePoint);
        }
        if (getLocalFlags().isIgnoreCase()) {
            caseClosure();
        }
        if (negated) {
            curCharClass.invert(source.getEncoding());
        }

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

    private Token charClass(boolean invert) {
        boolean wasSingleChar = !invert && curCharClass.matchesSingleChar();
        if (getLocalFlags().isIgnoreCase()) {
            CaseFoldTable.CaseFoldingAlgorithm caseFolding = getLocalFlags().isUnicode() ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode;
            CaseFoldTable.applyCaseFold(curCharClass, charClassTmp, caseFolding);
        }
        CodePointSet cps = curCharClass.toCodePointSet();
        return Token.createCharClass(invert ? cps.createInverse(source.getEncoding()) : cps, wasSingleChar);
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
        collectCharClass();
        curCharClassBackup.addSet(curCharClass.get());
        releaseCodePointSetAccumulator(curCharClass);
        curCharClass = curCharClassBackup;
        return true;
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
        charClassTmp.intersectWith(source.getEncoding().getFullSet());

        curCharClass.addSet(charClassTmp.get());
    }

    /**
     * Calls the argument on any element of the character class which has a case-folding.
     */
    private void caseFoldCharClass(BiConsumer<Integer, int[]> caseFoldItem) {
        if (curCharClass.get().size() < RubyCaseFoldingData.CASE_FOLD.size()) {
            for (Range r : curCharClass) {
                RubyCaseFoldingData.CASE_FOLD.subMap(r.lo, r.hi + 1).forEach(caseFoldItem);
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

// private void curCharClassAddCodePoint(int... codepoints) {
//     for (int codepoint : codepoints) {
//         curCharClassAddCodePoint(codepoint);
//     }
// }

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


    private CodePointSet unicodeParseProperty(boolean capitalP, String propertySpec) {
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

        return property;
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
    private CodePointSet parsePredefCharClass(int c) {
        switch (c) {
            case 's':
                if (source.getOptions().isU180EWhitespace()) {
                    return Constants.LEGACY_WHITE_SPACE;
                } else {
                    return Constants.WHITE_SPACE;
                }
            case 'S':
                if (source.getOptions().isU180EWhitespace()) {
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
                if (getLocalFlags().isUnicodeCharacterClass()) {
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
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

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
            default:
                if (!getLocalFlags().isUnicode() && isOctDigit(c)) {
                    return parseOctal(c - '0');
                }
                if (!SYNTAX_CHARACTERS.get(c)) {
                    if (getLocalFlags().isUnicode()) {
                        throw syntaxErrorHere(ErrorMessages.INVALID_ESCAPE);
                    }
                }
                return c;
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
        int beginPos = index;
        switch (curChar()) {
            case 'x': {
                advance();
                String code = getUpTo(2, JavaLexer::isHexDigit);
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
                    code = getMany(JavaLexer::isHexDigit);
                    mustMatchClosingCurlyBracket();
                } else {
                    code = getUpTo(4, JavaLexer::isHexDigit);
                    if (code.length() < 4) {
                        throw syntaxErrorAt(JavaErrorMessages.incompleteEscape(code), beginPos);
                    }
                }
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    if (codePoint > 0x10FFFF) {
                        throw syntaxErrorAt(JavaErrorMessages.invalidUnicodeEscape(code), beginPos);
                    }
                    return Optional.of(codePoint);
                } catch (NumberFormatException e) {
                    throw syntaxErrorAt(JavaErrorMessages.badEscape(code), beginPos);
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
                String code = getUpTo(3, JavaLexer::isOctDigit);
                int codePoint = Integer.parseInt(code, 8);
                if (codePoint > 0xFF) {
                    throw syntaxErrorAt(JavaErrorMessages.TOO_BIG_NUMBER, beginPos);
                }
                return Optional.of(codePoint);
            }
            default:
                return Optional.empty();
        }
    }

    private CodePointSet getUnicodePosixCharClass(String className) {
        if (source.getEncoding() == Encodings.ASCII) {
            return ASCII_POSIX_CHAR_CLASSES.get(className);
        }

        return trimToEncoding(UNICODE_POSIX_CHAR_CLASSES.get(className));
    }

    private CodePointSet trimToEncoding(CodePointSet codePointSet) {
        return source.getEncoding().getFullSet().createIntersectionSingleRange(codePointSet);
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
        if (isPredefCharClass(curChar())) {
            return Optional.empty();
        }

        Optional<Integer> characterEscape = characterEscape();
        if (characterEscape.isPresent()) {
            return characterEscape;
        } else {
            return Optional.of(parseEscapeChar(consumeChar(), true));
        }
    }

    /**
     * Parses a group name terminated by '>'.
     *
     * @return the group name
     */
    private String parseGroupName() {
        String groupName = getMany(c -> c != '>');
        if (!match(Character.toString('>'))) {
            throw syntaxErrorHere(JavaErrorMessages.unterminatedName('>'));
        }
        if (groupName.isEmpty()) {
            throw syntaxErrorHere(JavaErrorMessages.MISSING_GROUP_NAME);
        }
        return groupName;
    }

    public int numberOfCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return nGroups;
    }

    private void registerCaptureGroup() {
        if (!identifiedAllGroups) {
            nGroups++;
        }
    }

    public Map<String, Integer> getNamedCaptureGroups() {
//        return Collections.emptyMap();  // TODO what to be done here?
        return namedCaptureGroups;
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
                        parens();
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        index = restoreIndex;
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
    private Token parens() {
        if (atEnd()) {
            throw syntaxErrorAtEnd(JavaErrorMessages.UNTERMINATED_SUBPATTERN);
        }
        if (match("?")) {
            final int ch1 = consumeChar();
            switch (ch1) {
                case ':':
                    return Token.createNonCaptureGroupBegin();
                case '<': {
                    final int ch2 = consumeChar();
                    switch (ch2) {
                        case '=':
                            return Token.createLookBehindAssertionBegin(false);
                        case '!':
                            return Token.createLookBehindAssertionBegin(true);
                        default:
                            retreat();
                            String groupName = parseGroupName();
                            registerNamedCaptureGroup(groupName);
                            return Token.createCaptureGroupBegin();
                    }
                }
                case '=':
                    return Token.createLookAheadAssertionBegin(false);
                case '!':
                    return Token.createLookAheadAssertionBegin(true);
                case '>':
                    if (!source.getOptions().isIgnoreAtomicGroups()) {
                        bailOut("atomic groups are not supported");
                    }
                    return Token.createNonCaptureGroupBegin();
                case '-':       // https://www.regular-expressions.info/refmodifiers.html
                case 'm':
                case 's':
                case 'i':
                case 'x':
                case 'd':
                case 'u':
                case 'U':
                    // use the InlineFlagToken, check if ":" or ")" and deal with it properly
                    return inlineFlag(ch1);
                default:
                    throw syntaxErrorAt(JavaErrorMessages.unknownExtension(ch1), index - 1);
            }
        } else {
            registerCaptureGroup();
            return Token.createCaptureGroupBegin();
        }
    }

    /**
     * Parses a local flag block or an inline declaration of a global flags. Assumes that the prefix
     * '(?' was already parsed, as well as the first flag which is passed as the argument.
     */
    private Token inlineFlag(int ch) {
        boolean negative = false;
        if (ch == '-') {
            negative = true;
            ch = curChar();
        }

        JavaFlags newFlags = getLocalFlags();

        while (ch != ')' && ch != ':') {
            if (JavaFlags.isValidFlagChar(ch)) {
                if (negative) {
                    if (JavaFlags.isTypeFlag(ch)) {
                        throw syntaxErrorHere(JavaErrorMessages.UNDEFINED_GROUP_OPTION);
                    }
                    newFlags = newFlags.delFlag(ch);
                } else {
                    newFlags = newFlags.addFlag(ch);
                }
            } else if (Character.isAlphabetic(ch)) {
                throw syntaxErrorHere(JavaErrorMessages.UNDEFINED_GROUP_OPTION);
            } else {
                throw syntaxErrorHere(JavaErrorMessages.MISSING_DASH_COLON_PAREN);
            }

            if (atEnd()) {
                throw syntaxErrorAtEnd(JavaErrorMessages.MISSING_FLAG_DASH_COLON_PAREN);
            }
            ch = consumeChar();
        }

        return Token.addInlineFlag(negative, newFlags.toString(), ch == ')');
    }

    // ---- Quantifier

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.charClass, Token.Kind.groupEnd, Token.Kind.backReference);

    private Token parseQuantifier(int c) throws RegexSyntaxException {
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

    private Token countedRepetitionSyntaxError(int resetIndex) throws RegexSyntaxException {
        if (getLocalFlags().isUnicode()) {
            throw syntaxErrorHere(ErrorMessages.INCOMPLETE_QUANTIFIER);
        }
        index = resetIndex;
        return charClass('{');
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

    private BigInteger parseDecimal() {
        if (atEnd() || !isDecDigit(curChar())) {
            return BigInteger.valueOf(-1);
        }

        BigInteger ret = BigInteger.ZERO;
        while (!atEnd() && isDecDigit(curChar())) {
            ret = ret.multiply(BigInteger.TEN);
            ret = ret.add(BigInteger.valueOf(consumeChar() - '0'));
        }
        return ret;
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

}

