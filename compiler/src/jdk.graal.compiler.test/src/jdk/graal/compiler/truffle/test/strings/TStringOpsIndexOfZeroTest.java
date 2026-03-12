/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.replacements.nodes.IndexOfZeroNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TStringOpsIndexOfZeroTest extends TStringOpsTest<IndexOfZeroNode> {

    public TStringOpsIndexOfZeroTest() {
        super(IndexOfZeroNode.class);
    }

    @Test
    public void testIndexOfNull() {
        byte[] src = new byte[8192];
        Arrays.fill(src, (byte) 1);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(src.length);
        try {
            for (Stride stride : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                ResolvedJavaMethod method = getTStringOpsMethod("strlen" + (stride.value * 8) + "Bit", long.class);

                long bufferAddress = getBufferAddress(byteBuffer);
                UNSAFE.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, bufferAddress, src.length);
                for (int i = 0; i < src.length; i += stride.value) {
                    switch (stride) {
                        case S1 -> {
                            byteBuffer.put(i, (byte) 0);
                            test(method, null, DUMMY_LOCATION, bufferAddress);
                            for (int j = Math.max(0, i - 256); j < i; j++) {
                                test(method, null, DUMMY_LOCATION, bufferAddress + j);
                            }
                            byteBuffer.put(i, (byte) 1);
                        }
                        case S2 -> {
                            byteBuffer.putShort(i, (byte) 0);
                            test(method, null, DUMMY_LOCATION, bufferAddress);
                            for (int j = Math.max(0, i - 256); j < i; j += 2) {
                                test(method, null, DUMMY_LOCATION, bufferAddress + j);
                            }
                            byteBuffer.putShort(i, (byte) 1);
                        }
                        case S4 -> {
                            byteBuffer.putInt(i, (byte) 0);
                            test(method, null, DUMMY_LOCATION, bufferAddress);
                            for (int j = Math.max(0, i - 256); j < i; j += 4) {
                                test(method, null, DUMMY_LOCATION, bufferAddress + j);
                            }
                            byteBuffer.putInt(i, (byte) 1);
                        }
                    }
                }
            }
        } finally {
            Reference.reachabilityFence(byteBuffer);
        }
    }
}
