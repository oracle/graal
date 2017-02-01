/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(value = Parameterized.class)
public abstract class StringIndexOfTestBase extends GraalCompilerTest {
    @Parameterized.Parameter(value = 0) public String sourceString;
    @Parameterized.Parameter(value = 1) public String constantString;

    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        String[] targets = new String[]{"foobar", "foo", "bar"};
        for (String source : targets) {
            for (String target : targets) {
                tests.add(new Object[]{source, target});
            }
        }
        for (String source : targets) {
            String s = "";
            for (int i = 0; i < 10; i++) {
                s = s + source.substring(0, source.length() - 1);
            }
            for (String target : targets) {
                tests.add(new Object[]{s, target});
                tests.add(new Object[]{s + target, target});
                tests.add(new Object[]{s.substring(0, s.length() - 1) + s, s});
            }
        }
        return tests;
    }

    public int testStringIndexOf(String a, String b) {
        return a.indexOf(b);
    }

    public int testStringIndexOfOffset(String a, String b, int fromIndex) {
        return a.indexOf(b, fromIndex);
    }

    public int testStringBuilderIndexOf(StringBuilder a, String b) {
        return a.indexOf(b);
    }
}
