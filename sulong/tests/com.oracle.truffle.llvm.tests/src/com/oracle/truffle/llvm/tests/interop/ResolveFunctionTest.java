package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class ResolveFunctionTest extends InteropTestBase {

    private static Object testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("createResolveFunction.c");
    }


    public static class fortytwoFunctionNode extends SulongTestNode {
        public fortytwoFunctionNode() {
            super(testLibrary, "test_native_fortytwo_function");
        }
    }

    @Test
    public void testfortytwoFunction(@Inject(fortytwoFunctionNode.class) CallTarget function) {
        Object ret = function.call();
        if (LLVMManagedPointer.isInstance(ret)) {
            LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
            String name = retFunction.getLLVMFunction().getName();
            Assert.assertEquals("fortytwo", name);
            DirectCallNode call = DirectCallNode.create(retFunction.getFunctionCode().getLLVMIRFunctionSlowPath());
            Assert.assertEquals("42", call.call());
        }
        throw new AssertionError();
    }

    private Object fortytwoFunction;
    private Object maxFunction;

    public class ResolveFunctionNode extends SulongTestNode {
        public ResolveFunctionNode() {
            super(testLibrary, "test_resolve_function");
            try {
                fortytwoFunction = InteropLibrary.getFactory().getUncached().readMember(testLibrary, "fortytwo");
                maxFunction = InteropLibrary.getFactory().getUncached().readMember(testLibrary, "max");
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    @Test
    public void testFortytwoResolveFunction(@Inject(ResolveFunctionNode.class) CallTarget function) {
        Object ret = function.call(fortytwoFunction);
        if (LLVMManagedPointer.isInstance(ret)) {
            LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
            DirectCallNode call = DirectCallNode.create(retFunction.getFunctionCode().getLLVMIRFunctionSlowPath());
            //CallTarget call = retFunction.getFunctionCode().getLLVMIRFunctionSlowPath();
            //Assert.assertEquals("42", IndirectCallNode.create().call(call));
            Assert.assertEquals("42", call.call());
        }
        throw new AssertionError();
    }

    @Test
    public void testMaxResolveFunction(@Inject(ResolveFunctionNode.class) CallTarget function) {
        Object ret = function.call(maxFunction);
        if (LLVMManagedPointer.isInstance(ret)) {
            LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
            DirectCallNode call = DirectCallNode.create(retFunction.getFunctionCode().getLLVMIRFunctionSlowPath());
            Object arguments = new int[]{41, 42};
            Assert.assertEquals("42", call.call(arguments));
        }
        throw new AssertionError();
    }
}
