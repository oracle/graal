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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.function.IntFunction;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeIndexMode;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.bytecode.Yield;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Tests bytecode-index lookup and explicit frame stores in each mode, in both uncached and cached
 * interpreters. Also covers the legacy frame-storage option and proxied bytecode-index bindings.
 */
public class BytecodeIndexLookupTest {

    private static final int UNSUPPORTED = Integer.MIN_VALUE;

    record ProbeResult(int boundIndex, int locationBefore, int rootBefore, int rootNullBefore, int frameBefore,
                    boolean setterSupported, int frameAfter, int rootAfter, int rootNullAfter) {
    }

    @Test
    public void testBytecodeIndexModes() {
        FrameBytecodeIndexRootNode frame = FrameBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitProbe();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertTrue(frame.isCaptureFrames(false));
        assertTrue(frame.isCaptureFrames(true));
        assertMode(frame, bci -> new ProbeResult(bci, bci, bci, -1, bci, true, -1, -1, -1));

        CachedNodeBytecodeIndexRootNode cachedNode = CachedNodeBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitProbe();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertTrue(cachedNode.isCaptureFrames(false));
        assertFalse(cachedNode.isCaptureFrames(true));
        assertMode(cachedNode, bci -> new ProbeResult(bci, bci, bci, -1, UNSUPPORTED, false, UNSUPPORTED, bci, -1));

        ManualBytecodeIndexRootNode manual = ManualBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitProbe();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertTrue(manual.isCaptureFrames(false));
        assertTrue(manual.isCaptureFrames(true));
        assertMode(manual, bci -> new ProbeResult(bci, bci, bci, bci, bci, true, bci, bci, bci));

        ManualBytecodeIndexRootNode manualWithoutStore = ManualBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.emitClearBytecodeIndex();
            b.beginReturn();
            b.emitProbeWithoutStore();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertMode(manualWithoutStore, "c.ProbeWithoutStore", bci -> new ProbeResult(bci, -1, -1, -1, -1, true, bci, bci, bci));

        NoBytecodeIndexRootNode none = NoBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitProbe();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertFalse(none.isCaptureFrames(false));
        assertFalse(none.isCaptureFrames(true));
        assertMode(none, bci -> new ProbeResult(bci, -1, -1, -1, UNSUPPORTED, false, UNSUPPORTED, -1, -1));

        int slotsWithoutBytecodeIndex = none.getFrameDescriptor().getNumberOfSlots();
        assertEquals(slotsWithoutBytecodeIndex + 1, frame.getFrameDescriptor().getNumberOfSlots());
        assertEquals(slotsWithoutBytecodeIndex + 1, cachedNode.getFrameDescriptor().getNumberOfSlots());
        assertEquals(slotsWithoutBytecodeIndex + 1, manual.getFrameDescriptor().getNumberOfSlots());
    }

    @Test
    public void testLocalHelpersWithUnknownBytecodeIndex() {
        IndexRootNode[] roots = {
                        ManualBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
                            b.beginRoot();
                            b.beginReturn();
                            b.emitLoadConstant(42);
                            b.endReturn();
                            b.endRoot();
                        }).getNode(0),
                        NoBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
                            b.beginRoot();
                            b.beginReturn();
                            b.emitLoadConstant(42);
                            b.endReturn();
                            b.endRoot();
                        }).getNode(0)
        };
        for (IndexRootNode root : roots) {
            for (int tier = 0; tier < 2; tier++) {
                BytecodeNode bytecode = root.getBytecodeNode();
                Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getFrameDescriptor());
                if (root instanceof ManualBytecodeIndexRootNode) {
                    bytecode.setBytecodeIndex(frame, -1);
                }
                FrameInstance unknown = new FrameInstance() {
                    @Override
                    public Frame getFrame(FrameAccess access) {
                        return frame;
                    }

                    @Override
                    public boolean isVirtualFrame() {
                        return false;
                    }

                    @Override
                    public Node getCallNode() {
                        return bytecode;
                    }

                    @Override
                    public CallTarget getCallTarget() {
                        return root.getCallTarget();
                    }
                };
                assertEquals(-1, unknown.getBytecodeIndex());
                assertNull(BytecodeNode.getLocalValues(unknown));
                assertNull(BytecodeNode.getLocalNames(unknown));
                assertFalse(BytecodeNode.setLocalValues(unknown, new Object[]{43}));
                bytecode.setUncachedThreshold(0);
            }
        }
    }

    @Test
    public void testCustomYieldBytecodeIndexStorage() {
        for (var variant : YieldIndexRootNodeBuilder.variants()) {
            for (boolean explicitStore : new boolean[]{false, true}) {
                YieldIndexRootNode root = variant.create(null, BytecodeConfig.DEFAULT, b -> {
                    b.beginRoot();
                    b.emitClearBytecodeIndex();
                    b.beginReturn();
                    if (explicitStore) {
                        b.emitYieldWithStore();
                    } else {
                        b.emitYieldWithoutStore();
                    }
                    b.endReturn();
                    b.endRoot();
                }).getNode(0);
                int yieldBci = findInstructionBytecodeIndex(root, explicitStore ? "c.YieldWithStore" : "c.YieldWithoutStore");
                int expected = explicitStore || variant == YieldIndexRootNodeFrame.BYTECODE ? yieldBci : -1;
                for (int tier = 0; tier < 2; tier++) {
                    ContinuationResult result = (ContinuationResult) root.getCallTarget().call();
                    assertEquals(expected, result.getResult());
                    assertEquals(42, result.continueWith(42));
                    root.getBytecodeNode().setUncachedThreshold(0);
                }
            }
        }
    }

    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "Frame", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.FRAME)),
                    @Variant(suffix = "Manual", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.MANUAL))
    })
    public abstract static class YieldIndexRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected YieldIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        static final class ClearBytecodeIndex {
            @Specialization
            static void doDefault(VirtualFrame frame, @Bind BytecodeNode bytecode) {
                bytecode.setBytecodeIndex(frame, -1);
            }
        }

        @Yield
        static final class YieldWithoutStore {
            @Specialization
            @SuppressWarnings("truffle-unused") // Exercise the explicit $node binding from the regression.
            public static Object doYield(@Bind MaterializedFrame frame, @Bind ContinuationRootNode root,
                            @Bind BytecodeNode bytecode, @Bind("$node") Node node,
                            @Cached InlinedConditionProfile profile) {
                assertTrue(profile.profile(node, true));
                return ContinuationResult.create(root, frame, bytecode.getBytecodeIndex(frame));
            }
        }

        @Yield
        static final class YieldWithStore {
            @Specialization
            @SuppressWarnings("truffle-unused") // Exercise the explicit $node binding from the regression.
            @StoreBytecodeIndex
            public static Object doYield(@Bind MaterializedFrame frame, @Bind ContinuationRootNode root,
                            @Bind BytecodeNode bytecode, @Bind("$node") Node node,
                            @Cached InlinedConditionProfile profile) {
                assertTrue(profile.profile(node, true));
                return ContinuationResult.create(root, frame, bytecode.getBytecodeIndex(frame));
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyStoreBytecodeIndexInFrame() {
        LegacyFrameBytecodeIndexRootNode root = LegacyFrameBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitProbe();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertMode(root, bci -> new ProbeResult(bci, bci, bci, -1, bci, true, -1, -1, -1));
    }

    @Test
    public void testOperationProxyBytecodeIndex() {
        assertEquals(-1, BytecodeIndexLookupTestFactory.BytecodeIndexProxyNodeGen.create().execute());
        assertEquals(-1, BytecodeIndexLookupTestFactory.BytecodeIndexProxyNodeGen.getUncached().execute());

        BytecodeIndexProxyRootNode root = BytecodeIndexProxyRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitBytecodeIndexProxy();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        int expectedBytecodeIndex = findInstructionBytecodeIndex(root, "c.BytecodeIndexProxy");
        assertEquals(expectedBytecodeIndex, root.getCallTarget().call());
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(expectedBytecodeIndex, root.getCallTarget().call());
    }

    @Test
    public void testSetBytecodeIndexFromDeepNodes() {
        RootNode root = createStoreHelperRoot(false);
        assertEquals(true, root.getCallTarget().call());
        ((BytecodeRootNode) root).getBytecodeNode().setUncachedThreshold(0);
        assertEquals(true, root.getCallTarget().call());
    }

    @Test
    public void testSetBytecodeIndexFromUncachedNullLocation() {
        RootNode root = createStoreHelperRoot(true);
        assertEquals(true, root.getCallTarget().call());
    }

    @Test
    public void testSetBytecodeIndexFromCachedNullLocation() {
        RootNode root = createStoreHelperRoot(true);
        ((BytecodeRootNode) root).getBytecodeNode().setUncachedThreshold(0);
        EncapsulatingNodeReference reference = EncapsulatingNodeReference.getCurrent();
        Node previous = reference.set(null);
        try {
            NullPointerException error = assertThrows(NullPointerException.class, () -> root.getCallTarget().call());
            assertNotNull(error.getMessage());
        } finally {
            reference.set(previous);
        }
    }

    @Test
    public void testAdoptableLocationDoesNotUseEncapsulatingNode() {
        RootNode root = createStoreHelperRoot(false);
        root.getCallTarget();
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getFrameDescriptor());
        EncapsulatingNodeReference reference = EncapsulatingNodeReference.getCurrent();
        Node previous = reference.set(((BytecodeRootNode) root).getBytecodeNode());
        try {
            Node unrelated = new Node() {
            };
            assertThrows(NullPointerException.class, () -> BytecodeNode.setBytecodeIndex(unrelated, frame, 0));
        } finally {
            reference.set(previous);
        }
    }

    public static RootNode createStoreHelperRoot(boolean nullLocation) {
        return ManualBytecodeIndexRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            if (nullLocation) {
                b.emitStoreFromNullLocation();
            } else {
                b.emitStoreFromDeepNode();
            }
            b.endReturn();
            b.endRoot();
        }).getNode(0);
    }

    static final class StoreNode extends Node {
        private static final StoreNode UNCACHED = new StoreNode(0, false);
        private final boolean adoptable;
        @Child private StoreNode child;

        private StoreNode(int depth, boolean adoptable) {
            this.adoptable = adoptable;
            this.child = depth == 0 ? null : new StoreNode(depth - 1, true);
        }

        static StoreNode create() {
            return new StoreNode(3, true);
        }

        static StoreNode getUncached() {
            return UNCACHED;
        }

        @Override
        public boolean isAdoptable() {
            return adoptable;
        }

        void store(Frame frame, int bytecodeIndex) {
            if (child != null) {
                child.store(frame, bytecodeIndex);
            } else {
                BytecodeNode.setBytecodeIndex(this, frame, bytecodeIndex);
            }
        }
    }

    private static void assertMode(IndexRootNode root, IntFunction<ProbeResult> expectedResult) {
        assertMode(root, "c.Probe", expectedResult);
    }

    private static void assertMode(IndexRootNode root, String instructionName, IntFunction<ProbeResult> expectedResult) {
        ProbeResult expected = expectedResult.apply(findInstructionBytecodeIndex(root, instructionName));
        assertEquals(expected, root.getCallTarget().call());
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(expected, root.getCallTarget().call());
    }

    private static int findInstructionBytecodeIndex(BytecodeRootNode root, String name) {
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().equals(name)) {
                return instruction.getBytecodeIndex();
            }
        }
        throw new AssertionError("Instruction not found: " + name);
    }

    private static ProbeResult probe(IndexRootNode root, VirtualFrame frame, Node node, BytecodeNode bytecode, int bytecodeIndex, BytecodeIndexMode mode) {
        int locationBefore = bytecode.getBytecodeLocation(frame, node) == null ? -1 : bytecode.getBytecodeLocation(frame, node).getBytecodeIndex();
        int rootBefore = root.findIndex(node, frame);
        int rootNullBefore = root.findIndex(null, frame);
        int getBefore;
        try {
            getBefore = bytecode.getBytecodeIndex(frame);
        } catch (UnsupportedOperationException e) {
            getBefore = UNSUPPORTED;
        }

        boolean setSupported;
        try {
            BytecodeNode.setBytecodeIndex(node, frame, mode == BytecodeIndexMode.MANUAL ? bytecodeIndex : -1);
            setSupported = true;
        } catch (UnsupportedOperationException e) {
            setSupported = false;
        }

        int getAfter;
        try {
            getAfter = bytecode.getBytecodeIndex(frame);
        } catch (UnsupportedOperationException e) {
            getAfter = UNSUPPORTED;
        }
        return new ProbeResult(bytecodeIndex, locationBefore, rootBefore, rootNullBefore, getBefore, setSupported, getAfter, root.findIndex(node, frame), root.findIndex(null, frame));
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true)
    abstract static class BytecodeIndexProxyNode extends Node {

        abstract int execute();

        @Specialization
        static int doDefault(@Bind("$bytecodeIndex") int bytecodeIndex) {
            return bytecodeIndex;
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true)
    @OperationProxy(BytecodeIndexProxyNode.class)
    abstract static class BytecodeIndexProxyRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BytecodeIndexProxyRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    abstract static class IndexRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected IndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        final boolean isCaptureFrames(boolean compiled) {
            return isCaptureFramesForTrace(compiled);
        }

        final int findIndex(Node node, Frame frame) {
            return findBytecodeIndex(node, frame);
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.FRAME)
    abstract static class FrameBytecodeIndexRootNode extends IndexRootNode {

        protected FrameBytecodeIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        public static final class Probe {
            @Specialization
            @StoreBytecodeIndex
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind FrameBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.FRAME);
            }
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.CACHED_NODE)
    abstract static class CachedNodeBytecodeIndexRootNode extends IndexRootNode {

        protected CachedNodeBytecodeIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        public static final class Probe {
            @Specialization
            @StoreBytecodeIndex
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind CachedNodeBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.CACHED_NODE);
            }
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.MANUAL)
    abstract static class ManualBytecodeIndexRootNode extends IndexRootNode {

        protected ManualBytecodeIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Probe {
            @Specialization
            @StoreBytecodeIndex
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind ManualBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.MANUAL);
            }
        }

        @Operation
        public static final class StoreFromDeepNode {
            @Specialization
            public static boolean doDefault(VirtualFrame frame,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bytecodeIndex,
                            @Cached(value = "create()", uncached = "getUncached()", neverDefault = true) StoreNode node) {
                node.store(frame, bytecodeIndex);
                int stored = bytecode.getBytecodeIndex(frame);
                node.store(frame, -1);
                return stored == bytecodeIndex && bytecode.getBytecodeIndex(frame) == -1;
            }
        }

        @Operation
        public static final class StoreFromNullLocation {
            @Specialization
            public static boolean doDefault(VirtualFrame frame,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                BytecodeNode.setBytecodeIndex(null, frame, bytecodeIndex);
                int stored = bytecode.getBytecodeIndex(frame);
                BytecodeNode.setBytecodeIndex(null, frame, -1);
                return stored == bytecodeIndex && bytecode.getBytecodeIndex(frame) == -1;
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class ClearBytecodeIndex {
            @Specialization
            static void doDefault(VirtualFrame frame, @Bind BytecodeNode bytecode) {
                bytecode.setBytecodeIndex(frame, -1);
            }
        }

        @Operation
        public static final class ProbeWithoutStore {
            @Specialization
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind ManualBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.MANUAL);
            }
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.NONE)
    abstract static class NoBytecodeIndexRootNode extends IndexRootNode {

        protected NoBytecodeIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        public static final class Probe {
            @Specialization
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind NoBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.NONE);
            }
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, storeBytecodeIndexInFrame = true, bytecodeIndexMode = BytecodeIndexMode.CACHED_NODE)
    abstract static class LegacyFrameBytecodeIndexRootNode extends IndexRootNode {

        protected LegacyFrameBytecodeIndexRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        public static final class Probe {
            @Specialization
            @StoreBytecodeIndex
            public static ProbeResult doDefault(VirtualFrame frame,
                            @Bind Node node,
                            @Bind BytecodeNode bytecode,
                            @Bind LegacyFrameBytecodeIndexRootNode root,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return probe(root, frame, node, bytecode, bytecodeIndex, BytecodeIndexMode.FRAME);
            }
        }
    }

    @ExpectError("GenerateBytecode#storeBytecodeIndexInFrame cannot be true when GenerateBytecode#bytecodeIndexMode is explicitly set to a non-default mode.")
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, storeBytecodeIndexInFrame = true, bytecodeIndexMode = BytecodeIndexMode.MANUAL)
    abstract static class ConflictingBytecodeIndexModeRootNode extends IndexRootNode {

        protected ConflictingBytecodeIndexModeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }
}
