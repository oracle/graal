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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
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
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.Loggers;

public class TRegexExecRootNode extends RegexExecRootNode implements RegexProfile.TracksRegexProfile {

    private static final LazyCaptureGroupRegexSearchNode LAZY_DFA_BAILED_OUT = new LazyCaptureGroupRegexSearchNode(null, null, null, null, null, null, null, null);
    private static final EagerCaptureGroupRegexSearchNode EAGER_DFA_BAILED_OUT = new EagerCaptureGroupRegexSearchNode(null);

    private final TRegexCompiler tRegexCompiler;
    private LazyCaptureGroupRegexSearchNode lazyDFANode;
    private LazyCaptureGroupRegexSearchNode regressTestNoSimpleCGLazyDFANode;
    private EagerCaptureGroupRegexSearchNode eagerDFANode;
    private NFARegexSearchNode nfaNode;
    private NFARegexSearchNode regressTestBacktrackingNode;
    private RegexProfile regexProfile;
    private final Encoding encoding;
    private final int numberOfCaptureGroups;
    private final boolean regressionTestMode;
    private final boolean backtrackingMode;
    private final ConditionProfile inputProfile = ConditionProfile.createBinaryProfile();

    @Child private RunRegexSearchNode runnerNode;

    public TRegexExecRootNode(TRegexCompiler tRegexCompiler, RegexAST ast, TRegexExecutorNode nfaExecutor) {
        super(tRegexCompiler.getLanguage(), ast.getSource(), ast.getFlags().isUnicode());
        this.tRegexCompiler = tRegexCompiler;
        this.encoding = ast.getEncoding();
        this.numberOfCaptureGroups = ast.getNumberOfCaptureGroups();
        this.nfaNode = new NFARegexSearchNode(createEntryNode(nfaExecutor));
        this.backtrackingMode = nfaExecutor instanceof TRegexBacktrackingNFAExecutorNode;
        this.regressionTestMode = !backtrackingMode && tRegexCompiler.getOptions().isRegressionTestMode();
        this.runnerNode = nfaNode;
        if (this.regressionTestMode) {
            regressTestBacktrackingNode = new NFARegexSearchNode(createEntryNode(tRegexCompiler.compileBacktrackingExecutor(((TRegexNFAExecutorNode) nfaNode.getExecutor()).getNFA())));
            switchToLazyDFA();
        }
    }

    @Override
    public final RegexResult execute(Object input, int fromIndex) {
        final RegexResult result = runnerNode.run(input, fromIndex, inputLength(input));
        assert !regressionTestMode || backtrackerProducesSameResult(input, fromIndex, result);
        assert !regressionTestMode || nfaProducesSameResult(input, fromIndex, result);
        assert !regressionTestMode || noSimpleCGLazyDFAProducesSameResult(input, fromIndex, result);
        assert !regressionTestMode || eagerAndLazyDFAProduceSameResult(input, fromIndex, result);
        assert validResult(input, fromIndex, result);
        if (CompilerDirectives.inInterpreter() && !backtrackingMode) {
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

    public Encoding getEncoding() {
        return encoding;
    }

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    public ConditionProfile getInputProfile() {
        return inputProfile;
    }

    private boolean validResult(Object input, int fromIndex, RegexResult result) {
        if (result == NoMatchResult.getInstance()) {
            return true;
        }
        if (result instanceof LazyResult) {
            ((LazyResult) result).debugForceEvaluation();
        }
        for (int i = 0; i < getNumberOfCaptureGroups(); i++) {
            int start = result.getStart(i);
            int end = result.getEnd(i);
            if (start > end || (Math.min(start, end) < 0 && Math.max(start, end) >= 0)) {
                Loggers.LOG_INTERNAL_ERRORS.severe(() -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nINVALID Result: %s", getSource(), input, fromIndex, result));
                return false;
            }
        }
        return true;
    }

    private boolean backtrackerProducesSameResult(Object input, int fromIndex, RegexResult result) {
        RegexResult btResult = regressTestBacktrackingNode.run(input, fromIndex, inputLength(input));
        if (resultsEqual(result, btResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(
                        () -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nBacktracker Result: %s\nDFA Result:         %s", getSource().toStringEscaped(), input, fromIndex, btResult, result));
        return false;
    }

    private boolean nfaProducesSameResult(Object input, int fromIndex, RegexResult result) {
        if (lazyDFANode == LAZY_DFA_BAILED_OUT) {
            return true;
        }
        assert !(runnerNode instanceof NFARegexSearchNode);
        RegexResult btResult = nfaNode.run(input, fromIndex, inputLength(input));
        if (resultsEqual(result, btResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(
                        () -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nNFA executor Result: %s\nDFA Result:         %s", getSource().toStringEscaped(), input, fromIndex, btResult, result));
        return false;
    }

    private boolean noSimpleCGLazyDFAProducesSameResult(Object input, int fromIndex, RegexResult result) {
        if (lazyDFANode == LAZY_DFA_BAILED_OUT || !lazyDFANode.isSimpleCG() || regressTestNoSimpleCGLazyDFANode == LAZY_DFA_BAILED_OUT) {
            return true;
        }
        assert !regressTestNoSimpleCGLazyDFANode.isSimpleCG();
        RegexResult noSimpleCGResult = regressTestNoSimpleCGLazyDFANode.run(input, fromIndex, inputLength(input));
        if (resultsEqual(result, noSimpleCGResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(
                        () -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nLazyDFA Result:    %s\nSimplCGDFA Result: %s", getSource().toStringEscaped(), input, fromIndex, noSimpleCGResult,
                                        result));
        return false;
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
        boolean equal = resultsEqual(lazyResult, eagerResult, getNumberOfCaptureGroups());
        if (!equal) {
            Loggers.LOG_INTERNAL_ERRORS.severe(() -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nLazy Result: %s\nEager Result: %s", getSource(), input, fromIndex, lazyResult, eagerResult));
        }
        return equal;
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

    private synchronized void switchToLazyDFA() {
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
            lazyDFANode = compileLazyDFA(true);
        }
        if (regressionTestMode && lazyDFANode != LAZY_DFA_BAILED_OUT && lazyDFANode.isSimpleCG()) {
            regressTestNoSimpleCGLazyDFANode = compileLazyDFA(false);
        }
    }

    private LazyCaptureGroupRegexSearchNode compileLazyDFA(boolean allowSimpleCG) {
        try {
            return tRegexCompiler.compileLazyDFAExecutor(((TRegexNFAExecutorNode) nfaNode.getExecutor()).getNFA(), this, allowSimpleCG);
        } catch (UnsupportedRegexException e) {
            Loggers.LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
            return LAZY_DFA_BAILED_OUT;
        }
    }

    private boolean canSwitchToEagerDFA() {
        return lazyDFANode.captureGroupEntryNode != null;
    }

    private void switchToEagerDFA(RegexProfile profile) {
        compileEagerDFA();
        if (eagerDFANode != EAGER_DFA_BAILED_OUT) {
            Loggers.LOG_SWITCH_TO_EAGER.fine(() -> "regex " + getSource() + ": switching to eager matching." + (profile == null ? "" : " profile: " + profile));
            runnerNode = insert(eagerDFANode);
        }
    }

    private void compileEagerDFA() {
        if (eagerDFANode == null) {
            try {
                TRegexDFAExecutorNode executorNode = tRegexCompiler.compileEagerDFAExecutor(getSource());
                eagerDFANode = new EagerCaptureGroupRegexSearchNode(createEntryNode(executorNode));
            } catch (UnsupportedRegexException e) {
                Loggers.LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
                eagerDFANode = EAGER_DFA_BAILED_OUT;
            }
        }
    }

    public TRegexExecutorEntryNode createEntryNode(TRegexExecutorNode executor) {
        if (executor == null) {
            return null;
        }
        executor.setRoot(this);
        if (executor instanceof TRegexBacktrackingNFAExecutorNode) {
            ((TRegexBacktrackingNFAExecutorNode) executor).initialize(this);
        }
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
            this.forwardEntryNode = forwardNode;
            this.flags = flags;
            this.preCalculatedResults = preCalculatedResults;
            this.backwardEntryNode = backwardNode;
            if (forwardNode == null) {
                // LAZY_DFA_BAILED_OUT
                assert language == null && source == null && flags == null && preCalculatedResults == null && backwardNode == null && captureGroupNode == null && rootNode == null;
                backwardCallTarget = null;
                captureGroupCallTarget = null;
                return;
            }
            if (backwardNode == null) {
                assert singlePreCalcResult() || getForwardExecutor().isAnchored() || getForwardExecutor().isSimpleCG();
                backwardCallTarget = null;
            } else {
                backwardCallTarget = Truffle.getRuntime().createCallTarget(
                                new RegexRootNode(language, new TRegexLazyFindStartRootNode(language, source, backwardNode)));
            }
            this.captureGroupEntryNode = insert(captureGroupNode);
            if (captureGroupNode == null) {
                captureGroupCallTarget = null;
            } else {
                captureGroupCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, new TRegexLazyCaptureGroupsRootNode(language, source, captureGroupNode, rootNode)));
            }
        }

        public TRegexDFAExecutorNode getForwardExecutor() {
            return forwardEntryNode == null ? null : (TRegexDFAExecutorNode) forwardEntryNode.getExecutor();
        }

        public TRegexDFAExecutorNode getBackwardExecutor() {
            return backwardEntryNode == null ? null : (TRegexDFAExecutorNode) backwardEntryNode.getExecutor();
        }

        public boolean isSimpleCG() {
            return (forwardEntryNode != null && getForwardExecutor().isSimpleCG()) || (backwardEntryNode != null && getBackwardExecutor().isSimpleCG());
        }

        @Override
        RegexResult run(Object input, int fromIndexArg, int inputLength) {
            if (backwardEntryNode != null && getBackwardExecutor().isAnchored()) {
                return executeBackwardAnchored(input, fromIndexArg, inputLength);
            } else {
                return executeForward(input, fromIndexArg, inputLength);
            }
        }

        private RegexResult executeForward(Object input, int fromIndexArg, int inputLength) {
            if (getForwardExecutor().isSimpleCG()) {
                int[] result = (int[]) forwardEntryNode.execute(input, fromIndexArg, fromIndexArg, inputLength);
                return result == null ? NoMatchResult.getInstance() : new SingleIndexArrayResult(result);
            }
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
                if (getForwardExecutor().isAnchored() || flags.isSticky()) {
                    return new SingleResult(fromIndexArg, end);
                }
                return new SingleResultLazyStart(input, fromIndexArg, end, backwardCallTarget);
            } else {
                if (preCalculatedResults != null) { // traceFinder
                    return new TraceFinderResult(input, fromIndexArg, end, backwardCallTarget, preCalculatedResults);
                } else {
                    if (getForwardExecutor().isAnchored() || (flags.isSticky() && getForwardExecutor().getPrefixLength() == 0)) {
                        return new LazyCaptureGroupsResult(input, fromIndexArg, end, null, captureGroupCallTarget);
                    }
                    return new LazyCaptureGroupsResult(input, fromIndexArg, end, backwardCallTarget, captureGroupCallTarget);
                }
            }
        }

        private RegexResult executeBackwardAnchored(Object input, int fromIndexArg, int inputLength) {
            if (getBackwardExecutor().isSimpleCG()) {
                int[] result = (int[]) backwardEntryNode.execute(input, fromIndexArg, inputLength, inputLength);
                return result == null ? NoMatchResult.getInstance() : new SingleIndexArrayResult(result);
            }
            final int backwardResult = (int) backwardEntryNode.execute(input, fromIndexArg, inputLength, inputLength);
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
            final int start = backwardResult;
            if (flags.isSticky() && start != fromIndexArg) {
                return NoMatchResult.getInstance();
            }
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromStart(start);
            }
            if (getForwardExecutor().isSimpleCG()) {
                int[] result = (int[]) forwardEntryNode.execute(input, fromIndexArg, start, inputLength);
                assert result != null;
                return new SingleIndexArrayResult(result);
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
            this.entryNode = entryNode;
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
            this.entryNode = entryNode;
        }

        public TRegexExecutorNode getExecutor() {
            return entryNode.getExecutor();
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
