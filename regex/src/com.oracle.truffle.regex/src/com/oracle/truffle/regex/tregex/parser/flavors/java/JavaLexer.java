package com.oracle.truffle.regex.tregex.parser.flavors.java;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.ClassSetContents;
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
                    0x0000, 0x0009,
                    0x000e, 0x0084,
                    0x0086, 0x2027,
                    0x202a, 0x10ffff);
    public static final CodePointSet NOT_VERTICAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x000a, 0x000d,
                    0x0085, 0x0085,
                    0x2028, 0x2029);
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' ');

    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASS_SETS;

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
    private final JavaFlags globalFlags;

    public JavaLexer(RegexSource source, JavaFlags flags, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        this.globalFlags = flags;
    }

    JavaFlags getLocalFlags() {
        return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
    }

    public void popLocalFlags() {
        flagsStack.pop();
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
        return true;
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
        CaseFoldData.CaseFoldUnfoldAlgorithm caseFolding = getLocalFlags().isUnicodeCase() ? CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptUnicode
                        : CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptNonUnicode;
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
                return NOT_VERTICAL_WHITE_SPACE;
            case 'V':
                return VERTICAL_WHITE_SPACE;
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
    protected void handleIncompleteEscapeX() {
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
        return null;
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        switch (c) {
            case '0':
                if (lookahead(RegexLexer::isOctalDigit, 1)) {
                    return parseOctal(0, 3);
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_OCT_ESCAPE);
            default:
                return -1;
        }
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        return 0;
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
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
