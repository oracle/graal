/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

@SuppressWarnings("truffle")
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
