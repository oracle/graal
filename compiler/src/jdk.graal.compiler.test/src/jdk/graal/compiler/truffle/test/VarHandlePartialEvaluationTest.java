/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.graal.compiler.truffle.test.nodes.AbstractTestNode;
import jdk.graal.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class VarHandlePartialEvaluationTest extends PartialEvaluationTest {

    static final VarHandle byteArrayHandle = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
    static final VarHandle byteBufferHandle = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    /**
     * Tests partial evaluation of a byte array view {@link VarHandle#get}.
     */
    @Test
    public void byteArrayHandleGet() {
        byte[] array = ByteBuffer.allocate(42).order(ByteOrder.nativeOrder()).putInt(0, 42).array();
        testCommon(new VarHandleTestNode(true, false), "byteArrayHandleGetInt", array, 0);
    }

    /**
     * Tests partial evaluation of a byte array view {@link VarHandle#set}.
     */
    @Test
    public void byteArrayHandleSet() {
        byte[] array = ByteBuffer.allocate(42).order(ByteOrder.nativeOrder()).putInt(0, 42).array();
        testCommon(new VarHandleTestNode(true, true), "byteArrayHandleSetInt", array, 0, 42);
    }

    /**
     * Tests partial evaluation of a byte buffer view {@link VarHandle#get}.
     */
    @Test
    public void byteBufferHandleGet() {
        Assume.assumeTrue(isByteBufferPartialEvaluationSupported());
        ByteBuffer byteBuffer = ByteBuffer.allocate(42).order(ByteOrder.nativeOrder()).putInt(0, 42);
        testCommon(new VarHandleTestNode(false, false), "byteBufferHandleGetInt", byteBuffer, 0);
    }

    /**
     * Tests partial evaluation of a byte buffer view {@link VarHandle#set}.
     */
    @Test
    public void byteBufferHandleSet() {
        Assume.assumeTrue(isByteBufferPartialEvaluationSupported());
        ByteBuffer byteBuffer = ByteBuffer.allocate(42).order(ByteOrder.nativeOrder()).putInt(0, 42);
        testCommon(new VarHandleTestNode(false, true), "byteArrayHandleSetInt", byteBuffer, 0, 42);
    }

    private void testCommon(AbstractTestNode testNode, String testName, Object... args) {
        FrameDescriptor fd = new FrameDescriptor();
        RootNode rootNode = new RootTestNode(fd, testName, testNode);
        RootCallTarget callTarget = rootNode.getCallTarget();
        Assert.assertEquals(42, callTarget.call(args));
        assertPartialEvalNoInvokes(callTarget, args);
    }

    static final class VarHandleTestNode extends AbstractTestNode {
        private final boolean isArray;
        private final boolean set;

        VarHandleTestNode(boolean isArray, boolean set) {
            this.isArray = isArray;
            this.set = set;
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object buf = frame.getArguments()[0];
            int idx = (int) frame.getArguments()[1];
            if (set) {
                int val = (int) frame.getArguments()[2];
                if (isArray) {
                    byteArrayHandle.set((byte[]) buf, idx, val);
                } else {
                    byteBufferHandle.set((ByteBuffer) buf, idx, val);
                }
                return val;
            } else {
                if (isArray) {
                    return (int) byteArrayHandle.get((byte[]) buf, idx);
                } else {
                    return (int) byteBufferHandle.get((ByteBuffer) buf, idx);
                }
            }
        }
    }
}
