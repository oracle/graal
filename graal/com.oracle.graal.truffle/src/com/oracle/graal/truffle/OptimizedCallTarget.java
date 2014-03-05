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
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public final class OptimizedCallTarget extends DefaultCallTarget implements LoopCountReceiver, ReplaceObserver {

    private static final PrintStream OUT = TTY.out().out();

    private InstalledCode installedCode;
    private Future<InstalledCode> installedCodeTask;
    private boolean compilationEnabled;
    private boolean inlined;
    private int callCount;

    private final TruffleCompiler compiler;
    private final CompilationProfile compilationProfile;
    private final CompilationPolicy compilationPolicy;
    private final SpeculationLog speculationLog = new SpeculationLog();
    private OptimizedCallTarget splitSource;

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

    public OptimizedCallTarget getSplitSource() {
        return splitSource;
    }

    public void setSplitSource(OptimizedCallTarget splitSource) {
        this.splitSource = splitSource;
    }

    @Override
    public String toString() {
        String superString = super.toString();
        if (installedCode != null) {
            superString += " <compiled>";
        }
        if (splitSource != null) {
            superString += " <split>";
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
        invalidate(null, null, "Compiled code invalidated");
        return call(caller, args);
    }

    private void invalidate(Node oldNode, Node newNode, String reason) {
        InstalledCode m = this.installedCode;
        if (m != null) {
            CompilerAsserts.neverPartOfCompilation();
            installedCode = null;
            compilationProfile.reportInvalidated();
            logOptimizedInvalidated(this, oldNode, newNode, reason);
        }
        cancelInstalledTask(oldNode, newNode, reason);
    }

    private void cancelInstalledTask(Node oldNode, Node newNode, String reason) {
        Future<InstalledCode> task = this.installedCodeTask;
        if (task != null) {
            task.cancel(true);
            this.installedCodeTask = null;
            logOptimizingUnqueued(this, oldNode, newNode, reason);
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
        if (inlined) {
            return;
        }
        inlined = true;

        logInliningStart(this);
        PriorityQueue<TruffleInliningProfile> queue = new PriorityQueue<>();

        // Used to avoid running in cycles or inline nodes in Truffle trees
        // which do not suffice the tree property twice.
        Set<CallNode> visitedCallNodes = new HashSet<>();

        queueCallSitesForInlining(this, getRootNode(), visitedCallNodes, queue);
        TruffleInliningProfile callSite = queue.poll();
        while (callSite != null) {
            if (callSite.isInliningAllowed()) {
                OptimizedCallNode callNode = callSite.getCallNode();
                logInlined(this, callSite);
                RootNode inlinedRoot = callNode.inlineImpl().getCurrentRootNode();
                assert inlinedRoot != null;
                queueCallSitesForInlining(this, inlinedRoot, visitedCallNodes, queue);
            } else {
                logInliningFailed(callSite);
            }
            callSite = queue.poll();
        }
        logInliningDone(this);
    }

    private static void queueCallSitesForInlining(final OptimizedCallTarget target, RootNode rootNode, final Set<CallNode> visitedCallSites, final PriorityQueue<TruffleInliningProfile> queue) {
        rootNode.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedCallNode) {
                    OptimizedCallNode call = ((OptimizedCallNode) node);
                    if (call.isInlinable() && !call.isInlined() && !visitedCallSites.contains(call)) {
                        queue.add(call.createInliningProfile(target));
                        visitedCallSites.add(call);
                    }
                    RootNode root = call.getCurrentRootNode();
                    if (root != null && call.isInlined()) {
                        root.accept(this);
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
            performInlining();
            logOptimizingQueued(this);
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
            logOptimizingFailed(this, e.getMessage());
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

        // delegate to inlined call sites
        for (CallNode callNode : getRootNode().getCachedCallNodes()) {
            if (callNode.isInlined()) {
                callNode.getRootNode().reportLoopCount(count);
            }
        }
    }

    @Override
    public void nodeReplaced(Node oldNode, Node newNode, String reason) {
        compilationProfile.reportNodeReplaced();
        invalidate(oldNode, newNode, reason);

        // delegate to inlined call sites
        for (CallNode callNode : getRootNode().getCachedCallNodes()) {
            if (callNode.isInlined()) {
                CallTarget target = callNode.getRootNode().getCallTarget();
                if (target instanceof ReplaceObserver) {
                    ((ReplaceObserver) target).nodeReplaced(oldNode, newNode, reason);
                }
            }
        }
    }

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        addASTSizeProperty(getRootNode(), properties);
        properties.putAll(getCompilationProfile().getDebugProperties());
        return properties;

    }

    private static void logInliningFailed(TruffleInliningProfile callSite) {
        if (TraceTruffleInliningDetails.getValue()) {
            log(2, "inline failed", callSite.getCallNode().getCurrentCallTarget().toString(), callSite.getDebugProperties());
        }
    }

    private static void logInlined(@SuppressWarnings("unused") final OptimizedCallTarget target, TruffleInliningProfile callSite) {
        if (TraceTruffleInliningDetails.getValue() || TraceTruffleInlining.getValue()) {
            log(2, "inline success", callSite.getCallNode().getCurrentCallTarget().toString(), callSite.getDebugProperties());

            if (TraceTruffleInliningDetails.getValue()) {
                RootNode root = callSite.getCallNode().getCurrentCallTarget().getRootNode();
                root.accept(new NodeVisitor() {
                    int depth = 1;

                    public boolean visit(Node node) {
                        if (node instanceof OptimizedCallNode) {
                            OptimizedCallNode callNode = ((OptimizedCallNode) node);
                            RootNode inlinedRoot = callNode.getCurrentRootNode();

                            if (inlinedRoot != null && callNode.isInlined()) {
                                Map<String, Object> properties = new LinkedHashMap<>();
                                addASTSizeProperty(callNode.getCurrentRootNode(), properties);
                                log(2 + (depth * 2), "inline success", callNode.getCurrentCallTarget().toString(), properties);
                                depth++;
                                inlinedRoot.accept(this);
                                depth--;
                            }
                        }
                        return true;
                    }
                });
            }
        }
    }

    private static void logInliningStart(OptimizedCallTarget target) {
        if (TraceTruffleInliningDetails.getValue()) {
            log(0, "inline start", target.toString(), target.getDebugProperties());
        }
    }

    private static void logInliningDone(OptimizedCallTarget target) {
        if (TraceTruffleInliningDetails.getValue()) {
            log(0, "inline done", target.toString(), target.getDebugProperties());
        }
    }

    private static void logOptimizingQueued(OptimizedCallTarget target) {
        if (TraceTruffleCompilationDetails.getValue()) {
            log(0, "opt queued", target.toString(), target.getDebugProperties());
        }
    }

    private static void logOptimizingUnqueued(OptimizedCallTarget target, Node oldNode, Node newNode, String reason) {
        if (TraceTruffleCompilationDetails.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addReplaceProperties(properties, oldNode, newNode);
            properties.put("Reason", reason);
            log(0, "opt unqueued", target.toString(), properties);
        }
    }

    private static void addReplaceProperties(Map<String, Object> properties, Node oldNode, Node newNode) {
        if (oldNode != null && newNode != null) {
            properties.put("OldClass", oldNode.getClass().getSimpleName());
            properties.put("NewClass", newNode.getClass().getSimpleName());
            properties.put("Node", newNode);
        }
    }

    static void logOptimizingStart(OptimizedCallTarget target) {
        if (TraceTruffleCompilationDetails.getValue()) {
            log(0, "opt start", target.toString(), target.getDebugProperties());
        }
    }

    private static void logOptimizedInvalidated(OptimizedCallTarget target, Node oldNode, Node newNode, String reason) {
        if (TraceTruffleCompilation.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addReplaceProperties(properties, oldNode, newNode);
            properties.put("Reason", reason);
            log(0, "opt invalidated", target.toString(), properties);
        }
    }

    private static void logOptimizingFailed(OptimizedCallTarget callSite, String reason) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Reason", reason);
        log(0, "opt fail", callSite.toString(), properties);
    }

    static void logOptimizingDone(OptimizedCallTarget target, Map<String, Object> properties) {
        if (TraceTruffleCompilationDetails.getValue() || TraceTruffleCompilation.getValue()) {
            log(0, "opt done", target.toString(), properties);
        }
    }

    private static int splitCount = 0;

    static void logSplit(OptimizedCallNode callNode, OptimizedCallTarget target, OptimizedCallTarget newTarget) {
        if (TraceTruffleSplitting.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addASTSizeProperty(target.getRootNode(), properties);
            properties.put("Split#", ++splitCount);
            properties.put("Source", callNode.getEncapsulatingSourceSection());
            log(0, "split", newTarget.toString(), properties);
        }
    }

    static void addASTSizeProperty(RootNode target, Map<String, Object> properties) {
        String value = String.format("%4d (%d/%d)", NodeUtil.countNodes(target.getRootNode(), null, true), //
                        NodeUtil.countNodes(target.getRootNode(), null, Kind.POLYMORPHIC, true), NodeUtil.countNodes(target.getRootNode(), null, Kind.GENERIC, true)); //

        properties.put("ASTSize", value);
    }

    static synchronized void log(int indent, String msg, String details, Map<String, Object> properties) {
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
                RootNode root = callNode.getCurrentRootNode();
                if (root != null) {
                    count += countInlinedNodes(root);
                }
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
