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
    private boolean compilationEnabled;
    private int callCount;

    private final TruffleCompiler compiler;
    private final CompilationProfile compilationProfile;
    private final CompilationPolicy compilationPolicy;
    private final SpeculationLog speculationLog = new SpeculationLog();

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
    }

    @Override
    public String toString() {
        String superString = super.toString();
        if (installedCode != null) {
            superString += " <compiled>";
        }
        return superString;
    }

    public boolean isOptimized() {
        return installedCode != null || installedCodeTask != null;
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
        invalidate("Compiled code invalidated");
        return call(caller, args);
    }

    private void invalidate(String reason) {
        InstalledCode m = this.installedCode;
        if (m != null) {
            CompilerAsserts.neverPartOfCompilation();
            installedCode = null;
            compilationProfile.reportInvalidated();
            if (TraceTruffleCompilation.getValue()) {
                logOptimizedInvalidated(this, reason);
            }
        }
        cancelInstalledTask(reason);
    }

    private void cancelInstalledTask(String reason) {
        Future<InstalledCode> task = this.installedCodeTask;
        if (task != null) {
            task.cancel(true);
            this.installedCodeTask = null;
            logOptimizingCancelled(this, reason);
            compilationProfile.reportInvalidated();
        }
    }

    private Object interpreterCall(PackedFrame caller, Arguments args) {
        CompilerAsserts.neverPartOfCompilation();
        compilationProfile.reportInterpreterCall();

        if (compilationEnabled && compilationPolicy.shouldCompile(compilationProfile)) {
            InstalledCode code = compile();
            if (code != null && code.isValid()) {
                this.installedCode = code;
                try {
                    return code.execute(this, caller, args);
                } catch (InvalidInstalledCodeException ex) {
                    return compiledCodeInvalidated(caller, args);
                }
            }
        }
        return executeHelper(caller, args);
    }

    public void performInlining() {
        if (!TruffleCompilerOptions.TruffleFunctionInlining.getValue()) {
            return;
        }
        PriorityQueue<TruffleInliningProfile> queue = new PriorityQueue<>();
        queueCallSitesForInlining(getRootNode(), queue);

        TruffleInliningProfile callSite = queue.poll();
        while (callSite != null) {
            if (callSite.isInliningAllowed()) {
                OptimizedCallNode callNode = callSite.getCallNode();
                logInlined(callSite);
                RootNode inlinedRoot = callNode.inlineImpl().getInlinedRoot();
                assert inlinedRoot != null;
                queueCallSitesForInlining(inlinedRoot, queue);
            } else {
                logInliningFailed(callSite);
            }
            callSite = queue.poll();
        }
    }

    private static void queueCallSitesForInlining(RootNode rootNode, final PriorityQueue<TruffleInliningProfile> queue) {
        rootNode.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedCallNode) {
                    OptimizedCallNode call = ((OptimizedCallNode) node);
                    if (call.isInlinable() && !call.isInlined()) {
                        queue.add(call.createInliningProfile());
                    } else if (call.getInlinedRoot() != null) {
                        call.getInlinedRoot().accept(this);
                    }
                }
                return true;
            }
        });
    }

    private boolean isCompiling() {
        Future<InstalledCode> codeTask = this.installedCodeTask;
        if (codeTask != null) {
            if (codeTask.isCancelled()) {
                installedCodeTask = null;
                return false;
            }
            return true;
        }
        return false;
    }

    public InstalledCode compile() {
        if (isCompiling()) {
            if (installedCodeTask.isDone()) {
                return receiveInstalledCode();
            }
            return null;
        } else {
            logOptimizing(this);
            performInlining();
            this.installedCodeTask = compiler.compile(this);
            if (!TruffleBackgroundCompilation.getValue()) {
                return receiveInstalledCode();
            }
        }
        return null;
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
    public void nodeReplaced(Node oldNode, Node newNode, String reason) {
        compilationProfile.reportNodeReplaced();
        invalidate(reason);
    }

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    private static void logInliningFailed(TruffleInliningProfile callSite) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleCompilationDetails.getValue()) {
            log(0, "inline failed", callSite.getCallNode().getExecutedCallTarget().toString(), callSite.getDebugProperties());
        }
    }

    private static void logOptimizing(OptimizedCallTarget target) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleCompilationDetails.getValue()) {
            log(0, "optimizing", target.toString(), null);
        }
    }

    private static void logOptimizedInvalidated(OptimizedCallTarget target, String reason) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleCompilationDetails.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("Invalidation#", target.compilationProfile.getInvalidationCount());
            properties.put("Replace#", target.compilationProfile.getNodeReplaceCount());
            properties.put("Reason", reason);
            log(0, "invalidated", target.toString(), properties);
        }
    }

    private static void logOptimizingCancelled(OptimizedCallTarget target, String reason) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleCompilationDetails.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("Invalidation#", target.compilationProfile.getInvalidationCount());
            properties.put("Replace#", target.compilationProfile.getNodeReplaceCount());
            properties.put("Reason", reason);
            log(0, "optimizing stop", target.toString(), properties);
        }
    }

    static void logOptimized(OptimizedCallTarget target, Map<String, Object> properties) {
        if (TraceTruffleCompilationDetails.getValue() || TraceTruffleCompilation.getValue()) {
            log(0, "optimizing done", target.toString(), properties);
        }
    }

    private static void logInlined(TruffleInliningProfile callSite) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleInlining.getValue()) {
            log(0, "inline success", callSite.getCallNode().getExecutedCallTarget().toString(), callSite.getDebugProperties());
        }
    }

    static void logSplit(@SuppressWarnings("unused") OptimizedCallTarget target, OptimizedCallTarget newTarget) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleInlining.getValue()) {
            log(0, "split", newTarget.toString(), null);
        }
    }

    static void log(int indent, String msg, String details, Map<String, Object> properties) {
        OUT.printf("[truffle] %-16s ", msg);
        for (int i = 0; i < indent; i++) {
            OUT.print(" ");
        }
        OUT.printf("%-" + (60 - indent) + "s", details);
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                OUT.print("|");
                OUT.print(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                OUT.printf(" %" + length + "s ", propertyBuilder.toString());
            }
        }
        OUT.println();
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
        int totalNodeCount = 0;
        int totalInvalidationCount = 0;

        OUT.println();
        OUT.printf("%-50s | %-10s | %s / %s | %s | %s\n", "Call Target", "Call Count", "Calls Sites Inlined", "Not Inlined", "Node Count", "Inv");
        for (OptimizedCallTarget callTarget : sortedCallTargets) {
            if (callTarget.callCount == 0) {
                continue;
            }

            int nodeCount = NodeUtil.countNodes(callTarget.getRootNode(), null, true);
            int inlinedCallSiteCount = countInlinedNodes(callTarget.getRootNode());
            String comment = callTarget.installedCode == null ? " int" : "";
            comment += callTarget.compilationEnabled ? "" : " fail";
            OUT.printf("%-50s | %10d | %15d | %10d | %3d%s\n", callTarget.getRootNode(), callTarget.callCount, inlinedCallSiteCount, nodeCount,
                            callTarget.getCompilationProfile().getInvalidationCount(), comment);

            totalCallCount += callTarget.callCount;
            totalInlinedCallSiteCount += inlinedCallSiteCount;
            totalNodeCount += nodeCount;
            totalInvalidationCount += callTarget.getCompilationProfile().getInvalidationCount();
        }
        OUT.printf("%-50s | %10d | %15d | %10d | %3d\n", "Total", totalCallCount, totalInlinedCallSiteCount, totalNodeCount, totalInvalidationCount);
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
