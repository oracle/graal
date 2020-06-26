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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.tck.TruffleRunner;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
@SuppressWarnings("deprecation") // tests backwards compatibility to deprecated ReferenceLibrary
public class DeprecatedPointerCompareTest extends InteropTestBase {

    static final InteropLibrary INTEROP = InteropLibrary.getUncached();
    static final ReferenceLibrary REFERENCES = ReferenceLibrary.getFactory().getUncached();

    static Object testPointerAdd;

    @BeforeClass
    public static void loadLibrary() throws InteropException {
        Object testLibrary = loadTestBitcodeInternal("pointerArithmetic.c");
        testPointerAdd = INTEROP.readMember(testLibrary, "test_pointer_add");
    }

    @ExportLibrary(ReferenceLibrary.class)
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("deprecation") // tests backwards compatibility to deprecated ReferenceLibrary
    static class ReferenceEqualObject implements TruffleObject {

        final int identity;

        ReferenceEqualObject(int identity) {
            this.identity = identity;
        }

        @ExportMessage
        static class IsSame {

            @Specialization
            static boolean doCompare(ReferenceEqualObject self, ReferenceEqualObject other) {
                return self.identity == other.identity;
            }

            @Fallback
            static boolean doOther(@SuppressWarnings("unused") ReferenceEqualObject self, Object other) {
                assert !(other instanceof ReferenceEqualObject);
                return false;
            }
        }

        @ExportMessage
        void toNative() {
            Assert.fail("unexpected toNative");
        }
    }

    private static Object ptr(long v) {
        return new NativeValue(v);
    }

    @Test
    public void testIdenticalObject() throws InteropException {
        Object ptr1 = INTEROP.execute(testPointerAdd, new ReferenceEqualObject(1), ptr(42));
        Object ptr2 = INTEROP.execute(testPointerAdd, new ReferenceEqualObject(1), ptr(42));
        Assert.assertTrue("equals", REFERENCES.isSame(ptr1, ptr2));
    }

    @Test
    public void testSameAndIdenticalObject() throws InteropException {
        ReferenceEqualObject obj = new ReferenceEqualObject(2);
        Object ptr1 = INTEROP.execute(testPointerAdd, obj, ptr(42));
        Object ptr2 = INTEROP.execute(testPointerAdd, obj, ptr(42));
        Assert.assertTrue("equals", REFERENCES.isSame(ptr1, ptr2));
    }

    @Test
    public void testNotIdenticalObject() throws InteropException {
        Object ptr1 = INTEROP.execute(testPointerAdd, new ReferenceEqualObject(3), ptr(42));
        Object ptr2 = INTEROP.execute(testPointerAdd, new ReferenceEqualObject(4), ptr(42));
        Assert.assertFalse("!equals", REFERENCES.isSame(ptr1, ptr2));
    }
}
