/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PassItselfBackViaPolyglotEngineTest {
    private PolyglotEngine engine;
    private MyObj myObj;
    private PolyglotEngine.Value myObjWrapped;
    private CallWithValue myObjCall;

    @Test
    public void callbackWithParamTen() {
        myObjWrapped.execute(10);
        assertEquals("Assigned to ten", 10, myObj.value);
    }

    @Test
    public void callbackWithParamTruffleObject() {
        myObjWrapped.execute(myObjWrapped.get());
        assertEquals("Assigned to itself", myObj, myObj.value);
    }

    @Test
    public void callbackWithValueTen() {
        myObjCall.call(10);
        assertEquals("Assigned to ten", 10, myObj.value);
    }

    @Test
    public void callbackWithValueTruffleObject() {
        myObjCall.call(myObjWrapped.get());
        assertEquals("Assigned to itself", myObj, myObj.value);
    }

    @Before
    public void prepareSystem() {
        myObj = new MyObj();
        engine = PolyglotEngine.newBuilder().globalSymbol("myObj", myObj).build();
        myObjWrapped = engine.eval(Source.newBuilder("" + "function main() {\n" + "  return import(\"myObj\");\n" + "}\n").mimeType("application/x-sl").name("myObjWrapped.sl").build());
        assertNotNull(myObjWrapped.get());
        assertTrue(myObjWrapped.get() instanceof TruffleObject);
        assertFalse(myObjWrapped.get() instanceof MyObj);
        myObjCall = myObjWrapped.as(CallWithValue.class);
    }

    @After
    public void disposeSystem() {
        engine.dispose();
    }

    @MessageResolution(receiverType = MyObj.class)
    static final class MyObj implements TruffleObject {
        private Object value;

        @Override
        public ForeignAccess getForeignAccess() {
            return com.oracle.truffle.sl.test.MyObjForeign.ACCESS;
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
