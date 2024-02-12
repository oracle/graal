/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.RootNodeTest.verifyStackTraceElementGuestObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class TruffleExceptionTest extends AbstractPolyglotTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private VerifyingHandler verifyingHandler;

    @Before
    public void setUp() {
        verifyingHandler = new VerifyingHandler(AbstractTruffleException.class);
    }

    @Test
    public void testTruffleException() {
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(AbstractTruffleException.class, languageInstance, (n) -> new TruffleExceptionImpl("Test exception", n), false);
            }
        });
        verifyingHandler.expect(BlockNode.Kind.TRY, BlockNode.Kind.CATCH, BlockNode.Kind.FINALLY);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testTruffleExceptionCustomGuestObject() {
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(AbstractTruffleException.class, languageInstance, (n) -> new TruffleExceptionImpl("Test exception", n), true);
            }
        });
        verifyingHandler.expect(BlockNode.Kind.TRY, BlockNode.Kind.CATCH, BlockNode.Kind.FINALLY);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testPolyglotStackTrace() {
        testStackTraceImpl(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                ThrowNode throwNode = new ThrowNode((n) -> {
                    return new TruffleExceptionImpl("Test exception", n);
                });
                return new TestRootNode(languageInstance, "test", null, throwNode).getCallTarget();
            }
        },
                        "<proxyLanguage> test",
                        "(org.graalvm.polyglot/)?org.graalvm.polyglot.Context.eval");
    }

    @Test
    public void testPolyglotStackTrace2() {
        testStackTraceImpl(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                ThrowNode throwNode = new ThrowNode((n) -> {
                    return new TruffleExceptionImpl("Test exception", n);
                });
                CallTarget throwTarget = new TestRootNode(languageInstance, "test-throw", null, throwNode).getCallTarget();
                CallTarget innerInvokeTarget = new TestRootNode(languageInstance, "test-call-inner", null, new InvokeNode(throwTarget)).getCallTarget();
                CallTarget outerInvokeTarget = new TestRootNode(languageInstance, "test-call-outer", null, new InvokeNode(innerInvokeTarget)).getCallTarget();
                return outerInvokeTarget;
            }
        },
                        "<proxyLanguage> test-throw",
                        "<proxyLanguage> test-call-inner",
                        "<proxyLanguage> test-call-outer",
                        "(org.graalvm.polyglot/)?org.graalvm.polyglot.Context.eval");
    }

    @Test
    public void testPolyglotStackTraceInternalFrame() {
        testStackTraceImpl(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                ThrowNode throwNode = new ThrowNode((n) -> {
                    return new TruffleExceptionImpl("Test exception", n);
                });
                CallTarget throwTarget = new TestRootNode(languageInstance, "test-throw-internal", null, true, throwNode).getCallTarget();
                CallTarget innerInvokeTarget = new TestRootNode(languageInstance, "test-call-inner", null, new InvokeNode(throwTarget)).getCallTarget();
                CallTarget internalInvokeTarget = new TestRootNode(languageInstance, "test-call-internal", null, true, new InvokeNode(innerInvokeTarget)).getCallTarget();
                CallTarget outerInvokeTarget = new TestRootNode(languageInstance, "test-call-outer", null, new InvokeNode(internalInvokeTarget)).getCallTarget();
                return outerInvokeTarget;
            }
        },
                        "<proxyLanguage> test-call-inner",
                        "<proxyLanguage> test-call-outer",
                        "(org.graalvm.polyglot/)?org.graalvm.polyglot.Context.eval");
    }

    @Test
    public void testPolyglotStackTraceExplicitFillIn() {
        testStackTraceImpl(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                ThrowNode throwNode = new ThrowNode((n) -> {
                    TruffleExceptionImpl e = new TruffleExceptionImpl("Test exception", n);
                    TruffleStackTrace.fillIn(e);
                    return e;
                });
                return new TestRootNode(languageInstance, "test", null, throwNode).getCallTarget();
            }
        },
                        "<proxyLanguage> test",
                        "(org.graalvm.polyglot/)?org.graalvm.polyglot.Context.eval");
    }

    @Test
    public void testPolyglotStackTraceInternalError() {
        testStackTraceImpl(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                ThrowNode throwNode = new ThrowNode(new InternalExceptionFactory());
                return new TestRootNode(languageInstance, "test", null, throwNode).getCallTarget();
            }
        },
                        Pattern.quote("com.oracle.truffle.api.test.TruffleExceptionTest$InternalExceptionFactory.apply"),
                        Pattern.quote("com.oracle.truffle.api.test.TruffleExceptionTest$ThrowNode.executeVoid"),
                        Pattern.quote("com.oracle.truffle.api.test.TruffleExceptionTest$TestRootNode.execute"),
                        "<proxyLanguage> test",
                        "(org.graalvm.polyglot/)?org.graalvm.polyglot.Context.eval");
    }

    @Test
    public void testExceptionFromCreateContext() {
        String message = "Failed to create";
        ExceptionType type = ExceptionType.EXIT;
        assertFails(() -> setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env env) {
                throw new TruffleExceptionImpl(message, null, type, null);
            }
        }), PolyglotException.class, (pe) -> {
            Assert.assertEquals(message, pe.getMessage());
            Assert.assertTrue(pe.isExit());
            Assert.assertFalse(pe.isInternalError());
            Assert.assertEquals(0, pe.getExitStatus());
            Assert.assertNull(pe.getGuestObject());
        });
    }

    private void testStackTraceImpl(ProxyLanguage proxy, String... patterns) {
        setupEnv(Context.create(), proxy);
        assertFails(() -> context.eval(ProxyLanguage.ID, "Test"), PolyglotException.class, (pe) -> {
            verifyStackTrace(pe, patterns);
        });
    }

    static void verifyStackTrace(PolyglotException pe, String... patterns) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter out = new PrintWriter(buffer)) {
            pe.printStackTrace(out);
        }
        String[] lines = Arrays.stream(buffer.toString().split(System.lineSeparator())).map((l) -> l.trim()).filter((l) -> l.startsWith("at ")).map((l) -> {
            int end = l.lastIndexOf('(');
            if (end < 0) {
                end = l.length();
            }
            return l.substring(3, end);
        }).toArray((len) -> new String[len]);
        Assert.assertTrue("Not enough lines " + Arrays.toString(lines), patterns.length <= lines.length);
        for (int i = 0; i < lines.length && i < patterns.length; i++) {
            String line = lines[i];
            Pattern pattern = Pattern.compile(patterns[i]);
            Assert.assertTrue("Expected " + patterns[i] + " but got " + line, pattern.matcher(line).matches());
        }
    }

    @Test
    public void testExceptionFromPolyglotExceptionConstructor() {
        // The IS_EXCEPTION cannot be tested, it is called by the InteropLibrary.Asserts before we
        // get to the creation of PolyglotExceptionImpl.
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.RUNTIME_ERROR, false);
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.RUNTIME_ERROR, true, TruffleExceptionImpl.MessageKind.GET_EXCEPTION_TYPE);
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.EXIT, true, TruffleExceptionImpl.MessageKind.GET_EXCEPTION_EXIT_STATUS);
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.PARSE_ERROR, true, TruffleExceptionImpl.MessageKind.IS_EXCEPTION_INCOMPLETE_SOURCE);
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.RUNTIME_ERROR, true, TruffleExceptionImpl.MessageKind.HAS_SOURCE_LOCATION);
        testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType.RUNTIME_ERROR, true, TruffleExceptionImpl.MessageKind.GET_SOURCE_LOCATION);
    }

    private void testExceptionFromPolyglotExceptionConstructorImpl(ExceptionType type, boolean hasInternal, TruffleExceptionImpl.MessageKind... failOn) {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                InjectException injectException = new InjectException(failOn);
                ThrowNode throwNode = new ThrowNode((n) -> new TruffleExceptionImpl("test", n, type, injectException));
                return new TestRootNode(languageInstance, "test", "unnamed", throwNode).getCallTarget();
            }
        });
        assertFails(() -> context.eval(ProxyLanguage.ID, "Test"), PolyglotException.class, (pe) -> {
            Assert.assertFalse(pe.isInternalError());
            if (hasInternal) {
                Assert.assertEquals(1, pe.getSuppressed().length);
                Assert.assertTrue(((PolyglotException) pe.getSuppressed()[0]).isInternalError());
            } else {
                Assert.assertEquals(0, pe.getSuppressed().length);
            }
        });
    }

    static Context createContext(VerifyingHandler handler) {
        return Context.newBuilder().option(String.format("log.%s.level", handler.loggerName), "FINE").logHandler(handler).build();
    }

    static CallTarget createAST(Class<?> testClass, TruffleLanguage<ProxyLanguage.LanguageContext> lang,
                    ExceptionFactory exceptionObjectFactroy, boolean customStackTraceElementGuestObject) {
        ThrowNode throwNode = new ThrowNode(exceptionObjectFactroy);
        TryCatchNode tryCatch = new TryCatchNode(new BlockNode(testClass, BlockNode.Kind.TRY, throwNode),
                        new BlockNode(testClass, BlockNode.Kind.CATCH),
                        new BlockNode(testClass, BlockNode.Kind.FINALLY));
        return new TestRootNode(lang, "test", customStackTraceElementGuestObject ? "unnamed" : null, tryCatch).getCallTarget();
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    static final class TestRootNode extends RootNode {

        private final String name;
        private final String ownerName;
        private final boolean internal;
        private final StackTraceElementGuestObject customStackTraceElementGuestObject;
        @Child StatementNode body;

        TestRootNode(TruffleLanguage<?> language, String name, String ownerName, StatementNode body) {
            this(language, name, ownerName, false, body);
        }

        TestRootNode(TruffleLanguage<?> language, String name, String ownerName, boolean internal, StatementNode body) {
            super(language);
            this.name = name;
            this.ownerName = ownerName;
            this.internal = internal;
            this.body = body;
            this.customStackTraceElementGuestObject = ownerName != null ? new StackTraceElementGuestObject(name, ownerName) : null;
        }

        @Override
        public String getQualifiedName() {
            return ownerName != null ? ownerName + '.' + name : name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            body.executeVoid(frame);
            return true;
        }

        @Override
        protected Object translateStackTraceElement(TruffleStackTraceElement element) {
            if (customStackTraceElementGuestObject != null) {
                return customStackTraceElementGuestObject;
            } else {
                return super.translateStackTraceElement(element);
            }
        }

        @Override
        public boolean isInternal() {
            return internal;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StackTraceElementGuestObject implements TruffleObject {

        private final String name;
        private final Object owner;

        StackTraceElementGuestObject(String name, String ownerName) {
            this.name = name;
            this.owner = new OwnerMetaObject(ownerName);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasExecutableName() {
            return true;
        }

        @ExportMessage
        Object getExecutableName() {
            return name;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasDeclaringMetaObject() {
            return true;
        }

        @ExportMessage
        Object getDeclaringMetaObject() {
            return owner;
        }

        @ExportLibrary(InteropLibrary.class)
        static final class OwnerMetaObject implements TruffleObject {

            private final String name;

            OwnerMetaObject(String name) {
                this.name = name;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isMetaObject() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings({"static-method", "unused"})
            boolean isMetaInstance(Object object) {
                return false;
            }

            @ExportMessage
            Object getMetaQualifiedName() {
                return name;
            }

            @ExportMessage
            Object getMetaSimpleName() {
                return name;
            }
        }
    }

    abstract static class StatementNode extends Node {
        abstract void executeVoid(VirtualFrame frame);
    }

    static class BlockNode extends StatementNode {

        enum Kind {
            TRY,
            CATCH,
            FINALLY
        }

        @Children private StatementNode[] children;

        BlockNode(Class<?> testClass, Kind kind, StatementNode... children) {
            this.children = new StatementNode[children.length + 1];
            this.children[0] = new LogNode(testClass, kind.name());
            System.arraycopy(children, 0, this.children, 1, children.length);
        }

        @Override
        @ExplodeLoop
        void executeVoid(VirtualFrame frame) {
            for (StatementNode child : children) {
                child.executeVoid(frame);
            }
        }
    }

    private static class LogNode extends StatementNode {

        private final TruffleLogger log;
        private final String message;

        LogNode(Class<?> testClass, String message) {
            log = TruffleLogger.getLogger(ProxyLanguage.ID, testClass.getName());
            this.message = message;
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            log.fine(message);
        }
    }

    private static final class TryCatchNode extends StatementNode {

        @Child private BlockNode block;
        @Child private BlockNode catchBlock;
        @Child private BlockNode finallyBlock;
        @Child private InteropLibrary exceptions = InteropLibrary.getFactory().createDispatched(5);
        private final BranchProfile exceptionProfile = BranchProfile.create();

        TryCatchNode(BlockNode block, BlockNode catchBlock, BlockNode finallyBlock) {
            this.block = block;
            this.catchBlock = catchBlock;
            this.finallyBlock = finallyBlock;
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            Throwable exception = null;
            try {
                block.executeVoid(frame);
            } catch (Throwable ex) {
                exception = executeCatchBlock(frame, ex, catchBlock);
            }
            // Java finally blocks that execute nodes are not allowed for
            // compilation as code in finally blocks is duplicated
            // by the Java bytecode compiler. This can lead to
            // exponential code growth in worst cases.
            if (finallyBlock != null) {
                finallyBlock.executeVoid(frame);
            }
            if (exception != null) {
                if (exception instanceof ControlFlowException) {
                    throw (ControlFlowException) exception;
                }
                try {
                    throw exceptions.throwException(exception);
                } catch (UnsupportedMessageException ie) {
                    throw CompilerDirectives.shouldNotReachHere(ie);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private <T extends Throwable> Throwable executeCatchBlock(VirtualFrame frame, Throwable ex, BlockNode catchBlk) throws T {
            if (ex instanceof ControlFlowException) {
                // run finally blocks for control flow
                return ex;
            }
            exceptionProfile.enter();
            if (exceptions.isException(ex)) {
                assertTruffleExceptionProperties(ex);
                if (catchBlk != null) {
                    try {
                        catchBlk.executeVoid(frame);
                        return null;
                    } catch (Throwable catchEx) {
                        return executeCatchBlock(frame, catchEx, null);
                    }
                } else {
                    // run finally blocks for any interop exception
                    return ex;
                }
            } else {
                // do not run finally blocks for internal errors or unwinds
                throw (T) ex;
            }
        }

        @TruffleBoundary
        private void assertTruffleExceptionProperties(Throwable ex) {
            try {
                Assert.assertEquals(ExceptionType.RUNTIME_ERROR, exceptions.getExceptionType(ex));
                AbstractPolyglotTest.assertFails(() -> {
                    exceptions.getExceptionExitStatus(ex);
                    return null;
                }, UnsupportedMessageException.class);
                if (ex.getMessage() != null) {
                    Assert.assertTrue(exceptions.hasExceptionMessage(ex));
                    Assert.assertEquals(ex.getMessage(), exceptions.getExceptionMessage(ex));
                } else {
                    Assert.assertFalse(exceptions.hasExceptionMessage(ex));
                }
                assertStackTrace(ex);
            } catch (InteropException ie) {
                CompilerDirectives.shouldNotReachHere(ie);
            }
        }

        private void assertStackTrace(Throwable t) throws UnsupportedMessageException, InvalidArrayIndexException {
            List<TruffleStackTraceElement> stack = TruffleStackTrace.getStackTrace(t);
            Object stackGuestObject = exceptions.getExceptionStackTrace(t);
            Assert.assertTrue(exceptions.hasArrayElements(stackGuestObject));
            Assert.assertEquals(stack.size(), exceptions.getArraySize(stackGuestObject));
            for (int i = 0; i < stack.size(); i++) {
                Object stackTraceElementObject = exceptions.readArrayElement(stackGuestObject, i);
                verifyStackTraceElementGuestObject(stackTraceElementObject);
                Assert.assertTrue(exceptions.hasExecutableName(stackTraceElementObject));
                String executableName = exceptions.asString(exceptions.getExecutableName(stackTraceElementObject));
                Assert.assertEquals(stack.get(i).getTarget().getRootNode().getName(), executableName);
                String qualifiedName;
                if (exceptions.hasDeclaringMetaObject(stackTraceElementObject)) {
                    qualifiedName = exceptions.asString(exceptions.getMetaQualifiedName(exceptions.getDeclaringMetaObject(stackTraceElementObject))) + '.' + executableName;
                } else {
                    qualifiedName = executableName;
                }
                Assert.assertEquals(stack.get(i).getTarget().getRootNode().getQualifiedName(), qualifiedName);
            }
        }
    }

    interface ExceptionFactory {
        Object apply(Node t);
    }

    static final class InternalExceptionFactory implements ExceptionFactory {
        @Override
        public Object apply(Node t) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException();
        }
    }

    static class ThrowNode extends StatementNode {

        private final ExceptionFactory exceptionObjectFactory;
        @Child InteropLibrary interop;

        ThrowNode(ExceptionFactory exceptionObjectFactroy) {
            this.exceptionObjectFactory = exceptionObjectFactroy;
            this.interop = InteropLibrary.getFactory().createDispatched(1);
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            try {
                throw interop.throwException(exceptionObjectFactory.apply(this));
            } catch (UnsupportedMessageException um) {
                throw CompilerDirectives.shouldNotReachHere(um);
            }
        }
    }

    static class InvokeNode extends StatementNode {

        private final DirectCallNode call;

        InvokeNode(CallTarget target) {
            this.call = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            this.call.call();
        }
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class TruffleExceptionImpl extends AbstractTruffleException {

        enum MessageKind {
            IS_EXCEPTION,
            THROW_EXCEPTION,
            GET_EXCEPTION_TYPE,
            GET_EXCEPTION_EXIT_STATUS,
            IS_EXCEPTION_INCOMPLETE_SOURCE,
            HAS_SOURCE_LOCATION,
            GET_SOURCE_LOCATION
        }

        private final ExceptionType exceptionType;
        private final Consumer<MessageKind> exceptionInjection;

        TruffleExceptionImpl(String message, Node location) {
            this(message, location, ExceptionType.RUNTIME_ERROR, null);
        }

        TruffleExceptionImpl(
                        String message,
                        Node location,
                        ExceptionType exceptionType,
                        Consumer<MessageKind> exceptionInjection) {
            super(message, location);
            this.exceptionType = exceptionType;
            this.exceptionInjection = exceptionInjection;
        }

        @ExportMessage
        boolean isException() {
            injectException(MessageKind.IS_EXCEPTION);
            return true;
        }

        @ExportMessage
        RuntimeException throwException() {
            injectException(MessageKind.THROW_EXCEPTION);
            throw this;
        }

        @ExportMessage
        ExceptionType getExceptionType() {
            injectException(MessageKind.GET_EXCEPTION_TYPE);
            return exceptionType;
        }

        @ExportMessage
        int getExceptionExitStatus() throws UnsupportedMessageException {
            injectException(MessageKind.GET_EXCEPTION_EXIT_STATUS);
            if (exceptionType != ExceptionType.EXIT) {
                throw UnsupportedMessageException.create();
            } else {
                return 0;
            }
        }

        @ExportMessage
        boolean isExceptionIncompleteSource() throws UnsupportedMessageException {
            injectException(MessageKind.IS_EXCEPTION_INCOMPLETE_SOURCE);
            if (exceptionType != ExceptionType.PARSE_ERROR) {
                throw UnsupportedMessageException.create();
            } else {
                return true;
            }
        }

        @ExportMessage
        boolean hasSourceLocation() {
            injectException(MessageKind.HAS_SOURCE_LOCATION);
            Node location = getLocation();
            return location != null && location.getEncapsulatingSourceSection() != null;
        }

        @ExportMessage(name = "getSourceLocation")
        SourceSection getSource() throws UnsupportedMessageException {
            injectException(MessageKind.GET_SOURCE_LOCATION);
            Node location = getLocation();
            SourceSection section = location == null ? null : location.getEncapsulatingSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            } else {
                return section;
            }
        }

        @TruffleBoundary
        private void injectException(MessageKind messageKind) {
            if (exceptionInjection != null) {
                exceptionInjection.accept(messageKind);
            }
        }
    }

    private static final class InjectException implements Consumer<TruffleExceptionImpl.MessageKind> {

        private final Set<TruffleExceptionImpl.MessageKind> messages;

        private InjectException(TruffleExceptionImpl.MessageKind... messages) {
            this.messages = EnumSet.noneOf(TruffleExceptionImpl.MessageKind.class);
            Collections.addAll(this.messages, messages);
        }

        @Override
        public void accept(TruffleExceptionImpl.MessageKind kind) {
            if (messages.contains(kind)) {
                throw new RuntimeException();
            }
        }
    }

    static final class VerifyingHandler extends Handler {

        final String loggerName;
        private Queue<String> expected = new ArrayDeque<>();

        VerifyingHandler(Class<?> testClass) {
            loggerName = String.format("%s.%s", ProxyLanguage.ID, testClass.getName());
        }

        void expect(BlockNode.Kind... kinds) {
            Arrays.stream(kinds).map(BlockNode.Kind::name).forEach(expected::add);
        }

        @Override
        public void publish(LogRecord lr) {
            if (loggerName.equals(lr.getLoggerName())) {
                String head = expected.remove();
                Assert.assertEquals(head, lr.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            Assert.assertTrue("All expected events must be consumed. Remaining events: " + String.join(", ", expected), expected.isEmpty());
        }
    }

}
