/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndexOobBytecodeExceptionTest extends BytecodeExceptionTest {

    public static void oobSnippet(Object[] empty, int idx, int length) {
        GraalDirectives.blackhole(empty[idx]);
        GraalDirectives.blackhole(length);
    }

    @Parameter public int index;

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        int[] values = {Integer.MIN_VALUE, -42, -1, 0, 1, 42, Integer.MAX_VALUE};

        ArrayList<Object[]> ret = new ArrayList<>(values.length);
        for (int i : values) {
            ret.add(new Object[]{i});
        }
        return ret;
    }

    @Test
    public void testOutOfBoundsException() {
        Object[] empty = new Object[0];
        test("oobSnippet", empty, index, empty.length);
    }
}
