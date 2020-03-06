/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class PointerArithmeticDerefTest extends InteropTestBase {

    static TruffleObject testLibrary;

    @BeforeClass
    public static void loadLibrary() {
        testLibrary = loadTestBitcodeInternal("pointerArithmetic.c");
    }

    public static class DerefPointerNode extends SulongTestNode {

        public DerefPointerNode() {
            super(testLibrary, "deref_pointer");
        }
    }

    public static class TestPointerAddNode extends SulongTestNode {

        public TestPointerAddNode() {
            super(testLibrary, "test_pointer_add");
        }
    }

    public static class TestPointerSubNode extends SulongTestNode {

        public TestPointerSubNode() {
            super(testLibrary, "test_pointer_sub");
        }
    }

    public static class TestPointerMulNode extends SulongTestNode {

        public TestPointerMulNode() {
            super(testLibrary, "test_pointer_mul");
        }
    }

    public static class TestPointerXorNode extends SulongTestNode {

        public TestPointerXorNode() {
            super(testLibrary, "test_pointer_xor");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class DerefableObject implements TruffleObject {

        boolean access = false;
        long lastAccessOffset;

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return Long.MAX_VALUE;
        }

        @ExportMessage
        boolean isArrayElementReadable(@SuppressWarnings("unused") long offset) {
            return true;
        }

        @ExportMessage
        byte readArrayElement(long offset) {
            access = true;
            lastAccessOffset = offset;
            return 42;
        }

        @ExportMessage
        void toNative() {
            Assert.fail("unexpected toNative");
        }

        void verify(int expectedOffset) {
            Assert.assertTrue("access", access);
            Assert.assertEquals("offset", expectedOffset, lastAccessOffset);
        }
    }

    private static Object ptr(long v) {
        return new NativeValue(v);
    }

    @Test
    public void testSimple(@Inject(DerefPointerNode.class) CallTarget derefPointer) {
        DerefableObject obj = new DerefableObject();
        derefPointer.call(obj);
        obj.verify(0);
    }

    @Test
    public void testOffset(
                    @Inject(DerefPointerNode.class) CallTarget derefPointer,
                    @Inject(TestPointerAddNode.class) CallTarget testPointerAdd) {
        DerefableObject obj = new DerefableObject();
        Object offsetPtr = testPointerAdd.call(obj, ptr(42));
        derefPointer.call(offsetPtr);
        obj.verify(42);
    }

    @Test
    public void testNegativeOffset(
                    @Inject(DerefPointerNode.class) CallTarget derefPointer,
                    @Inject(TestPointerSubNode.class) CallTarget testPointerSub) {
        DerefableObject obj = new DerefableObject();
        Object offsetPtr = testPointerSub.call(obj, ptr(42));
        derefPointer.call(offsetPtr);
        obj.verify(-42);
    }

    @Test
    public void testNegativePointer(
                    @Inject(DerefPointerNode.class) CallTarget derefPointer,
                    @Inject(TestPointerAddNode.class) CallTarget testPointerAdd,
                    @Inject(TestPointerSubNode.class) CallTarget testPointerSub,
                    @Inject(TestPointerMulNode.class) CallTarget testPointerMul) {
        DerefableObject obj = new DerefableObject();
        Object negative = testPointerSub.call(ptr(5), obj);
        Object negOffset = testPointerAdd.call(negative, ptr(3));
        Object posOffset = testPointerMul.call(negOffset, ptr(-1));
        derefPointer.call(posOffset);
        obj.verify(-8);
    }

    @Test
    public void testXorPointer(
                    @Inject(DerefPointerNode.class) CallTarget derefPointer,
                    @Inject(TestPointerXorNode.class) CallTarget testPointerXor,
                    @Inject(TestPointerSubNode.class) CallTarget testPointerSub) {
        DerefableObject obj = new DerefableObject();
        Object xored = testPointerXor.call(obj, ptr(-1));
        Object neg = testPointerSub.call(ptr(0), xored);
        derefPointer.call(neg);
        // 0 - (p ^ -1) == p + 1
        obj.verify(1);
    }

    @Test
    public void testSubtractCancel(
                    @Inject(TestPointerAddNode.class) CallTarget testPointerAdd,
                    @Inject(TestPointerSubNode.class) CallTarget testPointerSub) throws UnsupportedMessageException {
        DerefableObject obj = new DerefableObject();
        Object ptr1 = testPointerAdd.call(obj, ptr(27));
        Object ptr2 = testPointerAdd.call(obj, ptr(12));
        Object diff = testPointerSub.call(ptr1, ptr2);

        Assert.assertEquals("diff", 15, InteropLibrary.getFactory().getUncached().asPointer(diff));
    }
}
