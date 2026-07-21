/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.cai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pgo.PGOUtils;
import com.oracle.svm.hosted.pgo.profiles.PGOProfilesLookup;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This is an immutable compact representation of sampling profiles. Calling contexts are stored as
 * chains of nodes starting from the entry point (root node). Each callee in a context is
 * represented as a child node of its caller, all the way down to the innermost ("deepest") callee.
 * As a consequence, multiple calling contexts that start with the same chain of calls (i.e.,
 * prefix) share the same prefix nodes in the tree (single path for the distinct prefix).
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public class PrefixTree {

    private final Map<AnalysisMethod, Node> entryPoints = new HashMap<>();
    private final Set<AnalysisMethod> sampledMethods = new HashSet<>();
    private final long totalTreeCount;

    @SuppressWarnings("this-escape")
    public PrefixTree(PGOProfilesLookup pgoProfiles) {
        populatePrefixTree(pgoProfiles);
        this.totalTreeCount = updateSubtreeCounts();
    }

    private void populatePrefixTree(PGOProfilesLookup pgoProfiles) {
        Map<NodeSourcePosition, Long> samples = pgoProfiles.getSampleCounts().orElseGet(HashMap::new);
        Map<ResolvedJavaMethod, String> methodNameCache = new HashMap<>();
        for (Map.Entry<NodeSourcePosition, Long> entry : samples.entrySet()) {
            if (ignored(entry.getKey(), methodNameCache)) {
                continue;
            }
            List<NodeSourcePosition> callContext = new ArrayList<>();
            for (NodeSourcePosition nodeSourcePosition : entry.getKey()) {
                callContext.add(nodeSourcePosition);
                sampledMethods.add(((AnalysisMethod) nodeSourcePosition.getMethod()));
            }
            NodeSourcePosition entryPoint = callContext.get(0);
            Node node = entryPoints.computeIfAbsent((AnalysisMethod) entryPoint.getMethod(), Node::new);
            node.addProfile(callContext, entry.getValue());
        }
    }

    /**
     * @return the total number of samples in the entire tree.
     */
    public long getTotalTreeCount() {
        return totalTreeCount;
    }

    private long updateSubtreeCounts() {
        long total = 0;
        for (Node node : entryPoints.values()) {
            computeSubtreeCount(node);
            total += node.subtreeCount;
        }
        return total;
    }

    private static final Set<String> METHODS_TO_IGNORE = new HashSet<>(List.of(
                    "java.lang.Object.wait",
                    "java.lang.Object.notifyAll",
                    "java.lang.Thread.sleep",
                    "java.util.concurrent.locks.LockSupport.setBlocker",
                    "java.util.concurrent.locks.LockSupport.unpark",
                    "java.util.concurrent.locks.ReentrantLock$Sync.lock",
                    "java.util.concurrent.locks.ReentrantLock.lock",
                    "java.util.concurrent.locks.ReentrantLock.tryLock",
                    "jdk.internal.misc.Unsafe.park",
                    "jdk.internal.misc.Unsafe.unpark"));

    private static boolean ignored(NodeSourcePosition context, Map<ResolvedJavaMethod, String> methodNameCache) {
        for (NodeSourcePosition position : context) {
            String name = methodNameCache.computeIfAbsent(position.getMethod(), m -> m.format("%H.%n"));
            if (METHODS_TO_IGNORE.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private void computeSubtreeCount(Node node) {
        node.subtreeCount += node.count;
        for (List<Node> nodeList : node.children.values()) {
            for (Node child : nodeList) {
                computeSubtreeCount(child);
                node.subtreeCount += child.subtreeCount;
            }
        }
    }

    public Map<AnalysisMethod, Node> entryPoints() {
        return Collections.unmodifiableMap(entryPoints);
    }

    /**
     * Determine if the given method is contained in any node of this tree.
     */
    public boolean hasMethod(HostedMethod m) {
        return sampledMethods.contains(m.wrapped);
    }

    public final class Node implements Cursor {

        private final Node parent;
        private final AnalysisMethod method;
        private final Map<Integer, List<Node>> children = new HashMap<>();
        private final boolean recursive;
        private final int bciAtParent;

        // Effectively final once PrefixTree is constructed
        private long count = 0;
        private long subtreeCount = 0;

        private HostedMethod compilationRoot;
        private State state = State.INLINED;

        private Node(AnalysisMethod method) {
            this.method = method;
            this.recursive = false;
            this.parent = null;
            bciAtParent = -1;
        }

        private Node(AnalysisMethod method, boolean recursive, Node parent, int bciAtParent) {
            this.method = method;
            this.recursive = recursive;
            this.parent = parent;
            this.bciAtParent = bciAtParent;
        }

        /**
         * Used for build report only. Checks if the given callee context is referring to an
         * existing compilation root that was reused instead of duplicating (to avoid excessive
         * recursive duplication).
         *
         * Note: recursive here includes indirect recursions of arbitrary depth.
         */
        private static boolean isReusedRecursiveMethod(PrefixTree.Node calleeContext) {
            if (!calleeContext.isRecursive()) {
                return false;
            }
            HostedMethod topOfContext = calleeContext.getCompilationRoot();
            PrefixTree.Node currentContext = calleeContext;
            while (currentContext.parent() != null) {
                currentContext = (PrefixTree.Node) currentContext.parent();
                HostedMethod candidateCompilationRoot = currentContext.getCompilationRoot();
                if (candidateCompilationRoot != null && candidateCompilationRoot.equals(topOfContext)) {
                    return true;
                }
            }
            return false;
        }

        private static void traverseAndMarkAsCold(PrefixTree.Node node) {
            node.state = State.COLD;
            for (List<PrefixTree.Node> children : node.children.values()) {
                for (PrefixTree.Node child : children) {
                    traverseAndMarkAsCold(child);
                }
            }
        }

        public long getCount() {
            return count;
        }

        private List<Node> getChildren(int bci) {
            return children.get(bci);
        }

        public Map<Integer, List<Node>> getChildren() {
            return Collections.unmodifiableMap(children);
        }

        private List<Node> find(BytecodePosition position) {
            if (position == null) {
                return null;
            }
            if (position.getCaller() == null) {
                return this.getChildren(position.getBCI());
            }
            List<Node> nodes = find(position.getCaller());
            if (nodes == null) {
                return null;
            }
            Node node = findNode(nodes, position.getMethod());
            if (node == null) {
                return null;
            }
            return node.getChildren(position.getBCI());
        }

        private Node findNode(List<Node> nodes, ResolvedJavaMethod target) {
            AnalysisMethod analysisMethod = target instanceof HostedMethod ? ((HostedMethod) target).wrapped : ((AnalysisMethod) target);
            for (Node node : nodes) {
                if (node.method.equals(analysisMethod)) {
                    return node;
                }
            }
            return null;
        }

        @Override
        public AnalysisMethod getMethod() {
            return method;
        }

        /**
         * @param callContext caller-first {@link NodeSourcePosition}! A regular
         *            {@link NodeSourcePosition} keeps a reference to its caller (i.e. it is
         *            callee-first). This method expects a caller-first list of positions in order
         *            to populate the prefix tree, thus a "reversed" list of
         *            {@link NodeSourcePosition} is expected .
         */
        private void addProfile(List<NodeSourcePosition> callContext, long sampleCount) {
            Node currentNode = this;
            Set<AnalysisMethod> methodsInContext = new HashSet<>();
            for (int i = 0; i < callContext.size() - 1; i++) {
                methodsInContext.add(currentNode.method);
                NodeSourcePosition currentPosition = callContext.get(i);
                NodeSourcePosition positionToAdd = callContext.get(i + 1);
                currentNode = currentNode.findOrCreateChild(currentPosition, (AnalysisMethod) positionToAdd.getMethod(), methodsInContext);
            }
            currentNode.count += sampleCount;
        }

        private Node findOrCreateChild(NodeSourcePosition currentPosition, AnalysisMethod methodToAdd, Set<AnalysisMethod> methodsInContext) {
            List<Node> bciChildren = this.children.computeIfAbsent(currentPosition.getBCI(), _ -> new ArrayList<>());
            for (Node bciChild : bciChildren) {
                if (bciChild.getMethod().equals(methodToAdd)) {
                    return bciChild;
                }
            }
            Node newNode = new Node(methodToAdd, methodsInContext.contains(methodToAdd), this, currentPosition.getBCI());
            bciChildren.add(newNode);
            return newNode;
        }

        @Override
        public JavaMethodProfile profileFor(HostedUniverse universe, BytecodePosition position) {
            List<Node> candidates = find(position);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            HashMap<HostedMethod, Long> profile = new HashMap<>();
            for (Node candidate : candidates) {
                profile.put(universe.lookup(candidate.getMethod()), candidate.subtreeCount);
            }
            return PGOUtils.createJavaMethodProfile(profile);

        }

        @Override
        public Node findForMethod(BytecodePosition position, ResolvedJavaMethod target) {
            List<Node> nodes = find(position);
            if (nodes == null) {
                return null;
            }
            return findNode(nodes, target);
        }

        @Override
        public Cursor parent() {
            return parent;
        }

        @Override
        public boolean isHot(double hotContextRatioThreshold) {
            return subtreeRatio() >= hotContextRatioThreshold;
        }

        public boolean isRecursive() {
            return recursive;
        }

        private double subtreeRatio() {
            return ((double) subtreeCount) / totalTreeCount;
        }

        @Override
        public double ratio(NodeSourcePosition callPosition, ResolvedJavaMethod dispatchedMethod) {
            Node callee = findForMethod(callPosition, dispatchedMethod);
            if (callee == null) {
                return 0;
            }
            return ((double) callee.subtreeCount) / this.subtreeCount;
        }

        @Override
        /*
         * TODO BS GR-42484 move all tuning-related decisions into a Policy object specific to the
         * (aot-sampling-)inliner.
         */
        public boolean safeToReuse() {
            if (!isRecursive()) {
                return false;
            }
            List<AnalysisMethod> callerMethods = new ArrayList<>();
            List<Integer> callerBCIs = new ArrayList<>();
            Node current = this;
            while (!Objects.equals(current.parent.method, method)) {
                callerMethods.add(current.parent.method);
                callerBCIs.add(current.parent.bciAtParent);
                // parent cannot be null because this.isRecursive() == true
                current = current.parent;
            }
            Collections.reverse(callerMethods);
            Collections.reverse(callerBCIs);
            current = this;
            for (int i = 0; i < callerMethods.size(); i++) {
                AnalysisMethod analysisMethod = callerMethods.get(i);
                int bci = callerBCIs.get(i);
                current = findMatchingChild(current, analysisMethod, bci);
                if (current == null) {
                    return false;
                }
            }
            return true;
        }

        private Node findMatchingChild(Node current, AnalysisMethod analysisMethod, int bci) {
            List<Node> bciChildren = current.children.get(bci);
            if (bciChildren == null) {
                return null;
            }
            for (Node child : bciChildren) {
                if (child.method.equals(analysisMethod)) {
                    return child;
                }
            }
            return null;
        }

        /**
         * @return the total number of samples in the subtree rooted in this {@link Node node}.
         */
        @Override
        public long getSubtreeCount() {
            return subtreeCount;
        }

        @Override
        public void updateState(HostedMethod compilationRootParam, State newState) {
            this.compilationRoot = compilationRootParam;
            this.state = newState;
            if (state == PrefixTree.State.COLD_ROOT || isReusedRecursiveMethod(this)) {
                for (List<PrefixTree.Node> nodes : children.values()) {
                    for (PrefixTree.Node child : nodes) {
                        traverseAndMarkAsCold(child);
                    }
                }
            }
        }

        /**
         * Traverses this nodes parent chain looking for a compilation root that equals the argument
         * target. If such a node is found, the compilation root {@link HostedMethod} is returned as
         * it might be re-used for a call site to "tie down" the recursive calls (i.e. stop the
         * duplication).
         *
         * Note: recursive here includes indirect recursions of arbitrary depth.
         *
         * @param target Which hosted method are we looking for.
         * @return An existing compilation root to potentially be reused, null if not found.
         */
        @Override
        public HostedMethod findCompilationRootParent(HostedMethod target) {
            PrefixTree.Node currentCursor = this;
            while (currentCursor != null) {
                HostedMethod currentCompilationRoot = currentCursor.getCompilationRoot();
                if (currentCompilationRoot != null && Objects.equals(currentCompilationRoot.wrapped, target.wrapped)) {
                    return currentCompilationRoot;
                }
                currentCursor = (Node) currentCursor.parent();
            }
            return null;
        }

        public HostedMethod getCompilationRoot() {
            return compilationRoot;
        }

        public State getState() {
            return state;
        }
    }

    public interface Cursor {
        AnalysisMethod getMethod();

        JavaMethodProfile profileFor(HostedUniverse universe, BytecodePosition position);

        Cursor findForMethod(BytecodePosition position, ResolvedJavaMethod method);

        Cursor parent();

        boolean isHot(double hotContextRatioThreshold);

        double ratio(NodeSourcePosition callPosition, ResolvedJavaMethod dispatchedMethod);

        /**
         * TODO BS GR-42484 move all tuning-related decisions into a Policy object specific to the
         * (aot-sampling-)inliner.
         *
         * @return Whether we consider the method at this cursor safe to reuse instead of
         *         duplicating. Useful for limiting duplication for recursive call.
         */
        boolean safeToReuse();

        long getSubtreeCount();

        void updateState(HostedMethod callee, State newState);

        HostedMethod findCompilationRootParent(HostedMethod target);
    }

    public enum State {
        HOT_ROOT,
        COLD_ROOT,
        INLINED,
        COLD
    }
}
