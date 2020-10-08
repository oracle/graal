/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TruffleExceptionTest extends AbstractPolyglotTest {

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

    static Context createContext(VerifyingHandler handler) {
        return Context.newBuilder().option(String.format("log.%s.level", handler.loggerName), "FINE").logHandler(handler).build();
    }

    static CallTarget createAST(Class<?> testClass, TruffleLanguage<ProxyLanguage.LanguageContext> lang,
                    Function<Node, Object> exceptionObjectFactroy, boolean customStackTraceElementGuestObject) {
        ThrowNode throwNode = new ThrowNode(exceptionObjectFactroy);
        TryCatchNode tryCatch = new TryCatchNode(new BlockNode(testClass, BlockNode.Kind.TRY, throwNode),
                        new BlockNode(testClass, BlockNode.Kind.CATCH),
                        new BlockNode(testClass, BlockNode.Kind.FINALLY));
        return Truffle.getRuntime().createCallTarget(new TestRootNode(lang, "test", customStackTraceElementGuestObject ? "unnamed" : null, tryCatch));
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    private static final class TestRootNode extends RootNode {

        private final String name;
        private final String ownerName;
        private final StackTraceElementGuestObject customStackTraceElementGuestObject;
        @Child StatementNode body;

        TestRootNode(TruffleLanguage<?> language, String name, String ownerName, StatementNode body) {
            super(language);
            this.name = name;
            this.ownerName = ownerName;
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

    private abstract static class StatementNode extends Node {
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

    private static class ThrowNode extends StatementNode {

        private final Object exceptionObject;
        @Child InteropLibrary interop;

        ThrowNode(Function<Node, Object> exceptionObjectFactroy) {
            this.exceptionObject = exceptionObjectFactroy.apply(this);
            interop = InteropLibrary.getFactory().create(exceptionObject);
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            try {
                throw interop.throwException(exceptionObject);
            } catch (UnsupportedMessageException um) {
                throw CompilerDirectives.shouldNotReachHere(um);
            }
        }
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class TruffleExceptionImpl extends AbstractTruffleException {

        TruffleExceptionImpl(String message, Node location) {
            super(message, location);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
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
