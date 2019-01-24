/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class CompilationFinalWeakReferencePartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    private static class TestData implements IntSupplier {
        private final TestData left;
        private final TestData right;

        TestData() {
            this.left = null;
            this.right = null;
        }

        TestData(TestData left, TestData right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public int getAsInt() {
            if (left == null && right == null) {
                return 1;
            }
            return (left == null ? 0 : left.getAsInt()) + (right == null ? 0 : right.getAsInt());
        }
    }

    private static class CompilationFinalWeakReferenceTestNode extends AbstractTestNode {
        @CompilationFinal private WeakReference<Integer> finalWeakRefInteger;
        @CompilationFinal private WeakReference<Object> finalWeakRefNull;
        @CompilationFinal private WeakReference<IntSupplier> finalWeakRef;

        CompilationFinalWeakReferenceTestNode(IntSupplier data) {
            this.finalWeakRefInteger = new WeakReference<>(0);
            this.finalWeakRefNull = new WeakReference<>(null);
            this.finalWeakRef = new WeakReference<>(data);
        }

        @Override
        public int execute(VirtualFrame frame) {
            partialEvaluationConstantAndEquals(finalWeakRefInteger.get(), Integer.valueOf(0));
            partialEvaluationConstantAndEquals(finalWeakRefNull.get(), null);

            IntSupplier supplier = finalWeakRef.get();
            if (supplier != null) {
                return supplier.getAsInt();
            } else {
                return 0xdead;
            }
        }
    }

    private static class CompilationFinalWeakReferenceTestGCNode extends AbstractTestNode {
        @CompilationFinal private WeakReference<IntSupplier> finalWeakRef;

        CompilationFinalWeakReferenceTestGCNode(IntSupplier data) {
            this.finalWeakRef = new WeakReference<>(data);
        }

        @Override
        public int execute(VirtualFrame frame) {
            IntSupplier supplier = finalWeakRef.get();
            if (supplier == null) {
                return 0xdead;
            } else if (supplier == frame.getArguments()[0]) {
                return supplier.getAsInt();
            } else {
                return -1;
            }
        }
    }

    private static void partialEvaluationConstantAndEquals(Object a, Object b) {
        CompilerAsserts.partialEvaluationConstant(a);
        CompilerAsserts.partialEvaluationConstant(b);
        if (a != b) {
            throw new AssertionError();
        }
    }

    /**
     * {@link WeakReference} constant-folded but not embedded in compiled code.
     */
    @Test
    public void compilationFinalWeakReferenceTest() {
        String name = "compilationFinalWeakReferenceTest";
        FrameDescriptor fd = new FrameDescriptor();
        IntSupplier data = generateTestData();
        AbstractTestNode result = new CompilationFinalWeakReferenceTestNode(data);
        RootTestNode rootNode = new RootTestNode(fd, name, result);
        assertPartialEvalEquals("constant42", rootNode);

        OptimizedCallTarget callTarget = compileHelper(name, rootNode, new Object[0]);
        Assert.assertEquals(42, (int) callTarget.call(new Object[0]));
        assert data != null;

        WeakReference<IntSupplier> witness = new WeakReference<>(data);
        data = null;
        boolean cleared = false;
        for (int i = 1; i <= 5 && !cleared; i++) {
            System.gc();
            cleared = witness.get() == null;
        }

        // Reference not embedded in compiled code
        Assert.assertEquals(42, (int) callTarget.call(new Object[0]));
        assertTrue(callTarget.isValid());
    }

    /**
     * {@link WeakReference} constant-folded and embedded in compiled code.
     */
    @Test
    public void compilationFinalWeakReferenceTestGC() {
        String name = "compilationFinalWeakReferenceTestGC";
        FrameDescriptor fd = new FrameDescriptor();
        IntSupplier data = generateTestData();
        AbstractTestNode result = new CompilationFinalWeakReferenceTestGCNode(data);
        RootTestNode rootNode = new RootTestNode(fd, name, result);
        OptimizedCallTarget callTarget = compileHelper(name, rootNode, new Object[]{data});
        Assert.assertEquals(42, (int) callTarget.call(new Object[]{data}));
        Assert.assertEquals(-1, (int) callTarget.call(new Object[]{null}));
        callTarget = compileHelper(name, rootNode, new Object[]{data});
        assertTrue(callTarget.isValid());
        assert data != null;

        clearDebugScopeTL();

        WeakReference<IntSupplier> witness = new WeakReference<>(data);
        data = null;
        boolean cleared = false;
        for (int i = 1; i <= 5 && !cleared; i++) {
            System.gc();
            cleared = witness.get() == null;
        }

        assertTrue("Test data should have been garbage collected at this point", cleared);

        // Compiled code had the collected reference embedded so it had to be invalidated
        assertFalse(callTarget.isValid());
        Assert.assertEquals(0xdead, (int) callTarget.call(new Object[]{null}));
    }

    private static IntSupplier generateTestData() {
        return IntStream.range(0, 42).mapToObj(i -> new TestData()).reduce((l, r) -> new TestData(l, r)).get();
    }

    /**
     * Perform a dummy compilation to ensure compilation result data of the last compilation kept
     * alive through DebugScope thread locals are freed.
     */
    private void clearDebugScopeTL() {
        compileHelper("dummy", RootNode.createConstantNode(null), new Object[]{});
    }
}
