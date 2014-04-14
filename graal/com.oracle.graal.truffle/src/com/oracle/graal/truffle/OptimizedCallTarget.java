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
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public abstract class OptimizedCallTarget extends DefaultCallTarget implements LoopCountReceiver, ReplaceObserver {

    protected static final PrintStream OUT = TTY.out().out();

    protected InstalledCode installedCode;
    protected boolean compilationEnabled;
    protected int callCount;
    protected boolean inliningPerformed;
    protected final CompilationProfile compilationProfile;
    protected final CompilationPolicy compilationPolicy;
    private OptimizedCallTarget splitSource;
    private final AtomicInteger callSitesKnown = new AtomicInteger(0);

    public OptimizedCallTarget(RootNode rootNode, int invokeCounter, int compilationThreshold, boolean compilationEnabled, CompilationPolicy compilationPolicy) {
        super(rootNode);
        this.compilationEnabled = compilationEnabled;
        this.compilationPolicy = compilationPolicy;
        this.compilationProfile = new CompilationProfile(compilationThreshold, invokeCounter, rootNode.toString());
        if (TruffleCallTargetProfiling.getValue()) {
            registerCallTarget(this);
        }
    }

    public final int getKnownCallSiteCount() {
        return callSitesKnown.get();
    }

    public final void incrementKnownCallSites() {
        callSitesKnown.incrementAndGet();
    }

    public final void decrementKnownCallSites() {
        callSitesKnown.decrementAndGet();
    }

    public final OptimizedCallTarget getSplitSource() {
        return splitSource;
    }

    public final void setSplitSource(OptimizedCallTarget splitSource) {
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

    public CompilationProfile getCompilationProfile() {
        return compilationProfile;
    }

    @Override
    public abstract Object call(Object[] args);

    public abstract InstalledCode compile();

    public final Object callInlined(Object[] arguments) {
        if (CompilerDirectives.inInterpreter()) {
            compilationProfile.reportInlinedCall();
        }
        return executeHelper(arguments);
    }

    public final void performInlining() {
        if (!TruffleFunctionInlining.getValue()) {
            return;
        }
        if (inliningPerformed) {
            return;
        }

        TruffleInliningHandler handler = new TruffleInliningHandler(new DefaultInliningPolicy());
        TruffleInliningResult result = handler.decideInlining(this, 0);
        performInlining(result);
        logInliningDecision(result, 0);
    }

    private void performInlining(TruffleInliningResult result) {
        if (inliningPerformed) {
            return;
        }
        inliningPerformed = true;
        for (TruffleInliningProfile profile : result) {
            profile.getCallNode().inline();
            TruffleInliningResult recursiveResult = profile.getRecursiveResult();
            if (recursiveResult != null) {
                recursiveResult.getCallTarget().performInlining(recursiveResult);
            }
        }
    }

    protected abstract void invalidate(Node oldNode, Node newNode, CharSequence reason);

    public final Object executeHelper(Object[] args) {
        VirtualFrame frame = createFrame(getRootNode().getFrameDescriptor(), args);
        return callProxy(frame);
    }

    public static FrameWithoutBoxing createFrame(FrameDescriptor descriptor, Object[] args) {
        return new FrameWithoutBoxing(descriptor, args);
    }

    public static FrameWithoutBoxing createMaterializedFrame(FrameDescriptor descriptor, Object[] args) {
        return new FrameWithoutBoxing(descriptor, args);
    }

    @Override
    public void reportLoopCount(int count) {
        compilationProfile.reportLoopCount(count);
    }

    @Override
    public void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        compilationProfile.reportNodeReplaced();
        invalidate(oldNode, newNode, reason);
    }

    public abstract SpeculationLog getSpeculationLog();

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        addASTSizeProperty(this, properties);
        properties.putAll(getCompilationProfile().getDebugProperties());
        return properties;

    }

    private static void logInliningDecision(TruffleInliningResult result, int depth) {
        if (!TraceTruffleInlining.getValue()) {
            return;
        }

        logInliningStart(result.getCallTarget());
        logInliningDecisionRecursive(result, depth);
        logInliningDone(result.getCallTarget());
    }

    private static void logInliningDecisionRecursive(TruffleInliningResult result, int depth) {
        List<OptimizedCallNode> callNodes = searchCallNodes(result.getCallTarget());
        for (OptimizedCallNode callNode : callNodes) {
            TruffleInliningProfile profile = result.getProfiles().get(callNode);
            boolean inlined = result.isInlined(callNode);
            String msg = inlined ? "inline success" : "inline failed";
            logInlinedImpl(msg, profile, depth);
            if (profile.getRecursiveResult() != null && inlined) {
                logInliningDecisionRecursive(profile.getRecursiveResult(), depth + 1);
            }
        }
    }

    private static List<OptimizedCallNode> searchCallNodes(final OptimizedCallTarget target) {
        final List<OptimizedCallNode> callNodes = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedCallNode) {
                    callNodes.add((OptimizedCallNode) node);
                }
                return true;
            }
        });
        return callNodes;
    }

    private static void logInlinedImpl(String status, TruffleInliningProfile profile, int depth) {
        Map<String, Object> properties = new LinkedHashMap<>();
        addASTSizeProperty(profile.getCallNode().getCurrentCallTarget(), properties);
        if (profile != null) {
            properties.putAll(profile.getDebugProperties());
        }
        log((depth * 2), status, profile.getCallNode().getCurrentCallTarget().toString(), properties);
    }

    private static void logInliningStart(OptimizedCallTarget target) {
        if (TraceTruffleInlining.getValue()) {
            log(0, "inline start", target.toString(), target.getDebugProperties());
        }
    }

    private static void logInliningDone(OptimizedCallTarget target) {
        if (TraceTruffleInlining.getValue()) {
            log(0, "inline done", target.toString(), target.getDebugProperties());
        }
    }

    protected static void logOptimizingQueued(OptimizedCallTarget target) {
        if (TraceTruffleCompilationDetails.getValue()) {
            log(0, "opt queued", target.toString(), target.getDebugProperties());
        }
    }

    protected static void logOptimizingUnqueued(OptimizedCallTarget target, Node oldNode, Node newNode, CharSequence reason) {
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

    protected static void logOptimizedInvalidated(OptimizedCallTarget target, Node oldNode, Node newNode, CharSequence reason) {
        if (TraceTruffleCompilation.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addReplaceProperties(properties, oldNode, newNode);
            properties.put("Reason", reason);
            log(0, "opt invalidated", target.toString(), properties);
        }
    }

    protected static void logOptimizingFailed(OptimizedCallTarget callSite, CharSequence reason) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Reason", reason);
        log(0, "opt fail", callSite.toString(), properties);
    }

    static void logOptimizingDone(OptimizedCallTarget target, Map<String, Object> properties) {
        if (TraceTruffleCompilationDetails.getValue() || TraceTruffleCompilation.getValue()) {
            log(0, "opt done", target.toString(), properties);
        }
        if (TraceTruffleCompilationPolymorphism.getValue()) {

            target.getRootNode().accept(new NodeVisitor() {
                public boolean visit(Node node) {
                    NodeCost kind = node.getCost();
                    if (kind == NodeCost.POLYMORPHIC || kind == NodeCost.MEGAMORPHIC) {
                        Map<String, Object> props = new LinkedHashMap<>();
                        props.put("simpleName", node.getClass().getSimpleName());
                        String msg = kind == NodeCost.MEGAMORPHIC ? "megamorphic" : "polymorphic";
                        log(0, msg, node.toString(), props);
                    }
                    if (node instanceof CallNode) {
                        CallNode callNode = (CallNode) node;
                        if (callNode.isInliningForced()) {
                            callNode.getCurrentRootNode().accept(this);
                        }
                    }
                    return true;
                }
            });

        }
    }

    private static int splitCount = 0;

    static void logSplit(OptimizedCallNode callNode, OptimizedCallTarget target, OptimizedCallTarget newTarget) {
        if (TraceTruffleSplitting.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addASTSizeProperty(target, properties);
            properties.put("Split#", ++splitCount);
            properties.put("Source", callNode.getEncapsulatingSourceSection());
            log(0, "split", newTarget.toString(), properties);
        }
    }

    static void addASTSizeProperty(OptimizedCallTarget target, Map<String, Object> properties) {
        int polymorphicCount = OptimizedCallUtils.countNodes(target, new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == NodeCost.POLYMORPHIC;
            }
        }, true);

        int megamorphicCount = OptimizedCallUtils.countNodes(target, new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == NodeCost.MEGAMORPHIC;
            }
        }, true);

        String value = String.format("%4d (%d/%d)", OptimizedCallUtils.countNonTrivialNodes(target, true), polymorphicCount, megamorphicCount);
        properties.put("ASTSize", value);
    }

    static void log(int indent, String msg, String details, Map<String, Object> properties) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[truffle] %-16s ", msg));
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        sb.append(String.format("%-" + (60 - indent) + "s", details));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
                sb.append(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                sb.append(String.format(" %" + length + "s ", propertyBuilder.toString()));
            }
        }
        OUT.println(sb.toString());
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

            int nodeCount = OptimizedCallUtils.countNonTrivialNodes(callTarget, true);
            String comment = callTarget.installedCode == null ? " int" : "";
            comment += callTarget.compilationEnabled ? "" : " fail";
            OUT.printf("%-50s | %10d | %15d | %10d | %3d%s\n", callTarget.getRootNode(), callTarget.callCount, nodeCount, nodeCount, callTarget.getCompilationProfile().getInvalidationCount(), comment);

            totalCallCount += callTarget.callCount;
            totalInlinedCallSiteCount += nodeCount;
            totalNodeCount += nodeCount;
            totalInvalidationCount += callTarget.getCompilationProfile().getInvalidationCount();
        }
        OUT.printf("%-50s | %10d | %15d | %10d | %3d\n", "Total", totalCallCount, totalInlinedCallSiteCount, totalNodeCount, totalInvalidationCount);
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
