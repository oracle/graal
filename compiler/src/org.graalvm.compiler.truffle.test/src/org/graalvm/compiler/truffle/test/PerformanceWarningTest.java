/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.GraalCompilerDirectives;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class PerformanceWarningTest extends TruffleCompilerImplTest {

    // Marker object indicating that no performance warnings are expected.
    private static final String[] EMPTY_PERF_WARNINGS = new String[0];

    // Ensure classes are loaded.
    @SuppressWarnings("unused") private static final VirtualObject1 object1 = new VirtualObject1();
    @SuppressWarnings("unused") private static final VirtualObject2 object2 = new VirtualObject2();
    @SuppressWarnings("unused") private static final SubClass object3 = new SubClass();
    @SuppressWarnings("unused") private static final L9a object4 = new L9a();
    @SuppressWarnings("unused") private static final L9b object5 = new L9b();
    @SuppressWarnings("unused") private static final Boolean inFirstTier = GraalCompilerDirectives.inFirstTier();

    private ByteArrayOutputStream outContent;

    @Before
    public void setUp() {
        outContent = new ByteArrayOutputStream();
        setupContext(Context.newBuilder().logHandler(outContent).allowAllAccess(true).allowExperimentalOptions(true).option("engine.TracePerformanceWarnings", "all").option(
                        "engine.TreatPerformanceWarningsAsErrors", "all").option("engine.CompilationFailureAction", "ExitVM").build());
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
        if (GraalServices.hasLookupReferencedType()) {
            testHelper(new RootNodeInterfaceSingleImplementorCall(), false, EMPTY_PERF_WARNINGS);
        }
    }

    @Test
    public void testSlowClassCast() {
        testHelper(new RootNodeDeepClass(), false, "perf info", L8.class.getSimpleName(), "foo", "execute");
    }

    @SuppressWarnings("try")
    private void testHelper(RootNode rootNode, boolean expectException, String... outputStrings) {

        // Compile and capture output to logger's stream.
        boolean seenException = false;
        try {
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
            DebugContext debug = new Builder(TruffleCompilerOptions.getOptions()).build();
            try (DebugCloseable d = debug.disableIntercept(); DebugContext.Scope s = debug.scope("PerformanceWarningTest")) {
                final OptimizedCallTarget compilable = target;
                CompilationIdentifier compilationId = getTruffleCompiler(target).createCompilationIdentifier(compilable);
                TruffleInliningPlan inliningPlan = new TruffleInlining(compilable, new DefaultInliningPolicy());
                getTruffleCompiler(target).compileAST(compilable.getOptionValues(), debug, compilable, inliningPlan, compilationId, null, null);
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

    private abstract class TestRootNode extends RootNode {

        private TestRootNode() {
            super(null);
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
}
