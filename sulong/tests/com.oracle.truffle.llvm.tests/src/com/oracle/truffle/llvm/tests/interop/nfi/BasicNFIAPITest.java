/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop.nfi;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback.Function;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class BasicNFIAPITest extends NFIAPITest {

    @Test
    public void returnIntSulong(@Inject(SulongCallReturnIntNode.class) CallTarget callTarget) {
        Assert.assertEquals(42, callTarget.call());
    }

    public static class SulongCallReturnIntNode extends SendExecuteNode {
        public SulongCallReturnIntNode() {
            super(sulongObject, "returnInt", "():sint32");
        }
    }

    @Test
    public void addSulong(@Inject(SulongCallAddNode.class) CallTarget callTarget) {
        Assert.assertEquals(42, callTarget.call(40, 2));
    }

    public static class SulongCallAddNode extends SendExecuteNode {
        public SulongCallAddNode() {
            super(sulongObject, "add", "(sint32,sint32):sint32");
        }
    }

    @Test
    public void functionPointerSulong(@Inject(SulongCallFunctionPointerNode.class) CallTarget callTarget) {
        TestCallback add = new TestCallback(2, new Function() {
            @Override
            public Object call(Object... args) {
                return (int) args[0] + (int) args[1];
            }
        });
        Assert.assertEquals(42, callTarget.call(add));
    }

    public static class SulongCallFunctionPointerNode extends SendExecuteNode {
        public SulongCallFunctionPointerNode() {
            super(sulongObject, "functionPointer", "((sint32,sint32):sint32):sint32");
        }
    }
}
