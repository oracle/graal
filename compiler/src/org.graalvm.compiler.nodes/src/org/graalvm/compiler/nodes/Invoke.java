/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface Invoke extends StateSplit, Lowerable, SingleMemoryKill, DeoptimizingNode.DeoptDuring, FixedNodeInterface, Invokable {

    String CYCLES_UNKNOWN_RATIONALE = "Cannot estimate the runtime cost of a call; it's a blackhole.";
    String SIZE_UNKNOWN_RATIONALE = "Can only dynamically decide how much code is generated based on the type of a call (special, static, virtual, interface).";

    enum InlineControl {
        Normal(true, true),
        BytecodesOnly(true, false),
        Never(false, false);

        private final boolean allowInlining;
        private final boolean allowSubstitution;

        InlineControl(boolean allowInlining, boolean allowSubstitution) {
            this.allowInlining = allowInlining;
            this.allowSubstitution = allowSubstitution;
        }

        public boolean allowInlining() {
            return allowInlining;
        }

        public boolean allowSubstitution() {
            return allowSubstitution;
        }
    }

    FixedNode next();

    void setNext(FixedNode x);

    default boolean isAlive() {
        return asFixedNode().isAlive();
    }

    CallTargetNode callTarget();

    Node predecessor();

    ValueNode classInit();

    void setClassInit(ValueNode node);

    InlineControl getInlineControl();

    void setInlineControl(InlineControl control);

    default boolean useForInlining() {
        return getInlineControl().allowInlining();
    }

    default void setUseForInlining(boolean useForInlining) {
        setInlineControl(useForInlining ? Invoke.InlineControl.Normal : Invoke.InlineControl.Never);
    }

    /**
     * True if this invocation is almost certainly polymorphic, false when in doubt.
     */
    boolean isPolymorphic();

    void setPolymorphic(boolean value);

    @Override
    default ResolvedJavaMethod getTargetMethod() {
        return callTarget() != null ? callTarget().targetMethod() : null;
    }

    @Override
    default ResolvedJavaMethod getContextMethod() {
        FrameState state = stateAfter();
        if (state == null) {
            state = stateDuring();
        }
        return state.getMethod();
    }

    /**
     * Returns the {@linkplain ResolvedJavaType type} from which this invoke is executed. This is
     * the declaring type of the caller method.
     *
     * @return the type from which this invoke is executed.
     */
    default ResolvedJavaType getContextType() {
        ResolvedJavaMethod contextMethod = getContextMethod();
        if (contextMethod == null) {
            return null;
        }
        return contextMethod.getDeclaringClass();
    }

    @Override
    default void computeStateDuring(FrameState stateAfter) {
        FrameState newStateDuring = stateAfter.duplicateModifiedDuringCall(bci(), asNode().getStackKind());
        setStateDuring(newStateDuring);
    }

    default ValueNode getReceiver() {
        assert getInvokeKind().hasReceiver();
        return callTarget().arguments().get(0);
    }

    default ResolvedJavaType getReceiverType() {
        ResolvedJavaType receiverType = StampTool.typeOrNull(getReceiver());
        if (receiverType == null) {
            receiverType = ((MethodCallTargetNode) callTarget()).targetMethod().getDeclaringClass();
        }
        return receiverType;
    }

    default InvokeKind getInvokeKind() {
        return callTarget().invokeKind();
    }

}
