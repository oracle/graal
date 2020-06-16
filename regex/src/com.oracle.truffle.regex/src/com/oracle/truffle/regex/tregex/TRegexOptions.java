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
package com.oracle.truffle.regex.tregex;

import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.ASTStep;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

public class TRegexOptions {

    /**
     * Number of regex searches done without generating a DFA for a given regular expression. When
     * this threshold is reached, TRegex tries to generate a fully expanded DFA to speed up further
     * searches. This threshold is only checked in interpreter mode, so it should be sufficiently
     * smaller than the Graal compilation threshold!
     */
    public static final int TRegexGenerateDFAThreshold = 100;

    /**
     * Try to pre-calculate results of tree-like expressions (see {@link NFATraceFinderGenerator}).
     * A regular expression is considered tree-like if it does not contain infinite loops (+ or *).
     * This option will increase performance at the cost of startup time and memory usage.
     */
    public static final boolean TRegexEnableTraceFinder = true;

    /**
     * Maximum number of pre-calculated results per TraceFinder DFA. This number must not be higher
     * than 254, because we compress the result indices to {@code byte} in
     * {@link TraceFinderDFAStateNode}, with 255 being reserved for "no result"!
     */
    public static final int TRegexTraceFinderMaxNumberOfResults = 254;

    /**
     * Try to make control flow through DFAs reducible by node splitting (see {@link DFANodeSplit}).
     * This option will increase performance at the cost of startup time and memory usage.
     */
    public static final boolean TRegexEnableNodeSplitter = false;

    /**
     * Maximum size of a DFA after being altered by {@link DFANodeSplit}.
     */
    public static final int TRegexMaxDFASizeAfterNodeSplitting = 4_000;

    /**
     * Minimum number of ranges that have the same high byte to convert into a bit set in a
     * {@link com.oracle.truffle.regex.tregex.matchers.RangeListMatcher} or
     * {@link com.oracle.truffle.regex.tregex.matchers.RangeTreeMatcher}. The threshold value must
     * be greater than 1. Example:
     *
     * <pre>
     *     [\u1000-\u1020], [\u1030-\u1040], [\u1050-\u1060]
     *     are three ranges that have the same high byte (0x10).
     *     if TRegexRangeToBitSetConversionThreshold is <= 3, they will be converted to a
     *     bit set if they appear in a RangeList or RangeTree matcher.
     * </pre>
     */
    public static final int TRegexRangeToBitSetConversionThreshold = 3;

    /**
     * Bailout threshold for number of nodes in the parser tree ({@link RegexAST} generated by
     * {@link RegexParser}).
     */
    public static final int TRegexParserTreeMaxSize = Integer.MAX_VALUE;

    /**
     * Bailout threshold for number of {@link Sequence}s in a {@link Group}.
     */
    public static final int TRegexParserTreeMaxNumberOfSequencesInGroup = Short.MAX_VALUE;

    /**
     * Bailout threshold for number of {@link Term}s in a {@link Sequence}.
     */
    public static final int TRegexParserTreeMaxNumberOfTermsInSequence = Short.MAX_VALUE;

    /**
     * Parser trees bigger than this setting will not be considered for DFA generation. The current
     * setting is based on run times of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxParseTreeSizeForDFA = 4_000;

    /**
     * Bailout threshold for number of nodes in the NFA ({@link NFA} generated by
     * {@link NFAGenerator}). This number must not be higher than {@link Short#MAX_VALUE}, because
     * we use {@code short} values for indexing NFA nodes. The current setting is based on run times
     * of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxNFASize = 3_500;

    /**
     * Bailout threshold for number of ASTSuccessor instances allowed in a single {@link ASTStep}.
     * It is possible to construct patterns where the number of NFA transitions grows exponentially.
     * {@link ASTStep} is an intermediate data structure between the AST and the NFA, which is
     * filled eagerly and can cause an {@link OutOfMemoryError} if not capped. Since ASTSuccessors
     * roughly correspond to NFA transitions, the cap has been set to the maximum number of NFA
     * transitions we allow in a single NFA.
     */
    public static final int TRegexMaxNumberOfASTSuccessorsInOneASTStep = Short.MAX_VALUE;

    /**
     * Bailout threshold for number of nodes in the DFA ({@link TRegexDFAExecutorNode} generated by
     * {@link DFAGenerator}). This number must not be higher than {@link Short#MAX_VALUE}, because
     * we use {@code short} values for indexing DFA nodes. The current setting is based on run times
     * of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxDFASize = 2_400;

    /**
     * Maximum number of entries in the global compilation cache in
     * {@link com.oracle.truffle.regex.RegexLanguage}.
     */
    public static final int RegexMaxCacheSize = 1_000;

    /**
     * The parser will try to unroll bounded quantifiers on single character classes up to this
     * limit.
     */
    public static final int TRegexQuantifierUnrollThresholdSingleCC = 20;

    /**
     * The parser will try to unroll bounded quantifiers on groups up to this limit.
     */
    public static final int TRegexQuantifierUnrollThresholdGroup = 5;

    /**
     * Bailout threshold for number of capture groups.
     */
    public static final int TRegexMaxNumberOfCaptureGroups = Short.MAX_VALUE;

    /**
     * Bailout threshold for number of capture groups in the DFA generator. This number must not be
     * higher than 127, because we compress capture group boundary indices to {@code byte} in
     * {@link DFACaptureGroupPartialTransition}!
     */
    public static final int TRegexMaxNumberOfCaptureGroupsForDFA = Byte.MAX_VALUE;

    /**
     * Maximum number of NFA states involved in one DFA transition. This number must not be higher
     * than 255, because the maximum number of NFA states in one DFA transition determines the
     * number of simultaneously tracked result sets (arrays) in capture group tracking mode, which
     * are accessed over byte indices in {@link DFACaptureGroupPartialTransition}.
     */
    public static final int TRegexMaxNumberOfNFAStatesInOneDFATransition = 255;

    static {
        assert TRegexTraceFinderMaxNumberOfResults <= 254;
        assert TRegexParserTreeMaxSize <= Integer.MAX_VALUE;
        assert TRegexParserTreeMaxNumberOfSequencesInGroup <= Short.MAX_VALUE;
        assert TRegexParserTreeMaxNumberOfTermsInSequence <= Short.MAX_VALUE;
        assert TRegexMaxNFASize <= Short.MAX_VALUE;
        assert TRegexMaxDFASize <= Short.MAX_VALUE;
        assert TRegexMaxDFASizeAfterNodeSplitting <= Short.MAX_VALUE;
        assert TRegexMaxNumberOfCaptureGroupsForDFA <= 127;
        assert TRegexMaxNumberOfNFAStatesInOneDFATransition <= 255;
        assert TRegexRangeToBitSetConversionThreshold > 1;
    }
}
