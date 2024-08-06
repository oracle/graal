/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * <h3>Storing Values in Frame Slots</h3>
 *
 * <p>
 * The frame is the preferred data structure for passing values between nodes. It can in particular
 * be used for storing the values of local variables of the guest language. The
 * {@link FrameDescriptor} represents the current structure of the frame. The method
 * {@link FrameDescriptor#addFrameSlot(Object, FrameSlotKind)} can be used to create predefined
 * frame slots. The setter and getter methods in the {@link Frame} class can be used to access the
 * current value of a particular frame slot. Values can be removed from a frame via the
 * {@link FrameDescriptor#removeFrameSlot(Object)} method.
 * </p>
 *
 * <p>
 * There are five primitive types for slots available: {@link java.lang.Boolean},
 * {@link java.lang.Integer}, {@link java.lang.Long}, {@link java.lang.Float}, and
 * {@link java.lang.Double} . It is encouraged to use those types whenever possible. Dynamically
 * typed languages can speculate on the type of a value fitting into a primitive (see
 * {@link FrameSlotTypeSpecializationTest}). When a frame slot is of one of those particular
 * primitive types, its value may only be accessed with the respectively typed getter method (
 * {@link Frame#getBoolean}, {@link Frame#getInt}, {@link Frame#getLong}, {@link Frame#getFloat}, or
 * {@link Frame#getDouble}) or setter method ({@link Frame#setBoolean}, {@link Frame#setInt},
 * {@link Frame#setLong}, {@link Frame#setFloat}, or {@link Frame#setDouble}) in the {@link Frame}
 * class.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.FrameSlotTypeSpecializationTest}.
 * </p>
 */
public class FrameTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void framesCanBeMaterialized() {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        final TruffleRuntime runtime = Truffle.getRuntime();

        class FrameRootNode extends RootNode {

            FrameRootNode() {
                super(null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Frame result = runtime.iterateFrames(f -> {
                    Frame readWrite = f.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                    Frame materialized = f.getFrame(FrameInstance.FrameAccess.MATERIALIZE);

                    assertTrue("Really materialized: " + materialized, materialized instanceof MaterializedFrame);
                    assertEquals("It's my frame", frame, readWrite);
                    return materialized;
                });
                // at least one frame available
                assertNotNullBoundary(result.materialize());

                return this;
            }

            @TruffleBoundary
            private static void assertNotNullBoundary(MaterializedFrame frame) {
                assertNotNull(frame);
            }
        }

        FrameRootNode frn = new FrameRootNode();
        Object ret = frn.getCallTarget().call();
        assertEquals("Returns itself", frn, ret);
    }
}
