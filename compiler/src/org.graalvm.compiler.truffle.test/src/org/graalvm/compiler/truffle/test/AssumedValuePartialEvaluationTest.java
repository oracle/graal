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

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.AssumedValue;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Test;

public class AssumedValuePartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void testFold() {
        AssumedValue<Integer> value = new AssumedValue<>(42);

        RootTestNode root = new RootTestNode(new FrameDescriptor(), "assumedValue", new ReadConstantAssumedValueNode(value));
        OptimizedCallTarget target = assertPartialEvalEquals("constant42", root);

        Assert.assertTrue("CallTarget is valid", target.isValid());
        Assert.assertEquals(42, target.call());
    }

    @Test
    public void testDeopt() {
        AssumedValue<Integer> value = new AssumedValue<>(42);

        RootTestNode root = new RootTestNode(new FrameDescriptor(), "assumedValue", new ReadConstantAssumedValueNode(value));
        OptimizedCallTarget target = assertPartialEvalEquals("constant42", root);

        Assert.assertTrue("CallTarget is valid", target.isValid());
        Assert.assertEquals(42, target.call());

        value.set(37);

        Assert.assertFalse("CallTarget was invalidated", target.isValid());
        Assert.assertEquals(37, target.call());
    }

    @Test
    public void testDynamic() {
        AssumedValue<Integer> value = new AssumedValue<>(42);

        RootTestNode root = new RootTestNode(new FrameDescriptor(), "assumedValue", new ReadDynamicAssumedValueNode());
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);

        StructuredGraph graph = partialEval(target, new Object[]{value});
        compile(target, graph);

        Assert.assertTrue("CallTarget is valid", target.isValid());
        Assert.assertEquals(42, target.call(value));
    }

    @Test
    public void testDynamicNoDeopt() {
        AssumedValue<Integer> value = new AssumedValue<>(42);

        RootTestNode root = new RootTestNode(new FrameDescriptor(), "assumedValue", new ReadDynamicAssumedValueNode());
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);

        StructuredGraph graph = partialEval(target, new Object[]{value});
        compile(target, graph);

        Assert.assertTrue("CallTarget is valid", target.isValid());
        Assert.assertEquals(42, target.call(value));

        value.set(37);

        Assert.assertTrue("CallTarget is still valid", target.isValid());
        Assert.assertEquals(37, target.call(value));
    }

    private static class ReadConstantAssumedValueNode extends AbstractTestNode {

        private final AssumedValue<Integer> assumedValue;

        ReadConstantAssumedValueNode(AssumedValue<Integer> assumedValue) {
            this.assumedValue = assumedValue;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return assumedValue.get();
        }
    }

    private static class ReadDynamicAssumedValueNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            AssumedValue<?> assumedValue = (AssumedValue<?>) frame.getArguments()[0];
            return (Integer) assumedValue.get();
        }
    }
}
