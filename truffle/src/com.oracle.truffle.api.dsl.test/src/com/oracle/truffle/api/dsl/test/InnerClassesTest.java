/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.InnerClassesTest.AbstractEnclosingNode.MyNodeInNode;
import com.oracle.truffle.api.dsl.test.InnerClassesTest.AbstractEnclosingNodeWithFactory.MyNodeInFactory;
import com.oracle.truffle.api.dsl.test.InnerClassesTest.AbstractEnclosingNonNode.MyNode;
import com.oracle.truffle.api.dsl.test.InnerClassesTest.FinalEnclosingNonNode.MyNodeInFinal;
import com.oracle.truffle.api.dsl.test.InnerClassesTestFactory.AbstractEnclosingNodeGen.MyNodeInNodeFactory;
import com.oracle.truffle.api.dsl.test.InnerClassesTestFactory.AbstractEnclosingNodeWithFactoryFactory;
import com.oracle.truffle.api.dsl.test.InnerClassesTestFactory.AbstractEnclosingNodeWithFactoryFactory.MyNodeInFactoryFactory;
import com.oracle.truffle.api.dsl.test.InnerClassesTestFactory.AbstractEnclosingNonNodeFactory.MyNodeFactory;
import com.oracle.truffle.api.dsl.test.InnerClassesTestFactory.FinalEnclosingNonNodeFactory.MyNodeInFinalFactory;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("truffle-inlining")
public class InnerClassesTest {
    public abstract static class AbstractEnclosingNonNode {
        @GenerateNodeFactory
        public abstract static class MyNode extends Node {
            public abstract String execute(Object arg);

            @Specialization
            @TruffleBoundary
            static String doInt(int x) {
                return String.format("MyNode%d", x);
            }
        }
    }

    public abstract static class AbstractEnclosingNode extends Node {
        public abstract String execute(Object arg);

        @Specialization
        @TruffleBoundary
        static String doInt(int x) {
            return String.format("AbstractEnclosingNode%d", x);
        }

        @GenerateNodeFactory
        public abstract static class MyNodeInNode extends Node {
            public abstract String execute(Object arg);

            @Specialization
            @TruffleBoundary
            static String doInt(int x) {
                return String.format("MyNodeInNode%d", x);
            }
        }
    }

    @GenerateNodeFactory
    public abstract static class AbstractEnclosingNodeWithFactory extends Node {
        public abstract String execute(Object arg);

        @Specialization
        @TruffleBoundary
        static String doInt(int x) {
            return String.format("AbstractEnclosingNodeWithFactory%d", x);
        }

        @GenerateNodeFactory
        public abstract static class MyNodeInFactory extends Node {
            public abstract String execute(Object arg);

            @Specialization
            static String doInt(int x) {
                return String.format("MyNodeInFactory%d", x);
            }
        }
    }

    public static final class FinalEnclosingNonNode {
        @GenerateNodeFactory
        public abstract static class MyNodeInFinal extends Node {
            public abstract String execute(Object arg);

            @Specialization
            static String doInt(int x) {
                return String.format("MyNodeInFinal%d", x);
            }
        }
    }

    @Test
    public void sanityCheck() {
        Assert.assertEquals(5, InnerClassesTestFactory.getFactories().size());

        var nodeClasses = InnerClassesTestFactory.getFactories().stream().map(NodeFactory::getNodeClass).collect(Collectors.toSet());
        var expectedNodeClasses = List.of(MyNode.class, MyNodeInNode.class, MyNodeInFactory.class, AbstractEnclosingNodeWithFactory.class, MyNodeInFinal.class);
        Assert.assertTrue(nodeClasses.containsAll(expectedNodeClasses));

        Assert.assertEquals("MyNode1", MyNodeFactory.create().execute(1));
        Assert.assertEquals("MyNodeInNode2", MyNodeInNodeFactory.create().execute(2));
        Assert.assertEquals("MyNodeInFactory3", MyNodeInFactoryFactory.create().execute(3));
        Assert.assertEquals("AbstractEnclosingNodeWithFactory4", AbstractEnclosingNodeWithFactoryFactory.create().execute(4));
        Assert.assertEquals("MyNodeInFinal5", MyNodeInFinalFactory.create().execute(5));
    }
}
