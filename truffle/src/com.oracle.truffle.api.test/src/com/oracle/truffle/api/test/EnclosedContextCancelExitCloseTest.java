/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate;
import static com.oracle.truffle.api.TruffleLanguage.Env;
import static com.oracle.truffle.api.TruffleLanguage.Registration;
import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.InteropMapObject;
import com.oracle.truffle.api.test.common.TestUtils;

public class EnclosedContextCancelExitCloseTest {

    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static final class DummyNode extends BaseNode {
        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static final class WhileLoopNode extends BaseNode {

        final SourceSection sourceSection;

        @Node.Child private LoopNode loop;

        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot loopIndexSlot;
        @CompilationFinal com.oracle.truffle.api.frame.FrameSlot loopResultSlot;

        WhileLoopNode(SourceSection sourceSection, Object loopCount, BaseNode child) {
            this.sourceSection = sourceSection;
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, child));
        }

        com.oracle.truffle.api.frame.FrameSlot getLoopIndex() {
            if (loopIndexSlot == null) {
                transferToInterpreterAndInvalidate();
                loopIndexSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopIndex" + getLoopDepth());
            }
            return loopIndexSlot;
        }

        com.oracle.truffle.api.frame.FrameSlot getResult() {
            if (loopResultSlot == null) {
                transferToInterpreterAndInvalidate();
                loopResultSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopResult" + getLoopDepth());
            }
            return loopResultSlot;
        }

        private int getLoopDepth() {
            Node node = getParent();
            int count = 0;
            while (node != null) {
                if (node instanceof WhileLoopNode) {
                    count++;
                }
                node = node.getParent();
            }
            return count;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            signalLoopStarted();
            frame.setObject(getResult(), false);
            frame.setInt(getLoopIndex(), 0);
            loop.execute(frame);
            try {
                return frame.getObject(loopResultSlot);
            } catch (FrameSlotTypeException e) {
                transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        @TruffleBoundary
        private static void signalLoopStarted() {
            EnclosedTestLanguageContext innerLanguageContext = TestAPIAccessor.engineAccess().getCurrentContext(EnclosedTestLanguage.class);
            Object remoteCountdown = innerLanguageContext.scope.get("remoteCountdown");
            if (remoteCountdown != null) {
                try {
                    InteropLibrary.getUncached().execute(remoteCountdown);
                } catch (UnsupportedMessageException | ArityException | UnsupportedTypeException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        final class LoopConditionNode extends BaseNode implements RepeatingNode {

            @Node.Child private volatile BaseNode child;

            private final int loopCount;
            private final boolean infinite;

            LoopConditionNode(Object loopCount, BaseNode child) {
                this.child = child;
                boolean inf = false;
                if (loopCount instanceof Double) {
                    if (((Double) loopCount).isInfinite()) {
                        inf = true;
                    }
                    this.loopCount = ((Double) loopCount).intValue();
                } else if (loopCount instanceof Integer) {
                    this.loopCount = (int) loopCount;
                } else {
                    this.loopCount = 0;
                }
                this.infinite = inf;

            }

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                int i;
                try {
                    i = frame.getInt(loopIndexSlot);
                } catch (FrameSlotTypeException e) {
                    transferToInterpreter();
                    throw new AssertionError(e);
                }
                if (infinite || i < loopCount) {
                    Object resultValue = execute(frame);
                    frame.setInt(loopIndexSlot, i + 1);
                    frame.setObject(loopResultSlot, resultValue);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }

            @Override
            public SourceSection getSourceSection() {
                return WhileLoopNode.this.sourceSection;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class DummyMemberNames implements TruffleObject {

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        long getArraySize() {
            return 1;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object readArrayElement(long index) {
            return "0";
        }
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class CallTargetExecutable implements TruffleObject {
        private final CallTarget callTarget;
        private final Class<? extends TruffleLanguage<?>> languageClass;
        private final SourceSection sourceSection;

        CallTargetExecutable(CallTarget callTarget, Class<? extends TruffleLanguage<?>> language, SourceSection sourceSection) {
            this.callTarget = callTarget;
            this.languageClass = language;
            this.sourceSection = sourceSection;
        }

        @ExportMessage
        final boolean hasMembers() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new DummyMemberNames();
        }

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        final boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        public SourceSection getSourceLocation() {
            return sourceSection;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return languageClass;
        }

        @ExportMessage
        abstract static class Execute {

            @Specialization
            protected static Object doIndirect(CallTargetExecutable function, Object[] arguments,
                            @Cached IndirectCallNode callNode) {
                return callNode.call(function.getCallTarget(), arguments);
            }
        }

        public CallTarget getCallTarget() {
            return callTarget;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(boolean allowSideEffects) {
            return callTarget.toString();
        }
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class RemoteCountdown implements TruffleObject {
        CountDownLatch infiniteLoopLatch = new CountDownLatch(1);

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        final Object execute(Object[] arguments) {
            infiniteLoopLatch.countDown();
            return true;
        }

        void await() {
            try {
                infiniteLoopLatch.await();
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
        }
    }

    static class EnclosedTestLanguageContext {
        private final Env env;
        private final InteropMapObject scope = new InteropMapObject(EnclosedTestLanguage.class, "EnclosedTestLanguage scope");

        EnclosedTestLanguageContext(Env env) {
            this.env = env;
        }
    }

    @Registration
    static class EnclosedTestLanguage extends TruffleLanguage<EnclosedTestLanguageContext> {
        static final String ID = TestUtils.getDefaultLanguageId(EnclosedTestLanguage.class);

        @Override
        protected EnclosedTestLanguageContext createContext(Env env) {
            return new EnclosedTestLanguageContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            if ("DoNothingSource".contentEquals(source.getCharacters())) {
                return RootNode.createConstantNode(true).getCallTarget();
            }
            if ("ExitSource".contentEquals(source.getCharacters())) {
                return new RootNode(this) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        EnclosedTestLanguageContext innerLanguageContext = TestAPIAccessor.engineAccess().getCurrentContext(EnclosedTestLanguage.class);
                        innerLanguageContext.env.getContext().closeExited(this, 42);
                        return true;
                    }

                }.getCallTarget();
            }
            SourceSection section = source.createSection(1);
            CallTarget loopExecuteTarget = new RootNode(this) {
                final SourceSection sourceSection = section;

                @Node.Child private volatile BaseNode child = new WhileLoopNode(sourceSection, Double.POSITIVE_INFINITY, new DummyNode());

                @Override
                public Object execute(VirtualFrame frame) {
                    return child.execute(frame);
                }

                @Override
                public SourceSection getSourceSection() {
                    return sourceSection;
                }

            }.getCallTarget();
            return new RootNode(this) {
                final SourceSection sourceSection = section;
                final CallTargetExecutable callTargetExecutable = new CallTargetExecutable(loopExecuteTarget, EnclosedTestLanguage.class, section);

                @Override
                public Object execute(VirtualFrame frame) {
                    return callTargetExecutable;
                }

                @Override
                public SourceSection getSourceSection() {
                    return sourceSection;
                }

            }.getCallTarget();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected Object getScope(EnclosedTestLanguageContext context) {
            return context.scope;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class OtherContextDiedException extends AbstractTruffleException {

        private static final long serialVersionUID = 2978960949054801996L;

        OtherContextDiedException(String name) {
            super(name);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
        }
    }

    abstract static class AbstractOuterTestLanguage extends AbstractExecutableTestLanguage {

        @SuppressWarnings("unused")
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {

        }

        abstract Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch);

        abstract void checkException(Exception e);

        @SuppressWarnings("try")
        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Thread t = null;
            try {
                TruffleContext.Builder builder = env.newContextBuilder();
                adjustInnerContextBuilder(builder);
                RemoteCountdown remoteCountdown = new RemoteCountdown();
                try (TruffleContext innerContext = builder.build()) {
                    EnclosedTestLanguageContext innerLanguageContext;
                    // Trigger inner language initialization
                    assertTrue((Boolean) innerContext.evalPublic(node, Source.newBuilder(EnclosedTestLanguage.ID, "DoNothingSource", "InnerSourceDoNothing").build()));
                    // Capture inner language context
                    Object prev = innerContext.enter(node);
                    try {
                        innerLanguageContext = TestAPIAccessor.engineAccess().getCurrentContext(EnclosedTestLanguage.class);
                        innerLanguageContext.scope.put("remoteCountdown", remoteCountdown);
                    } finally {
                        innerContext.leave(node, prev);
                    }

                    // Cancel/exit/close
                    t = cancelExitOrClose(env, node, innerContext, remoteCountdown);
                    /*
                     * Execute infinite loop in case of cancel/exit, or just fail during enter if
                     * the context is already closed.
                     */
                    try {
                        Object executable = innerContext.evalPublic(node, Source.newBuilder(EnclosedTestLanguage.ID, "", "InnerSourceInfLoop").build());
                        InteropLibrary.getUncached().execute(executable);
                        fail();
                    } catch (UnsupportedMessageException | ArityException | UnsupportedTypeException e) {
                        throw new AssertionError(e);
                    } catch (Exception e) {
                        checkException(e);
                    }
                }
            } finally {
                try {
                    if (t != null) {
                        t.join();
                    }
                } catch (InterruptedException ie) {
                    throw new AssertionError(ie);
                }
            }
            return "";
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Registration
    static class CancelInnerContextInternalErrorOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Thread t = env.createThread(new Runnable() {
                @Override
                public void run() {
                    innerContextRunningLatch.await();
                    innerContext.closeCancelled(null, "cancel infinite loop");
                }
            });
            t.start();
            return t;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Context cancel exception of inner context leaks outside to a non-cancelled context!", e.getMessage());
        }
    }

    @Test
    public void testCancelInnerContextInternalError() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CancelInnerContextInternalErrorOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class CancelInnerContextOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {
            builder.onCancelled(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException("Inner context cancelled");
                }
            });
        }

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Thread t = env.createThread(new Runnable() {
                @Override
                public void run() {
                    innerContextRunningLatch.await();
                    innerContext.closeCancelled(null, "cancel infinite loop");
                }
            });
            t.start();
            return t;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof OtherContextDiedException);
            assertEquals("Inner context cancelled", e.getMessage());
        }
    }

    @Test
    public void testCancelInnerContext() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CancelInnerContextOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class CancelInnerContextUpfrontInternalErrorOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            innerContext.closeCancelled(null, "cancel upfront");
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Context cancel exception of inner context leaks outside to a non-cancelled context!", e.getMessage());
        }
    }

    @Test
    public void testCancelInnerContextUpfrontInternalError() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CancelInnerContextUpfrontInternalErrorOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class CancelInnerContextUpfrontOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {
            builder.onCancelled(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException("Inner context cancelled");
                }
            });
        }

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            innerContext.closeCancelled(null, "cancel upfront");
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof OtherContextDiedException);
            assertEquals("Inner context cancelled", e.getMessage());
        }
    }

    @Test
    public void testCancelInnerContextUpfront() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CancelInnerContextUpfrontOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class ExitInnerContextInternalErrorOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Thread t = env.createThread(new Runnable() {
                @Override
                public void run() {
                    innerContextRunningLatch.await();
                    Object prev = innerContext.enter(null);
                    try {
                        innerContext.closeExited(null, 42);
                    } finally {
                        innerContext.leave(null, prev);
                    }
                }
            });
            t.start();
            return t;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Context exit exception of inner context leaks outside to a non-exited context!", e.getMessage());
        }
    }

    @Test
    public void testExitInnerContextInternalError() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, ExitInnerContextInternalErrorOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class ExitInnerContextOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {
            builder.onExited(new Consumer<Integer>() {
                @Override
                public void accept(Integer exitCode) {
                    throw new OtherContextDiedException("Inner context exited with exit code " + exitCode);
                }
            });
        }

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Thread t = env.createThread(new Runnable() {
                @Override
                public void run() {
                    innerContextRunningLatch.await();
                    Object prev = innerContext.enter(null);
                    try {
                        innerContext.closeExited(null, 42);
                    } finally {
                        innerContext.leave(null, prev);
                    }
                }
            });
            t.start();
            return t;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof OtherContextDiedException);
            assertEquals("Inner context exited with exit code 42", e.getMessage());
        }
    }

    @Test
    public void testExitInnerContext() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, ExitInnerContextOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class ExitInnerContextUpfrontInternalErrorOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Object prev = innerContext.enter(node);
            try {
                innerContext.closeExited(node, 42);
            } catch (ThreadDeath e) {
                assertEquals("Exit was called with exit code 42.", e.getMessage());
            } finally {
                innerContext.leave(node, prev);
            }
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Context exit exception of inner context leaks outside to a non-exited context!", e.getMessage());
        }
    }

    @Test
    public void testExitInnerContextUpfrontInternalError() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, ExitInnerContextUpfrontInternalErrorOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class ExitInnerContextUpfrontOuterTestLanguage extends ExitInnerContextUpfrontInternalErrorOuterTestLanguage {

        @Override
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {
            builder.onExited(new Consumer<Integer>() {
                @Override
                public void accept(Integer exitCode) {
                    throw new OtherContextDiedException("Inner context exited with exit code " + exitCode);
                }
            });
        }

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            Object prev = innerContext.enter(node);
            try {
                innerContext.closeExited(node, 42);
            } catch (ThreadDeath e) {
                assertEquals("Exit was called with exit code 42.", e.getMessage());
            } finally {
                innerContext.leave(node, prev);
            }
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof OtherContextDiedException);
            assertEquals("Inner context exited with exit code 42", e.getMessage());
        }
    }

    @Test
    public void testExitInnerContextUpfront() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, ExitInnerContextUpfrontOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class CloseInnerContextUpfrontInternalErrorOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            innerContext.close();
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Context close exception of inner context leaks outside to a non-closed context!", e.getMessage());
        }
    }

    @Test
    public void testCloseInnerContextUpfrontInternalError() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CloseInnerContextUpfrontInternalErrorOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class CloseInnerContextUpfrontOuterTestLanguage extends AbstractOuterTestLanguage {

        @Override
        void adjustInnerContextBuilder(TruffleContext.Builder builder) {
            builder.onClosed(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException("Inner context closed");
                }
            });
        }

        @Override
        Thread cancelExitOrClose(Env env, Node node, TruffleContext innerContext, RemoteCountdown innerContextRunningLatch) {
            innerContext.close();
            return null;
        }

        @Override
        void checkException(Exception e) {
            assertTrue(e instanceof OtherContextDiedException);
            assertEquals("Inner context closed", e.getMessage());
        }
    }

    @Test
    public void testCloseInnerContextUpfront() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, CloseInnerContextUpfrontOuterTestLanguage.class, "");
        }
    }

    @Registration
    static class EnclosingTestLanguage extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            InteropLibrary.getUncached().execute(contextArguments[0]);
            fail();
            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    interface EnclosedOuterContextTestInterface {
        Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService);
    }

    private static void testEnclosedOuterContext(EnclosedOuterContextTestInterface testRunnable, Consumer<Throwable> exceptionCheck) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context.Builder builder = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).allowHostAccess(HostAccess.ALL);
        Future<?> future = null;
        try (Context context1 = builder.build();
                        Context context2 = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).allowHostAccess(HostAccess.ALL).build()) {
            RemoteCountdown remoteCountdown = new RemoteCountdown();
            context1.getBindings(EnclosedTestLanguage.ID).putMember("remoteCountdown", remoteCountdown);
            Value val = context1.eval(EnclosedTestLanguage.ID, "");

            future = testRunnable.run(context1, remoteCountdown, executorService);

            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            exceptionCheck.accept(pe.asHostException());
        } finally {
            if (future != null) {
                future.get();
            }
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testCancelEnclosedOuterContext() throws InterruptedException, ExecutionException {
        testEnclosedOuterContext(new EnclosedOuterContextTestInterface() {
            @Override
            public Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService) {
                return executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        enclosedContextRunningLatch.await();
                        enclosedContext.close(true);
                    }
                });
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                assertTrue(throwable instanceof PolyglotException);
                assertEquals("Context execution was cancelled.", throwable.getMessage());
            }
        });
    }

    @Test
    public void testCancelEnclosedOuterContextUpfront() throws InterruptedException, ExecutionException {
        testEnclosedOuterContext(new EnclosedOuterContextTestInterface() {
            @Override
            public Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService) {
                enclosedContext.close(true);
                return null;
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                assertTrue(throwable instanceof PolyglotException);
                assertEquals("Context execution was cancelled.", throwable.getMessage());
            }
        });
    }

    @Test
    public void testExitEnclosedOuterContext() throws InterruptedException, ExecutionException {
        testEnclosedOuterContext(new EnclosedOuterContextTestInterface() {
            @Override
            public Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService) {
                return executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        enclosedContextRunningLatch.await();
                        try {
                            enclosedContext.eval(EnclosedTestLanguage.ID, "ExitSource");
                        } catch (PolyglotException pe) {
                            assertTrue(pe.isExit());
                            assertEquals(pe.getExitStatus(), 42);
                        }

                    }
                });
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                assertTrue(throwable instanceof PolyglotException);
                assertEquals("Exit was called with exit code 42.", throwable.getMessage());
            }
        });
    }

    @Test
    public void testExitEnclosedOuterContextUpfront() throws InterruptedException, ExecutionException {
        testEnclosedOuterContext(new EnclosedOuterContextTestInterface() {
            @Override
            public Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService) {
                try {
                    enclosedContext.eval(EnclosedTestLanguage.ID, "ExitSource");
                } catch (PolyglotException pe) {
                    assertTrue(pe.isExit());
                    assertEquals(pe.getExitStatus(), 42);
                }
                return null;
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                assertTrue(throwable instanceof PolyglotException);
                assertEquals("Exit was called with exit code 42.", throwable.getMessage());
            }
        });
    }

    @Test
    public void testCloseEnclosedOuterContextUpfront() throws InterruptedException, ExecutionException {
        testEnclosedOuterContext(new EnclosedOuterContextTestInterface() {
            @Override
            @SuppressWarnings("try")
            public Future<?> run(Context enclosedContext, RemoteCountdown enclosedContextRunningLatch, ExecutorService executorService) {
                enclosedContext.close();
                return null;
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                assertTrue(throwable instanceof IllegalStateException);
                assertEquals("The Context is already closed.", throwable.getMessage());
            }
        });
    }

}
