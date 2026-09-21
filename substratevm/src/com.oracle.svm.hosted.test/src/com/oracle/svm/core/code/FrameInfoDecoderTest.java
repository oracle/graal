/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;

import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.graal.compiler.nodes.FrameState;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class FrameInfoDecoderTest {
    @Test
    public void discardedVirtualObjectsPreserveLocals() {
        for (int virtualObjectCount = 0; virtualObjectCount <= 2; virtualObjectCount++) {
            FrameInfoQueryResult frame = decode(virtualObjectCount, new CodeInfoDecoder.SingleShotValueInfoAllocator(31));
            assertLocals(frame);
            Assert.assertNull(frame.getVirtualObjects());
        }
    }

    @Test
    public void retainedVirtualObjectsPreserveLocals() {
        FrameInfoQueryResult frame = decode(2, FrameInfoDecoder.HeapBasedValueInfoAllocator);
        assertLocals(frame);
        Assert.assertEquals(2, frame.getVirtualObjects().length);
        for (ValueInfo[] virtualObject : frame.getVirtualObjects()) {
            Assert.assertEquals(2, virtualObject.length);
            Assert.assertEquals(ValueType.Constant, virtualObject[0].getType());
            Assert.assertEquals(Object.class, FrameInfoDecoder.SubstrateConstantAccess.asObject(virtualObject[0].getValue()));
            assertStackSlot(virtualObject[1], JavaKind.Long, 112);
        }
    }

    private static void assertLocals(FrameInfoQueryResult frame) {
        assertStackSlot(frame.getValueInfos()[0], JavaKind.Object, 40);
        assertStackSlot(frame.getValueInfos()[1], JavaKind.Object, 32);
        Assert.assertEquals(42, frame.getSourceLineNumber());
        Assert.assertNull(frame.getCaller());
    }

    private static void assertStackSlot(ValueInfo value, JavaKind kind, long offset) {
        Assert.assertEquals(ValueType.StackSlot, value.getType());
        Assert.assertEquals(kind, value.getKind());
        Assert.assertEquals(offset, value.getData());
    }

    private static FrameInfoQueryResult decode(int virtualObjectCount, FrameInfoDecoder.ValueInfoAllocator allocator) {
        CodeInfoEncoder.Encoders encoders = new CodeInfoEncoder.Encoders(false, null, false);
        JavaConstant hub = FrameInfoDecoder.SubstrateConstantAccess.forObject(Object.class, false);
        encoders.objectConstants.addObject(hub);
        JavaConstant[] objectConstants = encoders.objectConstants.encodeAll(new JavaConstant[1]);

        FrameInfoQueryResult frame = new FrameInfoQueryResult();
        frame.encodedBci = FrameInfoEncoder.encodeBci(0, FrameState.StackState.BeforePop);
        frame.numLocals = 2;
        frame.valueInfos = new ValueInfo[]{stackSlot(JavaKind.Object, 40), stackSlot(JavaKind.Object, 32)};
        frame.virtualObjects = new ValueInfo[virtualObjectCount][];
        for (int i = 0; i < virtualObjectCount; i++) {
            ValueInfo hubValue = new ValueInfo();
            hubValue.type = ValueType.Constant;
            hubValue.kind = JavaKind.Object;
            hubValue.value = hub;
            frame.virtualObjects[i] = new ValueInfo[]{hubValue, stackSlot(JavaKind.Long, 112)};
        }
        frame.sourceLineNumber = 42;

        UnsafeArrayTypeWriter writer = UnsafeArrayTypeWriter.create(true);
        FrameInfoEncoder.encodeUncompressedFrameData(frame, writer, encoders, FrameInfoDecoder.SubstrateConstantAccess);

        ImageCodeInfo image = new ImageCodeInfo();
        image.objectConstants = new Object[objectConstants.length];
        for (int i = 0; i < objectConstants.length; i++) {
            image.objectConstants[i] = FrameInfoDecoder.SubstrateConstantAccess.asObject(objectConstants[i]);
        }
        byte[] bytes = writer.toArray();
        ReusableTypeReader reader = new ReusableTypeReader(NonmovableArrays.fromImageHeap((Object) bytes), 0);
        FrameInfoQueryResult result = FrameInfoDecoder.decodeFrameInfo(false, reader, image.new HostedImageCodeInfo(),
                        FrameInfoDecoder.HeapBasedFrameInfoQueryResultAllocator, allocator,
                        FrameInfoDecoder.SubstrateConstantAccess, new CodeInfoDecoder.FrameInfoState());
        Assert.assertEquals(bytes.length, reader.getByteIndex());
        return result;
    }

    private static ValueInfo stackSlot(JavaKind kind, long offset) {
        ValueInfo value = new ValueInfo();
        value.type = ValueType.StackSlot;
        value.kind = kind;
        value.data = offset;
        return value;
    }
}
