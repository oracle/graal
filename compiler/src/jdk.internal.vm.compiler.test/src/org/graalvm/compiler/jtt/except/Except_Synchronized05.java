/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
/*
 */
package org.graalvm.compiler.jtt.except;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class Except_Synchronized05 extends JTTTest {

    static class Foo {

        Object field;

        public synchronized Object bar(int arg) {
            try {
                String f = foo1(arg);
                if (f == null) {
                    field = new Object();
                }
            } catch (NullPointerException e) {
                // do nothing
            }
            return field;
        }

        public Object baz(int arg) {
            synchronized (this) {
                try {
                    String f = foo1(arg);
                    if (f == null) {
                        field = new Object();
                    }
                } catch (NullPointerException e) {
                    // do nothing
                }
                return field;
            }
        }

        @SuppressWarnings("static-method")
        private String foo1(int arg) {
            if (arg == 0) {
                throw null;
            }
            return null;
        }

    }

    public static int test(int arg) {
        Foo obj = new Foo();
        int a = obj.bar(arg) != null ? 1 : 0;
        int b = obj.baz(arg) != null ? 1 : 0;
        return a + b;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

}
