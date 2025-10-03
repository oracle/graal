/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.api;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSSymbol;
import org.graalvm.webimage.api.JSValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class JSSymbolTest {

    public static void main(String[] args) {
        testAsyncDispose();
        testAsyncIterator();
        testForKey();
        testDispose();
        testEquality();
        testHasInstance();
        testIsSameSymbol();
        testIterator();
        testKeyFor();
        testMatchAll();
        testMatch();
        testReplace();
        testSearch();
        testSpecies();
        testSplit();
        testToPrimitive();
        testToStringTag();
        testUnscopables();
        testValueOf();
    }

    public static void testAsyncDispose() {
        JSSymbol sym = JSSymbol.asyncDispose();

        assertEquals("JavaScript<symbol; Symbol(nodejs.asyncDispose)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("nodejs.asyncDispose", JSSymbol.description(sym));
    }

    public static void testAsyncIterator() {
        JSSymbol sym = JSSymbol.asyncIterator();

        assertEquals("JavaScript<symbol; Symbol(Symbol.asyncIterator)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.asyncIterator", JSSymbol.description(sym));
    }

    public static void testForKey() {
        JSSymbol sym = JSSymbol.forKey("alpha");

        assertEquals("JavaScript<symbol; Symbol(alpha)>", sym.toString());
        assertEquals("alpha", JSSymbol.description(sym));
    }

    public static void testDispose() {
        JSSymbol sym = JSSymbol.dispose();

        assertEquals("JavaScript<symbol; Symbol(nodejs.dispose)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("nodejs.dispose", JSSymbol.description(sym));
    }

    public static void testEquality() {
        JSSymbol sym1 = JSSymbol.forKey("shared");
        JSSymbol sym2 = JSSymbol.forKey("shared");
        JSSymbol sym3 = JSSymbol.forKey("unique");

        assertEquals(sym1, sym2);
        assertNotEquals(sym1, sym3);
    }

    public static void testHasInstance() {
        JSSymbol sym = JSSymbol.hasInstance();

        assertEquals("JavaScript<symbol; Symbol(Symbol.hasInstance)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.hasInstance", JSSymbol.description(sym));
    }

    public static void testIsSameSymbol() {
        JSSymbol sym1 = JSSymbol.forKey("shared");
        JSSymbol sym2 = JSSymbol.forKey("shared");
        JSSymbol sym3 = JSSymbol.forKey("unique");

        assertTrue(JSSymbol.isSameSymbol(sym1, sym2));
        assertFalse(JSSymbol.isSameSymbol(sym1, sym3));
    }

    public static void testIterator() {
        JSSymbol sym = JSSymbol.iterator();

        assertEquals("JavaScript<symbol; Symbol(Symbol.iterator)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.iterator", JSSymbol.description(sym));
    }

    public static void testKeyFor() {
        JSSymbol shared1 = JSSymbol.forKey("alpha");
        JSSymbol shared2 = JSSymbol.forKey("beta");
        String result1 = JSValue.checkedCoerce(JSSymbol.keyFor(shared1), String.class);
        String result2 = JSValue.checkedCoerce(JSSymbol.keyFor(shared2), String.class);
        JSSymbol local = createLocalSymbol("gamma");
        JSValue result3 = JSSymbol.keyFor(local);

        assertEquals("alpha", result1);
        assertEquals("beta", result2);
        assertEquals(JSValue.undefined(), result3);
    }

    public static void testMatchAll() {
        JSSymbol sym = JSSymbol.matchAll();

        assertEquals("JavaScript<symbol; Symbol(Symbol.matchAll)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.matchAll", JSSymbol.description(sym));
    }

    public static void testMatch() {
        JSSymbol sym = JSSymbol.match();

        assertEquals("JavaScript<symbol; Symbol(Symbol.match)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.match", JSSymbol.description(sym));
    }

    public static void testReplace() {
        JSSymbol sym = JSSymbol.replace();

        assertEquals("JavaScript<symbol; Symbol(Symbol.replace)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.replace", JSSymbol.description(sym));
    }

    public static void testSearch() {
        JSSymbol sym = JSSymbol.search();

        assertEquals("JavaScript<symbol; Symbol(Symbol.search)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.search", JSSymbol.description(sym));
    }

    public static void testSpecies() {
        JSSymbol sym = JSSymbol.species();

        assertEquals("JavaScript<symbol; Symbol(Symbol.species)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.species", JSSymbol.description(sym));
    }

    public static void testSplit() {
        JSSymbol sym = JSSymbol.split();

        assertEquals("JavaScript<symbol; Symbol(Symbol.split)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.split", JSSymbol.description(sym));
    }

    public static void testToPrimitive() {
        JSSymbol sym = JSSymbol.toPrimitive();

        assertEquals("JavaScript<symbol; Symbol(Symbol.toPrimitive)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.toPrimitive", JSSymbol.description(sym));
    }

    public static void testToStringTag() {
        JSSymbol sym = JSSymbol.toStringTag();

        assertEquals("JavaScript<symbol; Symbol(Symbol.toStringTag)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.toStringTag", JSSymbol.description(sym));
    }

    public static void testUnscopables() {
        JSSymbol sym = JSSymbol.unscopables();

        assertEquals("JavaScript<symbol; Symbol(Symbol.unscopables)>", sym.toString());
        assertEquals(JSSymbol.class, sym.getClass());
        assertEquals("Symbol.unscopables", JSSymbol.description(sym));
    }

    public static void testValueOf() {
        JSSymbol original = JSSymbol.forKey("alpha");
        JSSymbol value = JSSymbol.valueOf(original);

        assertEquals("JavaScript<symbol; Symbol(alpha)>", original.toString());
        assertEquals("JavaScript<symbol; Symbol(alpha)>", value.toString());
        assertNotSame(original, value);
        assertEquals("alpha", JSSymbol.description(value));
    }

    @JS.Coerce
    @JS(value = "return Symbol(desc);")
    private static native JSSymbol createLocalSymbol(String desc);
}
