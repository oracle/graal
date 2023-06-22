/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class FrameDescriptorTest extends PartialEvaluationTest {

    @Test
    public void constantInfo() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        Object obj = new Object();
        FrameDescriptor fd = builder.info("foo").info(obj).build();
        RootNode root1 = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "constantInfo1";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(frame.getFrameDescriptor().getInfo());
                return frame.getFrameDescriptor().getInfo();
            }
        };
        RootNode root2 = new RootNode(null, fd) {

            private final FrameDescriptor descriptor = fd;

            @Override
            public String toString() {
                return "constantInfo2";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(descriptor.getInfo());
                return descriptor.getInfo();
            }
        };
        MaterializedFrame materializedFrame = Truffle.getRuntime().createMaterializedFrame(new Object[]{}, fd);
        RootNode root3 = new RootNode(null) {

            private final Frame fr = materializedFrame;

            @Override
            public String toString() {
                return "constantInfo3";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(fr.getFrameDescriptor().getInfo());
                return fr.getFrameDescriptor().getInfo();
            }
        };
        testConstantReturn(root1, obj);
        testConstantReturn(root2, obj);
        testConstantReturn(root3, obj);
    }

    private void testConstantReturn(RootNode rootNode, Object result) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        Assert.assertTrue(callTarget.call() == result);

        StructuredGraph graph = partialEval(callTarget, new Object[]{});
        assertTrue(graph.getNodes().filter(ReturnNode.class).first().result() instanceof ConstantNode);
        compile(callTarget, graph);
        Assert.assertTrue("CallTarget is valid", callTarget.isValid());
        Assert.assertTrue(callTarget.call() == result);
    }

    @SuppressWarnings("deprecation")
    private static void assertEmptyFrameDescriptor(FrameDescriptor fd, Object info) {
        Assert.assertEquals(0, fd.getNumberOfSlots());
        Assert.assertEquals(0, fd.getNumberOfAuxiliarySlots());
        Assert.assertEquals(null, fd.getDefaultValue());
        Assert.assertTrue(info == fd.getInfo());
    }

    @Test
    public void testEmpty() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        FrameDescriptor fd = builder.build();
        assertEmptyFrameDescriptor(fd, null);
        assertEmptyFrameDescriptor(new FrameDescriptor(), null);
        assertEmptyFrameDescriptor(new FrameDescriptor(null), null);
    }

    @Test
    public void testInfo() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        Object obj = new Object();
        FrameDescriptor fd = builder.info("foo").info(obj).build();
        assertEmptyFrameDescriptor(fd, obj);
    }
}
