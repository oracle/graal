/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.HashMap;
import org.junit.Assert;

@RunWith(CommonTestUtils.ExcludingTruffleRunner.class)
public class CmpXChgTest extends InteropTestBase {

    private static Object testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("cmpxchg.c");
    }

    public class TestCmpXChgNode extends SulongTestNode {

        public TestCmpXChgNode() {
            super(testLibrary, "cmpxchg_test");
        }
    }

    private static StructObject createContainer(Object content) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("content", content);
        return new StructObject(map);
    }

    @Test
    public void testPointer(@Inject(TestCmpXChgNode.class) CallTarget cmpXChg) {
        LLVMPointer expected = LLVMNativePointer.create(123);
        StructObject container = createContainer(expected);

        LLVMPointer value = LLVMNativePointer.create(456);
        Object ret = cmpXChg.call(container, expected, value);
        Assert.assertEquals("ret", 1, ret);
        Assert.assertEquals("content", value, container.get("content"));
    }

    @Test
    public void testNative(@Inject(TestCmpXChgNode.class) CallTarget cmpXChg) {
        NativeValue expected = new NativeValue(123);
        StructObject container = createContainer(expected);

        Object ret = cmpXChg.call(container, expected, new NativeValue(456));
        Assert.assertEquals("ret", 1, ret);
    }
}
