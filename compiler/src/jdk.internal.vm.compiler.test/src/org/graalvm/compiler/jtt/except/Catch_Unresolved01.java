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
package org.graalvm.compiler.jtt.except;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Catch_Unresolved01 extends JTTTest {

    public static boolean executed;

    public static int test(int arg) {
        executed = false;
        try {
            helper1(arg);
        } catch (Catch_Unresolved_Exception3 e) {
            return 1;
        } catch (Catch_Unresolved_Exception4 e) {
            return 2;
        }
        return 0;
    }

    private static void helper1(int arg) {
        if (executed) {
            throw new IllegalStateException("helper1 may only be called once");
        }
        executed = true;
        if (arg == 1) {
            throw new Catch_Unresolved_Exception3();
        } else if (arg == 2) {
            throw new Catch_Unresolved_Exception4();
        }
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

}

@SuppressWarnings("serial")
class Catch_Unresolved_Exception3 extends RuntimeException {
}

@SuppressWarnings("serial")
class Catch_Unresolved_Exception4 extends RuntimeException {
}
