/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
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
    private static Object fortyTwoFunction;
    private static Object maxFunction;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("createResolveFunction.c");
        try {
            fortyTwoFunction = InteropLibrary.getFactory().getUncached().readMember(testLibrary, "fortytwo");
            maxFunction = InteropLibrary.getFactory().getUncached().readMember(testLibrary, "max");
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    public static class FortyTwoFunctionNode extends SulongTestNode {
        public FortyTwoFunctionNode() {
            super(testLibrary, "test_native_fortytwo_function");
        }
    }

    @Test
    public void testFunctionFortyTwo(@Inject(FortyTwoFunctionNode.class) CallTarget function) throws InteropException {
        Object ret = function.call();
        Assert.assertTrue(LLVMManagedPointer.isInstance(ret));
        LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
        String name = retFunction.getLLVMFunction().getName();
        Assert.assertEquals("fortytwo", name);
        Assert.assertEquals(42, InteropLibrary.getUncached().execute(retFunction));
    }

    public class ResolveFunctionNode extends SulongTestNode {
        public ResolveFunctionNode() {
            super(testLibrary, "test_resolve_function");
        }
    }

    @Test
    public void testResolveFunctionFortytwo(@Inject(ResolveFunctionNode.class) CallTarget function) throws InteropException {
        Object ret = function.call(fortyTwoFunction);
        Assert.assertTrue(LLVMManagedPointer.isInstance(ret));
        LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
        Assert.assertEquals(42, InteropLibrary.getUncached().execute(retFunction));
    }

    @Test
    public void testResolveNativeFunctionFortytwo(@Inject(ResolveFunctionNode.class) CallTarget function) throws InteropException {
        InteropLibrary.getUncached().toNative(fortyTwoFunction);
        long pointer = InteropLibrary.getUncached().asPointer(fortyTwoFunction);
        Object ret = function.call(pointer);
        Assert.assertTrue(LLVMManagedPointer.isInstance(ret));
        LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
        Assert.assertEquals(42, InteropLibrary.getUncached().execute(retFunction));
    }

    @Test
    public void testResolveNativeFunctionMax(@Inject(ResolveFunctionNode.class) CallTarget function) throws InteropException {
        InteropLibrary.getUncached().toNative(maxFunction);
        long pointer = InteropLibrary.getUncached().asPointer(maxFunction);
        Object ret = function.call(pointer);
        Assert.assertTrue(LLVMManagedPointer.isInstance(ret));
        LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
        Assert.assertEquals(42, InteropLibrary.getUncached().execute(retFunction, 1, 42));
    }

    @Test
    public void testResolveFunctionMax(@Inject(ResolveFunctionNode.class) CallTarget function) throws InteropException {
        Object ret = function.call(maxFunction);
        Assert.assertTrue(LLVMManagedPointer.isInstance(ret));
        LLVMFunctionDescriptor retFunction = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(ret).getObject();
        Assert.assertEquals(42, InteropLibrary.getUncached().execute(retFunction, 1, 42));
    }
}
