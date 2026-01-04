/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SingleTierCompilationThreshold;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

public class TruffleBoundaryExceptionsTest extends TestWithSynchronousCompiling {

    private static final OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();

    @Override
    @Before
    public void before() {
        setupContext("engine.MultiTier", "false");
    }

    @Test
    public void testExceptionOnTruffleBoundaryDeoptsOnce() {
        class DeoptCountingExceptionOverBoundaryRootNode extends RootNode {

            protected DeoptCountingExceptionOverBoundaryRootNode() {
                super(null);
            }

            int deoptCounter = 0;
            int catchCounter = 0;
            int interpretCount = 0;

            @Override
            public Object execute(VirtualFrame frame) {
                boolean startedCompiled = CompilerDirectives.inCompiledCode();
                if (!startedCompiled) {
                    interpretCount++;
                }
                try {
                    throwExceptionBoundary();
                } catch (RuntimeException e) {
                    catchCounter++;
                }
                if (startedCompiled && CompilerDirectives.inInterpreter()) {
                    deoptCounter++;
                }
                return null;
            }

            @CompilerDirectives.TruffleBoundary
            public void throwExceptionBoundary() {
                throw new RuntimeException();
            }
        }
        final int[] compilationCount = {0};
        OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
            @Override
            public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
                compilationCount[0]++;
            }
        };

        setupContext("engine.MultiTier", "false");
        DeoptCountingExceptionOverBoundaryRootNode rootNode = new DeoptCountingExceptionOverBoundaryRootNode();
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) rootNode.getCallTarget();

        final int compilationThreshold = outerTarget.getOptionValue(SingleTierCompilationThreshold);
        for (int i = 0; i < compilationThreshold; i++) {
            outerTarget.call();
        }
        // deoptimizes immediately due to the exception
        assertEquals("Incorrect number of deopts detected!", 1, rootNode.deoptCounter);
        assertNotCompiled(outerTarget);
        // recompile with exception branch
        outerTarget.call();
        assertCompiled(outerTarget);

        runtime.addListener(listener);
        try {
            final int execCount = 10;
            for (int i = 0; i < execCount; i++) {
                outerTarget.call();
            }

            final int totalExecutions = compilationThreshold + 1 + execCount;
            assertEquals("Incorrect number of catch block executions", totalExecutions, rootNode.catchCounter);

            assertEquals("Incorrect number of interpreted executions", compilationThreshold - 1, rootNode.interpretCount);
            assertEquals("Incorrect number of deopts detected!", 1, rootNode.deoptCounter);

            assertEquals("Compilation happened!", 0, compilationCount[0]);
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testExceptionOnTruffleBoundaryWithNoTransferToInterpreter() {
        class DeoptCountingExceptionOverBoundaryRootNode extends RootNode {

            protected DeoptCountingExceptionOverBoundaryRootNode() {
                super(null);
            }

            int deoptCounter = 0;
            int catchCounter = 0;

            @Override
            public Object execute(VirtualFrame frame) {
                boolean startedCompiled = CompilerDirectives.inCompiledCode();
                try {
                    throwExceptionBoundary();
                } catch (RuntimeException e) {
                    catchCounter++;
                }
                if (startedCompiled && CompilerDirectives.inInterpreter()) {
                    deoptCounter++;
                }
                return null;
            }

            @CompilerDirectives.TruffleBoundary(transferToInterpreterOnException = false)
            public void throwExceptionBoundary() {
                throw new RuntimeException();
            }
        }

        DeoptCountingExceptionOverBoundaryRootNode rootNode = new DeoptCountingExceptionOverBoundaryRootNode();
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        final int compilationThreshold = outerTarget.getOptionValue(OptimizedRuntimeOptions.SingleTierCompilationThreshold);
        for (int i = 0; i < compilationThreshold; i++) {
            outerTarget.call();
        }
        assertCompiled(outerTarget);

        final int execCount = 10;
        for (int i = 0; i < execCount; i++) {
            outerTarget.call();
        }

        final int totalExecutions = compilationThreshold + execCount;
        assertEquals("Incorrect number of catch block executions", totalExecutions, rootNode.catchCounter);

        assertEquals("Incorrect number of deopts detected!", 0, rootNode.deoptCounter);
    }

    @Test
    public void testExceptionOnTruffleBoundaryWithNoCatch() {
        class DeoptCountingExceptionOverBoundaryRootNode extends RootNode {

            protected DeoptCountingExceptionOverBoundaryRootNode() {
                super(null);
            }

            int deoptCounter = 0;

            @Override
            public Object execute(VirtualFrame frame) {
                boolean startedCompiled = CompilerDirectives.inCompiledCode();
                try {
                    throwExceptionBoundary();
                } catch (Exception e) {
                    if (startedCompiled && CompilerDirectives.inInterpreter()) {
                        deoptCounter++;
                    }
                    throw e;
                }
                return null;
            }

            @CompilerDirectives.TruffleBoundary
            public void throwExceptionBoundary() {
                throw new RuntimeException();
            }
        }

        DeoptCountingExceptionOverBoundaryRootNode rootNode = new DeoptCountingExceptionOverBoundaryRootNode();
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        final int compilationThreshold = outerTarget.getOptionValue(OptimizedRuntimeOptions.SingleTierCompilationThreshold);
        final int invalidationReprofileCount = 3;

        for (int i = 0; i < compilationThreshold; i++) {
            try {
                outerTarget.call();
                fail();
            } catch (RuntimeException e) {
                // do nothing
            }
        }
        // deoptimizes immediately due to the exception
        assertNotCompiled(outerTarget);
        assertEquals("Incorrect number of deopts detected!", 1, rootNode.deoptCounter);
        // recompile with exception branch
        for (int i = 0; i < invalidationReprofileCount; i++) {
            try {
                outerTarget.call();
                fail();
            } catch (RuntimeException e) {
                // do nothing
            }
        }
        assertCompiled(outerTarget);

        final int execCount = 10;
        for (int i = 0; i < execCount; i++) {
            try {
                outerTarget.call();
                fail();
            } catch (RuntimeException e) {
                // do nothing
            }
        }

        assertEquals("Incorrect number of deopts detected!", 1, rootNode.deoptCounter);
    }

    @Test
    public void testExceptionOnTruffleBoundaryWithNoCatchTransferFalse() {
        class DeoptCountingExceptionOverBoundaryRootNode extends RootNode {

            protected DeoptCountingExceptionOverBoundaryRootNode() {
                super(null);
            }

            int deoptCounter = 0;

            @Override
            public Object execute(VirtualFrame frame) {
                boolean startedCompiled = CompilerDirectives.inCompiledCode();
                try {
                    throwExceptionBoundary();
                } catch (Exception e) {
                    if (startedCompiled && CompilerDirectives.inInterpreter()) {
                        deoptCounter++;
                    }
                    throw e;
                }
                return null;
            }

            @CompilerDirectives.TruffleBoundary(transferToInterpreterOnException = false)
            public void throwExceptionBoundary() {
                throw new RuntimeException();
            }
        }

        DeoptCountingExceptionOverBoundaryRootNode rootNode = new DeoptCountingExceptionOverBoundaryRootNode();
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        final int compilationThreshold = outerTarget.getOptionValue(OptimizedRuntimeOptions.SingleTierCompilationThreshold);
        for (int i = 0; i < compilationThreshold; i++) {
            try {
                outerTarget.call();
                fail();
            } catch (RuntimeException e) {
                // do nothing
            }
        }
        assertCompiled(outerTarget);

        final int execCount = 10;
        for (int i = 0; i < execCount; i++) {
            try {
                outerTarget.call();
                fail();
            } catch (RuntimeException e) {
                // do nothing
            }
        }

        assertEquals("Incorrect number of deopts detected!", 0, rootNode.deoptCounter);
    }
}
