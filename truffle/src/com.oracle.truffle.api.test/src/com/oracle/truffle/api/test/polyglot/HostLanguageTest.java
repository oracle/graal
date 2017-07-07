/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class HostLanguageTest {

    public static class MyClass {

    }

    @Test
    public void testHostLanguage() {
        Context context = Context.newBuilder("java").setOption("java.AllowClassLoading", "true").build();
        Language language = context.getEngine().getLanguage("java");

        assertTrue(language.isHost());
        assertEquals("java", language.getId());
        assertEquals("Java", language.getName());
        assertEquals(System.getProperty("java.version"), language.getVersion());

        MyClass clazz = new MyClass();
        Value value;
        value = context.eval("java", MyClass.class.getName());
        assertTrue(value.isHostObject());
        assertSame(MyClass.class, value.asHostObject());
        // context class loader is captured when the engine is created to create new instances.
        assertSame(clazz.getClass().getClassLoader(), ((Class<?>) value.asHostObject()).getClassLoader());

        value = context.lookup("java", MyClass.class.getName());
        assertTrue(value.isHostObject());
        assertSame(MyClass.class, value.asHostObject());
        assertSame(clazz.getClass().getClassLoader(), ((Class<?>) value.asHostObject()).getClassLoader());
    }

}
