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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTransition;
import com.oracle.truffle.api.bytecode.BytecodeVersion;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Tests that {@link BytecodeRootNode#traceTransition(BytecodeTransition, Frame)} is invoked
 * correctly for transition callbacks, including transfer-to-interpreter (tested via
 * {@link BytecodeDSLCompilationTest}).
 */
public class TransitionInterceptionTest {

    static TransitionTracingInterpreter parse(BytecodeParser<TransitionTracingInterpreterGen.Builder> builder) {
        BytecodeRootNodes<TransitionTracingInterpreter> nodes = TransitionTracingInterpreterGen.create(null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    /**
     * When an uncached interpreter transitions to cached, only the tier changes. The bytecode array
     * identity stays the same, so {@code isBytecodeUpdate()} must be {@code false}.
     */
    @Test
    public void testBytecodeUpdateTransition() {
        TransitionTracingInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // Force immediate transition to cached on the first call.
        root.getBytecodeNode().setUncachedThreshold(0);

        // Execute once; uncached-to-cached transition fires.
        assertEquals(42L, root.getCallTarget().call());

        assertEquals(1, root.transitions.size());
        BytecodeTransition transition = root.transitions.get(0);
        assertFalse("Expected !isBytecodeUpdate when bytecode array identity is unchanged", transition.isBytecodeUpdate());
        assertFalse("Expected !isTransferToInterpreter (not compiled)", transition.isTransferToInterpreter());
        assertNotNull(transition.getOldLocation());
        assertNotNull(transition.getNewLocation());
        assertTrue(transition.getOldLocation().getBytecodeNode().getTier() != transition.getNewLocation().getBytecodeNode().getTier());
    }

    /**
     * For simple interpreters, {@link BytecodeNode#ensureSourceInformation()} performs a
     * metadata-only update: the source info arrays are updated but the bytecodes array is unchanged
     * ({@code bytecodes_ == null}). Because the old bytecodes are never patched with INVALIDATE
     * instructions, the execute loop cannot detect the change mid-execution, so no additional
     * transition fires.
     * <p>
     * This test verifies that calling {@link BytecodeNode#ensureSourceInformation()} from within an
     * executing cached bytecode works correctly: only the uncached->cached tier-only transition
     * fires, and the interpreter correctly reports source information afterwards.
     */
    @Test
    public void testSourceInformationMaterializationDoesNotCreateAdditionalTransition() {
        TransitionTracingInterpreter root = parse(b -> {
            b.beginRoot();
            // Trigger source info materialization during cached execution. For simple interpreters
            // this is a metadata-only update (bytecodes_ == null), so no INVALIDATE is patched and
            // no mid-execution transition fires.
            b.emitEnsureSourceInformation();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // Force immediate uncached->cached transition on the first call.
        root.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(42L, root.getCallTarget().call());

        // Only the uncached->cached tier-only transition fires in this interpreter configuration.
        assertEquals(1, root.transitions.size());
        BytecodeTransition transition = root.transitions.get(0);
        assertFalse("Expected !isBytecodeUpdate when bytecode array identity is unchanged", transition.isBytecodeUpdate());
        assertFalse("Expected !isTransferToInterpreter (not compiled)", transition.isTransferToInterpreter());

        // The bytecode node should now report source information (metadata was updated).
        assertTrue("Expected source information after ensureSourceInformation()", root.getBytecodeNode().hasSourceInformation());
    }

    /**
     * The frame passed to {@code traceTransition} should be non-null.
     */
    @Test
    public void testTransitionReceivesFrame() {
        TransitionTracingInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // Force immediate transition to cached on the first call.
        root.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(42L, root.getCallTarget().call());
        assertFalse(root.transitions.isEmpty());
        assertTrue("Frame should be non-null in traceTransition", root.frameWasNonNull);
    }

    /**
     * After a bytecode update, both the old and new locations should resolve correctly.
     */
    @Test
    public void testTransitionLocations() {
        TransitionTracingInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // Force immediate transition to cached on the first call.
        root.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(42L, root.getCallTarget().call());
        assertEquals(1, root.transitions.size());

        BytecodeTransition t = root.transitions.get(0);
        BytecodeLocation oldLoc = t.getOldLocation();
        BytecodeLocation newLoc = t.getNewLocation();
        assertNotNull(oldLoc);
        assertNotNull(newLoc);
        // Both locations refer to the entry bci (0).
        assertEquals(0, oldLoc.getBytecodeIndex());
        assertEquals(0, newLoc.getBytecodeIndex());
    }
}

@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true)
abstract class TransitionTracingInterpreter extends RootNode implements BytecodeRootNode {

    final List<BytecodeTransition> transitions = new ArrayList<>();
    boolean frameWasNonNull = false;

    protected TransitionTracingInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Override
    public void traceTransition(BytecodeTransition transition, Frame frame) {
        transitions.add(transition);
        frameWasNonNull |= frame != null;
    }

    /**
     * Triggers source materialization from within an executing bytecode.
     */
    @Operation
    static final class EnsureSourceInformation {
        @Specialization
        public static void doDefault(@Bind BytecodeNode node) {
            node.ensureSourceInformation();
        }
    }
}
