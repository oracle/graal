/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.vm.PolyglotEngine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

public class ConvertAndClassCastTest {
    @Test(expected = ClassCastException.class)
    public void convertStringToIntThrowsClassCastException() {
        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", "strObj").build();

        eng.findGlobalSymbol("value").as(Integer.class);
    }

    @Test(expected = ClassCastException.class)
    public void convertObjToIntThrowsClassCastException() {
        Object obj = new Object();
        TruffleObject truffleObj = JavaInterop.asTruffleObject(obj);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        Integer v = eng.findGlobalSymbol("value").as(Integer.class);
        fail("No value, but exception: " + v);
    }

    @Test(expected = ClassCastException.class)
    public void convertObjToCharacterThrowsClassCastException() {
        Object obj = new Object();
        TruffleObject truffleObj = JavaInterop.asTruffleObject(obj);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        Character v = eng.findGlobalSymbol("value").as(Character.class);
        fail("No value, but exception: " + v);
    }

    public void convertObjToStringUsesObjectsToString() {
        Object obj = new Object();
        TruffleObject truffleObj = JavaInterop.asTruffleObject(obj);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        String v = eng.findGlobalSymbol("value").as(String.class);
        assertEquals(obj.toString(), v);
    }

    public void convertUnboxableIntToCharWorks() {
        TruffleObject truffleObj = new UnboxToIntObject(42);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        Character v = eng.findGlobalSymbol("value").as(Character.class);
        assertEquals('*', v.charValue());
    }

    @Test(expected = ClassCastException.class)
    public void convertUnboxableObjToBooleanThrowsClassCastException() {
        TruffleObject truffleObj = new UnboxToIntObject(42);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        Boolean v = eng.findGlobalSymbol("value").as(Boolean.class);
        fail("No value, but exception: " + v);
    }

    public void convertUnboxableObjToStringUsesObjectToString() {
        TruffleObject truffleObj = new UnboxToIntObject(42);

        PolyglotEngine eng = PolyglotEngine.newBuilder().globalSymbol("value", truffleObj).build();

        String v = eng.findGlobalSymbol("value").as(String.class);
        assertEquals(truffleObj.toString(), v);
    }
}
