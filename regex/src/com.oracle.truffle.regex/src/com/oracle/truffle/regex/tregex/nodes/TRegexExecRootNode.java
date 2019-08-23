/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.nodes;

import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_BAILOUT_MESSAGES;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_INTERNAL_ERRORS;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_SWITCH_TO_EAGER;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexProfile;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;
import com.oracle.truffle.regex.result.LazyResult;
import com.oracle.truffle.regex.result.NoMatchResult;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.result.SingleIndexArrayResult;
import com.oracle.truffle.regex.result.SingleResult;
import com.oracle.truffle.regex.result.SingleResultLazyStart;
import com.oracle.truffle.regex.result.TraceFinderResult;
import com.oracle.truffle.regex.tregex.TRegexCompiler;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;

public class TRegexExecRootNode extends RegexExecRootNode implements RegexProfile.TracksRegexProfile {

    private static final LazyCaptureGroupRegexSearchNode LAZY_DFA_BAILED_OUT = new LazyCaptureGroupRegexSearchNode(null, null, null, null, null, null, null, null);
    private static final EagerCaptureGroupRegexSearchNode EAGER_DFA_BAILED_OUT = new EagerCaptureGroupRegexSearchNode(null);

    private final TRegexCompiler tRegexCompiler;
    private LazyCaptureGroupRegexSearchNode lazyDFANode;
    private EagerCaptureGroupRegexSearchNode eagerDFANode;
    private NFARegexSearchNode nfaNode;
    private RegexProfile regexProfile;
    private final boolean regressionTestMode;

    @Child private RunRegexSearchNode runnerNode;

    public TRegexExecRootNode(RegexLanguage language, TRegexCompiler tRegexCompiler, RegexSource source, RegexFlags flags, boolean regressionTestMode, TRegexExecutorNode backTrackingExecutor) {
        super(language, source, flags.isUnicode());
        this.tRegexCompiler = tRegexCompiler;
        this.regressionTestMode = regressionTestMode;
        this.nfaNode = new NFARegexSearchNode(createEntryNode(backTrackingExecutor));
        this.runnerNode = insert(nfaNode);
        if (this.regressionTestMode) {
            switchToLazyDFA();
        }
    }

    @Override
    public final RegexResult execute(Object input, int fromIndex) {
        final RegexResult result = runnerNode.run(input, fromIndex, inputLength(input));
        assert !regressionTestMode || nfaProducesSameResult(input, fromIndex, result);
        assert !regressionTestMode || eagerAndLazyDFAProduceSameResult(input, fromIndex, result);
        if (CompilerDirectives.inInterpreter()) {
            RegexProfile profile = getRegexProfile();
            if (lazyDFANode == null) {
                assert !regressionTestMode;
                profile.incCalls();
                if (profile.shouldGenerateDFA()) {
                    switchToLazyDFA();
                    profile.resetCalls();
                    // free the NFA for garbage collection
                    nfaNode = null;
                }
            } else if (canSwitchToEagerDFA() && runnerNode == lazyDFANode) {
                if (profile.atEvaluationTripPoint() && profile.shouldUseEagerMatching()) {
                    switchToEagerDFA(profile);
                }
                profile.incCalls();
                if (result != NoMatchResult.getInstance()) {
                    profile.incMatches();
                }
            }
        }
        return result;
    }

    private boolean nfaProducesSameResult(Object input, int fromIndex, RegexResult result) {
        assert !(runnerNode instanceof NFARegexSearchNode);
        RegexResult btResult = nfaNode.run(input, fromIndex, inputLength(input));
        if (resultsEqual(result, btResult, ((TRegexNFAExecutorNode) nfaNode.entryNode.getExecutor()).getNumberOfCaptureGroups())) {
            return true;
        }
        LOG_INTERNAL_ERRORS.severe(() -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nBacktracker Result: %s\nDFA Result:         %s", getSource(), input, fromIndex, btResult, result));
        return false;
    }

    private static boolean resultsEqual(RegexResult a, RegexResult b, int numberOfCaptureGroups) {
        if (a == NoMatchResult.getInstance()) {
            return b == NoMatchResult.getInstance();
        }
        if (a instanceof LazyResult) {
            ((LazyResult) a).debugForceEvaluation();
        }
        if (b instanceof LazyResult) {
            ((LazyResult) b).debugForceEvaluation();
        }
        for (int i = 0; i < numberOfCaptureGroups; i++) {
            if (a.getStart(i) != b.getStart(i) || a.getEnd(i) != b.getEnd(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RegexProfile getRegexProfile() {
        if (regexProfile == null) {
            regexProfile = new RegexProfile();
        }
        return regexProfile;
    }

    private void switchToLazyDFA() {
        compileLazyDFA();
        if (lazyDFANode != LAZY_DFA_BAILED_OUT) {
            runnerNode = insert(lazyDFANode);
            if (canSwitchToEagerDFA()) {
                if (regressionTestMode) {
                    compileEagerDFA();
                }
                if (tRegexCompiler.getOptions().isAlwaysEager()) {
                    switchToEagerDFA(null);
                }
            }
        }
    }

    private void compileLazyDFA() {
        if (lazyDFANode == null) {
            try {
                lazyDFANode = tRegexCompiler.compileLazyDFAExecutor(((TRegexNFAExecutorNode) nfaNode.entryNode.getExecutor()).getNFA(), this);
            } catch (UnsupportedRegexException e) {
                LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
                lazyDFANode = LAZY_DFA_BAILED_OUT;
            }
        }
    }

    private boolean canSwitchToEagerDFA() {
        return lazyDFANode.captureGroupEntryNode != null;
    }

    private void switchToEagerDFA(RegexProfile profile) {
        compileEagerDFA();
        if (eagerDFANode != EAGER_DFA_BAILED_OUT) {
            LOG_SWITCH_TO_EAGER.fine(() -> "regex " + getSource() + ": switching to eager matching." + (profile == null ? "" : " profile: " + profile));
            runnerNode = insert(eagerDFANode);
        }
    }

    private void compileEagerDFA() {
        if (eagerDFANode == null) {
            try {
                TRegexDFAExecutorNode executorNode = tRegexCompiler.compileEagerDFAExecutor(getSource());
                eagerDFANode = new EagerCaptureGroupRegexSearchNode(createEntryNode(executorNode));
            } catch (UnsupportedRegexException e) {
                LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
                eagerDFANode = EAGER_DFA_BAILED_OUT;
            }
        }
    }

    private boolean eagerAndLazyDFAProduceSameResult(Object input, int fromIndex, RegexResult resultOfCurrentSearchNode) {
        if (lazyDFANode.captureGroupEntryNode == null || eagerDFANode == EAGER_DFA_BAILED_OUT) {
            return true;
        }
        RegexResult lazyResult;
        RegexResult eagerResult;
        if (runnerNode == lazyDFANode) {
            lazyResult = resultOfCurrentSearchNode;
            eagerResult = eagerDFANode.run(input, fromIndex, inputLength(input));
        } else {
            lazyResult = lazyDFANode.run(input, fromIndex, inputLength(input));
            eagerResult = resultOfCurrentSearchNode;
        }
        boolean equal;
        if (lazyResult == NoMatchResult.getInstance()) {
            equal = eagerResult == NoMatchResult.getInstance();
        } else {
            ((LazyCaptureGroupsResult) lazyResult).debugForceEvaluation();
            equal = Arrays.equals(((LazyCaptureGroupsResult) lazyResult).getResult(), ((LazyCaptureGroupsResult) eagerResult).getResult());
        }
        if (!equal) {
            LOG_INTERNAL_ERRORS.severe(() -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nLazy Result: %s\nEager Result: %s", getSource(), input, fromIndex, lazyResult, eagerResult));
        }
        return equal;
    }

    public TRegexExecutorEntryNode createEntryNode(TRegexExecutorNode executor) {
        if (executor == null) {
            return null;
        }
        executor.setRoot(this);
        return TRegexExecutorEntryNode.create(executor);
    }

    @Override
    public final String getEngineLabel() {
        return "TRegex fwd";
    }

    abstract static class RunRegexSearchNode extends Node {

        abstract RegexResult run(Object input, int fromIndexArg, int inputLength);
    }

    public static final class LazyCaptureGroupRegexSearchNode extends RunRegexSearchNode {

        private final RegexFlags flags;
        @CompilationFinal(dimensions = 1) private final PreCalculatedResultFactory[] preCalculatedResults;

        @Child private TRegexExecutorEntryNode forwardEntryNode;
        @Child private TRegexExecutorEntryNode backwardEntryNode;
        @Child private TRegexExecutorEntryNode captureGroupEntryNode;

        private final CallTarget backwardCallTarget;
        private final CallTarget captureGroupCallTarget;

        public LazyCaptureGroupRegexSearchNode(RegexLanguage language,
                        RegexSource source,
                        RegexFlags flags,
                        PreCalculatedResultFactory[] preCalculatedResults,
                        TRegexExecutorEntryNode forwardNode,
                        TRegexExecutorEntryNode backwardNode,
                        TRegexExecutorEntryNode captureGroupNode,
                        TRegexExecRootNode rootNode) {
            this.forwardEntryNode = insert(forwardNode);
            this.flags = flags;
            this.preCalculatedResults = preCalculatedResults;
            this.backwardEntryNode = insert(backwardNode);
            if (backwardNode == null) {
                assert singlePreCalcResult() || forwardNode == null;
                backwardCallTarget = null;
            } else {
                backwardCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language,
                                new TRegexLazyFindStartRootNode(language, source, ((TRegexDFAExecutorNode) forwardNode.getExecutor()).getPrefixLength(), backwardNode)));
            }
            this.captureGroupEntryNode = insert(captureGroupNode);
            if (captureGroupNode == null) {
                captureGroupCallTarget = null;
            } else {
                captureGroupCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, new TRegexLazyCaptureGroupsRootNode(language, source, captureGroupNode, rootNode)));
            }
        }

        @Override
        RegexResult run(Object input, int fromIndexArg, int inputLength) {
            if (backwardEntryNode != null && ((TRegexDFAExecutorNode) backwardEntryNode.getExecutor()).isAnchored()) {
                return executeBackwardAnchored(input, fromIndexArg, inputLength);
            } else {
                return executeForward(input, fromIndexArg, inputLength);
            }
        }

        private RegexResult executeForward(Object input, int fromIndexArg, int inputLength) {
            final int end = (int) forwardEntryNode.execute(input, fromIndexArg, fromIndexArg, inputLength);
            if (end == TRegexDFAExecutorNode.NO_MATCH) {
                return NoMatchResult.getInstance();
            }
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromEnd(end);
            }
            if (preCalculatedResults == null && captureGroupEntryNode == null) {
                if (end == fromIndexArg) { // zero-length match
                    return new SingleResult(end, end);
                }
                if (((TRegexDFAExecutorNode) forwardEntryNode.getExecutor()).isAnchored() || flags.isSticky()) {
                    return new SingleResult(fromIndexArg, end);
                }
                return new SingleResultLazyStart(input, fromIndexArg, end, backwardCallTarget);
            } else {
                if (preCalculatedResults != null) { // traceFinder
                    return new TraceFinderResult(input, fromIndexArg, end, backwardCallTarget, preCalculatedResults);
                } else {
                    if (((TRegexDFAExecutorNode) forwardEntryNode.getExecutor()).isAnchored() ||
                                    (flags.isSticky() && ((TRegexDFAExecutorNode) forwardEntryNode.getExecutor()).getPrefixLength() == 0)) {
                        return new LazyCaptureGroupsResult(input, fromIndexArg, end, null, captureGroupCallTarget);
                    }
                    return new LazyCaptureGroupsResult(input, fromIndexArg, end, backwardCallTarget, captureGroupCallTarget);
                }
            }
        }

        private RegexResult executeBackwardAnchored(Object input, int fromIndexArg, int inputLength) {
            final int backwardResult = (int) backwardEntryNode.execute(input, fromIndexArg, inputLength - 1,
                            Math.max(-1, fromIndexArg - 1 - ((TRegexDFAExecutorNode) forwardEntryNode.getExecutor()).getPrefixLength()));
            if (backwardResult == TRegexDFAExecutorNode.NO_MATCH) {
                return NoMatchResult.getInstance();
            }
            if (multiplePreCalcResults()) { // traceFinder
                RegexResult preCalcResult = preCalculatedResults[backwardResult].createFromEnd(inputLength);
                if (flags.isSticky() && preCalcResult.getStart(0) != fromIndexArg) {
                    return NoMatchResult.getInstance();
                }
                return preCalcResult;
            }
            final int start = backwardResult + 1;
            if (flags.isSticky() && start != fromIndexArg) {
                return NoMatchResult.getInstance();
            }
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromStart(start);
            }
            if (captureGroupEntryNode != null) {
                return new LazyCaptureGroupsResult(input, start, inputLength, null, captureGroupCallTarget);
            }
            return new SingleResult(start, inputLength);
        }

        private boolean singlePreCalcResult() {
            return preCalculatedResults != null && preCalculatedResults.length == 1;
        }

        private boolean multiplePreCalcResults() {
            return preCalculatedResults != null && preCalculatedResults.length > 1;
        }
    }

    static final class EagerCaptureGroupRegexSearchNode extends RunRegexSearchNode {

        @Child private TRegexExecutorEntryNode entryNode;

        EagerCaptureGroupRegexSearchNode(TRegexExecutorEntryNode entryNode) {
            this.entryNode = insert(entryNode);
        }

        @Override
        RegexResult run(Object input, int fromIndexArg, int inputLength) {
            final int[] resultArray = (int[]) entryNode.execute(input, fromIndexArg, fromIndexArg, inputLength);
            if (resultArray == null) {
                return NoMatchResult.getInstance();
            }
            return new LazyCaptureGroupsResult(input, resultArray);
        }
    }

    static final class NFARegexSearchNode extends RunRegexSearchNode {

        @Child private TRegexExecutorEntryNode entryNode;

        NFARegexSearchNode(TRegexExecutorEntryNode entryNode) {
            this.entryNode = insert(entryNode);
        }

        @Override
        RegexResult run(Object input, int fromIndexArg, int inputLength) {
            final int[] resultArray = (int[]) entryNode.execute(input, fromIndexArg, fromIndexArg, inputLength);
            if (resultArray == null) {
                return NoMatchResult.getInstance();
            }
            return new SingleIndexArrayResult(resultArray);
        }
    }
}
