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

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.compiler.common.type.StampFactory.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Used by a {@link GraphBuilderPlugin} to interface with a graph builder object.
 */
public interface GraphBuilderContext {

    /**
     * Information about a snippet or method substitution currently being processed by the graph
     * builder. When in the scope of a replacement, the graph builder does not check the value kinds
     * flowing through the JVM state since replacements can employ non-Java kinds to represent
     * values such as raw machine words and pointers.
     */
    public interface Replacement {

        /**
         * Gets the method being replaced.
         */
        ResolvedJavaMethod getOriginalMethod();

        /**
         * Gets the replacement method.
         */
        ResolvedJavaMethod getReplacementMethod();

        /**
         * Determines if this replacement is being inlined as a compiler intrinsic. A compiler
         * intrinsic is atomic with respect to deoptimization. Deoptimization within a compiler
         * intrinsic will restart the interpreter at the intrinsified call.
         */
        boolean isIntrinsic();

        /**
         * Determines if a call within the compilation scope of this replacement represents a call
         * to the {@linkplain #getOriginalMethod() original} method.
         */
        boolean isCallToOriginal(ResolvedJavaMethod method);
    }

    /**
     * Raw operation for adding a node to the graph when neither {@link #add},
     * {@link #addPush(ValueNode)} nor {@link #addPush(Kind, ValueNode)} can be used.
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
            if (stateSplit.stateAfter() == null) {
                stateSplit.setStateAfter(createStateAfter());
            }
        }
        return equivalentValue;
    }

    /**
     * Adds a node with a non-void kind to the graph, pushes it to the stack. If the returned node
     * is a {@link StateSplit} with a null {@linkplain StateSplit#stateAfter() frame state}, the
     * frame state is initialized.
     *
     * @param value the value to add to the graph and push to the stack. The {@code value.getKind()}
     *            kind is used when type checking this operation.
     * @return a node equivalent to {@code value} in the graph
     */
    default <T extends ValueNode> T addPush(T value) {
        return addPush(value.getKind().getStackKind(), value);
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
        push(kind.getStackKind(), equivalentValue);
        if (equivalentValue instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) equivalentValue;
            if (stateSplit.stateAfter() == null) {
                stateSplit.setStateAfter(createStateAfter());
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
     * currently being parsed.
     */
    FrameState createStateAfter();

    /**
     * Gets the parsing context for the method that inlines the method being parsed by this context.
     */
    GraphBuilderContext getParent();

    /**
     * Gets the method currently being parsed.
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

    /**
     * Gets the inline depth of this context. 0 implies this is the context for the compilation root
     * method.
     */
    default int getDepth() {
        GraphBuilderContext parent = getParent();
        return parent == null ? 0 : 1 + parent.getDepth();
    }

    /**
     * Determines if the current parsing context is a snippet or method substitution.
     */
    default boolean parsingReplacement() {
        return getReplacement() != null;
    }

    /**
     * Gets the replacement of the current parsing context or {@code null} if not
     * {@link #parsingReplacement() parsing a replacement}.
     */
    Replacement getReplacement();

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
