/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.CompiledRegex;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.RegexProfile;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.result.SingleResult;
import com.oracle.truffle.regex.result.SingleResultLazyStart;
import com.oracle.truffle.regex.result.TraceFinderResult;
import com.oracle.truffle.regex.tregex.TRegexCompiler;
import com.oracle.truffle.regex.tregex.nodes.input.InputLengthNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.Arrays;

public class TRegexExecRootNode extends RegexExecRootNode implements CompiledRegex, RegexProfile.TracksRegexProfile {

    private static final DebugUtil.DebugLogger LOG_BAILOUT = new DebugUtil.DebugLogger("TRegex Bailout: ", DebugUtil.LOG_BAILOUT_MESSAGES);

    private static final EagerCaptureGroupRegexSearchNode EAGER_SEARCH_BAILED_OUT = new EagerCaptureGroupRegexSearchNode(null);

    private final CallTarget regexCallTarget;
    private final LazyCaptureGroupRegexSearchNode lazySearchNode;
    private EagerCaptureGroupRegexSearchNode eagerSearchNode;
    private RegexProfile regexProfile;
    private final TRegexCompiler tRegexCompiler;
    private final boolean eagerCompilation;

    @Child private RunRegexSearchNode runRegexSearchNode;

    public TRegexExecRootNode(RegexLanguage language,
                    TRegexCompiler tRegexCompiler,
                    RegexSource source,
                    boolean eagerCompilation,
                    PreCalculatedResultFactory[] preCalculatedResults,
                    TRegexDFAExecutorNode forwardExecutor,
                    TRegexDFAExecutorNode backwardExecutor,
                    TRegexDFAExecutorNode captureGroupExecutor) {
        super(language, source);
        lazySearchNode = new LazyCaptureGroupRegexSearchNode(language, source, preCalculatedResults, forwardExecutor, backwardExecutor, captureGroupExecutor, this);
        runRegexSearchNode = insert(lazySearchNode);
        regexCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, forwardExecutor.getProperties().getFrameDescriptor(), this));
        this.tRegexCompiler = tRegexCompiler;
        this.eagerCompilation = eagerCompilation;
        if (canSwitchToEagerSearch()) {
            if (eagerCompilation) {
                compileEagerSearchNode();
            }
            if (DebugUtil.DEBUG_ALWAYS_EAGER) {
                switchToEagerSearch(null);
            }
        }
    }

    @Override
    public final RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
        final RegexResult result = runRegexSearchNode.run(frame, regex, input, fromIndex);
        assert !eagerCompilation || eagerAndLazySearchNodesProduceSameResult(frame, regex, input, fromIndex, result);
        if (CompilerDirectives.inInterpreter() && canSwitchToEagerSearch() && runRegexSearchNode == lazySearchNode) {
            RegexProfile profile = getRegexProfile();
            if (profile.atEvaluationTripPoint() && profile.shouldUseEagerMatching()) {
                switchToEagerSearch(profile);
            }
            profile.incCalls();
            if (result != RegexResult.NO_MATCH) {
                profile.incMatches();
            }
        }
        return result;
    }

    @Override
    public RegexProfile getRegexProfile() {
        if (regexProfile == null) {
            regexProfile = new RegexProfile();
        }
        return regexProfile;
    }

    private boolean canSwitchToEagerSearch() {
        return lazySearchNode.captureGroupEntryNode != null;
    }

    private void switchToEagerSearch(RegexProfile profile) {
        compileEagerSearchNode();
        if (eagerSearchNode != EAGER_SEARCH_BAILED_OUT) {
            if (DebugUtil.LOG_SWITCH_TO_EAGER) {
                System.out.println("regex " + getSource() + ": switching to eager matching." + (profile == null ? "" : " profile: " + profile));
            }
            runRegexSearchNode = insert(eagerSearchNode);
        }
    }

    private void compileEagerSearchNode() {
        if (eagerSearchNode == null) {
            try {
                TRegexDFAExecutorNode executorNode = tRegexCompiler.compileEagerDFAExecutor(getSource());
                eagerSearchNode = new EagerCaptureGroupRegexSearchNode(executorNode);
            } catch (UnsupportedRegexException e) {
                LOG_BAILOUT.log(e.getMessage() + ": " + source);
                eagerSearchNode = EAGER_SEARCH_BAILED_OUT;
            }
        }
    }

    private boolean eagerAndLazySearchNodesProduceSameResult(VirtualFrame frame, RegexObject regex, Object input, int fromIndex, RegexResult resultOfCurrentSearchNode) {
        if (lazySearchNode.captureGroupEntryNode == null || eagerSearchNode == EAGER_SEARCH_BAILED_OUT) {
            return true;
        }
        RegexResult lazyResult;
        RegexResult eagerResult;
        if (runRegexSearchNode == lazySearchNode) {
            lazyResult = resultOfCurrentSearchNode;
            eagerResult = eagerSearchNode.run(frame, regex, input, fromIndex);
        } else {
            lazyResult = lazySearchNode.run(frame, regex, input, fromIndex);
            eagerResult = resultOfCurrentSearchNode;
        }
        boolean equal;
        if (lazyResult == RegexResult.NO_MATCH) {
            equal = eagerResult == RegexResult.NO_MATCH;
        } else {
            ((LazyCaptureGroupsResult) lazyResult).debugForceEvaluation();
            equal = Arrays.equals(((LazyCaptureGroupsResult) lazyResult).getResult(), ((LazyCaptureGroupsResult) eagerResult).getResult());
        }
        if (!equal) {
            System.out.println("ERROR:");
            System.out.println("Regex: " + getSource());
            System.out.println("Input: " + input);
            System.out.println("fromIndex: " + fromIndex);
            System.out.println("Lazy  Result: " + lazyResult);
            System.out.println("Eager Result: " + eagerResult);
        }
        return equal;
    }

    @Override
    public CallTarget getRegexCallTarget() {
        return regexCallTarget;
    }

    @Override
    public final String getEngineLabel() {
        return "TRegex fwd";
    }

    abstract static class RunRegexSearchNode extends Node {

        @Child InputLengthNode inputLengthNode = InputLengthNode.create();

        abstract RegexResult run(VirtualFrame frame, RegexObject regex, Object input, int fromIndexArg);
    }

    static final class LazyCaptureGroupRegexSearchNode extends RunRegexSearchNode {

        private final RegexSource source;
        @CompilationFinal(dimensions = 1) private final PreCalculatedResultFactory[] preCalculatedResults;

        @Child private TRegexDFAExecutorEntryNode forwardEntryNode;
        @Child private TRegexDFAExecutorEntryNode backwardEntryNode;
        @Child private TRegexDFAExecutorEntryNode captureGroupEntryNode;

        private final CallTarget backwardCallTarget;
        private final CallTarget captureGroupCallTarget;

        LazyCaptureGroupRegexSearchNode(RegexLanguage language,
                        RegexSource source,
                        PreCalculatedResultFactory[] preCalculatedResults,
                        TRegexDFAExecutorNode forwardNode,
                        TRegexDFAExecutorNode backwardNode,
                        TRegexDFAExecutorNode captureGroupExecutor,
                        RegexProfile.TracksRegexProfile profiler) {
            this.forwardEntryNode = TRegexDFAExecutorEntryNode.create(forwardNode);
            this.source = source;
            this.preCalculatedResults = preCalculatedResults;
            this.backwardEntryNode = TRegexDFAExecutorEntryNode.create(backwardNode);
            if (backwardNode == null) {
                assert singlePreCalcResult();
                backwardCallTarget = null;
            } else {
                backwardCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, backwardNode.getProperties().getFrameDescriptor(),
                                new TRegexLazyFindStartRootNode(language, source, forwardNode.getPrefixLength(), backwardNode)));
            }
            this.captureGroupEntryNode = TRegexDFAExecutorEntryNode.create(captureGroupExecutor);
            if (captureGroupExecutor == null) {
                captureGroupCallTarget = null;
            } else {
                captureGroupCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, captureGroupExecutor.getProperties().getFrameDescriptor(),
                                new TRegexLazyCaptureGroupsRootNode(language, source, captureGroupExecutor, profiler)));
            }
        }

        @Override
        RegexResult run(VirtualFrame frame, RegexObject regex, Object input, int fromIndexArg) {
            if (backwardEntryNode != null && backwardEntryNode.getExecutor().isAnchored()) {
                return executeBackwardAnchored(frame, regex, input, fromIndexArg);
            } else {
                return executeForward(frame, regex, input, fromIndexArg);
            }
        }

        private RegexResult executeForward(VirtualFrame frame, RegexObject regex, Object input, int fromIndexArg) {
            forwardEntryNode.execute(frame, input, fromIndexArg, fromIndexArg, inputLengthNode.execute(input));
            final int end = forwardEntryNode.getExecutor().getResultInt(frame);
            if (end == TRegexDFAExecutorNode.NO_MATCH) {
                return RegexResult.NO_MATCH;
            }
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromEnd(regex, input, end);
            }
            if (preCalculatedResults == null && captureGroupEntryNode == null) {
                if (end == fromIndexArg) { // zero-length match
                    return new SingleResult(regex, input, end, end);
                }
                if (forwardEntryNode.getExecutor().isAnchored() || source.getFlags().isSticky()) {
                    return new SingleResult(regex, input, fromIndexArg, end);
                }
                return new SingleResultLazyStart(regex, input, fromIndexArg, end, backwardCallTarget);
            } else {
                if (preCalculatedResults != null) { // traceFinder
                    return new TraceFinderResult(regex, input, fromIndexArg, end, backwardCallTarget, preCalculatedResults);
                } else {
                    if (forwardEntryNode.getExecutor().isAnchored() ||
                                    (source.getFlags().isSticky() && forwardEntryNode.getExecutor().getPrefixLength() == 0)) {
                        return new LazyCaptureGroupsResult(regex, input, fromIndexArg, end,
                                        captureGroupEntryNode.getExecutor().getNumberOfCaptureGroups(), null, captureGroupCallTarget);
                    }
                    return new LazyCaptureGroupsResult(regex, input, fromIndexArg, end,
                                    captureGroupEntryNode.getExecutor().getNumberOfCaptureGroups(), backwardCallTarget, captureGroupCallTarget);
                }
            }
        }

        private RegexResult executeBackwardAnchored(VirtualFrame frame, RegexObject regex, Object input, int fromIndexArg) {
            final int inputLength = inputLengthNode.execute(input);
            backwardEntryNode.execute(frame, input, 0, inputLength - 1,
                            Math.max(-1, fromIndexArg - 1 - forwardEntryNode.getExecutor().getPrefixLength()));
            final int backwardResult = backwardEntryNode.getExecutor().getResultInt(frame);
            if (backwardResult == TRegexDFAExecutorNode.NO_MATCH) {
                return RegexResult.NO_MATCH;
            }
            if (multiplePreCalcResults()) { // traceFinder
                return preCalculatedResults[backwardResult].createFromEnd(regex, input, inputLength);
            }
            final int start = backwardResult + 1;
            if (singlePreCalcResult()) {
                return preCalculatedResults[0].createFromStart(regex, input, start);
            }
            if (captureGroupEntryNode != null) {
                return new LazyCaptureGroupsResult(regex, input, start, inputLength,
                                captureGroupEntryNode.getExecutor().getNumberOfCaptureGroups(), null, captureGroupCallTarget);
            }
            return new SingleResult(regex, input, start, inputLength);
        }

        private boolean singlePreCalcResult() {
            return preCalculatedResults != null && preCalculatedResults.length == 1;
        }

        private boolean multiplePreCalcResults() {
            return preCalculatedResults != null && preCalculatedResults.length > 1;
        }
    }

    static final class EagerCaptureGroupRegexSearchNode extends RunRegexSearchNode {

        @Child private TRegexDFAExecutorEntryNode entryNode;

        EagerCaptureGroupRegexSearchNode(TRegexDFAExecutorNode executorNode) {
            entryNode = TRegexDFAExecutorEntryNode.create(executorNode);
        }

        @Override
        RegexResult run(VirtualFrame frame, RegexObject regex, Object input, int fromIndexArg) {
            entryNode.execute(frame, input, fromIndexArg, fromIndexArg, inputLengthNode.execute(input));
            final int[] resultArray = entryNode.getExecutor().getResultCaptureGroups(frame);
            if (resultArray == null) {
                return RegexResult.NO_MATCH;
            }
            return new LazyCaptureGroupsResult(regex, input, resultArray);
        }
    }
}
