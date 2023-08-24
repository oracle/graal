package com.oracle.truffle.regex.tregex.parser.flavors.java;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.chardata.CharacterSet;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.ClassSetContentsAccumulator;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.JavaErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;
import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;

public class JavaLexer extends RegexLexer {

    // [A-Za-z]
    public static final CodePointSet ASCII_LETTERS = CodePointSet.createNoDedup(
                    0x0041, 0x005a,
                    0x0061, 0x007a);
    // [A-Za-z0-9]
    public static final CodePointSet ASCII_LETTERS_AND_DIGITS = CodePointSet.createNoDedup(
                    0x0030, 0x0039,
                    0x0041, 0x005a,
                    0x0061, 0x007a);

    // 0x0009, CHARACTER TABULATION, <TAB>
    // 0x0020, SPACE, <SP>
    // 0x00A0, NO-BREAK SPACE, <NBSP>
    // 0x1680, OGHAM SPACE MARK
    // 0x2000, EN QUAD
    // 0x2001, EM QUAD
    // 0x2002, EN SPACE
    // 0x2003, EM SPACE
    // 0x2004, THREE-PER-EM SPACE
    // 0x2005, FOUR-PER-EM SPACE
    // 0x2006, SIX-PER-EM SPACE
    // 0x2007, FIGURE SPACE
    // 0x2008, PUNCTUATION SPACE
    // 0x2009, THIN SPACE
    // 0x200A, HAIR SPACE
    // 0x202F, NARROW NO-BREAK SPACE
    // 0x205F, MEDIUM MATHEMATICAL SPACE
    // 0x3000, IDEOGRAPHIC SPACE
    public static final CodePointSet HORIZONTAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0009, 0x0009,
                    0x0020, 0x0020,
                    0x00a0, 0x00a0,
                    0x1680, 0x1680,
                    0x2000, 0x200a,
                    0x202f, 0x202f,
                    0x205f, 0x205f,
                    0x3000, 0x3000);

    public static final CodePointSet NOT_HORIZONTAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0008,
                    0x000a, 0x001f,
                    0x0021, 0x009f,
                    0x00a1, 0x180d,
                    0x180f, 0x1fff,
                    0x202b, 0x202e,
                    0x2030, 0x205e,
                    0x2060, 0x2fff,
                    0x3001, 0x10ffff);

    // 0x000A, LINE FEED (LF), <LF>
    // 0x000B, VERTICAL TAB (VT), <VT>
    // 0x000C, FORM FEED (FF), <FF>
    // 0x000D, CARRIAGE RETURN (CR), <CR>
    // 0x0085, NEXT LINE (NEL), <NEL>
    // 0x2028, LINE SEPARATOR, <LS>
    // 0x2029, PARAGRAPH SEPARATOR, <PS>
    public static final CodePointSet VERTICAL_WHITE_SPACE = CodePointSet.createNoDedup(
            0x000a, 0x000d,
            0x0085, 0x0085,
            0x2028, 0x2029);
    public static final CodePointSet NOT_VERTICAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0009,
                    0x000e, 0x0084,
                    0x0086, 0x2027,
                    0x202a, 0x10ffff);
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' ');
    private static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'H', 'S', 'V', 'W', 'd', 'h', 's', 'v', 'w');
    public static final Map<Character, CodePointSet> UNICODE_CHAR_CLASS_SETS;

    static {
        UNICODE_CHAR_CLASS_SETS = new HashMap<>();
        CodePointSet digits = UnicodeProperties.getProperty("General_Category=Decimal_Number");
        UNICODE_CHAR_CLASS_SETS.put('d', digits);
        UNICODE_CHAR_CLASS_SETS.put('D', digits.createInverse(Encodings.UTF_16));
        UNICODE_CHAR_CLASS_SETS.put('s', UnicodeProperties.getProperty("White_Space"));
        UNICODE_CHAR_CLASS_SETS.put('S', UnicodeProperties.getProperty("White_Space").createInverse(Encodings.UTF_16));
        CodePointSet alpha = UnicodeProperties.getProperty("Alphabetic");
        CodePointSet gcMn = UnicodeProperties.getProperty("General_Category=Mn");
        CodePointSet gcMe = UnicodeProperties.getProperty("General_Category=Me");
        CodePointSet gcMc = UnicodeProperties.getProperty("General_Category=Mc");
        CodePointSet gcPc = UnicodeProperties.getProperty("General_Category=Pc");
        CodePointSet joinControl = UnicodeProperties.getProperty("Join_Control");
        CodePointSet wordChars = alpha.union(digits).union(gcMn).union(gcMe).union(gcMc).union(gcPc).union(joinControl);
        CodePointSet nonWordChars = wordChars.createInverse(Encodings.UTF_16);
        UNICODE_CHAR_CLASS_SETS.put('w', wordChars);
        UNICODE_CHAR_CLASS_SETS.put('W', nonWordChars);
    }

    private final Deque<JavaFlags> flagsStack = new ArrayDeque<>();
    private JavaFlags currentFlags;
    private JavaFlags stagedFlags;
    private final JavaFlags globalFlags;

    public JavaLexer(RegexSource source, JavaFlags flags, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        if (flags.isCanonEq())
            throw new UnsupportedRegexException("Canonical equivalence is not supported");
        this.globalFlags = flags;
        this.currentFlags = flags;
        this.stagedFlags = flags;
    }

    @Override
    protected boolean isPredefCharClass(char c) {
        return PREDEFINED_CHAR_CLASSES.get(c);
    }

    JavaFlags getLocalFlags() {
        return currentFlags;
    }

    public void pushLocalFlags() {
        flagsStack.push(currentFlags);
    }

    public void popLocalFlags() {
        currentFlags = flagsStack.pop();
        stagedFlags = currentFlags;
    }

    public void applyFlags() {
        currentFlags = stagedFlags;
    }

    @Override
    protected boolean featureEnabledIgnoreCase() {
        return getLocalFlags().isCaseInsensitive();
    }

    @Override
    protected boolean featureEnabledAZPositionAssertions() {
        return true;
    }

    @Override
    protected boolean featureEnabledZLowerCaseAssertion() {
        return true;
    }

    @Override
    protected boolean featureEnabledWordBoundaries() {
        return true;
    }

    @Override
    protected boolean featureEnabledBoundedQuantifierEmptyMin() {
        return false;
    }

    @Override
    protected boolean featureEnabledPossessiveQuantifiers() {
        return false;
    }

    @Override
    protected boolean featureEnabledCharClassFirstBracketIsLiteral() {
        return true;
    }

    @Override
    protected boolean featureEnabledNestedCharClasses() {
        return false;
    }

    @Override
    protected boolean featureEnabledPOSIXCharClasses() {
        return false;
    }

    @Override
    protected CodePointSet getPOSIXCharClass(String name) {
        return null;
    }

    @Override
    protected void validatePOSIXCollationElement(String sequence) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void validatePOSIXEquivalenceClass(String sequence) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected boolean featureEnabledForwardReferences() {
        return false;
    }

    @Override
    protected boolean featureEnabledGroupComments() {
        return false;
    }

    @Override
    protected boolean featureEnabledLineComments() {
        return getLocalFlags().isComments();
    }

    @Override
    protected boolean featureEnabledIgnoreWhiteSpace() {
        return getLocalFlags().isComments();
    }

    @Override
    protected TBitSet getWhitespace() {
        // TODO: check if this is correct, may also depend on flag Pattern.UNICODE_CHARACTER_CLASS
        // (used only in skipWhitespace)
        return WHITESPACE;
    }

    @Override
    protected boolean featureEnabledOctalEscapes() {
        return false;
    }

    @Override
    protected boolean featureEnabledSpecialGroups() {
        return true;
    }

    @Override
    protected boolean featureEnabledUnicodePropertyEscapes() {
        // TODO: check if these are equal to what we currently use
        return true;
    }

    @Override
    protected boolean featureEnabledClassSetExpressions() {
        return true;
    }

    @Override
    protected boolean featureEnabledClassSetDifference() {
        return false;
    }

    @Override
    protected void caseFoldUnfold(CodePointSetAccumulator charClass) {
        // TODO: this is just a copy of JS, check the Java casefolding behavior
        // Have a look at Java's behavior together with Jirka Marsik, we may be able to re-use the
        // implementation from Python or Ruby
        CaseFoldData.CaseFoldUnfoldAlgorithm caseFolding = getLocalFlags().isUnicodeCase() ? CaseFoldData.CaseFoldUnfoldAlgorithm.PythonUnicode
                        : CaseFoldData.CaseFoldUnfoldAlgorithm.PythonAscii;
        CaseFoldData.applyCaseFoldUnfold(charClass, compilationBuffer.getCodePointSetAccumulator1(), caseFolding);
    }

    @Override
    protected ClassSetContents caseFoldClassSetAtom(ClassSetContents classSetContents) {
        if (getLocalFlags().isCaseInsensitive()) {
            return classSetContents.caseFold(compilationBuffer.getCodePointSetAccumulator1());
        } else {
            return classSetContents;
        }
    }

    @Override
    protected CodePointSet complementClassSet(CodePointSet codePointSet) {
        if (getLocalFlags().isCaseInsensitive()) {
            return codePointSet.createInverse(CaseFoldData.FOLDED_CHARACTERS, compilationBuffer);
        } else {
            return codePointSet.createInverse(source.getEncoding());
        }
    }

    @Override
    protected CodePointSet getDotCodePointSet() {
        return getLocalFlags().isDotAll() ? Constants.DOT_ALL : Constants.JAVA_DOT;
    }

    @Override
    protected CodePointSet getIdStart() {
        return ASCII_LETTERS;
    }

    @Override
    protected CodePointSet getIdContinue() {
        return ASCII_LETTERS_AND_DIGITS;
    }

    @Override
    protected int getMaxBackReferenceDigits() {
        int n = getNumberOfParsedGroups();
        int digits = 1;
        while (n >= 10) {
            n /= 10;
            digits++;
        }
        return digits;
    }

    @Override
    protected CodePointSet getPredefinedCharClass(char c) {
        char lowerCaseC = Character.toLowerCase(c);
        if (getLocalFlags().isUnicodeCharacterClass() && lowerCaseC != 'v' && lowerCaseC != 'h') {
            return UNICODE_CHAR_CLASS_SETS.get(c);
        }
        switch (c) {
            case 's':
                return Constants.WHITE_SPACE;
            case 'S':
                return Constants.NON_WHITE_SPACE;
            case 'd':
                return Constants.DIGITS;
            case 'D':
                return Constants.NON_DIGITS;
            case 'w':
                return Constants.WORD_CHARS;
            case 'W':
                return Constants.NON_WORD_CHARS;
            case 'v':
                return VERTICAL_WHITE_SPACE;
            case 'V':
                return NOT_VERTICAL_WHITE_SPACE;
            case 'h':
                return HORIZONTAL_WHITE_SPACE;
            case 'H':
                return NOT_HORIZONTAL_WHITE_SPACE;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected long boundedQuantifierMaxValue() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected RegexSyntaxException handleBoundedQuantifierOutOfOrder() {
        return syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierEmptyOrMissingMin() {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierInvalidCharacter() {
        throw syntaxError(JavaErrorMessages.UNCLOSED_COUNTED_CLOSURE);
    }

    @Override
    protected Token handleBoundedQuantifierOverflow(long min, long max) {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierOverflowMin(long min, long max) {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected RegexSyntaxException handleCCRangeOutOfOrder(int startPos) {
        return syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
    }

    @Override
    protected void handleCCRangeWithPredefCharClass(int startPos, ClassSetContents firstAtom, ClassSetContents secondAtom) {
        throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
    }

    @Override
    protected RegexSyntaxException handleComplementOfStringSet() {
        return syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
    }

    @Override
    protected void handleGroupRedefinition(String name, int newId, int oldId) {
        throw syntaxError(JavaErrorMessages.groupRedefinition(name));
    }

    @Override
    protected int handleIncompleteEscapeX() {
        if (consumingLookahead('{') && !atEnd() && RegexLexer.isHexDigit(curChar())) {
            int ch = 0;
            while (consumingLookahead(RegexLexer::isHexDigit, 1)) {
                ch = (ch << 4) + Integer.parseInt(pattern, position - 1, position, 16);
                if (ch > Character.MAX_CODE_POINT)
                    throw syntaxError(JavaErrorMessages.HEX_TOO_BIG);
            }
            if (!consumingLookahead('}'))
                throw syntaxError(JavaErrorMessages.UNCLOSED_HEX);
            return ch;
        }

        throw syntaxError(JavaErrorMessages.ILLEGAL_HEX_ESCAPE);
    }

    @Override
    protected Token handleInvalidBackReference(int reference) {
        int clamped = reference;
        while (clamped >= 10 && clamped > getNumberOfParsedGroups()) {
            clamped /= 10;
            position--;
        }
        if (clamped > getNumberOfParsedGroups()) {
            return Token.createCharClass(CodePointSet.getEmpty());
        }
        return Token.createBackReference(clamped, false);
    }

    @Override
    protected ClassSetOperator handleTripleAmpersandInClassSetExpression() {
        // Java quirk: triple ampersands are interpreted as one literal ampersand. The parent
        // function skips the first two, so we can just return the union operator here to simulate
        // this behavior.
        return ClassSetOperator.Union;
    }

    @Override
    protected RegexSyntaxException handleInvalidGroupBeginQ() {
        return syntaxError(JavaErrorMessages.UNKNOWN_INLINE_MODIFIER);
    }

    @Override
    protected RegexSyntaxException handleMixedClassSetOperators(ClassSetOperator leftOperator, ClassSetOperator rightOperator) {
        return null;
    }

    @Override
    protected RegexSyntaxException handleMissingClassSetOperand(ClassSetOperator operator) {
        return null;
    }

    @Override
    protected void handleOctalOutOfRange() {
    }

    @Override
    protected RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator) {
        return null;
    }

    @Override
    protected void handleUnfinishedEscape() {
        throw syntaxError(JavaErrorMessages.UNESCAPED_TRAILING_BACKSLASH);
    }

    @Override
    protected void handleUnfinishedGroupComment() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleUnfinishedGroupQ() {
        return syntaxError(JavaErrorMessages.UNKNOWN_INLINE_MODIFIER);
    }

    @Override
    protected RegexSyntaxException handleUnfinishedRangeInClassSet() {
        return null;
    }

    @Override
    protected void handleUnmatchedRightBrace() {
    }

    @Override
    protected RegexSyntaxException handleUnmatchedLeftBracket() {
        return syntaxError(JavaErrorMessages.UNCLOSED_CHARACTER_CLASS);
    }

    @Override
    protected void handleUnmatchedRightBracket() {
    }

    @Override
    protected void checkClassSetCharacter(int codePoint) throws RegexSyntaxException {

    }

    @Override
    protected int parseCodePointInGroupName() throws RegexSyntaxException {
        final char c = consumeChar();
        return Character.isHighSurrogate(c) ? finishSurrogatePair(c) : c;
    }

    @Override
    protected ClassSetContents parseClassSetExpression() throws RegexSyntaxException {
        return ClassSetContents.createCharacterClass(parseCharClassInternal(true));
    }

    @Override
    protected ClassSetContents parseUnicodeCharacterProperty(boolean invert) throws RegexSyntaxException {
        String name;
        if (atEnd()) {
            // necessary to match the reference error message
            name = String.valueOf((char) 0);
        } else if (!consumingLookahead("{")) {
            name = String.valueOf(consumeChar());
        } else {
            int namePos = position;
            while (!atEnd() && curChar() != '}') {
                advance();
            }

            if (!consumingLookahead("}")) {
                throw syntaxError(JavaErrorMessages.UNCLOSED_CHAR_FAMILY);
            }

            name = pattern.substring(namePos, position - 1);

            if (name.isEmpty()) {
                throw syntaxError(JavaErrorMessages.EMPTY_CHAR_FAMILY);
            }
        }

        CodePointSet p = null;

        int i = name.indexOf('=');
        if (i != -1) {
            // property construct \p{name=value}
            String value = name.substring(i + 1);
            name = name.substring(0, i).toLowerCase(Locale.ENGLISH);
            switch (name) {
                case "sc":
                case "script":
                    // p = CharPredicates.forUnicodeScript(value);
                    p = UnicodeProperties.getScriptJava(value);
                    break;
                case "blk":
                case "block":
                    // p = CharPredicates.forUnicodeBlock(value);
                    p = UnicodeProperties.getBlockJava(value);
                    break;
                case "gc":
                case "general_category":
                    // p = CharPredicates.forProperty(value, has(CASE_INSENSITIVE));
                    p = UnicodeProperties.getPropertyJava(value, getLocalFlags().isCaseInsensitive());
                    break;
                default:
                    break;
            }
            if (p == null)
                throw syntaxError(JavaErrorMessages.unknownUnicodeProperty(name, value));

        } else {
            if (name.startsWith("In")) {
                // \p{InBlockName}
                p = UnicodeProperties.getBlockJava(name.substring(2));
            } else if (name.startsWith("Is")) {
                // TODO handling the case sensitivity in UnicodeProperties might be redundant
                // \p{IsGeneralCategory} and \p{IsScriptName}
                String shortName = name.substring(2);
                p = UnicodeProperties.forUnicodePropertyJava(shortName, getLocalFlags().isCaseInsensitive());
                if (p == null)
                    p = UnicodeProperties.getPropertyJava(shortName, getLocalFlags().isCaseInsensitive());
                if (p == null) {
                    p = UnicodeProperties.getScriptJava(shortName);
                }
            } else {
                if (getLocalFlags().isUnicodeCharacterClass()) {
                    p = UnicodeProperties.forPOSIXNameJava(name, getLocalFlags().isCaseInsensitive());
                }
                if (p == null) {
                    p = UnicodeProperties.getPropertyJava(name, getLocalFlags().isCaseInsensitive());
                }
            }
            if (p == null)
                throw syntaxError(JavaErrorMessages.unknownUnicodeCharacterProperty(name));
        }

        if (invert) {
            // TODO reference implementation has something with hasSupplementary, do we care about this?;
            p = p.createInverse(Encodings.UTF_16);
        }

        return ClassSetContents.createCharacterClass(p);
    }

    private CodePointSet parseCharClassInternal(boolean consume) throws RegexSyntaxException {
        boolean invert = false;
        // negation can only occur after a bracket, we cannot have negation after '&&' for example
        if (curChar() == '^' && pattern.charAt(position - 1) == '[') {
            advance();
            invert = true;
        }

        CodePointSet curr = null;
        CodePointSet prev = null;

        while(!atEnd()) {
            if (consumingLookahead('[')) {
                curr = parseCharClassInternal(true);
                if (prev == null)
                    prev = curr;
                else
                    prev = prev.union(curr);
                continue;
            } else if (consumingLookahead('&')) {
                if (consumingLookahead('&')) {
                    CodePointSet right = null;
                    if (!atEnd()) {
                        while (curChar() != ']' && curChar() != '&') {
                            CodePointSet parse = parseCharClassInternal(consumingLookahead('['));
                            if (right == null) {
                                right = parse;
                            } else {
                                right = right.union(parse);
                            }
                        }

                        if (right != null)
                            curr = right;
                        if (prev == null) {
                            if (right == null)
                                throw syntaxError(JavaErrorMessages.BAD_CLASS_SYNTAX);
                            else
                                prev = right;
                        } else {
                            prev = prev.createIntersection(curr, compilationBuffer);
                        }
                    }
                    continue;
                } else {
                    // the single '&' is treated as a literal
                    retreat();
                }
            } else if (curChar() == ']') {
                if (prev != null) {
                    if (consume)
                        advance();
                    if (invert)
                        return prev.createInverse(Encodings.UTF_16);
                    return prev;
                }

            }
            char ch = consumeChar();
            ClassSetContents predef = parseCharClassAtomPredefCharClass(ch);
            if (predef != null) {
                curr = predef.getCodePointSet();
                if (prev == null)
                    prev = curr;
                else
                    prev = prev.union(curr);
                continue;
            }

            ClassSetContents range = parseRange(ch);
            if (range != null) {
                curr = range.getCodePointSet();
                if (prev == null)
                    prev = curr;
                else
                    prev = prev.union(curr);
            }
        }

        throw syntaxError(JavaErrorMessages.UNCLOSED_CHARACTER_CLASS);
    }

    private ClassSetContents parseRange(char c) {
        int ch = parseCharClassAtomCodePoint(c);

        if (consumingLookahead('-')) {
            if (atEnd()) {
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
            }

            if (curChar() == ']' || curChar() == '[') {

                // unmatched '-' is treated as literal
                retreat();
                return ClassSetContents.createCharacter(ch);
            }

            int upper = parseCharClassAtomCodePoint(consumeChar());
            if (upper < ch) {
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
            }

            return ClassSetContents.createRange(ch, upper);
        } else {
            return ClassSetContents.createCharacter(ch);
        }

    }

    @Override
    protected boolean parseLiteralStart(char c) {
        return c == 'Q';
    }

    @Override
    protected boolean parseLiteralEnd(char c) {
        return c == '\\' && consumingLookahead('E');
    }

    @Override
    protected Token parseCustomEscape(char c) {
        if (c == 'k') {
            if (atEnd()) {
                handleUnfinishedEscape();
            }
            if (consumeChar() != '<') {
                throw syntaxError(JavaErrorMessages.NAMED_CAPTURE_GROUP_REFERENCE_MISSING_BEGIN);
            }
            String groupName = javaParseGroupName();
            // backward reference
            if (namedCaptureGroups.containsKey(groupName)) {
                return Token.createBackReference(getSingleNamedGroupNumber(groupName), false);
            }
            throw syntaxError(JavaErrorMessages.unknownGroupReference(groupName));
        }
        if (c == 'R') {
            return Token.createLineBreak();
        }
        if (c == 'X') {
            throw new UnsupportedRegexException("Grapheme clusters are not supported");
        }
        if (c == 'G') {
            throw new UnsupportedRegexException("End of previous match boundary matcher is not supported");
        }
        return null;
    }

    @Override
    protected Token handleWordBoundary() {
        if (consumingLookahead("{g}")) {
            throw new UnsupportedRegexException("Extended grapheme cluster boundaries are not supported");
        }
        return super.handleWordBoundary();
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        switch (c) {
            case 'b':
                // \b is only valid as the boundary matcher and not as a character escape
                throw syntaxError(JavaErrorMessages.ILLEGAL_ESCAPE_SEQUENCE);
            case '0':
                if (lookahead(RegexLexer::isOctalDigit, 1)) {
                    return parseOctal(0, 3);
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_OCT_ESCAPE);
            case 'u':
                if (consumingLookahead(RegexLexer::isHexDigit, 4)) {
                    return Integer.parseInt(pattern, position - 4, position, 16);
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_UNICODE_ESC_SEQ);
            case 'c':
                if (atEnd()) {
                    throw syntaxError(JavaErrorMessages.ILLEGAL_CTRL_SEQ);
                }
                return consumeChar() ^ 64;
            case 'N':
                if (consumingLookahead('{')) {
                    int i = position;
                    if (!findChars('}')) {
                        throw syntaxError(JavaErrorMessages.UNCLOSED_CHAR_NAME);
                    }
                    advance(); // skip '}'
                    String name = pattern.substring(i, position - 1);
                    try {
                        return Character.codePointOf(name);
                    } catch (IllegalArgumentException x) {
                        throw syntaxError(JavaErrorMessages.unknownCharacterName(name));
                    }
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_NAME);
            case 'a':
                return 0x7;
            case 'e':
                return 0x1b;
            default:
                return -1;
        }
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        // any non-alphabetic character can be used after an escape
        // digits are not accepted here since they should have been parsed as octal sequence or backreference earlier
        if (Character.isAlphabetic(c) || Character.isDigit(c)) {
            throw syntaxError(JavaErrorMessages.ILLEGAL_ESCAPE_SEQUENCE);
        }
        return c;
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
        // TODO do we need to do something else than inline flags? check "Special constructs" in documentation
        char c = charAfterQuestionMark;

        if (c == '>') {
            throw new UnsupportedRegexException("Independent non-capturing groups are not supported");
        }

        int firstCharPos = position;
        JavaFlags newFlags = getLocalFlags();
        while (JavaFlags.isValidFlagChar(c)) {

            newFlags = newFlags.addFlag(c);

            if (atEnd()) {
                throw handleUnfinishedGroupQ();
            }
            c = consumeChar();
        }

        if (c == '-') {

            if (atEnd()) {
                throw handleUnfinishedGroupQ();
            }
            c = consumeChar();

            while (JavaFlags.isValidFlagChar(c)) {
                newFlags = newFlags.delFlag(c);

                if (atEnd()) {
                    throw handleUnfinishedGroupQ();
                }
                c = consumeChar();
            }
        }

        stagedFlags = newFlags;

        if (c == ':') {
            if (firstCharPos == position) {
                return null;
            }
            return Token.createInlineFlags(stagedFlags, false);
        } else if (c == ')') {
            return Token.createInlineFlags(stagedFlags, true);
        }

        return null;
    }

    @Override
    protected Token parseGroupLt() {
        registerNamedCaptureGroup(javaParseGroupName());
        return Token.createCaptureGroupBegin();
    }

    private String javaParseGroupName() {
        ParseGroupNameResult result = parseGroupName('>');
        switch (result.state) {
            case empty, invalidStart:
                throw syntaxError(JavaErrorMessages.INVALID_GROUP_NAME_START);
            case unterminated, invalidRest:
                throw syntaxError(JavaErrorMessages.INVALID_GROUP_NAME_REST);
            case valid:
                return result.groupName;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
