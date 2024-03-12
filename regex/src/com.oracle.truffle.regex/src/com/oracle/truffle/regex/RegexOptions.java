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
import com.oracle.truffle.regex.tregex.parser.flavors.ECMAScriptFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.OracleDBFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonMethod;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyFlavor;
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
    private static final String BOOLEAN_MATCH_NAME = "BooleanMatch";
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
    private static final String[] FLAVOR_OPTIONS = {FLAVOR_PYTHON, FLAVOR_RUBY, FLAVOR_ORACLE_DB, FLAVOR_ECMASCRIPT};

    public static final String ENCODING_NAME = "Encoding";

    public static final String PYTHON_METHOD_NAME = "PythonMethod";
    public static final String PYTHON_METHOD_SEARCH = "search";
    public static final String PYTHON_METHOD_MATCH = "match";
    public static final String PYTHON_METHOD_FULLMATCH = "fullmatch";
    private static final String[] PYTHON_METHOD_OPTIONS = {PYTHON_METHOD_SEARCH, PYTHON_METHOD_MATCH, PYTHON_METHOD_FULLMATCH};

    public static final String PYTHON_LOCALE_NAME = "PythonLocale";

    public static final RegexOptions DEFAULT = new RegexOptions(0, ECMAScriptFlavor.INSTANCE, Encodings.UTF_16_RAW, null, null, null);

    private final int options;
    private final RegexFlavor flavor;
    private final Encodings.Encoding encoding;
    private final PythonMethod pythonMethod;
    private final String pythonLocale;
    private final String collation;

    private RegexOptions(int options, RegexFlavor flavor, Encodings.Encoding encoding, PythonMethod pythonMethod, String pythonLocale, String collation) {
        this.options = options;
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
        return newEnc == encoding ? this : new RegexOptions(options, flavor, newEnc, pythonMethod, pythonLocale, collation);
    }

    public RegexOptions withoutPythonMethod() {
        return pythonMethod == null ? this : new RegexOptions(options, flavor, encoding, null, pythonLocale, collation);
    }

    public RegexOptions withBooleanMatch() {
        return new RegexOptions(options | BOOLEAN_MATCH, flavor, encoding, pythonMethod, pythonLocale, collation);
    }

    public RegexOptions withoutBooleanMatch() {
        return new RegexOptions(options & ~BOOLEAN_MATCH, flavor, encoding, pythonMethod, pythonLocale, collation);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hash = options;
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
        if (!(obj instanceof RegexOptions)) {
            return false;
        }
        RegexOptions other = (RegexOptions) obj;
        return this.options == other.options && this.flavor == other.flavor && this.encoding == other.encoding && this.pythonMethod == other.pythonMethod &&
                        this.pythonLocale.equals(other.pythonLocale);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
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
        sb.append(ENCODING_NAME + "=" + encoding.getName() + ",");
        if (pythonMethod == PythonMethod.search) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_SEARCH + ",");
        } else if (pythonMethod == PythonMethod.match) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_MATCH + ",");
        } else if (pythonMethod == PythonMethod.fullmatch) {
            sb.append(PYTHON_METHOD_NAME + "=" + PYTHON_METHOD_FULLMATCH + ",");
        }
        if (pythonLocale != null) {
            sb.append(PYTHON_LOCALE_NAME + "=" + pythonLocale + ",");
        }
        if (isGenerateInput()) {
            sb.append(GENERATE_INPUT_NAME).append("=true");
        }
        return sb.toString();
    }

    public static final class Builder {

        private final Source source;
        private final String src;
        private int options;
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
            int i = 0;
            while (i < src.length()) {
                switch (src.charAt(i)) {
                    case 'A':
                        i = parseBooleanOption(i, ALWAYS_EAGER_NAME, ALWAYS_EAGER);
                        break;
                    case 'B':
                        i = parseBooleanOption(i, BOOLEAN_MATCH_NAME, BOOLEAN_MATCH);
                        break;
                    case 'C':
                        i = parseCollation(i);
                        break;
                    case 'D':
                        i = parseBooleanOption(i, DUMP_AUTOMATA_NAME, DUMP_AUTOMATA);
                        break;
                    case 'E':
                        i = parseEncoding(i);
                        break;
                    case 'F':
                        i = parseFlavor(i);
                        break;
                    case 'G':
                        if ((long) i + "Generate".length() >= src.length()) {
                            throw optionsSyntaxErrorUnexpectedKey(i);
                        }
                        switch (src.charAt(i + "Generate".length())) {
                            case 'D':
                                i = parseBooleanOption(i, GENERATE_DFA_IMMEDIATELY_NAME, GENERATE_DFA_IMMEDIATELY);
                                break;
                            case 'I':
                                i = parseBooleanOption(i, GENERATE_INPUT_NAME, GENERATE_INPUT);
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey(i);
                        }
                        break;
                    case 'I':
                        i = parseBooleanOption(i, IGNORE_ATOMIC_GROUPS_NAME, IGNORE_ATOMIC_GROUPS);
                        break;
                    case 'M':
                        i = parseBooleanOption(i, MUST_ADVANCE_NAME, MUST_ADVANCE);
                        break;
                    case 'P':
                        if (src.regionMatches(i, PYTHON_METHOD_NAME, 0, PYTHON_METHOD_NAME.length())) {
                            i = parsePythonMethod(i);
                        } else {
                            i = parsePythonLocale(i);
                        }
                        break;
                    case 'R':
                        i = parseBooleanOption(i, REGRESSION_TEST_MODE_NAME, REGRESSION_TEST_MODE);
                        break;
                    case 'S':
                        i = parseBooleanOption(i, STEP_EXECUTION_NAME, STEP_EXECUTION);
                        break;
                    case 'U':
                        if (i + 1 >= src.length()) {
                            throw optionsSyntaxErrorUnexpectedKey(i);
                        }
                        switch (src.charAt(i + 1)) {
                            case '1':
                                i = parseBooleanOption(i, U180E_WHITESPACE_NAME, U180E_WHITESPACE);
                                break;
                            case 'T':
                                i = parseBooleanOption(i, UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME, UTF_16_EXPLODE_ASTRAL_SYMBOLS);
                                break;
                            default:
                                throw optionsSyntaxErrorUnexpectedKey(i);
                        }
                        break;
                    case 'V':
                        i = parseBooleanOption(i, VALIDATE_NAME, VALIDATE);
                        break;
                    case ',':
                        i++;
                        break;
                    case '/':
                        return i;
                    default:
                        throw optionsSyntaxErrorUnexpectedKey(i);
                }
            }
            return i;
        }

        private int expectOptionName(int i, String key) {
            if (!src.regionMatches(i, key, 0, key.length()) || src.charAt(i + key.length()) != '=') {
                throw optionsSyntaxErrorUnexpectedKey(i);
            }
            return i + key.length() + 1;
        }

        private int expectValue(int i, String value, String... expected) {
            if (!src.regionMatches(i, value, 0, value.length())) {
                throw optionsSyntaxErrorUnexpectedValue(i, expected);
            }
            return i + value.length();
        }

        private int parseBooleanOption(int i, String key, int flag) throws RegexSyntaxException {
            int iVal = expectOptionName(i, key);
            if (src.regionMatches(iVal, "true", 0, "true".length())) {
                options |= flag;
                return iVal + "true".length();
            } else if (!src.regionMatches(iVal, "false", 0, "false".length())) {
                throw optionsSyntaxErrorUnexpectedValue(iVal, "true", "false");
            }
            return iVal + "false".length();
        }

        private int parseFlavor(int i) throws RegexSyntaxException {
            int iVal = expectOptionName(i, FLAVOR_NAME);
            if (iVal >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(iVal, FLAVOR_OPTIONS);
            }
            switch (src.charAt(iVal)) {
                case 'E':
                    flavor = ECMAScriptFlavor.INSTANCE;
                    return expectValue(iVal, FLAVOR_ECMASCRIPT, FLAVOR_OPTIONS);
                case 'R':
                    flavor = RubyFlavor.INSTANCE;
                    return expectValue(iVal, FLAVOR_RUBY, FLAVOR_OPTIONS);
                case 'O':
                    flavor = OracleDBFlavor.INSTANCE;
                    return expectValue(iVal, FLAVOR_ORACLE_DB, FLAVOR_OPTIONS);
                case 'P':
                    flavor = PythonFlavor.INSTANCE;
                    return expectValue(iVal, FLAVOR_PYTHON, FLAVOR_OPTIONS);
                default:
                    throw optionsSyntaxErrorUnexpectedValue(iVal, FLAVOR_OPTIONS);
            }
        }

        private int parseCollation(int i) {
            int iVal = expectOptionName(i, COLLATION_NAME);
            if (iVal >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValueMsg(iVal, "expected a valid collation identifier");
            }
            int endPos = ArrayUtils.indexOf(src, iVal, src.length(), ',', '/');
            if (endPos < 0) {
                throw optionsSyntaxErrorUnexpectedValueMsg(iVal, "expected a valid collation identifier");
            }
            collation = src.substring(iVal, endPos);
            return endPos;
        }

        private int parseEncoding(int i) throws RegexSyntaxException {
            int iVal = expectOptionName(i, ENCODING_NAME);
            if (iVal >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(iVal, Encodings.ALL_NAMES);
            }
            switch (src.charAt(iVal)) {
                case 'A':
                    encoding = Encodings.ASCII;
                    return expectValue(iVal, Encodings.ASCII.getName(), Encodings.ALL_NAMES);
                case 'B':
                    encoding = Encodings.BYTES;
                    return expectValue(iVal, "BYTES", Encodings.ALL_NAMES);
                case 'L':
                    encoding = Encodings.LATIN_1;
                    return expectValue(iVal, Encodings.LATIN_1.getName(), Encodings.ALL_NAMES);
                case 'U':
                    if (iVal + 4 >= src.length()) {
                        throw optionsSyntaxErrorUnexpectedValue(iVal, FLAVOR_OPTIONS);
                    }
                    switch (src.charAt(iVal + 4)) {
                        case '8':
                            encoding = Encodings.UTF_8;
                            return expectValue(iVal, Encodings.UTF_8.getName(), Encodings.ALL_NAMES);
                        case '1':
                            encoding = Encodings.UTF_16;
                            return expectValue(iVal, Encodings.UTF_16.getName(), Encodings.ALL_NAMES);
                        case '3':
                            encoding = Encodings.UTF_32;
                            return expectValue(iVal, Encodings.UTF_32.getName(), Encodings.ALL_NAMES);
                        default:
                            throw optionsSyntaxErrorUnexpectedValue(iVal, Encodings.ALL_NAMES);
                    }
                default:
                    throw optionsSyntaxErrorUnexpectedValue(iVal, Encodings.ALL_NAMES);
            }
        }

        private int parsePythonMethod(int i) throws RegexSyntaxException {
            int iVal = expectOptionName(i, PYTHON_METHOD_NAME);
            if (iVal >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(iVal, PYTHON_METHOD_OPTIONS);
            }
            switch (src.charAt(iVal)) {
                case 's':
                    pythonMethod = PythonMethod.search;
                    return expectValue(iVal, PYTHON_METHOD_SEARCH, PYTHON_METHOD_OPTIONS);
                case 'm':
                    pythonMethod = PythonMethod.match;
                    return expectValue(iVal, PYTHON_METHOD_MATCH, PYTHON_METHOD_OPTIONS);
                case 'f':
                    pythonMethod = PythonMethod.fullmatch;
                    return expectValue(iVal, PYTHON_METHOD_FULLMATCH, PYTHON_METHOD_OPTIONS);
                default:
                    throw optionsSyntaxErrorUnexpectedValue(iVal, PYTHON_METHOD_OPTIONS);
            }
        }

        private int parsePythonLocale(int i) throws RegexSyntaxException {
            int iStart = expectOptionName(i, PYTHON_LOCALE_NAME);
            int iEnd = ArrayUtils.indexOf(src, iStart, src.length(), ',', '/');
            if (iEnd == -1) {
                iEnd = src.length();
            }
            pythonLocale = src.substring(iStart, iEnd);
            return iEnd;
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedKey(int i) {
            int eqlPos = src.indexOf('=', i);
            return optionsSyntaxError(String.format("unexpected option '%s'", src.substring(i, eqlPos < 0 ? src.length() : eqlPos)), i);
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedValue(int i, String... expected) {
            return optionsSyntaxErrorUnexpectedValueMsg(i, String.format("expected one of %s", Arrays.toString(expected)));
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxErrorUnexpectedValueMsg(int i, String msg) {
            int commaPos = src.indexOf(',', i);
            String value = src.substring(i, commaPos < 0 ? src.length() : commaPos);
            return optionsSyntaxError(String.format("unexpected value '%s', %s", value, msg), i);
        }

        @TruffleBoundary
        private RegexSyntaxException optionsSyntaxError(String msg, int i) {
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
            return new RegexOptions(options, flavor, encoding, pythonMethod, pythonLocale, collation);
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
