/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Adds Truffle safepoints to loops and methods ends.
 *
 * Invocations of {@link TruffleSafepoint#poll(com.oracle.truffle.api.nodes.Node)} are removed
 * during PE. This phase ensures that they are efficiently added again at method and loop ends.
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

    public TruffleSafepointInsertionPhase(Providers providers) {
        this.providers = providers;
        TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
        this.nodeType = rt.resolveType(providers.getMetaAccess(), com.oracle.truffle.api.nodes.Node.class.getName());
        this.rootNodeType = rt.resolveType(providers.getMetaAccess(), RootNode.class.getName());
        this.osrRootNodeType = rt.resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.BaseOSRRootNode");
        this.callTargetClass = rt.resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        this.executeRootMethod = findMethod(callTargetClass, "executeRootNode");
        this.rootNodeField = findField(callTargetClass, "rootNode");
        this.parentField = findField(nodeType, "parent");
        this.loopNodeField = findField(osrRootNodeType, "loopNode");
    }

    public static boolean allowsSafepoints(StructuredGraph graph) {
        // only allowed in Truffle compilations.
        return graph.compilationId() instanceof TruffleCompilationIdentifier;
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
                if (loopEndNode.canGuestSafepoint()) {
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
            JavaConstant javaConstant = ((TruffleCompilationIdentifier) graph.compilationId()).getCompilable().asJavaConstant();
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
                            throw GraalError.shouldNotReachHere("Found a frame state of executeRootNode but not a constant node.");
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
                throw GraalError.shouldNotReachHere(String.format("%s must never be null but is for node %s.", loopNodeField.toString(), rootNode));
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

    static ResolvedJavaMethod findMethod(ResolvedJavaType type, String name) {
        for (ResolvedJavaMethod m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw GraalError.shouldNotReachHere("Required method " + name + " not found in " + type);
    }

    static ResolvedJavaField findField(ResolvedJavaType type, String name) {
        for (ResolvedJavaField field : type.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw GraalError.shouldNotReachHere("Required field " + name + " not found in " + type);
    }
}
