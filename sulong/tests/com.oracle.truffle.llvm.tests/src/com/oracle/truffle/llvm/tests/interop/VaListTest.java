/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.tests.Platform;
import com.oracle.truffle.tck.TruffleRunner;

@RunWith(TruffleRunner.class)
public class VaListTest extends InteropTestBase {

    static Value testLibrary;
    static Value testVaListCallback3;
    static Value getNextVaarg;
    static Value newStructA;

    @ExportLibrary(InteropLibrary.class)
    static class VaListCallback implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... arguments) {
            try {
                testInvokeInterop(arguments);

                Object vaList = arguments[0];
                Value res0 = getNextVaarg.execute(vaList);
                Value res1 = getNextVaarg.execute(vaList);
                Value res2 = getNextVaarg.execute(vaList);
                return res0.asInt() + res1.asInt() + res2.asInt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static void testInvokeInterop(Object... arguments) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
            Object vaList = arguments[0];
            Object libHandle = arguments[1];
            InteropLibrary libHandleInterop = InteropLibrary.getUncached(libHandle);

            Object getInttTypeId = libHandleInterop.readMember(libHandle, "get_int_t_typeid");
            Object inttTypeId = InteropLibrary.getUncached(getInttTypeId).execute(getInttTypeId);
            Object vaListElem0 = InteropLibrary.getUncached(vaList).invokeMember(vaList, "get", 0, inttTypeId);
            assertEquals(1, vaListElem0);

            Object getStructATypeId = libHandleInterop.readMember(libHandle, "get_StructA_typeid");
            Object structATypeId = InteropLibrary.getUncached(getInttTypeId).execute(getStructATypeId);
            Object vaListElem3 = InteropLibrary.getUncached(vaList).invokeMember(vaList, "get", 3, structATypeId);
            assertNotNull(LLVMPointer.isInstance(vaListElem3));
            assertEquals(structATypeId, LLVMPointer.cast(vaListElem3).getExportType());
        }
    }

    @BeforeClass
    public static void loadLibrary() {
        testLibrary = loadTestBitcodeValue("valist.c");
        testVaListCallback3 = testLibrary.getMember("test_va_list_callback3");
        getNextVaarg = testLibrary.getMember("get_next_vaarg");
        newStructA = testLibrary.getMember("newStructA");
    }

    @Test
    public void testVaListCallback() {
        if (Platform.isAArch64()) {
            // TODO: enable when the managed va_list for AArch64 implemented
            return;
        }

        Value sa = newStructA.execute(10, 20);
        Value res = testVaListCallback3.execute(new VaListCallback(), testLibrary, 1, 2, 3, sa);
        Assert.assertEquals(6, res.asInt());
    }
}
