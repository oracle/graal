/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static org.junit.Assert.assertArrayEquals;

import java.util.HashMap;

import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.llvm.tests.interop.values.ArrayObject;

public class DynamicTypeCastInteropTest extends InteropTestBase {

    private static TruffleObject testLibraryInternal;
    private static Value testLibrary;
    private static Value test;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibraryInternal = loadTestBitcodeInternal("polyglotRegisterDynamicCast.c");
        testLibrary = runWithPolyglot.getPolyglotContext().asValue(testLibraryInternal);
        test = testLibrary.getMember("test_dynamic_cast");
    }

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    static class DynamicStructlikeObject implements TruffleObject {
        final HashMap<String, Object> map = new HashMap<>();

        DynamicStructlikeObject(HashMap<String, Object> map2) {
            map.putAll(map2);
            map.put("base", this);
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof DynamicStructlikeObject;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String name) {
            return map.containsKey(name);
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String name) throws UnknownIdentifierException {
            Object value = map.get(name);
            if (value == null) {
                throw UnknownIdentifierException.create(name);
            }
            return value;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ArrayObject(map.keySet().toArray());
        }

        @ExportMessage
        boolean hasNativeType() {
            return true;
        }

        static Object lookupNativeType() {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            try {
                Object typecall = interop.readMember(testLibraryInternal, "get_object2_typeid");
                return interop.execute(typecall);
            } catch (InteropException e) {
                throw new IllegalStateException("could not determine typeid");
            }
        }

        @ExportMessage
        Object getNativeType(@Cached(value = "lookupNativeType()", allowUncached = true) Object nativeType) {
            return nativeType;
        }
    }

    @Test
    public void dynamicCast() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("field1", 1);
        map.put("field2", 2);
        map.put("field3", 3);

        DynamicStructlikeObject structObject = new DynamicStructlikeObject(map);
        Object[] outArray = new Object[3];

        test.execute(structObject, outArray);
        assertArrayEquals(new Object[]{1, 2, 3}, outArray);
    }
}
