/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.test.SubprocessTestUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;

/**
 * Tests that we can move frame array reads out of the loop even if there is a side-effect in the
 * loop. Tests with a default compiler configuration so this is
 *
 * This test is likely to fail with changes to {@link FrameWithoutBoxing}. Update this test
 * accordingly.
 */
public class FrameHostReadsTest extends TruffleCompilerImplTest {

    public static int snippetStaticReads(FrameWithoutBoxing frame, int index) {
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            GraalDirectives.sideEffect();
            /*
             * It is common in bytecode interpreter loops to access frame locals with an offset.
             */
            sum += frame.getIntStatic(index + i);
        }
        return sum;
    }

    @Test
    public void testStaticReads() throws IOException, InterruptedException {
        // Run in subprocess to disable assertions in FrameWithoutBoxing.
        // Assertion checking requires additional checks in the frame descriptor for handling of
        // static slots.
        SubprocessTestUtils.newBuilder(FrameHostReadsTest.class, () -> {
            compileAndCheck("snippetStaticReads", (graph) -> {

                int fieldReads = 0;
                int arrayReads = 0;
                int arrayLengthReads = 0;
                int otherReads = 0;

                for (ReadNode read : graph.getNodes(ReadNode.TYPE)) {
                    LocationIdentity identity = read.getLocationIdentity();
                    if (identity instanceof FieldLocationIdentity) {
                        fieldReads++;
                        if (fieldReads > 1) {
                            GraalError.guarantee(false, "%s", read.toString(Verbosity.All));
                        }
                    } else if (NamedLocationIdentity.isArrayLocation(identity)) {
                        arrayReads++;
                    } else if (identity == NamedLocationIdentity.ARRAY_LENGTH_LOCATION) {
                        arrayLengthReads++;
                    } else {
                        otherReads++;
                    }
                }

                /*
                 * Frame read for FrameWithoutBoxing.indexedPrimitiveLocals.
                 */
                Assert.assertEquals(1, fieldReads);

                /*
                 * Array reads inside of the loop. We expect the loop to get unrolled, so we expect
                 * 5 times the number of reads. It is important that the loop does not contain reads
                 * to FrameWithoutBoxing.indexedTags or FrameWithoutBoxing.indexedPrimitiveLocals.
                 */
                Assert.assertEquals(5, arrayReads);

                /*
                 * Array.length reads. We read one for FrameWithoutBoxing.indexedPrimitiveLocals.
                 */
                Assert.assertEquals(1, arrayLengthReads);
                Assert.assertEquals(0, otherReads);
            });
        }).disableAssertions(FrameWithoutBoxing.class).postfixVmOption("-Djdk.graal.TruffleTrustedFinalFrameFields=true").run();
    }

    public static int snippetReadsWritesWithoutZeroExtend(FrameWithoutBoxing frame, int index) {
        // this should optimize without zero extends and narros
        frame.setInt(index + 0, frame.getInt(index + 1));
        frame.setFloat(index + 2, frame.getFloat(index + 3));
        return 0;
    }

    @Test
    public void testReadsWritesWithoutZeroExtend() throws IOException, InterruptedException {
        // Run in subprocess to disable assertions in FrameWithoutBoxing.
        // Assertion checking requires additional checks in the frame descriptor for handling of
        // static slots.
        SubprocessTestUtils.newBuilder(FrameHostReadsTest.class, () -> {
            compileAndCheck("snippetReadsWritesWithoutZeroExtend", (graph) -> {
                int writeCount = 0;
                for (Node node : graph.getNodes()) {
                    if (node instanceof WriteNode write && write.getLocationIdentity().equals(NamedLocationIdentity.LONG_ARRAY_LOCATION)) {
                        /*
                         * No ZeroExtendNode or NarrowNode between read and write long.
                         */
                        assertTrue(write.value().toString(), write.value() instanceof ReadNode);
                        writeCount++;
                    }
                }
                Assert.assertEquals(2, writeCount);
            });
        }).disableAssertions(FrameWithoutBoxing.class).postfixVmOption("-Djdk.graal.TruffleTrustedFinalFrameFields=true").run();
    }

    static FrameWithoutBoxing escape;
    static final Object[] EMPTY_ARRAY = new Object[0];

    /**
     * Test that the loads should not be hoisted incorrectly.
     */
    public static Object snippetNoMoveReads(FrameDescriptor desc, Object obj) {
        FrameWithoutBoxing frame = new FrameWithoutBoxing(desc, EMPTY_ARRAY);
        frame.setObject(0, obj);
        VarHandle.fullFence();
        // Don't let the frame be scalar replaced
        escape = frame;
        VarHandle.fullFence();
        return frame.getObject(0);
    }

    @Test
    public void testNoMoveReads() throws IOException, InterruptedException {
        SubprocessTestUtils.newBuilder(FrameHostReadsTest.class, () -> {
            FrameDescriptor.Builder descBuilder = FrameDescriptor.newBuilder();
            descBuilder.addSlot(FrameSlotKind.Object, null, null);
            FrameDescriptor desc = descBuilder.build();
            Object obj = new Object();
            Result res = test("snippetNoMoveReads", desc, obj);
            Assert.assertSame(Objects.toString(res.returnValue), obj, res.returnValue);
        }).disableAssertions(FrameWithoutBoxing.class).postfixVmOption("-Djdk.graal.TruffleTrustedFinalFrameFields=true").run();
    }

    private void compileAndCheck(String snippet, Consumer<StructuredGraph> check) {
        getTruffleCompiler();
        initAssertionError();
        Assert.assertSame("New frame implementation detected. Make sure to update this test.", FrameWithoutBoxing.class,
                        Truffle.getRuntime().createVirtualFrame(new Object[0], FrameDescriptor.newBuilder().build()).getClass());
        Assert.assertTrue("Frame assertions should be disabled.", !FrameAssertionsChecker.areFrameAssertionsEnabled());
        StructuredGraph graph = getFinalGraph(snippet);
        check.accept(graph);
    }

    @SuppressWarnings({"serial"})
    private static AssertionError initAssertionError() {
        return new AssertionError() {
            @SuppressWarnings("sync-override")
            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        };
    }
}
