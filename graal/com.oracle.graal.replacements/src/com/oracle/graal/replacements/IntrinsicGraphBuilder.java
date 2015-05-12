/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Implementation of {@link GraphBuilderContext} used to produce a graph for a method based on an
 * {@link InvocationPlugin} for the method.
 */
public class IntrinsicGraphBuilder implements GraphBuilderContext, Receiver {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final StampProvider stampProvider;
    private final StructuredGraph graph;
    private final ResolvedJavaMethod method;
    private final int invokeBci;
    private FixedWithNextNode lastInstr;
    private ValueNode[] arguments;
    private ValueNode returnValue;

    public IntrinsicGraphBuilder(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, StampProvider stampProvider, ResolvedJavaMethod method, int invokeBci) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.stampProvider = stampProvider;
        this.graph = new StructuredGraph(method, AllowAssumptions.YES);
        this.method = method;
        this.invokeBci = invokeBci;
        this.lastInstr = graph.start();

        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        this.arguments = new ValueNode[max + (method.isStatic() ? 0 : 1)];

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // add the receiver
            Stamp receiverStamp = StampFactory.declaredNonNull(method.getDeclaringClass());
            FloatingNode receiver = graph.addWithoutUnique(new ParameterNode(javaIndex, receiverStamp));
            arguments[index] = receiver;
            javaIndex = 1;
            index = 1;
        }
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        for (int i = 0; i < max; i++) {
            JavaType type = sig.getParameterType(i, accessingClass).resolve(accessingClass);
            Kind kind = type.getKind();
            Stamp stamp;
            if (kind == Kind.Object && type instanceof ResolvedJavaType) {
                stamp = StampFactory.declared((ResolvedJavaType) type);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            FloatingNode param = graph.addWithoutUnique(new ParameterNode(index, stamp));
            arguments[index] = param;
            javaIndex += kind.getSlotCount();
            index++;
        }
    }

    private <T extends ValueNode> void updateLastInstruction(T v) {
        if (v instanceof FixedNode) {
            FixedNode fixedNode = (FixedNode) v;
            lastInstr.setNext(fixedNode);
            if (fixedNode instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                lastInstr = fixedWithNextNode;
            } else {
                lastInstr = null;
            }
        }
    }

    public <T extends ValueNode> T append(T v) {
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUnique(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    public <T extends ValueNode> T recursiveAppend(T v) {
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUniqueWithInputs(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    public void push(Kind kind, ValueNode value) {
        assert kind != Kind.Void;
        assert returnValue == null;
        returnValue = value;
    }

    public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public StampProvider getStampProvider() {
        return stampProvider;
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public StructuredGraph getGraph() {
        return graph;
    }

    public void setStateAfter(StateSplit sideEffect) {
        assert sideEffect.hasSideEffect();
        FrameState stateAfter = getGraph().add(new FrameState(BytecodeFrame.BEFORE_BCI));
        sideEffect.setStateAfter(stateAfter);
    }

    public GraphBuilderContext getParent() {
        return null;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public int bci() {
        return invokeBci;
    }

    public InvokeKind getInvokeKind() {
        return method.isStatic() ? InvokeKind.Static : InvokeKind.Virtual;
    }

    public JavaType getInvokeReturnType() {
        return method.getSignature().getReturnType(method.getDeclaringClass());
    }

    public int getDepth() {
        return 0;
    }

    public boolean parsingIntrinsic() {
        return true;
    }

    public Intrinsic getIntrinsic() {
        throw GraalInternalError.shouldNotReachHere();
    }

    public BailoutException bailout(String string) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public ValueNode get() {
        return arguments[0];
    }

    public StructuredGraph buildGraph(InvocationPlugin plugin) {
        Receiver receiver = method.isStatic() ? null : this;
        if (plugin.execute(this, method, receiver, arguments)) {
            assert (returnValue != null) == (method.getSignature().getReturnKind() != Kind.Void) : method;
            append(new ReturnNode(returnValue));
            return graph;
        }
        return null;
    }

    public void intrinsify(ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, ValueNode[] args) {
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public String toString() {
        return String.format("%s:intrinsic", method.format("%H.%n(%p)"));
    }
}
