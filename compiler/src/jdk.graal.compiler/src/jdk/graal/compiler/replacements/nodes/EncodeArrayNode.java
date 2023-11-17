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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CharsetName;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code java.lang.StringCoding.implEncodeISOArray} and
 * {@code java.lang.StringCoding.implEncodeAsciiArray}. It encodes the provided byte/char array with
 * the specified encoding and stores the result into a distinct array.
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
public final class EncodeArrayNode extends MemoryKillStubIntrinsicNode {
    public static final NodeClass<EncodeArrayNode> TYPE = NodeClass.create(EncodeArrayNode.class);

    private static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    private static final ForeignCallDescriptor STUB_ASCII = foreignCallDescriptor("stringCodingEncodeArrayAscii");
    private static final ForeignCallDescriptor STUB_LATIN_1 = foreignCallDescriptor("stringCodingEncodeArrayLatin1");
    public static final ForeignCallDescriptor[] STUBS = {STUB_ASCII, STUB_LATIN_1};

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return new ForeignCallDescriptor(name, int.class, new Class<?>[]{Pointer.class, Pointer.class, int.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false,
                        false);
    }

    @Input protected ValueNode src;
    @Input protected ValueNode dst;
    @Input protected ValueNode len;

    private final CharsetName charset;

    public EncodeArrayNode(ValueNode src, ValueNode dst, ValueNode len, CharsetName charset, JavaKind readKind) {
        this(src, dst, len, charset, null, NamedLocationIdentity.getArrayLocation(readKind));
    }

    /**
     * Constructor for stub compilation. We set {@code locationIdentity} to
     * {@link LocationIdentity#any()} here, because we want to re-use the same stub for call sites
     * reading byte and char arrays.
     */
    public EncodeArrayNode(ValueNode src, ValueNode dst, ValueNode len, CharsetName charset) {
        this(src, dst, len, charset, null, LocationIdentity.any());
    }

    public EncodeArrayNode(ValueNode src, ValueNode dst, ValueNode len, CharsetName charset, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(src, dst, len, charset, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    private EncodeArrayNode(ValueNode src, ValueNode dst, ValueNode len, CharsetName charset, EnumSet<?> runtimeCheckedCPUFeatures, LocationIdentity locationIdentity) {
        super(TYPE, IntegerStamp.create(32, 0, ((IntegerStamp) len.stamp(NodeView.DEFAULT)).upperBound()), runtimeCheckedCPUFeatures, locationIdentity);
        this.src = src;
        this.dst = dst;
        this.len = len;
        this.charset = charset;
        GraalError.guarantee(charset == CharsetName.ASCII || charset == CharsetName.ISO_8859_1, "charset must be one of: ASCII, ISO_8859_1");
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return charset == CharsetName.ASCII ? STUB_ASCII : STUB_LATIN_1;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{src, dst, len};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitEncodeArray(runtimeCheckedCPUFeatures, gen.operand(src), gen.operand(dst), gen.operand(len), charset));
    }

    @NodeIntrinsic
    @GenerateStub(name = "stringCodingEncodeArrayAscii", parameters = "ASCII")
    @GenerateStub(name = "stringCodingEncodeArrayLatin1", parameters = "ISO_8859_1")
    public static native int stringCodingEncodeArray(Pointer src, Pointer dst, int len, @ConstantNodeParameter CharsetName charSet);

    @NodeIntrinsic
    public static native int stringCodingEncodeArray(Pointer src, Pointer dst, int len, @ConstantNodeParameter CharsetName charSet, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
