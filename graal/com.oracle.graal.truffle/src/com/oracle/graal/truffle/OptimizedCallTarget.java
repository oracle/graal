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

    @Override
    public Object call(PackedFrame caller, Arguments args) {
        for (;;) {
            if (compiledMethod != null) {
                try {
                    return compiledMethod.execute(this, caller, args);
                } catch (InvalidInstalledCodeException ex) {
                    compiledMethod = null;
                    invokeCounter = invalidationReprofileCount;
                    if (TruffleFunctionInlining.getValue()) {
                        originalInvokeCounter += invalidationReprofileCount;
                    }
                    if (TraceTruffleCompilation.getValue()) {
                        OUT.printf("[truffle] invalidated %-48s |Alive %4.0fms\n", rootNode, (System.nanoTime() - timeCompilationFinished) / 1e6);
                    }
                }
            } else {
                invokeCounter--;
                loopAndInvokeCounter--;
                if (disableCompilation || loopAndInvokeCounter > 0 || invokeCounter > 0) {
                    return executeHelper(caller, args);
                } else {
                    if (TruffleFunctionInlining.getValue()) {
                        if (inline()) {
                            invokeCounter = 2;
                            loopAndInvokeCounter = inliningReprofileCount;
                            originalInvokeCounter = inliningReprofileCount;
                        } else {
                            compile();
                        }
                    } else {
                        compile();
                    }
                }
            }
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
                    OUT.printf("[truffle] optimized %-50s |Total %5.0fms |PE %4.0fms |Graal %4.0fms |CodeSize %d\n", rootNode, (timeCompilationFinished - timeCompilationStarted) / 1e6,
                                    (timePartialEvaluationFinished - timeCompilationStarted) / 1e6, (timeCompilationFinished - timePartialEvaluationFinished) / 1e6, codeSize);
                }
            }
        } catch (Throwable e) {
            invokeCounter = Integer.MAX_VALUE;
            loopAndInvokeCounter = Integer.MAX_VALUE;
            if (TraceTruffleCompilation.getValue()) {
                if (e instanceof BailoutException) {
                    disableCompilation = true;
                    BailoutException bailoutException = (BailoutException) e;
                    OUT.printf("[truffle] opt bailout %-48s  %s\n", rootNode, bailoutException.getMessage());
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

    private class InliningHelper {

        private static final int MAX_SIZE = 300;
        private static final int MAX_INLINE_SIZE = 62;

        private final OptimizedCallTarget target;

        public InliningHelper(OptimizedCallTarget target) {
            this.target = target;
        }

        public boolean inline() {
            CallTargetProfile profile = new CallTargetProfile(target.getRootNode());

            if (profile.inlinableCallSites.isEmpty()) {
                return false;
            }

            if (profile.nodeCount > MAX_SIZE) {
                return false;
            }

            double max = 0.0D;
            ProfiledInlinableCallSite inliningDecision = null;
            for (Node callNode : profile.inlinableCallSites) {
                InlinableCallSite callSite = (InlinableCallSite) callNode;
                Node inlineTree = callSite.getInlineTree();
                if (inlineTree == null) {
                    continue;
                }
                CallTargetProfile inlineProfile = new CallTargetProfile(inlineTree);
                if (inlineProfile.nodeCount > MAX_INLINE_SIZE || inlineProfile.nodeCount + profile.nodeCount > MAX_SIZE) {
                    continue;
                }

                ProfiledInlinableCallSite inlinable = new ProfiledInlinableCallSite(inlineProfile, callSite);
                double metric = (inlinable.callCount / inlineProfile.nodeCount) + ((double) inlinable.callCount / (double) originalInvokeCounter);
                if (metric >= max) {
                    inliningDecision = inlinable;
                    max = metric;
                }
            }

            for (Node callSite : profile.inlinableCallSites) {
                ((InlinableCallSite) callSite).resetCallCount();
            }

            if (inliningDecision != null) {
                if (inliningDecision.callSite.inline(target)) {
                    if (TraceTruffleCompilation.getValue()) {

                        String calls = String.format("%4s/%4s", inliningDecision.callCount, originalInvokeCounter);
                        String nodes = String.format("%3s/%3s", inliningDecision.profile.nodeCount, profile.nodeCount);

                        OUT.printf("[truffle] inlined   %-50s |Nodes %6s |Calls %6s         |into %s\n", inliningDecision.callSite, nodes, calls, target.getRootNode());
                    }
                    return true;
                }
            }
            return false;
        }

        private class ProfiledInlinableCallSite {

            final CallTargetProfile profile;
            final InlinableCallSite callSite;
            final int callCount;

            public ProfiledInlinableCallSite(CallTargetProfile profile, InlinableCallSite callSite) {
                this.profile = profile;
                this.callSite = callSite;
                this.callCount = callSite.getCallCount();
            }

        }

        private class CallTargetProfile {

            final Node root;
            final int nodeCount;
            final List<Node> inlinableCallSites = new ArrayList<>();

            public CallTargetProfile(Node rootNode) {
                root = rootNode;

                VisitorImpl impl = new VisitorImpl();
                root.accept(impl);

                this.nodeCount = impl.visitedCount;
            }

            private class VisitorImpl implements NodeVisitor {

                int visitedCount;

                @Override
                public boolean visit(Node node) {
                    if (node instanceof RootNode && node != root) {
                        return false;
                    }

                    if (node instanceof InlinableCallSite) {
                        inlinableCallSites.add(node);
                    }
                    visitedCount++;
                    return true;
                }
            }
        }
    }
}
