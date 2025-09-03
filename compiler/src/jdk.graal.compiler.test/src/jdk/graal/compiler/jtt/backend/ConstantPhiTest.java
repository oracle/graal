/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.jtt.backend;

import static jdk.graal.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.core.common.GraalOptions.MaximumInliningSize;

import java.lang.reflect.Method;

import jdk.graal.compiler.jtt.JTTTest;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

public class ConstantPhiTest extends JTTTest {

    public static int test(int i, int x) throws Throwable {
        int r;
        if (injectBranchProbability(LIKELY_PROBABILITY, i < 0)) {
            r = 42;
        } else {
            r = x;
        }
        destroyCallerSavedValues();
        return r;
    }

    protected static void destroyCallerSavedValues() throws Throwable {
        Class<ConstantPhiTest> c = ConstantPhiTest.class;
        Method m = c.getMethod("destroyCallerSavedValues0");
        m.invoke(null);
    }

    public static void destroyCallerSavedValues0() {
    }

    @Test
    public void run0() {
        runTest(new OptionValues(getInitialOptions(), MaximumInliningSize, -1), "test", 0, 0xDEADDEAD);
    }

    @Test
    public void run1() {
        runTest(new OptionValues(getInitialOptions(), MaximumInliningSize, -1), "test", -1, 0xDEADDEAD);
    }

    @Test
    public void run2() {
        runTest(new OptionValues(getInitialOptions(), MaximumInliningSize, -1), "test", 1, 0xDEADDEAD);
    }
}
