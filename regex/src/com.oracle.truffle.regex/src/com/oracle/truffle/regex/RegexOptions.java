/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.flavor.java.JavaFlavorProvider;
import com.oracle.truffle.regex.flavor.js.JSFlavorProvider;
import com.oracle.truffle.regex.flavor.oracledb.OracleDBFlavorProvider;
import com.oracle.truffle.regex.flavor.python.PythonFlavorProvider;
import com.oracle.truffle.regex.flavor.ruby.RubyFlavorProvider;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.MatchingMode;
import com.oracle.truffle.regex.tregex.parser.RegexFlavor;
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
 * <li><b>MatchingMode</b>: specifies implicit anchoring modes. See {@link MatchingMode} for
 * details. Possible values:
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
@Option.Group(RegexLanguage.ID)
public final class RegexOptions {

    private static final int U180E_WHITESPACE = 1;
    public static final String U180E_WHITESPACE_NAME = "U180EWhitespace";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Treat 0x180E MONGOLIAN VOWEL SEPARATOR as white space. Applies to ECMAScriptFlavor only.") //
    public static final OptionKey<Boolean> U180EWhitespace = new OptionKey<>(false);

    private static final int REGRESSION_TEST_MODE = 1 << 1;
    public static final String REGRESSION_TEST_MODE_NAME = "RegressionTestMode";

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Enable regression test mode. For internal testing only.") //
    public static final OptionKey<Boolean> RegressionTestMode = new OptionKey<>(false);

    private static final int DUMP_AUTOMATA = 1 << 2;
    public static final String DUMP_AUTOMATA_NAME = "DumpAutomata";

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Dump generated automata to disk. For internal testing only.") //
    public static final OptionKey<Boolean> DumpAutomata = new OptionKey<>(false);

    private static final int STEP_EXECUTION = 1 << 3;
    public static final String STEP_EXECUTION_NAME = "StepExecution";

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Dump automata execution traces to disk. For internal testing only.") //
    public static final OptionKey<Boolean> DumpAutomataExecution = new OptionKey<>(false);

    private static final int ALWAYS_EAGER = 1 << 4;
    public static final String ALWAYS_EAGER_NAME = "AlwaysEager";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Force eager capture group tracking.") //
    public static final OptionKey<Boolean> AlwaysEager = new OptionKey<>(false);

    // deprecated
    private static final int UTF_16_EXPLODE_ASTRAL_SYMBOLS = 1 << 5;
    public static final String UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME = "UTF16ExplodeAstralSymbols";

    private static final int VALIDATE = 1 << 6;
    public static final String VALIDATE_NAME = "Validate";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Don't generate a regex matcher, just check for syntax errors.") //
    public static final OptionKey<Boolean> Validate = new OptionKey<>(false);

    private static final int IGNORE_ATOMIC_GROUPS = 1 << 7;
    public static final String IGNORE_ATOMIC_GROUPS_NAME = "IgnoreAtomicGroups";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Treat atomic groups the same as regular groups.") //
    public static final OptionKey<Boolean> IgnoreAtomicGroups = new OptionKey<>(false);

    private static final int GENERATE_DFA_IMMEDIATELY = 1 << 8;
    private static final String GENERATE_DFA_IMMEDIATELY_NAME = "GenerateDFAImmediately";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Disable lazy DFA generation.") //
    public static final OptionKey<Boolean> GenerateDFAImmediately = new OptionKey<>(false);

    private static final int BOOLEAN_MATCH = 1 << 9;
    public static final String BOOLEAN_MATCH_NAME = "BooleanMatch";

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Don't report capture groups, only return a boolean result.") //
    public static final OptionKey<Boolean> BooleanMatch = new OptionKey<>(false);

    private static final int MUST_ADVANCE = 1 << 10;
    public static final String MUST_ADVANCE_NAME = "MustAdvance";

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.STABLE, help = "Ignore matches that start exactly at the starting index.") //
    public static final OptionKey<Boolean> MustAdvance = new OptionKey<>(false);

    private static final int GENERATE_INPUT = 1 << 11;
    public static final String GENERATE_INPUT_NAME = "GenerateInput";

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Try to generate strings the given regex would match. For internal testing only.") //
    public static final OptionKey<Boolean> GenerateInput = new OptionKey<>(false);

    private static final int FORCE_LINEAR_EXECUTION = 1 << 12;

    @Option(category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL, help = "Reject all regexes that cannot be executed in linear time.") //
    public static final OptionKey<Boolean> ForceLinearExecution = new OptionKey<>(false);

    public static final String FLAVOR_NAME = "Flavor";
    public static final String FLAVOR_PYTHON = "Python";
    public static final String FLAVOR_RUBY = "Ruby";
    public static final String FLAVOR_ORACLE_DB = "OracleDB";
    public static final String FLAVOR_ECMASCRIPT = "ECMAScript";
    public static final String FLAVOR_JAVA = "JavaUtilPattern";

    private static final RegexFlavor[] FLAVOR_CACHE = new RegexFlavor[FlavorOption.values().length];
    private static final String[] FLAVOR_OPTIONS = {FLAVOR_PYTHON, FLAVOR_RUBY, FLAVOR_ORACLE_DB, FLAVOR_ECMASCRIPT, FLAVOR_JAVA};

    static {
        FLAVOR_CACHE[FlavorOption.ECMAScript.ordinal()] = new JSFlavorProvider().get();
        FLAVOR_CACHE[FlavorOption.Python.ordinal()] = new PythonFlavorProvider().get();
        FLAVOR_CACHE[FlavorOption.Ruby.ordinal()] = new RubyFlavorProvider().get();
        FLAVOR_CACHE[FlavorOption.OracleDB.ordinal()] = new OracleDBFlavorProvider().get();
        FLAVOR_CACHE[FlavorOption.JavaUtilPattern.ordinal()] = new JavaFlavorProvider().get();
    }

    public enum FlavorOption {
        ECMAScript,
        Python,
        Ruby,
        OracleDB,
        JavaUtilPattern;

        RegexFlavor get() {
            return FLAVOR_CACHE[ordinal()];
        }
    }

    private static RegexFlavor getDefaultFlavor() {
        return FlavorOption.ECMAScript.get();
    }

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Regex flavor to use.", usageSyntax = "ECMAScript|JavaUtilPattern|OracleDB|Python|Ruby") //
    public static final OptionKey<FlavorOption> Flavor = new OptionKey<>(FlavorOption.ECMAScript);

    public static final String ENCODING_NAME = "Encoding";

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Input string encoding.", usageSyntax = "UTF-8|UTF-16|UTF-16-RAW|UTF-32|BYTES|LATIN-1") //
    public static final OptionKey<Encodings.Encoding> Encoding = new OptionKey<>(Encodings.UTF_16_RAW, new OptionType<>("Encoding", name -> {
        Encodings.Encoding enc = Encodings.getEncoding(name);
        if (enc == null) {
            throw new IllegalArgumentException(String.format("unknown encoding '%s'. Supported encodings are: UTF-8,UTF-16,UTF-16-RAW,UTF-32,BYTES,LATIN-1", name));
        }
        return enc;
    }));

    public static final String PYTHON_METHOD_NAME = "PythonMethod";
    public static final String MATCHING_MODE_NAME = "MatchingMode";
    public static final String MATCHING_MODE_SEARCH = "search";
    public static final String MATCHING_MODE_MATCH = "match";
    public static final String MATCHING_MODE_FULLMATCH = "fullmatch";
    private static final String[] MATCHING_MODE_OPTIONS = {MATCHING_MODE_SEARCH, MATCHING_MODE_MATCH, MATCHING_MODE_FULLMATCH};

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Regex matching mode. Supported modes are: " +
                    "'search': Default. Search for a match anywhere in the input string. " +
                    "'match': Anchor match at starting index. " +
                    "'fullmatch': Anchor match at starting and end index.", usageSyntax = "search|match|fullmatch") //
    public static final OptionKey<MatchingMode> MatchingMode = new OptionKey<>(com.oracle.truffle.regex.tregex.parser.MatchingMode.search);

    public static final String PYTHON_LOCALE_NAME = "PythonLocale";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Locale to use for Python flavor's locale sensitive features.") //
    public static final OptionKey<String> PythonLocale = new OptionKey<>("");

    public static final String JAVA_JDK_VERSION_NAME = "JavaJDKVersion";
    public static final String[] JAVA_JDK_VERSION_OPTIONS = {"21", "22", "23", "24", "25"};
    public static final short JAVA_JDK_VERSION_MIN = 21;
    private static final short JAVA_JDK_VERSION_DEFAULT = 24;

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "JDK compatibility version for Java flavor.", usageSyntax = "21|22|23|24|25") //
    public static final OptionKey<Integer> JavaJDKVersion = new OptionKey<>((int) JAVA_JDK_VERSION_DEFAULT);

    public static final String MAX_DFA_SIZE_NAME = "MaxDFASize";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "DFA bailout threshold.") //
    public static final OptionKey<Integer> MaxDFASize = new OptionKey<>(TRegexOptions.TRegexMaxDFATransitions);

    public static final String MAX_BACK_TRACKER_SIZE_NAME = "MaxBackTrackerCompileSize";

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Backtracker JIT compilation bailout threshold.") //
    public static final OptionKey<Integer> MaxBackTrackerJITSize = new OptionKey<>(TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Single character class quantifier unroll limit.") //
    public static final OptionKey<Integer> QuantifierUnrollLimitSingleCC = new OptionKey<>(TRegexOptions.TRegexQuantifierUnrollLimitSingleCC);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Group quantifier unroll limit.") //
    public static final OptionKey<Integer> QuantifierUnrollLimitGroup = new OptionKey<>(TRegexOptions.TRegexQuantifierUnrollLimitGroup);

    private static final String PARSE_SHORT_ERROR_MSG = "expected a short integer value";

    public static OptionDescriptors getDescriptors() {
        return new RegexOptionsOptionDescriptors();
    }

    public static final RegexOptions DEFAULT = new RegexOptions(0,
                    (short) TRegexOptions.TRegexMaxDFATransitions,
                    (short) TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize,
                    getDefaultFlavor(),
                    Encodings.UTF_16_RAW, null, null, JAVA_JDK_VERSION_DEFAULT,
                    (short) TRegexOptions.TRegexQuantifierUnrollLimitSingleCC,
                    (short) TRegexOptions.TRegexQuantifierUnrollLimitGroup);

    private final int options;
    private final short maxDFASize;
    private final short maxBackTrackerCompileSize;
    private final RegexFlavor flavor;
    private final Encodings.Encoding encoding;
    private final MatchingMode matchingMode;
    private final String pythonLocale;
    private final short javaJDKVersion;
    public final short quantifierUnrollLimitSingleCC;
    public final short quantifierUnrollLimitGroup;

    private RegexOptions(
                    int options,
                    short maxDFASize,
                    short maxBackTrackerCompileSize,
                    RegexFlavor flavor,
                    Encodings.Encoding encoding,
                    MatchingMode matchingMode,
                    String pythonLocale,
                    short javaJDKVersion,
                    short quantifierUnrollLimitSingleCC,
                    short quantifierUnrollLimitGroup) {
        this.options = options;
        this.maxDFASize = maxDFASize;
        this.maxBackTrackerCompileSize = maxBackTrackerCompileSize;
        this.flavor = flavor;
        this.encoding = encoding;
        this.matchingMode = matchingMode;
        this.pythonLocale = pythonLocale;
        this.javaJDKVersion = javaJDKVersion;
        this.quantifierUnrollLimitSingleCC = quantifierUnrollLimitSingleCC;
        this.quantifierUnrollLimitGroup = quantifierUnrollLimitGroup;
    }

    public static Builder builder(TruffleLanguage.ParsingRequest parsingRequest) {
        return builder(parsingRequest.getSource(), parsingRequest.getOptionValues());
    }

    public static Builder builder(Source source, OptionValues optionValues) {
        return new Builder(source, source.getCharacters().toString(), optionValues);
    }

    private boolean isBitSet(int bit) {
        return (options & bit) != 0;
    }

    /**
     * Maximum number of DFA transitions. Must be less than {@link Short#MAX_VALUE}. Defaults to
     * {@link TRegexOptions#TRegexMaxDFATransitions}.
     */
    public short getMaxDFASize() {
        return maxDFASize;
    }

    /**
     * Maximum number of NFA transitions to allow for runtime compilation. Must be less than
     * {@link Short#MAX_VALUE}. Defaults to
     * {@link TRegexOptions#TRegexMaxBackTrackerMergeExplodeSize}.
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
        return isDumpAutomata() && (getFlavor().getName().equals(FLAVOR_ECMASCRIPT) || getFlavor().getName().equals(FLAVOR_ORACLE_DB));
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

    /**
     * Reject all regexes that cannot be executed in linear time.
     */
    public boolean isForceLinearExecution() {
        return isBitSet(FORCE_LINEAR_EXECUTION);
    }

    public RegexFlavor getFlavor() {
        return flavor;
    }

    public Encodings.Encoding getEncoding() {
        return encoding;
    }

    public MatchingMode getMatchingMode() {
        return matchingMode;
    }

    public String getPythonLocale() {
        return pythonLocale;
    }

    /**
     * JDK compatibility version for {@code JavaFlavor}.
     */
    public int getJavaJDKVersion() {
        return javaJDKVersion;
    }

    public RegexOptions withBooleanMatch() {
        return new RegexOptions(options | BOOLEAN_MATCH, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, matchingMode, pythonLocale, javaJDKVersion, quantifierUnrollLimitSingleCC,
                        quantifierUnrollLimitGroup);
    }

    public RegexOptions withoutBooleanMatch() {
        return new RegexOptions(options & ~BOOLEAN_MATCH, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, matchingMode, pythonLocale, javaJDKVersion, quantifierUnrollLimitSingleCC,
                        quantifierUnrollLimitGroup);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hash = options;
        hash = prime * hash + Objects.hashCode(maxDFASize);
        hash = prime * hash + Objects.hashCode(maxBackTrackerCompileSize);
        hash = prime * hash + Objects.hashCode(flavor);
        hash = prime * hash + encoding.hashCode();
        hash = prime * hash + Objects.hashCode(matchingMode);
        hash = prime * hash + Objects.hashCode(pythonLocale);
        hash = prime * hash + Objects.hashCode(javaJDKVersion);
        hash = prime * hash + Objects.hashCode(quantifierUnrollLimitSingleCC);
        hash = prime * hash + Objects.hashCode(quantifierUnrollLimitGroup);
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
                        this.matchingMode == other.matchingMode &&
                        this.pythonLocale.equals(other.pythonLocale) &&
                        this.javaJDKVersion == other.javaJDKVersion &&
                        this.quantifierUnrollLimitSingleCC == other.quantifierUnrollLimitSingleCC &&
                        this.quantifierUnrollLimitGroup == other.quantifierUnrollLimitGroup;

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (maxDFASize != TRegexOptions.TRegexMaxDFATransitions) {
            sb.append(MAX_DFA_SIZE_NAME + "=").append(maxDFASize).append(',');
        }
        if (maxBackTrackerCompileSize != TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize) {
            sb.append(MAX_BACK_TRACKER_SIZE_NAME + "=").append(maxBackTrackerCompileSize).append(',');
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
        sb.append(FLAVOR_NAME).append('=').append(flavor.getName()).append(',');
        sb.append(ENCODING_NAME + "=").append(encoding.getName()).append(",");
        if (matchingMode != null) {
            sb.append(MATCHING_MODE_NAME).append('=').append(matchingMode).append(',');
        }
        if (pythonLocale != null) {
            sb.append(PYTHON_LOCALE_NAME + "=").append(pythonLocale).append(",");
        }
        if (isGenerateInput()) {
            sb.append(GENERATE_INPUT_NAME).append("=true").append(",");
        }
        if (javaJDKVersion != JAVA_JDK_VERSION_DEFAULT) {
            sb.append(JAVA_JDK_VERSION_NAME).append("=").append(javaJDKVersion).append(",");
        }
        if (!sb.isEmpty()) {
            assert sb.charAt(sb.length() - 1) == ',';
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public static final class Builder {

        private final Source source;
        private final String src;
        private final OptionValues optionValues;
        private int i;
        private int flags;
        private short maxDFASize = TRegexOptions.TRegexMaxDFATransitions;
        private short maxBackTrackerCompileSize = TRegexOptions.TRegexMaxBackTrackerMergeExplodeSize;
        private RegexFlavor flavor;
        private Encodings.Encoding encoding = Encodings.UTF_16_RAW;
        private MatchingMode matchingMode;
        private String pythonLocale;
        private short javaJDKVersion = JAVA_JDK_VERSION_DEFAULT;
        private short quantifierUnrollThresholdSingleCC;
        private short quantifierUnrollThresholdGroup;

        private Builder(Source source, String sourceString, OptionValues optionValues) {
            this.source = source;
            this.src = sourceString;
            this.optionValues = optionValues;
            this.flags = 0;
            this.flavor = getDefaultFlavor();
            quantifierUnrollThresholdSingleCC = DEFAULT.quantifierUnrollLimitSingleCC;
            quantifierUnrollThresholdGroup = DEFAULT.quantifierUnrollLimitGroup;
        }

        @TruffleBoundary
        public int parseOptions() throws RegexSyntaxException {
            if (src.startsWith("/")) {
                parseBooleanSrcOption(U180EWhitespace, U180E_WHITESPACE);
                parseBooleanSrcOption(RegressionTestMode, REGRESSION_TEST_MODE);
                parseBooleanSrcOption(DumpAutomata, DUMP_AUTOMATA);
                parseBooleanSrcOption(DumpAutomataExecution, STEP_EXECUTION);
                parseBooleanSrcOption(AlwaysEager, ALWAYS_EAGER);
                parseBooleanSrcOption(Validate, VALIDATE);
                parseBooleanSrcOption(IgnoreAtomicGroups, IGNORE_ATOMIC_GROUPS);
                parseBooleanSrcOption(GenerateDFAImmediately, GENERATE_DFA_IMMEDIATELY);
                parseBooleanSrcOption(BooleanMatch, BOOLEAN_MATCH);
                parseBooleanSrcOption(MustAdvance, MUST_ADVANCE);
                parseBooleanSrcOption(GenerateInput, GENERATE_INPUT);
                parseBooleanSrcOption(ForceLinearExecution, FORCE_LINEAR_EXECUTION);
                flavor = optionValues.get(Flavor).get();
                encoding = optionValues.get(Encoding);
                matchingMode = optionValues.get(MatchingMode);
                pythonLocale = optionValues.get(PythonLocale);
                javaJDKVersion = parseShortSrcOption("JavaJDKVersion", JavaJDKVersion, JAVA_JDK_VERSION_MIN);
                maxDFASize = parseShortSrcOption("MaxDFASize", MaxDFASize, 0);
                maxBackTrackerCompileSize = parseShortSrcOption("MaxBackTrackerJITSize", MaxBackTrackerJITSize, 0);
                quantifierUnrollThresholdSingleCC = parseShortSrcOption("QuantifierUnrollLimitSingleCC", QuantifierUnrollLimitSingleCC, 1);
                quantifierUnrollThresholdGroup = parseShortSrcOption("QuantifierUnrollLimitGroup", QuantifierUnrollLimitGroup, 1);
                return 0;
            }

            i = 0;
            while (i < src.length()) {
                switch (src.charAt(i)) {
                    case 'A':
                        parseBooleanOption(ALWAYS_EAGER_NAME, ALWAYS_EAGER);
                        break;
                    case 'B':
                        parseBooleanOption(BOOLEAN_MATCH_NAME, BOOLEAN_MATCH);
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
                    case 'J':
                        short version = parseShortOption(JAVA_JDK_VERSION_NAME);
                        if (version < JAVA_JDK_VERSION_MIN) {
                            throw optionsSyntaxErrorUnexpectedValue(JAVA_JDK_VERSION_OPTIONS);
                        }
                        javaJDKVersion = (byte) version;
                        break;
                    case 'M':
                        switch (lookAheadInKey(3)) {
                            case 'B':
                                maxBackTrackerCompileSize = parseShortOption(MAX_BACK_TRACKER_SIZE_NAME);
                                break;
                            case 'D':
                                maxDFASize = parseShortOption(MAX_DFA_SIZE_NAME);
                                break;
                            case 'c':
                                matchingMode = parseMatchingMode(MATCHING_MODE_NAME);
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
                                matchingMode = parseMatchingMode(PYTHON_METHOD_NAME);
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

        private void parseBooleanSrcOption(OptionKey<Boolean> key, int flag) {
            if (optionValues.get(key)) {
                flags |= flag;
            }
        }

        private short parseShortSrcOption(String optionName, OptionKey<Integer> key, int min) {
            int value = optionValues.get(key);
            if (value < min) {
                throw optionsSyntaxErrorUnexpectedValueMsg("value of " + optionName + " must be greater or equal to " + min);
            }
            if (value > Short.MAX_VALUE) {
                throw optionsSyntaxErrorUnexpectedValueMsg("value of " + optionName + " must be less or equal to " + Short.MAX_VALUE);
            }
            return (short) value;
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
                flags |= flag;
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
                    return expectValue(FlavorOption.ECMAScript, FLAVOR_ECMASCRIPT, FLAVOR_OPTIONS).get();
                case 'J':
                    return expectValue(FlavorOption.JavaUtilPattern, FLAVOR_JAVA, FLAVOR_OPTIONS).get();
                case 'R':
                    return expectValue(FlavorOption.Ruby, FLAVOR_RUBY, FLAVOR_OPTIONS).get();
                case 'O':
                    return expectValue(FlavorOption.OracleDB, FLAVOR_ORACLE_DB, FLAVOR_OPTIONS).get();
                case 'P':
                    return expectValue(FlavorOption.Python, FLAVOR_PYTHON, FLAVOR_OPTIONS).get();
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

        private MatchingMode parseMatchingMode(String optionName) throws RegexSyntaxException {
            expectOptionName(optionName);
            if (i >= src.length()) {
                throw optionsSyntaxErrorUnexpectedValue(MATCHING_MODE_OPTIONS);
            }
            switch (src.charAt(i)) {
                case 's':
                    return expectValue(com.oracle.truffle.regex.tregex.parser.MatchingMode.search, MATCHING_MODE_SEARCH, MATCHING_MODE_OPTIONS);
                case 'm':
                    return expectValue(com.oracle.truffle.regex.tregex.parser.MatchingMode.match, MATCHING_MODE_MATCH, MATCHING_MODE_OPTIONS);
                case 'f':
                    return expectValue(com.oracle.truffle.regex.tregex.parser.MatchingMode.fullmatch, MATCHING_MODE_FULLMATCH, MATCHING_MODE_OPTIONS);
                default:
                    throw optionsSyntaxErrorUnexpectedValue(MATCHING_MODE_OPTIONS);
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
            return (flags & bit) != 0;
        }

        public boolean isUtf16ExplodeAstralSymbols() {
            return isBitSet(UTF_16_EXPLODE_ASTRAL_SYMBOLS);
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

        public RegexOptions build() {
            return new RegexOptions(flags, maxDFASize, maxBackTrackerCompileSize, flavor, encoding, matchingMode, pythonLocale, javaJDKVersion, quantifierUnrollThresholdSingleCC,
                            quantifierUnrollThresholdGroup);
        }
    }
}
