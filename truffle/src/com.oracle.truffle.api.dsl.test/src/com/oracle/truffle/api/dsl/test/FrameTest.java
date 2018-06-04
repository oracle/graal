/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.FrameTestFactory.FrameDescriptorAccess1NodeGen;
import com.oracle.truffle.api.dsl.test.FrameTestFactory.FrameDescriptorAccess2NodeGen;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class FrameTest {

    @Test
    public void testFrameDescriptorAccess1() {
        FrameDescriptorAccess1Node node = FrameDescriptorAccess1NodeGen.create();
        FrameDescriptor descriptor = new FrameDescriptor();
        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(null, descriptor);
        Assert.assertSame(frame.getFrameDescriptor(), node.execute(frame, 2));
        Assert.assertSame(frame.getFrameDescriptor(), node.execute(frame, 3));
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class FrameDescriptorAccess1Node extends Node {

        public abstract Object execute(VirtualFrame frame, Object arg);

        @SuppressWarnings("unused")
        @Specialization
        FrameDescriptor s1(VirtualFrame frame, int value,
                        @Cached("frame.getFrameDescriptor()") FrameDescriptor descriptor) {
            return descriptor;
        }
    }

    @Test
    public void testFrameDescriptorAccess2() {
        FrameDescriptorAccess2Node node = FrameDescriptorAccess2NodeGen.create();
        FrameDescriptor descriptor = new FrameDescriptor();
        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(null, descriptor);
        Assert.assertSame(frame.getFrameDescriptor(), node.execute(frame, 2));
        Assert.assertSame(frame.getFrameDescriptor(), node.execute(frame, 3));
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class FrameDescriptorAccess2Node extends Node {

        public abstract Object execute(Frame frame, Object arg);

        @SuppressWarnings("unused")
        @Specialization(guards = "cachedValue == value", limit = "3")
        FrameDescriptor s1(VirtualFrame frame, int value,
                        @Cached("frame.getFrameDescriptor()") FrameDescriptor descriptor,
                        @Cached("value") int cachedValue) {
            return descriptor;
        }
    }

}
