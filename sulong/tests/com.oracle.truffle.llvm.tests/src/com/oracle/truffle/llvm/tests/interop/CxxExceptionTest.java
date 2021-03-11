/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class CxxExceptionTest extends InteropTestBase {

    private static Object testLibrary;
    private static Object execute;
    private static Object safeExecute;

    @BeforeClass
    public static void loadTestBitcode() throws UnsupportedMessageException, UnknownIdentifierException {
        testLibrary = loadTestBitcodeInternal("interopExceptionTest.cpp");

        /*
         * execute function: throws object of class A={member: int x=50} if arg==0, else returns
         * (int) arg/2.
         */
        execute = InteropLibrary.getUncached(testLibrary).readMember(testLibrary, "execute");
        safeExecute = InteropLibrary.getUncached(testLibrary).readMember(testLibrary, "safeExecute");

    }

    @Test
    public void testNoCatch() throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        Assert.assertEquals(5, InteropLibrary.getUncached(execute).execute(execute, 11));
    }

    @Test
    public void testSafe() throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        final TruffleObject callback = new CatchCallback();
        Object result = InteropLibrary.getUncached(safeExecute).execute(safeExecute, callback);
        Assert.assertEquals(1L, result);
    }

    @Test
    public void testCatch() throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        try {
            InteropLibrary.getUncached(execute).execute(execute, 0);
            // unreachable
            Assert.assertTrue(false);
        } catch (AbstractTruffleException e) {
            Object intResult = InteropLibrary.getUncached(e).readMember(e, "x");
            Assert.assertEquals(50, intResult);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public class CatchCallback implements TruffleObject {

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        /**
         * @param arguments
         * @throws UnsupportedMessageException
         * @throws ArityException
         * @throws UnsupportedTypeException
         */
        @ExportMessage
        public Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return InteropLibrary.getUncached(execute).execute(execute, 0);
        }
    }

}
