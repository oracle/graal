/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.BytecodeOSRMetadata;
import com.oracle.truffle.runtime.OptimizedCallTarget;

@SuppressWarnings("deprecation")
public class LoopNodeOSRTest extends TestWithSynchronousCompiling {

    static final Object[] ARGUMENTS = IntStream.range(21, 36).mapToObj(Integer::valueOf).toArray();

    private static class TestLoopRootNode extends RootNode {
        @Child private LoopNode loop;
        private final int iterationSlot;

        TestLoopRootNode(RepeatingNode body, FrameDescriptor frameDescriptor, int iterationSlot) {
            super(null, frameDescriptor);
            this.loop = Truffle.getRuntime().createLoopNode(body);
            this.iterationSlot = iterationSlot;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(iterationSlot, 0);
            return loop.execute(frame);
        }
    }

    private final class CheckStackWalkBody extends Node implements RepeatingNode {
        private final int total;
        private final int iterationSlot;
        private final FrameDescriptor frameDescriptor;
        boolean compiled;

        private CheckStackWalkBody(int total, FrameDescriptor frameDescriptor, int iterationSlot) {
            this.total = total;
            this.iterationSlot = iterationSlot;
            this.frameDescriptor = frameDescriptor;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            int iteration = frame.getInt(iterationSlot);
            if (iteration < total) {
                if (iteration % (total / 10) == 0) {
                    checkStack();
                }
                iteration++;
                frame.setInt(iterationSlot, iteration);
                return true;
            } else {
                if (CompilerDirectives.inCompiledCode()) {
                    compiled = true;
                }
                iteration = 0;
                frame.setInt(iterationSlot, iteration);
                return false;
            }
        }

        @TruffleBoundary
        private void checkStack() {
            Truffle.getRuntime().iterateFrames(frameInstance -> {
                Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                Assert.assertArrayEquals(ARGUMENTS, frame.getArguments());
                Assert.assertSame(frameDescriptor, frame.getFrameDescriptor());
                return null;
            });
        }
    }

    @Test
    public void testOSRStackFrame() {
        int osrThreshold = 10 * BytecodeOSRMetadata.OSR_POLL_INTERVAL;
        setupContext("engine.MultiTier", "false",
                        "engine.OSR", "true",
                        "engine.OSRCompilationThreshold", String.valueOf(osrThreshold));

        var builder = FrameDescriptor.newBuilder();
        int iterationSlot = builder.addSlot(FrameSlotKind.Int, "iteration", null);
        FrameDescriptor frameDescriptor = builder.build();
        CheckStackWalkBody loop = new CheckStackWalkBody(osrThreshold * 2, frameDescriptor, iterationSlot);
        TestLoopRootNode rootNode = new TestLoopRootNode(loop, frameDescriptor, iterationSlot);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();

        target.call(ARGUMENTS);

        Assert.assertTrue("Loop should have been OSR compiled", loop.compiled);
    }

}
