/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.*;

import sun.misc.*;

public class MarkUnsafeAccessTest extends GraalCompilerTest {

    public static Unsafe unsafe;

    public void getRaw() {
        unsafe.getInt(0L);
    }

    public void get() {
        unsafe.getInt(null, 0L);
    }

    public void putRaw() {
        unsafe.putInt(0L, 0);
    }

    public void put() {
        unsafe.putInt(null, 0L, 0);
    }

    public void cas() {
        unsafe.compareAndSwapInt(null, 0, 0, 0);
    }

    public void noAccess() {
        unsafe.addressSize();
        unsafe.pageSize();
    }

    private void assertHasUnsafe(String name, boolean hasUnsafe) {
        Assert.assertEquals(hasUnsafe, compile(getResolvedJavaMethod(name), null).hasUnsafeAccess());
    }

    @Test
    public void testGet() {
        assertHasUnsafe("get", true);
        assertHasUnsafe("getRaw", true);
    }

    @Test
    public void testPut() {
        assertHasUnsafe("put", true);
        assertHasUnsafe("putRaw", true);
    }

    @Test
    public void testCas() {
        assertHasUnsafe("cas", true);
    }

    @Test
    public void testNoAcces() {
        assertHasUnsafe("noAccess", false);
    }
}
