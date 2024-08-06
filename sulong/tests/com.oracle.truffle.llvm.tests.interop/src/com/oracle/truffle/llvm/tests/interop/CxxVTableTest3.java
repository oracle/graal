/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates.
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
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.StringContains;
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
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.tests.interop.CxxVTableTest3Factory.CallNodeGen;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class CxxVTableTest3 extends InteropTestBase {

    private static Value testCppLibrary;
    private static Object testCppLibraryInternal;

    private static Object[] args;
    private static String methodName;

    private static Value getA1;
    private static Value getA2;
    private static Object getA2Internal;
    private static Value getA3;
    private static Value getA4;
    private static Object getA4Internal;

    private static Value consA1;
    private static Value consA2;
    private static Value consA3;
    private static Value consA4;
    private static Value consImpl;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("vtableTest3.cpp");
        testCppLibraryInternal = loadTestBitcodeInternal("vtableTest3.cpp");
        getA1 = testCppLibrary.getMember("getA1");
        getA2 = testCppLibrary.getMember("getA2");
        getA3 = testCppLibrary.getMember("getA3");
        getA4 = testCppLibrary.getMember("getA4");
        consA1 = testCppLibrary.getMember("A1");
        consA2 = testCppLibrary.getMember("A2");
        consA3 = testCppLibrary.getMember("A3");
        consA4 = testCppLibrary.getMember("A4");
        consImpl = testCppLibrary.getMember("Impl");

        try {
            getA2Internal = InteropLibrary.getUncached().readMember(testCppLibraryInternal, "getA2");
            getA4Internal = InteropLibrary.getUncached().readMember(testCppLibraryInternal, "getA4");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(e);
        }
    }

    abstract static class CallNode extends Node {
        abstract Object execute(Object a);

        @Specialization(limit = "3")
        Object doFoo1(Object a,
                        @CachedLibrary("a") InteropLibrary interop) {
            try {
                return interop.invokeMember(a, methodName, args);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(ex);
            }
        }
    }

    public static class TestVirtualCallNode extends RootNode {
        @Child CallNode call = CallNodeGen.create();

        public TestVirtualCallNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return call.execute(frame.getArguments()[0]);
        }
    }

    @Test
    public void testA2Internal(@Inject(TestVirtualCallNode.class) CallTarget call) throws InteropException {

        args = new Object[0];
        methodName = "a2";
        Object a2 = InteropLibrary.getUncached().execute(getA2Internal);
        Object ret = call.call(a2);
        Assert.assertEquals(12, InteropLibrary.getUncached().asInt(ret));
    }

    @Test
    public void testA4Internal(@Inject(TestVirtualCallNode.class) CallTarget call) throws InteropException {
        methodName = "a4";
        args = new Object[]{14};
        Object a4 = InteropLibrary.getUncached().execute(getA4Internal);
        Object ret = call.call(a4);
        Assert.assertEquals(28, InteropLibrary.getUncached().asInt(ret));
    }

    @Test
    public void testSameClass() {
        Assert.assertEquals(1, consA1.newInstance().invokeMember("a1").asInt());
        Assert.assertEquals(2, consA2.newInstance().invokeMember("a2").asInt());
        Assert.assertEquals(3, consA3.newInstance().invokeMember("a3").asInt());
        Assert.assertEquals(8, consA4.newInstance().invokeMember("a4", 4).asInt());
        Assert.assertEquals(10, consImpl.newInstance().invokeMember("impl").asInt());
    }

    @Test
    public void testSimpleVirtuality() {
        Assert.assertEquals(11, getA1.execute().invokeMember("a1").asInt());
    }

    @Test
    public void testVirtuality() {
        Assert.assertEquals(12, getA2.execute().invokeMember("a2").asInt());
        Assert.assertEquals(3, getA3.execute().invokeMember("a3").asInt());
        Assert.assertEquals(28, getA4.execute().invokeMember("a4", 14).asInt());
    }

    @Test
    public void testNonExisting() {
        UnsupportedOperationException exception = Assert.assertThrows(UnsupportedOperationException.class, () -> getA2.execute().invokeMember("a3"));
        MatcherAssert.assertThat(exception.getMessage(), StringContains.containsString("Non readable or non-existent member key 'a3'"));
    }
}
