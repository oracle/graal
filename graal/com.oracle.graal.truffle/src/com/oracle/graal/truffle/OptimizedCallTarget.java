/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public final class OptimizedCallTarget extends DefaultCallTarget implements LoopCountReceiver, FrameFactory {

    private static final PrintStream OUT = TTY.out().out();

    private final int inliningReprofileCount;
    private final int invalidationReprofileCount;

    protected OptimizedCallTarget(RootNode rootNode, FrameDescriptor descriptor, TruffleCompiler compiler, int compilationThreshold, int inliningReprofileCount, int invalidationReprofileCount) {
        super(rootNode, descriptor);
        this.compiler = compiler;
        this.invokeCounter = compilationThreshold >> 7;
        this.loopAndInvokeCounter = compilationThreshold;
        this.originalInvokeCounter = compilationThreshold;
        this.rootNode.setCallTarget(this);
        this.inliningReprofileCount = inliningReprofileCount;
        this.invalidationReprofileCount = invalidationReprofileCount;
    }

    private InstalledCode compiledMethod;
    private final TruffleCompiler compiler;
    private int invokeCounter;
    private int originalInvokeCounter;
    private int loopAndInvokeCounter;
    private boolean disableCompilation;

    long timeCompilationStarted;
    long timePartialEvaluationFinished;
    long timeCompilationFinished;
    int codeSize;
    int nodeCountPartialEval;
    int nodeCountLowered;

    @Override
    public Object call(PackedFrame caller, Arguments args) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.FASTPATH_PROBABILITY, compiledMethod != null)) {
            try {
                return compiledMethod.execute(this, caller, args);
            } catch (InvalidInstalledCodeException ex) {
                return compiledCodeInvalidated(caller, args);
            }
        } else {
            return interpreterCall(caller, args);
        }
    }

    protected Object compiledCodeInvalidated(PackedFrame caller, Arguments args) {
        compiledMethod = null;
        invokeCounter = invalidationReprofileCount;
        if (TruffleFunctionInlining.getValue()) {
            originalInvokeCounter += invalidationReprofileCount;
        }
        if (TraceTruffleCompilation.getValue()) {
            OUT.printf("[truffle] invalidated %-48s |Alive %5.0fms\n", rootNode, (System.nanoTime() - timeCompilationFinished) / 1e6);
        }
        return call(caller, args);
    }

    private Object interpreterCall(PackedFrame caller, Arguments args) {
        invokeCounter--;
        loopAndInvokeCounter--;
        if (disableCompilation || loopAndInvokeCounter > 0 || invokeCounter > 0) {
            return executeHelper(caller, args);
        } else {
            if (TruffleFunctionInlining.getValue() && inline()) {
                invokeCounter = 2;
                loopAndInvokeCounter = inliningReprofileCount;
                originalInvokeCounter = inliningReprofileCount;
            } else {
                compile();
            }
            return call(caller, args);
        }
    }

    public boolean inline() {
        return new InliningHelper(this).inline();
    }

    public void compile() {
        try {
            compiledMethod = compiler.compile(this);
            if (compiledMethod == null) {
                throw new BailoutException(String.format("code installation failed (codeSize=%s)", codeSize));
            } else {
                if (TraceTruffleCompilation.getValue()) {
                    int nodeCountTruffle = NodeUtil.countNodes(rootNode);
                    OUT.printf("[truffle] optimized %-50s |Nodes %7d |Time %5.0f(%4.0f+%-4.0f)ms |Nodes %5d/%5d |CodeSize %d\n", rootNode, nodeCountTruffle,
                                    (timeCompilationFinished - timeCompilationStarted) / 1e6, (timePartialEvaluationFinished - timeCompilationStarted) / 1e6,
                                    (timeCompilationFinished - timePartialEvaluationFinished) / 1e6, nodeCountPartialEval, nodeCountLowered, codeSize);
                }
            }
        } catch (Throwable e) {
            disableCompilation = true;
            if (TraceTruffleCompilation.getValue()) {
                if (e instanceof BailoutException) {
                    OUT.printf("[truffle] opt bailout %-48s  %s\n", rootNode, e.getMessage());
                } else {
                    OUT.printf("[truffle] opt failed %-49s  %s\n", rootNode, e.toString());
                    if (TraceTruffleCompilationExceptions.getValue()) {
                        e.printStackTrace(OUT);
                    }
                    if (TruffleCompilationExceptionsAreFatal.getValue()) {
                        System.exit(-1);
                    }
                }
            }
        }
    }

    public Object executeHelper(PackedFrame caller, Arguments args) {
        VirtualFrame frame = createFrame(frameDescriptor, caller, args);
        return rootNode.execute(frame);
    }

    private static FrameWithoutBoxing createFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments args) {
        return new FrameWithoutBoxing(descriptor, caller, args);
    }

    @Override
    public VirtualFrame create(FrameDescriptor descriptor, PackedFrame caller, Arguments args) {
        return createFrame(descriptor, caller, args);
    }

    @Override
    public String toString() {
        return "CallTarget " + rootNode;
    }

    @Override
    public void reportLoopCount(int count) {
        loopAndInvokeCounter -= count;
    }

    private static class InliningHelper {

        private final OptimizedCallTarget target;

        public InliningHelper(OptimizedCallTarget target) {
            this.target = target;
        }

        public boolean inline() {
            final InliningPolicy policy = new InliningPolicy(target);
            if (!policy.continueInlining()) {
                if (TraceTruffleInliningDetails.getValue()) {
                    List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(target);
                    if (!inlinableCallSites.isEmpty()) {
                        OUT.printf("[truffle] inlining hit caller size limit (%3d >= %3d).%3d remaining call sites in %s:\n", policy.callerNodeCount, TruffleInliningMaxCallerSize.getValue(),
                                        inlinableCallSites.size(), target.getRootNode());
                        policy.sortByRelevance(inlinableCallSites);
                        printCallSiteInfo(policy, inlinableCallSites, "");
                    }
                }
                return false;
            }

            List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(target);
            if (inlinableCallSites.isEmpty()) {
                return false;
            }

            policy.sortByRelevance(inlinableCallSites);

            boolean inlined = false;
            for (InlinableCallSiteInfo inlinableCallSite : inlinableCallSites) {
                if (!policy.isWorthInlining(inlinableCallSite)) {
                    break;
                }
                if (inlinableCallSite.getCallSite().inline(target)) {
                    if (TraceTruffleInlining.getValue()) {
                        printCallSiteInfo(policy, inlinableCallSite, "inlined");
                    }
                    inlined = true;
                    break;
                }
            }

            if (inlined) {
                for (InlinableCallSiteInfo callSite : inlinableCallSites) {
                    callSite.getCallSite().resetCallCount();
                }
            } else {
                if (TraceTruffleInliningDetails.getValue()) {
                    OUT.printf("[truffle] inlining stopped.%3d remaining call sites in %s:\n", inlinableCallSites.size(), target.getRootNode());
                    printCallSiteInfo(policy, inlinableCallSites, "");
                }
            }

            return inlined;
        }

        private void printCallSiteInfo(InliningPolicy policy, List<InlinableCallSiteInfo> inlinableCallSites, String msg) {
            for (InlinableCallSiteInfo candidate : inlinableCallSites) {
                printCallSiteInfo(policy, candidate, msg);
            }
        }

        private void printCallSiteInfo(InliningPolicy policy, InlinableCallSiteInfo callSite, String msg) {
            String calls = String.format("%4s/%4s", callSite.getCallCount(), policy.callerInvocationCount);
            String nodes = String.format("%3s/%3s", callSite.getInlineNodeCount(), policy.callerNodeCount);
            OUT.printf("[truffle] %-9s %-50s |Nodes %6s |Calls %6s %7.3f |into %s\n", msg, callSite.getCallSite(), nodes, calls, policy.metric(callSite), target.getRootNode());
        }

        private static final class InliningPolicy {

            private final int callerNodeCount;
            private final int callerInvocationCount;

            public InliningPolicy(OptimizedCallTarget caller) {
                this.callerNodeCount = NodeUtil.countNodes(caller.getRootNode());
                this.callerInvocationCount = caller.originalInvokeCounter;
            }

            public boolean continueInlining() {
                return callerNodeCount < TruffleInliningMaxCallerSize.getValue();
            }

            public boolean isWorthInlining(InlinableCallSiteInfo callSite) {
                return callSite.getInlineNodeCount() <= TruffleInliningMaxCalleeSize.getValue() && callSite.getInlineNodeCount() + callerNodeCount <= TruffleInliningMaxCallerSize.getValue() &&
                                callSite.getCallCount() > 0;
            }

            public double metric(InlinableCallSiteInfo callSite) {
                double frequency = (double) callSite.getCallCount() / (double) callerInvocationCount;
                double metric = ((double) callSite.getCallCount() / (double) callSite.getInlineNodeCount()) + frequency;
                return metric;
            }

            public void sortByRelevance(List<InlinableCallSiteInfo> inlinableCallSites) {
                Collections.sort(inlinableCallSites, new Comparator<InlinableCallSiteInfo>() {

                    @Override
                    public int compare(InlinableCallSiteInfo cs1, InlinableCallSiteInfo cs2) {
                        int result = (isWorthInlining(cs2) ? 1 : 0) - (isWorthInlining(cs1) ? 1 : 0);
                        if (result == 0) {
                            return Double.compare(metric(cs2), metric(cs1));
                        }
                        return result;
                    }
                });
            }
        }

        private static final class InlinableCallSiteInfo {

            private final InlinableCallSite callSite;
            private final int callCount;
            private final int nodeCount;

            public InlinableCallSiteInfo(InlinableCallSite callSite) {
                this.callSite = callSite;
                this.callCount = callSite.getCallCount();
                this.nodeCount = NodeUtil.countNodes(callSite.getInlineTree());
            }

            public InlinableCallSite getCallSite() {
                return callSite;
            }

            public int getCallCount() {
                return callCount;
            }

            public int getInlineNodeCount() {
                return nodeCount;
            }
        }

        private static List<InlinableCallSiteInfo> getInlinableCallSites(final DefaultCallTarget target) {
            final ArrayList<InlinableCallSiteInfo> inlinableCallSites = new ArrayList<>();
            target.getRootNode().accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    if (node instanceof InlinableCallSite) {
                        inlinableCallSites.add(new InlinableCallSiteInfo((InlinableCallSite) node));
                    }
                    return true;
                }
            });
            return inlinableCallSites;
        }
    }
}
