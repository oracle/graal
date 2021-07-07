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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(TruffleRunner.class)
public class VAListInteropTest extends InteropTestBase {

    static Value testLibrary;
    static Value testVaListCallback4;
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
                testInterop(arguments);

                Object vaList = arguments[0];
                Value res0 = getNextVaarg.execute(vaList);
                Value res1 = getNextVaarg.execute(vaList);
                Value res2 = getNextVaarg.execute(vaList);
                return res0.asInt() + res1.asInt() + res2.asInt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static void testInterop(Object... arguments) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException, InvalidArrayIndexException {
            Object vaList = arguments[0];
            Object libHandle = arguments[1];

            final InteropLibrary vaInterop = InteropLibrary.getUncached(vaList);
            long vaListSize = vaInterop.getArraySize(vaList);
            assertEquals(5, vaListSize);

            assertEquals(1, vaInterop.readArrayElement(vaList, 0));
            assertEquals(2, vaInterop.readArrayElement(vaList, 1));
            assertEquals(3, vaInterop.readArrayElement(vaList, 2));
            Object saNative = vaInterop.readArrayElement(vaList, 3);
            Object saManaged = vaInterop.readArrayElement(vaList, 4);
            assertTrue(saManaged instanceof TruffleObject);

            InteropLibrary libHandleInterop = InteropLibrary.getUncached(libHandle);

            Object getInttTypeId = libHandleInterop.readMember(libHandle, "get_int_t_typeid");
            Object inttTypeId = InteropLibrary.getUncached(getInttTypeId).execute(getInttTypeId);
            Object vaListElem0 = vaInterop.invokeMember(vaList, "get", 0, inttTypeId);
            assertEquals(1, vaListElem0);

            Object getStructATypeId = libHandleInterop.readMember(libHandle, "get_StructA_typeid");
            Object structATypeId = InteropLibrary.getUncached(getInttTypeId).execute(getStructATypeId);
            Object vaListElem3 = vaInterop.invokeMember(vaList, "get", 3, structATypeId);
            assertEquals(saNative, vaListElem3);
            assertTrue(LLVMPointer.isInstance(vaListElem3));
            assertEquals(structATypeId, LLVMPointer.cast(vaListElem3).getExportType());
        }
    }

    @BeforeClass
    public static void loadLibrary() {
        testLibrary = loadTestBitcodeValue("valist.c");
        testVaListCallback4 = testLibrary.getMember("test_va_list_callback4");
        getNextVaarg = testLibrary.getMember("get_next_vaarg");
        newStructA = testLibrary.getMember("newStructA");
    }

    @Test
    public void testVaListCallback() {
        Value sa1 = newStructA.execute(10, 20);
        Value sa2 = Value.asValue(new StructA(30, 40));
        Value res = testVaListCallback4.execute(new VaListCallback(), testLibrary, 1, 2, 3, sa1, sa2);
        Assert.assertEquals(6, res.asInt());
    }

    public static class StructA {
        public int x;
        public int y;

        public StructA(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
