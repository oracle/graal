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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode;
import org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.ConstantTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("try")
public class OptimizedCallTargetTest extends TestWithSynchronousCompiling {
    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
    private static final Field nodeRewritingAssumptionField;
    static {
        try {
            nodeRewritingAssumptionField = OptimizedCallTarget.class.getDeclaredField("nodeRewritingAssumption");
            Util.setAccessible(nodeRewritingAssumptionField, true);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static Assumption getRewriteAssumption(OptimizedCallTarget target) {
        try {
            return (Assumption) nodeRewritingAssumptionField.get(target);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static final class CallTestNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        CallTestNode(CallTarget ct) {
            this.callNode = runtime.createDirectCallNode(ct);
        }

        @Override
        public int execute(VirtualFrame frame) {
            return (int) callNode.call(frame.getArguments());
        }
    }

    volatile int testInvalidationCounterCompiled = 0;
    volatile int testInvalidationCounterInterpreted = 0;
    volatile boolean doInvalidate;

    /*
     * GR-1328
     */
    @Test
    @Ignore("Fails non deterministically")
    public void testCompilationHeuristics() {
        testInvalidationCounterCompiled = 0;
        testInvalidationCounterInterpreted = 0;
        doInvalidate = false;
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (CompilerDirectives.inInterpreter()) {
                    testInvalidationCounterInterpreted++;
                } else {
                    testInvalidationCounterCompiled++;
                }
                // doInvalidate needs to be volatile otherwise it floats up.
                if (doInvalidate) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                return null;
            }
        });
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        final int reprofileCount = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount);
        assertTrue(compilationThreshold >= 2);

        int expectedCompiledCount = 0;
        int expectedInterpreterCount = 0;
        for (int i = 0; i < compilationThreshold; i++) {
            assertNotCompiled(target);
            target.call();
        }
        assertCompiled(target);
        expectedInterpreterCount += compilationThreshold;
        assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
        assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

        for (int j = 1; j < 100; j++) {

            target.invalidate(this, "test");
            for (int i = 0; i < reprofileCount; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
            expectedInterpreterCount += reprofileCount;
            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            doInvalidate = true;
            expectedCompiledCount++;
            target.call();
            assertNotCompiled(target);
            doInvalidate = false;

            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            for (int i = 0; i < reprofileCount; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
            expectedInterpreterCount += reprofileCount;

            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            for (int i = 0; i < compilationThreshold; i++) {
                assertCompiled(target);
                target.call();
            }

            assertCompiled(target);

            expectedCompiledCount += compilationThreshold;
            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);
        }
    }

    @Test
    public void testRewriteAssumption() {
        String testName = "testRewriteAssumption";
        final int compilationThreshold = 20;

        Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.Inlining", "true").option("engine.CompilationThreshold", String.valueOf(compilationThreshold)).build();
        context.enter();
        try {
            OptimizedCallTarget innermostCallTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 0, new AbstractTestNode() {
                @Child private AbstractTestNode child = new ConstantTestNode(42);
                @Child private AbstractTestNode dummy = new ConstantTestNode(17);

                @Override
                public int execute(VirtualFrame frame) {
                    int k = (int) frame.getArguments()[0];
                    if (k > compilationThreshold) {
                        CompilerDirectives.transferToInterpreter();
                        dummy.replace(new ConstantTestNode(k));
                    }
                    return child.execute(frame);
                }
            }));
            assertEquals(compilationThreshold, (int) innermostCallTarget.getOptionValue(PolyglotCompilerOptions.CompilationThreshold));

            OptimizedCallTarget ct = innermostCallTarget;
            ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 1, new CallTestNode(ct)));
            ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 2, new CallTestNode(ct)));
            final OptimizedCallTarget outermostCallTarget = ct;

            assertNull("assumption is initially null", getRewriteAssumption(innermostCallTarget));

            IntStream.range(0, compilationThreshold / 2).parallel().forEach(k -> {
                try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileImmediately, false)) {
                    assertEquals(42, outermostCallTarget.call(k));
                    assertNull("assumption stays null in the interpreter", getRewriteAssumption(innermostCallTarget));
                }
            });

            outermostCallTarget.compile(true);
            assertCompiled(outermostCallTarget);
            Assumption firstRewriteAssumption = getRewriteAssumption(innermostCallTarget);
            assertNotNull("assumption must not be null after compilation", firstRewriteAssumption);
            assertTrue(firstRewriteAssumption.isValid());

            List<Assumption> rewriteAssumptions = IntStream.range(0, 2 * compilationThreshold).parallel().mapToObj(k -> {
                assertEquals(42, outermostCallTarget.call(k));

                Assumption rewriteAssumptionAfter = getRewriteAssumption(innermostCallTarget);
                assertNotNull("assumption must not be null after compilation", rewriteAssumptionAfter);
                return rewriteAssumptionAfter;
            }).collect(Collectors.toList());

            Assumption finalRewriteAssumption = getRewriteAssumption(innermostCallTarget);
            assertNotNull("assumption must not be null after compilation", finalRewriteAssumption);
            assertNotSame(firstRewriteAssumption, finalRewriteAssumption);
            assertFalse(firstRewriteAssumption.isValid());
            assertTrue(finalRewriteAssumption.isValid());

            assertFalse(rewriteAssumptions.stream().filter(a -> a != finalRewriteAssumption).anyMatch(Assumption::isValid));
        } finally {
            context.leave();
            context.close();
        }
    }

    private static class NamedRootNode extends RootNode {

        private String name;

        NamedRootNode(String name) {
            super(null);
            this.name = name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Test
    public void testCompileOnly1() {
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);

        // test single include

        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileOnly, "foobar")) {
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("foobar"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
            target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("baz"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertNotCompiled(target);
        }

    }

    @Test
    public void testCompileOnly2() {
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        // test single exclude
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileOnly, "~foobar")) {
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("foobar"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertNotCompiled(target);
            target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("baz"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
        }
    }

    @Test
    public void testCompileOnly3() {
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        // test two includes/excludes
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileOnly, "foo,baz")) {
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("foobar"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
            target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("baz"));
            for (int i = 0; i < compilationThreshold; i++) {
                assertNotCompiled(target);
                target.call();
            }
            assertCompiled(target);
        }
    }

    private static class OSRRepeatingNode extends Node implements RepeatingNode {
        int count = 0;
        final int osrCompilationThreshold;

        OSRRepeatingNode(int osrCompilationThreshold) {
            this.osrCompilationThreshold = osrCompilationThreshold;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            count++;
            return count < (osrCompilationThreshold + 10);
        }
    }

    @Test
    public void testCompileOnly4() {
        // OSR should not trigger for compile-only includes
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileOnly, "foobar")) {
            final OSRRepeatingNode repeating = new OSRRepeatingNode(TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold));
            final LoopNode loop = runtime.createLoopNode(repeating);
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("foobar") {

                @Child LoopNode loopChild = loop;

                @Override
                public Object execute(VirtualFrame frame) {
                    loopChild.executeLoop(frame);
                    return super.execute(frame);
                }

            });
            target.call();
            OptimizedCallTarget osrTarget = findOSRTarget(loop);
            if (osrTarget != null) {
                assertNotCompiled(osrTarget);
            }
        }
    }

    private static OptimizedCallTarget findOSRTarget(Node loopNode) {
        if (loopNode instanceof OptimizedOSRLoopNode) {
            return ((OptimizedOSRLoopNode) loopNode).getCompiledOSRLoop();
        }

        for (Node child : loopNode.getChildren()) {
            OptimizedCallTarget target = findOSRTarget(child);
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    @Test
    public void testCompileOnly5() {
        // OSR should trigger if compile-only with excludes
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileOnly, "~foobar")) {
            final OSRRepeatingNode repeating = new OSRRepeatingNode(TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold));
            final LoopNode loop = runtime.createLoopNode(repeating);
            OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new NamedRootNode("foobar") {

                @Child LoopNode loopChild = loop;

                @Override
                public Object execute(VirtualFrame frame) {
                    loopChild.executeLoop(frame);
                    return super.execute(frame);
                }

            });
            target.call();
            OptimizedCallTarget osrTarget = findOSRTarget(loop);
            assertCompiled(osrTarget);
        }
    }

    @Ignore
    @Test
    public void testInCompilationRootDirective() {
        final int compilationThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationThreshold);

        int[] outerExecute = {0};
        int[] outerMethod = {0};
        int[] outerBoundary = {0};

        int[] innerExecute = {0};
        int[] innerMethod = {0};
        int[] innerBoundary = {0};

        final OptimizedCallTarget innerTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

            @Override
            public String toString() {
                return "inner";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                // FALSE
                if (CompilerDirectives.inCompilationRoot()) {
                    innerExecute[0]++;
                }
                innerMethod();
                return null;
            }

            @CompilerDirectives.TruffleBoundary
            void innerMethod() {
                // FALSE
                if (CompilerDirectives.inCompilationRoot()) {
                    innerMethod[0]++;
                }
            }

            @CompilerDirectives.TruffleBoundary
            void innerBoundary() {
                // FALSE
                if (CompilerDirectives.inCompilationRoot()) {
                    innerBoundary[0] = 1;
                }
            }
        });
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

            @Override
            public String toString() {
                return "outer";
            }

            @Child private DirectCallNode child = runtime.createDirectCallNode(innerTarget);

            @Override
            public Object execute(VirtualFrame frame) {
                // TRUE
                if (CompilerDirectives.inCompilationRoot()) {
                    outerExecute[0]++;
                }
                outerMethod();
                return child.call(new Object[0]);
            }

            void outerMethod() {
                // TRUE
                if (CompilerDirectives.inCompilationRoot()) {
                    outerMethod[0]++;
                    outerBoundary();
                }
            }

            @CompilerDirectives.TruffleBoundary
            void outerBoundary() {
                // FALSE
                if (CompilerDirectives.inCompilationRoot()) {
                    outerBoundary[0]++;
                }
            }
        });

        for (int i = 0; i < compilationThreshold; i++) {
            outerTarget.call();
        }
        assertCompiled(outerTarget);
        final int executionCount = 10;
        for (int i = 0; i < executionCount; i++) {
            outerTarget.call();
        }

        Assert.assertEquals(executionCount, outerExecute[0]);
        Assert.assertEquals(executionCount, outerMethod[0]);
        Assert.assertEquals(0, outerBoundary[0]);

        Assert.assertEquals(0, innerExecute[0]);
        Assert.assertEquals(0, innerMethod[0]);
        Assert.assertEquals(0, innerBoundary[0]);
    }

    @Test
    public void testManyArguments() {
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleCompileImmediately, true,
                        SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown, true)) {
            CallTarget fortyTwo = runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), "42", new ConstantTestNode(42)));
            OptimizedCallTarget ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), "caller", new CallTestNode(fortyTwo)));
            for (int i = 0; i < 3; i++) {
                ct.call(IntStream.range(0, 100000).mapToObj(Integer::valueOf).toArray());
            }
            assertCompiled(ct);
        }
    }

    @Test
    public void testNoArgumentTypeSpeculation() {
        try (TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleCompileImmediately, true,
                        SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown, true,
                        SharedTruffleRuntimeOptions.TruffleArgumentTypeSpeculation, false)) {
            CallTarget fortyTwo = runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), "42", new ConstantTestNode(42)));
            OptimizedCallTarget ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), "caller", new CallTestNode(fortyTwo)));
            for (int i = 0; i < 3; i++) {
                ct.call(IntStream.range(0, 20).mapToObj(Integer::valueOf).toArray());
            }
            assertCompiled(ct);

            // argument type change does not invalidate
            ct.call(IntStream.range(0, 20).mapToObj(String::valueOf).toArray());
            assertCompiled(ct);
        }
    }
}
