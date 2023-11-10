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
package com.oracle.truffle.regex;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.charset.collation.BinaryCollator;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.flavors.ECMAScriptFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.OracleDBFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonMethod;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.java.JavaFlavor;
import com.oracle.truffle.regex.tregex.string.Encodings;

/**
 * These options define how TRegex should interpret a given parsing request.
 * <p>
 * Available options:
 * <ul>
 * <li><b>Flavor</b>: specifies the regex dialect to use. Possible values:
 * <ul>
 * <li><b>ECMAScript</b>: ECMAScript/JavaScript syntax (default).</li>
 * <li><b>Python</b>: Python 3 syntax</li>
 * <li><b>Ruby</b>: Ruby syntax.</li>
 * </ul>
 * </li>
 * <li><b>Encoding</b>: specifies the string encoding to match against. Possible values:
 * <ul>
 * <li><b>UTF-8</b></li>
 * <li><b>UTF-16</b></li>
 * <li><b>UTF-32</b></li>
 * <li><b>LATIN-1</b></li>
 * <li><b>BYTES</b> (equivalent to LATIN-1)</li>
 * </ul>
 * </li>
 * <li><b>PythonMethod</b>: specifies which Python {@code Pattern} method was called (Python flavors
 * only). Possible values:
 * <ul>
 * <li><b>search</b></li>
 * <li><b>match</b></li>
 * <li><b>fullmatch</b></li>
 * </ul>
 * </li>
 * <li><b>PythonLocale</b>: specifies which locale is to be used by this locale-sensitive Python
 * regexp</li>
 * <li><b>Validate</b>: don't generate a regex matcher object, just check the regex for syntax
 * errors.</li>
 * <li><b>U180EWhitespace</b>: treat 0x180E MONGOLIAN VOWEL SEPARATOR as part of {@code \s}. This is
 * a legacy feature for languages using a Unicode standard older than 6.3, such as ECMAScript 6 and
 * older.</li>
 * <li><b>UTF16ExplodeAstralSymbols</b>: generate one DFA states per (16 bit) {@code char} instead
 * of per-codepoint. This may improve performance in certain scenarios, but increases the likelihood
 * of DFA state explosion.</li>
 * <li><b>AlwaysEager</b>: do not generate any lazy regex matchers (lazy in the sense that they may
 * lazily compute properties of a {@link RegexResult}).</li>
 * <li><b>RegressionTestMode</b>: exercise all supported regex matcher variants, and check if they
 * produce the same results.</li>
 * <li><b>DumpAutomata</b>: dump all generated parser trees, NFA, and DFA to disk. This will
 * generate debugging dumps of most relevant data structures in JSON, GraphViz and LaTex
 * format.</li>
 * <li><b>StepExecution</b>: dump tracing information about all DFA matcher runs.</li>
 * <li><b>IgnoreAtomicGroups</b>: treat atomic groups as ordinary groups (experimental).</li>
 * <li><b>MustAdvance</b>: force the matcher to advance by at least one character, either by finding
 * a non-zero-width match or by skipping at least one character before matching.</li>
 * </ul>
 * All options except {@code Flavor}, {@code Encoding} and {@code PythonMethod} are boolean and
 * {@code false} by default.
 */
public final class RegexOptions {

    private static final int U180E_WHITESPACE = 1;
    public static final String U180E_WHITESPACE_NAME = "U180EWhitespace";
    private static final int REGRESSION_TEST_MODE = 1 << 1;
    public static final String REGRESSION_TEST_MODE_NAME = "RegressionTestMode";
    private static final int DUMP_AUTOMATA = 1 << 2;
    public static final String DUMP_AUTOMATA_NAME = "DumpAutomata";
    private static final int STEP_EXECUTION = 1 << 3;
    public static final String STEP_EXECUTION_NAME = "StepExecution";
    private static final int ALWAYS_EAGER = 1 << 4;
    public static final String ALWAYS_EAGER_NAME = "AlwaysEager";
    private static final int UTF_16_EXPLODE_ASTRAL_SYMBOLS = 1 << 5;
    public static final String UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME = "UTF16ExplodeAstralSymbols";
    private static final int VALIDATE = 1 << 6;
    public static final String VALIDATE_NAME = "Validate";
    private static final int IGNORE_ATOMIC_GROUPS = 1 << 7;
    public static final String IGNORE_ATOMIC_GROUPS_NAME = "IgnoreAtomicGroups";
    private static final int GENERATE_DFA_IMMEDIATELY = 1 << 8;
    private static final String GENERATE_DFA_IMMEDIATELY_NAME = "GenerateDFAImmediately";
    private static final int BOOLEAN_MATCH = 1 << 9;
    public static final String BOOLEAN_MATCH_NAME = "BooleanMatch";
    private static final int MUST_ADVANCE = 1 << 10;
    public static final String MUST_ADVANCE_NAME = "MustAdvance";
    private static final int GENERATE_INPUT = 1 << 11;
    public static final String GENERATE_INPUT_NAME = "GenerateInput";
    public static final String COLLATION_NAME = "Collation";
    public static final String FLAVOR_NAME = "Flavor";
    public static final String FLAVOR_PYTHON = "Python";
    public static final String FLAVOR_RUBY = "Ruby";
    public static final String FLAVOR_ORACLE_DB = "OracleDB";
    public static final String FLAVOR_ECMASCRIPT = "ECMAScript";
    public static final String FLAVOR_JAVA = "JavaUtilPattern";
    private static final String[] FLAVOR_OPTIONS = {FLAVOR_PYTHON, FLAVOR_RUBY, FLAVOR_ORACLE_DB, FLAVOR_ECMASCRIPT, FLAVOR_JAVA};

    public static final String ENCODING_NAME = "Encoding";

    public static final String PYTHON_METHOD_NAME = "PythonMethod";
    public static final String PYTHON_METHOD_SEARCH = "search";
    public static final String PYTHON_METHOD_MATCH = "match";
    public static final String PYTHON_METHOD_FULLMATCH = "fullmatch";
    private static final String[] PYTHON_METHOD_OPTIONS = {PYTHON_METHOD_SEARCH, PYTHON_METHOD_MATCH, PYTHON_METHOD_FULLMATCH};

    public static final String PYTHON_LOCALE_NAME = "PythonLocale";

    public static final String MAX_DFA_SIZE_NAME = "MaxDFASize";

    public static final String MAX_BACK_TRACKER_SIZE_NAME = "MaxBackTrackerCompileSize";

    private static final String PARSE_SHORT_ERROR_MSG = "expected a short integer value";

    public static final RegexOptions DEFAULT = new RegexOptions(0,
                    (short) TRegexOptions.TRegexMaxDFATransitions,
                    (short) TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize,
                    ECMAScriptFlavor.INSTANCE,
                    Encodings.UTF_16_RAW, null, null, null);

    private final int options;
    private final short maxDFASize;
    private final short maxBackTrackerCompileSize;
    private final RegexFlavor flavor;
    private final Encodings.Encoding encoding;
    private final PythonMethod pythonMethod;
    private final String pythonLocale;
    private final String collation;

    private RegexOptions(
                    int options,
                    short maxDFASize,
                    short maxBackTrackerCompileSize,
                    RegexFlavor flavor,
                    Encodings.Encoding encoding,
                    PythonMethod pythonMethod,
                    String pythonLocale,
                    String collation) {
        this.options = options;
        this.maxDFASize = maxDFASize;
        this.maxBackTrackerCompileSize = maxBackTrackerCompileSize;
        this.flavor = flavor;
        this.encoding = encoding;
        this.pythonMethod = pythonMethod;
        this.pythonLocale = pythonLocale;
        this.collation = collation;
    }

    public static Builder builder(Source source, String sourceString) {
        return new Builder(source, sourceString);
    }

    private boolean isBitSet(int bit) {
        return (options & bit) != 0;
    }

    /**
     * Maximum number of DFA transitions. Must be less than {@link Short#MAX_VALUE}. Defaults to
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxDFATransitions}.
     */
    public short getMaxDFASize() {
        return maxDFASize;
    }

    /**
     * Maximum number of NFA transitions to allow for runtime compilation. Must be less than
     * {@link Short#MAX_VALUE}. Defaults to
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxBackTrackerMergeExplodeSize}.
     */
    public short getMaxBackTrackerCompileSize() {
        return maxBackTrackerCompileSize;
    }

    public boolean isU180EWhitespace() {
        return isBitSet(U180E_WHITESPACE);
    }

    public boolean isRegressionTestMode() {
        return isBitSet(REGRESSION_TEST_MODE);
    }

    /**
     * Produce ASTs and automata in JSON, DOT (GraphViz) and LaTeX formats.
     */
    public boolean isDumpAutomata() {
        return isBitSet(DUMP_AUTOMATA);
    }

    public boolean isDumpAutomataWithSourceSections() {
        return isDumpAutomata() && getFlavor() == ECMAScriptFlavor.INSTANCE;
    }

    /**
     * Trace the execution of automata in JSON files.
     */
    public boolean isStepExecution() {
        return isBitSet(STEP_EXECUTION);
    }

    /**
     * Generate DFA matchers immediately after parsing the expression.
     */
    public boolean isGenerateDFAImmediately() {
        return isBitSet(GENERATE_DFA_IMMEDIATELY);
    }

    /**
     * Don't track capture groups, just return a boolean match result instead.
     */
    public boolean isBooleanMatch() {
        return isBitSet(BOOLEAN_MATCH);
    }

    /**
     * Always match capture groups eagerly.
     */
    public boolean isAlwaysEager() {
        return isBitSet(ALWAYS_EAGER);
    }

    /**
     * Explode astral symbols ({@code 0x10000 - 0x10FFFF}) into sub-automata where every state
     * matches one {@code char} as opposed to one code point.
     */
    public boolean isUTF16ExplodeAstralSymbols() {
        return isBitSet(UTF_16_EXPLODE_ASTRAL_SYMBOLS);
    }

    /**
     * Do not generate an actual regular expression matcher, just check the given regular expression
     * for syntax errors.
     */
    public boolean isValidate() {
        return isBitSet(VALIDATE);
    }

    /**
     * Ignore atomic groups (found e.g. in Ruby regular expressions), treat them as regular groups.
     */
    public boolean isIgnoreAtomicGroups() {
        return isBitSet(IGNORE_ATOMIC_GROUPS);
    }

    /**
     * Do not return zero-width matches at the beginning of the search string. The matcher must
     * advance by at least one character by either finding a match of non-zero width or finding a
     * match after advancing skipping several characters.
     */
    public boolean isMustAdvance() {
        return isBitSet(MUST_ADVANCE);
    }

    /**
     * Try to generate a string that matches the given regex and return it instead of the compiled
     * regex.
     */
    public boolean isGenerateInput() {
        return isBitSet(GENERATE_INPUT);
    }

    public RegexFlavor getFlavor() {
        return flavor;
    }

    public Encodings.Encoding getEncoding() {
        return encoding;
    }

    public PythonMethod getPythonMethod() {
        return pythonMethod;
    }

    public String getPythonLocale() {
        return pythonLocale;
    }

    public RegexOptions withEncoding(Encodings.Encoding newEnc) {
        return newEnc == encoding ? this : new RegexOptions(options, maxDFASize, maxBackTrackerCompileSize, flavor, newEnc, pythonMethod, pythonLocale, collation);
    }

    public RegexOptions withoutPythonMethod() {
        return pythonMethod == null ? this : new RegexOptions(options, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, null, pythonLocale, collation);
    }

    public RegexOptions withBooleanMatch() {
        return new RegexOptions(options | BOOLEAN_MATCH, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, pythonMethod, pythonLocale, collation);
    }

    public RegexOptions withoutBooleanMatch() {
        return new RegexOptions(options & ~BOOLEAN_MATCH, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, pythonMethod, pythonLocale, collation);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hash = options;
        hash = prime * hash + Objects.hashCode(maxDFASize);
        hash = prime * hash + Objects.hashCode(maxBackTrackerCompileSize);
        hash = prime * hash + Objects.hashCode(flavor);
        hash = prime * hash + encoding.hashCode();
        hash = prime * hash + Objects.hashCode(pythonMethod);
        hash = prime * hash + Objects.hashCode(pythonLocale);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RegexOptions other)) {
            return false;
        }
        return this.options == other.options &&
                        this.maxDFASize == other.maxDFASize &&
                        this.maxBackTrackerCompileSize == other.maxBackTrackerCompileSize &&
                        this.flavor == other.flavor &&
                        this.encoding == other.encoding &&
                        this.pythonMethod == other.pythonMethod &&
                        this.pythonLocale.equals(other.pythonLocale);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (maxDFASize != TRegexOptions.TRegexMaxDFATransitions) {
            sb.append(MAX_DFA_SIZE_NAME + "=").append(maxDFASize);
        }
        if (maxBackTrackerCompileSize != TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize) {
            sb.append(MAX_BACK_TRACKER_SIZE_NAME + "=").append(maxBackTrackerCompileSize);
        }
        if (isU180EWhitespace()) {
            sb.append(U180E_WHITESPACE_NAME + "=true,");
        }
        if (isRegressionTestMode()) {
            sb.append(REGRESSION_TEST_MODE_NAME + "=true,");
        }
        if (isDumpAutomata()) {
            sb.append(DUMP_AUTOMATA_NAME + "=true,");
        }
        if (isStepExecution()) {
            sb.append(STEP_EXECUTION_NAME + "=true,");
        }
        if (isAlwaysEager()) {
            sb.append(ALWAYS_EAGER_NAME + "=true,");
        }
        if (isUTF16ExplodeAstralSymbols()) {
            sb.append(UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME + "=true,");
        }
        if (isValidate()) {
            sb.append(VALIDATE_NAME + "=true,");
        }
        if (isIgnoreAtomicGroups()) {
            sb.append(IGNORE_ATOMIC_GROUPS_NAME + "=true,");
        }
        if (isGenerateDFAImmediately()) {
            sb.append(GENERATE_DFA_IMMEDIATELY_NAME + "=true,");
        }
        if (isBooleanMatch()) {
            sb.append(BOOLEAN_MATCH_NAME + "=true,");
        }
        if (isMustAdvance()) {
            sb.append(MUST_ADVANCE_NAME + "=true,");
        }
        if (flavor == PythonFlavor.INSTANCE) {
            sb.append(FLAVOR_NAME + "=" + FLAVOR_PYTHON + ",");
        } else if (flavor == RubyFlavor.INSTANCE) {
            sb.append(FLAVOR_NAME + "=" + FLAVOR_RUBY + ",");
        }
        sb.append(ENCODING_NAME + "=").append(encoding.getName()).append(",");
        if (pythonMethod == PythonMethod.search) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_SEARCH + ",");
        } else if (pythonMethod == PythonMethod.match) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_MATCH + ",");
        } else if (pythonMethod == PythonMethod.fullmatch) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_FULLMATCH + ",");
        }
        if (pythonLocale != null) {
            sb.append(PYTHON_LOCALE_NAME + "=").append(pythonLocale).append(",");
        }
        if (isGenerateInput()) {
            sb.append(GENERATE_INPUT_NAME).append("=true");
        }
        return sb.toString();
    }

    public static final class Builder {

        private final Source source;
        private final String src;
        private int i;
        private int options;
        private short maxDFASize = TRegexOptions.TRegexMaxDFATransitions;
        private short maxBackTrackerCompileSize = TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize;
        private RegexFlavor flavor;
        private Encodings.Encoding encoding = Encodings.UTF_16_RAW;
        private PythonMethod pythonMethod;
        private String pythonLocale;
        private String collation = BinaryCollator.NAME;

        private Builder(Source source, String sourceString) {
            this.source = source;
            this.src = sourceString;
            this.options = 0;
            this.flavor = ECMAScriptFlavor.INSTANCE;
        }

        @TruffleBoundary
        public int parseOptions() throws RegexSyntaxException {
            i = 0;
            while (i < src.length()) {
                switch (src.charAt(i)) {
                    case 'A':
                        parseBooleanOption(ALWAYS_EAGER_NAME, ALWAYS_EAGER);
                        break;
                    case 'B':
                        parseBooleanOption(BOOLEAN_MATCH_NAME, BOOLEAN_MATCH);
                        break;
                    case 'C':
                        collation = parseStringOption(COLLATION_NAME, "expected a valid collation identifier");
                        break;
                    case 'D':
                        parseBooleanOption(DUMP_AUTOMATA_NAME, DUMP_AUTOMATA);
                        break;
                    case 'E':
                        encoding = parseEncoding();
                        break;
                    case 'F':
                        flavor = parseFlavor();
                        break;
                    case 'G':
                        switch (lookAheadInKey("Generate".length())) {
                            case 'D':
                                parseBooleanOption(GENERATE_DFA_IMMEDIATELY_NAME, GENERATE_DFA_IMMEDIATELY);
                                break;
                            case 'I':
                                parseBooleanOption(GENERATE_INPUT_NAME, GENERATE_INPUT);
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey();
                        }
                        break;
                    case 'I':
                        parseBooleanOption(IGNORE_ATOMIC_GROUPS_NAME, IGNORE_ATOMIC_GROUPS);
                        break;
                    case 'M':
                        switch (lookAheadInKey(3)) {
                            case 'B':
                                maxBackTrackerCompileSize = parseShortOption(MAX_BACK_TRACKER_SIZE_NAME);
                                break;
                            case 'D':
                                maxDFASize = parseShortOption(MAX_DFA_SIZE_NAME);
                                break;
                            case 't':
                                parseBooleanOption(MUST_ADVANCE_NAME, MUST_ADVANCE);
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey();
                        }
                        break;
                    case 'P':
                        switch (lookAheadInKey("Python".length())) {
                            case 'M':
                                pythonMethod = parsePythonMethod();
                                break;
                            case 'L':
                                pythonLocale = parseStringOption(PYTHON_LOCALE_NAME, "expected a python locale name");
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey();
                        }
                        break;
                    case 'R':
                        parseBooleanOption(REGRESSION_TEST_MODE_NAME, REGRESSION_TEST_MODE);
                        break;
                    case 'S':
                        parseBooleanOption(STEP_EXECUTION_NAME, STEP_EXECUTION);
                        break;
                    case 'U':
                        switch (lookAheadInKey(1)) {
                            case '1':
                                parseBooleanOption(U180E_WHITESPACE_NAME, U180E_WHITESPACE);
                                break;
                            case 'T':
                                parseBooleanOption(UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME, UTF_16_EXPLODE_ASTRAL_SYMBOLS);
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey();
                        }
                        break;
                    case 'V':
                        parseBooleanOption(VALIDATE_NAME, VALIDATE);
                        break;
                    case ',':
                        i++;
                        break;
                    case '/':
                        return i;
                    default:
                        throw optionsSyntaxErrorUnexpectedKey();
                }
            }
            return i;
        }

        private char lookAheadInKey(int offset) {
            if (Integer.compareUnsigned(i + offset, src.length()) >= 0) {
                throw optionsSyntaxErrorUnexpectedKey();
            }
            return src.charAt(i + offset);
        }

        private void expectOptionName(String key) {
            if (!src.regionMatches(i, key, 0, key.length()) || src.charAt(i + key.length()) != '=') {
                throw optionsSyntaxErrorUnexpectedKey();
            }
            i += key.length() + 1;
        }

        private <T> T expectValue(T returnValue, String value, String... expected) {
            if (!src.regionMatches(i, value, 0, value.length())) {
                throw optionsSyntaxErrorUnexpectedValue(expected);
            }
            i += value.length();
            return returnValue;
        }

        private void parseBooleanOption(String key, int flag) throws RegexSyntaxException {
            expectOptionName(key);
            if (src.regionMatches(i, "true", 0, "true".length())) {
                options |= flag;
                i += "true".length();
            } else if (src.regionMatches(i, "false", 0, "false".length())) {
                i += "false".length();
            } else {
                throw optionsSyntaxErrorUnexpectedValue("true", "false");
            }
        }

        private short parseShortOption(String key) throws RegexSyntaxException {
            expectOptionName(key);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValueMsg(PARSE_SHORT_ERROR_MSG);
            }
            int endPos = findValueEndPos(PARSE_SHORT_ERROR_MSG);
            try {
                int value = Integer.parseUnsignedInt(src, i, endPos, 10);
                if (value < 0 || value > Short.MAX_VALUE) {
                    throw optionsSyntaxErrorUnexpectedValueMsg(PARSE_SHORT_ERROR_MSG);
                }
                i = endPos;
                return (short) value;
            } catch (NumberFormatException e) {
                throw optionsSyntaxErrorUnexpectedValueMsg(PARSE_SHORT_ERROR_MSG);
            }
        }

        private String parseStringOption(String key, String errorMsg) throws RegexSyntaxException {
            expectOptionName(key);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValueMsg(errorMsg);
            }
            int endPos = findValueEndPos(errorMsg);
            String value = src.substring(i, endPos);
            i = endPos;
            return value;
        }

        private int findValueEndPos(String errorMsg) {
            int endPos = ArrayUtils.indexOf(src, i, src.length(), ',', '/');
            if (endPos < 0) {
                throw optionsSyntaxErrorUnexpectedValueMsg(errorMsg);
            }
            return endPos;
        }

        private RegexFlavor parseFlavor() throws RegexSyntaxException {
            expectOptionName(FLAVOR_NAME);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(FLAVOR_OPTIONS);
            }
            switch (src.charAt(i)) {
                case 'E':
                    return expectValue(ECMAScriptFlavor.INSTANCE, FLAVOR_ECMASCRIPT, FLAVOR_OPTIONS);
                case 'J':
                    return expectValue(JavaFlavor.INSTANCE, FLAVOR_JAVA, FLAVOR_OPTIONS);
                case 'R':
                    return expectValue(RubyFlavor.INSTANCE, FLAVOR_RUBY, FLAVOR_OPTIONS);
                case 'O':
                    return expectValue(OracleDBFlavor.INSTANCE, FLAVOR_ORACLE_DB, FLAVOR_OPTIONS);
                case 'P':
                    return expectValue(PythonFlavor.INSTANCE, FLAVOR_PYTHON, FLAVOR_OPTIONS);
                default:
                    throw optionsSyntaxErrorUnexpectedValue(FLAVOR_OPTIONS);
            }
        }

        private Encodings.Encoding parseEncoding() throws RegexSyntaxException {
            expectOptionName(ENCODING_NAME);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(Encodings.ALL_NAMES);
            }
            switch (src.charAt(i)) {
                case 'A':
                    return expectEncodingValue(Encodings.ASCII);
                case 'B':
                    return expectValue(Encodings.BYTES, "BYTES", Encodings.ALL_NAMES);
                case 'L':
                    return expectEncodingValue(Encodings.LATIN_1);
                case 'U':
                    switch (lookAheadInKey(4)) {
                        case '8':
                            return expectEncodingValue(Encodings.UTF_8);
                        case '1':
                            return expectEncodingValue(Encodings.UTF_16);
                        case '3':
                            return expectEncodingValue(Encodings.UTF_32);
                        default:
                            throw optionsSyntaxErrorUnexpectedValue(Encodings.ALL_NAMES);
                    }
                default:
                    throw optionsSyntaxErrorUnexpectedValue(Encodings.ALL_NAMES);
            }
        }

        private Encodings.Encoding expectEncodingValue(Encodings.Encoding enc) {
            return expectValue(enc, enc.getName(), Encodings.ALL_NAMES);
        }

        private PythonMethod parsePythonMethod() throws RegexSyntaxException {
            expectOptionName(PYTHON_METHOD_NAME);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(PYTHON_METHOD_OPTIONS);
            }
            switch (src.charAt(i)) {
                case 's':
                    return expectValue(PythonMethod.search, PYTHON_METHOD_SEARCH, PYTHON_METHOD_OPTIONS);
                case 'm':
                    return expectValue(PythonMethod.match, PYTHON_METHOD_MATCH, PYTHON_METHOD_OPTIONS);
                case 'f':
                    return expectValue(PythonMethod.fullmatch, PYTHON_METHOD_FULLMATCH, PYTHON_METHOD_OPTIONS);
                default:
                    throw optionsSyntaxErrorUnexpectedValue(PYTHON_METHOD_OPTIONS);
            }
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedKey() {
            int eqlPos = src.indexOf('=', i);
            return optionsSyntaxError(String.format("unexpected option '%s'", src.substring(i, eqlPos < 0 ? src.length() : eqlPos)));
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedValue(String... expected) {
            return optionsSyntaxErrorUnexpectedValueMsg(String.format("expected one of %s", Arrays.toString(expected)));
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedValueMsg(String msg) {
            int commaPos = src.indexOf(',', i);
            String value = src.substring(i, commaPos < 0 ? src.length() : commaPos);
            return optionsSyntaxError(String.format("unexpected value '%s', %s", value, msg));
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxError(String msg) {
            return RegexSyntaxException.createOptions(source, String.format("Invalid options syntax in '%s': %s", src, msg), i);
        }

        private boolean isBitSet(int bit) {
            return (options & bit) != 0;
        }

        public Builder u180eWhitespace(boolean enabled) {
            updateOption(enabled, U180E_WHITESPACE);
            return this;
        }

        public Builder regressionTestMode(boolean enabled) {
            updateOption(enabled, REGRESSION_TEST_MODE);
            return this;
        }

        public Builder dumpAutomata(boolean enabled) {
            updateOption(enabled, DUMP_AUTOMATA);
            return this;
        }

        public Builder stepExecution(boolean enabled) {
            updateOption(enabled, STEP_EXECUTION);
            return this;
        }

        public Builder alwaysEager(boolean enabled) {
            updateOption(enabled, ALWAYS_EAGER);
            return this;
        }

        public Builder utf16ExplodeAstralSymbols(boolean enabled) {
            updateOption(enabled, UTF_16_EXPLODE_ASTRAL_SYMBOLS);
            return this;
        }

        public boolean isUtf16ExplodeAstralSymbols() {
            return isBitSet(UTF_16_EXPLODE_ASTRAL_SYMBOLS);
        }

        public Builder validate(boolean enabled) {
            updateOption(enabled, VALIDATE);
            return this;
        }

        public Builder ignoreAtomicGroups(boolean enabled) {
            updateOption(enabled, IGNORE_ATOMIC_GROUPS);
            return this;
        }

        public Builder generateDFAImmediately(boolean enabled) {
            updateOption(enabled, GENERATE_DFA_IMMEDIATELY);
            return this;
        }

        public Builder booleanMatch(boolean enabled) {
            updateOption(enabled, BOOLEAN_MATCH);
            return this;
        }

        public Builder mustAdvance(boolean enabled) {
            updateOption(enabled, MUST_ADVANCE);
            return this;
        }

        public Builder flavor(@SuppressWarnings("hiding") RegexFlavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public RegexFlavor getFlavor() {
            return flavor;
        }

        public Builder encoding(@SuppressWarnings("hiding") Encodings.Encoding encoding) {
            this.encoding = encoding;
            return this;
        }

        public Encodings.Encoding getEncoding() {
            return encoding;
        }

        public Builder pythonMethod(@SuppressWarnings("hiding") PythonMethod pythonMethod) {
            this.pythonMethod = pythonMethod;
            return this;
        }

        public PythonMethod getPythonMethod() {
            return pythonMethod;
        }

        public Builder pythonLocale(@SuppressWarnings("hiding") String pythonLocale) {
            this.pythonLocale = pythonLocale;
            return this;
        }

        public String getPythonLocale() {
            return pythonLocale;
        }

        public RegexOptions build() {
            return new RegexOptions(options, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, pythonMethod, pythonLocale, collation);
        }

        private void updateOption(boolean enabled, int bitMask) {
            if (enabled) {
                this.options |= bitMask;
            } else {
                this.options &= ~bitMask;
            }
        }
    }
}
