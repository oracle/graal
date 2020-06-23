/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.test.ObjectsRequireNonNullPartialEvaluationTest.TestClass.InnerClass;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Objects;

public class ObjectsRequireNonNullPartialEvaluationTest extends PartialEvaluationTest {

    @Test
    public void testRequireNonNull() {
        AbstractTestNode testNode = new RequireNonNullNode();
        testCommon(testNode, "testRequireNonNull", new TestClass(42));
    }

    @Test
    public void testInnerClassCtor() {
        AbstractTestNode testNode = new InnerClassNode();
        testCommon(testNode, "testInnerClassCtor", new TestClass(21));
    }

    @Test
    public void testFinalField() {
        AbstractTestNode testNode = new FinalFieldNode();
        testCommon(testNode, "testFinalField", new FinalField());
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        AbstractTestNode testNode = new RequireNonNullNode();
        testCommon(testNode, "testNull", null);
    }

    private void testCommon(AbstractTestNode testNode, String testName, Object arg) {
        FrameDescriptor fd = new FrameDescriptor();
        RootNode rootNode = new RootTestNode(fd, testName, testNode);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        Assert.assertEquals(42, callTarget.call(arg));
        assertPartialEvalNoInvokes(callTarget, new Object[]{arg});
    }

    static class RequireNonNullNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            Objects.requireNonNull(frame.getArguments()[0]);
            return 42;
        }
    }

    static class InnerClassNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            TestClass test = (TestClass) frame.getArguments()[0];
            // javac produces an implicit Objects.requireNonNull(test) for the next line
            InnerClass inner = test.new InnerClass(2);
            return inner.get();
        }
    }

    static class TestClass {

        int foo;

        TestClass(int foo) {
            this.foo = foo;
        }

        class InnerClass {

            int bar;

            InnerClass(int bar) {
                this.bar = bar;
            }

            int get() {
                return foo * bar;
            }
        }
    }

    static class FinalFieldNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            FinalField f = (FinalField) frame.getArguments()[0];
            // javac folds the field read to constant 42, and inserts Objects.requireNonNull(f)
            return f.foo;
        }
    }

    static class FinalField {

        final int foo = 42;
    }
}
