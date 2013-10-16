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
package com.oracle.graal.replacements.test;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.phases.common.*;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class DeoptimizeOnExceptionTest extends GraalCompilerTest {

    public DeoptimizeOnExceptionTest() {
        getSuites().getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test1() {
        test("test1Snippet", "m1", "m2", "m3", "m4", "m5");
    }

    // no local exception handler - will deopt
    public static String test1Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            raiseException(m1, m2, m3, m4, m5);
        }
        return m1 + m2 + m3 + m4 + m5;
    }
}
