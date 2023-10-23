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

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, cyclesRationale = "may be replaced with non-throwing counterpart", size = SIZE_8)
public class SubstrateObjectCloneWithExceptionNode extends WithExceptionNode implements ObjectClone, SingleMemoryKill, Lowerable {

    public static final NodeClass<SubstrateObjectCloneWithExceptionNode> TYPE = NodeClass.create(SubstrateObjectCloneWithExceptionNode.class);

    public final InvokeKind invokeKind;
    public final ResolvedJavaMethod callerMethod;
    public final ResolvedJavaMethod targetMethod;
    public final int bci;
    public final StampPair returnStamp;

    @Input protected ValueNode object;
    @OptionalInput(InputType.State) protected FrameState stateBefore;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    public SubstrateObjectCloneWithExceptionNode(MacroParams macroParams) {
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
        AbstractBeginNode oldException = this.exceptionEdge;
        graph().replaceSplitWithFixed(this, plainObjectClone, this.next());
        GraphUtil.killCFG(oldException);
        return plainObjectClone;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (SubstrateObjectCloneSnippets.canVirtualize(this, tool)) {
            ObjectClone.super.virtualize(tool);
        }
    }
}
