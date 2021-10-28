/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.tests.interop.CxxVTableTest2Factory.CallFoo1NodeGen;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

@RunWith(TruffleRunner.class)
public class CxxVTableTest2 extends InteropTestBase {

    private static Value testCppLibrary;
    private static Object testCppLibraryInternal;
    private static Value preparePolyglotA;
    private static Value preparePolyglotBasA;
    private static Object preparePolyglotBasAInternal;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("vtableTest2.cpp");
        testCppLibraryInternal = loadTestBitcodeInternal("vtableTest2.cpp");
        preparePolyglotA = testCppLibrary.getMember("preparePolyglotA");
        preparePolyglotBasA = testCppLibrary.getMember("preparePolyglotBasA");
        try {
            preparePolyglotBasAInternal = InteropLibrary.getUncached().readMember(testCppLibraryInternal, "preparePolyglotBasA");
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(e);
        }
    }

    abstract static class CallFoo1Node extends Node {
        abstract Object execute(Object a);

        @Specialization(limit = "3")
        Object doFoo1(Object a,
                        @CachedLibrary("a") InteropLibrary interop) {
            try {
                return interop.invokeMember(a, "foo1");
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(ex);
            }
        }
    }

    public static class TestVirtualCallNode extends RootNode {
        @Child CallFoo1Node callFoo1 = CallFoo1NodeGen.create();

        public TestVirtualCallNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callFoo1.execute(frame.getArguments()[0]);
        }
    }

    @Test
    public void testVirtualCall(@Inject(TestVirtualCallNode.class) CallTarget call) throws InteropException {
        Object a = InteropLibrary.getUncached().execute(preparePolyglotBasAInternal);
        Object ret = call.call(a);
        Assert.assertEquals(11, InteropLibrary.getUncached().asInt(ret));
    }

    @Test
    public void testVirtualMethodsHiddenSubclass() {
        // checks if (A* a=new B())->foo is calling B::foo
        Value a = preparePolyglotBasA.execute();
        int foo1Result = a.invokeMember("foo1").asInt();
        int foo2Result = a.invokeMember("foo2").asInt();
        Assert.assertEquals(11, foo1Result);
        Assert.assertEquals(12, foo2Result);
    }

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNonExistingVirtualMethod() {
        Value a = preparePolyglotBasA.execute();

        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Non readable or non-existent member key 'foo3'");

        a.invokeMember("foo3").asInt();
    }

    @Test
    public void testVirtualMethodsParentOnly() {
        // checks if (A* a=new A())->foo is calling A::foo
        Value a = preparePolyglotA.execute();
        int foo1Result = a.invokeMember("foo1").asInt();
        int foo2Result = a.invokeMember("foo2").asInt();
        Assert.assertEquals(1, foo1Result);
        Assert.assertEquals(2, foo2Result);
    }

}
