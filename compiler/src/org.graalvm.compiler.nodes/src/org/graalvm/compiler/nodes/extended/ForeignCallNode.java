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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.List;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call.
 */
// @formatter:off
@NodeInfo(nameTemplate = "ForeignCall#{p#descriptor/s}",
          allowedUsageTypes = Memory,
          cycles = CYCLES_2,
          cyclesRationale = "Rough estimation of the call operation itself.",
          size = SIZE_2,
          sizeRationale = "Rough estimation of the call operation itself.")
// @formatter:on
public class ForeignCallNode extends AbstractMemoryCheckpoint implements ForeignCall {
    public static final NodeClass<ForeignCallNode> TYPE = NodeClass.create(ForeignCallNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) protected FrameState stateDuring;
    protected final ForeignCallsProvider foreignCalls;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;

    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod targetMethod, @InjectedNodeParameter Stamp returnStamp, @InjectedNodeParameter ForeignCallsProvider foreignCalls,
                    ForeignCallDescriptor descriptor, ValueNode... arguments) {
        if (!foreignCalls.isAvailable(descriptor)) {
            // When using encoded snippets a graph main contain a reference to a foreign call that's
            // not actually available in the current configuration. It's assumed that further
            // simplification of the graph will eliminate this call completely.
            return false;
        }

        ForeignCallNode node = new ForeignCallNode(foreignCalls, descriptor, arguments);
        node.setStamp(returnStamp);

        assert verifyDescriptor(b, targetMethod, descriptor);

        /*
         * Need to update the BCI of a ForeignCallNode so that it gets the stateDuring in the case
         * that the foreign call can deoptimize. As with all deoptimization, we need a state in a
         * non-intrinsic method.
         */
        GraphBuilderContext nonIntrinsicAncestor = b.getNonIntrinsicAncestor();
        if (nonIntrinsicAncestor != null) {
            node.setBci(nonIntrinsicAncestor.bci());
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            b.add(node);
        } else {
            b.addPush(returnKind, node);
        }

        return true;
    }

    static boolean verifyDescriptor(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ForeignCallDescriptor descriptor) {
        int parameters = 1;
        for (Class<?> arg : descriptor.getArgumentTypes()) {
            ResolvedJavaType res = b.getMetaAccess().lookupJavaType(arg);
            ResolvedJavaType parameterType = (ResolvedJavaType) targetMethod.getSignature().getParameterType(parameters, targetMethod.getDeclaringClass());
            assert parameterType.equals(res) : descriptor + ": parameter " + parameters + " mismatch: " + res + " != " + parameterType;
            parameters++;
        }
        return true;
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, foreignCalls, descriptor, arguments);
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp, List<ValueNode> arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    protected ForeignCallNode(NodeClass<? extends ForeignCallNode> c, ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(JavaKind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @Override
    public boolean hasSideEffect() {
        return !foreignCalls.isReexecutable(descriptor);
    }

    @Override
    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert hasSideEffect() || x == null;
        super.setStateAfter(x);
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        assert this.bci == BytecodeFrame.UNKNOWN_BCI || this.bci == bci;
        this.bci = bci;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }
}
