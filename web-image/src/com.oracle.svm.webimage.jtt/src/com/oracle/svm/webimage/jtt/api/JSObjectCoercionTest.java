/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSValue;

public class JSObjectCoercionTest {
    public static void main(String[] args) {
        testRawObjectCoercion();
        testFacadeCoercion();
    }

    /**
     * Raw JS objects (with no Java counterpart) can be coerced {@link JSObject} subtypes.
     */
    private static void testRawObjectCoercion() {
        JSObject baseObject = createTestObject();
        JSObjectA facade = baseObject.as(JSObjectA.class);
        assertEquals(12, facade.propA);
        assertEquals("Hello World", facade.propB);
        assertEquals("Hidden String", facade.get("propC", String.class));
    }

    /**
     * {@link JSObject} subtypes can be coerced between each other.
     */
    private static void testFacadeCoercion() {
        JSObject objA = new JSObjectA();
        JSObject objB = new JSObjectB();

        JSObjectB aAsB = objA.as(JSObjectB.class);
        JSObjectA bAsA = objB.as(JSObjectA.class);

        assertEquals(12, aAsB.get("propA"));
        assertEquals(13, bAsA.get("someField"));

        // The Java fields are not initialized during coercion and are undefined when looked up.
        assertTrue(JSValue.isUndefined(aAsB.get("someField")));
        assertTrue(JSValue.isUndefined(bAsA.get("propA")));
        assertTrue(JSValue.isUndefined(bAsA.get("propB")));
    }

    @JS("""
                    return {
                        propA: 12,
                        propB: "Hello World",
                        propC: "Hidden String",
                    };
                    """)
    private static native JSObject createTestObject();

    public static class JSObjectA extends JSObject {
        protected int propA = 12;
        protected String propB;
    }

    public static class JSObjectB extends JSObject {
        protected int someField = 13;
    }
}
