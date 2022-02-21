/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.FloatableMemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
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
public final class AMD64CalcStringAttributesNode extends FixedWithNextNode implements LIRLowerable, FloatableMemoryAccess {

    public static final NodeClass<AMD64CalcStringAttributesNode> TYPE = NodeClass.create(AMD64CalcStringAttributesNode.class);

    private final AMD64CalcStringAttributesOp.Op op;
    private final boolean assumeValid;
    private final LocationIdentity locationIdentity;

    @Input protected ValueNode array;
    @Input protected ValueNode offset;
    @Input protected ValueNode length;

    @OptionalInput(Memory) private MemoryKill lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    /**
     * This constructor is used by the {@code NodeIntrinsic} plugins below, which in turn are used
     * only in {@code AMD64CalcStringAttributesStub}, which is why we are using
     * {@link LocationIdentity#any()} here. The nodes calling the stubs are using more fine-grained
     * location identities, but are calling the same stubs (after
     * {@link #generate(NodeLIRBuilderTool) assembly generation}).
     */
    protected AMD64CalcStringAttributesNode(
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    ValueNode array, ValueNode offset, ValueNode length) {
        this(op, assumeValid, LocationIdentity.any(), array, offset, length);
    }

    public AMD64CalcStringAttributesNode(
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    LocationIdentity locationIdentity,
                    ValueNode array, ValueNode offset, ValueNode length) {
        super(TYPE, StampFactory.forKind(op == AMD64CalcStringAttributesOp.Op.UTF_8 || op == AMD64CalcStringAttributesOp.Op.UTF_16 ? JavaKind.Long : JavaKind.Int));
        this.op = op;
        this.assumeValid = assumeValid;
        this.locationIdentity = locationIdentity;
        this.array = array;
        this.offset = offset;
        this.length = length;
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
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
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
        Value result = gen.getLIRGeneratorTool().emitCalcStringAttributes(op, gen.operand(array), gen.operand(offset), gen.operand(length), assumeValid);
        gen.setResult(this, result);
    }

    /* NodeIntrinsic plugins for snippet stubs. */

    @NodeIntrinsic
    private static native int intReturnValue(
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    Object array, long offset, int length);

    @NodeIntrinsic
    private static native long longReturnValue(
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    Object array, long offset, int length);

    public static int latin1Internal(Object array, long offset, int length) {
        return intReturnValue(AMD64CalcStringAttributesOp.Op.LATIN1, false, array, offset, length);
    }

    public static int bmpInternal(Object array, long offset, int length) {
        return intReturnValue(AMD64CalcStringAttributesOp.Op.BMP, false, array, offset, length);
    }

    public static long utf8Internal(boolean assumeValid, Object array, long offset, int length) {
        return longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_8, assumeValid, array, offset, length);
    }

    public static long utf16Internal(boolean assumeValid, Object array, long offset, int length) {
        return longReturnValue(AMD64CalcStringAttributesOp.Op.UTF_16, assumeValid, array, offset, length);
    }

    public static int utf32Internal(Object array, long offset, int length) {
        return intReturnValue(AMD64CalcStringAttributesOp.Op.UTF_32, false, array, offset, length);
    }
}
