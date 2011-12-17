/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.lang;

import java.io.*;

import com.sun.max.ide.*;

/**
 * Find out whether throwing an exception in a finally clause cancels out another exception in propagation flight.
 */
public class ThrowTest extends MaxTestCase {

    public ThrowTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ThrowTest.class);
    }

    private void throwArithmeticException() throws ArithmeticException {
        throw new ArithmeticException();
    }

    private void throwIOExcepion() throws IOException {
        throw new IOException();
    }

    public void test() {
        try {
            try {
                throwArithmeticException();
            } finally {
                throwIOExcepion();
            }
        } catch (IOException ioException) {
            System.out.println(ioException);
        }
    }

}
