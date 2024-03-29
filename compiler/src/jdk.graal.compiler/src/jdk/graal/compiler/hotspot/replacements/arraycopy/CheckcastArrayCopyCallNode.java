/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
//JaCoCo Exclude
package jdk.graal.compiler.hotspot.replacements.arraycopy;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyCallNode;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;

/**
 * Implements {@link System#arraycopy} via a {@linkplain ForeignCallNode stub call} that performs a
 * fast {@code CHECKCAST} check.
 *
 * The target of the call is queried via
 * {@link HotSpotHostForeignCallsProvider#lookupCheckcastArraycopyDescriptor}.
 *
 * Instead of throwing an {@link ArrayStoreException}, the stub is expected to return the number of
 * copied elements xor'd with {@code -1}. Users of this node are responsible for converting that
 * into the expected exception. A return value of {@code 0} indicates that the operation was
 * successful.
 * <p>
 * See {@link GenericArrayCopyCallNode} for a generic {@link System#arraycopy} stub call node.
 * <p>
 * See {@link ArrayCopyCallNode} for a {@link System#arraycopy} stub call node that calls
 * specialzied stubs based element type and memory properties.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, sizeRationale = "depends on length", size = SIZE_UNKNOWN, cyclesRationale = "depends on length")
public final class CheckcastArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, SingleMemoryKill {

    public static final NodeClass<CheckcastArrayCopyCallNode> TYPE = NodeClass.create(CheckcastArrayCopyCallNode.class);

    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;
    @Input ValueNode destElemKlass;
    @Input ValueNode superCheckOffset;

    protected final boolean uninit;

    protected CheckcastArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest,
                    ValueNode destPos, ValueNode length,
                    ValueNode superCheckOffset, ValueNode destElemKlass, boolean uninit) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.superCheckOffset = superCheckOffset;
        this.destElemKlass = destElemKlass;
        this.uninit = uninit;
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    public ValueNode getLength() {
        return length;
    }

    public boolean isUninit() {
        return uninit;
    }

    public ValueNode getDestElemKlass() {
        return destElemKlass;
    }

    public ValueNode getSuperCheckOffset() {
        return superCheckOffset;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        /*
         * Because of restrictions that the memory graph of snippets matches the original node,
         * pretend that we kill any.
         */
        return LocationIdentity.any();
    }

    @NodeIntrinsic
    public static native int checkcastArraycopy(Object src, int srcPos, Object dest, int destPos, int length, Word superCheckOffset, Object destElemKlass, @ConstantNodeParameter boolean uninit);

}
