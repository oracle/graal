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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JSSymbolTest {

    public static void main(String[] args) {
        testForKey();
        testEquality();
        testIsSameSymbol();
        testKeyFor();
    }

    public static void testForKey() {
        JSSymbol sym = JSSymbol.forKey("alpha");

        assertEquals("JavaScript<symbol; Symbol(alpha)>", sym.toString());
    }

    public static void testEquality() {
        JSSymbol sym1 = JSSymbol.forKey("shared");
        JSSymbol sym2 = JSSymbol.forKey("shared");
        JSSymbol sym3 = JSSymbol.forKey("unique");

        assertEquals(sym1, sym2);
        assertNotEquals(sym1, sym3);
    }

    public static void testIsSameSymbol() {
        JSSymbol sym1 = JSSymbol.forKey("shared");
        JSSymbol sym2 = JSSymbol.forKey("shared");
        JSSymbol sym3 = JSSymbol.forKey("unique");

        assertTrue(JSSymbol.isSameSymbol(sym1, sym2));
        assertFalse(JSSymbol.isSameSymbol(sym1, sym3));
    }

    public static void testKeyFor() {
        JSSymbol shared1 = JSSymbol.forKey("alpha");
        JSSymbol shared2 = JSSymbol.forKey("beta");
        String result1 = JSSymbol.keyFor(shared1);
        String result2 = JSSymbol.keyFor(shared2);
        JSSymbol local = createLocalSymbol("gamma");
        String result3 = JSSymbol.keyFor(local);

        assertEquals("alpha", result1);
        assertEquals("beta", result2);
        assertNull(result3);
    }

    @JS.Coerce
    @JS(value = "return Symbol(desc);")
    private static native JSSymbol createLocalSymbol(String desc);
}
