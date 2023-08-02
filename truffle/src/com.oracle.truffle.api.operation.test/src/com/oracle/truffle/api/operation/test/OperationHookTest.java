package com.oracle.truffle.api.operation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.test.OperationNodeWithHooks.MyException;
import com.oracle.truffle.api.operation.test.OperationNodeWithHooks.ThrowStackOverflow;

public class OperationHookTest {

    public static OperationNodeWithHooks parseNode(OperationParser<OperationNodeWithHooksGen.Builder> builder) {
        OperationNodes<OperationNodeWithHooks> nodes = OperationNodeWithHooksGen.create(OperationConfig.DEFAULT, builder);
        return nodes.getNodes().get(0);
    }

    @Test
    public void testSimplePrologEpilog() {
        OperationNodeWithHooks root = parseNode(b -> {
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
        OperationNodeWithHooks root = parseNode(b -> {
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
        OperationNodeWithHooks root = parseNode(b -> {
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
        OperationNodeWithHooks root = parseNode(b -> {
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
            assertNotEquals(-1, ex.bci);
        }
    }

    @Test
    public void testInterceptTruffleExceptionFromInternal() {
        // The stack overflow should be intercepted as an internal error and then the converted
        // exception should be intercepted as a Truffle exception.
        OperationNodeWithHooks root = parseNode(b -> {
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
            assertNotEquals(-1, ex.bci);
        }
    }

    @Test
    public void testInterceptTruffleExceptionPropagated() {
        // The bci should be overridden when it propagates to the root from child.
        OperationNodeWithHooks child = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginThrow();
            b.emitLoadConstant(123);
            b.endThrow();
            b.endReturn();
            b.endRoot();
        });
        child.setRefs(new Object[2]);

        OperationNodeWithHooks root = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            // insert dummy instructions so the throwing bci is different from the child's.
            b.emitLoadArgument(0);
            b.emitLoadArgument(0);
            b.emitLoadArgument(0);
            b.emitLoadArgument(0);
            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(child);
            b.endInvoke();
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        root.setRefs(new Object[2]);

        int childThrowBci = -1;
        try {
            child.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            childThrowBci = ex.bci;
            assertNotEquals(-1, childThrowBci);
        }

        int rootThrowBci = -1;
        try {
            root.getCallTarget().call(42);
            Assert.fail("call should have thrown an exception");
        } catch (MyException ex) {
            rootThrowBci = ex.bci;
            assertNotEquals(-1, rootThrowBci);
        }

        assertNotEquals(childThrowBci, rootThrowBci);
    }
}

@GenerateOperations(languageClass = TestOperationsLanguage.class)
abstract class OperationNodeWithHooks extends RootNode implements OperationRootNode {
    // Used to validate whether hooks get called.
    private Object[] refs;

    protected OperationNodeWithHooks(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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
    public Throwable interceptInternalException(Throwable t, int bci) {
        return new MyException(t.getMessage());
    }

    @Override
    public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, int bci) {
        if (ex instanceof MyException myEx) {
            myEx.bci = bci;
        }
        return ex;
    }

    public static final class MyException extends AbstractTruffleException {
        private static final long serialVersionUID = 1L;
        public final Object result;
        public int bci = -1;

        MyException(Object result) {
            super();
            this.result = result;
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

    @Operation
    public static final class Invoke {
        @Specialization
        public static Object perform(VirtualFrame frame, OperationNodeWithHooks callee) {
            return callee.getCallTarget().call(frame.getArguments());
        }
    }
}