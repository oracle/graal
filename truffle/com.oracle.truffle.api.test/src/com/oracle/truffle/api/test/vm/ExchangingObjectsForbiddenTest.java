/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.vm;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotEngine;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ExchangingObjectsForbiddenTest {
    private MyObj myObj;
    private PolyglotEngine myEngine;
    private PolyglotEngine.Value myObjWrapped;
    private CallWithValue myObjCall;
    private MyObj otherObj;
    private PolyglotEngine otherEngine;
    private PolyglotEngine.Value otherObjWrapped;

    @Test
    public void sendOtherObjectAsParameterToMyEngine() {
        myObjWrapped.execute(otherObj);
        assertEquals("Plain TruffleObject can be sent anywhere", otherObj, myObj.value);
    }

    @Test
    public void sendWrappedOtherObjectAsParameterToMyEngine() {
        myObj.value = null;
        try {
            myObjWrapped.execute(otherObjWrapped.get());
        } catch (IllegalArgumentException ex) {
            // OK
        }
        assertNull("Value hasn't been changed", myObj.value);
    }

    @Test
    public void sendOtherObjectViaCallToMyEngine() {
        myObjCall.call(otherObj);
        assertEquals("Plain TruffleObject can be sent anywhere", otherObj, myObj.value);
    }

    @Test
    public void sendWrappedOtherObjectViaCallToMyEngine() {
        myObj.value = null;
        try {
            myObjWrapped.execute(otherObjWrapped.get());
        } catch (IllegalArgumentException ex) {
            // OK
        }
        assertNull("Value hasn't been changed", myObj.value);
    }

    @Before
    public void prepareSystem() {
        myObj = new MyObj();
        myEngine = PolyglotEngine.newBuilder().globalSymbol("myObj", myObj).build();
        myObjWrapped = myEngine.findGlobalSymbol("myObj");
        assertNotNull(myObjWrapped.get());
        assertTrue(myObjWrapped.get() instanceof TruffleObject);
        assertFalse(myObjWrapped.get() instanceof MyObj);
        myObjCall = myObjWrapped.as(CallWithValue.class);

        otherObj = new MyObj();
        otherEngine = PolyglotEngine.newBuilder().globalSymbol("myObj", otherObj).build();
        otherObjWrapped = otherEngine.findGlobalSymbol("myObj");
    }

    @After
    public void disposeSystem() {
        myEngine.dispose();
    }

    @MessageResolution(receiverType = MyObj.class)
    static final class MyObj implements TruffleObject {
        private Object value;

        @Override
        public ForeignAccess getForeignAccess() {
            return MyObjForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof MyObj;
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecNode extends Node {
            protected Object access(MyObj obj, Object... value) {
                obj.value = value[0];
                return JavaInterop.asTruffleValue(null);
            }
        }
    }

    abstract static class MyLang extends TruffleLanguage<Object> {
    }

    @FunctionalInterface
    interface CallWithValue {
        @MethodMessage(message = "EXECUTE")
        void call(Object value);
    }

}
