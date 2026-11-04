/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/** Tests explicit execution metadata on regular and proxied DSL nodes. */
public class ProxyExecutionMetadataTest {

    private static final int A = 1009;
    private static final int B = 1013;
    private static final int C = 1019;
    private static final int D = 1021;
    private static final int SUM = A + B + C + D;

    @Test
    public void testRegularCachedAndUncachedExecutionMetadata() {
        MetadataRootNode firstRoot = createAllMetadataRoot();
        MetadataRootNode secondRoot = createAllMetadataRoot();
        BytecodeNode firstBytecode = firstRoot.getBytecodeNode();
        BytecodeNode secondBytecode = secondRoot.getBytecodeNode();
        assertNotEquals(firstBytecode, secondBytecode);

        VirtualFrame firstFrame = Truffle.getRuntime().createVirtualFrame(new Object[]{"first"}, FrameDescriptor.newBuilder().build());
        VirtualFrame secondFrame = Truffle.getRuntime().createVirtualFrame(new Object[]{"second"}, FrameDescriptor.newBuilder().build());

        AllMetadataNode cached = ProxyExecutionMetadataTestFactory.AllMetadataNodeGen.create();
        assertAllMetadata(cached.execute(firstFrame, firstBytecode, 37, A, B, C, D), firstFrame, firstBytecode, 37);
        assertAllMetadata(cached.execute(secondFrame, secondBytecode, 81, A, B, C, D), secondFrame, secondBytecode, 81);
        assertAllMetadata(cached.execute(firstFrame, null, -1, A, B, C, D), firstFrame, null, -1);

        AllMetadataNode uncached = ProxyExecutionMetadataTestFactory.AllMetadataNodeGen.getUncached();
        assertAllMetadata(uncached.execute(firstFrame, firstBytecode, 103, A, B, C, D), firstFrame, firstBytecode, 103);
        assertAllMetadata(uncached.execute(secondFrame, secondBytecode, 211, A, B, C, D), secondFrame, secondBytecode, 211);
        assertAllMetadata(uncached.execute(secondFrame, null, -1, A, B, C, D), secondFrame, null, -1);
    }

    @Test
    public void testOperationProxySuppliesCurrentMetadata() {
        MetadataRootNode root = createAllMetadataRoot();
        int expectedBci = findInstructionBytecodeIndex(root, "c.AllMetadata");
        assertTrue("the test must exercise a real, nonzero instruction index", expectedBci > 0);

        Observation uncached = (Observation) root.getCallTarget().call();
        assertProxyMetadata(uncached, root, expectedBci);

        root.getBytecodeNode().setUncachedThreshold(0);
        Observation cached = (Observation) root.getCallTarget().call();
        assertProxyMetadata(cached, root, expectedBci);
    }

    @Test
    public void testTypedExecuteDelegationPreservesMetadata() throws UnexpectedResultException {
        MetadataRootNode firstRoot = createAllMetadataRoot();
        MetadataRootNode secondRoot = createAllMetadataRoot();
        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], FrameDescriptor.newBuilder().build());
        TypedMetadataNode node = ProxyExecutionMetadataTestFactory.TypedMetadataNodeGen.create();

        assertEquals(48, node.executeInt(frame, firstRoot.getBytecodeNode(), 37, 11));
        assertEquals(94, node.execute(frame, secondRoot.getBytecodeNode(), 81, 13));

        try {
            node.executeInt(frame, firstRoot.getBytecodeNode(), 103, "typed fallback");
            fail("expected a non-int specialization result");
        } catch (UnexpectedResultException e) {
            assertTypedFallback((TypedFallback) e.getResult(), frame, firstRoot.getBytecodeNode(), 103, "typed fallback");
        }
        assertTypedFallback((TypedFallback) node.execute(frame, secondRoot.getBytecodeNode(), 211, "generic fallback"), frame,
                        secondRoot.getBytecodeNode(), 211, "generic fallback");
    }

    @Test
    public void testInlineNodeMetadataPrefix() {
        MetadataRootNode firstRoot = createAllMetadataRoot();
        MetadataRootNode secondRoot = createAllMetadataRoot();
        VirtualFrame firstFrame = Truffle.getRuntime().createVirtualFrame(new Object[]{"inline cached"}, FrameDescriptor.newBuilder().build());
        VirtualFrame secondFrame = Truffle.getRuntime().createVirtualFrame(new Object[]{"inline uncached"}, FrameDescriptor.newBuilder().build());

        InlineParentNode cached = ProxyExecutionMetadataTestFactory.InlineParentNodeGen.create();
        assertInlineMetadata(cached.execute(firstFrame, firstRoot.getBytecodeNode(), 307, "cached"), cached,
                        firstFrame, firstRoot.getBytecodeNode(), 307, "cached");

        InlineParentNode uncached = ProxyExecutionMetadataTestFactory.InlineParentNodeGen.getUncached();
        // An uncached node binds its own singleton as $node, rather than the inlining target.
        assertInlineMetadata(uncached.execute(secondFrame, secondRoot.getBytecodeNode(), 401, "uncached"), ProxyExecutionMetadataTestFactory.InlineMetadataNodeGen.getUncached(),
                        secondFrame, secondRoot.getBytecodeNode(), 401, "uncached");
    }

    @Test
    public void testQuickenedProxyMetadata() {
        MetadataRootNode root = MetadataRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTypedMetadata();
            b.emitLoadArgument(0);
            b.endTypedMetadata();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        int expectedBci = findInstructionBytecodeIndex(root, "c.TypedMetadata");
        assertEquals(expectedBci + 11, root.getCallTarget().call(11));
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(expectedBci + 29, root.getCallTarget().call(29));
        assertEquals(expectedBci + 43, root.getCallTarget().call(43));
        TypedFallback fallback = (TypedFallback) root.getCallTarget().call("fallback");
        assertSame(root.getBytecodeNode(), fallback.bytecode);
        assertEquals(expectedBci, fallback.bytecodeIndex);
        assertEquals("fallback", fallback.value);
    }

    @Test
    public void testChildExecutionPreservesMetadata() {
        MetadataChildNode child = ProxyExecutionMetadataTestFactory.MetadataChildNodeGen.create();
        MetadataChildParentNode parent = ProxyExecutionMetadataTestFactory.MetadataChildParentNodeGen.create(child);
        assertEquals(73, parent.execute(73));
        assertEquals(109, parent.execute(109));
    }

    @Test
    public void testMetadataFlagsAreIndependent() {
        MetadataRootNode metadataRoot = createAllMetadataRoot();
        BytecodeNode explicitBytecode = metadataRoot.getBytecodeNode();

        int[] bciOnly = ProxyExecutionMetadataTestFactory.BytecodeIndexOnlyNodeGen.create().execute(73, A);
        assertEquals(73, bciOnly[0]);
        assertEquals(A, bciOnly[1]);

        Object[] nodeOnly = ProxyExecutionMetadataTestFactory.BytecodeNodeOnlyNodeGen.create().execute(explicitBytecode, B);
        assertSame(explicitBytecode, nodeOnly[0]);
        assertEquals(B, nodeOnly[1]);

        Frame ordinaryFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{"ordinary"});
        Object[] disabled = ProxyExecutionMetadataTestFactory.DisabledMetadataNodeGen.create().execute(ordinaryFrame, C);
        assertSame(ordinaryFrame, disabled[0]);
        assertEquals(-1, disabled[1]);
        assertEquals(C, disabled[2]);

        assertBytecodeIndexOnlyProxy();
        assertBytecodeNodeOnlyProxy();
        assertDisabledMetadataProxy(ordinaryFrame);
    }

    private static void assertBytecodeIndexOnlyProxy() {
        MetadataRootNode root = createBytecodeIndexOnlyRoot();
        int expectedBci = findInstructionBytecodeIndex(root, "c.BytecodeIndexOnly");
        int[] uncached = (int[]) root.getCallTarget().call();
        assertEquals(expectedBci, uncached[0]);
        assertEquals(A, uncached[1]);
        root.getBytecodeNode().setUncachedThreshold(0);
        int[] cached = (int[]) root.getCallTarget().call();
        assertEquals(expectedBci, cached[0]);
        assertEquals(A, cached[1]);
    }

    private static void assertBytecodeNodeOnlyProxy() {
        MetadataRootNode root = createBytecodeNodeOnlyRoot();
        Object[] uncached = (Object[]) root.getCallTarget().call();
        assertSame(root.getBytecodeNode(), uncached[0]);
        assertEquals(B, uncached[1]);
        root.getBytecodeNode().setUncachedThreshold(0);
        Object[] cached = (Object[]) root.getCallTarget().call();
        assertSame(root.getBytecodeNode(), cached[0]);
        assertEquals(B, cached[1]);
    }

    private static void assertDisabledMetadataProxy(Frame guestFrame) {
        MetadataRootNode root = createDisabledMetadataRoot(guestFrame);
        int expectedBci = findInstructionBytecodeIndex(root, "c.DisabledMetadata");
        Object[] uncached = (Object[]) root.getCallTarget().call();
        assertSame(guestFrame, uncached[0]);
        assertEquals(expectedBci, uncached[1]);
        assertEquals(C, uncached[2]);
        root.getBytecodeNode().setUncachedThreshold(0);
        Object[] cached = (Object[]) root.getCallTarget().call();
        assertSame(guestFrame, cached[0]);
        assertEquals(expectedBci, cached[1]);
        assertEquals(C, cached[2]);
    }

    private static void assertAllMetadata(Observation observation, VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex) {
        assertSame(frame, observation.legacyFrame);
        assertSame(frame, observation.boundFrame);
        if (bytecode == null) {
            assertNull(observation.defaultBytecode);
            assertNull(observation.bytecode);
        } else {
            assertSame(bytecode, observation.defaultBytecode);
            assertSame(bytecode, observation.bytecode);
        }
        assertEquals(bytecodeIndex, observation.bytecodeIndex);
        assertEquals(bytecodeIndex, observation.cachedBytecodeIndex);
        assertEquals(SUM, observation.sum);
    }

    private static void assertProxyMetadata(Observation observation, MetadataRootNode root, int bytecodeIndex) {
        assertNotNull(observation.legacyFrame);
        assertSame(observation.legacyFrame, observation.boundFrame);
        assertSame(root.getBytecodeNode(), observation.defaultBytecode);
        assertSame(root.getBytecodeNode(), observation.bytecode);
        assertEquals(bytecodeIndex, observation.bytecodeIndex);
        assertEquals(bytecodeIndex, observation.cachedBytecodeIndex);
        assertEquals(SUM, observation.sum);
    }

    private static void assertTypedFallback(TypedFallback fallback, VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value) {
        assertSame(frame, fallback.frame);
        assertSame(bytecode, fallback.bytecode);
        assertEquals(bytecodeIndex, fallback.bytecodeIndex);
        assertEquals(value, fallback.value);
    }

    private static void assertInlineMetadata(InlineObservation observation, Node parent, VirtualFrame frame,
                    BytecodeNode bytecode, int bytecodeIndex, Object value) {
        assertSame(parent, observation.inliningTarget);
        assertSame(frame, observation.frame);
        assertSame(bytecode, observation.bytecode);
        assertEquals(bytecodeIndex, observation.bytecodeIndex);
        assertEquals(value, observation.value);
    }

    private static MetadataRootNode createAllMetadataRoot() {
        return MetadataRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAllMetadata();
            b.emitLoadConstant(A);
            b.emitLoadConstant(B);
            b.emitLoadConstant(C);
            b.emitLoadConstant(D);
            b.endAllMetadata();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
    }

    private static MetadataRootNode createBytecodeIndexOnlyRoot() {
        return MetadataRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBytecodeIndexOnly();
            b.emitLoadConstant(A);
            b.endBytecodeIndexOnly();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
    }

    private static MetadataRootNode createBytecodeNodeOnlyRoot() {
        return MetadataRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBytecodeNodeOnly();
            b.emitLoadConstant(B);
            b.endBytecodeNodeOnly();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
    }

    private static MetadataRootNode createDisabledMetadataRoot(Frame guestFrame) {
        return MetadataRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginDisabledMetadata();
            b.emitLoadConstant(guestFrame);
            b.emitLoadConstant(C);
            b.endDisabledMetadata();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
    }

    private static int findInstructionBytecodeIndex(BytecodeRootNode root, String name) {
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().equals(name)) {
                return instruction.getBytecodeIndex();
            }
        }
        throw new AssertionError("Instruction not found: " + name);
    }

    static final class Observation {
        final VirtualFrame legacyFrame;
        final Object boundFrame;
        final BytecodeNode defaultBytecode;
        final BytecodeNode bytecode;
        final int bytecodeIndex;
        final int cachedBytecodeIndex;
        final int sum;

        Observation(VirtualFrame legacyFrame, Object boundFrame, BytecodeNode defaultBytecode,
                        BytecodeNode bytecode, int bytecodeIndex, int cachedBytecodeIndex, int sum) {
            this.legacyFrame = legacyFrame;
            this.boundFrame = boundFrame;
            this.defaultBytecode = defaultBytecode;
            this.bytecode = bytecode;
            this.bytecodeIndex = bytecodeIndex;
            this.cachedBytecodeIndex = cachedBytecodeIndex;
            this.sum = sum;
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true, bindBytecodeNode = true, bindBytecodeIndex = true)
    abstract static class AllMetadataNode extends Node {

        abstract Observation execute(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, int a, int b, int c, int d);

        @Specialization(guards = "bytecodeIndex == cachedBytecodeIndex", limit = "3")
        // Explicit expressions intentionally verify equivalence with implicit bindings.
        @SuppressWarnings("truffle-unused")
        static Observation doInts(VirtualFrame frame, int a, int b, int c, int d,
                        @Bind("$frame") Object boundFrame,
                        @Bind BytecodeNode defaultBytecode,
                        @Bind("$bytecodeNode") BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Cached(value = "bytecodeIndex", neverDefault = false) int cachedBytecodeIndex) {
            return new Observation(frame, boundFrame, defaultBytecode, bytecode, bytecodeIndex, cachedBytecodeIndex, a + b + c + d);
        }
    }

    static final class TypedFallback {
        final VirtualFrame frame;
        final BytecodeNode bytecode;
        final int bytecodeIndex;
        final Object value;

        TypedFallback(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value) {
            this.frame = frame;
            this.bytecode = bytecode;
            this.bytecodeIndex = bytecodeIndex;
            this.value = value;
        }
    }

    static final class InlineObservation {
        final Node inliningTarget;
        final VirtualFrame frame;
        final BytecodeNode bytecode;
        final int bytecodeIndex;
        final Object value;

        InlineObservation(Node inliningTarget, VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value) {
            this.inliningTarget = inliningTarget;
            this.frame = frame;
            this.bytecode = bytecode;
            this.bytecodeIndex = bytecodeIndex;
            this.value = value;
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true, bindBytecodeNode = true, bindBytecodeIndex = true)
    abstract static class TypedMetadataNode extends Node {

        abstract Object execute(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value);

        abstract int executeInt(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value) throws UnexpectedResultException;

        @Specialization
        static int doInt(int value, @Bind("$bytecodeIndex") int bytecodeIndex) {
            return bytecodeIndex + value;
        }

        @Fallback
        static TypedFallback doOther(VirtualFrame frame, Object value,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bytecodeIndex) {
            return new TypedFallback(frame, bytecode, bytecodeIndex, value);
        }
    }

    @GenerateInline
    @GenerateUncached
    @OperationProxy.Proxyable(allowUncached = true, bindBytecodeNode = true, bindBytecodeIndex = true)
    abstract static class InlineMetadataNode extends Node {

        abstract InlineObservation execute(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Node inliningTarget, Object value);

        @Specialization
        static InlineObservation doValue(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bytecodeIndex) {
            return new InlineObservation(inliningTarget, frame, bytecode, bytecodeIndex, value);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class InlineParentNode extends Node {

        abstract InlineObservation execute(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value);

        @Specialization
        static InlineObservation doValue(VirtualFrame frame, BytecodeNode bytecode, int bytecodeIndex, Object value,
                        @Bind Node inliningTarget,
                        @Cached(inline = true) InlineMetadataNode inlineNode) {
            return inlineNode.execute(frame, bytecode, bytecodeIndex, inliningTarget, value);
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindFrame = false, bindBytecodeIndex = true)
    abstract static class MetadataChildNode extends Node {

        abstract int execute(int bytecodeIndex);

        @Specialization
        static int doDefault(@Bind("$bytecodeIndex") int bytecodeIndex) {
            return bytecodeIndex;
        }
    }

    @NodeChild(value = "child", type = MetadataChildNode.class)
    @OperationProxy.Proxyable(bindFrame = false, bindBytecodeIndex = true)
    abstract static class MetadataChildParentNode extends Node {

        abstract int execute(int bytecodeIndex);

        @Specialization
        static int doDefault(int child) {
            return child;
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true, bindFrame = false, bindBytecodeIndex = true)
    abstract static class BytecodeIndexOnlyNode extends Node {

        abstract int[] execute(int bytecodeIndex, int value);

        @Specialization
        static int[] doInt(int value, @Bind("$bytecodeIndex") int bytecodeIndex) {
            return new int[]{bytecodeIndex, value};
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true, bindFrame = false, bindBytecodeNode = true)
    abstract static class BytecodeNodeOnlyNode extends Node {

        abstract Object[] execute(BytecodeNode bytecode, int value);

        @Specialization
        static Object[] doInt(int value, @Bind BytecodeNode bytecode) {
            return new Object[]{bytecode, value};
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true, bindFrame = false)
    abstract static class DisabledMetadataNode extends Node {

        abstract Object[] execute(Frame frame, int value);

        @Specialization
        static Object[] doFrame(Frame frame, int value, @Bind("$bytecodeIndex") int bytecodeIndex) {
            return new Object[]{frame, bytecodeIndex, value};
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableQuickening = true, boxingEliminationTypes = {int.class})
    @OperationProxy(AllMetadataNode.class)
    @OperationProxy(TypedMetadataNode.class)
    @OperationProxy(BytecodeIndexOnlyNode.class)
    @OperationProxy(BytecodeNodeOnlyNode.class)
    @OperationProxy(DisabledMetadataNode.class)
    abstract static class MetadataRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected MetadataRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }
}
