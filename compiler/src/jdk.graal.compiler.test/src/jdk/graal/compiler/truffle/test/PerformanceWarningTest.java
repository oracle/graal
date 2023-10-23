/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

public class PerformanceWarningTest extends TruffleCompilerImplTest {

    // Marker object indicating that no performance warnings are expected.
    private static final String[] EMPTY_PERF_WARNINGS = new String[0];

    // Ensure classes are loaded.
    @SuppressWarnings("unused") private static final VirtualObject1 object1 = new VirtualObject1();
    @SuppressWarnings("unused") private static final VirtualObject2 object2 = new VirtualObject2();
    @SuppressWarnings("unused") private static final SubClass object3 = new SubClass();
    @SuppressWarnings("unused") private static final L9a object4 = new L9a();
    @SuppressWarnings("unused") private static final L9b object5 = new L9b();

    private ByteArrayOutputStream outContent;

    @Before
    public void setUp() {
        outContent = new ByteArrayOutputStream();
        setupContext(Context.newBuilder().logHandler(outContent).allowAllAccess(true).allowExperimentalOptions(true).option("compiler.TracePerformanceWarnings", "all").option(
                        "compiler.TreatPerformanceWarningsAsErrors", "all").option("engine.CompilationFailureAction", "ExitVM").build());
    }

    @Test
    public void testVirtualCall() {
        testHelper(new RootNodeVirtualCall(), true, "perf warn", "execute");
    }

    @Test
    public void testDeepStack() {
        testHelper(new RootNodeDeepStack(), true, "perf warn", "foo", "bar", "execute");
    }

    @Test
    public void testInstanceOf() {
        testHelper(new RootNodeInstanceOf(), false, "perf info", "foo", "bar", "execute");
    }

    @Test
    public void testCombined() {
        testHelper(new RootNodeCombined(), true, "perf info", "perf warn", "foo", "bar", "execute");
    }

    @Test
    public void testBoundaryCall() {
        testHelper(new RootNodeBoundaryCall(), false, EMPTY_PERF_WARNINGS);
    }

    @Test
    public void testBoundaryVirtualCall() {
        testHelper(new RootNodeBoundaryVirtualCall(), false, EMPTY_PERF_WARNINGS);
    }

    @Test
    public void testInterfaceCast() {
        testHelper(new RootNodeInterfaceCast(), false, "perf info", Interface.class.getSimpleName(), "foo", "bar", "execute");
    }

    @Test
    public void testSingleImplementor() {
        testHelper(new RootNodeInterfaceSingleImplementorCall(), false, EMPTY_PERF_WARNINGS);
    }

    @Test
    public void testSlowClassCast() {
        testHelper(new RootNodeDeepClass(), false, "perf info", L8.class.getSimpleName(), "foo", "execute");
    }

    @Test
    public void testFrameAccessVerification() {
        testHelper(new RootNodeFrameAccessVerification(), true, "perf warn");
    }

    @SuppressWarnings("try")
    private void testHelper(RootNode rootNode, boolean expectException, String... outputStrings) {
        // Compile and capture output to logger's stream.
        boolean seenException = false;
        try {
            OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
            TruffleCompilerImpl compiler = getTruffleCompiler(target);
            DebugContext debug = new Builder(compiler.getOrCreateCompilerOptions(target)).build();
            try (DebugCloseable d = debug.disableIntercept(); DebugContext.Scope s = debug.scope("PerformanceWarningTest")) {
                final OptimizedCallTarget compilable = target;
                TruffleCompilationTask task = PartialEvaluationTest.newTask();
                try (TruffleCompilation compilation = compiler.openCompilation(task, compilable)) {
                    compiler.compileAST(debug, compilable, compilation.getCompilationId(), task, null);
                }

                assertTrue(compilable.isValid());
            }
        } catch (AssertionError e) {
            seenException = true;
            if (!expectException) {
                throw new AssertionError("Unexpected exception caught." + (outContent.size() > 0 ? '\n' + outContent.toString() : ""), e);
            }
        }
        if (expectException && !seenException) {
            Assert.assertTrue("Expected exception not caught.", false);
        }

        // Check output on TTY.
        String output = outContent.toString();
        if (outputStrings == EMPTY_PERF_WARNINGS) {
            Assert.assertEquals("", output);
        } else {
            for (String s : outputStrings) {
                Assert.assertTrue(String.format("Root node class %s: \"%s\" not found in output \"%s\"", rootNode.getClass().getName(), s, output), output.contains(s));
            }
        }
    }

    @Test
    public void failedTrivial() {
        testHelper(new TrivialCallsInnerNode(), true, "perf warn", "trivial");
    }

    private interface Interface {

        void doVirtual();

        @TruffleBoundary
        void doBoundaryVirtual();

    }

    private interface SingleImplementedInterface {
        void doVirtual();
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

    private static class SingleImplementorClass implements SingleImplementedInterface {
        @Override
        public void doVirtual() {
        }
    }

    private static class SubClass extends SingleImplementorClass {
    }

    private static class L1 {
    }

    private static class L2 extends L1 {
    }

    private static class L3 extends L2 {
    }

    private static class L4 extends L3 {
    }

    private static class L5 extends L4 {
    }

    private static class L6 extends L5 {
    }

    private static class L7 extends L6 {
    }

    private static class L8 extends L7 {
    }

    private static class L9a extends L8 {
    }

    private static class L9b extends L8 {
    }

    private abstract static class TestRootNode extends RootNode {

        private TestRootNode() {
            super(null);
        }

        private TestRootNode(FrameDescriptor fd) {
            super(null, fd);
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

    private final class RootNodeInterfaceCast extends TestRootNode {
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

    private final class RootNodeDeepClass extends TestRootNode {
        protected Object obj;

        @Override
        public Object execute(VirtualFrame frame) {
            foo();
            return null;
        }

        @SuppressWarnings("unused")
        private void foo() {
            L8 c = (L8) obj;
        }
    }

    private static final class RootNodeFrameAccessVerification extends TestRootNode {

        private static final int SLOT = 0;

        RootNodeFrameAccessVerification() {
            super(createFrameDescriptor());
        }

        private static FrameDescriptor createFrameDescriptor() {
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
            assert SLOT == slot;
            return builder.build();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();

            if ((boolean) args[0]) {
                frame.setDouble(0, 5);
            } else {
                frame.setInt(0, 1);
            }
            // Expected Perf warn
            boundary();
            return null;
        }

        @TruffleBoundary
        private void boundary() {
        }
    }

    protected class TrivialCallsInnerNode extends RootNode {

        @Child private OptimizedDirectCallNode callNode;

        public TrivialCallsInnerNode() {
            super(null);
            this.callNode = (OptimizedDirectCallNode) OptimizedTruffleRuntime.getRuntime().createDirectCallNode(new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 0;
                }
            }.getCallTarget());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return (int) callNode.call(frame.getArguments()) + 1;
        }

        @Override
        protected boolean isTrivial() {
            return true;
        }

        @Override
        public String toString() {
            return "trivial";
        }
    }

    private static final class RootNodeDevirtualizeInvokeVirtual extends TestRootNode {
        Base base;

        @Override
        public Object execute(VirtualFrame frame) {
            return base.bar();
        }

        abstract static class Base {
            public String bar() {
                return "Base";
            }
        }

        abstract static class AbstractBaseImpl extends Base {
            @Override
            public String bar() {
                return "AbstractBaseImpl";
            }
        }

        static final class ConcreteImpl extends AbstractBaseImpl {
            public static void ensureInitialized() {
            }
        }
    }

    @Test
    public void testDevirtualizeInvokeVirtual() {
        RootNodeDevirtualizeInvokeVirtual.ConcreteImpl.ensureInitialized();
        testHelper(new RootNodeDevirtualizeInvokeVirtual(), false, EMPTY_PERF_WARNINGS);
    }
}
