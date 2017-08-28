/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;

@RunWith(SeparateClassloaderTestRunner.class)
public class JavaInteropDocumentationTest {
    private static boolean loadedOK;

    private static final Class<?> INTEROP_SNIPPETS;

    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("com.oracle.truffle.api.interop.java.JavaInteropSnippets");
        } catch (ClassNotFoundException e) {
        }
        INTEROP_SNIPPETS = clazz;
    }

    @BeforeClass
    public static void isAvailable() throws Throwable {
        loadedOK = INTEROP_SNIPPETS != null;
    }

    @Test
    public void showHowToCheckForNull() throws Throwable {
        if (!loadedOK) {
            return;
        }

        Method m = INTEROP_SNIPPETS.getDeclaredMethod("isNullValue", TruffleObject.class);
        m.setAccessible(true);
        Constructor<?> constructor = INTEROP_SNIPPETS.getDeclaredConstructor();
        constructor.setAccessible(true);

        Object interopSnippets = constructor.newInstance();
        assertTrue("Yes, it is null", (boolean) m.invoke(interopSnippets, JavaInterop.asTruffleObject(null)));

        TruffleObject nonNullValue = JavaInterop.asTruffleObject(this);
        assertFalse("No, it is not null", (boolean) m.invoke(interopSnippets, nonNullValue));
    }
}
