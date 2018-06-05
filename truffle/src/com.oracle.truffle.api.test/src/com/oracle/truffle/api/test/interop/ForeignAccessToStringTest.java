/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;

public class ForeignAccessToStringTest {
    @Test
    public void checkRegularFactory() {
        ForeignAccess fa = ForeignAccess.create(new SimpleTestingFactory());
        assertEquals("ForeignAccess[" + ForeignAccessToStringTest.class.getName() + "$SimpleTestingFactory]", fa.toString());
    }

    @Test
    public void check10Factory() {
        ForeignAccess fa = ForeignAccess.create(new Simple10TestingFactory(), null);
        assertEquals("ForeignAccess[" + ForeignAccessToStringTest.class.getName() + "$Simple10TestingFactory]", fa.toString());
    }

    private static class SimpleTestingFactory implements ForeignAccess.Factory {
        SimpleTestingFactory() {
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            return false;
        }

        @Override
        public CallTarget accessMessage(Message tree) {
            return null;
        }
    }

    private static class Simple10TestingFactory implements ForeignAccess.StandardFactory, ForeignAccess.Factory {
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
        public CallTarget accessMessage(Message unknown) {
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
