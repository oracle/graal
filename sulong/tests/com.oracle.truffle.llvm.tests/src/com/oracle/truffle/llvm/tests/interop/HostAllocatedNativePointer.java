/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.tck.TruffleRunner;

import sun.misc.Unsafe;

@RunWith(TruffleRunner.class)
public class HostAllocatedNativePointer extends InteropTestBase {
    @Test
    public void testHostAllocatedNativePointer() {
        final Unsafe unsafeMem;

        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafeMem = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new LLVMUnreachableNode.LLVMUnreachableException();
        }

        String str = "Hello!";

        /* Allocate space for the string + NULL termination. */
        long buf = unsafeMem.allocateMemory(str.length() + 1);

        /* Fill the buffer. */
        for (int i = 0; i < str.length(); i++) {
            unsafeMem.putByte(buf + i, (byte) str.charAt(i));
        }

        /* NULL-terminate the target string. */
        unsafeMem.putByte(buf + str.length(), (byte) 0);

        NativeValue myRawPointer = new NativeValue(buf);

        try (Context context = Context.newBuilder().allowAllAccess(true).allowNativeAccess(true).build()) {
            Value file = loadTestBitcodeValue("strcmp.c");
            Value strcmp = file.getMember("compare_with_native");
            strcmp.execute(myRawPointer);
        }
    }
}
