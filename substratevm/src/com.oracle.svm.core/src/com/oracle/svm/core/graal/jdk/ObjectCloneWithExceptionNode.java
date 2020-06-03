/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.jdk;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.replacements.nodes.ObjectClone;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_8)
public class ObjectCloneWithExceptionNode extends WithExceptionNode implements ObjectClone, SingleMemoryKill, Lowerable {

    public static final NodeClass<ObjectCloneWithExceptionNode> TYPE = NodeClass.create(ObjectCloneWithExceptionNode.class);

    public final InvokeKind invokeKind;
    public final ResolvedJavaMethod callerMethod;
    public final ResolvedJavaMethod targetMethod;
    public final int bci;
    public final StampPair returnStamp;

    @Input protected ValueNode object;
    @OptionalInput(InputType.State) protected FrameState stateBefore;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    public ObjectCloneWithExceptionNode(MacroParams macroParams) {
        super(TYPE, macroParams.returnStamp.getTrustedStamp());
        this.invokeKind = macroParams.invokeKind;
        this.callerMethod = macroParams.callerMethod;
        this.targetMethod = macroParams.targetMethod;
        this.bci = macroParams.bci;
        this.returnStamp = macroParams.returnStamp;
        assert macroParams.arguments.length == 1;
        this.object = macroParams.arguments[0];
    }

    public InvokeKind getInvokeKind() {
        return invokeKind;
    }

    public ResolvedJavaMethod getCallerMethod() {
        return callerMethod;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public int bci() {
        return bci;
    }

    public StampPair getReturnStamp() {
        return returnStamp;
    }

    @Override
    public ValueNode getObject() {
        return object;
    }

    private MacroParams macroParams() {
        return MacroParams.of(invokeKind, callerMethod, targetMethod, bci, returnStamp, new ValueNode[]{object});
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        SubstrateObjectCloneNode plainObjectClone = this.asNode().graph().add(new SubstrateObjectCloneNode(macroParams()));
        plainObjectClone.setStateAfter(stateAfter());
        AbstractBeginNode nextBegin = this.next;
        AbstractBeginNode oldException = this.exceptionEdge;
        graph().replaceSplitWithFixed(this, plainObjectClone, this.next());
        GraphUtil.killCFG(oldException);
        if (nextBegin instanceof KillingBeginNode && ((KillingBeginNode) nextBegin).getKilledLocationIdentity().equals(plainObjectClone.getKilledLocationIdentity())) {
            plainObjectClone.graph().removeFixed(nextBegin);
        }
        return plainObjectClone;
    }
}
