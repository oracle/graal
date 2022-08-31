/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ValueProfile;

public class CastExactPartialEvaluationTest extends PartialEvaluationTest {

    /**
     * Partial evaluation (of ByteBuffer code) only works with currently supported JDK versions.
     */
    private static boolean isSupportedJavaVersion() {
        return JavaVersionUtil.JAVA_SPEC == 11 || JavaVersionUtil.JAVA_SPEC == 17 || JavaVersionUtil.JAVA_SPEC >= 19;
    }

    @Before
    public void setup() {
        // Ensure read-only byte buffer subclass is loaded.
        ByteBuffer.allocate(42).putInt(42).asReadOnlyBuffer();

        // Ensure exception paths are resolved for reproducibility.
        try {
            ByteBuffer.allocate(0).getInt(0);
            Assert.fail();
        } catch (IndexOutOfBoundsException ex) {
        }
        try {
            ByteBuffer.allocate(0).getInt();
            Assert.fail();
        } catch (BufferUnderflowException ex) {
        }
        try {
            ByteBuffer.allocate(0).putInt(0);
            Assert.fail();
        } catch (BufferOverflowException ex) {
        }
        try {
            ByteBuffer.allocate(0).limit(1);
            Assert.fail();
        } catch (IllegalArgumentException ex) {
        }
        try {
            ByteBuffer.allocate(0).position(1);
            Assert.fail();
        } catch (IllegalArgumentException ex) {
        }
    }

    /**
     * Tests the effect of {@link CompilerDirectives#castExact} on method devirtualization.
     */
    @Test
    public void castExactCompilerDirective() {
        AbstractTestNode result = new CastExactTestNode(bufferClass());
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
        Assume.assumeTrue(isSupportedJavaVersion());
        AbstractTestNode result = new BufferGetPutTestNode(bufferClass());
        testCommon(result, "byteBufferAccess");
    }

    @Test
    public void byteBufferAccessIndex() {
        Assume.assumeTrue(isSupportedJavaVersion());
        AbstractTestNode result = new BufferGetPutIndexTestNode(bufferClass());
        testCommon(result, "byteBufferAccessIndex");
    }

    private void testCommon(AbstractTestNode testNode, String testName) {
        FrameDescriptor fd = new FrameDescriptor();
        RootNode rootNode = new RootTestNode(fd, testName, testNode);
        RootCallTarget callTarget = rootNode.getCallTarget();
        Assert.assertEquals(42, callTarget.call(newBuffer()));
        assertPartialEvalNoInvokes(callTarget, new Object[]{newBuffer()});
    }

    @Test
    public void byteBufferLimitException() {
        testExceptionSpeculationCommon(new BufferExceptionTestNode(bufferClass(), BufferMethod.limit), "byteBufferLimitException", true);
    }

    @Test
    public void byteBufferPositionException() {
        testExceptionSpeculationCommon(new BufferExceptionTestNode(bufferClass(), BufferMethod.position), "byteBufferPositionException", true);
    }

    @Test
    public void byteBufferDuplicate() {
        testExceptionSpeculationCommon(new BufferExceptionTestNode(bufferClass(), BufferMethod.duplicate), "byteBufferDuplicate", false);
    }

    private void testExceptionSpeculationCommon(AbstractTestNode testNode, String testName, boolean expectException) {
        Assume.assumeTrue(isSupportedJavaVersion());
        RootNode rootNode = new RootTestNode(testName, testNode);
        OptimizedCallTarget callTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        Object[] arguments = {newBuffer()};
        Assert.assertEquals(42, callTarget.call(arguments));

        CompilationIdentifier compilationId = getCompilationId(callTarget);
        StructuredGraph graph = partialEval(callTarget, arguments, compilationId);
        compile(callTarget, graph);
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found unexpected method call target node: " + node + " (" + node.targetMethod() + ")");
        }
        Assert.assertTrue(callTarget.isValid());
        callTarget.call(arguments);

        if (expectException) {
            // Speculation has failed and invalidated the code.
            Assert.assertFalse("Speculation should have failed", callTarget.isValid());
            // Recompile to ensure partial evaluation still succeeds.
            partialEval(callTarget, arguments, compilationId);
        } else {
            Assert.assertTrue("Speculation should not have failed", callTarget.isValid());
        }
    }

    private static ByteBuffer newBuffer() {
        return ByteBuffer.allocate(42).putInt(0, 42);
    }

    private static Class<? extends ByteBuffer> bufferClass() {
        return newBuffer().getClass();
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

    static class BufferGetPutIndexTestNode extends AbstractTestNode {
        private final Class<? extends ByteBuffer> bufferClass;

        BufferGetPutIndexTestNode(Class<? extends ByteBuffer> exactClass) {
            this.bufferClass = exactClass;
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            ByteBuffer buffer = CompilerDirectives.castExact(arg, bufferClass);
            int value = buffer.getInt(0);
            buffer.putInt(0, value);
            return buffer.getInt(0);
        }
    }

    enum BufferMethod {
        limit,
        position,
        duplicate,
    }

    static class BufferExceptionTestNode extends AbstractTestNode {
        private final Class<? extends ByteBuffer> bufferClass;

        private final BufferMethod bufferMethod;

        BufferExceptionTestNode(Class<? extends ByteBuffer> exactClass, BufferMethod bufferMethod) {
            this.bufferClass = exactClass;
            this.bufferMethod = bufferMethod;
        }

        @Override
        public int execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            ByteBuffer buffer = CompilerDirectives.castExact(arg, bufferClass);
            switch (bufferMethod) {
                case limit:
                    try {
                        buffer.limit(43);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    break;
                case position:
                    try {
                        buffer.position(43);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    break;
                case duplicate:
                    return buffer.duplicate().getInt(0);
            }
            return buffer.getInt(0);
        }
    }
}
