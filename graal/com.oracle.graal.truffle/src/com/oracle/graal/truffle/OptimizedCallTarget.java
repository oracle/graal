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
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public abstract class OptimizedCallTarget extends DefaultCallTarget implements LoopCountReceiver, ReplaceObserver {

    protected static final PrintStream OUT = TTY.out().out();

    protected InstalledCode installedCode;
    protected boolean compilationEnabled;
    private boolean inlined;
    protected int callCount;

    protected final CompilationProfile compilationProfile;
    protected final CompilationPolicy compilationPolicy;
    private SpeculationLog speculationLog = new SpeculationLog();
    private OptimizedCallTarget splitSource;

    public OptimizedCallTarget(RootNode rootNode, int invokeCounter, int compilationThreshold, boolean compilationEnabled, CompilationPolicy compilationPolicy) {
        super(rootNode);

        this.compilationEnabled = compilationEnabled;
        this.compilationPolicy = compilationPolicy;

        this.compilationProfile = new CompilationProfile(compilationThreshold, invokeCounter, rootNode.toString());
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

    public CompilationProfile getCompilationProfile() {
        return compilationProfile;
    }

    @Override
    public abstract Object call(PackedFrame caller, Arguments args);

    public abstract InstalledCode compile();

    public Object callInlined(PackedFrame caller, Arguments arguments) {
        if (CompilerDirectives.inInterpreter()) {
            compilationProfile.reportInlinedCall();
        }
        return executeHelper(caller, arguments);
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
        // which do not suffice the tree property.
        Set<CallNode> visitedCallNodes = new HashSet<>();

        queueCallSitesForInlining(this, getRootNode(), visitedCallNodes, queue);
        TruffleInliningProfile callSite = queue.poll();
        while (callSite != null) {
            if (callSite.isInliningAllowed()) {
                OptimizedCallNode callNode = callSite.getCallNode();
                RootNode inlinedRoot = callNode.inlineImpl().getCurrentRootNode();
                logInlined(this, callSite);
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
                    if (!call.isInlined() && !visitedCallSites.contains(call)) {
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

    protected boolean shouldCompile() {
        return compilationPolicy.shouldCompile(compilationProfile);
    }

    protected static boolean shouldInline() {
        return TruffleFunctionInlining.getValue();
    }

    protected abstract void invalidate(Node oldNode, Node newNode, CharSequence reason);

    public Object executeHelper(PackedFrame caller, Arguments args) {
        VirtualFrame frame = createFrame(getRootNode().getFrameDescriptor(), caller, args);
        return getRootNode().execute(frame);
    }

    public static FrameWithoutBoxing createFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments args) {
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
    public void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
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
            if (callNode instanceof OptimizedCallNode) {
                ((OptimizedCallNode) callNode).nodeReplaced(oldNode, newNode, reason);
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

    private static void logInlined(final OptimizedCallTarget target, TruffleInliningProfile callSite) {
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

                            if (inlinedRoot != null) {
                                Map<String, Object> properties = new LinkedHashMap<>();
                                addASTSizeProperty(callNode.getCurrentRootNode(), properties);
                                properties.putAll(callNode.createInliningProfile(target).getDebugProperties());
                                String message;
                                if (callNode.isInlined()) {
                                    message = "inline success";
                                } else {
                                    message = "inline dispatch";
                                }
                                log(2 + (depth * 2), message, callNode.getCurrentCallTarget().toString(), properties);

                                if (callNode.isInlined()) {
                                    depth++;
                                    inlinedRoot.accept(this);
                                    depth--;
                                }
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
                        if (callNode.isInlined()) {
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
            addASTSizeProperty(target.getRootNode(), properties);
            properties.put("Split#", ++splitCount);
            properties.put("Source", callNode.getEncapsulatingSourceSection());
            log(0, "split", newTarget.toString(), properties);
        }
    }

    static void addASTSizeProperty(RootNode target, Map<String, Object> properties) {
        int polymorphicCount = NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == NodeCost.POLYMORPHIC;
            }
        }, true);

        int megamorphicCount = NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == NodeCost.MEGAMORPHIC;
            }
        }, true);

        String value = String.format("%4d (%d/%d)", NodeUtil.countNodes(target.getRootNode(), OptimizedCallNodeProfile.COUNT_FILTER, true), //
                        polymorphicCount, megamorphicCount); //

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

            int nodeCount = NodeUtil.countNodes(callTarget.getRootNode(), OptimizedCallNodeProfile.COUNT_FILTER, false);
            int inlinedCallSiteCount = NodeUtil.countNodes(callTarget.getRootNode(), OptimizedCallNodeProfile.COUNT_FILTER, true);
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
