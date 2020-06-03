/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ValueProfile;

public class CastExactPartialEvaluationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        // Ensure read-only byte buffer subclass is loaded.
        ByteBuffer.allocate(42).putInt(42).asReadOnlyBuffer();

        // Ensure exception paths are resolved for reproducibility.
        try {
            ByteBuffer.allocate(0).getInt(0);
        } catch (IndexOutOfBoundsException ex) {
        }
        try {
            ByteBuffer.allocate(0).getInt();
        } catch (BufferUnderflowException ex) {
        }
        try {
            ByteBuffer.allocate(0).putInt(0);
        } catch (BufferOverflowException ex) {
        }
    }

    /**
     * Tests the effect of {@link CompilerDirectives#castExact} on method devirtualization.
     */
    @Test
    public void castExactCompilerDirective() {
        AbstractTestNode result = new CastExactTestNode(newBuffer().getClass());
        testCommon(result, "castExactCompilerDirective");
    }

    /**
     * Tests that the profile created by {@link ValueProfile#createClassProfile()} casts the
     * argument to the exact profiled class.
     */
    @Test
    public void exactClassProfile() {
        AbstractTestNode result = new ExactClassProfileTestNode();
        testCommon(result, "exactClassProfile");
    }

    /**
     * Tests that {@link ByteBuffer} accesses do not contain any method calls, e.g. for exceptions.
     */
    @Test
    public void byteBufferAccess() {
        Assume.assumeTrue("GR-23778", JavaVersionUtil.JAVA_SPEC <= 11);
        AbstractTestNode result = new BufferGetPutTestNode(newBuffer().getClass());
        testCommon(result, "byteBufferAccess");
    }

    private void testCommon(AbstractTestNode testNode, String testName) {
        FrameDescriptor fd = new FrameDescriptor();
        RootNode rootNode = new RootTestNode(fd, testName, testNode);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        Assert.assertEquals(42, callTarget.call(newBuffer()));
        assertPartialEvalNoInvokes(callTarget, new Object[]{newBuffer()});
    }

    private static ByteBuffer newBuffer() {
        return ByteBuffer.allocate(42).putInt(0, 42);
    }

    static class CastExactTestNode extends AbstractTestNode {
        private final Class<? extends ByteBuffer> bufferClass;

        CastExactTestNode(Class<? extends ByteBuffer> exactClass) {
            this.bufferClass = exactClass;
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            ByteBuffer buffer = CompilerDirectives.castExact(arg, bufferClass);
            return buffer.duplicate().slice().limit();
        }
    }

    static class ExactClassProfileTestNode extends AbstractTestNode {
        private final ValueProfile classProfile = ValueProfile.createClassProfile();

        ExactClassProfileTestNode() {
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            ByteBuffer buffer = (ByteBuffer) classProfile.profile(arg);
            return buffer.duplicate().slice().limit();
        }
    }

    static class BufferGetPutTestNode extends AbstractTestNode {
        private final Class<? extends ByteBuffer> bufferClass;

        BufferGetPutTestNode(Class<? extends ByteBuffer> exactClass) {
            this.bufferClass = exactClass;
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            ByteBuffer buffer = CompilerDirectives.castExact(arg, bufferClass);
            ByteBuffer dup = buffer.duplicate();
            int value = dup.getInt();
            int pos = dup.position();
            dup.putInt(value);
            return dup.getInt(pos);
        }
    }
}
