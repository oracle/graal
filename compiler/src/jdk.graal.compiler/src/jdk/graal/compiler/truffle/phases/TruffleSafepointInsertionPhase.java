/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.nodes.TruffleSafepointNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Adds Truffle safepoints to loops and methods ends.
 *
 * Invocations of TruffleSafepoint.poll are removed during PE. This phase ensures that they are
 * efficiently added again at method and loop ends.
 */
public final class TruffleSafepointInsertionPhase extends Phase {

    private final Providers providers;
    private final ResolvedJavaType nodeType;
    private final ResolvedJavaType rootNodeType;
    private final ResolvedJavaType osrRootNodeType;
    private final ResolvedJavaType callTargetClass;
    private final ResolvedJavaField rootNodeField;
    private final ResolvedJavaField parentField;
    private final ResolvedJavaField loopNodeField;
    private final ResolvedJavaMethod executeRootMethod;

    public TruffleSafepointInsertionPhase(KnownTruffleTypes types, Providers providers) {
        this.providers = providers;
        this.nodeType = types.Node;
        this.rootNodeType = types.RootNode;
        this.osrRootNodeType = types.BaseOSRRootNode;
        this.callTargetClass = types.OptimizedCallTarget;
        this.executeRootMethod = types.OptimizedCallTarget_executeRootNode;
        this.rootNodeField = types.OptimizedCallTarget_rootNode;
        this.parentField = types.Node_parent;
        this.loopNodeField = types.BaseOSRRootNode_loopNode;
    }

    public static boolean allowsSafepoints(StructuredGraph graph) {
        // only allowed in Truffle compilations.
        return TruffleCompilation.isTruffleCompilation(graph);
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        if (!allowsSafepoints(graph)) {
            /*
             * This filters HotSpot call stubs and SVM method typed signatures.
             */
            return;
        }

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            try (DebugCloseable s = returnNode.withNodeSourcePosition()) {
                insertSafepoint(graph, returnNode);
            }
        }
        for (LoopBeginNode loopBeginNode : graph.getNodes(LoopBeginNode.TYPE)) {
            for (LoopEndNode loopEndNode : loopBeginNode.loopEnds()) {
                if (loopEndNode.getGuestSafepointState().canSafepoint()) {
                    try (DebugCloseable s = loopEndNode.withNodeSourcePosition()) {
                        insertSafepoint(graph, loopEndNode);
                    }
                }
            }
        }

        for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
            try (DebugCloseable s = callTarget.withNodeSourcePosition()) {
                insertSafepoint(graph, (FixedNode) callTarget.invoke());
            }
        }
    }

    private void insertSafepoint(StructuredGraph graph, FixedNode returnNode) {
        ConstantNode node = findTruffleNode(returnNode);
        if (node == null) {
            // we did not found a truffle node in any frame state so we need to use the root node of
            // the compilation unit
            JavaConstant javaConstant = TruffleCompilation.lookupCompilable(graph).asJavaConstant();
            JavaConstant rootNode = providers.getConstantReflection().readFieldValue(rootNodeField, javaConstant);
            ObjectStamp stamp = StampFactory.object(TypeReference.createExactTrusted(rootNodeField.getType().resolve(callTargetClass)));
            node = new ConstantNode(skipOSRRoot(rootNode), stamp);
        }

        assert node.asJavaConstant() != null : "must be a java constant";
        assert nodeType.isAssignableFrom(node.stamp(NodeView.DEFAULT).javaType(providers.getMetaAccess())) : "must be a truffle node";
        node = graph.addOrUnique(node);
        graph.addBeforeFixed(returnNode, graph.add(new TruffleSafepointNode(node)));
    }

    private ConstantNode findTruffleNode(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof NodeWithState) {
                for (FrameState innerMostState : ((NodeWithState) n).states()) {
                    FrameState state = innerMostState;
                    while (state != null) {
                        ConstantNode foundTruffleConstant = findTruffleNode(state);
                        if (foundTruffleConstant != null) {
                            return foundTruffleConstant;
                        }
                        if (state.getMethod().equals(executeRootMethod)) {
                            // we should not need to cross a call boundary to find a constant node
                            // it must be guaranteed that we find this earlier.
                            throw GraalError.shouldNotReachHere("Found a frame state of executeRootNode but not a constant node."); // ExcludeFromJacocoGeneratedReport
                        }
                        state = state.outerFrameState();
                    }
                    break;
                }
            }

            /*
             * This makes the location a guess. In the future we want to require the location
             * available from the TruffleSafepoint.poll(Node) call. We need languages to adopt the
             * TruffleSafepoint.poll(Node) call.
             */
            n = n.predecessor();
        }
        return null;
    }

    private ConstantNode findTruffleNode(FrameState state) {
        ResolvedJavaMethod method = state.getMethod();
        if (!method.hasReceiver()) {
            // we are looking for available constant receivers
            return null;
        }
        ResolvedJavaType receiverType = method.getDeclaringClass();
        boolean truffleNode = nodeType.isAssignableFrom(receiverType);
        if (!truffleNode && !callTargetClass.isAssignableFrom(receiverType)) {
            // not an interesting receiver type
            return null;
        }
        if (state.localsSize() == 0) {
            // not enough locals
            return null;
        }
        // receiver type is local 0
        ValueNode value = state.localAt(0);
        if (value == null) {
            // no receiver value available
            return null;
        }
        JavaConstant javaConstant = value.asJavaConstant();
        if (javaConstant == null) {
            // not a java constant we cannot use that value
            return null;
        }
        ResolvedJavaType javaType = value.stamp(NodeView.DEFAULT).javaType(providers.getMetaAccess());
        if (javaType == null) {
            // not sure this can happen for constants. just a safety check
            return null;
        }
        if (!receiverType.isAssignableFrom(javaType)) {
            // is of receiver type (sanity check)
            assert false : "unexpected case";
            return null;
        }
        JavaConstant rootNode;
        ObjectStamp stamp;
        if (truffleNode) {
            rootNode = getRootNode(javaConstant);
            if (rootNode == null) {
                return null;
            }
            stamp = StampFactory.object(TypeReference.createExactTrusted(rootNodeType));
        } else {
            // we did not find a truffle node but we arrived at executeRootNode of the call target
            // this is common if the truffle safepoint was inserted into a method end.
            rootNode = providers.getConstantReflection().readFieldValue(rootNodeField, javaConstant);
            stamp = StampFactory.object(TypeReference.createExactTrusted(rootNodeField.getType().resolve(callTargetClass)));
        }
        return new ConstantNode(skipOSRRoot(rootNode), stamp);
    }

    private JavaConstant skipOSRRoot(JavaConstant rootNode) {
        ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(rootNode);
        if (osrRootNodeType.isAssignableFrom(type)) {
            JavaConstant loopNode = providers.getConstantReflection().readFieldValue(loopNodeField, rootNode);
            if (loopNode.isNull()) {
                throw GraalError.shouldNotReachHere(String.format("%s must never be null but is for node %s.", loopNodeField.toString(), rootNode)); // ExcludeFromJacocoGeneratedReport
            }
            return getRootNode(loopNode);
        }
        return rootNode;
    }

    private JavaConstant getRootNode(JavaConstant node) {
        JavaConstant current = node;
        JavaConstant parent = current;
        do {
            // traversing the parent pointer must always be cycle free
            current = parent;
            parent = providers.getConstantReflection().readFieldValue(parentField, current);
        } while (!parent.isNull());

        // not a RootNode instance at the end of the parent chain -> not adopted
        // not adopted means this node will not have a valid source location
        ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(current);
        if (rootNodeType.isAssignableFrom(type)) {
            return current;
        }
        return null;
    }

}
