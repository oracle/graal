/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code java.lang.StringCoding.hasNegatives}. It tests if a byte array
 * contain a negative value.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
public final class HasNegativesNode extends PureFunctionStubIntrinsicNode {
    public static final NodeClass<HasNegativesNode> TYPE = NodeClass.create(HasNegativesNode.class);

    public static final ForeignCallDescriptor STUB = ForeignCalls.pureFunctionForeignCallDescriptor("stringCodingHasNegatives", boolean.class, Pointer.class, int.class);

    @Input protected ValueNode array;
    @Input protected ValueNode len;

    public HasNegativesNode(ValueNode array, ValueNode len) {
        this(array, len, null);
    }

    public HasNegativesNode(ValueNode array, ValueNode len, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        this.array = array;
        this.len = len;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array, len};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitHasNegatives(runtimeCheckedCPUFeatures, gen.operand(array), gen.operand(len)));
    }

    @NodeIntrinsic
    @GenerateStub
    public static native boolean stringCodingHasNegatives(Pointer array, int len);

    @NodeIntrinsic
    public static native boolean stringCodingHasNegatives(Pointer array, int len, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
