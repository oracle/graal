/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class MaterializeVirtualFramesTest extends TestWithSynchronousCompiling {

    private static int blackHole = 100;
    private static TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope maxGraalNodeCountScope;
    private static TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope performanceWarningsAreFatalScope;

    @BeforeClass
    public static void setUp() {
        Assume.assumeFalse(TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompileImmediately));
        performanceWarningsAreFatalScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal, false);
        maxGraalNodeCountScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleMaximumGraalNodeCount, 40);
    }

    @AfterClass
    public static void tearDown() {
        maxGraalNodeCountScope.close();
        performanceWarningsAreFatalScope.close();
    }

    @Test
    public void test() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        final FrameSlot slot = frameDescriptor.addFrameSlot("test");
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        int[] execCount = {0};
        final RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootNode(null, frameDescriptor) {
            @Override
            public Object execute(VirtualFrame frame) {
                for (int i = 0; i < blackHole; i += 2) {
                    blackHole++;
                }
                return boundary(frame);
            }

            Object boundary(VirtualFrame frame) {
                CompilerAsserts.neverPartOfCompilation();
                frame.setInt(slot, (Integer) frame.getArguments()[0]);
                execCount[0]++;
                return frame.getArguments()[0];
            }
        });

        for (int i = 0; i < 2 * compilationThreshold; i++) {
            callTarget.call(0);
        }
        Assert.assertEquals(2 * compilationThreshold, execCount[0]);
    }
}
