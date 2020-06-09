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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopy;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_64)
public class ArrayCopyWithExceptionNode extends WithExceptionNode implements ArrayCopy {

    public static final NodeClass<ArrayCopyWithExceptionNode> TYPE = NodeClass.create(ArrayCopyWithExceptionNode.class);

    @Input NodeInputList<ValueNode> args;
    @OptionalInput(State) FrameState stateDuring;
    @OptionalInput(State) protected FrameState stateAfter;
    @OptionalInput(Memory) protected MemoryKill lastLocationAccess;

    protected JavaKind elementKind;

    protected int bci;

    public ArrayCopyWithExceptionNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind, int bci) {
        super(TYPE, StampFactory.forVoid());
        this.bci = bci;
        this.args = new NodeInputList<>(this, new ValueNode[]{src, srcPos, dest, destPos, length});
        this.elementKind = elementKind != JavaKind.Illegal ? elementKind : null;
        if (this.elementKind == null) {
            this.elementKind = ArrayCopy.selectComponentKind(this);
        }
    }

    @Override
    public NodeInputList<ValueNode> args() {
        return args;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return LocationIdentity.any();
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
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
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        updateUsages(this.stateAfter, stateAfter);
        this.stateAfter = stateAfter;
    }

    @Override
    public int getBci() {
        return bci;
    }

    @Override
    public JavaKind getElementKind() {
        return elementKind;
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        SubstrateArraycopyNode plainArrayCopy = this.asNode().graph()
                        .add(new SubstrateArraycopyNode(getSource(), getSourcePosition(), getDestination(), getDestinationPosition(), getLength(), getElementKind(), getBci()));
        plainArrayCopy.setStateAfter(stateAfter);
        AbstractBeginNode nextBegin = this.next;
        AbstractBeginNode oldException = this.exceptionEdge;
        graph().replaceSplitWithFixed(this, plainArrayCopy, this.next());
        GraphUtil.killCFG(oldException);
        if (nextBegin instanceof KillingBeginNode && ((KillingBeginNode) nextBegin).getKilledLocationIdentity().equals(plainArrayCopy.getKilledLocationIdentity())) {
            plainArrayCopy.graph().removeFixed(nextBegin);
        }
        return plainArrayCopy;
    }
}
