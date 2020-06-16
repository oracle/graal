/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class PassItselfBackViaContextTest {
    private Context context;
    private MyObj myObj;
    private Value myObjWrapped;
    private CallWithValue myObjCall;

    @Test
    public void callbackWithParamTen() {
        myObjWrapped.execute(10);
        assertEquals("Assigned to ten", 10, myObj.value);
    }

    @Test
    public void callbackWithParamTruffleObject() {
        myObjWrapped.execute(myObjWrapped);
        assertEquals("Assigned to itself", myObj, myObj.value);
    }

    @Test
    public void callbackWithValueTen() {
        myObjCall.call(10);
        assertEquals("Assigned to ten", 10, myObj.value);
    }

    @Test
    public void callbackWithValueTruffleObject() {
        myObjCall.call(myObjWrapped);
        assertEquals("Assigned to itself", myObj, myObj.value);
    }

    @Before
    public void prepareSystem() {
        myObj = new MyObj();
        context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
        context.getPolyglotBindings().putMember("myObj", myObj);
        context.eval("sl", "function main() {\n" + "  return import(\"myObj\");\n" + "}\n");
        myObjWrapped = context.getBindings("sl").getMember("main").execute();
        assertFalse(myObjWrapped.isNull());
        myObjCall = myObjWrapped.as(CallWithValue.class);
    }

    @After
    public void disposeSystem() {
        context.close();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MyObj implements TruffleObject {
        private Object value;

        @ExportMessage
        Object execute(Object[] arguments) {
            value = arguments[0];
            return "";
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

    }

    abstract static class MyLang extends TruffleLanguage<Object> {
    }

    @FunctionalInterface
    interface CallWithValue {
        void call(Object value);
    }
}
