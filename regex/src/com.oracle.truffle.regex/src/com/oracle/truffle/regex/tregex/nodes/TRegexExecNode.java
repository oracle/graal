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
package com.oracle.truffle.regex.tregex.nodes;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyBackwardSimpleCGRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexTraceFinderRootNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.util.Loggers;

public final class TRegexExecNode extends RegexExecNode implements RegexProfile.TracksRegexProfile {

    private RegexProfile regexProfile;
    private boolean lazyDFABailedOut = false;
    private boolean eagerDFABailedOut = false;
    private final int numberOfCaptureGroups;
    private final boolean backtrackingMode;
    private final boolean sticky;
    private final Lock optimizeLock;

    @Child private RunRegexSearchNode runnerNode;

    private TRegexExecNode(RegexAST ast, boolean backtrackingMode, RunRegexSearchNode runnerNode) {
        super(ast.getLanguage(), ast.getSource(), ast.getFlags().isEitherUnicode());
        this.numberOfCaptureGroups = ast.getNumberOfCaptureGroups();
        this.backtrackingMode = backtrackingMode;
        this.sticky = ast.getFlags().isSticky();
        this.optimizeLock = new ReentrantLock();
        this.runnerNode = insert(runnerNode);
        if (!backtrackingMode && runnerNode instanceof NFARegexSearchNode nfaNode && ast.getOptions().isGenerateDFAImmediately()) {
            switchToLazyDFA(nfaNode);
        }
    }

    public static TRegexExecNode create(RegexAST ast, NFA nfa, RunRegexSearchNode runnerNodeArg) {
        RunRegexSearchNode runnerNode = runnerNodeArg;
        boolean isBacktracking = runnerNode instanceof NFARegexSearchNode && ((NFARegexSearchNode) runnerNode).getExecutor().unwrap() instanceof TRegexBacktrackingNFAExecutorNode;
        if (!isBacktracking && ast.getOptions().isRegressionTestMode()) {
            runnerNode = RegressionTestModeSearchNode.create(ast, nfa, runnerNodeArg);
        }
        return new TRegexExecNode(ast, isBacktracking, runnerNode);
    }

    @Override
    public RegexResult execute(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {

        if (CompilerDirectives.inInterpreter() && !backtrackingMode) {
            RegexProfile profile = getRegexProfile();
            RunRegexSearchNode curRunnerNode = runnerNode;
            if (curRunnerNode instanceof NFARegexSearchNode nfaNode) {
                if (profile.shouldGenerateDFA(maxIndex - fromIndex) && optimizeLock.tryLock()) {
                    try {
                        switchToLazyDFA(nfaNode);
                        profile.resetCalls();
                    } finally {
                        optimizeLock.unlock();
                    }
                }
            } else if (canSwitchToEagerDFA(source, curRunnerNode)) {
                assert ((LazyCaptureGroupRegexSearchNode) curRunnerNode).forwardEntryNode != null;
                if (profile.atEvaluationTripPoint() && profile.shouldUseEagerMatching() && optimizeLock.tryLock()) {
                    try {
                        switchToEagerDFA(profile);
                    } finally {
                        optimizeLock.unlock();
                    }
                }
            }
        }

        final RegexResult result = runnerNode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
        assert !sticky || source.getOptions().isBooleanMatch() || result == RegexResult.getNoMatchInstance() || RegexResult.RegexResultGetStartNode.getUncached().execute(result, 0) == fromIndex;
        assert validResult(input, fromIndex, maxIndex, regionFrom, regionTo, result);

        if (CompilerDirectives.inInterpreter() && !backtrackingMode) {
            RunRegexSearchNode curRunnerNode = runnerNode;
            RegexProfile profile = getRegexProfile();
            if (curRunnerNode instanceof NFARegexSearchNode) {
                profile.incCalls();
                profile.incProcessedCharacters(charactersProcessedDuringSearch(result, fromIndex, maxIndex));
            } else if (canSwitchToEagerDFA(source, curRunnerNode)) {
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

    @Override
    public RegexProfile getRegexProfile() {
        if (regexProfile == null) {
            regexProfile = new RegexProfile();
        }
        return regexProfile;
    }

    private void switchToLazyDFA(NFARegexSearchNode nfaNode) {
        CompilerAsserts.neverPartOfCompilation();
        if (!lazyDFABailedOut) {
            LazyCaptureGroupRegexSearchNode lazyDFANode = compileLazyDFA(((TRegexNFAExecutorNode) nfaNode.getExecutor().unwrap()).getNFA(), getRegexProfile(), true);
            if (lazyDFANode == null) {
                lazyDFABailedOut = true;
                ((TRegexNFAExecutorNode) nfaNode.getExecutor().unwrap()).notifyDfaGeneratorBailedOut();
            } else if (getSource().getOptions().isAlwaysEager() && canSwitchToEagerDFA(source, lazyDFANode)) {
                switchToEagerDFA(null);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                runnerNode = insert(lazyDFANode);
            }
        }
    }

    private static LazyCaptureGroupRegexSearchNode compileLazyDFA(NFA nfa, RegexProfile profile, boolean allowSimpleCG) {
        try {
            return TRegexCompiler.compileLazyDFAExecutor(nfa.getAst().getLanguage(), new NFA(nfa), profile, allowSimpleCG);
        } catch (UnsupportedRegexException e) {
            Loggers.LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + nfa.getAst().getSource());
            return null;
        }
    }

    private static boolean canSwitchToEagerDFA(RegexSource source, RunRegexSearchNode curRunnerNode) {
        return !source.getOptions().isBooleanMatch() && curRunnerNode instanceof LazyCaptureGroupRegexSearchNode && ((LazyCaptureGroupRegexSearchNode) curRunnerNode).captureGroupEntryNode != null;
    }

    private void switchToEagerDFA(RegexProfile profile) {
        CompilerAsserts.neverPartOfCompilation();
        if (!eagerDFABailedOut) {
            EagerCaptureGroupRegexSearchNode eagerDFANode = compileEagerDFA(getRegexLanguage(), getSource());
            if (eagerDFANode == null) {
                eagerDFABailedOut = true;
            } else {
                Loggers.LOG_SWITCH_TO_EAGER.fine(() -> "regex " + getSource() + ": switching to eager matching." + (profile == null ? "" : " profile: " + profile));
                CompilerDirectives.transferToInterpreterAndInvalidate();
                runnerNode = insert(eagerDFANode);
            }
        }
    }

    private static EagerCaptureGroupRegexSearchNode compileEagerDFA(RegexLanguage language, RegexSource source) {
        try {
            assert !source.getOptions().isBooleanMatch();
            TRegexDFAExecutorNode executorNode = TRegexCompiler.compileEagerDFAExecutor(language, source);
            return new EagerCaptureGroupRegexSearchNode(createEntryNode(language, executorNode));
        } catch (UnsupportedRegexException e) {
            Loggers.LOG_BAILOUT_MESSAGES.fine(() -> e.getReason() + ": " + source);
            return null;
        }
    }

    public static TRegexExecutorEntryNode createEntryNode(RegexLanguage language, TRegexExecutorNode executor) {
        if (executor == null) {
            return null;
        }
        return TRegexExecutorEntryNode.create(language, executor);
    }

    @Override
    public String getEngineLabel() {
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
                        RegexProfile profile) {
            this.forwardEntryNode = forwardNode;
            this.flags = flags;
            this.booleanMatch = source != null && source.getOptions().isBooleanMatch();
            this.preCalculatedResults = preCalculatedResults;
            this.backwardEntryNode = backwardNode;
            if (backwardNode == null) {
                assert singlePreCalcResult() || getForwardExecutor().isAnchored() || getForwardExecutor().isSimpleCG() || booleanMatch;
                backwardCallTarget = null;
            } else {
                final RegexBodyNode bodyNode;
                if (preCalculatedResults != null) {
                    bodyNode = new TRegexTraceFinderRootNode(language, source, preCalculatedResults, backwardNode);
                } else if (getBackwardExecutor().isSimpleCG()) {
                    bodyNode = new TRegexLazyBackwardSimpleCGRootNode(language, source, backwardNode);
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
                                (backwardEntryNode != null && getBackwardExecutor().isAnchored() && !flags.isSticky()) || getForwardExecutor().canFindStart()) {
                    findStartCallTarget = null;
                } else {
                    findStartCallTarget = backwardCallTarget;
                }
                captureGroupCallTarget = new RegexRootNode(language, new TRegexLazyCaptureGroupsRootNode(language, source, captureGroupNode, profile, findStartCallTarget)).getCallTarget();
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
                if ((backwardCallTarget == null || getForwardExecutor().getNumberOfCaptureGroups() == 1) && end == fromIndex) {
                    // zero-length match
                    return RegexResult.create((int) end, (int) end);
                }
                if (getForwardExecutor().isAnchored() || flags.isSticky()) {
                    return RegexResult.create(fromIndex, (int) end);
                }
                if (backwardCallTarget == null && getForwardExecutor().canFindStart()) {
                    return RegexResult.create((int) (end >>> 32), (int) end);
                }
                assert backwardCallTarget != null;
                return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, -1, (int) end, backwardCallTarget);
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
                return RegexResult.createLazy(input, fromIndex, regionFrom, regionTo, start, maxIndex, captureGroupCallTarget);
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

    public static final class NFARegexSearchNode extends RunRegexSearchNode {

        @Child private TRegexExecutorEntryNode entryNode;

        private NFARegexSearchNode(TRegexExecutorEntryNode entryNode) {
            this.entryNode = entryNode;
        }

        public static NFARegexSearchNode create(RegexLanguage language, TRegexExecutorNode nfaExecutor) {
            return new NFARegexSearchNode(createEntryNode(language, nfaExecutor));
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

    private static final class RegressionTestModeSearchNode extends RunRegexSearchNode {

        private final RegexSource source;
        private final int numberOfCaptureGroups;
        private final RunRegexSearchNode runner;
        private final NFARegexSearchNode nfaNode;
        private final NFARegexSearchNode backtrackingNode;
        private final LazyCaptureGroupRegexSearchNode lazyDFANode;
        private final LazyCaptureGroupRegexSearchNode noSimpleCGLazyDFANode;
        private final EagerCaptureGroupRegexSearchNode eagerDFANode;

        private RegressionTestModeSearchNode(
                        RegexSource source,
                        int numberOfCaptureGroups, RunRegexSearchNode runner,
                        NFARegexSearchNode nfaNode,
                        NFARegexSearchNode backtrackingNode,
                        LazyCaptureGroupRegexSearchNode lazyDFANode,
                        LazyCaptureGroupRegexSearchNode noSimpleCGLazyDFANode,
                        EagerCaptureGroupRegexSearchNode eagerDFANode) {
            this.source = source;
            this.numberOfCaptureGroups = numberOfCaptureGroups;
            this.runner = runner;
            this.nfaNode = nfaNode;
            this.backtrackingNode = backtrackingNode;
            this.lazyDFANode = lazyDFANode;
            this.noSimpleCGLazyDFANode = noSimpleCGLazyDFANode;
            this.eagerDFANode = eagerDFANode;
        }

        private static RegressionTestModeSearchNode create(RegexAST ast, NFA nfa, RunRegexSearchNode runnerNode) {
            RegexLanguage language = ast.getLanguage();

            NFARegexSearchNode nfaNode = null;
            NFARegexSearchNode backtrackingNode;
            LazyCaptureGroupRegexSearchNode lazyDFANode;
            LazyCaptureGroupRegexSearchNode noSimpleCGLazyDFANode = null;
            EagerCaptureGroupRegexSearchNode eagerDFANode = null;

            if (runnerNode instanceof NFARegexSearchNode) {
                nfaNode = (NFARegexSearchNode) runnerNode;
                assert (!(nfaNode.getExecutor().unwrap() instanceof TRegexBacktrackingNFAExecutorNode));
            }
            backtrackingNode = new NFARegexSearchNode(createEntryNode(language, TRegexCompiler.compileBacktrackingExecutor(language, nfa)));
            if (runnerNode instanceof LazyCaptureGroupRegexSearchNode) {
                lazyDFANode = (LazyCaptureGroupRegexSearchNode) runnerNode;
            } else {
                lazyDFANode = compileLazyDFA(nfa, new RegexProfile(), true);
            }
            if (lazyDFANode != null) {
                if (canSwitchToEagerDFA(ast.getSource(), lazyDFANode)) {
                    eagerDFANode = compileEagerDFA(ast.getLanguage(), ast.getSource());
                }
                if (lazyDFANode.isSimpleCG()) {
                    noSimpleCGLazyDFANode = compileLazyDFA(nfa, new RegexProfile(), false);
                }
            }
            RunRegexSearchNode runner = lazyDFANode == null ? nfaNode : lazyDFANode;
            return new RegressionTestModeSearchNode(ast.getSource(), ast.getNumberOfCaptureGroups(), runner, nfaNode, backtrackingNode, lazyDFANode, noSimpleCGLazyDFANode, eagerDFANode);
        }

        @Override
        protected RegexResult run(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo) {
            RegexResult result = runner.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            runInternalRegressionTests(frame.materialize(), input, fromIndex, maxIndex, regionFrom, regionTo, result);
            return result;
        }

        @TruffleBoundary
        private void runInternalRegressionTests(MaterializedFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
            if (!(backtrackerProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            nfaProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            noSimpleCGLazyDFAProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result) &&
                            (source.getOptions().isBooleanMatch() || eagerDFAProducesSameResult(frame, input, fromIndex, maxIndex, regionFrom, regionTo, result)))) {
                throw new AssertionError("Inconsistent results between different matching modes");
            }
        }

        private boolean backtrackerProducesSameResult(MaterializedFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
            RegexResult btResult = backtrackingNode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            if (resultsEqual(result, btResult)) {
                return true;
            }
            Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "Backtracker", btResult, "DFA", result));
            return false;
        }

        private boolean nfaProducesSameResult(MaterializedFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
            if (lazyDFANode == null || nfaNode == null) {
                return true;
            }
            RegexResult nfaResult = nfaNode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            if (resultsEqual(result, nfaResult)) {
                return true;
            }
            Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "NFA", nfaResult, "DFA", result));
            return false;
        }

        private boolean noSimpleCGLazyDFAProducesSameResult(MaterializedFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
            if (noSimpleCGLazyDFANode == null) {
                return true;
            }
            assert !noSimpleCGLazyDFANode.isSimpleCG();
            RegexResult noSimpleCGResult = noSimpleCGLazyDFANode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            if (resultsEqual(result, noSimpleCGResult)) {
                return true;
            }
            Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "LazyDFA", noSimpleCGResult, "SimplCGDFA", result));
            return false;
        }

        private boolean eagerDFAProducesSameResult(MaterializedFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, RegexResult result) {
            if (eagerDFANode == null) {
                return true;
            }
            RegexResult eagerResult = eagerDFANode.run(frame, input, fromIndex, maxIndex, regionFrom, regionTo);
            boolean equal = resultsEqual(result, eagerResult);
            if (!equal) {
                Loggers.LOG_INTERNAL_ERRORS.severe(() -> regressionTestErrorMsg(input, fromIndex, maxIndex, regionFrom, regionTo, "Lazy", result, "Eager", eagerResult));
            }
            return equal;
        }

        private String regressionTestErrorMsg(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, String nameA, RegexResult a, String nameB, RegexResult b) {
            return String.format("Regex: %s\nInput: %s\nfromIndex: %d\nmaxIndex: %d\nregionFrom: %d\nregionTo: %d\n%s Result: %s\n%s Result: %s",
                            source.toStringEscaped(), input, fromIndex, maxIndex, regionFrom, regionTo, nameA, a, nameB, b);
        }

        private boolean resultsEqual(RegexResult a, RegexResult b) {
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
                throw CompilerDirectives.shouldNotReachHere("mixed boolean result and regular result: " + a);
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
    }
}
