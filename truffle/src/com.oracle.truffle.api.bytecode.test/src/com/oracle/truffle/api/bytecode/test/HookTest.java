/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeNodeWithHooks.MyException;
import com.oracle.truffle.api.bytecode.test.BytecodeNodeWithHooks.ThrowStackOverflow;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;

public class HookTest {

    public static BytecodeNodeWithHooks parseNode(BytecodeParser<BytecodeNodeWithHooksGen.Builder> builder) {
        BytecodeRootNodes<BytecodeNodeWithHooks> nodes = BytecodeNodeWithHooksGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testSimplePrologEpilog() {
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.emitReadArgument();
            b.endReturn();
            b.endRoot();
        });
        Object[] refs = new Object[2];
        root.setRefs(refs);

        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(42, refs[0]);
        assertEquals(42, refs[1]);
    }

    @Test
    public void testThrowPrologEpilog() {
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginThrow();
            b.emitLoadConstant(123);
            b.endThrow();
            b.endReturn();
            b.endRoot();
        });
        Object[] refs = new Object[2];
        root.setRefs(refs);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            assertEquals(123, ex.result);
        }

        assertEquals(42, refs[0]);
        assertEquals(123, refs[1]);
    }

    @Test
    public void testInterceptStackOverflow() {
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.emitThrowStackOverflow();
            b.endReturn();
            b.endRoot();
        });
        Object[] refs = new Object[2];
        root.setRefs(refs);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            assertEquals(ThrowStackOverflow.MESSAGE, ex.result);
        }

        assertEquals(42, refs[0]);
        assertEquals(ThrowStackOverflow.MESSAGE, refs[1]);
    }

    @Test
    public void testInterceptTruffleExceptionSimple() {
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginThrow();
            b.emitLoadConstant(123);
            b.endThrow();
            b.endReturn();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            BytecodeLocation location = ex.getBytecodeLocation();
            assertNotNull(location);
            assertTrue(location.findInstruction().getName().contains("Throw"));
        }
    }

    @Test
    public void testInterceptTruffleExceptionFromInternal() {
        // The stack overflow should be intercepted as an internal error and then the converted
        // exception should be intercepted as a Truffle exception.
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.emitThrowStackOverflow();
            b.endReturn();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            BytecodeLocation location = ex.getBytecodeLocation();
            assertNotNull(location);
            assertTrue(location.findInstruction().getName().contains("ThrowStackOverflow"));
        }
    }

    @Test
    public void testInterceptTruffleExceptionPropagated() {
        // The location should be overridden when it propagates to the root from child.
        BytecodeNodeWithHooks child = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginThrow();
            b.emitLoadConstant(123);
            b.endThrow();
            b.endReturn();
            b.endRoot();
        });
        child.setRefs(new Object[2]);

        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(child);
            b.endInvoke();
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        BytecodeLocation childThrowLocation = null;
        try {
            child.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            childThrowLocation = ex.getBytecodeLocation();
            assertNotNull(childThrowLocation);
            assertTrue(childThrowLocation.findInstruction().getName().contains("Throw"));
        }

        BytecodeLocation rootThrowLocation = null;
        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            rootThrowLocation = ex.getBytecodeLocation();
            assertNotNull(rootThrowLocation);
            assertTrue(rootThrowLocation.findInstruction().getName().contains("Invoke"));
        }

        assertNotEquals(childThrowLocation, rootThrowLocation);
    }

    @Test
    public void testControlFlowEarlyReturn() {
        // The early return value should be returned.
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            b.beginThrowEarlyReturn();
            b.emitLoadConstant(42);
            b.endThrowEarlyReturn();
            b.beginReturn();
            b.emitLoadConstant(123);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        assertEquals(42, root.getCallTarget().call(42));
    }

    @Test
    public void testControlFlowUnhandled() {
        // The control flow exception should go unhandled.
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            b.emitThrowUnhandledControlFlowException();
            b.beginReturn();
            b.emitLoadConstant(123);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (ControlFlowException ex) {
            // pass
        }
    }

    @Test
    public void testControlFlowInternalError() {
        // The control flow exception should be intercepted by the internal handler and then the
        // Truffle handler.
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            b.emitThrowControlFlowInternalError();
            b.beginReturn();
            b.emitLoadConstant(123);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            assertEquals("internal error", ex.result);
            BytecodeLocation location = ex.getBytecodeLocation();
            assertNotNull(location);
            assertTrue(location.findInstruction().getName().contains("ThrowControlFlowInternalError"));
        }
    }

    @Test
    public void testControlFlowTruffleException() {
        // The control flow exception should be intercepted by the Truffle handler.
        BytecodeNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            b.beginThrowControlFlowTruffleException();
            b.emitLoadConstant(42);
            b.endThrowControlFlowTruffleException();
            b.beginReturn();
            b.emitLoadConstant(123);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            assertEquals(42, ex.result);
            BytecodeLocation location = ex.getBytecodeLocation();
            assertNotNull(location);
            assertTrue(location.findInstruction().getName().contains("ThrowControlFlowTruffleException"));
        }
    }

    @Test
    public void testInterceptsNothing() {
        BytecodeNodeInterceptsNothing root = BytecodeNodeInterceptsNothingGen.create(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot(null);
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.emitThrowUnhandledControlFlowException();
            b.emitThrowStackOverflow();
            b.endIfThenElse();
            b.endRoot();
        }).getNode(0);

        try {
            root.getCallTarget().call(true);
            Assert.fail("call should have thrown an exception");
        } catch (ControlFlowException ex) {
            // expected
        }

        try {
            root.getCallTarget().call(false);
            Assert.fail("call should have thrown an exception");
        } catch (StackOverflowError ex) {
            // expected
        }
    }

    @Test
    public void testInterceptsCF() {
        BytecodeNodeInterceptsCF root = BytecodeNodeInterceptsCFGen.create(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot(null);
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.emitThrowUnhandledControlFlowException();
            b.emitThrowStackOverflow();
            b.endIfThenElse();
            b.endRoot();
        }).getNode(0);

        assertEquals(42, root.getCallTarget().call(true));

        try {
            root.getCallTarget().call(false);
            Assert.fail("call should have thrown an exception");
        } catch (StackOverflowError ex) {
            // expected
        }
    }

    @Test
    public void testInterceptsInternal() {
        BytecodeNodeInterceptsInternal root = BytecodeNodeInterceptsInternalGen.create(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot(null);
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.emitThrowUnhandledControlFlowException();
            b.emitThrowStackOverflow();
            b.endIfThenElse();
            b.endRoot();
        }).getNode(0);

        try {
            root.getCallTarget().call(true);
            Assert.fail("call should have thrown an exception");
        } catch (ControlFlowException ex) {
            // expected
        }

        try {
            root.getCallTarget().call(false);
            Assert.fail("call should have thrown an exception");
        } catch (RuntimeException ex) {
            assertTrue(ex.getCause() instanceof StackOverflowError);
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BytecodeNodeWithHooks extends RootNode implements BytecodeRootNode {
    // Used to validate whether hooks get called.
    private Object[] refs;

    protected BytecodeNodeWithHooks(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    void setRefs(Object[] refs) {
        assert refs.length == 2;
        this.refs = refs;
    }

    @Override
    public void executeProlog(VirtualFrame frame) {
        refs[0] = frame.getArguments()[0];
    }

    @Override
    public void executeEpilog(VirtualFrame frame, Object returnValue, Throwable throwable) {
        if (throwable != null) {
            if (throwable instanceof MyException myEx) {
                refs[1] = myEx.result;
            }
        } else {
            refs[1] = returnValue;
        }
    }

    @Override
    public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
        if (ex instanceof EarlyReturnException er) {
            // Return a result
            return er.result;
        } else if (ex instanceof ControlFlowInternalError err) {
            // Rethrow an internal error
            throw err.error;
        } else if (ex instanceof ControlFlowTruffleException tex) {
            // Rethrow a Truffle error
            throw tex.ex;
        } else {
            // Rethrow a control flow exception
            throw ex;
        }
    }

    @Override
    public Throwable interceptInternalException(Throwable t, BytecodeNode bytecodeNode, int bci) {
        return new MyException(t.getMessage());
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        if (ex instanceof MyException myEx) {
            // These can be used to construct a BytecodeLocation if necessary.
            myEx.bytecodeNode = bytecodeNode;
            myEx.bci = bci;
        }
        return ex;
    }

    @SuppressWarnings({"serial"})
    public static final class MyException extends AbstractTruffleException {
        private static final long serialVersionUID = 1L;
        public final Object result;
        public BytecodeNode bytecodeNode = null;
        public int bci = -1;

        MyException(Object result) {
            super();
            this.result = result;
        }

        public BytecodeLocation getBytecodeLocation() {
            if (bytecodeNode == null) {
                return null;
            }
            return bytecodeNode.getBytecodeLocation(bci);
        }
    }

    @Operation
    public static final class ReadArgument {
        @Specialization
        public static Object perform(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    @Operation
    public static final class Throw {
        @Specialization
        public static Object perform(Object result) {
            throw new MyException(result);
        }
    }

    @Operation
    public static final class ThrowStackOverflow {
        public static final String MESSAGE = "unbounded recursion";

        @Specialization
        public static Object perform() {
            throw new StackOverflowError(MESSAGE);
        }
    }

    @SuppressWarnings("serial")
    public static final class EarlyReturnException extends ControlFlowException {
        public final Object result;

        EarlyReturnException(Object result) {
            this.result = result;
        }
    }

    @Operation
    public static final class ThrowEarlyReturn {
        @Specialization
        public static Object perform(Object result) {
            throw new EarlyReturnException(result);
        }
    }

    @Operation
    public static final class ThrowUnhandledControlFlowException {
        @Specialization
        public static Object perform() {
            throw new ControlFlowException();
        }
    }

    @SuppressWarnings("serial")
    public static final class ControlFlowInternalError extends ControlFlowException {
        public final Throwable error;

        ControlFlowInternalError(Throwable error) {
            this.error = error;
        }
    }

    @Operation
    public static final class ThrowControlFlowInternalError {
        @Specialization
        public static Object perform() {
            throw new ControlFlowInternalError(new RuntimeException("internal error"));
        }
    }

    @SuppressWarnings("serial")
    public static final class ControlFlowTruffleException extends ControlFlowException {
        public final AbstractTruffleException ex;

        ControlFlowTruffleException(AbstractTruffleException ex) {
            this.ex = ex;
        }
    }

    @Operation
    public static final class ThrowControlFlowTruffleException {
        @Specialization
        public static Object perform(Object value) {
            throw new ControlFlowTruffleException(new MyException(value));
        }
    }

    @Operation
    public static final class Invoke {
        @Specialization
        public static Object perform(VirtualFrame frame, BytecodeNodeWithHooks callee) {
            return callee.getCallTarget().call(frame.getArguments());
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BytecodeNodeInterceptsNothing extends RootNode implements BytecodeRootNode {

    protected BytecodeNodeInterceptsNothing(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class ThrowUnhandledControlFlowException {
        @Specialization
        public static Object perform() {
            throw new ControlFlowException();
        }
    }

    @Operation
    public static final class ThrowStackOverflow {
        public static final String MESSAGE = "unbounded recursion";

        @Specialization
        public static Object perform() {
            throw new StackOverflowError(MESSAGE);
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BytecodeNodeInterceptsCF extends RootNode implements BytecodeRootNode {

    protected BytecodeNodeInterceptsCF(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Override
    public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
        return 42;
    }

    @Operation
    public static final class ThrowUnhandledControlFlowException {
        @Specialization
        public static Object perform() {
            throw new ControlFlowException();
        }
    }

    @Operation
    public static final class ThrowStackOverflow {
        public static final String MESSAGE = "unbounded recursion";

        @Specialization
        public static Object perform() {
            throw new StackOverflowError(MESSAGE);
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BytecodeNodeInterceptsInternal extends RootNode implements BytecodeRootNode {

    protected BytecodeNodeInterceptsInternal(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Override
    public Throwable interceptInternalException(Throwable t, BytecodeNode bytecodeNode, int bci) {
        return new RuntimeException(t);
    }

    @Operation
    public static final class ThrowUnhandledControlFlowException {
        @Specialization
        public static Object perform() {
            throw new ControlFlowException();
        }
    }

    @Operation
    public static final class ThrowStackOverflow {
        public static final String MESSAGE = "unbounded recursion";

        @Specialization
        public static Object perform() {
            throw new StackOverflowError(MESSAGE);
        }
    }
}
