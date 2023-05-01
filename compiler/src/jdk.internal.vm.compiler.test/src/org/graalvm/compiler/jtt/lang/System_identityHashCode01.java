/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class System_identityHashCode01 extends JTTTest {

    private static final Object object0 = new Object();
    private static final Object object1 = new Object();
    private static final Object object2 = new Object();

    private static final int hash0 = System.identityHashCode(object0);
    private static final int hash1 = System.identityHashCode(object1);
    private static final int hash2 = System.identityHashCode(object2);

    public static boolean test(int i) {
        if (i == 0) {
            return hash0 == System.identityHashCode(object0);
        }
        if (i == 1) {
            return hash1 == System.identityHashCode(object1);
        }
        if (i == 2) {
            return hash2 == System.identityHashCode(object2);
        }
        if (i == 3) {
            return 0 == System.identityHashCode(null);
        }
        return false;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }
}
