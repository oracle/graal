/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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

    static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    static Value testLibrary;
    static Value testVaListCallback4;
    static Value testMaybeVaPtr;
    static Value getNextVaarg;
    static Value derefCharCharPtr;
    static Value newStructA;

    @ExportLibrary(InteropLibrary.class)
    static class VaListCallback implements TruffleObject {

        private final boolean useNextMessage;

        VaListCallback(boolean useNextMessage) {
            this.useNextMessage = useNextMessage;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... arguments) {
            try {
                testInterop(arguments);

                if (useNextMessage) {
                    return 3;
                } else {
                    Object vaList = arguments[0];
                    Value res0 = getNextVaarg.execute(vaList);
                    Value res1 = getNextVaarg.execute(vaList);
                    return res0.asInt() + res1.asInt();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void testInterop(Object... arguments) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException, InvalidArrayIndexException {
            Object vaList = arguments[0];
            Object libHandle = arguments[1];

            long vaListSize = INTEROP.getArraySize(vaList);
            assertEquals(5, vaListSize);

            assertEquals(1, INTEROP.readArrayElement(vaList, 0));
            assertEquals(2, INTEROP.readArrayElement(vaList, 1));
            assertEquals(Double.doubleToRawLongBits(3.1), INTEROP.asPointer(INTEROP.readArrayElement(vaList, 2)));
            Object saNative = INTEROP.readArrayElement(vaList, 3);
            Object saManaged = INTEROP.readArrayElement(vaList, 4);
            assertTrue(saManaged instanceof TruffleObject);

            Object inttTypeId = INTEROP.execute(INTEROP.readMember(libHandle, "get_int_t_typeid"));
            Object doubletTypeId = INTEROP.execute(INTEROP.readMember(libHandle, "get_double_t_typeid"));
            Object structATypeId = INTEROP.execute(INTEROP.readMember(libHandle, "get_StructA_typeid"));

            Object[] types = {inttTypeId, inttTypeId, doubletTypeId, structATypeId, structATypeId};

            Object[] values = new Object[types.length];
            for (int i = types.length - 1; i >= 0; i--) {
                values[i] = INTEROP.invokeMember(vaList, "get", i, types[i]);
            }

            checkValues(saNative, structATypeId, values);

            if (useNextMessage) {
                for (int i = 0; i < types.length; i++) {
                    values[i] = INTEROP.invokeMember(vaList, "next", types[i]);
                }
                checkValues(saNative, structATypeId, values);
            }
        }

        private static void checkValues(Object saNative, Object structATypeId, Object[] values) {
            assertEquals(1, values[0]);
            assertEquals(2, values[1]);
            assertEquals(3.1, values[2]);
            assertEquals(saNative, values[3]);
            assertTrue(LLVMPointer.isInstance(values[3]));
            assertEquals(structATypeId, LLVMPointer.cast(values[3]).getExportType());
        }
    }

    @BeforeClass
    public static void loadLibrary() {
        testLibrary = loadTestBitcodeValue("valist.c");
        testVaListCallback4 = testLibrary.getMember("test_va_list_callback4");
        testMaybeVaPtr = testLibrary.getMember("test_maybe_va_ptr");
        getNextVaarg = testLibrary.getMember("get_next_vaarg");
        derefCharCharPtr = testLibrary.getMember("deref_chr_chr_ptr");
        newStructA = testLibrary.getMember("newStructA");
    }

    @Test
    public void testVaListCallback() {
        Value sa1 = newStructA.execute(10, 20);
        Value sa2 = Value.asValue(new StructA(30, 40));
        Value res = testVaListCallback4.execute(new VaListCallback(true), testLibrary, 1, 2, 3.1, sa1, sa2);
        Assert.assertEquals(3, res.asInt());
        res = testVaListCallback4.execute(new VaListCallback(false), testLibrary, 1, 2, 3.1, sa1, sa2);
        Assert.assertEquals(3, res.asInt());
    }

    @ExportLibrary(InteropLibrary.class)
    static class MaybeVaListCallback implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... arguments) {
            try {
                /*
                 * On darwin-aarch64 / windows-amd64 arguments[0] is a LLVMMaybeVaPointer, and in
                 * this case must behave like a pointer
                 */
                return derefCharCharPtr.execute(arguments[0]).asInt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testTestMaybeVaPtr() {
        Value res = testMaybeVaPtr.execute(new MaybeVaListCallback());
        Assert.assertEquals('A', res.asInt());
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
