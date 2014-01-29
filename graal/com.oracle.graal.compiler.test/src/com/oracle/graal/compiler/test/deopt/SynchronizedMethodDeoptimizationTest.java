/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.deopt;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.compiler.test.ea.EATestBase.TestClassObject;

/**
 * In the following tests, we try to deoptimize out of synchronized methods.
 */
public class SynchronizedMethodDeoptimizationTest extends GraalCompilerTest {

    public static final TestClassObject testObject = null;

    public static synchronized Object testMethodSynchronized(Object o) {
        if (o == null) {
            // this branch will always deoptimize
            return testObject.x;
        }
        return o;
    }

    @Test
    public void test1() {
        test("testMethodSynchronized", "test");
        test("testMethodSynchronized", (Object) null);
    }
}
