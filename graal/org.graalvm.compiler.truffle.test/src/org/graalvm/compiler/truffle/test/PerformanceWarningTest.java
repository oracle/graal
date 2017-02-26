/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.ByteArrayOutputStream;

import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.DefaultTruffleCompiler;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleOptionsOverrideScope;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class PerformanceWarningTest {

    // Marker object indicating that no performance warnings are expected.
    private static final String[] EMPTY_PERF_WARNINGS = new String[0];

    // Ensure classes are loaded.
    @SuppressWarnings("unused") private static final VirtualObject1 object1 = new VirtualObject1();
    @SuppressWarnings("unused") private static final VirtualObject2 object2 = new VirtualObject2();
    @SuppressWarnings("unused") private static final SubClass object3 = new SubClass();

    @Test
    public void testVirtualCall() {
        testHelper(new RootNodeVirtualCall(), "perf warn", "execute");
    }

    @Test
    public void testDeepStack() {
        testHelper(new RootNodeDeepStack(), "perf warn", "foo", "bar", "execute");
    }

    @Test
    public void testInstanceOf() {
        testHelper(new RootNodeInstanceOf(), "perf info", "foo", "bar", "execute");
    }

    @Test
    public void testCombined() {
        testHelper(new RootNodeCombined(), "perf info", "perf warn", "foo", "bar", "execute");
    }

    @Test
    public void testBoundaryCall() {
        testHelper(new RootNodeBoundaryCall(), EMPTY_PERF_WARNINGS);
    }

    @Test
    public void testBoundaryVirtualCall() {
        testHelper(new RootNodeBoundaryVirtualCall(), EMPTY_PERF_WARNINGS);
    }

    @Test
    public void testCast() {
        testHelper(new RootNodeCast(), "perf info", "foo", "bar", "execute");
    }

    @Test
    public void testSingleImplementor() {
        testHelper(new RootNodeInterfaceSingleImplementorCall(), "perf info", "type check", "foo");
    }

    @SuppressWarnings("try")
    private static void testHelper(RootNode rootNode, String... outputStrings) {

        OptimizedCallTarget target = (OptimizedCallTarget) GraalTruffleRuntime.getRuntime().createCallTarget(rootNode);

        // Compile and capture output to TTY.
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        try (TTY.Filter filter = new TTY.Filter(new LogStream(outContent))) {
            try (TruffleOptionsOverrideScope scope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TraceTrufflePerformanceWarnings, Boolean.TRUE)) {
                DefaultTruffleCompiler.create(GraalTruffleRuntime.getRuntime()).compileMethod(target, GraalTruffleRuntime.getRuntime(), null);
            }
        }

        // Check output on TTY.
        String output = outContent.toString();
        if (outputStrings == EMPTY_PERF_WARNINGS) {
            Assert.assertEquals("", output);
        } else {
            for (String s : outputStrings) {
                Assert.assertTrue(String.format("Root node class %s: \"%s\" not found in output \"%s\"", rootNode.getClass(), s, output), output.contains(s));
            }
        }
    }

    private abstract class TestRootNode extends RootNode {

        private TestRootNode() {
            super(TruffleLanguage.class, null, null);
        }
    }

    private final class RootNodeVirtualCall extends TestRootNode {
        protected Interface a;

        @Override
        public Object execute(VirtualFrame frame) {
            a.doVirtual();
            return null;
        }
    }

    private final class RootNodeDeepStack extends TestRootNode {
        protected Interface a;

        @Override
        public Object execute(VirtualFrame frame) {
            bar();
            return null;
        }

        private void bar() {
            foo();
        }

        private void foo() {
            a.doVirtual();
        }
    }

    private final class RootNodeInstanceOf extends TestRootNode {
        protected Object obj;

        @Override
        public Object execute(VirtualFrame frame) {
            return bar();
        }

        private boolean bar() {
            return foo();
        }

        private boolean foo() {
            return (obj instanceof Interface);
        }
    }

    private final class RootNodeCombined extends TestRootNode {
        protected Object obj;
        protected Interface a;

        @Override
        public Object execute(VirtualFrame frame) {
            return bar();
        }

        private int bar() {
            return foo();
        }

        private int foo() {
            int result = 0;
            if (obj instanceof Interface) {
                result++;
            }
            a.doVirtual();
            if (obj instanceof Interface) {
                result++;
            }
            return result;
        }
    }

    private final class RootNodeBoundaryCall extends TestRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return bar();
        }

        @TruffleBoundary
        private int bar() {
            return 42;
        }
    }

    private final class RootNodeBoundaryVirtualCall extends TestRootNode {

        protected Interface a;

        @Override
        public Object execute(VirtualFrame frame) {
            a.doBoundaryVirtual();
            return null;
        }
    }

    private final class RootNodeCast extends TestRootNode {
        protected Object obj;

        @Override
        public Object execute(VirtualFrame frame) {
            bar();
            return null;
        }

        private void bar() {
            foo();
        }

        @SuppressWarnings("unused")
        private void foo() {
            Interface i = (Interface) obj;
        }
    }

    private interface Interface {

        void doVirtual();

        @TruffleBoundary
        void doBoundaryVirtual();

    }

    private static class VirtualObject1 implements Interface {
        @Override
        public void doVirtual() {
        }

        @Override
        public void doBoundaryVirtual() {
        }
    }

    private static class VirtualObject2 implements Interface {
        @Override
        public void doVirtual() {
        }

        @Override
        public void doBoundaryVirtual() {
        }
    }

    private final class RootNodeInterfaceSingleImplementorCall extends TestRootNode {
        protected SingleImplementedInterface a;

        @Override
        public Object execute(VirtualFrame frame) {
            foo();
            return null;
        }

        public void foo() {
            a.doVirtual();
        }
    }

    private interface SingleImplementedInterface {
        void doVirtual();
    }

    private static class SingleImplementorClass implements SingleImplementedInterface {
        @Override
        public void doVirtual() {
        }
    }

    private static class SubClass extends SingleImplementorClass {
    }

}
