/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graphbuilderconf;

import com.oracle.jvmci.code.BailoutException;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.Assumptions;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.ConstantReflectionProvider;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.JavaType;
import static com.oracle.jvmci.meta.DeoptimizationAction.*;
import static com.oracle.jvmci.meta.DeoptimizationReason.*;
import static com.oracle.graal.compiler.common.type.StampFactory.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Used by a {@link GraphBuilderPlugin} to interface with an object that parses the bytecode of a
 * single {@linkplain #getMethod() method} as part of building a {@linkplain #getGraph() graph} .
 */
public interface GraphBuilderContext {

    /**
     * Raw operation for adding a node to the graph when neither {@link #add} nor
     * {@link #addPush(Kind, ValueNode)} can be used.
     *
     * @return either the node added or an equivalent node
     */
    <T extends ValueNode> T append(T value);

    /**
     * Adds the given node to the graph and also adds recursively all referenced inputs.
     *
     * @param value the node to be added to the graph
     * @return either the node added or an equivalent node
     */
    <T extends ValueNode> T recursiveAppend(T value);

    /**
     * Pushes a given value to the frame state stack using an explicit kind. This should be used
     * when {@code value.getKind()} is different from the kind that the bytecode instruction
     * currently being parsed pushes to the stack.
     *
     * @param kind the kind to use when type checking this operation
     * @param value the value to push to the stack. The value must already have been
     *            {@linkplain #append(ValueNode) appended}.
     */
    void push(Kind kind, ValueNode value);

    /**
     * Adds a node to the graph. If the returned node is a {@link StateSplit} with a null
     * {@linkplain StateSplit#stateAfter() frame state}, the frame state is initialized.
     *
     * @param value the value to add to the graph and push to the stack. The {@code value.getKind()}
     *            kind is used when type checking this operation.
     * @return a node equivalent to {@code value} in the graph
     */
    default <T extends ValueNode> T add(T value) {
        if (value.graph() != null) {
            assert !(value instanceof StateSplit) || ((StateSplit) value).stateAfter() != null;
            return value;
        }
        T equivalentValue = append(value);
        if (equivalentValue instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) equivalentValue;
            if (stateSplit.stateAfter() == null && stateSplit.hasSideEffect()) {
                setStateAfter(stateSplit);
            }
        }
        return equivalentValue;
    }

    /**
     * Adds a node with a non-void kind to the graph, pushes it to the stack. If the returned node
     * is a {@link StateSplit} with a null {@linkplain StateSplit#stateAfter() frame state}, the
     * frame state is initialized.
     *
     * @param kind the kind to use when type checking this operation
     * @param value the value to add to the graph and push to the stack
     * @return a node equivalent to {@code value} in the graph
     */
    default <T extends ValueNode> T addPush(Kind kind, T value) {
        T equivalentValue = value.graph() != null ? value : append(value);
        push(kind, equivalentValue);
        if (equivalentValue instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) equivalentValue;
            if (stateSplit.stateAfter() == null && stateSplit.hasSideEffect()) {
                setStateAfter(stateSplit);
            }
        }
        return equivalentValue;
    }

    /**
     * Handles an invocation that a plugin determines can replace the original invocation (i.e., the
     * one for which the plugin was applied). This applies all standard graph builder processing to
     * the replaced invocation including applying any relevant plugins.
     *
     * @param invokeKind the kind of the replacement invocation
     * @param targetMethod the target of the replacement invocation
     * @param args the arguments to the replacement invocation
     */
    void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args);

    /**
     * Intrinsifies an invocation of a given method by inlining the bytecodes of a given
     * substitution method.
     *
     * @param targetMethod the method being intrinsified
     * @param substitute the intrinsic implementation
     * @param args the arguments with which to inline the invocation
     */
    void intrinsify(ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, ValueNode[] args);

    StampProvider getStampProvider();

    MetaAccessProvider getMetaAccess();

    default Assumptions getAssumptions() {
        return getGraph().getAssumptions();
    }

    ConstantReflectionProvider getConstantReflection();

    /**
     * Gets the graph being constructed.
     */
    StructuredGraph getGraph();

    /**
     * Creates a snap shot of the current frame state with the BCI of the instruction after the one
     * currently being parsed and assigns it to a given {@linkplain StateSplit#hasSideEffect() side
     * effect} node.
     *
     * @param sideEffect a side effect node just appended to the graph
     */
    void setStateAfter(StateSplit sideEffect);

    /**
     * Gets the parsing context for the method that inlines the method being parsed by this context.
     */
    GraphBuilderContext getParent();

    /**
     * Gets the first ancestor parsing context that is not parsing a
     * {@linkplain #parsingIntrinsic() intrinsic}.
     */
    default GraphBuilderContext getNonIntrinsicAncestor() {
        GraphBuilderContext ancestor = getParent();
        while (ancestor != null && ancestor.parsingIntrinsic()) {
            ancestor = ancestor.getParent();
        }
        return ancestor;
    }

    /**
     * Gets the method being parsed by this context.
     */
    ResolvedJavaMethod getMethod();

    /**
     * Gets the index of the bytecode instruction currently being parsed.
     */
    int bci();

    /**
     * Gets the kind of invocation currently being parsed.
     */
    InvokeKind getInvokeKind();

    /**
     * Gets the return type of the invocation currently being parsed.
     */
    JavaType getInvokeReturnType();

    default Stamp getInvokeReturnStamp() {
        JavaType returnType = getInvokeReturnType();
        if (returnType.getKind() == Kind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.declared((ResolvedJavaType) returnType);
        } else {
            return StampFactory.forKind(returnType.getKind());
        }
    }

    /**
     * Gets the inline depth of this context. A return value of 0 implies that this is the context
     * for the parse root.
     */
    default int getDepth() {
        GraphBuilderContext parent = getParent();
        return parent == null ? 0 : 1 + parent.getDepth();
    }

    /**
     * Determines if this parsing context is within the bytecode of an intrinsic or a method inlined
     * by an intrinsic.
     */
    default boolean parsingIntrinsic() {
        return getIntrinsic() != null;
    }

    /**
     * Gets the intrinsic of the current parsing context or {@code null} if not
     * {@link #parsingIntrinsic() parsing an intrinsic}.
     */
    IntrinsicContext getIntrinsic();

    BailoutException bailout(String string);

    /**
     * Gets a version of a given value that has a {@linkplain StampTool#isPointerNonNull(ValueNode)
     * non-null} stamp.
     */
    default ValueNode nullCheckedValue(ValueNode value) {
        if (!StampTool.isPointerNonNull(value.stamp())) {
            IsNullNode condition = getGraph().unique(new IsNullNode(value));
            ObjectStamp receiverStamp = (ObjectStamp) value.stamp();
            Stamp stamp = receiverStamp.join(objectNonNull());
            FixedGuardNode fixedGuard = append(new FixedGuardNode(condition, NullCheckException, InvalidateReprofile, true));
            PiNode nonNullReceiver = getGraph().unique(new PiNode(value, stamp));
            nonNullReceiver.setGuard(fixedGuard);
            // TODO: Propogating the non-null into the frame state would
            // remove subsequent null-checks on the same value. However,
            // it currently causes an assertion failure when merging states.
            //
            // frameState.replace(value, nonNullReceiver);
            return nonNullReceiver;
        }
        return value;
    }
}
