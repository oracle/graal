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
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class PointerCompareTest extends InteropTestBase {

    static Value testPointerAdd;

    @BeforeClass
    public static void loadLibrary() {
        Value testLibrary = loadTestBitcodeValue("pointerArithmetic.c");
        testPointerAdd = testLibrary.getMember("test_pointer_add");
    }

    @ExportLibrary(InteropLibrary.class)
    static class NotComparableObject implements TruffleObject {

        @ExportMessage
        void toNative() {
            Assert.fail("unexpected toNative");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class ComparableObject implements TruffleObject {

        final int identity;

        ComparableObject(int identity) {
            this.identity = identity;
        }

        @ExportMessage
        static class IsIdenticalOrUndefined {

            @Specialization
            static TriState doCompare(ComparableObject self, ComparableObject other) {
                return TriState.valueOf(self.identity == other.identity);
            }

            @Fallback
            static TriState doOther(@SuppressWarnings("unused") ComparableObject self, Object other) {
                assert !(other instanceof ComparableObject);
                return TriState.UNDEFINED;
            }
        }

        @ExportMessage
        int identityHashCode() {
            return identity;
        }

        @ExportMessage
        void toNative() {
            Assert.fail("unexpected toNative");
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static class WrappedObject implements TruffleObject {

        final Object delegate;

        WrappedObject(Object delegate) {
            this.delegate = delegate;
        }
    }

    private static Object ptr(long v) {
        return new NativeValue(v);
    }

    @Test
    public void testSameObject() {
        NotComparableObject obj = new NotComparableObject();
        Value ptr1 = testPointerAdd.execute(obj, ptr(42));
        Value ptr2 = testPointerAdd.execute(obj, ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }

    @Test
    public void testSameObjectDifferentOffset() {
        NotComparableObject obj = new NotComparableObject();
        Value ptr1 = testPointerAdd.execute(obj, ptr(42));
        Value ptr2 = testPointerAdd.execute(obj, ptr(38));
        Assert.assertFalse("!equals", ptr1.equals(ptr2));
    }

    @Test
    public void testDifferentObject() {
        Value ptr1 = testPointerAdd.execute(new NotComparableObject(), ptr(42));
        Value ptr2 = testPointerAdd.execute(new NotComparableObject(), ptr(42));
        Assert.assertFalse("!equals", ptr1.equals(ptr2));
    }

    @Test
    public void testIdenticalObject() {
        Value ptr1 = testPointerAdd.execute(new ComparableObject(1), ptr(42));
        Value ptr2 = testPointerAdd.execute(new ComparableObject(1), ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }

    @Test
    public void testSameAndIdenticalObject() {
        ComparableObject obj = new ComparableObject(2);
        Value ptr1 = testPointerAdd.execute(obj, ptr(42));
        Value ptr2 = testPointerAdd.execute(obj, ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }

    @Test
    public void testNotIdenticalObject() {
        Value ptr1 = testPointerAdd.execute(new ComparableObject(3), ptr(42));
        Value ptr2 = testPointerAdd.execute(new ComparableObject(4), ptr(42));
        Assert.assertFalse("!equals", ptr1.equals(ptr2));
    }

    @Test
    public void testDifferentTypes1() {
        Value ptr1 = testPointerAdd.execute(new NotComparableObject(), ptr(42));
        Value ptr2 = testPointerAdd.execute(new ComparableObject(5), ptr(42));
        Assert.assertFalse("!equals", ptr1.equals(ptr2));
    }

    @Test
    public void testDifferentTypes2() {
        Value ptr1 = testPointerAdd.execute(new ComparableObject(6), ptr(42));
        Value ptr2 = testPointerAdd.execute(new NotComparableObject(), ptr(42));
        Assert.assertFalse("!equals", ptr1.equals(ptr2));
    }

    @Test
    public void testWrappedObject1() {
        Value ptr1 = testPointerAdd.execute(new WrappedObject(new ComparableObject(7)), ptr(42));
        Value ptr2 = testPointerAdd.execute(new ComparableObject(7), ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }

    @Test
    public void testWrappedObject2() {
        Value ptr1 = testPointerAdd.execute(new ComparableObject(8), ptr(42));
        Value ptr2 = testPointerAdd.execute(new WrappedObject(new ComparableObject(8)), ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }

    @Test
    public void testWrappedObject3() {
        Value ptr1 = testPointerAdd.execute(new WrappedObject(new ComparableObject(9)), ptr(42));
        Value ptr2 = testPointerAdd.execute(new WrappedObject(new ComparableObject(9)), ptr(42));
        Assert.assertTrue("equals", ptr1.equals(ptr2));
    }
}
