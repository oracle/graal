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
package com.oracle.truffle.regex.tregex.nodes;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexBodyNode;
import com.oracle.truffle.regex.RegexExecNode;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexProfile;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.TRegexCompiler;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexTraceFinderRootNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.util.Loggers;

public class TRegexExecNode extends RegexExecNode implements RegexProfile.TracksRegexProfile {

    private static final LazyCaptureGroupRegexSearchNode LAZY_DFA_BAILED_OUT = new LazyCaptureGroupRegexSearchNode(null, null, null, null, null, null, null, null);
    private static final EagerCaptureGroupRegexSearchNode EAGER_DFA_BAILED_OUT = new EagerCaptureGroupRegexSearchNode(null);

    @CompilationFinal private LazyCaptureGroupRegexSearchNode lazyDFANode;
    private LazyCaptureGroupRegexSearchNode regressTestNoSimpleCGLazyDFANode;
    private EagerCaptureGroupRegexSearchNode eagerDFANode;
    private NFARegexSearchNode nfaNode;
    private NFARegexSearchNode regressTestBacktrackingNode;
    private RegexProfile regexProfile;
    private final int numberOfCaptureGroups;
    private final boolean regressionTestMode;
    private final boolean backtrackingMode;
    private final boolean sticky;
    private final Lock optimizeLock;

    @Child private RunRegexSearchNode runnerNode;

    public TRegexExecNode(RegexAST ast, TRegexExecutorNode nfaExecutor) {
        super(ast.getLanguage(), ast.getSource(), ast.getFlags().isEitherUnicode());
        this.numberOfCaptureGroups = ast.getNumberOfCaptureGroups();
        this.nfaNode = new NFARegexSearchNode(createEntryNode(nfaExecutor));
        this.backtrackingMode = nfaExecutor instanceof TRegexBacktrackingNFAExecutorNode;
        this.regressionTestMode = !backtrackingMode && ast.getOptions().isRegressionTestMode();
        this.sticky = ast.getFlags().isSticky();
        this.optimizeLock = new ReentrantLock();
        this.runnerNode = insert(nfaNode);
        if (this.regressionTestMode || !backtrackingMode && ast.getOptions().isGenerateDFAImmediately()) {
            switchToLazyDFA();
        }
        if (this.regressionTestMode) {
            regressTestBacktrackingNode = new NFARegexSearchNode(
                            createEntryNode(TRegexCompiler.compileBacktrackingExecutor(getRegexLanguage(), ((TRegexNFAExecutorNode) nfaNode.getExecutor().unwrap()).getNFA())));
        }
    }

    @Override
    public final RegexResult execute(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {

        if (CompilerDirectives.inInterpreter() && !backtrackingMode) {
            RegexProfile profile = getRegexProfile();
            if (lazyDFANode == null) {
                assert !regressionTestMode;
                if (profile.shouldGenerateDFA(maxIndex - fromIndex) && optimizeLock.tryLock()) {
                    try {
                        switchToLazyDFA();
                        profile.resetCalls();
                        // free the NFA for garbage collection
                        nfaNode = null;
                    } finally {
                        optimizeLock.unlock();
                    }
                }
            } else if (canSwitchToEagerDFA() && runnerNode == lazyDFANode) {
                assert lazyDFANode.forwardEntryNode != null;
                if (profile.atEvaluationTripPoint() && profile.shouldUseEagerMatching() && optimizeLock.tryLock()) {
                    try {
                        switchToEagerDFA(profile);
                    } finally {
                        optimizeLock.unlock();
                    }
                }
            }
        }

        final RegexResult result;
        result = runnerNode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
        assert !sticky || source.getOptions().isBooleanMatch() || result == RegexResult.getNoMatchInstance() || RegexResult.RegexResultGetStartNode.getUncached().execute(result, 0) == fromIndex;
        assert validResult(input, fromIndex, maxIndex, regionFrom, regionTo, result);
        if (regressionTestMode) {
            if (!(backtrackerProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            nfaProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            noSimpleCGLazyDFAProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            (source.getOptions().isBooleanMatch() || eagerAndLazyDFAProduceSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result)))) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError("Inconsistent results between different matching modes");
            }
        }

        if (CompilerDirectives.inInterpreter() && !backtrackingMode) {
            RegexProfile profile = getRegexProfile();
            if (lazyDFANode == null) {
                profile.incCalls();
                profile.incProcessedCharacters(charactersProcessedDuringSearch(result, fromIndex, maxIndex));
            } else if (canSwitchToEagerDFA() && runnerNode == lazyDFANode) {
                profile.incCalls();
                if (result != RegexResult.getNoMatchInstance()) {
                    profile.incMatches();
                }
            }
        }

        return result;
    }

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    @Override
    public boolean isBacktracking() {
        return backtrackingMode;
    }

    @Override
    public boolean isNFA() {
        return runnerNode instanceof NFARegexSearchNode;
    }

    private static int charactersProcessedDuringSearch(RegexResult result, int fromIndex, int maxIndex) {
        if (result == RegexResult.getNoMatchInstance()) {
            return maxIndex - fromIndex;
        } else {
            return result.getEnd(0) + 1 - fromIndex;
        }
    }

    private boolean validResult(Object input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
        if (result == RegexResult.getNoMatchInstance() || result == RegexResult.getBooleanMatchInstance()) {
            return true;
        }
        result.debugForceEvaluation();
        for (int i = 0; i < getNumberOfCaptureGroups(); i++) {
            int start = result.getStart(i);
            int end = result.getEnd(i);
            if (start > end || (start < 0 && end >= 0)) {
                Loggers.LOG_INTERNAL_ERRORS.severe(() -> String.format("Regex: %s\nInput: %s\nfromIndex: %d\nmaxIndex: %d\nregionFrom: %d\nregionTo: %d\nINVALID Result: %s\n",
                                getSource().toStringEscaped(), input, fromIndex, maxIndex, regionFrom, regionTo, result));
                return false;
            }
        }
        return true;
    }

    private RegexResult regressionTestRun(VirtualFrame frame, RunRegexSearchNode node, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
        RunRegexSearchNode old = runnerNode;
        runnerNode = insert(node);
        RegexResult result = runnerNode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
        runnerNode = insert(old);
        return result;
    }

    private boolean backtrackerProducesSameResult(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
        RegexResult btResult = regressionTestRun(frame, regressTestBacktrackingNode, input, fromIndex, maxIndex, regionFrom, regionTo);
        if (resultsEqual(result, btResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "Backtracker", btResult, "DFA", result));
        return false;
    }

    private boolean nfaProducesSameResult(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
        if (lazyDFANode == LAZY_DFA_BAILED_OUT) {
            return true;
        }
        assert !(runnerNode instanceof NFARegexSearchNode);
        RegexResult nfaResult = regressionTestRun(frame, nfaNode, input, fromIndex, maxIndex, regionFrom, regionTo);
        if (resultsEqual(result, nfaResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "NFA", nfaResult, "DFA", result));
        return false;
    }

    private boolean noSimpleCGLazyDFAProducesSameResult(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
        if (lazyDFANode == LAZY_DFA_BAILED_OUT || !lazyDFANode.isSimpleCG() || regressTestNoSimpleCGLazyDFANode == LAZY_DFA_BAILED_OUT) {
            return true;
        }
        assert !regressTestNoSimpleCGLazyDFANode.isSimpleCG();
        RegexResult noSimpleCGResult = regressionTestRun(frame, regressTestNoSimpleCGLazyDFANode, input, fromIndex, maxIndex, regionFrom, regionTo);
        if (resultsEqual(result, noSimpleCGResult, getNumberOfCaptureGroups())) {
            return true;
        }
        Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "LazyDFA", noSimpleCGResult, "SimplCGDFA", result));
        return false;
    }

    private boolean eagerAndLazyDFAProduceSameResult(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult resultOfCurrentSearchNode) {
        if (lazyDFANode.captureGroupEntryNode == null || eagerDFANode == EAGER_DFA_BAILED_OUT) {
            return true;
        }
        RegexResult lazyResult;
        RegexResult eagerResult;
        if (runnerNode == lazyDFANode) {
            lazyResult = resultOfCurrentSearchNode;
            eagerResult = regressionTestRun(frame, eagerDFANode, input, fromIndex, maxIndex, regionFrom, regionTo);
        } else {
            lazyResult = regressionTestRun(frame, lazyDFANode, input, fromIndex, maxIndex, regionFrom, regionTo);
            eagerResult = resultOfCurrentSearchNode;
        }
        boolean equal = resultsEqual(lazyResult, eagerResult, getNumberOfCaptureGroups());
        if (!equal) {
            Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "Lazy", lazyResult, "Eager", eagerResult));
        }
        return equal;
    }

    private String regressionTestErrorMsg(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, String nameA, RegexResult a, String nameB, RegexResult b) {
        return String.format("Regex: %s\nInput: %s\nfromIndex: %d\nmaxIndex: %d\nregionFrom: %d\nregionTo: %d\n%s Result: %s\n%s Result: %s",
                        getSource().toStringEscaped(), input, fromIndex, maxIndex, regionFrom, regionTo, nameA, a, nameB, b);
    }

    private static boolean resultsEqual(RegexResult a, RegexResult b, int numberOfCaptureGroups) {
        if (a == RegexResult.getNoMatchInstance()) {
            return b == RegexResult.getNoMatchInstance();
        } else {
            a.debugForceEvaluation();
        }
        if (b == RegexResult.getNoMatchInstance()) {
            return a == RegexResult.getNoMatchInstance();
        } else {
            b.debugForceEvaluation();
        }
        if (a == RegexResult.getBooleanMatchInstance()) {
            return b != RegexResult.getNoMatchInstance();
        }
        if (b == RegexResult.getBooleanMatchInstance()) {
            return true;
        }
        for (int i = 0; i < numberOfCaptureGroups; i++) {
            if (a.getStart(i) != b.getStart(i) || a.getEnd(i) != b.getEnd(i)) {
                return false;
            }
        }
        if (a.getLastGroup() != b.getLastGroup()) {
            return false;
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
                if (getSource().getOptions().isAlwaysEager()) {
                    switchToEagerDFA(null);
                }
            }
        } else if (!backtrackingMode) {
            TRegexNFAExecutorNode nfaExecutorNode = (TRegexNFAExecutorNode) ((NFARegexSearchNode) runnerNode).getExecutor().unwrap();
            nfaExecutorNode.notifyDfaGeneratorBailedOut();
        }
    }

    private void compileLazyDFA() {
        if (lazyDFANode == null) {
            lazyDFANode = compileLazyDFA(true);
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        if (regressionTestMode && lazyDFANode != LAZY_DFA_BAILED_OUT && lazyDFANode.isSimpleCG()) {
            regressTestNoSimpleCGLazyDFANode = compileLazyDFA(false);
        }
    }

    private LazyCaptureGroupRegexSearchNode compileLazyDFA(boolean allowSimpleCG) {
        try {
            return TRegexCompiler.compileLazyDFAExecutor(getRegexLanguage(), new NFA(((TRegexNFAExecutorNode) nfaNode.getExecutor().unwrap()).getNFA()), this, allowSimpleCG);
        } catch (UnsupportedRegexException e) {
            Loggers.LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
            return LAZY_DFA_BAILED_OUT;
        }
    }

    private boolean canSwitchToEagerDFA() {
        return !source.getOptions().isBooleanMatch() && lazyDFANode.captureGroupEntryNode != null;
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
                assert !getSource().getOptions().isBooleanMatch();
                TRegexDFAExecutorNode executorNode = TRegexCompiler.compileEagerDFAExecutor(getRegexLanguage(), getSource());
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
        return TRegexExecutorEntryNode.create(getRegexLanguage(), executor);
    }

    @Override
    public final String getEngineLabel() {
        return "TRegex fwd";
    }

    public abstract static class RunRegexSearchNode extends Node {

        protected abstract RegexResult run(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo);
    }

    public static final class LazyCaptureGroupRegexSearchNode extends RunRegexSearchNode {

        private final RegexFlags flags;
        private final boolean booleanMatch;
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
                        TRegexExecNode rootNode) {
            this.forwardEntryNode = forwardNode;
            this.flags = flags;
            this.booleanMatch = source != null && source.getOptions().isBooleanMatch();
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
                assert singlePreCalcResult() || getForwardExecutor().isAnchored() || getForwardExecutor().isSimpleCG() || booleanMatch;
                backwardCallTarget = null;
            } else {
                final RegexBodyNode bodyNode;
                if (preCalculatedResults != null) {
                    bodyNode = new TRegexTraceFinderRootNode(language, source, preCalculatedResults, backwardNode);
                } else {
                    bodyNode = new TRegexLazyFindStartRootNode(language, source, backwardNode, captureGroupNode == null);
                }
                backwardCallTarget = new RegexRootNode(language, bodyNode).getCallTarget();
            }
            this.captureGroupEntryNode = insert(captureGroupNode);
            if (captureGroupNode == null) {
                captureGroupCallTarget = null;
            } else {
                final CallTarget findStartCallTarget;
                if ((getForwardExecutor().isAnchored() || (flags.isSticky() && getForwardExecutor().getPrefixLength() == 0)) ||
                                (backwardEntryNode != null && getBackwardExecutor().isAnchored()) || getForwardExecutor().canFindStart()) {
                    findStartCallTarget = null;
                } else {
                    findStartCallTarget = backwardCallTarget;
                }
                captureGroupCallTarget = new RegexRootNode(language, new TRegexLazyCaptureGroupsRootNode(language, source, captureGroupNode, rootNode, findStartCallTarget)).getCallTarget();
            }
        }

        public TRegexDFAExecutorNode getForwardExecutor() {
            return forwardEntryNode == null ? null : (TRegexDFAExecutorNode) forwardEntryNode.getExecutor().unwrap();
        }

        public TRegexDFAExecutorNode getBackwardExecutor() {
            return backwardEntryNode == null ? null : (TRegexDFAExecutorNode) backwardEntryNode.getExecutor().unwrap();
        }

        public boolean isSimpleCG() {
            return (forwardEntryNode != null && getForwardExecutor().isSimpleCG()) || (backwardEntryNode != null && getBackwardExecutor().isSimpleCG());
        }

        @Override
        protected RegexResult run(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            if (backwardEntryNode != null && getBackwardExecutor().isAnchored() && !flags.isSticky()) {
                return executeBackwardAnchored(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            } else {
                return executeForward(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            }
        }

        private RegexResult executeForward(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            if (getForwardExecutor().isSimpleCG()) {
                Object result = forwardEntryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, fromIndex);
                return RegexResult.createFromExecutorResult(result);
            }
            final long end = (long) forwardEntryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, fromIndex);
            if (end == TRegexDFAExecutorNode.NO_MATCH) {
                return RegexResult.getNoMatchInstance();
            }
            if (booleanMatch) {
                return RegexResult.getBooleanMatchInstance();
            }
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromEnd((int) end);
            }
            if (preCalculatedResults == null && captureGroupEntryNode == null) {
                if (end == fromIndex) { // zero-length match
                    return RegexResult.create((int) end, (int) end);
                }
                if (getForwardExecutor().isAnchored() || flags.isSticky()) {
                    return RegexResult.create(fromIndex, (int) end);
                }
                if (getForwardExecutor().canFindStart()) {
                    return RegexResult.create((int) (end >>> 32), (int) end);
                } else {
                    return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, -1, (int) end, backwardCallTarget);
                }
            } else {
                if (preCalculatedResults != null) { // traceFinder
                    return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, -1, (int) end, backwardCallTarget);
                } else if (getForwardExecutor().canFindStart()) {
                    return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, (int) (end >>> 32), (int) end, captureGroupCallTarget);
                } else {
                    return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, fromIndex, (int) end, captureGroupCallTarget);
                }
            }
        }

        private RegexResult executeBackwardAnchored(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            if (getBackwardExecutor().isSimpleCG()) {
                Object result = backwardEntryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, maxIndex);
                return RegexResult.createFromExecutorResult(result);
            }
            final int backwardResult = (int) ((long) backwardEntryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, maxIndex));
            if (backwardResult == TRegexDFAExecutorNode.NO_MATCH) {
                return RegexResult.getNoMatchInstance();
            }
            if (booleanMatch) {
                return RegexResult.getBooleanMatchInstance();
            }
            if (multiplePreCalcResults()) { // traceFinder
                return preCalculatedResults[backwardResult].createFromEnd(maxIndex);
            }
            final int start = backwardResult;
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromStart(start);
            }
            if (getForwardExecutor().isSimpleCG()) {
                Object result = forwardEntryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, start);
                assert result != null;
                return RegexResult.createFromExecutorResult(result);
            }
            if (captureGroupEntryNode != null) {
                return RegexResult.createLazy(input, start, regionFrom, regionTo, start, maxIndex, captureGroupCallTarget);
            }
            return RegexResult.create(start, maxIndex);
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

        public TRegexExecutorBaseNode getExecutor() {
            return entryNode.getExecutor();
        }

        @Override
        protected RegexResult run(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            Object result = entryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, fromIndex);
            return RegexResult.createFromExecutorResult(result);
        }
    }

    static final class NFARegexSearchNode extends RunRegexSearchNode {

        @Child private TRegexExecutorEntryNode entryNode;

        NFARegexSearchNode(TRegexExecutorEntryNode entryNode) {
            this.entryNode = entryNode;
        }

        public TRegexExecutorBaseNode getExecutor() {
            return entryNode.getExecutor();
        }

        @Override
        protected RegexResult run(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            Object result = entryNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, fromIndex);
            if (entryNode.getExecutor().isBooleanMatch()) {
                return result == null ? RegexResult.getNoMatchInstance() : RegexResult.getBooleanMatchInstance();
            }
            return RegexResult.createFromExecutorResult(result);
        }
    }
}
