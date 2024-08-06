/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class CompareZeroExtendWithConstantTest extends GraalCompilerTest {

    public static byte[] a = {};

    public static void snippet01() {
        for (byte b : a) {
            char c = (char) b;
            GraalDirectives.blackhole(c);
            if ((short) c != -19704) {
                GraalDirectives.controlFlowAnchor();
            }
        }
    }

    @Test
    public void testSnippet01() {
        test("snippet01");
    }

    public static boolean snippet02(boolean p0, long p1) {
        boolean var0 = p0;
        byte b = (byte) p1;
        for (long i = 245799965; i >= 245797839; i = 3) {
            b = (byte) Character.toUpperCase((char) b);
        }
        return var0;
    }

    @Test
    public void testSnippet02() {
        test("snippet02", true, 53069L);
    }
}
