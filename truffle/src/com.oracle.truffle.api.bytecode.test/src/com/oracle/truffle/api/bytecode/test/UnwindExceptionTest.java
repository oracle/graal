/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;

/** Tests language-selected exceptions that unwind through guest finally blocks. */
public class UnwindExceptionTest {

    @Test
    public void testUnwindWithoutInterceptorsAndReservedThreadDeath() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindWithoutInterceptorsRootNode root = UnwindWithoutInterceptorsRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginTryFinally(b::emitRecordCleanup);
                b.emitThrowArgument();
                b.endTryFinally();
                b.endRoot();
            }).getNode(0);
            if (cached) {
                root.getBytecodeNode().setUncachedThreshold(0);
            }
            Throwable exception = new SelectedCheckedException();
            assertThrownSame(exception, () -> root.getCallTarget().call(exception));
            assertEquals(1, root.cleanupCount);

            ThreadDeath reserved = new ThreadDeath();
            assertThrownSame(reserved, () -> root.getCallTarget().call(reserved));
            assertEquals(1, root.cleanupCount);
        }
    }

    @Test
    public void testConfiguredThrowableKindsAndSubtypeMatch() {
        for (boolean cached : new boolean[]{false, true}) {
            assertRunsFinally(cached, new SelectedControlFlowExceptionSubclass());
            assertRunsFinally(cached, new SelectedRuntimeException());
            assertRunsFinally(cached, new SelectedGuestException());
            assertRunsFinally(cached, new SelectedCheckedException());
        }
    }

    private static void assertRunsFinally(boolean cached, Throwable exception) {
        UnwindExceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, "finally"));
            emitThrowArgument(b, 1);
            b.endTryFinally();
            b.endRoot();
        }, cached);
        List<String> log = new ArrayList<>();

        assertThrownSame(exception, () -> root.getCallTarget().call(log, exception));
        assertEquals(List.of("finally"), log);
        assertNoInterception(root);
        assertEquals(cached ? BytecodeTier.CACHED : BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());

        List<ExceptionHandler> handlers = root.getBytecodeNode().getExceptionHandlers();
        assertEquals(2, handlers.size());
        assertEquals(ExceptionHandler.HandlerKind.CUSTOM, handlers.get(0).getKind());
        assertEquals(ExceptionHandler.HandlerKind.EPILOG, handlers.get(1).getKind());
    }

    @Test
    public void testUnselectedControlFlowExceptionKeepsExistingBehavior() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parse(b -> {
                b.beginRoot();
                b.beginTryFinally(() -> emitAppend(b, "finally"));
                emitThrowArgument(b, 1);
                b.endTryFinally();
                b.endRoot();
            }, cached);
            List<String> log = new ArrayList<>();
            UnselectedControlFlowException exception = new UnselectedControlFlowException();

            assertThrownSame(exception, () -> root.getCallTarget().call(log, exception));
            assertEquals(List.of(), log);
            assertEquals(1, root.controlFlowInterceptCount);
            assertEquals(0, root.truffleInterceptCount);
            assertEquals(0, root.internalInterceptCount);
            assertEquals(0, root.exceptionalEpilogCount);
        }
    }

    @Test
    public void testSelectedExceptionsFromInterceptorsSkipSubsequentHooks() {
        for (boolean cached : new boolean[]{false, true}) {
            assertSelectedControlFlowInterceptorReplacement(cached, new SelectedRuntimeException());
            assertSelectedControlFlowInterceptorReplacement(cached, new SelectedGuestException());

            UnwindExceptionTestRootNode root = parse(b -> {
                b.beginRoot();
                emitThrowArgument(b, 0);
                b.endRoot();
            }, cached);
            UnselectedInternalException original = new UnselectedInternalException();
            SelectedGuestException replacement = new SelectedGuestException();
            root.internalReplacement = replacement;

            assertThrownSame(replacement, () -> root.getCallTarget().call(original));
            assertEquals(0, root.controlFlowInterceptCount);
            assertEquals(0, root.truffleInterceptCount);
            assertEquals(1, root.internalInterceptCount);
            assertEquals(0, root.exceptionalEpilogCount);
        }
    }

    private static void assertSelectedControlFlowInterceptorReplacement(boolean cached, Throwable replacement) {
        UnwindExceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            emitThrowArgument(b, 0);
            b.endRoot();
        }, cached);
        UnselectedControlFlowException original = new UnselectedControlFlowException();
        root.controlFlowReplacement = replacement;

        assertThrownSame(replacement, () -> root.getCallTarget().call(original));
        assertEquals(1, root.controlFlowInterceptCount);
        assertEquals(0, root.truffleInterceptCount);
        assertEquals(0, root.internalInterceptCount);
        assertEquals(0, root.exceptionalEpilogCount);
    }

    @Test
    public void testNestedFinallyBypassesCatchAndOtherwise() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parse(b -> {
                b.beginRoot();
                b.beginTryFinally(() -> emitAppend(b, "outer finally"));
                b.beginTryFinally(() -> emitAppend(b, "inner finally"));
                b.beginTryCatchOtherwise(() -> emitAppend(b, "otherwise"));
                b.beginTryCatch();
                emitThrowArgument(b, 1);
                emitAppend(b, "catch");
                b.endTryCatch();
                emitAppend(b, "catch otherwise");
                b.endTryCatchOtherwise();
                b.endTryFinally();
                b.endTryFinally();
                b.endRoot();
            }, cached);
            List<String> log = new ArrayList<>();
            SelectedRuntimeException exception = new SelectedRuntimeException();

            assertThrownSame(exception, () -> root.getCallTarget().call(log, exception));
            assertEquals(List.of("inner finally", "outer finally"), log);
            assertNoInterception(root);
        }
    }

    @Test
    public void testYieldingFinallyPreservesPendingExceptionIdentity() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parseYieldingFinally(cached, CleanupCompletion.NORMAL);
            List<String> log = new ArrayList<>();
            SelectedControlFlowException exception = new SelectedControlFlowExceptionSubclass();

            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call(log, exception, null);
            assertEquals("yielded", continuation.getResult());
            assertEquals(List.of("inner before yield"), log);

            assertThrownSame(exception, () -> continuation.continueWith("resumed"));
            assertEquals(List.of("inner before yield", "inner after yield", "outer finally"), log);
            assertNoInterception(root);
        }
    }

    @Test
    public void testYieldingFinallyReplacementTransferSupersedesPendingException() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parseYieldingFinally(cached, CleanupCompletion.THROW);
            List<String> log = new ArrayList<>();
            SelectedControlFlowException original = new SelectedControlFlowExceptionSubclass();
            SelectedControlFlowException replacement = new SelectedControlFlowExceptionSubclass();

            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call(log, original, replacement);
            assertThrownSame(replacement, () -> continuation.continueWith("resumed"));
            assertEquals(List.of("inner before yield", "inner after yield", "outer finally"), log);
            assertNoInterception(root);
        }
    }

    @Test
    public void testYieldingFinallyGuestExceptionSupersedesPendingException() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parseYieldingFinally(cached, CleanupCompletion.THROW);
            List<String> log = new ArrayList<>();
            SelectedControlFlowException original = new SelectedControlFlowExceptionSubclass();
            SelectedGuestException replacement = new SelectedGuestException();

            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call(log, original, replacement);
            assertThrownSame(replacement, () -> continuation.continueWith("resumed"));
            assertEquals(List.of("inner before yield", "inner after yield", "outer finally"), log);
            assertNoInterception(root);
        }
    }

    @Test
    public void testUnconfiguredGuestExceptionAfterYieldReturnsToOuterCatch() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parse(b -> {
                b.beginRoot();
                b.beginTryCatch();
                b.beginTryFinally(() -> emitAppend(b, "outer finally"));
                b.beginTryFinally(() -> {
                    b.beginBlock();
                    emitAppend(b, "inner before yield");
                    b.beginYield();
                    b.emitLoadConstant("yielded");
                    b.endYield();
                    emitAppend(b, "inner after yield");
                    emitThrowArgument(b, 2);
                    b.endBlock();
                });
                emitThrowArgument(b, 1);
                b.endTryFinally();
                b.endTryFinally();
                b.beginReturn();
                b.emitLoadException();
                b.endReturn();
                b.endTryCatch();
                b.endRoot();
            }, cached);
            List<String> log = new ArrayList<>();
            SelectedControlFlowException original = new SelectedControlFlowExceptionSubclass();
            UnconfiguredGuestException replacement = new UnconfiguredGuestException();

            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call(log, original, replacement);
            assertEquals("yielded", continuation.getResult());
            assertNoInterception(root);
            assertSame(replacement, continuation.continueWith("resumed"));
            assertEquals(List.of("inner before yield", "inner after yield", "outer finally"), log);
            assertEquals(0, root.controlFlowInterceptCount);
            // The replacement is intercepted at its throw and the outer finally's rethrow.
            assertEquals(2, root.truffleInterceptCount);
            assertEquals(0, root.internalInterceptCount);
            assertEquals(0, root.exceptionalEpilogCount);
        }
    }

    @Test
    public void testYieldingFinallyReturnSupersedesPendingException() {
        for (boolean cached : new boolean[]{false, true}) {
            UnwindExceptionTestRootNode root = parseYieldingFinally(cached, CleanupCompletion.RETURN);
            List<String> log = new ArrayList<>();
            SelectedControlFlowException original = new SelectedControlFlowExceptionSubclass();

            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call(log, original, "replacement result");
            assertEquals("replacement result", continuation.continueWith("resumed"));
            assertEquals(List.of("inner before yield", "inner after yield", "outer finally"), log);
            assertNoInterception(root);
        }
    }

    private enum CleanupCompletion {
        NORMAL,
        THROW,
        RETURN
    }

    private static UnwindExceptionTestRootNode parseYieldingFinally(boolean cached, CleanupCompletion completion) {
        return parse(b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, "outer finally"));
            b.beginTryFinally(() -> {
                b.beginBlock();
                emitAppend(b, "inner before yield");
                b.beginYield();
                b.emitLoadConstant("yielded");
                b.endYield();
                emitAppend(b, "inner after yield");
                switch (completion) {
                    case NORMAL:
                        break;
                    case THROW:
                        emitThrowArgument(b, 2);
                        break;
                    case RETURN:
                        b.beginReturn();
                        b.emitLoadArgument(2);
                        b.endReturn();
                        break;
                }
                b.endBlock();
            });
            emitThrowArgument(b, 1);
            b.endTryFinally();
            b.endTryFinally();
            b.endRoot();
        }, cached);
    }

    private static UnwindExceptionTestRootNode parse(BytecodeParser<UnwindExceptionTestRootNodeGen.Builder> parser, boolean cached) {
        BytecodeRootNodes<UnwindExceptionTestRootNode> nodes = UnwindExceptionTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, parser);
        UnwindExceptionTestRootNode root = nodes.getNode(0);
        if (cached) {
            root.getBytecodeNode().setUncachedThreshold(0);
        }
        return root;
    }

    private static void emitAppend(UnwindExceptionTestRootNodeGen.Builder b, String value) {
        b.beginAppend();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppend();
    }

    private static void emitThrowArgument(UnwindExceptionTestRootNodeGen.Builder b, int argument) {
        b.beginThrowThrowable();
        b.emitLoadArgument(argument);
        b.endThrowThrowable();
    }

    private static void assertNoInterception(UnwindExceptionTestRootNode root) {
        assertEquals(0, root.controlFlowInterceptCount);
        assertEquals(0, root.truffleInterceptCount);
        assertEquals(0, root.internalInterceptCount);
        assertEquals(0, root.exceptionalEpilogCount);
    }

    private static void assertThrownSame(Throwable expected, ThrowingCall call) {
        try {
            call.call();
            Assert.fail("expected exception");
        } catch (Throwable actual) {
            assertSame(expected, actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void call() throws Throwable;
    }

    @SuppressWarnings("serial")
    static class SelectedControlFlowException extends ControlFlowException {
    }

    @SuppressWarnings("serial")
    static final class SelectedControlFlowExceptionSubclass extends SelectedControlFlowException {
    }

    @SuppressWarnings("serial")
    static final class UnselectedControlFlowException extends ControlFlowException {
    }

    @SuppressWarnings("serial")
    static final class SelectedRuntimeException extends RuntimeException {
    }

    @SuppressWarnings("serial")
    static final class UnselectedInternalException extends RuntimeException {
    }

    @SuppressWarnings("serial")
    static final class SelectedGuestException extends AbstractTruffleException {
    }

    @SuppressWarnings("serial")
    static final class UnconfiguredGuestException extends AbstractTruffleException {
    }

    @SuppressWarnings("serial")
    static final class SelectedCheckedException extends Exception {
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, unwindExceptions = Throwable.class)
abstract class UnwindWithoutInterceptorsRootNode extends RootNode implements BytecodeRootNode {

    int cleanupCount;

    @Operation
    static final class ThrowArgument {
        @Specialization
        public static void perform(VirtualFrame frame) {
            UnwindExceptionTestRootNode.ThrowThrowable.perform((Throwable) frame.getArguments()[0]);
        }
    }

    protected UnwindWithoutInterceptorsRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    static final class RecordCleanup {
        @Specialization
        public static void perform(@Bind UnwindWithoutInterceptorsRootNode root) {
            root.cleanupCount++;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                enableYield = true, //
                enableUncachedInterpreter = true, //
                unwindExceptions = {UnwindExceptionTest.SelectedControlFlowException.class, UnwindExceptionTest.SelectedRuntimeException.class,
                                UnwindExceptionTest.SelectedGuestException.class, UnwindExceptionTest.SelectedCheckedException.class})
abstract class UnwindExceptionTestRootNode extends RootNode implements BytecodeRootNode {

    int controlFlowInterceptCount;
    int truffleInterceptCount;
    int internalInterceptCount;
    int exceptionalEpilogCount;
    Throwable controlFlowReplacement;
    Throwable internalReplacement;

    protected UnwindExceptionTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Override
    public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
        controlFlowInterceptCount++;
        if (controlFlowReplacement != null) {
            throw controlFlowReplacement;
        }
        throw ex;
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        truffleInterceptCount++;
        return ex;
    }

    @Override
    public Throwable interceptInternalException(Throwable t, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        internalInterceptCount++;
        return internalReplacement != null ? internalReplacement : t;
    }

    @Operation
    static final class Append {
        @Specialization
        @SuppressWarnings("unchecked")
        public static void perform(Object log, String value) {
            ((List<String>) log).add(value);
        }
    }

    @Operation
    static final class ThrowThrowable {
        @Specialization
        public static void perform(Throwable throwable) {
            throw sneakyThrow(throwable);
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws T {
            // Operations cannot declare checked exceptions, but selected unwind types may be checked.
            throw (T) throwable;
        }
    }

    @EpilogExceptional
    static final class CountExceptionalEpilog {
        @Specialization
        public static void perform(@SuppressWarnings("unused") AbstractTruffleException exception, @Bind UnwindExceptionTestRootNode root) {
            root.exceptionalEpilogCount++;
        }
    }
}
