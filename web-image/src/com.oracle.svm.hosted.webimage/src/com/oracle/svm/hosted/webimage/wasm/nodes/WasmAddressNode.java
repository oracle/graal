/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import com.oracle.svm.webimage.wasm.types.WasmLMUtil;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Represents a Wasm address of the form {@code base + offset}.
 * <p>
 * Wasm memory operations can inline the constant offset directly into the instruction instead of
 * having to perform an addition.
 * <p>
 * The {@link #offset} may still be non-constant. In which case the node is lowered as an addition.
 */
@NodeInfo(allowedUsageTypes = InputType.Association, cycles = CYCLES_1, cyclesRationale = "Is at most an addition", size = SIZE_1)
public class WasmAddressNode extends AddressNode {

    public static final NodeClass<WasmAddressNode> TYPE = NodeClass.create(WasmAddressNode.class);

    @Input ValueNode base;
    @Input ValueNode offset;

    private final boolean isConstantOffset;

    public WasmAddressNode(ValueNode base, ValueNode offset, boolean isConstantOffset) {
        super(TYPE);

        this.base = base;
        this.offset = offset;
        this.isConstantOffset = isConstantOffset;

        if (Assertions.assertionsEnabled()) {
            Stamp localStamp = offset.stamp(NodeView.DEFAULT);
            assert (localStamp instanceof ObjectStamp) || IntegerStamp.getBits(localStamp) == WasmLMUtil.POINTER_TYPE.getBitCount() : localStamp;
        }
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    @Override
    public ValueNode getIndex() {
        return offset;
    }

    @Override
    public long getMaxConstantDisplacement() {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * If this returns {@code true}, the offset is a non-negative constant value.
     * <p>
     * Otherwise, the offset cannot be used as an immediate value for memory instructions.
     */
    public boolean hasConstantOffset() {
        return isConstantOffset;
    }

    public JavaConstant getConstantOffset() {
        return offset.asJavaConstant();
    }
}
