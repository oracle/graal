/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.runtime.OptimizedCallTarget;

/**
 * Specific Bytecode DSL compilation tests for continuations.
 */
public final class BytecodeDSLContinuationCompilationTest extends TestWithSynchronousCompiling {
    @Before
    @Override
    public void before() {
        Context context = setupContext();
        context.initialize(BytecodeDSLTestLanguage.ID);
    }

    private static ExceptionInterceptingInterpreter parseExceptionInterceptingNode(BytecodeParser<ExceptionInterceptingInterpreterGen.Builder> builder) {
        BytecodeRootNodes<ExceptionInterceptingInterpreter> nodes = ExceptionInterceptingInterpreterGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testTruffleExceptionInterception() {
        ExceptionInterceptingInterpreter root = parseExceptionInterceptingNode(b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(0L);
            b.endYield();
            b.emitThrowObservedTruffleException();
            b.endRoot();
        });

        testObservedFrameOnResume((OptimizedCallTarget) root.getCallTarget(), InterceptKind.TRUFFLE);
    }

    @Test
    public void testInternalExceptionInterception() {
        ExceptionInterceptingInterpreter root = parseExceptionInterceptingNode(b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(0L);
            b.endYield();
            b.emitThrowObservedInternalException();
            b.endRoot();
        });

        testObservedFrameOnResume((OptimizedCallTarget) root.getCallTarget(), InterceptKind.INTERNAL);
    }

    @Test
    public void testControlFlowExceptionInterception() {
        ExceptionInterceptingInterpreter root = parseExceptionInterceptingNode(b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(0L);
            b.endYield();
            b.emitThrowObservedControlFlowException();
            b.endRoot();
        });

        testObservedFrameOnResume((OptimizedCallTarget) root.getCallTarget(), InterceptKind.CONTROL_FLOW);
    }

    private static void testObservedFrameOnResume(OptimizedCallTarget target, InterceptKind kind) {
        ContinuationResult warmupYielded = (ContinuationResult) target.call();
        assertObservedFrame(warmupYielded, kind);

        ContinuationResult yielded = (ContinuationResult) target.call();
        OptimizedCallTarget continuationTarget = (OptimizedCallTarget) yielded.getContinuationCallTarget();
        continuationTarget.compile(true);
        assertCompiled(continuationTarget);

        assertObservedFrame(yielded, kind);
    }

    private static void assertObservedFrame(ContinuationResult yielded, InterceptKind kind) {
        switch (kind) {
            case TRUFFLE:
                try {
                    yielded.continueWith(null);
                    fail("Expected ObservedTruffleException");
                } catch (ExceptionInterceptingInterpreter.ObservedTruffleException ex) {
                    assertSame(yielded.getFrame(), ex.observedFrame);
                }
                break;
            case INTERNAL:
                try {
                    yielded.continueWith(null);
                    fail("Expected ObservedInternalException");
                } catch (ExceptionInterceptingInterpreter.ObservedInternalException ex) {
                    assertSame(yielded.getFrame(), ex.observedFrame);
                }
                break;
            case CONTROL_FLOW:
                Object result = yielded.continueWith(null);
                assertTrue(result instanceof ExceptionInterceptingInterpreter.ObservedControlFlowException);
                assertSame(yielded.getFrame(), ((ExceptionInterceptingInterpreter.ObservedControlFlowException) result).observedFrame);
                break;
            default:
                throw new AssertionError(kind);
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true)
    abstract static class ExceptionInterceptingInterpreter extends DebugBytecodeRootNode implements BytecodeRootNode {
        protected ExceptionInterceptingInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            if (ex instanceof ObservedTruffleException observed) {
                observed.observedFrame = frame;
            }
            return ex;
        }

        @Override
        public Throwable interceptInternalException(Throwable t, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            if (t instanceof ObservedInternalException observed) {
                observed.observedFrame = frame;
            }
            return t;
        }

        @Override
        public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
            if (ex instanceof ObservedControlFlowException observed) {
                observed.observedFrame = frame;
                return observed;
            }
            throw ex;
        }

        @SuppressWarnings("serial")
        static final class ObservedTruffleException extends AbstractTruffleException {
            Object observedFrame;
        }

        @SuppressWarnings("serial")
        static final class ObservedInternalException extends RuntimeException {
            Object observedFrame;
        }

        @SuppressWarnings("serial")
        static final class ObservedControlFlowException extends ControlFlowException {
            Object observedFrame;
        }

        @Operation
        static final class ThrowObservedTruffleException {
            @Specialization
            static Object perform() {
                throw new ObservedTruffleException();
            }
        }

        @Operation
        static final class ThrowObservedInternalException {
            @Specialization
            static Object perform() {
                throw new ObservedInternalException();
            }
        }

        @Operation
        static final class ThrowObservedControlFlowException {
            @Specialization
            static Object perform() {
                throw new ObservedControlFlowException();
            }
        }
    }

    private enum InterceptKind {
        TRUFFLE,
        INTERNAL,
        CONTROL_FLOW
    }
}
