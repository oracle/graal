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
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public final class OptimizedCallTarget extends DefaultCallTarget implements LoopCountReceiver, ReplaceObserver {

    private static final PrintStream OUT = TTY.out().out();

    private InstalledCode installedCode;
    private Future<InstalledCode> installedCodeTask;
    private final TruffleCompiler compiler;
    private final CompilationProfile compilationProfile;
    private final CompilationPolicy compilationPolicy;
    private final TruffleInlining inlining;
    private boolean compilationEnabled;
    private int callCount;
    private SpeculationLog speculationLog = new SpeculationLog();

    OptimizedCallTarget(RootNode rootNode, TruffleCompiler compiler, int invokeCounter, int compilationThreshold, boolean compilationEnabled) {
        super(rootNode);
        this.compiler = compiler;
        this.compilationProfile = new CompilationProfile(compilationThreshold, invokeCounter, rootNode.toString());

        if (TruffleUseTimeForCompilationDecision.getValue()) {
            compilationPolicy = new TimedCompilationPolicy();
        } else {
            compilationPolicy = new DefaultCompilationPolicy();
        }
        this.compilationEnabled = compilationEnabled;

        if (TruffleCallTargetProfiling.getValue()) {
            registerCallTarget(this);
        }
        this.inlining = new TruffleInliningImpl();

    }

    @CompilerDirectives.SlowPath
    @Override
    public Object call(PackedFrame caller, Arguments args) {
        return callHelper(caller, args);
    }

    private Object callHelper(PackedFrame caller, Arguments args) {
        if (installedCode != null && installedCode.isValid()) {
            reinstallCallMethodShortcut();
        }
        if (TruffleCallTargetProfiling.getValue()) {
            callCount++;
        }
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.FASTPATH_PROBABILITY, installedCode != null)) {
            try {
                return installedCode.execute(this, caller, args);
            } catch (InvalidInstalledCodeException ex) {
                return compiledCodeInvalidated(caller, args);
            }
        } else {
            return interpreterCall(caller, args);
        }
    }

    private static void reinstallCallMethodShortcut() {
        if (TraceTruffleCompilation.getValue()) {
            OUT.println("[truffle] reinstall OptimizedCallTarget.call code with frame prolog shortcut.");
        }
        GraalTruffleRuntime.installOptimizedCallTargetCallMethod();
    }

    public CompilationProfile getCompilationProfile() {
        return compilationProfile;
    }

    private Object compiledCodeInvalidated(PackedFrame caller, Arguments args) {
        invalidate();
        return call(caller, args);
    }

    private void invalidate() {
        InstalledCode m = this.installedCode;
        if (m != null) {
            CompilerAsserts.neverPartOfCompilation();
            installedCode = null;
            compilationProfile.reportInvalidated();
            if (TraceTruffleCompilation.getValue()) {
                OUT.printf("[truffle] invalidated %-48s %08x |InvalidationCount %2d |ReplaceCount %3d\n", getRootNode(), getRootNode().hashCode(), compilationProfile.getInvalidationCount(),
                                compilationProfile.getNodeReplaceCount());
            }
        }

        Future<InstalledCode> task = this.installedCodeTask;
        if (task != null) {
            task.cancel(true);
            this.installedCodeTask = null;
            compilationProfile.reportInvalidated();
        }
    }

    private Object interpreterCall(PackedFrame caller, Arguments args) {
        CompilerAsserts.neverPartOfCompilation();
        compilationProfile.reportInterpreterCall();
        if (compilationEnabled && shouldCompile()) {
            if (isCompiling()) {
                return waitForCompilation(caller, args);
            }
            boolean inlined = shouldInline() && inline();
            if (!inlined) {
                compile();
            }
        }
        return executeHelper(caller, args);
    }

    private boolean shouldCompile() {
        return compilationPolicy.shouldCompile(compilationProfile);
    }

    private static boolean shouldInline() {
        return TruffleFunctionInlining.getValue();
    }

    private boolean isCompiling() {
        if (installedCodeTask != null) {
            if (installedCodeTask.isCancelled()) {
                installedCodeTask = null;
                return false;
            }
            return true;
        }
        return false;
    }

    public void compile() {
        this.installedCodeTask = compiler.compile(this);
        if (!TruffleBackgroundCompilation.getValue()) {
            installedCode = receiveInstalledCode();
        }
    }

    private Object waitForCompilation(PackedFrame caller, Arguments args) {
        if (installedCodeTask.isDone()) {
            installedCode = receiveInstalledCode();
        }
        return executeHelper(caller, args);
    }

    private InstalledCode receiveInstalledCode() {
        try {
            return installedCodeTask.get();
        } catch (InterruptedException | ExecutionException e) {
            compilationEnabled = false;
            OUT.printf("[truffle] opt failed %-48s  %s\n", getRootNode(), e.getMessage());
            if (e.getCause() instanceof BailoutException) {
                // Bailout => move on.
            } else {
                if (TraceTruffleCompilationExceptions.getValue()) {
                    e.printStackTrace(OUT);
                }
                if (TruffleCompilationExceptionsAreFatal.getValue()) {
                    System.exit(-1);
                }
            }
            return null;
        }
    }

    /**
     * Forces inlining whether or not function inlining is enabled.
     * 
     * @return true if an inlining was performed
     */
    public boolean inline() {
        boolean result = inlining.performInlining(this);
        if (result) {
            compilationProfile.reportInliningPerformed(inlining);
        }
        return result;
    }

    public Object executeHelper(PackedFrame caller, Arguments args) {
        VirtualFrame frame = createFrame(getRootNode().getFrameDescriptor(), caller, args);
        return getRootNode().execute(frame);
    }

    protected static FrameWithoutBoxing createFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments args) {
        return new FrameWithoutBoxing(descriptor, caller, args);
    }

    @Override
    public void reportLoopCount(int count) {
        compilationProfile.reportLoopCount(count);
    }

    @Override
    public void nodeReplaced() {
        compilationProfile.reportNodeReplaced();
        invalidate();
    }

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    private static void printProfiling() {
        List<OptimizedCallTarget> sortedCallTargets = new ArrayList<>(OptimizedCallTarget.callTargets.keySet());
        Collections.sort(sortedCallTargets, new Comparator<OptimizedCallTarget>() {

            @Override
            public int compare(OptimizedCallTarget o1, OptimizedCallTarget o2) {
                return o2.callCount - o1.callCount;
            }
        });

        int totalCallCount = 0;
        int totalInlinedCallSiteCount = 0;
        int totalNotInlinedCallSiteCount = 0;
        int totalNodeCount = 0;
        int totalInvalidationCount = 0;

        OUT.println();
        OUT.printf("%-50s | %-10s | %s / %s | %s | %s\n", "Call Target", "Call Count", "Calls Sites Inlined", "Not Inlined", "Node Count", "Inv");
        for (OptimizedCallTarget callTarget : sortedCallTargets) {
            if (callTarget.callCount == 0) {
                continue;
            }

            int notInlinedCallSiteCount = TruffleInliningImpl.getInlinableCallSites(callTarget, callTarget).size();
            int nodeCount = NodeUtil.countNodes(callTarget.getRootNode(), null, true);
            int inlinedCallSiteCount = countInlinedNodes(callTarget.getRootNode());
            String comment = callTarget.installedCode == null ? " int" : "";
            comment += callTarget.compilationEnabled ? "" : " fail";
            OUT.printf("%-50s | %10d | %15d | %15d | %10d | %3d%s\n", callTarget.getRootNode(), callTarget.callCount, inlinedCallSiteCount, notInlinedCallSiteCount, nodeCount,
                            callTarget.getCompilationProfile().getInvalidationCount(), comment);

            totalCallCount += callTarget.callCount;
            totalInlinedCallSiteCount += inlinedCallSiteCount;
            totalNotInlinedCallSiteCount += notInlinedCallSiteCount;
            totalNodeCount += nodeCount;
            totalInvalidationCount += callTarget.getCompilationProfile().getInvalidationCount();
        }
        OUT.printf("%-50s | %10d | %15d | %15d | %10d | %3d\n", "Total", totalCallCount, totalInlinedCallSiteCount, totalNotInlinedCallSiteCount, totalNodeCount, totalInvalidationCount);
    }

    private static int countInlinedNodes(Node rootNode) {
        List<CallNode> callers = NodeUtil.findAllNodeInstances(rootNode, CallNode.class);
        int count = 0;
        for (CallNode callNode : callers) {
            if (callNode.isInlined()) {
                count++;
                count += countInlinedNodes(callNode.getInlinedRoot());
            }
        }
        return count;
    }

    private static void registerCallTarget(OptimizedCallTarget callTarget) {
        callTargets.put(callTarget, 0);
    }

    private static Map<OptimizedCallTarget, Integer> callTargets;
    static {
        if (TruffleCallTargetProfiling.getValue()) {
            callTargets = new WeakHashMap<>();

            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    printProfiling();
                }
            });
        }
    }
}
