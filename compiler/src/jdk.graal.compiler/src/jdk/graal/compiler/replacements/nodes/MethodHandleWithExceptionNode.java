/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.lang.invoke.MethodHandle;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;

/**
 * Node for invocation methods defined on the class {@link MethodHandle}.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "see MacroNode", size = SIZE_UNKNOWN, sizeRationale = "see MacroNode")
public final class MethodHandleWithExceptionNode extends MacroWithExceptionNode implements Simplifiable {
    public static final NodeClass<MethodHandleWithExceptionNode> TYPE = NodeClass.create(MethodHandleWithExceptionNode.class);

    protected final IntrinsicMethod intrinsicMethod;

    public MethodHandleWithExceptionNode(IntrinsicMethod intrinsicMethod, MacroNode.MacroParams p) {
        super(TYPE, p);
        this.intrinsicMethod = intrinsicMethod;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        MethodHandleAccessProvider methodHandleAccess = tool.getConstantReflection().getMethodHandleAccess();
        trySimplify(methodHandleAccess);
    }

    public WithExceptionNode trySimplify(MethodHandleAccessProvider methodHandleAccess) {
        ValueNode[] argumentsArray = arguments.toArray(new ValueNode[arguments.size()]);

        MethodHandleNode.GraphAdder adder = MethodHandleNode.getGraphAdderBeforeNode(this);
        MethodHandleNode.InvokeFactory<InvokeWithExceptionNode> invokeFactory = (callTarget, bi, stmp) -> {
            InvokeWithExceptionNode invoke = new InvokeWithExceptionNode(callTarget, null, bi);
            invoke.setStamp(stmp);
            return invoke;
        };
        InvokeWithExceptionNode invoke = MethodHandleNode.tryResolveTargetInvoke(adder, invokeFactory, methodHandleAccess, intrinsicMethod, targetMethod, bci, returnStamp, argumentsArray);
        if (invoke == null) {
            return this;
        }
        assert invoke.graph() == null;
        invoke.setNodeSourcePosition(getNodeSourcePosition());
        invoke = graph().addOrUniqueWithInputs(invoke);
        invoke.setStateAfter(stateAfter());
        graph().replaceWithExceptionSplit(this, invoke);
        return invoke;
    }
}
