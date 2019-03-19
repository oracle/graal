/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;

@SuppressWarnings("deprecation")
public class ForeignAccessToStringTest {
    @Test
    public void checkRegularFactory() {
        com.oracle.truffle.api.interop.ForeignAccess fa = com.oracle.truffle.api.interop.ForeignAccess.create(new SimpleTestingFactory());
        assertEquals("ForeignAccess[" + ForeignAccessToStringTest.class.getName() + "$SimpleTestingFactory]", fa.toString());
    }

    @Test
    public void check10Factory() {
        com.oracle.truffle.api.interop.ForeignAccess fa = com.oracle.truffle.api.interop.ForeignAccess.create(new Simple10TestingFactory(), null);
        assertEquals("ForeignAccess[" + ForeignAccessToStringTest.class.getName() + "$Simple10TestingFactory]", fa.toString());
    }

    private static class SimpleTestingFactory implements com.oracle.truffle.api.interop.ForeignAccess.Factory {
        SimpleTestingFactory() {
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            return false;
        }

        @Override
        public CallTarget accessMessage(com.oracle.truffle.api.interop.Message tree) {
            return null;
        }
    }

    private static class Simple10TestingFactory implements com.oracle.truffle.api.interop.ForeignAccess.StandardFactory, com.oracle.truffle.api.interop.ForeignAccess.Factory {
        @Override
        public CallTarget accessIsNull() {
            return null;
        }

        @Override
        public CallTarget accessIsExecutable() {
            return null;
        }

        @Override
        public CallTarget accessIsBoxed() {
            return null;
        }

        @Override
        public CallTarget accessHasSize() {
            return null;
        }

        @Override
        public CallTarget accessGetSize() {
            return null;
        }

        @Override
        public CallTarget accessUnbox() {
            return null;
        }

        @Override
        public CallTarget accessRead() {
            return null;
        }

        @Override
        public CallTarget accessWrite() {
            return null;
        }

        @Override
        public CallTarget accessExecute(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessMessage(com.oracle.truffle.api.interop.Message unknown) {
            return null;
        }

        @Override
        public CallTarget accessNew(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessKeyInfo() {
            return null;
        }

        @Override
        public CallTarget accessKeys() {
            return null;
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            return true;
        }

        public CallTarget accessIsPointer() {
            return null;
        }

        public CallTarget accessAsPointer() {
            return null;
        }

        public CallTarget accessToNative() {
            return null;
        }

        @Override
        public CallTarget accessIsInstantiable() {
            return null;
        }

        @Override
        public CallTarget accessHasKeys() {
            return null;
        }
    }
}
