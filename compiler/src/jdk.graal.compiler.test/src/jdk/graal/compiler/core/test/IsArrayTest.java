/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.io.Serializable;

import org.junit.Test;

public class IsArrayTest extends GraalCompilerTest {
    @SuppressWarnings("cast")
    public static boolean test1(Object o) {
        if (o instanceof Serializable) {
            /*
             * Add explicit type information that the object implements Serializable. That still
             * means that the object can be an array, i.e., the type information must not lead to
             * constant folding of the "isArray" check.
             */
            return ((Serializable) o).getClass().isArray();
        }
        return false;
    }

    @Test
    public void run1() throws Throwable {
        Object[] args = new Object[]{new String[0]};
        test("test1", args);

        args = new Object[]{"abc"};
        test("test1", args);
    }
}
