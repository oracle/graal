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
import java.util.List;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.test.SubprocessTestUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;

/**
 * Tests that we can move frame array reads out of the loop even if there is a side-effect in the
 * loop. Tests with a default compiler configuration so this is
 *
 * This test is likely to fail with changes to {@link FrameWithoutBoxing}. Update this test
 * accordingly.
 */
public class FrameHostReadsTest extends TruffleCompilerImplTest {

    public static int snippet0(FrameWithoutBoxing frame, int index) {
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
    public void test0() throws IOException, InterruptedException {
        // Run in subprocess to disable assertions in FrameWithoutBoxing.
        // Assertion checking requires additional checks in the frame descriptor for handling of
        // static slots.
        SubprocessTestUtils.executeInSubprocessWithAssertionsDisabled(FrameHostReadsTest.class, () -> {
            compileAndCheck();
        },
                        true,
                        List.of(FrameWithoutBoxing.class));
    }

    private void compileAndCheck() {
        getTruffleCompiler();
        initAssertionError();
        Assert.assertSame("New frame implementation detected. Make sure to update this test.", FrameWithoutBoxing.class,
                        Truffle.getRuntime().createVirtualFrame(new Object[0], FrameDescriptor.newBuilder().build()).getClass());
        Assert.assertTrue("Frame assertions should be disabled.", !FrameAssertionsChecker.areFrameAssertionsEnabled());

        StructuredGraph graph = getFinalGraph("snippet0");

        int fieldReads = 0;
        int arrayReads = 0;
        int arrayLengthReads = 0;
        int otherReads = 0;

        for (ReadNode read : graph.getNodes(ReadNode.TYPE)) {
            LocationIdentity identity = read.getLocationIdentity();
            if (identity instanceof FieldLocationIdentity) {
                fieldReads++;
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
         * Array reads inside of the loop. We expect the loop to get unrolled, so we expect 5 times
         * the number of reads. It is important that the loop does not contain reads to
         * FrameWithoutBoxing.indexedTags or FrameWithoutBoxing.indexedPrimitiveLocals.
         */
        Assert.assertEquals(5, arrayReads);

        /*
         * Array.length reads. We read one for FrameWithoutBoxing.indexedPrimitiveLocals.
         */
        Assert.assertEquals(1, arrayLengthReads);
        Assert.assertEquals(0, otherReads);
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
