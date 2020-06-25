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
package com.oracle.truffle.regex;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.RegexFeatureSet;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;

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

    private static final String FLAVOR_NAME = "Flavor";
    private static final String FLAVOR_PYTHON_STR = "PythonStr";
    private static final String FLAVOR_PYTHON_BYTES = "PythonBytes";
    private static final String FLAVOR_ECMASCRIPT = "ECMAScript";

    private static final String FEATURE_SET_NAME = "FeatureSet";
    private static final String FEATURE_SET_TREGEX_JONI = "TRegexJoni";
    private static final String FEATURE_SET_JONI = "Joni";

    public static final RegexOptions DEFAULT = new RegexOptions(0, null, RegexFeatureSet.DEFAULT);

    private final int options;
    private final RegexFlavor flavor;
    private final RegexFeatureSet featureSet;

    private RegexOptions(int options, RegexFlavor flavor, RegexFeatureSet featureSet) {
        assert flavor == null || featureSet == RegexFeatureSet.DEFAULT;
        this.options = options;
        this.flavor = flavor;
        this.featureSet = featureSet;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @CompilerDirectives.TruffleBoundary
    public static RegexOptions parse(String optionsString) throws RegexSyntaxException {
        int options = 0;
        RegexFlavor flavor = null;
        RegexFeatureSet featureSet = RegexFeatureSet.DEFAULT;
        for (String propValue : optionsString.split(",")) {
            if (propValue.isEmpty()) {
                continue;
            }
            int eqlPos = propValue.indexOf('=');
            if (eqlPos < 0) {
                throw optionsSyntaxError(optionsString, propValue + " is not in form 'key=value'");
            }
            String key = propValue.substring(0, eqlPos);
            String value = propValue.substring(eqlPos + 1);
            switch (key) {
                case U180E_WHITESPACE_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, U180E_WHITESPACE);
                    break;
                case REGRESSION_TEST_MODE_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, REGRESSION_TEST_MODE);
                    break;
                case DUMP_AUTOMATA_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, DUMP_AUTOMATA);
                    break;
                case STEP_EXECUTION_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, STEP_EXECUTION);
                    break;
                case ALWAYS_EAGER_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, ALWAYS_EAGER);
                    break;
                case UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME:
                    options = parseBooleanOption(optionsString, options, key, value, UTF_16_EXPLODE_ASTRAL_SYMBOLS);
                    break;
                case FLAVOR_NAME:
                    flavor = parseFlavor(optionsString, value);
                    break;
                case FEATURE_SET_NAME:
                    featureSet = parseFeatureSet(optionsString, value);
                    break;
                default:
                    throw optionsSyntaxError(optionsString, "unexpected option " + key);
            }
        }
        return new RegexOptions(options, flavor, featureSet);
    }

    private static int parseBooleanOption(String optionsString, int options, String key, String value, int flag) throws RegexSyntaxException {
        if (value.equals("true")) {
            return options | flag;
        } else if (!value.equals("false")) {
            throw optionsSyntaxErrorUnexpectedValue(optionsString, key, value, "true", "false");
        }
        return options;
    }

    private static RegexFlavor parseFlavor(String optionsString, String value) throws RegexSyntaxException {
        switch (value) {
            case FLAVOR_PYTHON_STR:
                return PythonFlavor.STR_INSTANCE;
            case FLAVOR_PYTHON_BYTES:
                return PythonFlavor.BYTES_INSTANCE;
            case FLAVOR_ECMASCRIPT:
                return null;
            default:
                throw optionsSyntaxErrorUnexpectedValue(optionsString, FLAVOR_NAME, value, FLAVOR_PYTHON_STR, FLAVOR_PYTHON_BYTES, FLAVOR_ECMASCRIPT);
        }
    }

    private static RegexFeatureSet parseFeatureSet(String optionsString, String value) throws RegexSyntaxException {
        switch (value) {
            case FEATURE_SET_TREGEX_JONI:
                return RegexFeatureSet.DEFAULT;
            case FEATURE_SET_JONI:
                return RegexFeatureSet.DEFAULT;
            default:
                throw optionsSyntaxErrorUnexpectedValue(optionsString, FEATURE_SET_NAME, value, FEATURE_SET_TREGEX_JONI, FEATURE_SET_JONI);
        }
    }

    private static RegexSyntaxException optionsSyntaxErrorUnexpectedValue(String optionsString, String key, String value, String... expectedValues) {
        return optionsSyntaxError(optionsString, String.format("unexpected value '%s' for option '%s', expected one of %s", value, key, Arrays.toString(expectedValues)));
    }

    private static RegexSyntaxException optionsSyntaxError(String optionsString, String msg) {
        return new RegexSyntaxException(String.format("Invalid options syntax in '%s': %s", optionsString, msg));
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

    /**
     * Trace the execution of automata in JSON files.
     */
    public boolean isStepExecution() {
        return isBitSet(STEP_EXECUTION);
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

    public RegexFlavor getFlavor() {
        return flavor;
    }

    /**
     * The set of features that the regex compilers will be able to support. This is used to detect
     * unsupported regular expressions early, during their validation. This only applies to
     * ECMAScript regular expressions. Other flavors implement their own validation logic.
     */
    public RegexFeatureSet getFeatureSet() {
        return featureSet;
    }

    @Override
    public int hashCode() {
        int flavorHash = flavor == null ? 0 : flavor.hashCode();
        return options + 13 * flavorHash;
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
        return this.options == other.options && this.flavor == other.flavor;
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
        if (flavor == PythonFlavor.STR_INSTANCE) {
            sb.append(FLAVOR_NAME + "=" + FLAVOR_PYTHON_STR + ",");
        } else if (flavor == PythonFlavor.BYTES_INSTANCE) {
            sb.append(FLAVOR_NAME + "=" + FLAVOR_PYTHON_BYTES + ",");
        }
        sb.append(FEATURE_SET_NAME + "=Default");
        return sb.toString();
    }

    public static final class Builder {

        private int options;
        private RegexFlavor flavor;
        private RegexFeatureSet featureSet;

        private Builder() {
            this.options = 0;
            this.flavor = null;
            this.featureSet = RegexFeatureSet.DEFAULT;
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

        public Builder flavor(@SuppressWarnings("hiding") RegexFlavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public Builder featureSet(@SuppressWarnings("hiding") RegexFeatureSet featureSet) {
            this.featureSet = featureSet;
            return this;
        }

        public RegexOptions build() {
            return new RegexOptions(this.options, this.flavor, this.featureSet);
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
