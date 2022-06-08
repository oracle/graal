/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.replacements.nodes.PureFunctionStubIntrinsicNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * This intrinsic calculates properties of string contents in various encodings, see
 * {@link AMD64CalcStringAttributesOp} for details.
 *
 * @see AMD64CalcStringAttributesOp
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class AMD64CalcStringAttributesNode extends PureFunctionStubIntrinsicNode implements LIRLowerable {

    public static final NodeClass<AMD64CalcStringAttributesNode> TYPE = NodeClass.create(AMD64CalcStringAttributesNode.class);

    private final AMD64CalcStringAttributesOp.Op op;
    private final boolean assumeValid;

    @Input protected ValueNode array;
    @Input protected ValueNode offset;
    @Input protected ValueNode length;

    /**
     * This constructor is used by the {@code NodeIntrinsic} plugins below, which in turn are used
     * only in {@code AMD64CalcStringAttributesStub}, which is why we are using
     * {@link LocationIdentity#any()} here. The nodes calling the stubs are using more fine-grained
     * location identities, but are calling the same stubs (after
     * {@link #generate(NodeLIRBuilderTool) assembly generation}).
     */
    protected AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid) {
        this(array, offset, length, op, assumeValid, null, LocationIdentity.any());
    }

    protected AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(array, offset, length, op, assumeValid, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    public AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    LocationIdentity locationIdentity) {
        this(array, offset, length, op, assumeValid, null, locationIdentity);
    }

    public AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(getReturnValueKind(op)), runtimeCheckedCPUFeatures, locationIdentity);
        this.op = op;
        this.assumeValid = assumeValid;
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    private static JavaKind getReturnValueKind(AMD64CalcStringAttributesOp.Op op) {
        return op == AMD64CalcStringAttributesOp.Op.UTF_8 || op == AMD64CalcStringAttributesOp.Op.UTF_16 ? JavaKind.Long : JavaKind.Int;
    }

    public AMD64CalcStringAttributesOp.Op getOp() {
        return op;
    }

    public boolean isAssumeValid() {
        return assumeValid;
    }

    public ValueNode getArray() {
        return array;
    }

    public ValueNode getOffset() {
        return offset;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(array), gen.operand(offset), gen.operand(length));
                gen.setResult(this, result);
                return;
            }
        }
        Value result = gen.getLIRGeneratorTool().emitCalcStringAttributes(op, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(offset), gen.operand(length), assumeValid);
        gen.setResult(this, result);
    }

    /* NodeIntrinsic plugins for snippet stubs. */

    @NodeIntrinsic
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
