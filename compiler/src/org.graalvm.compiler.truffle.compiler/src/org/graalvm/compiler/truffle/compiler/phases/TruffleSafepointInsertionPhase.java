/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
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
    private final ResolvedJavaType callTargetClass;
    private final ResolvedJavaField rootNodeField;
    private final ResolvedJavaField parentField;
    private final ResolvedJavaMethod executeRootMethod;

    public TruffleSafepointInsertionPhase(Providers providers) {
        this.providers = providers;
        TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
        this.nodeType = rt.resolveType(providers.getMetaAccess(), com.oracle.truffle.api.nodes.Node.class.getName());
        this.rootNodeType = rt.resolveType(providers.getMetaAccess(), RootNode.class.getName());
        this.callTargetClass = rt.resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        this.executeRootMethod = findMethod(callTargetClass, "executeRootNode");
        this.rootNodeField = findField(callTargetClass, "rootNode");
        this.parentField = findField(nodeType, "parent");
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
    protected void run(StructuredGraph graph) {
        if (!allowsSafepoints(graph)) {
            return;
        }

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            try (DebugCloseable s = returnNode.withNodeSourcePosition()) {
                insertSafepoint(graph, returnNode);
            }
        }
        for (LoopBeginNode loopBeginNode : graph.getNodes(LoopBeginNode.TYPE)) {
            for (LoopEndNode loopEndNode : loopBeginNode.loopEnds()) {
                // Invokes inside truffle compilations do not have a guaranteed truffle safepoint so
                // we cannot elide them when Graal thinks it is safe to do so.
                if (loopEndNode.canSafepoint() || loopEndNode.guaranteedSafepoint()) {
                    try (DebugCloseable s = loopEndNode.withNodeSourcePosition()) {
                        insertSafepoint(graph, loopEndNode);
                    }
                }
            }
        }

        for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
            insertSafepoint(graph, (FixedNode) callTarget.invoke());
        }
    }

    private void insertSafepoint(StructuredGraph graph, FixedNode returnNode) {
        ConstantNode node = findTruffleNode(returnNode);
        if (node != null) {
            assert node.asJavaConstant() != null : "must be a java constant";
            assert nodeType.isAssignableFrom(node.stamp(NodeView.DEFAULT).javaType(providers.getMetaAccess())) : "must be a truffle node";
            node = graph.maybeAddOrUnique(node);
        } else {
            // we always must find a node location for truffle safepoints
            throw GraalError.shouldNotReachHere("No Truffle node found in frame state of " + returnNode);
        }
        graph.addBeforeFixed(returnNode, graph.add(new TruffleSafepointNode(node)));
    }

    private ConstantNode findTruffleNode(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof StateSplit) {
                FrameState state = ((StateSplit) n).stateAfter();
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
        if (state.values() == null || state.values().size() == 0) {
            // not enough values
            return null;
        }
        // receiver type is located at index 0
        ValueNode value = state.values().get(0);
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
        if (truffleNode) {
            if (!isAdoptedTruffleNode(javaConstant)) {
                return null;
            }
            // success the receiver in the frame state is a truffle node
            // and it is adopted by a root node
            return (ConstantNode) value;
        } else {
            // we did not find a truffle node but we arrived at executeRootNode of the call target
            // this is common if the truffle safepoint was inserted into a method end.
            JavaConstant constant = providers.getConstantReflection().readFieldValue(rootNodeField, javaConstant);
            return new ConstantNode(constant, StampFactory.object(TypeReference.createExactTrusted(rootNodeField.getType().resolve(callTargetClass))));
        }
    }

    private boolean isAdoptedTruffleNode(JavaConstant javaConstant) {
        JavaConstant current = javaConstant;
        JavaConstant parent = current;
        do {
            // traversing the parent pointer must always be cycle free
            current = parent;
            parent = providers.getConstantReflection().readFieldValue(parentField, current);
        } while (!parent.isNull());

        // not a RootNode instance at the end of the parent chain -> not adopted
        // not adopted means this node will not have a valid source location
        return rootNodeType.isInstance(current);
    }

    private static ResolvedJavaMethod findMethod(ResolvedJavaType type, String name) {
        for (ResolvedJavaMethod m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw GraalError.shouldNotReachHere("Required method " + name + " not found in " + type);
    }

    private static ResolvedJavaField findField(ResolvedJavaType type, String name) {
        // getInstanceFields(true) seems cached and getInstanceFields(false) is not
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw GraalError.shouldNotReachHere("Required field " + name + " not found in " + type);
    }
}
