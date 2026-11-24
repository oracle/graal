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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeIndexMode;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Yield;
import com.oracle.truffle.api.bytecode.test.ManualBytecodeIndexTransitionTest.ManualTransitionRootNode.Marker;
import com.oracle.truffle.api.bytecode.test.ManualBytecodeIndexTransitionTest.ManualTransitionRootNode.SecondMarker;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class ManualBytecodeIndexTransitionTest {

    @Test
    public void testTierTransitionPreservesStoredIndex() {
        ManualTransitionRootNode root = createTierTransitionRoot();
        int storedBci = findStoreIndex(root);
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(storedBci, root.getCallTarget().call(false));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
    }

    @Test
    public void testTierTransitionPreservesClearedIndex() {
        ManualTransitionRootNode root = createTierTransitionRoot();
        assertEquals(-1, root.getCallTarget().call(true));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
    }

    private static ManualTransitionRootNode createTierTransitionRoot() {
        ManualTransitionRootNode root = ManualTransitionRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginStoreIndex();
            b.emitLoadArgument(0);
            b.endStoreIndex();
            b.emitForceCached();
            b.beginReturn();
            b.emitReadIndex();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);
        return root;
    }

    @Test
    public void testBytecodeUpdateTranslatesStoredIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            ManualTransitionRootNode root = createBytecodeUpdateRoot(cached, true);
            int oldStoredBci = findStoreIndex(root);
            Object result = root.getCallTarget().call(false);
            int newStoredBci = findStoreIndex(root);
            assertNotEquals(oldStoredBci, newStoredBci);
            assertEquals(newStoredBci, result);
        }
    }

    @Test
    public void testBytecodeUpdatePreservesClearedIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            ManualTransitionRootNode root = createBytecodeUpdateRoot(cached, true);
            BytecodeNode oldBytecode = root.getBytecodeNode();
            assertEquals(-1, root.getCallTarget().call(true));
            assertNotSame(oldBytecode, root.getBytecodeNode());
        }
    }

    @Test
    public void testBytecodeUpdateWithoutStoredIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            ManualTransitionRootNode root = createBytecodeUpdateRoot(cached, false);
            BytecodeNode oldBytecode = root.getBytecodeNode();
            assertEquals(42, root.getCallTarget().call());
            assertNotSame(oldBytecode, root.getBytecodeNode());
        }
    }

    private static ManualTransitionRootNode createBytecodeUpdateRoot(boolean cached, boolean storeIndex) {
        ManualTransitionRootNode root = ManualTransitionRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.emitMarker();
            if (storeIndex) {
                b.beginStoreIndex();
                b.emitLoadArgument(0);
                b.endStoreIndex();
            }
            b.emitUpdateBytecode();
            b.beginReturn();
            if (storeIndex) {
                b.emitReadIndex();
            } else {
                b.emitLoadConstant(42);
            }
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(cached ? 0 : Integer.MAX_VALUE);
        return root;
    }

    @Test
    public void testContinuationResumeOverwritesStoredIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            for (boolean customYield : new boolean[]{false, true}) {
                for (boolean update : new boolean[]{false, true}) {
                    ManualTransitionRootNode root = createSuspendingRoot(cached, customYield, true);
                    int storedBci = findStoreIndex(root);
                    ContinuationResult suspended = (ContinuationResult) root.getCallTarget().call(false);
                    if (update) {
                        root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
                    }
                    int resumeBci = suspended.getBytecodeLocation().getBytecodeIndex();
                    assertNotEquals(storedBci, resumeBci);
                    assertEquals(resumeBci, suspended.continueWith(null));
                }
            }
        }
    }

    @Test
    public void testContinuationResumeOverwritesClearedIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            for (boolean customYield : new boolean[]{false, true}) {
                for (boolean update : new boolean[]{false, true}) {
                    ManualTransitionRootNode root = createSuspendingRoot(cached, customYield, true);
                    ContinuationResult suspended = (ContinuationResult) root.getCallTarget().call(true);
                    if (update) {
                        root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
                    }
                    int resumeBci = suspended.getBytecodeLocation().getBytecodeIndex();
                    assertEquals(resumeBci, suspended.continueWith(null));
                }
            }
        }
    }

    @Test
    public void testContinuationResumeInitializesUnsetIndex() {
        for (boolean cached : new boolean[]{false, true}) {
            for (boolean customYield : new boolean[]{false, true}) {
                for (boolean update : new boolean[]{false, true}) {
                    ManualTransitionRootNode root = createSuspendingRoot(cached, customYield, false);
                    ContinuationResult suspended = (ContinuationResult) root.getCallTarget().call();
                    if (update) {
                        root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
                    }
                    int resumeBci = suspended.getBytecodeLocation().getBytecodeIndex();
                    assertEquals(resumeBci, suspended.continueWith(null));
                }
            }
        }
    }

    @Test
    public void testContinuationDoesNotReserveAdditionalFrameSlot() {
        for (boolean customYield : new boolean[]{false, true}) {
            ManualTransitionRootNode root = createSuspendingRoot(true, customYield, true);
            // One BCI slot, one continuation-frame slot, and one operand-stack slot.
            assertEquals(3, root.getFrameDescriptor().getNumberOfSlots());
        }
    }

    @Test
    public void testSharedContinuationRootWithDifferentFrameVersions() {
        for (boolean cached : new boolean[]{false, true}) {
            for (boolean customYield : new boolean[]{false, true}) {
                ManualTransitionRootNode root = createSuspendingRoot(cached, customYield, true);
                int firstBci = findStoreIndex(root);
                ContinuationResult first = (ContinuationResult) root.getCallTarget().call(false);
                root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
                int secondBci = findStoreIndex(root);
                ContinuationResult second = (ContinuationResult) root.getCallTarget().call(false);
                assertSame(first.getContinuationRootNode(), second.getContinuationRootNode());
                root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).addInstrumentation(SecondMarker.class).build());
                int newStoredBci = findStoreIndex(root);
                assertNotEquals(firstBci, secondBci);
                assertNotEquals(secondBci, newStoredBci);
                int resumeBci = first.getBytecodeLocation().getBytecodeIndex();
                // Exercise the public two-argument calling convention directly as well.
                assertEquals(resumeBci, second.getContinuationCallTarget().call(second.getFrame(), null));
                assertEquals(resumeBci, first.continueWith(null));
            }
        }
    }

    @Test
    public void testContinuationYieldsAgainAfterBytecodeUpdate() {
        for (boolean cached : new boolean[]{false, true}) {
            ManualTransitionRootNode root = ManualTransitionRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.emitMarker();
                b.emitSecondMarker();
                b.beginStoreIndex();
                b.emitLoadArgument(0);
                b.endStoreIndex();
                b.beginYield();
                b.emitLoadConstant(42);
                b.endYield();
                b.beginCustomSuspend();
                b.emitLoadConstant(42);
                b.endCustomSuspend();
                b.beginReturn();
                b.emitReadIndex();
                b.endReturn();
                b.endRoot();
            }).getNode(0);
            root.getBytecodeNode().setUncachedThreshold(cached ? 0 : Integer.MAX_VALUE);
            ContinuationResult first = (ContinuationResult) root.getCallTarget().call(false);
            root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
            ContinuationResult second = (ContinuationResult) first.continueWith(null);
            root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).addInstrumentation(SecondMarker.class).build());
            int resumeBci = second.getBytecodeLocation().getBytecodeIndex();
            assertEquals(resumeBci, second.continueWith(null));
        }
    }

    public static ManualTransitionRootNode createSuspendingRoot(boolean cached, boolean customYield, boolean storeIndex) {
        ManualTransitionRootNode root = ManualTransitionRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.emitMarker();
            b.emitSecondMarker();
            if (storeIndex) {
                b.beginStoreIndex();
                b.emitLoadArgument(0);
                b.endStoreIndex();
            }
            if (customYield) {
                b.beginCustomSuspend();
            } else {
                b.beginYield();
            }
            b.emitLoadConstant(42);
            if (customYield) {
                b.endCustomSuspend();
            } else {
                b.endYield();
            }
            b.beginReturn();
            b.emitReadIndex();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(cached ? 0 : Integer.MAX_VALUE);
        return root;
    }

    private static int findStoreIndex(BytecodeRootNode root) {
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().equals("c.StoreIndex")) {
                return instruction.getBytecodeIndex();
            }
        }
        throw new AssertionError("StoreIndex instruction not found");
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.MANUAL)
    public abstract static class ManualTransitionRootNode extends RootNode implements BytecodeRootNode {

        protected ManualTransitionRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        static final class StoreIndex {
            @Specialization
            static void doDefault(VirtualFrame frame, boolean clear, @Bind BytecodeNode bytecode, @Bind("$bytecodeIndex") int bci) {
                bytecode.setBytecodeIndex(frame, clear ? -1 : bci);
            }
        }

        @Operation(forceCached = true, storeBytecodeIndex = false)
        @SuppressWarnings("truffle-force-cached")
        static final class ForceCached {
            @Specialization
            static void doDefault() {
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class ReadIndex {
            @Specialization
            static int doDefault(VirtualFrame frame, @Bind BytecodeNode bytecode) {
                return bytecode.getBytecodeIndex(frame);
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class UpdateBytecode {
            @Specialization
            @TruffleBoundary
            static void doDefault(@Bind ManualTransitionRootNode root) {
                root.getRootNodes().update(ManualTransitionRootNodeGen.newConfigBuilder().addInstrumentation(Marker.class).build());
            }
        }

        @Yield
        static final class CustomSuspend {
            @Specialization
            static ContinuationResult doDefault(VirtualFrame frame, Object value, @Bind ContinuationRootNode continuation) {
                return ContinuationResult.create(continuation, frame.materialize(), value);
            }
        }

        @Instrumentation(storeBytecodeIndex = false)
        static final class SecondMarker {
            @Specialization
            static void doDefault() {
            }
        }

        @Instrumentation(storeBytecodeIndex = false)
        public static final class Marker {
            @Specialization
            static void doDefault() {
            }
        }
    }
}
